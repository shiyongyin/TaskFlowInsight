# Context-Management API设计规范

本文档定义Context-Management模块的完整API规范，包括接口签名、异常类型、错误码和使用约束。

## 1. 核心API定义

### 1.1 ManagedThreadContext API

#### 创建与获取

```java
public final class ManagedThreadContext implements AutoCloseable {
    
    /**
     * 创建新的线程上下文
     * @return 新创建的上下文实例，必须在try-with-resources中使用
     * @throws ContextNestingWarning 当检测到嵌套上下文时记录警告（不抛出）
     */
    public static ManagedThreadContext create();
    
    /**
     * 获取当前线程的活动上下文
     * @return 当前活动的上下文
     * @throws IllegalStateException 当前线程没有活动上下文
     */
    public static ManagedThreadContext current();
    
    /**
     * 检查当前线程是否有活动上下文
     * @return true表示有活动上下文
     */
    public static boolean hasActiveContext();
    
    /**
     * 尝试获取当前上下文，不存在则返回Optional.empty()
     * @return Optional包装的上下文
     */
    public static Optional<ManagedThreadContext> tryGetCurrent();
}
```

#### 会话管理

```java
    /**
     * 开始新会话
     * @param sessionName 会话名称（可选）
     * @return 创建的会话实例
     * @throws IllegalStateException 上下文已关闭
     */
    public Session startSession(String sessionName);
    public Session startSession(); // sessionName默认为UUID
    
    /**
     * 结束当前会话
     * @throws IllegalStateException 没有活动会话或上下文已关闭
     * @throws ContextInconsistencyException 会话栈状态不一致
     */
    public void endSession();
    
    /**
     * 获取当前活动会话
     * @return 当前会话，无会话返回null
     */
    public Session getCurrentSession();
```

#### 任务管理

```java
    /**
     * 开始新任务
     * @param taskName 任务名称
     * @return 创建的任务节点
     * @throws IllegalArgumentException taskName为null或空
     * @throws IllegalStateException 上下文已关闭
     */
    public TaskNode startTask(String taskName);
    
    /**
     * 结束当前任务
     * @param status 任务结束状态（可选，默认COMPLETED）
     * @throws IllegalStateException 没有活动任务或上下文已关闭
     * @throws ContextInconsistencyException 任务栈状态不一致
     */
    public void endTask();
    public void endTask(TaskStatus status);
    
    /**
     * 标记当前任务失败
     * @param reason 失败原因
     * @throws IllegalStateException 没有活动任务
     */
    public void failTask(String reason);
    
    /**
     * 获取当前活动任务
     * @return 当前任务，无任务返回null
     */
    public TaskNode getCurrentTask();
```

#### 上下文快照

```java
    /**
     * 创建当前上下文的快照，用于跨线程传递
     * @return 上下文快照（不可变）
     * @throws IllegalStateException 上下文已关闭
     */
    public ContextSnapshot createSnapshot();
    
    /**
     * 上下文快照（内部类）
     */
    public static final class ContextSnapshot {
        /**
         * 恢复快照到新的上下文
         * @return 新的上下文实例，必须在try-with-resources中使用
         */
        public ManagedThreadContext restore();
        
        // 只读属性
        public String getContextId();
        public String getSessionId();
        public String getTaskPath();
        public long getSnapshotTime();
    }
```

#### 状态查询

```java
    /**
     * 获取上下文元数据
     */
    public String getContextId();
    public long getThreadId();
    public int getNestingLevel();
    public boolean isClosed();
    public long getCreatedTime();
    public Duration getAge();
    
    /**
     * 获取父上下文（用于嵌套检测）
     * @return 父上下文，无嵌套返回null
     */
    public ManagedThreadContext getParent();
```

### 1.2 SafeContextManager API

#### 单例获取

```java
public final class SafeContextManager {
    
    /**
     * 获取全局管理器实例
     * @return 单例管理器
     */
    public static SafeContextManager getInstance();
    
    /**
     * 启用管理器（默认启用）
     */
    public void enable();
    
    /**
     * 禁用管理器（测试用）
     */
    public void disable();
    
    /**
     * 优雅关闭管理器
     * @param timeout 最大等待时间
     * @return 是否成功关闭
     */
    public boolean shutdown(Duration timeout);
}
```

