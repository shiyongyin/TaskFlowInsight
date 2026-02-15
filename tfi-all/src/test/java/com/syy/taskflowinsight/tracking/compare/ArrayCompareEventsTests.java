package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 ArrayCompareStrategy 输出 ARRAY 容器事件，并包含 index。
 */
class ArrayCompareEventsTests {

    @Test
    void array_events_have_array_container_type_and_index() {
        Integer[] a = new Integer[] {1, 2, 3};
        Integer[] b = new Integer[] {1, 9, 3, 4};

        ArrayCompareStrategy strat = new ArrayCompareStrategy();
        CompareResult r = strat.compare(a, b, CompareOptions.DEFAULT);

        assertTrue(r.hasChanges());
        boolean hasArrayEvent = r.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
                fc.getElementEvent().getContainerType() == FieldChange.ContainerType.ARRAY &&
                fc.getElementEvent().getIndex() != null);
        assertTrue(hasArrayEvent);
    }
}

