package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class AopLazyPrototypeContractTests {

    /** 各 scope fixture 的构造次数，用于证明 refresh 不提前实例化 lazy/prototype。 */
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    TfiKernelCompareArtifactGuardAutoConfiguration.class,
                    TfiKernelRuntimeAutoConfiguration.class,
                    TfiCompareCoreAutoConfiguration.class,
                    TfiKernelCompareAutoConfiguration.class,
                    TfiKernelCompareAopAutoConfiguration.class))
            .withPropertyValues("tfi.kernel-compare.aop.enabled=true");

    @BeforeEach
    void resetConstructions() {
        CONSTRUCTIONS.set(0);
    }

    @Test
    void eagerSingletonInvalidMetadataPreventsContextReady() {
        contextRunner.withUserConfiguration(EagerInvalidConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("KCS_E_1102");
                    assertThat(stackTrace(context.getStartupFailure()))
                            .doesNotContain("INVALID_SECRET");
                    assertThat(CONSTRUCTIONS).hasValue(1);
                });
    }

    @Test
    void lazySingletonIsValidatedOnlyOnFirstBeanCreation() {
        contextRunner.withUserConfiguration(LazyInvalidConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(CONSTRUCTIONS).hasValue(0);
                    Throwable failure = catchThrowable(() -> context.getBean("lazyInvalidService"));
                    assertThat(failure).hasStackTraceContaining("KCS_E_1102");
                    assertThat(stackTrace(failure)).doesNotContain("INVALID_SECRET");
                    assertThat(CONSTRUCTIONS).hasValue(1);
                });
    }

    @Test
    void prototypeIsValidatedOnlyOnFirstBeanCreation() {
        contextRunner.withUserConfiguration(PrototypeInvalidConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(CONSTRUCTIONS).hasValue(0);
                    Throwable failure = catchThrowable(() -> context.getBean("prototypeInvalidService"));
                    assertThat(failure).hasStackTraceContaining("KCS_E_1102");
                    assertThat(stackTrace(failure)).doesNotContain("INVALID_SECRET");
                    assertThat(CONSTRUCTIONS).hasValue(1);
                });
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    static final class InvalidService {

        InvalidService() {
            CONSTRUCTIONS.incrementAndGet();
        }

        @TfiTracked(operation = "INVALID_SECRET")
        public Object execute(@TfiTrackTarget("target") Object target) {
            return target;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EagerInvalidConfiguration {

        @Bean
        InvalidService eagerInvalidService() {
            return new InvalidService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LazyInvalidConfiguration {

        @Bean
        @Lazy
        InvalidService lazyInvalidService() {
            return new InvalidService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrototypeInvalidConfiguration {

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        InvalidService prototypeInvalidService() {
            return new InvalidService();
        }
    }
}
