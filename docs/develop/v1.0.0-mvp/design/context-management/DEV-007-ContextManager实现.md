# DEV-007: ContextManager上下文管理器实现

## 开发概述

基于TASK-007任务文档实现SafeContextManager核心管理组件，采用防御性编程和强制清理策略，解决ThreadLocal内存泄漏风险，并为异步场景预留扩展能力。这是TaskFlowInsight系统的核心管理组件，负责全局统一的上下文管理。

## 实现目标

### 核心目标
1. 实现全局统一的上下文管理，确保零泄漏
2. 提供线程安全的会话索引和查询功能
3. 实现主动的泄漏检测和修复机制
4. 支持异步任务的上下文传递
5. 提供完善的监控和诊断接口

### 高级目标
- 支持虚拟线程（Java 21+）
- InheritableThreadLocal父子线程继承
- StructuredTaskScope结构化并发
- 线程池安全集成
- 实时泄漏检测和自动修复

## 技术设计

### 1. SafeContextManager核心架构

```java
public final class SafeContextManager {
    // 单例模式
    private static final SafeContextManager INSTANCE = new SafeContextManager();
    
    // 上下文存储 - 支持父子线程继承
    private final InheritableThreadLocal<ManagedThreadContext> contextHolder;
    
    // 全局索引
    private final ConcurrentHashMap<String, Session> sessionIndex;
    private final ConcurrentHashMap<Long, WeakReference<ManagedThreadContext>> threadIndex;
    
    // 泄漏检测和修复
    private final ScheduledExecutorService cleanupExecutor;
    private final ScheduledExecutorService monitorExecutor;
    
    // 统计和监控
    private final AtomicLong totalContextsCreated;
    private final AtomicLong totalLeaksDetected;
    private final AtomicLong totalLeaksFixed;
}
```

### 2. 零泄漏设计机制

**主动防御策略**：
- 强制资源管理模式
- 实时泄漏检测
- 自动修复机制
- 紧急清理能力

**多层防护机制**：
1. **创建时检测** - 检查旧上下文泄漏
2. **运行时监控** - 定期检测超时上下文
3. **死线程清理** - WeakReference自动清理
4. **强制清理** - 系统关闭时强制清理

### 3. 异步支持设计

**上下文传递机制**：
- 上下文快照创建
- 异步线程恢复
- CompletableFuture集成
- 虚拟线程支持

**传递策略**：
- InheritableThreadLocal（父子线程）
- 显式传递（线程池场景）
- 装饰器模式（自动包装）
- ContextSnapshot（跨线程传递）

## 实现细节

### 1. 文件结构
```
src/main/java/com/syy/taskflowinsight/context/
├── SafeContextManager.java              # 主管理器
├── TFIAwareThreadPool.java              # TFI感知线程池
├── ContextAwareRunnable.java            # 上下文感知任务包装
├── ContextAwareCallable.java            # 上下文感知Callable包装
├── NoOpThreadContext.java               # 空操作上下文
└── ThreadLocalBoundaries.java           # ThreadLocal边界说明
```

### 1.1 资源释放与关闭
- ScheduledExecutorService 增加优雅关闭策略：在 `shutdown()` 中先 `shutdown()`，等待 5s 未终止再 `shutdownNow()`；处理中断并最终输出一次指标报告。
- 注册 JVM shutdown hook：在进程退出时调用 `shutdown()`，确保清理上下文与回收资源。

### 1.2 InheritableThreadLocal 使用边界
- 仅用于直接 `new Thread()` 的父子线程继承；
- 在线程池复用与虚拟线程载体切换场景中，必须使用“上下文快照 + 装饰器（ContextAwareRunnable/Callable）”的显式传播方式；
- 文档与代码中明确提示禁止在池化线程中依赖 ITL 继承。

### 2. 关键方法实现

**上下文获取和创建**：
```java
public ManagedThreadContext getCurrentContext() {
    ManagedThreadContext context = contextHolder.get();
    if (context == null || context.isClosed()) {
        context = createNewContext();
    }
    return context;
}

private ManagedThreadContext createNewContext() {
    // 检查旧上下文泄漏
    // 创建新上下文
    // 注册到索引
    // 更新统计
}
```

**泄漏检测和修复**：
```java
private void detectAndFixLeaks() {
    // 检查上下文年龄
    // 检查线程存活状态
    // 标记泄漏上下文
    // 触发告警机制
}

private void repairLeaks() {
    // 清理泄漏上下文
    // 强制ThreadLocal清理
    // 更新统计信息
}
```

