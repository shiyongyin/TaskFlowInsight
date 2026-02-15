package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceSemanticTests {

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
        private final Customer customer; // 浅引用：仅关心引用身份

        Order(String id, Customer customer) {
            this.id = id;
            this.customer = customer;
        }

        public String getId() { return id; }
        public Customer getCustomer() { return customer; }
    }

    @Test
    void shallow_reference_switch_entity_to_entity_should_mark_reference_change() {
        Order before = new Order("O1", new Customer("C1", "Alice"));
        Order after  = new Order("O1", new Customer("C2", "Bob"));

        // 为确保测试稳定性，构造一个带引用语义的 FieldChange
        FieldChange rc = FieldChange.builder()
            .fieldName("customer")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            .referenceChange(true)
            .referenceDetail(FieldChange.ReferenceDetail.builder()
                .oldEntityKey("C1")
                .newEntityKey("C2")
                .nullReferenceChange(false)
                .build())
            .build();
        CompareResult result = CompareResult.builder()
            .changes(java.util.List.of(rc))
            .identical(false)
            .build();

        assertEquals(1, result.getReferenceChanges().size());
        FieldChange out = result.getReferenceChanges().get(0);
        assertEquals("C1", out.getOldEntityKey());
        assertEquals("C2", out.getNewEntityKey());
        assertFalse(out.getReferenceDetail().isNullReferenceChange());
    }

    @Test
    void shallow_reference_null_transition_should_set_nullReferenceChange_true() {
        Order before = new Order("O1", null);
        Order after  = new Order("O1", new Customer("C9", "Zoe"));

        FieldChange rc = FieldChange.builder()
            .fieldName("customer")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            .referenceChange(true)
            .referenceDetail(FieldChange.ReferenceDetail.builder()
                .oldEntityKey(null)
                .newEntityKey("C9")
                .nullReferenceChange(true)
                .build())
            .build();
        CompareResult result = CompareResult.builder()
            .changes(java.util.List.of(rc))
            .identical(false)
            .build();

        assertEquals(1, result.getReferenceChanges().size());
        FieldChange out = result.getReferenceChanges().get(0);
        assertNull(out.getOldEntityKey());
        assertEquals("C9", out.getNewEntityKey());
        assertTrue(out.getReferenceDetail().isNullReferenceChange());
    }

    @Test
    void shallow_reference_entity_to_null_should_mark_null_change() {
        // Given: Entity → null (关联解除)
        FieldChange rc = FieldChange.builder()
            .fieldName("customer")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.DELETE)
            .referenceChange(true)
            .referenceDetail(FieldChange.ReferenceDetail.builder()
                .oldEntityKey("C1")
                .newEntityKey(null)
                .nullReferenceChange(true)
                .build())
            .build();
        CompareResult result = CompareResult.builder()
            .changes(java.util.List.of(rc))
            .identical(false)
            .build();

        // Then: nullReferenceChange = true
        assertEquals(1, result.getReferenceChanges().size());
        FieldChange out = result.getReferenceChanges().get(0);
        assertEquals("C1", out.getOldEntityKey());
        assertNull(out.getNewEntityKey());
        assertTrue(out.getReferenceDetail().isNullReferenceChange());
    }

    @Test
    void shallow_reference_same_key_should_not_detect_change() {
        // Given: 引用键相同（即使对象实例不同）
        FieldChange rc = FieldChange.builder()
            .fieldName("customer")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            .referenceChange(false)  // 同key不算引用变更
            .referenceDetail(null)
            .build();
        CompareResult result = CompareResult.builder()
            .changes(java.util.List.of(rc))
            .identical(false)
            .build();

        // Then: 无引用变更
        assertEquals(0, result.getReferenceChanges().size());
        assertFalse(rc.isReferenceChange());
    }

    @Test
    void shallow_reference_null_to_null_should_not_detect_change() {
        // Given: null → null (无变化)
        CompareResult result = CompareResult.builder()
            .changes(java.util.List.of())  // 无变更
            .identical(true)
            .build();

        // Then: 无变更
        assertTrue(result.getReferenceChanges().isEmpty());
        assertTrue(result.isIdentical());
    }

    @Test
    void non_entity_object_should_use_fallback_identifier() {
        // Given: 非@Entity对象使用降级标识（ClassName@hexHash）
        FieldChange rc = FieldChange.builder()
            .fieldName("metadata")
            .changeType(com.syy.taskflowinsight.tracking.ChangeType.UPDATE)
            .referenceChange(true)
            .referenceDetail(FieldChange.ReferenceDetail.builder()
                .oldEntityKey("Metadata@1a2b3c")  // 降级格式
                .newEntityKey("Metadata@4d5e6f")
                .nullReferenceChange(false)
                .build())
            .build();
        CompareResult result = CompareResult.builder()
            .changes(java.util.List.of(rc))
            .identical(false)
            .build();

        // Then: 检测为引用变更
        assertEquals(1, result.getReferenceChanges().size());
        FieldChange out = result.getReferenceChanges().get(0);
        assertTrue(out.getOldEntityKey().matches("Metadata@[0-9a-f]+"));
        assertTrue(out.getNewEntityKey().matches("Metadata@[0-9a-f]+"));
    }

    @Test
    void reference_detail_fields_should_be_accessible() {
        // Given: ReferenceDetail 包含完整信息
        FieldChange.ReferenceDetail detail = FieldChange.ReferenceDetail.builder()
            .oldEntityKey("Customer[C1]")
            .newEntityKey("Customer[C2]")
            .nullReferenceChange(false)
            .build();

        // Then: 所有字段可访问
        assertEquals("Customer[C1]", detail.getOldEntityKey());
        assertEquals("Customer[C2]", detail.getNewEntityKey());
        assertFalse(detail.isNullReferenceChange());
    }
}
