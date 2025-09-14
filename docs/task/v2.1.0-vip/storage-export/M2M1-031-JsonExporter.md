# storage-export - M2M1-031-JsonExporter（VIP 合并版）

1. 目标与范围
- 业务目标：提供稳定、统一的 JSON 导出；JSONL 行式作为可选增强或延期项。
- Out of Scope：复杂格式（HTML/XML）。

2. A/B 对比与取舍
- A 组优点：提出统一字段规范与 compat/enhanced 双模式；JSONL 可选。
  - 参考：`../../v2.1.0-mvp/storage-export/V210-031-Json-JsonLines-Export.md` 与 README“导出字段规范”。
- B 组优点：聚焦 JSON 导出实现与验证。
  - 参考：`../../v2.1.1-mvp/tasks/M2M1-031-JsonExporter.md`

3. 最终设计（融合后）
3.1 字段规范
- 与 README 统一：threadId 字符串、valueKind/valueRepr、路径字典序、敏感脱敏；compat 最小字段，enhanced 增强不删旧。
3.2 模式
- compat 默认；enhanced 可配置；JSONL 可选或延期。

4. 与代码差异与改造清单
- JsonExporter 与 JsonLinesExporter 共享字段序列化策略；
- 测试：compat 保守断言、enhanced 新增断言；
- 迁移：threadId 字符串仅在 enhanced 引入，避免破坏消费方。

5. 开放问题与后续项
- JSONL 是否在本里程碑落地，视消费方需求决定。

---

