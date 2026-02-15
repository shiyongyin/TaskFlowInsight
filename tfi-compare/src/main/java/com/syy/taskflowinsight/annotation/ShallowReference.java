package com.syy.taskflowinsight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * ShallowReference 注解
 * 标记“仅追踪引用身份，不追踪内部属性”的浅层关联字段。
 *
 * <p>语义与行为：</p>
 * <ul>
 *   <li>仅比较关联对象的“引用键”（由 {@code @Key} 字段组成的稳定键），不产生深度字段变更（如 name/email）。</li>
 *   <li>当引用切换（Entity→Entity）、建立（null→Entity）、解除（Entity→null）时，输出 {@code referenceChange=true}，并在 {@code ReferenceDetail} 中填充 old/new 键与 null 过渡标记。</li>
 *   <li>键生成统一通过 {@code EntityKeyUtils}：优先稳定键，无法解析时降级为 {@code Class@identityHash}。</li>
 * </ul>
 *
 * <p>适用位置：</p>
 * <ul>
 *   <li>普通对象字段（如 {@code Order.customer}）。</li>
 *   <li>集合/数组/Map 的嵌套路径（如 {@code order.items[0].supplier}）。</li>
 * </ul>
 *
 * <p>对比“深度比较”：</p>
 * <table>
 *   <tr><th>模式</th><th>差异输出</th><th>性能</th></tr>
 *   <tr><td>ShallowReference</td><td>仅引用键变化（referenceChange）</td><td>O(1) 键比较，避免深度遍历</td></tr>
 *   <tr><td>深度比较</td><td>关联对象内部所有可见字段</td><td>按对象规模</td></tr>
 * </table>
 *
 * <p>注意：</p>
 * <ul>
 *   <li>若关联对象未标注 {@code @Key}，将使用受控降级标识；建议为实体补充 {@code @Key}。</li>
 *   <li>对于大型集合，请结合快照策略与集合策略控制；浅引用不会遍历关联对象内部结构。</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0
 * @since 2025-01-17
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ShallowReference {
}
