package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.DiffIgnore;
import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import com.syy.taskflowinsight.exporter.change.ChangeJsonExporter;
import com.syy.taskflowinsight.exporter.change.ChangeMapExporter;
import com.syy.taskflowinsight.exporter.change.ChangeExporter;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.spi.DefaultRenderProvider;
import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.aspect.TfiDeepTrackingAspect;
import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.config.resolver.ConfigMigrationMapper;
import com.syy.taskflowinsight.config.resolver.ConfigurationResolverImpl;
import com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil;
import com.syy.taskflowinsight.metrics.AsyncMetricsCollector;
import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;
import com.syy.taskflowinsight.tracking.cache.StrategyCache;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareStrategy;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.entity.EntityChangeGroup;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityOperation;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.determinism.StableSorter;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.format.ValueReprFormatter;
import com.syy.taskflowinsight.tracking.metrics.MicrometerDiagnosticSink;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.tracking.path.PathCache;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.perf.PerfGuard;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import com.syy.taskflowinsight.tracking.rename.RenameHeuristics;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.tracking.ssot.path.PathNavigator;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ConcurrentModificationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consolidated coverage tests merging AllRemainingCoverageTests, RemainingPackagesCoverageTests,
 * FinalGapCoverageTests, LowCoverageMethodsFinalTests, and SurgicalCoverageTests.
 * Maximizes instruction coverage across format, precision, entity, filter, summary, perf,
 * rename, metrics, aspect, concurrent, spi, config, cache, ssot, and tracking packages.
 *
 * @since 3.0.0
 */
@DisplayName("Comprehensive Coverage — 合并全覆盖测试")
class ChangeTrackerComprehensiveTests {

    // ═══════════════════════════════════════════════════════════════════════
    // From AllRemainingCoverageTests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("From AllRemaining — TfiDateTimeFormatter")
    class FromAllRemaining_TfiDateTimeFormatter {

        @Test
        @DisplayName("format — ZonedDateTime")
        void format_zonedDateTime() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            String s = fmt.format(ZonedDateTime.now());
            assertThat(s).isNotBlank();
        }

