package com.syy.taskflowinsight.tracking.compare.internal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TypeDescriptor} 类元数据缓存合同。
 *
 * <p>descriptor 只由类结构与静态注解决定；同类重复扫描既无语义收益，又会放大快照路径的短命对象分配。</p>
 */
class TypeDescriptorCacheContractTests {

    @Test
    void cacheUsesClassValueWithoutStrongClassMap() {
        List<Field> staticFields = Arrays.stream(TypeDescriptor.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .toList();
        List<Field> classValues = staticFields.stream()
                .filter(field -> ClassValue.class.isAssignableFrom(field.getType()))
                .toList();
        List<Field> strongClassMaps = staticFields.stream()
                .filter(field -> Map.class.isAssignableFrom(field.getType()))
                .filter(field -> field.getGenericType().getTypeName().contains("java.lang.Class"))
                .toList();

        assertThat(classValues).extracting(Field::getName).containsExactly("DESCRIPTORS");
        assertThat(strongClassMaps).isEmpty();
    }

    @Test
    void sameClassReusesDescriptorInstance() {
        TypeDescriptor first = TypeDescriptor.describe(FirstValue.class);
        TypeDescriptor second = TypeDescriptor.describe(FirstValue.class);

        assertThat(second).isSameAs(first);
    }

    @Test
    void differentClassesKeepIndependentDescriptors() {
        TypeDescriptor first = TypeDescriptor.describe(FirstValue.class);
        TypeDescriptor second = TypeDescriptor.describe(SecondValue.class);

        assertThat(second).isNotSameAs(first);
    }

    /** 具有单个普通属性的第一种固定测试类型。 */
    private static final class FirstValue {

        /** 用于触发字段元数据解析的固定文本属性。 */
        private String value;
    }

    /** 与第一种类型结构相同但 Class 身份不同的固定测试类型。 */
    private static final class SecondValue {

        /** 用于验证缓存按 Class 隔离的固定文本属性。 */
        private String value;
    }
}
