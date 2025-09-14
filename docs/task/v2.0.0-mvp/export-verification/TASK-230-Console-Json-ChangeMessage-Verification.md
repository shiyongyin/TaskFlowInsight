Title: TASK-230 — 导出验证：Console/JSON 含 CHANGE 消息

一、任务背景
- M0 无需修改导出器；验证 CHANGE 消息在 ConsoleExporter/JsonExporter 中可见且格式正确。

二、目标
- 增加集成测试：执行典型流程后导出到 Console（字符串）与 JSON，断言输出包含规范化 CHANGE。

三、做法
- 使用已有导出器：`exporter/text/ConsoleExporter` 与 `exporter/json/JsonExporter`。
- 校验规则：
  - Console：包含行 `Order.status: PENDING → PAID`（或相似）
  - JSON：messages 数组中存在 `{"type":"CHANGE","content":"..."}`

四、测试标准
- 用例通过；容忍空白/时间戳差异；避免硬编码 nodeId。

五、验收标准
- 导出结果稳定；若失败提供输出片段定位问题。

