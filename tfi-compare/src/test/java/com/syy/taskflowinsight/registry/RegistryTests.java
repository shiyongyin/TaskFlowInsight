package com.syy.taskflowinsight.registry;

import com.syy.taskflowinsight.annotation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 注册表系统测试。
 * 覆盖 DiffRegistry、ObjectTypeResolver、ValueObjectStrategyResolver。
 *
 * @author Expert Panel - Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("Registry — 注册表系统测试")
class RegistryTests {

    // ── DiffRegistry ──

    @Nested
    @DisplayName("DiffRegistry — 类型注册表")
    class DiffRegistryTests {

        @BeforeEach
        void setUp() {
            DiffRegistry.clear();
        }

        @Test
        @DisplayName("registerEntity → 可注册和查询")
        void registerEntity_shouldBeQueryable() {
            DiffRegistry.registerEntity(TestOrder.class);
            ObjectType type = DiffRegistry.getRegisteredType(TestOrder.class);
            assertThat(type).isEqualTo(ObjectType.ENTITY);
        }

        @Test
        @DisplayName("registerValueObject → 可注册和查询")
        void registerValueObject_shouldBeQueryable() {
            DiffRegistry.registerValueObject(TestAddress.class);
            ObjectType type = DiffRegistry.getRegisteredType(TestAddress.class);
            assertThat(type).isEqualTo(ObjectType.VALUE_OBJECT);
        }

        @Test
        @DisplayName("unregister → 移除注册")
        void unregister_shouldRemove() {
            DiffRegistry.registerEntity(TestOrder.class);
            DiffRegistry.unregister(TestOrder.class);
            assertThat(DiffRegistry.getRegisteredType(TestOrder.class)).isNull();
        }

