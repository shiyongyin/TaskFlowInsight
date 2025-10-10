package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-T1: 容器事件结构化 - 向后兼容性测试套件
 *
 * 测试目标:
 * 1. 验证不使用elementEvent的旧代码继续工作
 * 2. 验证elementEvent为null不会导致NPE
 * 3. 验证现有API行为不变
 * 4. 验证FieldChange.builder()兼容性
 */
@DisplayName("P1-T1: Backward Compatibility Tests")
class BackwardCompatibilityTests {

    // ==================== Legacy FieldChange Usage ====================

    @Test
    @DisplayName("FieldChange without elementEvent should build successfully")
    void fieldChange_withoutElementEvent_shouldBuildSuccessfully() {
        assertDoesNotThrow(() -> {
            FieldChange change = FieldChange.builder()
                .fieldName("legacyField")
                .oldValue(100)
                .newValue(200)
                .changeType(ChangeType.UPDATE)
                .build();

            assertEquals("legacyField", change.getFieldName());
            assertEquals(100, change.getOldValue());
            assertEquals(200, change.getNewValue());
            assertEquals(ChangeType.UPDATE, change.getChangeType());
            assertNull(change.getElementEvent(), "elementEvent defaults to null");
        });
    }

    @Test
    @DisplayName("Legacy code checking getFieldName() should continue to work")
    void legacyCode_getFieldName_shouldWork() {
        FieldChange change = FieldChange.builder()
            .fieldName("name")
            .oldValue("Alice")
            .newValue("Bob")
            .changeType(ChangeType.UPDATE)
            .build();

        // 旧代码只关心fieldName, oldValue, newValue
        assertEquals("name", change.getFieldName());
        assertEquals("Alice", change.getOldValue());
        assertEquals("Bob", change.getNewValue());
    }

    @Test
    @DisplayName("Legacy code checking changeType should continue to work")
    void legacyCode_changeType_shouldWork() {
        FieldChange create = FieldChange.builder()
            .fieldName("newField")
            .newValue("value")
            .changeType(ChangeType.CREATE)
            .build();

        FieldChange delete = FieldChange.builder()
            .fieldName("removedField")
            .oldValue("value")
            .changeType(ChangeType.DELETE)
            .build();

        assertEquals(ChangeType.CREATE, create.getChangeType());
        assertEquals(ChangeType.DELETE, delete.getChangeType());
    }

    // ==================== Null ElementEvent Safety ====================

    @Test
    @DisplayName("isContainerElementChange() with null elementEvent should return false")
    void isContainerElementChange_withNullElementEvent_shouldReturnFalse() {
        FieldChange change = FieldChange.builder()
            .fieldName("field")
            .changeType(ChangeType.UPDATE)
            .elementEvent(null)
            .build();

        assertFalse(change.isContainerElementChange());
    }

    @Test
    @DisplayName("getContainerIndex() with null elementEvent should return null")
    void getContainerIndex_withNullElementEvent_shouldReturnNull() {
        FieldChange change = FieldChange.builder()
            .fieldName("field")
            .changeType(ChangeType.UPDATE)
            .build();

        assertNull(change.getContainerIndex());
    }

    @Test
    @DisplayName("getContainerMapKey() with null elementEvent should return null")
    void getContainerMapKey_withNullElementEvent_shouldReturnNull() {
        FieldChange change = FieldChange.builder()
            .fieldName("field")
            .changeType(ChangeType.UPDATE)
            .build();

        assertNull(change.getContainerMapKey());
    }

    @Test
    @DisplayName("getContainerOperation() with null elementEvent should return null")
    void getContainerOperation_withNullElementEvent_shouldReturnNull() {
        FieldChange change = FieldChange.builder()
            .fieldName("field")
            .changeType(ChangeType.UPDATE)
            .build();

        assertNull(change.getContainerOperation());
    }

    // ==================== Existing Strategy Behavior ====================

