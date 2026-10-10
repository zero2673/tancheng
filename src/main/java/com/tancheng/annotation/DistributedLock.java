package com.tancheng.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解，基于 Redisson 实现，key 支持 SpEL 表达式。
 *
 * 示例：
 *   @DistributedLock(key = "'lock:order:' + #voucherOrder.userId")
 *   @DistributedLock(key = "'lock:shop:' + #id", waitTime = 500)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** 锁 key，支持 SpEL，例如 "'lock:order:' + #userId" */
    String key();

    /** 获取锁的最长等待时间，默认 0 表示不等待、立即失败 */
    long waitTime() default 0L;

    /** 持有时长，-1 表示交给 Redisson 看门狗自动续期 */
    long leaseTime() default -1L;

    TimeUnit unit() default TimeUnit.MILLISECONDS;
}