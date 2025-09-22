package com.syy.taskflowinsight.store;

import com.github.benmanes.caffeine.cache.CacheLoader;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;

class InstrumentedCaffeineStoreTests {

    @Test
    void loaderAndRefresh_workWithTtl() throws ExecutionException, InterruptedException {
        StoreConfig config = StoreConfig.builder()
            .maxSize(100)
            .defaultTtl(Duration.ofMillis(50))
            .recordStats(true)
            .build();

        CacheLoader<String, String> loader = new CacheLoader<>() {
            @Override public String load(String key) { return "v-" + key; }
        };

        InstrumentedCaffeineStore<String, String> store = new InstrumentedCaffeineStore<>(config, loader);

        // miss -> load
        Optional<String> v1 = store.get("a");
        assertThat(v1).contains("v-a");

        // refresh should not throw
        store.refresh("a");
        assertThat(store.get("a")).contains("v-a");

        // after ttl, value may expire -> load again
        Thread.sleep(60);
        assertThat(store.get("a")).contains("v-a");

        // stats available
        StoreStats stats = store.getStats();
        assertThat(stats.getEstimatedSize()).isGreaterThanOrEqualTo(0);
    }
}

