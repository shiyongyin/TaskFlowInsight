package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.core.TfiCore;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExportOptions;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.render.RenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    static final Logger logger = LoggerFactory.getLogger(TFI.class);
    
    // 核心服务引用（唯一的静态字段，通过ApplicationReadyEvent注入或懒加载兜底）
    // 注意：测试通过反射访问这些字段，不可改变字段名或移除
    static volatile TfiCore core;

    // 懒加载标记，确保兜底初始化只执行一次
    static volatile boolean fallbackInitialized = false;

    // ==================== Provider 缓存（v4.0.0 路由机制）====================
    // 缓存 Provider 实例，避免每次调用都查找（P95 < 100ns）

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
     *
     * <p>禁用只停止新数据采集，不改变在途资源的释放责任；因此清理不能受 enabled 门控，
     * 否则线程池中的 Context 会在运行期 disable 后永久驻留。
     */
    public static void clear() {
        try {
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    provider.clear();
                }
            }

            // 兜底实现：始终清理 legacy 上下文，避免路由开关切换导致残留
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

        if (sessionName == null || sessionName.trim().isEmpty()) {
            return null;
        }

        try {
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    return provider.startSession(sessionName.trim());
                }
            }

            // Legacy path (v3.0.0 behavior)
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                context = ManagedThreadContext.create(sessionName.trim());
            } else {
                /*
                 * G2 规定一 Session 一 Context。旧会话结束后旧 Context 已终止，
                 * 下一会话必须创建新 owner，不能复用已注销实例。
                 */
                context.endSession();
                context = ManagedThreadContext.create(sessionName.trim());
            }
            Session session = context.getCurrentSession();
            return session != null ? session.getSessionId() : null;
        } catch (Throwable t) {
            handleInternalError("Failed to start session: " + sessionName, t);
            return null;
        }
    }
    
    /**
     * 结束当前会话
     * 会清理所有变更追踪数据
     *
     * <p>与 {@link #clear()} 一致，禁用只停止采集，不能跳过在途 Context 的终止与注销。
     */
    public static void endSession() {
        try {
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    provider.endSession();
                    return;
                }
            }

            // Legacy path (v3.0.0 behavior)
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endSession();
            }
        } catch (Throwable t) {
            handleInternalError("Failed to end session", t);
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

        if (taskName == null || taskName.trim().isEmpty()) {
            return NullTaskContext.INSTANCE;
        }

        try {
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    // 确保有活动会话
                    if (provider.currentSession() == null) {
                        provider.startSession("auto-session");
                    }
                    TaskNode taskNode = provider.startTask(taskName.trim());
                    // 任务作用域必须保留创建时的 owner；子任务/close 期间 Registry 可能已切换，不能重新解析。
                    return taskNode != null ? new TaskContextImpl(taskNode, provider) : NullTaskContext.INSTANCE;
                }
            }

            // Legacy path (v3.0.0 behavior)
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                context = ManagedThreadContext.create("auto-session");
            }
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
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    provider.endTask();
                    return;
                }
            }

            // Legacy path (v3.0.0 behavior)
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endTask();
            }
        } catch (Throwable t) {
            handleInternalError("Failed to stop task", t);
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
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    provider.messageWithType(content.trim(), messageType);
                    return;
                }
            }

            // Legacy path (v3.0.0 behavior)
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
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    provider.message(content.trim(), customLabel.trim());
                    return;
                }
            }

            // Legacy path (v3.0.0 behavior)
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
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    provider.messageWithType(content.trim(), MessageType.ALERT);
                    return;
                }
            }

            // Legacy path (v3.0.0 behavior)
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

            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    provider.messageWithType(errorMessage, MessageType.ALERT);
                    return;
                }
            }

            // Legacy path (v3.0.0 behavior)
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
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    return provider.currentSession();
                }
            }

            // Legacy path (v3.0.0 behavior)
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
            // v4.0.0 Provider routing
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    return provider.currentTask();
                }
            }

            // Legacy path (v3.0.0 behavior)
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
            // v4.0.0 Provider routing with grayscale control
            {
                FlowProvider provider = getFlowProvider();
                if (provider != null) {
                    return provider.getTaskStack();
                }
            }

            // Legacy path (v3.0.0 behavior)
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                List<TaskNode> stack = new ArrayList<>();
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
     * 创建自定义追踪配置的构建器
     * 
     * @return TrackingOptions构建器
     */
    public static TrackingOptions.Builder trackingOptions() {
        return TrackingOptions.builder();
    }
    
    /**
     * 在业务action两侧建立词法化tracking scope。
     *
     * <p>该兼容入口保留void签名，不发布全局changes；需要结构化结果时应直接使用
     * {@link TrackingExecutor#withTracked(String, Object, Runnable, CompareOptions)}。字段域属runtime policy，
     * facade不会用legacy参数临时构造第二份比较语义。</p>
     *
     * @param name process-local目标名，trim后必须非空
     * @param target action期间被观察的业务对象
     * @param action 只能由final executor调用一次的业务逻辑
     * @param ignoredFields 仅保留的3.x兼容参数；不改变当前runtime equality domain
     */
    public static void withTracked(String name, Object target, Runnable action, String... ignoredFields) {
        TrackingProvider provider = getTrackingProvider();
        // 只有provider runtime能定义字段域；facade不根据单次参数分叉出第二套policy。
        new TrackingExecutor(provider).withTracked(
                name,
                target,
                action,
                CompareOptions.builder().build());
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
     * 导出当前会话为 TREE 控制台诊断文本，不显示消息时间戳。
     */
    public static void exportToConsole() {
        exportToConsole(false);
    }

    /**
     * 导出当前会话为 TREE 控制台诊断文本。
     *
     * <p>{@code showTimestamp} 只控制消息时间戳；routing 开关只选择会话解析路径，不改变输出样式。
     *
     * @param showTimestamp 是否显示消息时间戳
     * @return 有当前会话且完成导出时返回 true，否则返回 false
     */
    public static boolean exportToConsole(boolean showTimestamp) {
        if (!isEnabled()) {
            return false;
        }

        try {
            // v4.0.0 Provider routing with grayscale control
            {
                ExportProvider provider = getExportProvider();
                if (provider != null) {
                    return provider.exportToConsole(showTimestamp);
                }
            }

            // Legacy 只负责解析当前 Session；Console style/timestamp 契约不随 routing 开关变化。
            Session session = getCurrentSession();
            if (session != null) {
                ConsoleExportOptions options = new ConsoleExportOptions(
                        ConsoleExportOptions.ConsoleStyle.TREE, showTimestamp);
                new ConsoleExporter().print(session, System.out, options);
                return true;
            }
            return false;
        } catch (Throwable t) {
            handleInternalError("Failed to export to console", t);
            return false;
        }
    }

    /**
     * 导出当前会话为JSON
     *
     * @return JSON字符串，如果失败返回null
     */
    public static String exportToJson() {
        if (!isEnabled()) {
            return "{}";
        }

        try {
            // v4.0.0 Provider routing with grayscale control
            {
                ExportProvider provider = getExportProvider();
                if (provider != null) {
                    return provider.exportToJson();
                }
            }

            // Legacy path (v3.0.0 behavior)
            Session session = getCurrentSession();
            if (session != null) {
                JsonExporter exporter = new JsonExporter();
                return exporter.export(session);
            }
            return "{}";
        } catch (Throwable t) {
            handleInternalError("Failed to export to JSON", t);
            return "{}";
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
            // v4.0.0 Provider routing with grayscale control
            {
                ExportProvider provider = getExportProvider();
                if (provider != null) {
                    return provider.exportToMap();
                }
            }

            // Legacy path (v3.0.0 behavior)
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
                    core = new TfiCore();
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
     * 错误级别枚举
     */
    enum ErrorLevel {
        /** 非关键基础设施错误，只影响当前观测能力。 */
        WARN,
        /** 当前观测操作失败，但不能改变业务流程控制权。 */
        ERROR,
        /** 可能破坏进程稳定性的错误，需要保留完整诊断。 */
        FATAL
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

    // ==================== 对象比较与渲染 API（委托给 TfiCompareDelegate）====================

    /**
     * 比较两个对象
     *
     * @param a 第一个对象
     * @param b 第二个对象
     * @return 比较结果，包含差异详情；失败时返回稳定的错误结果
     * @since v3.0.0
     * @see TfiCompareDelegate#compare(Object, Object)
     */
    public static CompareResult compare(Object a, Object b) {
        return TfiCompareDelegate.compare(a, b);
    }

    /**
     * 创建链式比较器构建器
     *
     * @return ComparatorBuilder 实例
     * @since v3.0.0
     * @see TfiCompareDelegate#comparator()
     */
    public static ComparatorBuilder comparator() {
        return TfiCompareDelegate.comparator();
    }

    /**
     * 按不可变布局选项渲染比较结果。
     *
     * <p>门面只构造一次安全projection并委托typed provider，布局选择不会改变字段或脱敏语义。</p>
     *
     * @param result 比较结果
     * @param options 只选择Markdown或Console布局的不可变选项
     * @return 已脱敏projection的诊断文本
     * @since v3.0.0
     * @see TfiCompareDelegate#render
     */
    public static String render(CompareResult result, RenderOptions options) {
        return TfiCompareDelegate.render(result, options);
    }

    /**
     * 使用唯一默认Markdown布局渲染比较结果。
     *
     * @param result 比较结果，不允许为null
     * @return 已脱敏projection的Markdown诊断文本
     * @since 4.0.0
     */
    public static String render(CompareResult result) {
        return render(result, RenderOptions.defaults());
    }

    // ==================== SPI Provider注册方法 (v4.0.0) ====================

    /**
     * 注册自定义ComparisonProvider
     *
     * <p>手动注册的Provider优先级高于ServiceLoader发现的Provider，
     * 但低于Spring Bean注入的Provider。
     *
     * @param provider ComparisonProvider实例
     * @since 4.0.0
     */
    public static void registerComparisonProvider(ComparisonProvider provider) {
        TfiProviderDelegate.registerComparisonProvider(provider);
    }

    /**
     * 在Registry冻结前注册typed TrackingProvider。
     *
     * <p>facade不缓存provider，也不吞掉冻结异常，避免形成第二套选择状态。</p>
     *
     * @param provider 只负责创建batch scope、不得持有业务action的provider
     * @see TfiProviderDelegate#registerTrackingProvider
     */
    public static void registerTrackingProvider(TrackingProvider provider) {
        TfiProviderDelegate.registerTrackingProvider(provider);
    }

    /**
     * 在Registry冻结前注册Flow provider。
     *
     * @param provider 交由Core Registry选择的无状态Flow实现
     * @see TfiProviderDelegate#registerFlowProvider
     */
    public static void registerFlowProvider(FlowProvider provider) {
        TfiProviderDelegate.registerFlowProvider(provider);
    }

    /**
     * 在Registry冻结前注册typed渲染provider。
     *
     * @param provider 只接收canonical projection与不可变布局选项的实现
     * @see TfiProviderDelegate#registerRenderProvider
     */
    public static void registerRenderProvider(RenderProvider provider) {
        TfiProviderDelegate.registerRenderProvider(provider);
    }

    /**
     * 在Registry冻结前注册Flow会话导出provider。
     *
     * @param provider 负责会话导出且不参与Compare projection格式选择的实现
     * @see TfiProviderDelegate#registerExportProvider
     */
    public static void registerExportProvider(ExportProvider provider) {
        TfiProviderDelegate.registerExportProvider(provider);
    }

    /**
     * 使用自定义ClassLoader加载Providers
     *
     * @param cl 自定义ClassLoader
     * @since 4.0.0
     */
    public static void loadProviders(ClassLoader cl) {
        TfiProviderDelegate.loadProviders(cl);
    }

    // ==================== Provider 获取方法（委托给 TfiProviderDelegate）====================

    private static ComparisonProvider getComparisonProvider() {
        return TfiProviderDelegate.getComparisonProvider();
    }

    private static TrackingProvider getTrackingProvider() {
        return TfiProviderDelegate.getTrackingProvider();
    }

    private static FlowProvider getFlowProvider() {
        return TfiProviderDelegate.getFlowProvider();
    }

    private static RenderProvider getRenderProvider() {
        return TfiProviderDelegate.getRenderProvider();
    }

    private static ExportProvider getExportProvider() {
        return TfiProviderDelegate.getExportProvider();
    }
}
