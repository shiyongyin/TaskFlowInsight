# TASK-012: API异常安全处理实现

## 任务概述

为TaskFlowInsight的TFI核心API实现全面的异常安全处理机制，确保任何内部异常都不会影响业务逻辑的正常执行。

## 需求分析

1. 所有TFI核心API必须是异常安全的
2. 内部异常应当被优雅捕获和处理
3. 提供多级别的异常处理策略
4. 支持降级模式和禁用模式
5. 建立异常监控和报告机制

## 技术实现

### 1. 异常安全执行器
```java
/**
 * 异常安全执行器 - TFI内部所有关键操作的异常保护
 */
public final class ExceptionSafeExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionSafeExecutor.class);
    
    // 异常处理器注册表
    private static final Map<Class<? extends Exception>, ExceptionHandler> exceptionHandlers = new ConcurrentHashMap<>();
    
    // 异常统计信息
    private static final AtomicLong totalExceptions = new AtomicLong(0);
    private static final ConcurrentHashMap<String, AtomicLong> exceptionCounts = new ConcurrentHashMap<>();
    
    // 异常监听器
    private static final List<ExceptionListener> exceptionListeners = new CopyOnWriteArrayList<>();
    
    static {
        // 注册默认异常处理器
        registerDefaultHandlers();
    }
    
    /**
     * 安全执行无返回值操作
     * @param operation 要执行的操作
     * @param operationName 操作名称（用于日志）
     */
    public static void safeExecute(Runnable operation, String operationName) {
        try {
            operation.run();
        } catch (Exception e) {
            handleException(e, operationName, null);
        }
    }
    
    /**
     * 安全执行有返回值操作
     * @param operation 要执行的操作
     * @param defaultValue 异常时的默认返回值
     * @param operationName 操作名称
     * @return 操作结果或默认值
     */
    public static <T> T safeExecute(Supplier<T> operation, T defaultValue, String operationName) {
        try {
            return operation.get();
        } catch (Exception e) {
            handleException(e, operationName, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 安全执行可抛出异常的操作
     * @param operation 要执行的操作
     * @param defaultValue 异常时的默认返回值
     * @param operationName 操作名称
     * @return 操作结果或默认值
     */
    public static <T> T safeExecuteCallable(Callable<T> operation, T defaultValue, String operationName) {
        try {
            return operation.call();
        } catch (Exception e) {
            handleException(e, operationName, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 异常处理核心逻辑
     */
    private static void handleException(Exception exception, String operationName, Object defaultValue) {
        // 更新异常统计
        totalExceptions.incrementAndGet();
        exceptionCounts.computeIfAbsent(exception.getClass().getSimpleName(), k -> new AtomicLong(0))
                       .incrementAndGet();
        
        // 获取异常处理器
        ExceptionHandler handler = getExceptionHandler(exception.getClass());
        
        // 执行异常处理
        ExceptionHandlingResult result = handler.handle(exception, operationName, defaultValue);
        
        // 根据处理结果执行相应动作
        switch (result.getAction()) {
            case LOG_AND_CONTINUE:
                logException(exception, operationName, ExceptionSeverity.LOW);
                break;
                
            case LOG_AND_RECOVER:
                logException(exception, operationName, ExceptionSeverity.MEDIUM);
                performRecovery(operationName, result.getRecoveryAction());
                break;
                
            case LOG_AND_DISABLE:
                logException(exception, operationName, ExceptionSeverity.HIGH);
                disableFeature(operationName);
                break;
                
            case LOG_AND_ALERT:
                logException(exception, operationName, ExceptionSeverity.CRITICAL);
                sendAlert(exception, operationName);
                break;
        }
        
        // 通知异常监听器
        notifyExceptionListeners(exception, operationName, result);
    }
    
    /**
     * 注册默认异常处理器
     */
    private static void registerDefaultHandlers() {
        // NullPointerException处理器
        exceptionHandlers.put(NullPointerException.class, new NullPointerExceptionHandler());
        
        // IllegalArgumentException处理器
        exceptionHandlers.put(IllegalArgumentException.class, new IllegalArgumentExceptionHandler());
        
        // IllegalStateException处理器
        exceptionHandlers.put(IllegalStateException.class, new IllegalStateExceptionHandler());
        
        // ConcurrentModificationException处理器
        exceptionHandlers.put(ConcurrentModificationException.class, new ConcurrentModificationExceptionHandler());
        
        // OutOfMemoryError处理器
        exceptionHandlers.put(OutOfMemoryError.class, new OutOfMemoryErrorHandler());
        
        // 通用异常处理器（兜底）
        exceptionHandlers.put(Exception.class, new GenericExceptionHandler());
    }
    
    /**
     * 获取异常处理器
     */
    private static ExceptionHandler getExceptionHandler(Class<? extends Exception> exceptionClass) {
        // 精确匹配查找
        ExceptionHandler handler = exceptionHandlers.get(exceptionClass);
        if (handler != null) {
            return handler;
        }
        
        // 继承关系查找
        for (Map.Entry<Class<? extends Exception>, ExceptionHandler> entry : exceptionHandlers.entrySet()) {
            if (entry.getKey().isAssignableFrom(exceptionClass)) {
                return entry.getValue();
            }
        }
        
        // 返回通用处理器
        return exceptionHandlers.get(Exception.class);
    }
}
```

