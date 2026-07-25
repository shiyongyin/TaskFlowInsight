package com.syy.taskflowinsight.context;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context registry 的包内状态所有者。
 *
 * <p>所有身份迁移和计数在同一把锁内发布，调用方只能在方法返回后执行 Context 生命周期；
 * 这样 replacement/terminal cleanup 即使并发，也不会把一次离开 registry 重复计为 closed。
 */
final class ContextRegistryState {

    private final Object stateLock = new Object();
    private final ConcurrentHashMap<Long, WeakReference<ManagedThreadContext>> registry =
            new ConcurrentHashMap<>();
    private final Map<SuspendedBinding, ManagedThreadContext> unresolvedSuspensions =
            new IdentityHashMap<>();
    private long created;
    private long closed;

    /**
     * 查询 owner 当前身份，并把已经被 GC 清除的 weak slot 记为一次真实关闭。
     *
     * <p>清扫和计数必须在同一临界区完成，否则 lookup 与 purge 可能对同一 slot 重复增加 closed。
     */
    ManagedThreadContext lookup(long ownerThreadId) {
        synchronized (stateLock) {
            WeakReference<ManagedThreadContext> reference = registry.get(ownerThreadId);
            if (reference == null) {
                return null;
            }
            ManagedThreadContext context = reference.get();
            if (context == null && registry.remove(ownerThreadId, reference)) {
                closed++;
            }
            return context;
        }
    }

    /**
     * 发布新身份并返回被替换者，但不在状态锁内执行其生命周期。
     *
     * <p>即使 weak reference 已清空，该 slot 仍代表一个尚未入账的历史身份，因此 replacement
     * 需要同时产生 created +1 与 closed +1。
     */
    RegistryTransition bind(ManagedThreadContext context) {
        synchronized (stateLock) {
            long threadId = context.getThreadId();
            WeakReference<ManagedThreadContext> reference = registry.get(threadId);
            ManagedThreadContext current = reference != null ? reference.get() : null;
            if (current == context) {
                return RegistryTransition.unchanged();
            }

            long closedDelta = 0;
            if (reference != null) {
                closed++;
                closedDelta = 1;
            }
            registry.put(threadId, new WeakReference<>(context));
            created++;
            return new RegistryTransition(true, current, 1, closedDelta);
        }
    }

    /**
     * 仅当 slot 仍指向目标身份时终止绑定，防止迟到的 close 删除后继 Context。
     */
    RegistryTransition terminalUnbind(ManagedThreadContext context) {
        synchronized (stateLock) {
            long threadId = context.getThreadId();
            WeakReference<ManagedThreadContext> reference = registry.get(threadId);
            if (reference == null) {
                return RegistryTransition.unchanged();
            }
            ManagedThreadContext current = reference.get();
            if (current != null && current != context) {
                return RegistryTransition.unchanged();
            }
            if (!registry.remove(threadId, reference)) {
                return RegistryTransition.unchanged();
            }
            closed++;
            return new RegistryTransition(true, current, 0, 1);
        }
    }

    /**
     * 暂时移出当前身份但不改变生命周期计数，为嵌套作用域保留一次性恢复资格。
     */
    SuspendedBinding suspend(ManagedThreadContext context) {
        synchronized (stateLock) {
            WeakReference<ManagedThreadContext> reference = registry.get(context.getThreadId());
            if (reference == null || reference.get() != context
                    || !registry.remove(context.getThreadId(), reference)) {
                return null;
            }
            SuspendedBinding token = new SuspendedBinding(context);
            unresolvedSuspensions.put(token, context);
            return token;
        }
    }

