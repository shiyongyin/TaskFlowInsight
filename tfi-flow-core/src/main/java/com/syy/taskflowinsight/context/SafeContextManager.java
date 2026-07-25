package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 线程 Context 身份、生命周期监控和泄漏 detector 的唯一运行时 owner。
 *
 * <p>该 Core 组件持有唯一 ThreadLocal、registry 与 cleanup scheduler；
 * {@link ManagedThreadContext} 负责业务生命周期终止，manager 只在线性化身份后于锁外触发生命周期动作。
 * 配置通过 {@link #apply(ContextManagerConfig)} 以完整不可变快照发布，避免多个 setter 产生混合状态。
 *
 * <p>本类线程安全。配置发布与泄漏选择使用不同锁，是为了避免 executor 停止等待或 Context cleanup
 * 反向进入 manager 时扩大临界区；具体锁序约束见字段注释。
 *
 * @since 3.0.0
 */
public final class SafeContextManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SafeContextManager.class);
    
    // 单例实例
    private static final SafeContextManager INSTANCE = new SafeContextManager();
    
    // 线程本地存储（不使用InheritableThreadLocal）
    private static final ThreadLocal<ManagedThreadContext> CONTEXT_LOCAL = new ThreadLocal<>();
    
    // registry 身份和 created/closed 计数由一个包内对象线性化，manager 仍是唯一 public authority。
    private final ContextRegistryState registryState = new ContextRegistryState();

    /*
     * callback 允许在 leakScanLock 内二次获取 configurationLock 来确认 generation；反向嵌套被禁止。
     * executor stop/await 和 Context cleanup 均在两个锁外执行，避免资源等待与业务终止形成锁反转。
     */
    private final Object configurationLock = new Object();
    private final Object leakScanLock = new Object();
    private final ScheduledExecutorFactory scheduledExecutorFactory;
    // volatile record 让锁外 observer 一次读取完整三元组，而不是拼接多个独立字段。
    private volatile ContextManagerConfig currentConfig = ContextManagerConfig.defaults();
    private long detectorGeneration;
    private DetectorRuntime detectorRuntime;
    private boolean shutdown;

    // 异步执行器（仅在 executeAsync 首次使用时创建）
    private volatile ThreadPoolExecutor asyncExecutor;

    // 监控指标
    private final AtomicLong leakDetectedCount = new AtomicLong();
    private final AtomicLong asyncTaskCount = new AtomicLong();
    private final AtomicLong propagationCount = new AtomicLong();
    
    // 泄漏监听器
    private final List<LeakListener> leakListeners = new CopyOnWriteArrayList<>();
    
    /**
     * 私有构造函数
     */
    private SafeContextManager() {
        this(SafeContextManager::newLeakDetector, true);
    }

    SafeContextManager(ScheduledExecutorFactory scheduledExecutorFactory, boolean registerShutdownHook) {
        this.scheduledExecutorFactory = Objects.requireNonNull(
                scheduledExecutorFactory, "ScheduledExecutorFactory cannot be null");
        logger.debug("SafeContextManager initialized with default configuration: " +
            "contextTimeout={}ms, leakDetectionEnabled={}, leakDetectionInterval={}ms",
            currentConfig.timeoutMillis(), currentConfig.leakDetectionEnabled(),
            currentConfig.leakDetectionIntervalMillis());

        if (registerShutdownHook) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "TFI-Shutdown"));
        }
    }
    
    /**
     * 获取单例实例
     * 
     * @return SafeContextManager实例
     */
    public static SafeContextManager getInstance() {
        return INSTANCE;
    }

    /**
     * 原子应用完整 Context 配置，并按需替换泄漏检测 generation。
     *
     * <p>新 detector 必须先成功创建和调度，才能与配置一起发布；任何准备失败都保留旧 generation。
     * 被替换 executor 在 configuration lock 外停止，避免把有界等待放进配置临界区。
     *
     * @param config 已通过构造校验的完整配置
     * @throws NullPointerException 如果 config 为 null
     * @throws IllegalStateException 如果 manager 已关闭
     */
    public void apply(ContextManagerConfig config) {
        Objects.requireNonNull(config, "ContextManagerConfig cannot be null");
        DetectorRuntime detached = null;
        ScheduledExecutorService failedPreparation = null;
        Throwable preparationFailure = null;

        synchronized (configurationLock) {
            ensureRunning();
            if (canReuseDetector(config)) {
                currentConfig = config;
                return;
            }

            DetectorRuntime prepared = null;
            if (config.leakDetectionEnabled()) {
                long nextGeneration = detectorGeneration + 1;
                ScheduledExecutorService executor = null;
                try {
                    executor = Objects.requireNonNull(
                            scheduledExecutorFactory.create("TFI-LeakDetector"),
                            "ScheduledExecutorFactory returned null");
                    ScheduledFuture<?> task = Objects.requireNonNull(
                            executor.scheduleWithFixedDelay(
                                    () -> runScheduledLeakDetection(nextGeneration),
                                    config.leakDetectionIntervalMillis(),
                                    config.leakDetectionIntervalMillis(),
                                    TimeUnit.MILLISECONDS),
                            "Scheduled leak detector task cannot be null");
                    prepared = new DetectorRuntime(nextGeneration, executor, task);
                } catch (RuntimeException | Error failure) {
                    failedPreparation = executor;
                    preparationFailure = failure;
                }
            }

            if (preparationFailure == null) {
                detached = detectorRuntime;
                detectorGeneration++;
                detectorRuntime = prepared;
                currentConfig = config;
            }
        }

        stopExecutor(failedPreparation);
        if (preparationFailure != null) {
            rethrowUnchecked(preparationFailure);
        }
        stopDetector(detached);
    }

    ContextManagerConfig currentConfigSnapshot() {
        return currentConfig;
    }

    void shutdownForTesting() {
        shutdown();
    }

    private boolean canReuseDetector(ContextManagerConfig config) {
        return config.leakDetectionEnabled()
                && detectorRuntime != null
                && detectorRuntime.isLive()
                && currentConfig.leakDetectionEnabled()
                && currentConfig.leakDetectionIntervalMillis()
                        == config.leakDetectionIntervalMillis();
    }

    private void ensureRunning() {
        if (shutdown) {
            throw new IllegalStateException("SafeContextManager is shut down");
        }
    }

    private void runScheduledLeakDetection(long generation) {
        if (!isCurrentGeneration(generation)) {
            return;
        }
        List<LeakCandidate> leakedContexts;
        synchronized (leakScanLock) {
            /*
             * 二次校验与 identity selection 必须对 config publish 保持同一临界区；否则旧 callback
             * 可在校验后、select 前被退休，却仍移除 Context。这里只做 registry 选择，cleanup 仍在锁外。
             */
            synchronized (configurationLock) {
                if (!isCurrentGenerationLocked(generation)) {
                    return;
                }
                leakedContexts = selectAndUnbindLeaks(currentConfig.timeoutMillis());
            }
        }
        cleanupSelectedLeaks(leakedContexts);
    }

    private boolean isCurrentGeneration(long generation) {
        synchronized (configurationLock) {
            return isCurrentGenerationLocked(generation);
        }
    }

    private boolean isCurrentGenerationLocked(long generation) {
        return !shutdown && detectorRuntime != null
                && detectorRuntime.generation() == generation;
    }
    
    /**
     * 获取或创建当前线程的上下文
     * 
     * @return 当前线程的上下文
     */
    public ManagedThreadContext getCurrentContext() {
        ManagedThreadContext context = CONTEXT_LOCAL.get();

        // 验证上下文有效性
        if (context != null && context.isClosed()) {
            CONTEXT_LOCAL.remove();
            registryState.terminalUnbind(context);
            return null;
        }

        return context;
    }

    /**
     * 发布新的当前 Context，并在 registry 状态锁释放后清理被替换身份。
     */
    void bindNewContext(ManagedThreadContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        
        long threadId = Thread.currentThread().threadId();
        if (threadId != context.getThreadId()) {
            throw new IllegalStateException("Context thread mismatch");
        }
        
        RegistryTransition transition = registryState.bind(context);
        CONTEXT_LOCAL.set(context);
        ManagedThreadContext displaced = transition.displaced();
        if (displaced != null && displaced != context && !displaced.isClosed()) {
            logger.warn("Replacing unclosed context: {}", displaced.getContextId());
            displaced.forceCleanup("replaced by new context");
        }
    }
    
    /**
     * 按身份终止绑定；旧 Context 的迟到清理不得移除同线程的后继 Context。
     *
     * @return 目标身份是否真实离开 registry
     */
    boolean terminalUnbind(ManagedThreadContext context) {
        if (context == null) {
            return false;
        }
        
        ManagedThreadContext current = CONTEXT_LOCAL.get();
        if (current == context) {
            CONTEXT_LOCAL.remove();
        }

        return registryState.terminalUnbind(context).changed();
    }

    /**
     * 把独立创建的 Session 适配为当前 Context，但 wrapper 不拥有 Session 终止权。
     *
     * @param session 要绑定的运行中 Session
     * @throws IllegalStateException 如果 Session 不在运行中，或调用线程不是 Session 创建线程
     * @deprecated 仅供 4.0 Session activation adapter 使用；业务代码应创建 ManagedThreadContext
     */
    @Deprecated(since = "4.0.0")
    public void bindLegacySession(Session session) {
        Objects.requireNonNull(session, "Session cannot be null");
        if (!session.isActive()) {
            throw inactiveSession(session);
        }
        if (!Long.toString(Thread.currentThread().threadId()).equals(session.getThreadId())) {
            throw new IllegalStateException(
                    "Session activation must occur on its creation thread");
        }
        ManagedThreadContext current = getCurrentContext();
        if (current != null && current.isBoundToSession(session)) {
            return;
        }

        ManagedThreadContext wrapper = ManagedThreadContext.nonOwning(session);
        bindNewContext(wrapper);
        if (!session.isActive()) {
            wrapper.releaseWithoutSessionTermination(session);
            terminalUnbind(wrapper);
            throw inactiveSession(session);
        }
    }

    /**
     * 释放 Session adapter 的 Context 身份，不改变 Session 业务终态。
     *
     * @param session 要解除当前绑定的 Session
     * @deprecated 仅供 4.0 Session activation adapter 使用
     */
    @Deprecated(since = "4.0.0")
    public void unbindLegacySession(Session session) {
        Objects.requireNonNull(session, "Session cannot be null");
        ManagedThreadContext context = findContextForSession(session);
        if (context == null) {
            return;
        }
        context.releaseWithoutSessionTermination(session);
        terminalUnbind(context);
    }

    /**
     * 在 Session 已发布终态后释放其 owning Context。
     *
     * <p>Session monitor 在进入本方法前必须释放。Context-owned 路径通过 volatile marker
     * 直接返回，由外层 Context 完成清理；direct Session 路径才获取 Context monitor。
     *
     * @param session 已处于终态的 Session
     */
    public void releaseExternallyTerminatedSession(Session session) {
        Objects.requireNonNull(session, "Session cannot be null");
        if (session.isActive()) {
            throw new IllegalStateException("Session must be terminal before external release");
        }

        ManagedThreadContext context = findContextForSession(session);
        if (context == null || context.isContextOwnedTerminalTransition(session)) {
            return;
        }
        if (!context.isBoundToSession(session)) {
            return;
        }
        context.releaseAfterExternalSessionTerminal(session);
        terminalUnbind(context);
    }

    /**
     * 解析 Session 当前唯一 Context owner。
     *
     * <p>正常 owner 线程优先读取现有 ThreadLocal，避免 registry 锁；泄漏检测和 shutdown 可能从其他
     * 线程触发 Context-owned terminal，因此再按 Session 创建线程 ID 查询唯一 registry。两条路径都只做
     * volatile identity 判断，不获取 Context monitor。
     */
    private ManagedThreadContext findContextForSession(Session session) {
        ManagedThreadContext current = CONTEXT_LOCAL.get();
        if (current != null && current.isBoundToSession(session)) {
            return current;
        }
        long ownerThreadId = Long.parseLong(session.getThreadId());
        ManagedThreadContext owner = registryState.lookup(ownerThreadId);
        return owner != null && owner.isBoundToSession(session) ? owner : null;
    }

    private static IllegalStateException inactiveSession(Session session) {
        return new IllegalStateException(
                "Cannot activate session that is not running. Current status: " + session.getStatus());
    }

    /**
     * 暂停当前绑定但不结束其生命周期，供后续唯一 ContextScope 实现嵌套恢复。
     */
    SuspendedBinding suspendCurrentContext() {
        ManagedThreadContext current = CONTEXT_LOCAL.get();
        if (current == null) {
            return null;
        }
        SuspendedBinding token = registryState.suspend(current);
        if (token == null) {
            throw new IllegalStateException("Current Context binding could not be suspended");
        }
        CONTEXT_LOCAL.remove();
        return token;
    }

    /**
     * 为临时传播创建 linked child，但不清理调用线程原绑定。
     *
     * <p>调用方必须先 suspend prior。构造与绑定分开，是为了让失败在发布新 ThreadLocal 前结束，
     * 由 {@link ContextScope} 可靠地恢复 prior。</p>
     */
    ManagedThreadContext restoreForScope(ContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }
        ManagedThreadContext restored = ManagedThreadContext.restoreUnboundFromSnapshot(snapshot);
        try {
            bindNewContext(restored);
            return restored;
        } catch (RuntimeException | Error restoreFailure) {
            clearCurrentBinding(restored);
            try {
                if (!restored.isClosed()) {
                    restored.forceCleanup("snapshot restore failed");
                }
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != restoreFailure) {
                    restoreFailure.addSuppressed(cleanupFailure);
                }
            }
            throw restoreFailure;
        }
    }

    /**
     * 执行 public API 的破坏式恢复：旧绑定先按 replacement 终止，再发布快照 child。
     */
    ManagedThreadContext restoreDestructively(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        ManagedThreadContext current = getCurrentContext();
        if (current != null && !current.isClosed()) {
            current.forceCleanup("replaced by snapshot restore");
        }
        ManagedThreadContext restored = restoreForScope(snapshot);
        recordPropagation();
        return restored;
    }

    /**
     * 仅在当前 occupant 与调用方声明的 scope 身份一致时恢复 prior。
     *
     * <p>状态先在线性化锁内发布，scope 的强制清理后置到锁外，避免 lifecycle 反向进入 manager。
     */
    void resumeContext(SuspendedBinding token, ManagedThreadContext expectedScopeContext) {
        if (token == null) {
            throw new IllegalArgumentException("Suspended binding cannot be null");
        }
        if (token.resolved()) {
            throw new IllegalStateException("Suspended binding has already been resolved");
        }
        RegistryTransition transition = registryState.resume(token, expectedScopeContext);
        if (!transition.changed()) {
            throw new IllegalStateException("Suspended Context could not be resumed over current binding");
        }
        CONTEXT_LOCAL.set(token.context());
        ManagedThreadContext displaced = transition.displaced();
        Throwable primary = null;
        try {
            if (displaced != null && !displaced.isClosed()) {
                displaced.forceCleanup("scope context replaced during resume");
            }
        } catch (RuntimeException | Error cleanupFailure) {
            primary = cleanupFailure;
        }
        try {
            releaseResumedContextIfSessionTerminated(token.context());
        } catch (RuntimeException | Error releaseFailure) {
            if (primary == null) {
                primary = releaseFailure;
            } else if (releaseFailure != primary) {
                primary.addSuppressed(releaseFailure);
            }
        }
        rethrowUnchecked(primary);
    }

    private void releaseResumedContextIfSessionTerminated(ManagedThreadContext context) {
        Session session = context.getCurrentSession();
        if (session == null || !session.isTerminated()) {
            return;
        }
        context.releaseAfterExternalSessionTerminal(session);
        terminalUnbind(context);
    }

    /**
     * scope 无法正常恢复时消费 token 并在状态锁外回收 prior，避免 unresolved suspension 泄漏。
     */
    void abandonSuspendedContext(SuspendedBinding token, String reason) {
        if (token == null) {
            throw new IllegalArgumentException("Suspended binding cannot be null");
        }
        RegistryTransition transition = registryState.abandon(token);
        ManagedThreadContext abandoned = transition.displaced();
        if (abandoned != null && !abandoned.isClosed()) {
            abandoned.forceCleanup(reason == null || reason.isBlank() ? "suspended context abandoned" : reason);
        }
    }

    /**
     * 只清除调用方持有的 exact scope，避免异常清理误伤已恢复或后来创建的 Context。
     */
    void clearCurrentBinding(ManagedThreadContext expectedScopeContext) {
        if (expectedScopeContext == null || CONTEXT_LOCAL.get() != expectedScopeContext) {
            return;
        }
        CONTEXT_LOCAL.remove();
        registryState.terminalUnbind(expectedScopeContext);
    }
    
    /**
     * 异步执行无返回值的具名任务。
     *
     * <p>该重载只负责把 Runnable 适配为 Callable；named task、独立 Context 和完成屏障语义
     * 统一由 {@link #executeAsync(String, Callable)} 保证，避免两个重载形成不同生命周期。</p>
     *
     * @param taskName 非空白的可观察任务名
     * @param task 非 null 的任务
     * @return 在全部 Context 资源收尾后完成的任务 Future
     */
    public CompletableFuture<Void> executeAsync(String taskName, Runnable task) {
        return executeAsync(taskName, () -> {
            task.run();
            return null;
        });
    }
    
    /**
     * 异步执行具名任务，并在独立的执行期 Context 中记录完整终态。
     *
     * <p>有提交方快照时创建 linked-child Session，避免跨线程共享可变任务树；无快照时创建以
     * {@code taskName} 命名的 owned root。Future 只在 task、Session 和临时绑定全部收尾后完成，
     * 因而调用方从 {@code get()} 返回时不会再观察到活动 Context。</p>
     *
     * @param taskName 非空白的可观察任务名；同时作为 named child 或 owned root 的名称
     * @param task 非 null 的任务
     * @param <T> 返回类型
     * @return 在全部 Context 资源收尾后完成的任务 Future
     */
    public <T> CompletableFuture<T> executeAsync(String taskName, Callable<T> task) {
        asyncTaskCount.incrementAndGet();
        ManagedThreadContext submittingContext = getCurrentContext();
        ContextSnapshot snapshot = submittingContext != null
                ? submittingContext.createSnapshot() : null;
        CompletableFuture<T> future = new CompletableFuture<>();

        getAsyncExecutor().execute(() -> {
            T result = null;
            Throwable primary = null;
            try (ContextScope scope = ContextScope.open(snapshot);
                    ManagedThreadContext root = snapshot == null
                            ? ManagedThreadContext.create(taskName) : null) {
                ManagedThreadContext context = root != null ? root : scope.context();
                TaskNode namedTask = root != null
                        ? context.getCurrentTask() : context.startTask(taskName);
                try {
                    result = task.call();
                    if (root == null) {
                        context.endTask();
                    }
                } catch (Throwable businessFailure) {
                    primary = businessFailure;
                    try {
                        failNamedAsyncTask(context, namedTask, businessFailure);
                    } catch (RuntimeException | Error taskCleanupFailure) {
                        addSuppressed(businessFailure, taskCleanupFailure);
                    }
                    try {
                        if (root == null) {
                            scope.fail(businessFailure);
                        } else {
                            root.fail(businessFailure);
                        }
                    } catch (RuntimeException | Error scopeCleanupFailure) {
                        addSuppressed(businessFailure, scopeCleanupFailure);
                    }
                }
            } catch (Throwable resourceFailure) {
                if (primary == null) {
                    primary = resourceFailure;
                } else {
                    addSuppressed(primary, resourceFailure);
                }
            }

            /*
             * Future 是调用方可见的完成屏障，必须晚于 try-with-resources；否则 get() 返回时
             * worker 仍可能持有 child/root，造成短暂但真实的 active-context 泄漏观测。
             */
            if (primary == null) {
                future.complete(result);
                return;
            }
            logger.error("Async task failed: {}", taskName, primary);
            future.completeExceptionally(primary);
            if (primary instanceof Error error) {
                // Future 先解除等待，再保留 JVM 级故障的 executor uncaught-error 语义。
                throw error;
            }
        });

        return future;
    }

    /**
     * 先把业务异常归因到 API 显式命名的节点，再由 Context 终止 Session。
     *
     * <p>若只调用 {@link ManagedThreadContext#fail(Throwable)}，Context 会用归一化字符串批量终止
     * 活动任务，named node 将失去原始异常消息。子任务成功标记失败后立即出栈，是为了让后续
     * Context failure 只负责 root/Session 终态；标记本身失败时保留栈，交给统一终止路径兜底。</p>
     */
    private void failNamedAsyncTask(
            ManagedThreadContext context, TaskNode namedTask, Throwable failure) {
        namedTask.fail(failure);
        if (namedTask.getParent() != null && context.getCurrentTask() == namedTask) {
            context.endTask();
        }
    }

    private static void addSuppressed(Throwable primary, Throwable secondary) {
        if (primary != null && secondary != null && secondary != primary) {
            primary.addSuppressed(secondary);
        }
    }
    
    /**
     * 捕获提交线程快照，并在执行时通过可恢复作用域传播。
     *
     * <p>即使提交时没有 Context 也保留 null snapshot，使执行线程的污染绑定在 delegate 期间不可见。</p>
     *
     * @param runnable 原始任务
     * @return 装饰后的任务
     */
    public Runnable wrapRunnable(Runnable runnable) {
        ContextSnapshot snapshot = null;
        ManagedThreadContext current = getCurrentContext();
        if (current != null) {
            snapshot = current.createSnapshot();
        }
        
        return new ContextAwareRunnable(runnable, snapshot);
    }
    
    /**
     * 捕获提交线程快照，并在执行时通过可恢复作用域传播。
     *
     * <p>业务异常保持原对象；failure signal 与 scope close 的异常只作为 suppressed 附加。</p>
     *
     * @param callable 原始任务
     * @param <T> 返回类型
     * @return 装饰后的任务
     */
    public <T> Callable<T> wrapCallable(Callable<T> callable) {
        ContextSnapshot snapshot = null;
        ManagedThreadContext current = getCurrentContext();
        if (current != null) {
            snapshot = current.createSnapshot();
        }
        
        return new ContextAwareCallable<>(callable, snapshot);
    }
    
    /**
     * 检测并清理泄漏的上下文
     * 对测试可见，用于清理测试环境
     */
    void detectAndCleanLeaks() {
        List<LeakCandidate> leakedContexts;
        synchronized (leakScanLock) {
            leakedContexts = selectAndUnbindLeaks(currentConfig.timeoutMillis());
        }
        cleanupSelectedLeaks(leakedContexts);
    }

    /**
     * 在唯一 scan 临界区内选择并移除泄漏身份，生命周期动作由调用方锁外执行。
     *
     * <p>先 identity-unbind 是为了让旧 generation、manual scan 与 shutdown 无法重复选择同一个 Context；
     * Context cleanup 可能进入 Session/listener 代码，不能占用 scan lock。
     */
    private List<LeakCandidate> selectAndUnbindLeaks(long timeoutMillis) {
        List<LeakCandidate> selected = new java.util.ArrayList<>();
        for (ManagedThreadContext context : registryState.liveContextsAndPurgeCleared()) {
            boolean threadAlive = context.isOwnerThreadAlive();
            long contextAge = (System.nanoTime() - context.getCreatedNanos()) / 1_000_000;
            if (threadAlive && contextAge <= timeoutMillis) {
                continue;
            }

            String reason = threadAlive ? "timeout" : "dead thread";
            if (registryState.terminalUnbind(context).changed()) {
                leakDetectedCount.incrementAndGet();
                selected.add(new LeakCandidate(context, reason, contextAge));
            }
        }
        return selected;
    }

    private void cleanupSelectedLeaks(List<LeakCandidate> leakedContexts) {
        for (LeakCandidate leak : leakedContexts) {
            ManagedThreadContext leaked = leak.context();
            logger.warn("Detected leaked context: {}, reason: {}, age: {}ms",
                    leaked.getContextId(), leak.reason(), leak.ageMillis());
            try {
                if (!leaked.isClosed()) {
                    leaked.forceCleanup(leak.reason());
                }
            } catch (RuntimeException | Error failure) {
                logger.warn("Failed to close leaked context: {}", leaked.getContextId(), failure);
            }
            notifyLeakListeners(leaked);
        }
    }
    
    /**
     * 通知泄漏监听器
     * 
     * @param context 泄漏的上下文
     */
    private void notifyLeakListeners(ManagedThreadContext context) {
        for (LeakListener listener : leakListeners) {
            try {
                listener.onLeakDetected(context);
            } catch (Exception e) {
                logger.warn("Leak listener failed", e);
            }
        }
    }
    
    /**
     * 注册泄漏监听器
     * 
     * @param listener 监听器
     */
    public void registerLeakListener(LeakListener listener) {
        if (listener != null) {
            leakListeners.add(listener);
        }
    }
    
    /**
     * 移除泄漏监听器
     * 
     * @param listener 监听器
     */
    public void unregisterLeakListener(LeakListener listener) {
        leakListeners.remove(listener);
    }
    
    /**
     * 获取活动上下文数量
     *
     * @return 活动上下文数量
     */
    public int getActiveContextCount() {
        return registryState.counts().active();
    }

    /**
     * 获取累计创建的上下文数量。
     *
     * @return 上下文创建总数
     */
    public long getContextCreatedCount() {
        return registryState.counts().created();
    }

    /**
     * 获取累计关闭的上下文数量。
     *
     * @return 上下文关闭总数
     */
    public long getContextClosedCount() {
        return registryState.counts().closed();
    }

    /**
     * 获取累计检测到的泄漏数量。
     *
     * @return 泄漏检测总数
     */
    public long getLeakDetectedCount() {
        return leakDetectedCount.get();
    }

    /**
     * 获取累计提交的异步任务数量。
     *
     * @return 异步任务总数
     */
    public long getAsyncTaskCount() {
        return asyncTaskCount.get();
    }

    /**
     * 获取异步执行器当前线程池大小。
     *
     * @return 当前线程池大小
     */
    public int getAsyncExecutorPoolSize() {
        ThreadPoolExecutor executor = asyncExecutor;
        return executor != null ? executor.getPoolSize() : 0;
    }

    /**
     * 获取异步执行器当前队列大小。
     *
     * @return 当前队列大小
     */
    public int getAsyncExecutorQueueSize() {
        ThreadPoolExecutor executor = asyncExecutor;
        return executor != null ? executor.getQueue().size() : 0;
    }

    /**
     * 捕获一次 Context 运行态指标。
     *
     * <p>registry 三项必须从同一个 {@link RegistryCounts} 读取，否则并发创建或关闭会让调用方看到
     * 不可能同时成立的计数。executor 同样只读取一个引用，避免 pool/queue 来自两个实例。
     *
     * @return 本次观测的不可变指标
     */
    public ContextMetrics metrics() {
        RegistryCounts counts = registryState.counts();
        ThreadPoolExecutor executor = asyncExecutor;
        return new ContextMetrics(
                counts.active(),
                counts.created(),
                counts.closed(),
                leakDetectedCount.get(),
                asyncTaskCount.get(),
                executor != null ? executor.getPoolSize() : 0,
                executor != null ? executor.getQueue().size() : 0,
                propagationCount.get(),
                Instant.now());
    }

    /** 传播只在快照成功应用后计数，避免把仅创建 wrapper 的调用误报为实际传播。 */
    void recordPropagation() {
        propagationCount.incrementAndGet();
    }

    /**
     * 清理所有活动上下文（仅供测试使用）
     * @deprecated 仅供测试使用，生产环境不应调用
     */
    @Deprecated
    void clearAllContextsForTesting() {
        forceCleanupAll("test cleanup");
        CONTEXT_LOCAL.remove();
    }

    /**
     * 使用同一强制终止入口回收当前注册表中的 Context。
     *
     * <p>shutdown 与测试清理共享该实现，是为了保证批量回收也携带明确原因，并继续由
     * {@link ManagedThreadContext#finish(ContextOutcome, Throwable, String)} 在锁外完成注销。
     */
    void forceCleanupAll(String reason) {
        List<ManagedThreadContext> drained;
        synchronized (leakScanLock) {
            drained = registryState.drainLiveContexts();
        }
        for (ManagedThreadContext context : drained) {
            try {
                if (context != null && !context.isClosed()) {
                    context.forceCleanup(reason);
                }
            } catch (RuntimeException | Error failure) {
                logger.warn("Failed to force-clean context, reason={}", reason, failure);
            }
        }
    }
    
    /**
     * 关闭管理器
     *
     * <p>先在 configuration lock 内发布 terminal generation，再在锁外停止 detector 和异步执行器；
     * 因此 shutdown 返回后，任何 apply 都不能复活 scheduler。
     */
    private void shutdown() {
        logger.info("Shutting down SafeContextManager");

        DetectorRuntime detached;
        synchronized (configurationLock) {
            if (shutdown) {
                return;
            }
            shutdown = true;
            detectorGeneration++;
            detached = detectorRuntime;
            detectorRuntime = null;
        }
        stopDetector(detached);

        // 关闭钩子属于基础设施强制回收，不能把仍在运行的 Session 记为正常完成。
        forceCleanupAll("manager shutdown");
        
        // 关闭执行器
        ThreadPoolExecutor executor = asyncExecutor;
        if (executor != null) {
            executor.shutdown();
        }

        try {
            if (executor != null && !executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ThreadPoolExecutor getAsyncExecutor() {
        ThreadPoolExecutor executor = asyncExecutor;
        if (executor != null) {
            return executor;
        }

        synchronized (this) {
            if (asyncExecutor == null) {
                asyncExecutor = createAsyncExecutor();
            }
            return asyncExecutor;
        }
    }

    private ThreadPoolExecutor createAsyncExecutor() {
        return new ThreadPoolExecutor(
            10, // 核心线程数
            50, // 最大线程数
            60L, TimeUnit.SECONDS, // 空闲线程存活时间
            new LinkedBlockingQueue<>(1000), // 任务队列
            new ThreadFactory() {
                private final AtomicLong counter = new AtomicLong();

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "TFI-Async-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );
    }

    private static ScheduledExecutorService newLeakDetector(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void stopDetector(DetectorRuntime runtime) {
        if (runtime == null) {
            return;
        }
        runtime.task().cancel(false);
        stopExecutor(runtime.executor());
    }

    private static void stopExecutor(ScheduledExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /** 把 generation、executor 与 task 绑定为一个替换单元，防止发布只完成一半。 */
    private record DetectorRuntime(
            long generation, ScheduledExecutorService executor, ScheduledFuture<?> task) {

        private boolean isLive() {
            return !executor.isShutdown() && !task.isCancelled() && !task.isDone();
        }
    }

    /** 跨出 scan lock 携带清理证据，避免锁外重新读取已变化的 registry 状态。 */
    private record LeakCandidate(
            ManagedThreadContext context, String reason, long ageMillis) {
    }
    
    /**
     * 泄漏监听器接口
     */
    public interface LeakListener {
        /**
         * 接收已被检测器判定为泄漏候选的上下文。
         *
         * @param context 泄漏检测时仍未正常关闭的上下文
         */
        void onLeakDetected(ManagedThreadContext context);
    }
    
    /**
     * 上下文感知的Runnable包装器
     */
    private static class ContextAwareRunnable implements Runnable {
        private final Runnable delegate;
        private final ContextSnapshot snapshot;

        ContextAwareRunnable(Runnable delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }

        @Override
        public void run() {
            try (ContextScope scope = ContextScope.open(snapshot)) {
                try {
                    delegate.run();
                } catch (RuntimeException | Error failure) {
                    failPreservingPrimary(scope, failure);
                    throw failure;
                }
            }
        }
    }

    /**
     * 上下文感知的Callable包装器
     */
    private static class ContextAwareCallable<T> implements Callable<T> {
        private final Callable<T> delegate;
        private final ContextSnapshot snapshot;

        ContextAwareCallable(Callable<T> delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }

        @Override
        public T call() throws Exception {
            try (ContextScope scope = ContextScope.open(snapshot)) {
                try {
                    return delegate.call();
                } catch (Exception | Error failure) {
                    failPreservingPrimary(scope, failure);
                    throw failure;
                }
            }
        }
    }

    private static void failPreservingPrimary(ContextScope scope, Throwable primary) {
        try {
            scope.fail(primary);
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != primary) {
                primary.addSuppressed(cleanupFailure);
            }
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

}

/**
 * 隔离 scheduler construction，使失败、竞态和 generation 测试无需依赖 sleep 或真实时间。
 *
 * <p>接口保持 package-private，生产环境仍只能由 {@link SafeContextManager} 创建并拥有 detector。
 */
@FunctionalInterface
interface ScheduledExecutorFactory {
    ScheduledExecutorService create(String threadName);
}
