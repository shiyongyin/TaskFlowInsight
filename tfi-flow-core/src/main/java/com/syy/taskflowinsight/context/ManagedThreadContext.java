package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import lombok.Getter;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个线程绑定内的 Session 与 LIFO 任务栈 owner。
 *
 * <p>G2 要求一 Session 一 Context；本类型负责业务终态和任务树，{@link SafeContextManager}
 * 负责 ThreadLocal、registry 与泄漏调度。跨线程只能传递 {@link ContextSnapshot}，
 * 不能共享本实例。</p>
 *
 * <p>正常调用方应使用 try-with-resources。泄漏检测器可能跨线程强制结束实例，
 * 因此任务栈生命周期操作在实例锁内串行，而 manager 注销固定在锁外。</p>
 *
 * @since 3.0.0
 * @see ContextScope
 */
public class ManagedThreadContext implements AutoCloseable {
    
    private final String contextId;
    private final long threadId;
    @Getter
    private final String threadName;
    // 弱引用持有创建线程，用于泄漏检测时精确判断线程存活，且不阻止线程对象被 GC
    private final WeakReference<Thread> ownerThreadRef;
    private final long createdNanos;
    /*
     * taskStack 本身非线程安全。除 owner 线程外，泄漏检测器/关闭钩子也可能跨线程调用
     * close()，因此所有触碰 taskStack 的生命周期方法均以本实例为锁互斥。
     */
    private final Deque<TaskNode> taskStack;
    // 手动声明了 getCurrentSession()，不再由 Lombok 生成，以避免重复方法警告
    private volatile Session currentSession;
    private volatile boolean closed = false;
    private final boolean ownsSessionTermination;
    private final ContextTerminalProbe terminalProbe;
    private volatile boolean sessionTerminalInProgress;

    /**
     * 判断该 owner 是否已完成引用清理；终态发布失败时也会返回 true，防止重复释放。
     *
     * @return 已进入任一终止路径时为 true
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * 获取线程ID
     * @return 线程ID
     */
    public long getThreadId() {
        return threadId;
    }
    
    /**
     * 获取上下文ID
     * @return 上下文ID
     */
    public String getContextId() {
        return contextId;
    }
    
    /**
     * 获取创建时间纳秒数
     * @return 创建时间纳秒数
     */
    public long getCreatedNanos() {
        return createdNanos;
    }

    /**
     * 判断创建该上下文的线程是否仍然存活。
     *
     * <p>通过 {@link WeakReference} 持有创建线程：当线程对象已被垃圾回收（{@code get()} 返回 {@code null}）
     * 或线程已终止（{@link Thread#isAlive()} 为 {@code false}）时返回 {@code false}。
     *
     * <p>相较于 {@code Thread.enumerate(...)}，该方式不受当前线程组（ThreadGroup）范围限制，
     * 可避免将其他线程组中仍然存活的线程误判为“死线程泄漏”，同时把单次判断的复杂度从
     * O(JVM 线程总数) 降为 O(1)。
     *
     * @return true 如果创建线程仍然存活
     */
    public boolean isOwnerThreadAlive() {
        Thread owner = ownerThreadRef.get();
        return owner != null && owner.isAlive();
    }
    
    // 元数据存储
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    
    /**
     * 私有构造函数，通过静态工厂方法创建
     */
    private ManagedThreadContext() {
        this(null, true, ContextTerminalProbe.NO_OP);
    }

    ManagedThreadContext(ContextTerminalProbe terminalProbe) {
        this(null, true, terminalProbe);
    }

    private ManagedThreadContext(
            Session session, boolean ownsSessionTermination, ContextTerminalProbe terminalProbe) {
        this.contextId = UUID.randomUUID().toString();
        Thread current = Thread.currentThread();
        this.threadId = current.threadId();
        this.threadName = current.getName();
        this.ownerThreadRef = new WeakReference<>(current);
        this.createdNanos = System.nanoTime();
        this.taskStack = new ArrayDeque<>();
        this.currentSession = session;
        this.ownsSessionTermination = ownsSessionTermination;
        this.terminalProbe = terminalProbe == null ? ContextTerminalProbe.NO_OP : terminalProbe;
        if (session != null) {
            this.taskStack.push(session.getRootTask());
        }
    }

