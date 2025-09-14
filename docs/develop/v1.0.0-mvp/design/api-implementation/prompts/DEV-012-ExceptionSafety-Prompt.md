# DEV-012: API异常安全实现 - AI开发提示词

## 第一阶段：需求澄清提示词

```markdown
你是一名异常处理和容错系统设计专家，拥有10年分布式系统容错设计经验。评审API异常安全机制的实现需求。

**输入材料：**
- 任务卡：docs/task/v1.0.0-mvp/api-implementation/TASK-012-APIExceptionSafety.md
- 设计文档：docs/develop/v1.0.0-mvp/design/api-implementation/DEV-012-API异常安全实现.md
- 相关接口：TFI、TaskContext、ContextManager

**评审重点：**
1. 异常分类和处理策略的完整性
2. 异常隔离边界的明确性
3. 降级和恢复机制的合理性
4. 性能影响评估（异常处理开销）
5. 监控和告警机制设计
6. 与业务逻辑的解耦程度

**关键问题：**
- 哪些异常需要记录但不处理？
- 哪些异常需要触发降级？
- 恢复策略的触发条件？
- 异常统计的保留时长？
- 异常处理的性能预算？

**输出格式：**
生成问题清单：docs/task/v1.0.0-mvp/api-implementation/DEV-012-Questions.md

结构要求：
# DEV-012 异常安全机制问题清单

## 设计问题
- 异常分类标准
- 处理策略映射
- 降级触发条件

## 实现问题
- 性能开销控制
- 内存使用限制
- 并发处理策略

## 结论
需求明确度：[百分比]
```

## 第二阶段：代码实现提示词

```markdown
你是一名Java异常处理专家，精通高可用系统设计。实现TFI API的完整异常安全机制。

**角色定位：** 容错专家 + 性能优化专家 + 监控设计专家

**实现目标：**
1. 异常安全执行器框架
2. 分级异常处理策略
3. 自动降级和恢复机制
4. 异常监控和统计
5. 零性能影响设计

**核心组件实现：**

### 1. ExceptionSafeExecutor（异常安全执行器）
```java
package com.syy.taskflowinsight.safety;

public final class ExceptionSafeExecutor {
    
    // 策略注册表
    private static final Map<Class<? extends Throwable>, ExceptionHandler> handlers;
    
    // 统计收集器
    private static final ExceptionStatistics statistics;
    
    // 降级控制器
    private static final CircuitBreaker circuitBreaker;
    
    /**
     * 核心执行方法 - 无返回值
     */
    public static void execute(Runnable task, String context) {
        if (circuitBreaker.isOpen()) {
            // 降级快速返回
            return;
        }
        
        try {
            task.run();
            circuitBreaker.recordSuccess();
        } catch (Throwable t) {
            handleException(t, context);
            circuitBreaker.recordFailure();
        }
    }
    
    /**
     * 核心执行方法 - 有返回值
     */
    public static <T> T execute(Supplier<T> task, T fallback, String context) {
        if (circuitBreaker.isOpen()) {
            return fallback;
        }
        
        try {
            T result = task.get();
            circuitBreaker.recordSuccess();
            return result;
        } catch (Throwable t) {
            handleException(t, context);
            circuitBreaker.recordFailure();
            return fallback;
        }
    }
}
```

### 2. ExceptionHandler（分级处理器）
```java
public interface ExceptionHandler {
    
    enum Severity {
        IGNORE,     // 忽略，仅记录debug
        LOG,        // 记录日志
        DEGRADE,    // 触发降级
        ALERT       // 告警通知
    }
    
    Severity getSeverity(Throwable t);
    
    void handle(Throwable t, String context);
    
    boolean canRecover(Throwable t);
}

// 默认处理器实现
public class DefaultExceptionHandler implements ExceptionHandler {
    
    @Override
    public Severity getSeverity(Throwable t) {
        if (t instanceof OutOfMemoryError) {
            return Severity.ALERT;
        } else if (t instanceof IllegalStateException) {
            return Severity.DEGRADE;
        } else if (t instanceof IllegalArgumentException) {
            return Severity.LOG;
        } else {
            return Severity.IGNORE;
        }
    }
    