### 2. 异常处理器接口
```java
/**
 * 异常处理器接口
 */
public interface ExceptionHandler {
    ExceptionHandlingResult handle(Exception exception, String operationName, Object context);
}

/**
 * 空指针异常处理器
 */
public class NullPointerExceptionHandler implements ExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NullPointerExceptionHandler.class);
    
    @Override
    public ExceptionHandlingResult handle(Exception exception, String operationName, Object context) {
        LOGGER.warn("NullPointerException in operation '{}': {}", operationName, exception.getMessage());
        
        // 空指针异常通常是编程错误，但不影响整体功能
        return ExceptionHandlingResult.builder()
            .action(ExceptionAction.LOG_AND_CONTINUE)
            .severity(ExceptionSeverity.MEDIUM)
            .message("Null pointer encountered in " + operationName)
            .recoverable(true)
            .build();
    }
}

/**
 * 非法参数异常处理器
 */
public class IllegalArgumentExceptionHandler implements ExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IllegalArgumentExceptionHandler.class);
    
    @Override
    public ExceptionHandlingResult handle(Exception exception, String operationName, Object context) {
        LOGGER.warn("IllegalArgumentException in operation '{}': {}", operationName, exception.getMessage());
        
        // 非法参数异常通常可以忽略
        return ExceptionHandlingResult.builder()
            .action(ExceptionAction.LOG_AND_CONTINUE)
            .severity(ExceptionSeverity.LOW)
            .message("Invalid argument in " + operationName + ": " + exception.getMessage())
            .recoverable(true)
            .build();
    }
}

/**
 * 非法状态异常处理器
 */
public class IllegalStateExceptionHandler implements ExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IllegalStateExceptionHandler.class);
    
    @Override
    public ExceptionHandlingResult handle(Exception exception, String operationName, Object context) {
        LOGGER.error("IllegalStateException in operation '{}': {}", operationName, exception.getMessage());
        
        // 非法状态异常可能需要恢复操作
        return ExceptionHandlingResult.builder()
            .action(ExceptionAction.LOG_AND_RECOVER)
            .severity(ExceptionSeverity.HIGH)
            .message("Invalid state in " + operationName)
            .recoverable(true)
            .recoveryAction(() -> performStateRecovery(operationName))
            .build();
    }
    
    private void performStateRecovery(String operationName) {
        LOGGER.info("Performing state recovery for operation: {}", operationName);
        // 执行状态恢复逻辑
        try {
            ContextManager.getInstance().cleanup();
        } catch (Exception e) {
            LOGGER.error("State recovery failed for operation: " + operationName, e);
        }
    }
}

/**
 * 并发修改异常处理器
 */
public class ConcurrentModificationExceptionHandler implements ExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentModificationExceptionHandler.class);
    
    @Override
    public ExceptionHandlingResult handle(Exception exception, String operationName, Object context) {
        LOGGER.warn("ConcurrentModificationException in operation '{}': {}", operationName, exception.getMessage());
        
        // 并发修改异常通常是暂时性的，可以继续运行
        return ExceptionHandlingResult.builder()
            .action(ExceptionAction.LOG_AND_CONTINUE)
            .severity(ExceptionSeverity.MEDIUM)
            .message("Concurrent modification detected in " + operationName)
            .recoverable(true)
            .build();
    }
}

/**
 * 内存溢出异常处理器
 */
public class OutOfMemoryErrorHandler implements ExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutOfMemoryErrorHandler.class);
    
    @Override
    public ExceptionHandlingResult handle(Exception exception, String operationName, Object context) {
        LOGGER.error("OutOfMemoryError in operation '{}': {}", operationName, exception.getMessage());
        
        // 内存溢出严重，需要立即报警
        return ExceptionHandlingResult.builder()
            .action(ExceptionAction.LOG_AND_ALERT)
            .severity(ExceptionSeverity.CRITICAL)
            .message("Out of memory in " + operationName)
            .recoverable(false)
            .recoveryAction(() -> performMemoryCleanup())
            .build();
    }
    
    private void performMemoryCleanup() {
        LOGGER.info("Performing emergency memory cleanup");
        try {
            // 强制GC
            System.gc();
            
            // 清理缓存
            IntelligentCacheManager.emergencyCleanup();
            
            // 清理对象池
            ObjectPoolManager.emergencyCleanup();
            
        } catch (Exception e) {
            LOGGER.error("Memory cleanup failed", e);
        }
    }
}

/**
 * 通用异常处理器
 */
public class GenericExceptionHandler implements ExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericExceptionHandler.class);
    
    @Override
    public ExceptionHandlingResult handle(Exception exception, String operationName, Object context) {
        LOGGER.error("Unexpected exception in operation '{}': {}", operationName, exception.getMessage(), exception);
        
        // 未知异常采用保守策略
        return ExceptionHandlingResult.builder()
            .action(ExceptionAction.LOG_AND_CONTINUE)
            .severity(ExceptionSeverity.MEDIUM)
            .message("Unexpected exception in " + operationName + ": " + exception.getClass().getSimpleName())
            .recoverable(true)
            .build();
    }
}
```

