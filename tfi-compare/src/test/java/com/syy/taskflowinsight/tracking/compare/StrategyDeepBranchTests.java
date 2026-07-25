package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Surgical branch coverage tests for MapCompareStrategy, SetCompareStrategy,
 * CompareEngine, MapCompareStrategy, and SetCompareStrategy.
 *
 * @since 3.0.0
 */
@DisplayName("Strategy Deep Branch Tests")
class StrategyDeepBranchTests {

    // ── MapCompareStrategy ──

    @Nested
    @DisplayName("MapCompareStrategy — compare branches")
    class MapCompareStrategyBranches {

        private final MapCompareStrategy strategy = new MapCompareStrategy();

        @Test
        @DisplayName("same reference returns identical")
        void sameReference() {
            Map<String, Integer> m = new HashMap<>(Map.of("a", 1));
            CompareResult r = strategy.compare(m, m, CompareOptions.builder().build());
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("map1 null returns null diff")
        void map1Null() {
            CompareResult r = strategy.compare(null, Map.of("a", 1), CompareOptions.builder().build());
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("map2 null returns null diff")
        void map2Null() {
            CompareResult r = strategy.compare(Map.of("a", 1), null, CompareOptions.builder().build());
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("same maps identical")
        void sameMaps() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 1, "b", 2);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("different maps with changes")
        void differentMaps() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 2, "b", 2, "c", 3);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r.isIdentical()).isFalse();
            assertThat(r.getChanges()).isNotEmpty();
        }

