package com.syy.taskflowinsight.config;

import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.config.resolver.ConfigMigrationMapper;
import com.syy.taskflowinsight.config.resolver.ConfigurationResolver;
import com.syy.taskflowinsight.config.resolver.ConfigurationResolverImpl;
import com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.monitoring.*;
import com.syy.taskflowinsight.tracking.precision.PrecisionController;
import com.syy.taskflowinsight.tracking.snapshot.filter.PathLevelFilterEngine;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Branch coverage tests for config, config/resolver, monitoring, precision,
 * tracking root, filter, spi, concurrent, format, ssot.
 * Uses StandardEnvironment, no @SpringBootTest.
 *
 * @since 3.0.0
 */
@DisplayName("Config Branch Coverage — 配置分支覆盖测试")
class ConfigBranchCoverageTests {

    @AfterEach
    void tearDown() {
        DegradationContext.reset();
    }

    @Nested
    @DisplayName("ConfigurationResolver — All priority levels and type conversions")
    class ConfigurationResolverTests {

        @Test
        @DisplayName("resolve with runtime config highest priority")
        void resolve_runtimeConfig() {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 15);
            Integer val = resolver.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 10);
            assertThat(val).isEqualTo(15);
        }

        @Test
        @DisplayName("resolve String to Integer conversion")
        void resolve_stringToInteger() {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setRuntimeConfig("test.key", "42");
            Integer val = resolver.resolve("test.key", Integer.class, 0);
            assertThat(val).isEqualTo(42);
        }

        @Test
        @DisplayName("resolve String to Boolean conversion")
        void resolve_stringToBoolean() {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setRuntimeConfig("test.bool", "true");
            Boolean val = resolver.resolve("test.bool", Boolean.class, false);
            assertThat(val).isTrue();
        }

        @Test
        @DisplayName("resolve String to Long conversion")
        void resolve_stringToLong() {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setRuntimeConfig("test.long", "999");
            Long val = resolver.resolve("test.long", Long.class, 0L);
            assertThat(val).isEqualTo(999L);
        }

        @Test
        @DisplayName("getEffectivePriority DEFAULT_VALUE for unknown key")
        void getEffectivePriority_default() {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            assertThat(resolver.getEffectivePriority("tfi.unknown.key"))
                    .isEqualTo(ConfigurationResolver.ConfigPriority.DEFAULT_VALUE);
        }

        @Test
        @DisplayName("getEffectivePriority RUNTIME_API")
        void getEffectivePriority_runtimeApi() {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 5);
            assertThat(resolver.getEffectivePriority(ConfigDefaults.Keys.MAX_DEPTH))
                    .isEqualTo(ConfigurationResolver.ConfigPriority.RUNTIME_API);
        }

        @Test
        @DisplayName("clearRuntimeConfig")
        void clearRuntimeConfig() {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.setRuntimeConfig("k", "v");
            resolver.clearRuntimeConfig("k");
            assertThat(resolver.resolve("k", String.class, "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("refresh clears cache")
        void refresh() {
            StandardEnvironment env = new StandardEnvironment();
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(env, new ConfigMigrationMapper());
            resolver.refresh();
            assertThat(resolver.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("ConfigPriority hasHigherPriorityThan")
        void configPriority_hasHigherPriorityThan() {
            assertThat(ConfigurationResolver.ConfigPriority.RUNTIME_API
                    .hasHigherPriorityThan(ConfigurationResolver.ConfigPriority.DEFAULT_VALUE)).isTrue();
            assertThat(ConfigurationResolver.ConfigPriority.DEFAULT_VALUE
                    .hasHigherPriorityThan(ConfigurationResolver.ConfigPriority.RUNTIME_API)).isFalse();
        }

        @Test
        @DisplayName("ConfigSource toString")
        void configSource_toString() {
            ConfigurationResolver.ConfigSource src = new ConfigurationResolver.ConfigSource(
                    ConfigurationResolver.ConfigPriority.RUNTIME_API, "key", "val", "detail");
            assertThat(src.toString()).contains("Runtime API").contains("key").contains("val");
        }
    }

    @Nested
    @DisplayName("ConfigMigrationMapper")
    class ConfigMigrationMapperTests {

        @Test
        @DisplayName("checkAndMigrate returns new key for deprecated")
        void checkAndMigrate_deprecated() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            String result = mapper.checkAndMigrate("tfi.change-tracking.max-depth");
            assertThat(result).isEqualTo("tfi.change-tracking.snapshot.max-depth");
        }

        @Test
        @DisplayName("isDeprecatedKey")
        void isDeprecatedKey() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            assertThat(mapper.isDeprecatedKey("tfi.change-tracking.max-depth")).isTrue();
            assertThat(mapper.isDeprecatedKey("tfi.unknown")).isFalse();
        }

        @Test
        @DisplayName("getOldKeyForNew")
        void getOldKeyForNew() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            String old = mapper.getOldKeyForNew("tfi.change-tracking.snapshot.max-depth");
            assertThat(old).isEqualTo("tfi.change-tracking.max-depth");
        }

        @Test
        @DisplayName("getMigrationReport empty")
        void getMigrationReport_empty() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            mapper.clearWarnings();
            assertThat(mapper.getMigrationReport()).contains("No deprecated");
        }

        @Test
        @DisplayName("getAllMappings")
        void getAllMappings() {
            Map<String, String> mappings = ConfigMigrationMapper.getAllMappings();
            assertThat(mappings).isNotEmpty().containsKey("tfi.change-tracking.max-depth");
        }
    }

    @Nested
    @DisplayName("DegradationDecisionEngine — All threshold paths")
    class DegradationDecisionEngineTests {

        @Test
        @DisplayName("calculateOptimalLevel memory pressure")
        void calculateOptimalLevel_memoryPressure() {
            DegradationConfig config = new DegradationConfig(null, null, null, null, null, null, null,
                    new DegradationConfig.MemoryThresholds(null, null, null, null),
                    new DegradationConfig.PerformanceThresholds(null, null, null),
                    null, null, null);
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                    .memoryUsagePercent(95.0)
                    .availableMemoryMB(50)
                    .averageOperationTime(Duration.ofMillis(50))
                    .cpuUsagePercent(30)
                    .threadCount(10)
                    .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, null);
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("calculateOptimalLevel performance severe")
        void calculateOptimalLevel_performanceSevere() {
            DegradationConfig config = new DegradationConfig(null, null, null, null, null, null, null,
                    new DegradationConfig.MemoryThresholds(null, null, null, null),
                    new DegradationConfig.PerformanceThresholds(null, null, null),
                    null, null, null);
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            SystemMetrics metrics = SystemMetrics.builder()
                    .memoryUsagePercent(30)
                    .availableMemoryMB(1000)
                    .averageOperationTime(Duration.ofMillis(2000))
                    .cpuUsagePercent(30)
                    .threadCount(10)
                    .build();
            DegradationLevel level = engine.calculateOptimalLevel(metrics, null);
            assertThat(level).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("shouldDegradeForListSize")
        void shouldDegradeForListSize() {
            DegradationConfig config = new DegradationConfig(null, null, null, null, null, null, null,
                    null, null, 500, null, null);
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            assertThat(engine.shouldDegradeForListSize(600)).isTrue();
            assertThat(engine.shouldDegradeForListSize(400)).isFalse();
        }

        @Test
        @DisplayName("shouldDegradeForKPairs")
        void shouldDegradeForKPairs() {
            DegradationConfig config = new DegradationConfig(null, null, null, null, null, null, null,
                    null, null, null, null, 10000);
            DegradationDecisionEngine engine = new DegradationDecisionEngine(config);
            assertThat(engine.shouldDegradeForKPairs(15000)).isTrue();
        }
    }

    @Nested
    @DisplayName("DegradationContext — State transitions")
    class DegradationContextTests {

        @Test
        @DisplayName("setCurrentLevel and getCurrentLevel")
        void setAndGetLevel() {
            DegradationContext.setCurrentLevel(DegradationLevel.SKIP_DEEP_ANALYSIS);
            assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
            DegradationContext.reset();
        }

        @Test
        @DisplayName("setCurrentLevel null ignored")
        void setCurrentLevel_nullIgnored() {
            DegradationContext.setCurrentLevel(DegradationLevel.DISABLED);
            DegradationContext.setCurrentLevel(null);
            assertThat(DegradationContext.getCurrentLevel()).isEqualTo(DegradationLevel.DISABLED);
            DegradationContext.reset();
        }

        @Test
        @DisplayName("clear")
        void clear() {
            DegradationContext.setCurrentLevel(DegradationLevel.SUMMARY_ONLY);
            DegradationContext.clear();
            DegradationContext.reset();
        }
    }

    @Nested
    @DisplayName("DegradationLevel")
    class DegradationLevelTests {

        @Test
        @DisplayName("getNextMoreRestrictive")
        void getNextMoreRestrictive() {
            assertThat(DegradationLevel.FULL_TRACKING.getNextMoreRestrictive()).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
            assertThat(DegradationLevel.DISABLED.getNextMoreRestrictive()).isEqualTo(DegradationLevel.DISABLED);
        }

        @Test
        @DisplayName("getNextLessRestrictive")
        void getNextLessRestrictive() {
            assertThat(DegradationLevel.DISABLED.getNextLessRestrictive()).isEqualTo(DegradationLevel.SUMMARY_ONLY);
            assertThat(DegradationLevel.FULL_TRACKING.getNextLessRestrictive()).isEqualTo(DegradationLevel.FULL_TRACKING);
        }

        @Test
        @DisplayName("isMoreRestrictiveThan")
        void isMoreRestrictiveThan() {
            assertThat(DegradationLevel.DISABLED.isMoreRestrictiveThan(DegradationLevel.FULL_TRACKING)).isTrue();
            assertThat(DegradationLevel.FULL_TRACKING.isMoreRestrictiveThan(DegradationLevel.DISABLED)).isFalse();
        }
    }

    @Nested
    @DisplayName("DegradationConfig — MemoryThresholds")
    class DegradationConfigMemoryThresholds {

        @Test
        @DisplayName("getDegradationLevelForMemory all branches")
        void getDegradationLevelForMemory() {
            DegradationConfig.MemoryThresholds mt = new DegradationConfig.MemoryThresholds(null, null, null, null);
            assertThat(mt.getDegradationLevelForMemory(95)).isEqualTo(DegradationLevel.DISABLED);
            assertThat(mt.getDegradationLevelForMemory(85)).isEqualTo(DegradationLevel.SUMMARY_ONLY);
            assertThat(mt.getDegradationLevelForMemory(75)).isEqualTo(DegradationLevel.SIMPLE_COMPARISON);
            assertThat(mt.getDegradationLevelForMemory(65)).isEqualTo(DegradationLevel.SKIP_DEEP_ANALYSIS);
            assertThat(mt.getDegradationLevelForMemory(50)).isEqualTo(DegradationLevel.FULL_TRACKING);
        }
    }

    @Nested
    @DisplayName("PrecisionController — @NumericPrecision and @DateFormat")
    class PrecisionControllerTests {

        @Test
        @DisplayName("getFieldPrecision null field returns default")
        void getFieldPrecision_nullField() {
            PrecisionController pc = new PrecisionController();
            PrecisionController.PrecisionSettings s = pc.getFieldPrecision(null);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("resolveToleranceForField near zero uses absolute")
        void resolveTolerance_nearZero() throws Exception {
            PrecisionController pc = new PrecisionController();
            Field f = TestBean.class.getDeclaredField("value");
            double tol = pc.resolveToleranceForField(f, 0.0000001);
            assertThat(tol).isEqualTo(ConfigDefaults.NUMERIC_FLOAT_TOLERANCE);
        }

        @Test
        @DisplayName("resolveToleranceForField large value uses relative")
        void resolveTolerance_largeValue() throws Exception {
            PrecisionController pc = new PrecisionController();
            Field f = TestBean.class.getDeclaredField("value");
            double tol = pc.resolveToleranceForField(f, 1000.0);
            assertThat(tol).isGreaterThan(0);
        }

        @Test
        @DisplayName("applyRoundingMode with scale >= 0")
        void applyRoundingMode_withScale() throws Exception {
            PrecisionController pc = new PrecisionController();
            Field f = AnnotatedBean.class.getDeclaredField("amount");
            BigDecimal result = pc.applyRoundingMode(new BigDecimal("1.23456"), f);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("validatePrecisionSettings errors and warnings")
        void validatePrecisionSettings() {
            PrecisionController pc = new PrecisionController();
            PrecisionController.PrecisionSettings bad = PrecisionController.PrecisionSettings.builder()
                    .absoluteTolerance(-0.1)
                    .relativeTolerance(-0.1)
                    .dateToleranceMs(-1)
                    .build();
            PrecisionController.ValidationResult r = pc.validatePrecisionSettings(bad);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("getWithFallback")
        void getWithFallback() throws Exception {
            PrecisionController pc = new PrecisionController();
            Field f = TestBean.class.getDeclaredField("value");
            PrecisionController.PrecisionSettings s = pc.getWithFallback(f);
            assertThat(s).isNotNull();
        }

        @Test
        @DisplayName("PrecisionSettings systemDefaults")
        void precisionSettings_systemDefaults() {
            PrecisionController.PrecisionSettings s = PrecisionController.PrecisionSettings.systemDefaults();
            assertThat(s.getAbsoluteTolerance()).isEqualTo(ConfigDefaults.NUMERIC_FLOAT_TOLERANCE);
        }

        static class TestBean { double value; }
        static class AnnotatedBean {
            @NumericPrecision(scale = 2)
            BigDecimal amount;
        }
    }

    @Nested
    @DisplayName("TfiDateTimeFormatter — All temporal types")
    class TfiDateTimeFormatterTests {

        @Test
        @DisplayName("format ZonedDateTime")
        void format_zonedDateTime() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            ZonedDateTime zdt = ZonedDateTime.of(2025, 1, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
            assertThat(fmt.format(zdt)).isNotNull();
        }

        @Test
        @DisplayName("format LocalDateTime")
        void format_localDateTime() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            LocalDateTime ldt = LocalDateTime.of(2025, 1, 15, 10, 30);
            assertThat(fmt.format(ldt)).isNotNull();
        }

        @Test
        @DisplayName("format Instant")
        void format_instant() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Instant inst = Instant.now();
            assertThat(fmt.format(inst)).isNotNull();
        }

        @Test
        @DisplayName("format LocalDate")
        void format_localDate() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            LocalDate ld = LocalDate.of(2025, 1, 15);
            assertThat(fmt.format(ld)).isNotNull();
        }

        @Test
        @DisplayName("format LocalTime")
        void format_localTime() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            LocalTime lt = LocalTime.of(10, 30, 0);
            assertThat(fmt.format(lt)).isNotNull();
        }

        @Test
        @DisplayName("format null returns null")
        void format_null() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.format((java.time.temporal.Temporal) null)).isNull();
        }

        @Test
        @DisplayName("formatDuration")
        void formatDuration() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofHours(2).plusMinutes(30);
            assertThat(fmt.formatDuration(d)).contains("2H").contains("30M");
        }

        @Test
        @DisplayName("formatDuration null")
        void formatDuration_null() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.formatDuration(null)).isNull();
        }

        @Test
        @DisplayName("formatPeriod")
        void formatPeriod() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Period p = Period.of(1, 2, 3);
            assertThat(fmt.formatPeriod(p)).contains("1Y").contains("2M").contains("3D");
        }

        @Test
        @DisplayName("parseWithTolerance LocalDate")
        void parseWithTolerance_localDate() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            java.time.temporal.Temporal t = fmt.parseWithTolerance("2025-01-15", LocalDate.class);
            assertThat(t).isInstanceOf(LocalDate.class);
        }

        @Test
        @DisplayName("parseDuration")
        void parseDuration() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = fmt.parseDuration("PT2H30M");
            assertThat(d).isNotNull();
        }

        @Test
        @DisplayName("parsePeriod")
        void parsePeriod() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Period p = fmt.parsePeriod("P1Y2M3D");
            assertThat(p).isNotNull();
        }

        @Test
        @DisplayName("constructor with null pattern uses default")
        void constructor_nullPattern() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter(null, "UTC");
            assertThat(fmt.getDefaultPattern()).isNotNull();
        }

        @Test
        @DisplayName("resolveZoneId UTC")
        void resolveZoneId_utc() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter("yyyy-MM-dd", "UTC");
            assertThat(fmt.getDefaultZoneId()).isEqualTo(ZoneOffset.UTC);
        }
    }

    @Nested
    @DisplayName("PathUtils — Path building and parsing")
    class PathUtilsTests {

        @Test
        @DisplayName("buildEntityPath")
        void buildEntityPath() {
            String path = PathUtils.buildEntityPath("id=1", "name");
            assertThat(path).contains("entity").contains("id=1").contains("name");
        }

        @Test
        @DisplayName("buildEntityPathWithDup")
        void buildEntityPathWithDup() {
            String path = PathUtils.buildEntityPathWithDup("id", 2, "field");
            assertThat(path).contains("id#2");
        }

        @Test
        @DisplayName("buildMapValuePath")
        void buildMapValuePath() {
            String path = PathUtils.buildMapValuePath("key");
            assertThat(path).contains("map").contains("key");
        }

        @Test
        @DisplayName("buildListIndexPath")
        void buildListIndexPath() {
            String path = PathUtils.buildListIndexPath(0, "id");
            assertThat(path).contains("[0]").contains("id");
        }

        @Test
        @DisplayName("unescape")
        void unescape() {
            assertThat(PathUtils.unescape("a\\:b")).isEqualTo("a:b");
            assertThat(PathUtils.unescape(null)).isNull();
        }

        @Test
        @DisplayName("parse entity path")
        void parse_entityPath() {
            PathUtils.KeyFieldPair pair = PathUtils.parse("entity[id=1].name");
            assertThat(pair.key()).contains("entity").contains("id=1");
            assertThat(pair.field()).isEqualTo("name");
        }

        @Test
        @DisplayName("parse non-entity path")
        void parse_nonEntityPath() {
            PathUtils.KeyFieldPair pair = PathUtils.parse("simple.path");
            assertThat(pair.key()).isEqualTo("-");
            assertThat(pair.field()).isEqualTo("simple.path");
        }
    }

    @Nested
    @DisplayName("EntityKeyUtils")
    class EntityKeyUtilsTests {

        @Test
        @DisplayName("tryComputeStableKey null returns empty")
        void tryComputeStableKey_null() {
            assertThat(EntityKeyUtils.tryComputeStableKey(null)).isEmpty();
        }

        @Test
        @DisplayName("computeStableKeyOrNull null returns null")
        void computeStableKeyOrNull_null() {
            assertThat(EntityKeyUtils.computeStableKeyOrNull(null)).isNull();
        }

        @Test
        @DisplayName("computeCompactKeyOrUnresolved object without Key")
        void computeCompactKeyOrUnresolved_noKey() {
            assertThat(EntityKeyUtils.computeCompactKeyOrUnresolved(new Object())).isEqualTo(EntityKeyUtils.UNRESOLVED);
        }

        @Test
        @DisplayName("computeReferenceIdentifier null")
        void computeReferenceIdentifier_null() {
            assertThat(EntityKeyUtils.computeReferenceIdentifier(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("computeReferenceIdentifier object without Key")
        void computeReferenceIdentifier_noKey() {
            String id = EntityKeyUtils.computeReferenceIdentifier(new Object());
            assertThat(id).contains("@");
        }

        @Test
        @DisplayName("collectKeyFields")
        void collectKeyFields() {
            List<Field> fields = EntityKeyUtils.collectKeyFields(Object.class);
            assertThat(fields).isEmpty();
        }
    }

    @Nested
    @DisplayName("PathLevelFilterEngine")
    class PathLevelFilterEngineTests {

        @Test
        @DisplayName("matchesIncludePatterns null path")
        void matchesIncludePatterns_nullPath() {
            assertThat(PathLevelFilterEngine.matchesIncludePatterns(null, Set.of("*.name"))).isFalse();
        }

        @Test
        @DisplayName("matchesIncludePatterns empty patterns")
        void matchesIncludePatterns_emptyPatterns() {
            assertThat(PathLevelFilterEngine.matchesIncludePatterns("user.name", Collections.emptySet())).isFalse();
        }

        @Test
        @DisplayName("shouldIgnoreByPath null path")
        void shouldIgnoreByPath_nullPath() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath(null, Set.of("*.secret"), Collections.emptySet())).isFalse();
        }

        @Test
        @DisplayName("shouldIgnoreByPath glob match")
        void shouldIgnoreByPath_globMatch() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("user.secret", Set.of("*.secret"), Collections.emptySet())).isTrue();
        }

        @Test
        @DisplayName("shouldIgnoreByPath regex match")
        void shouldIgnoreByPath_regexMatch() {
            assertThat(PathLevelFilterEngine.shouldIgnoreByPath("debug_1234", Collections.emptySet(), Set.of("^debug.*"))).isTrue();
        }
    }

    @Nested
    @DisplayName("ConcurrentRetryUtil")
    class ConcurrentRetryUtilTests {

        @Test
        @DisplayName("executeWithRetry with maxRetries")
        void executeWithRetry_maxRetries() {
            String result = ConcurrentRetryUtil.executeWithRetry(() -> "ok", 3, 1);
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("executeWithRetry Runnable")
        void executeWithRetry_runnable() {
            boolean[] ran = {false};
            ConcurrentRetryUtil.executeWithRetry(() -> ran[0] = true);
            assertThat(ran[0]).isTrue();
        }

        @Test
        @DisplayName("isRetryable cause chain with CME")
        void isRetryable_causeChain() {
            Exception e = new RuntimeException("wrap", new java.util.ConcurrentModificationException());
            assertThat(ConcurrentRetryUtil.isRetryable(e)).isTrue();
        }

        @Test
        @DisplayName("getGlobalStats")
        void getGlobalStats() {
            assertThat(ConcurrentRetryUtil.getGlobalStats()).isNotNull();
        }
    }

    @Nested
    @DisplayName("ConfigDefaults")
    class ConfigDefaultsTests {

        @Test
        @DisplayName("Keys constants")
        void keysConstants() {
            assertThat(ConfigDefaults.Keys.MAX_DEPTH).isEqualTo("tfi.change-tracking.snapshot.max-depth");
            assertThat(ConfigDefaults.Keys.TIME_BUDGET_MS).isNotNull();
        }
    }
}
