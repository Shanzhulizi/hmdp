package com.hmdp.utils;

public interface ILock {
    //分布式锁
    boolean tryLock( long timeoutSec);


    void unlock();
}
