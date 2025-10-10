package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Map<id, Entity> 值对象内部字段变更时，elementEvent.propertyPath 补齐。
 */
class MapPropertyPathForEntityValuesTests {

    private CompareService compareService;

    @BeforeEach
    void setUp() {
        // 使用默认执行器（Map 路径不依赖列表策略）
        compareService = new CompareService(
            new com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor(
                Arrays.asList(
                    new com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy(),
                    new com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy(),
                    new com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy(),
                    new com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy(),
                    new com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy()
                )
            ),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    @Test
    void entity_value_change_has_property_path_in_event_details() {
        Map<String, Order> m1 = new LinkedHashMap<>();
        Map<String, Order> m2 = new LinkedHashMap<>();
        m1.put("O1", new Order("O1", 100));
        m2.put("O1", new Order("O1", 120)); // amount 变更

        CompareResult r = compareService.compare(m1, m2, CompareOptions.DEFAULT);
        assertTrue(r.hasChanges());

        // 应存在一个 UPDATE，其 elementEvent.containerType=MAP，mapKey="O1"，propertyPath="amount"
        boolean ok = r.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
            fc.getElementEvent().getContainerType() == FieldChange.ContainerType.MAP &&
            Objects.equals(fc.getElementEvent().getMapKey(), "O1") &&
            Objects.equals(fc.getElementEvent().getPropertyPath(), "amount")
        );
        assertTrue(ok, "Map<Entity> 值对象属性变更应补齐 propertyPath");
    }

    @Entity
    private static class Order {
        @Key
        private final String id;
        private final int amount;
        Order(String id, int amount) { this.id = id; this.amount = amount; }
    }
}

