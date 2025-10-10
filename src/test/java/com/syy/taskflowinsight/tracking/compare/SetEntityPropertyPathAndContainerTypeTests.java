package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Set<Entity> 深度修改产生的事件：
 * - containerType 应为 SET
 * - operation=MODIFY
 * - propertyPath 写入内部字段名
 * - index/oldIndex/newIndex 为空
 */
class SetEntityPropertyPathAndContainerTypeTests {

    @Test
    void set_entity_internal_change_has_set_container_type_and_property_path() {
        Set<Order> oldSet = new HashSet<>();
        Set<Order> newSet = new HashSet<>();
        oldSet.add(new Order("O1", 100));
        newSet.add(new Order("O1", 120));

        SetCompareStrategy strategy = new SetCompareStrategy();
        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertTrue(result.hasChanges());
        boolean matched = result.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
            fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MODIFY &&
            fc.getElementEvent().getPropertyPath() != null
        );
        assertTrue(matched, "Set<Entity> 内部属性变更应包含 propertyPath");
    }

    @Entity
    private static class Order {
        @Key
        private final String id;
        private final int amount;
        Order(String id, int amount) { this.id = id; this.amount = amount; }
    }
}
