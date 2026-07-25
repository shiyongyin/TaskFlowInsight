package com.syy.taskflowinsight.store;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阻止 Caffeine refresh 把外部 loader Throwable 写入其内部 {@link System.Logger}。
 *
 * <p>Caffeine 对取消的 refresh 不记录 Throwable，并按失败保留旧值与原 write age。本包装器把
 * 进入 Caffeine 的外部异常转成内部取消；显式 refresh 仍走 Caffeine 唯一写回状态机，但通过请求桥
 * 向调用者保留原始异常、取消和 Error 语义。普通 get/load 仍原样传播，由 Store 固定分类日志处理。</p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 */
final class ConfidentialCacheLoader<K, V> implements CacheLoader<K, V> {

    /** 业务提供的真实 loader。 */
    private final CacheLoader<K, V> delegate;
    /** 显式 refresh 请求桥；生命周期严格绑定到仍在执行的 loader future。 */
    private final ConcurrentMap<K, ExplicitRefresh<V>> explicitRefreshes =
            new ConcurrentHashMap<>();

    /* default */ ConfidentialCacheLoader(final CacheLoader<K, V> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    /* default */ CompletableFuture<V> refresh(final LoadingCache<K, V> cache, final K key) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(key, "key");
        final ExplicitRefresh<V> created = new ExplicitRefresh<>();
        ExplicitRefresh<V> active;
        boolean retry;
        do {
            active = explicitRefreshes.putIfAbsent(key, created);
            retry = active != null && active.future().isDone();
            if (retry) {
                explicitRefreshes.remove(key, active);
            }
        } while (retry);
        final CompletableFuture<V> refreshResult;
        if (active == null) {
            created.future().whenComplete((value, failure) ->
                    explicitRefreshes.remove(key, created));
            try {
                // Caffeine 保持唯一写回状态机；request 只暴露脱敏前的显式调用结果。
                created.bind(cache.refresh(key));
            } catch (Throwable failure) {
                created.fail(failure);
            }
            refreshResult = created.future();
        } else {
            refreshResult = active.future();
        }
        return refreshResult;
    }

    @Override
    public V load(final K key) throws Exception {
        return delegate.load(key);
    }

    @Override
    @SuppressWarnings({"PMD.AvoidCatchingThrowable", "PMD.LocalVariableCouldBeFinal"})
    public CompletableFuture<? extends V> asyncLoad(
            final K key,
            final Executor executor) {
        CompletableFuture<? extends V> sanitized;
        try {
            final CompletableFuture<? extends V> loading = Objects.requireNonNull(
                    delegate.asyncLoad(key, executor), "asyncLoad future");
            bindExplicit(key, loading);
            sanitized = cancelOnFailure(loading);
        } catch (Throwable failure) {
            failExplicit(key, failure);
            sanitized = cancelledFuture(failure);
        }
        return sanitized;
    }

    @Override
    @SuppressWarnings({"PMD.AvoidCatchingThrowable", "PMD.LocalVariableCouldBeFinal"})
    public CompletableFuture<? extends V> asyncReload(
            final K key,
            final V oldValue,
            final Executor executor) {
        CompletableFuture<? extends V> sanitized;
        try {
            final CompletableFuture<? extends V> loading = Objects.requireNonNull(
                    delegate.asyncReload(key, oldValue, executor), "asyncReload future");
            bindExplicit(key, loading);
            sanitized = cancelOnFailure(loading);
        } catch (Throwable failure) {
            failExplicit(key, failure);
            sanitized = cancelledFuture(failure);
        }
        return sanitized;
    }

    private void bindExplicit(final K key, final CompletableFuture<? extends V> loading) {
        final ExplicitRefresh<V> refresh = explicitRefreshes.get(key);
        if (refresh != null) {
            refresh.bind(loading);
        }
    }

    private void failExplicit(final K key, final Throwable failure) {
        final ExplicitRefresh<V> refresh = explicitRefreshes.get(key);
        if (refresh != null) {
            refresh.fail(failure);
        }
    }

    private static <T> CompletableFuture<? extends T> cancelOnFailure(
            final CompletableFuture<? extends T> delegateFuture) {
        final CompletableFuture<T> sanitized = new SanitizedRefreshFuture<>(delegateFuture);
        delegateFuture.whenComplete((value, failure) -> {
            if (failure == null) {
                sanitized.complete(value);
            } else {
                restoreInterrupt(failure);
                sanitized.cancel(false);
            }
        });
        return sanitized;
    }

    private static <T> CompletableFuture<? extends T> cancelledFuture(
            final Throwable failure) {
        restoreInterrupt(failure);
        final CompletableFuture<T> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        return cancelled;
    }

    private static Throwable unwrapCompletion(final Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void restoreInterrupt(final Throwable failure) {
        final Throwable current = unwrapCompletion(failure);
        if (current instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 仅向 Caffeine 暴露脱敏完成态，同时保留显式 refresh 所需的原始 loader 控制面。
     *
     * @param <T> loader 值类型
     */
    private static final class SanitizedRefreshFuture<T> extends CompletableFuture<T> {

        /** 真实 loader future；显式调用用它保留失败原因并传播取消。 */
        private final CompletableFuture<? extends T> loaderFuture;

        private SanitizedRefreshFuture(final CompletableFuture<? extends T> loaderFuture) {
            super();
            this.loaderFuture = loaderFuture;
        }

    }

    /** 将一个真实 loader future 安全地桥接给全部同 key 显式调用者。 */
    private static final class ExplicitRefresh<T> {

        /** 调用方共享的完成结果。 */
        private final CompletableFuture<T> completion = new CompletableFuture<>();
        /** 第一个真实 loader 或已有 Caffeine refresh future。 */
        private final AtomicReference<CompletableFuture<? extends T>> loading =
                new AtomicReference<>();

        private ExplicitRefresh() {
            completion.whenComplete((value, failure) -> {
                final CompletableFuture<? extends T> active = loading.get();
                if (completion.isCancelled() && active != null) {
                    active.cancel(false);
                }
            });
        }

        private CompletableFuture<T> future() {
            return completion;
        }

        @SuppressWarnings("unchecked")
        private void bind(final CompletableFuture<? extends T> candidate) {
            CompletableFuture<? extends T> rawCandidate = candidate;
            if (candidate instanceof SanitizedRefreshFuture<?> sanitized) {
                rawCandidate = (CompletableFuture<? extends T>) sanitized.loaderFuture;
            }
            if (!loading.compareAndSet(null, rawCandidate)) {
                return;
            }
            if (completion.isCancelled()) {
                rawCandidate.cancel(false);
            }
            rawCandidate.whenComplete(this::complete);
        }

        private void fail(final Throwable failure) {
            final CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(failure);
            bind(failed);
        }

        private void complete(final T value, final Throwable failure) {
            if (failure == null) {
                completion.complete(value);
                return;
            }
            final Throwable cause = unwrapCompletion(failure);
            restoreInterrupt(cause);
            if (cause instanceof CancellationException) {
                completion.cancel(false);
            } else {
                completion.completeExceptionally(cause);
            }
        }
    }
}
