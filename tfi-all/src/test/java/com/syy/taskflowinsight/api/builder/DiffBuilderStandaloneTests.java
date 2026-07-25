package com.syy.taskflowinsight.api.builder;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import org.junit.jupiter.api.Test;

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
            .build();

        assertNotNull(ctx);
        CompareResult r = ctx.compare(new Foo("a"), new Foo("b"), CompareOptions.builder().maxDepth(10).build());
        assertNotNull(r);
    }

}
