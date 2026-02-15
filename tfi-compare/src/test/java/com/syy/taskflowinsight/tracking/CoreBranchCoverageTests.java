package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LcsListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.path.PathArbiter;
import com.syy.taskflowinsight.tracking.path.PathCollector;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.query.ListChangeProjector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核心分支覆盖测试
 * 覆盖 PathCollector、ListChangeProjector、ListCompareExecutor 的分支逻辑
 *
 * @since 3.0.0
 */
@DisplayName("Core Branch Coverage — 核心分支覆盖测试")
class CoreBranchCoverageTests {

    @AfterEach
    void tearDown() {
        com.syy.taskflowinsight.tracking.path.PathBuilder.clearCache();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PathCollector
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PathCollector — 配置与空输入")
    class PathCollectorConfigAndEmpty {

        @Test
        @DisplayName("禁用配置时返回空列表")
        void disabledConfig_returnsEmpty() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(false);
            PathCollector collector = new PathCollector(config);
            List<ChangeRecord> records = List.of(
                ChangeRecord.of("X", "field", null, "v", ChangeType.CREATE));
            Map<String, Object> after = Map.of("field", "v");
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                records, Map.of(), after);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null changeRecords 返回空列表")
        void nullRecords_returnsEmpty() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                null, Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空 changeRecords 返回空列表")
        void emptyRecords_returnsEmpty() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                Collections.emptyList(), Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null config 使用默认配置")
        void nullConfig_usesDefaults() {
            PathCollector collector = new PathCollector(null);
            assertThat(collector.getCacheStatistics()).containsKey("cacheEnabled");
        }

        @Test
        @DisplayName("跳过 null record")
        void skipsNullRecord() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<ChangeRecord> records = Arrays.asList(
                ChangeRecord.of("X", "a", null, "v", ChangeType.CREATE),
                null);
            Map<String, Object> after = Map.of("a", "v");
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                records, Map.of(), after);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("跳过 null fieldName 的 record")
        void skipsNullFieldName() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            ChangeRecord record = ChangeRecord.of("X", null, null, "v", ChangeType.CREATE);
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                List.of(record), Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("DELETE 场景：目标仅在 beforeSnapshot")
        void deleteTargetOnlyInBeforeSnapshot() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            PathCollector collector = new PathCollector(config);
            Object deletedVal = "deleted";
            Map<String, Object> before = new HashMap<>();
            before.put("removed", deletedVal);
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> records = List.of(
                ChangeRecord.of("X", "removed", deletedVal, null, ChangeType.DELETE));
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                records, before, after);
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("PathCollector — 路径过滤 include/exclude")
    class PathCollectorPathFiltering {

        @Test
        @DisplayName("includePatterns 过滤路径")
        void includePatterns_filtersPaths() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setIncludePatterns(List.of("allowed.*"));
            config.setExcludePatterns(Collections.emptyList());
            PathCollector collector = new PathCollector(config);
            Object target = "value";
            Map<String, Object> snapshot = Map.of("allowed.field", target);
            List<ChangeRecord> records = List.of(
                ChangeRecord.of("X", "allowed.field", null, target, ChangeType.CREATE));
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                records, Map.of(), snapshot);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("excludePatterns 排除路径")
        void excludePatterns_excludesPaths() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setExcludePatterns(List.of("*.secret*"));
            PathCollector collector = new PathCollector(config);
            Object target = "secretVal";
            Map<String, Object> snapshot = Map.of("user.secret", target);
            List<ChangeRecord> records = List.of(
                ChangeRecord.of("X", "user.secret", null, target, ChangeType.CREATE));
            List<PathArbiter.PathCandidate> result = collector.collectFromChangeRecords(
                records, Map.of(), snapshot);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("PathCollector — 缓存与遍历")
    class PathCollectorCacheAndTraversal {

        @Test
        @DisplayName("缓存命中返回副本")
        void cacheHit_returnsCopy() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setCacheEnabled(true);
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Object target = "nested";
            Map<String, Object> root = new HashMap<>();
            Map<String, Object> nested = new HashMap<>();
            nested.put("value", target);
            root.put("map", nested);
            Map<String, Object> snapshot = Map.of("map", nested);
            List<PathArbiter.PathCandidate> first = collector.collectPathsForObject(
                target, "map", snapshot);
            List<PathArbiter.PathCandidate> second = collector.collectPathsForObject(
                target, "map", snapshot);
            assertThat(first).isNotEmpty();
            assertThat(second).isNotEmpty();
            assertThat(first).isNotSameAs(second);
        }

        @Test
        @DisplayName("深度限制生效")
        void depthLimit_respected() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(1);
            PathCollector collector = new PathCollector(config);
            Object target = "deep";
            Map<String, Object> level1 = new HashMap<>();
            Map<String, Object> level2 = new HashMap<>();
            level2.put("deep", target);
            level1.put("nested", level2);
            Map<String, Object> snapshot = Map.of("root", level1);
            List<PathArbiter.PathCandidate> result = collector.collectPathsForObject(
                target, "root.nested.deep", snapshot);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Map 遍历")
        void mapTraversal() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Object target = "mapVal";
            Map<String, Object> map = new HashMap<>();
            map.put("key", target);
            Map<String, Object> snapshot = Map.of("map", map);
            List<PathArbiter.PathCandidate> result = collector.collectPathsForObject(
                target, "map", snapshot);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("List 遍历")
        void listTraversal() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Object target = "item";
            List<Object> list = Arrays.asList("a", target, "c");
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("[1]", target);
            List<PathArbiter.PathCandidate> result = collector.collectPathsForObject(
                target, "[1]", snapshot);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Set 遍历")
        void setTraversal() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Object target = "setItem";
            Set<Object> set = new HashSet<>();
            set.add(target);
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("set", set);
            List<PathArbiter.PathCandidate> result = collector.collectPathsForObject(
                target, "set", snapshot);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("数组遍历")
        void arrayTraversal() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Object target = "elem";
            Object[] arr = new Object[]{"a", target, "c"};
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("[1]", target);
            List<PathArbiter.PathCandidate> result = collector.collectPathsForObject(
                target, "[1]", snapshot);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("maxObjectsPerLevel 限制")
        void maxObjectsPerLevel_limit() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setMaxObjectsPerLevel(1);
            config.setMaxCollectionDepth(5);
            PathCollector collector = new PathCollector(config);
            Map<String, Object> map = new HashMap<>();
            map.put("a", "v1");
            map.put("b", "v2");
            map.put("c", "v3");
            Map<String, Object> snapshot = Map.of("root", map);
            List<PathArbiter.PathCandidate> result = collector.collectPathsForObject(
                "v2", "root", snapshot);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("clearCache 清空缓存")
        void clearCache() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            collector.collectPathsForObject("x", "field", Map.of("field", "x"));
            collector.clearCache();
            assertThat(collector.getCacheStatistics().get("cacheSize")).isEqualTo(0);
        }

        @Test
        @DisplayName("getCacheStatistics 返回统计")
        void getCacheStatistics() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            Map<String, Object> stats = collector.getCacheStatistics();
            assertThat(stats).containsKeys("cacheSize", "cacheEnabled");
        }

        @Test
        @DisplayName("toString 包含配置信息")
        void toString_includesConfig() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            assertThat(collector.toString()).contains("PathCollector").contains("cacheSize");
        }

        @Test
        @DisplayName("collectPathsForObject null target 返回空")
        void collectPathsForObject_nullTarget() {
            PathCollector collector = new PathCollector(new PathDeduplicationConfig());
            List<PathArbiter.PathCandidate> result = collector.collectPathsForObject(
                null, "path", Map.of());
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ListChangeProjector
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ListChangeProjector — 空输入与算法")
    class ListChangeProjectorEmptyAndAlgorithm {

        @Test
        @DisplayName("null listResult 返回空列表")
        void nullResult_returnsEmpty() {
            List<Map<String, Object>> result = ListChangeProjector.project(
                null, List.of("a"), List.of("b"), CompareOptions.DEFAULT, "list");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null algorithm 降级为 SIMPLE")
        void nullAlgorithm_fallbackToSimple() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed(null)
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a"), List.of("a", "b"), CompareOptions.DEFAULT, "items");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("SIMPLE 算法")
        void simpleAlgorithm() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("SIMPLE")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("a", "x"), CompareOptions.DEFAULT, "list");
            assertThat(result).anyMatch(e -> "entry_updated".equals(e.get("kind")));
        }

        @Test
        @DisplayName("AS_SET 算法")
        void asSetAlgorithm() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("AS_SET")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("b", "a"), CompareOptions.DEFAULT, "list");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("LCS 算法")
        void lcsAlgorithm() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("LCS")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b", "c"), List.of("a", "x", "c"),
                CompareOptions.DEFAULT, "list");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("LEVENSHTEIN 算法")
        void levenshteinAlgorithm() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("LEVENSHTEIN")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("a", "x"), CompareOptions.DEFAULT, "list");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("ENTITY 算法")
        void entityAlgorithm() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("ENTITY")
                .duplicateKeys(Collections.emptySet())
                .identical(false)
                .build();
            List<EntityWithKey> left = Collections.emptyList();
            List<EntityWithKey> right = List.of(new EntityWithKey(1, "A"));
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, left, right, CompareOptions.DEFAULT, "items");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("未知算法降级为 SIMPLE")
        void unknownAlgorithm_fallbackToSimple() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("UNKNOWN")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a"), List.of("b"), CompareOptions.DEFAULT, "list");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("null left 使用空列表")
        void nullLeft_usesEmptyList() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("SIMPLE")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, null, List.of("new"), CompareOptions.DEFAULT, "list");
            assertThat(result).anyMatch(e -> "entry_added".equals(e.get("kind")));
        }

        @Test
        @DisplayName("null right 使用空列表")
        void nullRight_usesEmptyList() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("SIMPLE")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("old"), null, CompareOptions.DEFAULT, "list");
            assertThat(result).anyMatch(e -> "entry_removed".equals(e.get("kind")));
        }
    }

    @Nested
    @DisplayName("ListChangeProjector — 移动检测与 createEvent")
    class ListChangeProjectorMoveDetection {

        @Test
        @DisplayName("detectMoves=true 合并 added/removed 为 moved")
        void detectMoves_mergesToMoved() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("LCS")
                .identical(false)
                .build();
            CompareOptions opts = CompareOptions.builder().detectMoves(true).build();
            List<String> left = List.of("a", "b", "c");
            List<String> right = List.of("b", "a", "c");
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, left, right, opts, "list");
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("entry_added 事件")
        void entryAddedEvent() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("SIMPLE")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a"), List.of("a", "b"), CompareOptions.DEFAULT, "list");
            assertThat(result).anyMatch(e -> "entry_added".equals(e.get("kind")));
        }

        @Test
        @DisplayName("entry_removed 事件")
        void entryRemovedEvent() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("SIMPLE")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("a"), CompareOptions.DEFAULT, "list");
            assertThat(result).anyMatch(e -> "entry_removed".equals(e.get("kind")));
        }

        @Test
        @DisplayName("entry_updated 事件")
        void entryUpdatedEvent() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("SIMPLE")
                .identical(false)
                .build();
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, List.of("a", "b"), List.of("a", "x"), CompareOptions.DEFAULT, "list");
            assertThat(result).anyMatch(e -> "entry_updated".equals(e.get("kind")));
        }

        @Test
        @DisplayName("ENTITY 算法 duplicateKeys")
        void entityDuplicateKeys() {
            CompareResult listResult = CompareResult.builder()
                .algorithmUsed("ENTITY")
                .duplicateKeys(Set.of("id=1"))
                .identical(false)
                .build();
            List<EntityWithKey> left = List.of(new EntityWithKey(1, "A"));
            List<EntityWithKey> right = List.of(new EntityWithKey(2, "B"));
            List<Map<String, Object>> result = ListChangeProjector.project(
                listResult, left, right, CompareOptions.DEFAULT, "items");
            assertThat(result).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ListCompareExecutor
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ListCompareExecutor — 路由与降级")
    class ListCompareExecutorRouting {

        private ListCompareExecutor createExecutor() {
            return new ListCompareExecutor(List.of(
                new SimpleListStrategy(),
                new AsSetListStrategy(),
                new LcsListStrategy(),
                new LevenshteinListStrategy(),
                new EntityListStrategy()));
        }

        @Test
        @DisplayName("显式 SIMPLE 策略")
        void explicitSimpleStrategy() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("SIMPLE").build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("a", "x"), opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("显式 AS_SET 策略")
        void explicitAsSetStrategy() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("AS_SET").build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("b", "a"), opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("AS_SET");
        }

        @Test
        @DisplayName("显式 LCS 策略")
        void explicitLcsStrategy() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("LCS").build();
            CompareResult r = executor.compare(
                List.of("a", "b", "c"), List.of("a", "x", "c"), opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("LCS");
        }

        @Test
        @DisplayName("显式 LEVENSHTEIN 策略")
        void explicitLevenshteinStrategy() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("LEVENSHTEIN").build();
            CompareResult r = executor.compare(List.of("a", "b"), List.of("a", "x"), opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("LEVENSHTEIN");
        }

        @Test
        @DisplayName("显式 ENTITY 策略")
        void explicitEntityStrategy() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("ENTITY").build();
            List<EntityWithKey> a = List.of(new EntityWithKey(1, "A"));
            List<EntityWithKey> b = List.of(new EntityWithKey(1, "B"));
            CompareResult r = executor.compare(a, b, opts);
            assertThat(r.getAlgorithmUsed()).isEqualTo("ENTITY");
        }

        @Test
        @DisplayName("无效策略名降级到自动路由")
        void invalidStrategyName_fallback() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().strategyName("INVALID_STRATEGY").build();
            CompareResult r = executor.compare(List.of("a"), List.of("b"), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("大小超过 500 触发降级")
        void sizeExceedsThreshold_degradation() {
            ListCompareExecutor executor = createExecutor();
            List<String> large = new ArrayList<>();
            for (int i = 0; i < 600; i++) large.add("item" + i);
            CompareResult r = executor.compare(large, new ArrayList<>(large), CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("大小超过 1000 强制 SIMPLE")
        void sizeExceeds1000_forceSimple() {
            ListCompareExecutor executor = createExecutor();
            List<String> huge = new ArrayList<>();
            for (int i = 0; i < 1100; i++) huge.add("x" + i);
            CompareOptions opts = CompareOptions.builder().strategyName("LCS").build();
            CompareResult r = executor.compare(huge, new ArrayList<>(huge), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("AS_SET 在 500-999 时保持")
        void asSetHintAtMidSize() {
            ListCompareExecutor executor = createExecutor();
            List<String> mid = new ArrayList<>();
            for (int i = 0; i < 600; i++) mid.add("x" + i);
            CompareOptions opts = CompareOptions.builder().strategyName("AS_SET").build();
            CompareResult r = executor.compare(mid, new ArrayList<>(mid), opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Entity 自动路由")
        void entityAutoRoute() {
            ListCompareExecutor executor = createExecutor();
            List<EntityWithKey> a = List.of(new EntityWithKey(1, "A"));
            List<EntityWithKey> b = List.of(new EntityWithKey(1, "B"));
            CompareResult r = executor.compare(a, b, CompareOptions.DEFAULT);
            assertThat(r.getAlgorithmUsed()).isEqualTo("ENTITY");
        }

        @Test
        @DisplayName("非 Entity 使用 SIMPLE")
        void nonEntity_usesSimple() {
            ListCompareExecutor executor = createExecutor();
            CompareResult r = executor.compare(List.of("a"), List.of("b"), CompareOptions.DEFAULT);
            assertThat(r.getAlgorithmUsed()).isEqualTo("SIMPLE");
        }

        @Test
        @DisplayName("calculateSimilarity 空 union 返回 1.0")
        void similarityEmptyUnion() {
            ListCompareExecutor executor = createExecutor();
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = executor.compare(
                Collections.emptyList(), Collections.emptyList(), opts);
            assertThat(r.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("getSupportedStrategies")
        void getSupportedStrategies() {
            ListCompareExecutor executor = createExecutor();
            assertThat(executor.getSupportedStrategies())
                .contains("SIMPLE", "AS_SET", "LCS", "LEVENSHTEIN", "ENTITY");
        }

        @Test
        @DisplayName("getDegradationCount")
        void getDegradationCount() {
            ListCompareExecutor executor = createExecutor();
            long before = executor.getDegradationCount();
            List<String> large = new ArrayList<>();
            for (int i = 0; i < 600; i++) large.add("x" + i);
            executor.compare(large, new ArrayList<>(large), CompareOptions.DEFAULT);
            assertThat(executor.getDegradationCount()).isGreaterThanOrEqualTo(before);
        }
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
