package com.syy.taskflowinsight.compare.spring;

import com.syy.taskflowinsight.annotation.TfiTask;
import com.syy.taskflowinsight.api.TaskContext;
import com.syy.taskflowinsight.aspect.TfiAnnotationAspect;
import com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate;
import com.syy.taskflowinsight.config.ContextMonitoringAutoConfiguration;
import com.syy.taskflowinsight.spi.TrackingProvider;
import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.CompareInputException;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.ComparePolicy;
import com.syy.taskflowinsight.tracking.compare.CompareRuntime;
import com.syy.taskflowinsight.tracking.compare.InputViolation;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TfiTaskDeepTrackingDelegateContractTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(compareAutoConfigurations());
    private final ApplicationContextRunner activeTrackingContextRunner = new ApplicationContextRunner()
            .withConfiguration(compareAutoConfigurations())
            .withConfiguration(AutoConfigurations.of(ContextMonitoringAutoConfiguration.class))
            .withPropertyValues("tfi.annotation.enabled=true");

    @Test
    void trackingEnabledWithoutFlowFailsFast() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(TfiTaskDeepTrackingDelegate.class))
                .withPropertyValues("tfi.compare.tracking.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("tfi.compare.tracking.enabled=true requires tfi-flow-spring-starter");
                });
    }

    @Test
    void trackingDisabledWithoutFlowStarts() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(TfiTaskDeepTrackingDelegate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TrackingProvider.class);
                });
    }

    @Test
    void trackingEnabledWithFlowAutoConfigurationExcludedFailsFast() {
        contextRunner
                .withPropertyValues("tfi.compare.tracking.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "tfi.compare.tracking.enabled=true requires one local active "
                                            + "TfiAnnotationAspect");
                });
    }

    @Test
    void trackingEnabledWithAnnotationDisabledFailsFast() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(ContextMonitoringAutoConfiguration.class))
                .withPropertyValues("tfi.compare.tracking.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "tfi.compare.tracking.enabled=true requires one local active "
                                            + "TfiAnnotationAspect");
                });
    }

    @Test
    void trackingBacksOffWhenRuntimeExistsOnlyInParent() {
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext(CustomRuntimeConfiguration.class)) {
            new ApplicationContextRunner()
                    .withParent(parent)
                    .withConfiguration(AutoConfigurations.of(
                            ContextMonitoringAutoConfiguration.class,
                            TfiCompareTrackingAutoConfiguration.class))
                    .withPropertyValues(
                            "tfi.annotation.enabled=true",
                            "tfi.compare.tracking.enabled=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getSourceApplicationContext().getBeanFactory()
                                .getBeanNamesForType(TrackingProvider.class, true, false)).isEmpty();
                        assertThat(context.getSourceApplicationContext().getBeanFactory()
                                .getBeanNamesForType(
                                        TfiTaskDeepTrackingDelegate.class, true, false)).isEmpty();
                    });
        }
    }

    @Test
    void trackingUsesChildGraphDespitePrimaryParentBeans() throws Throwable {
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext(PrimaryParentTrackingConfiguration.class)) {
            activeTrackingContextRunner
                    .withParent(parent)
                    .withUserConfiguration(ValidAnnotationConfiguration.class)
                    .withPropertyValues(
                            "tfi.compare.tracking.enabled=true",
                            "tfi.compare.max-depth=2")
                    .run(context -> {
                        CompareRuntime childRuntime = localBean(context, CompareRuntime.class);
                        TfiTaskDeepTrackingDelegate delegate = localBean(
                                context, TfiTaskDeepTrackingDelegate.class);
                        AtomicInteger actionCalls = new AtomicInteger();
                        MutableValue value = new MutableValue();
                        TaskContext stage = mock(TaskContext.class);
                        when(stage.message("Deep tracking completed")).thenReturn(stage);
                        Method method = ValidService.class.getDeclaredMethod(
                                "execute", MutableValue.class);

                        assertThat(childRuntime.policy().maxDepth()).isEqualTo(2);
                        assertThat(childRuntime).isNotSameAs(parent.getBean(CompareRuntime.class));
                        assertThatCode(() -> delegate.execute(
                                method.getAnnotation(TfiTask.class),
                                method,
                                new Object[]{value},
                                stage,
                                () -> {
                                    actionCalls.incrementAndGet();
                                    value.count++;
                                    return value;
                                })).doesNotThrowAnyException();
                        assertThat(actionCalls).hasValue(1);
                        assertThat(parent.getBean(AtomicInteger.class)).hasValue(0);
                    });
        }
    }

    @Test
    void trackingIntegrationIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(TfiTaskDeepTrackingDelegate.class);
            assertThat(context).doesNotHaveBean(TrackingProvider.class);
        });
    }

    @Test
    void enabledIntegrationUsesTheFlowAspectAndOneHook() {
        activeTrackingContextRunner.withPropertyValues("tfi.compare.tracking.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(TfiTaskDeepTrackingDelegate.class);
                    assertThat(context).hasSingleBean(TrackingProvider.class);
                    assertThat(context).hasSingleBean(TfiAnnotationAspect.class);
                    assertThat(context.getBeanNamesForAnnotation(Aspect.class)).hasSize(1);
                });
    }

    @Test
    void trackingProviderUsesTheCurrentContextRuntimePolicy() {
        activeTrackingContextRunner.withUserConfiguration(CustomRuntimeConfiguration.class)
                .withPropertyValues("tfi.compare.tracking.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TrackingProvider provider = context.getBean(TrackingProvider.class);
                    CompareOptions tooDeep = CompareOptions.defaults(
                            ComparePolicy.builder().maxDepth(3).build());

                    assertThatThrownBy(() -> provider.begin(
                            List.of(new TrackingExecutor.Target("value", new MutableValue())),
                            tooDeep))
                            .isInstanceOf(CompareInputException.class)
                            .extracting(error -> ((CompareInputException) error).violation())
                            .isEqualTo(InputViolation.OPTION_OUT_OF_RANGE);
                });
    }

    @Test
    void customTrackingProviderCannotCreateAThirdRuntimeGraph() {
        activeTrackingContextRunner.withUserConfiguration(CustomTrackingProviderConfiguration.class)
                .withPropertyValues("tfi.compare.tracking.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void delegateMapsComplexArgumentsAndExecutesTheActionOnce() throws Throwable {
        AtomicReference<List<TrackingExecutor.Target>> observedTargets = new AtomicReference<>();
        AtomicReference<CompareOptions> observedOptions = new AtomicReference<>();
        TrackingProvider provider = (targets, options) -> {
            observedTargets.set(List.copyOf(targets));
            observedOptions.set(options);
            return emptyScope();
        };
        DefaultTfiTaskDeepTrackingDelegate delegate = new DefaultTfiTaskDeepTrackingDelegate(provider);
        TfiTask annotation = mock(TfiTask.class);
        when(annotation.maxDepth()).thenReturn(2);
        when(annotation.timeBudgetMs()).thenReturn(500L);
        when(annotation.includeFields()).thenReturn(new String[]{"count"});
        when(annotation.excludeFields()).thenReturn(new String[0]);
        when(annotation.collectionStrategy()).thenReturn("IGNORE");
        TaskContext stage = mock(TaskContext.class);
        when(stage.message("Deep tracking completed")).thenReturn(stage);
        Method method = SampleService.class.getDeclaredMethod("execute", Object.class);
        MutableValue value = new MutableValue();
        AtomicInteger actionCalls = new AtomicInteger();
        Object expected = new Object();

        Object actual = delegate.execute(annotation, method, new Object[]{value}, stage, () -> {
            actionCalls.incrementAndGet();
            return expected;
        });

        assertThat(actual).isSameAs(expected);
        assertThat(actionCalls).hasValue(1);
        assertThat(observedTargets.get()).extracting(TrackingExecutor.Target::name).containsExactly("arg-0");
        assertThat(observedOptions.get().maxDepth()).isEqualTo(2);
        assertThat(observedOptions.get().deadline()).isEqualTo(Duration.ofMillis(500));
        assertThat(observedOptions.get().includeCollectionContents()).isFalse();
    }

    @Test
    void invalidStaticAnnotationFailsBeforeBusinessTraffic() {
        activeTrackingContextRunner.withUserConfiguration(InvalidAnnotationConfiguration.class)
                .withPropertyValues("tfi.compare.tracking.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isInstanceOf(CompareInputException.class);
                });
    }

    private static TrackingBatchScope emptyScope() {
        return new TrackingBatchScope() {
            @Override
            public List<TrackingExecutor.Item> capture() {
                return List.of();
            }

            @Override
            public void close() {
            }
        };
    }

    private static AutoConfigurations compareAutoConfigurations() {
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (InputStream input = TfiCompareAutoConfiguration.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing starter auto-configuration imports");
            }
            Class<?>[] configurations = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .map(TfiTaskDeepTrackingDelegateContractTests::loadClass)
                    .toArray(Class<?>[]::new);
            return AutoConfigurations.of(configurations);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read starter auto-configuration imports", exception);
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot load auto-configuration " + className, exception);
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
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
    static class CustomRuntimeConfiguration {
        @Bean
        CompareRuntime customRuntime() {
            return CompareRuntime.builder()
                    .policy(ComparePolicy.builder().maxDepth(2).build())
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTrackingProviderConfiguration {
        @Bean
        TrackingProvider customTrackingProvider() {
            return (targets, options) -> emptyScope();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidAnnotationConfiguration {
        @Bean
        SampleService sampleService() {
            return new SampleService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ValidAnnotationConfiguration {
        @Bean
        ValidService validService() {
            return new ValidService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryParentTrackingConfiguration {
        @Bean
        @Primary
        CompareRuntime parentRuntime() {
            return CompareRuntime.builder()
                    .policy(ComparePolicy.builder().maxDepth(8).build())
                    .build();
        }

        @Bean
        AtomicInteger parentTrackingCalls() {
            return new AtomicInteger();
        }

        @Bean
        @Primary
        TrackingProvider parentTrackingProvider(AtomicInteger parentTrackingCalls) {
            return (targets, options) -> {
                parentTrackingCalls.incrementAndGet();
                return emptyScope();
            };
        }
    }

    static final class SampleService {
        @TfiTask(deepTracking = true, timeBudgetMs = 0)
        public void execute(Object value) {
        }
    }

    static final class ValidService {
        @TfiTask(deepTracking = true, maxDepth = 2, collectionStrategy = "IGNORE")
        public MutableValue execute(MutableValue value) {
            return value;
        }
    }

    static final class MutableValue {
        int count;
    }
}
