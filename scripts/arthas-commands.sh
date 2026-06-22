#!/bin/bash
# =============================================================
# Arthas 诊断命令速查手册
# 对应 jvm-arthas-demo 的四类异常场景
# =============================================================

# ---------- 0. 启动 Arthas ----------
# 下载
curl -O https://arthas.aliyun.com/arthas-boot.jar
# 启动（自动列出 Java 进程，输入编号 attach）
java -jar arthas-boot.jar
# 或直接指定 PID
java -jar arthas-boot.jar <PID>


# =============================================================
# 场景一：CPU 飙升排查
# 触发：POST /jvm/cpu/start?threads=2
# =============================================================

# Step1：找出 CPU 最高的前3个线程（直接定位，面试必背）
thread -n 3

# 输出示例：
# "cpu-busy-thread-0" Id=23 cpuUsage=48%
#   at com.demo.jvm.cpu.CpuSimulator.lambda$triggerCpuBusy$0(CpuSimulator.java:30)
# 直接告诉你是哪个类哪一行！

# Step2：查看某个线程的完整栈（tid 从 thread -n 结果中拿）
thread <tid>

# Step3：查看所有线程状态概览
thread

# Step4：找死锁（专用命令，秒出结果）
thread -b


# =============================================================
# 场景二：内存泄漏 / OOM 排查
# 触发：POST /jvm/leak/static（反复调用）
# =============================================================

# Step1：查看 JVM 内存分布
dashboard
# 顶部实时刷新，可以看 heap used/total、GC 次数和时间

# Step2：查看堆内存对象统计（按实例数排序）
heap -t 5
# 或者
memory

# Step3：统计某个类的实例数量（重点！排查静态缓存泄漏）
# 找出哪些类占用实例最多
ognl "@com.demo.jvm.leak.MemoryLeakSimulator@staticCache.size()"

# Step4：直接执行 OGNL 表达式查看静态字段值
ognl "@com.demo.jvm.leak.MemoryLeakSimulator@staticCache.keySet()"

# Step5：强制触发 GC 后再观察（看是否下降，判断是否真泄漏）
ognl "@java.lang.Runtime@getRuntime().gc()"

# Step6：查看类加载情况（排查 Metaspace 泄漏）
classloader
# 按 ClassLoader 统计已加载类数量，数量异常多说明有动态类生成泄漏


# =============================================================
# 场景三：接口响应慢 / 方法耗时排查
# 不需要修改代码，生产可直接用
# =============================================================

# Step1：监听方法执行，统计耗时（最常用！）
# 格式：trace 类名 方法名
trace com.demo.jvm.controller.JvmDemoController redos

# 输出：方法调用链 + 每一步耗时，直接看出哪里慢

# Step2：只看方法入参/出参/耗时（不展开调用链）
watch com.demo.jvm.cpu.CpuSimulator triggerRegexReDoS "{params,returnObj,throwExp}" -x 2
# params: 入参  returnObj: 返回值  throwExp: 异常
# -x 2: 展开深度

# Step3：统计方法调用次数和平均耗时（压测时用）
monitor com.demo.jvm.controller.JvmDemoController redos -c 5
# -c 5: 每5秒统计一次，输出 total/success/fail/avg/max

# Step4：方法调用时打印完整入参（排查参数问题）
watch com.demo.jvm.controller.JvmDemoController redos "params[0]"


# =============================================================
# 场景四：死锁排查
# 触发：POST /jvm/deadlock
# =============================================================

# 一条命令搞定，直接输出死锁线程和持锁信息
thread -b

# 输出示例：
# "deadlock-thread-1" Id=25 BLOCKED
#   waiting to lock <0x...> (com.demo.jvm.deadlock.DeadlockSimulator$$Lambda...)
#   which is held by "deadlock-thread-2"
# "deadlock-thread-2" Id=26 BLOCKED
#   waiting to lock <0x...>
#   which is held by "deadlock-thread-1"


# =============================================================
# 通用诊断命令
# =============================================================

# 查看 JVM 整体状态（内存/线程/GC/类加载，首选入口）
dashboard

# 查看 JVM 参数
jvm
# 可以看到 -Xmx -Xms MetaspaceSize 等所有参数

# 查看某个类的加载来源（排查 jar 包冲突）
sc -d com.demo.jvm.oom.OomSimulator

# 查看类的所有方法
sm com.demo.jvm.oom.OomSimulator

# 反编译已加载的类（不需要源码！）
jad com.demo.jvm.oom.OomSimulator

# 热替换类（不重启修复 bug！生产慎用）
# 1. 先修改源码编译得到 .class 文件
# 2. retransform /path/to/OomSimulator.class

# 查看 logger 级别 / 动态修改日志级别（不重启！）
logger
logger --name root --level debug


# =============================================================
# OGNL 表达式常用片段（动态执行 Java 代码）
# =============================================================

# 调用静态方法
ognl "@java.lang.System@currentTimeMillis()"

# 查看 Spring Bean
ognl "@org.springframework.boot.SpringApplication@getApplicationContext().getBean('orderService')"

# 查看 Map 大小
ognl "@com.demo.jvm.leak.MemoryLeakSimulator@staticCache.size()"

# 强制 GC
ognl "@java.lang.Runtime@getRuntime().gc()"

# 查看系统属性
ognl "@java.lang.System@getProperty('java.version')"


# =============================================================
# 退出 Arthas
# =============================================================
stop
