package com.syy.taskflowinsight.annotation;

import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class CustomComparatorAnnotationTests {

    static class DummyComparator implements PropertyComparator {
        @Override public boolean areEqual(Object left, Object right, Field field) { return true; }
    }

    static class Entity {
        @CustomComparator(value = DummyComparator.class, cached = true, description = "case-insensitive")
        String name;
    }

    @Test
    void reflection_should_read_annotation_values() throws Exception {
        Field f = Entity.class.getDeclaredField("name");
        assertTrue(f.isAnnotationPresent(CustomComparator.class));
        CustomComparator cc = f.getAnnotation(CustomComparator.class);
        assertEquals(DummyComparator.class, cc.value());
        assertTrue(cc.cached());
        assertEquals("case-insensitive", cc.description());
    }
}

