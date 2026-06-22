# JVM 线上问题定位 SOP

## 项目结构

```
jvm-arthas-demo/
├── pom.xml
├── scripts/
│   └── arthas-commands.sh          ← Arthas 全命令速查
└── src/main/java/com/demo/jvm/
    ├── JvmArthasDemoApplication.java
    ├── controller/JvmDemoController.java   ← 触发入口（HTTP 接口）
    ├── oom/OomSimulator.java               ← 堆OOM / Metaspace OOM / 栈溢出
    ├── deadlock/DeadlockSimulator.java     ← 死锁
    ├── cpu/CpuSimulator.java               ← CPU飙升 / ReDoS
    └── leak/MemoryLeakSimulator.java       ← 静态缓存泄漏 / ThreadLocal泄漏
```

---

## 推荐启动参数

```bash
java -Xms128m -Xmx256m \
     -XX:MetaspaceSize=64m -XX:MaxMetaspaceSize=128m \
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./dump.hprof \
     -jar jvm-arthas-demo-1.0.0.jar
```

---

## 四类异常的定位 SOP

### 1. CPU 飙升

```
触发  →  POST /jvm/cpu/start?threads=2
定位  →  thread -n 3          # 直接看到高CPU线程的代码位置
深挖  →  thread <tid>         # 看完整调用栈
```


---

### 2. 死锁

```
触发  →  POST /jvm/deadlock
定位  →  thread -b            # 一条命令，直接输出死锁线程和持锁关系
```


---

### 3. 内存泄漏（缓慢 OOM）

```
触发  →  反复调用 POST /jvm/leak/static
定位  →  dashboard            # 看 heap used 是否持续增长
       →  ognl "@com.demo.jvm.leak.MemoryLeakSimulator@staticCache.size()"
       →  ognl "@java.lang.Runtime@getRuntime().gc()"  # GC后看是否下降
深挖  →  classloader          # 排查 Metaspace 泄漏
```


---

### 4. 接口耗时/方法慢

```
触发  →  POST /jvm/cpu/redos?input=aaaaaaaaaaaaaaaaaaaab
定位  →  trace com.demo.jvm.controller.JvmDemoController redos
定位  →  watch 类名 方法名 "{params,returnObj,throwExp}" -x 2
统计  →  monitor 类名 方法名 -c 5
```


---

## Arthas 核心命令一览表

| 命令 | 用途 | 对应场景 |
|------|------|---------|
| `dashboard` | JVM 整体状态（内存/线程/GC） | 所有场景首选入口 |
| `thread -n 3` | CPU 最高的 N 个线程 + 代码位置 | CPU 飙升 |
| `thread -b` | 检测并输出死锁信息 | 死锁 |
| `thread <tid>` | 某线程完整调用栈 | CPU / 死锁 |
| `trace 类 方法` | 方法调用链 + 每步耗时 | 接口慢 |
| `watch 类 方法 表达式` | 方法入参/返回值/异常 | 接口慢 / 参数排查 |
| `monitor 类 方法 -c N` | N秒内调用次数/成功率/平均耗时 | 压测监控 |
| `ognl "表达式"` | 动态执行 Java 代码/查静态字段 | 内存泄漏 / 万能 |
| `jad 类名` | 反编译已加载类（不需要源码） | 排查热修复/版本问题 |
| `classloader` | ClassLoader 统计 | Metaspace 泄漏 |
| `jvm` | 查看所有 JVM 启动参数 | 参数核查 |
| `logger` | 查看/动态修改日志级别 | 开启 debug 日志 |
| `sc -d 类名` | 查看类的加载来源 | jar 包冲突 |

