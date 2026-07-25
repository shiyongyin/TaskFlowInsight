package com.syy.taskflowinsight.quickstart;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuickstartSnippetsCompileTests {

    static class Person { String name; int age; Person(String n, int a){this.name=n;this.age=a;} }

    @Test
    void non_spring_snippet_should_compile_and_run() {
        TfiContext ctx = DiffBuilder.create()
            .withDeepCompare(true)
            .withMaxDepth(5)
            .build();
        CompareResult r = ctx.compare(new Person("A", 1), new Person("B", 1), CompareOptions.builder().maxDepth(10).build());
        assertNotNull(r);
    }

}
