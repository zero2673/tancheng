package com.tancheng.utils;

public interface ILock {
boolean tryLock(long timeoutSec);
void unLock();





}
