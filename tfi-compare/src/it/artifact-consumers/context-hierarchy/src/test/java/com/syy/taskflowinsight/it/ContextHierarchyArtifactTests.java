package com.syy.taskflowinsight.it;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration;
import com.syy.taskflowinsight.ops.compare.ObservedCompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 父子 Context 只使用本层 Compare/Ops graph 的 retained artifact 合同。
 *
 * <p>Boot 的 composite 与 registry closer 都会遍历父层 Registry；本夹具排除二者并显式提供本层 Registry，
 * 以便只验证 TFI 对象图、meter 与关闭生命周期的归属边界。</p>
 */
class ContextHierarchyArtifactTests {

    @Test
    void parentAndChildGraphsRemainLocalAcrossBothCloseOrders() throws Exception {
        assertCloseOrder(true);
        assertCloseOrder(false);
        ArtifactCodeSourceEvidence.write(List.of(
                TfiCompareAutoConfiguration.class,
                CompareEngine.class,
                ObservedCompareOperations.class));
    }

    private static void assertCloseOrder(boolean closeParentFirst) {
        AnnotationConfigApplicationContext parent = openParent();
        AnnotationConfigApplicationContext child = openChild(parent);
        try {
            CompareRuntime parentRuntime = localSingle(parent, CompareRuntime.class);
            CompareRuntime childRuntime = localSingle(child, CompareRuntime.class);
            CompareEngine parentEngine = localSingle(parent, CompareEngine.class);
            CompareEngine childEngine = localSingle(child, CompareEngine.class);
            ObservedCompareOperations parentObserved =
                    localSingle(parent, ObservedCompareOperations.class);
            ObservedCompareOperations childObserved =
                    localSingle(child, ObservedCompareOperations.class);
            MeterRegistry parentRegistry = localSingle(parent, MeterRegistry.class);
            MeterRegistry childRegistry = localSingle(child, MeterRegistry.class);

            assertThat(parent.getBeanFactory().containsLocalBean("parentRuntimeAuthority")).isTrue();
            assertThat(parentRuntime.policy().maxDepth()).isEqualTo(3);
            assertThat(childRuntime.policy().maxDepth()).isEqualTo(7);
            assertThat(childRuntime).isNotSameAs(parentRuntime);
            assertThat(parentObserved.delegate()).isSameAs(parentEngine);
            assertThat(childObserved.delegate()).isSameAs(childEngine);
            assertThat(parent.getBean(CompareOperations.class)).isSameAs(parentObserved);
            assertThat(child.getBean(CompareOperations.class)).isSameAs(childObserved);

            childObserved.compare("before", "after");
            assertThat(requestCount(childRegistry)).isEqualTo(1.0);
            assertThat(requestCount(parentRegistry)).isZero();

            AnnotationConfigApplicationContext closed = closeParentFirst ? parent : child;
            AnnotationConfigApplicationContext survivor = closeParentFirst ? child : parent;
            MeterRegistry survivorRegistry = closeParentFirst ? childRegistry : parentRegistry;
            closed.close();
            survivor.getBean(CompareOperations.class).compare("left", "right");
            assertThat(requestCount(survivorRegistry)).isPositive();
        } finally {
            if (child.isActive()) {
                child.close();
            }
            if (parent.isActive()) {
                parent.close();
            }
        }
    }

    private static AnnotationConfigApplicationContext openParent() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(ParentApplication.class);
        context.refresh();
        return context;
    }

    private static AnnotationConfigApplicationContext openChild(
            AnnotationConfigApplicationContext parent) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setParent(parent);
        TestPropertyValues.of("tfi.compare.max-depth=7").applyTo(context);
        context.register(ChildApplication.class);
        context.refresh();
        return context;
    }

    private static <T> T localSingle(AnnotationConfigApplicationContext context, Class<T> type) {
        String[] names = context.getBeanFactory().getBeanNamesForType(type, true, false);
        assertThat(names).as(type.getName()).hasSize(1);
        assertThat(context.getBeanFactory().containsLocalBean(names[0])).isTrue();
        return context.getBeanFactory().getBean(names[0], type);
    }

    private static double requestCount(MeterRegistry registry) {
        var counter = registry.find("tfi.compare.request").counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            CompositeMeterRegistryAutoConfiguration.class,
            MetricsAutoConfiguration.class
    })
    static class ParentApplication {
        @Bean
        @Primary
        CompareRuntime parentRuntimeAuthority() {
            return CompareRuntime.builder()
                    .policy(ComparePolicy.builder().maxDepth(3).build())
                    .build();
        }

        @Bean
        MeterRegistry parentMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            CompositeMeterRegistryAutoConfiguration.class,
            MetricsAutoConfiguration.class
    })
    static class ChildApplication {

        @Bean
        @Primary
        MeterRegistry childMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
