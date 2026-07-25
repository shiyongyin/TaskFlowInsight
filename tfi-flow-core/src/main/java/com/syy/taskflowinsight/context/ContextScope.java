package com.syy.taskflowinsight.context;

/**
 * 一次传播调用的临时 Context 绑定作用域。
 *
 * <p>作用域把 prior suspension token 保存在调用栈对象中，而不是新增 ThreadLocal。
 * 线程池 worker 因而能在传播任务结束后恢复原身份，同时仍由 {@link SafeContextManager}
 * 作为唯一绑定和 registry owner。
 * same-source 分支只借用当前 Context；只有本作用域创建的 linked child 才能被 fail/close。</p>
 */
final class ContextScope implements AutoCloseable {

    private final SafeContextManager manager;
    private final SuspendedBinding previous;
    private final ManagedThreadContext active;
    private final boolean ownsActive;
    private final boolean suspended;
    private boolean closed;

    private ContextScope(
            SafeContextManager manager,
            SuspendedBinding previous,
            ManagedThreadContext active,
            boolean ownsActive,
            boolean suspended) {
        this.manager = manager;
        this.previous = previous;
        this.active = active;
        this.ownsActive = ownsActive;
        this.suspended = suspended;
    }

    /**
     * 应用快照并建立可恢复的临时绑定。
     *
     * <p>null 快照也必须暂停污染 worker，否则“提交时无 Context”的任务
     * 会意外继承执行线程状态。
     * non-null 快照只有成功应用后才计数；失败路径先恢复 prior，恢复也失败时再终止消费
     * token。</p>
     */
    static ContextScope open(ContextSnapshot snapshot) {
        SafeContextManager manager = SafeContextManager.getInstance();
        ManagedThreadContext current = manager.getCurrentContext();
        if (snapshot == null) {
            if (current == null) {
                return new ContextScope(manager, null, null, false, false);
            }
            SuspendedBinding previous = manager.suspendCurrentContext();
            return new ContextScope(manager, previous, null, false, true);
        }
        if (snapshot.matches(current)) {
            manager.recordPropagation();
            return new ContextScope(manager, null, current, false, false);
        }

        SuspendedBinding previous = manager.suspendCurrentContext();
        try {
            ManagedThreadContext restored = manager.restoreForScope(snapshot);
            manager.recordPropagation();
            return new ContextScope(manager, previous, restored, true, true);
        } catch (RuntimeException | Error restoreFailure) {
            try {
                restorePrevious(manager, previous, null);
            } catch (RuntimeException | Error resumeFailure) {
                addSuppressed(restoreFailure, resumeFailure);
                abandonPrevious(manager, previous, restoreFailure);
            }
            throw restoreFailure;
        }
    }

    ManagedThreadContext context() {
        return active;
    }

    /**
     * 把业务失败写入本作用域拥有的 child；借用的 same-source Context
     * 由外层调用者决定终态。
     */
    void fail(Throwable failure) {
        if (ownsActive && active != null) {
            active.fail(failure);
        }
    }

    /**
     * 先结束 owned child，再恢复 prior；恢复失败时必须消费 token，
     * 不能遗留未注册的运行中 Context。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!suspended) {
            return;
        }

        Throwable primary = null;
        try {
            if (ownsActive && active != null && !active.isClosed()) {
                active.close();
            }
        } catch (RuntimeException | Error closeFailure) {
            primary = closeFailure;
        }
        try {
            restorePrevious(manager, previous, active);
        } catch (RuntimeException | Error resumeFailure) {
            if (primary == null) {
                primary = resumeFailure;
            } else {
                addSuppressed(primary, resumeFailure);
            }
            abandonPrevious(manager, previous, primary);
        }

        rethrowUnchecked(primary);
    }

    private static void restorePrevious(
            SafeContextManager manager,
            SuspendedBinding previous,
            ManagedThreadContext expectedActive) {
        if (previous == null) {
            manager.clearCurrentBinding(expectedActive);
        } else {
            manager.resumeContext(previous, expectedActive);
        }
    }

    private static void abandonPrevious(
            SafeContextManager manager, SuspendedBinding previous, Throwable primary) {
        if (previous == null) {
            return;
        }
        try {
            manager.abandonSuspendedContext(previous, "Context resume failed");
        } catch (RuntimeException | Error abandonFailure) {
            addSuppressed(primary, abandonFailure);
        }
    }

    private static void addSuppressed(Throwable primary, Throwable secondary) {
        if (primary != null && secondary != null && secondary != primary) {
            primary.addSuppressed(secondary);
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
