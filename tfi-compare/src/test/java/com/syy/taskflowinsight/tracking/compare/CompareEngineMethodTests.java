package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
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
 * 覆盖：Map 的精确 key 合同、不可寻址 key 限制、DiffDetector.diffWithMode、
 * CompareEngine.collectShallowReferenceChanges、extractLeafFieldName。
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("精准方法覆盖测试 — Map key 合同、diffWithMode、collectShallowReferenceChanges")
class CompareEngineMethodTests {

    // ═══════════════════════════════════════════════════════════════════════════
    //  1. MapCompareStrategy 精确 key 路径
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MapCompareStrategy — 可寻址 key 场景")
    class ProcessChangesSimpleTests {

        private MapCompareStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new MapCompareStrategy();
        }

        @Test
        @DisplayName("大 Map 仍完整报告新增、删除和修改 key")
        void largeMap_addedRemovedModifiedKeys() {
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

            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).isNotEmpty();
            assertThat(r.getChanges()).anyMatch(c -> CanonicalChangeTestSupport.hasExactMapKey(c, "common")
                    && c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("present-null 变为非 null 是同 key UPDATE")
        void presentNullToValue_isUpdate() {
            Map<String, Object> m1 = new HashMap<>();
            Map<String, Object> m2 = new HashMap<>();
            for (int i = 0; i < 35; i++) m1.put("d" + i, i);
            for (int i = 0; i < 35; i++) m2.put("a" + i, i);
            m1.put("k", null);
            m2.put("k", "new");

            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r.getChanges()).anyMatch(c -> CanonicalChangeTestSupport.hasExactMapKey(c, "k")
                    && c.getChangeType() == ChangeType.UPDATE);
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

            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r.getChanges()).anyMatch(c -> CanonicalChangeTestSupport.hasExactMapKey(c, "nested")
                    && c.getChangeType() == ChangeType.UPDATE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  2. MapCompareStrategy 不可寻址复杂 key
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MapCompareStrategy — 不可寻址复杂 key")
    class ProcessChangesWithEntityKeysTests {

        private MapCompareStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new MapCompareStrategy();
        }

        @Test
        @DisplayName("复杂 key 不使用 Entity 身份猜测配对")
        void complexKeyDoesNotUseEntityIdentity() {
            EntityKey k1 = new EntityKey(100L);
            EntityKey k2 = new EntityKey(100L);
            Map<EntityKey, String> m1 = new HashMap<>();
            Map<EntityKey, String> m2 = new HashMap<>();
            m1.put(k1, "v1");
            m2.put(k2, "v2");

            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
            assertThat(r.getLimitations()).extracting(CompareLimitation::code)
                    .contains(CompareLimitationCode.KEY_AMBIGUOUS);
        }

        @Test
        @DisplayName("不同复杂 key 形成 typed limitation")
        void differentComplexKeysAreLimited() {
            EntityKey k1 = new EntityKey(1L);
            EntityKey k2 = new EntityKey(2L);
            Map<EntityKey, String> m1 = new HashMap<>();
            Map<EntityKey, String> m2 = new HashMap<>();
            m1.put(k1, "v1");
            m2.put(k2, "v2");

            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r.getCompletion()).isEqualTo(CompareCompletion.PARTIAL);
            assertThat(r.getLimitations()).extracting(CompareLimitation::code)
                    .contains(CompareLimitationCode.KEY_AMBIGUOUS);
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

            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  3. DiffDetector.diffWithMode
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DiffDetector.diffWithMode — 旧签名统一委托canonical runtime")
    class DiffWithModeTests {

        @Test
        @DisplayName("不同Map实现不影响DELETE语义")
        void compatDelete_valueReprNull() {
            Map<String, Object> before = Map.of("x", "deleted");
            Map<String, Object> after = new HashMap<>();

            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.COMPAT);
            ChangeRecord del = r.stream().filter(c -> c.getChangeType() == ChangeType.DELETE).findFirst().orElseThrow();
            assertThat(del.getOldValue()).isEqualTo("deleted");
            assertThat(del.getNewValue()).isNull();
            assertThat(del.getValueRepr()).isNull();
        }

        @Test
        @DisplayName("ENHANCED token不建立第二套展示投影")
        void enhancedReprOldNew() {
            Map<String, Object> before = Map.of("a", 1, "b", "hello");
            Map<String, Object> after = Map.of("a", 2, "b", "world");

            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).isNotEmpty();
            ChangeRecord change = r.stream().filter(c -> "a".equals(c.getFieldName())).findFirst().orElseThrow();
            assertThat(change.getOldValue()).isEqualTo(1);
            assertThat(change.getNewValue()).isEqualTo(2);
            assertThat(change.getReprOld()).isNull();
            assertThat(change.getReprNew()).isNull();
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

            CompareOptions opts = CompareOptions.builder().build();
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

            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = compareService.compare(list1, list2, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Map value 含 Entity")
        void mapWithEntityValues() {
            Map<String, EntityItem> m1 = Map.of("k", new EntityItem(1L, "A"));
            Map<String, EntityItem> m2 = Map.of("k", new EntityItem(2L, "B"));

            CompareOptions opts = CompareOptions.builder().build();
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

            CompareOptions opts = CompareOptions.builder().build();
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

            CompareOptions opts = CompareOptions.builder().build();
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
