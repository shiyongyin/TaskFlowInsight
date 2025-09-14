# Context-Management 故障排查手册

本手册提供Context-Management模块常见问题的诊断方法、调试技巧和解决方案。

## 1. 快速诊断流程

### 1.1 问题分类决策树

```
问题发生
    ├── 内存相关？
    │   ├── 内存泄漏 → 见 2.1
    │   ├── OOM错误 → 见 2.2
    │   └── GC频繁 → 见 2.3
    ├── 性能相关？
    │   ├── 响应慢 → 见 3.1
    │   ├── CPU高 → 见 3.2
    │   └── 吞吐量低 → 见 3.3
    ├── 功能异常？
    │   ├── 上下文丢失 → 见 4.1
    │   ├── 任务状态错误 → 见 4.2
    │   └── 并发问题 → 见 4.3
    └── 系统崩溃？
        ├── JVM崩溃 → 见 5.1
        ├── 死锁 → 见 5.2
        └── 栈溢出 → 见 5.3
```

### 1.2 紧急处理流程

1. **立即止损**
   ```bash
   # 查看系统状态
   curl http://localhost:19090/actuator/health
   
   # 导出诊断信息
   curl http://localhost:19090/actuator/taskflow/diagnostics > diagnostics.json
   
   # 触发紧急清理
   curl -X POST http://localhost:19090/actuator/taskflow/cleanup
   ```

2. **收集现场**
   ```bash
   # 线程dump
   jstack <pid> > thread_dump.txt
   
   # 堆dump
   jmap -dump:format=b,file=heap_dump.hprof <pid>
   
   # GC日志
   tail -n 1000 gc.log > gc_recent.log
   ```

3. **临时缓解**
   ```bash
   # 增加内存
   export JAVA_OPTS="-Xmx8g -Xms8g"
   
   # 重启服务
   systemctl restart taskflow-insight
   ```

## 2. 内存问题排查

### 2.1 ThreadLocal内存泄漏

**症状**：
- 内存持续增长
- `contexts_created` >> `contexts_cleaned`
- 老年代不断增长

**诊断步骤**：

1. **检查泄漏统计**
   ```java
   SafeContextManager manager = SafeContextManager.getInstance();
   System.out.println("Created: " + manager.getTotalContextsCreated());
   System.out.println("Cleaned: " + manager.getTotalContextsCleaned());
   System.out.println("Leaked: " + manager.getTotalLeaksDetected());
   System.out.println("Fixed: " + manager.getTotalLeaksFixed());
   ```

2. **分析堆dump**
   ```bash
   # 使用MAT分析
   # 查找ThreadLocalMap$Entry对象
   # 检查引用链：Thread -> threadLocals -> table -> entry -> value
   ```

3. **检查代码模式**
   ```java
   // ❌ 错误：未使用try-with-resources
   ManagedThreadContext ctx = ManagedThreadContext.create();
   // 业务逻辑
   // 忘记关闭！
   
   // ✅ 正确
   try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
       // 业务逻辑
   } // 自动清理
   ```

**解决方案**：

1. **启用泄漏检测**
   ```yaml
   taskflow:
     context-manager:
       leak-detection-interval: 30s
       enable-auto-repair: true
   ```

2. **手动清理**
   ```java
   // 强制清理当前线程
   SafeContextManager.getInstance().clearCurrentThread(true);
   
   // 触发全局泄漏修复
   ZeroLeakThreadLocalManager.getInstance().repairLeaks(true);
   ```

3. **代码修复**
   - 确保所有上下文使用try-with-resources
   - 在finally块中添加清理逻辑
   - 使用SafeContextManager.executeInContext()自动管理

### 2.2 OutOfMemoryError

**症状**：
- `java.lang.OutOfMemoryError: Java heap space`
- `java.lang.OutOfMemoryError: Metaspace`

**诊断步骤**：

1. **分析堆使用**
   ```bash
   # 查看堆使用情况
   jmap -heap <pid>
   
   # 对象统计
   jmap -histo:live <pid> | head -20
   
   # 查找大对象
   jmap -clstats <pid>
   ```

2. **检查内存配置**
   ```bash
   # 查看JVM参数
   jinfo -flags <pid>
   
   # 检查是否设置了合理的堆大小
   # -Xms4G -Xmx4G
   ```

