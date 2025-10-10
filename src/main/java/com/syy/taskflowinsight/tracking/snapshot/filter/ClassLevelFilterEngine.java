package com.syy.taskflowinsight.tracking.snapshot.filter;

import com.syy.taskflowinsight.annotation.IgnoreDeclaredProperties;
import com.syy.taskflowinsight.annotation.IgnoreInheritedProperties;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 类级过滤引擎
 * 提供基于类/包级注解和配置的字段过滤能力
 *
 * 核心功能：
 * - 包级过滤（支持 ".**" 通配符）
 * - 类级注解过滤（@IgnoreDeclaredProperties）
 * - 继承字段过滤（@IgnoreInheritedProperties）
 *
 * 优先级规则（在UnifiedFilterEngine中统一处理）：
 * - 字段级@DiffInclude/@DiffIgnore优先于类级注解
 * - 类级注解优先于包级过滤
 *
 * 设计原则：
 * - 静态工具类，无状态
 * - 单一职责：仅判定是否应忽略
 * - 性能优先：注解和包匹配结果应在调用方缓存
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-10-09
 */
public final class ClassLevelFilterEngine {

    /**
     * 私有构造器，防止实例化
     */
    private ClassLevelFilterEngine() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 判断字段是否应被类级/包级规则忽略
     *
     * @param ownerClass 字段所属的类（当前对象的类）
     * @param field 待判定的字段
     * @param excludePackages 排除包列表（可为null）
     * @return true表示应忽略，false表示不应忽略
     */
    public static boolean shouldIgnoreByClass(Class<?> ownerClass, Field field, List<String> excludePackages) {
        if (ownerClass == null || field == null) {
            return false;
        }

        // 1. 包级过滤（支持 ".**" 通配）
        if (excludePackages != null && !excludePackages.isEmpty()) {
            String packageName = ownerClass.getPackage() != null
                ? ownerClass.getPackage().getName()
                : "";
            for (String pattern : excludePackages) {
                if (matchesPackage(packageName, pattern)) {
                    return true;
                }
            }
        }

        // 2. 类级注解：@IgnoreDeclaredProperties（仅对声明字段生效）
        Class<?> declaringClass = field.getDeclaringClass();
        IgnoreDeclaredProperties ignoreDeclared = ownerClass.getAnnotation(IgnoreDeclaredProperties.class);

        if (ignoreDeclared != null && declaringClass == ownerClass) {
            String[] specifiedFields = ignoreDeclared.value();

            // 空数组表示忽略所有声明字段
            if (specifiedFields == null || specifiedFields.length == 0) {
                return true;
            }

            // 非空数组表示仅忽略指定字段
            String fieldName = field.getName();
            for (String name : specifiedFields) {
                if (fieldName.equals(name)) {
                    return true;
                }
            }
        }

        // 3. 类级注解：@IgnoreInheritedProperties（仅对继承字段生效）
        IgnoreInheritedProperties ignoreInherited = ownerClass.getAnnotation(IgnoreInheritedProperties.class);
        if (ignoreInherited != null && declaringClass != ownerClass) {
            return true;
        }

        return false;
    }

    /**
     * 包名匹配
     * 支持精确匹配和 ".**" 通配符
     *
     * 匹配规则：
     * - "com.example" 仅匹配 "com.example" 包（不含子包）
     * - "com.example.**" 匹配 "com.example" 及其所有子包
     *
     * @param packageName 待匹配的包名
     * @param pattern 包名模式
     * @return true表示匹配，false表示不匹配
     */
    private static boolean matchesPackage(String packageName, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // 支持 ".**" 通配符
        if (pattern.endsWith(".**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            // 匹配包本身或子包
            return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
        }

        // 精确匹配
        return packageName.equals(pattern);
    }
}
