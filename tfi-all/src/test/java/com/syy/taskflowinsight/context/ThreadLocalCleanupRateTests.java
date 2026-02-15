package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validate 100% ThreadLocal cleanup in a controlled short run.
 */
@DisplayName("CT-006: ThreadLocal cleanup rate = 100% in sample run")
class ThreadLocalCleanupRateTests {

    @Test
    @DisplayName("All registered contexts from short-lived threads are cleaned")
    void allShortLivedContextsAreCleaned() throws Exception {
        ZeroLeakThreadLocalManager tlm = ZeroLeakThreadLocalManager.getInstance();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread t = new Thread(() -> {
                tlm.registerContext(Thread.currentThread(), new Object());
                tlm.registerNestedStage(Thread.currentThread().threadId(), "stage", 1);
                try { Thread.sleep(20); } catch (InterruptedException ignored) { }
            }, "tl-cleanup-" + i);
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) t.join();

        // Give JVM a moment to enqueue dead thread refs
        Thread.sleep(100);

        int leaksDetected = tlm.detectLeaks();
        int batchCleaned = tlm.cleanupNestedStagesBatch();

        // After cleanup passes, no active contexts should remain for these threads
        var diag = tlm.getDiagnostics();
        int active = (int) diag.getOrDefault("contexts.active", 0);

        assertThat(leaksDetected + batchCleaned).isGreaterThanOrEqualTo(20);
        assertThat(active).isZero();
    }
}

