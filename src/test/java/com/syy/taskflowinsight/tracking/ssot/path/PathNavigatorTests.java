package com.syy.taskflowinsight.tracking.ssot.path;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PathNavigatorTests {

    @Entity
    static class Supplier { @Key String code; Supplier(String c){this.code=c;} public String getCode(){return code;} }

    @Entity
    static class Item {
        @Key String sku; @ShallowReference Supplier supplier;
        Item(String sku, Supplier s){this.sku=sku; this.supplier=s;}
        public String getSku(){return sku;}
        public Supplier getSupplier(){return supplier;}
    }

    @Entity
    static class Order {
        @Key String id; List<Item> items = new ArrayList<>(); Map<String,Object> attrs = new HashMap<>();
        Order(String id){this.id=id;}
        public String getId(){return id;}
        public List<Item> getItems(){return items;}
        public Map<String,Object> getAttrs(){return attrs;}
    }

    @Test
    void resolve_list_index_and_map_key_should_work() {
        Order o = new Order("O1");
        o.items.add(new Item("S1", new Supplier("SUP-1")));
        o.attrs.put("region", new Supplier("CN"));

        Object s0 = PathNavigator.resolve(o, "Order.items[0].supplier");
        assertNotNull(s0);

        Object s1 = PathNavigator.resolve(o, "Order.attrs[region]");
        assertNotNull(s1);
    }

    @Test
    void isAnnotatedField_should_detect_shallow_reference_on_last_segment() {
        Order o = new Order("O1");
        o.items.add(new Item("S1", new Supplier("SUP-1")));
        boolean annotated = PathNavigator.isAnnotatedField(o, "Order.items[0].supplier", ShallowReference.class);
        assertTrue(annotated);
    }
}

