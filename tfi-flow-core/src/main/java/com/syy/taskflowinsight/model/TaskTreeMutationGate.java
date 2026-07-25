package com.syy.taskflowinsight.model;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 协调同一 Session 任务树的变更与冻结捕获。
 *
 * <p>变更使用无超时读锁，确保终态清理不会因导出等待预算而被丢弃；捕获使用公平写锁和有限等待，
 * 防止持续业务写入饿死导出，同时不把锁或 permit 暴露给调用方。该类型只在 model 包内使用，
 * 后继快照捕获必须通过 Session 的词法作用域入口消费。
 */
final class TaskTreeMutationGate {

    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(30);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Duration lockTimeout;

    TaskTreeMutationGate() {
        this(DEFAULT_LOCK_TIMEOUT);
    }

    TaskTreeMutationGate(Duration lockTimeout) {
        this.lockTimeout = Objects.requireNonNull(lockTimeout, "lockTimeout");
        if (lockTimeout.isZero() || lockTimeout.isNegative()) {
            throw new IllegalArgumentException("Lock timeout must be positive");
        }
    }

    <T> T mutate(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return action.get();
        } finally {
            readLock.unlock();
        }
    }

    void mutate(Runnable action) {
        Objects.requireNonNull(action, "action");
        mutate(() -> {
            action.run();
            return null;
        });
    }

    <T> T capture(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        Lock writeLock = lock.writeLock();
        acquireCapture(writeLock);
        try {
            return action.get();
        } finally {
            writeLock.unlock();
        }
    }

    private void acquireCapture(Lock requestedLock) {
        final boolean acquired;
        try {
            acquired = requestedLock.tryLock(lockTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for task tree capture lock", interrupted);
        }
        if (!acquired) {
            throw new IllegalStateException(
                    "Timed out waiting for task tree capture lock after "
                            + lockTimeout.toMillis() + " ms");
        }
    }
}