    @Override
    public void handle(Throwable t, String context) {
        switch (getSeverity(t)) {
            case ALERT:
                alertService.sendAlert(t, context);
                // fall through
            case DEGRADE:
                degradeService.triggerDegrade(context);
                // fall through
            case LOG:
                logger.error("Exception in {}: {}", context, t.getMessage(), t);
                break;
            case IGNORE:
                logger.debug("Ignored exception in {}: {}", context, t.getMessage());
                break;
        }
    }
}
```

### 3. CircuitBreaker（熔断器）
```java
public class CircuitBreaker {
    
    private enum State {
        CLOSED,     // 正常
        OPEN,       // 熔断
        HALF_OPEN   // 半开
    }
    
    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    
    // 配置参数
    private final int failureThreshold = 5;
    private final long resetTimeout = 60000; // 60秒
    private final double failureRatio = 0.5;
    
    public boolean isOpen() {
        return state == State.OPEN && !shouldAttemptReset();
    }
    
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            failureCount.set(0);
        }
    }
    
    public void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (failureCount.get() >= failureThreshold) {
            state = State.OPEN;
        }
    }
    
    private boolean shouldAttemptReset() {
        return System.currentTimeMillis() - lastFailureTime.get() > resetTimeout;
    }
}
```

### 4. SafeTaskContext（安全包装器）
```java
public class SafeTaskContext implements TaskContext {
    
    private final TaskContext delegate;
    private final ExceptionSafeExecutor executor;
    
    public SafeTaskContext(TaskContext delegate) {
        this.delegate = delegate;
        this.executor = new ExceptionSafeExecutor();
    }
    
    @Override
    public TaskContext message(String message) {
        return executor.execute(
            () -> delegate.message(message),
            this,
            "TaskContext.message"
        );
    }
    
    @Override
    public void close() {
        executor.execute(
            () -> delegate.close(),
            "TaskContext.close"
        );
    }
    // ... 其他方法的安全包装
}
```

**性能优化要点：**
1. 熔断器快速失败
2. 异常处理异步化
3. 统计采样而非全量
4. 内存池化复用对象

**输出要求：**
1. ExceptionSafeExecutor.java
2. ExceptionHandler接口和实现
3. CircuitBreaker.java
4. SafeTaskContext.java
5. 异常统计和监控组件
```

## 第三阶段：测试设计提示词

```markdown
你是一名混沌工程专家，精通故障注入和容错测试。为异常安全机制设计全面的测试。

**测试策略：**

### 1. 异常注入测试
```java
@Test
void testExceptionIsolation() {
    // 注入各类异常，验证隔离效果
    TFI.enable();
    
    // 注入运行时异常
    injectException(new RuntimeException("Test"));
    TFI.startTask("test");
    // 验证业务继续执行
    
    // 注入Error
    injectException(new OutOfMemoryError("Test"));
    TFI.message("test");
    // 验证降级生效
}

@Test
void testExceptionPropagation() {
    // 验证异常不向外传播
    assertDoesNotThrow(() -> {
        TFI.startTask(null); // 应该安全处理
        TFI.message(null);   // 应该安全处理
        TFI.endTask();       // 应该安全处理
    });
}
```

### 2. 降级测试
```java
@Test
void testCircuitBreaker() {
    // 触发熔断
    for (int i = 0; i < 10; i++) {
        injectException(new IllegalStateException());
        TFI.startTask("test" + i);
    }
    
    // 验证熔断生效
    assertTrue(circuitBreaker.isOpen());
    
    // 验证快速失败
    long start = System.currentTimeMillis();
    TFI.startTask("should-fail-fast");
    long duration = System.currentTimeMillis() - start;
    assertTrue(duration < 1); // 应该立即返回
}

@Test
void testAutoRecovery() {
    // 触发熔断
    triggerCircuitBreaker();
    
    // 等待恢复时间
    Thread.sleep(resetTimeout);
    
    // 验证自动恢复
    assertFalse(circuitBreaker.isOpen());
}
```

### 3. 性能影响测试
```java
@PerformanceTest
void testExceptionHandlingOverhead() {
    // 正常执行基准
    long normalTime = measureNormalExecution();
    
    // 异常处理执行
    long exceptionTime = measureWithExceptionHandling();
    
    // 验证开销<1%
    double overhead = (exceptionTime - normalTime) * 100.0 / normalTime;
    assertTrue(overhead < 1.0);
}

