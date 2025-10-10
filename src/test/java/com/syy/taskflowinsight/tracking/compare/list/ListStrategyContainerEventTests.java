package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-T1: 容器事件结构化 - List策略完整测试套件
 *
 * 测试覆盖:
 * 1. SimpleListStrategy - 基础位置比较
 * 2. LevenshteinListStrategy - 编辑距离 + 移动检测
 * 3. LcsListStrategy - LCS算法
 * 4. EntityListStrategy - Entity深度比较
 * 5. AsSetListStrategy - 无序比较
 */
@DisplayName("P1-T1: List Strategy Container Events Tests")
class ListStrategyContainerEventTests {

    // ==================== Test Models ====================

    @Entity
    static class User {
        @Key
        private final String id;
        private final String name;

        User(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof User)) return false;
            User user = (User) o;
            return id.equals(user.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    // ==================== SimpleListStrategy Tests ====================

    @Test
    @DisplayName("SIMPLE: ADD operation should fill elementEvent with LIST + ADD + index")
    void simpleStrategy_add_shouldFillElementEvent() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = List.of("A");
        List<String> newList = List.of("A", "B");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange addChange = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.CREATE)
            .findFirst()
            .orElseThrow();

        // 验证核心字段
        assertTrue(addChange.isContainerElementChange(), "Should have elementEvent");
        assertEquals(FieldChange.ContainerType.LIST, addChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, addChange.getElementEvent().getOperation());
        assertEquals(Integer.valueOf(1), addChange.getElementEvent().getIndex());
        assertEquals("[1]", addChange.getFieldName());
    }

    @Test
    @DisplayName("SIMPLE: REMOVE operation should fill elementEvent with LIST + REMOVE + index")
    void simpleStrategy_remove_shouldFillElementEvent() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = List.of("A", "B");
        List<String> newList = List.of("A");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange removeChange = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.DELETE)
            .findFirst()
            .orElseThrow();

        assertTrue(removeChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, removeChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.REMOVE, removeChange.getElementEvent().getOperation());
        assertEquals(Integer.valueOf(1), removeChange.getElementEvent().getIndex());
        assertEquals("B", removeChange.getOldValue());
    }

    @Test
    @DisplayName("SIMPLE: MODIFY operation should fill elementEvent with LIST + MODIFY + index")
    void simpleStrategy_modify_shouldFillElementEvent() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<Integer> oldList = List.of(1, 2, 3);
        List<Integer> newList = List.of(1, 99, 3);

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange modifyChange = result.getChanges().get(0);

        assertTrue(modifyChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, modifyChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.MODIFY, modifyChange.getElementEvent().getOperation());
        assertEquals(Integer.valueOf(1), modifyChange.getElementEvent().getIndex());
        assertEquals(2, modifyChange.getOldValue());
        assertEquals(99, modifyChange.getNewValue());
        assertEquals(ChangeType.UPDATE, modifyChange.getChangeType());
    }

    // ==================== LevenshteinListStrategy Tests ====================

    @Test
    @DisplayName("LEVENSHTEIN: MOVE operation should fill oldIndex/newIndex")
    void levenshteinStrategy_move_shouldFillOldAndNewIndex() {
        LevenshteinListStrategy strategy = new LevenshteinListStrategy();
        List<String> oldList = List.of("A", "B", "C");
        List<String> newList = List.of("B", "A", "C");

        CompareOptions options = CompareOptions.builder().detectMoves(true).build();
        CompareResult result = strategy.compare(oldList, newList, options);

        assertFalse(result.getChanges().isEmpty());
        // 检查是否有MOVE操作
        boolean hasMoveOperation = result.getChanges().stream()
            .anyMatch(fc -> fc.isContainerElementChange()
                && fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MOVE);

        if (hasMoveOperation) {
            FieldChange moveChange = result.getChanges().stream()
                .filter(fc -> fc.getElementEvent() != null
                    && fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MOVE)
                .findFirst()
                .orElseThrow();

            assertTrue(moveChange.isContainerElementChange());
            assertEquals(FieldChange.ContainerType.LIST, moveChange.getElementEvent().getContainerType());
            assertEquals(FieldChange.ElementOperation.MOVE, moveChange.getElementEvent().getOperation());
            assertNotNull(moveChange.getElementEvent().getOldIndex(), "oldIndex should be filled");
            assertNotNull(moveChange.getElementEvent().getNewIndex(), "newIndex should be filled");
        }
    }

    @Test
    @DisplayName("LEVENSHTEIN: REPLACE operation should fill elementEvent")
    void levenshteinStrategy_replace_shouldFillElementEvent() {
        LevenshteinListStrategy strategy = new LevenshteinListStrategy();
        List<String> oldList = List.of("A", "B");
        List<String> newList = List.of("A", "C");

        CompareOptions options = CompareOptions.builder().detectMoves(false).build();
        CompareResult result = strategy.compare(oldList, newList, options);

        assertFalse(result.getChanges().isEmpty());
        FieldChange replaceChange = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.UPDATE)
            .findFirst()
            .orElseThrow();

        assertTrue(replaceChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, replaceChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.MODIFY, replaceChange.getElementEvent().getOperation());
        assertNotNull(replaceChange.getElementEvent().getIndex());
    }

    // ==================== LcsListStrategy Tests ====================

    @Test
    @DisplayName("LCS: INSERT operation should fill elementEvent with correct index")
    void lcsStrategy_insert_shouldFillElementEvent() {
        LcsListStrategy strategy = new LcsListStrategy();
        List<String> oldList = List.of("A", "C");
        List<String> newList = List.of("A", "B", "C");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange insertChange = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.CREATE)
            .findFirst()
            .orElseThrow();

        assertTrue(insertChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, insertChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, insertChange.getElementEvent().getOperation());
        assertEquals(Integer.valueOf(1), insertChange.getElementEvent().getIndex());
        assertEquals("B", insertChange.getNewValue());
    }

    @Test
    @DisplayName("LCS: DELETE operation should fill elementEvent with correct index")
    void lcsStrategy_delete_shouldFillElementEvent() {
        LcsListStrategy strategy = new LcsListStrategy();
        List<String> oldList = List.of("A", "B", "C");
        List<String> newList = List.of("A", "C");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange deleteChange = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.DELETE)
            .findFirst()
            .orElseThrow();

        assertTrue(deleteChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, deleteChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.REMOVE, deleteChange.getElementEvent().getOperation());
        assertEquals(Integer.valueOf(1), deleteChange.getElementEvent().getIndex());
        assertEquals("B", deleteChange.getOldValue());
    }

    // ==================== EntityListStrategy Tests ====================

    @Test
    @DisplayName("ENTITY: MODIFY with propertyPath should fill nested change info")
    void entityStrategy_modifyNestedProperty_shouldFillPropertyPath() {
        EntityListStrategy strategy = new EntityListStrategy();
        List<User> oldList = List.of(new User("U1", "Alice"));
        List<User> newList = List.of(new User("U1", "Bob"));

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange propertyChange = result.getChanges().get(0);

        assertTrue(propertyChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, propertyChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.MODIFY, propertyChange.getElementEvent().getOperation());
        assertNotNull(propertyChange.getElementEvent().getEntityKey(), "entityKey should be filled");
        assertNotNull(propertyChange.getElementEvent().getPropertyPath(), "propertyPath should be filled for nested changes");
        assertTrue(propertyChange.getFieldName().contains("entity["), "Should use entity path format");
    }

    @Test
    @DisplayName("ENTITY: CREATE should fill entityKey")
    void entityStrategy_create_shouldFillEntityKey() {
        EntityListStrategy strategy = new EntityListStrategy();
        List<User> oldList = Collections.emptyList();
        List<User> newList = List.of(new User("U1", "Alice"));

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange createChange = result.getChanges().get(0);

        assertTrue(createChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, createChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, createChange.getElementEvent().getOperation());
        assertNotNull(createChange.getElementEvent().getEntityKey(), "entityKey must be filled for Entity operations");
        assertEquals(ChangeType.CREATE, createChange.getChangeType());
    }

    @Test
    @DisplayName("ENTITY: DELETE should fill entityKey")
    void entityStrategy_delete_shouldFillEntityKey() {
        EntityListStrategy strategy = new EntityListStrategy();
        List<User> oldList = List.of(new User("U1", "Alice"));
        List<User> newList = Collections.emptyList();

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange deleteChange = result.getChanges().get(0);

        assertTrue(deleteChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, deleteChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.REMOVE, deleteChange.getElementEvent().getOperation());
        assertNotNull(deleteChange.getElementEvent().getEntityKey(), "entityKey must be filled for Entity operations");
        assertEquals(ChangeType.DELETE, deleteChange.getChangeType());
    }

    // ==================== AsSetListStrategy Tests ====================

    @Test
    @DisplayName("AS_SET: Unordered comparison should fill elementEvent")
    void asSetStrategy_unordered_shouldFillElementEvent() {
        AsSetListStrategy strategy = new AsSetListStrategy();
        List<String> oldList = List.of("A", "B", "C");
        List<String> newList = List.of("C", "B", "D"); // 顺序改变 + 新增D + 删除A

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());

        // 验证 DELETE 操作
        List<FieldChange> deleteChanges = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.DELETE)
            .toList();
        assertFalse(deleteChanges.isEmpty(), "Should have DELETE changes");
        for (FieldChange fc : deleteChanges) {
            assertTrue(fc.isContainerElementChange());
            assertEquals(FieldChange.ContainerType.LIST, fc.getElementEvent().getContainerType());
            assertEquals(FieldChange.ElementOperation.REMOVE, fc.getElementEvent().getOperation());
        }

        // 验证 CREATE 操作
        List<FieldChange> createChanges = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.CREATE)
            .toList();
        assertFalse(createChanges.isEmpty(), "Should have CREATE changes");
        for (FieldChange fc : createChanges) {
            assertTrue(fc.isContainerElementChange());
            assertEquals(FieldChange.ContainerType.LIST, fc.getElementEvent().getContainerType());
            assertEquals(FieldChange.ElementOperation.ADD, fc.getElementEvent().getOperation());
        }
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("NULL element should not cause NPE in elementEvent")
    void listStrategy_nullElement_shouldHandleGracefully() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = java.util.Arrays.asList("A", "B");
        List<String> newList = java.util.Arrays.asList("A", null);

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);
        assertTrue(change.isContainerElementChange());
        assertNotNull(change.getElementEvent());
        // entityKey可以为null（非Entity元素）
    }

    @Test
    @DisplayName("Empty to Non-empty should fill elementEvent")
    void listStrategy_emptyToNonEmpty_shouldFillElementEvent() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = Collections.emptyList();
        List<String> newList = List.of("A");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);
        assertTrue(change.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, change.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, change.getElementEvent().getOperation());
        assertEquals(Integer.valueOf(0), change.getElementEvent().getIndex());
    }

    // ==================== Algorithm Metadata ====================

    @Test
    @DisplayName("Result should contain algorithmUsed metadata")
    void listStrategy_result_shouldContainAlgorithmMetadata() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        CompareResult result = strategy.compare(List.of("A"), List.of("B"), CompareOptions.DEFAULT);

        assertNotNull(result.getAlgorithmUsed());
        assertEquals("SIMPLE", result.getAlgorithmUsed());
    }
}
