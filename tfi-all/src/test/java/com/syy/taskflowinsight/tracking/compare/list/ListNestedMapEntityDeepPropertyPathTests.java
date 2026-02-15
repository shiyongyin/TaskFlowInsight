package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 嵌套容器：List<Map<String, Entity>> 值对象内部修改时，应出现带 propertyPath 的变更。
 */
class ListNestedMapEntityDeepPropertyPathTests {

    @Test
    void list_of_map_entity_value_change_has_property_path() {
        Map<String, Order> m1 = new LinkedHashMap<>();
        Map<String, Order> m2 = new LinkedHashMap<>();
        m1.put("K1", new Order("O1", 100));
        m2.put("K1", new Order("O1", 120));
        List<Map<String, Order>> a = Collections.singletonList(m1);
        List<Map<String, Order>> b = Collections.singletonList(m2);

        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(), new EntityListStrategy()
        ));
        CompareService svc = new CompareService(exec);

        CompareResult r = svc.compare(a, b, CompareOptions.builder().enableDeepCompare(true).build());
        assertTrue(r.hasChanges());

        boolean hasProp = r.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
                fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MODIFY &&
                fc.getElementEvent().getPropertyPath() != null &&
                fc.getElementEvent().getPropertyPath().contains("amount")
        );
        assertTrue(hasProp, "应存在带 propertyPath(包含 amount) 的容器事件");
    }

    @Entity
    private static class Order {
        @Key
        private final String id;
        private final int amount;
        Order(String id, int amount) { this.id = id; this.amount = amount; }
    }
}

