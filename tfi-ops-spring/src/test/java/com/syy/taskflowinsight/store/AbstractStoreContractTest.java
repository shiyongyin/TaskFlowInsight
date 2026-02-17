package com.syy.taskflowinsight.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract contract test base for {@link Store} interface.
 * Subclasses must implement {@link #createStore()} to provide a concrete Store implementation.
 */
abstract class AbstractStoreContractTest {

    protected Store<String, String> store;

    @BeforeEach
    void setUp() {
        store = createStore();
    }

    /**
     * Creates a fresh Store instance for each test.
     */
    protected abstract Store<String, String> createStore();

    @Test
    void putGet_roundtrip() {
        store.put("key1", "value1");
        Optional<String> result = store.get("key1");

        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    void get_returnsEmptyForMissingKey() {
        Optional<String> result = store.get("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void remove_works() {
        store.put("key1", "value1");
        store.remove("key1");

        assertTrue(store.get("key1").isEmpty());
    }

    @Test
    void clear_emptiesTheStore() {
        store.put("key1", "value1");
        store.put("key2", "value2");
        store.clear();

        assertEquals(0, store.size());
        assertTrue(store.get("key1").isEmpty());
        assertTrue(store.get("key2").isEmpty());
    }

    @Test
    void size_reflectsActualEntries() {
        assertEquals(0, store.size());

        store.put("key1", "value1");
        assertEquals(1, store.size());

        store.put("key2", "value2");
        assertEquals(2, store.size());

        store.remove("key1");
        assertEquals(1, store.size());

        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    void get_nullKey_returnsEmpty() {
        Optional<String> result = store.get(null);

        assertTrue(result.isEmpty());
    }
}
