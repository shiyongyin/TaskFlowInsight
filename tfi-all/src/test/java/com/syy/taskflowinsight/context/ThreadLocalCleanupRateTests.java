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
        ContextMetrics before = SafeContextManager.getInstance().metrics();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread t = new Thread(() -> {
                try (ManagedThreadContext ignored = ManagedThreadContext.create("cleanup-rate")) {
                    // close() must return the exact owner identity to the manager registry.
                }
            }, "tl-cleanup-" + i);
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) t.join();

        ContextMetrics after = SafeContextManager.getInstance().metrics();
        assertThat(after.createdContexts() - before.createdContexts()).isEqualTo(20L);
        assertThat(after.closedContexts() - before.closedContexts()).isEqualTo(20L);
        assertThat(after.activeContexts()).isEqualTo(before.activeContexts());
    }
}
