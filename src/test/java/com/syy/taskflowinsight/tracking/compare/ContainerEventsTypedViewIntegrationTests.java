package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.list.*;
import com.syy.taskflowinsight.tracking.query.ChangeAdapters;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端验证：CompareService → FieldChange → TypedView(Map) 输出。
 */
class ContainerEventsTypedViewIntegrationTests {

    @Test
    void composite_object_changes_are_exported_to_typed_view() {
        Composite before = new Composite();
        before.items = new ArrayList<>(Arrays.asList(1, 2, 3));
        before.orders = new LinkedHashSet<>(Collections.singletonList(new Order("O1", 100)));
        before.orderMap = new LinkedHashMap<>();
        before.orderMap.put("K1", new Order("M1", 10));

        Composite after = new Composite();
        after.items = new ArrayList<>(Arrays.asList(1, 3, 4, 2)); // 2 移动到尾部，新增 4
        after.orders = new LinkedHashSet<>(Collections.singletonList(new Order("O1", 120))); // amount 修改
        after.orderMap = new LinkedHashMap<>();
        after.orderMap.put("K1", new Order("M1", 15)); // Map 值修改

        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(), new EntityListStrategy()
        ));
        CompareService svc = new CompareService(exec);

        CompareResult result = svc.compare(before, after, CompareOptions.builder().detectMoves(true).build());
        assertTrue(result.hasChanges());

        List<Map<String, Object>> typed = ChangeAdapters.toTypedView("Composite", result.getChanges());
        assertNotNull(typed);
        assertFalse(typed.isEmpty());

        // ChangeAdapters 的 typedView 为通用视图：验证基本键存在
        boolean hasKind = typed.stream().anyMatch(v -> v.get("kind") != null);
        boolean hasPath = typed.stream().anyMatch(v -> v.get("path") != null);
        boolean hasDetails = typed.stream().anyMatch(v -> v.get("details") instanceof Map);
        assertTrue(hasKind && hasPath && hasDetails);
    }

    private static class Composite {
        List<Integer> items;
        Set<Order> orders;
        Map<String, Order> orderMap;
    }

    @Entity
    private static class Order {
        @Key
        private final String id;
        private final int amount;
        Order(String id, int amount) { this.id = id; this.amount = amount; }
    }
}
