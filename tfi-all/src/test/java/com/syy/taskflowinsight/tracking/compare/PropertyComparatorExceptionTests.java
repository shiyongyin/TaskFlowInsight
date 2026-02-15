package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 字段级比较器异常路径测试
 *
 * 目标：当 PropertyComparator 抛出异常时，DiffDetectorService 应记录并回退至默认精度比较，不影响结果产出。
 */
class PropertyComparatorExceptionTests {

    static class Foo { String name; int id; Foo(String n, int i){ this.name=n; this.id=i; } }

    static class ThrowingComparator implements PropertyComparator {
        @Override
        public boolean areEqual(Object left, Object right, Field field) throws PropertyComparisonException {
            throw new PropertyComparisonException("boom");
        }
        @Override
        public String getName() { return "ThrowingComparator"; }
    }

    @Test
    @DisplayName("comparator exception falls back to default precision compare")
    void comparator_exception_should_fallback() {
        // 注册会抛异常的比较器于字段路径 "name"
        TfiContext ctx = DiffBuilder.create()
            .withDeepCompare(true)
            .withMaxDepth(5)
            .withPropertyComparator("name", new ThrowingComparator())
            .build();

        CompareOptions opts = CompareOptions.builder()
            .enableDeepCompare(true)
            .maxDepth(5)
            .build();

        Foo a = new Foo("A", 1);
        Foo b = new Foo("B", 1);

        CompareResult r = ctx.compare(a, b, opts);
        assertNotNull(r);
        assertFalse(r.isIdentical(), "should detect name difference despite comparator throwing");
        assertTrue(r.getChangeCount() >= 1, "should contain at least one change");
    }
}

