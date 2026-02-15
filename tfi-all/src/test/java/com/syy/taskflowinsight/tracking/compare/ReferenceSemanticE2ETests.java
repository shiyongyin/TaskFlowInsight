package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for reference change detection using CompareService.compare().
 * Focuses on @ShallowReference semantics and null transitions.
 */
class ReferenceSemanticE2ETests {

    @Entity
    static class Customer {
        @Key
        private final String id;
        private final String name;

        Customer(String id, String name) {
            this.id = id;
            this.name = name;
        }
        public String getId() { return id; }
        public String getName() { return name; }
    }

    @Entity
    static class Order {
        @Key
        private final String id;
        @ShallowReference
        private final Customer customer;

        Order(String id, Customer customer) {
            this.id = id;
            this.customer = customer;
        }
        public String getId() { return id; }
        public Customer getCustomer() { return customer; }
    }

    @Test
    void entity_to_entity_switch_marks_reference_change() {
        Order before = new Order("O1", new Customer("C1", "Alice"));
        Order after  = new Order("O1", new Customer("C2", "Bob"));

        CompareService service = CompareService.createDefault(CompareOptions.DEEP);
        CompareResult result = service.compare(before, after, CompareOptions.DEEP);

        assertFalse(result.isIdentical());
        assertEquals(1, result.getReferenceChanges().size());
        FieldChange change = result.getReferenceChanges().get(0);
        assertTrue(change.isReferenceChange());
        assertNotNull(change.getReferenceDetail());
        // key format should reflect a reference identity; allow either compact or class-wrapped
        String oldKey = change.getReferenceDetail().getOldEntityKey();
        String newKey = change.getReferenceDetail().getNewEntityKey();
        assertNotNull(oldKey);
        assertNotNull(newKey);
        assertTrue(oldKey.contains("C1"));
        assertTrue(newKey.contains("C2"));
        assertFalse(change.getReferenceDetail().isNullReferenceChange());
    }

    @Test
    void null_to_entity_marks_null_reference_change() {
        Order before = new Order("O1", null);
        Order after  = new Order("O1", new Customer("C9", "Zoe"));

        CompareService service = CompareService.createDefault(CompareOptions.DEEP);
        CompareResult result = service.compare(before, after, CompareOptions.DEEP);

        assertFalse(result.isIdentical());
        assertEquals(1, result.getReferenceChanges().size());
        FieldChange change = result.getReferenceChanges().get(0);
        assertNull(change.getReferenceDetail().getOldEntityKey());
        assertNotNull(change.getReferenceDetail().getNewEntityKey());
        assertTrue(change.getReferenceDetail().isNullReferenceChange());
    }

    @Test
    void entity_to_null_marks_null_reference_change() {
        Order before = new Order("O1", new Customer("C3", "Amy"));
        Order after  = new Order("O1", null);

        CompareService service = CompareService.createDefault(CompareOptions.DEEP);
        CompareResult result = service.compare(before, after, CompareOptions.DEEP);

        assertFalse(result.isIdentical());
        assertEquals(1, result.getReferenceChanges().size());
        FieldChange change = result.getReferenceChanges().get(0);
        assertNotNull(change.getReferenceDetail().getOldEntityKey());
        assertNull(change.getReferenceDetail().getNewEntityKey());
        assertTrue(change.getReferenceDetail().isNullReferenceChange());
    }

    @Test
    void same_key_different_instance_should_not_report_reference_change() {
        // Same key, different object instance; @ShallowReference should ignore internal attr changes
        Order before = new Order("O1", new Customer("C7", "X"));
        Order after  = new Order("O1", new Customer("C7", "Y"));

        CompareService service = CompareService.createDefault(CompareOptions.DEEP);
        CompareResult result = service.compare(before, after, CompareOptions.DEEP);

        // shallow snapshot for customer will be identical → no field change for 'customer'
        assertTrue(result.getReferenceChanges().isEmpty());
    }
}

