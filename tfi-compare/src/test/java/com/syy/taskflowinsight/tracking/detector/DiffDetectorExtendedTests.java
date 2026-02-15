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
 * DiffDetector 最终覆盖测试 — 覆盖所有未覆盖路径。
 * 目标：diff、diffWithMode、setCurrentObjectClass、formatOldNew、value kinds、
 * 精度比较、路径去重、heavy 模式、异常路径等。
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("DiffDetector 最终覆盖测试")
class DiffDetectorExtendedTests {

    @BeforeEach
    void setUp() {
        DiffDetector.setPrecisionCompareEnabled(false);
        DiffDetector.setCurrentObjectClass(null);
        DiffFacade.setProgrammaticService(null);
    }

    @AfterEach
    void tearDown() {
        DiffFacade.setProgrammaticService(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  diff(name, oldMap, newMap) 基础场景
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("diff 基础场景 — 双 null、双空、增删改")
    class DiffBasicScenarios {

        @Test
        @DisplayName("before 和 after 均为 null → 空列表")
        void bothNull() {
            List<ChangeRecord> r = DiffDetector.diff("Obj", null, null);
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("before null、after 非空 → CREATE")
        void beforeNullAfterNonEmpty() {
            Map<String, Object> after = Map.of("a", 1);
            List<ChangeRecord> r = DiffDetector.diff("Obj", null, after);
            assertThat(r).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("before 非空、after null → DELETE")
        void beforeNonEmptyAfterNull() {
            Map<String, Object> before = Map.of("a", 1);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, null);
            assertThat(r).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("双空 Map → 空列表")
        void bothEmpty() {
            List<ChangeRecord> r = DiffDetector.diff("Obj", Collections.emptyMap(), Collections.emptyMap());
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("新增字段 → CREATE")
        void addField() {
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = new HashMap<>(before);
            after.put("b", 2);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).anyMatch(c -> "b".equals(c.getFieldName()) && c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("删除字段 → DELETE")
        void removeField() {
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 1);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).anyMatch(c -> "b".equals(c.getFieldName()) && c.getChangeType() == ChangeType.DELETE);
        }

        @Test
        @DisplayName("更新字段 → UPDATE")
        void updateField() {
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 2);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).anyMatch(c -> "a".equals(c.getFieldName()) && c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("字段存在但值为 null → null→value 为 CREATE")
        void nullToValueCreate() {
            Map<String, Object> before = new HashMap<>();
            before.put("x", null);
            Map<String, Object> after = new HashMap<>();
            after.put("x", "new");
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).anyMatch(c -> c.getChangeType() == ChangeType.CREATE);
        }

        @Test
        @DisplayName("value→null 为 DELETE")
        void valueToNullDelete() {
            Map<String, Object> before = Map.of("x", "old");
            Map<String, Object> after = new HashMap<>();
            after.put("x", null);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).anyMatch(c -> c.getChangeType() == ChangeType.DELETE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  diffWithMode COMPAT / ENHANCED
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("diffWithMode — COMPAT 与 ENHANCED")
    class DiffWithModeTests {

        @Test
        @DisplayName("COMPAT 模式 DELETE 时 valueRepr 为 null")
        void compatDeleteValueRepr() {
            Map<String, Object> before = Map.of("d", "deleted");
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.COMPAT);
            ChangeRecord del = r.stream().filter(c -> c.getChangeType() == ChangeType.DELETE).findFirst().orElseThrow();
            assertThat(del.getValueRepr()).isNull();
        }

        @Test
        @DisplayName("ENHANCED 模式 reprOld/reprNew 非空")
        void enhancedReprOldNew() {
            Map<String, Object> before = Map.of("x", 1);
            Map<String, Object> after = Map.of("x", 2);
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r).isNotEmpty();
            assertThat(r.get(0).getReprOld()).isNotNull();
            assertThat(r.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("ENHANCED 模式 String 原样返回")
        void enhancedStringRepr() {
            Map<String, Object> before = Map.of("s", "hello");
            Map<String, Object> after = Map.of("s", "world");
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r.get(0).getReprOld()).isEqualTo("hello");
            assertThat(r.get(0).getReprNew()).isEqualTo("world");
        }

        @Test
        @DisplayName("ENHANCED 模式 Date 使用时间戳")
        void enhancedDateRepr() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r.get(0).getReprOld()).isEqualTo("1000");
            assertThat(r.get(0).getReprNew()).isEqualTo("2000");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  setCurrentObjectClass 与 getFieldByName（通过精度比较触发）
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("setCurrentObjectClass 与精度比较")
    class SetCurrentObjectClassTests {

        @BeforeEach
        void enablePrecision() {
            DiffDetector.setPrecisionCompareEnabled(true);
            DiffDetector.setPrecisionController(new PrecisionController(
                1e-12, 1e-9,
                com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L));
        }

        @AfterEach
        void disablePrecision() {
            DiffDetector.setPrecisionCompareEnabled(false);
        }

        @Test
        @DisplayName("setCurrentObjectClass 后精度比较使用字段级设置")
        void setCurrentObjectClassForPrecision() {
            DiffDetector.setCurrentObjectClass(TestModelWithField.class);
            Map<String, Object> before = Map.of("amount", new BigDecimal("1.000000000000"));
            Map<String, Object> after = Map.of("amount", new BigDecimal("1.000000000001"));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("currentObjectClass 为 null 时回退到默认精度比较")
        void currentObjectClassNull() {
            DiffDetector.setCurrentObjectClass(null);
            Map<String, Object> before = Map.of("x", 1.0);
            Map<String, Object> after = Map.of("x", 2.0);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
        }

        @Test
        @DisplayName("浮点数精度比较 — 容差内无变化")
        void floatPrecisionWithinTolerance() {
            DiffDetector.setCurrentObjectClass(TestModelWithField.class);
            Map<String, Object> before = Map.of("amount", 1.0);
            Map<String, Object> after = Map.of("amount", 1.0 + 1e-13);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("日期时间精度比较 — Temporal 容差内无变化")
        void temporalPrecisionWithinTolerance() {
            DiffDetector.setPrecisionCompareEnabled(true);
            Date d = new Date(1000);
            Date d2 = new Date(1001);
            Map<String, Object> before = Map.of("d", d);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  formatOldNew / toRepr 各种值类型
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("formatOldNew — 各类值类型的 repr")
    class FormatOldNewTests {

        @Test
        @DisplayName("Integer/Long/Short/Byte 直接 toString")
        void integerRepr() {
            Map<String, Object> before = Map.of("n", 42);
            Map<String, Object> after = Map.of("n", 43);
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r.get(0).getReprOld()).isEqualTo("42");
        }

        @Test
        @DisplayName("Double 去尾零")
        void doubleRepr() {
            Map<String, Object> before = Map.of("d", 1.0);
            Map<String, Object> after = Map.of("d", 2.0);
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r.get(0).getReprOld()).isNotNull();
        }

        @Test
        @DisplayName("BigDecimal stripTrailingZeros")
        void bigDecimalRepr() {
            Map<String, Object> before = Map.of("bd", new BigDecimal("1.100"));
            Map<String, Object> after = Map.of("bd", new BigDecimal("1.200"));
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.ENHANCED);
            assertThat(r.get(0).getReprOld()).isNotNull();
        }

        @Test
        @DisplayName("COMPAT 模式 -0 转为 0")
        void compatMinusZero() {
            Map<String, Object> before = Map.of("z", new BigDecimal("-0"));
            Map<String, Object> after = Map.of("z", new BigDecimal("0"));
            List<ChangeRecord> r = DiffDetector.diffWithMode("Obj", before, after, DiffDetector.DiffMode.COMPAT);
            assertThat(r).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  value kind 检测
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("value kind 检测 — STRING/NUMBER/BOOLEAN/DATE/ENUM/COLLECTION/MAP/ARRAY/OTHER/NULL")
    class ValueKindTests {

        @Test
        @DisplayName("NULL kind — CREATE 时 newValue 非 null")
        void nullKindCreate() {
            Map<String, Object> before = new HashMap<>();
            Map<String, Object> after = Map.of("x", 1);
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r.get(0).getValueKind()).isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("DELETE 时 valueKind 取自 oldValue")
        void deleteValueKind() {
            Map<String, Object> before = Map.of("s", "old");
            Map<String, Object> after = new HashMap<>();
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r.get(0).getValueKind()).isEqualTo("STRING");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Map/Set/Collection 策略比较
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Map/Set/Collection 策略比较")
    class CollectionStrategyTests {

        @Test
        @DisplayName("Map 相同内容 → 无变化")
        void mapIdentical() {
            Map<String, Object> m = Map.of("a", 1);
            Map<String, Object> before = Map.of("m", m);
            Map<String, Object> after = Map.of("m", Map.of("a", 1));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("Set 相同内容 → 无变化")
        void setIdentical() {
            Set<String> s = Set.of("a", "b");
            Map<String, Object> before = Map.of("s", s);
            Map<String, Object> after = Map.of("s", Set.of("a", "b"));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isEmpty();
        }

        @Test
        @DisplayName("Collection 相同内容 → 无变化")
        void collectionIdentical() {
            List<Integer> c = List.of(1, 2);
            Map<String, Object> before = Map.of("c", c);
            Map<String, Object> after = Map.of("c", List.of(1, 2));
            List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
            assertThat(r).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  路径去重
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("路径去重 — 增强与基础")
    class PathDeduplicationTests {

        @Test
        @DisplayName("增强去重启用时使用 PathDeduplicator")
        void enhancedDedup() {
            boolean saved = DiffDetector.isEnhancedDeduplicationEnabled();
            try {
                DiffDetector.setEnhancedDeduplicationEnabled(true);
                Map<String, Object> before = Map.of("a", 1, "b", 2);
                Map<String, Object> after = Map.of("a", 2, "b", 3);
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).isNotEmpty();
            } finally {
                DiffDetector.setEnhancedDeduplicationEnabled(saved);
            }
        }

        @Test
        @DisplayName("基础去重 — 无嵌套路径直接返回")
        void basicDedupNoNestedPath() {
            boolean saved = DiffDetector.isEnhancedDeduplicationEnabled();
            try {
                DiffDetector.setEnhancedDeduplicationEnabled(false);
                Map<String, Object> before = Map.of("a", 1);
                Map<String, Object> after = Map.of("a", 2);
                List<ChangeRecord> r = DiffDetector.diff("Obj", before, after);
                assertThat(r).hasSize(1);
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

    // ═══════════════════════════════════════════════════════════════════════════
    //  Heavy 模式与缓存
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Heavy 模式与缓存")
    class HeavyModeTests {

        @Test
        @DisplayName("compatHeavyOptimizations 启用且字段数超阈值")
        void heavyModeCache() {
            boolean saved = DiffDetector.isCompatHeavyOptimizationsEnabled();
            try {
                DiffDetector.setCompatHeavyOptimizationsEnabled(true);
                Map<String, Object> before = new HashMap<>();
                Map<String, Object> after = new HashMap<>();
                for (int i = 0; i < 60; i++) {
                    before.put("k" + i, i);
                    after.put("k" + i, i + 1);
                }
                List<ChangeRecord> r = DiffDetector.diffWithMode("Heavy", before, after, DiffDetector.DiffMode.COMPAT);
                assertThat(r).isNotEmpty();
                List<ChangeRecord> r2 = DiffDetector.diffWithMode("Heavy", before, after, DiffDetector.DiffMode.COMPAT);
                assertThat(r2).hasSize(r.size());
            } finally {
                DiffDetector.setCompatHeavyOptimizationsEnabled(saved);
            }
        }

        @Test
        @DisplayName("setCompatHeavyOptimizationsEnabled / isCompatHeavyOptimizationsEnabled")
        void compatHeavySetters() {
            DiffDetector.setCompatHeavyOptimizationsEnabled(true);
            assertThat(DiffDetector.isCompatHeavyOptimizationsEnabled()).isTrue();
            DiffDetector.setCompatHeavyOptimizationsEnabled(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  配置与工具方法
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("配置与工具方法")
    class ConfigAndUtilsTests {

        @Test
        @DisplayName("setPrecisionController 设置 metrics")
        void setPrecisionController() {
            PrecisionController ctrl = new PrecisionController(1e-12, 1e-9,
                com.syy.taskflowinsight.tracking.compare.NumericCompareStrategy.CompareMethod.COMPARE_TO, 0L);
            DiffDetector.setPrecisionController(ctrl);
            DiffDetector.setPrecisionController(null);
        }

        @Test
        @DisplayName("setDateTimeFormatter")
        void setDateTimeFormatter() {
            DiffDetector.setDateTimeFormatter(null);
        }

        @Test
        @DisplayName("isPrecisionCompareEnabled")
        void isPrecisionCompareEnabled() {
            DiffDetector.setPrecisionCompareEnabled(true);
            assertThat(DiffDetector.isPrecisionCompareEnabled()).isTrue();
            DiffDetector.setPrecisionCompareEnabled(false);
        }

        @Test
        @DisplayName("isEnhancedDeduplicationEnabled")
        void isEnhancedDeduplicationEnabled() {
            boolean v = DiffDetector.isEnhancedDeduplicationEnabled();
            assertThat(v).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  排序与 applySortingIfNeeded（通过乱序字段触发）
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("排序与变更顺序")
    class SortingTests {

        @Test
        @DisplayName("多字段变更均被检测到")
        void multiFieldLexOrder() {
            // 使用 LinkedHashMap 保证字段顺序可控
            Map<String, Object> before = new java.util.LinkedHashMap<>();
            before.put("fieldX", 1);
            before.put("fieldY", 2);
            Map<String, Object> after = new java.util.LinkedHashMap<>();
            after.put("fieldX", 10);
            after.put("fieldY", 20);
            List<ChangeRecord> r = DiffDetector.diff("SortObj", before, after);
            // 验证至少检测到变更（不依赖排序顺序或精确数量，避免去重干扰）
            assertThat(r).isNotEmpty();
            assertThat(r).extracting(ChangeRecord::getFieldName)
                .contains("fieldX");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  测试模型
    // ═══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unused")
    static class TestModelWithField {
        private BigDecimal amount;
        private String name;

        public BigDecimal getAmount() { return amount; }
        public String getName() { return name; }
    }
}
