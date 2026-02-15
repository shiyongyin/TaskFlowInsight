package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.path.PathDeduplicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Deep coverage tests for tracking/detector package.
 * Maximizes coverage for DiffDetector, DiffFacade, DiffDetectorService, ChangeRecordComparator.
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Detector Deep Coverage — 差异检测深度覆盖测试")
class DetectorDeepCoverageTests {

    @BeforeEach
    void setUp() {
        DiffDetector.setPrecisionCompareEnabled(false);
    }

    // ── DiffDetector ──

    @Nested
    @DisplayName("DiffDetector")
    class DiffDetectorTests {

        @Test
        @DisplayName("diff null before uses empty map")
        void diff_nullBefore() {
            Map<String, Object> after = Map.of("a", 1);
            List<ChangeRecord> changes = DiffDetector.diff("x", null, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff null after uses empty map")
        void diff_nullAfter() {
            Map<String, Object> before = Map.of("a", 1);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, null);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diffWithMode COMPAT")
        void diffWithMode_compat() {
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 2, "b", 2);
            List<ChangeRecord> changes = DiffDetector.diffWithMode("x", before, after, DiffDetector.DiffMode.COMPAT);
            assertThat(changes).anyMatch(c -> "a".equals(c.getFieldName()) && c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("diffWithMode ENHANCED")
        void diffWithMode_enhanced() {
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 2);
            List<ChangeRecord> changes = DiffDetector.diffWithMode("x", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(changes).isNotEmpty();
            assertThat(changes.get(0).getReprOld()).isNotNull();
            assertThat(changes.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("setPrecisionCompareEnabled and isPrecisionCompareEnabled")
        void precisionCompare() {
            DiffDetector.setPrecisionCompareEnabled(true);
            assertThat(DiffDetector.isPrecisionCompareEnabled()).isTrue();
            DiffDetector.setPrecisionCompareEnabled(false);
        }

        @Test
        @DisplayName("setEnhancedDeduplicationEnabled and isEnhancedDeduplicationEnabled")
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

        @Test
        @DisplayName("diff with Map values")
        void diff_mapValues() {
            Map<String, Object> before = new HashMap<>();
            before.put("m", Map.of("k1", "v1"));
            Map<String, Object> after = new HashMap<>();
            after.put("m", Map.of("k1", "v2"));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff with Set values")
        void diff_setValues() {
            Map<String, Object> before = new HashMap<>();
            before.put("s", Set.of("a"));
            Map<String, Object> after = new HashMap<>();
            after.put("s", Set.of("b"));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff with Collection values")
        void diff_collectionValues() {
            Map<String, Object> before = new HashMap<>();
            before.put("c", List.of(1));
            Map<String, Object> after = new HashMap<>();
            after.put("c", List.of(2));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff with Date values")
        void diff_dateValues() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("diff with nested path deduplication")
        void diff_nestedPathDedup() {
            Map<String, Object> before = Map.of("a.b", 1, "a", Map.of("b", 1));
            Map<String, Object> after = Map.of("a.b", 2, "a", Map.of("b", 2));
            List<ChangeRecord> changes = DiffDetector.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }
    }

    // ── DiffFacade ──

    @Nested
    @DisplayName("DiffFacade")
    class DiffFacadeTests {

        @Test
        @DisplayName("diff falls back to static DiffDetector when no Spring")
        void diff_fallbackToStatic() {
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
        @DisplayName("detectReferenceChange with null roots")
        void detectReferenceChange_nullRoots() {
            var detail = DiffFacade.detectReferenceChange(null, null, "field", "old", "new");
            assertThat(detail).isNull();
        }
    }

    // ── DiffDetectorService ──

    @Nested
    @DisplayName("DiffDetectorService")
    class DiffDetectorServiceTests {

        @Test
        @DisplayName("diff null before/after")
        void diff_nullInputs() {
            DiffDetectorService svc = new DiffDetectorService();
            assertThat(svc.diff("x", null, Map.of("a", 1))).isNotEmpty();
            assertThat(svc.diff("x", Map.of("a", 1), null)).isNotEmpty();
        }

        @Test
        @DisplayName("diff CREATE DELETE UPDATE")
        void diff_allChangeTypes() {
            DiffDetectorService svc = new DiffDetectorService();
            Map<String, Object> before = Map.of("a", 1, "b", 2, "d", 4);
            Map<String, Object> after = Map.of("a", 1, "c", 3, "b", 99);
            List<ChangeRecord> changes = svc.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);  // d removed
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.CREATE); // c added
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE); // b changed
        }

        @Test
        @DisplayName("diff with numeric precision")
        void diff_numericPrecision() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.setPrecisionCompareEnabled(true);
            Map<String, Object> before = Map.of("n", 1.0000000001);
            Map<String, Object> after = Map.of("n", 1.0000000002);
            List<ChangeRecord> changes = svc.diff("x", before, after);
            assertThat(changes).isNotNull();
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
        @DisplayName("registerObjectType")
        void registerObjectType() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.registerObjectType("User", String.class);
        }

        @Test
        @DisplayName("getMetricsSnapshot resetMetrics")
        void metrics() {
            DiffDetectorService svc = new DiffDetectorService();
            assertThat(svc.getMetricsSnapshot()).isNotNull();
            svc.resetMetrics();
        }

        @Test
        @DisplayName("isPrecisionCompareEnabled setPrecisionCompareEnabled")
        void precisionCompare() {
            DiffDetectorService svc = new DiffDetectorService();
            svc.setPrecisionCompareEnabled(false);
            assertThat(svc.isPrecisionCompareEnabled()).isFalse();
        }
    }

    // ── ChangeRecordComparator ──

    @Nested
    @DisplayName("ChangeRecordComparator")
    class ChangeRecordComparatorTests {

        @Test
        @DisplayName("compare same reference")
        void compare_sameRef() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r, r)).isZero();
        }

        @Test
        @DisplayName("compare null first")
        void compare_nullFirst() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(null, r)).isNegative();
        }

