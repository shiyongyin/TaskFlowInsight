package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证单次options不可变且只能收紧所属runtime policy。 */
class CompareInputValidationTests {

    @Test
    void optionsExposeNoMutableStateAfterBuild() {
        assertThat(CompareOptions.class.getDeclaredFields())
                .filteredOn(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
        assertThat(CompareOptions.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set"));
    }

    @Test
    void unspecifiedOptionsInheritActivePolicy() {
        ComparePolicy policy = ComparePolicy.builder()
                .maxDepth(7)
                .maxElements(700)
                .maxIssues(32)
                .build();

        CompareOptions options = CompareOptions.defaults(policy);

        assertThat(options.maxDepth()).isEqualTo(7);
        assertThat(options.maxElements()).isEqualTo(700);
        assertThat(options.maxIssues()).isEqualTo(32);
        assertThat(options.maxComparedNodes()).isEqualTo(policy.maxComparedNodes());
        assertThat(options.deadline()).isEqualTo(policy.deadline());
        assertThat(options.maxChangeDetails()).isEqualTo(policy.maxChangeDetails());
        assertThat(options.maxResultValueChars()).isEqualTo(policy.maxResultValueChars());
        assertThat(options.maxPathEncodedChars()).isEqualTo(policy.maxPathEncodedChars());
        assertThat(options.maxResultTotalChars()).isEqualTo(policy.maxResultTotalChars());
        assertThat(options.maxEntityKeyComponents()).isEqualTo(policy.maxEntityKeyComponents());
        assertThat(options.maxEntityKeyEncodedBytes()).isEqualTo(policy.maxEntityKeyEncodedBytes());
        assertThat(options.numericAbsoluteTolerance()).isEqualByComparingTo(policy.numericAbsoluteTolerance());
        assertThat(options.numericRelativeTolerance()).isEqualTo(policy.numericRelativeTolerance());
        assertThat(options.temporalTolerance()).isEqualTo(policy.temporalTolerance());
        assertThat(options.computeSimilarity()).isFalse();
        assertThat(options.includeCollectionContents()).isTrue();
    }

    @Test
    void explicitOptionsMayTightenPolicyLimits() {
        ComparePolicy policy = ComparePolicy.defaults();

        CompareOptions options = CompareOptions.builder(policy)
                .maxDepth(5)
                .maxComparedNodes(5_000)
                .maxElements(500)
                .deadline(Duration.ofMillis(500))
                .maxChangeDetails(500)
                .maxResultValueChars(2_048)
                .maxPathEncodedChars(2_048)
                .maxResultTotalChars(500_000)
                .maxEntityKeyComponents(4)
                .maxEntityKeyEncodedBytes(256)
                .includeCollectionContents(false)
                .computeSimilarity(true)
                .build();

        assertThat(options.maxDepth()).isEqualTo(5);
        assertThat(options.maxElements()).isEqualTo(500);
        assertThat(options.maxComparedNodes()).isEqualTo(5_000);
        assertThat(options.deadline()).isEqualTo(Duration.ofMillis(500));
        assertThat(options.maxResultTotalChars()).isEqualTo(500_000);
        assertThat(options.includeCollectionContents()).isFalse();
        assertThat(options.computeSimilarity()).isTrue();
    }

    @Test
    void optionsCannotExpandPolicyLimitsOrDisabledSemantics() {
        ComparePolicy policy = ComparePolicy.builder()
                .includeCollectionContents(false)
                .maxDepth(5)
                .build();

        assertOptionViolation(() -> CompareOptions.builder(policy).maxDepth(6).build());
        assertOptionViolation(() -> CompareOptions.builder(policy)
                .includeCollectionContents(true)
                .build());
        assertOptionViolation(() -> CompareOptions.builder(policy).maxComparedNodes(100_001).build());
        assertOptionViolation(() -> CompareOptions.builder(policy).deadline(Duration.ofSeconds(31)).build());
        assertOptionViolation(() -> CompareOptions.builder(policy).maxResultTotalChars(65_535).build());
        assertOptionViolation(() -> CompareOptions.builder(policy)
                .numericAbsoluteTolerance(BigDecimal.ONE)
                .build());
    }

    private static void assertOptionViolation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CompareInputException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("CMP_E_1001");
                    assertThat(exception.violation()).isEqualTo(InputViolation.OPTION_OUT_OF_RANGE);
                });
    }
}
