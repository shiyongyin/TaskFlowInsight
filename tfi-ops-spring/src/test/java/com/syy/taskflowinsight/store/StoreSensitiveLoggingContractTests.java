package com.syy.taskflowinsight.store;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Store 运行日志不得把调用方控制的 key、value 或异常文本带出进程边界。
 */
@ExtendWith(OutputCaptureExtension.class)
class StoreSensitiveLoggingContractTests {

    /** 任何编码或大小写形态都不应进入日志的唯一测试 key。 */
    private static final String CANARY_KEY = "TFI-Secret-Key/Customer-7429";

    /** Store 不得记录的调用方业务 value。 */
    private static final String CANARY_VALUE = "TFI-Private-Value/Account-3081";

    /** 外部 loader 异常中不得进入日志的宿主细节。 */
    private static final String CANARY_EXCEPTION = "TFI-Loader-Failure/Tenant-9917";

    @Test
    void caffeineEvictionLogContainsCauseButNoBusinessKey() throws Exception {
        try (LogCapture capture = LogCapture.start(
                CaffeineStore.class, Level.DEBUG, "Cache eviction")) {
            StoreConfig config = StoreConfig.builder()
                    .maxSize(1)
                    .defaultTtl(Duration.ofMinutes(1))
                    .logEvictions(true)
                    .build();
            CaffeineStore<String, String> store = new CaffeineStore<>(config);

            store.put(CANARY_KEY, CANARY_VALUE);
            store.remove(CANARY_KEY);
            store.cleanUp();

            assertThat(capture.awaitExpected()).as("Caffeine removal log must be observed").isTrue();
            assertNoSensitiveText(capture);
        }
    }

    @Test
    void fifoLogsContainOnlyFixedEventsAndCounts() throws Exception {
        try (LogCapture capture = LogCapture.start(
                FifoCaffeineStore.class,
                Level.DEBUG,
                "Cache put",
                "FIFO evicted oldest",
                "Cache entry removed")) {
            StoreConfig config = StoreConfig.builder()
                    .maxSize(1)
                    .defaultTtl(Duration.ofMinutes(1))
                    .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
                    .build();
            FifoCaffeineStore<String, String> store = new FifoCaffeineStore<>(config);

            store.put(CANARY_KEY, CANARY_VALUE);
            store.put("replacement", "public-value");

            assertThat(capture.awaitExpected()).as("all FIFO log paths must be observed").isTrue();
            assertNoSensitiveText(capture);
        }
    }

    @Test
    void fifoRemovalListenerNeverInvokesBusinessEquality(
            CapturedOutput output) throws Exception {
        try (LogCapture capture = LogCapture.start(
                FifoCaffeineStore.class, Level.DEBUG, "Cache entry removed")) {
            StoreConfig config = StoreConfig.builder()
                    .maxSize(2)
                    .defaultTtl(Duration.ofNanos(1))
                    .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
                    .build();
            FifoCaffeineStore<ThrowingEqualityKey, String> store =
                    new FifoCaffeineStore<>(config);

            store.put(new ThrowingEqualityKey(), CANARY_VALUE);
            assertThat(store.size()).isZero();

            assertThat(capture.awaitExpected())
                    .as("expiration listener must finish without touching business equality")
                    .isTrue();
            assertThat(output.getAll())
                    .doesNotContain(CANARY_EXCEPTION)
                    .doesNotContain("Exception thrown by removal listener");
            assertNoSensitiveText(capture);
        }
    }

