package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * MapCompareStrategy 最终覆盖测试 — 覆盖所有未覆盖路径。
 * 目标：compare、generateDetailedChangeRecords、Entity key/value、
 * 重命名检测、相似度、报告生成、trackEntityKeyAttributes 等。
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("MapCompareStrategy 最终覆盖测试")
class MapCompareStrategyExtendedTests {

    private MapCompareStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MapCompareStrategy();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  compare 基础场景
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("compare 基础场景 — null、空、相同、不同")
    class CompareBasicScenarios {

        @Test
        @DisplayName("同一引用 → identical")
        void sameReference() {
            Map<String, Integer> m = Map.of("a", 1);
            CompareResult r = strategy.compare(m, m, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("map1 null → ofNullDiff")
        void map1Null() {
            CompareResult r = strategy.compare(null, Map.of("a", 1), CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getObject1()).isNull();
        }

        @Test
        @DisplayName("map2 null → ofNullDiff")
        void map2Null() {
            CompareResult r = strategy.compare(Map.of("a", 1), null, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getObject2()).isNull();
        }

        @Test
        @DisplayName("双空 Map → identical")
        void bothEmpty() {
            CompareResult r = strategy.compare(Collections.emptyMap(), Collections.emptyMap(), CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("相同内容 → identical")
        void sameContent() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 1, "b", 2);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("不同 key → 有变更")
        void differentKeys() {
            Map<String, Integer> m1 = Map.of("a", 1);
            Map<String, Integer> m2 = Map.of("b", 1);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("不同 value → 有变更")
        void differentValues() {
            Map<String, Integer> m1 = Map.of("a", 1);
            Map<String, Integer> m2 = Map.of("a", 2);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).anyMatch(c -> "a".equals(c.getFieldName()));
        }

        @Test
        @DisplayName("Map 中 null 值")
        void nullValuesInMap() {
            Map<String, Object> m1 = new HashMap<>();
            m1.put("a", null);
            m1.put("b", 1);
            Map<String, Object> m2 = new HashMap<>();
            m2.put("a", "new");
            m2.put("b", 1);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> "a".equals(c.getFieldName()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CompareOptions — calculateSimilarity、generateReport
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CompareOptions — 相似度与报告")
    class CompareOptionsTests {

        @Test
        @DisplayName("calculateSimilarity=true → 空 Map 相似度 1.0")
        void similarityEmptyMaps() {
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(Collections.emptyMap(), Collections.emptyMap(), opts);
            assertThat(r.getSimilarity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("calculateSimilarity=true → 部分相同")
        void similarityPartialSame() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 1, "b", 3);
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r.getSimilarity()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("generateReport=true + MARKDOWN")
        void reportMarkdown() {
            Map<String, Integer> m1 = Map.of("x", 1);
            Map<String, Integer> m2 = Map.of("x", 2);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r.getReport()).contains("## Map Comparison").contains("| Key |");
        }

        @Test
        @DisplayName("generateReport=true + TEXT")
        void reportText() {
            Map<String, Integer> m1 = Map.of("x", 1);
            Map<String, Integer> m2 = Map.of("x", 2);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.TEXT)
                .build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r.getReport()).contains("Map Comparison");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  generateDetailedChangeRecords
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateDetailedChangeRecords")
    class GenerateDetailedChangeRecordsTests {

        @Test
        @DisplayName("双 null → 空列表")
        void bothNull() {
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "f", null, null, null, null);
            assertThat(recs).isEmpty();
        }

        @Test
        @DisplayName("old null、new 非空 → CREATE")
        void oldNullNewNonEmpty() {
            Map<String, Integer> newVal = Map.of("k", 1);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "field", null, newVal, "s1", "t1");
            assertThat(recs).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("old 非空、new null → DELETE")
        void oldNonEmptyNewNull() {
            Map<String, Integer> oldVal = Map.of("k", 1);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "field", oldVal, null, "s1", "t1");
            assertThat(recs).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("键值变更 → UPDATE")
        void keyValueUpdate() {
            Map<String, Integer> oldVal = Map.of("k", 1);
            Map<String, Integer> newVal = Map.of("k", 2);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "field", oldVal, newVal, null, null);
            assertThat(recs).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("valueKind 为 MAP_ENTRY")
        void valueKindMapEntry() {
            Map<String, Integer> oldVal = Map.of("k", 1);
            Map<String, Integer> newVal = Map.of("k", 2);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "field", oldVal, newVal, null, null);
            assertThat(recs).anyMatch(c -> "MAP_ENTRY".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("fieldName 包含键路径")
        void fieldNameWithKey() {
            Map<String, Integer> oldVal = Map.of("a", 1);
            Map<String, Integer> newVal = Map.of("a", 2);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords("Obj", "items", oldVal, newVal, null, null);
            assertThat(recs).anyMatch(c -> c.getFieldName().startsWith("items."));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  键重命名检测
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("键重命名检测")
    class KeyRenameTests {

        @Test
        @DisplayName("相似键重命名 → MOVE 类型")
        void keyRenameSimilar() {
            Map<String, String> m1 = Map.of("userName", "alice");
            Map<String, String> m2 = Map.of("user_name", "alice");
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.MOVE);
        }

        @Test
        @DisplayName("值不同 → 不视为重命名")
        void keyRenameValueDifferent() {
            Map<String, String> m1 = Map.of("oldKey", "v1");
            Map<String, String> m2 = Map.of("newKey", "v2");
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Entity key / value
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Entity key / value 深度对比")
    class EntityKeyValueTests {

        @Test
        @DisplayName("Map value 为 Entity → 深度对比")
        void mapValueEntity() {
            EntityItem item1 = new EntityItem(1L, "A");
            EntityItem item2 = new EntityItem(1L, "B");
            Map<String, EntityItem> m1 = Map.of("i", item1);
            Map<String, EntityItem> m2 = Map.of("i", item2);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("Map key 为 Entity → 使用 @Key 匹配")
        void mapKeyEntity() {
            EntityKey key1 = new EntityKey(100L);
            EntityKey key2 = new EntityKey(100L);
            Map<EntityKey, String> m1 = new HashMap<>();
            m1.put(key1, "v1");
            Map<EntityKey, String> m2 = new HashMap<>();
            m2.put(key2, "v2");
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Entity value 一侧 null → CREATE/DELETE")
        void entityValueOneNull() {
            EntityItem item = new EntityItem(1L, "A");
            Map<String, EntityItem> m1 = new HashMap<>();
            m1.put("i", null);
            Map<String, EntityItem> m2 = new HashMap<>();
            m2.put("i", item);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("trackEntityKeyAttributes=true → 比较 key 非 @Key 属性")
        void trackEntityKeyAttributes() {
            EntityKeyWithAttr k1 = new EntityKeyWithAttr(100L, "desc1");
            EntityKeyWithAttr k2 = new EntityKeyWithAttr(100L, "desc2");
            Map<EntityKeyWithAttr, String> m1 = new HashMap<>();
            m1.put(k1, "v");
            Map<EntityKeyWithAttr, String> m2 = new HashMap<>();
            m2.put(k2, "v");
            CompareOptions opts = CompareOptions.builder().trackEntityKeyAttributes(true).build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  supports / getName
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("supports 与 getName")
    class SupportsAndNameTests {

        @Test
        @DisplayName("supports Map")
        void supportsMap() {
            assertThat(strategy.supports(Map.class)).isTrue();
        }

        @Test
        @DisplayName("supports HashMap")
        void supportsHashMap() {
            assertThat(strategy.supports(HashMap.class)).isTrue();
        }

        @Test
        @DisplayName("getName")
        void getName() {
            assertThat(strategy.getName()).isEqualTo("MapCompare");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  嵌套 Map 与深层对象
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("嵌套 Map")
    class NestedMapTests {

        @Test
        @DisplayName("嵌套 Map 相同内容")
        void nestedMapIdentical() {
            Map<String, Object> inner = Map.of("k", 1);
            Map<String, Object> m1 = Map.of("outer", inner);
            Map<String, Object> m2 = Map.of("outer", Map.of("k", 1));
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("嵌套 Map 不同内容")
        void nestedMapDifferent() {
            Map<String, Object> m1 = Map.of("outer", Map.of("k", 1));
            Map<String, Object> m2 = Map.of("outer", Map.of("k", 2));
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
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
}
