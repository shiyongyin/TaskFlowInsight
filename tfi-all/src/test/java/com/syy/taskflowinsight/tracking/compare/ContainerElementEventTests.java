package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-T1: 容器事件结构化 单元测试
 */
class ContainerElementEventTests {

    @Test
    void list_create_should_fill_element_event_with_index_and_add() {
        SimpleListStrategy strategy = new SimpleListStrategy();
        CompareResult result = strategy.compare(List.of(1), List.of(1, 2), CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange last = result.getChanges().get(result.getChanges().size() - 1);
        assertTrue(last.isContainerElementChange());
        assertNotNull(last.getElementEvent());
        assertEquals(FieldChange.ContainerType.LIST, last.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, last.getElementEvent().getOperation());
        assertEquals(Integer.valueOf(1), last.getElementEvent().getIndex());
    }

    @Test
    void set_add_remove_should_fill_element_event() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        CompareResult result = strategy.compare(Set.of("A"), Set.of("A", "B"), CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        boolean hasAdd = result.getChanges().stream()
            .anyMatch(fc -> fc.isContainerElementChange()
                && fc.getElementEvent().getContainerType() == FieldChange.ContainerType.SET
                && fc.getElementEvent().getOperation() == FieldChange.ElementOperation.ADD);
        assertTrue(hasAdd, "SET should contain ADD element event");
    }

    @Test
    void map_update_should_fill_element_event_with_mapKey() {
        MapCompareStrategy strategy = new MapCompareStrategy();
        CompareResult result = strategy.compare(Map.of("k", 1), Map.of("k", 2), CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);
        assertTrue(change.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.MAP, change.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.MODIFY, change.getElementEvent().getOperation());
        assertEquals("k", String.valueOf(change.getElementEvent().getMapKey()));
    }
}

