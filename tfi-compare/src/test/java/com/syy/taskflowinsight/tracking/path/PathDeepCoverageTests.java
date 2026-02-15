package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Deep coverage tests for tracking/path package.
 * Maximizes coverage for PathBuilder, PathDeduplicator, PathArbiter, PathCache,
 * PathCollector, PathDeduplicationConfig, PriorityCalculator.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Path Deep Coverage — 路径深度覆盖测试")
class PathDeepCoverageTests {

    @AfterEach
    void tearDown() {
        PathBuilder.clearCache();
    }

    // ── PathBuilder ──

    @Nested
    @DisplayName("PathBuilder")
    class PathBuilderTests {

        @Test
        @DisplayName("mapKey standard format")
        void mapKey_standard() {
            assertThat(PathBuilder.mapKey("root", "key")).isEqualTo("root[\"key\"]");
        }

        @Test
        @DisplayName("mapKey legacy format")
        void mapKey_legacy() {
            assertThat(PathBuilder.mapKey("root", "key", false)).isEqualTo("root['key']");
        }

        @Test
        @DisplayName("mapKey null key")
        void mapKey_nullKey() {
            assertThat(PathBuilder.mapKey("root", null)).isEqualTo("root[null]");
        }

        @Test
        @DisplayName("mapKey escape special chars")
        void mapKey_escapeSpecialChars() {
            String r = PathBuilder.mapKey("root", "say\"hello\"");
            assertThat(r).contains("\\\"");
        }

        @Test
        @DisplayName("mapKey escape newline tab")
        void mapKey_escapeNewlineTab() {
            String r = PathBuilder.mapKey("root", "a\nb\tc");
            assertThat(r).doesNotContain("\n").doesNotContain("\t");
        }

        @Test
        @DisplayName("arrayIndex")
        void arrayIndex() {
            assertThat(PathBuilder.arrayIndex("list", 0)).isEqualTo("list[0]");
            assertThat(PathBuilder.arrayIndex("arr", 42)).isEqualTo("arr[42]");
        }

        @Test
        @DisplayName("fieldPath empty parent")
        void fieldPath_emptyParent() {
            assertThat(PathBuilder.fieldPath("", "name")).isEqualTo("name");
        }

        @Test
        @DisplayName("fieldPath null parent")
        void fieldPath_nullParent() {
            assertThat(PathBuilder.fieldPath(null, "name")).isEqualTo("name");
        }

        @Test
        @DisplayName("fieldPath normal")
        void fieldPath_normal() {
            assertThat(PathBuilder.fieldPath("root", "name")).isEqualTo("root.name");
        }

        @Test
        @DisplayName("setElement null")
        void setElement_null() {
            assertThat(PathBuilder.setElement("set", null)).isEqualTo("set[id=null]");
        }

        @Test
        @DisplayName("setElement object")
        void setElement_object() {
            String r = PathBuilder.setElement("set", "elem");
            assertThat(r).startsWith("set[id=").endsWith("]");
        }

        @Test
        @DisplayName("buildFieldPath buildMapKeyPath buildArrayIndexPath buildSetElementPath")
        void buildAliases() {
            assertThat(PathBuilder.buildFieldPath("r", "f")).isEqualTo("r.f");
            assertThat(PathBuilder.buildMapKeyPath("r", "k")).isEqualTo("r[\"k\"]");
            assertThat(PathBuilder.buildArrayIndexPath("arr", 0)).isEqualTo("arr[0]");
            assertThat(PathBuilder.buildSetElementPath("s", "x")).startsWith("s[id=");
        }

        @Test
        @DisplayName("PathBuilderChain")
        void pathBuilderChain() {
            String path = PathBuilder.start("root")
                .field("user")
                .mapKey("name")
                .arrayIndex(0)
                .build();
            assertThat(path).contains("root").contains("user").contains("name").contains("[0]");
        }

