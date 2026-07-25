/**
 * 变更检测层：在两份快照（{@code Map<String,Object>}）之间计算差异，产出
 * {@link com.syy.taskflowinsight.tracking.model.ChangeRecord}。
 *
 * <p><b>与 compare 包的关系（为什么是相互递归）</b>：检测嵌套结构时存在领域固有的递归——
 * 本层在判断 Map/Set/Collection 字段“是否相等”时，委托给
 * {@code com.syy.taskflowinsight.tracking.compare} 的容器策略以获得深语义；而这些容器/列表策略
 * 在遇到实体值时又会回调 {@link com.syy.taskflowinsight.tracking.detector.DiffFacade} 重新进入本层。
 * 因此 detector↔compare 形成一个有意为之的“比较子系统”闭环，并非偶然耦合，不应盲目拆分。</p>
 *
 * <p><b>边界约束（已由 ArchUnit 守护）</b>：本层只允许依赖 compare 的“叶子比较原语”
 * （如数值/日期/容器相等判断），不得依赖 api / aspect / actuator 等上层；也不得把该闭环
 * 扩散到更多包。新增耦合前请先评估是否属于固有递归。</p>
 */
package com.syy.taskflowinsight.tracking.detector;
