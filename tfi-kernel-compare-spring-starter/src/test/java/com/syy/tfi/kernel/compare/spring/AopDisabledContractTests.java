package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.compare.KernelCompareRecorder;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;
import com.syy.tfi.kernel.spi.KernelClock;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AopDisabledContractTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    TfiKernelCompareArtifactGuardAutoConfiguration.class,
                    TfiKernelRuntimeAutoConfiguration.class,
                    TfiCompareCoreAutoConfiguration.class,
                    TfiKernelCompareAutoConfiguration.class,
                    TfiKernelCompareAopAutoConfiguration.class))
            .withUserConfiguration(InvalidTrackedFixtureConfiguration.class);

    @Test
    void missingAopPropertyCreatesNoAdvisorOrMethodPlanScan() {
        contextRunner.run(this::assertAopDisabled);
    }

    @Test
    void explicitFalseAopPropertyCreatesNoAdvisorOrTrackingWork() {
        contextRunner
                .withPropertyValues("tfi.kernel-compare.aop.enabled=false")
                .run(this::assertAopDisabled);
    }

    @Test
    void disabledIntegrationKeepsTheAopPathAbsent() {
        contextRunner
                .withPropertyValues(
                        "tfi.kernel-compare.enabled=false",
                        "tfi.kernel-compare.aop.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(Advisor.class)).isEmpty();
                    assertThat(context).doesNotHaveBean(KernelCompareRecorder.class);
                });
    }

    private void assertAopDisabled(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context.getBeansOfType(Advisor.class)).isEmpty();
        assertThat(context).doesNotHaveBean("tfiKernelCompareAdvisor");
        InvalidTrackedService service = context.getBean(InvalidTrackedService.class);
        CountingClock clock = context.getBean(CountingClock.class);
        Object expected = new Object();

        assertThat(service.execute(expected)).isSameAs(expected);

        assertThat(service.actionCalls).hasValue(1);
        assertThat(clock.calls).hasValue(0);
    }

    static final class InvalidTrackedService {

        /** AOP disabled 时仍应只由业务方法增加的 action 次数。 */
        private final AtomicInteger actionCalls = new AtomicInteger();

        @TfiTracked(operation = "INVALID_OPERATION")
        public Object execute(@TfiTrackTarget("invalid.target") Object target) {
            actionCalls.incrementAndGet();
            return target;
        }
    }

    static final class CountingClock implements KernelClock {

        /** Kernel begin/record/close 对时钟的总访问次数。 */
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public long wallTimeMillis() {
            calls.incrementAndGet();
            return 0L;
        }

        @Override
        public long monotonicNanos() {
            calls.incrementAndGet();
            return 0L;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidTrackedFixtureConfiguration {

        @Bean
        InvalidTrackedService invalidTrackedService() {
            return new InvalidTrackedService();
        }

        @Bean
        CountingClock countingClock() {
            return new CountingClock();
        }
    }
}
