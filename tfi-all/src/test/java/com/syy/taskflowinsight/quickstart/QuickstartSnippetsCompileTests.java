package com.syy.taskflowinsight.quickstart;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class QuickstartSnippetsCompileTests {

    static class Person { String name; int age; Person(String n, int a){this.name=n;this.age=a;} }

    @Test
    void non_spring_snippet_should_compile_and_run() {
        TfiContext ctx = DiffBuilder.create()
            .withDeepCompare(true)
            .withMaxDepth(5)
            .build();
        CompareResult r = ctx.compare(new Person("A", 1), new Person("B", 1), CompareOptions.DEEP);
        assertNotNull(r);
    }

    @Test
    void from_spring_env_snippet_should_build() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("tfi.change-tracking.snapshot.max-depth", "5");
        env.setProperty("tfi.change-tracking.snapshot.enable-deep", "true");
        TfiContext ctx = DiffBuilder.fromSpring(env).build();
        assertNotNull(ctx);
    }
}

@SpringBootTest
class QuickstartSpringBeansCompileTests {
    static class Product { String name; Product(String n){this.name=n;} }

    @Autowired private PropertyComparatorRegistry registry;

    @Test
    void spring_snippet_context_should_build() {
        // 注册一个示例比较器（不强约束命中效果，仅验证可用性）
        registry.register("name", (left, right, field) -> true);
        TfiContext ctx = DiffBuilder.create().withDeepCompare(true).withMaxDepth(5).build();
        CompareResult r = ctx.compare(new Product("X"), new Product("Y"));
        assertNotNull(r);
    }
}

