Title: CARD-270 — ChangeTrackingDemo（显式 + 便捷 API）

一、开发目标
- ☐ 在 `src/main/java/com/syy/taskflowinsight/demo` 下新增 `ChangeTrackingDemo.java`（或在现有 Demo 增加章节）。
- ☐ 演示两种接入方式：显式 API 与 `withTracked` 便捷 API；输出展示 CHANGE 消息。

二、开发清单
- ☐ 示例场景：订单支付（Order/Payment），演示两个字段变更（如 status、amount）。
- ☐ 显式 API：`start` → `track` → 修改 → 可选 `getChanges` → `stop`。
- ☐ 便捷 API：`TFI.withTracked("order", order, () -> { ... }, "status","amount");`
- ☐ 导出：Console/JSON；说明 withTracked 已在 finally 刷写并清理，stop 不重复输出这些变更。

三、测试要求
- ☐ Demo 运行无异常；Console 输出含 CHANGE；JSON 导出符合预期。

四、关键指标
- ☐ 示例简洁、注释清楚；便于快速入门。

五、验收标准
- ☐ README 快速入门引用该示例；与文档描述一致。

六、风险评估
- ☐ 示例偏离真实场景：确保选用贴近业务的简单模型，并覆盖异常路径示例。

七、核心技术设计（必读）
- ☐ Demo 结构：
  - ☐ 显式 API 流程与便捷 API 流程并排展示；演示 stop 刷新与 withTracked 即时刷新差异。
- ☐ 导出：
  - ☐ Console 简化/完整两种展示；JSON 导出包含 messages。

八、核心代码说明（示例）
```java
TFI.start("order-pay");
try {
  TFI.track("order", order, "status","amount");
  order.setStatus("PAID"); order.setAmount(order.getAmount().add(BigDecimal.ONE));
} finally { TFI.stop(); }

TFI.withTracked("order", order, () -> {
  order.setStatus("REFUNDING");
}, "status");
```
