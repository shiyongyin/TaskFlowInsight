package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * TaskFlow Insight Flow-only 门面类
 *
 * <p>提供纯 Flow 核心能力的静态入口，不依赖 compare/tracking 模块。
 * 适用于只需要 Session/Task/Stage/Message/Export 功能的用户。
 *
 * <p>设计原则：
 * <ul>
 *   <li>纯 Java 实现：不依赖 Spring/Micrometer/Actuator/Caffeine</li>
 *   <li>异常安全：门面层捕获所有异常，使用 SLF4J 记录</li>
 *   <li>轻量级：最小依赖集合，适合嵌入式/库场景</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 4.0.0
 * @since 2025-01-16
 */
public final class TfiFlow {

    private static final Logger logger = LoggerFactory.getLogger(TfiFlow.class);

    // 启用/禁用开关
    private static volatile boolean enabled = true;

    // FlowProvider 缓存（双重检查锁机制）
    private static volatile FlowProvider cachedFlowProvider;

    /**
     * 私有构造函数，防止实例化
     */
    private TfiFlow() {
        throw new UnsupportedOperationException("TfiFlow is a utility class");
    }

    // ==================== 系统控制方法 ====================

    /**
     * 启用 TfiFlow
     */
    public static void enable() {
        enabled = true;
        logger.info("TfiFlow enabled");
    }

    /**
     * 禁用 TfiFlow
     */
    public static void disable() {
        enabled = false;
        logger.info("TfiFlow disabled");
    }

