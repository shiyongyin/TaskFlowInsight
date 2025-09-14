# Context Management API 参考

> **版本**: 1.0.0  
> **生成日期**: 2025-09-05

## 📚 API概览

Context Management模块提供以下核心API：

| 类 | 用途 | 线程安全 |
|-----|------|----------|
| `ManagedThreadContext` | 线程上下文管理 | ✅ |
| `SafeContextManager` | 全局上下文管理器 | ✅ |
| `TFIAwareExecutor` | 上下文感知线程池 | ✅ |
| `ContextSnapshot` | 上下文快照 | ✅ |

## 🏗️ ManagedThreadContext

线程安全的上下文容器，实现AutoCloseable接口。

### 创建和生命周期

```java
/**
 * 创建新的上下文实例
 * @param sessionName 会话名称
 * @return 新的上下文实例，必须在try-with-resources中使用
 */
public static ManagedThreadContext create(String sessionName);

/**
 * 获取当前线程的上下文
 * @return 当前上下文
 * @throws IllegalStateException 如果当前线程没有活跃上下文
 */
public static ManagedThreadContext current();

/**
 * 关闭上下文，清理所有资源
 * 实现AutoCloseable，支持try-with-resources自动调用
 */
@Override
public void close();
```

**使用示例**:

```java
// ✅ 推荐用法
try (ManagedThreadContext ctx = ManagedThreadContext.create("userService")) {
    // 上下文操作
    TaskNode task = ctx.startTask("processUser");
    processUser(userId);
    ctx.endTask();
} // 自动清理

// ❌ 错误用法 - 没有使用try-with-resources
ManagedThreadContext ctx = ManagedThreadContext.create("userService"); // 可能泄漏
```

### 任务管理

```java
/**
 * 开始一个新任务
 * @param taskName 任务名称
 * @return 任务节点
 * @throws IllegalStateException 如果上下文已关闭
 */
public TaskNode startTask(String taskName);

/**
 * 结束当前任务
 * @return 结束的任务节点
 * @throws IllegalStateException 如果没有活跃任务或上下文已关闭
 */
public TaskNode endTask();

/**
 * 获取当前任务
 * @return 当前任务节点，如果没有活跃任务则返回null
 */
public TaskNode getCurrentTask();
```

### 会话管理

```java
/**
 * 开始一个新会话
 * @param sessionName 会话名称
 * @throws IllegalStateException 如果已存在活跃会话
 */
public void startSession(String sessionName);

/**
 * 结束当前会话
 * @throws IllegalStateException 如果没有活跃会话
 */
public void endSession();

/**
 * 获取当前会话
 * @return 当前会话，如果没有活跃会话则返回null
 */
public Session getCurrentSession();
```

### 属性管理

```java
/**
 * 设置上下文属性
 * @param key 属性键
 * @param value 属性值
 */
public void setAttribute(String key, Object value);

/**
 * 获取上下文属性
 * @param key 属性键
 * @return 属性值，如果不存在则返回null
 */
public Object getAttribute(String key);

/**
 * 移除上下文属性
 * @param key 属性键
 * @return 被移除的属性值
 */
public Object removeAttribute(String key);

/**
 * 获取所有属性
 * @return 属性的只读副本
 */
public Map<String, Object> getAttributes();
```

### 快照支持

```java
/**
 * 创建上下文快照，用于异步传播
 * @return 上下文快照
 */
public ContextSnapshot createSnapshot();

/**
 * 从快照恢复上下文
 * @param snapshot 上下文快照
 * @return 恢复的上下文实例
 */
public static ManagedThreadContext restoreFromSnapshot(ContextSnapshot snapshot);
```

## 🎯 SafeContextManager

全局单例管理器，提供上下文生命周期管理和异步支持。

### 实例获取

```java
/**
 * 获取单例实例
 * @return SafeContextManager实例
 */
public static SafeContextManager getInstance();
```

### 异步执行