        @Test
        @DisplayName("maps with null values")
        void mapsWithNullValues() {
            Map<String, Object> m1 = new HashMap<>();
            m1.put("a", null);
            m1.put("b", 1);
            Map<String, Object> m2 = new HashMap<>();
            m2.put("a", "new");
            m2.put("b", 1);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r.getChanges()).anyMatch(c -> CanonicalChangeTestSupport.hasExactMapKey(c, "a"));
        }

        @Test
        @DisplayName("with calculateSimilarity")
        void withSimilarity() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 1, "b", 3);
            CompareOptions opts = CompareOptions.builder().computeSimilarity(true).build();
            CompareResult r = strategy.compare(m1, m2, opts);
        }

        @Test
        @DisplayName("with generateReport MARKDOWN")
        void withReportMarkdown() {
            Map<String, Integer> m1 = Map.of("a", 1);
            Map<String, Integer> m2 = Map.of("a", 2);
            CompareOptions opts = CompareOptions.builder()
                
                
                .build();
            CompareResult r = strategy.compare(m1, m2, opts);
        }

        @Test
        @DisplayName("with generateReport TEXT")
        void withReportText() {
            Map<String, Integer> m1 = Map.of("a", 1);
            Map<String, Integer> m2 = Map.of("a", 2);
            CompareOptions opts = CompareOptions.builder()
                
                
                .build();
            CompareResult r = strategy.compare(m1, m2, opts);
        }

        @Test
        @DisplayName("empty maps identical")
        void emptyMaps() {
            Map<String, Integer> m1 = Collections.emptyMap();
            Map<String, Integer> m2 = Collections.emptyMap();
            CompareResult r = strategy.compare(m1, m2, CompareOptions.builder().build());
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("generateDetailedChangeRecords both null")
        void generateDetailedBothNull() {
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords(
                "obj", "field", null, null, "s1", "t1");
            assertThat(recs).isEmpty();
        }

        @Test
        @DisplayName("generateDetailedChangeRecords old null new non-null")
        void generateDetailedOldNull() {
            Map<String, Integer> m2 = Map.of("a", 1);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords(
                "obj", "field", null, m2, "s1", "t1");
            assertThat(recs).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("generateDetailedChangeRecords old non-null new null")
        void generateDetailedNewNull() {
            Map<String, Integer> m1 = Map.of("a", 1);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords(
                "obj", "field", m1, null, "s1", "t1");
            assertThat(recs).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("generateDetailedChangeRecords UPDATE")
        void generateDetailedUpdate() {
            Map<String, Integer> m1 = Map.of("a", 1);
            Map<String, Integer> m2 = Map.of("a", 2);
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords(
                "obj", "field", m1, m2, "s1", "t1");
            assertThat(recs).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("supports Map")
        void supports() {
            assertThat(strategy.supports(Map.class)).isTrue();
            assertThat(strategy.supports(HashMap.class)).isTrue();
        }

        @Test
        @DisplayName("getName")
        void getName() {
            assertThat(strategy.getName()).isEqualTo("MapCompare");
        }
    }

    // ── SetCompareStrategy ──

    @Nested
    @DisplayName("SetCompareStrategy — compare branches")
    class SetCompareStrategyBranches {

        private final SetCompareStrategy strategy = new SetCompareStrategy();

        @Test
        @DisplayName("same reference returns identical")
        void sameReference() {
            Set<String> s = new HashSet<>(Set.of("a", "b"));
            CompareResult r = strategy.compare(s, s, CompareOptions.builder().build());
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("set1 null")
        void set1Null() {
            CompareResult r = strategy.compare(null, Set.of("a"), CompareOptions.builder().build());
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("set2 null")
        void set2Null() {
            CompareResult r = strategy.compare(Set.of("a"), null, CompareOptions.builder().build());
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("empty sets identical")
        void emptySets() {
            CompareResult r = strategy.compare(
                Collections.emptySet(),
                Collections.emptySet(),
                CompareOptions.builder().build());
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("added elements")
        void addedElements() {
            Set<String> s1 = Set.of("a", "b");
            Set<String> s2 = Set.of("a", "b", "c");
            CompareResult r = strategy.compare(s1, s2, CompareOptions.builder().build());
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("removed elements")
        void removedElements() {
            Set<String> s1 = Set.of("a", "b", "c");
            Set<String> s2 = Set.of("a", "b");
            CompareResult r = strategy.compare(s1, s2, CompareOptions.builder().build());
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("with similarity")
        void withSimilarity() {
            Set<String> s1 = Set.of("a", "b");
            Set<String> s2 = Set.of("a", "c");
            CompareOptions opts = CompareOptions.builder().computeSimilarity(true).build();
            CompareResult r = strategy.compare(s1, s2, opts);
        }

        @Test
        @DisplayName("with report MARKDOWN")
        void withReportMarkdown() {
            Set<String> s1 = Set.of("a");
            Set<String> s2 = Set.of("b");
            CompareOptions opts = CompareOptions.builder()
                
                
                .build();
            CompareResult r = strategy.compare(s1, s2, opts);
        }

        @Test
        @DisplayName("generateDetailedChangeRecords both null")
        void generateDetailedBothNull() {
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords(
                "obj", "field", null, null, "s1", "t1");
            assertThat(recs).isEmpty();
        }

        @Test
        @DisplayName("generateDetailedChangeRecords added")
        void generateDetailedAdded() {
            Set<String> s1 = Collections.emptySet();
            Set<String> s2 = Set.of("x");
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords(
                "obj", "field", s1, s2, "s1", "t1");
            assertThat(recs).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("generateDetailedChangeRecords removed")
        void generateDetailedRemoved() {
            Set<String> s1 = Set.of("x");
            Set<String> s2 = Collections.emptySet();
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords(
                "obj", "field", s1, s2, "s1", "t1");
            assertThat(recs).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("supports Set")
        void supports() {
            assertThat(strategy.supports(Set.class)).isTrue();
            assertThat(strategy.supports(HashSet.class)).isTrue();
        }
    }

    // ── SetCompareStrategy Entity Set ──

    @Nested
    @DisplayName("SetCompareStrategy — Entity Set")
    class SetCompareStrategyEntitySet {

        private final SetCompareStrategy strategy = new SetCompareStrategy();

        @Test
        @DisplayName("Entity Set compare")
        void entitySetCompare() {
            Set<TestEntity> s1 = Set.of(new TestEntity(1, "A"));
            Set<TestEntity> s2 = Set.of(new TestEntity(1, "B"));
            CompareResult r = strategy.compare(s1, s2, CompareOptions.builder().build());
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Entity Set generateDetailedChangeRecords")
        void entitySetGenerateDetailed() {
            Set<TestEntity> s1 = Set.of(new TestEntity(1, "A"));
            Set<TestEntity> s2 = Set.of(new TestEntity(1, "B"));
            List<ChangeRecord> recs = strategy.generateDetailedChangeRecords(
                "obj", "field", s1, s2, "s1", "t1");
            assertThat(recs).isNotNull();
        }
    }

    // ── CompareEngine ──

    @Nested
    @DisplayName("CompareEngine — branches")
    class CompareEngineBranches {

        private CompareService service;

        @BeforeEach
        void setUp() {
            service = new CompareService();
        }

        @Test
        @DisplayName("same reference")
        void sameReference() {
            Map<String, Integer> m = Map.of("a", 1);
            CompareResult r = service.compare(m, m);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("null first")
        void nullFirst() {
            CompareResult r = service.compare(null, Map.of("a", 1));
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("null second")
        void nullSecond() {
            CompareResult r = service.compare(Map.of("a", 1), null);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("type mismatch")
        void typeMismatch() {
            CompareResult r = service.compare("a", 1);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("List routing")
        void listRouting() {
            List<String> a = List.of("a", "b");
            List<String> b = List.of("a", "c");
            CompareResult r = service.compare(a, b);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Map strategy")
        void mapStrategy() {
            Map<String, Integer> m1 = Map.of("a", 1);
            Map<String, Integer> m2 = Map.of("a", 2);
            CompareResult r = service.compare(m1, m2);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("Set strategy")
        void setStrategy() {
            Set<String> s1 = Set.of("a", "b");
            Set<String> s2 = Set.of("a", "c");
            CompareResult r = service.compare(s1, s2);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("deep fallback POJO")
        void deepFallback() {
            CompareOptions opts = CompareOptions.builder().build();
            CompareResult r = service.compare(
                new SimplePojo("a"),
                new SimplePojo("b"),
                opts);
            assertThat(r).isNotNull();
        }
    }

    @Entity
    static class TestEntity {
        @Key
        private final int id;
        @SuppressWarnings("unused")
        private String name;

        TestEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestEntity that = (TestEntity) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    static class SimplePojo {
        private String value;

        SimplePojo(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
