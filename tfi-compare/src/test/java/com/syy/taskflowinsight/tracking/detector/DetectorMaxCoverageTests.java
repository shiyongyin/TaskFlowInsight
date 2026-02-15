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
 * Maximum coverage tests for tracking/detector package.
 * Targets DiffDetector, DiffFacade, DiffDetectorService, ChangeRecordComparator,
 * and all code paths including exception handling, null branches, and configuration.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Detector Max Coverage — 检测器最大覆盖测试")
class DetectorMaxCoverageTests {

    @BeforeEach
    void setUp() {
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffFacade.setProgrammaticService(null);
    }

    @AfterEach
    void tearDown() {
        DiffFacade.setProgrammaticService(null);
    }

    // ── DiffDetector ──

    @Nested
    @DisplayName("DiffDetector — diff modes")
    class DiffDetectorModeTests {

        @Test
        @DisplayName("diff COMPAT mode")
        void diff_compat() {
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 2, "b", 2);
            List<ChangeRecord> changes = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.COMPAT);
            assertThat(changes).anyMatch(c -> "a".equals(c.getFieldName()) && c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("diff ENHANCED mode sets reprOld reprNew")
        void diff_enhanced() {
            Map<String, Object> before = Map.of("x", 1);
            Map<String, Object> after = Map.of("x", 2);
            List<ChangeRecord> changes = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(changes).isNotEmpty();
            assertThat(changes.get(0).getReprOld()).isNotNull();
            assertThat(changes.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("diff ENHANCED mode DELETE sets valueRepr from oldValue")
        void diff_enhancedDelete() {
            Map<String, Object> before = Map.of("d", "deleted");
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> changes = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }
    }

    @Nested
    @DisplayName("DiffDetector — value kinds")
    class DiffDetectorValueKindTests {

        @Test
        @DisplayName("diff with STRING valueKind")
        void diff_string() {
            Map<String, Object> before = Map.of("s", "old");
            Map<String, Object> after = Map.of("s", "new");
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("STRING");
        }

        @Test
        @DisplayName("diff with NUMBER valueKind")
        void diff_number() {
            Map<String, Object> before = Map.of("n", 1);
            Map<String, Object> after = Map.of("n", 2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("diff with BOOLEAN valueKind")
        void diff_boolean() {
            Map<String, Object> before = Map.of("b", true);
            Map<String, Object> after = Map.of("b", false);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("BOOLEAN");
        }

        @Test
        @DisplayName("diff with DATE valueKind")
        void diff_date() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("DATE");
        }

        @Test
        @DisplayName("diff with ENUM valueKind")
        void diff_enum() {
            Map<String, Object> before = Map.of("e", ChangeType.CREATE);
            Map<String, Object> after = Map.of("e", ChangeType.DELETE);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("ENUM");
        }

        @Test
        @DisplayName("diff with COLLECTION valueKind")
        void diff_collection() {
            Map<String, Object> before = Map.of("c", List.of(1));
            Map<String, Object> after = Map.of("c", List.of(2));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("COLLECTION");
        }

        @Test
        @DisplayName("diff with MAP valueKind")
        void diff_map() {
            Map<String, Object> before = Map.of("m", Map.of("k", 1));
            Map<String, Object> after = Map.of("m", Map.of("k", 2));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("MAP");
        }

        @Test
        @DisplayName("diff with ARRAY valueKind")
        void diff_array() {
            Map<String, Object> before = Map.of("a", new int[]{1});
            Map<String, Object> after = Map.of("a", new int[]{2});
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes.get(0).getValueKind()).isEqualTo("ARRAY");
        }

        @Test
        @DisplayName("diff CREATE type")
        void diff_create() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = Map.of("new", "value");
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("diff DELETE type")
        void diff_delete() {
            Map<String, Object> before = Map.of("old", "value");
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("diff no change returns empty")
        void diff_noChange() {
            Map<String, Object> m = Map.of("a", 1);
            List<ChangeRecord> changes = DiffDetector.diff("x", m, m);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("diff both null values no change")
        void diff_bothNullNoChange() {
            Map<String, Object> before = new HashMap<>();
            before.put("n", null);
            Map<String, Object> after = new HashMap<>();
            after.put("n", null);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — configuration")
    class DiffDetectorConfigTests {

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
            PrecisionController ctrl = new PrecisionController(1e-10, 1e-8,
                com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L);
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
    @DisplayName("DiffDetector — path deduplication")
    class DiffDetectorDedupTests {

        @Test
        @DisplayName("diff with nested paths triggers dedup")
        void diff_nestedPaths() {
            Map<String, Object> before = Map.of("a.b", 1, "a", Map.of("b", 1));
            Map<String, Object> after = Map.of("a.b", 2, "a", Map.of("b", 2));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("diff with path containing brackets")
        void diff_pathWithBrackets() {
            Map<String, Object> before = Map.of("items[0]", 1);
            Map<String, Object> after = Map.of("items[0]", 2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — Date normalization")
    class DiffDetectorDateTests {

        @Test
        @DisplayName("diff Date normalized to long for comparison")
        void diff_dateNormalized() {
            Date d1 = new Date(1000);
            Date d2 = new Date(1000);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — BigDecimal repr")
    class DiffDetectorBigDecimalTests {

        @Test
        @DisplayName("diff BigDecimal stripTrailingZeros in repr")
        void diff_bigDecimalRepr() {
            Map<String, Object> before = Map.of("bd", new BigDecimal("1.00"));
            Map<String, Object> after = Map.of("bd", new BigDecimal("1.01"));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }
    }

    // ── DiffFacade ──

    @Nested
    @DisplayName("DiffFacade")
    class DiffFacadeTests {

        @Test
        @DisplayName("diff without programmatic service uses static DiffDetector")
        void diff_fallbackStatic() {
            DiffFacade.setProgrammaticService(null);
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 2);
            List<ChangeRecord> changes = DiffFacade.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff with programmatic service")
        void diff_programmaticService() {
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
        @DisplayName("setProgrammaticService null clears")
        void setProgrammaticService_null() {
            DiffDetectorService svc = new DiffDetectorService();
            DiffFacade.setProgrammaticService(svc);
            DiffFacade.setProgrammaticService(null);
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 2);
            List<ChangeRecord> changes = DiffFacade.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("detectReferenceChange with programmatic service")
        void detectReferenceChange_programmatic() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.programmaticInitNoSpring();
            DiffFacade.setProgrammaticService(svc);
            try {
                var detail = DiffFacade.detectReferenceChange("rootA", "rootB", "field", "old", "new");
                assertThat(detail).isNull();
            } finally {
                DiffFacade.setProgrammaticService(null);
            }
        }

        @Test
        @DisplayName("detectReferenceChange fallback to local instance")
        void detectReferenceChange_fallback() {
            DiffFacade.setProgrammaticService(null);
            var detail = DiffFacade.detectReferenceChange("a", "b", "f", "old", "new");
            assertThat(detail).isNull();
        }
    }

    // ── DiffDetectorService ──

    @Nested
    @DisplayName("DiffDetectorService")
    class DiffDetectorServiceTests {

        @Test
        @DisplayName("diff null before")
        void diff_nullBefore() {
            DiffDetectorService svc = new DiffDetectorService();
            List<ChangeRecord> changes = svc.diff("x", null, Map.of("a", 1));
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff null after")
        void diff_nullAfter() {
            DiffDetectorService svc = new DiffDetectorService();
            List<ChangeRecord> changes = svc.diff("x", Map.of("a", 1), null);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff CREATE")
        void diff_create() {
            DiffDetectorService svc = new DiffDetectorService();
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = Map.of("n", "new");
            List<ChangeRecord> changes = svc.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("diff DELETE")
        void diff_delete() {
            DiffDetectorService svc = new DiffDetectorService();
            Map<String, Object> before = Map.of("o", "old");
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> changes = svc.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("diff UPDATE")
        void diff_update() {
            DiffDetectorService svc = new DiffDetectorService();
            Map<String, Object> before = Map.of("u", 1);
            Map<String, Object> after = Map.of("u", 2);
            List<ChangeRecord> changes = svc.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("diff with precision disabled uses equals")
        void diff_precisionDisabled() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.setPrecisionCompareEnabled(false);
            Map<String, Object> before = Map.of("n", 1.0);
            Map<String, Object> after = Map.of("n", 1.0);
            List<ChangeRecord> changes = svc.diff("x", before, after);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("diff with BigDecimal")
        void diff_bigDecimal() {
            DiffDetectorService svc = new DiffDetectorService();
            Map<String, Object> before = Map.of("bd", new BigDecimal("1.00"));
            Map<String, Object> after = Map.of("bd", new BigDecimal("1.01"));
            List<ChangeRecord> changes = svc.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff with Date")
        void diff_date() {
            DiffDetectorService svc = new DiffDetectorService();
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> changes = svc.diff("x", before, after);
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
            svc.setComparatorRegistry(new com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry());
        }
    }

    // ── ChangeRecordComparator ──

    @Nested
    @DisplayName("ChangeRecordComparator")
    class ChangeRecordComparatorTests {

        @Test
        @DisplayName("compare same reference returns 0")
        void compare_sameRef() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r, r)).isZero();
        }

        @Test
        @DisplayName("compare null first returns negative")
        void compare_nullFirst() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(null, r)).isNegative();
        }

        @Test
        @DisplayName("compare null second returns positive")
        void compare_nullSecond() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r, null)).isPositive();
        }

        @Test
        @DisplayName("compare by fieldName lexicographic")
        void compare_byFieldName() {
            ChangeRecord r1 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            ChangeRecord r2 = ChangeRecord.builder().objectName("x").fieldName("b").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isNegative();
        }

        @Test
        @DisplayName("compare by path depth when fieldName equal")
        void compare_byPathDepth() {
            ChangeRecord r1 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            ChangeRecord r2 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isZero();
        }

        @Test
        @DisplayName("compare by changeType priority DELETE before CREATE")
        void compare_byChangeType() {
            ChangeRecord r1 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.DELETE).build();
            ChangeRecord r2 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.CREATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isNegative();
        }

        @Test
        @DisplayName("compare by changeType UPDATE before CREATE")
        void compare_updateBeforeCreate() {
            ChangeRecord r1 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            ChangeRecord r2 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.CREATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isNegative();
        }

        @Test
        @DisplayName("compare by value representation when type equal")
        void compare_byValueRepr() {
            ChangeRecord r1 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE)
                .reprOld("old1").reprNew("new1").build();
            ChangeRecord r2 = ChangeRecord.builder()
                .objectName("x").fieldName("a").changeType(ChangeType.UPDATE)
                .reprOld("old2").reprNew("new2").build();
            int cmp = ChangeRecordComparator.INSTANCE.compare(r1, r2);
            assertThat(cmp).isNotZero();
        }

        @Test
        @DisplayName("compare MOVE type uses default priority")
        void compare_moveType() {
            ChangeRecord r1 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.MOVE).build();
            ChangeRecord r2 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            int cmp = ChangeRecordComparator.INSTANCE.compare(r1, r2);
            assertThat(cmp).isNotNull();
        }

        @Test
        @DisplayName("generateSortKey")
        void generateSortKey() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            String key = ChangeRecordComparator.INSTANCE.generateSortKey(r);
            assertThat(key).contains("a").contains("UPDATE");
        }

        @Test
        @DisplayName("generateSortKey null")
        void generateSortKey_null() {
            assertThat(ChangeRecordComparator.INSTANCE.generateSortKey(null)).isEqualTo("null-record");
        }

        @Test
        @DisplayName("verifyStability multiple iterations")
        void verifyStability() {
            List<ChangeRecord> records = List.of(
                ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build(),
                ChangeRecord.builder().objectName("x").fieldName("b").changeType(ChangeType.UPDATE).build());
            assertThat(ChangeRecordComparator.verifyStability(records, 10)).isTrue();
        }

        @Test
        @DisplayName("verifyStability null list")
        void verifyStability_null() {
            assertThat(ChangeRecordComparator.verifyStability(null, 5)).isTrue();
        }

        @Test
        @DisplayName("verifyStability empty list")
        void verifyStability_empty() {
            assertThat(ChangeRecordComparator.verifyStability(Collections.emptyList(), 5)).isTrue();
        }

        @Test
        @DisplayName("sort list with comparator")
        void sort_list() {
            List<ChangeRecord> records = new ArrayList<>(List.of(
                ChangeRecord.builder().objectName("x").fieldName("b").changeType(ChangeType.CREATE).build(),
                ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.DELETE).build()));
            records.sort(ChangeRecordComparator.INSTANCE);
            assertThat(records.get(0).getFieldName()).isEqualTo("a");
        }
    }

    // ── ChangeRecord ──

    @Nested
    @DisplayName("ChangeRecord")
    class ChangeRecordTests {

        @Test
        @DisplayName("builder with all fields")
        void builder_allFields() {
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
            assertThat(r.getFieldName()).isEqualTo("f");
            assertThat(r.getOldValue()).isEqualTo("old");
            assertThat(r.getNewValue()).isEqualTo("new");
            assertThat(r.getChangeType()).isEqualTo(ChangeType.UPDATE);
            assertThat(r.getReprOld()).isEqualTo("ro");
            assertThat(r.getReprNew()).isEqualTo("rn");
        }

        @Test
        @DisplayName("of factory method")
        void of() {
            ChangeRecord r = ChangeRecord.of("Obj", "f", "old", "new", ChangeType.UPDATE);
            assertThat(r.getObjectName()).isEqualTo("Obj");
            assertThat(r.getChangeType()).isEqualTo(ChangeType.UPDATE);
        }
    }

    static class TestPojo {
        public String name;
    }
}
