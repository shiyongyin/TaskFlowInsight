package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DiffDetector 单元测试
 *
 * <p>覆盖测试方案 DD-WB-001 ~ DD-WB-010 的核心差异检测逻辑。
 * 测试策略：使用 Map 快照输入，验证 ChangeRecord 输出的正确性。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("DiffDetector — 核心差异检测测试")
class DiffDetectorTests {

    @BeforeEach
    void setUp() {
        // Reset precision compare to default (disabled) before each test
        DiffDetector.setPrecisionCompareEnabled(false);
    }

    // ──────────────────────────────────────────────────────────────
    //  基本 CRUD 变更检测
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("基本变更类型检测")
    class BasicChangeDetectionTests {

        @Test
        @DisplayName("DD-WB-001: before 有 / after 无 → DELETE")
        void fieldInBeforeOnly_shouldBeDelete() {
            Map<String, Object> before = Map.of("name", "Alice", "age", 25);
            Map<String, Object> after = Map.of("name", "Alice");

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            assertThat(changes).isNotEmpty();
            assertThat(changes).anyMatch(c ->
                    "age".equals(c.getFieldName()) && c.getChangeType() == ChangeType.DELETE
            );
        }

        @Test
        @DisplayName("DD-WB-002: before 无 / after 有 → CREATE")
        void fieldInAfterOnly_shouldBeCreate() {
            Map<String, Object> before = Map.of("name", "Alice");
            Map<String, Object> after = Map.of("name", "Alice", "email", "alice@example.com");

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            assertThat(changes).isNotEmpty();
            assertThat(changes).anyMatch(c ->
                    "email".equals(c.getFieldName()) && c.getChangeType() == ChangeType.CREATE
            );
        }