    @Test
    void fifoRemovalSignalCannotEraseReinsertedMetadata() throws Exception {
        try (LogCapture capture = LogCapture.start(
                FifoCaffeineStore.class, Level.DEBUG, "Cache entry removed")) {
            StoreConfig config = StoreConfig.builder()
                    .maxSize(1)
                    .defaultTtl(Duration.ofMillis(20))
                    .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
                    .build();
            FifoCaffeineStore<String, String> store = new FifoCaffeineStore<>(config);
            store.put(CANARY_KEY, CANARY_VALUE);
            Thread.sleep(40L);

            AtomicReference<Throwable> threadFailure = new AtomicReference<>();
            var lockField = FifoCaffeineStore.class.getDeclaredField("fifoLock");
            assertThat(lockField.trySetAccessible()).isTrue();
            Object fifoLock = lockField.get(store);
            Thread expiring = new Thread(
                    () -> runCapturingFailure(() -> store.get(CANARY_KEY), threadFailure),
                    "fifo-expire-race");
            synchronized (fifoLock) {
                expiring.start();
                assertThat(capture.awaitExpected())
                        .as("expiration must emit the coalesced removal signal")
                        .isTrue();
                assertThat(awaitThreadBlocked(expiring))
                        .as("expiration read must wait at FIFO metadata reconciliation")
                        .isTrue();
                store.put(CANARY_KEY, "reinserted-value");
            }
            expiring.join(2_000L);

            assertThat(expiring.isAlive()).isFalse();
            assertThat(threadFailure.get()).isNull();
            assertThat(store.get(CANARY_KEY)).contains("reinserted-value");
            assertThat(store.getFifoStats().getQueueSize()).isEqualTo(1);
            assertThat(store.getFifoStats().isIntegrityCheck()).isTrue();

            store.put("replacement", "replacement-value");
            assertThat(store.size()).isEqualTo(1L);
            assertThat(store.get(CANARY_KEY)).isEmpty();
            assertThat(store.get("replacement")).contains("replacement-value");
            assertThat(store.getFifoStats().isIntegrityCheck()).isTrue();
        }
    }

    @Test
    void fifoReadDoesNotScanAllTrackedKeysWithoutRemovals() {
        StoreConfig config = StoreConfig.builder()
                .maxSize(128)
                .defaultTtl(Duration.ofMinutes(1))
                .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
                .build();
        FifoCaffeineStore<CountingKey, String> store = new FifoCaffeineStore<>(config);
        AtomicInteger hashCalls = new AtomicInteger();
        var keys = new java.util.ArrayList<CountingKey>();
        for (int index = 0; index < 64; index++) {
            CountingKey key = new CountingKey(index, hashCalls);
            keys.add(key);
            store.put(key, "value-" + index);
        }
        hashCalls.set(0);

        assertThat(store.get(keys.getFirst())).contains("value-0");

        assertThat(hashCalls.get())
                .as("a cache hit must not reconcile every tracked FIFO key")
                .isLessThan(10);
    }