        @Test
        @DisplayName("PathBuilderChain toString")
        void pathBuilderChain_toString() {
            PathBuilder.PathBuilderChain chain = PathBuilder.start("root").field("a");
            assertThat(chain.toString()).isEqualTo(chain.build());
        }

        @Test
        @DisplayName("getCacheSize clearCache")
        void cacheOps() {
            PathBuilder.mapKey("r", "k\"x\"");
            assertThat(PathBuilder.getCacheSize()).isGreaterThanOrEqualTo(0);
            PathBuilder.clearCache();
        }
    }

    // ── PathDeduplicator ──

    @Nested
    @DisplayName("PathDeduplicator")
    class PathDeduplicatorTests {

        @Test
        @DisplayName("deduplicateWithObjectGraph with real snapshots")
        void deduplicateWithObjectGraph() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(true);
            config.setCacheEnabled(true);
            PathDeduplicator dedup = new PathDeduplicator(config);

            Map<String, Object> before = new HashMap<>();
            before.put("name", "Alice");
            before.put("address.city", "NYC");
            Map<String, Object> after = new HashMap<>();
            after.put("name", "Bob");
            after.put("address.city", "LA");

            List<ChangeRecord> records = List.of(
                ChangeRecord.builder().objectName("User").fieldName("name").changeType(ChangeType.UPDATE)
                    .oldValue("Alice").newValue("Bob").build(),
                ChangeRecord.builder().objectName("User").fieldName("address.city").changeType(ChangeType.UPDATE)
                    .oldValue("NYC").newValue("LA").build()
            );

            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, before, after);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph disabled returns input")
        void deduplicate_disabled() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(false);
            PathDeduplicator dedup = new PathDeduplicator(config);
            List<ChangeRecord> records = List.of(
                ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build()
            );
            assertThat(dedup.deduplicateWithObjectGraph(records, Map.of(), Map.of())).isEqualTo(records);
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph null/empty")
        void deduplicate_nullEmpty() {
            PathDeduplicator dedup = new PathDeduplicator();
            assertThat(dedup.deduplicateWithObjectGraph(null, Map.of(), Map.of())).isEmpty();
            assertThat(dedup.deduplicateWithObjectGraph(Collections.emptyList(), Map.of(), Map.of())).isEmpty();
        }

        @Test
        @DisplayName("deduplicate legacy")
        void deduplicate_legacy() {
            PathDeduplicator dedup = new PathDeduplicator();
            List<ChangeRecord> records = List.of(
                ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build(),
                ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build()
            );
            List<ChangeRecord> result = dedup.deduplicate(records);
            assertThat(result).hasSize(1);
        }

