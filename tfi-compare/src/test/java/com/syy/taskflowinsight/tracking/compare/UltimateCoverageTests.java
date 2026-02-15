package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Ultimate coverage tests for MapCompareStrategy, SetCompareStrategy, CompareEngine.
 * Exercises private methods through public APIs to maximize instruction coverage.
 *
 * @author Senior Java Test Expert
 * @since 3.0.0
 */
@DisplayName("Ultimate Coverage — MapCompareStrategy, SetCompareStrategy, CompareEngine")
class UltimateCoverageTests {

    private MapCompareStrategy mapStrategy;
    private SetCompareStrategy setStrategy;
    private CompareService compareService;

    @BeforeEach
    void setUp() {
        mapStrategy = new MapCompareStrategy();
        setStrategy = new SetCompareStrategy();
        compareService = new CompareService();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MapCompareStrategy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MapCompareStrategy — compare")
    class MapCompareTests {

        @Test
        @DisplayName("same reference returns identical")
        void sameRef() {
            Map<String, Integer> m = new HashMap<>(Map.of("a", 1));
            CompareResult r = mapStrategy.compare(m, m, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("null diff")
        void nullDiff() {
            CompareResult r = mapStrategy.compare(null, Map.of("a", 1), CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getSimilarity()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("both null")
        void bothNull() {
            CompareResult r = mapStrategy.compare(null, null, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.getObject1()).isNull();
            assertThat(r.getObject2()).isNull();
        }

        @Test
        @DisplayName("calculateSimilarity=true")
        void calculateSimilarity() {
            Map<String, Integer> m1 = new HashMap<>();
            for (int i = 0; i < 15; i++) m1.put("k" + i, i);
            Map<String, Integer> m2 = new HashMap<>(m1);
            m2.put("k5", 99);
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = mapStrategy.compare(m1, m2, opts);
            assertThat(r.getSimilarity()).isNotNull();
            assertThat(r.getSimilarity()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("generateReport=true MARKDOWN")
        void generateReportMarkdown() {
            Map<String, Object> m1 = Map.of("a", 1, "b", 2);
            Map<String, Object> m2 = Map.of("a", 2, "b", 2);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = mapStrategy.compare(m1, m2, opts);
            assertThat(r.getReport()).contains("## Map Comparison");
            assertThat(r.getReport()).contains("| Key |");
        }

        @Test
        @DisplayName("generateReport=true TEXT")
        void generateReportText() {
            Map<String, Object> m1 = Map.of("x", "old");
            Map<String, Object> m2 = Map.of("x", "new");
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.TEXT)
                .build();
            CompareResult r = mapStrategy.compare(m1, m2, opts);
            assertThat(r.getReport()).contains("Map Comparison:");
        }

        @Test
        @DisplayName("rename detection — same value different key")
        void renameDetection() {
            Map<String, String> m1 = new LinkedHashMap<>();
            m1.put("userName", "john");
            m1.put("userEmail", "john@example.com");
            Map<String, String> m2 = new LinkedHashMap<>();
            m2.put("user_name", "john");
            m2.put("user_email", "john@example.com");
            CompareOptions opts = CompareOptions.DEFAULT;
            CompareResult r = mapStrategy.compare(m1, m2, opts);
            assertThat(r.getChanges()).isNotEmpty();
            assertThat(r.getChanges()).anyMatch(fc -> fc.getChangeType() == ChangeType.MOVE);
        }

        @Test
        @DisplayName("simple mode — many entries different types")
        void simpleModeManyEntries() {
            Map<String, Object> m1 = new HashMap<>();
            m1.put("str", "a");
            m1.put("int", 1);
            m1.put("long", 2L);
            m1.put("double", 3.0);
            m1.put("bool", true);
            m1.put("list", List.of(1, 2));
            m1.put("map", Map.of("nested", 1));
            m1.put("nullVal", null);
            Map<String, Object> m2 = new HashMap<>(m1);
            m2.put("str", "b");
            m2.put("int", 10);
            m2.put("nullVal", "nowSet");
            CompareResult r = mapStrategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("empty maps — similarity 1.0")
        void emptyMaps() {
            Map<String, Object> m1 = Collections.emptyMap();
            Map<String, Object> m2 = Collections.emptyMap();
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = mapStrategy.compare(m1, m2, opts);
            assertThat(r.getSimilarity()).isEqualTo(1.0);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("Entity key map — processChangesWithEntityKeys")
        void entityKeyMap() {
            EntityKey k1 = new EntityKey(1, "A");
            EntityKey k2 = new EntityKey(1, "A");
            Map<EntityKey, String> m1 = new HashMap<>();
            m1.put(k1, "v1");
            Map<EntityKey, String> m2 = new HashMap<>();
            m2.put(k2, "v2");
            CompareResult r = mapStrategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("Entity key map with trackEntityKeyAttributes")
        void entityKeyMapTrackAttributes() {
            EntityKeyWithAttr ka = new EntityKeyWithAttr(1, "oldName");
            EntityKeyWithAttr kb = new EntityKeyWithAttr(1, "newName");
            Map<EntityKeyWithAttr, String> m1 = new HashMap<>();
            m1.put(ka, "val");
            Map<EntityKeyWithAttr, String> m2 = new HashMap<>();
            m2.put(kb, "val");
            CompareOptions opts = CompareOptions.builder().trackEntityKeyAttributes(true).build();
            CompareResult r = mapStrategy.compare(m1, m2, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Entity value map — deep compare")
        void entityValueMap() {
            Map<String, EntityVal> m1 = new HashMap<>();
            m1.put("e1", new EntityVal(1, "a"));
            Map<String, EntityVal> m2 = new HashMap<>();
            m2.put("e1", new EntityVal(1, "b"));
            CompareResult r = mapStrategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("Entity value CREATE/DELETE")
        void entityValueCreateDelete() {
            Map<String, EntityVal> m1 = new HashMap<>();
            m1.put("e1", new EntityVal(1, "a"));
            Map<String, EntityVal> m2 = new HashMap<>();
            CompareResult r = mapStrategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(fc -> fc.getChangeType() == ChangeType.DELETE);
        }
    }

    @Nested
    @DisplayName("MapCompareStrategy — generateDetailedChangeRecords")
    class MapGenerateDetailedChangeRecordsTests {

        @Test
        @DisplayName("both null returns empty")
        void bothNull() {
            List<ChangeRecord> recs = mapStrategy.generateDetailedChangeRecords(
                "obj", "field", null, null, "s1", "t1");
            assertThat(recs).isEmpty();
        }

        @Test
        @DisplayName("CREATE scenario")
        void create() {
            Map<String, Object> oldMap = Map.of("a", 1);
            Map<String, Object> newMap = Map.of("a", 1, "b", 2);
            List<ChangeRecord> recs = mapStrategy.generateDetailedChangeRecords(
                "obj", "field", oldMap, newMap, "s1", "t1");
            assertThat(recs).anyMatch(r -> r.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("DELETE scenario")
        void delete() {
            Map<String, Object> oldMap = Map.of("a", 1, "b", 2);
            Map<String, Object> newMap = Map.of("a", 1);
            List<ChangeRecord> recs = mapStrategy.generateDetailedChangeRecords(
                "obj", "field", oldMap, newMap, "s1", "t1");
            assertThat(recs).anyMatch(r -> r.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("UPDATE scenario")
        void update() {
            Map<String, Object> oldMap = Map.of("a", 1);
            Map<String, Object> newMap = Map.of("a", 2);
            List<ChangeRecord> recs = mapStrategy.generateDetailedChangeRecords(
                "obj", "field", oldMap, newMap, "s1", "t1");
            assertThat(recs).anyMatch(r -> r.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("old null new non-empty")
        void oldNullNewNonEmpty() {
            List<ChangeRecord> recs = mapStrategy.generateDetailedChangeRecords(
                "obj", "field", null, Map.of("k", 1), "s1", "t1");
            assertThat(recs).anyMatch(r -> r.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("old non-empty new null")
        void oldNonEmptyNewNull() {
            List<ChangeRecord> recs = mapStrategy.generateDetailedChangeRecords(
                "obj", "field", Map.of("k", 1), null, "s1", "t1");
            assertThat(recs).anyMatch(r -> r.getChangeType() == ChangeType.DELETE);
        }
    }

    @Nested
    @DisplayName("MapCompareStrategy — getName and supports")
    class MapStrategyMetaTests {

        @Test
        void getName() {
            assertThat(mapStrategy.getName()).isEqualTo("MapCompare");
        }

        @Test
        void supports() {
            assertThat(mapStrategy.supports(Map.class)).isTrue();
            assertThat(mapStrategy.supports(HashMap.class)).isTrue();
            assertThat(mapStrategy.supports(String.class)).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SetCompareStrategy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SetCompareStrategy — compare")
    class SetCompareTests {

        @Test
        @DisplayName("same reference")
        void sameRef() {
            Set<String> s = new HashSet<>(Set.of("a", "b"));
            CompareResult r = setStrategy.compare(s, s, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("null diff")
        void nullDiff() {
            CompareResult r = setStrategy.compare(null, Set.of("a"), CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("simple set with similarity and report")
        void simpleSetWithSimilarityAndReport() {
            Set<String> s1 = Set.of("a", "b", "c");
            Set<String> s2 = Set.of("a", "b", "d");
            CompareOptions opts = CompareOptions.builder()
                .calculateSimilarity(true)
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = setStrategy.compare(s1, s2, opts);
            assertThat(r.getSimilarity()).isNotNull();
            assertThat(r.getReport()).contains("## Set Comparison");
        }

        @Test
        @DisplayName("simple set TEXT report")
        void simpleSetTextReport() {
            Set<Integer> s1 = Set.of(1, 2);
            Set<Integer> s2 = Set.of(1, 3);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.TEXT)
                .build();
            CompareResult r = setStrategy.compare(s1, s2, opts);
            assertThat(r.getReport()).contains("Set Comparison:");
        }

        @Test
        @DisplayName("Entity set")
        void entitySet() {
            Set<EntityVal> s1 = Set.of(new EntityVal(1, "a"), new EntityVal(2, "b"));
            Set<EntityVal> s2 = Set.of(new EntityVal(1, "a"), new EntityVal(2, "c"));
            CompareResult r = setStrategy.compare(s1, s2, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Entity set with added element")
        void entitySetAdded() {
            Set<EntityVal> s1 = Set.of(new EntityVal(1, "a"));
            Set<EntityVal> s2 = Set.of(new EntityVal(1, "a"), new EntityVal(2, "b"));
            CompareResult r = setStrategy.compare(s1, s2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(fc -> fc.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("Entity set with removed element")
        void entitySetRemoved() {
            Set<EntityVal> s1 = Set.of(new EntityVal(1, "a"), new EntityVal(2, "b"));
            Set<EntityVal> s2 = Set.of(new EntityVal(1, "a"));
            CompareResult r = setStrategy.compare(s1, s2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(fc -> fc.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("empty sets similarity 1.0")
        void emptySets() {
            Set<String> s1 = Collections.emptySet();
            Set<String> s2 = Collections.emptySet();
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = setStrategy.compare(s1, s2, opts);
            assertThat(r.getSimilarity()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("SetCompareStrategy — generateDetailedChangeRecords")
    class SetGenerateDetailedChangeRecordsTests {

        @Test
        @DisplayName("both null")
        void bothNull() {
            List<ChangeRecord> recs = setStrategy.generateDetailedChangeRecords(
                "obj", "field", null, null, "s1", "t1");
            assertThat(recs).isEmpty();
        }

        @Test
        @DisplayName("simple set CREATE")
        void simpleSetCreate() {
            Set<String> oldSet = Set.of("a");
            Set<String> newSet = Set.of("a", "b");
            List<ChangeRecord> recs = setStrategy.generateDetailedChangeRecords(
                "obj", "field", oldSet, newSet, "s1", "t1");
            assertThat(recs).anyMatch(r -> r.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("simple set DELETE")
        void simpleSetDelete() {
            Set<String> oldSet = Set.of("a", "b");
            Set<String> newSet = Set.of("a");
            List<ChangeRecord> recs = setStrategy.generateDetailedChangeRecords(
                "obj", "field", oldSet, newSet, "s1", "t1");
            assertThat(recs).anyMatch(r -> r.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("Entity set detailed")
        void entitySetDetailed() {
            Set<EntityVal> s1 = Set.of(new EntityVal(1, "a"));
            Set<EntityVal> s2 = Set.of(new EntityVal(1, "b"));
            List<ChangeRecord> recs = setStrategy.generateDetailedChangeRecords(
                "obj", "field", s1, s2, "s1", "t1");
            assertThat(recs).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("SetCompareStrategy — getName and supports")
    class SetStrategyMetaTests {

        @Test
        void getName() {
            assertThat(setStrategy.getName()).isEqualTo("SetCompare");
        }

        @Test
        void supports() {
            assertThat(setStrategy.supports(Set.class)).isTrue();
            assertThat(setStrategy.supports(HashSet.class)).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CompareEngine
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CompareEngine — execute via CompareService")
    class CompareEngineTests {

        @Test
        @DisplayName("same ref quick path")
        void sameRef() {
            Object o = "x";
            CompareResult r = compareService.compare(o, o, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("null diff quick path")
        void nullDiff() {
            CompareResult r = compareService.compare("a", null, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("type diff quick path")
        void typeDiff() {
            CompareResult r = compareService.compare("str", 42, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("List routing")
        void listRouting() {
            List<String> l1 = List.of("a", "b");
            List<String> l2 = List.of("a", "c");
            CompareResult r = compareService.compare(l1, l2, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback POJO")
        void deepFallback() {
            FullAnnotatedPojo a = new FullAnnotatedPojo();
            a.str = "a";
            a.num = 1;
            FullAnnotatedPojo b = new FullAnnotatedPojo();
            b.str = "b";
            b.num = 2;
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("deep fallback with excludeFields")
        void deepFallbackExcludeFields() {
            FullAnnotatedPojo a = new FullAnnotatedPojo();
            a.str = "x";
            FullAnnotatedPojo b = new FullAnnotatedPojo();
            b.str = "y";
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .excludeFields(List.of("num"))
                .build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback with ignoreFields")
        void deepFallbackIgnoreFields() {
            FullAnnotatedPojo a = new FullAnnotatedPojo();
            a.str = "x";
            FullAnnotatedPojo b = new FullAnnotatedPojo();
            b.str = "y";
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .ignoreFields(List.of("num"))
                .build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback with includeNullChanges")
        void deepFallbackIncludeNullChanges() {
            FullAnnotatedPojo a = new FullAnnotatedPojo();
            a.str = "x";
            a.nested = null;
            FullAnnotatedPojo b = new FullAnnotatedPojo();
            b.str = "x";
            b.nested = null;
            CompareOptions opts = CompareOptions.builder()
                .enableDeepCompare(true)
                .includeNullChanges(true)
                .build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("POJO with superclass fields")
        void pojoWithSuperclass() {
            SubPojo a = new SubPojo();
            a.baseField = "base";
            a.subField = 1;
            SubPojo b = new SubPojo();
            b.baseField = "base";
            b.subField = 2;
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("object with List field")
        void objectWithList() {
            PojoWithList a = new PojoWithList();
            a.items = List.of(1, 2);
            PojoWithList b = new PojoWithList();
            b.items = List.of(1, 3);
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("object with Map field")
        void objectWithMap() {
            PojoWithMap a = new PojoWithMap();
            a.data = Map.of("k", 1);
            PojoWithMap b = new PojoWithMap();
            b.data = Map.of("k", 2);
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("object with Set field")
        void objectWithSet() {
            PojoWithSet a = new PojoWithSet();
            a.tags = Set.of("a", "b");
            PojoWithSet b = new PojoWithSet();
            b.tags = Set.of("a", "c");
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("object with array field")
        void objectWithArray() {
            PojoWithArray a = new PojoWithArray();
            a.arr = new int[]{1, 2};
            PojoWithArray b = new PojoWithArray();
            b.arr = new int[]{1, 3};
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("object with enum field")
        void objectWithEnum() {
            PojoWithEnum a = new PojoWithEnum();
            a.status = ChangeType.CREATE;
            PojoWithEnum b = new PojoWithEnum();
            b.status = ChangeType.DELETE;
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("object with boolean field")
        void objectWithBoolean() {
            PojoWithBoolean a = new PojoWithBoolean();
            a.flag = true;
            PojoWithBoolean b = new PojoWithBoolean();
            b.flag = false;
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
            CompareResult r = compareService.compare(a, b, opts);
            assertThat(r.getChanges()).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test entities and POJOs
    // ═══════════════════════════════════════════════════════════════════════════

    @Entity
    static class EntityKey {
        @Key
        final int id;
        @SuppressWarnings("unused")
        String name;

        EntityKey(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityKey that = (EntityKey) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    @Entity
    static class EntityKeyWithAttr {
        @Key
        final int id;
        String displayName;

        EntityKeyWithAttr(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityKeyWithAttr that = (EntityKeyWithAttr) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    @Entity
    static class EntityVal {
        @Key
        final int id;
        String name;

        EntityVal(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EntityVal that = (EntityVal) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    static class FullAnnotatedPojo {
        String str;
        int num;
        FullAnnotatedPojo nested;
    }

    static class BasePojo {
        String baseField;
    }

    static class SubPojo extends BasePojo {
        int subField;
    }

    static class PojoWithList {
        List<Integer> items;
    }

    static class PojoWithMap {
        Map<String, Integer> data;
    }

    static class PojoWithSet {
        Set<String> tags;
    }

    static class PojoWithArray {
        int[] arr;
    }

    static class PojoWithEnum {
        ChangeType status;
    }

    static class PojoWithBoolean {
        boolean flag;
    }
}
