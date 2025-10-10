package com.syy.taskflowinsight.tracking.snapshot.filter;

import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;
import com.syy.taskflowinsight.annotation.IgnoreInheritedProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClassLevelFilterEngine 单元测试
 *
 * 测试覆盖：
 * - @IgnoreDeclaredProperties 注解行为
 * - @IgnoreInheritedProperties 注解行为
 * - 包级过滤规则
 * - 优先级与覆盖规则
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
class ClassLevelFilterTests {

    // ========== 测试用例模型类 ==========

    /**
     * 空注解：忽略所有声明字段
     */
    @IgnoreDeclaredProperties
    static class IgnoreAllDeclared {
        private String field1;
        private String field2;
    }

    /**
     * 指定字段：仅忽略指定字段
     */
    @IgnoreDeclaredProperties({"sensitiveField", "internalCache"})
    static class IgnoreSpecificFields {
        private String normalField;
        private String sensitiveField;
        private String internalCache;
    }

    /**
     * 父类
     */
    static class BaseEntity {
        protected Long id;
        protected String createdAt;
    }

    /**
     * 子类：忽略继承字段
     */
    @IgnoreInheritedProperties
    static class ChildEntity extends BaseEntity {
        private String orderId;
        private String amount;
    }

    /**
     * 组合注解：既忽略声明字段又忽略继承字段
     */
    @IgnoreDeclaredProperties({"transientField"})
    @IgnoreInheritedProperties
    static class CombinedAnnotations extends BaseEntity {
        private String businessField;
        private String transientField;
    }

    /**
     * 无注解类
     */
    static class NoAnnotationClass {
        private String field1;
        private String field2;
    }

    // ========== @IgnoreDeclaredProperties 测试 ==========