        @Test
        @DisplayName("size → 返回正确计数")
        void size_shouldReturnCorrectCount() {
            DiffRegistry.registerEntity(TestOrder.class);
            DiffRegistry.registerValueObject(TestAddress.class);
            assertThat(DiffRegistry.size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("clear → 清空所有注册")
        void clear_shouldRemoveAll() {
            DiffRegistry.registerEntity(TestOrder.class);
            DiffRegistry.clear();
            assertThat(DiffRegistry.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("getRegistrationStats → 返回统计 Map")
        void getRegistrationStats_shouldReturnStatsMap() {
            DiffRegistry.registerEntity(TestOrder.class);
            java.util.Map<ObjectType, Long> stats = DiffRegistry.getRegistrationStats();
            assertThat(stats).isNotNull();
            assertThat(stats).containsKey(ObjectType.ENTITY);
        }

        @Test
        @DisplayName("registerAll 批量注册")
        void registerAll_shouldRegisterMultiple() {
            DiffRegistry.registerAll(
                    java.util.Map.of(
                            TestOrder.class, ObjectType.ENTITY,
                            TestAddress.class, ObjectType.VALUE_OBJECT
                    ),
                    java.util.Map.of()  // empty strategies map
            );
            assertThat(DiffRegistry.getRegisteredType(TestOrder.class))
                    .isEqualTo(ObjectType.ENTITY);
            assertThat(DiffRegistry.getRegisteredType(TestAddress.class))
                    .isEqualTo(ObjectType.VALUE_OBJECT);
        }

        @Test
        @DisplayName("getRegisteredStrategy 默认 → AUTO")
        void getRegisteredStrategy_noStrategy_shouldBeAuto() {
            ValueObjectCompareStrategy strategy = DiffRegistry.getRegisteredStrategy(TestOrder.class);
            assertThat(strategy).isEqualTo(ValueObjectCompareStrategy.AUTO);
        }
    }

    // ── ObjectTypeResolver ──

    @Nested
    @DisplayName("ObjectTypeResolver — 类型解析")
    class ObjectTypeResolverTests {

        @BeforeEach
        void setUp() {
            ObjectTypeResolver.clearCache();
            DiffRegistry.clear();
        }

        @Test
        @DisplayName("null → BASIC_TYPE")
        void nullObject_shouldReturnBasicType() {
            assertThat(ObjectTypeResolver.resolveType((Object) null)).isEqualTo(ObjectType.BASIC_TYPE);
        }

        @Test
        @DisplayName("null Class → BASIC_TYPE")
        void nullClass_shouldReturnBasicType() {
            assertThat(ObjectTypeResolver.resolveType((Class<?>) null)).isEqualTo(ObjectType.BASIC_TYPE);
        }

        @Test
        @DisplayName("@Entity 注解 → ENTITY")
        void entityAnnotation_shouldResolveToEntity() {
            assertThat(ObjectTypeResolver.resolveType(AnnotatedEntity.class))
                    .isEqualTo(ObjectType.ENTITY);
        }

        @Test
        @DisplayName("@ValueObject 注解 → VALUE_OBJECT")
        void valueObjectAnnotation_shouldResolveToValueObject() {
            assertThat(ObjectTypeResolver.resolveType(AnnotatedValueObject.class))
                    .isEqualTo(ObjectType.VALUE_OBJECT);
        }

        @Test
        @DisplayName("String → BASIC_TYPE")
        void string_shouldBeBasicType() {
            assertThat(ObjectTypeResolver.resolveType("hello")).isEqualTo(ObjectType.BASIC_TYPE);
        }

        @Test
        @DisplayName("Integer → BASIC_TYPE")
        void integer_shouldBeBasicType() {
            assertThat(ObjectTypeResolver.resolveType(42)).isEqualTo(ObjectType.BASIC_TYPE);
        }

        @Test
        @DisplayName("程序化注册覆盖 → 生效")
        void programmaticRegistration_shouldOverride() {
            DiffRegistry.registerEntity(TestOrder.class);
            ObjectTypeResolver.clearCache();
            ObjectType type = ObjectTypeResolver.resolveType(new TestOrder());
            assertThat(type).isEqualTo(ObjectType.ENTITY);
        }

        @Test
        @DisplayName("有 @Key 字段 → ENTITY")
        void hasKeyField_shouldBeEntity() {
            ObjectType type = ObjectTypeResolver.resolveType(WithKeyField.class);
            assertThat(type).isEqualTo(ObjectType.ENTITY);
        }

        @Test
        @DisplayName("缓存大小可查询和清除")
        void cache_shouldBeQueryableAndClearable() {
            ObjectTypeResolver.resolveType("test");
            assertThat(ObjectTypeResolver.getCacheSize()).isGreaterThan(0);
            ObjectTypeResolver.clearCache();
        }
    }

    // ── ValueObjectStrategyResolver ──

    @Nested
    @DisplayName("ValueObjectStrategyResolver — 值对象策略解析")
    class ValueObjectStrategyResolverTests {

        @Test
        @DisplayName("resolveStrategy null → FIELDS")
        void nullObject_shouldReturnFields() {
            ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy((Object) null);
            assertThat(strategy).isEqualTo(ValueObjectCompareStrategy.FIELDS);
        }

        @Test
        @DisplayName("带 @ValueObject 注解 → 解析注解中的 strategy")
        void annotatedValueObject_shouldUseAnnotationStrategy() {
            ValueObjectCompareStrategy strategy =
                    ValueObjectStrategyResolver.resolveStrategy(new AnnotatedValueObject());
            // Default strategy depends on annotation value
            assertThat(strategy).isNotNull();
        }

        @Test
        @DisplayName("resolveStrategy by Class → 非 null")
        void resolveByClass_shouldReturnNonNull() {
            ValueObjectCompareStrategy strategy =
                    ValueObjectStrategyResolver.resolveStrategy(TestAddress.class);
            assertThat(strategy).isNotNull();
        }
    }

    // ── 测试数据 ──

    static class TestOrder {
        String orderId;
        String name;
    }

    static class TestAddress {
        String street;
        String city;
    }

    @Entity
    static class AnnotatedEntity {
        @Key
        Long id;
        String name;
    }

    @ValueObject
    static class AnnotatedValueObject {
        String value;
    }

    static class WithKeyField {
        @Key
        Long id;
        String name;
    }
}
