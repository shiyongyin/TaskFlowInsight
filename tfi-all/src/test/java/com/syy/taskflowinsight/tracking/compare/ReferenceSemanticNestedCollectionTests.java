package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 嵌套集合 + 索引路径的浅引用检测用例
 * 场景：Order.items[0].supplier 标注 @ShallowReference，仅检测引用键变化
 */
class ReferenceSemanticNestedCollectionTests {

    @Entity
    static class Supplier {
        @Key
        private final String code;
        private final String name;
        Supplier(String code, String name) { this.code = code; this.name = name; }
        public String getCode() { return code; }
        public String getName() { return name; }
    }

    @Entity
    static class Item {
        @Key
        private final String sku;
        private final int quantity;
        @ShallowReference
        private final Supplier supplier;
        Item(String sku, int quantity, Supplier supplier) {
            this.sku = sku; this.quantity = quantity; this.supplier = supplier;
        }
        public String getSku() { return sku; }
        public int getQuantity() { return quantity; }
        public Supplier getSupplier() { return supplier; }
    }

    @Entity
    static class Order {
        @Key
        private final String id;
        private final List<Item> items;
        Order(String id, List<Item> items) { this.id = id; this.items = items; }
        public String getId() { return id; }
        public List<Item> getItems() { return items; }
    }

    @Test
    void shallow_reference_in_nested_list_element_should_mark_reference_change() {
        Supplier s1 = new Supplier("S1", "Alpha");
        Supplier s2 = new Supplier("S2", "Beta");
        Item i1a = new Item("SKU-1", 2, s1);
        Item i1b = new Item("SKU-1", 2, s2); // 仅 supplier 切换

        List<Item> listA = new ArrayList<>();
        listA.add(i1a);
        Order before = new Order("O100", listA);

        List<Item> listB = new ArrayList<>();
        listB.add(i1b);
        Order after = new Order("O100", listB);

        CompareService service = CompareService.createDefault(CompareOptions.DEEP);
        CompareResult result = service.compare(before, after, CompareOptions.DEEP);

        // 应有且仅有一个引用变更（items[0].supplier）
        List<FieldChange> refChanges = result.getReferenceChanges();
        assertEquals(1, refChanges.size());
        FieldChange c = refChanges.get(0);
        assertTrue(c.isReferenceChange());
        assertNotNull(c.getReferenceDetail());
        assertTrue(c.getReferenceDetail().getOldEntityKey().contains("S1"));
        assertTrue(c.getReferenceDetail().getNewEntityKey().contains("S2"));

        // 验证路径包含集合索引
        String path = c.getFieldPath() != null ? c.getFieldPath() : c.getFieldName();
        assertTrue(path.contains("items[0]"));
        assertTrue(path.endsWith("supplier"));

        // 不应产生 supplier 内部 name 等深度字段变更
        boolean deepSupplierFields = result.getChanges().stream()
            .anyMatch(fc -> fc.getFieldPath() != null && fc.getFieldPath().contains("supplier."));
        assertFalse(deepSupplierFields);
    }
}

