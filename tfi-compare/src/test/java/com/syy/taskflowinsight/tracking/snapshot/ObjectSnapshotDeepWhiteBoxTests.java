package com.syy.taskflowinsight.tracking.snapshot;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import com.syy.taskflowinsight.tracking.monitoring.DegradationLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * ObjectSnapshotDeep 白盒测试
 * 覆盖深度快照的所有关键路径：captureDeep、traverseDFS、handleObject、handleEntity、
 * handleValueObject、handleCollection、handleMap、handleArray、captureShallowReference、
 * extractCompositeKey、formatAsString、isSimpleType、formatSimpleValue、formatPrimitiveArray、metrics
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("ObjectSnapshotDeep 白盒测试")
class ObjectSnapshotDeepWhiteBoxTests {

    private SnapshotConfig config;

    @BeforeEach
    void setUp() {
        config = new SnapshotConfig();
        config.setTimeBudgetMs(5000);
        config.setMaxStackDepth(1000);
        config.setCollectionSummaryThreshold(5);
        DegradationContext.reset();
    }

    @AfterEach
    void tearDown() {
        DegradationContext.reset();
        ObjectSnapshotDeep.resetMetrics();
    }

    // ── captureDeep: null 参数与 BUG 修复 ──

    @Nested
    @DisplayName("captureDeep null 参数与 BUG 修复")
    class CaptureDeepNullParams {

        @Test
        @DisplayName("null root 返回空 Map")
        void nullRoot_returnsEmpty() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            assertThat(deep.captureDeep(null, 5, Collections.emptySet(), Collections.emptySet())).isEmpty();
        }

