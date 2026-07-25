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
import com.syy.tfi.kernel.spi.KernelClock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ProgrammaticCompositionContextTests {

    /** D1 只登记三组程序化配置；生命周期 Guard 与 AOP 由后续卡片追加。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TfiKernelRuntimeAutoConfiguration.class,
                    TfiCompareCoreAutoConfiguration.class,
                    TfiKernelCompareAutoConfiguration.class));

    @Test
    void defaultContextPublishesOneIdentityClosedProgrammaticGraph() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KernelConfig.class);
            assertThat(context).hasSingleBean(KernelRuntime.class);
            assertThat(context).hasSingleBean(ComparePolicy.class);
            assertThat(context).hasSingleBean(CompareRuntime.class);
            assertThat(context).hasSingleBean(CompareEngine.class);
            assertThat(context).hasSingleBean(CompareOperations.class);
            assertThat(context).hasSingleBean(TrackingExecutor.class);
            assertThat(context).hasSingleBean(CompareProjectionFactory.class);
            assertThat(context).hasSingleBean(MaskingPolicy.class);
            assertThat(context).hasSingleBean(KernelCompareRecordPolicy.class);
            assertThat(context).hasSingleBean(KernelCompareRecorder.class);

            assertThat(context).hasBean("tfiKernelConfig");
            assertThat(context).hasBean("tfiKernelRuntime");
            assertThat(context).hasBean("tfiComparePolicy");
            assertThat(context).hasBean("tfiCompareRuntime");
            assertThat(context).hasBean("tfiCompareEngine");
            assertThat(context).hasBean("tfiTrackingExecutor");
            assertThat(context).hasBean("tfiCompareProjectionFactory");
            assertThat(context).hasBean("tfiMaskingPolicy");
            assertThat(context).hasBean("tfiKernelCompareRecordPolicy");
            assertThat(context).hasBean("tfiKernelCompareRecorder");

            CompareRuntime runtime = context.getBean(CompareRuntime.class);
            assertThat(context.getBean(ComparePolicy.class)).isSameAs(runtime.policy());
            assertThat(context.getBean(CompareEngine.class)).isSameAs(runtime.engine());
            assertThat(context.getBean(CompareOperations.class)).isSameAs(runtime.engine());
            assertThat(context).hasSingleBean(KernelRuntimeRetirement.class);
            assertThat(context).hasBean("tfiKernelRuntimeRetirement");
            assertThat(context.getBeansOfType(Advisor.class)).isEmpty();
        });
    }

    @Test
    void disabledIntegrationKeepsBothCoreGraphsWithoutBridgeOrAop() {
        contextRunner
                .withPropertyValues("tfi.kernel-compare.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(KernelRuntime.class);
                    assertThat(context).hasSingleBean(CompareRuntime.class);
                    assertThat(context).hasSingleBean(CompareEngine.class);
                    assertThat(context).doesNotHaveBean(KernelCompareRecordPolicy.class);
                    assertThat(context).doesNotHaveBean(KernelCompareRecorder.class);
                    assertThat(context.getBeansOfType(Advisor.class)).isEmpty();
                });
    }

    @Test
    void customClockAlsoOwnsTheDefaultUlidGenerator() {
        contextRunner
                .withUserConfiguration(CustomClockConfiguration.class)
                .run(context -> {
                    KernelConfig config = context.getBean(KernelConfig.class);
                    KernelClock clock = context.getBean(KernelClock.class);
                    assertThat(config.clock()).isSameAs(clock);
                    assertThat(config.idGenerator().nextId()).startsWith("0000000000");
                });
    }

    @Test
    void missingKernelCoreClassBacksOffTheWholeComposition() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(KernelRuntime.class))
                .run(context -> assertNoCompositionBeans(context));
    }

    @Test
    void missingCompareCoreClassBacksOffTheWholeComposition() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(CompareRuntime.class))
                .run(context -> assertNoCompositionBeans(context));
    }

    private static void assertNoCompositionBeans(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean("tfiKernelConfig");
        assertThat(context).doesNotHaveBean("tfiKernelRuntime");
        assertThat(context).doesNotHaveBean("tfiComparePolicy");
        assertThat(context).doesNotHaveBean("tfiCompareRuntime");
        assertThat(context).doesNotHaveBean("tfiCompareEngine");
        assertThat(context).doesNotHaveBean("tfiTrackingExecutor");
        assertThat(context).doesNotHaveBean("tfiCompareProjectionFactory");
        assertThat(context).doesNotHaveBean("tfiMaskingPolicy");
        assertThat(context).doesNotHaveBean("tfiKernelCompareRecordPolicy");
        assertThat(context).doesNotHaveBean("tfiKernelCompareRecorder");
        assertThat(context).doesNotHaveBean("tfiKernelCompareCompositionValidator");
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomClockConfiguration {

        @Bean
        KernelClock customKernelClock() {
            return new KernelClock() {
                @Override
                public long wallTimeMillis() {
                    return 0L;
                }

                @Override
                public long monotonicNanos() {
                    return 0L;
                }
            };
        }
    }
}
