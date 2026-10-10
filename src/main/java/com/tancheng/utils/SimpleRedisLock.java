package com.tancheng.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private String key;
    private static  final String lockName = "lock:";
    private StringRedisTemplate stringRedisTemplate;
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
static {
    UNLOCK_SCRIPT= new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    UNLOCK_SCRIPT.setResultType(Long.class);

}
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标示
       String threadId = ID_PREFIX+Thread.currentThread().getId();
        //获取锁

       Boolean success=stringRedisTemplate.opsForValue().
        setIfAbsent(lockName+name,threadId,timeoutSec, TimeUnit.SECONDS);


        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock(){
    //执行lua脚本
stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockName+name),
        ID_PREFIX+Thread.currentThread().getId());
    }
}
