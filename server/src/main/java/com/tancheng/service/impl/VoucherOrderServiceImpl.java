package com.tancheng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tancheng.annotation.DistributedLock;
import com.tancheng.dto.Result;
import com.tancheng.entity.VoucherOrder;
import com.tancheng.exception.LockFailedException;
import com.tancheng.mapper.VoucherOrderMapper;
import com.tancheng.service.ISeckillVoucherService;
import com.tancheng.service.IVoucherOrderService;
import com.tancheng.service.RateLimitService;
import com.tancheng.service.SeckillResultService;
import com.tancheng.utils.OrderDelayQueue;
import com.tancheng.utils.RedisConstants;
import com.tancheng.utils.RedisIdWorker;
import com.tancheng.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 优惠券秒杀主流程：
 * 1. 用户维度滑动窗口限流，挡住脚本刷接口
 * 2. Lua 脚本在 Redis 里原子完成「库存校验 + 一人一单 + 扣减 + 投递消息」
 * 3. Redis Stream 消费者组异步落库（pending 列表兜底重试），落库方法用 @DistributedLock 保证同一用户串行
 * 4. 结果通过 SSE 实时推送给前端，前端不需要轮询
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final String STREAM_QUEUE = "stream.orders";
    private static final String STREAM_GROUP = "g1";
    private static final String STREAM_CONSUMER = "c1";
    private static final String SECKILL_LIMIT_KEY = "limit:seckill:";

    /** 订单状态：1 未支付、2 已支付、4 已取消 */
    private static final int ORDER_STATUS_UNPAID = 1;
    private static final int ORDER_STATUS_PAID = 2;
    private static final int ORDER_STATUS_CANCELLED = 4;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SeckillResultService seckillResultService;
    @Resource
    private RateLimitService rateLimitService;
