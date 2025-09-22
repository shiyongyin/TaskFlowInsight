package com.syy.taskflowinsight.store;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class CaffeineStoreTests {

    @Test
    void putGetRemoveAndStats_workAsExpected() {
        StoreConfig config = StoreConfig.defaultConfig();
        CaffeineStore<String, String> store = new CaffeineStore<>(config);

        // put & get
        store.put("k1", "v1");
        assertThat(store.get("k1")).isEqualTo(Optional.of("v1"));

        // size and stats
        assertThat(store.size()).isGreaterThanOrEqualTo(1);
        StoreStats stats = store.getStats();
        assertThat(stats.getEstimatedSize()).isGreaterThanOrEqualTo(1);

        // putAll & getAll
        store.putAll(Map.of("k2", "v2", "k3", "v3"));
        assertThat(store.getAll(java.util.List.of("k2", "k3")))
            .containsEntry("k2", "v2")
            .containsEntry("k3", "v3");

        // remove & removeAll
        store.remove("k1");
        assertThat(store.get("k1")).isEmpty();
        store.removeAll(java.util.List.of("k2", "k3"));
        assertThat(store.get("k2")).isEmpty();
        assertThat(store.get("k3")).isEmpty();

        // clear
        store.put("k4", "v4");
        store.clear();
        assertThat(store.size()).isZero();
    }

    @Test
    void getWithNullKey_returnsEmpty() {
        CaffeineStore<String, String> store = new CaffeineStore<>(StoreConfig.defaultConfig());
        assertThat(store.get(null)).isEmpty();
    }

    @Test
    void putRejectsNulls() {
        CaffeineStore<String, String> store = new CaffeineStore<>(StoreConfig.defaultConfig());
        assertThatThrownBy(() -> store.put(null, "v")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("k", null)).isInstanceOf(IllegalArgumentException.class);
    }
}

