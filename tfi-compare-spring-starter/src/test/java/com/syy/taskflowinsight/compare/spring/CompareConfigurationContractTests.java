package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.CompareOperationsDecorator;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CompareConfigurationContractTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TfiCompareAutoConfiguration.class));

    @Test
    void canonicalPropertiesBuildTheContextPolicy() {
        contextRunner.withPropertyValues(
                        "tfi.compare.enabled=false",
                        "tfi.compare.compute-similarity=true",
                        "tfi.compare.include-collection-contents=false",
                        "tfi.compare.max-depth=3",
                        "tfi.compare.max-compared-nodes=200",
                        "tfi.compare.max-elements=50",
                        "tfi.compare.deadline=250ms",
                        "tfi.compare.max-change-details=20",
                        "tfi.compare.numeric-absolute-tolerance=0.01",
                        "tfi.compare.numeric-relative-tolerance=0.1",
                        "tfi.compare.temporal-tolerance=2s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ComparePolicy policy = context.getBean(ComparePolicy.class);
                    assertThat(policy.enabled()).isFalse();
                    assertThat(policy.computeSimilarity()).isTrue();
                    assertThat(policy.includeCollectionContents()).isFalse();
                    assertThat(policy.maxDepth()).isEqualTo(3);
                    assertThat(policy.maxComparedNodes()).isEqualTo(200);
                    assertThat(policy.maxElements()).isEqualTo(50);
                    assertThat(policy.deadline()).isEqualTo(Duration.ofMillis(250));
                    assertThat(policy.maxChangeDetails()).isEqualTo(20);
                    assertThat(policy.numericAbsoluteTolerance()).isEqualByComparingTo(new BigDecimal("0.01"));
                    assertThat(policy.numericRelativeTolerance()).isEqualTo(0.1);
                    assertThat(policy.temporalTolerance()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    void customPolicyOwnsTheRuntimeWithoutPropertyMerge() {
        contextRunner.withUserConfiguration(CustomPolicyConfiguration.class)
                .withPropertyValues("tfi.compare.max-depth=3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CompareRuntime.class).policy().maxDepth()).isEqualTo(7);
                    assertThat(context).hasSingleBean(ComparePolicy.class);
                });
    }

    @Test
    void customRuntimeCompletelyOwnsPolicyAndEngine() {
        contextRunner.withUserConfiguration(CustomRuntimeConfiguration.class)
                .withPropertyValues("tfi.compare.max-depth=3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CompareRuntime runtime = context.getBean(CompareRuntime.class);
                    assertThat(runtime.policy().maxDepth()).isEqualTo(8);
                    assertThat(context.getBeansOfType(ComparePolicy.class)).isEmpty();
                    assertThat(context.getBean(CompareEngine.class)).isSameAs(runtime.engine());
                });
    }

    @Test
    void policyAndRuntimeCombinationFailsStartup() {
        contextRunner.withUserConfiguration(CustomPolicyAndRuntimeConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void customOperationsCannotCreateAThirdExecutionGraph() {
        contextRunner.withUserConfiguration(CustomOperationsConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void customOperationsCannotImpersonateReservedOpsBeanName() {
        contextRunner.withUserConfiguration(ObservedOperationsConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void typedDecoratorDoesNotRequireAnOpsClassOrReservedBeanName() {
        contextRunner.withUserConfiguration(TypedDecoratorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CompareEngine engine = context.getBean(CompareEngine.class);
                    CompareOperations selected = context.getBean(CompareOperations.class);
                    assertThat(selected).isSameAs(context.getBean("metricsDecorator"));
                    assertThat(selected).isInstanceOf(CompareOperationsDecorator.class);
                    assertThat(((CompareOperationsDecorator) selected).delegate()).isSameAs(engine);
                });
    }

    @Test
    void includeSensitiveMaskingPolicyFailsStartup() {
        contextRunner.withUserConfiguration(UnsafeMaskingConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void multipleMaskingPoliciesFailStartup() {
        contextRunner.withUserConfiguration(MultipleMaskingConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void oneLegacyAliasIsTypedAndWarnedOnce(CapturedOutput output) {
        contextRunner.withPropertyValues("tfi.change-tracking.snapshot.max-depth=4")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ComparePolicy.class).maxDepth()).isEqualTo(4);
                });

        assertThat(output).containsOnlyOnce(
                "Deprecated TFI Compare configuration key 'tfi.change-tracking.snapshot.max-depth'");
    }

    @Test
    void canonicalAndEqualAliasUseCanonicalAndWarnOnce(CapturedOutput output) {
        contextRunner.withPropertyValues(
                        "tfi.compare.max-depth=4",
                        "tfi.change-tracking.snapshot.max-depth=4")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ComparePolicy.class).maxDepth()).isEqualTo(4);
                });

        assertThat(output).containsOnlyOnce(
                "Deprecated TFI Compare configuration key 'tfi.change-tracking.snapshot.max-depth'");
    }

    @Test
    void canonicalAndDifferentAliasFailStartup() {
        contextRunner.withPropertyValues(
                        "tfi.compare.max-depth=3",
                        "tfi.change-tracking.snapshot.max-depth=4")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void retiredGlobalKeyDoesNotAffectCompareOrWarn(CapturedOutput output) {
        contextRunner.withPropertyValues("tfi.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ComparePolicy.class).enabled()).isTrue();
                });

        assertThat(output).doesNotContain(
                "Deprecated TFI Compare configuration key 'tfi.enabled'");
    }

    @Test
    void retiredGlobalKeyCannotOverrideCanonicalOrSingleAlias() {
        contextRunner.withPropertyValues(
                        "tfi.enabled=false",
                        "tfi.compare.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ComparePolicy.class).enabled()).isTrue();
                });

        contextRunner.withPropertyValues(
                        "tfi.enabled=true",
                        "tfi.compare.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ComparePolicy.class).enabled()).isFalse();
                });

        contextRunner.withPropertyValues(
                        "tfi.enabled=true",
                        "tfi.change-tracking.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ComparePolicy.class).enabled()).isFalse();
                });
    }

    @Test
    void canonicalAndEqualEnableAliasUseCanonicalAndWarnOnce(CapturedOutput output) {
        contextRunner.withPropertyValues(
                        "tfi.compare.enabled=true",
                        "tfi.change-tracking.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ComparePolicy.class).enabled()).isTrue();
                });

        assertThat(output).containsOnlyOnce(
                "Deprecated TFI Compare configuration key 'tfi.change-tracking.enabled'");
    }

    @Test
    void aliasConversionFailureFailsStartup() {
        contextRunner.withPropertyValues("tfi.change-tracking.snapshot.max-depth=not-a-number")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomPolicyConfiguration {
        @Bean
        ComparePolicy customPolicy() {
            return ComparePolicy.builder().maxDepth(7).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRuntimeConfiguration {
        @Bean
        CompareRuntime customRuntime() {
            return CompareRuntime.builder().policy(ComparePolicy.builder().maxDepth(8).build()).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomPolicyAndRuntimeConfiguration {
        @Bean
        ComparePolicy customPolicy() {
            return ComparePolicy.builder().maxDepth(7).build();
        }

        @Bean
        CompareRuntime customRuntime() {
            return CompareRuntime.builder().policy(ComparePolicy.builder().maxDepth(8).build()).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomOperationsConfiguration {
        @Bean
        CompareOperations customOperations(CompareRuntime runtime) {
            return runtime.engine();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ObservedOperationsConfiguration {
        @Bean(name = "observedCompareOperations")
        @Primary
        CompareOperations observedCompareOperations(CompareRuntime runtime) {
            return new CompareOperations() {
                @Override
                public CompareResult compare(Object before, Object after) {
                    return runtime.engine().compare(before, after);
                }

                @Override
                public CompareResult compare(Object before, Object after, CompareOptions options) {
                    return runtime.engine().compare(before, after, options);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TypedDecoratorConfiguration {
        @Bean
        @Primary
        CompareOperationsDecorator metricsDecorator(CompareRuntime runtime) {
            CompareOperations delegate = runtime.engine();
            return new CompareOperationsDecorator() {
                @Override
                public CompareOperations delegate() {
                    return delegate;
                }

                @Override
                public CompareResult compare(Object before, Object after) {
                    return delegate.compare(before, after);
                }

                @Override
                public CompareResult compare(Object before, Object after, CompareOptions options) {
                    return delegate.compare(before, after, options);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnsafeMaskingConfiguration {
        @Bean
        MaskingPolicy maskingPolicy() {
            return MaskingPolicy.explicitlyIncludeSensitiveValues();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleMaskingConfiguration {
        @Bean
        MaskingPolicy firstMaskingPolicy() {
            return MaskingPolicy.safeDefaults();
        }

        @Bean
        MaskingPolicy secondMaskingPolicy() {
            return MaskingPolicy.safeDefaultsWithAdditionalRules(List.of("PROPERTY:customerSecret"));
        }
    }
}