### 3. TFI API异常安全包装
```java
/**
 * TFI API的异常安全包装实现
 */
public final class SafeTFIImplementation {
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeTFIImplementation.class);
    
    // 运行模式标志
    private static final AtomicBoolean degradedMode = new AtomicBoolean(false);
    private static final AtomicBoolean disabled = new AtomicBoolean(false);
    
    /**
     * 异常安全的start方法
     */
    public static TaskContext safeStart(String taskName) {
        if (disabled.get()) {
            return createDisabledTaskContext(taskName);
        }
        
        return ExceptionSafeExecutor.safeExecute(
            () -> {
                if (degradedMode.get()) {
                    return createDegradedTaskContext(taskName);
                }
                
                // 正常执行start逻辑
                ContextManager contextManager = ContextManager.getInstance();
                ThreadContext threadContext = contextManager.getOrCreateThreadContext();
                
                if (threadContext.getCurrentSession() == null) {
                    contextManager.startNewSession();
                }
                
                TaskNode taskNode = new TaskNode(taskName, threadContext.getCurrentDepth(), System.nanoTime());
                threadContext.pushTask(taskNode);
                
                return new TaskContextImpl(taskNode, threadContext, null);
            },
            createDisabledTaskContext(taskName), // 异常时的默认返回值
            "TFI.start"
        );
    }
    
    /**
     * 异常安全的stop方法
     */
    public static void safeStop() {
        if (disabled.get()) {
            return;
        }
        
        ExceptionSafeExecutor.safeExecute(
            () -> {
                if (degradedMode.get()) {
                    return; // 降级模式下不执行任何操作
                }
                
                // 正常执行stop逻辑
                ContextManager contextManager = ContextManager.getInstance();
                ThreadContext threadContext = contextManager.getOrCreateThreadContext();
                
                TaskNode currentTask = threadContext.popTask();
                if (currentTask != null) {
                    currentTask.complete(System.nanoTime());
                }
            },
            "TFI.stop"
        );
    }
    
    /**
     * 异常安全的message方法
     */
    public static void safeMessage(String message) {
        if (disabled.get()) {
            return;
        }
        
        ExceptionSafeExecutor.safeExecute(
            () -> {
                if (degradedMode.get()) {
                    // 降级模式下仅输出到日志
                    LOGGER.debug("TFI Message (degraded): {}", message);
                    return;
                }
                
                // 正常执行message逻辑
                ContextManager contextManager = ContextManager.getInstance();
                ThreadContext threadContext = contextManager.getOrCreateThreadContext();
                TaskNode currentTask = threadContext.getCurrentTask();
                
                if (currentTask != null) {
                    Message msg = new Message(message, System.currentTimeMillis());
                    currentTask.addMessage(msg);
                }
            },
            "TFI.message"
        );
    }
    
    /**
     * 异常安全的printTree方法
     */
    public static void safePrintTree() {
        if (disabled.get()) {
            System.out.println("TFI is disabled due to critical errors");
            return;
        }
        
        ExceptionSafeExecutor.safeExecute(
            () -> {
                if (degradedMode.get()) {
                    System.out.println("TFI is in degraded mode - limited functionality");
                    return;
                }
                
                // 正常执行printTree逻辑
                ContextManager contextManager = ContextManager.getInstance();
                ThreadContext threadContext = contextManager.getOrCreateThreadContext();
                Session currentSession = threadContext.getCurrentSession();
                
                if (currentSession != null && currentSession.getRoot() != null) {
                    ConsoleTreeOutputRenderer renderer = new ConsoleTreeOutputRenderer();
                    renderer.render(currentSession);
                } else {
                    System.out.println("No active session to display");
                }
            },
            "TFI.printTree"
        );
    }
    
    /**
     * 异常安全的exportJson方法
     */
    public static String safeExportJson() {
        if (disabled.get()) {
            return "{\"status\":\"disabled\",\"reason\":\"TFI disabled due to critical errors\"}";
        }
        
        return ExceptionSafeExecutor.safeExecute(
            () -> {
                if (degradedMode.get()) {
                    return "{\"status\":\"degraded\",\"reason\":\"TFI in degraded mode\"}";
                }
                
                // 正常执行exportJson逻辑
                ContextManager contextManager = ContextManager.getInstance();
                ThreadContext threadContext = contextManager.getOrCreateThreadContext();
                Session currentSession = threadContext.getCurrentSession();
                
                if (currentSession != null) {
                    JsonExporter exporter = new JsonExporter();
                    return exporter.export(currentSession);
                } else {
                    return "{\"status\":\"no_session\",\"message\":\"No active session\"}";
                }
            },
            "{\"status\":\"error\",\"message\":\"Export failed due to internal error\"}", // 异常时的默认返回值
            "TFI.exportJson"
        );
    }
    
    /**
     * 创建禁用状态的TaskContext
     */
    private static TaskContext createDisabledTaskContext(String taskName) {
        return new DisabledTaskContext(taskName);
    }
    
    /**
     * 创建降级模式的TaskContext
     */
    private static TaskContext createDegradedTaskContext(String taskName) {
        return new DegradedTaskContext(taskName);
    }
    
    /**
     * 启用降级模式
     */
    public static void enableDegradedMode() {
        degradedMode.set(true);
        LOGGER.warn("TFI has been switched to degraded mode");
    }
    
    /**
     * 禁用TFI
     */
    public static void disable() {
        disabled.set(true);
        LOGGER.error("TFI has been disabled due to critical errors");
    }
    
    /**
     * 恢复正常运行
     */
    public static void recover() {
        degradedMode.set(false);
        disabled.set(false);
        LOGGER.info("TFI has recovered to normal mode");
    }
    
    /**
     * 获取当前运行状态
     */
    public static TFIStatus getStatus() {
        if (disabled.get()) {
            return TFIStatus.DISABLED;
        } else if (degradedMode.get()) {
            return TFIStatus.DEGRADED;
        } else {
            return TFIStatus.NORMAL;
        }
    }
}

/**
 * TFI运行状态枚举
 */
public enum TFIStatus {
    NORMAL,     // 正常运行
    DEGRADED,   // 降级模式
    DISABLED    // 已禁用
}
```

