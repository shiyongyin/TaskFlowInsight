package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 AsSetListStrategy 仅输出 ADD/REMOVE，且包含 index。
 */
class AsSetListAddRemoveEventsTests {

    @Test
    void as_set_add_and_remove_have_index() {
        List<String> a = Arrays.asList("A", "B", "C");
        List<String> b = Arrays.asList("B", "C", "D");

        AsSetListStrategy strat = new AsSetListStrategy();
        CompareResult r = strat.compare(a, b, CompareOptions.DEFAULT);

        assertTrue(r.hasChanges());
        boolean hasRemoveWithIndex = r.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
                fc.getElementEvent().getOperation() == FieldChange.ElementOperation.REMOVE &&
                fc.getElementEvent().getIndex() != null);
        boolean hasAddWithIndex = r.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
                fc.getElementEvent().getOperation() == FieldChange.ElementOperation.ADD &&
                fc.getElementEvent().getIndex() != null);

        assertTrue(hasRemoveWithIndex);
        assertTrue(hasAddWithIndex);
    }
}

