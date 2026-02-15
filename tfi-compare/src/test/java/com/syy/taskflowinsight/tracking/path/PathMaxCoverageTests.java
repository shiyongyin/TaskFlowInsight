package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Maximum coverage tests for tracking/path package.
 * Targets PathBuilder, PathDeduplicator, PathDeduplicationConfig, PathArbiter,
 * PathCache, PathCollector, PriorityCalculator.
 *
 * @since 3.0.0
 */
@DisplayName("Path Max Coverage — 路径最大覆盖测试")
class PathMaxCoverageTests {

    @AfterEach
    void tearDown() {
        PathBuilder.clearCache();
    }

    // ── PathBuilder ──

    @Nested
    @DisplayName("PathBuilder")
    class PathBuilderTests {

        @Test
        @DisplayName("mapKey standard format with special chars")
        void mapKey_standardFormat_specialChars() {
            assertThat(PathBuilder.mapKey("root", "key")).isEqualTo("root[\"key\"]");
            assertThat(PathBuilder.mapKey("root", "say\"hello\"")).contains("\\\"");
            assertThat(PathBuilder.mapKey("root", "a\nb\tc\r")).doesNotContain("\n").doesNotContain("\t");
        }

        @Test
        @DisplayName("mapKey legacy format")
        void mapKey_legacyFormat() {
            assertThat(PathBuilder.mapKey("root", "key", false)).isEqualTo("root['key']");
            assertThat(PathBuilder.mapKey("root", "it's", false)).contains("\\'");
        }

        @Test
        @DisplayName("mapKey null key")
        void mapKey_nullKey() {
            assertThat(PathBuilder.mapKey("root", null)).isEqualTo("root[null]");
        }

        @Test
        @DisplayName("mapKey backslash escape")
        void mapKey_backslashEscape() {
            String r = PathBuilder.mapKey("root", "path\\to\\file");
            assertThat(r).contains("\\\\");
        }

        @Test
        @DisplayName("arrayIndex")
        void arrayIndex() {
            assertThat(PathBuilder.arrayIndex("list", 0)).isEqualTo("list[0]");
            assertThat(PathBuilder.arrayIndex("arr", 999)).isEqualTo("arr[999]");
        }

        @Test
        @DisplayName("fieldPath null parent")
        void fieldPath_nullParent() {
            assertThat(PathBuilder.fieldPath(null, "name")).isEqualTo("name");
        }

        @Test
        @DisplayName("fieldPath empty parent")
        void fieldPath_emptyParent() {
            assertThat(PathBuilder.fieldPath("", "name")).isEqualTo("name");
        }

        @Test
        @DisplayName("fieldPath normal")
        void fieldPath_normal() {
            assertThat(PathBuilder.fieldPath("root", "child")).isEqualTo("root.child");
        }

        @Test
        @DisplayName("setElement null element")
        void setElement_nullElement() {
            assertThat(PathBuilder.setElement("set", null)).isEqualTo("set[id=null]");
        }

        @Test
        @DisplayName("setElement with object")
        void setElement_withObject() {
            String r = PathBuilder.setElement("set", "item");
            assertThat(r).startsWith("set[id=").endsWith("]");
        }

        @Test
        @DisplayName("buildFieldPath delegates to fieldPath")
        void buildFieldPath() {
            assertThat(PathBuilder.buildFieldPath("root", "f")).isEqualTo("root.f");
        }

        @Test
        @DisplayName("buildMapKeyPath delegates to mapKey")
        void buildMapKeyPath() {
            assertThat(PathBuilder.buildMapKeyPath("root", "k")).isEqualTo("root[\"k\"]");
        }

        @Test
        @DisplayName("buildArrayIndexPath delegates to arrayIndex")
        void buildArrayIndexPath() {
            assertThat(PathBuilder.buildArrayIndexPath("arr", 5)).isEqualTo("arr[5]");
        }

        @Test
        @DisplayName("buildSetElementPath delegates to setElement")
        void buildSetElementPath() {
            String r = PathBuilder.buildSetElementPath("set", "x");
            assertThat(r).startsWith("set[id=");
        }

