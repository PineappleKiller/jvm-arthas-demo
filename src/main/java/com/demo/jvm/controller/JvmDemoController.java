package com.demo.jvm.controller;

import com.demo.jvm.cpu.CpuSimulator;
import com.demo.jvm.deadlock.DeadlockSimulator;
import com.demo.jvm.leak.MemoryLeakSimulator;
import com.demo.jvm.oom.OomSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/jvm")
@RequiredArgsConstructor
public class JvmDemoController {

    private final OomSimulator oomSimulator;
    private final DeadlockSimulator deadlockSimulator;
    private final CpuSimulator cpuSimulator;
    private final MemoryLeakSimulator leakSimulator;

    // ===== OOM =====
    /** 触发堆 OOM：POST /jvm/oom/heap */
    @PostMapping("/oom/heap")
    public String heapOom() {
        oomSimulator.triggerHeapOom();
        return "not reached";
    }

    /** 触发栈溢出：POST /jvm/oom/stack */
    @PostMapping("/oom/stack")
    public String stackOverflow() {
        try {
            oomSimulator.triggerStackOverflow();
        } catch (StackOverflowError e) {
            return "StackOverflowError 已捕获: " + e.getClass().getName();
        }
        return "not reached";
    }

    // ===== 死锁 =====
    /** 触发死锁：POST /jvm/deadlock，调用后使用 Arthas thread -b 定位 */
    @PostMapping("/deadlock")
    public String deadlock() {
        deadlockSimulator.triggerDeadlock();
        return "死锁线程已启动，请用 Arthas: thread -b";
    }

    // ===== CPU 飙升 =====
    /** 启动 CPU 死循环：POST /jvm/cpu/start?threads=2 */
    @PostMapping("/cpu/start")
    public String startCpu(@RequestParam(defaultValue = "2") int threads) {
        cpuSimulator.triggerCpuBusy(threads);
        return "已启动 " + threads + " 个 CPU 飙升线程，请用 Arthas: thread -n 3";
    }

    /** 停止 CPU 死循环：POST /jvm/cpu/stop */
    @PostMapping("/cpu/stop")
    public String stopCpu() {
        cpuSimulator.stopCpuBusy();
        return "CPU 死循环线程已停止";
    }

    /**
     * 触发 ReDoS：POST /jvm/cpu/redos
     * 默认 input = "aaaaaaaaaaaaaaaaaaaab"（20个a + b，触发回溯爆炸）
     */
    @PostMapping("/cpu/redos")
    public Map<String, Object> redos(@RequestParam(defaultValue = "aaaaaaaaaaaaaaaaaaaab") String input) {
        long cost = cpuSimulator.triggerRegexReDoS(input);
        return Map.of("input", input, "costMs", cost);
    }

    // ===== 内存泄漏 =====
    /** 静态缓存泄漏：POST /jvm/leak/static（反复调用，内存缓慢增长） */
    @PostMapping("/leak/static")
    public Map<String, Object> staticLeak() {
        String sessionId = UUID.randomUUID().toString();
        leakSimulator.triggerStaticLeak(sessionId);
        return Map.of("sessionId", sessionId, "cacheSize", leakSimulator.getStaticCacheSize());
    }

    /** ThreadLocal 泄漏：POST /jvm/leak/threadlocal */
    @PostMapping("/leak/threadlocal")
    public String threadLocalLeak() {
        leakSimulator.triggerThreadLocalLeak();
        return "ThreadLocal 已设置但未 remove，线程名=" + Thread.currentThread().getName();
    }
}