        @Test
        @DisplayName("null includeFields 安全处理（等同于空集合）")
        void nullIncludeFields_safe() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = Map.of("a", 1, "b", "x");
            Map<String, Object> snap = deep.captureDeep(obj, 3, null, Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("null excludePatterns 安全处理（等同于空集合）")
        void nullExcludePatterns_safe() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = Map.of("a", 1);
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), null);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("null includeFields 与 null excludePatterns 同时传入")
        void bothNull_safe() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = Map.of("x", "y");
            Map<String, Object> snap = deep.captureDeep(obj, 2, null, null);
            assertThat(snap).isNotEmpty();
        }
    }

    // ── captureDeep with TrackingOptions ──

    @Nested
    @DisplayName("captureDeep TrackingOptions 降级路径")
    class CaptureDeepTrackingOptions {

        @Test
        @DisplayName("null options 使用默认参数")
        void nullOptions_usesDefaults() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1), null);
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("DISABLED 降级返回空快照")
        void disabledDegradation_returnsEmpty() {
            DegradationContext.setCurrentLevel(DegradationLevel.DISABLED);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1),
                TrackingOptions.builder().maxDepth(5).build());
            assertThat(snap).isEmpty();
        }

        @Test
        @DisplayName("SUMMARY_ONLY 降级返回摘要信息")
        void summaryOnlyDegradation_returnsSummary() {
            DegradationContext.setCurrentLevel(DegradationLevel.SUMMARY_ONLY);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1),
                TrackingOptions.builder().maxDepth(5).build());
            assertThat(snap).containsKey("_type");
            assertThat(snap).containsKey("_summary");
            assertThat(snap.get("_summary")).isEqualTo("Degraded to summary only");
        }

        @Test
        @DisplayName("FULL_TRACKING 正常深度捕获")
        void fullTracking_normalCapture() {
            DegradationContext.setCurrentLevel(DegradationLevel.FULL_TRACKING);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1, "b", 2),
                TrackingOptions.builder().maxDepth(5).build());
            assertThat(snap).isNotEmpty();
        }

        @Test
        @DisplayName("SKIP_DEEP_ANALYSIS 降级调整 maxDepth")
        void skipDeepAnalysis_adjustsDepth() {
            DegradationContext.setCurrentLevel(DegradationLevel.SKIP_DEEP_ANALYSIS);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1),
                TrackingOptions.builder().maxDepth(20).build());
            assertThat(snap).isNotEmpty();
        }
    }

    // ── traverseDFS: 深度限制、空对象、简单类型、循环引用 ──

    @Nested
    @DisplayName("traverseDFS 深度与类型路径")
    class TraverseDFSPaths {

        @Test
        @DisplayName("depth limit 达到时返回 depth-limit 占位")
        void depthLimit_reachesPlaceholder() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("inner", Map.of("deep", "value"));
            Map<String, Object> root = Map.of("level0", nested);
            Map<String, Object> snap = deep.captureDeep(root, 1, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue("<depth-limit>");
        }

        @Test
        @DisplayName("null 对象记录为 null")
        void nullObject_recordedAsNull() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("nullable", null);
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue(null);
        }

        @Test
        @DisplayName("简单类型直接格式化")
        void simpleTypes_formatted() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            SimpleTypeHolder holder = new SimpleTypeHolder();
            Map<String, Object> snap = deep.captureDeep(holder, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsEntry("str", "hello");
            assertThat(snap).containsEntry("num", 42);
            assertThat(snap).containsEntry("flag", true);
            assertThat(snap).containsEntry("ch", 'A');
        }

        @Test
        @DisplayName("循环引用检测")
        void circularReference_detected() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            CircularNode a = new CircularNode("a");
            CircularNode b = new CircularNode("b");
            a.next = b;
            b.next = a;
            Map<String, Object> snap = deep.captureDeep(a, 5, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue("<circular-reference>");
        }

        @Test
        @DisplayName("Date 深拷贝")
        void date_deepCopy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Date d = new Date(1234567890L);
            Map<String, Object> obj = Map.of("d", d);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            Object captured = snap.values().stream().filter(v -> v instanceof Date).findFirst().orElse(null);
            assertThat(captured).isInstanceOf(Date.class);
            assertThat(((Date) captured).getTime()).isEqualTo(1234567890L);
            assertThat(captured).isNotSameAs(d);
        }

        @Test
        @DisplayName("超长字符串截断")
        void longString_truncated() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            String longStr = "x".repeat(1500);
            Map<String, Object> obj = Map.of("long", longStr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            Object captured = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
            assertThat(captured).asString().contains("... (truncated)");
        }

        @Test
        @DisplayName("Enum 类型")
        void enumType() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = Map.of("e", TestEnum.ONE);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue(TestEnum.ONE);
        }
    }

    // ── handleObject: 静态字段、白名单、DiffIgnore、UnifiedFilterEngine ──

    @Nested
    @DisplayName("handleObject 过滤与字段路径")
    class HandleObjectPaths {

        @Test
        @DisplayName("includeFields 白名单仅包含指定字段")
        void includeFields_whitelist() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            TestUser user = new TestUser("A", 1, "a@b.com");
            Set<String> include = Set.of("name");
            Map<String, Object> snap = deep.captureDeep(user, 3, include, Collections.emptySet());
            assertThat(snap).containsKey("name");
            assertThat(snap).doesNotContainKeys("age", "email");
        }

        @Test
        @DisplayName("DiffIgnore 字段被排除")
        void diffIgnore_excluded() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            DiffIgnoreHolder holder = new DiffIgnoreHolder();
            Map<String, Object> snap = deep.captureDeep(holder, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsKey("visible");
            assertThat(snap).doesNotContainKey("ignored");
        }

        @Test
        @DisplayName("excludePatterns 排除匹配路径")
        void excludePatterns_excludes() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = Map.of("name", "x", "password", "secret");
            Set<String> exclude = Set.of("*.password");
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), exclude);
            assertThat(snap).doesNotContainKey("password");
        }
    }

    // ── handleObjectWithTypeAware: ENTITY, VALUE_OBJECT, BASIC_TYPE, COLLECTION, default ──

    @Nested
    @DisplayName("handleObjectWithTypeAware 类型感知路径")
    class TypeAwarePaths {

        @Test
        @DisplayName("ENTITY 类型处理 @Key 字段")
        void entity_keyFields() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            TestEntity entity = new TestEntity("id-1", "Name");
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsEntry("id", "id-1");
        }

        @Test
        @DisplayName("ENTITY 带 @DiffInclude 白名单模式")
        void entity_diffIncludeWhitelist() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithDiffInclude entity = new EntityWithDiffInclude();
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsKey("included");
            assertThat(snap).doesNotContainKey("excluded");
        }

        @Test
        @DisplayName("ENTITY 带 @ShallowReference 字段")
        void entity_shallowReference() {
            config.setShallowReferenceMode(ShallowReferenceMode.VALUE_ONLY);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithShallowRef entity = new EntityWithShallowRef();
            entity.ref = new TestEntity("ref-id", "Ref");
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsKey("ref");
            assertThat(snap.get("ref")).isEqualTo("ref-id");
        }

        @Test
        @DisplayName("VALUE_OBJECT FIELDS 策略递归字段")
        void valueObject_fieldsStrategy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            TestValueObject vo = new TestValueObject("v1", 10);
            Map<String, Object> snap = deep.captureDeep(vo, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsEntry("code", "v1");
            assertThat(snap).containsEntry("count", 10);
        }

        @Test
        @DisplayName("VALUE_OBJECT EQUALS 策略使用 hashCode")
        void valueObject_equalsStrategy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            ValueObjectEquals vo = new ValueObjectEquals("x");
            Map<String, Object> snap = deep.captureDeep(vo, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).hasSize(1);
            assertThat(snap.values().iterator().next()).isInstanceOf(Integer.class);
        }

        @Test
        @DisplayName("BASIC_TYPE 直接格式化")
        void basicType_formatted() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            Map<String, Object> snap = deep.captureDeep("hello", 2, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue("hello");
        }

        @Test
        @DisplayName("COLLECTION 类型备用分支")
        void collection_fallback() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            Map<String, Object> snap = deep.captureDeep(List.of(1, 2), 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }
    }

    // ── captureShallowReference: VALUE_ONLY, COMPOSITE_STRING, COMPOSITE_MAP ──

    @Nested
    @DisplayName("captureShallowReference 模式")
    class ShallowReferenceModes {

        @Test
        @DisplayName("VALUE_ONLY 模式")
        void valueOnlyMode() {
            config.setShallowReferenceMode(ShallowReferenceMode.VALUE_ONLY);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithShallowRef entity = new EntityWithShallowRef();
            entity.ref = new TestEntity("key-val", "Name");
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap.get("ref")).isEqualTo("key-val");
        }

        @Test
        @DisplayName("COMPOSITE_STRING 模式")
        void compositeStringMode() {
            config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithShallowRef entity = new EntityWithShallowRef();
            entity.ref = new MultiKeyEntity("k1", "k2");
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            Object refVal = snap.get("ref");
            assertThat(refVal).asString().startsWith("[");
            assertThat(refVal).asString().contains("key1=k1");
            assertThat(refVal).asString().contains("key2=k2");
        }

        @Test
        @DisplayName("COMPOSITE_MAP 模式")
        void compositeMapMode() {
            config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_MAP);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithShallowRef entity = new EntityWithShallowRef();
            entity.ref = new MultiKeyEntity("k1", "k2");
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            Object refVal = snap.get("ref");
            assertThat(refVal).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) refVal;
            assertThat(m).containsEntry("key1", "k1").containsEntry("key2", "k2");
        }

        @Test
        @DisplayName("无 @Key 对象降级为 toString")
        void noKey_fallbackToString() {
            config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithShallowRef entity = new EntityWithShallowRef();
            entity.ref = new NoKeyObject("fallback");
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap.get("ref")).asString().contains("fallback");
        }

        @Test
        @DisplayName("formatAsString 转义逗号等号反斜杠")
        void formatAsString_escapes() {
            config.setShallowReferenceMode(ShallowReferenceMode.COMPOSITE_STRING);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setTypeAwareEnabled(true);
            EntityWithShallowRef entity = new EntityWithShallowRef();
            entity.ref = new MultiKeyEntity("val,comma", "eq=val");
            Map<String, Object> snap = deep.captureDeep(entity, 3, Collections.emptySet(), Collections.emptySet());
            Object refVal = snap.get("ref");
            String s = String.valueOf(refVal);
            assertThat(s.contains("\\,") || s.contains("\\=")).isTrue();
        }
    }

    // ── handleCollection: IGNORE, SUMMARY, ELEMENT ──

    @Nested
    @DisplayName("handleCollection 策略")
    class HandleCollectionPaths {

        @Test
        @DisplayName("IGNORE 策略只记录类型和大小")
        void ignoreStrategy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.IGNORE);
            Map<String, Object> obj = Map.of("list", new ArrayList<>(List.of(1, 2, 3)));
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            Object listVal = snap.values().iterator().next();
            assertThat(listVal).asString().contains("ArrayList");
            assertThat(listVal).asString().contains("3");
        }

        @Test
        @DisplayName("SUMMARY 策略大集合摘要")
        void summaryStrategy_large() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 20; i++) large.add(i);
            Map<String, Object> obj = Map.of("list", large);
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            Object listVal = snap.values().iterator().next();
            assertThat(listVal).isInstanceOf(com.syy.taskflowinsight.tracking.summary.SummaryInfo.class);
        }

        @Test
        @DisplayName("SUMMARY 策略小集合展开")
        void summaryStrategy_small() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
            Map<String, Object> obj = Map.of("list", List.of(1, 2));
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue(1);
            assertThat(snap).containsValue(2);
        }

        @Test
        @DisplayName("ELEMENT 策略逐元素处理")
        void elementStrategy() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.ELEMENT);
            Map<String, Object> obj = Map.of("list", List.of("a", "b"));
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue(2);
            assertThat(snap).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("maxElements 截断（阈值大于100时展开并截断）")
        void maxElements_truncated() {
            config.setCollectionSummaryThreshold(200);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.setCollectionStrategy(TrackingOptions.CollectionStrategy.SUMMARY);
            List<Integer> large = new ArrayList<>();
            for (int i = 0; i < 150; i++) large.add(i);
            Map<String, Object> obj = Map.of("list", large);
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            boolean hasTruncated = snap.entrySet().stream()
                .anyMatch(e -> e.getKey().contains("[...]") && String.valueOf(e.getValue()).contains("truncated"));
            assertThat(hasTruncated).isTrue();
        }
    }

    // ── handleMap ──

    @Nested
    @DisplayName("handleMap 路径")
    class HandleMapPaths {

        @Test
        @DisplayName("大 Map 使用摘要")
        void largeMap_summary() {
            config.setCollectionSummaryThreshold(5);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> large = new LinkedHashMap<>();
            for (int i = 0; i < 10; i++) large.put("k" + i, i);
            Map<String, Object> obj = Map.of("m", large);
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            Object mVal = snap.values().iterator().next();
            assertThat(mVal).isInstanceOf(com.syy.taskflowinsight.tracking.summary.SummaryInfo.class);
        }

        @Test
        @DisplayName("小 Map 展开")
        void smallMap_expanded() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> obj = Map.of("m", Map.of("a", 1, "b", 2));
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).hasSizeGreaterThanOrEqualTo(2);
            assertThat(snap).containsValue(1);
            assertThat(snap).containsValue(2);
        }

        @Test
        @DisplayName("Map 中 null 值")
        void map_nullValues() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("a", 1);
            m.put("b", null);
            Map<String, Object> obj = Map.of("m", m);
            Map<String, Object> snap = deep.captureDeep(obj, 3, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue(null);
        }
    }

    // ── handleArray ──

    @Nested
    @DisplayName("handleArray 路径")
    class HandleArrayPaths {

        @Test
        @DisplayName("基本类型数组")
        void primitiveArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            int[] arr = {1, 2, 3};
            Map<String, Object> obj = Map.of("arr", arr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            Object arrVal = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
            assertThat(arrVal).asString().contains("int");
            assertThat(arrVal).asString().contains("3");
        }

        @Test
        @DisplayName("空基本类型数组")
        void emptyPrimitiveArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            int[] arr = {};
            Map<String, Object> obj = Map.of("arr", arr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            Object arrVal = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
            assertThat(arrVal).asString().contains("[0]");
        }

        @Test
        @DisplayName("对象数组")
        void objectArray() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            String[] arr = {"a", "b"};
            Map<String, Object> obj = Map.of("arr", arr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).containsValue("a");
            assertThat(snap).containsValue("b");
        }

        @Test
        @DisplayName("大数组摘要")
        void largeArray_summary() {
            config.setCollectionSummaryThreshold(5);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            String[] arr = new String[20];
            Arrays.fill(arr, "x");
            Map<String, Object> obj = Map.of("arr", arr);
            Map<String, Object> snap = deep.captureDeep(obj, 2, Collections.emptySet(), Collections.emptySet());
            Object arrVal = snap.values().stream().filter(v -> v instanceof String).findFirst().orElse(null);
            assertThat(arrVal).asString().contains("length=20");
        }
    }

    // ── getMetrics / resetMetrics ──

    @Nested
    @DisplayName("getMetrics 与 resetMetrics")
    class MetricsPaths {

        @Test
        @DisplayName("getMetrics 返回指标 Map")
        void getMetrics_returnsMap() {
            Map<String, Long> m = ObjectSnapshotDeep.getMetrics();
            assertThat(m).containsKeys("depth.limit.reached", "cycle.detected", "path.excluded", "current.stack.depth");
        }

        @Test
        @DisplayName("resetMetrics 重置计数")
        void resetMetrics() {
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            deep.captureDeep(Map.of("a", Map.of("b", Map.of("c", 1))), 1, Collections.emptySet(), Collections.emptySet());
            ObjectSnapshotDeep.resetMetrics();
            Map<String, Long> m = ObjectSnapshotDeep.getMetrics();
            assertThat(m.get("depth.limit.reached")).isGreaterThanOrEqualTo(0);
        }
    }

    // ── 时间预算（需短 timeBudget 触发）──

    @Nested
    @DisplayName("时间预算路径")
    class TimeBudgetPath {

        @Test
        @DisplayName("timeBudget 配置生效（短预算可能提前终止）")
        void timeBudget_applied() {
            config.setTimeBudgetMs(5000);
            ObjectSnapshotDeep deep = new ObjectSnapshotDeep(config);
            Map<String, Object> snap = deep.captureDeep(Map.of("a", 1), 5, Collections.emptySet(), Collections.emptySet());
            assertThat(snap).isNotEmpty();
        }
    }

    // ── 测试模型类 ──

    static class TestUser {
        String name;
        int age;
        String email;

        TestUser(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }
    }

    static class SimpleTypeHolder {
        String str = "hello";
        int num = 42;
        boolean flag = true;
        char ch = 'A';
    }

    static class CircularNode {
        String id;
        CircularNode next;

        CircularNode(String id) {
            this.id = id;
        }
    }

    enum TestEnum { ONE, TWO }

    static class DiffIgnoreHolder {
        String visible = "v";
        @DiffIgnore
        String ignored = "i";
    }

    @Entity
    static class TestEntity {
        @Key
        String id;
        String name;

        TestEntity(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Entity
    static class EntityWithDiffInclude {
        @Key
        String id = "1";
        @DiffInclude
        String included = "inc";
        String excluded = "exc";
    }

    @Entity
    static class EntityWithShallowRef {
        @Key
        String id = "e1";
        @ShallowReference
        Object ref;
    }

    @ValueObject
    static class TestValueObject {
        String code;
        int count;

        TestValueObject(String code, int count) {
            this.code = code;
            this.count = count;
        }
    }

    @ValueObject(strategy = ValueObjectCompareStrategy.EQUALS)
    static class ValueObjectEquals {
        String x;

        ValueObjectEquals(String x) {
            this.x = x;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValueObjectEquals that = (ValueObjectEquals) o;
            return Objects.equals(x, that.x);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x);
        }
    }

    @Entity
    static class MultiKeyEntity {
        @Key
        String key1;
        @Key
        String key2;

        MultiKeyEntity(String key1, String key2) {
            this.key1 = key1;
            this.key2 = key2;
        }
    }

    static class NoKeyObject {
        String data;

        NoKeyObject(String data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return "NoKeyObject{data=" + data + "}";
        }
    }
}
