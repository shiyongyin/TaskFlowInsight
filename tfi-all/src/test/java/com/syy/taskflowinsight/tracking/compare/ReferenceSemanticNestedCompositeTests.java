package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 复杂快照结构（Map→List→Entity等）的回退检测用例集合。
 * 说明：这些用例直接调用 DiffFacade.detectReferenceChange，模拟快照回退路径。
 */
class ReferenceSemanticNestedCompositeTests {

    @Entity
    static class Customer { @Key private final String id; Customer(String id){this.id=id;} public String getId(){return id;} }

    @Entity
    static class Order { @Key private final String id; @ShallowReference private final Customer customer; Order(String id, Customer c){this.id=id; this.customer=c;} public String getId(){return id;} public Customer getCustomer(){return customer;} }

    @Test
    void composite_map_flat_should_detect() {
        Order before = new Order("O1", null);
        Order after  = new Order("O1", null);

        Map<String,Object> oldMap = new LinkedHashMap<>();
        oldMap.put("id", "C1");
        oldMap.put("region", "US");
        Map<String,Object> newMap = new LinkedHashMap<>();
        newMap.put("id", "C2");
        newMap.put("region", "US");

        var detail = com.syy.taskflowinsight.tracking.detector.DiffFacade
            .detectReferenceChange(before, after, "Order.customer", oldMap, newMap);

        assertNotNull(detail);
        assertTrue(String.valueOf(detail.getOldEntityKey()).contains("id=C1"));
        assertTrue(String.valueOf(detail.getOldEntityKey()).contains("region=US"));
        assertTrue(String.valueOf(detail.getNewEntityKey()).contains("id=C2"));
        assertFalse(detail.isNullReferenceChange());
    }

    @Test
    void composite_map_with_list_values_should_detect() {
        Order before = new Order("O2", null);
        Order after  = new Order("O2", null);

        Map<String,Object> oldMap = new LinkedHashMap<>();
        oldMap.put("ids", List.of(1,2,3));
        oldMap.put("region", "EU");
        Map<String,Object> newMap = new LinkedHashMap<>();
        newMap.put("ids", List.of(4,5,6));
        newMap.put("region", "EU");

        var detail = com.syy.taskflowinsight.tracking.detector.DiffFacade
            .detectReferenceChange(before, after, "Order.customer", oldMap, newMap);

        assertNotNull(detail);
        assertTrue(String.valueOf(detail.getOldEntityKey()).contains("ids=[1, 2, 3]"));
        assertTrue(String.valueOf(detail.getNewEntityKey()).contains("ids=[4, 5, 6]"));
        assertTrue(String.valueOf(detail.getOldEntityKey()).contains("region=EU"));
        assertFalse(detail.isNullReferenceChange());
    }

    @Test
    void composite_map_with_nested_map_should_detect() {
        Order before = new Order("O3", null);
        Order after  = new Order("O3", null);

        Map<String,Object> oldMeta = new LinkedHashMap<>();
        oldMeta.put("level", "gold");
        Map<String,Object> newMeta = new LinkedHashMap<>();
        newMeta.put("level", "platinum");

        Map<String,Object> oldMap = new LinkedHashMap<>();
        oldMap.put("id", "C9");
        oldMap.put("meta", oldMeta);

        Map<String,Object> newMap = new LinkedHashMap<>();
        newMap.put("id", "C9");
        newMap.put("meta", newMeta);

        var detail = com.syy.taskflowinsight.tracking.detector.DiffFacade
            .detectReferenceChange(before, after, "Order.customer", oldMap, newMap);

        assertNotNull(detail);
        // 嵌套 Map 的字符串来自 LinkedHashMap 的稳定 toString，包含 level=value
        assertTrue(String.valueOf(detail.getOldEntityKey()).contains("level=gold"));
        assertTrue(String.valueOf(detail.getNewEntityKey()).contains("level=platinum"));
        assertFalse(detail.isNullReferenceChange());
    }
}

