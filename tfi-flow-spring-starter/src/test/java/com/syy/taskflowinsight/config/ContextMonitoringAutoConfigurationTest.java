package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.context.ContextManagerConfig;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.aspect.TfiAnnotationAspect;
import com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate;
import com.syy.taskflowinsight.masking.UnifiedDataMasker;
import com.syy.taskflowinsight.spel.SafeSpELEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link ContextMonitoringAutoConfiguration} 单元测试.
 *
 * <p>覆盖 sanitizeMillis 边界值和配置应用逻辑。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@DisplayName("ContextMonitoringAutoConfiguration 自动配置测试")
class ContextMonitoringAutoConfigurationTest {

    /** 不依赖宿主component scan的starter装配合同。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ContextMonitoringAutoConfiguration.class));

    @AfterEach
    void resetManagerSingletons() {
        SafeContextManager.getInstance().apply(ContextManagerConfig.defaults());
    }

    @Nested
    @DisplayName("sanitizeMillis - 毫秒值校验")
    class SanitizeMillisTests {

        @Test
        @DisplayName("正常正数值直接返回")
        void positiveValue_returnsAsIs() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(5000L, 60000L))
                    .isEqualTo(5000L);
        }

        @Test
        @DisplayName("零值回退到默认值")
        void zeroValue_returnsFallback() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(0L, 60000L))
                    .isEqualTo(60000L);
        }

        @Test
        @DisplayName("负值回退到默认值")
        void negativeValue_returnsFallback() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(-1L, 60000L))
                    .isEqualTo(60000L);
        }

        @Test
        @DisplayName("Long.MIN_VALUE 回退到默认值")
        void minValue_returnsFallback() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(Long.MIN_VALUE, 3600000L))
                    .isEqualTo(3600000L);
        }

        @Test
        @DisplayName("1 毫秒（最小正值）直接返回")
        void oneMillis_returnsAsIs() {
            assertThat(ContextMonitoringAutoConfiguration.sanitizeMillis(1L, 60000L))
                    .isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("applyMonitoringProperties - 配置应用")
    class ApplyPropertiesTests {

        @Test
        @DisplayName("构造完整配置并统一回退到 Core defaults")
        void buildsOneSanitizedContextManagerConfig() {
            TfiContextProperties props = new TfiContextProperties();
            props.setMaxAgeMillis(0L);
            props.setLeakDetectionEnabled(true);
            props.setLeakDetectionIntervalMillis(-1L);
            ContextMonitoringAutoConfiguration configuration =
                    new ContextMonitoringAutoConfiguration(props);

            ContextManagerConfig config = configuration.contextManagerConfig();
            ContextManagerConfig defaults = ContextManagerConfig.defaults();

            assertThat(config).isEqualTo(new ContextManagerConfig(
                    defaults.timeoutMillis(),
                    true,
                    defaults.leakDetectionIntervalMillis()));
        }

        @Test
        @DisplayName("默认配置不抛异常")
        void defaultProperties_noException() {
            TfiContextProperties props = new TfiContextProperties();
            ContextMonitoringAutoConfiguration config =
                    new ContextMonitoringAutoConfiguration(props);

            // 应不抛异常（即使 SafeContextManager 在测试环境的行为可能不同）
            assertDoesNotThrow(config::applyMonitoringProperties);
        }

        @Test
        @DisplayName("自定义配置正常应用")
        void customProperties_appliedSuccessfully() {
            TfiContextProperties props = new TfiContextProperties();
            props.setMaxAgeMillis(1800000L);
            props.setLeakDetectionEnabled(true);

            ContextMonitoringAutoConfiguration config =
                    new ContextMonitoringAutoConfiguration(props);
            assertDoesNotThrow(config::applyMonitoringProperties);
        }
    }

    @Test
    @DisplayName("零 delegate 时自动配置提供唯一 Flow aspect")
    void zeroDelegateAutoConfigurationProvidesOneAspectWithoutComponentScan() {
        contextRunner
                .withPropertyValues("tfi.annotation.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(TfiAnnotationAspect.class));
    }

    @Test
    @DisplayName("多 delegate 使自动配置启动失败")
    void multipleDelegatesFailAutoConfiguration() {
        contextRunner
                .withUserConfiguration(MultipleDelegatesConfiguration.class)
                .withPropertyValues("tfi.annotation.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
                });
    }

    @Test
    @DisplayName("存在 Primary 时多 delegate 仍使自动配置启动失败")
    void primaryDoesNotResolveAmbiguousDelegates() {
        contextRunner
                .withUserConfiguration(PrimaryDelegateConfiguration.class)
                .withPropertyValues("tfi.annotation.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
                });
    }

    @Test
    @DisplayName("父层基础设施、Aspect和delegate不抑制或进入子层")
    void parentAspectAndDelegateCannotEnterChild() {
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext(ParentFlowConfiguration.class)) {
            contextRunner
                    .withParent(parent)
                    .withUserConfiguration(ChildDelegateConfiguration.class)
                    .withPropertyValues("tfi.annotation.enabled=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getSourceApplicationContext().getBeanFactory()
                                .getBeanNamesForType(SafeSpELEvaluator.class, true, false)).hasSize(1);
                        assertThat(context.getSourceApplicationContext().getBeanFactory()
                                .getBeanNamesForType(UnifiedDataMasker.class, true, false)).hasSize(1);
                        assertThat(context.getSourceApplicationContext().getBeanFactory()
                                .getBeanNamesForType(TfiAnnotationAspect.class, true, false)).hasSize(1);
                        assertThat(context.getSourceApplicationContext().getBeanFactory()
                                .getBeanNamesForType(
                                        TfiTaskDeepTrackingDelegate.class, true, false)).hasSize(1);
                        SafeSpELEvaluator evaluator = context.getBean(SafeSpELEvaluator.class);
                        UnifiedDataMasker masker = context.getBean(UnifiedDataMasker.class);
                        assertThat(evaluator.evaluateString("'ok'", null)).isEqualTo("ok");
                        assertThat(masker.maskValue("password", "value")).isNotEqualTo("value");
                    });
        }
    }

    @Test
    @DisplayName("4.0 不暴露第二 cleaner 配置契约")
    void legacySecondCleanerContractIsAbsent() throws Exception {
        assertThat(Arrays.stream(TfiContextProperties.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain(
                        "isCleanupEnabled",
                        "setCleanupEnabled",
                        "getCleanupIntervalMillis",
                        "setCleanupIntervalMillis");

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(input).isNotNull();
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(metadata)
                    .doesNotContain("\"name\": \"tfi.context.cleanup-enabled\"")
                    .doesNotContain("\"name\": \"tfi.context.cleanup-interval-millis\"");
        }

        String source = Files.readString(autoConfigurationSource());
        assertThat(source)
                .doesNotContain("ZeroLeakThreadLocalManager", "applyToZeroLeakManager");
    }

    private static Path autoConfigurationSource() {
        Path modulePath = Path.of(
                "src/main/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfiguration.java");
        return Files.isRegularFile(modulePath)
                ? modulePath
                : Path.of("tfi-flow-spring-starter", modulePath.toString());
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleDelegatesConfiguration {

        @Bean
        TfiTaskDeepTrackingDelegate firstDelegate() {
            return (annotation, method, arguments, stage, invocation) -> invocation.proceed();
        }

        @Bean
        TfiTaskDeepTrackingDelegate secondDelegate() {
            return (annotation, method, arguments, stage, invocation) -> invocation.proceed();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryDelegateConfiguration {

        @Bean
        @Primary
        TfiTaskDeepTrackingDelegate primaryDelegate() {
            return (annotation, method, arguments, stage, invocation) -> invocation.proceed();
        }

        @Bean
        TfiTaskDeepTrackingDelegate secondaryDelegate() {
            return (annotation, method, arguments, stage, invocation) -> invocation.proceed();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ParentFlowConfiguration {

        @Bean
        @Primary
        TfiSecurityProperties parentSecurityProperties() {
            TfiSecurityProperties properties = new TfiSecurityProperties();
            properties.setSpelMaxLength(1);
            properties.setSensitiveKeywords(Set.of("parent-only"));
            return properties;
        }

        @Bean
        SafeSpELEvaluator parentEvaluator(TfiSecurityProperties parentSecurityProperties) {
            return new SafeSpELEvaluator(parentSecurityProperties);
        }

        @Bean
        UnifiedDataMasker parentMasker(TfiSecurityProperties parentSecurityProperties) {
            return new UnifiedDataMasker(parentSecurityProperties);
        }

        @Bean
        TfiAnnotationAspect parentAspect(
                SafeSpELEvaluator parentEvaluator,
                UnifiedDataMasker parentMasker) {
            return new TfiAnnotationAspect(parentEvaluator, parentMasker);
        }

        @Bean
        TfiTaskDeepTrackingDelegate parentDelegate() {
            return (annotation, method, arguments, stage, invocation) -> invocation.proceed();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ChildDelegateConfiguration {

        @Bean
        TfiTaskDeepTrackingDelegate childDelegate() {
            return (annotation, method, arguments, stage, invocation) -> invocation.proceed();
        }
    }
}
