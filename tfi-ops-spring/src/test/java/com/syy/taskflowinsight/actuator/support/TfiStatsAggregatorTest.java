package com.syy.taskflowinsight.actuator.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TfiStatsAggregator}.
 */
class TfiStatsAggregatorTest {

    private TfiStatsAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new TfiStatsAggregator();
    }

    @Test
    void aggregateStats_returnsNonNullMap() {
        Map<String, Object> stats = aggregator.aggregateStats();

        assertNotNull(stats);
        assertNotNull(stats.get("sessionCount"));
        assertNotNull(stats.get("totalChanges"));
        assertNotNull(stats.get("changesByObject"));
        assertNotNull(stats.get("changesByType"));
        assertNotNull(stats.get("threadContext"));
        assertNotNull(stats.get("timestamp"));
    }

    @Test
    void aggregateStats_threadContext_containsExpectedKeys() {
        Map<String, Object> stats = aggregator.aggregateStats();

        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) stats.get("threadContext");
        assertNotNull(ctx);
        assertTrue(ctx.containsKey("activeContexts"));
        assertTrue(ctx.containsKey("totalCreated"));
        assertTrue(ctx.containsKey("totalPropagations"));
    }

    @Test
    void getTotalChangesCount_returnsNonNegative() {
        int count = aggregator.getTotalChangesCount();
        assertTrue(count >= 0, "Total changes count should be non-negative");
    }

    @Test
    void getActiveSessionCount_returnsNonNegative() {
        int count = aggregator.getActiveSessionCount();
        assertTrue(count >= 0, "Active session count should be non-negative");
    }

    @Test
    void aggregateStats_emptyState_sessionCountIsZero() {
        Map<String, Object> stats = aggregator.aggregateStats();

        assertEquals(0, stats.get("sessionCount"));
        assertEquals(0, stats.get("totalChanges"));
    }
}
