package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.annotation.ShallowReference;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.PropertyComparator;
import com.syy.taskflowinsight.tracking.compare.PropertyComparatorRegistry;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.precision.PrecisionMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * DiffDetectorService 白盒测试
 * 覆盖 diff、detectChangeWithPrecision、formatValue、getValueKind、detectReferenceChange、
 * computeReferenceKeyString、getFieldByName、精度比较、PropertyComparatorRegistry 等路径
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("DiffDetectorService 白盒测试")
class DiffDetectorServiceWhiteBoxTests {

    private DiffDetectorService service;

    @BeforeEach
    void setUp() {
        service = new DiffDetectorService();
        service.programmaticInitNoSpring();
    }

    // ── 构造与初始化 ──

    @Nested
    @DisplayName("构造与初始化")
    class ConstructorAndInit {

        @Test
        @DisplayName("无参构造器创建实例")
        void constructor_createsInstance() {
            DiffDetectorService svc = new DiffDetectorService();
            assertThat(svc).isNotNull();
        }

        @Test
        @DisplayName("programmaticInitNoSpring 不抛异常")
        void programmaticInitNoSpring_noThrow() {
            assertThatCode(() -> service.programmaticInitNoSpring()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("init 后精度比较默认启用")
        void init_precisionEnabledByDefault() {
            DiffDetectorService svc = new DiffDetectorService();
            assertThat(svc.isPrecisionCompareEnabled()).isTrue();
        }
    }

    // ── diff 方法：所有 value kinds ──

    @Nested
    @DisplayName("diff 各值类型路径")
    class DiffValueKinds {

        @Test
        @DisplayName("null before 使用空 Map")
        void diff_nullBefore() {
            Map<String, Object> after = Map.of("a", 1);
            List<ChangeRecord> changes = service.diff("x", null, after);
            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).getChangeType()).isEqualTo(ChangeType.CREATE);
        }

