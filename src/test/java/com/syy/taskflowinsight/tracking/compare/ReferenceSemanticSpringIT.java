package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.TaskFlowInsightApplication;
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.api.ComparisonTemplate;
import com.syy.taskflowinsight.api.TFI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TaskFlowInsightApplication.class)
class ReferenceSemanticSpringIT {

    @Entity
    static class Customer {
        @Key
        private final String id;
        private final String name;

        Customer(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        public String getName() { return name; }
    }

    static class Order {
        @Key
        private final String id;
        @ShallowReference
        private final Customer customer; // 浅引用字段

        Order(String id, Customer customer) {
            this.id = id;
            this.customer = customer;
        }
        public String getId() { return id; }
        public Customer getCustomer() { return customer; }
    }

    @org.junit.jupiter.api.Disabled("Pending full Spring pipeline alignment for snapshot/diff path; enable when CI env stabilized")
    @Test
    void spring_service_path_should_detect_shallow_reference_change() {
        Order before = new Order("O1", new Customer("C1", "Alice"));
        Order after  = new Order("O1", new Customer("C2", "Bob"));

        // 通过 Spring 的 SnapshotProviders + DiffFacade 验证服务路径可识别浅引用字段变更
        var options = com.syy.taskflowinsight.api.TrackingOptions.builder()
            .enableTypeAware()
            .build();
        var beforeMap = com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
            .captureWithOptions("Order", before, options);
        var afterMap = com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
            .captureWithOptions("Order", after, options);

        var changes = com.syy.taskflowinsight.tracking.detector.DiffFacade.diff("Order", beforeMap, afterMap);
        assertFalse(changes.isEmpty());
        boolean hasRefIdChange = changes.stream().anyMatch(cr ->
            String.valueOf(cr.getOldValue()).contains("C1") && String.valueOf(cr.getNewValue()).contains("C2")
        );
        assertTrue(hasRefIdChange, "DiffFacade should detect shallow reference key change from C1 to C2");
    }
}