#### 上下文管理

```java
    /**
     * 获取或创建当前线程的上下文
     * @return 当前上下文（自动创建）
     */
    public ManagedThreadContext getCurrentContext();
    
    /**
     * 清理当前线程的上下文
     * @param force 是否强制清理（忽略未完成的任务）
     */
    public void clearCurrentThread(boolean force);
    
    /**
     * 在指定上下文中执行任务
     * @param taskName 任务名称
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     * @throws ContextExecutionException 执行异常包装
     */
    public <T> T executeInContext(String taskName, Supplier<T> task);
    public void executeInContext(String taskName, Runnable task);
```

#### 异步执行

```java
    /**
     * 异步执行任务（自动传递上下文）
     * @param taskName 任务名称
     * @param task 异步任务
     * @return CompletableFuture
     */
    public CompletableFuture<Void> executeAsync(String taskName, Runnable task);
    public <T> CompletableFuture<T> executeAsync(String taskName, Supplier<T> task);
    
    /**
     * 使用指定线程池异步执行
     * @param executor 线程池
     * @param taskName 任务名称
     * @param task 任务
     */
    public CompletableFuture<Void> executeAsync(Executor executor, String taskName, Runnable task);
```

#### 虚拟线程支持（Java 21+）

```java
    /**
     * 使用StructuredTaskScope执行并发任务
     * @param scopeName 作用域名称
     * @param tasks 任务列表
     * @return 所有任务的结果
     * @throws StructuredConcurrencyException 结构化并发异常
     */
    public <T> List<T> executeStructuredTasks(String scopeName, List<Callable<T>> tasks);
    
    /**
     * 创建虚拟线程执行器（自动上下文传递）
     * @return 配置好的虚拟线程执行器
     */
    public ExecutorService createVirtualThreadExecutor();
```

#### 会话管理

```java
    /**
     * 注册会话到全局索引
     * @param session 会话实例
     * @throws IllegalArgumentException session为null
     */
    public void registerSession(Session session);
    
    /**
     * 注销会话
     * @param sessionId 会话ID
     */
    public void unregisterSession(String sessionId);
    
    /**
     * 查询会话
     * @param sessionId 会话ID
     * @return 会话实例，不存在返回null
     */
    public Session getSession(String sessionId);
    
    /**
     * 获取所有活动会话
     * @return 不可变的会话集合
     */
    public Collection<Session> getActiveSessions();
```

#### 监控与诊断

```java
    /**
     * 获取统计指标
     */
    public long getTotalContextsCreated();
    public long getTotalContextsCleaned();
    public long getTotalLeaksDetected();
    public long getTotalLeaksFixed();
    public int getActiveContextCount();
    public int getActiveThreadCount();
    
    /**
     * 获取健康状态
     * @return 健康状态枚举
     */
    public HealthStatus getHealthStatus();
    
    /**
     * 生成诊断报告
     * @return JSON格式的诊断信息
     */
    public String generateDiagnosticReport();
    
    /**
     * 注册泄漏监听器
     * @param listener 监听器实现
     */
    public void registerLeakListener(LeakListener listener);
```

### 1.3 ZeroLeakThreadLocalManager API

#### 单例获取

```java
public final class ZeroLeakThreadLocalManager {
    
    /**
     * 获取管理器实例
     * @return 单例实例
     */
    public static ZeroLeakThreadLocalManager getInstance();
    
    /**
     * 初始化管理器（应用启动时调用）
     * @param config 配置参数
     */
    public void initialize(LeakManagerConfig config);
}
```

#### 上下文注册

```java
    /**
     * 注册线程上下文（用于跟踪）
     * @param thread 线程实例
     * @param context 上下文对象
     * @throws IllegalArgumentException 参数为null
     */
    public void registerContext(Thread thread, Object context);
    
    /**
     * 注销线程上下文
     * @param threadId 线程ID
     * @return 是否成功注销
     */
    public boolean unregisterContext(long threadId);
    
    /**
     * 检查线程是否有泄漏风险
     * @param threadId 线程ID
     * @return 泄漏风险等级
     */
    public LeakRisk checkLeakRisk(long threadId);
```

#### 泄漏检测与修复

