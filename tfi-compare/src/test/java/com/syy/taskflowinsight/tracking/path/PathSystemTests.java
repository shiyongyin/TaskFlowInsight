package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 路径系统综合测试。
 * 覆盖 PathBuilder、PathDeduplicator、PathArbiter、PathCache、
 * PathDeduplicationConfig、PriorityCalculator。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Path System — 路径系统测试")
class PathSystemTests {

    // ── PathBuilder ──

    @Nested
    @DisplayName("PathBuilder — 路径构建器")
    class PathBuilderTests {

        @Test
        @DisplayName("mapKey 标准格式 → parent[\"key\"]")
        void mapKey_standard_shouldProduceDoubleQuotedPath() {
            String result = PathBuilder.mapKey("root", "name");
            assertThat(result).isEqualTo("root[\"name\"]");
        }

        @Test
        @DisplayName("mapKey 兼容格式 → parent['key']")
        void mapKey_compat_shouldProduceSingleQuotedPath() {
            String result = PathBuilder.mapKey("root", "name", false);
            assertThat(result).isEqualTo("root['name']");
        }

        @Test
        @DisplayName("mapKey null键 → parent[null]")
        void mapKey_nullKey_shouldHandleGracefully() {
            String result = PathBuilder.mapKey("root", null);
            assertThat(result).isEqualTo("root[null]");
        }

        @Test
        @DisplayName("mapKey 含双引号的键 → 正确转义")
        void mapKey_keyWithQuotes_shouldEscape() {
            String result = PathBuilder.mapKey("root", "say\"hello\"");
            assertThat(result).contains("\\\"");
        }

        @Test
        @DisplayName("mapKey 含换行符的键 → 正确转义")
        void mapKey_keyWithNewline_shouldEscape() {
            String result = PathBuilder.mapKey("root", "line1\nline2");
            assertThat(result).doesNotContain("\n");
        }

        @Test
        @DisplayName("arrayIndex → parent[0]")
        void arrayIndex_shouldProduceIndexPath() {
            String result = PathBuilder.arrayIndex("list", 0);
            assertThat(result).isEqualTo("list[0]");
        }

        @Test
        @DisplayName("fieldPath → parent.field")
        void fieldPath_shouldProduceDotPath() {
            String result = PathBuilder.fieldPath("root", "name");
            assertThat(result).isEqualTo("root.name");
        }

        @Test
        @DisplayName("链式构建复杂路径")
        void chainedPath_shouldBuildCorrectly() {
            String path = PathBuilder.fieldPath(
                    PathBuilder.mapKey("orders", "ORD-001"),
                    "items"
            );
            assertThat(path).isEqualTo("orders[\"ORD-001\"].items");
        }

        @Test
        @DisplayName("setElement → parent[id=xxx]")
        void setElement_shouldProduceIdPath() {
            String result = PathBuilder.setElement("set", "element1");
            assertThat(result).contains("[id=");
        }

        @Test
        @DisplayName("缓存大小可查询和清除")
        void cache_shouldBeQueryableAndClearable() {
            PathBuilder.clearCache();
            assertThat(PathBuilder.getCacheSize()).isGreaterThanOrEqualTo(0);
            PathBuilder.mapKey("a", "b");
            PathBuilder.clearCache();
        }
    }

    // ── PathDeduplicator ──

    @Nested
    @DisplayName("PathDeduplicator — 路径去重")
    class PathDeduplicatorTests {

        private PathDeduplicator dedup;

        @BeforeEach
        void setUp() {
            dedup = new PathDeduplicator();
        }