### 4. 降级模式TaskContext实现
```java
/**
 * 降级模式TaskContext - 提供有限功能，确保不影响业务
 */
public class DegradedTaskContext implements TaskContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(DegradedTaskContext.class);
    
    private final String taskName;
    private final long startTime;
    private volatile boolean closed = false;
    
    public DegradedTaskContext(String taskName) {
        this.taskName = taskName;
        this.startTime = System.nanoTime();
        LOGGER.debug("Created degraded TaskContext for task: {}", taskName);
    }
    
    @Override
    public String getTaskId() {
        return "degraded-" + taskName.hashCode();
    }
    
    @Override
    public String getTaskName() {
        return taskName;
    }
    
    @Override
    public int getDepth() {
        return 0; // 降级模式下深度固定为0
    }
    
    @Override
    public TaskStatus getStatus() {
        return closed ? TaskStatus.COMPLETED : TaskStatus.RUNNING;
    }
    
    @Override
    public long getStartTime() {
        return startTime;
    }
    
    @Override
    public long getEndTime() {
        return closed ? System.nanoTime() : 0;
    }
    
    @Override
    public long getDuration() {
        return System.nanoTime() - startTime;
    }
    
    @Override
    public TaskContext message(String message) {
        LOGGER.debug("Degraded message for task '{}': {}", taskName, message);
        return this;
    }
    
    @Override
    public TaskContext message(String format, Object... args) {
        try {
            String formattedMessage = String.format(format, args);
            return message(formattedMessage);
        } catch (Exception e) {
            return message("Failed to format message: " + format);
        }
    }
    
    @Override
    public TaskContext startSubTask(String subTaskName) {
        LOGGER.debug("Creating degraded subtask '{}' for parent '{}'", subTaskName, taskName);
        return new DegradedTaskContext(subTaskName);
    }
    
    @Override
    public void end() {
        closed = true;
        LOGGER.debug("Ended degraded task: {}", taskName);
    }
    
    @Override
    public void close() {
        end();
    }
    
    @Override
    public TaskContext getParent() {
        return null;
    }
    
    @Override
    public List<TaskContext> getChildren() {
        return Collections.emptyList();
    }
    
    @Override
    public List<Message> getMessages() {
        return Collections.emptyList();
    }
}

/**
 * 禁用状态TaskContext - 完全不执行任何操作
 */
public class DisabledTaskContext implements TaskContext {
    private final String taskName;
    
    public DisabledTaskContext(String taskName) {
        this.taskName = taskName;
    }
    
    @Override
    public String getTaskId() {
        return "disabled";
    }
    
    @Override
    public String getTaskName() {
        return taskName;
    }
    
    @Override
    public int getDepth() {
        return 0;
    }
    
    @Override
    public TaskStatus getStatus() {
        return TaskStatus.COMPLETED;
    }
    
    @Override
    public long getStartTime() {
        return 0;
    }
    
    @Override
    public long getEndTime() {
        return 0;
    }
    
    @Override
    public long getDuration() {
        return 0;
    }
    
    @Override
    public TaskContext message(String message) {
        return this; // 不执行任何操作
    }
    
    @Override
    public TaskContext message(String format, Object... args) {
        return this; // 不执行任何操作
    }
    
    @Override
    public TaskContext startSubTask(String subTaskName) {
        return new DisabledTaskContext(subTaskName);
    }
    
    @Override
    public void end() {
        // 不执行任何操作
    }
    
    @Override
    public void close() {
        // 不执行任何操作
    }
    
    @Override
    public TaskContext getParent() {
        return null;
    }
    
    @Override
    public List<TaskContext> getChildren() {
        return Collections.emptyList();
    }
    
    @Override
    public List<Message> getMessages() {
        return Collections.emptyList();
    }
}
```

