package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.TfiListDiffFacade;
import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CompareContextIsolationTests {

    @Test
    void parentAndChildDefaultGraphsRemainLocal() {
        ComparisonProvider staticProvider = ProviderRegistry.resolve(ComparisonProvider.class);
        long generation = ProviderRegistry.getGeneration();

        AnnotationConfigApplicationContext parent = openContext(3);
        AnnotationConfigApplicationContext child = openChildContext(parent, 7);
        try {
            CompareRuntime parentRuntime = assertLocalGraph(parent, 3);
            CompareRuntime childRuntime = assertLocalGraph(child, 7);

            assertThat(childRuntime).isNotSameAs(parentRuntime);
            assertThat(localBean(child, ComparePolicy.class))
                    .isNotSameAs(localBean(parent, ComparePolicy.class));
            assertThat(localBean(child, CompareEngine.class))
                    .isNotSameAs(localBean(parent, CompareEngine.class));
            assertThat(localBean(child, MaskingPolicy.class))
                    .isNotSameAs(localBean(parent, MaskingPolicy.class));
            assertThat(ProviderRegistry.resolve(ComparisonProvider.class)).isSameAs(staticProvider);
            assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation);
        } finally {
            child.close();
            parent.close();
        }
    }

    @Test
    void differentNamedCustomRuntimesRemainLocal() {
        AnnotationConfigApplicationContext parent = openContext(
                ParentCustomRuntimeConfiguration.class,
                TfiCompareAutoConfiguration.class);
        AnnotationConfigApplicationContext child = openChildContext(
                parent,
                ChildCustomRuntimeConfiguration.class,
                TfiCompareAutoConfiguration.class);
        try {
            CompareRuntime parentRuntime = localBean(parent, CompareRuntime.class);
            CompareRuntime childRuntime = localBean(child, CompareRuntime.class);

            assertThat(parent.getBeanFactory().containsLocalBean("parentRuntime")).isTrue();
            assertThat(child.getBeanFactory().containsLocalBean("childRuntime")).isTrue();
            assertThat(parentRuntime.policy().maxDepth()).isEqualTo(3);
            assertThat(childRuntime.policy().maxDepth()).isEqualTo(7);
            assertThat(childRuntime).isNotSameAs(parentRuntime);
            assertThat(localBean(parent, CompareEngine.class)).isSameAs(parentRuntime.engine());
            assertThat(localBean(child, CompareEngine.class)).isSameAs(childRuntime.engine());
        } finally {
            child.close();
            parent.close();
        }
    }

    @Test
    void closingEitherContextDoesNotAffectTheOther() {
        assertOtherContextSurvivesClosure(true);
        assertOtherContextSurvivesClosure(false);
    }

    @Test
    void parallelContextsBuildDistinctRuntimeGraphs() throws Exception {
        CompletableFuture<AnnotationConfigApplicationContext> firstFuture =
                CompletableFuture.supplyAsync(() -> openContext(4));
        CompletableFuture<AnnotationConfigApplicationContext> secondFuture =
                CompletableFuture.supplyAsync(() -> openContext(6));
        AnnotationConfigApplicationContext first = firstFuture.get(10, TimeUnit.SECONDS);
        AnnotationConfigApplicationContext second = secondFuture.get(10, TimeUnit.SECONDS);
        try {
            CompareRuntime firstRuntime = first.getBean(CompareRuntime.class);
            CompareRuntime secondRuntime = second.getBean(CompareRuntime.class);
            assertThat(firstRuntime).isNotSameAs(secondRuntime);
            assertThat(firstRuntime.policy().maxDepth()).isEqualTo(4);
            assertThat(secondRuntime.policy().maxDepth()).isEqualTo(6);
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void differentNamedPrimaryParentBeansCannotEnterChildGraph() {
        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(
                PrimaryParentGraphConfiguration.class);
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        TestPropertyValues.of("tfi.compare.max-depth=7").applyTo(child);
        child.register(TfiCompareAutoConfiguration.class);
        try {
            child.refresh();

            ComparePolicy childPolicy = localBean(child, ComparePolicy.class);
            CompareRuntime childRuntime = localBean(child, CompareRuntime.class);
            CompareEngine childEngine = localBean(child, CompareEngine.class);
            CompareOperations childOperations = localBean(child, CompareOperations.class);
            MaskingPolicy childMasking = localBean(child, MaskingPolicy.class);
            assertThat(localBean(child, TfiListDiffFacade.class)).isNotNull();

            assertThat(childPolicy.maxDepth()).isEqualTo(7);
            assertThat(childRuntime.policy()).isSameAs(childPolicy);
            assertThat(childRuntime.engine()).isSameAs(childEngine);
            assertThat(childOperations).isSameAs(childEngine);
            assertThat(childPolicy).isNotSameAs(parent.getBean("parentPolicy"));
            assertThat(childRuntime).isNotSameAs(parent.getBean("parentRuntime"));
            assertThat(childOperations).isNotSameAs(parent.getBean("parentOperations"));
            assertThat(childMasking).isNotSameAs(parent.getBean("parentMaskingPolicy"));
        } finally {
            child.close();
            parent.close();
        }
    }

    private static <T> T localBean(AnnotationConfigApplicationContext context, Class<T> type) {
        String[] names = context.getBeanFactory().getBeanNamesForType(type, true, false);
        assertThat(names).as("local bean for %s", type.getName()).hasSize(1);
        assertThat(context.getBeanFactory().containsLocalBean(names[0])).isTrue();
        return context.getBeanFactory().getBean(names[0], type);
    }

    private static CompareRuntime assertLocalGraph(
            AnnotationConfigApplicationContext context,
            int maxDepth) {
        ComparePolicy policy = localBean(context, ComparePolicy.class);
        CompareRuntime runtime = localBean(context, CompareRuntime.class);
        CompareEngine engine = localBean(context, CompareEngine.class);
        assertThat(runtime.policy()).isSameAs(policy);
        assertThat(runtime.engine()).isSameAs(engine);
        assertThat(localBean(context, CompareOperations.class)).isSameAs(engine);
        assertThat(localBean(context, MaskingPolicy.class)).isNotNull();
        assertThat(localBean(context, TfiListDiffFacade.class)).isNotNull();
        assertThat(policy.maxDepth()).isEqualTo(maxDepth);
        return runtime;
    }

    private static void assertOtherContextSurvivesClosure(boolean closeParentFirst) {
        ComparisonProvider staticProvider = ProviderRegistry.resolve(ComparisonProvider.class);
        long generation = ProviderRegistry.getGeneration();
        AnnotationConfigApplicationContext parent = openContext(3);
        AnnotationConfigApplicationContext child = openChildContext(parent, 7);
        try {
            AnnotationConfigApplicationContext closed = closeParentFirst ? parent : child;
            AnnotationConfigApplicationContext survivor = closeParentFirst ? child : parent;
            CompareRuntime survivorRuntime = localBean(survivor, CompareRuntime.class);

            closed.close();

            assertThat(localBean(survivor, CompareRuntime.class)).isSameAs(survivorRuntime);
            assertThat(survivorRuntime.engine().compare("same", "same").getOutcome())
                    .isEqualTo(CompareOutcome.EQUAL);
            assertThat(ProviderRegistry.resolve(ComparisonProvider.class)).isSameAs(staticProvider);
            assertThat(ProviderRegistry.getGeneration()).isEqualTo(generation);
        } finally {
            if (child.isActive()) {
                child.close();
            }
            if (parent.isActive()) {
                parent.close();
            }
        }
    }

    private static AnnotationConfigApplicationContext openContext(int maxDepth) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of("tfi.compare.max-depth=" + maxDepth).applyTo(context);
        context.register(TfiCompareAutoConfiguration.class);
        context.refresh();
        return context;
    }

    @SafeVarargs
    private static AnnotationConfigApplicationContext openContext(
            Class<?>... configurations) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(configurations);
        context.refresh();
        return context;
    }

    private static AnnotationConfigApplicationContext openChildContext(
            AnnotationConfigApplicationContext parent,
            int maxDepth) {
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        TestPropertyValues.of("tfi.compare.max-depth=" + maxDepth).applyTo(child);
        child.register(TfiCompareAutoConfiguration.class);
        child.refresh();
        return child;
    }

    @SafeVarargs
    private static AnnotationConfigApplicationContext openChildContext(
            AnnotationConfigApplicationContext parent,
            Class<?>... configurations) {
        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        child.register(configurations);
        child.refresh();
        return child;
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryParentGraphConfiguration {

        @Bean
        @Primary
        ComparePolicy parentPolicy() {
            return ComparePolicy.builder().maxDepth(1).build();
        }

        @Bean
        @Primary
        CompareRuntime parentRuntime(ComparePolicy parentPolicy) {
            return CompareRuntime.builder().policy(parentPolicy).build();
        }

        @Bean
        CompareEngine parentEngine(CompareRuntime parentRuntime) {
            return parentRuntime.engine();
        }

        @Bean
        @Primary
        CompareOperations parentOperations(CompareRuntime parentRuntime) {
            return parentRuntime.engine();
        }

        @Bean
        @Primary
        MaskingPolicy parentMaskingPolicy() {
            return MaskingPolicy.safeDefaults();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ParentCustomRuntimeConfiguration {
        @Bean
        CompareRuntime parentRuntime() {
            return CompareRuntime.builder()
                    .policy(ComparePolicy.builder().maxDepth(3).build())
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ChildCustomRuntimeConfiguration {
        @Bean
        CompareRuntime childRuntime() {
            return CompareRuntime.builder()
                    .policy(ComparePolicy.builder().maxDepth(7).build())
                    .build();
        }
    }
}
