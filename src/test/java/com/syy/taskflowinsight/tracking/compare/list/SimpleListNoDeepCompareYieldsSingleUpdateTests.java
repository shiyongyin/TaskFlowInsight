package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SimpleListNoDeepCompareYieldsSingleUpdateTests {

    @Test
    void no_deep_compare_yields_single_update_event_without_property_path() {
        List<Person> a = Arrays.asList(new Person("A", 18));
        List<Person> b = Arrays.asList(new Person("B", 21));

        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(), new AsSetListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(), new EntityListStrategy()
        ));
        CompareService svc = new CompareService(exec, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        CompareResult r = svc.compare(a, b, CompareOptions.builder().enableDeepCompare(false).build());
        assertTrue(r.hasChanges());
        assertEquals(1, r.getChanges().size(), "未开启深度对比时，应只产生一条位置级 UPDATE 事件");

        FieldChange fc = r.getChanges().get(0);
        assertNotNull(fc.getElementEvent());
        assertEquals(FieldChange.ElementOperation.MODIFY, fc.getElementEvent().getOperation());
        assertNull(fc.getElementEvent().getPropertyPath(), "未开启深度对比时不应产生 propertyPath");
    }

    private static class Person {
        private final String name;
        private final int age;
        Person(String n, int a) { this.name = n; this.age = a; }
    }
}

