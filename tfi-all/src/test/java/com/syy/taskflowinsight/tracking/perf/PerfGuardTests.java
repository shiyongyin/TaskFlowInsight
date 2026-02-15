package com.syy.taskflowinsight.tracking.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerfGuardTests {

    @Test
    void budget_notExceeded_shouldPass() {
        PerfGuard guard = new PerfGuard();
        PerfGuard.PerfOptions opts = PerfGuard.PerfOptions.defaults();

        long start = System.currentTimeMillis();
        PerfGuard.PerfDecision decision = guard.checkBudget(start, opts, false);

        assertTrue(decision.ok);
        assertFalse(decision.partial);
        assertTrue(decision.reasons.isEmpty());
    }

    @Test
    void budget_exceeded_strict_shouldDegrade() {
        PerfGuard guard = new PerfGuard();
        PerfGuard.PerfOptions opts = new PerfGuard.PerfOptions(0, 1000, true); // 0ms budget

        long start = System.currentTimeMillis() - 100; // 100ms ago
        PerfGuard.PerfDecision decision = guard.checkBudget(start, opts, true);

        assertFalse(decision.ok);
        assertTrue(decision.partial);
        assertEquals("TIME_BUDGET_EXCEEDED", decision.reasons.get(0));
    }

    @Test
    void budget_exceeded_default_shouldDegrade() {
        PerfGuard guard = new PerfGuard();
        PerfGuard.PerfOptions opts = new PerfGuard.PerfOptions(0, 1000, true);

        long start = System.currentTimeMillis() - 100;
        PerfGuard.PerfDecision decision = guard.checkBudget(start, opts, false);

        assertFalse(decision.ok);
        assertFalse(decision.partial); // Non-strict mode: partial=false
        assertEquals("TIME_BUDGET_EXCEEDED", decision.reasons.get(0));
    }

    @Test
    void list_exceeds_threshold_shouldDegrade() {
        PerfGuard guard = new PerfGuard();
        PerfGuard.PerfOptions opts = new PerfGuard.PerfOptions(5000, 100, true);

        assertTrue(guard.shouldDegradeList(150, opts));
        assertFalse(guard.shouldDegradeList(50, opts));
    }

    @Test
    void lazySnapshot_shouldReturnConfigValue() {
        PerfGuard guard = new PerfGuard();

        PerfGuard.PerfOptions lazyTrue = new PerfGuard.PerfOptions(5000, 1000, true);
        assertTrue(guard.lazySnapshot(lazyTrue));

        PerfGuard.PerfOptions lazyFalse = new PerfGuard.PerfOptions(5000, 1000, false);
        assertFalse(guard.lazySnapshot(lazyFalse));
    }
}