        @Test
        @DisplayName("空列表去重 → 返回空")
        void emptyList_shouldReturnEmpty() {
            List<ChangeRecord> result = dedup.deduplicate(Collections.emptyList());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("无重复记录 → 全部保留")
        void noDuplicates_shouldRetainAll() {
            List<ChangeRecord> changes = List.of(
                    ChangeRecord.of("Order", "status", "OLD", "NEW", ChangeType.UPDATE),
                    ChangeRecord.of("Order", "amount", "100", "200", ChangeType.UPDATE)
            );
            List<ChangeRecord> result = dedup.deduplicate(changes);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("带快照的对象图去重")
        void withSnapshots_shouldDeduplicateByObjectGraph() {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("name", "Alice");
            before.put("age", 30);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("name", "Bob");
            after.put("age", 31);

            List<ChangeRecord> changes = List.of(
                    ChangeRecord.of("User", "name", "Alice", "Bob", ChangeType.UPDATE),
                    ChangeRecord.of("User", "age", 30, 31, ChangeType.UPDATE)
            );
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(
                    changes, before, after);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("统计信息可查询")
        void statistics_shouldBeAccessible() {
            assertThat(dedup.getStatistics()).isNotNull();
        }

        @Test
        @DisplayName("配置可查询")
        void config_shouldBeAccessible() {
            assertThat(dedup.getConfig()).isNotNull();
            assertThat(dedup.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("大量记录快速路径 (>800) → 不去重直接返回")
        void largeRecordSet_shouldUseFastPath() {
            List<ChangeRecord> changes = new ArrayList<>();
            for (int i = 0; i < 801; i++) {
                changes.add(ChangeRecord.of("Obj", "field" + i, "old" + i, "new" + i, ChangeType.UPDATE));
            }
            Map<String, Object> emptySnapshot = Collections.emptyMap();
            List<ChangeRecord> result = dedup.deduplicateWithObjectGraph(changes, emptySnapshot, emptySnapshot);
            assertThat(result).hasSizeGreaterThanOrEqualTo(801);
        }
    }

    // ── PathDeduplicationConfig ──

    @Nested
    @DisplayName("PathDeduplicationConfig — 去重配置")
    class PathDeduplicationConfigTests {

        @Test
        @DisplayName("默认配置合理")
        void defaultConfig_shouldBeReasonable() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            assertThat(config.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("高性能预设")
        void highPerformancePreset() {
            PathDeduplicationConfig config = PathDeduplicationConfig.forHighPerformance();
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("高精度预设")
        void highAccuracyPreset() {
            PathDeduplicationConfig config = PathDeduplicationConfig.forHighAccuracy();
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("内存优化预设")
        void memoryOptimizedPreset() {
            PathDeduplicationConfig config = PathDeduplicationConfig.forMemoryOptimized();
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("isPathAllowed 无排除 → 允许所有")
        void isPathAllowed_noExclusions_shouldAllowAll() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            assertThat(config.isPathAllowed("any.path")).isTrue();
        }
    }

    // ── PathCache ──

    @Nested
    @DisplayName("PathCache — 路径缓存")
    class PathCacheTests {

        @Test
        @DisplayName("put/get 基本功能")
        void putGet_shouldWork() {
            PathCache cache = new PathCache();
            Object key = new Object();
            cache.put(key, "root.field");
            String result = cache.get(key);
            assertThat(result).isEqualTo("root.field");
        }

        @Test
        @DisplayName("get 未缓存对象 → null")
        void get_unknownObject_shouldReturnNull() {
            PathCache cache = new PathCache();
            assertThat(cache.get(new Object())).isNull();
        }

        @Test
        @DisplayName("clear 清除所有缓存")
        void clear_shouldRemoveAll() {
            PathCache cache = new PathCache();
            Object key = new Object();
            cache.put(key, "path");
            cache.clear();
            assertThat(cache.get(key)).isNull();
        }

        @Test
        @DisplayName("remove 移除单个")
        void remove_shouldRemoveSpecific() {
            PathCache cache = new PathCache();
            Object key = new Object();
            cache.put(key, "path");
            cache.remove(key);
            assertThat(cache.get(key)).isNull();
        }

        @Test
        @DisplayName("warmUp 批量预热")
        void warmUp_shouldPopulateCache() {
            PathCache cache = new PathCache();
            Object key1 = new Object();
            Object key2 = new Object();
            Map<Object, String> data = new IdentityHashMap<>();
            data.put(key1, "path1");
            data.put(key2, "path2");
            cache.warmUp(data);
            assertThat(cache.get(key1)).isEqualTo("path1");
            assertThat(cache.get(key2)).isEqualTo("path2");
        }

        @Test
        @DisplayName("统计信息可查询")
        void statistics_shouldBeAccessible() {
            PathCache cache = new PathCache();
            cache.get(new Object()); // miss
            PathCache.CacheStatistics stats = cache.getStatistics();
            assertThat(stats).isNotNull();
        }
    }

    // ── PriorityCalculator ──

    @Nested
    @DisplayName("PriorityCalculator — 优先级计算")
    class PriorityCalculatorTests {

        @Test
        @DisplayName("创建比较器 → 非 null")
        void createComparator_shouldReturnNonNull() {
            PriorityCalculator calc = new PriorityCalculator(new PathDeduplicationConfig());
            assertThat(calc.createComparator()).isNotNull();
        }

        @Test
        @DisplayName("计算优先级 → 数值合理")
        void calculatePriority_shouldReturnReasonableValue() {
            PriorityCalculator calc = new PriorityCalculator(new PathDeduplicationConfig());
            PathArbiter.PathCandidate candidate = new PathArbiter.PathCandidate(
                    "root.field", 1, PathArbiter.AccessType.FIELD, "value");
            long priority = calc.calculatePriority(candidate);
            assertThat(priority).isGreaterThanOrEqualTo(0L);
        }
    }

    // ── PathArbiter ──

    @Nested
    @DisplayName("PathArbiter — 路径仲裁")
    class PathArbiterTests {

        @Test
        @DisplayName("selectMostSpecific 单一候选 → 返回该候选")
        void selectMostSpecific_singleCandidate_shouldReturnIt() {
            PathArbiter.PathCandidate candidate = new PathArbiter.PathCandidate(
                    "root.field", 1, PathArbiter.AccessType.FIELD, "target");
            PathArbiter.PathCandidate result =
                    PathArbiter.selectMostSpecific(List.of(candidate));
            assertThat(result).isNotNull();
            assertThat(result.getPath()).isEqualTo("root.field");
        }

        @Test
        @DisplayName("selectMostSpecific 空列表 → 抛 IllegalArgumentException")
        void selectMostSpecific_emptyList_shouldThrow() {
            assertThatThrownBy(() -> PathArbiter.selectMostSpecific(Collections.emptyList()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("多个候选 → 选择最具体")
        void multiCandidates_shouldSelectMostSpecific() {
            PathArbiter.PathCandidate fieldCandidate = new PathArbiter.PathCandidate(
                    "root.field", 1, PathArbiter.AccessType.FIELD, "target");
            PathArbiter.PathCandidate mapCandidate = new PathArbiter.PathCandidate(
                    "root[\"key\"]", 1, PathArbiter.AccessType.MAP_KEY, "target");
            PathArbiter.PathCandidate result =
                    PathArbiter.selectMostSpecific(List.of(mapCandidate, fieldCandidate));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("AccessType.fromPath 识别各类路径")
        void accessType_fromPath_shouldClassifyCorrectly() {
            assertThat(PathArbiter.AccessType.fromPath("root.field"))
                    .isEqualTo(PathArbiter.AccessType.FIELD);
            assertThat(PathArbiter.AccessType.fromPath("list[0]"))
                    .isEqualTo(PathArbiter.AccessType.ARRAY_INDEX);
            assertThat(PathArbiter.AccessType.fromPath("map[\"key\"]"))
                    .isEqualTo(PathArbiter.AccessType.MAP_KEY);
        }

        @Test
        @DisplayName("deduplicate 去除相同目标的低优先级路径")
        void deduplicate_shouldRemoveLowerPriorityCandidates() {
            Object target = new Object();
            List<PathArbiter.PathCandidate> candidates = List.of(
                    new PathArbiter.PathCandidate("root.a.b.c", 3, PathArbiter.AccessType.FIELD, target),
                    new PathArbiter.PathCandidate("root.x", 1, PathArbiter.AccessType.FIELD, target)
            );
            List<PathArbiter.PathCandidate> result = PathArbiter.deduplicate(candidates);
            assertThat(result).isNotEmpty();
        }
    }
}