@StressTest
void testHighConcurrencyExceptionHandling() {
    // 1000个线程并发触发异常
    ExecutorService executor = Executors.newFixedThreadPool(1000);
    CountDownLatch latch = new CountDownLatch(1000);
    
    for (int i = 0; i < 1000; i++) {
        executor.submit(() -> {
            injectRandomException();
            TFI.startTask("concurrent-test");
            TFI.endTask();
            latch.countDown();
        });
    }
    
    latch.await();
    // 验证系统稳定性
    assertTrue(TFI.isEnabled());
    assertFalse(hasMemoryLeak());
}
```

### 4. 监控测试
```java
@Test
void testExceptionStatistics() {
    // 触发不同类型异常
    triggerException(RuntimeException.class, 5);
    triggerException(IllegalStateException.class, 3);
    triggerException(OutOfMemoryError.class, 1);
    
    // 验证统计准确性
    ExceptionStatistics stats = getStatistics();
    assertEquals(9, stats.getTotalCount());
    assertEquals(5, stats.getCount(RuntimeException.class));
    assertEquals(3, stats.getCount(IllegalStateException.class));
    assertEquals(1, stats.getCount(OutOfMemoryError.class));
}
```

**测试场景矩阵：**
| 异常类型 | 处理策略 | 预期行为 |
|---------|---------|---------|
| NPE | LOG | 记录日志，继续执行 |
| OOM | ALERT | 告警，触发降级 |
| IllegalState | DEGRADE | 降级处理 |
| Custom | IGNORE | 静默忽略 |

**输出要求：**
1. ExceptionSafetyTest.java
2. CircuitBreakerTest.java
3. 性能基准测试报告
4. 故障注入测试场景
```

## 综合执行提示词（完整实现）

```markdown
你是TaskFlowInsight的异常安全架构师，负责构建完整的异常防护体系。

**完整实现计划：**

## Phase 1: 框架搭建（20分钟）
构建异常安全基础设施：
1. ExceptionSafeExecutor - 核心执行器
2. ExceptionHandler - 处理策略
3. CircuitBreaker - 熔断器
4. ExceptionStatistics - 统计监控

## Phase 2: API集成（25分钟）
将异常安全机制集成到API：
1. TFI类方法包装
2. TaskContext安全代理
3. ContextManager保护
4. 降级实现类

关键实现示例：
```java
// TFI集成
public final class TFI {
    
    public static TaskContext start(String taskName) {
        return ExceptionSafeExecutor.execute(
            () -> {
                if (!isEnabled()) {
                    return NullTaskContext.INSTANCE;
                }
                TaskNode node = contextManager.startTask(taskName);
                return new SafeTaskContext(new TaskContextImpl(node));
            },
            NullTaskContext.INSTANCE,
            "TFI.start"
        );
    }
    
    public static void message(String message) {
        ExceptionSafeExecutor.execute(
            () -> {
                if (isEnabled() && message != null) {
                    contextManager.addMessage(Message.create(message));
                }
            },
            "TFI.message"
        );
    }
}

// 降级TaskContext
public class DegradedTaskContext implements TaskContext {
    // 最小功能实现，保证基本可用
    @Override
    public TaskContext message(String message) {
        // 仅记录关键信息
        if (isImportant(message)) {
            simpleLog(message);
        }
        return this;
    }
}
```

## Phase 3: 测试验证（20分钟）
全面测试异常安全机制：
1. 异常隔离测试
2. 降级恢复测试
3. 性能影响测试
4. 并发压力测试

## Phase 4: 监控配置（15分钟）
配置监控和告警：
1. 异常统计报表
2. 熔断状态监控
3. 性能指标采集
4. 告警规则配置

**交付标准：**
1. 零异常泄漏
2. 性能开销<1%
3. 自动降级恢复
4. 完整监控覆盖
5. 100%测试通过

**最终产出：**
- safety/ExceptionSafeExecutor.java
- safety/CircuitBreaker.java
- safety/ExceptionHandler.java
- safety/ExceptionStatistics.java
- api/SafeTaskContext.java
- api/DegradedTaskContext.java
- 测试套件（5个测试类）
- 监控配置文件
- 实现报告
```