package com.syy.tfi.kernel.compare.spring;

import com.syy.tfi.kernel.compare.spring.annotation.TfiTrackTarget;
import com.syy.tfi.kernel.compare.spring.annotation.TfiTracked;
import com.syy.tfi.kernel.spi.KernelClock;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AopProxyModeContractTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    TfiKernelCompareArtifactGuardAutoConfiguration.class,
                    TfiKernelRuntimeAutoConfiguration.class,
                    TfiCompareCoreAutoConfiguration.class,
                    TfiKernelCompareAutoConfiguration.class,
                    TfiKernelCompareAopAutoConfiguration.class))
            .withPropertyValues("tfi.kernel-compare.aop.enabled=true");

    @Test
    void jdkAndClassProxiesShareInterfaceImplementationAndBridgeSemantics() {
        assertProxyMode(false);
        assertProxyMode(true);
    }

    @Test
    void inconsistentInterfaceAndImplementationDeclarationsFailInBothProxyModes() {
        assertMismatchFails(false);
        assertMismatchFails(true);
    }

    private void assertProxyMode(boolean classProxy) {
        contextRunner
                .withPropertyValues("spring.aop.proxy-target-class=" + classProxy)
                .withUserConfiguration(ProxyFixtureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TrackedService service = context.getBean(TrackedService.class);
                    GenericTrackedService<String> generic = genericTrackedService(context);
                    AtomicInteger actions = context.getBean("aopActionCalls", AtomicInteger.class);
                    CountingClock clock = context.getBean(CountingClock.class);
                    assertThat(AopUtils.isCglibProxy(service)).isEqualTo(classProxy);
                    assertThat(AopUtils.isJdkDynamicProxy(service)).isEqualTo(!classProxy);

                    Object interfaceInput = new Object();
                    Object implementationInput = new Object();
                    Object consistentInput = new Object();
                    assertThat(service.interfaceDeclared(interfaceInput)).isSameAs(interfaceInput);
                    assertThat(service.implementationDeclared(implementationInput))
                            .isSameAs(implementationInput);
                    assertThat(service.consistentlyDeclared(consistentInput)).isSameAs(consistentInput);
                    assertThat(generic.bridgeDeclared("bridge-value")).isSameAs("bridge-value");
                    assertThat(actions).hasValue(4);

                    clock.reset();
                    assertThatThrownBy(() -> service.interfaceDeclared(null))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("KCS_E_1102")
                            .hasMessageContaining("parameterIndex=0")
                            .hasMessageNotContaining("interface.operation")
                            .hasMessageNotContaining("interface_target");
                    assertThat(actions).hasValue(4);
                    assertThat(clock.calls).hasValue(0);
                });
    }

    /** Spring 代理运行时擦除泛型，unchecked cast 只局限在测试 fixture 的取 Bean 边界。 */
    @SuppressWarnings("unchecked")
    private static GenericTrackedService<String> genericTrackedService(
            ApplicationContext context) {
        return (GenericTrackedService<String>) context.getBean(GenericTrackedService.class);
    }

    private void assertMismatchFails(boolean classProxy) {
        contextRunner
                .withPropertyValues("spring.aop.proxy-target-class=" + classProxy)
                .withUserConfiguration(MismatchFixtureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("KCS_E_1102");
                    assertThat(stackTrace(context.getStartupFailure()))
                            .doesNotContain("interface_secret", "implementation_secret");
                });
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    interface TrackedService {

        @TfiTracked(operation = "interface.operation")
        Object interfaceDeclared(@TfiTrackTarget("interface_target") Object target);

        Object implementationDeclared(Object target);

        @TfiTracked(operation = "consistent.operation")
        Object consistentlyDeclared(@TfiTrackTarget("consistent_target") Object target);
    }

    static class TrackedServiceImpl implements TrackedService {

        /** 所有被代理业务 action 共用的调用计数。 */
        private final AtomicInteger actions;

        TrackedServiceImpl(AtomicInteger actions) {
            this.actions = actions;
        }

        @Override
        public Object interfaceDeclared(Object target) {
            actions.incrementAndGet();
            return target;
        }

        @Override
        @TfiTracked(operation = "implementation.operation")
        public Object implementationDeclared(
                @TfiTrackTarget("implementation_target") Object target) {
            actions.incrementAndGet();
            return target;
        }

        @Override
        @TfiTracked(operation = "consistent.operation")
        public Object consistentlyDeclared(@TfiTrackTarget("consistent_target") Object target) {
            actions.incrementAndGet();
            return target;
        }
    }

    interface GenericTrackedService<T> {

        @TfiTracked(operation = "bridge.operation")
        T bridgeDeclared(@TfiTrackTarget("bridge_target") T target);
    }

    static class StringGenericTrackedService implements GenericTrackedService<String> {

        /** bridge method 路径的业务 action 调用计数。 */
        private final AtomicInteger actions;

        StringGenericTrackedService(AtomicInteger actions) {
            this.actions = actions;
        }

        @Override
        public String bridgeDeclared(String target) {
            actions.incrementAndGet();
            return target;
        }
    }

    interface MismatchedService {

        @TfiTracked(operation = "interface_secret")
        Object execute(@TfiTrackTarget("target") Object target);
    }

    static class MismatchedServiceImpl implements MismatchedService {

        @Override
        @TfiTracked(operation = "implementation_secret")
        public Object execute(@TfiTrackTarget("target") Object target) {
            return target;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProxyFixtureConfiguration {

        @Bean("aopActionCalls")
        AtomicInteger aopActionCalls() {
            return new AtomicInteger();
        }

        @Bean
        CountingClock countingClock() {
            return new CountingClock();
        }

        @Bean
        TrackedServiceImpl trackedService(AtomicInteger aopActionCalls) {
            return new TrackedServiceImpl(aopActionCalls);
        }

        @Bean
        StringGenericTrackedService genericTrackedService(AtomicInteger aopActionCalls) {
            return new StringGenericTrackedService(aopActionCalls);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MismatchFixtureConfiguration {

        @Bean
        MismatchedServiceImpl mismatchedService() {
            return new MismatchedServiceImpl();
        }
    }

    static final class CountingClock implements KernelClock {

        /** begin/id 路径读取时钟的总次数。 */
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

        void reset() {
            calls.set(0);
        }
    }
}
