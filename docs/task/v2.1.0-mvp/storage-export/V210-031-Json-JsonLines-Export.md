# V210-031: JSON / JSONL 导出（流式 + 元数据）

- 优先级：P1  
- 预估工期：M（3–4天）  
- Phase：M1 P1  
- Owner：待定  
- 前置依赖：V210-004（Diff 输出）  
- 关联设计：`Storage & Export – JSON/JSONL`

## 目标
- 将变更集以 JSON 与 JSONL（行式）导出，支持流式写入；
- 兼容/增强模式：兼容（最小字段）/增强（含统计与 meta）；
- 严格字符转义与稳定字段顺序。

### 口径统一
- 必须与现有 `exporter.json.JsonExporter` 字段语义与顺序完全一致；
- 字段规范详见 README 的“导出字段规范（JSON/JSONL）”。

## 实现要点
- 包与类：`exporter.json.JsonLinesExporter`（新增）、现有 `JsonExporter` 增强；
- 元数据：版本、时间戳、线程信息、会话 ID（如可用），计数与耗时；
- Writer API：`export(diffList, writer, mode)`。

## 测试要点
- 兼容/增强输出对比；回归测试；字符转义；
- 大量变更的流式写入性能。

## 验收标准
- [ ] 功能/质量/性能与可观测满足全局 DoD；
- [ ] 与 Console/Text 输出并存。

## 对现有代码的影响（Impact）
- 影响级别：低到中（测试预期）。
- 行为：JSONL 为新增（可选/可延后）；JSON 保持 compat 模式；enhanced 模式新增字段但不删除旧字段。
- 对外 API：不变；建议统一 threadId 为字符串仅在 enhanced 模式，避免消费方破坏。
- 测试：导出断言需按“导出字段规范”更新（顺序/repr/敏感脱敏）；compat 测试可维持旧预期。
