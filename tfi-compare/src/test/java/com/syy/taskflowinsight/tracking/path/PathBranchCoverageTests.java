package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Branch coverage tests for tracking/path package.
 * Targets PathDeduplicationConfig, PathDeduplicator, PathArbiter with 120+ tests.
 *
 * @since 3.0.0
 */
@DisplayName("Path Branch Coverage — 路径分支覆盖测试")
class PathBranchCoverageTests {

    @BeforeEach
    void setUp() {
        clearPathSystemProperties();
    }

    @AfterEach
    void tearDown() {
        clearPathSystemProperties();
        PathBuilder.clearCache();
    }

    private void clearPathSystemProperties() {
        System.clearProperty("tfi.path.dedup.enabled");
        System.clearProperty("tfi.path.cache.enabled");
        System.clearProperty("tfi.path.cache.maxSize");
        System.clearProperty("tfi.path.cache.policy");
        System.clearProperty("tfi.path.dedup.maxDepth");
        System.clearProperty("tfi.path.dedup.parallel");
        System.clearProperty("tfi.path.dedup.statistics");
        System.clearProperty("tfi.path.cache.prewarm");
        System.clearProperty("tfi.path.dedup.maxCandidates");
        System.clearProperty("tfi.change-tracking.degradation.max-candidates");
    }

    // ==================== PathDeduplicationConfig ====================

    @Nested
    @DisplayName("PathDeduplicationConfig — applySystemOverrides")
    class PathDeduplicationConfigApplySystemOverrides {

        @Test
        @DisplayName("applySystemOverrides with tfi.path.dedup.enabled=true")
        void applySystemOverrides_enabledTrue() {
            System.setProperty("tfi.path.dedup.enabled", "true");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setEnabled(false);
            c.applySystemOverrides();
            assertThat(c.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.dedup.enabled=false")
        void applySystemOverrides_enabledFalse() {
            System.setProperty("tfi.path.dedup.enabled", "false");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.cache.enabled=true")
        void applySystemOverrides_cacheEnabledTrue() {
            System.setProperty("tfi.path.cache.enabled", "true");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setCacheEnabled(false);
            c.applySystemOverrides();
            assertThat(c.isCacheEnabled()).isTrue();
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.cache.maxSize")
        void applySystemOverrides_cacheMaxSize() {
            System.setProperty("tfi.path.cache.maxSize", "5000");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.getMaxCacheSize()).isEqualTo(5000);
        }

        @Test
        @DisplayName("applySystemOverrides with invalid cache maxSize (negative) does not apply")
        void applySystemOverrides_invalidCacheMaxSize() {
            System.setProperty("tfi.path.cache.maxSize", "-1");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            int before = c.getMaxCacheSize();
            c.applySystemOverrides();
            assertThat(c.getMaxCacheSize()).isEqualTo(before);
        }

        @Test
        @DisplayName("applySystemOverrides with invalid integer (NumberFormatException) does not apply")
        void applySystemOverrides_invalidInteger() {
            System.setProperty("tfi.path.cache.maxSize", "not-a-number");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            int before = c.getMaxCacheSize();
            c.applySystemOverrides();
            assertThat(c.getMaxCacheSize()).isEqualTo(before);
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.cache.policy")
        void applySystemOverrides_cachePolicy() {
            System.setProperty("tfi.path.cache.policy", "FIFO");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.getCacheEvictionPolicy()).isEqualTo("FIFO");
        }

        @Test
        @DisplayName("applySystemOverrides with blank policy does not apply")
        void applySystemOverrides_blankPolicy() {
            System.setProperty("tfi.path.cache.policy", "   ");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            String before = c.getCacheEvictionPolicy();
            c.applySystemOverrides();
            assertThat(c.getCacheEvictionPolicy()).isEqualTo(before);
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.dedup.maxDepth")
        void applySystemOverrides_maxDepth() {
            System.setProperty("tfi.path.dedup.maxDepth", "15");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.getMaxCollectionDepth()).isEqualTo(15);
        }

        @Test
        @DisplayName("applySystemOverrides with maxDepth < 1 does not apply")
        void applySystemOverrides_maxDepthLessThanOne() {
            System.setProperty("tfi.path.dedup.maxDepth", "0");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            int before = c.getMaxCollectionDepth();
            c.applySystemOverrides();
            assertThat(c.getMaxCollectionDepth()).isEqualTo(before);
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.dedup.parallel")
        void applySystemOverrides_parallel() {
            System.setProperty("tfi.path.dedup.parallel", "true");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.isParallelProcessing()).isTrue();
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.dedup.statistics")
        void applySystemOverrides_statistics() {
            System.setProperty("tfi.path.dedup.statistics", "true");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.isDetailedStatistics()).isTrue();
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.cache.prewarm")
        void applySystemOverrides_prewarm() {
            System.setProperty("tfi.path.cache.prewarm", "true");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.isCachePrewarmEnabled()).isTrue();
        }

        @Test
        @DisplayName("applySystemOverrides with tfi.path.dedup.maxCandidates")
        void applySystemOverrides_maxCandidates() {
            System.setProperty("tfi.path.dedup.maxCandidates", "10");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.getMaxCandidates()).isEqualTo(10);
        }

        @Test
        @DisplayName("applySystemOverrides with maxCandidates < 1 does not apply")
        void applySystemOverrides_maxCandidatesLessThanOne() {
            System.setProperty("tfi.path.dedup.maxCandidates", "0");
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            int before = c.getMaxCandidates();
            c.applySystemOverrides();
            assertThat(c.getMaxCandidates()).isEqualTo(before);
        }

        @Test
        @DisplayName("applySystemOverrides with unset properties leaves defaults")
        void applySystemOverrides_unsetProperties() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
            assertThat(c.isEnabled()).isTrue();
            assertThat(c.getMaxCacheSize()).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("PathDeduplicationConfig — validate")
    class PathDeduplicationConfigValidate {

        @Test
        @DisplayName("validate throws when maxCacheSize < 0")
        void validate_invalidMaxCacheSize() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setMaxCacheSize(-1);
            assertThatThrownBy(c::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxCacheSize");
        }

        @Test
        @DisplayName("validate throws when maxCollectionDepth < 1")
        void validate_invalidMaxCollectionDepth() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setMaxCollectionDepth(0);
            assertThatThrownBy(c::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxCollectionDepth");
        }

        @Test
        @DisplayName("validate throws when maxObjectsPerLevel < 1")
        void validate_invalidMaxObjectsPerLevel() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setMaxObjectsPerLevel(0);
            assertThatThrownBy(c::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxObjectsPerLevel");
        }

        @Test
        @DisplayName("validate throws when statisticsReportInterval < 1")
        void validate_invalidStatisticsReportInterval() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setStatisticsReportInterval(0);
            assertThatThrownBy(c::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("statisticsReportInterval");
        }

        @Test
        @DisplayName("validate throws when cacheEvictionPolicy is unknown")
        void validate_invalidCacheEvictionPolicy() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setCacheEvictionPolicy("UNKNOWN");
            assertThatThrownBy(c::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cacheEvictionPolicy");
        }

        @Test
        @DisplayName("validate accepts LRU policy")
        void validate_validLruPolicy() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setCacheEvictionPolicy("LRU");
            c.validate();
        }

        @Test
        @DisplayName("validate accepts FIFO policy")
        void validate_validFifoPolicy() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setCacheEvictionPolicy("FIFO");
            c.validate();
        }

        @Test
        @DisplayName("validate accepts SIZE_BASED policy")
        void validate_validSizeBasedPolicy() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setCacheEvictionPolicy("SIZE_BASED");
            c.validate();
        }
    }