    static ManagedThreadContext nonOwning(Session session) {
        if (session == null) {
            throw new NullPointerException("Session cannot be null");
        }
        return new ManagedThreadContext(session, false, ContextTerminalProbe.NO_OP);
    }
    
    /**
     * 创建并激活新的线程上下文
     * 
     * @param rootTaskName 根任务名称
     * @return 新创建的上下文实例
     */
    public static ManagedThreadContext create(String rootTaskName) {
        ManagedThreadContext context = new ManagedThreadContext();

        /*
         * 先完成 Session 校验与构造，再发布新身份。若构造失败，当前线程原有 Context 必须继续可用；
         * 成功后的 replacement 统一由 manager 原子换绑并在状态锁外清理，避免出现两个替换入口。
         */
        // 创建会话
        context.startSession(rootTaskName);

        // 注册新上下文
        SafeContextManager manager = SafeContextManager.getInstance();
        manager.bindNewContext(context);
        
        return context;
    }
    
    /**
     * 获取当前线程的活动上下文
     * 
     * @return 当前线程的上下文，如果不存在返回null
     */
    public static ManagedThreadContext current() {
        return SafeContextManager.getInstance().getCurrentContext();
    }
    
    /**
     * 开始新会话
     * MVP阶段每线程仅支持单会话
     * 
     * @param rootTaskName 根任务名称
     * @return 创建的会话
     */
    public synchronized Session startSession(String rootTaskName) {
        checkNotClosed();
        
        if (currentSession != null && currentSession.isActive()) {
            throw new IllegalStateException("Session already active: " + currentSession.getSessionId());
        }
        
        /*
         * Session 的运行时 owner 是本 Context。这里不再写 legacy Session 静态注册表，
         * 否则 Session 与 SafeContextManager 会同时声明“当前会话”，形成两个状态源。
         */
        Session session = Session.create(rootTaskName);
        this.currentSession = session;
        
        // 根任务自动入栈
        this.taskStack.push(session.getRootTask());
        
        return session;
    }
    
    /**
     * 正常结束当前 Session 及其唯一 Context。
     *
     * <p>G2 采用“一 Session 一 Context”，因此会话结束不能只清空 Session 引用；
     * 必须复用统一终止路径，在同一临界区完成任务树并在锁外注销 Context，避免遗留
     * ThreadLocal/registry owner。锁由 {@link #finish(ContextOutcome, Throwable, String)}
     * 内部管理，避免外层 monitor 包住 manager 注销。
     */
    public void endSession() {
        finish(ContextOutcome.SUCCESS, null, null);
    }
    
    /**
     * 开始新任务
     * 
     * @param taskName 任务名称
     * @return 创建的任务节点
     */
    public synchronized TaskNode startTask(String taskName) {
        checkNotClosed();
        
        if (currentSession == null) {
            throw new IllegalStateException("No active session");
        }
        
        TaskNode parent = taskStack.peek();
        if (parent == null) {
            throw new IllegalStateException("No parent task available");
        }
        
        // 创建子任务
        TaskNode task = new TaskNode(parent, taskName);
        taskStack.push(task);
        
        return task;
    }
    
    /**
     * 结束当前任务
     * 
     * @return 结束的任务节点
     */
    public synchronized TaskNode endTask() {
        checkNotClosed();
        
        if (taskStack.isEmpty()) {
            throw new IllegalStateException("No active task to end");
        }
        
        TaskNode task = taskStack.pop();
        
        // 自动完成活动任务
        if (task.getStatus().isActive()) {
            task.complete();
        }
        
        return task;
    }
    
    /**
     * 创建上下文快照用于跨线程传播
     * 仅包含ID和不可变元数据，不包含对象引用
     * 
     * @return 上下文快照
     */
    public synchronized ContextSnapshot createSnapshot() {
        checkNotClosed();
        
        String sessionId = currentSession != null ? currentSession.getSessionId() : null;
        String taskPath = !taskStack.isEmpty() ? taskStack.peek().getTaskPath() : null;
        
        return new ContextSnapshot(contextId, sessionId, taskPath, System.nanoTime());
    }
    
