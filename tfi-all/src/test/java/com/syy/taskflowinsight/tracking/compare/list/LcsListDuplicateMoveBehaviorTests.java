package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 LCS 在存在重复元素时，仅唯一元素可判定为移动。
 */
class LcsListDuplicateMoveBehaviorTests {

    @Test
    void duplicates_do_not_yield_multiple_moves() {
        List<String> a = Arrays.asList("X", "X", "Y", "Z");
        List<String> b = Arrays.asList("X", "Y", "X", "Z");

        LcsListStrategy strat = new LcsListStrategy();
        CompareResult r = strat.compare(a, b, CompareOptions.builder().detectMoves(true).build());

        long moveCount = r.getChanges().stream()
            .filter(fc -> fc.getElementEvent() != null &&
                fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MOVE)
            .count();

        assertTrue(moveCount <= 1, "重复元素不应导致多个 MOVE 事件");
    }
}
