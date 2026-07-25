package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AlgorithmId作为结果schema和运行时注册共同使用的低基数标识，必须在进入结果模型前冻结编码语义。
 */
class AlgorithmIdValueContractTests {

    @Test
    void minimalVersionedIdentifierKeepsItsCanonicalEncoding() {
        AlgorithmId algorithmId = AlgorithmId.of("a:b:v1");

        assertThat(algorithmId.value()).isEqualTo("a:b:v1");
    }

    @Test
    void uppercaseIdentifierIsRejectedInsteadOfBeingNormalized() {
        assertThatThrownBy(() -> AlgorithmId.of("Acme:compare:v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalEncodingHonorsTheInclusiveLengthBoundary() {
        String atLimit = "a:" + "b".repeat(123) + ":v1";
        String overLimit = "a:" + "b".repeat(124) + ":v1";

        assertThat(AlgorithmId.of(atLimit).value()).hasSize(128);
        assertThatThrownBy(() -> AlgorithmId.of(overLimit))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
