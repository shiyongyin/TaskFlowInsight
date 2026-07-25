package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.tfi.kernel.KernelConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationBindingContractTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TfiKernelRuntimeAutoConfiguration.class,
                    TfiCompareCoreAutoConfiguration.class,
                    TfiKernelCompareAutoConfiguration.class));

    @Test
    void unboundPropertiesUseOnlyCoreOwnerDefaults() {
        contextRunner.run(context -> {
            KernelConfig kernelDefaults = KernelConfig.defaults();
            TfiKernelProperties kernel = context.getBean(TfiKernelProperties.class);
            assertThat(List.of(
                    kernel.enabled(),
                    kernel.maxStages(),
                    kernel.maxSessionEncodedBytes(),
                    kernel.maxRecordEncodedBytes(),
                    kernel.maxAttrs())).containsExactly(
                    kernelDefaults.enabled(),
                    kernelDefaults.maxStages(),
                    kernelDefaults.maxSessionEncodedBytes(),
                    kernelDefaults.maxRecordEncodedBytes(),
                    kernelDefaults.maxAttrs());

            TfiCompareCoreProperties compare = context.getBean(TfiCompareCoreProperties.class);
            assertThat(policyFacts(compare.toPolicyBuilder().build()))
                    .containsExactlyElementsOf(policyFacts(ComparePolicy.defaults()));
            assertThat(compare.masking().additionalRules()).isEmpty();

            TfiKernelCompareProperties integration =
                    context.getBean(TfiKernelCompareProperties.class);
            assertThat(integration.enabled()).isTrue();
            assertThat(integration.maxRecordedChanges()).isZero();
            assertThat(integration.aop().enabled()).isFalse();
        });
    }

    @Test
    void booleanPropertiesBindWithoutLegacyAliases() {
        contextRunner.withPropertyValues(
                "tfi.kernel.enabled=false",
                "tfi.compare.enabled=false",
                "tfi.compare.compute-similarity=true",
                "tfi.compare.include-collection-contents=false",
                "tfi.change-tracking.enabled=true",
                "tfi.kernel-compare.aop.enabled=false")
                .run(context -> {
                    TfiKernelProperties kernel = context.getBean(TfiKernelProperties.class);
                    TfiCompareCoreProperties compare = context.getBean(TfiCompareCoreProperties.class);
                    assertThat(kernel.enabled()).isFalse();
                    assertThat(compare.enabled()).isFalse();
                    assertThat(compare.computeSimilarity()).isTrue();
                    assertThat(compare.includeCollectionContents()).isFalse();
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("numericBoundaryCases")
    void acceptsDocumentedMinimumAndMaximum(BoundaryCase boundary) {
        runValid(boundary.valuesFor(boundary.minimum(), boundary.minimumCompanions()));
        runValid(boundary.valuesFor(boundary.maximum(), boundary.maximumCompanions()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("numericBoundaryCases")
    void rejectsValuesOutsideDocumentedRange(BoundaryCase boundary) {
        runInvalid(
                boundary.valuesFor(boundary.belowMinimum(), boundary.minimumCompanions()),
                boundary.property());
        runInvalid(
                boundary.valuesFor(boundary.aboveMaximum(), boundary.maximumCompanions()),
                boundary.property());
    }

    @Test
    void ruleCollectionsBindAndRejectMalformedOrOversizedInput() {
        runValid(new String[] {
                "tfi.compare.include-path-rules[0]=PROPERTY:public*",
                "tfi.compare.exclude-path-rules[0]=PROPERTY:internal*",
                "tfi.compare.masking.additional-rules[0]=PROPERTY:phone"
        });
        runInvalid(
                new String[] {"tfi.compare.include-path-rules[0]=not-a-typed-rule"},
                "tfi.compare.include-path-rules");
        runInvalid(
                new String[] {"tfi.compare.exclude-path-rules[0]=not-a-typed-rule"},
                "tfi.compare.exclude-path-rules");
        runInvalid(
                new String[] {"tfi.compare.masking.additional-rules[0]=not-a-typed-rule"},
                "tfi.compare.masking.additional-rules");
        runInvalid(
                indexedRules("tfi.compare.include-path-rules", 129),
                "tfi.compare.include-path-rules");
        runInvalid(
                indexedRules("tfi.compare.exclude-path-rules", 129),
                "tfi.compare.exclude-path-rules");
        runInvalid(
                indexedRules("tfi.compare.masking.additional-rules", 117),
                "tfi.compare.masking.additional-rules");
    }

    @Test
    void kernelRecordBudgetCannotExceedSessionBudget() {
        runInvalid(new String[] {
                "tfi.kernel.max-session-encoded-bytes=1024",
                "tfi.kernel.max-record-encoded-bytes=1025"
        }, "tfi.kernel.max-record-encoded-bytes");
    }

    @Test
    void numericRelativeToleranceRejectsNonFiniteValues() {
        runInvalid(
                new String[] {"tfi.compare.numeric-relative-tolerance=NaN"},
                "tfi.compare.numeric-relative-tolerance");
        runInvalid(
                new String[] {"tfi.compare.numeric-relative-tolerance=Infinity"},
                "tfi.compare.numeric-relative-tolerance");
    }

    @Test
    void aopCannotBeEnabledWhenIntegrationIsDisabled() {
        contextRunner.withPropertyValues(
                "tfi.kernel-compare.enabled=false",
                "tfi.kernel-compare.aop.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("KCS_E_1003");
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("tfi.kernel-compare.aop.enabled");
                });
    }

    private void runValid(String[] properties) {
        contextRunner.withPropertyValues(properties)
                .run(context -> assertThat(context).hasNotFailed());
    }

    private void runInvalid(String[] properties, String expectedPath) {
        contextRunner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("KCS_E_1003");
            assertThat(context.getStartupFailure()).hasStackTraceContaining(expectedPath);
        });
    }

    private static String[] indexedRules(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> prefix + "[" + index + "]=PROPERTY:field" + index)
                .toArray(String[]::new);
    }

    private static Stream<BoundaryCase> numericBoundaryCases() {
        return Stream.of(
                boundary("tfi.kernel.max-stages", "1", "1024", "0", "1025"),
                new BoundaryCase(
                        "tfi.kernel.max-session-encoded-bytes", "1", "1048576", "0", "1048577",
                        List.of("tfi.kernel.max-record-encoded-bytes=1"), List.of()),
                new BoundaryCase(
                        "tfi.kernel.max-record-encoded-bytes", "1", "65536", "0", "65537",
                        List.of(), List.of("tfi.kernel.max-session-encoded-bytes=65536")),
                boundary("tfi.kernel.max-attrs", "0", "256", "-1", "257"),
                boundary("tfi.compare.max-depth", "0", "100", "-1", "101"),
                boundary("tfi.compare.max-compared-nodes", "1", "100000", "0", "100001"),
                boundary("tfi.compare.max-elements", "1", "10000", "0", "10001"),
                boundary("tfi.compare.deadline", "1ns", "30s", "0s", "31s"),
                boundary("tfi.compare.max-change-details", "1", "1000", "0", "1001"),
                boundary("tfi.compare.max-issues", "3", "256", "2", "257"),
                boundary("tfi.compare.max-result-value-chars", "64", "8192", "63", "8193"),
                boundary("tfi.compare.max-path-encoded-chars", "64", "16384", "63", "16385"),
                boundary(
                        "tfi.compare.max-result-total-chars",
                        "65536", "10000000", "65535", "10000001"),
                boundary("tfi.compare.max-entity-key-components", "1", "32", "0", "33"),
                boundary("tfi.compare.max-entity-key-encoded-bytes", "64", "2048", "63", "2049"),
                boundary("tfi.compare.max-registered-extensions", "1", "128", "0", "129"),
                boundary("tfi.compare.max-path-rules", "0", "128", "-1", "129"),
                boundary("tfi.compare.max-pattern-segments", "1", "100", "0", "101"),
                boundary("tfi.compare.max-pattern-token-chars", "1", "128", "0", "129"),
                boundary("tfi.compare.max-pattern-total-chars", "1", "16384", "0", "16385"),
                boundary("tfi.compare.max-tracking-targets", "1", "8", "0", "9"),
                boundary("tfi.compare.max-tracking-name-chars", "1", "256", "0", "257"),
                boundary("tfi.compare.numeric-absolute-tolerance", "0", "1E+64", "-0.1", "1E+65"),
                boundary("tfi.compare.numeric-relative-tolerance", "0", "1", "-0.1", "1.1"),
                boundary("tfi.compare.temporal-tolerance", "0s", "24h", "-1ns", "25h"),
                boundary("tfi.kernel-compare.max-recorded-changes", "0", "32", "-1", "33"));
    }

    private static BoundaryCase boundary(
            String property,
            String minimum,
            String maximum,
            String belowMinimum,
            String aboveMaximum) {
        return new BoundaryCase(
                property,
                minimum,
                maximum,
                belowMinimum,
                aboveMaximum,
                List.of(),
                List.of());
    }

    private static List<Object> policyFacts(ComparePolicy policy) {
        List<Object> facts = new ArrayList<>();
        facts.add(policy.enabled());
        facts.add(policy.computeSimilarity());
        facts.add(policy.includeCollectionContents());
        facts.add(policy.maxDepth());
        facts.add(policy.maxComparedNodes());
        facts.add(policy.maxElements());
        facts.add(policy.deadline());
        facts.add(policy.maxChangeDetails());
        facts.add(policy.maxIssues());
        facts.add(policy.maxResultValueChars());
        facts.add(policy.maxPathEncodedChars());
        facts.add(policy.maxResultTotalChars());
        facts.add(policy.maxEntityKeyComponents());
        facts.add(policy.maxEntityKeyEncodedBytes());
        facts.add(policy.maxRegisteredExtensions());
        facts.add(policy.maxPathRules());
        facts.add(policy.maxPatternSegments());
        facts.add(policy.maxPatternTokenChars());
        facts.add(policy.maxPatternTotalChars());
        facts.add(policy.includePathPatterns().size());
        facts.add(policy.excludePathPatterns().size());
        facts.add(policy.maxTrackingTargets());
        facts.add(policy.maxTrackingNameChars());
        facts.add(policy.numericAbsoluteTolerance());
        facts.add(policy.numericRelativeTolerance());
        facts.add(policy.temporalTolerance());
        return List.copyOf(facts);
    }

    private record BoundaryCase(
            /** Canonical Spring property path. */ String property,
            /** Documented inclusive minimum. */ String minimum,
            /** Documented inclusive maximum. */ String maximum,
            /** Representative value below the minimum. */ String belowMinimum,
            /** Representative value above the maximum. */ String aboveMaximum,
            /** Properties needed to isolate the minimum from cross-field constraints. */
            List<String> minimumCompanions,
            /** Properties needed to isolate the maximum from cross-field constraints. */
            List<String> maximumCompanions) {

        private BoundaryCase {
            minimumCompanions = List.copyOf(minimumCompanions);
            maximumCompanions = List.copyOf(maximumCompanions);
        }

        String[] valuesFor(String value, List<String> companions) {
            return Stream.concat(
                            Stream.of(property + "=" + value),
                            companions.stream())
                    .toArray(String[]::new);
        }

        @Override
        public String toString() {
            return property;
        }
    }
}
