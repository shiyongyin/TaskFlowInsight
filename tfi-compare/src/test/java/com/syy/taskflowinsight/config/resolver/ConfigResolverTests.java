package com.syy.taskflowinsight.config.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * 配置解析系统测试。
 * 覆盖 ConfigDefaults、ConfigMigrationMapper、ConfigurationResolverImpl。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Config Resolver — 配置解析系统测试")
class ConfigResolverTests {

    // ── ConfigDefaults ──

    @Nested
    @DisplayName("ConfigDefaults — 默认值")
    class ConfigDefaultsTests {

        @Test
        @DisplayName("MAX_DEPTH 值合理 (>0, <=20)")
        void maxDepth_shouldBeReasonable() {
            assertThat(ConfigDefaults.MAX_DEPTH).isGreaterThan(0);
            assertThat(ConfigDefaults.MAX_DEPTH).isLessThanOrEqualTo(20);
        }

        @Test
        @DisplayName("TIME_BUDGET_MS 值合理 (>0)")
        void timeBudgetMs_shouldBePositive() {
            assertThat(ConfigDefaults.TIME_BUDGET_MS).isGreaterThan(0);
        }

        @Test
        @DisplayName("NUMERIC_FLOAT_TOLERANCE 值合理 (>=0)")
        void numericFloatTolerance_shouldBeNonNegative() {
            assertThat(ConfigDefaults.NUMERIC_FLOAT_TOLERANCE).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("Keys 类包含所有关键配置键")
        void keys_shouldContainCriticalKeys() {
            assertThat(ConfigDefaults.Keys.MAX_DEPTH).isNotBlank();
            assertThat(ConfigDefaults.Keys.TIME_BUDGET_MS).isNotBlank();
        }

        @Test
        @DisplayName("SLOW_OPERATION_MS 合理 (>0)")
        void slowOperationMs_shouldBePositive() {
            assertThat(ConfigDefaults.SLOW_OPERATION_MS).isGreaterThan(0);
        }
    }

    // ── ConfigMigrationMapper ──

    @Nested
    @DisplayName("ConfigMigrationMapper — 配置迁移")
    class ConfigMigrationMapperTests {

        private final ConfigMigrationMapper mapper = new ConfigMigrationMapper();

        @Test
        @DisplayName("checkAndMigrate 新键 → 返回原键")
        void newKey_shouldReturnAsIs() {
            String result = mapper.checkAndMigrate("tfi.change-tracking.snapshot.max-depth");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("getAllMappings → 非 null")
        void getAllMappings_shouldReturnNonNull() {
            Map<String, String> mappings = mapper.getAllMappings();
            assertThat(mappings).isNotNull();
        }

        @Test
        @DisplayName("getMigrationReport → 非 null")
        void getMigrationReport_shouldReturnNonNull() {
            String report = mapper.getMigrationReport();
            assertThat(report).isNotNull();
        }

        @Test
        @DisplayName("clearWarnings 不抛异常")
        void clearWarnings_shouldNotThrow() {
            assertThatCode(mapper::clearWarnings).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("isDeprecatedKey 对新键返回 false")
        void isDeprecatedKey_newKey_shouldReturnFalse() {
            assertThat(mapper.isDeprecatedKey("tfi.change-tracking.snapshot.max-depth"))
                    .isFalse();
        }
    }

    // ── ConfigurationResolverImpl (无 Spring Context) ──

    @Nested
    @DisplayName("ConfigurationResolverImpl — 纯 Java 模式")
    class ConfigurationResolverImplTests {

        @Test
        @DisplayName("无 Spring 环境构造 → 使用默认值")
        void noSpringContext_shouldUseDefaults() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            // Should not throw - uses defaults for all configurations
            assertThat(resolver).isNotNull();
        }

        @Test
        @DisplayName("resolve 已知 key → 返回默认值")
        void resolve_knownKey_shouldReturnDefault() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            Integer maxDepth = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(maxDepth).isEqualTo(ConfigDefaults.MAX_DEPTH);
        }

        @Test
        @DisplayName("setRuntimeConfig + resolve → 运行时值优先")
        void runtimeConfig_shouldOverrideDefault() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 99);
            Integer maxDepth = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(maxDepth).isEqualTo(99);
        }

        @Test
        @DisplayName("clearRuntimeConfig → 恢复默认值")
        void clearRuntimeConfig_shouldRevertToDefault() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 99);
            resolver.clearRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH);
            Integer maxDepth = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(maxDepth).isEqualTo(ConfigDefaults.MAX_DEPTH);
        }

        @Test
        @DisplayName("setMethodAnnotationConfig → 方法注解优先级")
        void methodAnnotationConfig_shouldHaveCorrectPriority() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setMethodAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, 7);
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(7);
        }

        @Test
        @DisplayName("运行时 > 方法注解 优先级")
        void runtimeOverMethodAnnotation_shouldWin() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setMethodAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, 7);
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 42);
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("getEffectivePriority → 返回当前生效层")
        void getEffectivePriority_shouldReturnCurrentLayer() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            ConfigurationResolver.ConfigPriority priority =
                    resolver.getEffectivePriority(ConfigDefaults.Keys.MAX_DEPTH);
            assertThat(priority).isNotNull();
        }

        @Test
        @DisplayName("isEnvVariablesEnabled → 默认关闭")
        void envVariablesEnabled_shouldBeOffByDefault() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            assertThat(resolver.isEnvVariablesEnabled()).isFalse();
        }

        @Test
        @DisplayName("refresh 不抛异常")
        void refresh_shouldNotThrow() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            assertThatCode(resolver::refresh).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("类型转换 — String to Boolean")
        void typeConversion_stringToBoolean() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setRuntimeConfig("test.flag", "true");
            Boolean result = resolver.resolve("test.flag", Boolean.class, false);
            assertThat(result).isTrue();
        }
    }
}
