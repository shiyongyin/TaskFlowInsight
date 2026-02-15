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
 * Surgical branch coverage tests for DiffDetectorService and DiffDetector.
 * Targets COMPAT/ENHANCED modes, precision compare, value kinds,
 * path deduplication, and heavy mode.
 *
 * @since 3.0.0
 */
@DisplayName("DiffDetector Branch Tests")
class DiffDetectorBranchTests {

    @BeforeEach
    void setUp() {
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffFacade.setProgrammaticService(null);
    }

    @AfterEach
    void tearDown() {
        DiffFacade.setProgrammaticService(null);
    }

    // ── DiffDetectorService ──

    @Nested
    @DisplayName("DiffDetectorService — diff branches")
    class DiffDetectorServiceBranches {

        private DiffDetectorService service;

        @BeforeEach
        void initService() {
            service = new DiffDetectorService();
        }

        @Test
        @DisplayName("before null uses emptyMap")
        void beforeNull() {
            List<ChangeRecord> changes = service.diff("Obj", null, Map.of("a", 1));
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("after null uses emptyMap")
        void afterNull() {
            List<ChangeRecord> changes = service.diff("Obj", Map.of("a", 1), null);
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("both null")
        void bothNull() {
            List<ChangeRecord> changes = service.diff("Obj", null, null);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("CREATE change")
        void createChange() {
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 1, "b", 2);
            List<ChangeRecord> changes = service.diff("Obj", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("DELETE change")
        void deleteChange() {
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 1);
            List<ChangeRecord> changes = service.diff("Obj", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("UPDATE change")
        void updateChange() {
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 2);
            List<ChangeRecord> changes = service.diff("Obj", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("precision disabled uses Objects.equals")
        void precisionDisabled() {
            service.setPrecisionCompareEnabled(false);
            Map<String, Object> before = Map.of("x", 1.0);
            Map<String, Object> after = Map.of("x", 1.0000000001);
            List<ChangeRecord> changes = service.diff("Obj", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("precision enabled BigDecimal within tolerance")
        void precisionEnabledBigDecimal() {
            service.setPrecisionCompareEnabled(true);
            Map<String, Object> before = Map.of("bd", new BigDecimal("1.000000000000"));
            Map<String, Object> after = Map.of("bd", new BigDecimal("1.000000000001"));
            List<ChangeRecord> changes = service.diff("Obj", before, after);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("precision enabled BigDecimal outside tolerance")
        void precisionEnabledBigDecimalOutside() {
            service.setPrecisionCompareEnabled(true);
            Map<String, Object> before = Map.of("bd", new BigDecimal("1.0"));
            Map<String, Object> after = Map.of("bd", new BigDecimal("1.1"));
            List<ChangeRecord> changes = service.diff("Obj", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("registerObjectType")
        void registerObjectType() {
            service.registerObjectType("User", String.class);
            Map<String, Object> before = Map.of("name", "a");
            Map<String, Object> after = Map.of("name", "b");
            List<ChangeRecord> changes = service.diff("User", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("registerObjectType null key ignored")
        void registerObjectTypeNullKey() {
            service.registerObjectType(null, String.class);
            assertThat(service.diff("x", Map.of("a", 1), Map.of("a", 2))).isNotEmpty();
        }

        @Test
        @DisplayName("getMetricsSnapshot")
        void getMetricsSnapshot() {
            service.diff("x", Map.of("a", 1), Map.of("a", 2));
            assertThat(service.getMetricsSnapshot()).isNotNull();
        }

        @Test
        @DisplayName("resetMetrics")
        void resetMetrics() {
            service.diff("x", Map.of("a", 1), Map.of("a", 2));
            service.resetMetrics();
            assertThat(service.getMetricsSnapshot()).isNotNull();
        }

        @Test
        @DisplayName("isPrecisionCompareEnabled")
        void isPrecisionCompareEnabled() {
            service.setPrecisionCompareEnabled(true);
            assertThat(service.isPrecisionCompareEnabled()).isTrue();
            service.setPrecisionCompareEnabled(false);
            assertThat(service.isPrecisionCompareEnabled()).isFalse();
        }

        @Test
        @DisplayName("programmaticInitNoSpring")
        void programmaticInitNoSpring() {
            assertThatCode(() -> service.programmaticInitNoSpring()).doesNotThrowAnyException();
        }
    }

    // ── DiffDetectorService value kinds ──

    @Nested
    @DisplayName("DiffDetectorService — value kinds")
    class DiffDetectorServiceValueKinds {

        private final DiffDetectorService service = new DiffDetectorService();

        @Test
        @DisplayName("STRING")
        void stringKind() {
            List<ChangeRecord> changes = service.diff("x", Map.of("s", "a"), Map.of("s", "b"));
            assertThat(changes.get(0).getValueKind()).isEqualTo("STRING");
        }

        @Test
        @DisplayName("NUMBER")
        void numberKind() {
            List<ChangeRecord> changes = service.diff("x", Map.of("n", 1), Map.of("n", 2));
            assertThat(changes.get(0).getValueKind()).isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("BOOLEAN")
        void booleanKind() {
            List<ChangeRecord> changes = service.diff("x", Map.of("b", true), Map.of("b", false));
            assertThat(changes.get(0).getValueKind()).isEqualTo("BOOLEAN");
        }

        @Test
        @DisplayName("DATE")
        void dateKind() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            List<ChangeRecord> changes = service.diff("x", Map.of("d", d1), Map.of("d", d2));
            assertThat(changes.get(0).getValueKind()).isEqualTo("DATE");
        }

        @Test
        @DisplayName("ENUM")
        void enumKind() {
            List<ChangeRecord> changes = service.diff("x",
                Map.of("e", ChangeType.CREATE),
                Map.of("e", ChangeType.DELETE));
            assertThat(changes.get(0).getValueKind()).isEqualTo("ENUM");
        }

        @Test
        @DisplayName("COLLECTION")
        void collectionKind() {
            List<ChangeRecord> changes = service.diff("x",
                Map.of("c", List.of(1)),
                Map.of("c", List.of(2)));
            assertThat(changes.get(0).getValueKind()).isEqualTo("COLLECTION");
        }

        @Test
        @DisplayName("MAP")
        void mapKind() {
            List<ChangeRecord> changes = service.diff("x",
                Map.of("m", Map.of("k", 1)),
                Map.of("m", Map.of("k", 2)));
            assertThat(changes.get(0).getValueKind()).isEqualTo("MAP");
        }

        @Test
        @DisplayName("ARRAY")
        void arrayKind() {
            List<ChangeRecord> changes = service.diff("x",
                Map.of("a", new int[]{1}),
                Map.of("a", new int[]{2}));
            assertThat(changes.get(0).getValueKind()).isEqualTo("ARRAY");
        }

        @Test
        @DisplayName("OTHER")
        void otherKind() {
            List<ChangeRecord> changes = service.diff("x",
                Map.of("o", new Object()),
                Map.of("o", new Object()));
            assertThat(changes.get(0).getValueKind()).isEqualTo("OTHER");
        }
    }

    // ── DiffDetector ──

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
        @DisplayName("ENHANCED mode reprOld reprNew")
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
        @DisplayName("ENHANCED DELETE valueRepr")
        void enhancedDelete() {
            Map<String, Object> before = Map.of("d", "deleted");
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> changes = DiffDetector.diffWithMode(
                "Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }
    }

    @Nested
    @DisplayName("DiffDetector — precision compare")
    class DiffDetectorPrecision {

        @BeforeEach
        void enablePrecision() {
            DiffDetector.setPrecisionCompareEnabled(true);
            PrecisionController ctrl = new PrecisionController(1e-12, 1e-9,
                com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L);
            DiffDetector.setPrecisionController(ctrl);
        }

        @AfterEach
        void disablePrecision() {
            DiffDetector.setPrecisionCompareEnabled(false);
        }

        @Test
        @DisplayName("BigDecimal within tolerance")
        void bigDecimalWithinTolerance() {
            Map<String, Object> before = Map.of("bd", new BigDecimal("1.000000000000"));
            Map<String, Object> after = Map.of("bd", new BigDecimal("1.000000000001"));
            List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("Date normalization")
        void dateNormalization() {
            Date d1 = new Date(1000);
            Date d2 = new Date(1000);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
            assertThat(changes).isEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — path deduplication")
    class DiffDetectorPathDedup {

        @Test
        @DisplayName("enhanced deduplication enabled")
        void enhancedDedupEnabled() {
            boolean saved = DiffDetector.isEnhancedDeduplicationEnabled();
            try {
                DiffDetector.setEnhancedDeduplicationEnabled(true);
                Map<String, Object> before = Map.of("a", 1, "b", 2);
                Map<String, Object> after = Map.of("a", 2, "b", 3);
                List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
                assertThat(changes).isNotEmpty();
            } finally {
                DiffDetector.setEnhancedDeduplicationEnabled(saved);
            }
        }

        @Test
        @DisplayName("enhanced deduplication disabled")
        void enhancedDedupDisabled() {
            boolean saved = DiffDetector.isEnhancedDeduplicationEnabled();
            try {
                DiffDetector.setEnhancedDeduplicationEnabled(false);
                Map<String, Object> before = Map.of("a", 1);
                Map<String, Object> after = Map.of("a", 2);
                List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
                assertThat(changes).isNotEmpty();
            } finally {
                DiffDetector.setEnhancedDeduplicationEnabled(saved);
            }
        }

        @Test
        @DisplayName("getDeduplicationStatistics")
        void getDeduplicationStatistics() {
            DiffDetector.diff("Obj", Map.of("a", 1), Map.of("a", 2));
            PathDeduplicator.DeduplicationStatistics stats = DiffDetector.getDeduplicationStatistics();
            assertThat(stats).isNotNull();
        }
    }

    @Nested
    @DisplayName("DiffDetector — heavy mode")
    class DiffDetectorHeavyMode {

        @Test
        @DisplayName("compat heavy optimizations")
        void compatHeavyOptimizations() {
            boolean saved = DiffDetector.isCompatHeavyOptimizationsEnabled();
            try {
                DiffDetector.setCompatHeavyOptimizationsEnabled(true);
                Map<String, Object> before = new HashMap<>();
                Map<String, Object> after = new HashMap<>();
                for (int i = 0; i < 60; i++) {
                    before.put("k" + i, i);
                    after.put("k" + i, i + 1);
                }
                List<ChangeRecord> changes = DiffDetector.diffWithMode(
                    "Heavy", before, after, DiffDetector.DiffMode.COMPAT);
                assertThat(changes).isNotEmpty();
            } finally {
                DiffDetector.setCompatHeavyOptimizationsEnabled(saved);
            }
        }
    }

    @Nested
    @DisplayName("DiffDetector — Map/Set/Collection strategy")
    class DiffDetectorCollectionStrategy {

        @Test
        @DisplayName("Map strategy identical")
        void mapIdentical() {
            Map<String, Object> m = Map.of("a", 1);
            Map<String, Object> before = Map.of("m", m);
            Map<String, Object> after = Map.of("m", Map.of("a", 1));
            List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("Map strategy different")
        void mapDifferent() {
            Map<String, Object> before = Map.of("m", Map.of("a", 1));
            Map<String, Object> after = Map.of("m", Map.of("a", 2));
            List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("Set strategy identical")
        void setIdentical() {
            Set<String> s = Set.of("a", "b");
            Map<String, Object> before = Map.of("s", s);
            Map<String, Object> after = Map.of("s", Set.of("a", "b"));
            List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("Set strategy different")
        void setDifferent() {
            Map<String, Object> before = Map.of("s", Set.of("a"));
            Map<String, Object> after = Map.of("s", Set.of("b"));
            List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("Collection strategy")
        void collectionStrategy() {
            Map<String, Object> before = Map.of("c", List.of(1, 2));
            Map<String, Object> after = Map.of("c", List.of(1, 3));
            List<ChangeRecord> changes = DiffDetector.diff("Obj", before, after);
            assertThat(changes).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DiffDetector — toRepr branches")
    class DiffDetectorToRepr {

        @Test
        @DisplayName("Number with stripTrailingZeros")
        void numberStripZeros() {
            Map<String, Object> before = Map.of("n", 1.0);
            Map<String, Object> after = Map.of("n", 2.0);
            List<ChangeRecord> changes = DiffDetector.diffWithMode(
                "Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(changes.get(0).getReprOld()).isNotNull();
        }

        @Test
        @DisplayName("Date repr")
        void dateRepr() {
            Date d = new Date(1000);
            Map<String, Object> before = Map.of("d", d);
            Map<String, Object> after = Map.of("d", new Date(2000));
            List<ChangeRecord> changes = DiffDetector.diffWithMode(
                "Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(changes.get(0).getReprOld()).isNotNull();
        }
    }
}
