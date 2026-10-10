package com.tancheng.service;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 基于 Redis ZSet + Lua 的滑动窗口限流。
 *
 * 为什么不用固定窗口（INCR + EXPIRE）：固定窗口在边界处会放过约两倍流量
 * （第 59 秒刷满 100 次、第 61 秒再刷 100 次），滑动窗口按"最近 N 毫秒"统计，没有这个问题。
 * 代价是每个请求在 ZSet 里占一个成员，内存开销比计数器高，所以窗口和粒度要按业务量级选。
 */
@Slf4j
@Service
public class RateLimitService {

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("rate_limit.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * @param key      限流维度，例如 limit:code:{phone}、limit:seckill:{userId}
     * @param limit    窗口内允许的最大请求数
     * @param windowMs 窗口大小（毫秒）
     * @return true 放行；false 被限流
     */
    public boolean tryAcquire(String key, int limit, long windowMs) {
        Long result = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(windowMs),
                String.valueOf(limit),
                UUID.randomUUID().toString(true)
        );
        return result != null && result == 1L;
    }
}