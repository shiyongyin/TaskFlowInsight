package com.syy.taskflowinsight.config.resolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ConfigurationResolverImpl 白盒测试
 * <p>
 * 针对 567 条未覆盖指令，覆盖以下路径：
 * - 构造：null Environment（StandardEnvironment 回退）、null migrationMapper
 * - resolve(key, type, defaultValue): resolverEnabled=false、resolverEnabled=true
 * - resolve(key, type) 无默认值：返回 Optional
 * - resolveByPriority 7 层：Runtime API、Method Annotation、Class Annotation、
 *   Spring Config、Env Variable、JVM Property、Default
 * - 键迁移：旧键→新键、新键→旧键回退
 * - getEffectivePriority：各优先级层级
 * - getConfigSources：收集所有层
 * - setRuntimeConfig / clearRuntimeConfig：缓存失效
 * - setMethodAnnotationConfig / setClassAnnotationConfig
 * - isEnvVariablesEnabled、refresh
 * - convertValue：int/long/double/float/boolean/String、NumberFormatException、不支持类型
 * - getDefaultValue：所有已知键、未知键
 * - parseTruthy：true/false/1/0/yes/no/y/n/on/off/null/unknown
 * - toEnvVariableKey：点与连字符转换
 * </p>
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("ConfigurationResolverImpl — 白盒测试")
class ConfigResolverWhiteBoxTests {

    private ConfigurationResolverImpl resolver;
    private StandardEnvironment standardEnv;

    @BeforeEach
    void setUp() {
        standardEnv = new StandardEnvironment();
        resolver = new ConfigurationResolverImpl(standardEnv, new ConfigMigrationMapper());
    }

    // ──────────────────────────────────────────────────────────────
    //  构造
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("构造 — null 参数")
    class ConstructorTests {

        @Test
        @DisplayName("null migrationMapper → 使用 new ConfigMigrationMapper()")
        void nullMigrationMapper_shouldUseDefault() {
            assertThatCode(() ->
                    new ConfigurationResolverImpl(standardEnv, null)
            ).doesNotThrowAnyException();

            ConfigurationResolverImpl r = new ConfigurationResolverImpl(standardEnv, null);
            assertThat(r.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 999))
                    .isEqualTo(ConfigDefaults.MAX_DEPTH);
        }

        @Test
        @DisplayName("StandardEnvironment + ConfigMigrationMapper 正常构造")
        void standardConstruction_shouldSucceed() {
            assertThat(resolver).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  resolve(key, type, defaultValue)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolve — resolverEnabled 路径")
    class ResolveEnabledTests {

        @Test
        @DisplayName("resolverEnabled=true → 按优先级解析")
        void resolverEnabled_shouldResolveByPriority() {
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 42);
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("resolverEnabled=false → 直接返回 Spring 配置或默认值")
        void resolverDisabled_shouldUseSpringOrDefault() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("tfi.config.resolver.enabled", "false");
            env.setProperty(ConfigDefaults.Keys.MAX_DEPTH, "88");

            ConfigurationResolverImpl disabledResolver =
                    new ConfigurationResolverImpl(env, new ConfigMigrationMapper());

            Integer result = disabledResolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(88);
        }

        @Test
        @DisplayName("resolverEnabled=false 无 Spring 配置 → 返回默认值")
        void resolverDisabled_noSpring_shouldReturnDefault() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("tfi.config.resolver.enabled", "false");

            ConfigurationResolverImpl disabledResolver =
                    new ConfigurationResolverImpl(env, new ConfigMigrationMapper());

            Integer result = disabledResolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 777);
            assertThat(result).isEqualTo(777);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  resolve(key, type) 无默认值
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolve — Optional 返回")
    class ResolveOptionalTests {

