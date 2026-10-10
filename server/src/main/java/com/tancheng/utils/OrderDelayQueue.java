package com.tancheng.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 订单超时延时队列（Redis ZSet 实现）。
 *
 * score = 订单到期时间戳（毫秒），member = orderId。
 * 取单用 Lua 保证「查到期 + 删除」原子完成，多实例部署时同一个订单不会被两个实例同时取到。
 * 相比定时扫表：不需要全表扫描，也不需要额外的调度中间件。
 */
@Slf4j
@Component
public class OrderDelayQueue {

    private static final DefaultRedisScript<List> TAKE_EXPIRED_SCRIPT;

    static {
        TAKE_EXPIRED_SCRIPT = new DefaultRedisScript<>();
        TAKE_EXPIRED_SCRIPT.setLocation(new ClassPathResource("order_timeout.lua"));
        TAKE_EXPIRED_SCRIPT.setResultType(List.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void add(Long orderId, long expireAtMillis) {
        stringRedisTemplate.opsForZSet().add(RedisConstants.ORDER_DELAY_KEY, orderId.toString(), expireAtMillis);
    }

    public void remove(Long orderId) {
        stringRedisTemplate.opsForZSet().remove(RedisConstants.ORDER_DELAY_KEY, orderId.toString());
    }

    @SuppressWarnings("unchecked")
    public List<String> takeExpired(int batchSize) {
        return (List<String>) stringRedisTemplate.execute(
                TAKE_EXPIRED_SCRIPT,
                Collections.singletonList(RedisConstants.ORDER_DELAY_KEY),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(batchSize)
        );
    }
}