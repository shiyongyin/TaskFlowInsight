package com.syy.taskflowinsight.tracking.detector;

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
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Branch coverage tests for tracking/detector package.
 * Targets DiffDetector COMPAT/ENHANCED modes, value kinds, heavy mode,
 * path deduplication, precision compare, and ChangeRecordComparator.
 *
 * @since 3.0.0
 */
@DisplayName("Detector Branch Coverage Tests")
class DetectorBranchCoverageTests {

    @BeforeEach
    void setUp() {
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffFacade.setProgrammaticService(null);
    }

    @AfterEach
    void tearDown() {
        DiffFacade.setProgrammaticService(null);
    }

    // ── DiffDetector modes ──

    @Nested
    @DisplayName("DiffDetector — COMPAT vs ENHANCED")
    class DiffDetectorModes {

        @Test
        @DisplayName("COMPAT mode")
        void compatMode() {
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 2, "b", 2);
            List<ChangeRecord> changes = DiffDetector.diffWithMode(
                "Obj", before, after, DiffDetector.DiffMode.COMPAT);
            assertThat(changes).anyMatch(c -> "a".equals(c.getFieldName()));
        }

        @Test
        @DisplayName("ENHANCED mode sets reprOld reprNew")
        void enhancedMode() {
            Map<String, Object> before = Map.of("x", 1);
            Map<String, Object> after = Map.of("x", 2);
            List<ChangeRecord> changes = DiffDetector.diffWithMode(
                "Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(changes).isNotEmpty();
            assertThat(changes.get(0).getReprOld()).isNotNull();
            assertThat(changes.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("ENHANCED mode DELETE valueRepr from oldValue")
        void enhancedDelete() {
            Map<String, Object> before = Map.of("d", "deleted");
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> changes = DiffDetector.diffWithMode(
                "Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }
    }

    @Nested
    @DisplayName("DiffDetector — value kinds")
    class DiffDetectorValueKinds {

        @Test
        @DisplayName("STRING")
        void valueKindString() {
            Map<String, Object> before = Map.of("s", "old");
            Map<String, Object> after = Map.of("s", "new");
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("STRING");
        }

        @Test
        @DisplayName("NUMBER")
        void valueKindNumber() {
            Map<String, Object> before = Map.of("n", 1);
            Map<String, Object> after = Map.of("n", 2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("BOOLEAN")
        void valueKindBoolean() {
            Map<String, Object> before = Map.of("b", true);
            Map<String, Object> after = Map.of("b", false);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("BOOLEAN");
        }

        @Test
        @DisplayName("DATE")
        void valueKindDate() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("DATE");
        }

        @Test
        @DisplayName("ENUM")
        void valueKindEnum() {
            Map<String, Object> before = Map.of("e", ChangeType.CREATE);
            Map<String, Object> after = Map.of("e", ChangeType.DELETE);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("ENUM");
        }

        @Test
        @DisplayName("COLLECTION")
        void valueKindCollection() {
            Map<String, Object> before = Map.of("c", List.of(1));
            Map<String, Object> after = Map.of("c", List.of(2));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("COLLECTION");
        }

        @Test
        @DisplayName("MAP")
        void valueKindMap() {
            Map<String, Object> before = Map.of("m", Map.of("k", 1));
            Map<String, Object> after = Map.of("m", Map.of("k", 2));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("MAP");
        }

        @Test
        @DisplayName("ARRAY")
        void valueKindArray() {
            Map<String, Object> before = Map.of("a", new int[]{1});
            Map<String, Object> after = Map.of("a", new int[]{2});
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("ARRAY");
        }

        @Test
        @DisplayName("CREATE type")
        void changeTypeCreate() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = Map.of("new", "value");
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("DELETE type")
        void changeTypeDelete() {
            Map<String, Object> before = Map.of("old", "value");
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("no change empty")
        void noChange() {
            Map<String, Object> m = Map.of("a", 1);
            List<ChangeRecord> changes = DiffDetector.diff("x", m, m);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("both null no change")
        void bothNullNoChange() {
            Map<String, Object> before = new HashMap<>();
            before.put("n", null);
            Map<String, Object> after = new HashMap<>();
            after.put("n", null);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — null inputs")
    class DiffDetectorNullInputs {

        @Test
        @DisplayName("null before")
        void nullBefore() {
            List<ChangeRecord> changes = DiffDetector.diff("x", null, Map.of("a", 1));
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("null after")
        void nullAfter() {
            List<ChangeRecord> changes = DiffDetector.diff("x", Map.of("a", 1), null);
            assertThat(changes).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — config")
    class DiffDetectorConfig {

        @Test
        @DisplayName("setPrecisionCompareEnabled")
        void precisionCompare() {
            DiffDetector.setPrecisionCompareEnabled(true);
            assertThat(DiffDetector.isPrecisionCompareEnabled()).isTrue();
            DiffDetector.setPrecisionCompareEnabled(false);
        }

        @Test
        @DisplayName("setPrecisionController")
        void setPrecisionController() {
            PrecisionController ctrl = new PrecisionController(
                1e-10, 1e-8,
                com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO,
                0L);
            DiffDetector.setPrecisionController(ctrl);
            DiffDetector.setPrecisionController(null);
        }

        @Test
        @DisplayName("setDateTimeFormatter")
        void setDateTimeFormatter() {
            DiffDetector.setDateTimeFormatter(
                new com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter());
            DiffDetector.setDateTimeFormatter(null);
        }

        @Test
        @DisplayName("setCurrentObjectClass")
        void setCurrentObjectClass() {
            DiffDetector.setCurrentObjectClass(String.class);
            DiffDetector.setCurrentObjectClass(null);
        }

        @Test
        @DisplayName("setEnhancedDeduplicationEnabled")
        void enhancedDedup() {
            boolean orig = DiffDetector.isEnhancedDeduplicationEnabled();
            DiffDetector.setEnhancedDeduplicationEnabled(false);
            assertThat(DiffDetector.isEnhancedDeduplicationEnabled()).isFalse();
            DiffDetector.setEnhancedDeduplicationEnabled(orig);
        }

        @Test
        @DisplayName("setCompatHeavyOptimizationsEnabled")
        void compatHeavy() {
            DiffDetector.setCompatHeavyOptimizationsEnabled(true);
            assertThat(DiffDetector.isCompatHeavyOptimizationsEnabled()).isTrue();
            DiffDetector.setCompatHeavyOptimizationsEnabled(false);
        }

        @Test
        @DisplayName("getDeduplicationStatistics")
        void getDedupStats() {
            PathDeduplicator.DeduplicationStatistics stats = DiffDetector.getDeduplicationStatistics();
            assertThat(stats).isNotNull();
        }
    }

    @Nested
    @DisplayName("DiffDetector — BigDecimal repr")
    class DiffDetectorBigDecimal {

        @Test
        @DisplayName("BigDecimal stripTrailingZeros")
        void bigDecimalRepr() {
            Map<String, Object> before = Map.of("bd", new BigDecimal("1.00"));
            Map<String, Object> after = Map.of("bd", new BigDecimal("1.01"));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — path deduplication")
    class DiffDetectorPathDedup {

        @Test
        @DisplayName("nested paths")
        void nestedPaths() {
            Map<String, Object> before = Map.of("a.b", 1, "a", Map.of("b", 1));
            Map<String, Object> after = Map.of("a.b", 2, "a", Map.of("b", 2));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("path with brackets")
        void pathWithBrackets() {
            Map<String, Object> before = Map.of("items[0]", 1);
            Map<String, Object> after = Map.of("items[0]", 2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — enhanced vs basic dedup")
    class DiffDetectorDedupModes {

        @Test
        @DisplayName("enhanced dedup enabled")
        void enhancedDedupEnabled() {
            boolean saved = DiffDetector.isEnhancedDeduplicationEnabled();
            DiffDetector.setEnhancedDeduplicationEnabled(true);
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 2, "b", 2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
            DiffDetector.setEnhancedDeduplicationEnabled(saved);
        }

        @Test
        @DisplayName("basic dedup when enhanced disabled")
        void basicDedup() {
            boolean saved = DiffDetector.isEnhancedDeduplicationEnabled();
            DiffDetector.setEnhancedDeduplicationEnabled(false);
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 2, "b", 2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
            DiffDetector.setEnhancedDeduplicationEnabled(saved);
        }
    }

    @Nested
    @DisplayName("DiffFacade")
    class DiffFacadeBranches {

        @Test
        @DisplayName("fallback to static DiffDetector")
        void fallbackStatic() {
            DiffFacade.setProgrammaticService(null);
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 2);
            List<ChangeRecord> changes = DiffFacade.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("programmatic service")
        void programmaticService() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.programmaticInitNoSpring();
            DiffFacade.setProgrammaticService(svc);
            try {
                Map<String, Object> before = Map.of("a", 1);
                Map<String, Object> after = Map.of("a", 2);
                List<ChangeRecord> changes = DiffFacade.diff("x", before, after);
                assertThat(changes).isNotEmpty();
            } finally {
                DiffFacade.setProgrammaticService(null);
            }
        }

        @Test
        @DisplayName("detectReferenceChange fallback")
        void detectReferenceChangeFallback() {
            DiffFacade.setProgrammaticService(null);
            var detail = DiffFacade.detectReferenceChange("a", "b", "f", "old", "new");
            assertThat(detail).isNull();
        }
    }

    @Nested
    @DisplayName("DiffDetectorService")
    class DiffDetectorServiceBranches {

        @Test
        @DisplayName("diff null before")
        void diffNullBefore() {
            DiffDetectorService svc = new DiffDetectorService();
            List<ChangeRecord> changes = svc.diff("x", null, Map.of("a", 1));
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff null after")
        void diffNullAfter() {
            DiffDetectorService svc = new DiffDetectorService();
            List<ChangeRecord> changes = svc.diff("x", Map.of("a", 1), null);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("registerObjectType")
        void registerObjectType() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.registerObjectType("User", TestPojo.class);
        }

        @Test
        @DisplayName("getMetricsSnapshot")
        void getMetricsSnapshot() {
            DiffDetectorService svc = new DiffDetectorService();
            assertThat(svc.getMetricsSnapshot()).isNotNull();
        }

        @Test
        @DisplayName("resetMetrics")
        void resetMetrics() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.diff("x", Map.of("a", 1), Map.of("a", 2));
            svc.resetMetrics();
        }

        @Test
        @DisplayName("setPrecisionCompareEnabled")
        void setPrecisionCompareEnabled() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.setPrecisionCompareEnabled(false);
            assertThat(svc.isPrecisionCompareEnabled()).isFalse();
        }

        @Test
        @DisplayName("setComparatorRegistry")
        void setComparatorRegistry() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.setComparatorRegistry(
                new com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry());
        }
    }

    @Nested
    @DisplayName("ChangeRecordComparator")
    class ChangeRecordComparatorBranches {

        @Test
        @DisplayName("compare same reference 0")
        void compareSameRef() {
            ChangeRecord r = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r, r)).isZero();
        }

        @Test
        @DisplayName("compare null first negative")
        void compareNullFirst() {
            ChangeRecord r = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(null, r)).isNegative();
        }

        @Test
        @DisplayName("compare null second positive")
        void compareNullSecond() {
            ChangeRecord r = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r, null)).isPositive();
        }

        @Test
        @DisplayName("compare by fieldName")
        void compareByFieldName() {
            ChangeRecord r1 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            ChangeRecord r2 = ChangeRecord.builder()
                .objectName("x").fieldName("b").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isNegative();
        }

        @Test
        @DisplayName("compare by changeType DELETE before CREATE")
        void compareByChangeType() {
            ChangeRecord r1 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.DELETE).build();
            ChangeRecord r2 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.CREATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isNegative();
        }

        @Test
        @DisplayName("compare by changeType UPDATE before CREATE")
        void compareUpdateBeforeCreate() {
            ChangeRecord r1 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            ChangeRecord r2 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.CREATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isNegative();
        }

        @Test
        @DisplayName("compare MOVE type")
        void compareMoveType() {
            ChangeRecord r1 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.MOVE).build();
            ChangeRecord r2 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            int cmp = ChangeRecordComparator.INSTANCE.compare(r1, r2);
            assertThat(cmp).isNotNull();
        }

        @Test
        @DisplayName("generateSortKey")
        void generateSortKey() {
            ChangeRecord r = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            String key = ChangeRecordComparator.INSTANCE.generateSortKey(r);
            assertThat(key).contains("a").contains("UPDATE");
        }

        @Test
        @DisplayName("generateSortKey null")
        void generateSortKeyNull() {
            assertThat(ChangeRecordComparator.INSTANCE.generateSortKey(null))
                .isEqualTo("null-record");
        }

        @Test
        @DisplayName("verifyStability")
        void verifyStability() {
            List<ChangeRecord> records = List.of(
                ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build(),
                ChangeRecord.builder().objectName("x").fieldName("b").changeType(ChangeType.UPDATE).build());
            assertThat(ChangeRecordComparator.verifyStability(records, 10)).isTrue();
        }

        @Test
        @DisplayName("verifyStability null")
        void verifyStabilityNull() {
            assertThat(ChangeRecordComparator.verifyStability(null, 5)).isTrue();
        }

        @Test
        @DisplayName("verifyStability empty")
        void verifyStabilityEmpty() {
            assertThat(ChangeRecordComparator.verifyStability(
                Collections.emptyList(), 5)).isTrue();
        }

        @Test
        @DisplayName("sort list")
        void sortList() {
            List<ChangeRecord> records = new ArrayList<>(List.of(
                ChangeRecord.builder().objectName("x").fieldName("b").changeType(ChangeType.CREATE).build(),
                ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.DELETE).build()));
            records.sort(ChangeRecordComparator.INSTANCE);
            assertThat(records.get(0).getFieldName()).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("ChangeRecord builder")
    class ChangeRecordBuilderBranches {

        @Test
        @DisplayName("builder all fields")
        void builderAllFields() {
            ChangeRecord r = ChangeRecord.builder()
                .objectName("Obj")
                .fieldName("f")
                .oldValue("old")
                .newValue("new")
                .timestamp(12345L)
                .sessionId("s1")
                .taskPath("t1")
                .changeType(ChangeType.UPDATE)
                .valueType("java.lang.String")
                .valueKind("STRING")
                .valueRepr("repr")
                .reprOld("ro")
                .reprNew("rn")
                .build();
            assertThat(r.getObjectName()).isEqualTo("Obj");
            assertThat(r.getChangeType()).isEqualTo(ChangeType.UPDATE);
        }

        @Test
        @DisplayName("of factory")
        void of() {
            ChangeRecord r = ChangeRecord.of("Obj", "f", "old", "new", ChangeType.UPDATE);
            assertThat(r.getObjectName()).isEqualTo("Obj");
        }
    }

    static class TestPojo {
        @SuppressWarnings("unused")
        public String name;
    }
}