    /**
     * 消费一次性 token 并恢复 prior；只有 exact scope 身份可在恢复时被替换。
     *
     * <p>unrelated occupant 必须保持原状且 token 不被消费，否则调用方无法在正确作用域结束后重试恢复。
     */
    RegistryTransition resume(SuspendedBinding token, ManagedThreadContext expectedScopeContext) {
        synchronized (stateLock) {
            ManagedThreadContext prior = unresolvedSuspensions.get(token);
            if (prior == null) {
                return RegistryTransition.unchanged();
            }

            long threadId = prior.getThreadId();
            WeakReference<ManagedThreadContext> reference = registry.get(threadId);
            ManagedThreadContext current = reference != null ? reference.get() : null;
            if (current != null && current != expectedScopeContext) {
                return RegistryTransition.unchanged();
            }

            long closedDelta = 0;
            if (reference != null) {
                registry.remove(threadId, reference);
                closed++;
                closedDelta = 1;
            }
            unresolvedSuspensions.remove(token);
            token.resolve();
            registry.put(threadId, new WeakReference<>(prior));
            return new RegistryTransition(true, current, 0, closedDelta);
        }
    }

    /**
     * 放弃未恢复的 prior，并把它离开 registry 生命周期记为一次关闭。
     */
    RegistryTransition abandon(SuspendedBinding token) {
        synchronized (stateLock) {
            ManagedThreadContext context = unresolvedSuspensions.remove(token);
            if (context == null) {
                return RegistryTransition.unchanged();
            }
            token.resolve();
            closed++;
            return new RegistryTransition(true, context, 0, 1);
        }
    }

    /**
     * 返回线性一致的活动数与累计计数；读取计数时顺便结算 cleared weak slots。
     */
    RegistryCounts counts() {
        synchronized (stateLock) {
            return new RegistryCounts(collectLiveAndPurgeCleared().size(), created, closed);
        }
    }

    /**
     * 捕获供泄漏检测使用的强引用快照，避免锁释放后 weak identity 在遍历中消失。
     */
    List<ManagedThreadContext> liveContextsAndPurgeCleared() {
        synchronized (stateLock) {
            return List.copyOf(collectLiveAndPurgeCleared());
        }
    }

    /**
     * 一次性排空 active 与 suspended 身份并先完成 closed 计数，生命周期清理由调用方在锁外执行。
     */
    List<ManagedThreadContext> drainLiveContexts() {
        synchronized (stateLock) {
            List<ManagedThreadContext> drained = collectLiveAndPurgeCleared();
            registry.clear();
            closed += drained.size();
            for (Map.Entry<SuspendedBinding, ManagedThreadContext> entry : unresolvedSuspensions.entrySet()) {
                entry.getKey().resolve();
                drained.add(entry.getValue());
                closed++;
            }
            unresolvedSuspensions.clear();
            return List.copyOf(drained);
        }
    }

    /**
     * 在持有 stateLock 时构建 live 快照，并确保每个 cleared slot 只结算一次。
     */
    private List<ManagedThreadContext> collectLiveAndPurgeCleared() {
        List<ManagedThreadContext> live = new ArrayList<>();
        for (Map.Entry<Long, WeakReference<ManagedThreadContext>> entry : registry.entrySet()) {
            WeakReference<ManagedThreadContext> reference = entry.getValue();
            ManagedThreadContext context = reference.get();
            if (context != null) {
                live.add(context);
            } else if (registry.remove(entry.getKey(), reference)) {
                closed++;
            }
        }
        return live;
    }
}

/**
 * 状态锁守护的一次性暂停凭证；volatile 只用于让锁外诊断读取 resolved 可见。
 */
final class SuspendedBinding {
    private final ManagedThreadContext context;
    private volatile boolean resolved;

    SuspendedBinding(ManagedThreadContext context) {
        this.context = context;
    }

    ManagedThreadContext context() {
        return context;
    }

    boolean resolved() {
        return resolved;
    }

    void resolve() {
        resolved = true;
    }
}

/**
 * 一次 registry 迁移的不可变结果，供 manager 在状态锁外执行被替换身份的生命周期动作。
 */
record RegistryTransition(
        boolean changed,
        ManagedThreadContext displaced,
        long createdDelta,
        long closedDelta) {

    static RegistryTransition unchanged() {
        return new RegistryTransition(false, null, 0, 0);
    }
}

/**
 * 同一线性化读取点上的 registry 指标快照。
 */
record RegistryCounts(int active, long created, long closed) { }
