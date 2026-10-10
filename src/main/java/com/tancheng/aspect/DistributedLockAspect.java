package com.tancheng.aspect;

import com.tancheng.annotation.DistributedLock;
import com.tancheng.exception.LockFailedException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @DistributedLock 的切面实现。
 *
 * 注意顺序：切面要排在 @Transactional 外层（Spring 事务默认是 LOWEST_PRECEDENCE），
 * 保证「先加锁 → 开事务 → 提交 → 再解锁」。如果锁在事务提交前就释放，
 * 其他线程可能读到还没提交的数据，出现并发下的空窗。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DistributedLockAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final RedissonClient redissonClient;

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = resolveKey(joinPoint, distributedLock.key());
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = distributedLock.waitTime() > 0
                ? lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.unit())
                : lock.tryLock();
        if (!acquired) {
            log.warn("获取分布式锁失败, key={}", lockKey);
            throw new LockFailedException("操作过于频繁，请稍后再试");
        }
        log.debug("获取分布式锁成功, key={}", lockKey);
        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, String expression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        EvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = NAME_DISCOVERER.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return PARSER.parseExpression(expression).getValue(context, String.class);
    }
}