/**
 * 比较层：面向调用方的对象/集合/Map/列表比较策略与引擎，产出
 * {@link com.syy.taskflowinsight.tracking.compare.CompareResult}。
 *
 * <p><b>与 detector 包的关系（为什么依赖它）</b>：容器与列表策略在对比“实体值”时，
 * 复用 detector 的快照差分能力（{@link com.syy.taskflowinsight.tracking.detector.DiffFacade}），
 * 避免在本层重复实现一套对象级 diff。反过来 detector 又会借用本层的容器相等判断，
 * 二者构成领域固有的相互递归（详见 detector 包的 package-info）。</p>
 *
 * <p><b>边界约束（已由 ArchUnit 守护）</b>：该 detector↔compare 闭环是唯一被允许的环，
 * 禁止扩散到其它包；本层不得依赖 api / aspect / actuator 等上层。numeric 与 temporal
 * 相等性只由 Policy/Options 驱动的 request-local differ 承载，不保留并行容差 owner。</p>
 */
package com.syy.taskflowinsight.tracking.compare;
