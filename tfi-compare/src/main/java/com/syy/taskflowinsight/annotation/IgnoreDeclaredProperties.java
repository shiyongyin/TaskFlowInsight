package com.syy.taskflowinsight.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * IgnoreDeclaredProperties注解
 * 标记类在变更检测中忽略其声明的字段（不包括继承字段）
 *
 * 使用场景：
 * - 减少字段级@DiffIgnore样板代码
 * - 类级批量忽略内部字段（如缓存、计算字段、内部状态）
 * - 避免敏感字段遗漏
 *
 * 用法：
 * - @IgnoreDeclaredProperties() 或 @IgnoreDeclaredProperties - 忽略该类声明的所有字段
 * - @IgnoreDeclaredProperties({"field1", "field2"}) - 仅忽略指定字段
 *
 * 优先级（7级决策链）：
 * 1. Include路径白名单（最高优先级，可覆盖本注解）
 * 2. @DiffIgnore字段注解
 * 3. 路径黑名单（excludePatterns/regexExcludes）
 * 4. @IgnoreDeclaredProperties / @IgnoreInheritedProperties（本注解）
 * 5. 包级过滤（excludePackages）
 * 6. 默认忽略规则（static/transient/$jacocoData等）
 * 7. 默认保留（无匹配规则则包含）
 *
 * 注意：Include白名单可覆盖本注解（详见FAQ Q1-Q4）
 *
 * 示例：
 * <pre>
 * {@literal @}IgnoreDeclaredProperties({"internalCache", "computedValue"})
 * public class Order {
 *     private String orderId;        // 会被追踪
 *     private String internalCache;  // 被忽略（注解指定）
 *     private int computedValue;     // 被忽略（注解指定）
 * }
 *
 * {@literal @}IgnoreDeclaredProperties  // 忽略所有声明字段
 * public class InternalMetrics {
 *     private long count;            // 被忽略
 *     private long timestamp;        // 被忽略
 * }
 * </pre>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-10-09
 * @see IgnoreInheritedProperties
 * @see DiffIgnore
 * @see DiffInclude
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreDeclaredProperties {

    /**
     * 要忽略的字段名列表
     *
     * @return 字段名数组，空数组表示忽略所有声明字段
     */
    String[] value() default {};
}
