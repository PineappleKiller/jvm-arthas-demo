package com.demo.jvm.deadlock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 模拟死锁
 * 原因：两个线程互相等待对方持有的锁
 * 现象：线程永久阻塞，业务接口 hang 住不返回
 */
@Slf4j
@Service
public class DeadlockSimulator {

    private final Object lockA = new Object();
    private final Object lockB = new Object();

    public void triggerDeadlock() {
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                log.info("[死锁] 线程1 持有 lockA，等待 lockB...");
                sleep(500);
                synchronized (lockB) {
                    log.info("[死锁] 线程1 获得 lockB");
                }
            }
        }, "deadlock-thread-1");

        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                log.info("[死锁] 线程2 持有 lockB，等待 lockA...");
                sleep(500);
                synchronized (lockA) {
                    log.info("[死锁] 线程2 获得 lockA");
                }
            }
        }, "deadlock-thread-2");

        t1.start();
        t2.start();

        log.info("[死锁] 两个线程已启动，使用 Arthas thread -b 可以直接定位死锁");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