    @Nested
    @DisplayName("PathDeduplicationConfig — isPathAllowed")
    class PathDeduplicationConfigIsPathAllowed {

        @Test
        @DisplayName("isPathAllowed returns false for null path")
        void isPathAllowed_nullPath() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            assertThat(c.isPathAllowed(null)).isFalse();
        }

        @Test
        @DisplayName("isPathAllowed returns false when excluded")
        void isPathAllowed_excludedPath() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("*.secret", "internal.*"));
            assertThat(c.isPathAllowed("user.secret")).isFalse();
            assertThat(c.isPathAllowed("internal.data")).isFalse();
        }

        @Test
        @DisplayName("isPathAllowed returns true when includePatterns null")
        void isPathAllowed_includePatternsNull() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setIncludePatterns(null);
            assertThat(c.isPathAllowed("user.name")).isTrue();
        }

        @Test
        @DisplayName("isPathAllowed returns true when includePatterns empty")
        void isPathAllowed_includePatternsEmpty() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setIncludePatterns(Collections.emptyList());
            assertThat(c.isPathAllowed("user.name")).isTrue();
        }

        @Test
        @DisplayName("isPathAllowed returns true when path matches include pattern")
        void isPathAllowed_includedPath() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setIncludePatterns(List.of("user.*", "order.*"));
            assertThat(c.isPathAllowed("user.name")).isTrue();
            assertThat(c.isPathAllowed("order.amount")).isTrue();
        }

        @Test
        @DisplayName("isPathAllowed returns false when path does not match any include")
        void isPathAllowed_nonMatchingInclude() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setIncludePatterns(List.of("user.*"));
            assertThat(c.isPathAllowed("order.amount")).isFalse();
        }

        @Test
        @DisplayName("isPathAllowed skips null pattern in excludePatterns")
        void isPathAllowed_excludeNullPattern() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            java.util.List<String> patterns = new java.util.ArrayList<>();
            patterns.add(null);
            patterns.add("*.secret");
            c.setExcludePatterns(patterns);
            assertThat(c.isPathAllowed("user.name")).isTrue();
        }

        @Test
        @DisplayName("isPathAllowed skips null pattern in includePatterns")
        void isPathAllowed_includeNullPattern() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            java.util.List<String> patterns = new java.util.ArrayList<>();
            patterns.add(null);
            patterns.add("user.*");
            c.setIncludePatterns(patterns);
            assertThat(c.isPathAllowed("user.name")).isTrue();
        }
    }

    @Nested
    @DisplayName("PathDeduplicationConfig — globToRegex via isPathAllowed")
    class PathDeduplicationConfigGlobToRegex {

        @Test
        @DisplayName("glob pattern * matches multiple chars")
        void glob_star() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("user.*"));
            assertThat(c.isPathAllowed("user.secret")).isFalse();
        }

        @Test
        @DisplayName("glob pattern ? matches single char")
        void glob_questionMark() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setIncludePatterns(List.of("a?c"));
            assertThat(c.isPathAllowed("abc")).isTrue();
        }

        @Test
        @DisplayName("glob pattern with literal dot")
        void glob_literalDot() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("field.1"));
            assertThat(c.isPathAllowed("field.1")).isFalse();
        }

        @Test
        @DisplayName("glob pattern with brackets")
        void glob_brackets() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("list[0]"));
            assertThat(c.isPathAllowed("list[0]")).isFalse();
        }

        @Test
        @DisplayName("glob pattern with special regex chars")
        void glob_specialRegexChars() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("field(1)"));
            assertThat(c.isPathAllowed("field(1)")).isFalse();
        }

        @Test
        @DisplayName("glob pattern with plus")
        void glob_plus() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("a+b"));
            assertThat(c.isPathAllowed("a+b")).isFalse();
        }

        @Test
        @DisplayName("glob pattern with pipe")
        void glob_pipe() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("a|b"));
            assertThat(c.isPathAllowed("a|b")).isFalse();
        }

        @Test
        @DisplayName("glob pattern with backslash")
        void glob_backslash() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("path\\to"));
            assertThat(c.isPathAllowed("path\\to")).isFalse();
        }
    }

    @Nested
    @DisplayName("PathDeduplicationConfig — factory methods")
    class PathDeduplicationConfigFactoryMethods {

        @Test
        @DisplayName("forHighPerformance sets correct values")
        void forHighPerformance() {
            PathDeduplicationConfig c = PathDeduplicationConfig.forHighPerformance();
            assertThat(c.getMaxCacheSize()).isEqualTo(50000);
            assertThat(c.isParallelProcessing()).isTrue();
            assertThat(c.getMaxCollectionDepth()).isEqualTo(5);
        }

        @Test
        @DisplayName("forHighAccuracy sets correct values")
        void forHighAccuracy() {
            PathDeduplicationConfig c = PathDeduplicationConfig.forHighAccuracy();
            assertThat(c.getMaxCollectionDepth()).isEqualTo(20);
            assertThat(c.getMaxObjectsPerLevel()).isEqualTo(5000);
            assertThat(c.isDetailedStatistics()).isTrue();
        }

        @Test
        @DisplayName("forMemoryOptimized sets correct values")
        void forMemoryOptimized() {
            PathDeduplicationConfig c = PathDeduplicationConfig.forMemoryOptimized();
            assertThat(c.getMaxCacheSize()).isEqualTo(1000);
            assertThat(c.getCacheEvictionPolicy()).isEqualTo("SIZE_BASED");
        }

        @Test
        @DisplayName("copy constructor copies all fields")
        void copyConstructor() {
            PathDeduplicationConfig orig = new PathDeduplicationConfig();
            orig.setEnabled(false);
            orig.setMaxCandidates(7);
            PathDeduplicationConfig copy = new PathDeduplicationConfig(orig);
            assertThat(copy.isEnabled()).isFalse();
            assertThat(copy.getMaxCandidates()).isEqualTo(7);
        }
    }

    // ==================== PathDeduplicator ====================

    @Nested
    @DisplayName("PathDeduplicator — constructor")
    class PathDeduplicatorConstructor {

        @Test
        @DisplayName("constructor with null config uses default")
        void constructor_nullConfig() {
            PathDeduplicator dedup = new PathDeduplicator((PathDeduplicationConfig) null);
            assertThat(dedup.getConfig()).isNotNull();
            assertThat(dedup.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("constructor applies system overrides")
        void constructor_appliesSystemOverrides() {
            System.setProperty("tfi.path.dedup.enabled", "false");
            PathDeduplicator dedup = new PathDeduplicator(new PathDeduplicationConfig());
            assertThat(dedup.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("legacy constructor with null cache")
        void legacyConstructor_nullCache() {
            PathDeduplicator dedup = new PathDeduplicator(true, null);
            assertThat(dedup.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("legacy constructor with cache")
        void legacyConstructor_withCache() {
            PathCache cache = new PathCache(true, 100);
            PathDeduplicator dedup = new PathDeduplicator(true, cache);
            assertThat(dedup.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("PathDeduplicator — deduplicateWithObjectGraph")
    class PathDeduplicatorDeduplicateWithObjectGraph {

        @Test
        @DisplayName("deduplicateWithObjectGraph null records returns empty")
        void nullRecords() {
            PathDeduplicator dedup = new PathDeduplicator();
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(null, Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph empty records")
        void emptyRecords() {
            PathDeduplicator dedup = new PathDeduplicator();
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(
                    Collections.emptyList(), Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph disabled config returns input")
        void disabledConfig() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(false);
            PathDeduplicator dedup = new PathDeduplicator(config);
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE));
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, Map.of(), Map.of());
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph records exceed fastLimit returns input")
        void recordsExceedFastLimit() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setFastPathChangeLimit(5);
            PathDeduplicator dedup = new PathDeduplicator(config);
            List<ChangeRecord> records = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                records.add(ChangeRecord.of("X", "a" + i, null, "v", ChangeType.CREATE));
            }
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, Map.of(), Map.of());
            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph with snapshot and valid paths")
        void withSnapshot() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setFastPathChangeLimit(1000);
            PathDeduplicator dedup = new PathDeduplicator(config);
            Map<String, Object> before = Map.of("name", "old");
            Map<String, Object> after = Map.of("name", "new");
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("User", "name", "old", "new", ChangeType.UPDATE));
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, before, after);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph with cache enabled and prewarm")
        void cacheEnabledAndPrewarm() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setCacheEnabled(true);
            config.setCachePrewarmEnabled(true);
            config.setFastPathChangeLimit(1000);
            PathDeduplicator dedup = new PathDeduplicator(config);
            Map<String, Object> before = Map.of("name", "old");
            Map<String, Object> after = Map.of("name", "new");
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("User", "name", "old", "new", ChangeType.UPDATE));
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, before, after);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph skips null record")
        void skipsNullRecord() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setFastPathChangeLimit(1000);
            PathDeduplicator dedup = new PathDeduplicator(config);
            List<ChangeRecord> records = new ArrayList<>();
            records.add(ChangeRecord.of("User", "name", "old", "new", ChangeType.UPDATE));
            records.add(null);
            Map<String, Object> before = Map.of("name", "old");
            Map<String, Object> after = Map.of("name", "new");
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, before, after);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph skips record with null fieldName")
        void skipsNullFieldName() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setFastPathChangeLimit(1000);
            PathDeduplicator dedup = new PathDeduplicator(config);
            ChangeRecord record = ChangeRecord.of("User", null, "old", "new", ChangeType.UPDATE);
            Map<String, Object> before = Map.of();
            Map<String, Object> after = Map.of();
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(
                    List.of(record), before, after);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph with multiple candidates triggers clipping")
        void candidateClipping() throws Exception {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCandidates(2);
            config.setFastPathChangeLimit(1000);
            PathDeduplicator dedup = new PathDeduplicator(config);
            Object target = "value";
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("a", target);
            snapshot.put("a.b", target);
            snapshot.put("a.b.c", target);
            snapshot.put("a.b.c.d", target);
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("X", "a", null, target, ChangeType.UPDATE),
                    ChangeRecord.of("X", "a.b", null, target, ChangeType.UPDATE),
                    ChangeRecord.of("X", "a.b.c", null, target, ChangeType.UPDATE),
                    ChangeRecord.of("X", "a.b.c.d", null, target, ChangeType.UPDATE));
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, snapshot, snapshot);
            assertThat(result).hasSizeLessThanOrEqualTo(4);
            PathDeduplicator.DeduplicationStatistics stats = dedup.getStatistics();
            assertThat(stats.getClippedGroupsCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("hydrateMaxCandidatesFromSystem with valid value")
        void hydrateMaxCandidates_valid() {
            System.setProperty("tfi.change-tracking.degradation.max-candidates", "15");
            PathDeduplicator dedup = new PathDeduplicator(new PathDeduplicationConfig());
            assertThat(dedup.getConfig().getMaxCandidates()).isEqualTo(15);
        }

        @Test
        @DisplayName("hydrateMaxCandidatesFromSystem with invalid value")
        void hydrateMaxCandidates_invalid() {
            System.setProperty("tfi.change-tracking.degradation.max-candidates", "invalid");
            PathDeduplicator dedup = new PathDeduplicator(new PathDeduplicationConfig());
            assertThat(dedup.getConfig().getMaxCandidates()).isEqualTo(5);
        }

        @Test
        @DisplayName("hydrateMaxCandidatesFromSystem with value > 50")
        void hydrateMaxCandidates_tooHigh() {
            System.setProperty("tfi.change-tracking.degradation.max-candidates", "100");
            PathDeduplicator dedup = new PathDeduplicator(new PathDeduplicationConfig());
            assertThat(dedup.getConfig().getMaxCandidates()).isEqualTo(5);
        }

        @Test
        @DisplayName("hydrateMaxCandidatesFromSystem with value < 1")
        void hydrateMaxCandidates_tooLow() {
            System.setProperty("tfi.change-tracking.degradation.max-candidates", "0");
            PathDeduplicator dedup = new PathDeduplicator(new PathDeduplicationConfig());
            assertThat(dedup.getConfig().getMaxCandidates()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("PathDeduplicator — deduplicate and deduplicateLegacy")
    class PathDeduplicatorDeduplicateLegacy {

        @Test
        @DisplayName("deduplicate null records returns empty")
        void deduplicate_nullRecords() {
            PathDeduplicator dedup = new PathDeduplicator();
            List<ChangeRecord> result = dedup.deduplicate(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicate empty records")
        void deduplicate_emptyRecords() {
            PathDeduplicator dedup = new PathDeduplicator();
            List<ChangeRecord> result = dedup.deduplicate(Collections.emptyList());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicate legacy with single path group")
        void deduplicateLegacy_singlePathGroup() {
            PathDeduplicator dedup = new PathDeduplicator(true, new PathCache());
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("X", "a", "v1", "v2", ChangeType.UPDATE));
            List<ChangeRecord> result = dedup.deduplicate(records);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicate legacy with multiple path groups")
        void deduplicateLegacy_multiplePathGroups() {
            PathDeduplicator dedup = new PathDeduplicator(true, new PathCache());
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("X", "a", "v1", "v2", ChangeType.UPDATE),
                    ChangeRecord.of("X", "a", "v1", "v2", ChangeType.UPDATE),
                    ChangeRecord.of("X", "b", null, "v", ChangeType.CREATE));
            List<ChangeRecord> result = dedup.deduplicate(records);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("deduplicate legacy disabled returns input")
        void deduplicateLegacy_disabled() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(false);
            PathDeduplicator dedup = new PathDeduplicator(config);
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE));
            List<ChangeRecord> result = dedup.deduplicate(records);
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("PathDeduplicator — getStatistics and resetStatistics")
    class PathDeduplicatorStatistics {

        @Test
        @DisplayName("getStatistics with zero totalDeduplicationCount")
        void getStatistics_zeroTotal() {
            PathDeduplicator dedup = new PathDeduplicator();
            PathDeduplicator.DeduplicationStatistics stats = dedup.getStatistics();
            assertThat(stats.getTotalDeduplicationCount()).isZero();
            assertThat(stats.getDuplicateRemovalRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getStatistics with null collectorCacheStats")
        void getStatistics_nullCollectorCacheStats() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator.DeduplicationStatistics stats = new PathDeduplicator.DeduplicationStatistics(
                    1, 0, 0, 0, null, config, 0.0, 0, 0);
            assertThat(stats.getCollectorCacheStats()).isEmpty();
        }

        @Test
        @DisplayName("getStatistics DeduplicationStatistics toString")
        void getStatistics_toString() {
            PathDeduplicator dedup = new PathDeduplicator();
            dedup.deduplicate(List.of(ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE)));
            PathDeduplicator.DeduplicationStatistics stats = dedup.getStatistics();
            assertThat(stats.toString()).contains("DeduplicationStats").contains("total=");
        }

        @Test
        @DisplayName("DeduplicationStatistics getDuplicateRemovalRate zero total")
        void dedupStats_zeroTotal() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator.DeduplicationStatistics stats = new PathDeduplicator.DeduplicationStatistics(
                    0, 0, 0, 0, Collections.emptyMap(), config, 0.0, 0, 0);
            assertThat(stats.getDuplicateRemovalRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("DeduplicationStatistics meetsPerformanceThreshold false")
        void dedupStats_belowThreshold() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator.DeduplicationStatistics stats = new PathDeduplicator.DeduplicationStatistics(
                    1, 0, 0, 0, Collections.emptyMap(), config, 50.0, 0, 0);
            assertThat(stats.meetsPerformanceThreshold()).isFalse();
        }

        @Test
        @DisplayName("DeduplicationStatistics meetsPerformanceThreshold true")
        void dedupStats_meetsThreshold() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator.DeduplicationStatistics stats = new PathDeduplicator.DeduplicationStatistics(
                    1, 0, 0, 0, Collections.emptyMap(), config, 95.0, 0, 0);
            assertThat(stats.meetsPerformanceThreshold()).isTrue();
        }

        @Test
        @DisplayName("resetStatistics clears counts")
        void resetStatistics() {
            PathDeduplicator dedup = new PathDeduplicator();
            dedup.deduplicate(List.of(ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE)));
            dedup.resetStatistics();
            PathDeduplicator.DeduplicationStatistics stats = dedup.getStatistics();
            assertThat(stats.getTotalDeduplicationCount()).isZero();
        }

        @Test
        @DisplayName("getCacheStats deprecated returns non-null")
        void getCacheStats_deprecated() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator.DeduplicationStatistics stats = new PathDeduplicator.DeduplicationStatistics(
                    1, 0, 0, 0, Map.of("cacheSize", 10), config, 0.0, 0, 0);
            assertThat(stats.getCacheStats()).isNotNull();
        }
    }

    @Nested
    @DisplayName("PathDeduplicator — performPeriodicCleanup")
    class PathDeduplicatorPeriodicCleanup {

        @Test
        @DisplayName("performPeriodicCleanup triggered when interval exceeded")
        void performPeriodicCleanup_intervalExceeded() throws Exception {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setFastPathChangeLimit(1000);
            PathDeduplicator dedup = new PathDeduplicator(config);
            Field lastCleanupField = PathDeduplicator.class.getDeclaredField("lastCleanupTime");
            lastCleanupField.setAccessible(true);
            lastCleanupField.setLong(dedup, System.currentTimeMillis() - 70000);

            Map<String, Object> snapshot = Map.of("name", "value");
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("User", "name", "old", "value", ChangeType.UPDATE));
            dedup.deduplicateWithObjectGraph(records, snapshot, snapshot);
        }

        @Test
        @DisplayName("performPeriodicCleanup with cache over 80 percent")
        void performPeriodicCleanup_cacheOverCapacity() throws Exception {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCacheSize(10);
            config.setFastPathChangeLimit(1000);
            PathDeduplicator dedup = new PathDeduplicator(config);
            Field lastCleanupField = PathDeduplicator.class.getDeclaredField("lastCleanupTime");
            lastCleanupField.setAccessible(true);
            lastCleanupField.setLong(dedup, System.currentTimeMillis() - 70000);

            PathCache cache = dedup.getPathArbiter().getPathCache();
            for (int i = 0; i < 15; i++) {
                cache.put(new Object(), "path" + i);
            }
            Map<String, Object> snapshot = Map.of("name", "value");
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("User", "name", "old", "value", ChangeType.UPDATE));
            dedup.deduplicateWithObjectGraph(records, snapshot, snapshot);
        }
    }

    // ==================== PathArbiter ====================

    @Nested
    @DisplayName("PathArbiter — constructor")
    class PathArbiterConstructor {

        @Test
        @DisplayName("constructor with null config uses default")
        void constructor_nullConfig() {
            PathArbiter arbiter = new PathArbiter((PathDeduplicationConfig) null);
            assertThat(arbiter.getPathCache()).isNotNull();
        }

        @Test
        @DisplayName("default constructor")
        void defaultConstructor() {
            PathArbiter arbiter = new PathArbiter();
            assertThat(arbiter.getPathCache()).isNotNull();
        }
    }

    @Nested
    @DisplayName("PathArbiter — selectMostSpecificConfigurable")
    class PathArbiterSelectMostSpecificConfigurable {

        @Test
        @DisplayName("selectMostSpecificConfigurable throws for null candidates")
        void nullCandidates() {
            PathArbiter arbiter = new PathArbiter();
            assertThatThrownBy(() -> arbiter.selectMostSpecificConfigurable(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("selectMostSpecificConfigurable throws for empty candidates")
        void emptyCandidates() {
            PathArbiter arbiter = new PathArbiter();
            assertThatThrownBy(() -> arbiter.selectMostSpecificConfigurable(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("selectMostSpecificConfigurable single candidate")
        void singleCandidate() {
            PathArbiter arbiter = new PathArbiter();
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate result = arbiter.selectMostSpecificConfigurable(List.of(c));
            assertThat(result).isSameAs(c);
        }

        @Test
        @DisplayName("selectMostSpecificConfigurable multiple candidates uncached")
        void multipleCandidatesUncached() {
            PathArbiter arbiter = new PathArbiter();
            Object t = new Object();
            PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate result = arbiter.selectMostSpecificConfigurable(List.of(deep, shallow));
            assertThat(result.getPath()).isEqualTo("a.b.c");
        }

        @Test
        @DisplayName("selectMostSpecificConfigurable with cache hit")
        void cacheHit() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setCacheEnabled(true);
            PathArbiter arbiter = new PathArbiter(config);
            Object t = new Object();
            arbiter.getPathCache().put(t, "a.b.c");
            PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate result = arbiter.selectMostSpecificConfigurable(List.of(deep, shallow));
            assertThat(result.getPath()).isEqualTo("a.b.c");
        }

        @Test
        @DisplayName("selectMostSpecificConfigurable with cache miss writes back")
        void cacheMissWritesBack() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setCacheEnabled(true);
            PathArbiter arbiter = new PathArbiter(config);
            Object t = new Object();
            PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            arbiter.selectMostSpecificConfigurable(List.of(deep, shallow));
            assertThat(arbiter.getPathCache().get(t)).isEqualTo("a.b.c");
        }
    }

    @Nested
    @DisplayName("PathArbiter — selectMostSpecific static")
    class PathArbiterSelectMostSpecificStatic {

        @Test
        @DisplayName("selectMostSpecific throws for null")
        void nullCandidates() {
            assertThatThrownBy(() -> PathArbiter.selectMostSpecific(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("selectMostSpecific throws for empty")
        void emptyCandidates() {
            assertThatThrownBy(() -> PathArbiter.selectMostSpecific(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("selectMostSpecific single item")
        void singleItem() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate result = PathArbiter.selectMostSpecific(List.of(c));
            assertThat(result).isSameAs(c);
        }

        @Test
        @DisplayName("selectMostSpecific multiple items")
        void multipleItems() {
            Object t = new Object();
            PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate result = PathArbiter.selectMostSpecific(List.of(deep, shallow));
            assertThat(result.getPath()).isEqualTo("a.b.c");
        }
    }

    @Nested
    @DisplayName("PathArbiter — deduplicateConfigurable")
    class PathArbiterDeduplicateConfigurable {

        @Test
        @DisplayName("deduplicateConfigurable null returns empty")
        void nullPaths() {
            PathArbiter arbiter = new PathArbiter();
            List<PathArbiter.PathCandidate> result = arbiter.deduplicateConfigurable(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateConfigurable empty returns empty")
        void emptyPaths() {
            PathArbiter arbiter = new PathArbiter();
            List<PathArbiter.PathCandidate> result = arbiter.deduplicateConfigurable(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateConfigurable with paths")
        void withPaths() {
            PathArbiter arbiter = new PathArbiter();
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> result = arbiter.deduplicateConfigurable(List.of(c));
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("PathArbiter.PathCandidate")
    class PathArbiterPathCandidate {

        @Test
        @DisplayName("PathCandidate null path defaults to empty")
        void nullPath() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate(null, 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c.getPath()).isEmpty();
            assertThat(c.getStableId()).startsWith("ID");
        }

        @Test
        @DisplayName("PathCandidate null accessType defaults to FIELD")
        void nullAccessType() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, null, t);
            assertThat(c.getAccessType()).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("PathCandidate getTargetId with null target")
        void getTargetId_nullTarget() {
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, null);
            assertThat(c.getTargetId()).isEqualTo("null-target");
        }

        @Test
        @DisplayName("PathCandidate generateStableId with empty path")
        void generateStableId_emptyPath() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c.getStableId()).isEqualTo("ID00000000");
        }

        @Test
        @DisplayName("PathCandidate equals same instance")
        void equals_sameInstance() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c).isEqualTo(c);
        }

        @Test
        @DisplayName("PathCandidate equals different target")
        void equals_differentTarget() {
            PathArbiter.PathCandidate a = new PathArbiter.PathCandidate("p", 1, PathArbiter.AccessType.FIELD, new Object());
            PathArbiter.PathCandidate b = new PathArbiter.PathCandidate("p", 1, PathArbiter.AccessType.FIELD, new Object());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("PathCandidate equals null")
        void equals_null() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c).isNotEqualTo(null);
        }

        @Test
        @DisplayName("PathCandidate equals different class")
        void equals_differentClass() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c).isNotEqualTo("x");
        }

        @Test
        @DisplayName("PathCandidate hashCode consistent")
        void hashCode_consistent() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c.hashCode()).isEqualTo(c.hashCode());
        }

        @Test
        @DisplayName("PathCandidate convenience constructor")
        void convenienceConstructor() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("list[0]", 1, t);
            assertThat(c.getAccessType()).isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
        }

        @Test
        @DisplayName("PathCandidate toString")
        void toString_format() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, t);
            assertThat(c.toString()).contains("PathCandidate").contains("a.b");
        }
    }

    @Nested
    @DisplayName("PathArbiter.AccessType — fromPath")
    class PathArbiterAccessTypeFromPath {

        @Test
        @DisplayName("fromPath null returns FIELD")
        void nullPath() {
            assertThat(PathArbiter.AccessType.fromPath(null)).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("fromPath empty returns FIELD")
        void emptyPath() {
            assertThat(PathArbiter.AccessType.fromPath("")).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("fromPath bracket format map key with quotes")
        void bracketMapKeyQuotes() {
            assertThat(PathArbiter.AccessType.fromPath("map[\"key\"]")).isEqualTo(PathArbiter.AccessType.MAP_KEY);
        }

        @Test
        @DisplayName("fromPath bracket format array index")
        void bracketArrayIndex() {
            assertThat(PathArbiter.AccessType.fromPath("list[123]")).isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
        }

        @Test
        @DisplayName("fromPath bracket format set element id=")
        void bracketSetElement() {
            assertThat(PathArbiter.AccessType.fromPath("set[id=abc]")).isEqualTo(PathArbiter.AccessType.SET_ELEMENT);
        }

        @Test
        @DisplayName("fromPath bracket format map key no quotes")
        void bracketMapKeyNoQuotes() {
            assertThat(PathArbiter.AccessType.fromPath("map[abc]")).isEqualTo(PathArbiter.AccessType.MAP_KEY);
        }

        @Test
        @DisplayName("fromPath dot format field")
        void dotFormatField() {
            assertThat(PathArbiter.AccessType.fromPath("user.name")).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("fromPath simple field")
        void simpleField() {
            assertThat(PathArbiter.AccessType.fromPath("name")).isEqualTo(PathArbiter.AccessType.FIELD);
        }
    }

    @Nested
    @DisplayName("PathArbiter — isAncestor via deduplicateMostSpecific")
    class PathArbiterIsAncestor {

        @Test
        @DisplayName("deduplicateMostSpecific null returns empty")
        void nullCandidates() {
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateMostSpecific empty returns empty")
        void emptyCandidates() {
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateMostSpecific removes ancestor when descendant exists")
        void removesAncestor() {
            Object t = new Object();
            PathArbiter.PathCandidate ancestor = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate descendant = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of(ancestor, descendant));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("a.b.c");
        }

        @Test
        @DisplayName("deduplicateMostSpecific removes ancestor with bracket")
        void removesAncestorBracket() {
            Object t = new Object();
            PathArbiter.PathCandidate ancestor = new PathArbiter.PathCandidate("list", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate descendant = new PathArbiter.PathCandidate("list[0]", 1, PathArbiter.AccessType.ARRAY_INDEX, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of(ancestor, descendant));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("list[0]");
        }

        @Test
        @DisplayName("deduplicateMostSpecific keeps non-related paths")
        void keepsNonRelatedPaths() {
            Object t1 = new Object();
            Object t2 = new Object();
            PathArbiter.PathCandidate c1 = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, t1);
            PathArbiter.PathCandidate c2 = new PathArbiter.PathCandidate("x.y", 1, PathArbiter.AccessType.FIELD, t2);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of(c1, c2));
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("deduplicateMostSpecific skips null path")
        void skipsNullPath() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("", 0, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of(c));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateMostSpecific single candidate")
        void singleCandidate() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of(c));
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("PathArbiter — verifyStability")
    class PathArbiterVerifyStability {

        @Test
        @DisplayName("verifyStability null returns true")
        void nullCandidates() {
            assertThat(PathArbiter.verifyStability(null, 5)).isTrue();
        }

        @Test
        @DisplayName("verifyStability empty list returns true")
        void emptyList() {
            assertThat(PathArbiter.verifyStability(Collections.emptyList(), 5)).isTrue();
        }

        @Test
        @DisplayName("verifyStability iterations < 1 returns true")
        void zeroIterations() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(PathArbiter.verifyStability(List.of(c), 0)).isTrue();
        }

        @Test
        @DisplayName("verifyStability single iteration returns true")
        void singleIteration() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(PathArbiter.verifyStability(List.of(c), 1)).isTrue();
        }

        @Test
        @DisplayName("verifyStability consistent results returns true")
        void consistentResults() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            assertThat(PathArbiter.verifyStability(List.of(c), 10)).isTrue();
        }

        @Test
        @DisplayName("verifyStability multiple candidates deterministic")
        void multipleCandidatesDeterministic() {
            Object t = new Object();
            PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(PathArbiter.verifyStability(List.of(deep, shallow), 5)).isTrue();
        }
    }

    @Nested
    @DisplayName("PathArbiter — selectMostSpecificAdvanced")
    class PathArbiterSelectMostSpecificAdvanced {

        @Test
        @DisplayName("selectMostSpecificAdvanced delegates to selectMostSpecific")
        void delegatesToSelectMostSpecific() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate result = PathArbiter.selectMostSpecificAdvanced(List.of(c), "custom");
            assertThat(result).isSameAs(c);
        }
    }

    @Nested
    @DisplayName("PathArbiter — deduplicate static")
    class PathArbiterDeduplicateStatic {

        @Test
        @DisplayName("deduplicate null returns empty")
        void nullPaths() {
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicate(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicate empty returns empty")
        void emptyPaths() {
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicate(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicate groups by target")
        void groupsByTarget() {
            Object t = new Object();
            PathArbiter.PathCandidate c1 = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate c2 = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicate(List.of(c1, c2));
            assertThat(result).hasSize(1);
        }
    }

    // ==================== PathBuilder edge cases ====================

    @Nested
    @DisplayName("PathBuilder — Edge cases")
    class PathBuilderEdgeCases {

        @Test
        @DisplayName("mapKey with deeply nested key containing dots")
        void mapKey_dotsInKey() {
            String r = PathBuilder.mapKey("root", "a.b.c");
            assertThat(r).isEqualTo("root[\"a.b.c\"]");
        }

        @Test
        @DisplayName("mapKey with brackets in key")
        void mapKey_bracketsInKey() {
            String r = PathBuilder.mapKey("root", "key[0]");
            assertThat(r).contains("key").contains("[");
        }

        @Test
        @DisplayName("fieldPath with empty fieldName")
        void fieldPath_emptyFieldName() {
            assertThat(PathBuilder.fieldPath("root", "")).isEqualTo("root.");
        }

        @Test
        @DisplayName("chain builder with multiple array indices")
        void chain_multipleArrayIndices() {
            String path = PathBuilder.start("arr")
                    .arrayIndex(0)
                    .field("nested")
                    .arrayIndex(1)
                    .build();
            assertThat(path).contains("[0]").contains("[1]");
        }
    }

    // ==================== PathCollector ====================

    @Nested
    @DisplayName("PathCollector — Edge cases")
    class PathCollectorEdgeCases {

        @Test
        @DisplayName("collectPathsForObject with Map containing target")
        void collectPathsForObject_mapContainingTarget() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Object target = "value";
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("key", target);
            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(target, "key", snapshot);
            assertThat(paths).isNotEmpty();
        }

        @Test
        @DisplayName("collectPathsForObject null target returns empty")
        void collectPathsForObject_nullTarget() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(null, "path", Map.of());
            assertThat(paths).isEmpty();
        }

        @Test
        @DisplayName("getCacheStatistics")
        void getCacheStatistics() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            Map<String, Object> stats = collector.getCacheStatistics();
            assertThat(stats).containsKey("cacheSize").containsKey("cacheEnabled");
        }
    }

    // ==================== PathCache ====================

    @Nested
    @DisplayName("PathCache — Edge cases")
    class PathCacheEdgeCases {

        @Test
        @DisplayName("get with disabled cache returns null")
        void get_disabledCache() {
            PathCache cache = new PathCache(false, 100);
            Object k = new Object();
            cache.put(k, "path");
            assertThat(cache.get(k)).isNull();
        }

        @Test
        @DisplayName("get with null object returns null")
        void get_nullObject() {
            PathCache cache = new PathCache(true, 100);
            assertThat(cache.get(null)).isNull();
        }

        @Test
        @DisplayName("put with same path skips duplicate")
        void put_samePathSkips() {
            PathCache cache = new PathCache(true, 100);
            Object k = new Object();
            cache.put(k, "p1");
            cache.put(k, "p1");
            assertThat(cache.getStatistics().getPuts()).isEqualTo(1);
        }
    }

    // ==================== PriorityCalculator ====================

    @Nested
    @DisplayName("PriorityCalculator — Edge cases")
    class PriorityCalculatorEdgeCases {

        @Test
        @DisplayName("sortByPriority ARRAY_INDEX vs FIELD")
        void sortByPriority_arrayVsField() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            Object t = new Object();
            PathArbiter.PathCandidate arr = new PathArbiter.PathCandidate("list[0]", 1, PathArbiter.AccessType.ARRAY_INDEX, t);
            PathArbiter.PathCandidate field = new PathArbiter.PathCandidate("name", 0, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> sorted = pc.sortByPriority(List.of(arr, field));
            assertThat(sorted).hasSize(2);
        }

        @Test
        @DisplayName("selectHighestPriority with single candidate")
        void selectHighestPriority_single() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate r = pc.selectHighestPriority(List.of(c));
            assertThat(r).isSameAs(c);
        }
    }
}