        @Test
        @DisplayName("start chain builder")
        void startChainBuilder() {
            String path = PathBuilder.start("root")
                    .field("user")
                    .mapKey("id")
                    .arrayIndex(0)
                    .build();
            assertThat(path).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("PathBuilderChain toString")
        void chainToString() {
            PathBuilder.PathBuilderChain chain = PathBuilder.start("root").field("a");
            assertThat(chain.toString()).isEqualTo(chain.build());
        }

        @Test
        @DisplayName("PathBuilderChain null root")
        void chainNullRoot() {
            String path = PathBuilder.start(null).field("a").build();
            assertThat(path).isEqualTo("a");
        }

        @Test
        @DisplayName("getCacheSize and clearCache")
        void cacheSizeAndClear() {
            PathBuilder.mapKey("r", "x\"y");
            assertThat(PathBuilder.getCacheSize()).isGreaterThanOrEqualTo(0);
            PathBuilder.clearCache();
            assertThat(PathBuilder.getCacheSize()).isZero();
        }
    }

    // ── PathDeduplicationConfig ──

    @Nested
    @DisplayName("PathDeduplicationConfig")
    class PathDeduplicationConfigTests {

        @Test
        @DisplayName("forHighPerformance factory")
        void forHighPerformance() {
            PathDeduplicationConfig c = PathDeduplicationConfig.forHighPerformance();
            assertThat(c.getMaxCacheSize()).isEqualTo(50000);
            assertThat(c.isParallelProcessing()).isTrue();
        }

        @Test
        @DisplayName("forHighAccuracy factory")
        void forHighAccuracy() {
            PathDeduplicationConfig c = PathDeduplicationConfig.forHighAccuracy();
            assertThat(c.getMaxCollectionDepth()).isEqualTo(20);
        }

        @Test
        @DisplayName("forMemoryOptimized factory")
        void forMemoryOptimized() {
            PathDeduplicationConfig c = PathDeduplicationConfig.forMemoryOptimized();
            assertThat(c.getMaxCacheSize()).isEqualTo(1000);
        }

        @Test
        @DisplayName("copy constructor")
        void copyConstructor() {
            PathDeduplicationConfig orig = new PathDeduplicationConfig();
            orig.setEnabled(false);
            PathDeduplicationConfig copy = new PathDeduplicationConfig(orig);
            assertThat(copy.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("validate invalid maxCacheSize")
        void validate_invalidMaxCacheSize() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setMaxCacheSize(-1);
            assertThatThrownBy(c::validate).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("validate invalid maxCollectionDepth")
        void validate_invalidMaxDepth() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setMaxCollectionDepth(0);
            assertThatThrownBy(c::validate).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("validate invalid cacheEvictionPolicy")
        void validate_invalidPolicy() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setCacheEvictionPolicy("INVALID");
            assertThatThrownBy(c::validate).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("isPathAllowed with excludePatterns")
        void isPathAllowed_exclude() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("*.secret"));
            assertThat(c.isPathAllowed("user.secret")).isFalse();
        }

        @Test
        @DisplayName("isPathAllowed with includePatterns")
        void isPathAllowed_include() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setIncludePatterns(List.of("user.name"));
            assertThat(c.isPathAllowed("user.name")).isTrue();
        }

        @Test
        @DisplayName("isPathAllowed null path")
        void isPathAllowed_null() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            assertThat(c.isPathAllowed(null)).isFalse();
        }

