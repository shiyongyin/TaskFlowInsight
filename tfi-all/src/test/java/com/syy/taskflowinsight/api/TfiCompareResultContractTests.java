package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 all-in-one facade 不会把禁用或执行失败伪装成比较相等。 */
class TfiCompareResultContractTests {

    @Test
    void unavailableComparatorServiceReturnsTypedFailure() {
        CompareResult result = new ComparatorBuilder(null).compare("before", "after");

        assertThat(result.getOutcome()).isEqualTo(CompareOutcome.INDETERMINATE);
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.FAILED);
        assertThat(result.getProblems())
                .extracting(problem -> problem.code())
                .containsExactly(CompareProblemCode.PROVIDER_UNAVAILABLE);
    }
}
