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
 * Branch coverage tests for tracking/path package.
 * Targets edge cases: deeply nested paths, special characters, PathDeduplicator
 * object-graph flow, PathCollector Map/List/Set traversal, PathArbiter AccessType.
 *
 * @since 3.0.0
 */
@DisplayName("Path Branch Coverage — 路径分支覆盖测试")
class PathBranchCoverageTests {

    @AfterEach
    void tearDown() {
        PathBuilder.clearCache();
    }

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
        @DisplayName("mapKey with newline in key triggers escape cache")
        void mapKey_newlineInKey() {
            String r = PathBuilder.mapKey("root", "a\nb");
            assertThat(r).contains("\\n").doesNotContain("\n");
            assertThat(PathBuilder.getCacheSize()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("mapKey with carriage return")
        void mapKey_carriageReturn() {
            String r = PathBuilder.mapKey("root", "x\ry");
            assertThat(r).contains("\\r");
        }

        @Test
        @DisplayName("mapKey with tab")
        void mapKey_tab() {
            String r = PathBuilder.mapKey("root", "a\tb");
            assertThat(r).contains("\\t");
        }

        @Test
        @DisplayName("mapKey legacy format with backslash in key")
        void mapKey_legacy_backslash() {
            String r = PathBuilder.mapKey("root", "path\\to", false);
            assertThat(r).contains("path");
        }

        @Test
        @DisplayName("setElement with object having negative hashCode")
        void setElement_negativeHashCode() {
            Object obj = new Object() {
                @Override
                public int hashCode() { return -12345; }
                @Override
                public String toString() { return "neg"; }
            };
            String r = PathBuilder.setElement("set", obj);
            assertThat(r).startsWith("set[id=").endsWith("]");
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

        @Test
        @DisplayName("chain builder with mapKey then field")
        void chain_mapKeyThenField() {
            String path = PathBuilder.start("map")
                    .mapKey("user")
                    .field("name")
                    .build();
            assertThat(path).contains("[\"").contains("user").contains("name");
        }
    }

    @Nested
    @DisplayName("PathDeduplicationConfig — isPathAllowed edge cases")
    class PathDeduplicationConfigEdgeCases {

        @Test
        @DisplayName("isPathAllowed with includePatterns empty returns true for non-excluded")
        void isPathAllowed_emptyInclude_nonExcluded() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("*.secret"));
            assertThat(c.isPathAllowed("user.name")).isTrue();
        }

        @Test
        @DisplayName("isPathAllowed with excludePatterns matching")
        void isPathAllowed_excludeMatches() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("*.secret", "internal.*"));
            assertThat(c.isPathAllowed("user.secret")).isFalse();
        }

        @Test
        @DisplayName("globToRegex with special regex chars")
        void isPathAllowed_specialRegexChars() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setExcludePatterns(List.of("field(1)"));
            assertThat(c.isPathAllowed("field(1)")).isFalse();
        }

        @Test
        @DisplayName("validate invalid statisticsReportInterval")
        void validate_invalidStatisticsReportInterval() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setStatisticsReportInterval(0);
            assertThatThrownBy(c::validate).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("validate invalid maxObjectsPerLevel")
        void validate_invalidMaxObjectsPerLevel() {
            PathDeduplicationConfig c = new PathDeduplicationConfig();
            c.setMaxObjectsPerLevel(0);
            assertThatThrownBy(c::validate).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("PathArbiter — AccessType and deduplicate edge cases")
    class PathArbiterEdgeCases {

        @Test
        @DisplayName("AccessType fromPath map key without quotes")
        void accessType_mapKeyNoQuotes() {
            assertThat(PathArbiter.AccessType.fromPath("map[abc]")).isEqualTo(PathArbiter.AccessType.MAP_KEY);
        }

        @Test
        @DisplayName("AccessType fromPath empty string")
        void accessType_emptyPath() {
            assertThat(PathArbiter.AccessType.fromPath("")).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("PathCandidate with null path defaults to empty")
        void pathCandidate_nullPath() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate(null, 0, PathArbiter.AccessType.FIELD, t);
            assertThat(c.getPath()).isEmpty();
            assertThat(c.getStableId()).startsWith("ID");
        }

        @Test
        @DisplayName("PathCandidate with null AccessType defaults to FIELD")
        void pathCandidate_nullAccessType() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, null, t);
            assertThat(c.getAccessType()).isEqualTo(PathArbiter.AccessType.FIELD);
        }

        @Test
        @DisplayName("PathCandidate equals different target")
        void pathCandidate_equals_differentTarget() {
            PathArbiter.PathCandidate a = new PathArbiter.PathCandidate("p", 1, PathArbiter.AccessType.FIELD, new Object());
            PathArbiter.PathCandidate b = new PathArbiter.PathCandidate("p", 1, PathArbiter.AccessType.FIELD, new Object());
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("deduplicateMostSpecific with ancestor path removal")
        void deduplicateMostSpecific_removesAncestor() {
            Object t = new Object();
            PathArbiter.PathCandidate a = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate b = new PathArbiter.PathCandidate("a.b", 1, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of(a, b));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("a.b.c");
        }

        @Test
        @DisplayName("deduplicateMostSpecific with null path skipped")
        void deduplicateMostSpecific_skipsNullPath() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("", 0, PathArbiter.AccessType.FIELD, t);
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicateMostSpecific(List.of(c));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("verifyStability with empty list")
        void verifyStability_emptyList() {
            assertThat(PathArbiter.verifyStability(Collections.emptyList(), 5)).isTrue();
        }

        @Test
        @DisplayName("verifyStability with iterations < 1")
        void verifyStability_zeroIterations() {
            Object t = new Object();
            PathArbiter.PathCandidate c = new PathArbiter.PathCandidate("x", 0, PathArbiter.AccessType.FIELD, t);
            assertThat(PathArbiter.verifyStability(List.of(c), 0)).isTrue();
        }

        @Test
        @DisplayName("instance selectMostSpecificConfigurable with multiple candidates")
        void instanceSelectMostSpecific_multipleCandidates() {
            PathArbiter arbiter = new PathArbiter();
            Object t = new Object();
            PathArbiter.PathCandidate deep = new PathArbiter.PathCandidate("a.b.c", 2, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate shallow = new PathArbiter.PathCandidate("a", 0, PathArbiter.AccessType.FIELD, t);
            PathArbiter.PathCandidate r = arbiter.selectMostSpecificConfigurable(List.of(deep, shallow));
            assertThat(r.getPath()).isEqualTo("a.b.c");
        }
    }

    @Nested
    @DisplayName("PathDeduplicator — Object graph and legacy flows")
    class PathDeduplicatorObjectGraph {

        @Test
        @DisplayName("deduplicateWithObjectGraph null records returns empty")
        void deduplicateWithObjectGraph_nullRecords() {
            PathDeduplicator dedup = new PathDeduplicator();
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(null, Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph empty records")
        void deduplicateWithObjectGraph_emptyRecords() {
            PathDeduplicator dedup = new PathDeduplicator();
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(Collections.emptyList(), Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph disabled config")
        void deduplicateWithObjectGraph_disabled() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(false);
            PathDeduplicator dedup = new PathDeduplicator(config);
            List<ChangeRecord> records = List.of(ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE));
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, Map.of(), Map.of());
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicateWithObjectGraph with snapshot and valid paths")
        void deduplicateWithObjectGraph_withSnapshot() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setFastPathChangeLimit(1000);
            PathDeduplicator dedup = new PathDeduplicator(config);
            Map<String, Object> before = Map.of("name", "old");
            Map<String, Object> after = Map.of("name", "new");
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("User", "name", "old", "new", ChangeType.UPDATE)
            );
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(records, before, after);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("deduplicate legacy with duplicate paths")
        void deduplicate_legacy_duplicates() {
            PathDeduplicator dedup = new PathDeduplicator(true, new PathCache());
            List<ChangeRecord> records = List.of(
                    ChangeRecord.of("X", "a", "v1", "v2", ChangeType.UPDATE),
                    ChangeRecord.of("X", "a", "v1", "v2", ChangeType.UPDATE),
                    ChangeRecord.of("X", "b", null, "v", ChangeType.CREATE)
            );
            List<ChangeRecord> result = dedup.deduplicate(records);
            assertThat(result).hasSize(2);
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
    }

    @Nested
    @DisplayName("PathCollector — Map List Set traversal")
    class PathCollectorTraversal {

        @Test
        @DisplayName("collectPathsForObject with Map containing target")
        void collectPathsForObject_mapContainingTarget() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Object target = "value";
            Map<String, Object> root = new HashMap<>();
            root.put("key", target);
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("key", target);
            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(target, "key", snapshot);
            assertThat(paths).isNotEmpty();
        }

        @Test
        @DisplayName("collectPathsForObject with List containing target")
        void collectPathsForObject_listContainingTarget() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Object target = "item";
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("[0]", target);
            List<PathArbiter.PathCandidate> paths = collector.collectPathsForObject(target, "[0]", snapshot);
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
        @DisplayName("collectFromChangeRecords with null record skipped")
        void collectFromChangeRecords_skipsNullRecord() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<ChangeRecord> records = Arrays.asList(
                    ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE),
                    null
            );
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                    records, Map.of(), Map.of("a", "v"));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("collectFromChangeRecords with null fieldName skipped")
        void collectFromChangeRecords_skipsNullFieldName() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            ChangeRecord record = ChangeRecord.of("X", null, null, "v", ChangeType.CREATE);
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                    List.of(record), Map.of(), Map.of());
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("PathCache — Eviction policies")
    class PathCacheEviction {

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

        @Test
        @DisplayName("warmUp with null key skipped")
        void warmUp_skipsNullKey() {
            PathCache cache = new PathCache(true, 100);
            Map<Object, String> entries = new HashMap<>();
            entries.put(null, "path");
            cache.warmUp(entries);
            assertThat(cache.getStatistics().getCurrentSize()).isZero();
        }

        @Test
        @DisplayName("warmUp with null value skipped")
        void warmUp_skipsNullValue() {
            PathCache cache = new PathCache(true, 100);
            Map<Object, String> entries = new HashMap<>();
            entries.put(new Object(), null);
            cache.warmUp(entries);
            assertThat(cache.getStatistics().getCurrentSize()).isZero();
        }
    }

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
            assertThat(sorted.get(0).getAccessType()).isEqualTo(PathArbiter.AccessType.FIELD);
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

        @Test
        @DisplayName("verifyConsistency with null candidate")
        void verifyConsistency_nullCandidate() {
            PriorityCalculator pc = new PriorityCalculator(new PathDeduplicationConfig());
            assertThat(pc.verifyConsistency(null, 5)).isTrue();
        }
    }
}