        @Test
        @DisplayName("null after 使用空 Map")
        void diff_nullAfter() {
            Map<String, Object> before = Map.of("a", 1);
            List<ChangeRecord> changes = service.diff("x", before, null);
            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).getChangeType()).isEqualTo(ChangeType.DELETE);
        }

        @Test
        @DisplayName("String 类型变更")
        void diff_string() {
            Map<String, Object> before = Map.of("s", "old");
            Map<String, Object> after = Map.of("s", "new");
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "STRING".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("Number 类型变更")
        void diff_number() {
            Map<String, Object> before = Map.of("n", 1);
            Map<String, Object> after = Map.of("n", 2);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "NUMBER".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("Boolean 类型变更")
        void diff_boolean() {
            Map<String, Object> before = Map.of("b", true);
            Map<String, Object> after = Map.of("b", false);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "BOOLEAN".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("Date 类型变更")
        void diff_date() {
            Date d1 = new Date(1000);
            Date d2 = new Date(2000);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "DATE".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("Enum 类型变更")
        void diff_enum() {
            Map<String, Object> before = Map.of("e", TestEnum.A);
            Map<String, Object> after = Map.of("e", TestEnum.B);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "ENUM".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("Collection 类型变更")
        void diff_collection() {
            Map<String, Object> before = Map.of("c", List.of(1));
            Map<String, Object> after = Map.of("c", List.of(2));
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "COLLECTION".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("Map 类型变更")
        void diff_map() {
            Map<String, Object> before = Map.of("m", Map.of("k", "v1"));
            Map<String, Object> after = Map.of("m", Map.of("k", "v2"));
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "MAP".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("Array 类型变更")
        void diff_array() {
            Map<String, Object> before = Map.of("a", new int[]{1});
            Map<String, Object> after = Map.of("a", new int[]{2});
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "ARRAY".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("OTHER 类型变更")
        void diff_other() {
            Map<String, Object> before = Map.of("o", new CustomObject("x"));
            Map<String, Object> after = Map.of("o", new CustomObject("y"));
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> "OTHER".equals(c.getValueKind()));
        }

        @Test
        @DisplayName("双 null 无变更")
        void diff_bothNull_noChange() {
            Map<String, Object> before = new HashMap<>();
            before.put("f", null);
            Map<String, Object> after = new HashMap<>();
            after.put("f", null);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isEmpty();
        }

        @Test
        @DisplayName("CREATE 变更")
        void diff_create() {
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 1, "b", 2);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.CREATE && "b".equals(c.getFieldName()));
        }

        @Test
        @DisplayName("DELETE 变更")
        void diff_delete() {
            Map<String, Object> before = Map.of("a", 1, "b", 2);
            Map<String, Object> after = Map.of("a", 1);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.DELETE && "b".equals(c.getFieldName()));
        }

        @Test
        @DisplayName("UPDATE 变更")
        void diff_update() {
            Map<String, Object> before = Map.of("a", 1);
            Map<String, Object> after = Map.of("a", 2);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).anyMatch(c -> c.getChangeType() == ChangeType.UPDATE);
        }

        @Test
        @DisplayName("结果按字段名排序")
        void diff_sortedByFieldName() {
            Map<String, Object> before = Map.of("z", 1, "a", 2);
            Map<String, Object> after = Map.of("z", 2, "a", 3);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isSortedAccordingTo(Comparator.comparing(ChangeRecord::getFieldName));
        }
    }

    // ── 精度比较 ──

    @Nested
    @DisplayName("精度比较路径")
    class PrecisionComparePaths {

        @Test
        @DisplayName("setPrecisionCompareEnabled 关闭时使用 equals")
        void precisionDisabled_usesEquals() {
            service.setPrecisionCompareEnabled(false);
            Map<String, Object> before = Map.of("n", 1.0000000001);
            Map<String, Object> after = Map.of("n", 1.0000000002);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("BigDecimal 精度比较路径")
        void bigDecimal_precisionPath() {
            service.setPrecisionCompareEnabled(true);
            Map<String, Object> before = Map.of("bd", new BigDecimal("1.00"));
            Map<String, Object> after = Map.of("bd", new BigDecimal("1.01"));
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("Float 精度比较路径")
        void float_precisionPath() {
            service.setPrecisionCompareEnabled(true);
            Map<String, Object> before = Map.of("f", 1.0f);
            Map<String, Object> after = Map.of("f", 1.0000001f);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("Double 精度比较路径")
        void double_precisionPath() {
            service.setPrecisionCompareEnabled(true);
            Map<String, Object> before = Map.of("d", 1.0);
            Map<String, Object> after = Map.of("d", 1.00000000001);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("Date 精度比较路径")
        void date_precisionPath() {
            service.setPrecisionCompareEnabled(true);
            Date d1 = new Date(1000);
            Date d2 = new Date(1001);
            Map<String, Object> before = Map.of("d", d1);
            Map<String, Object> after = Map.of("d", d2);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("Instant 精度比较路径")
        void instant_precisionPath() {
            service.setPrecisionCompareEnabled(true);
            Instant i1 = Instant.ofEpochMilli(1000);
            Instant i2 = Instant.ofEpochMilli(2000);
            Map<String, Object> before = Map.of("i", i1);
            Map<String, Object> after = Map.of("i", i2);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isNotNull();
        }

        @Test
        @DisplayName("LocalDateTime 精度比较路径")
        void localDateTime_precisionPath() {
            service.setPrecisionCompareEnabled(true);
            LocalDateTime l1 = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime l2 = LocalDateTime.of(2025, 1, 1, 0, 1);
            Map<String, Object> before = Map.of("l", l1);
            Map<String, Object> after = Map.of("l", l2);
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes).isNotNull();
        }
    }

    // ── formatValue 路径（通过 diff 的 reprOld/reprNew）──

    @Nested
    @DisplayName("formatValue 路径")
    class FormatValuePaths {

        @Test
        @DisplayName("Date 格式化")
        void formatValue_date() {
            Date d = new Date(1234567890000L);
            Map<String, Object> before = Map.of("d", d);
            Map<String, Object> after = Map.of("d", new Date(1234567891000L));
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes.get(0).getReprOld()).isNotNull();
            assertThat(changes.get(0).getReprNew()).isNotNull();
        }

        @Test
        @DisplayName("Temporal 格式化")
        void formatValue_temporal() {
            Instant i = Instant.ofEpochMilli(1000);
            Map<String, Object> before = Map.of("i", i);
            Map<String, Object> after = Map.of("i", Instant.ofEpochMilli(2000));
            List<ChangeRecord> changes = service.diff("x", before, after);
            assertThat(changes.get(0).getReprOld()).isNotNull();
        }
    }

    // ── 注册对象类型与父类字段解析 ──

    @Nested
    @DisplayName("registerObjectType 与父类字段")
    class RegisterAndSuperclass {

        @Test
        @DisplayName("registerObjectType 注册类型")
        void registerObjectType() {
            service.registerObjectType("User", UserWithSuperField.class);
            Map<String, Object> before = Map.of("baseField", "old");
            Map<String, Object> after = Map.of("baseField", "new");
            List<ChangeRecord> changes = service.diff("User", before, after);
            assertThat(changes).isNotEmpty();
        }

        @Test
        @DisplayName("registerObjectType null 参数忽略")
        void registerObjectType_nullIgnored() {
            assertThatCode(() -> {
                service.registerObjectType(null, String.class);
                service.registerObjectType("x", null);
            }).doesNotThrowAnyException();
        }
    }

    // ── getMetricsSnapshot / resetMetrics ──

    @Nested
    @DisplayName("getMetricsSnapshot 与 resetMetrics")
    class MetricsPaths {

        @Test
        @DisplayName("getMetricsSnapshot 返回快照")
        void getMetricsSnapshot() {
            service.diff("x", Map.of("a", 1), Map.of("a", 2));
            PrecisionMetrics.MetricsSnapshot snap = service.getMetricsSnapshot();
            assertThat(snap).isNotNull();
        }

        @Test
        @DisplayName("resetMetrics 重置")
        void resetMetrics() {
            service.diff("x", Map.of("a", 1), Map.of("a", 2));
            service.resetMetrics();
            PrecisionMetrics.MetricsSnapshot snap = service.getMetricsSnapshot();
            assertThat(snap).isNotNull();
        }
    }

    // ── PropertyComparatorRegistry 交互 ──

    @Nested
    @DisplayName("PropertyComparatorRegistry 交互")
    class ComparatorRegistryPaths {

        @Test
        @DisplayName("setComparatorRegistry 注入注册表")
        void setComparatorRegistry() {
            PropertyComparatorRegistry registry = new PropertyComparatorRegistry();
            registry.register("customField", new AlwaysEqualComparator());
            service.setComparatorRegistry(registry);
            Map<String, Object> before = Map.of("customField", "a");
            Map<String, Object> after = Map.of("customField", "b");
            service.registerObjectType("Test", TestModelWithCustomField.class);
            List<ChangeRecord> changes = service.diff("Test", before, after);
            assertThat(changes).isNotNull();
        }
    }

    // ── detectReferenceChange 与 computeReferenceKeyString ──

    @Nested
    @DisplayName("detectReferenceChange 与 computeReferenceKeyString")
    class ReferenceChangePaths {

        @Test
        @DisplayName("非 @ShallowReference 字段返回 null")
        void detectReferenceChange_nonShallowRef_returnsNull() {
            var detail = service.detectReferenceChange(
                new PlainObject(), new PlainObject(), "field", "old", "new");
            assertThat(detail).isNull();
        }

        @Test
        @DisplayName("@ShallowReference 字段且键相同返回 null")
        void detectReferenceChange_sameKey_returnsNull() {
            RefHolder a = new RefHolder();
            a.ref = new KeyEntity("id1");
            RefHolder b = new RefHolder();
            b.ref = new KeyEntity("id1");
            var detail = service.detectReferenceChange(a, b, "ref", "id1", "id1");
            assertThat(detail).isNull();
        }

        @Test
        @DisplayName("@ShallowReference 字段且键不同返回 ReferenceDetail")
        void detectReferenceChange_differentKey_returnsDetail() {
            RefHolder a = new RefHolder();
            a.ref = new KeyEntity("id1");
            RefHolder b = new RefHolder();
            b.ref = new KeyEntity("id2");
            var detail = service.detectReferenceChange(a, b, "ref", "id1", "id2");
            assertThat(detail).isNotNull();
            assertThat(detail.getOldEntityKey()).contains("id1");
            assertThat(detail.getNewEntityKey()).contains("id2");
        }

        @Test
        @DisplayName("null 引用变更")
        void detectReferenceChange_nullRef() {
            RefHolder a = new RefHolder();
            a.ref = null;
            RefHolder b = new RefHolder();
            b.ref = new KeyEntity("id1");
            var detail = service.detectReferenceChange(a, b, "ref", null, "id1");
            assertThat(detail).isNotNull();
            assertThat(detail.isNullReferenceChange()).isTrue();
        }

        @Test
        @DisplayName("root 为 null 时跳过")
        void detectReferenceChange_nullRoot_skipped() {
            var detail = service.detectReferenceChange(null, null, "ref", "old", "new");
            assertThat(detail).isNull();
        }
    }

    // ── 测试模型类 ──

    enum TestEnum { A, B }

    static class CustomObject {
        String v;
        CustomObject(String v) { this.v = v; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomObject that = (CustomObject) o;
            return Objects.equals(v, that.v);
        }
        @Override
        public int hashCode() { return Objects.hash(v); }
    }

    static class BaseWithField {
        String baseField;
    }

    static class UserWithSuperField extends BaseWithField {}

    static class TestModelWithCustomField {
        String customField;
    }

    static class PlainObject {
        String field;
    }

    static class RefHolder {
        @ShallowReference
        KeyEntity ref;
    }

    static class KeyEntity {
        @com.syy.taskflowinsight.annotation.Key
        String id;
        KeyEntity(String id) { this.id = id; }
    }

    static class AlwaysEqualComparator implements PropertyComparator {
        @Override
        public String getName() { return "always-equal"; }
        @Override
        public boolean supports(Class<?> type) { return true; }
        @Override
        public boolean areEqual(Object a, Object b, Field field) { return true; }
    }
}