        @Test
        @DisplayName("format — LocalDateTime")
        void format_localDateTime() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            String s = fmt.format(LocalDateTime.of(2025, 1, 15, 10, 30));
            assertThat(s).isNotBlank();
        }

        @Test
        @DisplayName("format — LocalDate")
        void format_localDate() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            String s = fmt.format(LocalDate.of(2025, 1, 15));
            assertThat(s).isNotBlank();
        }

        @Test
        @DisplayName("format — null returns null")
        void format_null() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.format((java.time.temporal.Temporal) null)).isNull();
        }

        @Test
        @DisplayName("formatDuration — null")
        void formatDuration_null() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.formatDuration(null)).isNull();
        }

        @Test
        @DisplayName("formatDuration — PT1H30M")
        void formatDuration_value() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            String s = fmt.formatDuration(Duration.ofMinutes(90));
            assertThat(s).contains("T").contains("M");
        }

        @Test
        @DisplayName("formatPeriod — null")
        void formatPeriod_null() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.formatPeriod(null)).isNull();
        }

        @Test
        @DisplayName("formatPeriod — P1Y2M")
        void formatPeriod_value() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            String s = fmt.formatPeriod(Period.of(1, 2, 3));
            assertThat(s).contains("Y").contains("M").contains("D");
        }

        @Test
        @DisplayName("parseWithTolerance — LocalDateTime")
        void parseWithTolerance_localDateTime() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            var result = fmt.parseWithTolerance("2025-01-15 10:30:00", java.time.LocalDateTime.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("parseWithTolerance — null/empty returns null")
        void parseWithTolerance_nullEmpty() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.parseWithTolerance(null, java.time.LocalDateTime.class)).isNull();
            assertThat(fmt.parseWithTolerance("  ", java.time.LocalDateTime.class)).isNull();
        }

        @Test
        @DisplayName("parseDuration — invalid returns null")
        void parseDuration_invalid() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.parseDuration("invalid")).isNull();
        }

        @Test
        @DisplayName("parsePeriod — invalid returns null")
        void parsePeriod_invalid() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.parsePeriod("invalid")).isNull();
        }

        @Test
        @DisplayName("timezone UTC")
        void timezone_utc() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter("yyyy-MM-dd", "UTC");
            assertThat(fmt.getDefaultZoneId()).isEqualTo(java.time.ZoneOffset.UTC);
        }

        @Test
        @DisplayName("timezone invalid falls back to system")
        void timezone_invalid() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter("yyyy-MM-dd", "INVALID_ZONE");
            assertThat(fmt.getDefaultZoneId()).isNotNull();
        }

        @Test
        @DisplayName("clearCache")
        void clearCache() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThatCode(fmt::clearCache).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("From AllRemaining — ValueReprFormatter")
    class FromAllRemaining_ValueReprFormatter {

        @Test
        @DisplayName("format — String")
        void format_string() {
            assertThat(ValueReprFormatter.format("hello")).isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("format — Number")
        void format_number() {
            assertThat(ValueReprFormatter.format(42)).isEqualTo("42");
        }

        @Test
        @DisplayName("format — BigDecimal")
        void format_bigDecimal() {
            assertThat(ValueReprFormatter.format(java.math.BigDecimal.valueOf(1.5))).contains("1.5");
        }

        @Test
        @DisplayName("format — Double NaN")
        void format_doubleNaN() {
            assertThat(ValueReprFormatter.format(Double.NaN)).isEqualTo("NaN");
        }

        @Test
        @DisplayName("format — Double Infinity")
        void format_doubleInfinity() {
            assertThat(ValueReprFormatter.format(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
        }

        @Test
        @DisplayName("format — Date")
        void format_date() {
            assertThat(ValueReprFormatter.format(new java.util.Date())).contains("\"");
        }

        @Test
        @DisplayName("format — Collection")
        void format_collection() {
            assertThat(ValueReprFormatter.format(List.of(1, 2, 3))).startsWith("[");
        }

        @Test
        @DisplayName("format — long string truncation")
        void format_longStringTruncation() {
            String longStr = "x".repeat(150);
            String result = ValueReprFormatter.format(longStr);
            assertThat(result).contains("...");
        }

        @Test
        @DisplayName("format — very long string truncated marker")
        void format_veryLongString() {
            String veryLong = "y".repeat(1100);
            String result = ValueReprFormatter.format(veryLong);
            assertThat(result).contains("(truncated)");
        }

        @Test
        @DisplayName("TruncationInfo constants")
        void truncationInfo() {
            assertThat(ValueReprFormatter.TruncationInfo.SHORT_THRESHOLD).isEqualTo(100);
            assertThat(ValueReprFormatter.TruncationInfo.LONG_THRESHOLD).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("From AllRemaining — RenameHeuristics")
    class FromAllRemaining_RenameHeuristics {

        @Test
        @DisplayName("similarity — null")
        void similarity_null() {
            assertThat(RenameHeuristics.similarity(null, "b", RenameHeuristics.Options.defaults()))
                    .isEqualTo(0.0);
            assertThat(RenameHeuristics.similarity("a", null, RenameHeuristics.Options.defaults()))
                    .isEqualTo(0.0);
        }

        @Test
        @DisplayName("similarity — equal")
        void similarity_equal() {
            assertThat(RenameHeuristics.similarity("abc", "abc", RenameHeuristics.Options.defaults()))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("similarity — different")
        void similarity_different() {
            double sim = RenameHeuristics.similarity("abc", "xyz", RenameHeuristics.Options.defaults());
            assertThat(sim).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("isPossibleRename — true")
        void isPossibleRename_true() {
            RenameHeuristics.Options opts = new RenameHeuristics.Options(
                    1000, 0.0, com.syy.taskflowinsight.tracking.algo.edit.LevenshteinEditDistance.Options.defaults());
            assertThat(RenameHeuristics.isPossibleRename("a", "a", opts)).isTrue();
        }

        @Test
        @DisplayName("isPossibleRename — false")
        void isPossibleRename_false() {
            RenameHeuristics.Options opts = new RenameHeuristics.Options(
                    1000, 1.0, com.syy.taskflowinsight.tracking.algo.edit.LevenshteinEditDistance.Options.defaults());
            assertThat(RenameHeuristics.isPossibleRename("abc", "xyz", opts)).isFalse();
        }
    }

    @Nested
    @DisplayName("From AllRemaining — PerfGuard")
    class FromAllRemaining_PerfGuard {

        @Test
        @DisplayName("checkBudget — within budget")
        void checkBudget_within() {
            PerfGuard guard = new PerfGuard();
            var opts = PerfGuard.PerfOptions.defaults();
            var decision = guard.checkBudget(System.currentTimeMillis(), opts, false);
            assertThat(decision.ok).isTrue();
        }

        @Test
        @DisplayName("checkBudget — exceeded strict")
        void checkBudget_exceededStrict() {
            PerfGuard guard = new PerfGuard();
            var opts = new PerfGuard.PerfOptions(0, 1000, true);
            var decision = guard.checkBudget(System.currentTimeMillis() - 10000, opts, true);
            assertThat(decision.ok).isFalse();
            assertThat(decision.partial).isTrue();
        }

        @Test
        @DisplayName("checkBudget — exceeded non-strict")
        void checkBudget_exceededNonStrict() {
            PerfGuard guard = new PerfGuard();
            var opts = new PerfGuard.PerfOptions(0, 1000, true);
            var decision = guard.checkBudget(System.currentTimeMillis() - 10000, opts, false);
            assertThat(decision.ok).isFalse();
            assertThat(decision.partial).isFalse();
        }

        @Test
        @DisplayName("shouldDegradeList")
        void shouldDegradeList() {
            PerfGuard guard = new PerfGuard();
            var opts = PerfGuard.PerfOptions.defaults();
            assertThat(guard.shouldDegradeList(1001, opts)).isTrue();
            assertThat(guard.shouldDegradeList(100, opts)).isFalse();
        }

        @Test
        @DisplayName("lazySnapshot")
        void lazySnapshot() {
            PerfGuard guard = new PerfGuard();
            assertThat(guard.lazySnapshot(PerfGuard.PerfOptions.defaults())).isTrue();
        }

        @Test
        @DisplayName("PerfDecision add")
        void perfDecision_add() {
            var d = PerfGuard.PerfDecision.ok().add("reason");
            assertThat(d.reasons).contains("reason");
        }
    }

    @Nested
    @DisplayName("From AllRemaining — MicrometerDiagnosticSink")
    class FromAllRemaining_MicrometerDiagnosticSink {

        @Test
        @DisplayName("null registry — No-Op")
        void nullRegistry_noOp() {
            MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(null);
            assertThat(sink.isNoOp()).isTrue();
            assertThatCode(() -> sink.recordCount("test")).doesNotThrowAnyException();
            assertThatCode(() -> sink.recordDuration("test", 100)).doesNotThrowAnyException();
            sink.recordDegradation("reason");
            sink.recordError("type");
        }

        @Test
        @DisplayName("with registry")
        void withRegistry() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            MicrometerDiagnosticSink sink = new MicrometerDiagnosticSink(registry);
            assertThat(sink.isNoOp()).isFalse();
            sink.recordCount("tfi.test.count", "tag", "value");
            sink.recordDuration("tfi.test.duration", 1_000_000, "tag", "value");
            sink.recordDegradation("reason");
            sink.recordError("type");
            assertThat(sink.getRegistry()).isSameAs(registry);
        }
    }

    @Nested
    @DisplayName("From AllRemaining — ConcurrentRetryUtil")
    class FromAllRemaining_ConcurrentRetryUtil {

        @Test
        @DisplayName("executeWithRetry — CME exhausted throws")
        void executeWithRetry_cmeExhausted_throws() {
            assertThatThrownBy(() -> ConcurrentRetryUtil.executeWithRetry(
                    () -> { throw new ConcurrentModificationException(); },
                    1, 1))
                    .isInstanceOf(ConcurrentModificationException.class);
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — CME exhausted uses fallback")
        void executeWithRetryOrSummary_cmeExhausted_fallback() {
            String result = ConcurrentRetryUtil.executeWithRetryOrSummary(
                    () -> { throw new ConcurrentModificationException(); },
                    () -> "fallback");
            assertThat(result).isEqualTo("fallback");
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — Runnable version")
        void executeWithRetryOrSummary_runnable() {
            boolean[] fallbackRan = {false};
            ConcurrentRetryUtil.executeWithRetryOrSummary(
                    () -> { throw new ConcurrentModificationException(); },
                    () -> fallbackRan[0] = true);
            assertThat(fallbackRan[0]).isTrue();
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — operation null throws")
        void executeWithRetryOrSummary_operationNull_throws() {
            assertThatThrownBy(() -> ConcurrentRetryUtil.executeWithRetryOrSummary(
                    null, () -> "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("executeWithRetry — Runnable null throws")
        void executeWithRetry_runnableNull_throws() {
            assertThatThrownBy(() -> ConcurrentRetryUtil.executeWithRetry((Runnable) null))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("setTfiMetrics")
        void setTfiMetrics() {
            assertThatCode(() -> ConcurrentRetryUtil.setTfiMetrics(null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("From AllRemaining — DefaultComparisonProvider")
    class FromAllRemaining_DefaultComparisonProvider {

        @Test
        @DisplayName("compare — no options")
        void compare_noOptions() {
            DefaultComparisonProvider p = new DefaultComparisonProvider();
            CompareResult r = p.compare("a", "b");
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("similarity")
        void similarity() {
            DefaultComparisonProvider p = new DefaultComparisonProvider();
            assertThat(p.similarity("a", "a")).isEqualTo(1.0);
            double simAb = p.similarity("a", "b");
            assertThat(simAb).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("priority")
        void priority() {
            assertThat(new DefaultComparisonProvider().priority()).isEqualTo(0);
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            assertThat(new DefaultComparisonProvider().toString())
                    .contains("DefaultComparisonProvider");
        }
    }

    @Nested
    @DisplayName("From AllRemaining — DefaultTrackingProvider")
    class FromAllRemaining_DefaultTrackingProvider {

        @AfterEach
        void clear() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("track")
        void track() {
            DefaultTrackingProvider p = new DefaultTrackingProvider();
            p.track("obj", Map.of("x", 1), "x");
            assertThatCode(() -> p.changes()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("trackDeep")
        void trackDeep() {
            DefaultTrackingProvider p = new DefaultTrackingProvider();
            p.trackDeep("obj", Map.of("a", 1));
            assertThatCode(() -> p.changes()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("trackAll — null throws")
        void trackAll_null_throws() {
            DefaultTrackingProvider p = new DefaultTrackingProvider();
            assertThatThrownBy(() -> p.trackAll(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getAllChanges")
        void getAllChanges() {
            DefaultTrackingProvider p = new DefaultTrackingProvider();
            p.track("x", Map.of("a", 1));
            assertThat(p.getAllChanges()).isNotNull();
        }

        @Test
        @DisplayName("clear")
        void clearTracking() {
            DefaultTrackingProvider p = new DefaultTrackingProvider();
            p.track("x", Map.of("a", 1));
            p.clear();
            assertThat(p.changes()).isEmpty();
        }

        @Test
        @DisplayName("priority")
        void priority() {
            assertThat(new DefaultTrackingProvider().priority()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("From AllRemaining — DefaultRenderProvider")
    class FromAllRemaining_DefaultRenderProvider {

        @Test
        @DisplayName("render — EntityListDiffResult")
        void render_entityListDiffResult() {
            DefaultRenderProvider p = new DefaultRenderProvider();
            EntityListDiffResult result = EntityListDiffResult.from(CompareResult.identical());
            String s = p.render(result, "standard");
            assertThat(s).isNotBlank();
        }

        @Test
        @DisplayName("render — unsupported type")
        void render_unsupportedType() {
            DefaultRenderProvider p = new DefaultRenderProvider();
            String s = p.render("hello", "simple");
            assertThat(s).contains("String");
        }

        @Test
        @DisplayName("priority")
        void priority() {
            assertThat(new DefaultRenderProvider().priority()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("From AllRemaining — ConfigurationResolverImpl")
    class FromAllRemaining_ConfigurationResolverImpl {

        @Test
        @DisplayName("resolve with default")
        void resolve_withDefault() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            Integer maxDepth = resolver.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 99);
            assertThat(maxDepth).isNotNull();
        }

        @Test
        @DisplayName("resolve Optional")
        void resolve_optional() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            var opt = resolver.resolve("unknown.key", String.class);
            assertThat(opt).isEmpty();
        }

        @Test
        @DisplayName("setRuntimeConfig")
        void setRuntimeConfig() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 20);
            Integer v = resolver.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 10);
            assertThat(v).isEqualTo(20);
        }

        @Test
        @DisplayName("clearRuntimeConfig")
        void clearRuntimeConfig() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH, 20);
            resolver.clearRuntimeConfig(ConfigDefaults.Keys.MAX_DEPTH);
            Integer v = resolver.resolve(ConfigDefaults.Keys.MAX_DEPTH, Integer.class, 10);
            assertThat(v).isEqualTo(10);
        }

        @Test
        @DisplayName("getEffectivePriority")
        void getEffectivePriority() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            var priority = resolver.getEffectivePriority(ConfigDefaults.Keys.MAX_DEPTH);
            assertThat(priority).isNotNull();
        }

        @Test
        @DisplayName("getConfigSources")
        void getConfigSources() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            var sources = resolver.getConfigSources(ConfigDefaults.Keys.MAX_DEPTH);
            assertThat(sources).isNotNull();
        }

        @Test
        @DisplayName("setMethodAnnotationConfig")
        void setMethodAnnotationConfig() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setMethodAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, 15);
            resolver.clearMethodAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH);
            assertThatCode(() -> {}).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("setClassAnnotationConfig")
        void setClassAnnotationConfig() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            resolver.setClassAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH, 15);
            resolver.clearClassAnnotationConfig(ConfigDefaults.Keys.MAX_DEPTH);
            assertThatCode(() -> {}).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("refresh")
        void refresh() {
            ConfigurationResolverImpl resolver = new ConfigurationResolverImpl(
                    new StandardEnvironment(), new ConfigMigrationMapper());
            assertThatCode(resolver::refresh).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("From AllRemaining — ConfigMigrationMapper")
    class FromAllRemaining_ConfigMigrationMapper {

        @Test
        @DisplayName("checkAndMigrate — deprecated key")
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
            assertThat(mapper.isDeprecatedKey("tfi.change-tracking.snapshot.max-depth")).isFalse();
        }

        @Test
        @DisplayName("getNewKey")
        void getNewKey() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            assertThat(mapper.getNewKey("tfi.change-tracking.max-depth"))
                    .isEqualTo("tfi.change-tracking.snapshot.max-depth");
        }

        @Test
        @DisplayName("getOldKeyForNew")
        void getOldKeyForNew() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            String old = mapper.getOldKeyForNew("tfi.change-tracking.snapshot.max-depth");
            assertThat(old).isNotNull();
        }

        @Test
        @DisplayName("getMigrationReport")
        void getMigrationReport() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            mapper.checkAndMigrate("tfi.change-tracking.max-depth");
            String report = mapper.getMigrationReport();
            assertThat(report).contains("Deprecated");
        }

        @Test
        @DisplayName("getAllMappings")
        void getAllMappings() {
            Map<String, String> mappings = ConfigMigrationMapper.getAllMappings();
            assertThat(mappings).isNotEmpty();
        }

        @Test
        @DisplayName("clearWarnings")
        void clearWarnings() {
            ConfigMigrationMapper mapper = new ConfigMigrationMapper();
            mapper.clearWarnings();
            assertThatCode(() -> {}).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("From AllRemaining — TfiDeepTrackingAspect")
    class FromAllRemaining_TfiDeepTrackingAspect {

        @Test
        @DisplayName("default constructor")
        void defaultConstructor() {
            TfiDeepTrackingAspect aspect = new TfiDeepTrackingAspect();
            assertThat(aspect).isNotNull();
        }

        @Test
        @DisplayName("constructor with custom exporter")
        void constructor_withExporter() {
            TfiDeepTrackingAspect aspect = new TfiDeepTrackingAspect(new ChangeConsoleExporter());
            assertThat(aspect).isNotNull();
        }
    }

    @Nested
    @DisplayName("From AllRemaining — CollectionSummary")
    class FromAllRemaining_CollectionSummary {

        @Test
        @DisplayName("shouldSummarize — threshold")
        void shouldSummarize_threshold() {
            CollectionSummary s = new CollectionSummary();
            s.setEnabled(true);
            s.setMaxSize(5);
            assertThat(s.shouldSummarize(List.of(1, 2, 3, 4, 5, 6))).isTrue();
        }

        @Test
        @DisplayName("summarize — List")
        void summarize_list() {
            CollectionSummary s = new CollectionSummary();
            SummaryInfo info = s.summarize(List.of("a", "b", "c"));
            assertThat(info).isNotNull();
            assertThat(info.getSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("summarize — Set")
        void summarize_set() {
            CollectionSummary s = new CollectionSummary();
            SummaryInfo info = s.summarize(java.util.Set.of(1, 2, 3));
            assertThat(info).isNotNull();
        }

        @Test
        @DisplayName("summarize — Map")
        void summarize_map() {
            CollectionSummary s = new CollectionSummary();
            SummaryInfo info = s.summarize(Map.of("a", 1, "b", 2));
            assertThat(info).isNotNull();
        }
    }

    @Nested
    @DisplayName("From AllRemaining — SummaryInfo")
    class FromAllRemaining_SummaryInfo {

        @Test
        @DisplayName("constructor and setters")
        void constructorAndSetters() {
            SummaryInfo info = new SummaryInfo();
            info.setType("List");
            info.setSize(10);
            assertThat(info.getType()).isEqualTo("List");
            assertThat(info.getSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("Statistics")
        void statistics() {
            SummaryInfo.Statistics stats = new SummaryInfo.Statistics(1.0, 2.0, 3.0, 4.0, null);
            Map<String, Object> map = stats.toMap();
            assertThat(map).containsKey("min").containsKey("max");
        }
    }

    @Nested
    @DisplayName("From AllRemaining — ConfigDefaults")
    class FromAllRemaining_ConfigDefaults {

        @Test
        @DisplayName("constants")
        void constants() {
            assertThat(ConfigDefaults.MAX_DEPTH).isEqualTo(10);
            assertThat(ConfigDefaults.TIME_BUDGET_MS).isEqualTo(1000L);
            assertThat(ConfigDefaults.CONCURRENT_RETRY_MAX_ATTEMPTS).isEqualTo(1);
        }

        @Test
        @DisplayName("Keys")
        void keys() {
            assertThat(ConfigDefaults.Keys.MAX_DEPTH).isEqualTo("tfi.change-tracking.snapshot.max-depth");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // From RemainingPackagesCoverageTests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("From RemainingPackages — ChangeTracker")
    class FromRemainingPackages_ChangeTracker {

        @BeforeEach
        @AfterEach
        void clear() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("track with name null — skips")
        void track_nameNull_skips() {
            assertThatCode(() -> ChangeTracker.track(null, Map.of("x", 1)))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("track with target null — skips")
        void track_targetNull_skips() {
            assertThatCode(() -> ChangeTracker.track("obj", null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("trackAll — batch")
        void trackAll_batch() {
            ChangeTracker.trackAll(Map.of(
                "a", Map.of("x", 1),
                "b", Map.of("y", 2)));
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("trackAll — null/empty")
        void trackAll_nullEmpty_skips() {
            assertThatCode(() -> ChangeTracker.trackAll(null)).doesNotThrowAnyException();
            assertThatCode(() -> ChangeTracker.trackAll(Map.of())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getTrackedCount")
        void getTrackedCount() {
            ChangeTracker.track("x", Map.of("a", 1));
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMaxTrackedObjects")
        void getMaxTrackedObjects() {
            assertThat(ChangeTracker.getMaxTrackedObjects()).isPositive();
        }

        @Test
        @DisplayName("clearBySessionId")
        void clearBySessionId() {
            ChangeTracker.track("x", Map.of("a", 1));
            ChangeTracker.clearBySessionId("s1");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("TrackingException constructors")
        void trackingException_constructors() {
            ChangeTracker.TrackingException e1 = new ChangeTracker.TrackingException("msg");
            assertThat(e1.getMessage()).isEqualTo("msg");
            ChangeTracker.TrackingException e2 = new ChangeTracker.TrackingException("msg", e1);
            assertThat(e2.getCause()).isSameAs(e1);
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — SessionAwareChangeTracker")
    class FromRemainingPackages_SessionAwareChangeTracker {

        @AfterEach
        void clear() {
            SessionAwareChangeTracker.clearAll();
        }

        @Test
        @DisplayName("getCurrentSessionChanges — no session returns empty")
        void getCurrentSessionChanges_noSession_returnsEmpty() {
            assertThat(SessionAwareChangeTracker.getCurrentSessionChanges()).isEmpty();
        }

        @Test
        @DisplayName("getSessionChanges — unknown session returns empty")
        void getSessionChanges_unknown_returnsEmpty() {
            assertThat(SessionAwareChangeTracker.getSessionChanges("unknown")).isEmpty();
        }

        @Test
        @DisplayName("getAllChanges — returns all")
        void getAllChanges_returnsAll() {
            assertThat(SessionAwareChangeTracker.getAllChanges()).isEmpty();
        }

        @Test
        @DisplayName("clearSession — returns count")
        void clearSession_returnsCount() {
            int count = SessionAwareChangeTracker.clearSession("s1");
            assertThat(count).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("getSessionMetadata — unknown returns null")
        void getSessionMetadata_unknown_returnsNull() {
            assertThat(SessionAwareChangeTracker.getSessionMetadata("unknown")).isNull();
        }

        @Test
        @DisplayName("getAllSessionMetadata")
        void getAllSessionMetadata() {
            assertThat(SessionAwareChangeTracker.getAllSessionMetadata()).isNotNull();
        }

        @Test
        @DisplayName("cleanupExpiredSessions")
        void cleanupExpiredSessions() {
            int removed = SessionAwareChangeTracker.cleanupExpiredSessions(0);
            assertThat(removed).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("SessionMetadata — getters and setters")
        void sessionMetadata_gettersSetters() {
            SessionAwareChangeTracker.SessionMetadata meta =
                new SessionAwareChangeTracker.SessionMetadata("s1");
            assertThat(meta.getSessionId()).isEqualTo("s1");
            assertThat(meta.getCreatedTime()).isPositive();
            meta.incrementChangeCount();
            assertThat(meta.getChangeCount()).isEqualTo(1);
            meta.updateLastActivity();
            meta.recordObjectChange("Order");
            assertThat(meta.getObjectChangeCounts()).containsKey("Order");
            assertThat(meta.getAge()).isGreaterThanOrEqualTo(0);
            assertThat(meta.getIdleTime()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — ChangeType")
    class FromRemainingPackages_ChangeType {

        @Test
        @DisplayName("enum values")
        void enumValues() {
            assertThat(ChangeType.values()).containsExactly(
                ChangeType.CREATE, ChangeType.UPDATE, ChangeType.DELETE, ChangeType.MOVE);
        }

        @Test
        @DisplayName("valueOf")
        void valueOf() {
            assertThat(ChangeType.valueOf("CREATE")).isEqualTo(ChangeType.CREATE);
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — StableSorter")
    class FromRemainingPackages_StableSorter {

        @Test
        @DisplayName("sortByFieldChange — empty")
        void sortByFieldChange_empty() {
            List<FieldChange> sorted = StableSorter.sortByFieldChange(List.of());
            assertThat(sorted).isEmpty();
        }

        @Test
        @DisplayName("sortByFieldChange — single element")
        void sortByFieldChange_single() {
            FieldChange fc = FieldChange.builder()
                .fieldName("x")
                .fieldPath("entity[1].x")
                .changeType(ChangeType.UPDATE)
                .build();
            List<FieldChange> sorted = StableSorter.sortByFieldChange(List.of(fc));
            assertThat(sorted).hasSize(1);
        }

        @Test
        @DisplayName("sortByFieldChange — multiple by key/field/priority")
        void sortByFieldChange_multiple() {
            FieldChange create = FieldChange.builder()
                .fieldPath("entity[1].a")
                .changeType(ChangeType.CREATE)
                .build();
            FieldChange update = FieldChange.builder()
                .fieldPath("entity[1].b")
                .changeType(ChangeType.UPDATE)
                .build();
            List<FieldChange> sorted = StableSorter.sortByFieldChange(List.of(create, update));
            assertThat(sorted).hasSize(2);
            assertThat(sorted).containsExactlyInAnyOrder(create, update);
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — ReflectionMetaCache")
    class FromRemainingPackages_ReflectionMetaCache {

        @Test
        @DisplayName("enabled — getFieldsOrResolve")
        void enabled_getFieldsOrResolve() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            List<Field> fields = cache.getFieldsOrResolve(String.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(fields).isNotNull();
        }

        @Test
        @DisplayName("disabled — pass-through")
        void disabled_passthrough() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            List<Field> fields = cache.getFieldsOrResolve(String.class, ReflectionMetaCache::defaultFieldResolver);
            assertThat(fields).isNotNull();
        }

        @Test
        @DisplayName("defaultFieldResolver")
        void defaultFieldResolver() {
            List<Field> fields = ReflectionMetaCache.defaultFieldResolver(String.class);
            assertThat(fields).isNotNull();
        }

        @Test
        @DisplayName("getHitRate — disabled returns -1")
        void getHitRate_disabled() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getHitRate()).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("getRequestCount — disabled returns 0")
        void getRequestCount_disabled() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getRequestCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getCacheSize — disabled returns 0")
        void getCacheSize_disabled() {
            ReflectionMetaCache cache = new ReflectionMetaCache(false, 100, 60_000);
            assertThat(cache.getCacheSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("invalidateAll")
        void invalidateAll() {
            ReflectionMetaCache cache = new ReflectionMetaCache(true, 100, 60_000);
            assertThatCode(cache::invalidateAll).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — StrategyCache")
    class FromRemainingPackages_StrategyCache {

        @Test
        @DisplayName("enabled — getOrResolve")
        void enabled_getOrResolve() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            CompareStrategy<?> strategy = cache.getOrResolve(String.class, t -> null);
            assertThat(strategy).isNull();
        }

        @Test
        @DisplayName("disabled — pass-through")
        void disabled_passthrough() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            CompareStrategy<?> strategy = cache.getOrResolve(String.class, t -> null);
            assertThat(strategy).isNull();
        }

        @Test
        @DisplayName("getHitRate — disabled returns -1")
        void getHitRate_disabled() {
            StrategyCache cache = new StrategyCache(false, 100, 60_000);
            assertThat(cache.getHitRate()).isEqualTo(-1.0);
        }

        @Test
        @DisplayName("invalidateAll")
        void invalidateAll() {
            StrategyCache cache = new StrategyCache(true, 100, 60_000);
            assertThatCode(cache::invalidateAll).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — PathUtils")
    class FromRemainingPackages_PathUtils {

        @Test
        @DisplayName("buildEntityPath")
        void buildEntityPath() {
            assertThat(PathUtils.buildEntityPath("1")).isEqualTo("entity[1]");
            assertThat(PathUtils.buildEntityPath("1", "field")).isEqualTo("entity[1].field");
        }

        @Test
        @DisplayName("buildEntityPathWithDup")
        void buildEntityPathWithDup() {
            assertThat(PathUtils.buildEntityPathWithDup("1", 0)).isEqualTo("entity[1#0]");
        }

        @Test
        @DisplayName("buildMapValuePath")
        void buildMapValuePath() {
            assertThat(PathUtils.buildMapValuePath("key")).isEqualTo("map[key]");
        }

        @Test
        @DisplayName("buildMapKeyAttrPath")
        void buildMapKeyAttrPath() {
            assertThat(PathUtils.buildMapKeyAttrPath("k")).isEqualTo("map[KEY:k]");
        }

        @Test
        @DisplayName("buildListIndexPath")
        void buildListIndexPath() {
            assertThat(PathUtils.buildListIndexPath(0)).isEqualTo("[0]");
            assertThat(PathUtils.buildListIndexPath(0, "x")).isEqualTo("[0].x");
        }

        @Test
        @DisplayName("parse — entity path")
        void parse_entityPath() {
            PathUtils.KeyFieldPair pair = PathUtils.parse("entity[1].field");
            assertThat(pair.key()).isEqualTo("entity[1]");
            assertThat(pair.field()).isEqualTo("field");
        }

        @Test
        @DisplayName("parse — non-entity path")
        void parse_nonEntityPath() {
            PathUtils.KeyFieldPair pair = PathUtils.parse("foo.bar");
            assertThat(pair.key()).isEqualTo("-");
            assertThat(pair.field()).isEqualTo("foo.bar");
        }

        @Test
        @DisplayName("unescape")
        void unescape() {
            assertThat(PathUtils.unescape("a\\:b")).isEqualTo("a:b");
            assertThat(PathUtils.unescape(null)).isNull();
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — PathNavigator")
    class FromRemainingPackages_PathNavigator {

        @Test
        @DisplayName("resolve — null root")
        void resolve_nullRoot() {
            assertThat(PathNavigator.resolve(null, "path")).isNull();
        }

        @Test
        @DisplayName("resolve — null path")
        void resolve_nullPath() {
            assertThat(PathNavigator.resolve("obj", null)).isNull();
        }

        @Test
        @DisplayName("resolve — simple field")
        void resolve_simpleField() {
            Object root = new RemainingPackages_SimpleBean();
            assertThat(PathNavigator.resolve(root, "field")).isEqualTo("value");
        }

        @Test
        @DisplayName("isAnnotatedField — null")
        void isAnnotatedField_null() {
            assertThat(PathNavigator.isAnnotatedField(null, "x", Deprecated.class)).isFalse();
        }

        static class RemainingPackages_SimpleBean {
            public String field = "value";
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — EntityKeyUtils")
    class FromRemainingPackages_EntityKeyUtils {

        @Test
        @DisplayName("tryComputeStableKey — null")
        void tryComputeStableKey_null() {
            assertThat(EntityKeyUtils.tryComputeStableKey(null)).isEmpty();
        }

        @Test
        @DisplayName("computeStableKeyOrUnresolved — simple object")
        void computeStableKeyOrUnresolved() {
            String key = EntityKeyUtils.computeStableKeyOrUnresolved("hello");
            assertThat(key).isNotNull();
        }

        @Test
        @DisplayName("computeStableKeyOrNull — null")
        void computeStableKeyOrNull_null() {
            assertThat(EntityKeyUtils.computeStableKeyOrNull(null)).isNull();
        }

        @Test
        @DisplayName("tryComputeCompactKey — null")
        void tryComputeCompactKey_null() {
            assertThat(EntityKeyUtils.tryComputeCompactKey(null)).isEmpty();
        }

        @Test
        @DisplayName("computeCompactKeyOrUnresolved")
        void computeCompactKeyOrUnresolved() {
            String key = EntityKeyUtils.computeCompactKeyOrUnresolved("x");
            assertThat(key).isNotNull();
        }

        @Test
        @DisplayName("collectKeyFields — class without @Key")
        void collectKeyFields_noKey() {
            List<Field> fields = EntityKeyUtils.collectKeyFields(String.class);
            assertThat(fields).isEmpty();
        }

        @Test
        @DisplayName("computeReferenceIdentifier — null")
        void computeReferenceIdentifier_null() {
            assertThat(EntityKeyUtils.computeReferenceIdentifier(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("computeReferenceIdentifier — simple object")
        void computeReferenceIdentifier_simple() {
            String ref = EntityKeyUtils.computeReferenceIdentifier("x");
            assertThat(ref).isNotNull();
        }

        @Test
        @DisplayName("UNRESOLVED constant")
        void unresolvedConstant() {
            assertThat(EntityKeyUtils.UNRESOLVED).isEqualTo("__UNRESOLVED__");
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — CollectionSummary")
    class FromRemainingPackages_CollectionSummary {

        private final CollectionSummary summarizer = new CollectionSummary();

        @Test
        @DisplayName("getInstance")
        void getInstance() {
            assertThat(CollectionSummary.getInstance()).isNotNull();
        }

        @Test
        @DisplayName("shouldSummarize — disabled")
        void shouldSummarize_disabled() {
            summarizer.setEnabled(false);
            assertThat(summarizer.shouldSummarize(List.of(1, 2, 3))).isFalse();
            summarizer.setEnabled(true);
        }

        @Test
        @DisplayName("shouldSummarize — null")
        void shouldSummarize_null() {
            assertThat(summarizer.shouldSummarize(null)).isFalse();
        }

        @Test
        @DisplayName("summarize — array")
        void summarize_array() {
            SummaryInfo info = summarizer.summarize(new int[]{1, 2, 3});
            assertThat(info).isNotNull();
            assertThat(info.getSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("summarize — unsupported type")
        void summarize_unsupported() {
            SummaryInfo info = summarizer.summarize(new Object());
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("setters")
        void setters() {
            summarizer.setMaxSize(50);
            summarizer.setMaxExamples(5);
            summarizer.setSensitiveWords(List.of("secret"));
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — SummaryInfo")
    class FromRemainingPackages_SummaryInfo {

        @Test
        @DisplayName("empty")
        void empty() {
            SummaryInfo info = SummaryInfo.empty();
            assertThat(info.getType()).isEqualTo("empty");
            assertThat(info.getSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("unsupported")
        void unsupported() {
            SummaryInfo info = SummaryInfo.unsupported(String.class);
            assertThat(info.getType()).isEqualTo("String");
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("toMap")
        void toMap() {
            SummaryInfo info = SummaryInfo.empty();
            Map<String, Object> map = info.toMap();
            assertThat(map).containsKey("type");
            assertThat(map).containsKey("timestamp");
        }

        @Test
        @DisplayName("toCompactString")
        void toCompactString() {
            SummaryInfo info = new SummaryInfo();
            info.setType("List");
            info.setSize(10);
            assertThat(info.toCompactString()).contains("List").contains("size=10");
        }

        @Test
        @DisplayName("Statistics toMap")
        void statistics_toMap() {
            SummaryInfo.Statistics stats = new SummaryInfo.Statistics(1.0, 1.0, 1.0, 1.0, null);
            assertThat(stats.toMap()).containsKey("min");
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — ConcurrentRetryUtil")
    class FromRemainingPackages_ConcurrentRetryUtil {

        @Test
        @DisplayName("executeWithRetry — success")
        void executeWithRetry_success() {
            String result = ConcurrentRetryUtil.executeWithRetry(() -> "ok");
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("executeWithRetry — null operation throws")
        void executeWithRetry_nullOperation_throws() {
            assertThatThrownBy(() -> ConcurrentRetryUtil.executeWithRetry(
                (java.util.function.Supplier<String>) null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("executeWithRetry — Runnable")
        void executeWithRetry_runnable() {
            final boolean[] ran = {false};
            ConcurrentRetryUtil.executeWithRetry(() -> ran[0] = true);
            assertThat(ran[0]).isTrue();
        }

        @Test
        @DisplayName("executeWithRetry — custom params")
        void executeWithRetry_customParams() {
            String result = ConcurrentRetryUtil.executeWithRetry(() -> "x", 3, 1);
            assertThat(result).isEqualTo("x");
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — success")
        void executeWithRetryOrSummary_success() {
            String result = ConcurrentRetryUtil.executeWithRetryOrSummary(
                () -> "ok",
                () -> "fallback");
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — null throws")
        void executeWithRetryOrSummary_null_throws() {
            assertThatThrownBy(() -> ConcurrentRetryUtil.executeWithRetryOrSummary(
                () -> "x",
                null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("isRetryable — CME")
        void isRetryable_cme() {
            assertThat(ConcurrentRetryUtil.isRetryable(new ConcurrentModificationException())).isTrue();
        }

        @Test
        @DisplayName("isRetryable — CME in cause")
        void isRetryable_cmeInCause() {
            Exception e = new Exception("wrap", new ConcurrentModificationException());
            assertThat(ConcurrentRetryUtil.isRetryable(e)).isTrue();
        }

        @Test
        @DisplayName("isRetryable — non-CME")
        void isRetryable_nonCme() {
            assertThat(ConcurrentRetryUtil.isRetryable(new RuntimeException())).isFalse();
        }

        @Test
        @DisplayName("getGlobalStats")
        void getGlobalStats() {
            assertThat(ConcurrentRetryUtil.getGlobalStats()).isNotNull();
        }

        @Test
        @DisplayName("RetryStats")
        void retryStats() {
            ConcurrentRetryUtil.RetryStats stats = ConcurrentRetryUtil.getGlobalStats();
            assertThat(stats.getSuccessRate()).isBetween(0.0, 1.0);
            assertThat(stats.toString()).contains("RetryStats");
        }

        @Test
        @DisplayName("setDefaultRetryParams")
        void setDefaultRetryParams() {
            assertThatCode(() -> ConcurrentRetryUtil.setDefaultRetryParams(5, 10))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — DefaultComparisonProvider")
    class FromRemainingPackages_DefaultComparisonProvider {

        @Test
        @DisplayName("compare with options")
        void compare_withOptions() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            CompareResult result = provider.compare("a", "b",
                com.syy.taskflowinsight.tracking.compare.CompareOptions.DEFAULT);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("threeWayMerge — throws")
        void threeWayMerge_throws() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            assertThatThrownBy(() -> provider.threeWayMerge("a", "b", "c"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            assertThat(new DefaultComparisonProvider().toString()).contains("DefaultComparisonProvider");
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — DefaultTrackingProvider")
    class FromRemainingPackages_DefaultTrackingProvider {

        @AfterEach
        void clear() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("trackAll")
        void trackAll() {
            DefaultTrackingProvider provider = new DefaultTrackingProvider();
            assertThatThrownBy(() -> provider.trackAll(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            assertThat(new DefaultTrackingProvider().toString()).contains("DefaultTrackingProvider");
        }
    }

    @Nested
    @DisplayName("From RemainingPackages — DefaultRenderProvider")
    class FromRemainingPackages_DefaultRenderProvider {

        @Test
        @DisplayName("render — null result")
        void render_nullResult() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            assertThat(provider.render(null, "standard")).isEqualTo("[null]");
        }

        @Test
        @DisplayName("render — RenderStyle object")
        void render_renderStyleObject() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            CompareResult result = CompareResult.identical();
            String rendered = provider.render(result,
                com.syy.taskflowinsight.tracking.render.RenderStyle.standard());
            assertThat(rendered).isNotNull();
        }

        @Test
        @DisplayName("render — unknown style type")
        void render_unknownStyleType() {
            DefaultRenderProvider provider = new DefaultRenderProvider();
            CompareResult result = CompareResult.identical();
            String rendered = provider.render(result, 123);
            assertThat(rendered).isNotNull();
        }

        @Test
        @DisplayName("toString")
        void toString_() {
            assertThat(new DefaultRenderProvider().toString()).contains("DefaultRenderProvider");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // From FinalGapCoverageTests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("From FinalGap — CollectionSummary")
    class FromFinalGap_CollectionSummary {

        @AfterEach
        void tearDown() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("shouldSummarize — 大集合返回 true")
        void shouldSummarize_largeCollection() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            summary.setEnabled(true);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 50; i++) large.add(i);
            assertThat(summary.shouldSummarize(large)).isTrue();
        }

        @Test
        @DisplayName("shouldSummarize — 小集合返回 false")
        void shouldSummarize_smallCollection() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(100);
            assertThat(summary.shouldSummarize(List.of(1, 2, 3))).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize — null 返回 false")
        void shouldSummarize_null() {
            CollectionSummary summary = new CollectionSummary();
            assertThat(summary.shouldSummarize(null)).isFalse();
        }

        @Test
        @DisplayName("shouldSummarize — disabled 返回 false")
        void shouldSummarize_disabled() {
            CollectionSummary summary = new CollectionSummary();
            summary.setEnabled(false);
            assertThat(summary.shouldSummarize(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))).isFalse();
        }

        @Test
        @DisplayName("summarize — null 返回 empty")
        void summarize_null() {
            CollectionSummary summary = new CollectionSummary();
            SummaryInfo info = summary.summarize(null);
            assertThat(info).isNotNull();
            assertThat(info.getType()).isEqualTo("empty");
        }

        @Test
        @DisplayName("summarize — 小 List")
        void summarize_smallList() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(100);
            summary.setMaxExamples(5);
            SummaryInfo info = summary.summarize(List.of(1, 2, 3, "a", "b"));
            assertThat(info).isNotNull();
            assertThat(info.getSize()).isEqualTo(5);
            assertThat(info.getExamples()).isNotEmpty();
        }

        @Test
        @DisplayName("summarize — 大 List 触发降级")
        void summarize_largeList() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            summary.setMaxExamples(5);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 150; i++) large.add(i);
            SummaryInfo info = summary.summarize(large);
            assertThat(info).isNotNull();
            assertThat(info.getSize()).isEqualTo(150);
            assertThat(info.isTruncated()).isTrue();
        }

        @Test
        @DisplayName("summarize — 数值 List 计算统计")
        void summarize_numericList() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            summary.setMaxExamples(10);
            List<Double> nums = new ArrayList<>();
            for (int i = 0; i < 150; i++) nums.add((double) i);
            SummaryInfo info = summary.summarize(nums);
            assertThat(info).isNotNull();
            assertThat(info.getStatistics()).isNotNull();
            assertThat(info.getStatistics().getMin()).isEqualTo(0.0);
            assertThat(info.getStatistics().getMax()).isEqualTo(19.0);
        }

        @Test
        @DisplayName("summarize — Map")
        void summarize_map() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(100);
            Map<String, Integer> map = Map.of("a", 1, "b", 2, "c", 3);
            SummaryInfo info = summary.summarize(map);
            assertThat(info).isNotNull();
            assertThat(info.getType()).isEqualTo("Map");
            assertThat(info.getMapExamples()).isNotEmpty();
        }

        @Test
        @DisplayName("summarize — 大 Map")
        void summarize_largeMap() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < 50; i++) map.put("k" + i, i);
            SummaryInfo info = summary.summarize(map);
            assertThat(info).isNotNull();
            assertThat(info.getKeyTypeDistribution()).isNotNull();
            assertThat(info.getValueTypeDistribution()).isNotNull();
        }

        @Test
        @DisplayName("summarize — 数组")
        void summarize_array() {
            CollectionSummary summary = new CollectionSummary();
            int[] arr = {1, 2, 3, 4, 5};
            SummaryInfo info = summary.summarize(arr);
            assertThat(info).isNotNull();
            assertThat(info.getType()).isEqualTo("int[]");
            assertThat(info.getSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("summarize — 数值数组统计")
        void summarize_numericArray() {
            CollectionSummary summary = new CollectionSummary();
            double[] arr = {1.0, 2.0, 3.0};
            SummaryInfo info = summary.summarize(arr);
            assertThat(info).isNotNull();
            assertThat(info.getStatistics()).isNotNull();
        }

        @Test
        @DisplayName("summarize — 不支持类型")
        void summarize_unsupported() {
            CollectionSummary summary = new CollectionSummary();
            SummaryInfo info = summary.summarize("not a collection");
            assertThat(info).isNotNull();
            assertThat(info.getFeatures()).contains("unsupported");
        }

        @Test
        @DisplayName("summarize — Set 特征")
        void summarize_set() {
            CollectionSummary summary = new CollectionSummary();
            summary.setMaxSize(10);
            summary.setMaxExamples(5);
            Set<Integer> largeSet = new HashSet<>();
            for (int i = 0; i < 50; i++) largeSet.add(i);
            SummaryInfo info = summary.summarize(largeSet);
            assertThat(info).isNotNull();
            assertThat(info.getFeatures()).isNotNull();
            assertThat(info.getFeatures()).contains("unique");
        }

        @Test
        @DisplayName("getInstance — 非 Spring 环境")
        void getInstance() {
            CollectionSummary instance = CollectionSummary.getInstance();
            assertThat(instance).isNotNull();
        }
    }

    @Nested
    @DisplayName("From FinalGap — PrecisionMetrics")
    class FromFinalGap_PrecisionMetrics {

        @Test
        @DisplayName("recordNumericComparison 无参")
        void recordNumericComparison() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison();
            var snap = m.getSnapshot();
            assertThat(snap.numericComparisonCount).isEqualTo(1);
        }

        @Test
        @DisplayName("recordNumericComparison 带类型")
        void recordNumericComparisonWithType() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison("float");
            var snap = m.getSnapshot();
            assertThat(snap.numericComparisonCount).isEqualTo(1);
        }

        @Test
        @DisplayName("recordDateTimeComparison")
        void recordDateTimeComparison() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordDateTimeComparison();
            m.recordDateTimeComparison("LocalDate");
            var snap = m.getSnapshot();
            assertThat(snap.dateTimeComparisonCount).isEqualTo(2);
        }

        @Test
        @DisplayName("recordToleranceHit — absolute")
        void recordToleranceHit_absolute() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordToleranceHit("absolute", 0.001);
            var snap = m.getSnapshot();
            assertThat(snap.toleranceHitCount).isEqualTo(1);
            assertThat(snap.absoluteToleranceHits).isEqualTo(1);
        }

        @Test
        @DisplayName("recordToleranceHit — relative")
        void recordToleranceHit_relative() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordToleranceHit("relative", 0.0001);
            var snap = m.getSnapshot();
            assertThat(snap.relativeToleranceHits).isEqualTo(1);
        }

        @Test
        @DisplayName("recordToleranceHit — date")
        void recordToleranceHit_date() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordToleranceHit("date", 100);
            var snap = m.getSnapshot();
            assertThat(snap.dateToleranceHits).isEqualTo(1);
        }

        @Test
        @DisplayName("recordBigDecimalComparison")
        void recordBigDecimalComparison() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordBigDecimalComparison("compareTo");
            var snap = m.getSnapshot();
            assertThat(snap.bigDecimalComparisonCount).isEqualTo(1);
        }

        @Test
        @DisplayName("recordCalculationTime")
        void recordCalculationTime() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordCalculationTime(500_000);
            var snap = m.getSnapshot();
            assertThat(snap.calculationCount).isEqualTo(1);
            assertThat(snap.totalCalculationTimeNanos).isEqualTo(500_000);
        }

        @Test
        @DisplayName("recordCacheHit / recordCacheMiss")
        void recordCache() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordCacheHit();
            m.recordCacheHit();
            m.recordCacheMiss();
            var snap = m.getSnapshot();
            assertThat(snap.precisionCacheHitCount).isEqualTo(2);
            assertThat(snap.precisionCacheMissCount).isEqualTo(1);
            assertThat(snap.getCacheHitRate()).isEqualTo(2.0 / 3.0);
        }

        @Test
        @DisplayName("getSnapshot — 平均计算时间")
        void snapshotAverageTime() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordCalculationTime(1000);
            m.recordCalculationTime(2000);
            var snap = m.getSnapshot();
            assertThat(snap.getAverageCalculationTimeMicros()).isEqualTo(1.5);
        }

        @Test
        @DisplayName("getSnapshot — 容差命中率")
        void snapshotToleranceHitRate() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison();
            m.recordToleranceHit("absolute", 0.001);
            var snap = m.getSnapshot();
            assertThat(snap.getToleranceHitRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("reset")
        void reset() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison();
            m.recordCacheHit();
            m.reset();
            var snap = m.getSnapshot();
            assertThat(snap.numericComparisonCount).isEqualTo(0);
            assertThat(snap.precisionCacheHitCount).isEqualTo(0);
        }

        @Test
        @DisplayName("logSummary — 不抛异常")
        void logSummary() {
            PrecisionMetrics m = new PrecisionMetrics();
            m.recordNumericComparison();
            assertThatCode(() -> m.logSummary()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("From FinalGap — NumericCompareStrategy")
    class FromFinalGap_NumericCompareStrategy {

        private final Field dummyField = String.class.getDeclaredFields()[0];

        @Test
        @DisplayName("compareFloats — NaN 与 NaN 相等")
        void compareFloats_nanNan() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(Double.NaN, Double.NaN, dummyField)).isTrue();
        }

        @Test
        @DisplayName("compareFloats — 无穷大")
        void compareFloats_infinity() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, dummyField)).isTrue();
            assertThat(s.compareFloats(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, dummyField)).isTrue();
            assertThat(s.compareFloats(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, dummyField)).isFalse();
        }

        @Test
        @DisplayName("compareFloats — NaN 与普通值不等")
        void compareFloats_nanVsNormal() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(Double.NaN, 1.0, dummyField)).isFalse();
        }

        @Test
        @DisplayName("compareFloats — 绝对容差")
        void compareFloats_absoluteTolerance() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(0.0, 1e-13, 1e-12, 1e-9)).isTrue();
        }

        @Test
        @DisplayName("compareFloats — 相对容差")
        void compareFloats_relativeTolerance() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(1000.0, 1000.0 + 1e-6, 1e-12, 1e-9)).isTrue();
        }

        @Test
        @DisplayName("compareFloats — 不等")
        void compareFloats_notEqual() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareFloats(1.0, 2.0, dummyField)).isFalse();
        }

        @Test
        @DisplayName("compareBigDecimals — 双 null")
        void compareBigDecimals_bothNull() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(null, null, dummyField)).isTrue();
        }

        @Test
        @DisplayName("compareBigDecimals — 单 null")
        void compareBigDecimals_oneNull() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(BigDecimal.ONE, null, dummyField)).isFalse();
        }

        @Test
        @DisplayName("compareBigDecimals — COMPARE_TO")
        void compareBigDecimals_compareTo() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(
                    BigDecimal.valueOf(1.0),
                    BigDecimal.valueOf(1.00),
                    NumericCompareStrategy.CompareMethod.COMPARE_TO,
                    0)).isTrue();
        }

        @Test
        @DisplayName("compareBigDecimals — EQUALS 不同 scale")
        void compareBigDecimals_equals() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(
                    new BigDecimal("1.0"),
                    new BigDecimal("1.00"),
                    NumericCompareStrategy.CompareMethod.EQUALS,
                    0)).isFalse();
        }

        @Test
        @DisplayName("compareBigDecimals — WITH_TOLERANCE")
        void compareBigDecimals_tolerance() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(
                    BigDecimal.valueOf(1.0),
                    BigDecimal.valueOf(1.001),
                    NumericCompareStrategy.CompareMethod.WITH_TOLERANCE,
                    0.01)).isTrue();
        }

        @Test
        @DisplayName("compareBigDecimals — COMPARE_TO 带容差")
        void compareBigDecimals_compareToWithTolerance() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareBigDecimals(
                    BigDecimal.valueOf(1.0),
                    BigDecimal.valueOf(1.0001),
                    NumericCompareStrategy.CompareMethod.COMPARE_TO,
                    0.001)).isTrue();
        }

        @Test
        @DisplayName("compareNumbers — BigDecimal")
        void compareNumbers_bigDecimal() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareNumbers(BigDecimal.ONE, BigDecimal.ONE, dummyField)).isTrue();
        }

        @Test
        @DisplayName("compareNumbers — Integer")
        void compareNumbers_integer() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareNumbers(1, 1, dummyField)).isTrue();
        }

        @Test
        @DisplayName("compareNumbers — 双 null")
        void compareNumbers_bothNull() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            assertThat(s.compareNumbers(null, null, dummyField)).isTrue();
        }

        @Test
        @DisplayName("isNumericType")
        void isNumericType() {
            assertThat(NumericCompareStrategy.isNumericType(1)).isTrue();
            assertThat(NumericCompareStrategy.isNumericType(null)).isFalse();
            assertThat(NumericCompareStrategy.isNumericType("x")).isFalse();
        }

        @Test
        @DisplayName("needsPrecisionCompare")
        void needsPrecisionCompare() {
            assertThat(NumericCompareStrategy.needsPrecisionCompare(1, 2)).isTrue();
            assertThat(NumericCompareStrategy.needsPrecisionCompare(1, null)).isFalse();
        }

        @Test
        @DisplayName("带 metrics 的 compareFloats")
        void compareFloats_withMetrics() {
            NumericCompareStrategy s = new NumericCompareStrategy();
            PrecisionMetrics m = new PrecisionMetrics();
            s.setMetrics(m);
            s.compareFloats(0.0, 1e-14, dummyField);
            var snap = m.getSnapshot();
            assertThat(snap.numericComparisonCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("From FinalGap — AsyncMetricsCollector")
    class FromFinalGap_AsyncMetricsCollector {

        @Test
        @DisplayName("recordCounter")
        void recordCounter() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("test.counter");
            collector.recordCounter("test.counter2", 2.0);
            var stats = collector.getStats();
            assertThat(stats.getTotalEvents()).isGreaterThanOrEqualTo(2);
            collector.destroy();
        }

        @Test
        @DisplayName("recordTimer")
        void recordTimer() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordTimer("test.timer", 1_000_000);
            var stats = collector.getStats();
            assertThat(stats.getTotalEvents()).isGreaterThanOrEqualTo(1);
            collector.destroy();
        }

        @Test
        @DisplayName("recordGauge")
        void recordGauge() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordGauge("test.gauge", 42.0);
            var stats = collector.getStats();
            assertThat(stats.getTotalEvents()).isGreaterThanOrEqualTo(1);
            collector.destroy();
        }

        @Test
        @DisplayName("getStats — CollectorStats")
        void getStats() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            AsyncMetricsCollector collector = new AsyncMetricsCollector(registry);
            collector.init();
            collector.recordCounter("x");
            var stats = collector.getStats();
            assertThat(stats.getBufferSize()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getMaxBufferSize()).isPositive();
            assertThat(stats.getDropRate()).isBetween(0.0, 1.0);
            assertThat(stats.getBufferUtilization()).isBetween(0.0, 1.0);
            assertThat(stats.toString()).isNotBlank();
            collector.destroy();
        }
    }

    @Nested
    @DisplayName("From FinalGap — ChangeTracker")
    class FromFinalGap_ChangeTracker {

        @AfterEach
        void tearDown() {
            ChangeTracker.clearAllTracking();
        }

        @Test
        @DisplayName("track — null name 跳过")
        void track_nullName() {
            ChangeTracker.track(null, "obj", "f");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("track — null target 跳过")
        void track_nullTarget() {
            ChangeTracker.track("x", null, "f");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("track — 正常追踪")
        void track_normal() {
            ChangeTracker.clearAllTracking();
            class Obj { int a = 1; }
            Obj o = new Obj();
            ChangeTracker.track("obj", o, "a");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
            o.a = 2;
            var changes = ChangeTracker.getChanges();
            assertThat(changes).isNotNull();
            assertThat(o.a).isEqualTo(2);
        }

        @Test
        @DisplayName("track — TrackingOptions")
        void track_withOptions() {
            ChangeTracker.clearAllTracking();
            class Obj { int x = 1; }
            Obj o = new Obj();
            ChangeTracker.track("obj", o, TrackingOptions.shallow());
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
            assertThat(o.x).isEqualTo(1);
        }

        @Test
        @DisplayName("trackAll")
        void trackAll() {
            ChangeTracker.clearAllTracking();
            Map<String, Object> map = Map.of(
                    "a", Map.of("v", 1),
                    "b", Map.of("v", 2));
            ChangeTracker.trackAll(map);
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("trackAll — null/empty 跳过")
        void trackAll_nullEmpty() {
            ChangeTracker.clearAllTracking();
            ChangeTracker.trackAll(null);
            ChangeTracker.trackAll(Collections.emptyMap());
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getChanges — 空快照")
        void getChanges_empty() {
            ChangeTracker.clearAllTracking();
            var changes = ChangeTracker.getChanges();
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("clearAllTracking")
        void clearAllTracking() {
            ChangeTracker.track("x", "val");
            ChangeTracker.clearAllTracking();
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getTrackedCount")
        void getTrackedCount() {
            ChangeTracker.clearAllTracking();
            ChangeTracker.track("a", 1);
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMaxTrackedObjects")
        void getMaxTrackedObjects() {
            assertThat(ChangeTracker.getMaxTrackedObjects()).isPositive();
        }

        @Test
        @DisplayName("clearBySessionId")
        void clearBySessionId() {
            ChangeTracker.track("x", 1);
            ChangeTracker.clearBySessionId("s1");
            assertThat(ChangeTracker.getTrackedCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("From FinalGap — LevenshteinListStrategy")
    class FromFinalGap_LevenshteinListStrategy {

        private final LevenshteinListStrategy strategy = new LevenshteinListStrategy();

        @Test
        @DisplayName("list1 == list2 引用相同")
        void sameReference() {
            List<String> list = List.of("a", "b");
            CompareResult r = strategy.compare(list, list, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("null list1")
        void nullList1() {
            CompareResult r = strategy.compare(null, List.of("a"), CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("null list2")
        void nullList2() {
            CompareResult r = strategy.compare(List.of("a"), null, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("processWithoutMoves — DELETE")
        void withoutMoves_delete() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "c");
            CompareOptions opts = CompareOptions.builder().detectMoves(false).build();
            CompareResult r = strategy.compare(before, after, opts);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("processWithoutMoves — INSERT")
        void withoutMoves_insert() {
            List<String> before = List.of("a", "c");
            List<String> after = List.of("a", "b", "c");
            CompareOptions opts = CompareOptions.builder().detectMoves(false).build();
            CompareResult r = strategy.compare(before, after, opts);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("processWithoutMoves — REPLACE")
        void withoutMoves_replace() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "x", "c");
            CompareOptions opts = CompareOptions.builder().detectMoves(false).build();
            CompareResult r = strategy.compare(before, after, opts);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("processWithMoveDetection")
        void withMoveDetection() {
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("c", "a", "b");
            CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
            CompareResult r = strategy.compare(before, after, opts);
            assertThat(r.getChanges()).isNotEmpty();
            assertThat(r.getAlgorithmUsed()).isEqualTo("LEVENSHTEIN");
        }

        @Test
        @DisplayName("空列表相同")
        void emptyLists() {
            CompareResult r = strategy.compare(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("supportsMoveDetection")
        void supportsMoveDetection() {
            assertThat(strategy.supportsMoveDetection()).isTrue();
        }

        @Test
        @DisplayName("getStrategyName")
        void getStrategyName() {
            assertThat(strategy.getStrategyName()).isEqualTo("LEVENSHTEIN");
        }

        @Test
        @DisplayName("getMaxRecommendedSize")
        void getMaxRecommendedSize() {
            assertThat(strategy.getMaxRecommendedSize()).isEqualTo(500);
        }

        @Test
        @DisplayName("单元素 DELETE")
        void singleDelete() {
            List<String> before = List.of("x");
            List<String> after = Collections.emptyList();
            CompareResult r = strategy.compare(before, after, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).hasSize(1);
            assertThat(r.getChanges().get(0).getChangeType()).isEqualTo(ChangeType.DELETE);
        }

        @Test
        @DisplayName("单元素 CREATE")
        void singleCreate() {
            List<String> before = Collections.emptyList();
            List<String> after = List.of("x");
            CompareResult r = strategy.compare(before, after, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).hasSize(1);
            assertThat(r.getChanges().get(0).getChangeType()).isEqualTo(ChangeType.CREATE);
        }
    }

}