```java
/**
 * 异步执行任务，自动传播当前上下文
 * @param taskName 任务名称
 * @param task 要执行的任务
 * @return CompletableFuture
 */
public CompletableFuture<Void> executeAsync(String taskName, Runnable task);

/**
 * 异步执行有返回值的任务
 * @param taskName 任务名称
 * @param task 要执行的任务
 * @return CompletableFuture包含任务结果
 */
public <T> CompletableFuture<T> executeAsync(String taskName, Supplier<T> task);
```

### Runnable/Callable包装

```java
/**
 * 包装Runnable以支持上下文传播
 * @param task 原始任务
 * @return 包装后的任务
 */
public Runnable wrapRunnable(Runnable task);

/**
 * 包装Callable以支持上下文传播
 * @param task 原始任务
 * @return 包装后的任务
 */
public <T> Callable<T> wrapCallable(Callable<T> task);
```

### 监控支持

```java
/**
 * 获取管理器统计信息
 * @return 统计信息映射
 */
public Map<String, Object> getStatistics();

/**
 * 添加泄漏监听器
 * @param listener 泄漏监听器
 */
public void addLeakListener(LeakListener listener);
```

## 🏊 TFIAwareExecutor

支持上下文自动传播的线程池实现。

### 创建线程池

```java
/**
 * 创建固定大小的线程池
 * @param nThreads 线程数量
 * @return TFI感知的线程池
 */
public static TFIAwareExecutor newFixedThreadPool(int nThreads);

/**
 * 创建可配置的线程池
 * @param corePoolSize 核心线程数
 * @param maximumPoolSize 最大线程数
 * @param keepAliveTime 空闲线程存活时间
 * @param unit 时间单位
 * @return TFI感知的线程池
 */
public static TFIAwareExecutor newThreadPool(int corePoolSize, int maximumPoolSize, 
                                            long keepAliveTime, TimeUnit unit);
```

### 使用方式

```java
// 创建线程池
TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(4);

// 提交任务 - 上下文自动传播
executor.submit(() -> {
    // 这里可以直接使用父线程的上下文
    ManagedThreadContext ctx = ManagedThreadContext.current();
    processInThreadPool();
});

// 提交Callable
Future<String> result = executor.submit(() -> {
    ManagedThreadContext ctx = ManagedThreadContext.current();
    return computeResult();
});
```

## 📸 ContextSnapshot

轻量级上下文快照，支持跨线程传播。

### 核心方法

```java
/**
 * 恢复快照到新上下文
 * @return 恢复的上下文实例，必须在try-with-resources中使用
 */
public ManagedThreadContext restore();

/**
 * 检查快照是否包含会话信息
 * @return 如果包含会话则返回true
 */
public boolean hasSession();

/**
 * 获取任务路径
 * @return 任务路径字符串
 */
public String getTaskPath();
```

### 使用示例

```java
// 在父线程中创建快照
ContextSnapshot snapshot;
try (ManagedThreadContext ctx = ManagedThreadContext.create("parent")) {
    ctx.startTask("prepare");
    snapshot = ctx.createSnapshot();
    ctx.endTask();
}

// 在子线程中恢复快照
CompletableFuture.runAsync(() -> {
    try (ManagedThreadContext restored = snapshot.restore()) {
        // 恢复的上下文包含父线程的状态
        TaskNode task = restored.startTask("childTask");
        processChild();
        restored.endTask();
    }
});
```

## 📊 监控API

### 健康检查

```java
// Spring Boot Actuator 健康检查
GET /actuator/health

// 响应示例
{
  "status": "UP",
  "components": {
    "contextManagement": {
      "status": "UP",
      "details": {
        "contexts": {
          "created": 1250,
          "closed": 1248,
          "active": 2,
          "activeThreads": 3,
          "asyncExecutions": 45
        },
        "memory": {
          "usedBytes": 12857,
          "usedHuman": "12.6 KB",
          "warningThreshold": "50.0 MB",
          "criticalThreshold": "100.0 MB"
        },
        "leaks": {
          "detected": 5,
          "fixed": 5,
          "active": 0,
          "warningThreshold": 10,
          "criticalThreshold": 50
        }
      }
    }
  }
}
```

