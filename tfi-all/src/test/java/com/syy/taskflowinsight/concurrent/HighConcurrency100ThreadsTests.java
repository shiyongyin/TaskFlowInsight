package com.syy.taskflowinsight.concurrent;

import com.syy.taskflowinsight.context.ContextMetrics;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.store.FifoCaffeineStore;
import com.syy.taskflowinsight.store.StoreConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * High-concurrency validation for CT-006 (100 threads, no races).
 */
@DisplayName("CT-006: 100-thread concurrency safety")
class HighConcurrency100ThreadsTests {

    @Test
    @DisplayName("100 threads operate store and TL manager without races")
    void hundredThreadsNoDataRaces() throws Exception {
        int threadCount = 100;
        int opsPerThread = 200;

        ContextMetrics before = SafeContextManager.getInstance().metrics();

        // Shared store and the manager-owned Context registry are exercised together.
        StoreConfig storeConfig = StoreConfig.builder()
            .maxSize(5000)
            .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
            .recordStats(true)
            .build();
        FifoCaffeineStore<String, Integer> store = new FifoCaffeineStore<>(storeConfig);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        AtomicInteger puts = new AtomicInteger();
        AtomicInteger gets = new AtomicInteger();
        Random rnd = new Random(42);

        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);

                    try (ManagedThreadContext ignored = ManagedThreadContext.create(
                            "concurrent-" + Thread.currentThread().threadId())) {
                        for (int j = 0; j < opsPerThread; j++) {
                            int k = rnd.nextInt(4000);
                            String key = "k-" + k;
                            if ((j & 1) == 0) {
                                store.put(key, j);
                                puts.incrementAndGet();
                            } else {
                                store.get(key);
                                gets.incrementAndGet();
                            }
                            if ((j % 50) == 0) {
                                Thread.yield(); // encourage contention
                            }
                        }
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // No thread should error
        assertThat(errors).isEmpty();
        assertThat(finished).as("threads finished timely").isTrue();

        // Store sanity under concurrency
        assertThat(store.size()).isLessThanOrEqualTo(5000);

        // Basic sanity on total ops executed
        assertThat(puts.get() + gets.get()).isEqualTo(threadCount * opsPerThread);

        ContextMetrics after = SafeContextManager.getInstance().metrics();
        assertThat(after.createdContexts() - before.createdContexts()).isEqualTo(threadCount);
        assertThat(after.closedContexts() - before.closedContexts()).isEqualTo(threadCount);
        assertThat(after.activeContexts()).isEqualTo(before.activeContexts());
    }
}
