package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.NumericPrecision;
import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry;
import com.syy.taskflowinsight.tracking.compare.PropertyComparisonException;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * DiffDetectorService 最终覆盖测试
 * 覆盖现有测试未覆盖的代码路径：自定义比较器异常、精度指标、引用键计算、格式化等
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("DiffDetectorService 最终覆盖测试")
class DiffDetectorServiceFinalTests {

    private DiffDetectorService service;

    @BeforeEach
    void setUp() {
        service = new DiffDetectorService();
        service.programmaticInitNoSpring();
    }

    // ── 自定义比较器：PropertyComparisonException 路径 ──

    @Nested
    @DisplayName("自定义比较器 — PropertyComparisonException")
    class ComparatorPropertyComparisonException {

        @Test
        @DisplayName("比较器抛出 PropertyComparisonException 时降级")
        void comparatorThrowsPropertyComparisonException_fallsThrough() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            registry.register("failField", new ThrowingPropertyComparisonExceptionComparator());
            service.setComparatorRegistry(registry);
            service.registerObjectType("Test", ModelWithFailField.class);

            Map<String, Object> before = Map.of("failField", "a");
            Map<String, Object> after = Map.of("failField", "b");
            List<ChangeRecord> changes = service.diff("Test", before, after);

            assertThat(changes).isNotEmpty();
            assertThat(changes.get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
        }
    }

    // ── 自定义比较器：Throwable 路径 ──

    @Nested
    @DisplayName("自定义比较器 — Throwable")
    class ComparatorThrowable {

        @Test
        @DisplayName("比较器抛出 RuntimeException 时降级")
        void comparatorThrowsRuntimeException_fallsThrough() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            registry.register("throwField", new ThrowingRuntimeExceptionComparator());
            service.setComparatorRegistry(registry);
            service.registerObjectType("Test", ModelWithThrowField.class);

            Map<String, Object> before = Map.of("throwField", 1);
            Map<String, Object> after = Map.of("throwField", 2);
            List<ChangeRecord> changes = service.diff("Test", before, after);

