package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Map 值作为快照回退的引用键（COMPOSITE_MAP）用例
 * 根对象存在 @ShallowReference 字段，但该字段值为 null；
 * 通过 old/new 快照 Map 仍应检测引用切换。
 */
class ReferenceSemanticMapValueTests {

    @Entity
    static class Customer { @Key private final String id; Customer(String id){this.id=id;} public String getId(){return id;} }

    @Entity
    static class Order {
        @Key private final String id;
        @ShallowReference private final Customer customer; // 测试时置为 null，触发 Map 回退路径
        Order(String id, Customer c){this.id=id; this.customer=c;}
        public String getId(){return id;}
        public Customer getCustomer(){return customer;}
    }

    @Test
    void map_snapshot_values_should_detect_reference_change() {
        Order before = new Order("O1", null);
        Order after  = new Order("O1", null);

        Map<String, Object> oldMap = new LinkedHashMap<>();
        oldMap.put("id", "C1");
        Map<String, Object> newMap = new LinkedHashMap<>();
        newMap.put("id", "C2");

        var detail = com.syy.taskflowinsight.tracking.detector.DiffFacade
            .detectReferenceChange(before, after, "Order.customer", oldMap, newMap);

        assertNotNull(detail);
        assertTrue(String.valueOf(detail.getOldEntityKey()).contains("C1"));
        assertTrue(String.valueOf(detail.getNewEntityKey()).contains("C2"));
        assertFalse(detail.isNullReferenceChange());
    }
}

