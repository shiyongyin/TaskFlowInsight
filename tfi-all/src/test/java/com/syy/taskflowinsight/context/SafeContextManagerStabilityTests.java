package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class SafeContextManagerStabilityTests {

    @AfterEach
    void cleanup() {
        ManagedThreadContext current = ManagedThreadContext.current();
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }

    @Test
    void wrapRunnable_propagatesContext_andCleansOnExit() {
        SafeContextManager mgr = SafeContextManager.getInstance();
        try (ManagedThreadContext ctx = ManagedThreadContext.create("ctx-test")) {
            Runnable wrapped = mgr.wrapRunnable(() -> {
                ManagedThreadContext inner = ManagedThreadContext.current();
                assertThat(inner).isNotNull();
            });
            wrapped.run();
        }
        // After outer context closed, no current context should remain
        assertThat(ManagedThreadContext.current()).isNull();
    }

    @Test
    void executeAsync_propagatesContext_andCompletes() throws ExecutionException, InterruptedException {
        SafeContextManager mgr = SafeContextManager.getInstance();
        try (ManagedThreadContext ctx = ManagedThreadContext.create("async-test")) {
            CompletableFuture<String> f = mgr.executeAsync("task", () -> {
                ManagedThreadContext inner = ManagedThreadContext.current();
                assertThat(inner).isNotNull();
                return "done";
            });
            assertThat(f.get()).isEqualTo("done");
        }
        assertThat(ManagedThreadContext.current()).isNull();
    }
}

