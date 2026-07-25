package com.syy.taskflowinsight.tracking.ssot.key;

import com.syy.taskflowinsight.annotation.Key;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Entity key 反射元数据的 ClassLoader 生命周期合同。
 *
 * <p>实体 Class 只能由 {@link ClassValue} 持有，避免进程级 Map 阻止可卸载 ClassLoader 回收。</p>
 */
class EntityKeyClassCacheContractTests {

    @Test
    void classMetadataUsesOneClassValueWithoutStrongClassMap() {
        List<Field> staticFields = Arrays.stream(EntityKeyUtils.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .toList();
        List<Field> classValues = staticFields.stream()
                .filter(field -> ClassValue.class.isAssignableFrom(field.getType()))
                .toList();
        List<Field> strongClassMaps = staticFields.stream()
                .filter(field -> Map.class.isAssignableFrom(field.getType()))
                .filter(field -> field.getGenericType().getTypeName().contains("java.lang.Class"))
                .toList();

        assertThat(classValues).extracting(Field::getName).hasSize(1);
        assertThat(strongClassMaps).isEmpty();
    }

    @Test
    void keyFieldsRetainHierarchyOrderAndImmutablePublicProjection() {
        ChildEntity entity = new ChildEntity("tenant-a", 42L, "ignored");

        List<Field> first = EntityKeyUtils.collectKeyFields(ChildEntity.class);
        List<Field> second = EntityKeyUtils.collectKeyFields(ChildEntity.class);

        assertThat(first).extracting(Field::getName)
                .containsExactly("tenantId", "entityId");
        assertThat(second).isEqualTo(first);
        assertThatThrownBy(() -> first.add(first.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(EntityKeyUtils.tryComputeStableKey(entity))
                .contains("tenantId=tenant-a|entityId=42");
        assertThat(EntityKeyUtils.tryComputeCompactKey(entity)).contains("tenant-a:42");
    }

    /** 带父类业务键的测试实体，固定继承层级扫描顺序。 */
    private static class ParentEntity {

        /** 租户业务标识，必须先于子类键进入复合键。 */
        @Key
        private final String tenantId;

        private ParentEntity(String tenantId) {
            this.tenantId = tenantId;
        }
    }

    /** 同时包含业务键与普通属性的子类测试实体。 */
    private static final class ChildEntity extends ParentEntity {

        /** 实体在租户内的数值标识。 */
        @Key
        private final long entityId;

        /** 非键属性，用于证明扫描不会扩大字段集合。 */
        private final String description;

        private ChildEntity(String tenantId, long entityId, String description) {
            super(tenantId);
            this.entityId = entityId;
            this.description = description;
        }
    }
}
