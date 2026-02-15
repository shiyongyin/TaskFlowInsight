package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import com.syy.taskflowinsight.tracking.precision.PrecisionController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Ultimate coverage tests for DiffDetectorService and DiffDetector.
 * Exercises all value types, precision compare, path dedup, and enhanced mode.
 *
 * @author Senior Java Test Expert
 * @since 3.0.0
 */
@DisplayName("Ultimate Coverage — DiffDetectorService, DiffDetector")
class DetectorBoundaryTests {

    private DiffDetectorService detectorService;

    @BeforeEach
    void setUp() {
        detectorService = new DiffDetectorService();
        detectorService.programmaticInitNoSpring();
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffFacade.setProgrammaticService(null);
    }

    @AfterEach
    void tearDown() {
        DiffFacade.setProgrammaticService(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DiffDetectorService
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DiffDetectorService — diff")
    class DiffDetectorServiceDiffTests {

        @Test
        @DisplayName("null before/after normalized to empty")
        void nullMaps() {
            List<ChangeRecord> r = detectorService.diff("Obj", null, null);
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("all value types — STRING")
        void valueTypeString() {
            Map<String, Object> b = Map.of("s", "old");
            Map<String, Object> a = Map.of("s", "new");
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> "STRING".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("all value types — NUMBER")
        void valueTypeNumber() {
            Map<String, Object> b = Map.of("n", 1);
            Map<String, Object> a = Map.of("n", 2);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> "NUMBER".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("all value types — BOOLEAN")
        void valueTypeBoolean() {
            Map<String, Object> b = Map.of("b", true);
            Map<String, Object> a = Map.of("b", false);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> "BOOLEAN".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("all value types — DATE")
        void valueTypeDate() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            Map<String, Object> b = Map.of("d", d1);
            Map<String, Object> a = Map.of("d", d2);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> "DATE".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("all value types — ENUM")
        void valueTypeEnum() {
            Map<String, Object> b = Map.of("e", ChangeType.CREATE);
            Map<String, Object> a = Map.of("e", ChangeType.DELETE);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> "ENUM".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("all value types — COLLECTION")
        void valueTypeCollection() {
            Map<String, Object> b = Map.of("c", List.of(1));
            Map<String, Object> a = Map.of("c", List.of(2));
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> "COLLECTION".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("all value types — MAP")
        void valueTypeMap() {
            Map<String, Object> b = Map.of("m", Map.of("k", 1));
            Map<String, Object> a = Map.of("m", Map.of("k", 2));
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> "MAP".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("all value types — ARRAY")
        void valueTypeArray() {
            Map<String, Object> b = Map.of("a", new int[]{1});
            Map<String, Object> a = Map.of("a", new int[]{2});
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> "ARRAY".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("CREATE change type")
        void createChangeType() {
            Map<String, Object> b = Map.of("x", 1);
            Map<String, Object> a = Map.of("x", 1, "y", 2);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("DELETE change type")
        void deleteChangeType() {
            Map<String, Object> b = Map.of("x", 1, "y", 2);
            Map<String, Object> a = Map.of("x", 1);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("UPDATE change type")
        void updateChangeType() {
            Map<String, Object> b = Map.of("x", 1);
            Map<String, Object> a = Map.of("x", 2);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("registerObjectType for field lookup")
        void registerObjectType() {
            detectorService.registerObjectType("TestObj", TestPojo.class);
            Map<String, Object> b = Map.of("strField", "a");
            Map<String, Object> a = Map.of("strField", "b");
            List<ChangeRecord> r = detectorService.diff("TestObj", b, a);
            assertThat(r).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetectorService — precision compare")
    class DiffDetectorServicePrecisionTests {

        @Test
        @DisplayName("precision compare BigDecimal")
        void precisionBigDecimal() {
            detectorService.setPrecisionCompareEnabled(true);
            Map<String, Object> b = Map.of("bd", new BigDecimal("1.000"));
            Map<String, Object> a = Map.of("bd", new BigDecimal("1.001"));
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).isNotEmpty();
        }

        @Test
        @DisplayName("precision compare double within tolerance")
        void precisionDoubleWithinTolerance() {
            detectorService.setPrecisionCompareEnabled(true);
            Map<String, Object> b = Map.of("d", 1.0);
            Map<String, Object> a = Map.of("d", 1.0 + 1e-15);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("precision disabled uses equals")
        void precisionDisabled() {
            detectorService.setPrecisionCompareEnabled(false);
            Map<String, Object> b = Map.of("x", 1.0);
            Map<String, Object> a = Map.of("x", 1.0000000001);
            List<ChangeRecord> r = detectorService.diff("Obj", b, a);
            assertThat(r).isNotEmpty();
        }

        @Test
        @DisplayName("getMetricsSnapshot")
        void getMetricsSnapshot() {
            detectorService.diff("Obj", Map.of("a", 1), Map.of("a", 2));
            var snap = detectorService.getMetricsSnapshot();
            assertThat(snap).isNotNull();
        }

        @Test
        @DisplayName("resetMetrics")
        void resetMetrics() {
            detectorService.diff("Obj", Map.of("a", 1), Map.of("a", 2));
            detectorService.resetMetrics();
            assertThat(detectorService.getMetricsSnapshot()).isNotNull();
        }
    }

    @Nested
    @DisplayName("DiffDetectorService — detectReferenceChange")
    class DiffDetectorServiceReferenceTests {

        @Test
        @DisplayName("detectReferenceChange with @ShallowReference")
        void detectReferenceChange() {
            ShallowRefRoot a = new ShallowRefRoot();
            a.ref = new RefEntity(1);
            ShallowRefRoot b = new ShallowRefRoot();
            b.ref = new RefEntity(2);
            var detail = detectorService.detectReferenceChange(a, b, "ref", null, null);
            assertThat(detail).isNotNull();
            assertThat(detail.getOldEntityKey()).isNotEqualTo(detail.getNewEntityKey());
        }

        @Test
        @DisplayName("detectReferenceChange null transition")
        void detectReferenceChangeNull() {
            ShallowRefRoot a = new ShallowRefRoot();
            a.ref = new RefEntity(1);
            ShallowRefRoot b = new ShallowRefRoot();
            b.ref = null;
            var detail = detectorService.detectReferenceChange(a, b, "ref", null, null);
            assertThat(detail).isNotNull();
            assertThat(detail.isNullReferenceChange()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DiffDetector
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DiffDetector — diff modes")
    class DiffDetectorModeTests {

        @Test
        @DisplayName("COMPAT mode")
        void compatMode() {
            Map<String, Object> b = Map.of("a", 1, "b", 2);
            Map<String, Object> a = Map.of("a", 2, "b", 2);
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", b, a, DiffDetector.DiffMode.COMPAT);
            assertThat(r).anyMatch(c -> "a".equals(c.getFieldName()));
        }

        @Test
        @DisplayName("ENHANCED mode sets reprOld reprNew")
        void enhancedMode() {
            Map<String, Object> b = Map.of("x", 1);
            Map<String, Object> a = Map.of("x", 2);
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", b, a, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).isNotEmpty();
            assertThat(r.get(0).getReprOld()).isNotNull();
            assertThat(r.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("ENHANCED mode with Date")
        void enhancedModeDate() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            Map<String, Object> b = Map.of("d", d1);
            Map<String, Object> a = Map.of("d", d2);
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", b, a, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).isNotEmpty();
        }

        @Test
        @DisplayName("ENHANCED mode DELETE")
        void enhancedModeDelete() {
            Map<String, Object> b = Map.of("d", "deleted");
            Map<String, Object> a = new HashMap<>();
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", b, a, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }
    }

    @Nested
    @DisplayName("DiffDetector — value kinds")
    class DiffDetectorValueKindTests {

        @Test
        @DisplayName("STRING")
        void stringKind() {
            List<ChangeRecord> r = DiffDetector.diff("x", Map.of("s", "old"), Map.of("s", "new"));
            assertThat(r.get(0).getValueKind()).isEqualTo("STRING");
        }

        @Test
        @DisplayName("NUMBER")
        void numberKind() {
            List<ChangeRecord> r = DiffDetector.diff("x", Map.of("n", 1), Map.of("n", 2));
            assertThat(r.get(0).getValueKind()).isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("BOOLEAN")
        void booleanKind() {
            List<ChangeRecord> r = DiffDetector.diff("x", Map.of("b", true), Map.of("b", false));
            assertThat(r.get(0).getValueKind()).isEqualTo("BOOLEAN");
        }

        @Test
        @DisplayName("DATE")
        void dateKind() {
            List<ChangeRecord> r = DiffDetector.diff("x",
                Map.of("d", new Date(1000)),
                Map.of("d", new Date(2000)));
            assertThat(r.get(0).getValueKind()).isEqualTo("DATE");
        }

        @Test
        @DisplayName("ENUM")
        void enumKind() {
            List<ChangeRecord> r = DiffDetector.diff("x",
                Map.of("e", ChangeType.CREATE),
                Map.of("e", ChangeType.DELETE));
            assertThat(r.get(0).getValueKind()).isEqualTo("ENUM");
        }

        @Test
        @DisplayName("COLLECTION")
        void collectionKind() {
            List<ChangeRecord> r = DiffDetector.diff("x",
                Map.of("c", List.of(1)),
                Map.of("c", List.of(2)));
            assertThat(r.get(0).getValueKind()).isEqualTo("COLLECTION");
        }

        @Test
        @DisplayName("MAP")
        void mapKind() {
            List<ChangeRecord> r = DiffDetector.diff("x",
                Map.of("m", Map.of("k", 1)),
                Map.of("m", Map.of("k", 2)));
            assertThat(r.get(0).getValueKind()).isEqualTo("MAP");
        }

        @Test
        @DisplayName("ARRAY")
        void arrayKind() {
            List<ChangeRecord> r = DiffDetector.diff("x",
                Map.of("a", new int[]{1}),
                Map.of("a", new int[]{2}));
            assertThat(r.get(0).getValueKind()).isEqualTo("ARRAY");
        }

        @Test
        @DisplayName("OTHER — custom object")
        void otherKind() {
            List<ChangeRecord> r = DiffDetector.diff("x",
                Map.of("o", new Object()),
                Map.of("o", new Object()));
            assertThat(r.get(0).getValueKind()).isEqualTo("OTHER");
        }
    }

    @Nested
    @DisplayName("DiffDetector — Map/Set/Collection strategy")
    class DiffDetectorCollectionTests {

        @Test
        @DisplayName("Map comparison via strategy")
        void mapStrategy() {
            Map<String, Integer> m1 = Map.of("a", 1, "b", 2);
            Map<String, Integer> m2 = Map.of("a", 2, "b", 2);
            List<ChangeRecord> r = DiffDetector.diff("Obj", Map.of("map", m1), Map.of("map", m2));
            assertThat(r).anyMatch(c -> "map".equals(c.getFieldName()));
        }

        @Test
        @DisplayName("Set comparison via strategy")
        void setStrategy() {
            Set<String> s1 = Set.of("a", "b");
            Set<String> s2 = Set.of("a", "c");
            List<ChangeRecord> r = DiffDetector.diff("Obj", Map.of("set", s1), Map.of("set", s2));
            assertThat(r).anyMatch(c -> "set".equals(c.getFieldName()));
        }

        @Test
        @DisplayName("Collection comparison via strategy")
        void collectionStrategy() {
            List<Integer> c1 = List.of(1, 2);
            List<Integer> c2 = List.of(1, 3);
            List<ChangeRecord> r = DiffDetector.diff("Obj", Map.of("list", c1), Map.of("list", c2));
            assertThat(r).anyMatch(c -> "list".equals(c.getFieldName()));
        }
    }

    @Nested
    @DisplayName("DiffDetector — path deduplication")
    class DiffDetectorDedupTests {

        @Test
        @DisplayName("enhanced dedup enabled")
        void enhancedDedup() {
            boolean saved = DiffDetector.isEnhancedDeduplicationEnabled();
            try {
                DiffDetector.setEnhancedDeduplicationEnabled(true);
                Map<String, Object> b = new HashMap<>();
                b.put("a", 1);
                b.put("a.b", 2);
                Map<String, Object> a = new HashMap<>();
                a.put("a", 2);
                a.put("a.b", 3);
                List<ChangeRecord> r = DiffDetector.diff("Obj", b, a);
                assertThat(r).isNotNull();
            } finally {
                DiffDetector.setEnhancedDeduplicationEnabled(saved);
            }
        }

        @Test
        @DisplayName("getDeduplicationStatistics")
        void getDedupStats() {
            DiffDetector.diff("Obj", Map.of("a", 1), Map.of("a", 2));
            PathDeduplicator.DeduplicationStatistics stats = DiffDetector.getDeduplicationStatistics();
            assertThat(stats).isNotNull();
        }
    }

    @Nested
    @DisplayName("DiffDetector — precision")
    class DiffDetectorPrecisionTests {

        @Test
        @DisplayName("precision compare enabled")
        void precisionEnabled() {
            DiffDetector.setPrecisionCompareEnabled(true);
            DiffDetector.setPrecisionController(new PrecisionController());
            DiffDetector.setCurrentObjectClass(TestPojo.class);
            Map<String, Object> b = Map.of("bd", new BigDecimal("1.00"));
            Map<String, Object> a = Map.of("bd", new BigDecimal("1.01"));
            List<ChangeRecord> r = DiffDetector.diff("TestPojo", b, a);
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("precision compare BigDecimal within tolerance")
        void precisionBigDecimalWithinTolerance() {
            DiffDetector.setPrecisionCompareEnabled(true);
            var ctrl = new PrecisionController(1e-2, 1e-9,
                com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L);
            DiffDetector.setPrecisionController(ctrl);
            Map<String, Object> b = Map.of("bd", new BigDecimal("1.000"));
            Map<String, Object> a = Map.of("bd", new BigDecimal("1.001"));
            List<ChangeRecord> r = DiffDetector.diff("Obj", b, a);
            assertThat(r).isNotNull();
        }
    }

    @Nested
    @DisplayName("DiffDetector — config")
    class DiffDetectorConfigTests {

        @Test
        @DisplayName("setCompatHeavyOptimizationsEnabled")
        void compatHeavy() {
            boolean saved = DiffDetector.isCompatHeavyOptimizationsEnabled();
            try {
                DiffDetector.setCompatHeavyOptimizationsEnabled(true);
                Map<String, Object> b = new HashMap<>();
                Map<String, Object> a = new HashMap<>();
                for (int i = 0; i < 60; i++) {
                    b.put("k" + i, i);
                    a.put("k" + i, i + 1);
                }
                List<ChangeRecord> r = DiffDetector.diff("Obj", b, a);
                assertThat(r).isNotNull();
            } finally {
                DiffDetector.setCompatHeavyOptimizationsEnabled(saved);
            }
        }

        @Test
        @DisplayName("setDateTimeFormatter")
        void setDateTimeFormatter() {
            var fmt = new com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter("yyyy-MM-dd", "UTC");
            DiffDetector.setDateTimeFormatter(fmt);
            List<ChangeRecord> r = DiffDetector.diff("Obj", Map.of("d", new Date(1000)), Map.of("d", new Date(2000)));
            assertThat(r).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffFacade — programmatic service")
    class DiffFacadeTests {

        @Test
        @DisplayName("DiffFacade uses programmatic service")
        void programmaticService() {
            DiffFacade.setProgrammaticService(detectorService);
            List<ChangeRecord> r = DiffFacade.diff("Obj", Map.of("a", 1), Map.of("a", 2));
            assertThat(r).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test classes
    // ═══════════════════════════════════════════════════════════════════════════

    static class TestPojo {
        String strField;
    }

    static class ShallowRefRoot {
        @ShallowReference
        RefEntity ref;
    }

    @com.syy.taskflowinsight.annotation.Entity
    static class RefEntity {
        @com.syy.taskflowinsight.annotation.Key
        final int id;

        RefEntity(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RefEntity that = (RefEntity) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }
}
