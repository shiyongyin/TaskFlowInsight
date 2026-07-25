package com.syy.taskflowinsight.actuator.support;

import com.syy.taskflowinsight.context.ContextMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(stats).containsOnlyKeys(
                "activeContexts", "createdContexts", "closedContexts", "detectedLeaks",
                "asyncTasks", "executorPoolSize", "executorQueueSize", "propagations", "capturedAt");
    }

    @Test
    void aggregateStats_mapsOneCanonicalContextSnapshot() {
        ContextMetrics metrics = new ContextMetrics(7, 11L, 4L, 2L, 3L,
                5, 6, 13L, Instant.parse("2026-07-10T00:00:00Z"));
        Map<String, Object> stats = aggregator.aggregateStats(metrics);

        assertThat(stats).containsExactlyInAnyOrderEntriesOf(Map.of(
                "activeContexts", 7,
                "createdContexts", 11L,
                "closedContexts", 4L,
                "detectedLeaks", 2L,
                "asyncTasks", 3L,
                "executorPoolSize", 5,
                "executorQueueSize", 6,
                "propagations", 13L,
                "capturedAt", "2026-07-10T00:00:00Z"));
    }
}
