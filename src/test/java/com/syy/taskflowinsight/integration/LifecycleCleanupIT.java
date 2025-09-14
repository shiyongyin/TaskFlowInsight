package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生命周期清理测试（CARD-262）
 * - stop 后 TFI.getChanges() 空
 * - ManagedThreadContext.close() 后空
 * - TFI.endSession() 兜底清理；重复调用幂等无异常
 */
class LifecycleCleanupIT {

    static class Foo { String v = "a"; void set(String s){ this.v = s; } }

    @BeforeEach
    void init() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.clear();
    }

    @AfterEach
    void cleanup() {
        TFI.endSession();
        TFI.clear();
    }

    @Test
    void stopClearsPendingChanges() {
        TFI.startSession("LC-Stop");
        TFI.start("task");

        Foo foo = new Foo();
        TFI.track("Foo", foo, "v");
        foo.set("b");

        // Changes present before stop
        List<ChangeRecord> before = TFI.getChanges();
        assertFalse(before.isEmpty());

        // Stop flush & clear
        assertDoesNotThrow(TFI::stop);
        List<ChangeRecord> after = TFI.getChanges();
        assertTrue(after.isEmpty());

        // Idempotency
        assertDoesNotThrow(TFI::stop);
    }

    @Test
    void managedThreadContextCloseClearsTracking() {
        try (ManagedThreadContext ctx = ManagedThreadContext.create("LC-Close")) {
            TFI.start("t");
            Foo foo = new Foo();
            TFI.track("Foo", foo, "v");
            foo.set("c");
            // consume once
            assertFalse(TFI.getChanges().isEmpty());
        }
        // Context closed -> clear tracking
        assertTrue(TFI.getChanges().isEmpty());
    }

    @Test
    void endSessionClearsAndIdempotent() {
        TFI.startSession("LC-End");
        TFI.start("t");
        Foo foo = new Foo();
        TFI.track("Foo", foo, "v");
        foo.set("d");

        // Ensure pending
        assertFalse(TFI.getChanges().isEmpty());

        // End session should clear
        assertDoesNotThrow(TFI::endSession);
        assertTrue(TFI.getChanges().isEmpty());

        // Idempotent repeats
        assertDoesNotThrow(TFI::endSession);
        assertTrue(TFI.getChanges().isEmpty());
    }
}

