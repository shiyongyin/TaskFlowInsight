package com.syy.taskflowinsight.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * IgnoreInheritedProperties注解
 * 标记类在变更检测中忽略从父类继承的字段
 *
 * 使用场景：
 * - 仅关注子类新增字段的变更
 * - 忽略框架基类字段（如审计字段、版本号等）
 * - 减少噪音，聚焦业务字段
 *
 * 用法：
 * - @IgnoreInheritedProperties - 忽略所有从父类继承的字段
 *
 * 优先级（7级决策链）：
 * 1. Include路径白名单（最高优先级，可覆盖本注解）
 * 2. @DiffIgnore字段注解
 * 3. 路径黑名单（excludePatterns/regexExcludes）
 * 4. @IgnoreInheritedProperties / @IgnoreDeclaredProperties（本注解）
 * 5. 包级过滤（excludePackages）
 * 6. 默认忽略规则（static/transient/$jacocoData等）
 * 7. 默认保留（无匹配规则则包含）
 *
 * 注意：Include白名单可覆盖本注解（详见FAQ Q1-Q4）
 *
 * 示例：
 * <pre>
 * public class BaseEntity {
 *     protected Long id;
 *     protected Date createdAt;
 *     protected Date updatedAt;
 * }
 *
 * {@literal @}IgnoreInheritedProperties
 * public class Order extends BaseEntity {
 *     private String orderId;        // 会被追踪（声明字段）
 *     private BigDecimal amount;     // 会被追踪（声明字段）
 *     // id, createdAt, updatedAt 被忽略（继承字段）
 * }
 * </pre>
 *
 * 注意：
 * - 此注解仅影响继承字段，不影响当前类声明的字段
 * - 如需同时忽略声明字段，请组合使用@IgnoreDeclaredProperties
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-10-09
 * @see IgnoreDeclaredProperties
 * @see DiffIgnore
 * @see DiffInclude
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreInheritedProperties {
}
