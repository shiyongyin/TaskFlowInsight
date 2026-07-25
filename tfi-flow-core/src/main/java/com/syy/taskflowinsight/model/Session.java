package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.enums.SessionStatus;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 一次任务流执行的业务生命周期实体，每个会话拥有一棵以根任务为起点的任务树。
 *
 * <p>4.0 起，Session 只负责业务状态与任务树终态，不再维护线程本地注册表。当前线程绑定、活动数量和
 * 泄漏清理由 {@link SafeContextManager} 统一拥有；保留的静态激活入口只是无状态适配器，避免重新形成
 * Session/Context 两套 current 与 cleanup 状态源。
 *
 * <p>并发与所有权约束：
 * <ul>
 *   <li>Session monitor 内只发布一次 RUNNING 到 COMPLETED/ERROR 的终态；</li>
 *   <li>释放 Session monitor 后再通知 Context owner，避免 Session/Context 锁序反转；</li>
 *   <li>创建线程标识同时约束 legacy activation；跨线程传播必须使用 Context snapshot，不能共享
 *       mutable Session。</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public final class Session {

    private final String sessionId;
    private final String threadId;
    private final String threadName;
    private final long createdMillis;
    private final long createdNanos;
    private final TaskTreeMutationGate mutationGate;
    private final TaskNode rootTask;
    private final TerminalTransitionProbe terminalTransitionProbe;
    
    private final AtomicReference<SessionStatus> status;
    private volatile Long completedMillis;
    private volatile Long completedNanos;
    
    /**
     * 私有构造函数，强制使用工厂方法创建实例
     * 
     * @param rootTaskName 根任务名称
     */
    private Session(String rootTaskName) {
        this(rootTaskName, TerminalTransitionProbe.NOOP);
    }

    Session(String rootTaskName, TerminalTransitionProbe terminalTransitionProbe) {
        this.sessionId = UUID.randomUUID().toString();
        
        Thread currentThread = Thread.currentThread();
        this.threadId = String.valueOf(currentThread.threadId());
        this.threadName = currentThread.getName();
        this.createdMillis = System.currentTimeMillis();
        this.createdNanos = System.nanoTime();

        this.mutationGate = new TaskTreeMutationGate();
        this.rootTask = new TaskNode(rootTaskName, mutationGate);
        this.terminalTransitionProbe = Objects.requireNonNull(
                terminalTransitionProbe, "terminalTransitionProbe");
        this.status = new AtomicReference<>(SessionStatus.RUNNING);
    }
    
    /**
     * 创建新的会话实例
     * 
     * @param rootTaskName 根任务名称
     * @return 新创建的会话实例
     * @throws IllegalArgumentException 如果rootTaskName为null或空字符串
     */
    public static Session create(String rootTaskName) {
        if (rootTaskName == null || rootTaskName.trim().isEmpty()) {
            throw new IllegalArgumentException("Root task name cannot be null or empty");
        }
        return new Session(rootTaskName.trim());
    }
    
    /**
     * 获取当前线程的活跃会话
     * 
     * @return 当前线程的活跃会话，如果不存在则返回null
     * @deprecated 兼容旧的 {@link #activate()} / {@link #deactivate()} 入口。运行时流程 API 的当前会话以
     *     ManagedThreadContext/SafeContextManager 为准。
     */
    @Deprecated(since = "4.0.0")
    public static Session getCurrent() {
        return ThreadContext.currentSession();
    }
    
    /**
     * 将当前会话设置为线程本地活跃会话
     * 
     * @return 当前会话实例
     * @throws IllegalStateException 如果会话不在RUNNING状态，或调用线程不是 Session 创建线程
     * @deprecated 兼容旧的 {@link #getCurrent()} 入口。新代码应通过 TfiFlow 或 ManagedThreadContext
     *     管理会话生命周期。
     */
    @Deprecated(since = "4.0.0")
    public Session activate() {
        synchronized (this) {
            requireRunningForActivation();
            requireCreationThreadForActivation();
        }

        SafeContextManager.getInstance().bindLegacySession(this);
        requireRunningForActivation();
        return this;
    }
    
    /**
     * 取消激活当前会话
     * 
     * @return 当前会话实例
     * @deprecated 兼容旧的 {@link #getCurrent()} 入口。新代码应通过 TfiFlow 或 ManagedThreadContext
     *     管理会话生命周期。
     */
    @Deprecated(since = "4.0.0")
    public Session deactivate() {
        SafeContextManager.getInstance().unbindLegacySession(this);
        return this;
    }
    
    /**
     * 正常完成会话
     * 
     * @throws IllegalStateException 如果会话不在RUNNING状态
     */
    public void complete() {
        if (!terminate(SessionStatus.COMPLETED, TerminalPayload.none())) {
            throw new IllegalStateException(
                "Cannot complete session that is not running. Current status: " + status.get());
        }
    }

    /**
     * 尝试正常完成会话（幂等）。
     *
     * <p>根任务通过 {@link TaskNode#tryComplete()} 幂等收尾：即使根任务已被并发终止也不会抛异常。
     * 完成时间和 Session 状态在同一 monitor 内发布，Context 释放在 monitor 外执行，避免业务终态、
     * 完成时间与 owner 清理出现部分成功。
     *
     * @return true 如果本次调用完成了 RUNNING → COMPLETED 转换
     */
    public boolean tryComplete() {
        return terminate(SessionStatus.COMPLETED, TerminalPayload.none());
    }

    /**
     * 异常终止会话
     *
     * @throws IllegalStateException 如果会话不在RUNNING状态
     */
    public void error() {
        if (!terminate(SessionStatus.ERROR, TerminalPayload.none())) {
            throw new IllegalStateException(
                "Cannot error session that is not running. Current status: " + status.get());
        }
    }

    /**
     * 尝试异常终止会话（幂等）。
     *
     * @return true 如果本次调用完成了 RUNNING → ERROR 转换
     */
    public boolean tryError() {
        return terminate(SessionStatus.ERROR, TerminalPayload.none());
    }

    /**
     * 尝试异常终止会话并添加错误消息（幂等）。
     *
     * <p>会话已终止时不添加消息、不抛异常，供上下文清理等并发收尾路径安全调用。
     *
     * @param errorContent 错误消息内容
     * @return true 如果本次调用完成了状态转换并记录了消息
     * @throws IllegalArgumentException 如果errorContent为null或空字符串
     */
    public boolean tryError(String errorContent) {
        return terminate(SessionStatus.ERROR, TerminalPayload.message(errorContent));
    }

    /**
     * 异常终止会话并添加错误消息
     *
     * <p>先校验状态再产生副作用：会话已终止时直接抛异常，不会在根任务上残留错误消息。
     *
     * @param errorContent 错误消息内容
     * @throws IllegalStateException 如果会话不在RUNNING状态
     * @throws IllegalArgumentException 如果errorContent为null或空字符串
     */
    public void error(String errorContent) {
        if (!terminate(SessionStatus.ERROR, TerminalPayload.message(errorContent))) {
            throw new IllegalStateException(
                "Cannot error session that is not running. Current status: " + status.get());
        }
    }

    /**
     * 根据异常终止会话
     *
     * <p>先校验状态再产生副作用：会话已终止时直接抛异常，不会在根任务上残留错误消息。
     *
     * @param throwable 异常对象
     * @throws IllegalStateException 如果会话不在RUNNING状态
     * @throws IllegalArgumentException 如果throwable为null
     */
    public void error(Throwable throwable) {
        if (!terminate(SessionStatus.ERROR, TerminalPayload.failure(throwable))) {
            throw new IllegalStateException(
                "Cannot error session that is not running. Current status: " + status.get());
        }
    }

    /**
     * 在 Session monitor 内发布唯一终态，释放 monitor 后才通知 Context owner。
     *
     * <p>bridge 可能获取 Context monitor，因此必须严格后置；否则 direct Session terminal 与
     * Context-owned cleanup 会形成 Session -> Context / Context -> Session 的锁反转。
     */
    private boolean terminate(SessionStatus target, TerminalPayload payload) {
        Throwable primary;
        synchronized (this) {
            if (status.get() != SessionStatus.RUNNING) {
                return false;
            }
            Message errorMessage = payload.createErrorMessage();
            primary = mutationGate.mutate(() -> terminateTaskTreeLocked(target, errorMessage));
        }

        try {
            terminalTransitionProbe.afterTransition(this);
        } catch (RuntimeException | Error probeFailure) {
            primary = mergeFailure(primary, probeFailure);
        }
        try {
            SafeContextManager.getInstance().releaseExternallyTerminatedSession(this);
        } catch (RuntimeException | Error releaseFailure) {
            primary = mergeFailure(primary, releaseFailure);
        }
        rethrowUnchecked(primary);
        return true;
    }

    private Throwable terminateTaskTreeLocked(SessionStatus target, Message errorMessage) {
        Throwable primary = null;
        try {
            if (target == SessionStatus.COMPLETED) {
                rootTask.tryCompleteLocked();
            } else if (errorMessage == null) {
                rootTask.tryFailLocked();
            } else {
                rootTask.recordSessionFailureLocked(errorMessage);
            }
        } catch (RuntimeException | Error transitionFailure) {
            primary = transitionFailure;
        } finally {
            markCompleted();
            status.set(target);
        }
        return primary;
    }

    private static Throwable mergeFailure(Throwable primary, Throwable next) {
        if (primary == null) {
            return next;
        }
        if (next != primary) {
            primary.addSuppressed(next);
        }
        return primary;
    }

    private void requireRunningForActivation() {
        if (!isActive()) {
            throw new IllegalStateException(
                    "Cannot activate session that is not running. Current status: " + status.get());
        }
    }

    private void requireCreationThreadForActivation() {
        if (!threadId.equals(String.valueOf(Thread.currentThread().threadId()))) {
            throw new IllegalStateException("Session activation must occur on its creation thread");
        }
    }

    private static void rethrowUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private record TerminalPayload(
            TerminalPayloadType type, String errorContent, Throwable throwable) {

        private static TerminalPayload none() {
            return new TerminalPayload(TerminalPayloadType.NONE, null, null);
        }

        private static TerminalPayload message(String message) {
            return new TerminalPayload(TerminalPayloadType.STRING, message, null);
        }

        private static TerminalPayload failure(Throwable throwable) {
            return new TerminalPayload(TerminalPayloadType.THROWABLE, null, throwable);
        }

        private Message createErrorMessage() {
            return switch (type) {
                case NONE -> null;
                case STRING -> Message.error(errorContent);
                case THROWABLE -> Message.error(throwable);
            };
        }
    }

    private enum TerminalPayloadType {
        NONE,
        STRING,
        THROWABLE
    }

    /**
     * 在 Session/root 终态发布且所有模型锁释放后触发的内部确定性测试接缝。
     *
     * <p>生产工厂固定使用 NOOP；该接缝不能改变终态，也不能暴露为 public/protected extension point。
     */
    @FunctionalInterface
    interface TerminalTransitionProbe {
        TerminalTransitionProbe NOOP = ignored -> { };

        void afterTransition(Session session);
    }
    
    /**
     * 标记完成时间
     */
    private void markCompleted() {
        this.completedMillis = System.currentTimeMillis();
        this.completedNanos = System.nanoTime();
    }
    
    /**
     * 获取会话唯一标识
     * 
     * @return 会话ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * 获取线程ID
     * 
     * @return 线程ID
     */
    public String getThreadId() {
        return threadId;
    }
    
    /**
     * 获取线程名称
     * 
     * @return 线程名称
     */
    public String getThreadName() {
        return threadName;
    }
    
    /**
     * 获取创建时间（毫秒时间戳）
     * 
     * @return 毫秒级时间戳
     */
    public long getCreatedMillis() {
        return createdMillis;
    }
    
    /**
     * 获取创建时间（纳秒时间戳）
     * 
     * @return 纳秒级时间戳
     */
    public long getCreatedNanos() {
        return createdNanos;
    }
    
    /**
     * 获取根任务节点
     * 
     * @return 根任务节点
     */
    public TaskNode getRootTask() {
        return rootTask;
    }

    /**
     * 在当前 Session 的唯一写侧 gate 内执行一次冻结捕获。
     *
     * <p>callback 只供同包快照捕获器读取并冻结 private raw values；public snapshot 的组装与全图校验
     * 必须在该词法调用返回、锁已释放后执行，且不能让 permit 或 mutable model reference 逃逸。
     *
     * @param captureAction 只读取当前任务树并冻结 raw values 的动作
     * @param <T> 捕获结果类型
     * @return 捕获结果
     */
    <T> T captureExport(Supplier<T> captureAction) {
        return mutationGate.capture(Objects.requireNonNull(captureAction, "captureAction"));
    }
    
    /**
     * 获取当前会话状态
     * 
     * @return 会话状态
     */
    public SessionStatus getStatus() {
        return status.get();
    }
    
    /**
     * 获取完成时间（毫秒时间戳）
     * 
     * @return 毫秒级时间戳，如果会话未完成则返回null
     */
    public Long getCompletedMillis() {
        return completedMillis;
    }
    
    /**
     * 获取完成时间（纳秒时间戳）
     * 
     * @return 纳秒级时间戳，如果会话未完成则返回null
     */
    public Long getCompletedNanos() {
        return completedNanos;
    }
    
    /**
     * 获取会话持续时长（毫秒）
     * 
     * @return 持续时长，如果会话未完成则返回null
     */
    public Long getDurationMillis() {
        if (completedMillis == null) {
            return null;
        }
        return completedMillis - createdMillis;
    }
    
    /**
     * 获取会话持续时长（纳秒）
     * 
     * @return 持续时长，如果会话未完成则返回null
     */
    public Long getDurationNanos() {
        if (completedNanos == null) {
            return null;
        }
        return completedNanos - createdNanos;
    }
    
    /**
     * 判断会话是否处于活跃状态
     * 
     * @return true 如果会话处于RUNNING状态
     */
    public boolean isActive() {
        return status.get().isActive();
    }
    
    /**
     * 判断会话是否已经终止
     * 
     * @return true 如果会话已经终止
     */
    public boolean isTerminated() {
        return status.get().isTerminated();
    }
    
    /**
     * 判断会话是否正常完成
     * 
     * @return true 如果会话正常完成
     */
    public boolean isCompleted() {
        return status.get().isCompleted();
    }
    
    /**
     * 判断会话是否异常结束
     * 
     * @return true 如果会话异常结束
     */
    public boolean isError() {
        return status.get().isError();
    }
    
    /**
     * 获取当前活跃会话数量
     * 
     * @return 活跃会话数量
     * @deprecated 兼容旧的静态会话注册表指标。运行时上下文数量请使用 SafeContextManager。
     */
    @Deprecated(since = "4.0.0")
    public static int getActiveSessionCount() {
        return ThreadContext.getActiveContextCount();
    }
    
    /**
     * 触发唯一 Context owner 的泄漏检测，并返回本次检测减少的活动 Context 数量。
     *
     * <p>Session 不再保存独立注册表，因此该返回值是 manager 活动计数的差值，不代表扫描了第二份
     * Session 集合。
     *
     * @return 本次检测减少的活动 Context 数量，最小为 0
     * @deprecated 兼容旧的静态会话清理入口。运行时泄漏检测请使用 SafeContextManager。
     */
    @Deprecated(since = "4.0.0")
    public static int cleanupInactiveSessions() {
        int before = ThreadContext.getActiveContextCount();
        ThreadContext.detectPotentialLeaks();
        return Math.max(0, before - ThreadContext.getActiveContextCount());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        Session session = (Session) obj;
        return Objects.equals(sessionId, session.sessionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
    
    @Override
    public String toString() {
        return String.format("Session{id='%s', thread='%s', status=%s}", 
                sessionId, threadName, status.get());
    }
}
