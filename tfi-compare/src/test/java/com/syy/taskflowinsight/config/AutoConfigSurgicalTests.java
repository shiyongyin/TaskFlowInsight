package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.config.resolver.ConfigMigrationMapper;
import com.syy.taskflowinsight.config.resolver.ConfigurationResolver;
import com.syy.taskflowinsight.config.resolver.ConfigurationResolverImpl;
import com.syy.taskflowinsight.metrics.AsyncMetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.CompareRoutingProperties;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.monitoring.DegradationDecisionEngine;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 外科式覆盖测试：ChangeTrackingAutoConfiguration、ConfigurationResolverImpl.getConfigSources、
 * ListCompareExecutor.compare 的剩余未覆盖路径。
 * <p>
 * 使用 AnnotationConfigApplicationContext 或手动构造 + 反射调用 @PostConstruct，
 * 不依赖完整 @SpringBootTest。
 * </p>
 *
 * @since 3.0.0
 */
@DisplayName("AutoConfig Surgical — 外科式配置与列表比较覆盖测试")
class AutoConfigSurgicalTests {

    private int originalMaxValueLength;

    @BeforeEach
    void saveObjectSnapshotState() {
        originalMaxValueLength = ObjectSnapshot.getMaxValueLength();
    }

    @AfterEach
    void restoreObjectSnapshotState() {
        ObjectSnapshot.setMaxValueLength(originalMaxValueLength);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ChangeTrackingAutoConfiguration
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ChangeTrackingAutoConfiguration — 初始化与配置")
    class ChangeTrackingAutoConfigurationTests {

        @Test
        @DisplayName("initializeConfiguration — 无效配置时提前返回")
        void initializeConfiguration_invalidConfig_returnsEarly() {
            TfiConfig.ChangeTracking ct = new TfiConfig.ChangeTracking(
                true, 2_000_000, 5, null, null, null, 1024, null);
            TfiConfig config = new TfiConfig(true, ct, null, null, null);
            ChangeTrackingAutoConfiguration autoConfig = new ChangeTrackingAutoConfiguration(config);

            assertThatCode(autoConfig::initializeConfiguration).doesNotThrowAnyException();
            // 无效配置时不应修改 ObjectSnapshot
            assertThat(ObjectSnapshot.getMaxValueLength()).isEqualTo(originalMaxValueLength);
        }

        @Test
        @DisplayName("initializeConfiguration — 有效配置时应用 ObjectSnapshot")
        void initializeConfiguration_validConfig_appliesObjectSnapshot() {
            TfiConfig config = createValidTfiConfig();
            ChangeTrackingAutoConfiguration autoConfig = new ChangeTrackingAutoConfiguration(config);

            autoConfig.initializeConfiguration();

            assertThat(ObjectSnapshot.getMaxValueLength()).isEqualTo(4096);
        }

        @Test
        @DisplayName("initializeConfiguration — 带 Environment 时 getProperty 生效")
        void initializeConfiguration_withEnvironment_usesGetProperty() throws Exception {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("tfi.change-tracking.datetime.tolerance-ms", "500");
            env.setProperty("tfi.change-tracking.datetime.default-format", "yyyy-MM-dd");
            env.setProperty("tfi.change-tracking.datetime.timezone", "UTC");

            TfiConfig config = createValidTfiConfig();
            ChangeTrackingAutoConfiguration autoConfig = new ChangeTrackingAutoConfiguration(config);
            injectField(autoConfig, "environment", env);

            assertThatCode(autoConfig::initializeConfiguration).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("initializeConfiguration — 带 ConfigurationResolverImpl 时 resolveDouble 优先")
        void initializeConfiguration_withResolver_usesResolveDouble() throws Exception {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setRuntimeConfig(ConfigDefaults.Keys.NUMERIC_FLOAT_TOLERANCE, 1e-10);
            resolver.setRuntimeConfig(ConfigDefaults.Keys.NUMERIC_RELATIVE_TOLERANCE, 1e-8);

            TfiConfig config = createValidTfiConfig();
            ChangeTrackingAutoConfiguration autoConfig = new ChangeTrackingAutoConfiguration(config);
            injectField(autoConfig, "resolver", resolver);
            injectField(autoConfig, "environment", env);

            assertThatCode(autoConfig::initializeConfiguration).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("initializeConfiguration — 带 AsyncMetricsCollector 时注册日志")
        void initializeConfiguration_withAsyncMetricsCollector_logsRegistration() throws Exception {
            AsyncMetricsCollector collector = new AsyncMetricsCollector(new SimpleMeterRegistry());
            TfiConfig config = createValidTfiConfig();
            ChangeTrackingAutoConfiguration autoConfig = new ChangeTrackingAutoConfiguration(config);
            injectField(autoConfig, "asyncMetricsCollector", collector);

            assertThatCode(autoConfig::initializeConfiguration).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("configureDiffDetector — enhanced 模式")
        void configureDiffDetector_enhancedMode() {
            TfiConfig.ChangeTracking.Diff diff = new TfiConfig.ChangeTracking.Diff(
                "enhanced", false, 1000, true, "legacy");
            TfiConfig.ChangeTracking ct = new TfiConfig.ChangeTracking(
                true, 8192, 5, null, diff, null, 1024, null);
            TfiConfig config = new TfiConfig(true, ct, null, null, null);
            ChangeTrackingAutoConfiguration autoConfig = new ChangeTrackingAutoConfiguration(config);

            assertThatCode(autoConfig::initializeConfiguration).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("propertyComparatorRegistry Bean")
        void propertyComparatorRegistry_bean() {
            TfiConfig config = createValidTfiConfig();
            ChangeTrackingAutoConfiguration autoConfig = new ChangeTrackingAutoConfiguration(config);

            var registry = autoConfig.propertyComparatorRegistry();
            assertThat(registry).isNotNull();
        }

        @Test
        @DisplayName("diffFacadeAppContextInjector 静态 Bean")
        void diffFacadeAppContextInjector_staticBean() {
            var injector = ChangeTrackingAutoConfiguration.diffFacadeAppContextInjector();
            assertThat(injector).isNotNull();
        }

        @Test
        @DisplayName("snapshotProvidersAppContextInjector 静态 Bean")
        void snapshotProvidersAppContextInjector_staticBean() {
            var injector = ChangeTrackingAutoConfiguration.snapshotProvidersAppContextInjector();
            assertThat(injector).isNotNull();
        }

        private TfiConfig createValidTfiConfig() {
            TfiConfig.ChangeTracking.Snapshot snapshot = new TfiConfig.ChangeTracking.Snapshot(
                10, 100, null, 1000, false, 1000L);
            TfiConfig.ChangeTracking.Diff diff = new TfiConfig.ChangeTracking.Diff(
                "compat", false, 1000, true, "legacy");
            TfiConfig.ChangeTracking.Export export = new TfiConfig.ChangeTracking.Export(
                "json", true, false, false, false);
            TfiConfig.ChangeTracking ct = new TfiConfig.ChangeTracking(
                true, 4096, 5, snapshot, diff, export, 1024, null);
            TfiConfig.Context ctx = new TfiConfig.Context(
                3600000L, false, 60000L, false, 60000L);
            return new TfiConfig(true, ct, ctx, null, null);
        }

        private void injectField(Object target, String fieldName, Object value) throws Exception {
            Field f = ChangeTrackingAutoConfiguration.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ConfigurationResolverImpl.getConfigSources
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ConfigurationResolverImpl.getConfigSources — 7 层与迁移键")
    class GetConfigSourcesTests {

        @Test
        @DisplayName("getConfigSources — 收集 RUNTIME_API 层")
        void getConfigSources_runtimeApi() {
            MockEnvironment env = new MockEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 20);

            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                resolver.getConfigSources(ConfigDefaults.Keys.MAX_DEPTH);

            assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.RUNTIME_API);
            assertThat(sources.get(ConfigurationResolver.ConfigPriority.RUNTIME_API).value()).isEqualTo(20);
        }

        @Test
        @DisplayName("getConfigSources — 收集 METHOD_ANNOTATION 层")
        void getConfigSources_methodAnnotation() {
            MockEnvironment env = new MockEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setMethodAnnotationConfig("tfi.test.method.key", 11);

            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                resolver.getConfigSources("tfi.test.method.key");

            assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.METHOD_ANNOTATION);
        }

        @Test
        @DisplayName("getConfigSources — 收集 CLASS_ANNOTATION 层")
        void getConfigSources_classAnnotation() {
            MockEnvironment env = new MockEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setClassAnnotationConfig("tfi.test.class.key", 22);

            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                resolver.getConfigSources("tfi.test.class.key");

            assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.CLASS_ANNOTATION);
        }

        @Test
        @DisplayName("getConfigSources — 收集 SPRING_CONFIG 层")
        void getConfigSources_springConfig() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty(ConfigDefaults.Keys.MAX_DEPTH, "25");
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());

            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                resolver.getConfigSources(ConfigDefaults.Keys.MAX_DEPTH);

            assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.SPRING_CONFIG);
            assertThat(sources.get(ConfigurationResolver.ConfigPriority.SPRING_CONFIG).value()).isEqualTo("25");
        }

        @Test
        @DisplayName("getConfigSources — 迁移键 mappedNew 来源")
        void getConfigSources_migrationMappedNew() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("tfi.change-tracking.snapshot.max-depth", "30");
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());

            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                resolver.getConfigSources("tfi.change-tracking.max-depth");

            assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.SPRING_CONFIG);
        }

        @Test
        @DisplayName("getConfigSources — 迁移键 mappedOld 来源")
        void getConfigSources_migrationMappedOld() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("tfi.change-tracking.max-depth", "35");
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, mapper);

            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                resolver.getConfigSources("tfi.change-tracking.snapshot.max-depth");

            assertThat(sources).isNotNull();
        }

        @Test
        @DisplayName("getConfigSources — JVM_PROPERTY 层")
        void getConfigSources_jvmProperty() {
            try {
                System.setProperty("tfi.getconfigsources.jvm.test", "99");
                MockEnvironment env = new MockEnvironment();
                ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());

                Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                    resolver.getConfigSources("tfi.getconfigsources.jvm.test");

                assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.JVM_PROPERTY);
            } finally {
                System.clearProperty("tfi.getconfigsources.jvm.test");
            }
        }

        @Test
        @DisplayName("getConfigSources — DEFAULT_VALUE 层")
        void getConfigSources_defaultValue() {
            MockEnvironment env = new MockEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());

            Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                resolver.getConfigSources(ConfigDefaults.Keys.MAX_DEPTH);

            assertThat(sources).containsKey(ConfigurationResolver.ConfigPriority.DEFAULT_VALUE);
            assertThat(sources.get(ConfigurationResolver.ConfigPriority.DEFAULT_VALUE).value())
                .isEqualTo(ConfigDefaults.MAX_DEPTH);
        }

        @Test
        @DisplayName("getConfigSources — 环境变量短名映射（启用时）")
        void getConfigSources_envShortName() {
            try {
                System.setProperty("TFI_ENABLE_ENV", "true");
                System.setProperty("TFI_MAX_DEPTH", "42");
                MockEnvironment env = new MockEnvironment();
                ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());

                Map<ConfigurationResolver.ConfigPriority, ConfigurationResolver.ConfigSource> sources =
                    resolver.getConfigSources("tfi.change-tracking.snapshot.max-depth");

                assertThat(sources).isNotNull();
            } finally {
                System.clearProperty("TFI_ENABLE_ENV");
                System.clearProperty("TFI_MAX_DEPTH");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ListCompareExecutor.compare
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ListCompareExecutor.compare — 策略路由与降级")
    class ListCompareExecutorCompareTests {

        private ListCompareExecutor createExecutor() {
            return new ListCompareExecutor(List.of(
                new SimpleListStrategy(),
                new AsSetListStrategy(),
                new LcsListStrategy(),
                new LevenshteinListStrategy(),
                new EntityListStrategy()
            ));
        }

        @Test
        @DisplayName("指定策略不存在 — 降级到自动路由")
        void compare_specifiedStrategyNotFound_fallbackToAutoRoute() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("NONEXISTENT").build();

            CompareResult result = executor.compare(
                List.of("a", "b"), List.of("a", "x"), opts);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("Entity 自动路由 — @Entity 列表")
        void compare_entityAutoRoute_entityList() {
            ListCompareExecutor executor = createExecutor();
            List<EntityWithKey> before = List.of(new EntityWithKey(1, "A"));
            List<EntityWithKey> after = List.of(new EntityWithKey(1, "B"));

            CompareResult result = executor.compare(before, after, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("ENTITY");
        }

        @Test
        @DisplayName("Entity 自动路由 — 仅 list2 为实体列表")
        void compare_entityAutoRoute_onlyList2Entity() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = List.of("a");
            List<EntityWithKey> after = List.of(new EntityWithKey(1, "x"));

            CompareResult result = executor.compare(before, after, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("ENTITY");
        }

        @Test
        @DisplayName("LCS 自动路由 — detectMoves + preferLcs（需注入 CompareRoutingProperties）")
        void compare_lcsAutoRoute_detectMovesPreferLcs() throws Exception {
            CompareRoutingProperties props = new CompareRoutingProperties();
            props.getLcs().setEnabled(true);
            props.getLcs().setPreferLcsWhenDetectMoves(true);

            ListCompareExecutor executor = createExecutor();
            injectField(executor, "autoRouteProps", props);

            CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("b", "a", "c");

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("LCS");
        }

        @Test
        @DisplayName("大小降级 — >=1000 强制 SIMPLE")
        void compare_sizeDegradation_forceSimple() {
            ListCompareExecutor executor = createExecutor();
            List<String> large = new ArrayList<>(Collections.nCopies(1000, "x"));
            List<String> large2 = new ArrayList<>(Collections.nCopies(1000, "y"));

            CompareResult result = executor.compare(large, large2, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("大小降级 — 500–999 降级到 SIMPLE")
        void compare_sizeDegradation_500to999() {
            ListCompareExecutor executor = createExecutor();
            List<String> mid = new ArrayList<>(Collections.nCopies(600, "a"));
            List<String> mid2 = new ArrayList<>(Collections.nCopies(600, "b"));

            CompareResult result = executor.compare(mid, mid2, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("K 对数降级 — DegradationDecisionEngine")
        void compare_kPairsDegradation() throws Exception {
            var config = new com.syy.taskflowinsight.tracking.monitoring.DegradationConfig(
                null, null, null, null, null, null, null,
                new com.syy.taskflowinsight.tracking.monitoring.DegradationConfig.MemoryThresholds(null, null, null, null),
                new com.syy.taskflowinsight.tracking.monitoring.DegradationConfig.PerformanceThresholds(null, null, null),
                null, null, 100);
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);

            ListCompareExecutor executor = createExecutor();
            injectField(executor, "degradationDecisionEngine", engine);

            List<String> a = new ArrayList<>(Collections.nCopies(20, "a"));
            List<String> b = new ArrayList<>(Collections.nCopies(20, "b"));

            CompareResult result = executor.compare(a, b, CompareOptions.builder().strategyName("ENTITY").build());

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("calculateSimilarity — 空并集")
        void compare_calculateSimilarity_emptyUnion() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();

            CompareResult result = executor.compare(
                List.of(), List.of(), opts);

            assertThat(result).isNotNull();
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("calculateSimilarity — 非空并集")
        void compare_calculateSimilarity_nonEmptyUnion() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();

            CompareResult result = executor.compare(
                List.of("a", "b"), List.of("a", "c"), opts);

            assertThat(result).isNotNull();
            assertThat(result.getSimilarity()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Entity 自动路由禁用 — 回退 SIMPLE")
        void compare_entityAutoRouteDisabled_fallbackSimple() throws Exception {
            CompareRoutingProperties props = new CompareRoutingProperties();
            props.getEntity().setEnabled(false);

            ListCompareExecutor executor = createExecutor();
            injectField(executor, "autoRouteProps", props);

            List<EntityWithKey> before = List.of(new EntityWithKey(1, "A"));
            List<EntityWithKey> after = List.of(new EntityWithKey(1, "B"));

            CompareResult result = executor.compare(before, after, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("LCS 自动路由禁用 — 回退 SIMPLE")
        void compare_lcsAutoRouteDisabled_fallbackSimple() throws Exception {
            CompareRoutingProperties props = new CompareRoutingProperties();
            props.getLcs().setEnabled(false);

            ListCompareExecutor executor = createExecutor();
            injectField(executor, "autoRouteProps", props);

            CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
            CompareResult result = executor.compare(
                List.of("a", "b", "c"), List.of("b", "a", "c"), opts);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("getDegradationCount 递增")
        void compare_degradationCountIncrements() {
            ListCompareExecutor executor = createExecutor();
            long before = executor.getDegradationCount();

            executor.compare(
                new ArrayList<>(Collections.nCopies(600, "x")),
                new ArrayList<>(Collections.nCopies(600, "y")),
                CompareOptions.DEFAULT);

            assertThat(executor.getDegradationCount()).isGreaterThan(before);
        }

        private void injectField(Object target, String fieldName, Object value) throws Exception {
            Field f = ListCompareExecutor.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        }

        @Entity
        static class EntityWithKey {
            @Key
            private final int id;
            @SuppressWarnings("unused")
            private String name;

            EntityWithKey(int id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                EntityWithKey that = (EntityWithKey) o;
                return id == that.id;
            }

            @Override
            public int hashCode() {
                return Integer.hashCode(id);
            }
        }
    }
}
