# JVM 生产问题全景清单

---

## 一、CPU 飙升

**业务表现**
- 接口响应变慢，但不是完全 hang 死
- 监控 CPU 使用率持续 > 80%，甚至打满
- 其他正常接口也受到波及（资源被抢占）

**常见根因**

| 根因 | 特征 |
|------|------|
| 死循环 | CPU 立刻打满，特定线程持续 RUNNABLE |
| 频繁 Full GC | CPU 高 + GC 日志显示 STW 时间长，堆使用率接近上限 |
| 正则回溯 ReDoS | 特定请求进来后单线程 CPU 飙高，其余正常 |
| 序列化/反序列化 | 大对象 JSON 解析，CPU 持续高位 |

**排查步骤**
```
1. dashboard              # 看 CPU% 和线程列表
2. thread -n 3            # 找 CPU 最高的线程，直接显示代码行
3. thread <tid>           # 看完整调用栈确认根因
4. trace 类名 方法名       # 如果是某个接口慢，看调用链耗时
```

---

## 二、内存 OOM（堆）

**业务表现**
- 接口突然大量报 500
- 日志出现 `java.lang.OutOfMemoryError: Java heap space`
- 监控 heap used 长期贴近 Xmx，Full GC 频繁但回收效果越来越差
- 严重时进程直接崩溃

**常见根因**

| 根因 | 特征 |
|------|------|
| 静态集合无限增长 | heap 缓慢上涨，GC 后不降 |
| 大对象一次性加载 | heap 突刺，单次请求触发 |
| 缓存未设 TTL/上限 | 随时间线性增长 |
| 内存泄漏（见下节）| 长期运行后才爆 |

**排查步骤**
```
1. dashboard              # 看 heap used 趋势和 GC 频率
2. ognl "@java.lang.Runtime@getRuntime().gc()"   # 手动触发GC
3. ognl "表达式"           # 查静态集合 size，确认是否泄漏
4. jmap -histo:live <pid> # 按对象数量排序，找异常大的类
5. jmap -dump:format=b,file=dump.hprof <pid>  # 导出 heap dump
6. MAT 打开 dump          # 看 Retained Heap，找 GC Root 引用链
```

**如果已配置启动参数 `-XX:+HeapDumpOnOutOfMemoryError`，OOM 时会自动生成 dump.hprof，直接用 MAT 分析。**

---

## 三、内存泄漏（慢性 OOM）

**业务表现**
- 应用刚重启一切正常，运行几天后接口开始变慢
- 监控内存使用率缓慢爬升，从不下降
- Full GC 越来越频繁，每次回收量越来越少
- 最终触发 OOM，重启后恢复，然后循环

**常见根因**

| 根因 | 说明 |
|------|------|
| 静态 Map/List 持续堆积 | 放进去不移除，GC Root 可达 |
| ThreadLocal 未 remove | 线程池复用线程，旧值残留 |
| 监听器/回调未注销 | 注册了 EventListener 但没反注册 |
| 连接/流未关闭 | InputStream、Connection 对象堆积 |
| 内部类持有外部类引用 | 匿名类/Lambda 隐式持有 |

**排查步骤**
```
1. 监控 heap 趋势（是否只涨不跌）
2. ognl 查疑似泄漏的静态集合大小
3. GC 后再查，大小不降 → 确认泄漏
4. jmap -histo:live 对比两次结果，找持续增长的类
5. MAT 分析 dump → Leak Suspects Report 自动给出嫌疑
```

---

## 四、Metaspace OOM

**业务表现**
- 日志出现 `java.lang.OutOfMemoryError: Metaspace`
- 通常发生在使用动态代理/CGLib/Groovy 脚本/热部署的场景
- 现象与堆 OOM 类似：服务崩溃或响应异常

**常见根因**

| 根因 | 说明 |
|------|------|
| 动态生成类过多 | CGLib 代理、Groovy 脚本、字节码增强 |
| 自定义 ClassLoader 未释放 | ClassLoader 不能被 GC，其加载的所有类也不能回收 |
| 热部署/OSGi | 旧版本类未卸载，新版本又加载进来 |

**排查步骤**
```
1. classloader            # 看各 ClassLoader 加载类数量，找异常多的
2. jvm                    # 确认 MaxMetaspaceSize 配置
3. sc -d 类名             # 看某个类是由哪个 ClassLoader 加载的
4. jmap -clstats <pid>    # 详细 ClassLoader 统计
```

---

## 五、死锁

