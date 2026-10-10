package com.tancheng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.tancheng.dto.Result;
import com.tancheng.entity.VoucherOrder;
import com.tancheng.mapper.VoucherOrderMapper;
import com.tancheng.service.ISeckillVoucherService;
import com.tancheng.service.IVoucherOrderService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tancheng.utils.RedisIdWorker;
import com.tancheng.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /*    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);*/
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    String queuename = "stream.orders";
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queuename, ReadOffset.lastConsumed())
                    );
                    //判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，说明没有消息，继续下一个循环
                        continue;
                    }
//解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //获取成功，获取订单信息可以下单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
stringRedisTemplate.opsForStream().acknowledge(queuename, "g1",record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }


            }
        }
    }

    private void handlePendingList() {
        while (true) {
            try {
                //获取pending-list中的订单信息
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queuename, ReadOffset.from("0"))
                );
                //判断消息是否获取成功
                if (list == null || list.isEmpty()) {
                    //如果获取失败，说明pending-list没有异常消息，结束循环
                   break;
                }
//解析消息中的订单信息
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> value = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                //获取成功，获取订单信息可以下单
                handleVoucherOrder(voucherOrder);
                //ACK确认
                stringRedisTemplate.opsForStream().acknowledge(queuename, "g1",record.getId());

            } catch (Exception e) {
                log.error("处理订单异常", e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }


        }


    }


/*
private class VoucherOrderHandler implements Runnable{
    @Override
    public void run() {
while (true){
    //获取队列中的订单信息
    try {
        VoucherOrder voucherOrder = orderTasks.take();
        //创建订单
handleVoucherOrder(voucherOrder);


    } catch (Exception e) {
       log.error("处理订单异常",e);
    }


}
    }
}
*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取成功
        if (!isLock) {
            //获取失败，返回错误
            log.error("不允许重复下单");
            return;

        }
        try {

            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }


    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //订单id
        long orderId = redisIdWorker.nextId("order");
//1.执行lua脚本
        Long execute = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        //2.判断结果是否为0
        int i = execute.intValue();
        if (i != 0) {
            //2.1不为0，没有购买资格
            return Result.fail(i == 1 ? "库存不足" : "不能重复下单");
        }

        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回 订单id
        return Result.ok(orderId);

    }

    /*    @Override
        public Result seckillVoucher(Long voucherId) {
            //获取用户id
            Long userId = UserHolder.getUser().getId();

    //1.执行lua脚本
            Long execute = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(), userId.toString()
            );

            //2.判断结果是否为0
            int i = execute.intValue();
            if(i!=0){
                //2.1不为0，没有购买资格
                return Result.fail(i==1?"库存不足":"不能重复下单");
            }
            //2.2为0，有购买资格，把下单信息保存到阻塞队列
            //2.3.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            //用户id
            voucherOrder.setUserId(userId);
            //优惠券id
            voucherOrder.setVoucherId(voucherId);
       //放入阻塞队列
            orderTasks.add(voucherOrder);
            //获取代理对象
            proxy = (IVoucherOrderService) AopContext.currentProxy();
            //3.返回 订单id
    return Result.ok(orderId);

        }*/
/*    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucherServiceById = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucherServiceById.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }

        //3.判断秒杀是否结束
        if (voucherServiceById.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        if (voucherServiceById.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        //获取锁
        boolean isLock = lock.tryLock(10);
        //判断是否获取成功
        if(!isLock){
            //获取失败，返回错误
            return Result.fail("不允许重复下单");

        }
try {
    //获取代理对象
    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    return proxy.createVoucherOrder(voucherId);
}finally {
    //释放锁
    lock.unLock();
}
    }*/
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单
        Long userId = voucherOrder.getUserId();
        //5.1查询订单

        int count = Math.toIntExact(query().eq("user_id", userId).eq("voucher_id", voucherOrder).count());
        //5.2判断是否存在
        if (count > 0) {
            log.error("不允许重复下单");
            return;
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder).gt("stock", 0).update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        //7.创建订单

        save(voucherOrder);


    }
}
