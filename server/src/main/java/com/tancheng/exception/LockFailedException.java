package com.tancheng.exception;

/**
 * 获取分布式锁失败时抛出。
 */
public class LockFailedException extends RuntimeException {

    public LockFailedException(String message) {
        super(message);
    }
}