package com.syy.taskflowinsight.tracking.compare;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * 精确定位声明类自身字段的property comparator选择器。
 *
 * <p>selector不解析display path或继承字段，因此shadowed字段不会共享注册；构造期只保留类型与字段名事实，
 * runtime执行时再按同一声明类取得Field。</p>
 *
 * @since 4.0.0
 */
public final class PropertySelector {

    /** 字段实际声明类；不能用运行时子类代替。 */
    private final Class<?> declaringClass;
    /** 声明类中的exact Java字段名，不接受dotted path或wildcard。 */
    private final String fieldName;
    /** 构造时冻结的字段类型，用于build-time扩展冲突校验。 */
    private final Class<?> fieldType;

    private PropertySelector(Class<?> declaringClass, String fieldName, Class<?> fieldType) {
        this.declaringClass = declaringClass;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
    }

    /**
     * 验证并创建exact declared-field selector。
     *
     * @param declaringClass 字段实际声明类，不能为空
     * @param fieldName exact字段名，不能为空白
     * @return 不受继承与shadowing歧义影响的selector
     */
    public static PropertySelector of(Class<?> declaringClass, String fieldName) {
        if (declaringClass == null || fieldName == null || fieldName.isBlank()) {
            throw invalidSelector();
        }
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                throw invalidSelector();
            }
            return new PropertySelector(declaringClass, fieldName, field.getType());
        } catch (NoSuchFieldException exception) {
            throw invalidSelector();
        }
    }

    /** @return 字段实际声明类 */
    public Class<?> declaringClass() {
        return declaringClass;
    }

    /** @return exact Java字段名 */
    public String fieldName() {
        return fieldName;
    }

    /** @return 构造时解析的字段类型 */
    public Class<?> fieldType() {
        return fieldType;
    }

    Field resolveField() {
        try {
            return declaringClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("validated selector field disappeared", exception);
        }
    }

    private static CompareInputException invalidSelector() {
        return new CompareInputException(InputViolation.INVALID_SELECTOR);
    }

    /**
     * 按声明类与exact字段名比较selector身份，字段类型由声明字段唯一确定。
     *
     * @param other 待比较对象
     * @return 是否表示同一个声明字段
     */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PropertySelector selector
                && declaringClass.equals(selector.declaringClass)
                && fieldName.equals(selector.fieldName);
    }

    /**
     * 返回与selector身份字段一致的稳定哈希。
     *
     * @return 声明类与字段名组合哈希
     */
    @Override
    public int hashCode() {
        return Objects.hash(declaringClass, fieldName);
    }
}
