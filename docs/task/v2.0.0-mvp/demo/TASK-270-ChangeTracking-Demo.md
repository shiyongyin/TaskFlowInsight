Title: TASK-270 — ChangeTrackingDemo（显式 + 便捷 API）

一、任务背景
- 需要以最短路径向用户展示“可见价值”：在任务树中看到规范的 CHANGE 消息；并给出两种接入方式。

二、目标
- 在 `src/main/java/com/syy/taskflowinsight/demo` 下新增 `ChangeTrackingDemo.java`（或在现有 Demo 增加章节）。
- 演示显式 API 与 withTracked 便捷 API 的用法与输出片段。

三、做法
- 示例场景：订单支付（Order/Payment），演示两个字段的变更。
- 显式 API：start → track → 修改 → getChanges（可选）→ stop；
- 便捷 API：`TFI.withTracked("order", order, () -> { ... }, "status","amount");`
- 导出：Console/JSON，展示 CHANGE 消息。
 - 说明：withTracked 已在 finally 即时刷写并清理，因此 stop 阶段不会重复输出这些变更。

四、测试标准
- Demo 运行无异常；Console 输出含 CHANGE；JSON 导出符合预期。

五、验收标准
- 示例代码简洁、注释清楚；README 快速入门引用该示例。