    /**
     * 检查是否启用
     *
     * @return true 如果已启用
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 清理当前线程的所有上下文
     */
    public static void clear() {
        if (!enabled) {
            return;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                provider.clear();
            } else {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context != null) {
                    context.close();
                }
            }
        } catch (Throwable t) {
            logger.warn("Failed to clear context: {}", t.getMessage());
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
        if (!enabled || sessionName == null || sessionName.trim().isEmpty()) {
            return null;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                return provider.startSession(sessionName.trim());
            }

            // 传统路径（无 Provider 时的兜底逻辑）
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null) {
                context = ManagedThreadContext.create(sessionName.trim());
                Session session = context.getCurrentSession();
                return session != null ? session.getSessionId() : null;
            } else {
                if (context.getCurrentSession() != null && context.getCurrentSession().isActive()) {
                    context.endSession();
                }
                Session session = context.startSession(sessionName.trim());
                return session != null ? session.getSessionId() : null;
            }
        } catch (Throwable t) {
            logger.warn("Failed to start session: {}", t.getMessage());
            return null;
        }
    }

    /**
     * 结束当前会话
     */
    public static void endSession() {
        if (!enabled) {
            return;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                provider.endSession();
                return;
            }

            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endSession();
            }
        } catch (Throwable t) {
            logger.warn("Failed to end session: {}", t.getMessage());
        }
    }

    // ==================== 任务管理方法 ====================

    /**
     * 创建一个Stage（AutoCloseable任务块）
     *
     * <p>使用示例：
     * <pre>{@code
     * try (var stage = TfiFlow.stage("数据处理")) {
     *     stage.message("开始处理");
     *     // 业务逻辑
     *     stage.success();
     * }
     * }</pre>
     *
     * @param stageName Stage名称
     * @return TaskContext 支持AutoCloseable的任务上下文
     */
    public static TaskContext stage(String stageName) {
        return start(stageName);
    }

    /**
     * 在Stage中执行操作（函数式API）
     *
     * @param stageName Stage名称
     * @param stageFunction 要在Stage中执行的函数
     * @param <T> 返回值类型
     * @return 执行结果，如果失败返回null
     */
    public static <T> T stage(String stageName, StageFunction<T> stageFunction) {
        if (!enabled || stageFunction == null) {
            try {
                return stageFunction != null ? stageFunction.apply(NullTaskContext.INSTANCE) : null;
            } catch (Exception e) {
                return null;
            }
        }

        try (TaskContext stage = start(stageName)) {
            return stageFunction.apply(stage);
        } catch (Throwable t) {
            logger.warn("Failed to execute stage: {}", t.getMessage());
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
        if (!enabled || taskName == null || taskName.trim().isEmpty()) {
            return NullTaskContext.INSTANCE;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                if (provider.currentSession() == null) {
                    provider.startSession("auto-session");
                }
                TaskNode taskNode = provider.startTask(taskName.trim());
                return taskNode != null ? new TaskContextImpl(taskNode) : NullTaskContext.INSTANCE;
            }

            // 传统路径（无 Provider 时的兜底逻辑）
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
            logger.warn("Failed to start task: {}", t.getMessage());
            return NullTaskContext.INSTANCE;
        }
    }

    /**
     * 结束当前任务
     */
    public static void stop() {
        if (!enabled) {
            return;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                provider.endTask();
                return;
            }

            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endTask();
            }
        } catch (Throwable t) {
            logger.warn("Failed to stop task: {}", t.getMessage());
        }
    }

    /**
     * 在新任务中执行操作
     *
     * @param taskName 任务名称
     * @param runnable 要执行的操作
     */
    public static void run(String taskName, Runnable runnable) {
        if (!enabled || runnable == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }

        try (TaskContext ignored = start(taskName)) {
            runnable.run();
        } catch (Throwable t) {
            logger.warn("Failed to run task: {}", t.getMessage());
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
        if (!enabled || callable == null) {
            try {
                return callable != null ? callable.call() : null;
            } catch (Exception e) {
                return null;
            }
        }

        try (TaskContext ignored = start(taskName)) {
            return callable.call();
        } catch (Throwable t) {
            logger.warn("Failed to call task: {}", t.getMessage());
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
        if (!enabled || content == null || content.trim().isEmpty() || messageType == null) {
            return;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                provider.message(content.trim(), messageType.getDisplayName());
                return;
            }

            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && context.getCurrentTask() != null) {
                context.getCurrentTask().addMessage(content.trim(), messageType);
            }
        } catch (Throwable t) {
            logger.warn("Failed to record message: {}", t.getMessage());
        }
    }

    /**
     * 记录自定义标签消息
     *
     * @param content 消息内容
     * @param customLabel 自定义标签
     */
    public static void message(String content, String customLabel) {
        if (!enabled || content == null || content.trim().isEmpty() ||
            customLabel == null || customLabel.trim().isEmpty()) {
            return;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                provider.message(content.trim(), customLabel.trim());
                return;
            }

            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && context.getCurrentTask() != null) {
                context.getCurrentTask().addMessage(content.trim(), customLabel.trim());
            }
        } catch (Throwable t) {
            logger.warn("Failed to record message: {}", t.getMessage());
        }
    }

    /**
     * 记录错误消息
     *
     * @param content 错误消息内容
     */
    public static void error(String content) {
        message(content, MessageType.ALERT);
    }

    /**
     * 记录错误消息和异常
     *
     * @param content 错误消息内容
     * @param throwable 异常对象
     */
    public static void error(String content, Throwable throwable) {
        String errorMessage = content;
        if (throwable != null) {
            errorMessage = content + " - " + throwable.getClass().getSimpleName() + ": " + safeMessage(throwable);
        }
        message(errorMessage, MessageType.ALERT);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取当前会话
     *
     * @return 当前会话，如果没有返回null
     */
    public static Session getCurrentSession() {
        if (!enabled) {
            return null;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                return provider.currentSession();
            }

            ManagedThreadContext context = ManagedThreadContext.current();
            return context != null ? context.getCurrentSession() : null;
        } catch (Throwable t) {
            logger.warn("Failed to get current session: {}", t.getMessage());
            return null;
        }
    }

    /**
     * 获取当前任务
     *
     * @return 当前任务节点，如果没有返回null
     */
    public static TaskNode getCurrentTask() {
        if (!enabled) {
            return null;
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                return provider.currentTask();
            }

            ManagedThreadContext context = ManagedThreadContext.current();
            return context != null ? context.getCurrentTask() : null;
        } catch (Throwable t) {
            logger.warn("Failed to get current task: {}", t.getMessage());
            return null;
        }
    }

    /**
     * 获取任务堆栈
     *
     * @return 任务堆栈列表，如果没有返回空列表
     */
    public static List<TaskNode> getTaskStack() {
        if (!enabled) {
            return List.of();
        }

        try {
            FlowProvider provider = getFlowProvider();
            if (provider != null) {
                return provider.getTaskStack();
            }

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
            logger.warn("Failed to get task stack: {}", t.getMessage());
            return List.of();
        }
    }

    // ==================== 导出方法 ====================

    /**
     * 导出当前会话到控制台（简化输出，无时间戳）
     *
     * @return true 如果导出成功
     */
    public static boolean exportToConsole() {
        return exportToConsole(false);
    }

    /**
     * 导出当前会话的任务树到控制台
     *
     * @param showTimestamp 是否显示时间戳
     * @return true 如果导出成功
     */
    public static boolean exportToConsole(boolean showTimestamp) {
        if (!enabled) {
            return false;
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
                return true;
            }
            return false;
        } catch (Throwable t) {
            logger.warn("Failed to export to console: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 导出当前会话为JSON
     *
     * @return JSON字符串，如果失败返回空JSON对象
     */
    public static String exportToJson() {
        if (!enabled) {
            return "{}";
        }

        try {
            Session session = getCurrentSession();
            if (session != null) {
                JsonExporter exporter = new JsonExporter();
                return exporter.export(session);
            }
            return "{}";
        } catch (Throwable t) {
            logger.warn("Failed to export to JSON: {}", t.getMessage());
            return "{}";
        }
    }

    /**
     * 导出当前会话为Map
     *
     * @return Map对象，如果失败返回空Map
     */
    public static Map<String, Object> exportToMap() {
        if (!enabled) {
            return Map.of();
        }

        try {
            Session session = getCurrentSession();
            return session != null ? MapExporter.export(session) : Map.of();
        } catch (Throwable t) {
            logger.warn("Failed to export to Map: {}", t.getMessage());
            return Map.of();
        }
    }

    // ==================== Provider 获取方法 ====================

    /**
     * 获取 FlowProvider（带缓存）
     *
     * @return FlowProvider 实例，可能为 null
     */
    private static FlowProvider getFlowProvider() {
        if (cachedFlowProvider != null) {
            return cachedFlowProvider;
        }

        synchronized (TfiFlow.class) {
            if (cachedFlowProvider == null) {
                cachedFlowProvider = ProviderRegistry.lookup(FlowProvider.class);

                if (cachedFlowProvider != null) {
                    logger.debug("Found FlowProvider: {} (priority={})",
                        cachedFlowProvider.getClass().getSimpleName(),
                        cachedFlowProvider.priority());
                }
            }
        }

        return cachedFlowProvider;
    }

    /**
     * 注册自定义FlowProvider
     *
     * @param provider FlowProvider实例
     */
    public static void registerFlowProvider(FlowProvider provider) {
        try {
            ProviderRegistry.register(FlowProvider.class, provider);
            cachedFlowProvider = null; // 清除缓存，下次重新查找
            logger.info("Registered custom FlowProvider: {}", provider.getClass().getSimpleName());
        } catch (Throwable t) {
            logger.warn("Failed to register FlowProvider: {}", t.getMessage());
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 安全获取异常消息（null-safe）
     *
     * <p>当 {@link Throwable#getMessage()} 返回 {@code null} 时，
     * 回退到异常类的简单名称，避免拼接出 "null" 字符串。
     *
     * @param t 异常对象
     * @return 非 null 的异常描述信息
     */
    private static String safeMessage(Throwable t) {
        if (t == null) {
            return "unknown error";
        }
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }
}
