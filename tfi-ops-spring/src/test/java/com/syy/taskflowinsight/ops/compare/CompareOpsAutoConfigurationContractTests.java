package com.syy.taskflowinsight.ops.compare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.api.CompareOperationsDecorator;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class CompareOpsAutoConfigurationContractTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompareObservationAutoConfiguration.class));

    private final ApplicationContextRunner versionGuardRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LegacyCompareVersionGuardAutoConfiguration.class,
                    CompareObservationAutoConfiguration.class));

    @Test
    void should_order_after_compare_graph_and_composite_registry() {
        AutoConfiguration metadata = CompareObservationAutoConfiguration.class
                .getAnnotation(AutoConfiguration.class);

        assertThat(metadata.afterName()).containsExactly(
                "com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration");
    }

    @Test
    void should_back_off_when_compare_graph_is_absent() {
        contextRunner
                .withUserConfiguration(RegistryConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ObservedCompareOperations.class);
                    assertThat(context).doesNotHaveBean(CompareHealthIndicator.class);
                });
    }

    @Test
    void opsBacksOffWhenPrerequisitesExistOnlyInParent() {
        try (AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(
                RuntimeGraphConfiguration.class,
                RegistryConfiguration.class)) {
            contextRunner.withParent(parent)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getSourceApplicationContext().getBeanFactory()
                                .getBeanNamesForType(
                                        ObservedCompareOperations.class, true, false)).isEmpty();
                        assertThat(context.getSourceApplicationContext().getBeanFactory()
                                .getBeanNamesForType(
                                        CompareHealthIndicator.class, true, false)).isEmpty();
                    });
        }
    }

    @Test
    void opsUsesChildDependenciesDespitePrimaryParentBeans() {
        try (AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(
                PrimaryParentGraphConfiguration.class)) {
            contextRunner.withParent(parent)
                    .withUserConfiguration(RuntimeGraphConfiguration.class, RegistryConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        CompareRuntime childRuntime = localBean(context, CompareRuntime.class);
                        CompareEngine childEngine = localBean(context, CompareEngine.class);
                        MeterRegistry childRegistry = localBean(context, MeterRegistry.class);
                        CompareOperations selected = localBean(context, CompareOperationsDecorator.class);
                        CompareHealthIndicator health = localBean(
                                context, CompareHealthIndicator.class);

                        assertThat(((CompareOperationsDecorator) selected).delegate())
                                .isSameAs(childEngine);
                        assertThat(health).extracting("runtime").isSameAs(childRuntime);
                        assertThat(health).extracting("operations").isSameAs(selected);

                        selected.compare("before", "after");

                        assertThat(childRegistry.get(CompareMetrics.REQUEST_METER)
                                .counter().count()).isEqualTo(1.0);
                        MeterRegistry parentRegistry = parent.getBean(MeterRegistry.class);
                        assertThat(parentRegistry.find(CompareMetrics.REQUEST_METER).counter()).isNull();
                    });
        }
    }

    @Test
    void jdkProxyDecoratorIsValidatedByInterface() {
        contextRunner
                .withUserConfiguration(
                        RuntimeGraphConfiguration.class,
                        RegistryConfiguration.class,
                        JdkProxyDecoratorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ObservedCompareOperations.class);
                    CompareOperationsDecorator decorator =
                            context.getBean(CompareOperationsDecorator.class);
                    assertThat(Proxy.isProxyClass(decorator.getClass())).isTrue();
                    assertThat(context.getBean(CompareOperations.class)).isSameAs(decorator);
                    assertThat(decorator.delegate()).isSameAs(context.getBean(CompareEngine.class));
                });
    }

    @Test
    void should_back_off_observation_when_host_registry_is_absent() {
        contextRunner
                .withUserConfiguration(RuntimeGraphConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ObservedCompareOperations.class);
                    assertThat(context).hasSingleBean(CompareHealthIndicator.class);
                    assertThat(context.getBean(CompareOperations.class))
                            .isSameAs(context.getBean(CompareEngine.class));
                });
    }

    @Test
    void should_publish_primary_observed_operations_when_all_dependencies_exist() {
        contextRunner
                .withUserConfiguration(RuntimeGraphConfiguration.class, RegistryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObservedCompareOperations.class);
                    assertThat(context).hasSingleBean(CompareHealthIndicator.class);
                    assertThat(context.getBeansOfType(CompareOperations.class)).hasSize(2);
                    assertThat(context.getBean(CompareOperations.class))
                            .isSameAs(context.getBean("observedCompareOperations"));
                });
    }

    @Test
    void should_load_without_optional_compare_classes() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(CompareEngine.class, CompareOperations.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ObservedCompareOperations.class);
                    assertThat(context).doesNotHaveBean(CompareHealthIndicator.class);
                });
    }

    @Test
    void rejectsLegacyCompareWhenTypedOperationsAreMissing() {
        versionGuardRunner
                .withClassLoader(new FilteredClassLoader(CompareOperations.class))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessage(
                                "tfi-ops-spring 4.x is incompatible with tfi-compare 3.x"));
    }

    @Test
    void versionGuardBacksOffWithoutCompareAndWithCurrentCompare() {
        versionGuardRunner
                .withClassLoader(new FilteredClassLoader(CompareEngine.class, CompareOperations.class))
                .run(context -> assertThat(context).hasNotFailed());
        versionGuardRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void should_publish_only_current_boot_resources_and_metadata() throws Exception {
        ClassLoader loader = CompareOpsAutoConfigurationContractTests.class.getClassLoader();
        try (InputStream metadata = loader.getResourceAsStream(
                "META-INF/additional-spring-configuration-metadata.json");
             InputStream imports = loader.getResourceAsStream(
                     "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(metadata).isNotNull();
            assertThat(imports).isNotNull();

            JsonNode root = new ObjectMapper().readTree(metadata);
            assertThat(StreamSupport.stream(root.path("properties").spliterator(), false)
                    .map(property -> property.path("name").asText()).toList())
                    .containsExactlyInAnyOrder(
                            "tfi.actuator.cache.ttl-ms",
                            "tfi.health.memory-threshold",
                            "tfi.health.max-active-contexts",
                            "tfi.endpoint.advanced.enabled");
            assertThat(new String(imports.readAllBytes(), StandardCharsets.UTF_8).lines().toList())
                    .isEqualTo(List.of(
                            "com.syy.taskflowinsight.ops.compare.LegacyCompareVersionGuardAutoConfiguration",
                            "com.syy.taskflowinsight.ops.compare.CompareObservationAutoConfiguration"));
        }
    }

    private static <T> T localBean(
            AssertableApplicationContext context,
            Class<T> type) {
        String[] names = context.getSourceApplicationContext().getBeanFactory()
                .getBeanNamesForType(type, true, false);
        assertThat(names).as("local bean for %s", type.getName()).hasSize(1);
        return context.getSourceApplicationContext().getBeanFactory().getBean(names[0], type);
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeGraphConfiguration {

        @Bean
        CompareRuntime compareRuntime() {
            return CompareRuntime.builder().build();
        }

        @Bean
        CompareEngine compareEngine(CompareRuntime runtime) {
            return runtime.engine();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryParentGraphConfiguration {

        @Bean
        @Primary
        CompareRuntime parentRuntime() {
            return CompareRuntime.builder().build();
        }

        @Bean
        @Primary
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
        MeterRegistry parentRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdkProxyDecoratorConfiguration {

        @Bean(name = "observedCompareOperations")
        @Primary
        CompareOperationsDecorator jdkDecorator(CompareEngine engine) {
            return (CompareOperationsDecorator) Proxy.newProxyInstance(
                    CompareOperationsDecorator.class.getClassLoader(),
                    new Class<?>[]{CompareOperationsDecorator.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("delegate")) {
                            return engine;
                        }
                        return method.invoke(engine, arguments);
                    });
        }
    }
}
