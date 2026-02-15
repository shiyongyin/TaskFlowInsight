package com.syy.taskflowinsight.api.builder;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TfiContextConcurrencyTests {

    static class Item {
        final String id; final int v;
        Item(String id, int v) { this.id = id; this.v = v; }
    }

    @Test
    void concurrent_compare_should_be_consistent_and_safe() throws Exception {
        final TfiContext ctx = DiffBuilder.create().withDeepCompare(true).withMaxDepth(5).build();
        final int threads = 10;
        final int loops = 100;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger mismatches = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < loops; i++) {
                        Item a = new Item("k", 1);
                        Item b = new Item("k", (i % 2 == 0) ? 1 : 2);
                        CompareResult r = ctx.compare(a, b, CompareOptions.DEEP);
                        boolean expectedIdentical = (i % 2 == 0);
                        if (r.isIdentical() != expectedIdentical) {
                            mismatches.incrementAndGet();
                        }
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "concurrency test timed out");
        pool.shutdownNow();

        assertEquals(0, errors.get(), "no exceptions expected in concurrent compare");
        assertEquals(0, mismatches.get(), "all results should be consistent with inputs");
    }
}

