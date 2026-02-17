package com.syy.taskflowinsight.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Concrete implementation of {@link AbstractStoreContractTest} for {@link CaffeineStore}.
 */
class CaffeineStoreContractTest extends AbstractStoreContractTest {

    @Override
    protected Store<String, String> createStore() {
        return new CaffeineStore<>(StoreConfig.defaultConfig());
    }

    @Test
    void put_nullKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> store.put(null, "value"));
    }

    @Test
    void put_nullValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> store.put("key", null));
    }
}
