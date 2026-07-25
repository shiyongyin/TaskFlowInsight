/**
 * 核心领域模型.
 *
 * <p>定义 Session、TaskNode、Message 等表示执行状态的领域模型，构成流程树的数据结构。
 * 这些模型是流程追踪的持久化表示，供导出和查询使用。
 *
 * <p>任务树变更 gate 与模型共置，使一个 Session 的 root/descendants 共享唯一线性化边界；锁能力保持
 * package-private，导出层只能通过 Session 的词法捕获入口读取，不能持有 permit 或形成第二个 mutation owner。
 * public {@link com.syy.taskflowinsight.model.SessionExportSnapshot} 只承载深度不可变语义；同包 capturer
 * 是唯一 mutable model traversal owner，formatter 不得重新读取 Session/TaskNode/Message。同包
 * {@code CanonicalExportV2Projection} 是唯一机器 schema tree owner，Map/JSON 只能通过 snapshot delegate 复用。
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
package com.syy.taskflowinsight.model;
