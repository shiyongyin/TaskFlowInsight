package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Levenshtein 在存在重复元素时，不为重复元素产出 MOVE（仅唯一元素可判定为移动）。
 */
class LevenshteinDuplicateMoveBehaviorTests {

    @Test
    void duplicates_do_not_yield_move_events() {
        List<String> a = Arrays.asList("X", "X", "Y", "Z");
        List<String> b = Arrays.asList("X", "Y", "X", "Z"); // Y 从索引2 移到 1；X 为重复元素

        LevenshteinListStrategy strat = new LevenshteinListStrategy();
        CompareResult r = strat.compare(a, b, CompareOptions.builder().detectMoves(true).build());

        long moveCount = r.getChanges().stream()
            .filter(fc -> fc.getElementEvent() != null &&
                    fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MOVE)
            .count();

        assertEquals(1, moveCount, "仅唯一元素 Y 应被识别为 MOVE，重复元素 X 不应产出MOVE");
    }
}

