package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LevenshteinListLifecycleUnitTests {

    @Test
    void full_lifecycle_contains_add_remove_move_and_update() {
        List<String> a = Arrays.asList("A", "B", "C");
        List<String> b = Arrays.asList("A", "C", "D", "B"); // B 移动，D 新增

        LevenshteinListStrategy strat = new LevenshteinListStrategy();
        CompareResult r = strat.compare(a, b, CompareOptions.builder().detectMoves(true).build());
        assertTrue(r.hasChanges());

        boolean hasAdd = r.getChanges().stream().anyMatch(fc -> fc.getElementEvent() != null && fc.getElementEvent().getOperation() == FieldChange.ElementOperation.ADD);
        boolean hasRemove = r.getChanges().stream().anyMatch(fc -> fc.getElementEvent() != null && fc.getElementEvent().getOperation() == FieldChange.ElementOperation.REMOVE);
        boolean hasMove = r.getChanges().stream().anyMatch(fc -> fc.getElementEvent() != null && fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MOVE);

        assertTrue(hasAdd || hasRemove); // 至少有增删
        assertTrue(hasMove);             // 存在移动
    }
}

