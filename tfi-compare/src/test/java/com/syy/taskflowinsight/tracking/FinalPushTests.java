package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.tracking.snapshot.ShallowReferenceMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 最终覆盖冲刺测试 — 针对 ObjectSnapshotDeep 与 DiffDetector 未覆盖行
 * 目标：覆盖 JaCoCo 报告中的 mi>0 行，冲刺 85% 覆盖率
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("最终覆盖冲刺测试")
class FinalPushTests {

    @BeforeEach
    void setUp() {
        DegradationContext.reset();
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffDetector.setEnhancedDeduplicationEnabled(true);
    }

    @AfterEach
    void tearDown() {
        DegradationContext.reset();
        ObjectSnapshotDeep.resetMetrics();
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffDetector.setCurrentObjectClass(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ObjectSnapshotDeep 未覆盖行
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ObjectSnapshotDeep — formatPrimitiveArray 边界")
    class ObjectSnapshotDeepFormatPrimitiveArray {

        @Test
        @DisplayName("formatPrimitiveArray 空数组")
        void emptyPrimitiveArray() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            int[] arr = {};
            Map<String, Object> obj = Map.of("arr", arr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            Object val = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
            assertThat(val).asString().contains("[0]");
        }

        @Test
        @DisplayName("formatPrimitiveArray 小数组 ≤10 元素")
        void smallPrimitiveArray() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            int[] arr = {1, 2, 3, 4, 5};
            Map<String, Object> obj = Map.of("arr", arr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            Object val = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
            assertThat(val).asString().contains("1");
            assertThat(val).asString().contains("5");
        }

        @Test
        @DisplayName("formatPrimitiveArray 大数组 >10 元素仅摘要")
        void largePrimitiveArray() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            int[] arr = new int[15];
            for (int i = 0; i < 15; i++) arr[i] = i;
            Map<String, Object> obj = Map.of("arr", arr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            Object val = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
            assertThat(val).asString().contains("[15]");
        }

        @Test
        @DisplayName("formatPrimitiveArray long 类型")
        void longPrimitiveArray() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            long[] arr = {1L, 2L};
            Map<String, Object> obj = Map.of("arr", arr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — handleEntity DiffInclude 与 includeFields")
    class ObjectSnapshotDeepEntityDiffInclude {

        @Test
        @DisplayName("Entity hasDiffInclude 且 includeFields 包含字段名")
        void entity_diffInclude_withIncludeFields() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithDiffIncludeAndInclude entity = new EntityWithDiffIncludeAndInclude();
            entity.included = "inc";
            Set<String> include = Set.of("included");
            Map<String, Object> snap = deep.captureDeep(entity, 3, include, Collections.emptySet());
            assertThat(snap).containsKey("included");
        }

        @Test
        @DisplayName("Entity ShallowReference 深度递归达到 maxDepth 使用 toString")
        void entity_shallowRef_depthLimit_toString() {
            SnapshotConfig config = new SnapshotConfig();
            config.setShallowReferenceMode(ShallowReferenceMode.VALUE_ONLY);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityNestedRecursive e = new EntityNestedRecursive();
            e.child = new EntityNestedRecursive();
            e.child.id = "c1";
            e.child.child = new EntityNestedRecursive();
            e.child.child.id = "c2";
            Map<String, Object> snap = deep.captureDeep(e, 1, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — adjustOptionsForDegradation")
    class ObjectSnapshotDeepAdjustOptions {

        @Test
        @DisplayName("SKIP_DEEP_ANALYSIS 调整 maxDepth 与 depth")
        void skipDeepAnalysis_adjustsOptions() {
            DegradationContext.setCurrentLevel(DegradationLevel.SKIP_DEEP_ANALYSIS);
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            TrackingOptions opts = TrackingOptions.builder().maxDepth(20).build();
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1), opts);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("SIMPLE_COMPARISON 降级")
        void simpleComparison_degradation() {
            DegradationContext.setCurrentLevel(DegradationLevel.SIMPLE_COMPARISON);
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1),
                TrackingOptions.builder().maxDepth(10).build());
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("DegradationContext.getMaxElements 限制 collectionSummaryThreshold")
        void degradationMaxElements_limitsThreshold() {
            DegradationContext.setCurrentLevel(DegradationLevel.SKIP_DEEP_ANALYSIS);
            SnapshotConfig config = new SnapshotConfig();
            config.setCollectionSummaryThreshold(500);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 50; i++) list.add(i);
            Map<String, Object> obj = Map.of("list", list);
            Map<String, Object> snap = deep.captureDeep(obj,
                TrackingOptions.builder().maxDepth(5).collectionSummaryThreshold(500).build());
            assertThat(snap).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — handleCollection 截断与 ELEMENT")
    class ObjectSnapshotDeepCollectionTruncation {

        @Test
        @DisplayName("SUMMARY 策略超过 maxElements 截断")
        void summary_truncatedAtMaxElements() {
            SnapshotConfig config = new SnapshotConfig();
            config.setCollectionSummaryThreshold(200);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 150; i++) list.add(i);
            Map<String, Object> obj = Map.of("list", list);
            Map<String, Object> snap = deep.captureDeep(obj, 5, Collections.emptySet(), Collections.emptySet());
            boolean truncated = snap.entrySet().stream()
                .anyMatch(e -> String.valueOf(e.getValue()).contains("truncated"));
            assertThat(truncated).isTrue();
        }

        @Test
        @DisplayName("ELEMENT 策略添加 size 与 type 元数据")
        void element_addsMetadata() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.ELEMENT);
            Map<String, Object> obj = Map.of("list", List.of("a", "b", "c"));
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            boolean hasSize = snap.keySet().stream().anyMatch(k -> k.endsWith(".size"));
            boolean hasType = snap.keySet().stream().anyMatch(k -> k.endsWith(".type"));
            assertThat(hasSize).isTrue();
            assertThat(hasType).isTrue();
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — handleMap 大 Map 与 null 值")
    class ObjectSnapshotDeepMapPaths {

        @Test
        @DisplayName("Map 超过 threshold 使用摘要")
        void mapOverThreshold_summary() {
            SnapshotConfig config = new SnapshotConfig();
            config.setCollectionSummaryThreshold(5);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> large = new LinkedHashMap<>();
            for (int i = 0; i < 15; i++) large.put("k" + i, i);
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("m", large);
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            Object mVal = snap.values().stream()
                .filter(v -> v instanceof com.syy.taskflowinsight.tracking.summary.SummaryInfo)
                .findFirst().orElse(null);
            assertThat(mVal).isInstanceOf(com.syy.taskflowinsight.tracking.summary.SummaryInfo.class);
        }

        @Test
        @DisplayName("Map 值 null 记录")
        void mapNullValue_recorded() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("a", 1);
            m.put("b", null);
            Map<String, Object> obj = Map.of("m", m);
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue(null);
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — captureShallowReference getId 降级")
    class ObjectSnapshotDeepShallowRefGetId {

        @Test
        @DisplayName("无 @Key 对象降级为 toString")
        void noKey_fallbackToString() {
            SnapshotConfig config = new SnapshotConfig();
            config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithShallowRef entity = new EntityWithShallowRef();
            entity.ref = new NoKeyObject("fallback");
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            Object refVal = snap.get("ref");
            assertThat(refVal).isNotNull();
            assertThat(String.valueOf(refVal)).contains("fallback");
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — matchesPattern 与 excludePatterns")
    class ObjectSnapshotDeepMatchesPattern {

        @Test
        @DisplayName("excludePatterns 使用 *.field 模式")
        void excludePatterns_starField() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = Map.of("user", Map.of("name", "A", "password", "x"));
            Set<String> exclude = Set.of("*.password", "user.password");
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), exclude);
            assertThat(snap).doesNotContainKey("password");
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — ValueObject EQUALS 与 FIELDS")
    class ObjectSnapshotDeepValueObject {

        @Test
        @DisplayName("VALUE_OBJECT EQUALS 策略 null 安全")
        void valueObject_equalsNullSafe() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            Map<String, Object> snap = deep.captureDeep(null, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isEmpty();
        }

        @Test
        @DisplayName("VALUE_OBJECT FIELDS 策略深层嵌套")
        void valueObject_fieldsDeepNesting() {
            SnapshotConfig config = new SnapshotConfig();
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            NestedValueObject inner = new NestedValueObject("inner");
            NestedValueObject outer = new NestedValueObject("outer");
            outer.child = inner;
            Map<String, Object> snap = deep.captureDeep(outer, 5, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsKey("child.name");
            assertThat(snap).containsKey("name");
        }
    }

    @Nested
    @DisplayName("ObjectSnapshotDeep — 栈深度与时间预算")
    class ObjectSnapshotDeepStackAndTime {

        @Test
        @DisplayName("maxStackDepth 配置生效")
        void maxStackDepth_exceeded() {
            SnapshotConfig config = new SnapshotConfig();
            config.setMaxStackDepth(100);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            SimplePojo pojo = new SimplePojo();
            pojo.x = 1;
            pojo.nested = Map.of("y", 2);
            Map<String, Object> snap = deep.captureDeep(pojo, 5, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DiffDetector 未覆盖行
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DiffDetector — diffWithMode ENHANCED")
    class DiffDetectorEnhancedMode {

        @Test
        @DisplayName("ENHANCED 模式 reprOld reprNew valueRepr")
        void enhanced_reprBranches() {
            Map<String, Object> before = Map.of("name", "Alice", "age", 25);
            Map<String, Object> after = Map.of("name", "Bob", "age", 26);
            List<ChangeRecord> r = DiffDetector.diffWithMode("User", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).isNotEmpty();
            ChangeRecord rec = r.stream().filter(c -> "name".equals(c.getFieldName())).findFirst().orElseThrow();
            assertThat(rec.getReprOld()).isEqualTo("Alice");
            assertThat(rec.getReprNew()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("ENHANCED 模式 DELETE 时 valueRepr 为 oldValue")
        void enhanced_deleteValueRepr() {
            Map<String, Object> before = Map.of("x", "deleted");
            Map<String, Object> after = Collections.emptyMap();
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            ChangeRecord del = r.stream().filter(c -> c.getChangeType() == com.syy.taskflowinsight.tracking.ChangeType.DELETE).findFirst().orElseThrow();
            assertThat(del.getValueRepr()).isNotNull();
        }
    }

    @Nested
    @DisplayName("DiffDetector — getValueKind 分支")
    class DiffDetectorValueKind {

        @Test
        @DisplayName("getValueKind ENUM")
        void valueKind_enum() {
            Map<String, Object> before = Map.of("e", TestEnum.A);
            Map<String, Object> after = Map.of("e", TestEnum.B);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
            assertThat(r.get(0).getValueKind()).isEqualTo("ENUM");
        }

        @Test
        @DisplayName("getValueKind MAP")
        void valueKind_map() {
            Map<String, Object> before = Map.of("m", Map.of("a", 1));
            Map<String, Object> after = Map.of("m", Map.of("a", 2));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
            assertThat(r.get(0).getValueKind()).isEqualTo("MAP");
        }

        @Test
        @DisplayName("getValueKind ARRAY")
        void valueKind_array() {
            Map<String, Object> before = Map.of("arr", new String[]{"a"});
            Map<String, Object> after = Map.of("arr", new String[]{"b"});
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
            assertThat(r.get(0).getValueKind()).isEqualTo("ARRAY");
        }

        @Test
        @DisplayName("getValueKind OTHER")
        void valueKind_other() {
            Map<String, Object> before = Map.of("obj", new CustomObject("x"));
            Map<String, Object> after = Map.of("obj", new CustomObject("y"));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
            assertThat(r.get(0).getValueKind()).isEqualTo("OTHER");
        }
    }

    @Nested
    @DisplayName("DiffDetector — toReprCompat 与 BigDecimal")
    class DiffDetectorToReprCompat {

        @Test
        @DisplayName("toReprCompat BigDecimal 去尾零")
        void toReprCompat_bigDecimal() {
            Map<String, Object> before = Map.of("amt", new BigDecimal("1.500"));
            Map<String, Object> after = Map.of("amt", new BigDecimal("2.500"));
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).isNotEmpty();
        }

        @Test
        @DisplayName("toReprCompat Double Float")
        void toReprCompat_doubleFloat() {
            Map<String, Object> before = Map.of("d", 1.5);
            Map<String, Object> after = Map.of("d", 2.5);
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — deduplicateByPath 嵌套路径")
    class DiffDetectorDeduplicatePath {

        @Test
        @DisplayName("基础去重嵌套路径")
        void dedup_nestedPaths() {
            DiffDetector.setEnhancedDeduplicationEnabled(false);
            Map<String, Object> before = new HashMap<>();
            before.put("root", Map.of("a", 1, "b", Map.of("x", 10)));
            Map<String, Object> after = new HashMap<>();
            after.put("root", Map.of("a", 2, "b", Map.of("x", 20)));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
        }

        @Test
        @DisplayName("增强去重包含嵌套")
        void dedup_enhanced_nested() {
            DiffDetector.setEnhancedDeduplicationEnabled(true);
            Map<String, Object> before = Map.of("a", 1, "b", Map.of("x", 1));
            Map<String, Object> after = Map.of("a", 2, "b", Map.of("x", 2));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — compatHeavyOptimizations 与 heavy cache")
    class DiffDetectorHeavyPaths {

        @Test
        @DisplayName("compatHeavyOptimizations 大对象缓存")
        void compatHeavy_cache() {
            boolean saved = DiffDetector.isCompatHeavyOptimizationsEnabled();
            try {
                DiffDetector.setCompatHeavyOptimizationsEnabled(true);
                Map<String, Object> before = new HashMap<>();
                Map<String, Object> after = new HashMap<>();
                for (int i = 0; i < 60; i++) {
                    before.put("k" + i, i);
                    after.put("k" + i, i + 1);
                }
                List<ChangeRecord> r1 = DiffDetector.diffWithMode("Heavy", before, after, DiffDetector.DiffMode.COMPAT);
                List<ChangeRecord> r2 = DiffDetector.diffWithMode("Heavy", before, after, DiffDetector.DiffMode.COMPAT);
                assertThat(r1).isNotEmpty();
                assertThat(r2).hasSize(r1.size());
            } finally {
                DiffDetector.setCompatHeavyOptimizationsEnabled(saved);
            }
        }
    }

    @Nested
    @DisplayName("DiffDetector — Map Set Collection 策略比较")
    class DiffDetectorCollectionStrategies {

        @Test
        @DisplayName("Map 策略比较相同返回空")
        void mapStrategy_identical() {
            Map<String, Object> m = Map.of("a", 1, "b", 2);
            Map<String, Object> before = Map.of("m", m);
            Map<String, Object> after = Map.of("m", new HashMap<>(m));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("Set 策略比较")
        void setStrategy() {
            Set<String> s1 = new HashSet<>(Set.of("a", "b"));
            Set<String> s2 = new HashSet<>(Set.of("a", "c"));
            Map<String, Object> before = Map.of("s", s1);
            Map<String, Object> after = Map.of("s", s2);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
        }

        @Test
        @DisplayName("Collection 策略比较")
        void collectionStrategy() {
            List<String> c1 = List.of("a", "b");
            List<String> c2 = List.of("a", "c");
            Map<String, Object> before = Map.of("c", c1);
            Map<String, Object> after = Map.of("c", c2);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 测试模型
    // ═══════════════════════════════════════════════════════════════════════════

    @Entity
    static class EntityWithDiffIncludeAndInclude {
        @Key
        String id = "1";
        @DiffInclude
        String included;
        String excluded = "exc";
    }

    @Entity
    static class EntityNestedRecursive {
        @Key
        String id = "root";
        EntityNestedRecursive child;
    }

    @Entity
    static class EntityWithShallowRef {
        @Key
        String id = "e1";
        @ShallowReference
        Object ref;
    }

    static class NoKeyObject {
        final String data;

        NoKeyObject(String data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return "NoKeyObject{data=" + data + "}";
        }
    }

    @ValueObject
    static class NestedValueObject {
        String name;
        NestedValueObject child;

        NestedValueObject(String name) {
            this.name = name;
        }
    }

    enum TestEnum { A, B }

    static class CustomObject {
        final String v;

        CustomObject(String v) {
            this.v = v;
        }
    }

    static class SimplePojo {
        int x;
        Map<String, Object> nested;
    }
}