### 详细监控信息

```java
// 获取详细的Context Management信息
GET /actuator/context

// 响应示例
{
  "timestamp": "2025-09-05T22:30:00",
  "status": "RUNNING",
  "metrics": {
    "counters": {
      "contextsCreated": 1250,
      "contextsClosed": 1248,
      "leaksDetected": 5,
      "leaksFixed": 5,
      "asyncExecutions": 45
    },
    "gauges": {
      "activeContexts": 2,
      "activeThreads": 3,
      "memoryUsedBytes": 12857,
      "memoryUsedHuman": "12.6 KB",
      "healthStatus": "HEALTHY"
    }
  },
  "managers": {
    "safeContextManager": {
      "status": "RUNNING",
      "singleton": true
    },
    "zeroLeakManager": {
      "status": "RUNNING",
      "detectionEnabled": true,
      "autoRepairEnabled": true
    }
  }
}
```

### 手动清理

```java
// 触发手动清理
POST /actuator/context

// 响应示例
{
  "timestamp": "2025-09-05T22:30:00",
  "operation": "Manual Cleanup Triggered",
  "before": {
    "activeContexts": 2,
    "leaksDetected": 5,
    "leaksFixed": 5,
    "memoryUsed": "12.6 KB"
  },
  "after": {
    "activeContexts": 1,
    "leaksDetected": 5,
    "leaksFixed": 5,
    "memoryUsed": "8.9 KB"
  },
  "cleaned": 0,
  "status": "SUCCESS"
}
```

## ⚠️ 注意事项

### 强制使用模式

- **必须使用try-with-resources**: 所有`ManagedThreadContext`必须在try-with-resources中使用
- **避免手动close()**: 依赖try-with-resources自动清理，避免手动调用close()
- **单线程使用**: 每个上下文实例只能在创建它的线程中使用

### 线程池集成

```java
// ✅ 推荐 - 使用TFIAwareExecutor
TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(4);
executor.submit(() -> {
    ManagedThreadContext ctx = ManagedThreadContext.current(); // 自动传播
});

// ⚠️ 需要手动处理 - 普通线程池
ExecutorService executor = Executors.newFixedThreadPool(4);
ContextSnapshot snapshot = currentContext.createSnapshot();
executor.submit(() -> {
    try (ManagedThreadContext ctx = snapshot.restore()) {
        // 手动恢复上下文
    }
});
```

### 性能考虑

- **上下文创建**: 当前实测~19.54μs，适合中等频率使用
- **快照创建**: 极快（~57ns），适合高频异步场景
- **内存使用**: 约6.77KB/上下文，注意在高并发场景下的累积影响

## 🐛 错误处理

### 常见异常

| 异常 | 原因 | 解决方法 |
|------|------|----------|
| `IllegalStateException: No active context` | 当前线程没有活跃上下文 | 确保在正确的线程中调用，或使用快照恢复 |
| `IllegalStateException: Context already closed` | 尝试使用已关闭的上下文 | 检查上下文生命周期管理 |
| `IllegalStateException: Session already active` | 尝试在已有会话时启动新会话 | 先结束当前会话或检查业务逻辑 |

### 调试技巧

```java
// 启用详细日志
logging:
  level:
    com.syy.taskflowinsight.context: DEBUG

// 检查上下文状态
ManagedThreadContext ctx = ManagedThreadContext.current();
System.out.println("Context ID: " + ctx.getContextId());
System.out.println("Session: " + ctx.getCurrentSession());
System.out.println("Task: " + ctx.getCurrentTask());
```

---

**最后更新**: 2025-09-05  
**API版本**: v1.0.0  
**兼容性**: TaskFlowInsight v0.0.1-SNAPSHOT+