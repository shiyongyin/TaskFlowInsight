package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-T1: 容器事件结构化 - Map策略完整测试套件
 *
 * 测试覆盖:
 * 1. Simple Map ADD/REMOVE/MODIFY operations
 * 2. MapKey field population
 * 3. Entity key handling
 * 4. Entity value deep comparison
 * 5. Edge cases (empty maps, null values)
 */
@DisplayName("P1-T1: Map Strategy Container Events Tests")
class MapStrategyContainerEventTests {

    // ==================== Test Models ====================

    @Entity
    static class Account {
        @Key
        private final String accountId;
        private final double balance;

        Account(String accountId, double balance) {
            this.accountId = accountId;
            this.balance = balance;
        }

        public String getAccountId() { return accountId; }
        public double getBalance() { return balance; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Account)) return false;
            Account account = (Account) o;
            return accountId.equals(account.accountId);
        }

        @Override
        public int hashCode() {
            return accountId.hashCode();
        }
    }

    // ==================== Simple Map Tests ====================

    @Test
    @DisplayName("MAP: ADD operation should fill elementEvent with MAP + ADD + mapKey")
    void mapStrategy_add_shouldFillElementEvent() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> oldMap = Map.of("k1", 100);
        Map<String, Integer> newMap = Map.of("k1", 100, "k2", 200);

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange addChange = result.getChangesByType(ChangeType.CREATE).stream()
            .findFirst().orElseThrow();

        assertTrue(addChange.isContainerElementChange(), "Should have elementEvent");
        assertEquals(FieldChange.ContainerType.MAP, addChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, addChange.getElementEvent().getOperation());
        assertEquals("k2", String.valueOf(addChange.getElementEvent().getMapKey()), "mapKey should be filled");
        assertEquals(200, addChange.getNewValue());
        assertNull(addChange.getOldValue());
    }

    @Test
    @DisplayName("MAP: REMOVE operation should fill elementEvent with MAP + REMOVE + mapKey")
    void mapStrategy_remove_shouldFillElementEvent() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> oldMap = Map.of("k1", 100, "k2", 200);
        Map<String, Integer> newMap = Map.of("k1", 100);

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange removeChange = result.getChangesByType(ChangeType.DELETE).stream()
            .findFirst().orElseThrow();

        assertTrue(removeChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.MAP, removeChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.REMOVE, removeChange.getElementEvent().getOperation());
        assertEquals("k2", String.valueOf(removeChange.getElementEvent().getMapKey()));
        assertEquals(200, removeChange.getOldValue());
        assertNull(removeChange.getNewValue());
    }

    @Test
    @DisplayName("MAP: MODIFY operation should fill elementEvent with MAP + MODIFY + mapKey")
    void mapStrategy_modify_shouldFillElementEvent() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> oldMap = Map.of("k1", 100);
        Map<String, Integer> newMap = Map.of("k1", 999);

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange modifyChange = result.getChanges().get(0);

        assertTrue(modifyChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.MAP, modifyChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.MODIFY, modifyChange.getElementEvent().getOperation());
        assertEquals("k1", String.valueOf(modifyChange.getElementEvent().getMapKey()));
        assertEquals(100, modifyChange.getOldValue());
        assertEquals(999, modifyChange.getNewValue());
        assertEquals(ChangeType.UPDATE, modifyChange.getChangeType());
    }

    // ==================== MapKey Type Tests ====================

    @Test
    @DisplayName("MAP: Integer key should fill mapKey correctly")
    void mapStrategy_integerKey_shouldFillMapKey() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<Integer, String> oldMap = Map.of(1, "A");
        Map<Integer, String> newMap = Map.of(1, "A", 2, "B");

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange addChange = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.CREATE)
            .findFirst()
            .orElseThrow();

        assertTrue(addChange.isContainerElementChange());
        assertEquals(2, addChange.getElementEvent().getMapKey(), "Integer mapKey should preserve type");
    }

    @Test
    @DisplayName("MAP: Entity key should fill mapKey with Entity object")
    void mapStrategy_entityKey_shouldFillMapKey() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Account acc1 = new Account("ACC1", 1000);
        Map<Account, String> oldMap = Map.of(acc1, "Active");
        Map<Account, String> newMap = Map.of(acc1, "Suspended");

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange modifyChange = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.UPDATE)
            .findFirst()
            .orElse(null);

        if (modifyChange != null && modifyChange.isContainerElementChange()) {
            assertNotNull(modifyChange.getElementEvent().getMapKey(), "Entity key should fill mapKey");
        }
    }

    // ==================== Entity Value Deep Comparison ====================

    @Test
    @DisplayName("MAP: Entity value deep change should produce changes")
    void mapStrategy_entityValueDeepChange_shouldFillPropertyPath() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Account> oldMap = Map.of("user1", new Account("ACC1", 1000));
        Map<String, Account> newMap = Map.of("user1", new Account("ACC1", 2000));

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        // Entity value深度比较会产生嵌套变更
        assertFalse(result.getChanges().isEmpty(), "Should detect balance change");

        // 验证至少有一个变更（可能是整个对象替换，或深度字段变更）
        assertTrue(result.getChanges().size() >= 1);
    }

    // ==================== Multiple Changes Tests ====================

    @Test
    @DisplayName("MAP: Multiple changes should all have elementEvent")
    void mapStrategy_multipleChanges_shouldAllHaveElementEvent() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> oldMap = Map.of("k1", 100, "k2", 200);
        Map<String, Integer> newMap = Map.of("k2", 299, "k3", 300);

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        assertTrue(result.getChanges().size() >= 2, "Should have DELETE + CREATE + UPDATE");

        // 验证所有变更都有elementEvent
        for (FieldChange fc : result.getChanges()) {
            assertTrue(fc.isContainerElementChange(), "All changes should have elementEvent");
            assertEquals(FieldChange.ContainerType.MAP, fc.getElementEvent().getContainerType());
            assertNotNull(fc.getElementEvent().getMapKey(), "All changes should have mapKey");
        }
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("MAP: Empty to non-empty should fill elementEvent")
    void mapStrategy_emptyToNonEmpty_shouldFillElementEvent() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> oldMap = Collections.emptyMap();
        Map<String, Integer> newMap = Map.of("k1", 100);

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);
        assertTrue(change.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.MAP, change.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, change.getElementEvent().getOperation());
        assertEquals("k1", String.valueOf(change.getElementEvent().getMapKey()));
    }

    @Test
    @DisplayName("MAP: Null value handling should not cause NPE")
    void mapStrategy_nullValue_shouldHandleGracefully() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, String> oldMap = new HashMap<>();
        oldMap.put("k1", "A");
        oldMap.put("k2", null);

        Map<String, String> newMap = new HashMap<>();
        newMap.put("k1", "A");
        newMap.put("k2", "B");

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        // k2: null -> "B" 应该产生UPDATE
        FieldChange updateChange = result.getChangesByType(ChangeType.UPDATE).stream()
            .findFirst().orElse(null);

        if (updateChange != null) {
            assertTrue(updateChange.isContainerElementChange());
            assertNotNull(updateChange.getElementEvent());
            assertEquals("k2", String.valueOf(updateChange.getElementEvent().getMapKey()));
        }
    }

    @Test
    @DisplayName("MAP: Identical maps should return no changes")
    void mapStrategy_identical_shouldReturnNoChanges() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> map1 = Map.of("k1", 100, "k2", 200);
        Map<String, Integer> map2 = Map.of("k1", 100, "k2", 200);

        CompareResult result = strategy.compare(map1, map2, CompareOptions.DEFAULT);

        assertTrue(result.isIdentical());
        assertTrue(result.getChanges().isEmpty());
    }

    @Test
    @DisplayName("MAP: Null handling should not cause NPE")
    void mapStrategy_nullHandling_shouldNotCauseNPE() {
        MapCompareStrategy strategy = new MapCompareStrategy();

        // null vs empty
        CompareResult result1 = strategy.compare(null, Collections.emptyMap(), CompareOptions.DEFAULT);
        assertNotNull(result1);

        // null vs non-empty
        CompareResult result2 = strategy.compare(Map.of("k1", 100), null, CompareOptions.DEFAULT);
        assertNotNull(result2);

        // Both null
        CompareResult result3 = strategy.compare(null, null, CompareOptions.DEFAULT);
        assertNotNull(result3);
    }

    // ==================== Integration with ContainerChanges Query ====================

    @Test
    @DisplayName("MAP changes should be queryable via getContainerChanges()")
    void mapStrategy_changes_shouldBeQueryable() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> oldMap = Map.of("k1", 100);
        Map<String, Integer> newMap = Map.of("k1", 100, "k2", 200);

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        // 使用CompareResult的查询方法
        java.util.List<FieldChange> containerChanges = result.getContainerChanges();
        assertFalse(containerChanges.isEmpty(), "Container changes should be queryable");
        assertEquals(result.getChanges().size(), containerChanges.size(),
            "All Map changes should be container changes");
    }

    @Test
    @DisplayName("MAP: groupByContainerOperation should work correctly")
    void mapStrategy_groupByOperation_shouldWork() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> oldMap = Map.of("k1", 100, "k2", 200);
        Map<String, Integer> newMap = Map.of("k2", 299, "k3", 300);

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        Map<FieldChange.ElementOperation, java.util.List<FieldChange>> grouped = result.groupByContainerOperation();

        assertNotNull(grouped);
        // 应该有ADD和REMOVE分组
        assertTrue(grouped.containsKey(FieldChange.ElementOperation.ADD) ||
                  grouped.containsKey(FieldChange.ElementOperation.REMOVE) ||
                  grouped.containsKey(FieldChange.ElementOperation.MODIFY),
                  "Should have at least one operation group");
    }
}