```java
    /**
     * 手动触发泄漏检测
     * @return 检测到的泄漏数量
     */
    public int detectLeaks();
    
    /**
     * 修复检测到的泄漏
     * @param aggressive 是否采用激进策略（包括反射清理）
     * @return 修复的泄漏数量
     */
    public int repairLeaks(boolean aggressive);
    
    /**
     * 紧急清理指定线程
     * @param threadId 线程ID
     * @param force 是否强制清理
     * @throws ThreadCleanupException 清理失败
     */
    public void emergencyCleanup(long threadId, boolean force);
```

#### 诊断模式

```java
    /**
     * 启用/禁用诊断模式
     * @param enable 是否启用
     */
    public void setDiagnosticMode(boolean enable);
    
    /**
     * 启用/禁用反射清理（需要JVM参数）
     * @param enable 是否启用
     * @return 是否成功设置（JVM参数检查）
     */
    public boolean setEnableReflectionCleanup(boolean enable);
    
    /**
     * 检查JVM参数兼容性
     * @return 兼容性检查结果
     */
    public JvmCompatibility checkJvmCompatibility();
    
    /**
     * 导出ThreadLocal诊断信息
     * @return 诊断数据
     */
    public ThreadLocalDiagnostics exportDiagnostics();
```

## 2. 异常类型定义

### 2.1 异常层次结构

```java
// 基础异常
public class ContextException extends RuntimeException {
    private final String contextId;
    private final ErrorCode errorCode;
}

// 状态异常
public class IllegalContextStateException extends ContextException {}

// 一致性异常
public class ContextInconsistencyException extends ContextException {}

// 执行异常
public class ContextExecutionException extends ContextException {}

// 泄漏异常
public class ContextLeakException extends ContextException {}

// 清理异常
public class ThreadCleanupException extends ContextException {}

// 并发异常
public class StructuredConcurrencyException extends ContextException {}

// 资源异常
public class ResourceExhaustedException extends ContextException {}
```

### 2.2 异常使用场景

| 异常类型 | 使用场景 | 严重程度 | 处理建议 |
|---------|---------|----------|----------|
| IllegalContextStateException | 上下文状态不正确 | 中 | 检查调用顺序 |
| ContextInconsistencyException | 栈状态不一致 | 高 | 强制清理并重建 |
| ContextExecutionException | 任务执行失败 | 中 | 重试或降级 |
| ContextLeakException | 检测到内存泄漏 | 高 | 触发紧急清理 |
| ThreadCleanupException | 清理操作失败 | 严重 | 记录并告警 |
| ResourceExhaustedException | 资源耗尽 | 严重 | 限流或扩容 |

## 3. 错误码规范

### 3.1 错误码格式

格式：`CTX-{模块}-{编号}`

- CTX：Context模块前缀
- 模块：TC(ThreadContext)、CM(ContextManager)、TL(ThreadLocal)
- 编号：四位数字

### 3.2 错误码定义

```java
public enum ErrorCode {
    // ThreadContext错误（1000-1999）
    CTX_TC_1001("CTX-TC-1001", "上下文已关闭"),
    CTX_TC_1002("CTX-TC-1002", "没有活动上下文"),
    CTX_TC_1003("CTX-TC-1003", "嵌套上下文超过限制"),
    CTX_TC_1004("CTX-TC-1004", "任务栈不一致"),
    CTX_TC_1005("CTX-TC-1005", "会话栈不一致"),
    
    // ContextManager错误（2000-2999）
    CTX_CM_2001("CTX-CM-2001", "管理器未初始化"),
    CTX_CM_2002("CTX-CM-2002", "会话注册失败"),
    CTX_CM_2003("CTX-CM-2003", "异步执行失败"),
    CTX_CM_2004("CTX-CM-2004", "上下文传递失败"),
    CTX_CM_2005("CTX-CM-2005", "健康检查失败"),
    
    // ThreadLocal错误（3000-3999）
    CTX_TL_3001("CTX-TL-3001", "泄漏检测失败"),
    CTX_TL_3002("CTX-TL-3002", "反射清理不可用"),
    CTX_TL_3003("CTX-TL-3003", "JVM参数缺失"),
    CTX_TL_3004("CTX-TL-3004", "紧急清理失败"),
    CTX_TL_3005("CTX-TL-3005", "诊断模式错误");
    
    private final String code;
    private final String message;
}
```