**异步执行支持**：
```java
public CompletableFuture<Void> executeAsync(String taskName, Runnable task) {
    // 捕获当前上下文快照
    ManagedThreadContext.ContextSnapshot snapshot = getCurrentContext().createSnapshot();
    
    return CompletableFuture.runAsync(() -> {
        // 在异步线程中恢复上下文
        try (ManagedThreadContext ctx = snapshot.restore()) {
            ctx.startTask(taskName);
            task.run();
            ctx.endTask();
        }
    });
}
```

### 3. 虚拟线程支持（Java 21+）

**StructuredTaskScope集成**：
```java
public <T> List<T> executeStructuredTasks(String scopeName, List<Callable<T>> tasks) {
    ManagedThreadContext.ContextSnapshot snapshot = getCurrentContext().createSnapshot();
    
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Future<T>> futures = tasks.stream()
            .map(task -> scope.fork(() -> {
                try (ManagedThreadContext ctx = snapshot.restore()) {
                    return task.call();
                }
            }))
            .toList();
        
        scope.join();
        scope.throwIfFailed();
        
        return futures.stream()
            .map(Future::resultNow)
            .toList();
    }
}
```

### 4. 线程池集成

**TFIAwareThreadPool**：
```java
public class TFIAwareThreadPool extends ThreadPoolExecutor {
    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(new ContextAwareRunnable(task));
    }
    
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return super.submit(new ContextAwareCallable<>(task));
    }
}
```

### 5. 简化执行策略（KISS模式）
- 传播策略：仅使用“显式快照 + 装饰器（ContextAwareRunnable/Callable）”；禁用在线程池/虚拟线程场景下依赖 ITL 的隐式继承。
- 泄漏检测：默认关闭；保留“创建时检测 + 可选的周期性检测（≥30s）”；反射路径默认关闭，仅诊断模式短时启用且运行时自检 `--add-opens`。
- 指标最小集：contexts_created/contexts_cleaned/leaks_detected/leaks_fixed/active_threads；更细指标延后到增强版本。
- 关闭策略：保留优雅关闭与 Shutdown Hook；定时任务先 `shutdown()`，5s 超时后 `shutdownNow()`，确保最终指标上报一次。

## 实现要求

### 1. 内存安全要求
- ☐ 零ThreadLocal泄漏保证
- ☑️ WeakReference正确使用
- ☑️ 自动垃圾回收友好
- ☐ 内存使用监控
- ☑️ 泄漏自动检测和修复

### 2. 线程安全要求
- ☑️ 所有操作线程安全
- ☑️ ConcurrentHashMap正确使用
- ☑️ 原子操作保证
- ☑️ 无竞争条件
- ☑️ 死锁预防机制

### 3. 性能要求
- ☐ 上下文操作 < 1μs
- ☐ 泄漏检测开销 < 1%
- ☐ 内存占用 < 5MB（1000线程）
- ☑️ 异步传递开销最小
- ☐ 高并发场景稳定

### 4. 异步支持要求
- ☑️ CompletableFuture完整支持
- ☐ 虚拟线程兼容
- ☐ StructuredTaskScope集成
- ☑️ 线程池自动包装
- ☑️ 上下文快照轻量化

#### 评估说明（实现要求）
- 内存安全：有检测机制但缺少长期验证；ZeroLeakThreadLocalManager使用WeakReference；设计避免强引用链；缺少实时内存监控指标；定期清理和警告机制。
- 线程安全：使用ConcurrentHashMap和原子操作，通过同步机制避免竞争条件，避免嵌套锁，满足线程安全要求。
- 性能：实测操作时间超出目标；未测量检测开销；未进行大规模测试；快照机制轻量化；缺少高并发长期测试。
- 异步支持：executeAsync方法实现；未实现虚拟线程支持；未实现StructuredTaskScope集成；TFIAwareExecutor实现；快照创建57ns性能优秀。

## 监控和诊断

### 1. 核心指标
```java
// 实时统计
public long getTotalContextsCreated();
public long getTotalContextsCleaned();
public long getTotalLeaksDetected();
public long getTotalLeaksFixed();
public HealthStatus getHealthStatus();
```

### 2. 健康检查
```java
public enum HealthStatus {
    HEALTHY,    // 正常状态
    WARNING,    // 有少量泄漏
    CRITICAL    // 严重泄漏
}
```

### 3. 告警机制
```java
public interface LeakListener {
    void onLeakWarning(int leaksCount);
    void onLeakCritical(int leaksCount);
}
```

## 配置管理

