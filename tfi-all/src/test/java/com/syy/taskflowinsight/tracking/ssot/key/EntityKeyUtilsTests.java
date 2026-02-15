package com.syy.taskflowinsight.tracking.ssot.key;

import com.syy.taskflowinsight.annotation.Key;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EntityKeyUtilsTests {

    static class SimpleEntity {
        @Key
        private Long id;

        public SimpleEntity(Long id) {
            this.id = id;
        }
    }

    static class CompositeEntity {
        @Key
        private String code;
        @Key
        private String region;

        public CompositeEntity(String code, String region) {
            this.code = code;
            this.region = region;
        }
    }

    static class NoKeyEntity {
        private String name;

        public NoKeyEntity(String name) {
            this.name = name;
        }
    }

    @Test
    void computeStableKey_simpleKey() {
        SimpleEntity entity = new SimpleEntity(123L);
        Optional<String> key = EntityKeyUtils.tryComputeStableKey(entity);

        assertTrue(key.isPresent());
        assertTrue(key.get().contains("id=123"));
    }

    @Test
    void computeStableKey_compositeKey() {
        CompositeEntity entity = new CompositeEntity("ABC", "US");
        Optional<String> key = EntityKeyUtils.tryComputeStableKey(entity);

        assertTrue(key.isPresent());
        assertTrue(key.get().contains("code=ABC"));
        assertTrue(key.get().contains("region=US"));
    }

    @Test
    void computeStableKey_escapeColon() {
        CompositeEntity entity = new CompositeEntity("A:B", "US");
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(entity);

        assertTrue(key.contains("A\\:B")); // Should escape colon
    }

    @Test
    void computeStableKey_noKey_shouldReturnUnresolved() {
        NoKeyEntity entity = new NoKeyEntity("test");
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(entity);

        assertEquals(EntityKeyUtils.UNRESOLVED, key);
    }

    @Test
    void computeStableKey_null_shouldHandleGracefully() {
        Optional<String> key = EntityKeyUtils.tryComputeStableKey(null);
        assertFalse(key.isPresent());

        String orUnresolved = EntityKeyUtils.computeStableKeyOrUnresolved(null);
        assertEquals(EntityKeyUtils.UNRESOLVED, orUnresolved);
    }
}