    @Test
    void testIgnoreDeclaredProperties_EmptyValue_IgnoresAllDeclaredFields() throws NoSuchFieldException {
        Class<?> clazz = IgnoreAllDeclared.class;
        Field field1 = clazz.getDeclaredField("field1");
        Field field2 = clazz.getDeclaredField("field2");

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field1, null),
            "Empty @IgnoreDeclaredProperties should ignore all declared fields");
        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field2, null),
            "Empty @IgnoreDeclaredProperties should ignore all declared fields");
    }

    @Test
    void testIgnoreDeclaredProperties_SpecificFields_OnlyIgnoresListedFields() throws NoSuchFieldException {
        Class<?> clazz = IgnoreSpecificFields.class;
        Field normalField = clazz.getDeclaredField("normalField");
        Field sensitiveField = clazz.getDeclaredField("sensitiveField");
        Field internalCache = clazz.getDeclaredField("internalCache");

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, normalField, null),
            "normalField should NOT be ignored");
        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, sensitiveField, null),
            "sensitiveField should be ignored (listed in annotation)");
        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, internalCache, null),
            "internalCache should be ignored (listed in annotation)");
    }

    @Test
    void testIgnoreDeclaredProperties_DoesNotAffectInheritedFields() throws NoSuchFieldException {
        @IgnoreDeclaredProperties
        class TestClass extends BaseEntity {
            private String declaredField;
        }

        Class<?> clazz = TestClass.class;
        Field declaredField = clazz.getDeclaredField("declaredField");
        Field inheritedField = BaseEntity.class.getDeclaredField("id");

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, declaredField, null),
            "Declared field should be ignored");
        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, inheritedField, null),
            "Inherited field should NOT be affected by @IgnoreDeclaredProperties");
    }

    // ========== @IgnoreInheritedProperties 测试 ==========

    @Test
    void testIgnoreInheritedProperties_OnlyIgnoresInheritedFields() throws NoSuchFieldException {
        Class<?> clazz = ChildEntity.class;
        Field declaredField = clazz.getDeclaredField("orderId");
        Field inheritedField = BaseEntity.class.getDeclaredField("id");

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, declaredField, null),
            "Declared field should NOT be ignored");
        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, inheritedField, null),
            "Inherited field should be ignored");
    }

    @Test
    void testIgnoreInheritedProperties_DoesNotAffectDeclaredFields() throws NoSuchFieldException {
        Class<?> clazz = ChildEntity.class;
        Field orderId = clazz.getDeclaredField("orderId");
        Field amount = clazz.getDeclaredField("amount");

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, orderId, null),
            "Declared field 'orderId' should NOT be ignored");
        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, amount, null),
            "Declared field 'amount' should NOT be ignored");
    }

    // ========== 组合注解测试 ==========

    @Test
    void testCombinedAnnotations_BothAnnotationsWork() throws NoSuchFieldException {
        Class<?> clazz = CombinedAnnotations.class;
        Field businessField = clazz.getDeclaredField("businessField");
        Field transientField = clazz.getDeclaredField("transientField");
        Field inheritedField = BaseEntity.class.getDeclaredField("id");

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, businessField, null),
            "businessField should NOT be ignored (not listed in @IgnoreDeclaredProperties)");
        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, transientField, null),
            "transientField should be ignored (listed in @IgnoreDeclaredProperties)");
        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, inheritedField, null),
            "Inherited field should be ignored (@IgnoreInheritedProperties)");
    }

    // ========== 包级过滤测试 ==========

    @Test
    void testPackageLevelFilter_ExactMatch() throws NoSuchFieldException {
        Class<?> clazz = NoAnnotationClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 精确匹配包名
        String exactPackage = clazz.getPackage().getName();
        List<String> excludePackages = Collections.singletonList(exactPackage);

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Field should be ignored when package matches exactly");
    }

    @Test
    void testPackageLevelFilter_WildcardMatch() throws NoSuchFieldException {
        Class<?> clazz = NoAnnotationClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 通配符匹配 "com.syy.taskflowinsight.tracking.snapshot.filter.**"
        List<String> excludePackages = Collections.singletonList("com.syy.taskflowinsight.tracking.snapshot.filter.**");

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Field should be ignored when package matches wildcard pattern");
    }

    @Test
    void testPackageLevelFilter_WildcardSubpackages() throws NoSuchFieldException {
        Class<?> clazz = NoAnnotationClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 父包通配符 "com.syy.taskflowinsight.**"
        List<String> excludePackages = Collections.singletonList("com.syy.taskflowinsight.**");

        assertTrue(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Field should be ignored when parent package matches with wildcard");
    }

    @Test
    void testPackageLevelFilter_NoMatch() throws NoSuchFieldException {
        Class<?> clazz = NoAnnotationClass.class;
        Field field = clazz.getDeclaredField("field1");

        // 不匹配的包
        List<String> excludePackages = Arrays.asList(
            "com.example.other.**",
            "org.springframework.**"
        );

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, excludePackages),
            "Field should NOT be ignored when package doesn't match");
    }

    // ========== 边界条件测试 ==========

    @Test
    void testNullInputs_ReturnsFalse() throws NoSuchFieldException {
        Class<?> clazz = NoAnnotationClass.class;
        Field field = clazz.getDeclaredField("field1");

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(null, field, null),
            "Null class should return false");
        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, null, null),
            "Null field should return false");
        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(null, null, null),
            "Both null should return false");
    }

    @Test
    void testEmptyExcludePackages_DoesNotFilter() throws NoSuchFieldException {
        Class<?> clazz = NoAnnotationClass.class;
        Field field = clazz.getDeclaredField("field1");

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, Collections.emptyList()),
            "Empty exclude packages should not filter anything");
    }

    @Test
    void testNoAnnotation_NoPackageFilter_ReturnsFalse() throws NoSuchFieldException {
        Class<?> clazz = NoAnnotationClass.class;
        Field field = clazz.getDeclaredField("field1");

        assertFalse(ClassLevelFilterEngine.shouldIgnoreByClass(clazz, field, null),
            "No annotation and no package filter should return false");
    }
}