## 关键特性

### 异常安全特性
1. **异常隔离特性**：TFI内部异常不会影响业务逻辑
2. **优雅降级特性**：异常发生时优雅降级
3. **自动恢复特性**：具备自动恢复能力
4. **监控报警特性**：异常监控和报警机制

### 异常处理策略
1. **异常分类策略**：按异常类型分类处理
2. **异常恢复策略**：自动恢复异常状态
3. **异常统计策略**：异常发生频次统计
4. **异常监听策略**：异常监听器链式处理

### 性能保证特性
1. **正常运行性能**：异常安全机制不影响正常性能
2. **异常处理性能**：异常处理开销小于1%
3. **内存占用保证**：异常安全机制内存占用小于1MB
4. **并发安全保证**：多线程环境下的异常安全

## 验收标准

### 功能验收
- [ ] 异常安全执行器功能完整
- [ ] 所有TFI API都实现异常安全包装
- [ ] 降级模式和禁用模式正常工作
- [ ] 异常分类和处理策略正确

### 健壮性验收
- [ ] TFI内部异常100%被正确处理
- [ ] 任何内部异常都不影响业务逻辑
- [ ] 自动恢复机制正常工作
- [ ] 监控报警机制能够正常工作

### 性能验收
- [ ] 正常运行时性能影响小于1%
- [ ] 异常处理开销小于1毫秒
- [ ] 异常安全机制内存占用小于1MB
- [ ] 并发场景下异常安全机制稳定

