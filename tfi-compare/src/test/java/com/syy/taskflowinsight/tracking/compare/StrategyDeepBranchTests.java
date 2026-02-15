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
 * EnhancedDateCompareStrategy, and CompareEngine.
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
            CompareResult r = strategy.compare(m, m, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("map1 null returns null diff")
        void map1Null() {
            CompareResult r = strategy.compare(null, Map.of("a", 1), CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("map2 null returns null diff")
        void map2Null() {
            CompareResult r = strategy.compare(Map.of("a", 1), null, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("same maps identical")
        void sameMaps() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 1, "b", 2);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("different maps with changes")
        void differentMaps() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 2, "b", 2, "c", 3);
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
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
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> "a".equals(c.getFieldName()));
        }

        @Test
        @DisplayName("with calculateSimilarity")
        void withSimilarity() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 1, "b", 3);
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r.getSimilarity()).isNotNull();
            assertThat(r.getSimilarity()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("with generateReport MARKDOWN")
        void withReportMarkdown() {
            Map<String, Integer> m1 = Map.of("a", 1);
            Map<String, Integer> m2 = Map.of("a", 2);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r.getReport()).contains("Map Comparison").contains("| Key |");
        }

        @Test
        @DisplayName("with generateReport TEXT")
        void withReportText() {
            Map<String, Integer> m1 = Map.of("a", 1);
            Map<String, Integer> m2 = Map.of("a", 2);
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.TEXT)
                .build();
            CompareResult r = strategy.compare(m1, m2, opts);
            assertThat(r.getReport()).contains("Map Comparison");
        }

        @Test
        @DisplayName("empty maps identical")
        void emptyMaps() {
            Map<String, Integer> m1 = Collections.emptyMap();
            Map<String, Integer> m2 = Collections.emptyMap();
            CompareResult r = strategy.compare(m1, m2, CompareOptions.DEFAULT);
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
            CompareResult r = strategy.compare(s, s, CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("set1 null")
        void set1Null() {
            CompareResult r = strategy.compare(null, Set.of("a"), CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
            assertThat(r.isIdentical()).isFalse();
        }

        @Test
        @DisplayName("set2 null")
        void set2Null() {
            CompareResult r = strategy.compare(Set.of("a"), null, CompareOptions.DEFAULT);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("empty sets identical")
        void emptySets() {
            CompareResult r = strategy.compare(
                Collections.emptySet(),
                Collections.emptySet(),
                CompareOptions.DEFAULT);
            assertThat(r.isIdentical()).isTrue();
        }

        @Test
        @DisplayName("added elements")
        void addedElements() {
            Set<String> s1 = Set.of("a", "b");
            Set<String> s2 = Set.of("a", "b", "c");
            CompareResult r = strategy.compare(s1, s2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("removed elements")
        void removedElements() {
            Set<String> s1 = Set.of("a", "b", "c");
            Set<String> s2 = Set.of("a", "b");
            CompareResult r = strategy.compare(s1, s2, CompareOptions.DEFAULT);
            assertThat(r.getChanges()).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("with similarity")
        void withSimilarity() {
            Set<String> s1 = Set.of("a", "b");
            Set<String> s2 = Set.of("a", "c");
            CompareOptions opts = CompareOptions.builder().calculateSimilarity(true).build();
            CompareResult r = strategy.compare(s1, s2, opts);
            assertThat(r.getSimilarity()).isNotNull();
        }

        @Test
        @DisplayName("with report MARKDOWN")
        void withReportMarkdown() {
            Set<String> s1 = Set.of("a");
            Set<String> s2 = Set.of("b");
            CompareOptions opts = CompareOptions.builder()
                .generateReport(true)
                .reportFormat(ReportFormat.MARKDOWN)
                .build();
            CompareResult r = strategy.compare(s1, s2, opts);
            assertThat(r.getReport()).contains("Set Comparison");
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
            CompareResult r = strategy.compare(s1, s2, CompareOptions.DEFAULT);
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

    // ── EnhancedDateCompareStrategy ──

    @Nested
    @DisplayName("EnhancedDateCompareStrategy — branches")
    class EnhancedDateCompareStrategyBranches {

        private final EnhancedDateCompareStrategy strategy = new EnhancedDateCompareStrategy();

        @Test
        @DisplayName("compareDates both null")
        void compareDatesBothNull() {
            assertThat(strategy.compareDates(null, null)).isTrue();
        }

        @Test
        @DisplayName("compareDates one null")
        void compareDatesOneNull() {
            assertThat(strategy.compareDates(new Date(), null)).isFalse();
            assertThat(strategy.compareDates(null, new Date())).isFalse();
        }

        @Test
        @DisplayName("compareDates with tolerance")
        void compareDatesWithTolerance() {
            Date d1 = new Date(1000);
            Date d2 = new Date(1500);
            assertThat(strategy.compareDates(d1, d2, 600)).isTrue();
            assertThat(strategy.compareDates(d1, d2, 400)).isFalse();
        }

        @Test
        @DisplayName("compareInstants both null")
        void compareInstantsBothNull() {
            assertThat(strategy.compareInstants(null, null, 0)).isTrue();
        }

        @Test
        @DisplayName("compareInstants one null")
        void compareInstantsOneNull() {
            assertThat(strategy.compareInstants(Instant.EPOCH, null, 0)).isFalse();
        }

        @Test
        @DisplayName("compareLocalDateTimes tolerance 0")
        void compareLocalDateTimesToleranceZero() {
            LocalDateTime ldt = LocalDateTime.of(2025, 1, 1, 12, 0);
            assertThat(strategy.compareLocalDateTimes(ldt, ldt, 0)).isTrue();
        }

        @Test
        @DisplayName("compareLocalDates tolerance 0")
        void compareLocalDatesToleranceZero() {
            LocalDate ld = LocalDate.of(2025, 1, 1);
            assertThat(strategy.compareLocalDates(ld, ld, 0)).isTrue();
        }

        @Test
        @DisplayName("compareDurations")
        void compareDurations() {
            Duration d1 = Duration.ofSeconds(1);
            Duration d2 = Duration.ofMillis(1500);
            assertThat(strategy.compareDurations(d1, d2, 600)).isTrue();
        }

        @Test
        @DisplayName("comparePeriods")
        void comparePeriods() {
            Period p1 = Period.ofDays(1);
            Period p2 = Period.ofDays(1);
            assertThat(strategy.comparePeriods(p1, p2)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal different types")
        void compareTemporalDifferentTypes() {
            assertThat(strategy.compareTemporal(new Date(), Instant.EPOCH, 0)).isFalse();
        }

        @Test
        @DisplayName("compareTemporal Date")
        void compareTemporalDate() {
            Date d = new Date(1000);
            assertThat(strategy.compareTemporal(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Instant")
        void compareTemporalInstant() {
            Instant i = Instant.EPOCH;
            assertThat(strategy.compareTemporal(i, i, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal LocalDateTime")
        void compareTemporalLocalDateTime() {
            LocalDateTime ldt = LocalDateTime.of(2025, 1, 1, 12, 0);
            assertThat(strategy.compareTemporal(ldt, ldt, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal LocalDate")
        void compareTemporalLocalDate() {
            LocalDate ld = LocalDate.of(2025, 1, 1);
            assertThat(strategy.compareTemporal(ld, ld, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Duration")
        void compareTemporalDuration() {
            Duration d = Duration.ofSeconds(1);
            assertThat(strategy.compareTemporal(d, d, 0)).isTrue();
        }

        @Test
        @DisplayName("compareTemporal Period")
        void compareTemporalPeriod() {
            Period p = Period.ofDays(1);
            assertThat(strategy.compareTemporal(p, p, 0)).isTrue();
        }

        @Test
        @DisplayName("isTemporalType null")
        void isTemporalTypeNull() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(null)).isFalse();
        }

        @Test
        @DisplayName("isTemporalType Date")
        void isTemporalTypeDate() {
            assertThat(EnhancedDateCompareStrategy.isTemporalType(new Date())).isTrue();
        }

        @Test
        @DisplayName("needsTemporalCompare")
        void needsTemporalCompare() {
            assertThat(EnhancedDateCompareStrategy.needsTemporalCompare(new Date(), new Date())).isTrue();
            assertThat(EnhancedDateCompareStrategy.needsTemporalCompare(1, 2)).isFalse();
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
            CompareOptions opts = CompareOptions.builder().enableDeepCompare(true).build();
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
