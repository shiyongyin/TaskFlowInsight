package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.list.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 黄金 IT：Map / Set 值内部修改的 FieldChange.toTypedView() 验证。
 */
class MapSetTypedViewIT {

    @Test
    void map_entity_value_change_has_property_path_and_map_key_in_typed_view() {
        Map<String, Order> m1 = new LinkedHashMap<>();
        Map<String, Order> m2 = new LinkedHashMap<>();
        m1.put("K1", new Order("O1", 100));
        m2.put("K1", new Order("O1", 120)); // amount 变更

        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(), new EntityListStrategy()
        ));
        CompareService svc = new CompareService(exec);

        CompareResult r = svc.compare(m1, m2, CompareOptions.DEFAULT);
        assertTrue(r.hasChanges());

        boolean ok = r.getChanges().stream()
            .map(FieldChange::toTypedView)
            .filter(Objects::nonNull)
            .anyMatch(v -> {
                @SuppressWarnings("unchecked") Map<String,Object> details = (Map<String, Object>) v.get("details");
                return "entry_updated".equals(v.get("kind"))
                    && details != null
                    && "amount".equals(details.get("propertyPath"))
                    && Objects.equals("K1", details.get("mapKey"))
                    && details.get("entityKey") != null; // M3: Map<Entity> 提供 entityKey
            });
        assertTrue(ok, "Map<Entity> 值修改应在 TypedView 中包含 propertyPath 与 mapKey");
    }

    @Test
    void set_entity_value_change_has_property_path_in_typed_view() {
        Set<Order> s1 = new LinkedHashSet<>();
        Set<Order> s2 = new LinkedHashSet<>();
        s1.add(new Order("O1", 100));
        s2.add(new Order("O1", 130));

        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(), new EntityListStrategy()
        ));
        CompareService svc = new CompareService(exec);

        CompareResult r = svc.compare(s1, s2, CompareOptions.DEFAULT);
        assertTrue(r.hasChanges());

        boolean ok = r.getChanges().stream()
            .map(FieldChange::toTypedView)
            .filter(Objects::nonNull)
            .anyMatch(v -> {
                @SuppressWarnings("unchecked") Map<String,Object> details = (Map<String, Object>) v.get("details");
                return "entry_updated".equals(v.get("kind"))
                    && details != null
                    && details.get("propertyPath") != null; // 委托 EntityListStrategy 产生 LIST 事件，保留 propertyPath
            });
        assertTrue(ok, "Set<Entity> 值修改应在 TypedView 中包含 propertyPath");
    }

    @Entity
    private static class Order {
        @Key
        private final String id;
        private final int amount;
        Order(String id, int amount) { this.id = id; this.amount = amount; }
    }
}
