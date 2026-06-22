package com.demo.jvm.cpu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 模拟 CPU 飙升场景
 * 原因：无限循环 / 正则回溯 / 频繁 GC
 */
@Slf4j
@Service
public class CpuSimulator {

    private volatile boolean running = false;
    private ExecutorService executor;

    // -------------------------------------------------------
    // 场景1：死循环导致 CPU 100%
    // -------------------------------------------------------
    public void triggerCpuBusy(int threadCount) {
        if (running) {
            return;
        }
        running = true;
        executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                log.info("[CPU飙升] 线程 {} 开始死循环", idx);
                // 死循环：持续占用 CPU
                while (running) {
                    // 什么都不做，纯空转，迅速拉满单核 CPU
                }
            });
        }
        log.info("[CPU飙升] 已启动 {} 个死循环线程，使用 Arthas thread -n 3 定位高 CPU 线程", threadCount);
    }

    public void  stopCpuBusy() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
        }
        log.info("[CPU飙升] 死循环线程已停止");
    }

    // -------------------------------------------------------
    // 场景2：正则表达式回溯导致 CPU 飙升（ReDoS）
    // 单线程即可，模拟一次恶意输入导致正则引擎爆炸
    // -------------------------------------------------------
    public long triggerRegexReDoS(String input) {
        long start = System.currentTimeMillis();
        // 危险正则：嵌套量词，输入特定字符串触发指数级回溯
        String dangerousRegex = "^(((a|aa)+)+)+$";
        log.info("[ReDoS] 开始匹配，input.length={}", input.length());
        boolean result = input.matches(dangerousRegex);
        long cost = System.currentTimeMillis() - start;
        log.info("[ReDoS] 匹配完成，result={}, cost={}ms", result, cost);
        return cost;
    }
}
