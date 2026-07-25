package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证示例入口只在默认Policy边界内收紧请求选项。 */
class CompareExamplesPolicyContractTests {

    @Test
    void exampleComparatorUsesTheAcceptedDefaultDepthCeiling() {
        CompareResult result = TFI.comparator()
                .withMaxDepth(ComparePolicy.defaults().maxDepth())
                .compare("before", "after");

        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
    }
}