**解决方案**：

1. **调整内存配置**
   ```bash
   JAVA_OPTS="-Xms6G -Xmx6G -XX:MaxMetaspaceSize=512M"
   ```

2. **优化对象创建**
   ```java
   // 使用对象池
   private static final ObjectPool<TaskNode> TASK_POOL = 
       new GenericObjectPool<>(new TaskNodeFactory());
   ```

3. **限制并发数**
   ```java
   // 限制活动上下文数量
   if (getActiveContextCount() > MAX_CONTEXTS) {
       throw new ResourceExhaustedException("Too many active contexts");
   }
   ```

### 2.3 频繁GC

**症状**：
- GC暂停时间长
- CPU使用率高但业务处理慢
- `gc.log`显示频繁的Full GC

**诊断步骤**：

1. **分析GC日志**
   ```bash
   # 启用GC日志
   -Xlog:gc*:file=gc.log:time,uptime,level,tags
   
   # 使用GCViewer分析
   java -jar gcviewer.jar gc.log
   ```

2. **监控GC指标**
   ```java
   List<GarbageCollectorMXBean> gcBeans = 
       ManagementFactory.getGarbageCollectorMXBeans();
   for (GarbageCollectorMXBean gcBean : gcBeans) {
       System.out.println(gcBean.getName() + ": " + 
           gcBean.getCollectionCount() + " collections, " +
           gcBean.getCollectionTime() + " ms");
   }
   ```

**解决方案**：

1. **优化GC配置**
   ```bash
   # G1GC优化
   -XX:+UseG1GC
   -XX:MaxGCPauseMillis=50
   -XX:G1HeapRegionSize=16M
   
   # ZGC（低延迟）
   -XX:+UseZGC
   ```

2. **减少对象分配**
   ```java
   // 重用StringBuilder
   private static final ThreadLocal<StringBuilder> STRING_BUILDER = 
       ThreadLocal.withInitial(() -> new StringBuilder(256));
   ```

## 3. 性能问题排查

### 3.1 响应时间慢

**症状**：
- API响应时间超过预期
- 上下文操作耗时长

**诊断步骤**：

1. **性能profiling**
   ```bash
   # 使用async-profiler
   ./profiler.sh -d 30 -e cpu -f /tmp/cpu.html <pid>
   
   # JFR记录
   jcmd <pid> JFR.start duration=60s filename=perf.jfr
   ```

2. **添加计时日志**
   ```java
   long start = System.nanoTime();
   try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
       // 操作
   }
   long elapsed = System.nanoTime() - start;
   if (elapsed > 1_000_000) { // 超过1ms
       log.warn("Slow context creation: {} ns", elapsed);
   }
   ```

**解决方案**：

1. **识别热点方法**
   ```bash
   # 分析CPU热点
   jfr print --events ExecutionSample perf.jfr | head -50
   ```

2. **优化慢操作**
   ```java
   // 缓存计算结果
   private final Map<String, String> pathCache = new ConcurrentHashMap<>();
   
   public String getTaskPath() {
       return pathCache.computeIfAbsent(taskId, id -> buildPath());
   }
   ```

### 3.2 CPU使用率高

**症状**：
- CPU使用率持续高位
- 系统响应缓慢

**诊断步骤**：

1. **查看线程CPU使用**
   ```bash
   # top -H查看线程
   top -H -p <pid>
   
   # 将线程ID转换为16进制
   printf "%x\n" <thread_id>
   
   # 在jstack中查找对应线程
   jstack <pid> | grep -A 20 <hex_thread_id>
   ```

2. **检查死循环**
   ```java
   // 添加循环计数器防护
   int iterations = 0;
   while (condition) {
       if (++iterations > MAX_ITERATIONS) {
           throw new RuntimeException("Possible infinite loop detected");
       }
       // 逻辑
   }
   ```

**解决方案**：

1. **优化算法**
   ```java
   // 使用更高效的数据结构
   // HashMap -> ConcurrentHashMap
   // synchronized -> ReentrantLock/StampedLock
   ```

