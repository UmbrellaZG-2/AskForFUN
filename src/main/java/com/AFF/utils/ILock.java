package com.AFF.utils;

public interface ILock {
    /**
     * c尝试获取锁
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);
    /**
    *释放锁
     */
    void unlock();

}