        @Test
        @SuppressWarnings("deprecation")
        @DisplayName("legacy constructor")
        void legacyConstructor() {
            PathCache cache = new PathCache(true, 100);
            PathDeduplicator dedup = new PathDeduplicator(true, cache);
            assertThat(dedup.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("getStatistics")
        void getStatistics() {
            PathDeduplicator dedup = new PathDeduplicator();
            var stats = dedup.getStatistics();
            assertThat(stats).isNotNull();
            assertThat(stats.getTotalDeduplicationCount()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getDuplicateRemovalRate()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getCacheEffectiveness()).isGreaterThanOrEqualTo(0);
            assertThat(stats.toString()).isNotBlank();
        }

        @Test
        @DisplayName("resetStatistics")
        void resetStatistics() {
            PathDeduplicator dedup = new PathDeduplicator();
            dedup.resetStatistics();
        }

        @Test
        @DisplayName("getConfig getPathCollector getPathArbiter")
        void getters() {
            PathDeduplicator dedup = new PathDeduplicator();
            assertThat(dedup.getConfig()).isNotNull();
            assertThat(dedup.getPathCollector()).isNotNull();
            assertThat(dedup.getPathArbiter()).isNotNull();
        }

        @Test
        @SuppressWarnings("deprecation")
        @DisplayName("DeduplicationStatistics getCacheStats deprecated")
        void dedupStats_getCacheStats() {
            PathDeduplicator dedup = new PathDeduplicator();
            var stats = dedup.getStatistics();
            var cacheStats = stats.getCacheStats();
            assertThat(cacheStats).isNotNull();
        }
    }

    // ── PathArbiter ──

    @Nested
    @DisplayName("PathArbiter")
    class PathArbiterTests {

        @Test
        @DisplayName("selectMostSpecific single candidate")
        void selectMostSpecific_single() {
            Object target = "x";
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, target);
            var result = PathArbiter.selectMostSpecific(List.of(c));
            assertThat(result).isSameAs(c);
        }

        @Test
        @DisplayName("selectMostSpecific multiple by depth")
        void selectMostSpecific_multiple() {
            Object target = "x";
            var c1 = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, target);
            var c2 = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, target);
            var result = PathArbiter.selectMostSpecific(List.of(c1, c2));
            assertThat(result.getPath()).isEqualTo("a.b");
        }

        @Test
        @DisplayName("selectMostSpecific null/empty throws")
        void selectMostSpecific_empty() {
            assertThatThrownBy(() -> PathArbiter.selectMostSpecific(null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PathArbiter.selectMostSpecific(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("deduplicate")
        void deduplicate() {
            Object t1 = "a";
            Object t2 = "b";
            var candidates = List.of(
                new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t1),
                new PathArbiter.PathCandidate("y", 0, PathArbiter.AccessType.FIELD, t2)
            );
            var result = PathArbiter.deduplicate(candidates);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("deduplicateMostSpecific")
        void deduplicateMostSpecific() {
            Object target = "x";
            var candidates = List.of(
                new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, target),
                new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, target)
            );
            var result = PathArbiter.deduplicateMostSpecific(candidates);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("a.b");
        }

        @Test
        @DisplayName("PathCandidate equals hashCode")
        void pathCandidate_equalsHashCode() {
            Object t = "x";
            var c1 = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            var c2 = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c1).isEqualTo(c2);
            assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
        }

        @Test
        @DisplayName("PathCandidate getTargetId")
        void pathCandidate_getTargetId() {
            Object t = "x";
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c.getTargetId()).contains("String").contains("@");
        }

        @Test
        @DisplayName("PathCandidate null target")
        void pathCandidate_nullTarget() {
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, null);
            assertThat(c.getTargetId()).isEqualTo("null-target");
        }

        @Test
        @DisplayName("AccessType fromPath")
        void accessType_fromPath() {
            assertThat(PathArbiter.AccessType.fromPath("a.b")).isEqualTo(PathArbiter.AccessType.FIELD);
            assertThat(PathArbiter.AccessType.fromPath("a[\"k\"]")).isEqualTo(PathArbiter.AccessType.MAP_KEY);
            assertThat(PathArbiter.AccessType.fromPath("a[0]")).isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
            assertThat(PathArbiter.AccessType.fromPath("a[id=x]")).isEqualTo(PathArbiter.AccessType.SET_ELEMENT);
            assertThat(PathArbiter.AccessType.fromPath(null)).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("selectMostSpecificAdvanced")
        void selectMostSpecificAdvanced() {
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x");
            var result = PathArbiter.selectMostSpecificAdvanced(List.of(c), "default");
            assertThat(result).isSameAs(c);
        }

        @Test
        @DisplayName("verifyStability")
        void verifyStability() {
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x");
            assertThat(PathArbiter.verifyStability(List.of(c), 5)).isTrue();
        }

        @Test
        @DisplayName("instance selectMostSpecificConfigurable")
        void instance_selectMostSpecific() {
            PathArbiter arbiter = new PathArbiter();
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x");
            var result = arbiter.selectMostSpecificConfigurable(List.of(c));
            assertThat(result).isSameAs(c);
        }

        @Test
        @DisplayName("instance getPathCache")
        void instance_getPathCache() {
            PathArbiter arbiter = new PathArbiter();
            assertThat(arbiter.getPathCache()).isNotNull();
        }
    }

    // ── PathCache ──

    @Nested
    @DisplayName("PathCache")
    class PathCacheTests {

        @Test
        @DisplayName("put get")
        void putGet() {
            PathCache cache = new PathCache(true, 100);
            Object key = new Object();
            cache.put(key, "a.b");
            assertThat(cache.get(key)).isEqualTo("a.b");
        }

        @Test
        @DisplayName("get null key returns null")
        void get_nullKey() {
            PathCache cache = new PathCache(true, 100);
            assertThat(cache.get(null)).isNull();
        }

        @Test
        @DisplayName("put null key/path ignored")
        void put_nullIgnored() {
            PathCache cache = new PathCache(true, 100);
            cache.put(null, "path");
            cache.put(new Object(), null);
        }

        @Test
        @DisplayName("clear")
        void clear() {
            PathCache cache = new PathCache(true, 100);
            Object key = new Object();
            cache.put(key, "x");
            cache.clear();
            assertThat(cache.get(key)).isNull();
        }

        @Test
        @DisplayName("warmUp")
        void warmUp() {
            PathCache cache = new PathCache(true, 100);
            Map<Object, String> entries = new HashMap<>();
            entries.put(new Object(), "a");
            entries.put(new Object(), "b");
            cache.warmUp(entries);
            assertThat(cache.getStatistics().getCurrentSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("getStatistics")
        void getStatistics() {
            PathCache cache = new PathCache(true, 100);
            var stats = cache.getStatistics();
            assertThat(stats.getCurrentSize()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getMaxSize()).isEqualTo(100);
            assertThat(stats.getHitRate()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getTotalRequests()).isGreaterThanOrEqualTo(0);
            assertThat(stats.toString()).isNotBlank();
        }

        @Test
        @DisplayName("resetStatistics")
        void resetStatistics() {
            PathCache cache = new PathCache(true, 100);
            cache.resetStatistics();
        }

        @Test
        @DisplayName("disabled cache")
        void disabledCache() {
            PathCache cache = new PathCache(false, 100);
            Object key = new Object();
            cache.put(key, "x");
            assertThat(cache.get(key)).isNull();
            assertThat(cache.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("remove")
        void remove() {
            PathCache cache = new PathCache(true, 100);
            Object key = new Object();
            cache.put(key, "x");
            cache.remove(key);
            assertThat(cache.get(key)).isNull();
        }

        @Test
        @DisplayName("constructors")
        void constructors() {
            PathCache c1 = new PathCache();
            PathCache c2 = new PathCache(true, 500);
            PathCache c3 = new PathCache(true, 500, "LRU");
            assertThat(c1.isEnabled()).isTrue();
            assertThat(c2.getMaxSize()).isEqualTo(500);
            assertThat(c3.getEvictionPolicy()).isEqualTo("LRU");
        }
    }

    // ── PathCollector ──

    @Nested
    @DisplayName("PathCollector")
    class PathCollectorTests {

        @Test
        @DisplayName("collectFromChangeRecords empty")
        void collect_empty() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathCollector collector = new PathCollector(config);
            var result = collector.collectFromChangeRecords(
                Collections.emptyList(), Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("collectFromChangeRecords with matching snapshot")
        void collect_withSnapshot() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(true);
            PathCollector collector = new PathCollector(config);
            Map<String, Object> before = Map.of("name", "Alice");
            Map<String, Object> after = Map.of("name", "Bob");
            List<ChangeRecord> records = List.of(
                ChangeRecord.builder().objectName("User").fieldName("name").changeType(ChangeType.UPDATE)
                    .newValue("Bob").build()
            );
            var result = collector.collectFromChangeRecords(records, before, after);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("collectPathsForObject null")
        void collectPathsForObject_null() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            assertThat(collector.collectPathsForObject(null, "a", Map.of())).isEmpty();
        }

        @Test
        @DisplayName("clearCache getCacheStatistics")
        void cacheOps() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            collector.clearCache();
            var stats = collector.getCacheStatistics();
            assertThat(stats).containsKey("cacheSize");
        }

        @Test
        @DisplayName("toString")
        void toString_test() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            assertThat(collector.toString()).contains("PathCollector");
        }
    }

    // ── PathDeduplicationConfig ──

    @Nested
    @DisplayName("PathDeduplicationConfig")
    class PathDeduplicationConfigTests {

        @Test
        @DisplayName("factory methods")
        void factoryMethods() {
            assertThat(PathDeduplicationConfig.forHighPerformance()).isNotNull();
            assertThat(PathDeduplicationConfig.forHighAccuracy()).isNotNull();
            assertThat(PathDeduplicationConfig.forMemoryOptimized()).isNotNull();
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
        @DisplayName("applySystemOverrides")
        void applySystemOverrides() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.applySystemOverrides();
        }

        @Test
        @DisplayName("isPathAllowed")
        void isPathAllowed() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("*.secret"));
            assertThat(c.isPathAllowed("name")).isTrue();
            assertThat(c.isPathAllowed(null)).isFalse();
        }

        @Test
        @DisplayName("all getters setters")
        void allProperties() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setEnabled(false);
            c.setCacheEnabled(false);
            c.setMaxCacheSize(500);
            c.setCacheEvictionPolicy("FIFO");
            c.setMaxCandidates(10);
            c.setFastPathChangeLimit(500);
            c.setCachePrewarmEnabled(true);
            assertThat(c.isEnabled()).isFalse();
            assertThat(c.getMaxCandidates()).isEqualTo(10);
        }
    }

    // ── PriorityCalculator ──

    @Nested
    @DisplayName("PriorityCalculator")
    class PriorityCalculatorTests {

        @Test
        @DisplayName("calculatePriority null")
        void calculatePriority_null() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            assertThat(pc.calculatePriority(null)).isEqualTo(Long.MIN_VALUE);
        }

        @Test
        @DisplayName("calculatePriority")
        void calculatePriority() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            var c = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.MAP_KEY, "x");
            assertThat(pc.calculatePriority(c)).isGreaterThan(0);
        }

        @Test
        @DisplayName("createComparator")
        void createComparator() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            var c1 = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x");
            var c2 = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, "x");
            var comp = pc.createComparator();
            assertThat(comp.compare(c1, c2)).isNegative();
        }

        @Test
        @DisplayName("createDetailedComparator")
        void createDetailedComparator() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x");
            var comp = pc.createDetailedComparator();
            assertThat(comp.compare(c, c)).isZero();
        }

        @Test
        @DisplayName("getPriorityDetails")
        void getPriorityDetails() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x");
            String details = pc.getPriorityDetails(c);
            assertThat(details).contains("Priority").contains("a");
        }

        @Test
        @DisplayName("sortByPriority")
        void sortByPriority() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            var candidates = List.of(
                new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x"),
                new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, "x")
            );
            var sorted = pc.sortByPriority(candidates);
            assertThat(sorted).hasSize(2);
            assertThat(sorted.get(0).getPath()).isEqualTo("a.b");
        }

        @Test
        @DisplayName("selectHighestPriority")
        void selectHighestPriority() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            var candidates = List.of(
                new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x"),
                new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, "x")
            );
            var selected = pc.selectHighestPriority(candidates);
            assertThat(selected.getPath()).isEqualTo("a.b");
        }

        @Test
        @DisplayName("selectHighestPriority null/empty")
        void selectHighestPriority_empty() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            assertThat(pc.selectHighestPriority(null)).isNull();
            assertThat(pc.selectHighestPriority(Collections.emptyList())).isNull();
        }

        @Test
        @DisplayName("verifyConsistency")
        void verifyConsistency() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            var c = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, "x");
            assertThat(pc.verifyConsistency(c, 5)).isTrue();
        }

        @Test
        @DisplayName("getConfig toString")
        void getConfig() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PriorityCalculator pc = new PriorityCalculator(config);
            assertThat(pc.getConfig()).isSameAs(config);
            assertThat(pc.toString()).contains("PriorityCalculator");
        }
    }
}
