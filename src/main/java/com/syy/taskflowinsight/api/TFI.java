package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.core.TfiCore;
import com.syy.taskflowinsight.config.TfiConfig;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.exporter.change.ChangeExporter;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * TaskFlow Insight 主API门面类
 * 
 * 设计原则：
 * - 薄门面：仅作为静态方法接口，委托给现有实现
 * - 异常安全：门面层捕获所有异常，使用SLF4J记录
 * - API完整：提供完整方法集，为未来扩展预留接口
 * - 性能优先：禁用状态快速返回，避免不必要操作
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public final class TFI {
    
    private static final Logger logger = LoggerFactory.getLogger(TFI.class);
    
    // 核心服务引用（唯一的静态字段，通过ApplicationReadyEvent注入或懒加载兜底）
    private static volatile TfiCore core;
    
    // 懒加载标记，确保兜底初始化只执行一次
    private static volatile boolean fallbackInitialized = false;
    
    /**
     * 内部注入器类
     * 用于在Spring启动完成后注入TfiCore实例
     */
    @Component
    static class CoreInjector implements ApplicationListener<ApplicationReadyEvent> {
        @Autowired
        private TfiCore tfiCore;
        
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            TFI.core = tfiCore;
            logger.info("TfiCore injected into TFI facade at application ready");
        }
    }
    
    /**
     * 私有构造函数，防止实例化
     */
    private TFI() {
        throw new UnsupportedOperationException("TFI is a utility class");
    }
    
    // ==================== 系统控制方法 ====================
    
    /**
     * 启用 TaskFlow Insight 系统
     */
    public static void enable() {
        try {
            ensureCoreInitialized();
            if (core != null) {
                core.enable();
            }
        } catch (Throwable t) {
            handleInternalError("Failed to enable TFI", t);
        }
    }
    
    /**
     * 禁用 TaskFlow Insight 系统
     */
    public static void disable() {
        try {
            ensureCoreInitialized();
            if (core != null) {
                core.disable();
            }
        } catch (Throwable t) {
            handleInternalError("Failed to disable TFI", t);
        }
    }
    
    /**
     * 检查系统是否启用
     * 
     * @return true 如果系统已启用
     */
    public static boolean isEnabled() {
        ensureCoreInitialized();
        return core != null && core.isEnabled();
    }
    
    /**
     * 清理当前线程的所有上下文
     */
    public static void clear() {
        if (!isEnabled()) {
            return;
        }
        
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.close();
            }
        } catch (Throwable t) {
            handleInternalError("Failed to clear context", t);
        }
    }
    
    // ==================== 会话管理方法 ====================
    
    /**
     * 开始一个新的会话
     * 
     * @param sessionName 会话名称
     * @return 会话ID，如果失败返回null
     */
    public static String startSession(String sessionName) {
        if (!isEnabled()) {
            return null;
        }
        
        try {
            if (sessionName == null || sessionName.trim().isEmpty()) {
                return null;
            }
            
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                context = ManagedThreadContext.create(sessionName.trim());
                // create已经创建了session，直接返回
                Session session = context.getCurrentSession();
                return session != null ? session.getSessionId() : null;
            } else {
                // 如果已经有context，检查是否有活动会话
                if (context.getCurrentSession() != null && context.getCurrentSession().isActive()) {
                    // 如果有活动会话，先结束它
                    context.endSession();
                }
                Session session = context.startSession(sessionName.trim());
                return session != null ? session.getSessionId() : null;
            }
        } catch (Throwable t) {
            handleInternalError("Failed to start session: " + sessionName, t);
            return null;
        }
    }
    
    /**
     * 结束当前会话
     * 会清理所有变更追踪数据
     */
    public static void endSession() {
        if (!isEnabled()) {
            return;
        }
        
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endSession();
            }
        } catch (Throwable t) {
            handleInternalError("Failed to end session", t);
        } finally {
            // 清理变更追踪数据（三处清理之一）
            if (isChangeTrackingEnabled()) {
                try {
                    ChangeTracker.clearAllTracking();
                } catch (Throwable t) {
                    handleInternalError("Failed to clear tracking in endSession", t);
                }
            }
        }
    }
    
    // ==================== 任务管理方法 ====================
    
    /**
     * 创建一个Stage（AutoCloseable任务块）
     * 
     * <p>这是一个简化的任务创建API，专为try-with-resources设计：
     * <pre>{@code
     * try (var stage = TFI.stage("数据处理")) {
     *     stage.message("开始处理");
     *     // 业务逻辑
     *     stage.success();
     * } // 自动调用close()结束任务
     * }</pre>
     * 
     * @param stageName Stage名称
     * @return TaskContext 支持AutoCloseable的任务上下文
     * @since 3.0.0
     */
    public static TaskContext stage(String stageName) {
        return start(stageName);  // 复用现有start()逻辑
    }
    
    /**
     * 在Stage中执行操作（函数式API）
     * 
     * <p>提供函数式编程风格的Stage API：
     * <pre>{@code
     * TFI.stage("数据处理", stage -> {
     *     stage.message("开始处理");
     *     // 业务逻辑
     *     return "处理结果";
     * });
     * }</pre>
     * 
     * @param stageName Stage名称
     * @param stageFunction 要在Stage中执行的函数
     * @param <T> 返回值类型
     * @return 执行结果，如果失败返回null
     * @since 3.0.0
     */
    public static <T> T stage(String stageName, StageFunction<T> stageFunction) {
        if (!isEnabled() || stageFunction == null) {
            try {
                return stageFunction != null ? stageFunction.apply(NullTaskContext.INSTANCE) : null;
            } catch (Exception e) {
                return null;
            }
        }
        
        try (TaskContext stage = start(stageName)) {
            return stageFunction.apply(stage);
        } catch (Throwable t) {
            handleInternalError("Failed to execute stage: " + stageName, t);
            return null;
        }
    }
    
    /**
     * 开始一个新任务
     * 
     * @param taskName 任务名称
     * @return TaskContext 任务上下文，支持链式调用
     */
    public static TaskContext start(String taskName) {
        if (!isEnabled()) {
            return NullTaskContext.INSTANCE;
        }
        
        try {
            if (taskName == null || taskName.trim().isEmpty()) {
                return NullTaskContext.INSTANCE;
            }
            
            // 如果没有上下文，自动创建一个
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                context = ManagedThreadContext.create("auto-session");
            }
            
            // 如果没有会话，自动创建一个
            if (context.getCurrentSession() == null) {
                context.startSession("auto-session");
            }
            
            TaskNode taskNode = context.startTask(taskName.trim());
            return taskNode != null ? new TaskContextImpl(taskNode) : NullTaskContext.INSTANCE;
            
        } catch (Throwable t) {
            handleInternalError("Failed to start task: " + taskName, t);
            return NullTaskContext.INSTANCE;
        }
    }
    
    /**
     * 结束当前任务
     * 在结束任务前，会刷新所有变更记录到当前任务节点
     */
    public static void stop() {
        if (!isEnabled()) {
            return;
        }
        
        try {
            // 先刷新变更记录（如果启用了变更追踪）
            if (isChangeTrackingEnabled()) {
                flushChangesToCurrentTask();
            }
            
            // 再结束任务
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endTask();
            }
        } catch (Throwable t) {
            handleInternalError("Failed to stop task", t);
        } finally {
            // 清理变更追踪数据
            if (isChangeTrackingEnabled()) {
                try {
                    ChangeTracker.clearAllTracking();
                } catch (Throwable t) {
                    handleInternalError("Failed to clear tracking in stop", t);
                }
            }
        }
    }
    
    /**
     * 在新任务中执行操作
     * 
     * @param taskName 任务名称
     * @param runnable 要执行的操作
     */
    public static void run(String taskName, Runnable runnable) {
        if (!isEnabled() || runnable == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        
        try (TaskContext context = start(taskName)) {
            runnable.run();
        } catch (Throwable t) {
            handleInternalError("Failed to run task: " + taskName, t);
        }
    }
    
    /**
     * 在新任务中执行操作并返回结果
     * 
     * @param taskName 任务名称
     * @param callable 要执行的操作
     * @param <T> 返回值类型
     * @return 执行结果，如果失败返回null
     */
    public static <T> T call(String taskName, Callable<T> callable) {
        if (!isEnabled() || callable == null) {
            try {
                return callable != null ? callable.call() : null;
            } catch (Exception e) {
                return null;
            }
        }
        
        try (TaskContext context = start(taskName)) {
            return callable.call();
        } catch (Throwable t) {
            handleInternalError("Failed to call task: " + taskName, t);
            return null;
        }
    }
    
    // ==================== 消息记录方法 ====================

    /**
     * 记录指定类型的消息
     * 
     * @param content 消息内容
     * @param messageType 消息类型
     */
    public static void message(String content, MessageType messageType) {
        if (!isEnabled() || content == null || content.trim().isEmpty() || messageType == null) {
            return;
        }
        
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && context.getCurrentTask() != null) {
                TaskNode task = context.getCurrentTask();
                task.addMessage(content.trim(), messageType);
            }
        } catch (Throwable t) {
            handleInternalError("Failed to record message with type", t);
        }
    }
    
    /**
     * 记录自定义标签消息
     * 
     * @param content 消息内容
     * @param customLabel 自定义标签
     */
    public static void message(String content, String customLabel) {
        if (!isEnabled() || content == null || content.trim().isEmpty() || 
            customLabel == null || customLabel.trim().isEmpty()) {
            return;
        }
        
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && context.getCurrentTask() != null) {
                TaskNode task = context.getCurrentTask();
                task.addMessage(content.trim(), customLabel.trim());
            }
        } catch (Throwable t) {
            handleInternalError("Failed to record message with custom label", t);
        }
    }
    
    /**
     * 记录错误消息
     * 
     * @param content 错误消息内容
     */
    public static void error(String content) {
        if (!isEnabled() || content == null || content.trim().isEmpty()) {
            return;
        }
        
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && context.getCurrentTask() != null) {
                TaskNode task = context.getCurrentTask();
                task.addMessage(content.trim(), MessageType.ALERT);
            }
        } catch (Throwable t) {
            handleInternalError("Failed to record error", t);
        }
    }
    
    /**
     * 记录错误消息和异常
     * 
     * @param content 错误消息内容
     * @param throwable 异常对象
     */
    public static void error(String content, Throwable throwable) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String errorMessage = content;
            if (throwable != null) {
                errorMessage = content + " - " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            }
            
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && context.getCurrentTask() != null) {
                TaskNode task = context.getCurrentTask();
                task.addMessage(errorMessage, MessageType.ALERT);
            }
        } catch (Throwable t) {
            handleInternalError("Failed to record error with exception", t);
        }
    }
    
    // ==================== 查询方法 ====================
    
    /**
     * 获取当前会话
     * 
     * @return 当前会话，如果没有返回null
     */
    public static Session getCurrentSession() {
        if (!isEnabled()) {
            return null;
        }
        
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            return context != null ? context.getCurrentSession() : null;
        } catch (Throwable t) {
            handleInternalError("Failed to get current session", t);
            return null;
        }
    }
    
    /**
     * 获取当前任务
     * 
     * @return 当前任务节点，如果没有返回null
     */
    public static TaskNode getCurrentTask() {
        if (!isEnabled()) {
            return null;
        }
        
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            return context != null ? context.getCurrentTask() : null;
        } catch (Throwable t) {
            handleInternalError("Failed to get current task", t);
            return null;
        }
    }
    
    /**
     * 获取任务堆栈
     * 
     * @return 任务堆栈列表，如果没有返回空列表
     */
    public static List<TaskNode> getTaskStack() {
        if (!isEnabled()) {
            return List.of();
        }
        
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                List<TaskNode> stack = new ArrayList<>();
                // 手动构建任务栈列表
                TaskNode current = context.getCurrentTask();
                while (current != null) {
                    stack.add(0, current);
                    current = current.getParent();
                }
                return stack;
            }
            return List.of();
        } catch (Throwable t) {
            handleInternalError("Failed to get task stack", t);
            return List.of();
        }
    }
    
    // ==================== 变更追踪方法 ====================
    
    /**
     * 追踪对象的变更
     * 捕获对象的当前状态作为基线，后续可通过getChanges获取变更
     * 
     * @param name 对象名称（用于标识）
     * @param target 要追踪的对象
     * @param fields 要追踪的字段名，如果为空则追踪所有标量字段
     */
    public static void track(String name, Object target, String... fields) {
        // 快速状态检查
        if (!checkEnabled(true)) {
            return;
        }
        
        // 参数验证
        if (name == null || name.trim().isEmpty()) {
            logger.debug("Invalid tracking name: null or empty");
            return;
        }
        if (target == null) {
            logger.debug("Invalid tracking target: null for name '{}'", name);
            return;
        }
        
        try {
            ChangeTracker.track(name.trim(), target, fields);
            logger.debug("Started tracking object '{}' with {} fields", name.trim(), 
                fields != null ? fields.length : "all");
        } catch (ChangeTracker.TrackingException trackingError) {
            handleInternalError("Tracking failed for object: " + name, trackingError, ErrorLevel.WARN);
        } catch (OutOfMemoryError memError) {
            handleInternalError("Out of memory while tracking object: " + name, memError, ErrorLevel.FATAL);
        } catch (Throwable t) {
            handleInternalError("Unexpected error tracking object: " + name, t, ErrorLevel.ERROR);
        }
    }
    
    /**
     * 批量追踪多个对象（优化版）
     * 支持分批处理大数据集，提升性能
     * 
     * @param targets 对象名称到对象的映射
     */
    public static void trackAll(Map<String, Object> targets) {
        // 快速状态检查
        if (!checkEnabled(true)) {
            return;
        }
        
        if (targets == null || targets.isEmpty()) {
            logger.debug("Invalid targets for batch tracking: null or empty");
            return;
        }
        
        try {
            int batchSize = 50; // 优化的批次大小
            int totalSize = targets.size();
            
            // 大批量警告
            if (totalSize > 100) {
                logger.warn("Large batch tracking request: {} objects (will process in batches)", totalSize);
            }
            
            // 分批处理优化
            if (totalSize > batchSize) {
                long startTime = System.nanoTime();
                int processed = 0;
                Map<String, Object> batch = new java.util.HashMap<>(batchSize);
                
                for (Map.Entry<String, Object> entry : targets.entrySet()) {
                    batch.put(entry.getKey(), entry.getValue());
                    
                    if (batch.size() >= batchSize) {
                        // 处理当前批次
                        ChangeTracker.trackAll(batch);
                        processed += batch.size();
                        batch.clear();
                        
                        // 防止长时间占用CPU
                        if (processed % 200 == 0) {
                            Thread.yield();
                        }
                    }
                }
                
                // 处理剩余的对象
                if (!batch.isEmpty()) {
                    ChangeTracker.trackAll(batch);
                    processed += batch.size();
                }
                
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("Batch tracking completed: {} objects in {}ms ({} obj/ms)", 
                    processed, durationMs, 
                    durationMs > 0 ? processed / durationMs : processed);
            } else {
                // 小批量直接处理
                ChangeTracker.trackAll(targets);
                logger.debug("Started batch tracking for {} objects", totalSize);
            }
            
        } catch (ChangeTracker.TrackingException trackingError) {
            handleInternalError("Batch tracking failed for " + targets.size() + " objects", 
                trackingError, ErrorLevel.WARN);
        } catch (OutOfMemoryError memError) {
            handleInternalError("Out of memory during batch tracking", memError, ErrorLevel.FATAL);
        } catch (Throwable t) {
            handleInternalError("Unexpected error in batch tracking", t, ErrorLevel.ERROR);
        }
    }
    
    /**
     * 获取所有追踪对象的变更记录
     * 捕获当前状态，与基线对比，返回增量变更，并更新基线
     * 
     * @return 变更记录列表，如果禁用或失败返回空列表
     */
    public static List<ChangeRecord> getChanges() {
        // 快速状态检查
        if (!checkEnabled(true)) {
            return List.of();
        }
        
        try {
            List<ChangeRecord> changes = ChangeTracker.getChanges();
            if (logger.isDebugEnabled()) {
                logger.debug("Retrieved {} change records from {} tracked objects", 
                    changes.size(), ChangeTracker.getTrackedCount());
            }
            return changes;
        } catch (ChangeTracker.TrackingException trackingError) {
            handleInternalError("Change detection failed", trackingError, ErrorLevel.WARN);
            return List.of();
        } catch (OutOfMemoryError memError) {
            handleInternalError("Out of memory during change detection", memError, ErrorLevel.FATAL);
            return List.of();
        } catch (Throwable t) {
            handleInternalError("Unexpected error getting changes", t, ErrorLevel.ERROR);
            return List.of();
        }
    }
    
    /**
     * 获取所有变更记录
     * 
     * @return 所有变更记录列表
     */
    public static List<ChangeRecord> getAllChanges() {
        return getChanges();
    }
    
    /**
     * 开始追踪对象
     * 
     * @param name 对象名称
     */
    public static void startTracking(String name) {
        // 简化版本，创建一个空对象来追踪
        track(name, new Object());
    }
    
    /**
     * 手动记录变更
     * 
     * @param objectName 对象名称
     * @param fieldName 字段名称
     * @param oldValue 旧值
     * @param newValue 新值
     * @param changeType 变更类型
     */
    public static void recordChange(String objectName, String fieldName, Object oldValue, Object newValue, ChangeType changeType) {
        if (!isChangeTrackingEnabled()) {
            return;
        }
        
        try {
            // 手动创建变更记录
            ChangeRecord change = ChangeRecord.builder()
                .objectName(objectName)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changeType(changeType)
                .timestamp(System.currentTimeMillis())
                .build();
            
            // 记录到当前任务
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                TaskNode currentTask = context.getCurrentTask();
                if (currentTask != null) {
                    String changeMessage = String.format("%s.%s: %s → %s",
                        objectName, fieldName,
                        formatValueSafe(oldValue),
                        formatValueSafe(newValue));
                    currentTask.addMessage(changeMessage, MessageType.CHANGE);
                }
            }
        } catch (Exception e) {
            handleInternalError("Failed to record manual change", e, ErrorLevel.WARN);
        }
    }
    
    /**
     * 清理特定会话的追踪数据
     * 
     * @param sessionId 会话ID
     */
    public static void clearTracking(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        
        try {
            // 清理指定会话的追踪器
            ChangeTracker.clearBySessionId(sessionId);
            logger.debug("Cleared tracking for session: {}", sessionId);
        } catch (Exception e) {
            handleInternalError("Failed to clear tracking for session " + sessionId, e, ErrorLevel.WARN);
        }
    }
    
    /**
     * 清理当前线程的所有追踪数据
     */
    public static void clearAllTracking() {
        // 状态检查（即使禁用也应该能清理）
        if (!isEnabled()) {
            return;
        }
        
        try {
            int trackedCount = ChangeTracker.getTrackedCount();
            ChangeTracker.clearAllTracking();
            logger.debug("Cleared tracking for {} objects", trackedCount);
        } catch (ChangeTracker.TrackingException trackingError) {
            handleInternalError("Failed to clear tracking data", trackingError, ErrorLevel.WARN);
        } catch (Throwable t) {
            handleInternalError("Unexpected error clearing tracking", t, ErrorLevel.ERROR);
        }
    }
    
    /**
     * 便捷API：自动管理变更追踪的生命周期
     * 在lambda执行前开始追踪，执行后自动获取变更并清理
     * 
     * @param name 对象名称
     * @param target 追踪目标对象
     * @param action 要执行的业务逻辑
     * @param fields 要追踪的字段名列表
     */
    public static void withTracked(String name, Object target, Runnable action, String... fields) {
        if (!isChangeTrackingEnabled()) {
            // 即使禁用追踪，也要执行业务逻辑
            if (action != null) {
                action.run();
            }
            return;
        }
        
        try {
            // 开始追踪
            track(name, target, fields);
            
            // 执行业务逻辑
            if (action != null) {
                action.run();
            }
            
            // 获取变更并记录
            List<ChangeRecord> changes = getChanges();
            if (!changes.isEmpty()) {
                // 将变更记录到当前任务
                for (ChangeRecord change : changes) {
                    String changeMessage = String.format("%s.%s: %s → %s",
                        change.getObjectName(),
                        change.getFieldName(),
                        formatValueSafe(change.getOldValue()),
                        formatValueSafe(change.getNewValue()));
                    message(changeMessage, MessageType.CHANGE);
                }
            }
        } catch (Throwable t) {
            handleInternalError("Failed in withTracked", t);
        } finally {
            // 清理追踪数据
            clearAllTracking();
        }
    }
    
    /**
     * 设置变更追踪功能开关
     * 
     * @param enabled true启用，false禁用
     */
    public static void setChangeTrackingEnabled(boolean enabled) {
        ensureCoreInitialized();
        if (core != null) {
            core.setChangeTrackingEnabled(enabled);
        }
    }
    
    /**
     * 检查变更追踪功能是否启用
     * 
     * @return true如果启用
     */
    public static boolean isChangeTrackingEnabled() {
        return core != null && core.isChangeTrackingEnabled();
    }
    
    // ==================== 导出方法 ====================
    
    /**
     * 导出当前会话到控制台（默认简化输出，无时间戳）
     */
    public static void exportToConsole() {
        exportToConsole(false);
    }

    /**
     * 导出当前会话的任务树到控制台
     *
     * @param showTimestamp 是否显示时间戳
     * @return
     */
    public static boolean exportToConsole(boolean showTimestamp) {
        if (!isEnabled()) {
            return showTimestamp;
        }

        try {
            Session session = getCurrentSession();
            if (session != null) {
                ConsoleExporter exporter = new ConsoleExporter();
                if (showTimestamp) {
                    exporter.print(session);
                } else {
                    exporter.printSimple(session);
                }
            }
        } catch (Throwable t) {
            handleInternalError("Failed to export to console", t);
        }
        return showTimestamp;
    }

    /**
     * 导出当前会话为JSON
     *
     * @return JSON字符串，如果失败返回null
     */
    public static String exportToJson() {
        if (!isEnabled()) {
            return null;
        }

        try {
            Session session = getCurrentSession();
            if (session != null) {
                JsonExporter exporter = new JsonExporter();
                return exporter.export(session);
            }
            return null;
        } catch (Throwable t) {
            handleInternalError("Failed to export to JSON", t);
            return null;
        }
    }

    /**
     * 导出当前会话为Map
     * 
     * @return Map对象，如果失败返回空Map
     */
    public static Map<String, Object> exportToMap() {
        if (!isEnabled()) {
            return Map.of();
        }
        
        try {
            Session session = getCurrentSession();
            return session != null ? MapExporter.export(session) : Map.of();
        } catch (Throwable t) {
            handleInternalError("Failed to export to Map", t);
            return Map.of();
        }
    }
    
    // ==================== 内部辅助方法 ====================
    
    /**
     * 确保TfiCore已初始化（懒加载兜底机制）
     * 
     * 在非Spring环境下，如果core为null，则使用默认配置创建TfiCore实例
     * 此方法是线程安全的，只会初始化一次
     * 不会影响Spring正常的注入流程
     */
    private static void ensureCoreInitialized() {
        // 快速检查（避免同步开销）
        if (core != null) {
            return;
        }
        
        // 双重检查锁定模式
        synchronized (TFI.class) {
            if (core == null && !fallbackInitialized) {
                try {
                    // 创建默认配置（与TaskFlowInsightDemo中的保持一致）
                    TfiConfig defaultConfig = new TfiConfig(
                        true, // 启用TFI
                        new TfiConfig.ChangeTracking(true, null, null, null, null, null, null, null), // 启用变更追踪
                        new TfiConfig.Context(null, null, null, null, null),
                        new TfiConfig.Metrics(null, null, null),
                        new TfiConfig.Security(null, null)
                    );
                    
                    // 创建TfiCore实例
                    core = new TfiCore(defaultConfig);
                    fallbackInitialized = true;
                    
                    logger.info("TFI fallback initialization completed - running in non-Spring mode with default configuration");
                    
                } catch (Exception e) {
                    fallbackInitialized = true; // 避免重复尝试
                    logger.error("TFI fallback initialization failed: {}", e.getMessage());
                    if (logger.isDebugEnabled()) {
                        logger.debug("Fallback initialization error details", e);
                    }
                }
            }
        }
    }
    
    /**
     * 刷新变更记录到当前任务（增强版）
     * 获取所有变更，使用统一导出器格式化，批量写入当前任务节点
     * 支持敏感信息处理和性能监控
     */
    private static void flushChangesToCurrentTask() {
        long startTime = System.nanoTime();
        int processedChanges = 0;
        
        try {
            // 1. 获取变更记录
            List<ChangeRecord> changes = ChangeTracker.getChanges();
            
            if (changes.isEmpty()) {
                logger.debug("No changes to flush");
                return;
            }
            
            // 2. 验证上下文
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                logger.warn("No thread context available for flushing changes");
                return;
            }
            
            TaskNode currentTask = context.getCurrentTask();
            if (currentTask == null) {
                logger.warn("No current task to flush {} changes to", changes.size());
                return;
            }
            
            // 3. 批量处理变更记录
            try {
                // 使用ChangeConsoleExporter格式化（与Round 3一致）
                ChangeConsoleExporter exporter = new ChangeConsoleExporter();
                ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
                config.setIncludeSensitiveInfo(false); // 默认脱敏敏感信息
                config.setShowTimestamp(false); // 任务节点有自己的时间戳
                
                // 单独格式化每个变更以便逐条添加
                for (ChangeRecord change : changes) {
                    try {
                        // 使用统一格式化器
                        String formattedMessage = formatSingleChange(change, exporter, config);
                        currentTask.addMessage(formattedMessage, MessageType.CHANGE);
                        processedChanges++;
                    } catch (Exception e) {
                        logger.warn("Failed to format change record {}.{}: {}", 
                            change.getObjectName(), change.getFieldName(), e.getMessage());
                        // 降级处理：使用简单格式
                        String fallbackMessage = formatChangeMessageFallback(change);
                        currentTask.addMessage(fallbackMessage, MessageType.CHANGE);
                        processedChanges++;
                    }
                }
                
                // 4. 记录统计信息
                long durationNanos = System.nanoTime() - startTime;
                logger.debug("Flushed {} change records to task '{}' in {}μs", 
                    processedChanges, currentTask.getTaskName(), durationNanos / 1000);
                
            } catch (Exception batchError) {
                logger.error("Batch processing failed for {} changes: {}", 
                    changes.size(), batchError.getMessage());
                
                // 降级到最简单的处理方式
                for (ChangeRecord change : changes) {
                    try {
                        String simpleMessage = formatChangeMessageFallback(change);
                        currentTask.addMessage(simpleMessage, MessageType.CHANGE);
                        processedChanges++;
                    } catch (Exception individualError) {
                        logger.error("Critical: Failed to record individual change {}.{}: {}", 
                            change.getObjectName(), change.getFieldName(), individualError.getMessage());
                    }
                }
            }
            
        } catch (ChangeTracker.TrackingException trackingError) {
            // 变更追踪层面的异常
            logger.error("ChangeTracker error during flush: {}", trackingError.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("ChangeTracker error details", trackingError);
            }
        } catch (Exception criticalError) {
            // 其他关键错误
            logger.error("Critical error in flushChangesToCurrentTask: {}", criticalError.getMessage());
            if (logger.isDebugEnabled() || Boolean.getBoolean("tfi.debug")) {
                logger.error("Critical error details", criticalError);
            }
        } finally {
            // 确保有处理过的变更数量记录
            if (processedChanges > 0) {
                long totalDurationNanos = System.nanoTime() - startTime;
                logger.debug("Flush operation completed: {}/{} changes in {}μs", 
                    processedChanges, ChangeTracker.getTrackedCount(), totalDurationNanos / 1000);
            }
        }
    }
    
    /**
     * 使用统一导出器格式化单个变更
     */
    private static String formatSingleChange(ChangeRecord change, ChangeConsoleExporter exporter, 
                                           ChangeExporter.ExportConfig config) {
        // 创建单个变更的列表用于格式化
        List<ChangeRecord> singleChangeList = List.of(change);
        String fullOutput = exporter.format(singleChangeList, config);
        
        // 提取单行变更信息（去除头部和统计信息）
        String[] lines = fullOutput.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && 
                !line.startsWith("===") && 
                !line.startsWith("Total changes:") &&
                !line.startsWith("Summary:")) {
                return line;
            }
        }
        
        // 如果解析失败，使用备用格式
        return formatChangeMessageFallback(change);
    }
    
    /**
     * 备用变更格式化方法（最简格式，确保稳定）
     */
    private static String formatChangeMessageFallback(ChangeRecord change) {
        return String.format("%s.%s: %s → %s",
            change.getObjectName() != null ? change.getObjectName() : "Unknown",
            change.getFieldName() != null ? change.getFieldName() : "field",
            formatValueSafe(change.getOldValue()),
            formatValueSafe(change.getNewValue()));
    }
    
    /**
     * 安全值格式化（防止异常）
     */
    private static String formatValueSafe(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return ObjectSnapshot.repr(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
    
    
    /**
     * 错误级别枚举
     */
    private enum ErrorLevel {
        WARN,    // 非关键错误，不影响核心功能
        ERROR,   // 重要错误，影响当前操作
        FATAL    // 严重错误，可能影响系统稳定性
    }
    
    /**
     * 处理内部错误（增强版）
     * 根据异常类型和操作重要性分级处理
     * 
     * @param operation 操作描述
     * @param throwable 异常对象
     */
    static void handleInternalError(String operation, Throwable throwable) {
        handleInternalError(operation, throwable, ErrorLevel.ERROR);
    }
    
    /**
     * 处理内部错误（带级别）
     * 
     * @param operation 操作描述
     * @param throwable 异常对象
     * @param level 错误级别
     */
    static void handleInternalError(String operation, Throwable throwable, ErrorLevel level) {
        String errorMessage = String.format("[TFI-%s] %s: %s", 
            level, 
            operation,
            throwable.getMessage() != null ? throwable.getMessage() : 
            throwable.getClass().getSimpleName());
        
        // 根据级别选择日志方法
        switch (level) {
            case WARN:
                logger.warn(errorMessage);
                if (logger.isDebugEnabled()) {
                    logger.debug("Warning details", throwable);
                }
                break;
            case ERROR:
                logger.error(errorMessage);
                if (logger.isDebugEnabled() || Boolean.getBoolean("tfi.debug")) {
                    logger.error("Error details", throwable);
                }
                break;
            case FATAL:
                logger.error(errorMessage);
                logger.error("Fatal error details", throwable); // 总是记录FATAL的堆栈
                break;
        }
        
        // 特殊异常类型处理
        if (throwable instanceof OutOfMemoryError) {
            logger.error("[TFI-SYSTEM] OutOfMemoryError detected, consider reducing tracked objects");
        } else if (throwable instanceof StackOverflowError) {
            logger.error("[TFI-SYSTEM] StackOverflowError detected, possible circular reference");
        }
    }
    
    /**
     * 快速禁用状态检查
     * 
     * @return true如果应该继续执行，false如果应该直接返回
     */
    private static boolean checkEnabledQuick() {
        return isChangeTrackingEnabled();
    }
    
    /**
     * 完整状态检查（用于写操作）
     * 
     * @param requireChangeTracking 是否需要变更追踪功能
     * @return true如果应该继续执行
     */
    private static boolean checkEnabled(boolean requireChangeTracking) {
        if (!isEnabled()) {
            return false;
        }
        if (requireChangeTracking && !isChangeTrackingEnabled()) {
            return false;
        }
        return true;
    }
}