    @Test
    @DisplayName("SimpleListStrategy should still produce basic FieldChange fields")
    void simpleListStrategy_shouldProduceBasicFields() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = List.of("A");
        List<String> newList = List.of("B");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);

        // 验证基本字段（向后兼容）
        assertNotNull(change.getFieldName());
        assertNotNull(change.getChangeType());
        assertNotNull(change.getOldValue());
        assertNotNull(change.getNewValue());
    }

    @Test
    @DisplayName("MapCompareStrategy should still produce basic FieldChange fields")
    void mapCompareStrategy_shouldProduceBasicFields() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        Map<String, Integer> oldMap = Map.of("k1", 100);
        Map<String, Integer> newMap = Map.of("k1", 200);

        CompareResult result = strategy.compare(oldMap, newMap, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);

        // 验证基本字段（向后兼容）
        assertNotNull(change.getFieldName());
        assertNotNull(change.getChangeType());
        assertEquals(100, change.getOldValue());
        assertEquals(200, change.getNewValue());
    }

    @Test
    @DisplayName("SetCompareStrategy should still produce basic FieldChange fields")
    void setCompareStrategy_shouldProduceBasicFields() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<String> oldSet = Set.of("A");
        Set<String> newSet = Set.of("A", "B");

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.CREATE)
            .findFirst()
            .orElseThrow();

        // 验证基本字段（向后兼容）
        assertNotNull(change.getFieldName());
        assertEquals(ChangeType.CREATE, change.getChangeType());
        assertEquals("B", change.getNewValue());
    }

    // ==================== CompareResult Query Methods ====================

    @Test
    @DisplayName("getContainerChanges() on result without container changes should return empty list")
    void getContainerChanges_withoutContainerChanges_shouldReturnEmpty() {
        FieldChange nonContainerChange = FieldChange.builder()
            .fieldName("scalarField")
            .oldValue(10)
            .newValue(20)
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = CompareResult.builder()
            .object1("old")
            .object2("new")
            .changes(List.of(nonContainerChange))
            .identical(false)
            .build();

        List<FieldChange> containerChanges = result.getContainerChanges();

        assertNotNull(containerChanges);
        assertTrue(containerChanges.isEmpty(), "Should return empty list, not null");
    }

    @Test
    @DisplayName("groupByContainerOperation() on result without container changes should return empty map")
    void groupByContainerOperation_withoutContainerChanges_shouldReturnEmpty() {
        FieldChange nonContainerChange = FieldChange.builder()
            .fieldName("scalarField")
            .oldValue(10)
            .newValue(20)
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = CompareResult.builder()
            .object1("old")
            .object2("new")
            .changes(List.of(nonContainerChange))
            .identical(false)
            .build();

        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();

        assertNotNull(grouped);
        assertTrue(grouped.isEmpty(), "Should return empty map, not null");
    }

    // ==================== Legacy Test Patterns ====================

    @Test
    @DisplayName("Legacy test pattern: checking changes.size() should still work")
    void legacyPattern_checkingChangesSize_shouldWork() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = List.of("A", "B");
        List<String> newList = List.of("A", "C");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // 旧代码常见模式：只检查changes数量
        assertEquals(1, result.getChanges().size());
    }

    @Test
    @DisplayName("Legacy test pattern: filtering by ChangeType should still work")
    void legacyPattern_filteringByChangeType_shouldWork() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = List.of("A");
        List<String> newList = List.of("A", "B");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // 旧代码常见模式：按ChangeType过滤
        List<FieldChange> creates = result.getChanges().stream()
            .filter(fc -> fc.getChangeType() == ChangeType.CREATE)
            .toList();

        assertEquals(1, creates.size());
        assertEquals("B", creates.get(0).getNewValue());
    }

    @Test
    @DisplayName("Legacy test pattern: checking fieldName pattern should still work")
    void legacyPattern_checkingFieldNamePattern_shouldWork() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = List.of("A");
        List<String> newList = List.of("B");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // 旧代码常见模式：检查fieldName格式
        FieldChange change = result.getChanges().get(0);
        assertTrue(change.getFieldName().startsWith("["), "List index should start with [");
        assertTrue(change.getFieldName().endsWith("]"), "List index should end with ]");
    }

    // ==================== API Stability ====================

    @Test
    @DisplayName("FieldChange.builder() API should remain stable")
    void fieldChangeBuilder_apiStability() {
        assertDoesNotThrow(() -> {
            FieldChange.builder()
                .fieldName("test")
                .oldValue("old")
                .newValue("new")
                .changeType(ChangeType.UPDATE)
                .build();
        });
    }

    @Test
    @DisplayName("CompareResult.builder() API should remain stable")
    void compareResultBuilder_apiStability() {
        assertDoesNotThrow(() -> {
            CompareResult.builder()
                .object1("old")
                .object2("new")
                .changes(List.of())
                .identical(true)
                .build();
        });
    }

    @Test
    @DisplayName("CompareOptions.DEFAULT should remain accessible")
    void compareOptionsDefault_shouldBeAccessible() {
        assertNotNull(CompareOptions.DEFAULT);
        assertDoesNotThrow(() -> {
            SimpleListStrategy strategy = new SimpleListStrategy();
            strategy.compare(List.of(), List.of(), CompareOptions.DEFAULT);
        });
    }

    // ==================== No Breaking Changes Verification ====================

    @Test
    @DisplayName("Existing changes list iteration should not break")
    void existingChangesList_iteration_shouldNotBreak() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = List.of("A", "B", "C");
        List<String> newList = List.of("A", "D", "E");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // 旧代码常见模式：直接遍历changes
        assertDoesNotThrow(() -> {
            for (FieldChange change : result.getChanges()) {
                String fieldName = change.getFieldName();
                Object oldValue = change.getOldValue();
                Object newValue = change.getNewValue();
                ChangeType type = change.getChangeType();

                assertNotNull(fieldName);
                // oldValue or newValue can be null (CREATE/DELETE)
                assertNotNull(type);
            }
        });
    }

    @Test
    @DisplayName("Existing serialization patterns should not break")
    void existingSerialization_shouldNotBreak() {
        FieldChange change = FieldChange.builder()
            .fieldName("name")
            .oldValue("Alice")
            .newValue("Bob")
            .changeType(ChangeType.UPDATE)
            .build();

        // 旧代码常见模式：toString() for logging
        assertDoesNotThrow(() -> {
            String str = change.toString();
            assertNotNull(str);
        });
    }
}
