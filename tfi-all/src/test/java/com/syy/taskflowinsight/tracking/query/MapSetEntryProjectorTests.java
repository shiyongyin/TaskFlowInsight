package com.syy.taskflowinsight.tracking.query;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MapSetEntryProjector 单元测试
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 */
class MapSetEntryProjectorTests {

    // ========== Map Tests ==========

    @Test
    void projectMap_emptyMaps_shouldReturnEmpty() {
        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, Map.of(), Map.of(), "data");

        assertTrue(events.isEmpty());
    }

    @Test
    void projectMap_addedEntry_shouldGenerateEntryAdded() {
        Map<String, String> left = new HashMap<>();
        Map<String, String> right = new HashMap<>();
        right.put("key1", "value1");

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, left, right, "config");

        assertEquals(1, events.size());
        Map<String, Object> event = events.get(0);
        assertEquals("entry_added", event.get("kind"));
        assertEquals("Map", event.get("object"));
        assertTrue(event.get("path").toString().contains("config"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        assertEquals("key1", details.get("keyOrIndex"));
        assertEquals("value1", details.get("entryValue"));
    }

    @Test
    void projectMap_removedEntry_shouldGenerateEntryRemoved() {
        Map<String, String> left = new HashMap<>();
        left.put("key1", "value1");
        Map<String, String> right = new HashMap<>();

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, left, right, "config");

        assertEquals(1, events.size());
        Map<String, Object> event = events.get(0);
        assertEquals("entry_removed", event.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        assertEquals("key1", details.get("keyOrIndex"));
        assertEquals("value1", details.get("entryValue"));
    }

    @Test
    void projectMap_updatedEntry_shouldGenerateEntryUpdated() {
        Map<String, String> left = new HashMap<>();
        left.put("key1", "oldValue");
        Map<String, String> right = new HashMap<>();
        right.put("key1", "newValue");

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, left, right, "config");

        assertEquals(1, events.size());
        Map<String, Object> event = events.get(0);
        assertEquals("entry_updated", event.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        assertEquals("oldValue", details.get("oldEntryValue"));
        assertEquals("newValue", details.get("newEntryValue"));
    }

    @Test
    void projectMap_multipleChanges_shouldGenerateMultipleEvents() {
        Map<String, String> left = new HashMap<>();
        left.put("key1", "value1");
        left.put("key2", "value2");

        Map<String, String> right = new HashMap<>();
        right.put("key2", "updatedValue2");
        right.put("key3", "value3");

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, left, right, "config");

        assertEquals(3, events.size()); // 1 removed + 1 updated + 1 added

        long removedCount = events.stream()
                .filter(e -> "entry_removed".equals(e.get("kind")))
                .count();
        long updatedCount = events.stream()
                .filter(e -> "entry_updated".equals(e.get("kind")))
                .count();
        long addedCount = events.stream()
                .filter(e -> "entry_added".equals(e.get("kind")))
                .count();

        assertEquals(1, removedCount);
        assertEquals(1, updatedCount);
        assertEquals(1, addedCount);
    }

    @Test
    void projectMap_nullMaps_shouldHandleGracefully() {
        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, null, null, "config");

        assertTrue(events.isEmpty());
    }

    @Test
    void projectMap_nullContainerPath_shouldHandleGracefully() {
        Map<String, String> left = Map.of();
        Map<String, String> right = Map.of("key", "value");

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectMap(
                result, left, right, null);

        assertEquals(1, events.size());
        assertNotNull(events.get(0).get("path"));
    }

    // ========== Set Tests ==========

    @Test
    void projectSet_emptySets_shouldReturnEmpty() {
        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, Set.of(), Set.of(), "tags");

        assertTrue(events.isEmpty());
    }

    @Test
    void projectSet_addedElement_shouldGenerateEntryAdded() {
        Set<String> left = new HashSet<>();
        Set<String> right = new HashSet<>();
        right.add("tag1");

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, left, right, "tags");

        assertEquals(1, events.size());
        Map<String, Object> event = events.get(0);
        assertEquals("entry_added", event.get("kind"));
        assertEquals("Set", event.get("object"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        assertEquals("tag1", details.get("keyOrIndex"));
        assertEquals("tag1", details.get("entryValue"));
    }

    @Test
    void projectSet_removedElement_shouldGenerateEntryRemoved() {
        Set<String> left = new HashSet<>();
        left.add("tag1");
        Set<String> right = new HashSet<>();

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, left, right, "tags");

        assertEquals(1, events.size());
        Map<String, Object> event = events.get(0);
        assertEquals("entry_removed", event.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        assertEquals("tag1", details.get("entryValue"));
    }

    @Test
    void projectSet_multipleChanges_shouldGenerateMultipleEvents() {
        Set<String> left = new HashSet<>();
        left.add("tag1");
        left.add("tag2");

        Set<String> right = new HashSet<>();
        right.add("tag2");
        right.add("tag3");

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, left, right, "tags");

        assertEquals(2, events.size()); // 1 removed + 1 added

        long removedCount = events.stream()
                .filter(e -> "entry_removed".equals(e.get("kind")))
                .count();
        long addedCount = events.stream()
                .filter(e -> "entry_added".equals(e.get("kind")))
                .count();

        assertEquals(1, removedCount);
        assertEquals(1, addedCount);
    }

    @Test
    void projectSet_entityElements_shouldUseEntityKey() {
        Set<TestEntity> left = new HashSet<>();
        Set<TestEntity> right = new HashSet<>();
        right.add(new TestEntity("1", "Alice"));

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, left, right, "users");

        assertEquals(1, events.size());
        Map<String, Object> event = events.get(0);
        assertEquals("entry_added", event.get("kind"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) event.get("details");
        // keyOrIndex should use entity key if available
        assertNotNull(details.get("keyOrIndex"));
    }

    @Test
    void projectSet_nullSets_shouldHandleGracefully() {
        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, null, null, "tags");

        assertTrue(events.isEmpty());
    }

    @Test
    void projectSet_nullContainerPath_shouldHandleGracefully() {
        Set<String> left = Set.of();
        Set<String> right = Set.of("item");

        CompareResult result = CompareResult.builder().build();
        List<Map<String, Object>> events = MapSetEntryProjector.projectSet(
                result, left, right, null);

        assertEquals(1, events.size());
        assertNotNull(events.get(0).get("path"));
    }

    // ========== 测试实体类 ==========

    @Entity
    static class TestEntity {
        @Key
        private final String id;
        private final String name;

        TestEntity(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestEntity that = (TestEntity) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}