@Resource
    private ApplicationContext applicationContext;
    @Resource
    private OrderDelayQueue orderDelayQueue;

    /** 未支付超时时间（毫秒），默认 15 分钟 */
    @Value("${app.order-timeout-ms:900000}")
    private long orderTimeoutMs;
    private final ExecutorService seckillOrderExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "seckill-order-consumer");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running = true;

    @PostConstruct
    private void init() {
        seckillOrderExecutor.submit(new VoucherOrderHandler());
    }

    @PreDestroy
    private void destroy() {
        running = false;
        seckillOrderExecutor.shutdownNow();
        log.error("秒杀订单消费者已停止");
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 用户维度限流：10 秒内最多 5 次
        if (!rateLimitService.tryAcquire(SECKILL_LIMIT_KEY + userId, 5, 10_000L)) {
            return Result.fail("手速太快了，请稍后再试");
        }
        long orderId = redisIdWorker.nextId("order");
        Long code = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        if (code == null) {
            return Result.fail("下单失败，请稍后重试");
        }
        int result = code.intValue();
        if (result != 0) {
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }
        return Result.ok(orderId);
    }

    /**
     * 真正落库的方法：@DistributedLock 保证同一用户串行，@Transactional 保证订单与库存同事务。
     */
    @Override
    @Transactional
    @DistributedLock(key = "'lock:order:' + #voucherOrder.userId")
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 一人一单：Lua 是第一道防线，这里是数据库层的兜底
        // 注意：已取消（超时关单）的订单不能算重复下单，否则取消后用户永远下不了单
        long count = query().eq("user_id", userId).eq("voucher_id", voucherId)
                .ne("status", ORDER_STATUS_CANCELLED).count();
        if (count > 0) {
            log.error("不允许重复下单, userId=" + userId + ", voucherId=" + voucherId);
            seckillResultService.complete(voucherOrder.getId(), false, "不能重复下单");
            return;
        }
        // 扣减库存（带 stock > 0 条件，防止超卖）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足, voucherId=" + voucherId);
            seckillResultService.complete(voucherOrder.getId(), false, "库存不足");
            return;
        }
                save(voucherOrder);
        // 事务提交后再投递延时消息，避免「事务回滚但延时消息已发出」的不一致
        final Long orderId = voucherOrder.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orderDelayQueue.add(orderId, System.currentTimeMillis() + orderTimeoutMs);
            }
        });
        seckillResultService.complete(orderId, true, "下单成功");
    }
    @Override
    public Result payOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            return Result.fail("只能支付自己的订单");
        }
        if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_PAID) {
            return Result.fail("订单已支付");
        }
        boolean updated = update()
                .set("status", ORDER_STATUS_PAID)
                .set("pay_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", ORDER_STATUS_UNPAID)
                .update();
        if (!updated) {
            return Result.fail("订单状态已变更，无法支付");
        }
        // 支付成功后从延时队列摘掉，避免到期被误取消
        orderDelayQueue.remove(orderId);
        return Result.ok();
    }

    /**
     * 定时任务调用：取出到期仍未支付的订单，取消并回滚库存。
     */
    @Override
    public int cancelTimeoutOrders() {
        List<String> expiredOrderIds = orderDelayQueue.takeExpired(50);
        if (expiredOrderIds == null || expiredOrderIds.isEmpty()) {
            return 0;
        }
        int cancelled = 0;
        for (String idText : expiredOrderIds) {
            Long orderId;
            try {
                orderId = Long.valueOf(idText);
            } catch (NumberFormatException e) {
                continue;
            }
            VoucherOrder order = getById(orderId);
            if (order == null || order.getStatus() == null || order.getStatus() != ORDER_STATUS_UNPAID) {
                continue;
            }
            // 「状态仍是未支付」作为乐观条件：支付与取消并发时只有一个能生效
            boolean updated = update()
                    .set("status", ORDER_STATUS_CANCELLED)
                    .eq("id", orderId)
                    .eq("status", ORDER_STATUS_UNPAID)
                    .update();
            if (!updated) {
                continue;
            }
            // 回滚库存：数据库 + Redis；并把用户从「一人一单」集合移除，允许重新下单
            seckillVoucherService.update()
                    .setSql("stock = stock + 1")
                    .eq("voucher_id", order.getVoucherId())
                    .update();
            stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + order.getVoucherId());
            stringRedisTemplate.opsForSet().remove(
                    RedisConstants.SECKILL_ORDER_KEY + order.getVoucherId(),
                    order.getUserId().toString());
            cancelled++;
            log.error("订单超时未支付，已取消并回滚库存, orderId=" + orderId);
        }
        return cancelled;
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_QUEUE, ReadOffset.lastConsumed())
                    );
                    if (records == null || records.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> record = records.get(0);
                    handleRecord(record);
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_QUEUE, STREAM_GROUP, record.getId());
                } catch (Exception e) {
                    if (!running) {
                        break;
                    }
                    log.error("处理秒杀订单异常，转去处理 pending 列表", e);
                    handlePendingList();
                }
            }
        }
    }

    /**
     * pending 列表兜底：消费过程中异常退出的消息会留在 pending 里，这里重新消费。
     */
    private void handlePendingList() {
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM_QUEUE, ReadOffset.from("0"))
                );
                if (records == null || records.isEmpty()) {
                    return;
                }
                MapRecord<String, Object, Object> record = records.get(0);
                handleRecord(record);
                stringRedisTemplate.opsForStream().acknowledge(STREAM_QUEUE, STREAM_GROUP, record.getId());
            } catch (Exception e) {
                if (!running) {
                    return;
                }
                log.error("处理 pending 订单异常", e);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
    private void handleRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
        handleVoucherOrder(voucherOrder);
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        try {
            // 通过代理调用，@DistributedLock 与 @Transactional 才会生效
            applicationContext.getBean(IVoucherOrderService.class).createVoucherOrder(voucherOrder);
        } catch (LockFailedException e) {
            log.error("获取分布式锁失败, orderId=" + voucherOrder.getId());
            seckillResultService.complete(voucherOrder.getId(), false, "请勿重复提交");
        }
    }
}