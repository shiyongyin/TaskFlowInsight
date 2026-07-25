package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>Provider 单一路由：门面不直接操作线程上下文，默认实现也通过 SPI 承接</li>
 * </ul>
 *
 * <p>Provider 单一路由的目的不是增加抽象层，而是减少并行语义：
 * 若 facade 同时维护「Provider 路径」和「ManagedThreadContext 兜底路径」，
 * 自定义 Provider、默认 Provider、TaskContext 关闭、导出会逐渐出现不一致。
 * 因此默认行为也由 ServiceLoader Provider 承接，facade 只负责参数校验与异常边界，选择状态统一交给 Registry。
 *
 * @author TaskFlow Insight Team
 * @version 4.0.0
 * @since 2025-01-16
 */
public final class TfiFlow {

    private static final Logger logger = LoggerFactory.getLogger(TfiFlow.class);

    // 启用/禁用开关
    private static volatile boolean enabled = true;

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
     *
     * <p>注意：清理类 API 不受 {@code enabled} 开关门控。运行期 {@link #disable()}
     * 之后在途请求仍需释放已注册的 ThreadLocal 上下文，否则线程池场景下
     * Session/TaskNode 树将随池线程永久驻留（违反零泄漏承诺）。
     */
    public static void clear() {
        try {
            getFlowProvider().clear();
        } catch (Throwable t) {
            logInternalFailure("Failed to clear context", t);
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
            return getFlowProvider().startSession(sessionName.trim());
        } catch (Throwable t) {
            logInternalFailure("Failed to start session", t);
            return null;
        }
    }

    /**
     * 结束当前会话
     *
     * <p>清理类 API 不受 {@code enabled} 开关门控，理由见 {@link #clear()}。
     */
    public static void endSession() {
        try {
            getFlowProvider().endSession();
        } catch (Throwable t) {
            logInternalFailure("Failed to end session", t);
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
     * <p>业务函数抛出的异常会原样传播给调用方（受检异常包装为
     * {@link RuntimeException} 后传播，保留原因链）；TFI 自身的失败仍不影响业务执行。
     *
     * @param stageName Stage名称
     * @param stageFunction 要在Stage中执行的函数
     * @param <T> 返回值类型
     * @return 执行结果
     */
    public static <T> T stage(String stageName, StageFunction<T> stageFunction) {
        if (stageFunction == null) {
            return null;
        }
        if (!enabled) {
            return applyUserFunction(stageFunction, NullTaskContext.INSTANCE);
        }

        TaskContext stage = start(stageName);   // 异常安全：TFI 失败时返回 NullTaskContext
        try {
            return applyUserFunction(stageFunction, stage);
        } catch (RuntimeException | Error e) {
            stage.fail(e);                      // 记录失败后原样传播业务异常
            throw e;
        } finally {
            stage.close();                      // close 内部吞掉 TFI 自身异常
        }
    }

    /**
     * 执行业务函数：unchecked 异常原样传播，受检异常包装为 RuntimeException 传播。
     * TFI 门面不再吞掉业务代码自身的失败（红队审计 H3）。
     */
    private static <T> T applyUserFunction(StageFunction<T> stageFunction, TaskContext stage) {
        try {
            return stageFunction.apply(stage);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
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
            TaskNode taskNode = provider.startTask(taskName.trim());
            return taskNode != null ? new TaskContextImpl(taskNode, provider) : NullTaskContext.INSTANCE;
        } catch (Throwable t) {
            logInternalFailure("Failed to start task", t);
            return NullTaskContext.INSTANCE;
        }
    }

    /**
     * 结束当前任务
     *
     * <p>清理类 API 不受 {@code enabled} 开关门控，理由见 {@link #clear()}。
     */
    public static void stop() {
        try {
            getFlowProvider().endTask();
        } catch (Throwable t) {
            logInternalFailure("Failed to stop task", t);
        }
    }

    /**
     * 在新任务中执行操作
     *
     * <p>业务操作抛出的异常会原样传播给调用方；TFI 自身的失败不影响业务执行。
     *
     * @param taskName 任务名称
     * @param runnable 要执行的操作
     */
    public static void run(String taskName, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (!enabled) {
            runnable.run();
            return;
        }

        TaskContext ctx = start(taskName);
        try {
            runnable.run();
        } catch (RuntimeException | Error e) {
            ctx.fail(e);
            throw e;
        } finally {
            ctx.close();
        }
    }

    /**
     * 在新任务中执行操作并返回结果
     *
     * <p>业务操作抛出的异常会原样传播给调用方（受检异常包装为
     * {@link RuntimeException} 后传播，保留原因链）；TFI 自身的失败不影响业务执行。
     *
     * @param taskName 任务名称
     * @param callable 要执行的操作
     * @param <T> 返回值类型
     * @return 执行结果
     */
    public static <T> T call(String taskName, Callable<T> callable) {
        if (callable == null) {
            return null;
        }
        if (!enabled) {
            return invokeUserCallable(callable);
        }

        TaskContext ctx = start(taskName);
        try {
            return invokeUserCallable(callable);
        } catch (RuntimeException | Error e) {
            ctx.fail(e);
            throw e;
        } finally {
            ctx.close();
        }
    }

    /**
     * 执行业务 Callable：unchecked 异常原样传播，受检异常包装为 RuntimeException 传播。
     */
    private static <T> T invokeUserCallable(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
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
            // 传递 MessageType 本体而非 displayName，保留类型语义（getType()/isAlert() 等仍可用）
            getFlowProvider().messageWithType(content.trim(), messageType);
        } catch (Throwable t) {
            logInternalFailure("Failed to record message", t);
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
            getFlowProvider().message(content.trim(), customLabel.trim());
        } catch (Throwable t) {
            logInternalFailure("Failed to record message", t);
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
        // content 为空时以异常描述为消息主体，避免拼出字面 "null - Xxx: msg"
        String base = (content == null || content.trim().isEmpty()) ? null : content;
        String errorMessage;
        if (throwable != null) {
            String detail = throwable.getClass().getSimpleName() + ": " + safeMessage(throwable);
            errorMessage = base != null ? base + " - " + detail : detail;
        } else {
            errorMessage = base;
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
            return getFlowProvider().currentSession();
        } catch (Throwable t) {
            logInternalFailure("Failed to get current session", t);
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
            return getFlowProvider().currentTask();
        } catch (Throwable t) {
            logInternalFailure("Failed to get current task", t);
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
            return getFlowProvider().getTaskStack();
        } catch (Throwable t) {
            logInternalFailure("Failed to get task stack", t);
            return List.of();
        }
    }

    // ==================== 导出方法 ====================

    /**
     * 导出当前会话为 TREE 控制台诊断文本，不显示消息时间戳。
     *
     * @return true 如果导出成功
     */
    public static boolean exportToConsole() {
        return exportToConsole(false);
    }

    /**
     * 导出当前会话为 TREE 控制台诊断文本。
     *
     * <p>{@code showTimestamp} 只控制消息时间戳，不选择 TREE/SIMPLE 样式。
     *
     * @param showTimestamp 是否显示消息时间戳
     * @return true 如果导出成功
     */
    public static boolean exportToConsole(boolean showTimestamp) {
        if (!enabled) {
            return false;
        }

        try {
            return getExportProvider().exportToConsole(showTimestamp);
        } catch (Throwable t) {
            logInternalFailure("Failed to export to console", t);
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
            return getExportProvider().exportToJson();
        } catch (Throwable t) {
            logInternalFailure("Failed to export to JSON", t);
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
            return getExportProvider().exportToMap();
        } catch (Throwable t) {
            logInternalFailure("Failed to export to Map", t);
            return Map.of();
        }
    }

    // ==================== Provider 获取方法 ====================

    /**
     * 获取本 epoch 唯一的 FlowProvider。
     *
     * @return Registry 选中的 FlowProvider；没有可信实现时返回 null
     */
    private static FlowProvider getFlowProvider() {
        return ProviderRegistry.resolve(FlowProvider.class);
    }

    /**
     * 获取本 epoch 唯一的 ExportProvider。
     *
     * @return Registry 选中的 ExportProvider；没有可信实现时返回 null
     */
    private static ExportProvider getExportProvider() {
        return ProviderRegistry.resolve(ExportProvider.class);
    }

    /**
     * 注册自定义FlowProvider
     *
     * @param provider FlowProvider实例
     */
    public static void registerFlowProvider(FlowProvider provider) {
        if (provider == null) {
            logger.warn("Ignoring null FlowProvider registration");
            return;
        }
        try {
            ProviderRegistry.register(FlowProvider.class, provider);
            logger.info("Registered custom FlowProvider: {}", provider.getClass().getSimpleName());
        } catch (Throwable t) {
            logInternalFailure("Failed to register FlowProvider", t);
        }
    }

    /**
     * 注册自定义 ExportProvider。
     *
     * @param provider ExportProvider 实例
     */
    public static void registerExportProvider(ExportProvider provider) {
        if (provider == null) {
            logger.warn("Ignoring null ExportProvider registration");
            return;
        }
        try {
            ProviderRegistry.register(ExportProvider.class, provider);
            logger.info("Registered custom ExportProvider: {}", provider.getClass().getSimpleName());
        } catch (Throwable t) {
            logInternalFailure("Failed to register ExportProvider", t);
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 统一处理 TFI 内部路径的失败。
     *
     * <p>规则：
     * <ul>
     *   <li>{@link VirtualMachineError}（OOM/StackOverflow 等）原样重抛——JVM 级故障
     *       不应被降级为一条 warn 日志而系统性掩盖</li>
     *   <li>{@link InterruptedException} 恢复线程中断位，保证优雅停机信号不丢失</li>
     *   <li>debug 级别记录完整堆栈，warn 级别记录异常摘要（含类名，OOM 的 message 常为 null）</li>
     * </ul>
     *
     * @param action 失败动作描述
     * @param t 捕获的异常
     */
    private static void logInternalFailure(String action, Throwable t) {
        if (t instanceof VirtualMachineError vmError) {
            throw vmError;
        }
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (logger.isDebugEnabled()) {
            logger.warn("{}", action, t);
        } else {
            logger.warn("{}: {}", action, t.toString());
        }
    }

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