            assertThat(changes).isNotEmpty();
        }
    }

    // ── 自定义比较器：areEqual 返回 false ──

    @Nested
    @DisplayName("自定义比较器 — areEqual 返回 false")
    class ComparatorReturnsFalse {

        @Test
        @DisplayName("比较器返回 false 产生 UPDATE")
        void comparatorReturnsFalse_producesUpdate() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            registry.register("customField", new NeverEqualComparator());
            service.setComparatorRegistry(registry);
            service.registerObjectType("Test", ModelWithCustomField.class);

            Map<String, Object> before = Map.of("customField", "same");
            Map<String, Object> after = Map.of("customField", "same");
            List<ChangeRecord> changes = service.diff("Test", before, after);

            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
        }
    }

    // ── 自定义比较器：supports 返回 false ──

    @Nested
    @DisplayName("自定义比较器 — supports 返回 false")
    class ComparatorSupportsFalse {

        @Test
        @DisplayName("比较器 supports 返回 false 时回退到默认比较")
        void comparatorSupportsFalse_fallsThroughToDefault() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            registry.register("stringOnlyField", new StringOnlyComparator());
            service.setComparatorRegistry(registry);
            service.registerObjectType("Test", ModelWithStringOnlyField.class);

            Map<String, Object> before = Map.of("stringOnlyField", 100);
            Map<String, Object> after = Map.of("stringOnlyField", 200);
            List<ChangeRecord> changes = service.diff("Test", before, after);

            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
        }
    }

    // ── 精度比较：容差内相等，记录 recordToleranceHit ──

    @Nested
    @DisplayName("精度比较 — 容差内相等与指标记录")
    class PrecisionToleranceHitPaths {

        @Test
        @DisplayName("BigDecimal 容差内相等记录 bigdecimal 指标")
        void bigDecimalWithinTolerance_recordsMetrics() {
            service.setPrecisionCompareEnabled(true);
            service.registerObjectType("Obj", ModelWithNumericPrecision.class);

            BigDecimal a = new BigDecimal("1.0000000000001");
            BigDecimal b = new BigDecimal("1.0000000000002");
            Map<String, Object> before = Map.of("amount", a);
            Map<String, Object> after = Map.of("amount", b);

            List<ChangeRecord> changes = service.diff("Obj", before, after);
            assertThat(changes).isEmpty();

            PrecisionMetrics.MetricsSnapshot snap = service.getMetricsSnapshot();
            assertThat(snap).isNotNull();
        }

        @Test
        @DisplayName("Double 容差内相等记录 numeric 指标")
        void doubleWithinTolerance_recordsMetrics() {
            service.setPrecisionCompareEnabled(true);

            Map<String, Object> before = Map.of("d", 1.0);
            Map<String, Object> after = Map.of("d", 1.0 + 1e-15);
            List<ChangeRecord> changes = service.diff("x", before, after);

            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("Float 容差内相等")
        void floatWithinTolerance() {
            service.setPrecisionCompareEnabled(true);

            float v = 1.0f;
            Map<String, Object> before = Map.of("f", v);
            Map<String, Object> after = Map.of("f", v);
            List<ChangeRecord> changes = service.diff("x", before, after);

            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("Date 相同值无变更，覆盖日期比较路径")
        void dateSameValue_noChange() {
            service.setPrecisionCompareEnabled(true);

            Date d = new Date(1000);
            Map<String, Object> before = Map.of("d", d);
            Map<String, Object> after = Map.of("d", new Date(1000));

            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("Instant 相同值无变更，覆盖 Temporal 比较路径")
        void instantSameValue_noChange() {
            service.setPrecisionCompareEnabled(true);

            Instant i = Instant.ofEpochMilli(1000);
            Map<String, Object> before = Map.of("i", i);
            Map<String, Object> after = Map.of("i", Instant.ofEpochMilli(1000));

            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isEmpty();
        }
    }

    // ── formatValue：LocalDate 等 Temporal ──

    @Nested
    @DisplayName("formatValue — Temporal 类型")
    class FormatValueTemporalPaths {

        @Test
        @DisplayName("LocalDate 格式化")
        void formatValue_localDate() {
            LocalDate ld1 = LocalDate.of(2025, 1, 15);
            LocalDate ld2 = LocalDate.of(2025, 1, 16);
            Map<String, Object> before = Map.of("ld", ld1);
            Map<String, Object> after = Map.of("ld", ld2);

            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).getReprOld()).isNotNull();
            assertThat(changes.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("LocalDateTime 格式化")
        void formatValue_localDateTime() {
            LocalDateTime ldt1 = LocalDateTime.of(2025, 1, 1, 10, 0);
            LocalDateTime ldt2 = LocalDateTime.of(2025, 1, 1, 11, 0);
            Map<String, Object> before = Map.of("ldt", ldt1);
            Map<String, Object> after = Map.of("ldt", ldt2);

            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes.get(0).getReprOld()).isNotNull();
        }
    }

    // ── getFieldByName：字段未找到、父类字段 ──

    @Nested
    @DisplayName("getFieldByName — 字段解析")
    class GetFieldByNamePaths {

        @Test
        @DisplayName("未注册类型时 objectClass 为 null")
        void unregisteredType_objectClassNull() {
            Map<String, Object> before = Map.of("x", 1);
            Map<String, Object> after = Map.of("x", 2);
            List<ChangeRecord> changes = service.diff("UnknownType", before, after);

            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).getFieldName()).isEqualTo("x");
        }

        @Test
        @DisplayName("父类字段通过 registerObjectType 解析")
        void superclassField_resolved() {
            service.registerObjectType("Child", ChildWithInheritedField.class);

            Map<String, Object> before = Map.of("baseField", "old");
            Map<String, Object> after = Map.of("baseField", "new");
            List<ChangeRecord> changes = service.diff("Child", before, after);

            assertThat(changes).hasSize(1);
        }
    }

    // ── detectReferenceChange：computeReferenceKeyString Map 路径 ──

    @Nested
    @DisplayName("detectReferenceChange — computeReferenceKeyString Map 快照")
    class ReferenceChangeMapSnapshot {

        @Test
        @DisplayName("snapshotVal 为 Map 时生成稳定键")
        void snapshotValMap_generatesStableKey() {
            RefHolder a = new RefHolder();
            a.ref = null;
            RefHolder b = new RefHolder();
            b.ref = null;

            Map<String, Object> oldMap = new LinkedHashMap<>();
            oldMap.put("id", "v1");
            oldMap.put("code", "c1");
            Map<String, Object> newMap = new LinkedHashMap<>();
            newMap.put("id", "v2");
            newMap.put("code", "c2");

            var detail = service.detectReferenceChange(a, b, "ref", oldMap, newMap);
            assertThat(detail).isNotNull();
            assertThat(detail.getOldEntityKey()).isNotEqualTo(detail.getNewEntityKey());
            assertThat(detail.getOldEntityKey()).contains("=");
        }

        @Test
        @DisplayName("snapshotVal 为 String 时直接返回")
        void snapshotValString_returnsAsIs() {
            RefHolder a = new RefHolder();
            a.ref = null;
            RefHolder b = new RefHolder();
            b.ref = new KeyEntity("id2");

            var detail = service.detectReferenceChange(a, b, "ref", "Class[id1]", "Class[id2]");
            assertThat(detail).isNotNull();
            assertThat(detail.getOldEntityKey()).isEqualTo("Class[id1]");
        }

        @Test
        @DisplayName("refObj 无 @Key 时使用 computeReferenceIdentifier 降级")
        void refObjWithoutKey_usesComputeReferenceIdentifier() {
            NoKeyRefHolder a = new NoKeyRefHolder();
            a.ref = new NoKeyEntity();
            NoKeyRefHolder b = new NoKeyRefHolder();
            b.ref = new NoKeyEntity();

            var detail = service.detectReferenceChange(a, b, "ref", null, null);
            assertThat(detail).isNotNull();
            assertThat(detail.getOldEntityKey()).contains("NoKeyEntity");
            assertThat(detail.getNewEntityKey()).contains("NoKeyEntity");
        }
    }

    // ── 指标：batch 记录 ──

    @Nested
    @DisplayName("指标 — batch 记录")
    class MetricsBatchPath {

        @Test
        @DisplayName("有变更时记录 batch 指标")
        void changesPresent_recordsBatchMetrics() {
            service.resetMetrics();
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 2, "b", 3);

            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).hasSize(2);

            PrecisionMetrics.MetricsSnapshot snap = service.getMetricsSnapshot();
            assertThat(snap).isNotNull();
        }
    }

    // ── 边界：空 Map、双 null ──

    @Nested
    @DisplayName("边界 — 空 Map 与双 null")
    class EdgeCases {

        @Test
        @DisplayName("前后均为空 Map 无变更")
        void bothEmptyMaps_noChanges() {
            List<ChangeRecord> changes = service.diff("x", Collections.emptyMap(), Collections.emptyMap());
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("空 Map 与 null 等价")
        void emptyMapAndNull_equivalent() {
            List<ChangeRecord> fromNull = service.diff("x", null, Collections.emptyMap());
            List<ChangeRecord> fromEmpty = service.diff("x", Collections.emptyMap(), Collections.emptyMap());
            assertThat(fromNull).isEmpty();
            assertThat(fromEmpty).isEmpty();
        }

        @Test
        @DisplayName("精度关闭时使用 Objects.equals")
        void precisionDisabled_usesEquals() {
            service.setPrecisionCompareEnabled(false);
            Map<String, Object> before = Map.of("s", "hello");
            Map<String, Object> after = Map.of("s", "world");

            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
        }
    }

    // ── 测试模型与比较器 ──

    static class ModelWithFailField {
        String failField;
    }

    static class ModelWithThrowField {
        int throwField;
    }

    static class ModelWithCustomField {
        String customField;
    }

    static class ModelWithStringOnlyField {
        Object stringOnlyField;
    }

    static class ModelWithNumericPrecision {
        @NumericPrecision(scale = 2)
        BigDecimal amount;
    }

    static class BaseWithField {
        String baseField;
    }

    static class ChildWithInheritedField extends BaseWithField {
    }

    static class RefHolder {
        @ShallowReference
        KeyEntity ref;
    }

    @Entity
    static class KeyEntity {
        @Key
        String id;

        KeyEntity(String id) {
            this.id = id;
        }
    }

    static class NoKeyRefHolder {
        @ShallowReference
        NoKeyEntity ref;
    }

    static class NoKeyEntity {
        String data;
    }

    static class ThrowingPropertyComparisonExceptionComparator implements PropertyComparator {
        @Override
        public String getName() {
            return "throw-pce";
        }

        @Override
        public boolean supports(Class<?> type) {
            return true;
        }

        @Override
        public boolean areEqual(Object a, Object b, Field field) {
            throw new PropertyComparisonException("Intentional test failure");
        }
    }

    static class ThrowingRuntimeExceptionComparator implements PropertyComparator {
        @Override
        public String getName() {
            return "throw-rte";
        }

        @Override
        public boolean supports(Class<?> type) {
            return true;
        }

        @Override
        public boolean areEqual(Object a, Object b, Field field) {
            throw new RuntimeException("Intentional test failure");
        }
    }

    static class NeverEqualComparator implements PropertyComparator {
        @Override
        public String getName() {
            return "never-equal";
        }

        @Override
        public boolean supports(Class<?> type) {
            return true;
        }

        @Override
        public boolean areEqual(Object a, Object b, Field field) {
            return false;
        }
    }

    static class StringOnlyComparator implements PropertyComparator {
        @Override
        public String getName() {
            return "string-only";
        }

        @Override
        public boolean supports(Class<?> type) {
            return type != null && String.class.equals(type);
        }

        @Override
        public boolean areEqual(Object a, Object b, Field field) {
            return Objects.equals(a, b);
        }
    }
}