    /**
     * 从快照破坏式恢复 Context。
     *
     * <p>该兼容入口与 {@link ContextSnapshot#restore()} 共用 manager 路径，确保 replacement 和传播计数
     * 只有一个实现；临时 wrapper 传播不得调用本方法。</p>
     *
     * @param snapshot 非 null 的不可变快照
     * @return 新建并绑定的 linked-child Context
     */
    public static ManagedThreadContext restoreFromSnapshot(ContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }
        return SafeContextManager.getInstance().restoreDestructively(snapshot);
    }

    /**
     * 仅重建快照携带的 linked-child 状态，不发布线程绑定。
     *
     * <p>发布必须由 manager 完成；拆开构造和 bind 后，构造失败不会破坏 scope 已暂停的 prior。</p>
     */
    static ManagedThreadContext restoreUnboundFromSnapshot(ContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }

        // 创建新的上下文实例（不同线程）
        ManagedThreadContext context = new ManagedThreadContext();
        
        // 记录快照信息但不共享对象（处理null值）
        context.attributes.put("parent.contextId", snapshot.getContextId());
        if (snapshot.getSessionId() != null) {
            context.attributes.put("parent.sessionId", snapshot.getSessionId());
        }
        if (snapshot.getTaskPath() != null) {
            context.attributes.put("parent.taskPath", snapshot.getTaskPath());
        }
        context.attributes.put("parent.timestamp", snapshot.getTimestamp());
        
        // 如果有会话信息，创建一个新的会话（避免跨线程共享）
        if (snapshot.hasSession()) {
            String rootTaskName = extractRootTaskName(snapshot.getTaskPath());
            context.startSession(rootTaskName);
        }
        
        return context;
    }
    
    /**
     * 从任务路径中提取根任务名称
     */
    private static String extractRootTaskName(String taskPath) {
        if (taskPath == null || taskPath.trim().isEmpty()) {
            return "restored-task";
        }
        
        String[] parts = taskPath.split("/");
        return parts.length > 0 ? parts[0] : "restored-task";
    }
    
    /**
     * 获取当前任务栈深度
     * 
     * @return 任务栈深度
     */
    public synchronized int getTaskDepth() {
        return taskStack.size();
    }
    
    /**
     * 获取当前任务
     * 
     * @return 当前任务节点，如果栈为空返回null
     */
    public synchronized TaskNode getCurrentTask() {
        return taskStack.peek();
    }

    /**
     * 获取当前会话
     * 
     * @return 当前会话，如果没有返回null
     */
    public Session getCurrentSession() {
        return currentSession;
    }

    /**
     * 设置上下文属性
     * 
     * @param key 属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        checkNotClosed();
        attributes.put(key, value);
    }
    
    /**
     * 获取上下文属性
     * 
     * @param <T> 调用方期望的属性类型；类型正确性由写入方与读取方共同保证
     * @param key 属性键
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
    
    /**
     * 检查上下文是否已关闭
     */
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Context already closed: " + contextId);
        }
    }
    
    /**
     * 按明确结果终止 Context。
     *
     * <p>任务和 Session 状态转换在 Context 锁内完成；引用清理放在同一临界区的 finally，
     * 保证转换异常也不会遗留任务树。注销必须在释放 Context 锁后执行，避免 manager 与
     * lifecycle 之间形成反向重入。
     */
    void finish(ContextOutcome outcome, Throwable cause, String reason) {
        if (outcome == null) {
            throw new IllegalArgumentException("Context outcome cannot be null");
        }

        Throwable terminalFailure = null;
        boolean unregister = false;
        synchronized (this) {
            if (closed) {
                return;
            }

            try {
                if (ownsSessionTermination) {
                    sessionTerminalInProgress = currentSession != null && currentSession.isActive();
                    if (outcome == ContextOutcome.SUCCESS) {
                        completeActiveTasksAndSession();
                    } else {
                        failActiveTasksAndSession(terminalMessage(outcome, cause, reason));
                    }
                }
            } catch (RuntimeException | Error thrown) {
                terminalFailure = thrown;
            } finally {
                sessionTerminalInProgress = false;
                taskStack.clear();
                currentSession = null;
                attributes.clear();
                closed = true;
                unregister = true;
            }
        }

        if (unregister) {
            try {
                SafeContextManager.getInstance().terminalUnbind(this);
            } catch (RuntimeException | Error unregisterFailure) {
                if (terminalFailure == null) {
                    terminalFailure = unregisterFailure;
                } else {
                    terminalFailure.addSuppressed(unregisterFailure);
                }
            }
        }

        rethrowTerminalFailure(terminalFailure);
    }

    /**
     * 以原业务异常终止 Context，供 wrapper 在重新抛出同一异常前记录失败语义。
     */
    void fail(Throwable failure) {
        finish(ContextOutcome.FAILURE, failure, null);
    }

    /**
     * 以基础设施原因强制回收 Context；reason 用于区分 clear、替换、泄漏和 shutdown。
     */
    void forceCleanup(String reason) {
        finish(ContextOutcome.FORCED_CLEANUP, null, reason);
    }

    /**
     * 无锁判断当前 Session 回调是否由本 Context 发起。
     *
     * <p>Session bridge 可能在本 Context monitor 已被持有时回调，因此这里只读取 final/volatile 身份，
     * 不能同步获取 Context monitor；命中后由外层 {@link #finish(ContextOutcome, Throwable, String)} 清理。
     */
    boolean isContextOwnedTerminalTransition(Session session) {
        return ownsSessionTermination && sessionTerminalInProgress && currentSession == session;
    }

    /**
     * 以 identity 而非 Session ID 判断绑定，避免迟到终止误清同线程的后继 Context。
     */
    boolean isBoundToSession(Session session) {
        return currentSession == session;
    }

    /**
     * 响应 Session 已发布的外部终态；该入口只释放 Context，不得反向终止 Session。
     */
    void releaseAfterExternalSessionTerminal(Session session) {
        releaseWithoutSessionTermination(session);
    }

    /**
     * 只断开 wrapper/Context 引用，不调用 Session 终态方法。
     *
     * <p>该原语同时服务 legacy deactivate 与 direct Session terminal；两者都已经由外部决定
     * Session 状态，再次回调 Session 会造成重复终止或锁反转。
     */
    synchronized void releaseWithoutSessionTermination(Session session) {
        if (currentSession != session) {
            return;
        }
        taskStack.clear();
        currentSession = null;
        attributes.clear();
        closed = true;
    }

    /**
     * 正常结束 AutoCloseable 作用域。
     */
    @Override
    public void close() {
        finish(ContextOutcome.SUCCESS, null, null);
    }

    private void completeActiveTasksAndSession() {
        Session session = currentSession;
        TaskNode rootTask = session != null ? session.getRootTask() : null;
        while (!taskStack.isEmpty()) {
            TaskNode task = taskStack.pop();
            if (task != rootTask && task.getStatus().isActive()) {
                task.tryComplete();
            }
        }
        if (session != null && session.isActive()) {
            terminalProbe.beforeSessionTerminal(this, session);
            session.tryComplete();
        }
    }

    private void failActiveTasksAndSession(String message) {
        Session session = currentSession;
        TaskNode rootTask = session != null ? session.getRootTask() : null;
        while (!taskStack.isEmpty()) {
            TaskNode task = taskStack.pop();
            if (task != rootTask && task.getStatus().isActive()) {
                task.tryFail(message);
            }
        }
        if (session != null && session.isActive()) {
            terminalProbe.beforeSessionTerminal(this, session);
            session.tryError(message);
        }
    }

    private static String terminalMessage(ContextOutcome outcome, Throwable cause, String reason) {
        if (outcome == ContextOutcome.FAILURE) {
            if (cause == null) {
                return "Context failed";
            }
            String message = cause.getMessage();
            return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
        }
        return reason == null || reason.isBlank() ? "Context force-cleaned" : reason;
    }

    private static void rethrowTerminalFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    /**
     * 返回从构造到当前读取时点的单调耗时；关闭后仍继续增长，不代表 Session 最终耗时。
     *
     * @return 基于 {@link System#nanoTime()} 的纳秒差值
     */
    public long getElapsedNanos() {
        return System.nanoTime() - createdNanos;
    }
    
    @Override
    public String toString() {
        return String.format("ManagedThreadContext{id='%s', thread='%s', taskDepth=%d, closed=%s}",
                contextId, threadName, taskStack.size(), closed);
    }
}

/**
 * 在 Context 确实准备调用 Session 终态 helper 时触发的确定性测试接缝。
 *
 * <p>生产路径固定使用 {@link #NO_OP}。probe 位于 volatile marker 发布之后，使并发测试可以冻结精确
 * 锁序时点，而不在 manager 中增加第二份运行时状态。
 */
@FunctionalInterface
interface ContextTerminalProbe {
    ContextTerminalProbe NO_OP = (context, session) -> { };

    void beforeSessionTerminal(ManagedThreadContext context, Session session);
}
