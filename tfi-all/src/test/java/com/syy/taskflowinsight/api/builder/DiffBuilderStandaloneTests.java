package com.syy.taskflowinsight.api.builder;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class DiffBuilderStandaloneTests {

    static class DummyComparator implements PropertyComparator {
        @Override public boolean areEqual(Object left, Object right, Field field) { return true; }
    }

    static class Foo { String name; Foo(String n){this.name=n;} }

    @Test
    void create_build_should_return_context_and_compare() {
        TfiContext ctx = DiffBuilder.create()
            .withMaxDepth(5)
            .withDeepCompare(true)
            .withExcludePatterns("*.secret", "*.token")
            .withPropertyComparator("name", new DummyComparator())
            .build();

        assertNotNull(ctx);
        CompareResult r = ctx.compare(new Foo("a"), new Foo("b"), CompareOptions.DEEP);
        assertNotNull(r);
    }

    @Test
    void fromSpring_should_read_basic_flags() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("tfi.change-tracking.snapshot.max-depth", "7");
        env.setProperty("tfi.change-tracking.snapshot.enable-deep", "true");

        TfiContext ctx = DiffBuilder.fromSpring(env)
            .withDeepCompare(true) // 覆盖依然可用
            .build();
        assertNotNull(ctx);
    }

    @Test
    void withPropertyComparator_should_affect_non_spring_deep_compare() {
        // 注册路径级比较器：对字段 name 始终视为相等
        TfiContext ctx = DiffBuilder.create()
            .withDeepCompare(true)
            .withMaxDepth(5)
            .withPropertyComparator("name", new DummyComparator())
            .build();

        Foo a = new Foo("A");
        Foo b = new Foo("B");

        // 使用默认选项（已由 Builder 启用 deep/maxDepth），应命中比较器并视为相等
        CompareResult r = ctx.compare(a, b);
        assertTrue(r.isIdentical(), "property comparator should short-circuit to identical in non-spring mode");
    }
}
