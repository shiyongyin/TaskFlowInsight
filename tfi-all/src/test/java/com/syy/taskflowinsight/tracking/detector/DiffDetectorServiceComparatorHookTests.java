package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.annotation.CustomComparator;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiffDetectorServiceComparatorHookTests {

    static class AlwaysEqualComparator implements PropertyComparator {
        @Override
        public boolean areEqual(Object left, Object right, Field field) { return true; }
    }

    static class AlwaysDifferentComparator implements PropertyComparator {
        @Override
        public boolean areEqual(Object left, Object right, Field field) { return false; }
    }

    static class Product {
        @CustomComparator(AlwaysEqualComparator.class)
        String name;

        @CustomComparator(value = AlwaysDifferentComparator.class, cached = false)
        String code;
    }

    @Test
    void comparator_hit_equal_should_short_circuit_no_change() {
        DiffDetectorService svc = new DiffDetectorService();
        svc.setComparatorRegistry(new PropertyComparatorRegistry());
        svc.registerObjectType("Product", Product.class);

        Map<String, Object> before = new HashMap<>();
        before.put("name", "A");
        Map<String, Object> after = new HashMap<>();
        after.put("name", "B");

        List<ChangeRecord> changes = svc.diff("Product", before, after);
        // comparator returns equal => no change
        assertTrue(changes.stream().noneMatch(c -> c.getFieldName().equals("name")));
    }

    @Test
    void comparator_hit_not_equal_should_report_update() {
        DiffDetectorService svc = new DiffDetectorService();
        svc.setComparatorRegistry(new PropertyComparatorRegistry());
        svc.registerObjectType("Product", Product.class);

        Map<String, Object> before = new HashMap<>();
        before.put("code", "X1");
        Map<String, Object> after = new HashMap<>();
        after.put("code", "X2");

        List<ChangeRecord> changes = svc.diff("Product", before, after);
        assertTrue(changes.stream().anyMatch(c -> c.getFieldName().equals("code") && c.getChangeType() == ChangeType.UPDATE));
    }
}

