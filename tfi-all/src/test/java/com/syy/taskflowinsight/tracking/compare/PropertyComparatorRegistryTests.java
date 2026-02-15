package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.CustomComparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PropertyComparatorRegistryTests {

    static class CountingComparator implements PropertyComparator {
        static final AtomicInteger created = new AtomicInteger(0);
        CountingComparator() { created.incrementAndGet(); }
        @Override public boolean areEqual(Object left, Object right, Field field) { return left == right || (left != null && left.equals(right)); }
    }

    static class TestEntity {
        @CustomComparator(value = CountingComparator.class, cached = true)
        String cachedField;

        @CustomComparator(value = CountingComparator.class, cached = false)
        String uncachedField;

        String price; // for path match
    }

    @AfterEach
    void resetCounter() { CountingComparator.created.set(0); }

    @Test
    void register_and_find_by_path_should_work() throws Exception {
        PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
        CountingComparator cmp = new CountingComparator();
        registry.register("price", cmp);

        Field f = TestEntity.class.getDeclaredField("price");
        PropertyComparator found = registry.findComparator("price", f);
        assertSame(cmp, found);

        PropertyComparatorRegistry.MetricsSnapshot snap = registry.getMetricsSnapshot();
        assertEquals(1, snap.getPathHits());
        assertEquals(0, snap.getAnnotationHits());
        assertEquals(0, snap.getMisses());
    }

    @Test
    void invalid_path_should_throw() {
        PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(".bad", new CountingComparator()));
        assertThrows(IllegalArgumentException.class, () -> registry.register("bad.", new CountingComparator()));
        assertThrows(IllegalArgumentException.class, () -> registry.register("", new CountingComparator()));
    }

    @Test
    void annotation_cached_true_should_instantiate_once() throws Exception {
        PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
        Field f = TestEntity.class.getDeclaredField("cachedField");
        PropertyComparator c1 = registry.findComparator("cachedField", f);
        PropertyComparator c2 = registry.findComparator("cachedField", f);
        assertNotNull(c1);
        assertSame(c1, c2);
        assertEquals(1, CountingComparator.created.get());

        PropertyComparatorRegistry.MetricsSnapshot snap = registry.getMetricsSnapshot();
        assertEquals(2, snap.getAnnotationHits());
    }

    @Test
    void annotation_cached_false_should_instantiate_each_time() throws Exception {
        PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
        Field f = TestEntity.class.getDeclaredField("uncachedField");
        PropertyComparator c1 = registry.findComparator("uncachedField", f);
        PropertyComparator c2 = registry.findComparator("uncachedField", f);
        assertNotNull(c1);
        assertNotNull(c2);
        assertNotSame(c1, c2);
        assertEquals(2, CountingComparator.created.get());

        PropertyComparatorRegistry.MetricsSnapshot snap = registry.getMetricsSnapshot();
        assertEquals(2, snap.getAnnotationHits());
    }

    @Test
    void misses_should_increment_when_not_found() {
        PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
        PropertyComparator c = registry.findComparator("unknown", null);
        assertNull(c);
        PropertyComparatorRegistry.MetricsSnapshot snap = registry.getMetricsSnapshot();
        assertEquals(1, snap.getMisses());
        assertEquals(0, snap.getPathHits());
        assertEquals(0, snap.getAnnotationHits());
    }
}