        @Test
        @DisplayName("compare null second")
        void compare_nullSecond() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r, null)).isPositive();
        }

        @Test
        @DisplayName("compare by fieldName")
        void compare_byFieldName() {
            ChangeRecord r1 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            ChangeRecord r2 = ChangeRecord.builder().objectName("x").fieldName("b").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isNegative();
        }

        @Test
        @DisplayName("compare by changeType")
        void compare_byChangeType() {
            ChangeRecord r1 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.DELETE).build();
            ChangeRecord r2 = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.CREATE).build();
            assertThat(ChangeRecordComparator.INSTANCE.compare(r1, r2)).isNegative();
        }

        @Test
        @DisplayName("generateSortKey")
        void generateSortKey() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            String key = ChangeRecordComparator.INSTANCE.generateSortKey(r);
            assertThat(key).contains("a").contains("UPDATE");
        }

        @Test
        @DisplayName("generateSortKey null record")
        void generateSortKey_null() {
            assertThat(ChangeRecordComparator.INSTANCE.generateSortKey(null)).isEqualTo("null-record");
        }

        @Test
        @DisplayName("verifyStability")
        void verifyStability() {
            ChangeRecord r = ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build();
            assertThat(ChangeRecordComparator.verifyStability(List.of(r), 5)).isTrue();
        }

        @Test
        @DisplayName("verifyStability null/empty returns true")
        void verifyStability_nullEmpty() {
            assertThat(ChangeRecordComparator.verifyStability(null, 5)).isTrue();
            assertThat(ChangeRecordComparator.verifyStability(Collections.emptyList(), 5)).isTrue();
            assertThat(ChangeRecordComparator.verifyStability(List.of(
                ChangeRecord.builder().objectName("x").fieldName("a").changeType(ChangeType.UPDATE).build()
            ), 0)).isTrue();
        }
    }
}
