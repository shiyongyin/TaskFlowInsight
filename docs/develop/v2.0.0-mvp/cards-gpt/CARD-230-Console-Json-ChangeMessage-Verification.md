Title: CARD-230 — 导出验证：Console/JSON 含 CHANGE 消息

一、开发目标
- ☐ 验证 M0 下无需修改导出器即可看到标准化 CHANGE 消息。

二、开发清单
- ☐ 使用 `exporter/text/ConsoleExporter` 与 `exporter/json/JsonExporter`。
- ☐ 集成测试构造典型流程：start → track → 修改 → stop → 导出。
- ☐ 校验规则：
  - ☐ Console：包含行 `Object.field: old → new`（考虑转义/截断后表现）。
  - ☐ JSON：`messages` 数组存在 `{ "type": "CHANGE", "content": "..." }`。

三、测试要求
- ☐ 容忍空白/时间戳差异；避免硬编码 nodeId。
- ☐ 输出片段差异定位明确。

四、关键指标
- ☐ 导出结果稳定；在不同并发/层级结构下展示一致。

五、验收标准
- ☐ 用例通过；导出器无需改动即可展示 CHANGE。

六、风险评估
- ☐ 内容转义/截断对展示影响：对照 TASK-263 的格式规范进行断言。

七、核心技术设计（必读）
- ☐ ConsoleExporter：树/缩进样式打印 messages；CHANGE 通过 `MessageType.CHANGE` 展示，包含时间戳可选。
- ☐ JsonExporter：将 TaskNode.messages 序列化为 `[{"type":"CHANGE","content":"...","timestamp":...}]`。
- ☐ 兼容性：不改导出器，仅验证 CHANGE 可见性与格式稳定。

八、核心代码说明（示例断言）
```java
String console = new ConsoleExporter().export(session, false);
assertTrue(console.contains("order.status: PENDING → PAID"));

String json = new JsonExporter().export(session);
assertTrue(json.contains("\"type\":\"CHANGE\""));
```
