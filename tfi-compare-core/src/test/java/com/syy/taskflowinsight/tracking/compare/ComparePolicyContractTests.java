package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证纯 Java policy 是默认语义与 framework hard ceiling 的唯一事实源。 */
class ComparePolicyContractTests {

    @Test
    void defaultsMatchAcceptedBoundedMatrix() {
        ComparePolicy policy = ComparePolicy.defaults();

        assertThat(policy.enabled()).isTrue();
        assertThat(policy.computeSimilarity()).isFalse();
        assertThat(policy.includeCollectionContents()).isTrue();
        assertThat(policy.maxDepth()).isEqualTo(10);
        assertThat(policy.maxComparedNodes()).isEqualTo(10_000);
        assertThat(policy.maxElements()).isEqualTo(1_000);
        assertThat(policy.deadline()).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.maxChangeDetails()).isEqualTo(1_000);
        assertThat(policy.maxIssues()).isEqualTo(64);
        assertThat(policy.maxResultValueChars()).isEqualTo(4_096);
        assertThat(policy.maxPathEncodedChars()).isEqualTo(4_096);
        assertThat(policy.maxResultTotalChars()).isEqualTo(1_000_000);
        assertThat(policy.maxEntityKeyComponents()).isEqualTo(8);
        assertThat(policy.maxEntityKeyEncodedBytes()).isEqualTo(512);
        assertThat(policy.maxRegisteredExtensions()).isEqualTo(128);
        assertThat(policy.maxPathRules()).isEqualTo(128);
        assertThat(policy.maxPatternSegments()).isEqualTo(100);
        assertThat(policy.maxPatternTokenChars()).isEqualTo(128);
        assertThat(policy.maxPatternTotalChars()).isEqualTo(16_384);
        assertThat(policy.maxTrackingTargets()).isEqualTo(8);
        assertThat(policy.maxTrackingNameChars()).isEqualTo(128);
        assertThat(policy.numericAbsoluteTolerance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(policy.numericRelativeTolerance()).isZero();
        assertThat(policy.temporalTolerance()).isEqualTo(Duration.ZERO);
    }

    @Test
    void frameworkHardBoundaryIsAccepted() {
        ComparePolicy policy = ComparePolicy.builder()
                .maxDepth(100)
                .maxComparedNodes(100_000)
                .maxElements(10_000)
                .deadline(Duration.ofSeconds(30))
                .maxIssues(256)
                .maxResultValueChars(8_192)
                .maxPathEncodedChars(16_384)
                .maxResultTotalChars(10_000_000)
                .maxEntityKeyComponents(32)
                .maxEntityKeyEncodedBytes(2_048)
                .maxTrackingNameChars(256)
                .numericRelativeTolerance(1.0)
                .temporalTolerance(Duration.ofHours(24))
                .build();

        assertThat(policy.maxDepth()).isEqualTo(100);
        assertThat(policy.deadline()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void valueBeyondFrameworkHardBoundaryIsRejectedWithTypedViolation() {
        assertThatThrownBy(() -> ComparePolicy.builder().maxDepth(101).build())
                .isInstanceOfSatisfying(CompareInputException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("CMP_E_1001");
                    assertThat(exception.violation()).isEqualTo(InputViolation.POLICY_OUT_OF_RANGE);
                    assertThat(exception.getMessage()).doesNotContain("101");
                });
    }

    @Test
    void pathRulesAreCompiledWhenPolicyIsBuilt() {
        ComparePolicy policy = ComparePolicy.builder()
                .includePathRules(List.of("PROPERTY:order/PROPERTY:item*"))
                .excludePathRules(List.of("PROPERTY:order/PROPERTY:*Id"))
                .build();
        ComparePath included = ComparePath.root()
                .append(new PropertySegment("order"))
                .append(new PropertySegment("items"));
        ComparePath excluded = ComparePath.root()
                .append(new PropertySegment("order"))
                .append(new PropertySegment("internalId"));

        assertThat(policy.includePathPatterns()).singleElement()
                .satisfies(pattern -> assertThat(pattern.matches(included)).isTrue());
        assertThat(policy.excludePathPatterns()).singleElement()
                .satisfies(pattern -> assertThat(pattern.matches(excluded)).isTrue());
    }

    @Test
    void invalidPathRuleIsRejectedWithTypedSafeViolationAtBuildTime() {
        String invalidRule = "PROPERTY:secret**";

        assertThatThrownBy(() -> ComparePolicy.builder()
                .includePathRules(List.of(invalidRule))
                .build())
                .isInstanceOfSatisfying(CompareInputException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("CMP_E_1001");
                    assertThat(exception.violation()).isEqualTo(InputViolation.INVALID_PATTERN);
                    assertThat(exception.getMessage()).doesNotContain(invalidRule);
                });
    }

    @Test
    void pathRuleCountAndCumulativeTextAreBoundedPerRuleClass() {
        List<String> twoRules = List.of("PROPERTY:a", "PROPERTY:b");

        assertThatThrownBy(() -> ComparePolicy.builder()
                .maxPathRules(1)
                .includePathRules(twoRules)
                .build())
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.INVALID_PATTERN));
        assertThatThrownBy(() -> ComparePolicy.builder()
                .maxPathRules(2)
                .maxPatternTotalChars(19)
                .excludePathRules(twoRules)
                .build())
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.INVALID_PATTERN));

        ComparePolicy boundary = ComparePolicy.builder()
                .maxPathRules(2)
                .maxPatternTotalChars(20)
                .includePathRules(twoRules)
                .build();
        assertThat(boundary.includePathPatterns()).hasSize(2);
    }

    @Test
    void foreignPathSelectionCannotOverrideRuntimePolicy() {
        ComparePolicy runtimePolicy = ComparePolicy.builder()
                .includePathRules(List.of("PROPERTY:included"))
                .build();
        ComparePolicy foreignPolicy = ComparePolicy.builder()
                .includePathRules(List.of("PROPERTY:excluded"))
                .build();
        CompareEngine engine = CompareRuntime.builder().policy(runtimePolicy).build().engine();

        assertThatThrownBy(() -> engine.compare(
                new FilteredValue(1, 1),
                new FilteredValue(2, 2),
                CompareOptions.defaults(foreignPolicy)))
                .isInstanceOfSatisfying(CompareInputException.class, exception ->
                        assertThat(exception.violation()).isEqualTo(InputViolation.OPTION_OUT_OF_RANGE));
    }

    /** 让两份Policy选择不同字段，用于证明runtime是最终语义owner。 */
    private static final class FilteredValue {
        /** Runtime Policy唯一允许进入相等域的字段。 */
        private final int included;

        /** 外来Options试图选择该字段，必须在进入Runtime前被typed拒绝。 */
        private final int excluded;

        private FilteredValue(int included, int excluded) {
            this.included = included;
            this.excluded = excluded;
        }
    }
}