## 依赖关系

### 前置依赖
- TASK-010: TFI核心接口定义
- TASK-011: TaskContext接口实现
- TASK-007: ContextManager上下文管理器

### 后置依赖
- TASK-013: API接口单元测试

## 开发计划

### 分阶段开发计划
- **Day 1**: 异常安全执行器和异常处理器接口
- **Day 2**: TFI API异常安全包装实现
- **Day 3**: 降级模式和异常安全特性完善

## 风险评估

### 技术风险
1. **复杂度风险**：异常处理逻辑复杂，可能引入新问题
   - 缓解措施：充分的异常处理策略测试
2. **性能风险**：异常安全机制可能影响性能
   - 缓解措施：性能基准测试和优化

### 业务风险
1. **异常遗漏**：可能遗漏某些异常类型的处理
   - 缓解措施：全面的异常类型分析和测试
2. **过度保护**：异常安全可能过度影响正常功能
   - 缓解措施：精确的异常范围控制

## 实现文件

1. **异常安全执行器** (`ExceptionSafeExecutor.java`)
2. **异常处理器接口** (`*ExceptionHandler.java`)
3. **安全TFI API包装** (`SafeTFIImplementation.java`)
4. **降级TaskContext** (`DegradedTaskContext.java`, `DisabledTaskContext.java`)
5. **异常安全特性测试** (`ExceptionSafetyTest.java`)
6. **异常安全文档** (`exception-handling-guide.md`)