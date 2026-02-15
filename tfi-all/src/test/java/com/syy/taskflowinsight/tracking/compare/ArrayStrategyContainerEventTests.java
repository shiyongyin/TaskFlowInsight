package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ARRAY: Container events & query helper integration")
class ArrayStrategyContainerEventTests {

    @Test
    @DisplayName("Array changes should be queryable via getContainerChanges() and grouped by operation")
    void array_changes_shouldBeQueryable() {
        ArrayCompareStrategy strategy = new ArrayCompareStrategy();

        int[] oldArr = new int[]{1, 2, 3};
        int[] newArr = new int[]{1, 4, 3, 5};

        CompareResult result = strategy.compare(oldArr, newArr, CompareOptions.DEFAULT);

        assertNotNull(result);
        assertFalse(result.getChanges().isEmpty());

        List<FieldChange> containerChanges = result.getContainerChanges();
        assertFalse(containerChanges.isEmpty());
        for (FieldChange fc : containerChanges) {
            assertTrue(fc.isContainerElementChange());
            assertEquals(FieldChange.ContainerType.ARRAY, fc.getElementEvent().getContainerType());
            assertNotNull(fc.getElementEvent().getIndex());
        }

        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();
        assertNotNull(grouped);
        // 期望至少有 ADD 或 MODIFY
        assertTrue(grouped.containsKey(FieldChange.ElementOperation.ADD)
                || grouped.containsKey(FieldChange.ElementOperation.MODIFY));
    }

    @Test
    @DisplayName("Array diff should produce MODIFY/ADD/REMOVE correctly")
    void array_diff_shouldProduceExpectedOperations() {
        ArrayCompareStrategy strategy = new ArrayCompareStrategy();

        String[] a1 = new String[]{"A", "B", "C"};
        String[] a2 = new String[]{"A", "X"};

        CompareResult result = strategy.compare(a1, a2, CompareOptions.DEFAULT);
        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = result.groupByContainerOperation();

        // 索引1: B->X => MODIFY；索引2: C->null => REMOVE
        assertEquals(1, grouped.getOrDefault(FieldChange.ElementOperation.MODIFY, List.of()).size());
        assertEquals(1, grouped.getOrDefault(FieldChange.ElementOperation.REMOVE, List.of()).size());
    }
}