2. **添加限流**
   ```java
   private final RateLimiter rateLimiter = RateLimiter.create(1000); // 1000 ops/sec
   
   public void process() {
       rateLimiter.acquire();
       // 处理逻辑
   }
   ```

## 4. 功能异常排查

### 4.1 上下文丢失

**症状**：
- `IllegalStateException: No active context`
- 异步任务中获取不到上下文

**诊断步骤**：

1. **检查线程信息**
   ```java
   Thread current = Thread.currentThread();
   System.out.println("Thread: " + current.getName() + " [" + current.getId() + "]");
   System.out.println("Has context: " + ManagedThreadContext.hasActiveContext());
   ```

2. **跟踪上下文传递**
   ```java
   // 在父线程
   ManagedThreadContext.ContextSnapshot snapshot = 
       ManagedThreadContext.current().createSnapshot();
   System.out.println("Snapshot created: " + snapshot.getContextId());
   
   // 在子线程
   try (ManagedThreadContext ctx = snapshot.restore()) {
       System.out.println("Context restored: " + ctx.getContextId());
   }
   ```

**解决方案**：

1. **使用正确的异步模式**
   ```java
   // ✅ 使用SafeContextManager
   SafeContextManager.getInstance().executeAsync("task", () -> {
       // 自动继承上下文
   });
   
   // ✅ 手动传递快照
   var snapshot = ManagedThreadContext.current().createSnapshot();
   executor.submit(() -> {
       try (var ctx = snapshot.restore()) {
           // 使用恢复的上下文
       }
   });
   ```

2. **检查线程池配置**
   ```java
   // 使用装饰器包装
   executor = new ThreadPoolExecutor(...) {
       @Override
       public void execute(Runnable command) {
           super.execute(new ContextAwareRunnable(command));
       }
   };
   ```

### 4.2 任务状态不一致

**症状**：
- 任务栈状态异常
- `ContextInconsistencyException`

**诊断步骤**：

1. **打印任务栈**
   ```java
   ManagedThreadContext ctx = ManagedThreadContext.current();
   Stack<TaskNode> stack = ctx.getTaskStack();
   System.out.println("Task stack depth: " + stack.size());
   for (TaskNode task : stack) {
       System.out.println("  - " + task.getName() + " [" + task.getStatus() + "]");
   }
   ```

2. **验证配对操作**
   ```java
   // 添加断言验证
   assert taskStack.size() == expectedDepth : 
       "Task stack inconsistent: " + taskStack.size();
   ```

**解决方案**：

1. **确保配对调用**
   ```java
   ctx.startTask("task1");
   try {
       // 业务逻辑
   } finally {
       ctx.endTask(); // 确保在finally中结束
   }
   ```

2. **添加状态检查**
   ```java
   public void endTask() {
       if (taskStack.isEmpty()) {
           throw new IllegalStateException("No task to end");
       }
       TaskNode task = taskStack.pop();
       if (task.getStatus() != TaskStatus.RUNNING) {
           throw new IllegalStateException("Task not running: " + task.getStatus());
       }
       task.complete();
   }
   ```

## 5. 系统崩溃排查

### 5.1 JVM崩溃

**症状**：
- 生成`hs_err_pid*.log`文件
- 进程异常退出

**诊断步骤**：

1. **分析错误日志**
   ```bash
   # 查看崩溃日志
   cat hs_err_pid*.log
   
   # 关注以下部分：
   # - Problematic frame
   # - Register to memory mapping
   # - Stack
   # - Native frames
   ```

2. **检查本地代码**
   ```java
   // 反射操作可能导致崩溃
   // 检查--add-opens参数
   ```

**解决方案**：

1. **添加JVM参数**
   ```bash
   # 诊断模式需要
   --add-opens java.base/java.lang=ALL-UNNAMED
   --add-opens java.base/java.lang.ref=ALL-UNNAMED
   ```

2. **禁用危险操作**
   ```java
   // 默认关闭反射清理
   ZeroLeakThreadLocalManager.getInstance()
       .setEnableReflectionCleanup(false);
   ```

### 5.2 死锁

**症状**：
- 系统无响应
- 线程挂起

**诊断步骤**：

1. **检测死锁**
   ```bash
   # jstack自动检测死锁
   jstack <pid> | grep -A 100 "Found one Java-level deadlock"
   ```

