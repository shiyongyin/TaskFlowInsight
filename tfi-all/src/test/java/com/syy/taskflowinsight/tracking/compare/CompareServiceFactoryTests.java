package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.list.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompareServiceFactoryTests {

    static class Person {
        String name;
        int age;
        Person(String name, int age) { this.name = name; this.age = age; }
    }

    @Test
    void createDefault_should_behave_like_manual_baseline() {
        // Baseline service assembled manually
        ListCompareExecutor baselineExec = new ListCompareExecutor(Arrays.asList(
            new SimpleListStrategy(), new LcsListStrategy(), new LevenshteinListStrategy(),
            new AsSetListStrategy(), new EntityListStrategy()
        ));
        CompareService baseline = new CompareService(baselineExec);

        // Factory-produced service
        CompareService byFactory = CompareService.createDefault(CompareOptions.DEFAULT);

        // List compare smoke: ensure consistent identical/diff flags
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> b = Arrays.asList(1, 2, 4);

        CompareResult r1 = baseline.compare(a, b, CompareOptions.DEFAULT);
        CompareResult r2 = byFactory.compare(a, b, CompareOptions.DEFAULT);

        assertEquals(r1.isIdentical(), r2.isIdentical());
        assertEquals(r1.getChangeCount(), r2.getChangeCount());

        // Object deep-compare smoke
        Person p1 = new Person("alice", 18);
        Person p2 = new Person("bob", 18);
        CompareResult ro1 = baseline.compare(p1, p2, CompareOptions.DEFAULT);
        CompareResult ro2 = byFactory.compare(p1, p2, CompareOptions.DEFAULT);
        assertEquals(ro1.isIdentical(), ro2.isIdentical());
        assertEquals(ro1.getChangeCount(), ro2.getChangeCount());
    }
}

@SpringBootTest
class CompareServiceFactorySpringRegistryTests {

    static class AlwaysEqualComparator implements PropertyComparator {
        @Override
        public boolean areEqual(Object left, Object right, java.lang.reflect.Field field) { return true; }
    }

    static class Product {
        String name;
        Product(String name) { this.name = name; }
    }

    @Autowired private PropertyComparatorRegistry registry;
    @Autowired private com.syy.taskflowinsight.tracking.detector.DiffDetectorService diffService;

    @Test
    void createDefault_with_registry_in_spring_should_construct_and_compare() {
        // 注册一个比较器（不强制要求在本用例中被命中），只验证工厂可用且compare正常
        registry.register("name", new AlwaysEqualComparator());

        CompareService svc = CompareService.createDefault(CompareOptions.DEFAULT, registry);
        assertNotNull(svc);

        Product a = new Product("A");
        Product b = new Product("B");
        CompareResult r = svc.compare(a, b, CompareOptions.builder().enableDeepCompare(true).build());
        assertNotNull(r);
    }
}
