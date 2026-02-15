package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-T1: 容器事件结构化 - 便捷方法完整测试套件
 *
 * 测试覆盖:
 * 1. FieldChange便捷方法: isContainerElementChange(), getContainerIndex(), etc.
 * 2. CompareResult查询方法: getContainerChanges(), groupByContainerOperation()
 * 3. 边界情况: null elementEvent, 缺失字段
 * 4. 多容器类型混合查询
 */
@DisplayName("P1-T1: Convenience Methods Tests")
class ConvenienceMethodTests {

    // ==================== FieldChange Convenience Methods ====================

    @Test
    @DisplayName("isContainerElementChange() should return true when elementEvent exists")
    void isContainerElementChange_whenElementEventExists_shouldReturnTrue() {
        FieldChange change = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.UPDATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .build())
            .build();

        assertTrue(change.isContainerElementChange());
    }

    @Test
    @DisplayName("isContainerElementChange() should return false when elementEvent is null")
    void isContainerElementChange_whenElementEventNull_shouldReturnFalse() {
        FieldChange change = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.UPDATE)
            .build();

        assertFalse(change.isContainerElementChange());
    }

    @Test
    @DisplayName("getContainerIndex() should return index when available")
    void getContainerIndex_whenAvailable_shouldReturnValue() {
        FieldChange change = FieldChange.builder()
            .fieldName("[5]")
            .changeType(ChangeType.CREATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(5)
                .build())
            .build();

        assertEquals(Integer.valueOf(5), change.getContainerIndex());
    }

    @Test
    @DisplayName("getContainerIndex() should return null when elementEvent is null")
    void getContainerIndex_whenElementEventNull_shouldReturnNull() {
        FieldChange change = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.UPDATE)
            .build();

        assertNull(change.getContainerIndex());
    }

    @Test
    @DisplayName("getContainerMapKey() should return mapKey when available")
    void getContainerMapKey_whenAvailable_shouldReturnValue() {
        FieldChange change = FieldChange.builder()
            .fieldName("userMap")
            .changeType(ChangeType.UPDATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .operation(FieldChange.ElementOperation.MODIFY)
                .mapKey("user123")
                .build())
            .build();

        assertEquals("user123", change.getContainerMapKey());
    }

    @Test
    @DisplayName("getContainerMapKey() should return null when elementEvent is null")
    void getContainerMapKey_whenElementEventNull_shouldReturnNull() {
        FieldChange change = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.UPDATE)
            .build();

        assertNull(change.getContainerMapKey());
    }

    @Test
    @DisplayName("getContainerOperation() should return operation when available")
    void getContainerOperation_whenAvailable_shouldReturnValue() {
        FieldChange change = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.MOVE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MOVE)
                .oldIndex(0)
                .newIndex(5)
                .build())
            .build();

        assertEquals(FieldChange.ElementOperation.MOVE, change.getContainerOperation());
    }

    @Test
    @DisplayName("getContainerOperation() should return null when elementEvent is null")
    void getContainerOperation_whenElementEventNull_shouldReturnNull() {
        FieldChange change = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.UPDATE)
            .build();

        assertNull(change.getContainerOperation());
    }

    // ==================== CompareResult Query Methods ====================

    @Test
    @DisplayName("getContainerChanges() should filter changes with elementEvent")
    void getContainerChanges_shouldFilterCorrectly() {
        FieldChange containerChange = FieldChange.builder()
            .fieldName("[0]")
            .changeType(ChangeType.CREATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(0)
                .build())
            .build();

        FieldChange nonContainerChange = FieldChange.builder()
            .fieldName("name")
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = CompareResult.builder()
            .object1(List.of())
            .object2(List.of("A"))
            .changes(List.of(containerChange, nonContainerChange))
            .identical(false)
            .build();

        List<FieldChange> containerChanges = result.getContainerChanges();

        assertEquals(1, containerChanges.size(), "Should only return container changes");
        assertTrue(containerChanges.get(0).isContainerElementChange());
    }

    @Test
    @DisplayName("getContainerChanges() should return empty list when no container changes")
    void getContainerChanges_whenNoContainerChanges_shouldReturnEmpty() {
        FieldChange nonContainerChange = FieldChange.builder()
            .fieldName("name")
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = CompareResult.builder()
            .object1("old")
            .object2("new")
            .changes(List.of(nonContainerChange))
            .identical(false)
            .build();

        List<FieldChange> containerChanges = result.getContainerChanges();

        assertTrue(containerChanges.isEmpty(), "Should return empty list");
    }

    @Test
    @DisplayName("groupByContainerOperation() should group changes correctly")
    void groupByContainerOperation_shouldGroupCorrectly() {
        FieldChange addChange = FieldChange.builder()
            .fieldName("[0]")
            .changeType(ChangeType.CREATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(0)
                .build())
            .build();

        FieldChange removeChange = FieldChange.builder()
            .fieldName("[1]")
            .changeType(ChangeType.DELETE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.REMOVE)
                .index(1)
                .build())
            .build();

        FieldChange modifyChange = FieldChange.builder()
            .fieldName("[2]")
            .changeType(ChangeType.UPDATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.MODIFY)
                .index(2)
                .build())
            .build();

        CompareResult result = CompareResult.builder()
            .object1(List.of("A", "B", "C"))
            .object2(List.of("D", "E"))
            .changes(List.of(addChange, removeChange, modifyChange))
            .identical(false)
            .build();

        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();

        assertEquals(3, grouped.size(), "Should have 3 operation groups");
        assertTrue(grouped.containsKey(FieldChange.ElementOperation.ADD));
        assertTrue(grouped.containsKey(FieldChange.ElementOperation.REMOVE));
        assertTrue(grouped.containsKey(FieldChange.ElementOperation.MODIFY));

        assertEquals(1, grouped.get(FieldChange.ElementOperation.ADD).size());
        assertEquals(1, grouped.get(FieldChange.ElementOperation.REMOVE).size());
        assertEquals(1, grouped.get(FieldChange.ElementOperation.MODIFY).size());
    }

    @Test
    @DisplayName("groupByContainerOperation() should return empty map when no container changes")
    void groupByContainerOperation_whenNoContainerChanges_shouldReturnEmpty() {
        FieldChange nonContainerChange = FieldChange.builder()
            .fieldName("name")
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = CompareResult.builder()
            .object1("old")
            .object2("new")
            .changes(List.of(nonContainerChange))
            .identical(false)
            .build();

        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();

        assertTrue(grouped.isEmpty(), "Should return empty map");
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Convenience methods should work with real strategy results")
    void convenienceMethods_withRealStrategy_shouldWork() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        List<String> oldList = List.of("A", "B");
        List<String> newList = List.of("A", "C", "D");

        CompareResult result = strategy.compare(oldList, newList, CompareOptions.DEFAULT);

        // 使用 getContainerChanges()
        List<FieldChange> containerChanges = result.getContainerChanges();
        assertFalse(containerChanges.isEmpty());

        // 验证每个change的便捷方法
        for (FieldChange fc : containerChanges) {
            assertTrue(fc.isContainerElementChange());
            assertNotNull(fc.getContainerOperation());

            // LIST changes应该有index
            if (fc.getElementEvent().getContainerType() == FieldChange.ContainerType.LIST) {
                assertNotNull(fc.getContainerIndex(), "LIST changes should have index");
            }
        }

        // 使用 groupByContainerOperation()
        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();
        assertFalse(grouped.isEmpty());
    }

    @Test
    @DisplayName("Mixed container and non-container changes should be handled correctly")
    void mixedChanges_shouldBeHandledCorrectly() {
        FieldChange containerChange1 = FieldChange.builder()
            .fieldName("[0]")
            .changeType(ChangeType.CREATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                .operation(FieldChange.ElementOperation.ADD)
                .index(0)
                .build())
            .build();

        FieldChange containerChange2 = FieldChange.builder()
            .fieldName("key1")
            .changeType(ChangeType.UPDATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.MAP)
                .operation(FieldChange.ElementOperation.MODIFY)
                .mapKey("key1")
                .build())
            .build();

        FieldChange nonContainerChange = FieldChange.builder()
            .fieldName("scalarField")
            .changeType(ChangeType.UPDATE)
            .build();

        CompareResult result = CompareResult.builder()
            .object1("old")
            .object2("new")
            .changes(List.of(containerChange1, containerChange2, nonContainerChange))
            .identical(false)
            .build();

        // getContainerChanges应该只返回容器变更
        List<FieldChange> containerChanges = result.getContainerChanges();
        assertEquals(2, containerChanges.size());

        // 验证过滤正确
        for (FieldChange fc : containerChanges) {
            assertTrue(fc.isContainerElementChange());
        }

        // 验证非容器变更被正确排除
        assertFalse(containerChanges.contains(nonContainerChange));
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Convenience methods should handle null-safe operations")
    void convenienceMethods_nullSafe_shouldNotThrow() {
        FieldChange change = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.UPDATE)
            .elementEvent(null)
            .build();

        // 所有便捷方法应该null-safe
        assertDoesNotThrow(() -> {
            change.isContainerElementChange();
            change.getContainerIndex();
            change.getContainerMapKey();
            change.getContainerOperation();
        });
    }

    @Test
    @DisplayName("groupByContainerOperation() should handle changes without operations")
    void groupByContainerOperation_withoutOperations_shouldHandleGracefully() {
        FieldChange changeWithoutOperation = FieldChange.builder()
            .fieldName("test")
            .changeType(ChangeType.UPDATE)
            .elementEvent(FieldChange.ContainerElementEvent.builder()
                .containerType(FieldChange.ContainerType.LIST)
                // operation未设置
                .build())
            .build();

        CompareResult result = CompareResult.builder()
            .object1("old")
            .object2("new")
            .changes(List.of(changeWithoutOperation))
            .identical(false)
            .build();

        // 应该能处理operation为null的情况
        assertDoesNotThrow(() -> {
            Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();
            // 可能为空或包含null key，但不应抛异常
            assertNotNull(grouped);
        });
    }
}