    @Test
    void fifoConcurrentExpirationStormCompletesWithinBound() throws Exception {
        int maxSize = 32;
        int workerCount = 4;
        StoreConfig config = StoreConfig.builder()
                .maxSize(maxSize)
                .defaultTtl(Duration.ofMillis(2))
                .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
                .build();
        FifoCaffeineStore<String, String> store = new FifoCaffeineStore<>(config);
        ExecutorService workers = Executors.newFixedThreadPool(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < workerCount; worker++) {
                int workerId = worker;
                tasks.add(workers.submit(() -> {
                    start.await();
                    for (int operation = 0; operation < 200; operation++) {
                        String key = "worker-" + workerId + '-' + operation % 48;
                        store.put(key, "value-" + operation);
                        store.get(key);
                        if (operation % 7 == 0) {
                            store.remove(key);
                        }
                        if (operation % 16 == 0) {
                            Thread.sleep(1L);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<Void> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(2, TimeUnit.SECONDS))
                    .as("FIFO stress workers must terminate")
                    .isTrue();
        }

        assertThat(store.size()).isLessThanOrEqualTo(maxSize);
        FifoCaffeineStore.FifoStats stats = store.getFifoStats();
        assertThat(stats.getQueueSize()).isLessThanOrEqualTo(maxSize);
        assertThat(stats.isIntegrityCheck()).isTrue();
    }

    @Test
    void tieredHitAndMissLogsContainNoBusinessKey() throws Exception {
        try (LogCapture capture = LogCapture.start(
                TieredCaffeineStore.class, Level.TRACE, "L1 cache hit")) {
            TieredCaffeineStore<String, String> store = new TieredCaffeineStore<>(
                    StoreConfig.l1Config(), StoreConfig.l2Config());

            store.put(CANARY_KEY, CANARY_VALUE);
            assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
            assertThat(store.get(CANARY_KEY + "-missing")).isEmpty();

            assertThat(capture.awaitExpected()).as("Tiered hit log must be observed").isTrue();
            assertNoSensitiveText(capture);
        }
    }

    @Test
    void instrumentedLoadFailureLogsOnlyFixedEventAndExceptionClass() throws Exception {
        try (LogCapture capture = LogCapture.start(
                InstrumentedCaffeineStore.class,
                Level.DEBUG,
                "Failed to load value",
                "Cache eviction")) {
            StoreConfig config = StoreConfig.builder()
                    .maxSize(1)
                    .logEvictions(true)
                    .build();
            InstrumentedCaffeineStore<String, String> store = new InstrumentedCaffeineStore<>(
                    config,
                    key -> { throw new IllegalStateException(CANARY_EXCEPTION); });

            assertThat(store.get(CANARY_KEY)).isEmpty();
            store.put(CANARY_KEY, CANARY_VALUE);
            store.put("replacement", "public-value");
            store.cleanUp();

            assertThat(capture.awaitExpected())
                    .as("Instrumented failure and eviction logs must both be observed")
                    .isTrue();
            assertThat(capture.renderedEvents()).contains(IllegalStateException.class.getName());
            assertNoSensitiveText(capture);
        }
    }

    @Test
    void explicitAndAutomaticRefreshFailuresNeverReachCaffeineLogging(
            CapturedOutput output) throws Exception {
        CountDownLatch reloadAttempts = new CountDownLatch(2);
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public String reload(String key, String oldValue) {
                reloadAttempts.countDown();
                throw new IllegalStateException(CANARY_EXCEPTION + "/" + key);
            }
        };

        InstrumentedCaffeineStore<String, String> explicit =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        explicit.put(CANARY_KEY, CANARY_VALUE);
        assertThatThrownBy(() ->
                explicit.refreshAsync(CANARY_KEY).get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(explicit.get(CANARY_KEY)).contains(CANARY_VALUE);

        StoreConfig automaticConfig = StoreConfig.builder()
                .maxSize(8)
                .refreshAfterWrite(Duration.ofNanos(1))
                .loader(loader)
                .recordStats(true)
                .build();
        CaffeineStore<String, String> automatic = new CaffeineStore<>(automaticConfig);
        automatic.put(CANARY_KEY, CANARY_VALUE);
        assertThat(automatic.get(CANARY_KEY)).contains(CANARY_VALUE);

        assertThat(reloadAttempts.await(2, TimeUnit.SECONDS))
                .as("explicit and automatic refresh must both invoke the loader")
                .isTrue();
        awaitRefreshFailureCount(automatic);
        assertThat(explicit.getStats().getLoadFailureCount()).isGreaterThanOrEqualTo(1L);
        assertThat(automatic.getStats().getLoadFailureCount()).isGreaterThanOrEqualTo(1L);
        assertThat(output.getAll())
                .doesNotContain(CANARY_KEY)
                .doesNotContain(CANARY_EXCEPTION)
                .doesNotContain("Exception thrown during refresh");
    }

    @Test
    void automaticRefreshFailurePreservesAgeAndRetryEligibility(
            CapturedOutput output) throws Exception {
        AtomicLong ticker = new AtomicLong();
        AtomicInteger reloadAttempts = new AtomicInteger();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                reloadAttempts.incrementAndGet();
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException(CANARY_EXCEPTION));
            }
        };
        LoadingCache<String, String> cache = Caffeine.newBuilder()
                .ticker(ticker::get)
                .refreshAfterWrite(Duration.ofNanos(10))
                .recordStats()
                .build(new ConfidentialCacheLoader<>(loader));
        cache.put(CANARY_KEY, CANARY_VALUE);
        ticker.set(11L);

        var refresh = cache.refresh(CANARY_KEY);

        assertThat(refresh).isCancelled();
        assertThat(cache.policy().getIfPresentQuietly(CANARY_KEY)).isEqualTo(CANARY_VALUE);
        assertThat(cache.stats().loadSuccessCount()).isZero();
        assertThat(cache.stats().loadFailureCount()).isEqualTo(1L);
        assertThat(cache.policy().refreshAfterWrite().orElseThrow()
                .ageOf(CANARY_KEY, TimeUnit.NANOSECONDS).orElseThrow()).isEqualTo(11L);
        assertThat(cache.get(CANARY_KEY)).isEqualTo(CANARY_VALUE);
        assertThat(reloadAttempts).hasValue(2);
        assertThat(output.getAll())
                .doesNotContain(CANARY_EXCEPTION)
                .doesNotContain("Exception thrown during refresh");
    }

    @Test
    void automaticRefreshSynchronousErrorNeverReachesCaffeineLogging(
            CapturedOutput output) {
        AtomicLong ticker = new AtomicLong();
        AtomicInteger reloadAttempts = new AtomicInteger();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                reloadAttempts.incrementAndGet();
                throw new AssertionError(CANARY_EXCEPTION);
            }
        };
        LoadingCache<String, String> cache = Caffeine.newBuilder()
                .ticker(ticker::get)
                .refreshAfterWrite(Duration.ofNanos(10))
                .recordStats()
                .build(new ConfidentialCacheLoader<>(loader));
        cache.put(CANARY_KEY, CANARY_VALUE);
        ticker.set(11L);