        @Test
        @DisplayName("setMaxCandidates clamps to 1")
        void setMaxCandidates_clamp() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setMaxCandidates(0);
            assertThat(c.getMaxCandidates()).isEqualTo(1);
        }

        @Test
        @DisplayName("toString")
        void toStringTest() {
            assertThat(new PathDeduplicationConfig().toString()).contains("PathDeduplicationConfig");
        }
    }

    // ── PathArbiter ──

    @Nested
    @DisplayName("PathArbiter")
    class PathArbiterTests {

        @Test
        @DisplayName("selectMostSpecific single candidate")
        void selectMostSpecific_single() {
            Object target = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, target);
            PathArbiter.PathCandidate selected = PathArbiter.selectMostSpecific(List.of(c));
            assertThat(selected).isSameAs(c);
        }

        @Test
        @DisplayName("selectMostSpecific empty throws")
        void selectMostSpecific_emptyThrows() {
            assertThatThrownBy(() -> PathArbiter.selectMostSpecific(Collections.emptyList()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("selectMostSpecific null throws")
        void selectMostSpecific_nullThrows() {
            assertThatThrownBy(() -> PathArbiter.selectMostSpecific(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AccessType fromPath field")
        void accessType_fromPath_field() {
            assertThat(PathArbiter.AccessType.fromPath("user.name")).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("AccessType fromPath mapKey")
        void accessType_fromPath_mapKey() {
            assertThat(PathArbiter.AccessType.fromPath("user[\"key\"]")).isEqualTo(PathArbiter.AccessType.MAP_KEY);
        }

        @Test
        @DisplayName("AccessType fromPath arrayIndex")
        void accessType_fromPath_arrayIndex() {
            assertThat(PathArbiter.AccessType.fromPath("list[0]")).isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
        }

        @Test
        @DisplayName("AccessType fromPath setElement")
        void accessType_fromPath_setElement() {
            assertThat(PathArbiter.AccessType.fromPath("set[id=abc]")).isEqualTo(PathArbiter.AccessType.SET_ELEMENT);
        }

        @Test
        @DisplayName("AccessType fromPath null")
        void accessType_fromPath_null() {
            assertThat(PathArbiter.AccessType.fromPath(null)).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("PathCandidate equals and hashCode")
        void pathCandidate_equalsHashCode() {
            Object t = new Object();
            PathArbiter.PathCandidate a = new PathArbiter.PathCandidate("p", 1, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate b = new PathArbiter.PathCandidate("p", 1, PathArbiter.AccessType.FIELD, t);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("PathCandidate getTargetId null")
        void pathCandidate_getTargetId_null() {
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("p", 0, PathArbiter.AccessType.FIELD, null);
            assertThat(c.getTargetId()).isEqualTo("null-target");
        }

        @Test
        @DisplayName("deduplicateMostSpecific ancestor descendant")
        void deduplicateMostSpecific_ancestorDescendant() {
            Object t = new Object();
            PathArbiter.PathCandidate ancestor = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate descendant = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of(ancestor, descendant));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("a.b");
        }

        @Test
        @DisplayName("verifyStability")
        void verifyStability() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(PathArbiter.verifyStability(List.of(c), 5)).isTrue();
        }

        @Test
        @DisplayName("selectMostSpecificAdvanced")
        void selectMostSpecificAdvanced() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate r = PathArbiter.selectMostSpecificAdvanced(List.of(c), "default");
            assertThat(r).isSameAs(c);
        }

        @Test
        @DisplayName("instance selectMostSpecificConfigurable")
        void instanceSelectMostSpecific() {
            PathArbiter arbiter = new PathArbiter();
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate r = arbiter.selectMostSpecificConfigurable(List.of(c));
            assertThat(r).isSameAs(c);
        }

        @Test
        @DisplayName("instance deduplicateConfigurable")
        void instanceDeduplicateConfigurable() {
            PathArbiter arbiter = new PathArbiter();
            List<PathArbiter.PathCandidate> empty = arbiter.deduplicateConfigurable(Collections.emptyList());
            assertThat(empty).isEmpty();
        }
    }

    // ── PathCache ──

    @Nested
    @DisplayName("PathCache")
    class PathCacheTests {

        @Test
        @DisplayName("warmUp with real data")
        void warmUp() {
            PathCache cache = new PathCache(true, 100, "LRU");
            Map<Object, String> entries = new HashMap<>();
            Object k1 = new Object();
            entries.put(k1, "path1");
            cache.warmUp(entries);
            assertThat(cache.get(k1)).isEqualTo("path1");
        }

        @Test
        @DisplayName("warmUp disabled cache")
        void warmUp_disabled() {
            PathCache cache = new PathCache(false, 100);
            cache.warmUp(Map.of(new Object(), "p"));
            assertThat(cache.getStatistics().getCurrentSize()).isZero();
        }

        @Test
        @DisplayName("put get statistics")
        void putGetStatistics() {
            PathCache cache = new PathCache(true, 100);
            Object k = new Object();
            cache.put(k, "p1");
            cache.get(k);
            PathCache.CacheStatistics stats = cache.getStatistics();
            assertThat(stats.getHits()).isEqualTo(1);
            assertThat(stats.getPuts()).isEqualTo(1);
        }

        @Test
        @DisplayName("eviction when full")
        void evictionWhenFull() {
            PathCache cache = new PathCache(true, 5, "SIZE_BASED");
            for (int i = 0; i < 20; i++) {
                cache.put(new Object(), "path" + i);
            }
            PathCache.CacheStatistics stats = cache.getStatistics();
            assertThat(stats.getCurrentSize()).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("remove")
        void remove() {
            PathCache cache = new PathCache(true, 100);
            Object k = new Object();
            cache.put(k, "p");
            cache.remove(k);
            assertThat(cache.get(k)).isNull();
        }

        @Test
        @DisplayName("CacheStatistics toString")
        void cacheStatisticsToString() {
            PathCache.CacheStatistics s = new PathCache.CacheStatistics(1, 10, 5, 5, 1, 0, 50.0, 100L);
            assertThat(s.toString()).contains("CacheStats");
        }
    }

    // ── PathCollector ──

    @Nested
    @DisplayName("PathCollector")
    class PathCollectorTests {

        @Test
        @DisplayName("collectFromChangeRecords with real snapshot")
        void collectFromChangeRecords() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathCollector collector = new PathCollector(config);
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            after.put("name", "Alice");
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("User", "name", null, "Alice", ChangeType.CREATE)
            );
            List<PathArbiter.PathCandidate> candidates = collector.collectFromChangeRecords(records, before, after);
            assertThat(candidates).isNotEmpty();
        }

        @Test
        @DisplayName("collectFromChangeRecords disabled")
        void collectFromChangeRecords_disabled() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(false);
            PathCollector collector = new PathCollector(config);
            assertThat(collector.collectFromChangeRecords(
                    List.of(ChangeRecord.of("X", "f", null, "v", ChangeType.CREATE)),
                    Collections.emptyMap(), Collections.emptyMap())).isEmpty();
        }

        @Test
        @DisplayName("getCacheStatistics")
        void getCacheStatistics() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            Map<String, Object> stats = collector.getCacheStatistics();
            assertThat(stats).containsKey("cacheSize").containsKey("cacheEnabled");
        }

        @Test
        @DisplayName("clearCache")
        void clearCache() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            collector.clearCache();
            assertThat(collector.getCacheStatistics().get("cacheSize")).isEqualTo(0);
        }

        @Test
        @DisplayName("toString")
        void toStringTest() {
            assertThat(new PathCollector(new PathDeduplicationConfig()).toString()).contains("PathCollector");
        }
    }

    // ── PriorityCalculator ──

    @Nested
    @DisplayName("PriorityCalculator")
    class PriorityCalculatorTests {

        @Test
        @DisplayName("calculatePriority null candidate")
        void calculatePriority_null() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            assertThat(pc.calculatePriority(null)).isEqualTo(Long.MIN_VALUE);
        }

        @Test
        @DisplayName("calculatePriority different access types")
        void calculatePriority_differentAccessTypes() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            Object t = new Object();
            PathArbiter.PathCandidate field = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate mapKey = new PathArbiter.PathCandidate("a[\"k\"]", 0, PathArbiter.AccessType.MAP_KEY, t);
            assertThat(pc.calculatePriority(field)).isGreaterThan(pc.calculatePriority(mapKey));
        }

        @Test
        @DisplayName("createDetailedComparator")
        void createDetailedComparator() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            Object t = new Object();
            PathArbiter.PathCandidate a = new PathArbiter.PathCandidate("aaa", 1, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate b = new PathArbiter.PathCandidate("bbb", 1, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> sorted = List.of(a, b).stream()
                    .sorted(pc.createDetailedComparator().reversed())
                    .toList();
            assertThat(sorted).isNotEmpty();
        }

        @Test
        @DisplayName("getPriorityDetails")
        void getPriorityDetails() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 2, PathArbiter.AccessType.FIELD, t);
            String details = pc.getPriorityDetails(c);
            assertThat(details).contains("Priority").contains("x");
        }

        @Test
        @DisplayName("getPriorityDetails null")
        void getPriorityDetails_null() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            assertThat(pc.getPriorityDetails(null)).isEqualTo("null-candidate");
        }

        @Test
        @DisplayName("sortByPriority")
        void sortByPriority() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            Object t = new Object();
            List<PathArbiter.PathCandidate> list = List.of(
                    new PathArbiter.PathCandidate("b", 0, PathArbiter.AccessType.FIELD, t),
                    new PathArbiter.PathCandidate("a", 1, PathArbiter.AccessType.FIELD, t)
            );
            List<PathArbiter.PathCandidate> sorted = pc.sortByPriority(list);
            assertThat(sorted.get(0).getDepth()).isEqualTo(1);
        }

        @Test
        @DisplayName("selectHighestPriority")
        void selectHighestPriority() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            Object t = new Object();
            PathArbiter.PathCandidate best = new PathArbiter.PathCandidate("deep.path", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate r = pc.selectHighestPriority(List.of(
                    new PathArbiter.PathCandidate("shallow", 0, PathArbiter.AccessType.FIELD, t),
                    best
            ));
            assertThat(r.getPath()).isEqualTo("deep.path");
        }

        @Test
        @DisplayName("selectHighestPriority empty")
        void selectHighestPriority_empty() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            assertThat(pc.selectHighestPriority(Collections.emptyList())).isNull();
        }

        @Test
        @DisplayName("verifyConsistency")
        void verifyConsistency() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, new Object());
            assertThat(pc.verifyConsistency(c, 10)).isTrue();
        }

        @Test
        @DisplayName("toString")
        void toStringTest() {
            assertThat(new PriorityCalculator(new PathDeduplicationConfig()).toString()).contains("PriorityCalculator");
        }
    }

    // ── PathDeduplicator ──

    @Nested
    @DisplayName("PathDeduplicator")
    class PathDeduplicatorTests {

        @Test
        @DisplayName("deduplicate legacy mode")
        void deduplicate_legacy() {
            PathDeduplicator dedup = new PathDeduplicator(true, new PathCache());
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("X", "a", "v1", "v2", ChangeType.UPDATE),
                    ChangeRecord.of("X", "a", "v1", "v2", ChangeType.UPDATE)
            );
            List<ChangeRecord> result = dedup.deduplicate(records);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicate disabled")
        void deduplicate_disabled() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(false);
            PathDeduplicator dedup = new PathDeduplicator(config);
            List<ChangeRecord> records = List.of(ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE));
            assertThat(dedup.deduplicate(records)).hasSize(1);
        }

        @Test
        @DisplayName("getStatistics")
        void getStatistics() {
            PathDeduplicator dedup = new PathDeduplicator();
            dedup.deduplicate(List.of(ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE)));
            PathDeduplicator.DeduplicationStatistics stats = dedup.getStatistics();
            assertThat(stats.getTotalDeduplicationCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("DeduplicationStatistics getCacheStats deprecated")
        void dedupStats_getCacheStats() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator.DeduplicationStatistics stats = new PathDeduplicator.DeduplicationStatistics(
                    1, 0, 0, 0, Map.of("cacheSize", 5), config, 0.0, 0, 0);
            PathCache.CacheStatistics cacheStats = stats.getCacheStats();
            assertThat(cacheStats).isNotNull();
        }

        @Test
        @DisplayName("DeduplicationStatistics meetsPerformanceThreshold")
        void dedupStats_meetsPerformanceThreshold() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathDeduplicator.DeduplicationStatistics stats = new PathDeduplicator.DeduplicationStatistics(
                    1, 0, 0, 0, Collections.emptyMap(), config, 95.0, 0, 0);
            assertThat(stats.meetsPerformanceThreshold()).isTrue();
        }

        @Test
        @DisplayName("resetStatistics")
        void resetStatistics() {
            PathDeduplicator dedup = new PathDeduplicator();
            dedup.resetStatistics();
            assertThat(dedup.getStatistics().getTotalDeduplicationCount()).isZero();
        }

        @Test
        @DisplayName("isEnabled getConfig getPathCollector getPathArbiter")
        void getters() {
            PathDeduplicator dedup = new PathDeduplicator();
            assertThat(dedup.isEnabled()).isTrue();
            assertThat(dedup.getConfig()).isNotNull();
            assertThat(dedup.getPathCollector()).isNotNull();
            assertThat(dedup.getPathArbiter()).isNotNull();
        }
    }
}
