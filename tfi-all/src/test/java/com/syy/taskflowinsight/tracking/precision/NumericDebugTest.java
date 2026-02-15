package com.syy.taskflowinsight.tracking.precision;

import com.syy.taskflowinsight.api.builder.DiffBuilder;
import com.syy.taskflowinsight.api.builder.TfiContext;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.annotation.NumericPrecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class NumericDebugTest {
    static class Price {
        @NumericPrecision(absoluteTolerance = 0.01, compareMethod = "COMPARE_TO")
        BigDecimal amount;
        Price(String v) { this.amount = new BigDecimal(v); }
    }

    @Test
    void debug() {
        TfiContext ctx = DiffBuilder.create()
            .withDeepCompare(true)
            .withMaxDepth(5)
            .build();

        CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).maxDepth(5).build();
        Price left = new Price("1.000");
        Price right = new Price("1.02");
        System.out.println("class simple name=" + left.getClass().getSimpleName());
        System.out.println("opts maxDepth=" + opts.getMaxDepth());
        CompareResult r = ctx.compare(left, right, opts);
        System.out.println("identical=" + r.isIdentical());
        System.out.println("changes=" + r.getChanges());
    }
}