        assertThat(cache.get(CANARY_KEY)).isEqualTo(CANARY_VALUE);

        assertThat(reloadAttempts).hasValue(1);
        assertThat(cache.stats().loadFailureCount()).isEqualTo(1L);
        assertThat(output.getAll())
                .doesNotContain(CANARY_EXCEPTION)
                .doesNotContain("Exception thrown when submitting refresh task");
    }

    @Test
    void explicitRefreshDistinguishesMissingCancellationAndError(
            CapturedOutput output) throws Exception {
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-" + key;
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncLoad(
                    String key, java.util.concurrent.Executor executor) {
                return java.util.concurrent.CompletableFuture.completedFuture("loaded-" + key);
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                if (key.endsWith("cancel")) {
                    var cancelled = new java.util.concurrent.CompletableFuture<String>();
                    cancelled.cancel(false);
                    return cancelled;
                }
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new AssertionError(CANARY_EXCEPTION));
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);

        assertThat(store.refreshAsync("missing").get(2, TimeUnit.SECONDS))
                .isEqualTo("loaded-missing");
        assertThat(store.get("missing")).contains("loaded-missing");

        store.put("present-cancel", CANARY_VALUE);
        var cancelled = store.refreshAsync("present-cancel");
        assertThat(cancelled).isCancelled();
        assertThat(store.get("present-cancel")).contains(CANARY_VALUE);

        store.put("present-error", CANARY_VALUE);
        assertThatThrownBy(() ->
                store.refreshAsync("present-error").get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(AssertionError.class);
        assertThat(store.get("present-error")).contains(CANARY_VALUE);
        assertThat(store.getStats().getLoadSuccessCount()).isGreaterThanOrEqualTo(1L);
        assertThat(store.getStats().getLoadFailureCount()).isGreaterThanOrEqualTo(2L);
        assertThat(output.getAll())
                .doesNotContain(CANARY_EXCEPTION)
                .doesNotContain("Exception thrown during refresh");
    }

    @Test
    void explicitSynchronousErrorRemainsVisibleWithoutCaffeineLogging(
            CapturedOutput output) throws Exception {
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                throw new AssertionError(CANARY_EXCEPTION);
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        assertThatThrownBy(() ->
                store.refreshAsync(CANARY_KEY).get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(AssertionError.class);

        assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
        assertThat(output.getAll())
                .doesNotContain(CANARY_EXCEPTION)
                .doesNotContain("Exception thrown when submitting refresh task")
                .doesNotContain("Exception thrown during refresh");
    }

    @Test
    void explicitRefreshNeverOverwritesConcurrentPut() throws Exception {
        var reloaded = new java.util.concurrent.CompletableFuture<String>();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                return reloaded;
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        var refresh = store.refreshAsync(CANARY_KEY);
        store.put(CANARY_KEY, "concurrent-value");
        reloaded.complete("refreshed-value");

        assertThat(refresh.get(2, TimeUnit.SECONDS)).isEqualTo("refreshed-value");
        assertThat(store.get(CANARY_KEY)).contains("concurrent-value");
    }

    @Test
    void cancellingExplicitRefreshCancelsLoaderAndPreventsWriteBack() {
        var reloaded = new java.util.concurrent.CompletableFuture<String>();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                return reloaded;
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        var refresh = store.refreshAsync(CANARY_KEY);
        assertThat(refresh.cancel(false)).isTrue();

        assertThat(reloaded).isCancelled();
        assertThat(reloaded.complete("late-value")).isFalse();
        assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
    }

    @Test
    void concurrentExplicitRefreshesShareOneLoaderFuture() throws Exception {
        var reloaded = new java.util.concurrent.CompletableFuture<String>();
        AtomicInteger reloadAttempts = new AtomicInteger();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                reloadAttempts.incrementAndGet();
                return reloaded;
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        var first = store.refreshAsync(CANARY_KEY);
        var second = store.refreshAsync(CANARY_KEY);

        assertThat(second).isSameAs(first);
        assertThat(reloadAttempts).hasValue(1);
        reloaded.complete("refreshed-value");
        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("refreshed-value");
        assertThat(store.get(CANARY_KEY)).contains("refreshed-value");
    }

    @Test
    void explicitNullReloadRemovesExistingMapping() throws Exception {
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        assertThat(store.refreshAsync(CANARY_KEY).get(2, TimeUnit.SECONDS)).isNull();

        assertThat(store.size()).isZero();
        assertThat(store.getStats().getLoadFailureCount()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void explicitRefreshNeverResurrectsConcurrentRemoval() throws Exception {
        var reloaded = new java.util.concurrent.CompletableFuture<String>();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                return reloaded;
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        var refresh = store.refreshAsync(CANARY_KEY);
        store.remove(CANARY_KEY);
        reloaded.complete("late-value");

        assertThat(refresh.get(2, TimeUnit.SECONDS)).isEqualTo("late-value");
        assertThat(store.size()).isZero();
    }

    @Test
    void automaticAndExplicitRefreshShareOneLoaderInvocation() throws Exception {
        var reloaded = new java.util.concurrent.CompletableFuture<String>();
        AtomicInteger reloadAttempts = new AtomicInteger();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                reloadAttempts.incrementAndGet();
                return reloaded;
            }
        };
        StoreConfig config = StoreConfig.builder()
                .maxSize(8)
                .refreshAfterWrite(Duration.ofNanos(1))
                .build();
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(config, loader);
        store.put(CANARY_KEY, CANARY_VALUE);
        Thread.sleep(1L);

        assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
        var explicit = store.refreshAsync(CANARY_KEY);

        assertThat(reloadAttempts).hasValue(1);
        reloaded.complete("refreshed-value");
        assertThat(explicit.get(2, TimeUnit.SECONDS)).isEqualTo("refreshed-value");
        assertThat(store.get(CANARY_KEY)).contains("refreshed-value");
    }

    @Test
    void explicitRefreshJoiningAutomaticFailurePreservesOriginalError(
            CapturedOutput output) throws Exception {
        var reloaded = new java.util.concurrent.CompletableFuture<String>();
        AtomicInteger reloadAttempts = new AtomicInteger();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                reloadAttempts.incrementAndGet();
                return reloaded;
            }
        };
        StoreConfig config = StoreConfig.builder()
                .maxSize(8)
                .refreshAfterWrite(Duration.ofNanos(1))
                .build();
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(config, loader);
        store.put(CANARY_KEY, CANARY_VALUE);
        Thread.sleep(1L);
        assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);

        var explicit = store.refreshAsync(CANARY_KEY);
        reloaded.completeExceptionally(new AssertionError(CANARY_EXCEPTION));

        assertThatThrownBy(() -> explicit.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(AssertionError.class);
        assertThat(reloadAttempts).hasValue(1);
        assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
        assertThat(output.getAll())
                .doesNotContain(CANARY_EXCEPTION)
                .doesNotContain("Exception thrown during refresh");
    }

    @Test
    void cancellingExplicitRefreshJoiningAutomaticRefreshCancelsLoader() throws Exception {
        var reloaded = new java.util.concurrent.CompletableFuture<String>();
        CountDownLatch reloadStarted = new CountDownLatch(1);
        AtomicInteger reloadAttempts = new AtomicInteger();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                reloadAttempts.incrementAndGet();
                reloadStarted.countDown();
                return reloaded;
            }
        };
        StoreConfig config = StoreConfig.builder()
                .maxSize(8)
                .refreshAfterWrite(Duration.ofNanos(1))
                .build();
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(config, loader);
        store.put(CANARY_KEY, CANARY_VALUE);
        Thread.sleep(1L);

        assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
        assertThat(reloadStarted.await(2, TimeUnit.SECONDS))
                .as("automatic refresh must reach the raw loader")
                .isTrue();
        var explicit = store.refreshAsync(CANARY_KEY);

        assertThat(explicit.cancel(false)).isTrue();
        assertThat(reloaded).isCancelled();
        assertThat(reloadAttempts).hasValue(1);
        assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
    }

    @Test
    void completedExplicitRefreshIsNotReusedByNextRequest() throws Exception {
        AtomicInteger reloadAttempts = new AtomicInteger();
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                int attempt = reloadAttempts.incrementAndGet();
                return java.util.concurrent.CompletableFuture.completedFuture("refresh-" + attempt);
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        assertThat(store.refreshAsync(CANARY_KEY).get(2, TimeUnit.SECONDS))
                .isEqualTo("refresh-1");
        assertThat(store.refreshAsync(CANARY_KEY).get(2, TimeUnit.SECONDS))
                .isEqualTo("refresh-2");

        assertThat(reloadAttempts).hasValue(2);
        assertThat(store.get(CANARY_KEY)).contains("refresh-2");
    }

    @Test
    void explicitRefreshRestoresInterruptFromSynchronousLoaderFailure() throws Exception {
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor)
                    throws Exception {
                throw new InterruptedException("interrupted refresh");
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        try {
            assertThatThrownBy(() ->
                    store.refreshAsync(CANARY_KEY).get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void explicitRefreshRestoresInterruptFromExceptionalFuture() throws Exception {
        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override
            public String load(String key) {
                return "loaded-value";
            }

            @Override
            public java.util.concurrent.CompletableFuture<? extends String> asyncReload(
                    String key, String oldValue, java.util.concurrent.Executor executor) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new InterruptedException("interrupted async refresh"));
            }
        };
        InstrumentedCaffeineStore<String, String> store =
                new InstrumentedCaffeineStore<>(StoreConfig.defaultConfig(), loader);
        store.put(CANARY_KEY, CANARY_VALUE);

        var refresh = store.refreshAsync(CANARY_KEY);
        try {
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            Thread.interrupted();
            assertThatThrownBy(() -> refresh.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(InterruptedException.class);
            assertThat(store.get(CANARY_KEY)).contains(CANARY_VALUE);
        } finally {
            Thread.interrupted();
        }
    }

    private static void awaitRefreshFailureCount(
            CaffeineStore<?, ?> store) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (store.getStats().getLoadFailureCount() == 0L
                && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }

    /** 只要异步 housekeeping 调用业务 equality，就用敏感异常文本使合同确定失败。 */
    private static final class ThrowingEqualityKey {

        @Override
        public boolean equals(Object other) {
            throw new IllegalStateException(CANARY_EXCEPTION);
        }

        @Override
        public int hashCode() {
            return 17;
        }
    }

    /** 通过公开 key 合同量化一次命中读触碰的 FIFO 元数据规模。 */
    private record CountingKey(int id, AtomicInteger hashCalls) {

        @Override
        public int hashCode() {
            hashCalls.incrementAndGet();
            return Integer.hashCode(id);
        }
    }

    private static boolean awaitThreadBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.isAlive() && System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.BLOCKED) {
                return true;
            }
            Thread.sleep(1L);
        }
        return thread.getState() == Thread.State.BLOCKED;
    }

    private static void runCapturingFailure(
            Runnable action,
            AtomicReference<Throwable> failure) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }

    private static void assertNoSensitiveText(LogCapture capture) {
        String renderedEvents = capture.renderedEvents();
        assertThat(renderedEvents)
                .doesNotContain(CANARY_KEY)
                .doesNotContain(URLEncoder.encode(CANARY_KEY, StandardCharsets.UTF_8))
                .doesNotContain(CANARY_VALUE)
                .doesNotContain(URLEncoder.encode(CANARY_VALUE, StandardCharsets.UTF_8))
                .doesNotContain(CANARY_EXCEPTION)
                .doesNotContain(URLEncoder.encode(CANARY_EXCEPTION, StandardCharsets.UTF_8));
        assertThat(renderedEvents.toLowerCase(Locale.ROOT))
                .doesNotContain(CANARY_KEY.toLowerCase(Locale.ROOT))
                .doesNotContain(URLEncoder.encode(
                        CANARY_KEY, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT))
                .doesNotContain(CANARY_VALUE.toLowerCase(Locale.ROOT))
                .doesNotContain(URLEncoder.encode(
                        CANARY_VALUE, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT))
                .doesNotContain(CANARY_EXCEPTION.toLowerCase(Locale.ROOT))
                .doesNotContain(URLEncoder.encode(
                        CANARY_EXCEPTION, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT));
        assertThat(capture.hasThrowable())
                .as("store events must never carry an attached Throwable")
                .isFalse();
    }

    /** 临时绑定到单个 class logger 的线程安全 appender，关闭时恢复原日志级别。 */
    private static final class LogCapture implements AutoCloseable {

        /** 被测类型对应的 Logback logger。 */
        private final Logger logger;

        /** 测试前的显式级别，关闭时原样恢复。 */
        private final Level previousLevel;

        /** 收集格式化消息及 throwable 文本的 appender。 */
        private final CapturingAppender appender;

        private LogCapture(Logger logger, Level previousLevel, CapturingAppender appender) {
            this.logger = logger;
            this.previousLevel = previousLevel;
            this.appender = appender;
        }

        private static LogCapture start(
                Class<?> owner,
                Level level,
                String... expectedFragments) {
            Logger logger = (Logger) LoggerFactory.getLogger(owner);
            Level previousLevel = logger.getLevel();
            CapturingAppender appender = new CapturingAppender(expectedFragments);
            return attach(logger, previousLevel, level, appender);
        }

        private static LogCapture attach(
                Logger logger,
                Level previousLevel,
                Level level,
                CapturingAppender appender) {
            appender.setContext(logger.getLoggerContext());
            appender.start();
            logger.setLevel(level);
            logger.addAppender(appender);
            return new LogCapture(logger, previousLevel, appender);
        }

        private boolean awaitExpected() throws InterruptedException {
            return appender.awaitExpected();
        }

        private String renderedEvents() {
            return String.join("\n", appender.events());
        }

        private boolean hasThrowable() {
            return appender.hasThrowable();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    /** 异步 removal listener 也能确定等待的日志事件收集器。 */
    private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

        /** 尚未观察到的固定、非敏感事件片段。 */
        private final Set<String> remainingExpectedFragments = ConcurrentHashMap.newKeySet();

        /** 全部目标事件到达信号。 */
        private final CountDownLatch expectedEvents;

        /** 并发 listener 写入的全部日志事件表面。 */
        private final List<String> events = new CopyOnWriteArrayList<>();

        /** 是否观察到任意附带 Throwable 的事件。 */
        private final AtomicBoolean throwableSeen = new AtomicBoolean();

        private CapturingAppender(String... expectedFragments) {
            remainingExpectedFragments.addAll(Arrays.asList(expectedFragments));
            expectedEvents = new CountDownLatch(remainingExpectedFragments.size());
        }

        @Override
        protected void append(ILoggingEvent event) {
            StringBuilder rendered = new StringBuilder()
                    .append("message=").append(event.getMessage())
                    .append("\nformatted=").append(event.getFormattedMessage())
                    .append("\narguments=").append(Arrays.deepToString(event.getArgumentArray()))
                    .append("\nkeyValuePairs=").append(event.getKeyValuePairs())
                    .append("\nmdc=").append(event.getMDCPropertyMap())
                    .append("\nmarkers=").append(event.getMarkerList());
            if (event.getThrowableProxy() != null) {
                throwableSeen.set(true);
                rendered.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            events.add(rendered.toString());
            for (String expectedFragment : remainingExpectedFragments) {
                if (event.getFormattedMessage().contains(expectedFragment)
                        && remainingExpectedFragments.remove(expectedFragment)) {
                    expectedEvents.countDown();
                }
            }
        }

        private boolean awaitExpected() throws InterruptedException {
            return expectedEvents.await(2, TimeUnit.SECONDS);
        }

        private List<String> events() {
            return List.copyOf(events);
        }

        private boolean hasThrowable() {
            return throwableSeen.get();
        }
    }
}
