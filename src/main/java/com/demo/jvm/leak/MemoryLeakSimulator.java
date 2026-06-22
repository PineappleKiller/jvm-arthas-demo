package com.demo.jvm.leak;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟内存泄漏
 * 特点：不是立刻 OOM，而是慢慢涨，最终触发频繁 Full GC，响应变慢
 */
@Slf4j
@Service
public class MemoryLeakSimulator {

    // -------------------------------------------------------
    // 场景1：静态集合泄漏（最典型）
    // 对象放入静态 Map 后永远不删除，GC 无法回收
    // -------------------------------------------------------
    private static final Map<String, List<byte[]>> staticCache = new ConcurrentHashMap<>();

    public void triggerStaticLeak(String sessionId) {
        List<byte[]> dataList = new ArrayList<>();
        // 每个 session 积累 1MB 数据
        for (int i = 0; i < 10; i++) {
            dataList.add(new byte[100 * 1024]); // 100KB * 10 = 1MB
        }
        staticCache.put(sessionId, dataList);
        log.info("[静态泄漏] 已添加 sessionId={}，当前缓存大小={}", sessionId, staticCache.size());
    }

    public int getStaticCacheSize() {
        return staticCache.size();
    }

    // -------------------------------------------------------
    // 场景2：ThreadLocal 泄漏
    // 线程池复用线程时，ThreadLocal 值不 remove 导致泄漏
    // -------------------------------------------------------
    private static final ThreadLocal<List<byte[]>> threadLocalCache = new ThreadLocal<>();

    public void triggerThreadLocalLeak() {
        List<byte[]> data = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            data.add(new byte[200 * 1024]); // 200KB * 5 = 1MB
        }
        threadLocalCache.set(data);
        // 模拟：没有调用 threadLocalCache.remove()，线程归还线程池后数据残留
        log.info("[ThreadLocal泄漏] 线程 {} 已设置 ThreadLocal，但未 remove", Thread.currentThread().getName());
        // 正确做法应该是：
        // try { ... } finally { threadLocalCache.remove(); }
    }
}
