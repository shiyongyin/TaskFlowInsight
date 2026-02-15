package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 非实体 List 深度对比：多个字段变化应产出多条带 propertyPath 的 MODIFY。
 */
class SimpleListDeepMultipleFieldsTests {

    @Test
    void multiple_field_changes_produce_multiple_events_with_property_path() {
        List<Person> a = Arrays.asList(new Person("A", 18));
        List<Person> b = Arrays.asList(new Person("B", 21));

        ListCompareStrategy simple = new SimpleListStrategy();
        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            simple, new AsSetListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(), new EntityListStrategy()
        ));
        CompareService svc = new CompareService(exec, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        CompareResult r = svc.compare(a, b, CompareOptions.builder().enableDeepCompare(true).build());
        assertTrue(r.hasChanges());

        long propEvents = r.getChanges().stream().filter(fc ->
            fc.getElementEvent() != null && fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MODIFY &&
                fc.getElementEvent().getPropertyPath() != null).count();
        // 期望 name/age 两个字段均产生事件
        assertTrue(propEvents >= 2, "应至少两条带 propertyPath 的 MODIFY 事件");
    }

    private static class Person {
        private final String name;
        private final int age;
        Person(String n, int a) { this.name = n; this.age = a; }
    }
}