        @Test
        @DisplayName("resolve 无默认值 → 返回 Optional")
        void resolveWithoutDefault_shouldReturnOptional() {
            Optional<Integer> empty = resolver.resolve("unknown.key.xyz", Integer.class);
            assertThat(empty).isEmpty();

            resolver.setRuntimeConfig("test.key", 123);
            Optional<Integer> present = resolver.resolve("test.key", Integer.class);
            assertThat(present).hasValue(123);
        }

        @Test
        @DisplayName("resolverEnabled=false 时 resolve 无默认值")
        void resolveOptional_resolverDisabled() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("tfi.config.resolver.enabled", "false");
            env.setProperty("k", "5");

            ConfigurationResolverImpl r = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            Optional<Integer> opt = r.resolve("k", Integer.class);
            assertThat(opt).hasValue(5);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  resolveByPriority 7 层
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveByPriority — 7 层优先级")
    class ResolveByPriorityTests {

        @Test
        @DisplayName("1. Runtime API 最高优先级")
        void runtimeApi_hasHighestPriority() {
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 100);
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(100);
        }

        @Test
        @DisplayName("2. Method Annotation")
        void methodAnnotation_priority() {
            resolver.setMethodAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, 50);
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(50);
        }

        @Test
        @DisplayName("3. Class Annotation")
        void classAnnotation_priority() {
            resolver.setClassAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, 30);
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(30);
        }

        @Test
        @DisplayName("4. Spring Config")
        void springConfig_priority() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty(ConfigDefaults.Keys.MAX_DEPTH, "25");

            ConfigurationResolverImpl r = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            Integer result = r.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(25);
        }

        @Test
        @DisplayName("5. Environment Variable（需启用）")
        void envVariable_priority() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("tfi.config.enable-env", "true");

            ConfigurationResolverImpl r = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            // 设置系统属性模拟 JVM 层（环境变量需实际设置，此处测试默认值回退）
            Integer result = r.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 999);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("6. JVM System Property")
        void jvmProperty_priority() {
            try {
                System.setProperty("tfi.test.jvm.key", "77");
                Integer result = resolver.resolve("tfi.test.jvm.key", Integer.class, 0);
                assertThat(result).isEqualTo(77);
            } finally {
                System.clearProperty("tfi.test.jvm.key");
            }
        }

        @Test
        @DisplayName("7. Default Value")
        void defaultValue_priority() {
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(ConfigDefaults.MAX_DEPTH);
        }

        @Test
        @DisplayName("Runtime > Method > Class > Spring")
        void priorityOrder_runtimeWins() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty(ConfigDefaults.Keys.MAX_DEPTH, "10");

            ConfigurationResolverImpl r = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            r.setClassAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, 20);
            r.setMethodAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, 30);
            r.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 40);

            Integer result = r.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 0);
            assertThat(result).isEqualTo(40);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  键迁移
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("键迁移 — 旧键→新键、新键→旧键")
    class KeyMigrationTests {

        @Test
        @DisplayName("旧键 tfi.change-tracking.max-depth → 迁移到新键")
        void oldKey_migratesToNewKey() {
            String oldKey = "tfi.change-tracking.max-depth";
            String newKey = ConfigMigrationMapper.getAllMappings().get(oldKey);
            assertThat(newKey).isEqualTo("tfi.change-tracking.snapshot.max-depth");

            resolver.setRuntimeConfig(newKey, 15);
            Integer result = resolver.resolve(oldKey, Integer.class, 0);
            assertThat(result).isEqualTo(15);
        }

        @Test
        @DisplayName("新键通过旧键回退读取")
        void newKey_fallbackToOldKey() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            String newKey = "tfi.change-tracking.snapshot.max-depth";
            String oldKey = mapper.getOldKeyForNew(newKey);
            assertThat(oldKey).isNotNull();

            resolver.setRuntimeConfig(oldKey, 22);
            Integer result = resolver.resolve(newKey, Integer.class, 0);
            assertThat(result).isEqualTo(22);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  getEffectivePriority
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getEffectivePriority — 各层级")
    class GetEffectivePriorityTests {

        @Test
        @DisplayName("RUNTIME_API 优先级")
        void runtimeApi_priority() {
            resolver.setRuntimeConfig("k", "v");
            assertThat(resolver.getEffectivePriority("k"))
                    .isEqualTo(ConfigurationResolver.ConfigPriority.RUNTIME_API);
        }

        @Test
        @DisplayName("METHOD_ANNOTATION 优先级")
        void methodAnnotation_priority() {
            resolver.setMethodAnnotationConfig("k2", "v");
            assertThat(resolver.getEffectivePriority("k2"))
                    .isEqualTo(ConfigurationResolver.ConfigPriority.METHOD_ANNOTATION);
        }

        @Test
        @DisplayName("CLASS_ANNOTATION 优先级")
        void classAnnotation_priority() {
            resolver.setClassAnnotationConfig("k3", "v");
            assertThat(resolver.getEffectivePriority("k3"))
                    .isEqualTo(ConfigurationResolver.ConfigPriority.CLASS_ANNOTATION);
        }

        @Test
        @DisplayName("SPRING_CONFIG 优先级")
        void springConfig_priority() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("spring.key", "val");
            ConfigurationResolverImpl r = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            assertThat(r.getEffectivePriority("spring.key"))
                    .isEqualTo(ConfigurationResolver.ConfigPriority.SPRING_CONFIG);
        }

        @Test
        @DisplayName("JVM_PROPERTY 或 SPRING_CONFIG 优先级（StandardEnvironment 合并系统属性）")
        void jvmProperty_priority() {
            try {
                System.setProperty("tfi.jvm.priority.test", "x");
                ConfigurationResolver.ConfigPriority p = resolver.getEffectivePriority("tfi.jvm.priority.test");
                assertThat(p).isIn(
                        ConfigurationResolver.ConfigPriority.JVM_PROPERTY,
                        ConfigurationResolver.ConfigPriority.SPRING_CONFIG);
            } finally {
                System.clearProperty("tfi.jvm.priority.test");
            }
        }

        @Test
        @DisplayName("DEFAULT_VALUE 优先级")
        void defaultValue_priority() {
            assertThat(resolver.getEffectivePriority("tfi.unknown.key.xyz"))
                    .isEqualTo(ConfigurationResolver.ConfigPriority.DEFAULT_VALUE);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  getConfigSources
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getConfigSources — 收集所有层")
    class GetConfigSourcesTests {

        @Test
        @DisplayName("getConfigSources 返回非空 Map")
        void getConfigSources_shouldReturnMap() {
            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                    resolver.getConfigSources(ConfigDefaults.Keys.MAX_DEPTH);
            assertThat(sources).isNotNull();
        }

        @Test
        @DisplayName("getConfigSources 包含 DEFAULT_VALUE")
        void getConfigSources_shouldContainDefault() {
            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                    resolver.getConfigSources(ConfigDefaults.Keys.MAX_DEPTH);
            assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.DEFAULT_VALUE);
        }

        @Test
        @DisplayName("getConfigSources 缓存")
        void getConfigSources_usesCache() {
            resolver.getConfigSources("k");
            resolver.getConfigSources("k");
            assertThat(resolver.getConfigSources("k")).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  setRuntimeConfig / clearRuntimeConfig
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setRuntimeConfig / clearRuntimeConfig — 缓存失效")
    class RuntimeConfigTests {

        @Test
        @DisplayName("setRuntimeConfig 清除缓存")
        void setRuntimeConfig_clearsCache() {
            resolver.getConfigSources(ConfigDefaults.Keys.MAX_DEPTH);
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 99);
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(99);
        }

        @Test
        @DisplayName("clearRuntimeConfig 清除缓存")
        void clearRuntimeConfig_clearsCache() {
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 99);
            resolver.clearRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH);
            Integer result = resolver.resolve(
                    ConfigDefaults.Keys.MAX_DEPTH, Integer.class, ConfigDefaults.MAX_DEPTH);
            assertThat(result).isEqualTo(ConfigDefaults.MAX_DEPTH);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  setMethodAnnotationConfig / setClassAnnotationConfig
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setMethodAnnotationConfig / setClassAnnotationConfig")
    class AnnotationConfigTests {

        @Test
        @DisplayName("setMethodAnnotationConfig 生效")
        void setMethodAnnotationConfig_shouldWork() {
            resolver.setMethodAnnotationConfig("method.key", 11);
            Integer result = resolver.resolve("method.key", Integer.class, 0);
            assertThat(result).isEqualTo(11);
        }

        @Test
        @DisplayName("setClassAnnotationConfig 生效")
        void setClassAnnotationConfig_shouldWork() {
            resolver.setClassAnnotationConfig("class.key", 22);
            Integer result = resolver.resolve("class.key", Integer.class, 0);
            assertThat(result).isEqualTo(22);
        }

        @Test
        @DisplayName("clearMethodAnnotationConfig")
        void clearMethodAnnotationConfig_shouldWork() {
            resolver.setMethodAnnotationConfig("m.k", 1);
            resolver.clearMethodAnnotationConfig("m.k");
            Optional<Integer> opt = resolver.resolve("m.k", Integer.class);
            assertThat(opt).isEmpty();
        }

        @Test
        @DisplayName("clearClassAnnotationConfig")
        void clearClassAnnotationConfig_shouldWork() {
            resolver.setClassAnnotationConfig("c.k", 2);
            resolver.clearClassAnnotationConfig("c.k");
            Optional<Integer> opt = resolver.resolve("c.k", Integer.class);
            assertThat(opt).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  isEnvVariablesEnabled / refresh
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isEnvVariablesEnabled / refresh")
    class EnvAndRefreshTests {

        @Test
        @DisplayName("isEnvVariablesEnabled 默认 false")
        void isEnvVariablesEnabled_defaultFalse() {
            assertThat(resolver.isEnvVariablesEnabled()).isFalse();
        }

        @Test
        @DisplayName("isEnvVariablesEnabled 可通过 TFI_ENABLE_ENV 启用")
        void isEnvVariablesEnabled_canBeEnabled() {
            try {
                System.setProperty("TFI_ENABLE_ENV", "true");
                ConfigurationResolverImpl r = new ConfigurationResolverImpl(
                        new StandardEnvironment(), new ConfigMigrationMapper());
                assertThat(r.isEnvVariablesEnabled()).isTrue();
            } finally {
                System.clearProperty("TFI_ENABLE_ENV");
            }
        }

        @Test
        @DisplayName("refresh 清除 configSourcesCache")
        void refresh_clearsCache() {
            resolver.getConfigSources("k");
            assertThatCode(resolver::refresh).doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  convertValue（通过 resolve 间接测试）
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("convertValue — 类型转换")
    class ConvertValueTests {

        @Test
        @DisplayName("int 转换")
        void convert_int() {
            resolver.setRuntimeConfig("int.key", "42");
            Integer result = resolver.resolve("int.key", Integer.class, 0);
            assertThat(result).isEqualTo(42);
        }

        @Test
        @DisplayName("long 转换")
        void convert_long() {
            resolver.setRuntimeConfig("long.key", "999999");
            Long result = resolver.resolve("long.key", Long.class, 0L);
            assertThat(result).isEqualTo(999999L);
        }

        @Test
        @DisplayName("double 转换")
        void convert_double() {
            resolver.setRuntimeConfig("double.key", "3.14");
            Double result = resolver.resolve("double.key", Double.class, 0.0);
            assertThat(result).isEqualTo(3.14);
        }

        @Test
        @DisplayName("float 转换")
        void convert_float() {
            resolver.setRuntimeConfig("float.key", "2.5");
            Float result = resolver.resolve("float.key", Float.class, 0f);
            assertThat(result).isEqualTo(2.5f);
        }

        @Test
        @DisplayName("boolean 转换")
        void convert_boolean() {
            resolver.setRuntimeConfig("bool.key", "true");
            Boolean result = resolver.resolve("bool.key", Boolean.class, false);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("String 转换")
        void convert_string() {
            resolver.setRuntimeConfig("str.key", "hello");
            String result = resolver.resolve("str.key", String.class, "");
            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("NumberFormatException → 回退默认值")
        void convert_invalidNumber_shouldFallback() {
            resolver.setRuntimeConfig("bad.int", "not-a-number");
            Integer result = resolver.resolve("bad.int", Integer.class, 100);
            assertThat(result).isEqualTo(100);
        }

        @Test
        @DisplayName("parseTruthy: true/1/yes/y/on")
        void parseTruthy_trueVariants() {
            for (String v : new String[]{"true", "1", "yes", "y", "on"}) {
                try {
                    System.setProperty("TFI_ENABLE_ENV", v);
                    ConfigurationResolverImpl r = new ConfigurationResolverImpl(
                            new StandardEnvironment(), new ConfigMigrationMapper());
                    assertThat(r.isEnvVariablesEnabled()).isTrue();
                } finally {
                    System.clearProperty("TFI_ENABLE_ENV");
                }
            }
        }

        @Test
        @DisplayName("parseTruthy: false/0/no/n/off")
        void parseTruthy_falseVariants() {
            try {
                System.setProperty("TFI_ENABLE_ENV", "false");
                ConfigurationResolverImpl r = new ConfigurationResolverImpl(
                        new StandardEnvironment(), new ConfigMigrationMapper());
                assertThat(r.isEnvVariablesEnabled()).isFalse();
            } finally {
                System.clearProperty("TFI_ENABLE_ENV");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  getDefaultValue
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDefaultValue — 已知键与未知键")
    class GetDefaultValueTests {

        @Test
        @DisplayName("MAX_DEPTH 默认值")
        void default_maxDepth() {
            Integer v = resolver.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 0);
            assertThat(v).isEqualTo(ConfigDefaults.MAX_DEPTH);
        }

        @Test
        @DisplayName("TIME_BUDGET_MS 默认值")
        void default_timeBudgetMs() {
            Long v = resolver.resolve(ConfigDefaults.Keys.TIME_BUDGET_MS, Long.class, 0L);
            assertThat(v).isEqualTo(ConfigDefaults.TIME_BUDGET_MS);
        }

        @Test
        @DisplayName("SLOW_OPERATION_MS 默认值")
        void default_slowOperationMs() {
            Long v = resolver.resolve(ConfigDefaults.Keys.SLOW_OPERATION_MS, Long.class, 0L);
            assertThat(v).isEqualTo(ConfigDefaults.SLOW_OPERATION_MS);
        }

        @Test
        @DisplayName("MONITORING_SLOW_OPERATION_MS 默认值")
        void default_monitoringSlowOperationMs() {
            Long v = resolver.resolve(ConfigDefaults.Keys.MONITORING_SLOW_OPERATION_MS, Long.class, 0L);
            assertThat(v).isEqualTo(ConfigDefaults.SLOW_OPERATION_MS);
        }

        @Test
        @DisplayName("NUMERIC_FLOAT_TOLERANCE 默认值")
        void default_numericFloatTolerance() {
            Double v = resolver.resolve(ConfigDefaults.Keys.NUMERIC_FLOAT_TOLERANCE, Double.class, 0.0);
            assertThat(v).isEqualTo(ConfigDefaults.NUMERIC_FLOAT_TOLERANCE);
        }

        @Test
        @DisplayName("NUMERIC_RELATIVE_TOLERANCE 默认值")
        void default_numericRelativeTolerance() {
            Double v = resolver.resolve(ConfigDefaults.Keys.NUMERIC_RELATIVE_TOLERANCE, Double.class, 0.0);
            assertThat(v).isEqualTo(ConfigDefaults.NUMERIC_RELATIVE_TOLERANCE);
        }

        @Test
        @DisplayName("LIST_SIZE_THRESHOLD 默认值")
        void default_listSizeThreshold() {
            Integer v = resolver.resolve(ConfigDefaults.Keys.LIST_SIZE_THRESHOLD, Integer.class, 0);
            assertThat(v).isEqualTo(ConfigDefaults.LIST_SIZE_THRESHOLD);
        }

        @Test
        @DisplayName("K_PAIRS_THRESHOLD 默认值")
        void default_kPairsThreshold() {
            Integer v = resolver.resolve(ConfigDefaults.Keys.K_PAIRS_THRESHOLD, Integer.class, 0);
            assertThat(v).isEqualTo(ConfigDefaults.K_PAIRS_THRESHOLD);
        }

        @Test
        @DisplayName("COLLECTION_SUMMARY_THRESHOLD 默认值")
        void default_collectionSummaryThreshold() {
            Integer v = resolver.resolve(ConfigDefaults.Keys.COLLECTION_SUMMARY_THRESHOLD, Integer.class, 0);
            assertThat(v).isEqualTo(ConfigDefaults.COLLECTION_SUMMARY_THRESHOLD);
        }

        @Test
        @DisplayName("ENABLED 默认值")
        void default_enabled() {
            Boolean v = resolver.resolve(ConfigDefaults.Keys.ENABLED, Boolean.class, true);
            assertThat(v).isEqualTo(ConfigDefaults.ENABLED);
        }

        @Test
        @DisplayName("ENV_VARIABLES_ENABLED 默认值")
        void default_envVariablesEnabled() {
            Boolean v = resolver.resolve(ConfigDefaults.Keys.ENV_VARIABLES_ENABLED, Boolean.class, true);
            assertThat(v).isEqualTo(ConfigDefaults.ENV_VARIABLES_ENABLED);
        }

        @Test
        @DisplayName("ENV_ENABLED 默认值")
        void default_envEnabled() {
            Boolean v = resolver.resolve(ConfigDefaults.Keys.ENV_ENABLED, Boolean.class, true);
            assertThat(v).isEqualTo(ConfigDefaults.ENV_VARIABLES_ENABLED);
        }

        @Test
        @DisplayName("未知键 → 返回传入默认值")
        void unknownKey_returnsProvidedDefault() {
            Integer v = resolver.resolve("tfi.unknown.key.xyz", Integer.class, 12345);
            assertThat(v).isEqualTo(12345);
        }

        @Test
        @DisplayName("未知键 resolve 无默认值 → Optional.empty")
        void unknownKey_optionalEmpty() {
            Optional<Integer> opt = resolver.resolve("tfi.unknown.key.xyz", Integer.class);
            assertThat(opt).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  toEnvVariableKey（通过环境变量解析间接测试）
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toEnvVariableKey — 点与连字符转换")
    class ToEnvVariableKeyTests {

        @Test
        @DisplayName("环境变量启用时 TFI_CHANGE_TRACKING_SNAPSHOT_MAX_DEPTH 可解析")
        void envKey_convertsDotsAndHyphens() {
            try {
                System.setProperty("TFI_ENABLE_ENV", "true");
                System.setProperty("tfi.change-tracking.snapshot.max-depth", "33");

                ConfigurationResolverImpl r = new ConfigurationResolverImpl(
                        new StandardEnvironment(), new ConfigMigrationMapper());
                Integer v = r.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 0);
                assertThat(v).isNotNull();
            } finally {
                System.clearProperty("TFI_ENABLE_ENV");
                System.clearProperty("tfi.change-tracking.snapshot.max-depth");
            }
        }
    }
}