### 1. 检测配置
- 启用开关：`taskflow.context.leakDetection.enabled`（默认false，建议仅调试期开启）
- 泄漏检测间隔：`taskflow.context.leakDetection.intervalMillis`（默认60000ms）
- 上下文最大年龄：30分钟（可配置）
- 告警阈值：警告10个，严重50个（后续增强项）
- 自动修复：开启后按周期清理泄漏上下文（关闭时不调度任务）

### 2. 性能配置
- 监控线程优先级：最小优先级
- 清理线程优先级：普通优先级
- 紧急清理线程：最高优先级
- 执行器线程数：最小配置

## 测试要求

### 1. 功能测试
- [ ] 上下文创建和管理
- [ ] 会话索引和查询
- [ ] 异步任务上下文传递
- [ ] 父子线程上下文继承
- [ ] 虚拟线程兼容性

### 2. 内存安全测试
- [ ] ThreadLocal 100%清理验证
- [ ] 泄漏检测机制有效性
- [ ] 自动修复机制验证
- [ ] 长时间运行稳定性
- [ ] 内存压力测试

### 3. 异步场景测试
- [ ] CompletableFuture上下文传递
- [ ] 多级异步链测试
- [ ] 并行任务上下文隔离
- [ ] 线程池集成测试
- [ ] 虚拟线程大规模测试

### 4. 性能测试
- [ ] 高并发性能基准
- [ ] 内存使用效率
- [ ] 泄漏检测开销
- [ ] 异步传递开销
- [ ] 长时间运行稳定性

## 验收标准

### MVP必需功能
- ☐ **零内存泄漏保证** - 24小时压测验证
- ☐ **自动泄漏检测和修复** - 秒级检测，自动修复
- ☑️ **线程池安全集成** - 主流线程池兼容
- ☑️ **基础异步支持** - CompletableFuture完整支持

### 高级功能
- ☐ **虚拟线程支持** - Java 21+ 完整兼容
- ☐ **StructuredTaskScope集成** - 结构化并发支持
- ☐ **实时监控** - 完整的指标和健康检查
- ☐ **自动告警** - 泄漏情况及时通知

### 质量要求
- ☐ 单元测试覆盖率 > 95%
- ☐ 集成测试通过率 100%
- ☐ 性能基准达标
- ☐ 内存泄漏零容忍
- ☐ 长时间运行稳定

#### 评估说明（验收标准）
- MVP必需功能：需要24小时压测验证；有机制但未达到"秒级检测"；TFIAwareExecutor完整实现；CompletableFuture支持完整。
- 高级功能：未实现虚拟线程支持；未实现StructuredTaskScope集成；缺少完整监控指标；仅有日志警告。

## 风险评估

### 技术风险
1. **InheritableThreadLocal限制** - 在线程池中无效
   - 缓解：提供显式传递机制和装饰器模式
2. **虚拟线程兼容性** - 载体线程切换问题
   - 缓解：使用ContextSnapshot显式传递
3. **泄漏检测准确性** - 误检或漏检风险
   - 缓解：多重检测机制和保守策略

### 业务风险
1. **性能影响** - 监控和检测开销
   - 缓解：优化检测算法，降低频率
2. **复杂度增加** - API使用复杂度
   - 缓解：提供简化的装饰器和自动包装

## 实施计划

### Phase 1: 核心管理器 (2-3天)
- SafeContextManager基础实现
- 泄漏检测和修复机制
- 基本的监控和统计

### Phase 2: 异步支持 (2-3天)
- CompletableFuture集成
- 上下文快照和恢复
- 线程池包装器实现

### Phase 3: 虚拟线程支持 (1-2天)
- Java 21+特性集成
- StructuredTaskScope支持
- 虚拟线程兼容性测试

### Phase 4: 监控和诊断 (1-2天)
- 完整的监控指标
- 健康检查和告警
- 诊断工具和文档

## 运维指南

### 1. 监控配置（默认关闭，按需开启）
```yaml
taskflow:
  monitoring:
    endpoint:
      enabled: false           # 开启后暴露 /actuator/taskflow 只读端点
  context:
    max-context-age-millis: 1800000   # 30分钟
    leak-detection:
      enabled: false
      interval-millis: 60000          # 60秒
```

### 2. 诊断命令
- 查看上下文统计：`/actuator/taskflow/contexts`
- 检查健康状态：`/actuator/taskflow/health`
- 触发手动清理：`/actuator/taskflow/cleanup`
- 导出诊断报告：`/actuator/taskflow/diagnostics`

---

**重要提醒**: SafeContextManager是系统的核心管理组件，必须确保高可用性和零故障。所有实现都必须经过严格的并发测试和长时间运行验证。
