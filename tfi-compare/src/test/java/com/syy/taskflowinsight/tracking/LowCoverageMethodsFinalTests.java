package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.exporter.change.ChangeJsonExporter;
import com.syy.taskflowinsight.exporter.change.ChangeMapExporter;
import com.syy.taskflowinsight.exporter.change.ChangeExporter;
import com.syy.taskflowinsight.spi.DefaultComparisonProvider;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
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
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.path.PathCache;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.path.PathDeduplicationConfig;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 低覆盖率方法最终测试
 * 针对 40–60% 覆盖率的 10 个目标方法，最大化指令覆盖
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("LowCoverageMethodsFinal — 低覆盖率方法最终测试")
class LowCoverageMethodsFinalTests {

    @AfterEach
    void tearDown() {
        DiffDetector.setEnhancedDeduplicationEnabled(true);
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. ListCompareExecutor.compare
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("1. ListCompareExecutor.compare — 多策略与降级路径")
    class ListCompareExecutorTests {

        private ListCompareExecutor createExecutor() {
            List<ListCompareStrategy> strategies = List.of(
                new SimpleListStrategy(),
                new AsSetListStrategy(),
                new LcsListStrategy(),
                new LevenshteinListStrategy(),
                new EntityListStrategy()
            );
            return new ListCompareExecutor(strategies);
        }

        @Test
        @DisplayName("LEVENSHTEIN 策略 — 小列表")
        void compare_levenshtein_smallLists() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "x", "c");
            CompareOptions opts = CompareOptions.builder()
                .strategyName("LEVENSHTEIN")
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("ENTITY 策略 — @Entity 对象列表")
        void compare_entity_strategy_withEntityObjects() {
            ListCompareExecutor executor = createExecutor();
            List<TestEntity> before = List.of(new TestEntity(1, "A"), new TestEntity(2, "B"));
            List<TestEntity> after = List.of(new TestEntity(1, "A"), new TestEntity(2, "X"));
            CompareOptions opts = CompareOptions.builder()
                .strategyName("ENTITY")
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(result.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("LCS 策略 — detectMoves=true")
        void compare_lcs_withDetectMoves() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("c", "a", "b");
            CompareOptions opts = CompareOptions.builder()
                .strategyName("LCS")
                .detectMoves(true)
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("大列表降级 — 1000+ 元素")
        void compare_largeList_degradation() {
            ListCompareExecutor executor = createExecutor();
            List<Integer> before = new ArrayList<>();
            List<Integer> after = new ArrayList<>();
            for (int i = 0; i < 1100; i++) {
                before.add(i);
                after.add(i == 500 ? 9999 : i);
            }
            CompareOptions opts = CompareOptions.builder()
                .strategyName("LEVENSHTEIN")
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(executor.getDegradationCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("大列表 ENTITY 策略 — 触发 size 降级")
        void compare_entity_largeList_degradation() {
            ListCompareExecutor executor = createExecutor();
            List<Integer> before = new ArrayList<>(Collections.nCopies(600, 1));
            List<Integer> after = new ArrayList<>(Collections.nCopies(600, 2));
            CompareOptions opts = CompareOptions.builder()
                .strategyName("ENTITY")
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("相似度计算 — calculateSimilarity=true")
        void compare_calculateSimilarity() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = List.of("a", "b", "c");
            List<String> after = List.of("a", "b", "c");
            CompareOptions opts = CompareOptions.builder()
                .calculateSimilarity(true)
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("相似度计算 — 空并集")
        void compare_similarity_emptyUnion() {
            ListCompareExecutor executor = createExecutor();
            List<String> before = Collections.emptyList();
            List<String> after = Collections.emptyList();
            CompareOptions opts = CompareOptions.builder()
                .calculateSimilarity(true)
                .build();

            CompareResult result = executor.compare(before, after, opts);

            assertThat(result).isNotNull();
            assertThat(result.getSimilarity()).isEqualTo(1.0);
        }

        @Entity
        static class TestEntity {
            @Key
            final int id;
            final String name;

            TestEntity(int id, String name) {
                this.id = id;
                this.name = name;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. DiffDetector.diffWithMode (ENHANCED)
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("2. DiffDetector.diffWithMode — ENHANCED 模式")
    class DiffDetectorEnhancedTests {

        @Test
        @DisplayName("ENHANCED 模式 — Date 值 reprOld/reprNew")
        void diffWithMode_enhanced_dateValues() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            Date oldDate = new Date(1000000L);
            Date newDate = new Date(2000000L);
            before.put("createdAt", oldDate);
            after.put("createdAt", newDate);

            List<ChangeRecord> changes = DiffDetector.diffWithMode("User", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
            assertThat(changes.get(0).getReprOld()).isNotNull();
            assertThat(changes.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("ENHANCED 模式 — BigDecimal 值")
        void diffWithMode_enhanced_bigDecimal() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("amount", new BigDecimal("10.00"));
            after.put("amount", new BigDecimal("20.50"));

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Order", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("ENHANCED 模式 — Collection 值")
        void diffWithMode_enhanced_collection() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("tags", List.of("a", "b"));
            after.put("tags", List.of("a", "b", "c"));

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Item", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("ENHANCED 模式 — Enum 值")
        void diffWithMode_enhanced_enum() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("status", TestStatus.ACTIVE);
            after.put("status", TestStatus.INACTIVE);

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Entity", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("ENHANCED 模式 — null 值")
        void diffWithMode_enhanced_nullValues() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("optional", null);
            after.put("optional", "value");

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);

            assertThat(changes).isNotEmpty();
        }

        enum TestStatus { ACTIVE, INACTIVE }
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. DiffDetector.deduplicateByPath (基础去重)
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("3. DiffDetector.deduplicateByPath — 嵌套路径去重")
    class DiffDetectorDeduplicateTests {

        @Test
        @DisplayName("增强去重关闭 — 嵌套路径 order.items[0].name")
        void deduplicateByPath_nestedPaths() {
            DiffDetector.setEnhancedDeduplicationEnabled(false);

            Map<String, Object> before = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            before.put("order", "old");
            before.put("order.items", "oldItems");
            before.put("order.items[0]", "oldItem");
            before.put("order.items[0].name", "oldName");
            after.put("order", "new");
            after.put("order.items", "newItems");
            after.put("order.items[0]", "newItem");
            after.put("order.items[0].name", "newName");

            List<ChangeRecord> changes = DiffDetector.diffWithMode("Root", before, after, DiffDetector.DiffMode.COMPAT);

            assertThat(changes).isNotEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. PathDeduplicator.deduplicateWithObjectGraph
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("4. PathDeduplicator.deduplicateWithObjectGraph")
    class PathDeduplicatorObjectGraphTests {

        @Test
        @DisplayName("对象图去重 — 嵌套路径与快照")
        void deduplicateWithObjectGraph_nestedPaths() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(true);
            config.setFastPathChangeLimit(1000);
            PathDeduplicator deduplicator = new PathDeduplicator(config);

            Object sharedObj = "shared";
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("order.items[0].name", "old");
            after.put("order.items[0].name", "new");
            before.put("order.items[0]", sharedObj);
            after.put("order.items[0]", sharedObj);

            List<ChangeRecord> records = List.of(
                ChangeRecord.builder()
                    .objectName("Order")
                    .fieldName("order.items[0].name")
                    .oldValue("old")
                    .newValue("new")
                    .changeType(ChangeType.UPDATE)
                    .build(),
                ChangeRecord.builder()
                    .objectName("Order")
                    .fieldName("order.items[0]")
                    .oldValue(sharedObj)
                    .newValue(sharedObj)
                    .changeType(ChangeType.UPDATE)
                    .build()
            );

            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(records, before, after);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("去重 — 禁用时原样返回")
        void deduplicateWithObjectGraph_disabled() {
            PathDeduplicationConfig config = new PathDeduplicationConfig();
            config.setEnabled(false);
            PathDeduplicator deduplicator = new PathDeduplicator(config);

            List<ChangeRecord> records = List.of(
                ChangeRecord.of("O", "f1", null, "v", ChangeType.CREATE)
            );

            List<ChangeRecord> result = deduplicator.deduplicateWithObjectGraph(
                records, Map.of(), Map.of());

            assertThat(result).isEqualTo(records);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. MarkdownRenderer.renderChangesKeyPrefixed
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("5. MarkdownRenderer.renderChangesKeyPrefixed — KEY_PREFIXED 模式")
    class MarkdownRendererKeyPrefixedTests {

        @Test
        @DisplayName("KEY_PREFIXED 模式 — 实体变更渲染")
        void renderChangesKeyPrefixed() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            RenderStyle style = RenderStyle.keyPrefixed();

            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("entity[1001]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldPath("entity[1001].name")
                    .fieldName("name")
                    .oldValue("Alice")
                    .newValue("Bob")
                    .changeType(ChangeType.UPDATE)
                    .build())
                .addChange(FieldChange.builder()
                    .fieldPath("entity[1001].age")
                    .fieldName("age")
                    .oldValue(25)
                    .newValue(26)
                    .changeType(ChangeType.UPDATE)
                    .build())
                .build();

            EntityListDiffResult result = EntityListDiffResult.builder()
                .addGroup(group)
                .build();

            String output = renderer.render(result, style);

            assertThat(output).contains("[entity[1001]]");
            assertThat(output).contains("name");
            assertThat(output).contains("Alice");
            assertThat(output).contains("Bob");
        }

        @Test
        @DisplayName("KEY_PREFIXED — 表格格式 BORDERED")
        void renderChangesKeyPrefixed_borderedTable() {
            MarkdownRenderer renderer = new MarkdownRenderer();
            RenderStyle style = RenderStyle.builder()
                .entityKeyMode(RenderStyle.EntityKeyMode.KEY_PREFIXED)
                .tableFormat(RenderStyle.TableFormat.BORDERED)
                .build();

            EntityChangeGroup group = EntityChangeGroup.builder()
                .entityKey("order[O1]")
                .operation(EntityOperation.MODIFY)
                .addChange(FieldChange.builder()
                    .fieldPath("order[O1].amount")
                    .fieldName("amount")
                    .oldValue(100)
                    .newValue(200)
                    .changeType(ChangeType.UPDATE)
                    .build())
                .build();

            EntityListDiffResult result = EntityListDiffResult.builder()
                .addGroup(group)
                .build();

            String output = renderer.render(result, style);

            assertThat(output).contains("|");
            assertThat(output).contains("amount");
            assertThat(output).contains("100");
            assertThat(output).contains("200");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 6. PathCache.evictAccordingToPolicy
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("6. PathCache.evictAccordingToPolicy — LRU/FIFO")
    class PathCacheEvictionTests {

        @Test
        @DisplayName("LRU 策略 — 超容量触发驱逐")
        void evictAccordingToPolicy_lru() {
            PathCache cache = new PathCache(true, 5, "LRU");

            for (int i = 0; i < 15; i++) {
                cache.put(new Object(), "path" + i);
            }

            PathCache.CacheStatistics stats = cache.getStatistics();
            assertThat(stats.getEvictions()).isGreaterThan(0);
        }

        @Test
        @DisplayName("FIFO 策略 — 超容量触发驱逐")
        void evictAccordingToPolicy_fifo() {
            PathCache cache = new PathCache(true, 5, "FIFO");

            for (int i = 0; i < 15; i++) {
                cache.put(new Object(), "path" + i);
            }

            PathCache.CacheStatistics stats = cache.getStatistics();
            assertThat(stats.getEvictions()).isGreaterThan(0);
        }

        @Test
        @DisplayName("SIZE_BASED 策略 — 超容量触发驱逐")
        void evictAccordingToPolicy_sizeBased() {
            PathCache cache = new PathCache(true, 5, "SIZE_BASED");

            for (int i = 0; i < 15; i++) {
                cache.put(new Object(), "path" + i);
            }

            PathCache.CacheStatistics stats = cache.getStatistics();
            assertThat(stats.getEvictions()).isGreaterThan(0);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 7. TfiDateTimeFormatter.formatDuration
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("7. TfiDateTimeFormatter.formatDuration")
    class TfiDateTimeFormatterDurationTests {

        @Test
        @DisplayName("formatDuration — 仅天")
        void formatDuration_daysOnly() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofDays(3);
            assertThat(fmt.formatDuration(d)).isEqualTo("P3D");
        }

        @Test
        @DisplayName("formatDuration — 仅小时")
        void formatDuration_hoursOnly() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofHours(2);
            assertThat(fmt.formatDuration(d)).contains("2H");
        }

        @Test
        @DisplayName("formatDuration — 仅小时分钟")
        void formatDuration_hoursAndMinutes() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofHours(1).plusMinutes(30);
            assertThat(fmt.formatDuration(d)).contains("1H").contains("30M");
        }

        @Test
        @DisplayName("formatDuration — 秒与毫秒")
        void formatDuration_secondsAndMillis() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofSeconds(5).plusMillis(123);
            assertThat(fmt.formatDuration(d)).contains("5.123S");
        }

        @Test
        @DisplayName("formatDuration — 仅秒")
        void formatDuration_secondsOnly() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ofSeconds(10);
            assertThat(fmt.formatDuration(d)).contains("10S");
        }

        @Test
        @DisplayName("formatDuration — 零时长")
        void formatDuration_zero() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            Duration d = Duration.ZERO;
            assertThat(fmt.formatDuration(d)).isEqualTo("PT0S");
        }

        @Test
        @DisplayName("formatDuration — null")
        void formatDuration_null() {
            TfiDateTimeFormatter fmt = new TfiDateTimeFormatter();
            assertThat(fmt.formatDuration(null)).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 8. ChangeJsonExporter.writeChangeRecord
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("8. ChangeJsonExporter.writeChangeRecord")
    class ChangeJsonExporterTests {

        @Test
        @DisplayName("ENHANCED 模式 — 完整 ChangeRecord")
        void writeChangeRecord_enhanced() {
            ChangeJsonExporter exporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.ENHANCED);
            List<ChangeRecord> changes = List.of(
                ChangeRecord.builder()
                    .objectName("User")
                    .fieldName("name")
                    .oldValue("Alice")
                    .newValue("Bob")
                    .changeType(ChangeType.UPDATE)
                    .reprOld("Alice")
                    .reprNew("Bob")
                    .valueKind("STRING")
                    .valueType("java.lang.String")
                    .sessionId("s1")
                    .taskPath("t1")
                    .build()
            );

            String json = exporter.format(changes);

            assertThat(json).contains("reprOld");
            assertThat(json).contains("reprNew");
            assertThat(json).contains("valueKind");
            assertThat(json).contains("sessionId");
        }

        @Test
        @DisplayName("COMPAT 模式 — CREATE/DELETE")
        void writeChangeRecord_compat() {
            ChangeJsonExporter exporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.COMPAT);
            List<ChangeRecord> changes = List.of(
                ChangeRecord.builder()
                    .objectName("O")
                    .fieldName("f")
                    .oldValue(null)
                    .newValue("v")
                    .changeType(ChangeType.CREATE)
                    .build(),
                ChangeRecord.builder()
                    .objectName("O")
                    .fieldName("f")
                    .oldValue("v")
                    .newValue(null)
                    .changeType(ChangeType.DELETE)
                    .build()
            );

            String json = exporter.format(changes);

            assertThat(json).contains("changes");
            assertThat(json).contains("CREATE");
            assertThat(json).contains("DELETE");
        }

        @Test
        @DisplayName("带 ExportConfig — showTimestamp")
        void writeChangeRecord_withTimestamp() {
            ChangeJsonExporter exporter = new ChangeJsonExporter(ChangeJsonExporter.ExportMode.ENHANCED);
            ChangeExporter.ExportConfig config = new ChangeExporter.ExportConfig();
            config.setShowTimestamp(true);
            List<ChangeRecord> changes = List.of(
                ChangeRecord.of("O", "f", null, "v", ChangeType.CREATE)
            );

            String json = exporter.format(changes, config);

            assertThat(json).contains("timestamp");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 9. ChangeMapExporter.exportSingleChange
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("9. ChangeMapExporter.exportSingleChange")
    class ChangeMapExporterTests {

        @Test
        @DisplayName("所有 ChangeType — CREATE/UPDATE/DELETE")
        void exportSingleChange_allChangeTypes() {
            List<ChangeRecord> changes = List.of(
                ChangeRecord.builder()
                    .objectName("O")
                    .fieldName("f")
                    .oldValue(null)
                    .newValue("v")
                    .changeType(ChangeType.CREATE)
                    .valueRepr("v")
                    .valueKind("STRING")
                    .valueType("java.lang.String")
                    .sessionId("s1")
                    .taskPath("t1")
                    .build(),
                ChangeRecord.builder()
                    .objectName("O")
                    .fieldName("f")
                    .oldValue("a")
                    .newValue("b")
                    .changeType(ChangeType.UPDATE)
                    .reprOld("a")
                    .reprNew("b")
                    .build(),
                ChangeRecord.builder()
                    .objectName("O")
                    .fieldName("f")
                    .oldValue("v")
                    .newValue(null)
                    .changeType(ChangeType.DELETE)
                    .build()
            );

            Map<String, Object> result = ChangeMapExporter.export(changes);

            assertThat(result).containsKey("changes");
            List<?> changeList = (List<?>) result.get("changes");
            assertThat(changeList).hasSize(3);
        }

        @Test
        @DisplayName("MOVE 类型 — exportGroupedByObject 覆盖")
        void exportSingleChange_moveType() {
            List<ChangeRecord> changes = List.of(
                ChangeRecord.builder()
                    .objectName("O")
                    .fieldName("f")
                    .oldValue("v")
                    .newValue("v")
                    .changeType(ChangeType.MOVE)
                    .build()
            );
            Map<String, List<Map<String, Object>>> grouped = ChangeMapExporter.exportGroupedByObject(changes);
            assertThat(grouped).containsKey("O");
            assertThat(grouped.get("O").get(0).get("type")).isEqualTo("MOVE");
        }

        @Test
        @DisplayName("exportGroupedByObject")
        void exportGroupedByObject() {
            List<ChangeRecord> changes = List.of(
                ChangeRecord.of("User", "name", "a", "b", ChangeType.UPDATE),
                ChangeRecord.of("User", "age", 25, 26, ChangeType.UPDATE),
                ChangeRecord.of("Order", "amount", 100, 200, ChangeType.UPDATE)
            );

            Map<String, List<Map<String, Object>>> grouped = ChangeMapExporter.exportGroupedByObject(changes);

            assertThat(grouped).containsKeys("User", "Order");
            assertThat(grouped.get("User")).hasSize(2);
            assertThat(grouped.get("Order")).hasSize(1);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 10. DefaultComparisonProvider.compare
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("10. DefaultComparisonProvider.compare")
    class DefaultComparisonProviderTests {

        @Test
        @DisplayName("compare(Object, Object) — 不同 Map 内容")
        void compare_twoArgs() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            Map<String, Object> before = Map.of("k", "a");
            Map<String, Object> after = Map.of("k", "b");
            CompareResult result = provider.compare(before, after, CompareOptions.DEFAULT);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("compare — null 参数")
        void compare_nullParams() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            CompareResult result = provider.compare(null, "x");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("compare — 相同对象")
        void compare_sameObject() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            Object o = "same";
            CompareResult result = provider.compare(o, o);

            assertThat(result).isNotNull();
            assertThat(result.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("compare — options 为 null")
        void compare_nullOptions() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            CompareResult result = provider.compare("a", "b", null);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("similarity — 相同")
        void similarity_identical() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            double sim = provider.similarity("a", "a");
            assertThat(sim).isEqualTo(1.0);
        }

        @Test
        @DisplayName("similarity — 不同 Map")
        void similarity_different() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            Map<String, Object> a = Map.of("x", 1);
            Map<String, Object> b = Map.of("x", 2);
            double sim = provider.similarity(a, b);
            assertThat(sim).isLessThan(1.0);
        }

        @Test
        @DisplayName("priority")
        void priority() {
            DefaultComparisonProvider provider = new DefaultComparisonProvider();
            assertThat(provider.priority()).isEqualTo(0);
        }
    }
}
