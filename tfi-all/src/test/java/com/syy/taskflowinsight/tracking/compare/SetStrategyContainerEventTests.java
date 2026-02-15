package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-T1: 容器事件结构化 - Set策略完整测试套件
 *
 * 测试覆盖:
 * 1. Simple Set ADD/REMOVE operations
 * 2. Entity Set handling (delegates to EntityListStrategy)
 * 3. EntityKey resolution for Set elements
 * 4. Duplicate key detection (via Entity Set)
 * 5. Edge cases (empty sets, null elements)
 */
@DisplayName("P1-T1: Set Strategy Container Events Tests")
class SetStrategyContainerEventTests {

    // ==================== Test Models ====================

    @Entity
    static class Product {
        @Key
        private final String sku;
        private final String name;
        private final double price;

        Product(String sku, String name, double price) {
            this.sku = sku;
            this.name = name;
            this.price = price;
        }

        public String getSku() { return sku; }
        public String getName() { return name; }
        public double getPrice() { return price; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Product)) return false;
            Product product = (Product) o;
            return sku.equals(product.sku);
        }

        @Override
        public int hashCode() {
            return sku.hashCode();
        }
    }

    // ==================== Simple Set Tests ====================

    @Test
    @DisplayName("SET: ADD operation should fill elementEvent with SET + ADD")
    void setStrategy_add_shouldFillElementEvent() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<String> oldSet = Set.of("A", "B");
        Set<String> newSet = Set.of("A", "B", "C");

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange addChange = result.getChangesByType(ChangeType.CREATE).stream()
            .findFirst().orElseThrow();

        assertTrue(addChange.isContainerElementChange(), "Should have elementEvent");
        assertEquals(FieldChange.ContainerType.SET, addChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, addChange.getElementEvent().getOperation());
        assertEquals("C", addChange.getNewValue());
        assertNull(addChange.getOldValue());
    }

    @Test
    @DisplayName("SET: REMOVE operation should fill elementEvent with SET + REMOVE")
    void setStrategy_remove_shouldFillElementEvent() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<String> oldSet = Set.of("A", "B", "C");
        Set<String> newSet = Set.of("A", "B");

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange removeChange = result.getChangesByType(ChangeType.DELETE).stream()
            .findFirst().orElseThrow();

        assertTrue(removeChange.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.SET, removeChange.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.REMOVE, removeChange.getElementEvent().getOperation());
        assertEquals("C", removeChange.getOldValue());
        assertNull(removeChange.getNewValue());
    }

    @Test
    @DisplayName("SET: Multiple changes should all have elementEvent")
    void setStrategy_multipleChanges_shouldAllHaveElementEvent() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<String> oldSet = Set.of("A", "B", "C");
        Set<String> newSet = Set.of("B", "C", "D");

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        assertEquals(2, result.getChangeCount(), "Should have 1 DELETE + 1 CREATE");

        // 验证所有变更都有elementEvent
        for (FieldChange fc : result.getChanges()) {
            assertTrue(fc.isContainerElementChange(), "All changes should have elementEvent");
            assertEquals(FieldChange.ContainerType.SET, fc.getElementEvent().getContainerType());
        }
    }

    // ==================== Entity Set Tests ====================

    @Test
    @DisplayName("ENTITY_SET: Should delegate to EntityListStrategy and preserve LIST containerType")
    void entitySet_shouldDelegateAndPreserveListType() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<Product> oldSet = Set.of(new Product("SKU1", "iPhone", 999));
        Set<Product> newSet = Set.of(new Product("SKU1", "iPhone Pro", 1099));

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);

        // Entity Set委托给EntityListStrategy，所以containerType是LIST
        assertTrue(change.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.LIST, change.getElementEvent().getContainerType(),
            "Entity Set delegates to EntityListStrategy, so containerType should be LIST");
        assertNotNull(change.getElementEvent().getEntityKey(), "Entity Set should have entityKey");
    }

    @Test
    @DisplayName("ENTITY_SET: ADD should fill entityKey")
    void entitySet_add_shouldFillEntityKey() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<Product> oldSet = Collections.emptySet();
        Set<Product> newSet = Set.of(new Product("SKU1", "iPhone", 999));

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange addChange = result.getChanges().get(0);

        assertTrue(addChange.isContainerElementChange());
        assertNotNull(addChange.getElementEvent().getEntityKey(), "Entity ADD should fill entityKey");
        assertEquals(FieldChange.ElementOperation.ADD, addChange.getElementEvent().getOperation());
    }

    @Test
    @DisplayName("ENTITY_SET: DELETE should fill entityKey")
    void entitySet_delete_shouldFillEntityKey() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<Product> oldSet = Set.of(new Product("SKU1", "iPhone", 999));
        Set<Product> newSet = Collections.emptySet();

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange deleteChange = result.getChanges().get(0);

        assertTrue(deleteChange.isContainerElementChange());
        assertNotNull(deleteChange.getElementEvent().getEntityKey(), "Entity DELETE should fill entityKey");
        assertEquals(FieldChange.ElementOperation.REMOVE, deleteChange.getElementEvent().getOperation());
    }

    // ==================== EntityKey Resolution Tests ====================

    @Test
    @DisplayName("SET: Non-Entity elements should have null or valid entityKey")
    void set_nonEntity_shouldHaveNullOrValidEntityKey() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<String> oldSet = Set.of("A");
        Set<String> newSet = Set.of("A", "B");

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange addChange = result.getChanges().get(0);

        assertTrue(addChange.isContainerElementChange());
        // Non-Entity元素的entityKey可以为null（因为没有@Key注解）
        // 这是正常行为，不应导致NPE
    }

    @Test
    @DisplayName("SET: Entity elements must have non-null entityKey")
    void set_entity_mustHaveNonNullEntityKey() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<Product> oldSet = Collections.emptySet();
        Set<Product> newSet = Set.of(new Product("SKU999", "MacBook", 2499));

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange addChange = result.getChanges().get(0);

        assertTrue(addChange.isContainerElementChange());
        assertNotNull(addChange.getElementEvent().getEntityKey(),
            "Entity elements MUST have entityKey resolved");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("SET: Empty to non-empty should fill elementEvent")
    void set_emptyToNonEmpty_shouldFillElementEvent() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<String> oldSet = Collections.emptySet();
        Set<String> newSet = Set.of("A");

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        assertFalse(result.getChanges().isEmpty());
        FieldChange change = result.getChanges().get(0);
        assertTrue(change.isContainerElementChange());
        assertEquals(FieldChange.ContainerType.SET, change.getElementEvent().getContainerType());
        assertEquals(FieldChange.ElementOperation.ADD, change.getElementEvent().getOperation());
    }

    @Test
    @DisplayName("SET: Identical sets should return no changes")
    void set_identical_shouldReturnNoChanges() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<String> set1 = Set.of("A", "B", "C");
        Set<String> set2 = Set.of("A", "B", "C");

        CompareResult result = strategy.compare(set1, set2, CompareOptions.DEFAULT);

        assertTrue(result.isIdentical());
        assertTrue(result.getChanges().isEmpty());
    }

    @Test
    @DisplayName("SET: Null handling should not cause NPE")
    void set_nullHandling_shouldNotCauseNPE() {
        SetCompareStrategy strategy = new SetCompareStrategy();

        // null vs empty
        CompareResult result1 = strategy.compare(null, Collections.emptySet(), CompareOptions.DEFAULT);
        assertNotNull(result1);

        // null vs non-empty
        CompareResult result2 = strategy.compare(Set.of("A"), null, CompareOptions.DEFAULT);
        assertNotNull(result2);

        // Both null
        CompareResult result3 = strategy.compare(null, null, CompareOptions.DEFAULT);
        assertNotNull(result3);
    }

    // ==================== Integration with ContainerChanges Query ====================

    @Test
    @DisplayName("SET changes should be queryable via getContainerChanges()")
    void set_changes_shouldBeQueryable() {
        SetCompareStrategy strategy = new SetCompareStrategy();
        Set<String> oldSet = Set.of("A");
        Set<String> newSet = Set.of("A", "B");

        CompareResult result = strategy.compare(oldSet, newSet, CompareOptions.DEFAULT);

        // 使用CompareResult的查询方法
        java.util.List<FieldChange> containerChanges = result.getContainerChanges();
        assertFalse(containerChanges.isEmpty(), "Container changes should be queryable");
        assertEquals(result.getChanges().size(), containerChanges.size(),
            "All Set changes should be container changes");
    }
}
