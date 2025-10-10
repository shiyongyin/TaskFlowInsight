package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LcsListLifecycleUnitTests {

    @Test
    void lcs_move_and_add_remove_present_when_detect_moves_enabled() {
        List<String> a = Arrays.asList("A", "B", "C");
        List<String> b = Arrays.asList("A", "C", "D", "B");

        LcsListStrategy strat = new LcsListStrategy();
        CompareResult r = strat.compare(a, b, CompareOptions.builder().detectMoves(true).build());
        assertTrue(r.hasChanges());

        boolean hasMove = r.getChanges().stream().anyMatch(fc -> fc.getElementEvent() != null && fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MOVE);
        assertTrue(hasMove);
    }
}