**业务表现**
- 特定接口调用后一直 loading，不报错不返回（区别于超时报错）
- CPU 使用率低（线程全是 BLOCKED，不消耗 CPU）
- 线程池活跃线程数打满，但无任何处理进展
- 重启后恢复，下次触发同样场景再次复现

**常见根因**

| 根因 | 说明 |
|------|------|
| 锁获取顺序不一致 | 线程A先锁X再锁Y，线程B先锁Y再锁X |
| 数据库行锁死锁 | 事务A和事务B互相等待对方持有的行锁 |
| 分布式锁未释放 | Redis/ZK 锁超时但业务未正确处理 |

**排查步骤**
```
1. thread -b              # 一条命令，直接输出死锁线程和持锁关系
2. thread <tid>           # 查看具体线程调用栈，找到业务代码位置
3. jstack <pid> | grep -A 30 deadlock   # 不用 Arthas 时的替代方案
```

---

## 六、线程池打满 / 任务堆积

**业务表现**
- 接口返回 `RejectedExecutionException` 或自定义拒绝策略响应
- 区别于死锁：死锁是 hang 住，线程池打满是**直接报错拒绝**
- 监控：活跃线程数 = 最大线程数，队列堆积量持续增大

**常见根因**

| 根因 | 说明 |
|------|------|
| 下游响应慢 | 线程全在等待 IO，无法释放 |
| 线程池配置过小 | 峰值流量超出容量 |
| 任务执行异常未捕获 | 线程异常退出，池中线程减少 |
| 死锁（间接） | 线程全部 BLOCKED，等同于打满 |

**排查步骤**
```
1. dashboard              # 看线程数和状态分布
2. thread                 # 看 WAITING/BLOCKED 线程数量比例
3. thread <tid>           # 找 WAITING 线程在等什么（IO？锁？）
4. monitor 类名 方法名 -c 5  # 统计线程池任务执行耗时
```

---

## 七、接口响应慢（无明显异常）

**业务表现**
- 接口 P99 升高，但没有 OOM 和明显报错
- 通常是某个特定接口慢，而非全局慢
- 日志里可以看到请求耗时异常，但看不出哪一步慢

**常见根因**

| 根因 | 说明 |
|------|------|
| 慢 SQL | DB 响应慢拖慢整个链路 |
| 锁竞争 | synchronized 或数据库乐观锁重试 |
| 大对象序列化 | 返回体过大，JSON 序列化耗时 |
| 远程调用无超时 | RPC/HTTP 调用对端慢 |
| GC STW | Full GC 暂停期间所有请求都被延迟 |

**排查步骤**
```
1. trace 类名 方法名       # 展开调用链，看每一步耗时占比
2. watch 类名 方法名 "{params,returnObj}" -x 2  # 查入参是否有异常大对象
3. monitor 类名 方法名 -c 10   # 统计平均/最大耗时
4. dashboard               # 看 GC 频率，排查 STW 影响
```

---

## 八、StackOverflowError

**业务表现**
- 接口报 500，日志出现 `java.lang.StackOverflowError`
- 不是 OOM，是调用栈深度超限
- CPU 可能短暂升高，但很快随异常抛出而恢复

**常见根因**
- 无终止条件的递归调用
- 框架代理嵌套过深（Spring AOP + 事务 + 安全多层代理）
- 数据结构递归遍历（树/图的 toString、equals、hashCode）

**排查步骤**
```
1. 日志里 StackOverflowError 的堆栈，找重复出现的方法帧
2. watch 类名 方法名 "{params}" # 看入参，判断是否数据导致递归失控
3. jad 类名                # 反编译确认代理层数
```

---

## 快速判断矩阵

| 现象 | CPU | 内存 | 线程状态 | 优先怀疑 |
|------|-----|------|---------|---------|
| 接口 hang 死，CPU 低 | 低 | 正常 | BLOCKED | 死锁 |
| 接口慢，CPU 高 | 高 | 正常 | RUNNABLE | 死循环 / ReDoS |
| 接口慢，CPU 低 | 低 | 正常 | WAITING | 线程池打满 / 下游慢 |
| 全部接口慢，周期性 | 脉冲 | 高 | 混合 | Full GC STW |
| 内存只涨不跌 | 正常 | ↑↑↑ | 正常 | 内存泄漏 |
| 突然 500 + OOM | 正常 | 打满 | 正常 | 大对象 / 静态集合爆了 |
| 接口直接拒绝 | 正常 | 正常 | 大量WAITING | 线程池队列满 |