## 4. 使用约束与最佳实践

### 4.1 强制约束

```java
// ✅ 正确：必须使用try-with-resources
try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
    ctx.startSession();
    // 业务逻辑
    ctx.endSession();
}

// ❌ 错误：不使用自动资源管理
ManagedThreadContext ctx = ManagedThreadContext.create();
// 可能泄漏！
```

### 4.2 线程池使用

```java
// ✅ 正确：使用装饰器自动传递上下文
executor.submit(new ContextAwareRunnable(() -> {
    // 自动继承父线程上下文
}));

// ✅ 正确：使用SafeContextManager
SafeContextManager.getInstance().executeAsync("task", () -> {
    // 自动处理上下文传递
});

// ❌ 错误：直接提交任务
executor.submit(() -> {
    // 上下文丢失！
});
```

### 4.3 异步传递

```java
// ✅ 正确：创建快照显式传递
ContextSnapshot snapshot = ManagedThreadContext.current().createSnapshot();
CompletableFuture.runAsync(() -> {
    try (ManagedThreadContext ctx = snapshot.restore()) {
        // 使用恢复的上下文
    }
});

// ❌ 错误：依赖InheritableThreadLocal
CompletableFuture.runAsync(() -> {
    // InheritableThreadLocal在线程池中不可靠！
});
```

### 4.4 嵌套处理

```java
// ✅ 可接受：必要的嵌套（会产生警告）
try (ManagedThreadContext ctx1 = ManagedThreadContext.create()) {
    // 外层逻辑
    try (ManagedThreadContext ctx2 = ManagedThreadContext.create()) {
        // 嵌套逻辑，自动检测并警告
    }
    // ctx1自动恢复
}

// ⚠️ 建议：避免不必要的嵌套
try (ManagedThreadContext ctx = ManagedThreadContext.create()) {
    processMainTask();
    processSubTask(); // 使用同一个上下文
}
```

## 5. 性能契约

### 5.1 性能保证

| 操作 | 性能目标 | 最差情况 | 测试方法 |
|------|----------|----------|----------|
| 上下文创建 | <1μs | <5μs | JMH基准测试 |
| 任务操作 | <100ns | <500ns | JMH基准测试 |
| 快照创建 | <500ns | <2μs | JMH基准测试 |
| 快照恢复 | <1μs | <5μs | JMH基准测试 |
| 泄漏检测 | <10ms/1000线程 | <50ms | 压力测试 |

### 5.2 内存契约

| 组件 | 内存占用 | 说明 |
|------|----------|------|
| ManagedThreadContext | <1KB | 包含栈和元数据 |
| ContextSnapshot | <256B | 仅包含必要信息 |
| 每线程开销 | <2KB | 含ThreadLocal存储 |
| 管理器开销 | <10MB | 1000线程规模 |

## 6. 兼容性说明

### 6.1 Java版本

- **最低要求**：Java 17（基础功能）
- **推荐版本**：Java 21（完整功能）
- **虚拟线程**：需要Java 21+
- **StructuredTaskScope**：需要Java 21+

### 6.2 Spring Boot集成

```java
@Configuration
@ConditionalOnClass(SafeContextManager.class)
public class ContextManagementAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public SafeContextManager contextManager() {
        return SafeContextManager.getInstance();
    }
    
    @Bean
    public ContextWebFilter contextWebFilter() {
        return new ContextWebFilter();
    }
}
```

### 6.3 反射清理要求

```bash
# JVM启动参数（仅诊断模式需要）
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.ref=ALL-UNNAMED
```

## 7. API版本管理

### 7.1 版本策略

- **当前版本**：1.0.0
- **稳定性**：MVP阶段
- **兼容承诺**：1.x版本内API兼容

### 7.2 弃用策略

```java
/**
 * @deprecated Since 1.1.0, use {@link #executeInContext(String, Supplier)} instead
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public void oldMethod() {
    // 旧实现
}
```

### 7.3 实验性API

```java
/**
 * @apiNote Experimental API, subject to change
 * @since 1.0.0
 */
@Experimental
public void experimentalFeature() {
    // 实验性功能
}
```

---

**注意**：本API规范是Context-Management模块的核心契约，所有实现必须严格遵守。任何API变更需要经过评审并更新本文档。