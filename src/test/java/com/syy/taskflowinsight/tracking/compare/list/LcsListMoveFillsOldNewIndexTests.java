package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 LcsListStrategy 在 detectMoves=true 下输出 MOVE 事件并填充 oldIndex/newIndex。
 */
class LcsListMoveFillsOldNewIndexTests {

    private CompareService compareService;
    private ListCompareExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        List<ListCompareStrategy> strategies = Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(),
            new LevenshteinListStrategy(), new EntityListStrategy()
        );
        executor = new ListCompareExecutor(strategies);

        // 通过反射开启 LCS 自动路由（prefer when detectMoves=true 且 <300）
        Field f = ListCompareExecutor.class.getDeclaredField("autoRouteProps");
        f.setAccessible(true);
        CompareRoutingProperties props = new CompareRoutingProperties();
        props.getLcs().setEnabled(true);
        props.getLcs().setPreferLcsWhenDetectMoves(true);
        f.set(executor, props);

        compareService = new CompareService(
            executor,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    @Test
    void lcs_detects_move_and_fills_old_new_index() {
        List<String> a = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
        List<String> b = new ArrayList<>(Arrays.asList("A", "C", "B", "D")); // B 与 C 交换

        CompareResult r = compareService.compare(a, b, CompareOptions.builder().detectMoves(true).build());

        boolean hasMove = r.getChanges().stream().anyMatch(c ->
            c.getElementEvent() != null &&
            c.getElementEvent().getOperation() == com.syy.taskflowinsight.tracking.compare.FieldChange.ElementOperation.MOVE &&
            c.getElementEvent().getOldIndex() != null &&
            c.getElementEvent().getNewIndex() != null
        );
        assertTrue(hasMove, "应输出带 oldIndex/newIndex 的 MOVE 事件");
    }
}
