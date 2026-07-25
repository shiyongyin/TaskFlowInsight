package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证示例代码只消费结果真值与有界值事实，不依赖已删除的原始对象接口。 */
class CompareExamplesResultContractTests {

    private static final String FACADE_ENABLED_KEY = "tfi.api.facade.enabled";
    private static final String ROUTING_ENABLED_KEY = "tfi.api.routing.enabled";

    @AfterEach
    void clearFeatureFlags() {
        System.clearProperty(FACADE_ENABLED_KEY);
        System.clearProperty(ROUTING_ENABLED_KEY);
    }

    @Test
    void exampleConsumesCanonicalResultAndBoundedSnapshots() {
        System.setProperty(FACADE_ENABLED_KEY, "true");
        System.setProperty(ROUTING_ENABLED_KEY, "false");

        CompareResult result = TFI.compare(1, "after");

        assertThat(result.isDifferent()).isTrue();
        assertThat(result.getCompletion()).isEqualTo(CompareCompletion.COMPLETE);
        assertThat(result.getChanges()).singleElement().satisfies(change -> {
            ValueSnapshot before = change.beforeValue().orElseThrow();
            ValueSnapshot after = change.afterValue().orElseThrow();
            assertThat(before.representation()).isEqualTo(ValueSnapshot.Representation.EXACT);
            assertThat(before.typeCode()).isEqualTo("type-metadata");
            assertThat(before.canonicalTextFacts()).containsExactly("class", "java.lang.Integer");
            assertThat(after.representation()).isEqualTo(ValueSnapshot.Representation.EXACT);
            assertThat(after.typeCode()).isEqualTo("type-metadata");
            assertThat(after.canonicalTextFacts()).containsExactly("class", "java.lang.String");
        });
    }
}
