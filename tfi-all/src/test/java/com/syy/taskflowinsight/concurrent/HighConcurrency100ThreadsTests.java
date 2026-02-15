package com.syy.taskflowinsight.concurrent;

import com.syy.taskflowinsight.context.ZeroLeakThreadLocalManager;
import com.syy.taskflowinsight.store.FifoCaffeineStore;
import com.syy.taskflowinsight.store.StoreConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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

        // Shared components under test
        StoreConfig storeConfig = StoreConfig.builder()
            .maxSize(5000)
            .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
            .recordStats(true)
            .build();
        FifoCaffeineStore<String, Integer> store = new FifoCaffeineStore<>(storeConfig);
        ZeroLeakThreadLocalManager tlm = ZeroLeakThreadLocalManager.getInstance();

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

                    long tid = Thread.currentThread().threadId();
                    // register a dummy context and some nested stages
                    tlm.registerContext(Thread.currentThread(), "ctx-" + tid);
                    tlm.registerNestedStage(tid, "s1", 1);
                    tlm.registerNestedStage(tid, "s2", 2);

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

        // Cleanup leaked TL records from finished threads and verify batch cleanup works
        int leaks = tlm.detectLeaks();
        int cleaned = tlm.cleanupNestedStagesBatch();
        assertThat(leaks + cleaned).isGreaterThan(0);
    }
}
