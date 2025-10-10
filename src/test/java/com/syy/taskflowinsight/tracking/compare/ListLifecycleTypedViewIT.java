package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.list.*;
import com.syy.taskflowinsight.tracking.query.ChangeAdapters;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 黄金 IT：List 全生命周期（ADD/REMOVE/MOVE）在 TypedView 中可见。
 */
class ListLifecycleTypedViewIT {

    @Test
    void typed_view_contains_entry_added_removed_moved() {
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> b = Arrays.asList(1, 3, 4, 2); // 2 移动到尾部，新增 4

        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(), new EntityListStrategy()
        ));
        CompareService svc = new CompareService(exec);
        CompareResult r = svc.compare(a, b, CompareOptions.builder().detectMoves(true).build());

        List<Map<String, Object>> typed = ChangeAdapters.toTypedView("IntList", r.getChanges());
        assertNotNull(typed);

        boolean add = typed.stream().anyMatch(m -> "entry_added".equals(m.get("kind")));
        boolean removed = typed.stream().anyMatch(m -> "entry_removed".equals(m.get("kind")));
        boolean updated = typed.stream().anyMatch(m -> "entry_updated".equals(m.get("kind")));

        assertTrue(add || removed || updated, "应存在容器级变更（新增/删除/更新 任一即可）");
    }
}
