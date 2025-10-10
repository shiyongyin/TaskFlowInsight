package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LcsListNoMoveWhenDetectMovesDisabledTests {

    @Test
    void no_move_with_detect_moves_false() {
        List<String> a = Arrays.asList("A", "B", "C");
        List<String> b = Arrays.asList("A", "C", "B");

        LcsListStrategy strat = new LcsListStrategy();
        CompareResult r = strat.compare(a, b, CompareOptions.builder().detectMoves(false).build());
        assertNotNull(r);

        boolean hasMove = r.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
                fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MOVE);
        assertFalse(hasMove, "detectMoves=false 时不应产出 MOVE 事件");
    }
}