2. **程序化检测**
   ```java
   ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
   long[] deadlockedThreadIds = threadBean.findDeadlockedThreads();
   if (deadlockedThreadIds != null) {
       ThreadInfo[] threadInfos = threadBean.getThreadInfo(deadlockedThreadIds);
       for (ThreadInfo info : threadInfos) {
           System.err.println("Deadlocked thread: " + info.getThreadName());
       }
   }
   ```

**解决方案**：

1. **避免嵌套锁**
   ```java
   // 使用tryLock避免死锁
   if (lock1.tryLock(1, TimeUnit.SECONDS)) {
       try {
           if (lock2.tryLock(1, TimeUnit.SECONDS)) {
               try {
                   // 临界区
               } finally {
                   lock2.unlock();
               }
           }
       } finally {
           lock1.unlock();
       }
   }
   ```

2. **使用无锁结构**
   ```java
   // 使用ConcurrentHashMap替代同步Map
   // 使用AtomicReference替代锁
   ```

## 6. 日志分析技巧

### 6.1 关键日志位置

```yaml
日志文件:
  应用日志: /var/log/taskflow/application.log
  GC日志: /var/log/taskflow/gc.log
  访问日志: /var/log/taskflow/access.log
  错误日志: /var/log/taskflow/error.log
```

### 6.2 日志级别配置

```yaml
logging:
  level:
    com.syy.taskflowinsight.context: DEBUG
    com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager: TRACE
    org.springframework: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### 6.3 关键日志模式

```bash
# 查找泄漏警告
grep "WARN.*leak" application.log

# 查找异常
grep "ERROR\|Exception" application.log

# 统计上下文创建
grep "Context created" application.log | wc -l

# 查找慢操作
grep "Slow.*operation" application.log
```

## 7. 监控指标说明

### 7.1 核心指标阈值

| 指标 | 正常范围 | 警告阈值 | 严重阈值 |
|------|----------|----------|----------|
| contexts_created - contexts_cleaned | <100 | >100 | >500 |
| leak_detection_count | 0 | >10/min | >50/min |
| context_creation_p99 | <1μs | >5μs | >10μs |
| active_threads | <200 | >500 | >1000 |
| heap_usage | <70% | >80% | >90% |

### 7.2 告警配置

```yaml
alerts:
  - name: context_leak_warning
    condition: contexts_created - contexts_cleaned > 100
    severity: warning
    action: email
    
  - name: memory_critical
    condition: heap_usage > 0.9
    severity: critical
    action: pagerduty
```

## 8. 常见问题FAQ

### Q1: 如何确定是否有ThreadLocal泄漏？

**A**: 检查以下指标：
1. `contexts_created` 远大于 `contexts_cleaned`
2. 老年代内存持续增长
3. MAT分析显示大量ThreadLocalMap$Entry

### Q2: 反射清理功能安全吗？

**A**: 反射清理有一定风险：
- 默认关闭，仅在诊断时启用
- 需要JVM参数`--add-opens`
- 可能影响其他使用ThreadLocal的代码
- 建议只在测试环境使用

### Q3: 如何处理嵌套上下文警告？

**A**: 嵌套上下文通常表示设计问题：
1. 检查是否真的需要嵌套
2. 考虑重用父上下文
3. 如确实需要，忽略警告但注意资源清理

### Q4: 性能达不到目标怎么办？

**A**: 逐步优化：
1. 使用JMH确认基准
2. 分析火焰图找热点
3. 应用性能优化指南中的技术
4. 考虑降低目标或增加资源

### Q5: 生产环境推荐配置？

**A**: 
```yaml
taskflow:
  context-manager:
    leak-detection-interval: 60s
    context-max-age: 30m
    enable-auto-repair: true
  threadlocal-manager:
    enable-reflection-cleanup: false
    diagnostic-mode: false
```

---

**紧急联系**：
- 技术支持：support@taskflowinsight.com
- 紧急热线：400-123-4567
- Slack频道：#taskflow-support

**相关文档**：
- [API设计规范](./API-Design-Specification.md)
- [性能优化指南](./Performance-Optimization-Guide.md)
- [开发执行提示词](./Development-Execution-Prompt.md)