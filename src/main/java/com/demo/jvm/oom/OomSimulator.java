package com.demo.jvm.oom;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟三种 OOM 场景
 */
@Service
public class OomSimulator {

    // -------------------------------------------------------
    // 场景1：堆 OOM（最常见）
    // 原因：对象一直被 List 引用无法 GC，堆空间耗尽
    // 现象：java.lang.OutOfMemoryError: Java heap space
    // -------------------------------------------------------
    private final List<byte[]> heapHolder = new ArrayList<>();

    public void triggerHeapOom() {
        // 每次调用分配 10MB，反复调用直到 OOM
        while (true) {
            heapHolder.add(new byte[10 * 1024 * 1024]); // 10MB
        }
    }

    // -------------------------------------------------------
    // 场景2：Metaspace OOM
    // 原因：动态生成大量 Class（常见于 CGLib、动态代理、热部署）
    // 现象：java.lang.OutOfMemoryError: Metaspace
    // 需要启动参数：-XX:MaxMetaspaceSize=32m
    // -------------------------------------------------------
    public void triggerMetaspaceOom() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        // 使用 Java Compiler API 动态编译并加载类，撑爆 Metaspace
        int i = 0;
        while (true) {
            // 使用 Arthas 内置的 ClassGenerator 或自定义 ClassLoader 批量加载
            // 这里用简单的自定义 ClassLoader 模拟
            CustomClassLoader loader = new CustomClassLoader();
            Class<?> clazz = loader.defineClass("DynamicClass" + i++);
            classes.add(clazz);
        }
    }

    // -------------------------------------------------------
    // 场景3：栈溢出（StackOverflowError）
    // 原因：无限递归，每次方法调用都在栈上分配栈帧
    // 现象：java.lang.StackOverflowError
    // 注意：这是 Error 不是 OOM，但面试经常一起问
    // -------------------------------------------------------
    public void triggerStackOverflow() {
        triggerStackOverflow(); // 无限递归
    }

    // 内部类：自定义 ClassLoader 用于 Metaspace OOM 模拟
    static class CustomClassLoader extends ClassLoader {
        public Class<?> defineClass(String name) {
            // 构造一个最简 class 字节码（magic + 版本 + 常量池 + 访问标志 + 类/父类 + 接口）
            byte[] b = generateMinimalClassBytes(name);
            return defineClass(name, b, 0, b.length);
        }

        private byte[] generateMinimalClassBytes(String name) {
            // ASM 生成最小可用 class 字节码
            // 此处用预编译的模板字节码做演示
            return new byte[]{
                (byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE, // magic
                0x00, 0x00, 0x00, 0x34,                          // version 52 (Java 8)
                0x00, 0x07,                                       // constant_pool_count = 7
                0x07, 0x00, 0x02,                                // #1 Class -> #2
                0x01, 0x00, (byte)name.length()                  // #2 Utf8 (简化，实际需完整实现)
            };
        }
    }
}
