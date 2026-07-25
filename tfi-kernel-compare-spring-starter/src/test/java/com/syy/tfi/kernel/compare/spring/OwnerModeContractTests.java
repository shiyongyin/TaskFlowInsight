package com.syy.tfi.kernel.compare.spring;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.compare.KernelCompareRecordPolicy;
import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import com.syy.tfi.kernel.spi.Sampler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerModeContractTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TfiKernelRuntimeAutoConfiguration.class,
                    TfiCompareCoreAutoConfiguration.class,
                    TfiKernelCompareAutoConfiguration.class));

    @Test
    void kDefaultCreatesConfigAndRuntime() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KernelConfig.class);
            assertThat(context).hasSingleBean(KernelRuntime.class);
        });
    }

    @Test
    void kConfigUsesTheApplicationConfig() {
        contextRunner.withUserConfiguration(CustomKernelConfig.class).run(context -> {
            assertThat(context).hasSingleBean(KernelConfig.class);
            assertThat(context).hasSingleBean(KernelRuntime.class);
            assertThat(context.getBean(KernelConfig.class))
                    .isSameAs(context.getBean("applicationKernelConfig"));
        });
    }

    @Test
    void kRuntimeBacksOffConfigAndUsesTheApplicationOwner() {
        contextRunner.withUserConfiguration(CustomKernelRuntime.class).run(context -> {
            assertThat(context).doesNotHaveBean(KernelConfig.class);
            assertThat(context).hasSingleBean(KernelRuntime.class);
            assertThat(context.getBean(KernelRuntime.class))
                    .isSameAs(context.getBean("applicationKernelRuntime"));
        });
    }

    @Test
    void cDefaultCreatesPolicyAndRuntime() {
        contextRunner.run(context -> {
            CompareRuntime runtime = context.getBean(CompareRuntime.class);
            assertThat(context.getBean(ComparePolicy.class)).isSameAs(runtime.policy());
            assertThat(context.getBean(CompareEngine.class)).isSameAs(runtime.engine());
        });
    }

    @Test
    void cPolicyUsesTheApplicationPolicy() {
        contextRunner.withUserConfiguration(CustomComparePolicy.class).run(context -> {
            CompareRuntime runtime = context.getBean(CompareRuntime.class);
            assertThat(context.getBean(ComparePolicy.class))
                    .isSameAs(context.getBean("applicationComparePolicy"));
            assertThat(runtime.policy()).isSameAs(context.getBean(ComparePolicy.class));
        });
    }

    @Test
    void cRuntimeBacksOffPolicyAndExportsItsEngine() {
        contextRunner.withUserConfiguration(CustomCompareRuntime.class).run(context -> {
            CompareRuntime runtime = context.getBean(CompareRuntime.class);
            assertThat(context).doesNotHaveBean(ComparePolicy.class);
            assertThat(runtime).isSameAs(context.getBean("applicationCompareRuntime"));
            assertThat(context.getBean(CompareEngine.class)).isSameAs(runtime.engine());
        });
    }

    @Test
    void kernelConfigAndRuntimeMixFailsWithCompositionCode() {
        assertCompositionFailure(MixedKernelOwners.class);
    }

    @Test
    void customKernelConfigAndSpiMixFailsWithCompositionCode() {
        assertCompositionFailure(CustomKernelConfigWithSampler.class);
    }

    @Test
    void customKernelRuntimeAndSpiMixFailsWithCompositionCode() {
        assertCompositionFailure(CustomKernelRuntimeWithSampler.class);
    }

    @Test
    void multipleKernelRuntimeOwnersFailWithCompositionCode() {
        assertCompositionFailure(MultipleKernelRuntimes.class);
    }

    @Test
    void comparePolicyAndRuntimeMixFailsWithCompositionCode() {
        assertCompositionFailure(MixedCompareOwners.class);
    }

    @Test
    void multipleComparePolicyOwnersFailWithCompositionCode() {
        assertCompositionFailure(MultipleComparePolicies.class);
    }

    @Test
    void sensitiveMaskingOwnerFailsWithCompositionCode() {
        assertCompositionFailure(SensitiveMaskingPolicy.class);
    }

    @Test
    void duplicateEngineFailsWithCompositionCode() {
        assertCompositionFailure(DuplicateEngine.class);
    }

    @Test
    void duplicateExecutorFailsWithCompositionCode() {
        assertCompositionFailure(DuplicateExecutor.class);
    }

    @Test
    void duplicateProjectionFactoryFailsWithCompositionCode() {
        assertCompositionFailure(DuplicateProjectionFactory.class);
    }

    @Test
    void duplicateRecorderFailsWithCompositionCode() {
        assertCompositionFailure(DuplicateRecorder.class);
    }

    private void assertCompositionFailure(Class<?> userConfiguration) {
        contextRunner.withUserConfiguration(userConfiguration).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("KCS_E_1002");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomKernelConfig {

        @Bean
        KernelConfig applicationKernelConfig() {
            return KernelConfig.defaults();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomKernelRuntime {

        @Bean
        KernelRuntime applicationKernelRuntime() {
            return KernelRuntime.create(KernelConfig.defaults());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomComparePolicy {

        @Bean
        ComparePolicy applicationComparePolicy() {
            return ComparePolicy.builder().maxDepth(7).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCompareRuntime {

        @Bean
        CompareRuntime applicationCompareRuntime() {
            return CompareRuntime.builder()
                    .policy(ComparePolicy.builder().maxDepth(8).build())
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MixedKernelOwners {

        @Bean
        KernelConfig applicationKernelConfig() {
            return KernelConfig.defaults();
        }

        @Bean
        KernelRuntime applicationKernelRuntime(KernelConfig applicationKernelConfig) {
            return KernelRuntime.create(applicationKernelConfig);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomKernelConfigWithSampler {

        @Bean
        KernelConfig applicationKernelConfig() {
            return KernelConfig.defaults();
        }

        @Bean
        Sampler applicationSampler() {
            return name -> true;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomKernelRuntimeWithSampler {

        @Bean
        KernelRuntime applicationKernelRuntime() {
            return KernelRuntime.create(KernelConfig.defaults());
        }

        @Bean
        Sampler applicationSampler() {
            return name -> true;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleKernelRuntimes {

        @Bean
        KernelRuntime firstKernelRuntime() {
            return KernelRuntime.create(KernelConfig.defaults());
        }

        @Bean
        KernelRuntime secondKernelRuntime() {
            return KernelRuntime.create(KernelConfig.defaults());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MixedCompareOwners {

        @Bean
        ComparePolicy applicationComparePolicy() {
            return ComparePolicy.defaults();
        }

        @Bean
        CompareRuntime applicationCompareRuntime(ComparePolicy applicationComparePolicy) {
            return CompareRuntime.builder().policy(applicationComparePolicy).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleComparePolicies {

        @Bean
        ComparePolicy firstComparePolicy() {
            return ComparePolicy.defaults();
        }

        @Bean
        ComparePolicy secondComparePolicy() {
            return ComparePolicy.builder().maxDepth(9).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SensitiveMaskingPolicy {

        @Bean
        MaskingPolicy applicationMaskingPolicy() {
            return MaskingPolicy.explicitlyIncludeSensitiveValues();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateEngine {

        @Bean
        CompareEngine applicationCompareEngine(CompareRuntime runtime) {
            return runtime.engine();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateExecutor {

        @Bean
        TrackingExecutor applicationTrackingExecutor(CompareRuntime runtime) {
            return new TrackingExecutor(runtime.engine()::beginTracking);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateProjectionFactory {

        @Bean
        CompareProjectionFactory applicationProjectionFactory() {
            return new CompareProjectionFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateRecorder {

        @Bean
        KernelCompareRecorder applicationRecorder(
                CompareOperations operations,
                CompareProjectionFactory projectionFactory,
                MaskingPolicy maskingPolicy,
                KernelCompareRecordPolicy recordPolicy) {
            return new KernelCompareRecorder(
                    operations,
                    projectionFactory,
                    maskingPolicy,
                    recordPolicy);
        }
    }
}
