package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceChangeEndToEndTests {

    @Entity
    static class Customer {
        @Key
        String id;
        String name;
        Customer(String id, String name) { this.id = id; this.name = name; }
    }

    static class Order {
        @Key
        String id;
        @ShallowReference
        Customer customer;
        Order(String id, Customer customer) { this.id = id; this.customer = customer; }
    }

    @Test
    void deepCompare_shouldDetectShallowReferenceChanges() {
        Order before = new Order("O1", new Customer("C1", "Alice"));
        Order after  = new Order("O1", new Customer("C2", "Bob"));

        CompareService svc = CompareService.createDefault(CompareOptions.DEFAULT);
        CompareResult result = svc.compare(before, after, CompareOptions.DEFAULT);

        assertNotNull(result);
        assertFalse(result.getChanges().isEmpty(), "Should have changes");

        // 应检测到一次引用变更
        java.util.List<FieldChange> refChanges = result.getReferenceChanges();
        assertEquals(1, refChanges.size(), "Should detect one shallow reference change");

        FieldChange rc = refChanges.get(0);
        assertTrue(rc.isReferenceChange());
        assertNotNull(rc.getReferenceDetail());
        // 旧/新键应包含实体类名与键值
        assertTrue(rc.getOldEntityKey() == null || rc.getOldEntityKey().contains("Customer["));
        assertTrue(rc.getNewEntityKey() == null || rc.getNewEntityKey().contains("Customer["));
    }
}

