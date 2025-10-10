package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reference Semantic 集成测试（黄金测试）
 * 测试真实业务场景下的引用变更检测和API使用
 */
class ReferenceSemanticIntegrationTests {

    @Entity
    static class Customer {
        @Key
        private final String customerId;
        private final String name;
        private final String email;

        Customer(String customerId, String name, String email) {
            this.customerId = customerId;
            this.name = name;
            this.email = email;
        }

        public String getCustomerId() { return customerId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
    }

    @Entity
    static class Order {
        @Key
        private final String orderId;
        private final String productName;
        @ShallowReference
        private final Customer customer;

        Order(String orderId, String productName, Customer customer) {
            this.orderId = orderId;
            this.productName = productName;
            this.customer = customer;
        }

        public String getOrderId() { return orderId; }
        public String getProductName() { return productName; }
        public Customer getCustomer() { return customer; }
    }

    @Test
    void golden_test_order_customer_reference_change() {
        // Given: 订单换客户（真实业务场景）
        Order before = new Order("O-1", "Laptop", new Customer("C1", "Alice", "a@x.com"));
        Order after  = new Order("O-1", "Laptop", new Customer("C2", "Bob",   "b@x.com"));

        // When: 调用 compare() 主路径（DEEP 以触发 @ShallowReference 快照与检测）
        CompareService service = CompareService.createDefault(CompareOptions.DEEP);
        CompareResult result = service.compare(before, after, CompareOptions.DEEP);

        // Then: 应检测到引用变更
        assertFalse(result.getChanges().isEmpty(), "Should detect customer reference change");
        List<FieldChange> refChanges = result.getReferenceChanges();
        assertEquals(1, refChanges.size());

        FieldChange refChange = refChanges.get(0);
        assertTrue(refChange.isReferenceChange());
        assertNotNull(refChange.getReferenceDetail());

        // 验证引用键包含 C1 -> C2 语义（允许前缀如 Customer[...]
        String oldKey = refChange.getReferenceDetail().getOldEntityKey();
        String newKey = refChange.getReferenceDetail().getNewEntityKey();
        assertNotNull(oldKey);
        assertNotNull(newKey);
        assertTrue(oldKey.contains("C1"));
        assertTrue(newKey.contains("C2"));
        assertFalse(refChange.getReferenceDetail().isNullReferenceChange());

        // 不应有 customer.name / customer.email 的深度字段变更
        boolean hasDeepChanges = result.getChanges().stream()
            .anyMatch(c -> c.getFieldPath() != null &&
                          (c.getFieldPath().contains("customer.name") ||
                           c.getFieldPath().contains("customer.email")));
        assertFalse(hasDeepChanges, "Should not produce deep comparison changes for @ShallowReference field");
    }

    @Test
    void golden_test_null_reference_lifecycle() {
        // 阶段1: null → Entity (关联建立)
        Order s1_before = new Order("O-2", "Item", null);
        Order s1_after  = new Order("O-2", "Item", new Customer("C1", "Alice", "a@x.com"));
        CompareResult r1 = CompareService.createDefault(CompareOptions.DEEP).compare(s1_before, s1_after, CompareOptions.DEEP);
        assertEquals(1, r1.getReferenceChanges().size());
        FieldChange c1 = r1.getReferenceChanges().get(0);
        assertTrue(c1.getReferenceDetail().isNullReferenceChange());
        assertNull(c1.getOldEntityKey());
        assertTrue(c1.getNewEntityKey().contains("C1"));

        // 阶段2: Entity → Entity (切换)
        Order s2_before = s1_after;
        Order s2_after  = new Order("O-2", "Item", new Customer("C2", "Bob", "b@x.com"));
        CompareResult r2 = CompareService.createDefault(CompareOptions.DEEP).compare(s2_before, s2_after, CompareOptions.DEEP);
        assertEquals(1, r2.getReferenceChanges().size());
        FieldChange c2 = r2.getReferenceChanges().get(0);
        assertFalse(c2.getReferenceDetail().isNullReferenceChange());
        assertTrue(c2.getOldEntityKey().contains("C1"));
        assertTrue(c2.getNewEntityKey().contains("C2"));

        // 阶段3: Entity → null (解除)
        Order s3_before = s2_after;
        Order s3_after  = new Order("O-2", "Item", null);
        CompareResult r3 = CompareService.createDefault(CompareOptions.DEEP).compare(s3_before, s3_after, CompareOptions.DEEP);
        assertEquals(1, r3.getReferenceChanges().size());
        FieldChange c3 = r3.getReferenceChanges().get(0);
        assertTrue(c3.getReferenceDetail().isNullReferenceChange());
        assertTrue(c3.getOldEntityKey().contains("C2"));
        assertNull(c3.getNewEntityKey());
    }

    @Test
    void shallow_reference_should_skip_deep_traversal() {
        // Given: 仅发生引用切换
        Order before = new Order("O-3", "Item", new Customer("C1", "X", "x@x.com"));
        Order after  = new Order("O-3", "Item", new Customer("C2", "Y", "y@x.com"));

        CompareResult result = CompareService.createDefault(CompareOptions.DEEP).compare(before, after, CompareOptions.DEEP);

        // Then: 不应产生 customer.name / customer.email 等深度字段的变更
        long deepFieldChanges = result.getChanges().stream()
            .filter(c -> c.getFieldPath() != null)
            .filter(c -> c.getFieldPath().contains("customer."))
            .count();
        assertEquals(0, deepFieldChanges,
            "ShallowReference should prevent deep field comparison (customer.name, customer.email, etc.)");

        // 仅应有 customer 本身的引用变更
        assertEquals(1, result.getReferenceChanges().size());
    }
}
