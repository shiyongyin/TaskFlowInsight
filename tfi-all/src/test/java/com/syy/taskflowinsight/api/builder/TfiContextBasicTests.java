package com.syy.taskflowinsight.api.builder;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TfiContextBasicTests {

    static class Person {
        String name;
        int age;
        Person(String n, int a) { this.name = n; this.age = a; }
    }

    @Test
    void compare_overloads_should_work() {
        TfiContext ctx = DiffBuilder.create().withDeepCompare(true).withMaxDepth(5).build();
        assertNotNull(ctx.compareService());

        Person a = new Person("alice", 18);
        Person b = new Person("alice", 18);
        Person c = new Person("bob", 18);

        CompareResult r1 = ctx.compare(a, b);
        CompareResult r2 = ctx.compare(a, c, CompareOptions.DEEP);

        assertTrue(r1.isIdentical());
        assertFalse(r2.isIdentical());
    }

    @Test
    void immutability_should_hold_after_construction() {
        TfiContext ctx = DiffBuilder.create().build();
        assertNotNull(ctx.compareService());

        // 尝试通过外部引用影响内部状态（这里无公开 Setter，引用只读）
        // 断言 compare 仍然可用
        CompareResult r = ctx.compare("x", "y");
        assertNotNull(r);
    }
}

