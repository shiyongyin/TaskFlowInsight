package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReferenceChange 路径场景补充测试：List/Map/Array 下的浅引用字段。
 */
class ReferenceChangeCollectionPathTests {

    @Entity
    static class Customer {
        @Key
        String id;
        String name;
        Customer(String id, String name) { this.id = id; this.name = name; }
    }

    static class Item {
        @ShallowReference
        Customer buyer;
        Item(Customer buyer) { this.buyer = buyer; }
    }

    static class Order {
        @ShallowReference
        Customer customer; // 根字段的浅引用
        List<Item> items = new ArrayList<>();
        Map<String, Customer> contactByRegion = new HashMap<>();
        Customer[] approvers;
    }

    @Test
    @DisplayName("ShallowReference: root field & list element & map value & array element should be detected")
    void shallowReference_in_collections_should_be_detected() {
        // Given old
        Order oldOrder = new Order();
        oldOrder.customer = null;
        oldOrder.items.add(new Item(new Customer("C1", "Alice")));
        oldOrder.items.add(new Item(new Customer("C1", "Alice")));
        oldOrder.contactByRegion.put("APAC", new Customer("C9", "Zoe"));
        oldOrder.approvers = new Customer[] { new Customer("A1", "Ann") };

        // Given new
        Order newOrder = new Order();
        newOrder.customer = new Customer("C2", "Bob"); // null -> entity
        newOrder.items.add(new Item(new Customer("C1", "Alice")));
        newOrder.items.add(new Item(new Customer("C2", "Bob"))); // items[1].buyer switched
        newOrder.contactByRegion.put("APAC", null); // entity -> null
        newOrder.approvers = new Customer[] { new Customer("A2", "Amy") }; // switch

        // When deep compare with programmatic detector enabled
        CompareService svc = CompareService.createDefault(CompareOptions.typeAware());
        CompareResult result = svc.compare(oldOrder, newOrder, CompareOptions.typeAware());

        // Then
        List<FieldChange> refs = result.getReferenceChanges();
        assertFalse(refs.isEmpty(), "reference changes should be detected");

        // Root field
        assertTrue(refs.stream().anyMatch(rc ->
            ("customer".equals(rc.getFieldName()) || (rc.getFieldPath() != null && rc.getFieldPath().endsWith("customer")))
                && rc.isReferenceChange()
                && rc.getReferenceDetail() != null
                && rc.getReferenceDetail().isNullReferenceChange()
        ), "root ShallowReference should be detected (null->entity)");

        // List element
        assertTrue(refs.stream().anyMatch(rc ->
            rc.getFieldPath() != null && rc.getFieldPath().contains("items[1].buyer")
                && rc.isReferenceChange()
                && rc.getReferenceDetail() != null
        ), "list element ShallowReference should be detected");

        // Map value
        assertTrue(refs.stream().anyMatch(rc ->
            rc.getFieldPath() != null && rc.getFieldPath().contains("contactByRegion[APAC]")
                && rc.isReferenceChange()
                && rc.getReferenceDetail() != null
                && rc.getReferenceDetail().isNullReferenceChange()
        ), "map value ShallowReference should be detected (entity->null)");

        // Array element
        assertTrue(refs.stream().anyMatch(rc ->
            rc.getFieldPath() != null && rc.getFieldPath().contains("approvers[0]")
                && rc.isReferenceChange()
                && rc.getReferenceDetail() != null
        ), "array element ShallowReference should be detected");
    }
}

