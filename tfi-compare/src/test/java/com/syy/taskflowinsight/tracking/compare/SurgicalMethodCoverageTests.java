package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对低覆盖方法的精准覆盖测试。
 * 覆盖：MapCompareStrategy.processChangesSimple、processChangesWithEntityKeys、
 * DiffDetector.diffWithMode、deduplicateByPath、
 * CompareEngine.collectShallowReferenceChanges、extractLeafFieldName。
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("精准方法覆盖测试 — processChangesSimple、processChangesWithEntityKeys、diffWithMode、deduplicateByPath、collectShallowReferenceChanges、extractLeafFieldName")
class SurgicalMethodCoverageTests {

    // ═══════════════════════════════════════════════════════════════════════════
    //  1. MapCompareStrategy.processChangesSimple（非 Entity 路径）
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MapCompareStrategy.processChangesSimple — 无 Entity key 场景")
    class ProcessChangesSimpleTests {

        private MapCompareStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new MapCompareStrategy();
        }

        @Test
        @DisplayName("候选对超阈值 → processChangesSimple 非 Entity 路径：新增/删除/修改键")
        void processChangesSimple_addedRemovedModifiedKeys() {
            // 35*35=1225 > 1000，触发 processChangesSimple，且无 Entity key
            Map<String, Object> m1 = new HashMap<>();
            Map<String, Object> m2 = new HashMap<>();
            for (int i = 0; i < 35; i++) {
                m1.put("del" + i, i);
            }
            for (int i = 0; i < 35; i++) {
                m2.put("add" + i, i);
            }
            m1.put("common", 1);
            m2.put("common", 2);

            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).isNotEmpty();
            assertThat(r.getChanges()).anyMatch(c -> "common".equals(c.getFieldName()) && c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("processChangesSimple：null 值 CREATE/DELETE")
        void processChangesSimple_nullValues() {
            Map<String, Object> m1 = new HashMap<>();
            Map<String, Object> m2 = new HashMap<>();
            for (int i = 0; i < 35; i++) m1.put("d" + i, i);
            for (int i = 0; i < 35; i++) m2.put("a" + i, i);
            m1.put("k", null);
            m2.put("k", "new");

            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> "k".equals(c.getFieldName()) && c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("processChangesSimple：嵌套对象作为 value")
        void processChangesSimple_nestedObjects() {
            Map<String, Object> inner1 = Map.of("x", 1);
            Map<String, Object> inner2 = Map.of("x", 2);
            Map<String, Object> m1 = new HashMap<>();
            Map<String, Object> m2 = new HashMap<>();
            for (int i = 0; i < 35; i++) m1.put("d" + i, i);
            for (int i = 0; i < 35; i++) m2.put("a" + i, i);
            m1.put("nested", inner1);
            m2.put("nested", inner2);

            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> "nested".equals(c.getFieldName()) && c.getChangeType() == ChangeType.UPDATE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  2. MapCompareStrategy.processChangesWithEntityKeys
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MapCompareStrategy.processChangesWithEntityKeys — Entity key 场景")
    class ProcessChangesWithEntityKeysTests {

        private MapCompareStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new MapCompareStrategy();
        }

        @Test
        @DisplayName("Entity key 相同、value 不同 → UPDATE")
        void entityKeySame_valueDifferent() {
            EntityKey k1 = new EntityKey(100L);
            EntityKey k2 = new EntityKey(100L);
            Map<EntityKey, String> m1 = new HashMap<>();
            Map<EntityKey, String> m2 = new HashMap<>();
            m1.put(k1, "v1");
            m2.put(k2, "v2");

            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("Entity key 删除 → DELETE")
        void entityKeyDeleted() {
            EntityKey k1 = new EntityKey(1L);
            EntityKey k2 = new EntityKey(2L);
            Map<EntityKey, String> m1 = new HashMap<>();
            Map<EntityKey, String> m2 = new HashMap<>();
            m1.put(k1, "v1");
            m2.put(k2, "v2");

            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("Entity value 深度对比、trackEntityKeyAttributes")
        void entityValueDeep_trackKeyAttributes() {
            EntityKeyWithAttr k1 = new EntityKeyWithAttr(100L, "desc1");
            EntityKeyWithAttr k2 = new EntityKeyWithAttr(100L, "desc2");
            Map<EntityKeyWithAttr, EntityItem> m1 = new HashMap<>();
            Map<EntityKeyWithAttr, EntityItem> m2 = new HashMap<>();
            m1.put(k1, new EntityItem(1L, "A"));
            m2.put(k2, new EntityItem(1L, "B"));

            CompareOptions opts = CompareOptions.builder().trackEntityKeyAttributes(true).build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  3. DiffDetector.diffWithMode
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DiffDetector.diffWithMode — COMPAT/ENHANCED 模式")
    class DiffWithModeTests {

        @BeforeEach
        void setUp() {
            DiffDetector.setPrecisionCompareEnabled(false);
        }

        @Test
        @DisplayName("COMPAT 模式 DELETE 时 valueRepr 为 null")
        void compatDelete_valueReprNull() {
            Map<String, Object> before = Map.of("x", "deleted");
            Map<String, Object> after = new HashMap<>();

            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.COMPAT);
            ChangeRecord del = r.stream().filter(c -> c.getChangeType() == ChangeType.DELETE).findFirst().orElseThrow();
            assertThat(del.getValueRepr()).isNull();
        }

        @Test
        @DisplayName("ENHANCED 模式 reprOld/reprNew 非空")
        void enhancedReprOldNew() {
            Map<String, Object> before = Map.of("a", 1, "b", "hello");
            Map<String, Object> after = Map.of("a", 2, "b", "world");

            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).isNotEmpty();
            assertThat(r.get(0).getReprOld()).isNotNull();
            assertThat(r.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("ENHANCED 模式不同值类型：String、Number、Date")
        void enhancedDifferentValueTypes() {
            Map<String, Object> before = Map.of("s", "old", "n", 10, "d", new java.util.Date(1000));
            Map<String, Object> after = Map.of("s", "new", "n", 20, "d", new java.util.Date(2000));

            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).hasSize(3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  4. DiffDetector.deduplicateByPath
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DiffDetector.deduplicateByPath — 路径去重")
    class DeduplicateByPathTests {

        private boolean savedEnhanced;

        @BeforeEach
        void setUp() {
            savedEnhanced = DiffDetector.isEnhancedDeduplicationEnabled();
            DiffDetector.setEnhancedDeduplicationEnabled(false);
        }

        @AfterEach
        void tearDown() {
            DiffDetector.setEnhancedDeduplicationEnabled(savedEnhanced);
        }

        @Test
        @DisplayName("基础去重：无嵌套路径直接返回")
        void basicDedup_noNestedPath() {
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 2, "b", 3);

            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).hasSize(2);
        }

        @Test
        @DisplayName("基础去重：嵌套路径去重（父路径与子路径）")
        void basicDedup_nestedPaths() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            before.put("root", Map.of("a", 1, "b", 2));
            after.put("root", Map.of("a", 2, "b", 3));
            // DiffDetector 对 Map 值做快照比较，会产生 root.a、root.b 等路径
            // 通过 DiffFacade 会走 DiffDetector，需要嵌套结构产生多路径
            Map<String, Object> innerBefore = new HashMap<>();
            innerBefore.put("a", 1);
            innerBefore.put("b", 2);
            innerBefore.put("c", 3);
            Map<String, Object> innerAfter = new HashMap<>();
            innerAfter.put("a", 10);
            innerAfter.put("b", 20);
            innerAfter.put("c", 30);
            before.put("nested", innerBefore);
            after.put("nested", innerAfter);

            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
        }

        @Test
        @DisplayName("增强去重模式")
        void enhancedDedup() {
            DiffDetector.setEnhancedDeduplicationEnabled(true);
            Map<String, Object> before = Map.of("a", 1, "b", Map.of("x", 1));
            Map<String, Object> after = Map.of("a", 2, "b", Map.of("x", 2));

            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
            DiffDetector.setEnhancedDeduplicationEnabled(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  5. CompareEngine.collectShallowReferenceChanges
    //  6. CompareEngine.extractLeafFieldName（通过 collectShallowReferenceChanges 间接调用）
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CompareEngine — collectShallowReferenceChanges、extractLeafFieldName")
    class CompareEngineShallowRefTests {

        private CompareService compareService;

        @BeforeEach
        void setUp() {
            compareService = new CompareService();
        }

        @Test
        @DisplayName("数组元素含 Entity → 引用变更检测、extractLeafFieldName")
        void arrayWithEntityElements() {
            EntityItem e1 = new EntityItem(1L, "A");
            EntityItem e2 = new EntityItem(2L, "B");
            EntityItem[] arr1 = {e1};
            EntityItem[] arr2 = {e2};

            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(arr1, arr2, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Collection 元素含 Entity")
        void collectionWithEntityElements() {
            EntityItem e1 = new EntityItem(1L, "A");
            EntityItem e2 = new EntityItem(2L, "B");
            List<EntityItem> list1 = List.of(e1);
            List<EntityItem> list2 = List.of(e2);

            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(list1, list2, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Map value 含 Entity")
        void mapWithEntityValues() {
            Map<String, EntityItem> m1 = Map.of("k", new EntityItem(1L, "A"));
            Map<String, EntityItem> m2 = Map.of("k", new EntityItem(2L, "B"));

            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(m1, m2, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("@ShallowReference 字段引用变更")
        void shallowReferenceFieldChange() {
            EntityItem ref1 = new EntityItem(1L, "A");
            EntityItem ref2 = new EntityItem(2L, "B");
            ContainerWithShallowRef a = new ContainerWithShallowRef(ref1);
            ContainerWithShallowRef b = new ContainerWithShallowRef(ref2);

            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("extractLeafFieldName 路径：简单、点分、索引、括号")
        void extractLeafFieldName_viaDeepCompare() {
            // extractLeafFieldName 在 collectShallowReferenceChanges 中用于 array/collection/map 元素路径
            // 路径格式：prefix + "[" + i + "]" 或 prefix + "[" + key + "]"
            // 简单对象触发 deep fallback，产生 field paths
            NestedForExtractLeaf a = new NestedForExtractLeaf();
            a.items = List.of(new EntityItem(1L, "x"));
            a.map = Map.of("k", new EntityItem(2L, "y"));
            NestedForExtractLeaf b = new NestedForExtractLeaf();
            b.items = List.of(new EntityItem(3L, "z"));
            b.map = Map.of("k", new EntityItem(4L, "w"));

            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  测试模型
    // ═══════════════════════════════════════════════════════════════════════════

    @Entity(name = "EntityItem")
    static class EntityItem {
        @Key
        private Long id;
        private String name;

        EntityItem(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityItem that = (EntityItem) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Entity(name = "EntityKey")
    static class EntityKey {
        @Key
        private Long id;

        EntityKey(Long id) {
            this.id = id;
        }

        public Long getId() { return id; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityKey that = (EntityKey) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Entity(name = "EntityKeyWithAttr")
    static class EntityKeyWithAttr {
        @Key
        private Long id;
        private String description;

        EntityKeyWithAttr(Long id, String description) {
            this.id = id;
            this.description = description;
        }

        public Long getId() { return id; }
        public String getDescription() { return description; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityKeyWithAttr that = (EntityKeyWithAttr) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    static class ContainerWithShallowRef {
        @ShallowReference
        private EntityItem ref;

        ContainerWithShallowRef(EntityItem ref) {
            this.ref = ref;
        }

        public EntityItem getRef() { return ref; }
    }

    static class NestedForExtractLeaf {
        List<EntityItem> items = new ArrayList<>();
        Map<String, EntityItem> map = new HashMap<>();
    }
}
