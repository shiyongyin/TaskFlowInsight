package com.syy.taskflowinsight.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validate cache hit-rate threshold (>80%) under a reuse-heavy workload.
 */
@DisplayName("CT-006: FIFO cache hit rate > 80%")
class FifoCacheHitRateTests {

    @Test
    @DisplayName("Reuse-heavy access pattern yields high hit rate")
    void reusePatternYieldsHighHitRate() {
        StoreConfig config = StoreConfig.builder()
            .maxSize(500)
            .evictionStrategy(StoreConfig.EvictionStrategy.FIFO)
            .recordStats(true)
            .build();

        FifoCaffeineStore<String, Integer> store = new FifoCaffeineStore<>(config);

        // Prime cache with 200 keys
        for (int i = 0; i < 200; i++) {
            store.put("k" + i, i);
        }

        // Heavily reuse first 100 keys to drive hit-rate up
        for (int r = 0; r < 50; r++) {
            for (int i = 0; i < 100; i++) {
                store.get("k" + i);
            }
        }
        // Occasional misses
        for (int i = 1000; i < 1020; i++) {
            store.get("m" + i);
        }

        StoreStats stats = store.getStats();
        assertThat(stats.getHitRate()).isGreaterThan(0.80);
        // Also validate size and integrity
        assertThat(store.size()).isLessThanOrEqualTo(500);
        assertThat(store.getFifoStats().isIntegrityCheck()).isTrue();
    }
}

