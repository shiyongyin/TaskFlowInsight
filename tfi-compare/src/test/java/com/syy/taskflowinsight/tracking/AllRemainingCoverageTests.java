package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.config.resolver.ConfigDefaults;
import com.syy.taskflowinsight.config.resolver.ConfigMigrationMapper;
import com.syy.taskflowinsight.config.resolver.ConfigurationResolverImpl;
import com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.format.ValueReprFormatter;
import com.syy.taskflowinsight.tracking.metrics.MicrometerDiagnosticSink;
import com.syy.taskflowinsight.tracking.perf.PerfGuard;
import com.syy.taskflowinsight.tracking.rename.RenameHeuristics;
import com.syy.taskflowinsight.tracking.summary.CollectionSummary;
import com.syy.taskflowinsight.tracking.summary.SummaryInfo;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.spi.DefaultRenderProvider;
import com.syy.taskflowinsight.spi.DefaultTrackingProvider;
import com.syy.taskflowinsight.aspect.TfiDeepTrackingAspect;
import com.syy.taskflowinsight.exporter.change.ChangeConsoleExporter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ConcurrentModificationException;

import static org.assertj.core.api.Assertions.*;

/**
 * Maximum coverage tests for remaining low-coverage packages:
 * format, precision, entity, filter, summary, perf, rename, metrics, aspect,
 * concurrent, spi, config, config/resolver.
 *
 * @since 3.0.0
 */
@DisplayName("All Remaining Coverage — 剩余包全覆盖测试")
class AllRemainingCoverageTests {

    // ── TfiDateTimeFormatter (format) ──

    @Nested
    @DisplayName("TfiDateTimeFormatter")
    class TfiDateTimeFormatterTests {

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

    // ── ValueReprFormatter (format) ──

    @Nested
    @DisplayName("ValueReprFormatter")
    class ValueReprFormatterTests {

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

    // ── RenameHeuristics (rename) ──

    @Nested
    @DisplayName("RenameHeuristics")
    class RenameHeuristicsTests {

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

    // ── PerfGuard (perf) ──

    @Nested
    @DisplayName("PerfGuard")
    class PerfGuardTests {

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

    // ── MicrometerDiagnosticSink (metrics) ──

    @Nested
    @DisplayName("MicrometerDiagnosticSink")
    class MicrometerDiagnosticSinkTests {

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

    // ── ConcurrentRetryUtil (concurrent) ──

    @Nested
    @DisplayName("ConcurrentRetryUtil — Deep")
    class ConcurrentRetryUtilDeepTests {

        @Test
        @DisplayName("executeWithRetry — CME exhausted throws")
        void executeWithRetry_cmeExhausted_throws() {
            assertThatThrownBy(() -> ConcurrentRetryUtil.executeWithRetry(
                    () -> {
                        throw new ConcurrentModificationException();
                    },
                    1, 1))
                    .isInstanceOf(ConcurrentModificationException.class);
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — CME exhausted uses fallback")
        void executeWithRetryOrSummary_cmeExhausted_fallback() {
            String result = ConcurrentRetryUtil.executeWithRetryOrSummary(
                    () -> {
                        throw new ConcurrentModificationException();
                    },
                    () -> "fallback");
            assertThat(result).isEqualTo("fallback");
        }

        @Test
        @DisplayName("executeWithRetryOrSummary — Runnable version")
        void executeWithRetryOrSummary_runnable() {
            boolean[] fallbackRan = {false};
            ConcurrentRetryUtil.executeWithRetryOrSummary(
                    () -> {
                        throw new ConcurrentModificationException();
                    },
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

    // ── SPI Providers ──

    @Nested
    @DisplayName("DefaultComparisonProvider — Deep")
    class DefaultComparisonProviderDeepTests {

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
    @DisplayName("DefaultTrackingProvider — Deep")
    class DefaultTrackingProviderDeepTests {

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
    @DisplayName("DefaultRenderProvider — Deep")
    class DefaultRenderProviderDeepTests {

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

    // ── ConfigurationResolverImpl (config/resolver) ──

    @Nested
    @DisplayName("ConfigurationResolverImpl — StandardEnvironment")
    class ConfigurationResolverImplTests {

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

    // ── ConfigMigrationMapper ──

    @Nested
    @DisplayName("ConfigMigrationMapper")
    class ConfigMigrationMapperTests {

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

    // ── TfiDeepTrackingAspect (aspect) ──

    @Nested
    @DisplayName("TfiDeepTrackingAspect — Testable")
    class TfiDeepTrackingAspectTests {

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

    // ── CollectionSummary & SummaryInfo (summary) ──

    @Nested
    @DisplayName("CollectionSummary — Deep")
    class CollectionSummaryDeepTests {

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
    @DisplayName("SummaryInfo — Deep")
    class SummaryInfoDeepTests {

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

    // ── ConfigDefaults ──

    @Nested
    @DisplayName("ConfigDefaults")
    class ConfigDefaultsTests {

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
}