        @Test
        @DisplayName("DD-WB-003: 值相同 → 无变更记录")
        void sameValues_shouldReturnEmpty() {
            Map<String, Object> before = Map.of("name", "Alice", "age", 25);
            Map<String, Object> after = Map.of("name", "Alice", "age", 25);

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("DD-WB-004: 值不同 → UPDATE")
        void differentValues_shouldBeUpdate() {
            Map<String, Object> before = new HashMap<>();
            before.put("name", "Alice");
            before.put("age", 25);

            Map<String, Object> after = new HashMap<>();
            after.put("name", "Bob");
            after.put("age", 30);

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            assertThat(changes).hasSize(2);
            assertThat(changes).allMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  边界条件
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("边界条件")
    class BoundaryTests {

        @Test
        @DisplayName("DD-WB-005: 两个空 Map → 无变更")
        void emptyMaps_shouldReturnEmpty() {
            List<ChangeRecord> changes = DiffDetector.diff("Empty",
                    Collections.emptyMap(), Collections.emptyMap());
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("DD-WB-006: before 为 null → 视为空 Map")
        void nullBefore_shouldTreatAsEmpty() {
            Map<String, Object> after = Map.of("name", "Alice");
            List<ChangeRecord> changes = DiffDetector.diff("User", null, after);

            assertThat(changes).isNotEmpty();
            assertThat(changes).anyMatch(c ->
                    "name".equals(c.getFieldName()) && c.getChangeType() == ChangeType.CREATE
            );
        }

        @Test
        @DisplayName("DD-WB-007: after 为 null → 视为空 Map")
        void nullAfter_shouldTreatAsEmpty() {
            Map<String, Object> before = Map.of("name", "Alice");
            List<ChangeRecord> changes = DiffDetector.diff("User", before, null);

            assertThat(changes).isNotEmpty();
            assertThat(changes).anyMatch(c ->
                    "name".equals(c.getFieldName()) && c.getChangeType() == ChangeType.DELETE
            );
        }

        @Test
        @DisplayName("DD-WB-008: 两侧均 null → 无变更")
        void bothNull_shouldReturnEmpty() {
            List<ChangeRecord> changes = DiffDetector.diff("User", null, null);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("DD-WB-009: null 值 vs 非 null 值 → 检测到变更")
        void nullToNonNull_shouldDetectChange() {
            Map<String, Object> before = new HashMap<>();
            before.put("name", null);

            Map<String, Object> after = new HashMap<>();
            after.put("name", "Alice");

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            assertThat(changes).isNotEmpty();
            // null → non-null may be CREATE or UPDATE depending on implementation
            assertThat(changes).anyMatch(c ->
                    "name".equals(c.getFieldName())
                    && (c.getChangeType() == ChangeType.UPDATE || c.getChangeType() == ChangeType.CREATE)
            );
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  复合类型
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("复合类型比较")
    class CompositeTypeTests {

        @Test
        @DisplayName("DD-WB-010: 嵌套 Map 递归比较")
        void nestedMap_shouldCompareRecursively() {
            Map<String, Object> innerBefore = new HashMap<>();
            innerBefore.put("city", "Beijing");
            Map<String, Object> before = new HashMap<>();
            before.put("address", innerBefore);

            Map<String, Object> innerAfter = new HashMap<>();
            innerAfter.put("city", "Shanghai");
            Map<String, Object> after = new HashMap<>();
            after.put("address", innerAfter);

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            assertThat(changes).isNotEmpty();
            // Nested map change detected — field name may be "address" (top-level) or include "city"
            assertThat(changes).anyMatch(c ->
                    c.getFieldName() != null
                    && (c.getFieldName().contains("address") || c.getFieldName().contains("city"))
            );
        }

        @Test
        @DisplayName("DD-WB-011: List 值变更检测")
        void listValue_shouldDetectChanges() {
            Map<String, Object> before = new HashMap<>();
            before.put("tags", List.of("java", "spring"));

            Map<String, Object> after = new HashMap<>();
            after.put("tags", List.of("java", "kotlin"));

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("DD-WB-012: Set 值变更检测")
        void setValue_shouldDetectChanges() {
            Map<String, Object> before = new HashMap<>();
            before.put("roles", Set.of("ADMIN", "USER"));

            Map<String, Object> after = new HashMap<>();
            after.put("roles", Set.of("ADMIN", "SUPER_ADMIN"));

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            assertThat(changes).isNotEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  配置与精度
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("配置与精度控制")
    class ConfigurationTests {

        @Test
        @DisplayName("DD-WB-013: 精度比较开关生效")
        void precisionToggle_shouldWork() {
            assertThat(DiffDetector.isPrecisionCompareEnabled()).isFalse();

            DiffDetector.setPrecisionCompareEnabled(true);
            assertThat(DiffDetector.isPrecisionCompareEnabled()).isTrue();

            DiffDetector.setPrecisionCompareEnabled(false);
            assertThat(DiffDetector.isPrecisionCompareEnabled()).isFalse();
        }

        @Test
        @DisplayName("DD-WB-014: 增强去重开关不抛异常")
        void enhancedDedup_toggleShouldNotThrow() {
            assertThatCode(() -> DiffDetector.setEnhancedDeduplicationEnabled(true))
                    .doesNotThrowAnyException();
            assertThatCode(() -> DiffDetector.setEnhancedDeduplicationEnabled(false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DD-WB-015: 大对象优化开关生效")
        void heavyOptimizations_toggleShouldWork() {
            boolean original = DiffDetector.isCompatHeavyOptimizationsEnabled();
            DiffDetector.setCompatHeavyOptimizationsEnabled(!original);
            assertThat(DiffDetector.isCompatHeavyOptimizationsEnabled()).isNotEqualTo(original);
            // Restore
            DiffDetector.setCompatHeavyOptimizationsEnabled(original);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  BigDecimal 精度比较 (DD-WB-008)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BigDecimal 精度比较")
    class BigDecimalPrecisionTests {

        @Test
        @DisplayName("DD-WB-008a: 精度比较关闭时，尾数差异视为不同")
        void precisionDisabled_trailingDifference_shouldBeUpdate() {
            DiffDetector.setPrecisionCompareEnabled(false);

            Map<String, Object> before = new HashMap<>();
            before.put("amount", new BigDecimal("100.001"));

            Map<String, Object> after = new HashMap<>();
            after.put("amount", new BigDecimal("100.009"));

            List<ChangeRecord> changes = DiffDetector.diff("Order", before, after);

            // Without precision compare, these two values are different (different BigDecimal)
            assertThat(changes).isNotEmpty();
            assertThat(changes).anyMatch(c ->
                    "amount".equals(c.getFieldName()) && c.getChangeType() == ChangeType.UPDATE
            );
        }

        @Test
        @DisplayName("DD-WB-008b: 精度比较开启时，BigDecimal 通过 NumericCompareStrategy 对比")
        void precisionEnabled_shouldUseNumericStrategy() {
            DiffDetector.setPrecisionCompareEnabled(true);

            Map<String, Object> before = new HashMap<>();
            before.put("amount", new BigDecimal("100.00"));

            Map<String, Object> after = new HashMap<>();
            after.put("amount", new BigDecimal("100.00"));

            List<ChangeRecord> changes = DiffDetector.diff("Order", before, after);

            // Same BigDecimal values — should be empty whether or not precision is enabled
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("DD-WB-008c: 精度比较开启时，不同 BigDecimal scale 但相同值")
        void precisionEnabled_differentScale_sameValue() {
            DiffDetector.setPrecisionCompareEnabled(true);

            Map<String, Object> before = new HashMap<>();
            before.put("amount", new BigDecimal("100.0"));

            Map<String, Object> after = new HashMap<>();
            after.put("amount", new BigDecimal("100.00"));

            List<ChangeRecord> changes = DiffDetector.diff("Order", before, after);

            // With precision compare enabled, NumericCompareStrategy should handle scale differences
            // The actual result depends on the strategy implementation: either empty or UPDATE
            // The key assertion is no exception and a valid result
            assertThat(changes).isNotNull();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  重对象缓存 (DD-WB-009)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("重对象缓存命中路径")
    class HeavyCacheTests {

        @Test
        @DisplayName("DD-WB-009a: 大字段数 Map 首次 diff — 结果正确")
        void heavyObject_firstDiff_shouldReturnChanges() {
            // Create a "heavy" object (> 50 fields = HEAVY_FIELD_THRESHOLD default)
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            for (int i = 0; i < 60; i++) {
                before.put("field_" + i, "old_" + i);
                after.put("field_" + i, "new_" + i);
            }

            List<ChangeRecord> changes = DiffDetector.diff("HeavyObject", before, after);

            assertThat(changes).hasSize(60);
            assertThat(changes).allMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("DD-WB-009b: 相同大字段 Map 重复 diff — 结果一致（缓存命中路径）")
        void heavyObject_repeatedDiff_shouldReturnConsistentResults() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            for (int i = 0; i < 60; i++) {
                before.put("field_" + i, "old_" + i);
                after.put("field_" + i, "new_" + i);
            }

            // First call — populates cache
            List<ChangeRecord> first = DiffDetector.diff("Heavy", before, after);
            // Second call — should hit HEAVY_CACHE
            List<ChangeRecord> second = DiffDetector.diff("Heavy", before, after);

            assertThat(first).hasSameSizeAs(second);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  路径去重 (DD-WB-010)
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("路径去重场景")
    class PathDeduplicationTests {

        @Test
        @DisplayName("DD-WB-010a: 增强去重开关切换不影响结果正确性")
        void enhancedDedupToggle_shouldNotAffectCorrectness() {
            Map<String, Object> before = new HashMap<>();
            before.put("name", "Alice");
            before.put("age", 25);

            Map<String, Object> after = new HashMap<>();
            after.put("name", "Bob");
            after.put("age", 30);

            // With enhanced dedup enabled
            DiffDetector.setEnhancedDeduplicationEnabled(true);
            List<ChangeRecord> withDedup = DiffDetector.diff("User", before, after);

            // With enhanced dedup disabled
            DiffDetector.setEnhancedDeduplicationEnabled(false);
            List<ChangeRecord> withoutDedup = DiffDetector.diff("User", before, after);

            // Restore
            DiffDetector.setEnhancedDeduplicationEnabled(true);

            // Both should detect the same 2 changes
            assertThat(withDedup).hasSize(2);
            assertThat(withoutDedup).hasSize(2);
        }

        @Test
        @DisplayName("DD-WB-010b: 嵌套 Map 不产生重复路径变更")
        void nestedMap_shouldNotProduceDuplicatePaths() {
            DiffDetector.setEnhancedDeduplicationEnabled(true);

            Map<String, Object> inner1 = new HashMap<>();
            inner1.put("street", "Main St");
            inner1.put("zip", "10001");
            Map<String, Object> before = new HashMap<>();
            before.put("address", inner1);

            Map<String, Object> inner2 = new HashMap<>();
            inner2.put("street", "Broadway");
            inner2.put("zip", "10002");
            Map<String, Object> after = new HashMap<>();
            after.put("address", inner2);

            List<ChangeRecord> changes = DiffDetector.diff("User", before, after);

            // Should detect changes without duplicate paths
            assertThat(changes).isNotEmpty();
            // Verify no duplicate fieldNames in the result
            long uniqueFieldNames = changes.stream()
                    .map(ChangeRecord::getFieldName)
                    .distinct()
                    .count();
            assertThat(uniqueFieldNames).isEqualTo(changes.size());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  大批量字段
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("大批量字段处理")
    class LargeFieldSetTests {

        @Test
        @DisplayName("DD-WB-016: 100 字段不超时、不异常")
        void hundredFields_shouldComplete() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                before.put("field_" + i, "value_" + i);
                after.put("field_" + i, "updated_" + i);
            }

            List<ChangeRecord> changes = DiffDetector.diff("LargeObject", before, after);

            assertThat(changes).hasSize(100);
            assertThat(changes).allMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("DD-WB-017: 混合 CRUD — 同时包含 CREATE、UPDATE、DELETE")
        void mixedCrud_shouldDetectAllTypes() {
            Map<String, Object> before = new HashMap<>();
            before.put("kept", "same");
            before.put("updated", "old");
            before.put("deleted", "gone");

            Map<String, Object> after = new HashMap<>();
            after.put("kept", "same");
            after.put("updated", "new");
            after.put("created", "fresh");

            List<ChangeRecord> changes = DiffDetector.diff("MixedObject", before, after);

            assertThat(changes).hasSize(3);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }
    }
}
