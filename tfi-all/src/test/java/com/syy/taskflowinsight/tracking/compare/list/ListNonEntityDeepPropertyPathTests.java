package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 非实体 List 深度比较时，产生带 propertyPath 的 MODIFY 事件。
 */
class ListNonEntityDeepPropertyPathTests {

    @Test
    void simple_list_element_deep_change_has_property_path() {
        List<Person> a = new ArrayList<>(Arrays.asList(new Person("A", 18), new Person("B", 20)));
        List<Person> b = new ArrayList<>(Arrays.asList(new Person("A", 18), new Person("B", 21)));

        ListCompareStrategy simple = new SimpleListStrategy();
        ListCompareExecutor exec = new ListCompareExecutor(Arrays.asList(
            simple, new AsSetListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(), new EntityListStrategy()
        ));
        CompareService svc = new CompareService(exec, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        CompareResult r = svc.compare(a, b, CompareOptions.builder().enableDeepCompare(true).build());
        assertTrue(r.hasChanges());

        boolean ok = r.getChanges().stream().anyMatch(fc ->
            fc.getElementEvent() != null &&
            fc.getElementEvent().getContainerType() == FieldChange.ContainerType.LIST &&
            fc.getElementEvent().getOperation() == FieldChange.ElementOperation.MODIFY &&
            Integer.valueOf(1).equals(fc.getElementEvent().getIndex()) &&
            "age".equals(fc.getElementEvent().getPropertyPath())
        );
        assertTrue(ok, "非实体 List 深度修改应输出带 propertyPath 的 MODIFY 事件");
    }

    private static class Person {
        private final String name;
        private final int age;
        Person(String n, int a) { this.name = n; this.age = a; }
    }
}
