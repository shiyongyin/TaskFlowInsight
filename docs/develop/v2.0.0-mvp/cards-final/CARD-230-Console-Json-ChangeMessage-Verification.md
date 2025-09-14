---
id: TASK-230
title: 导出验证：Console/JSON 含 CHANGE（合并版）
owner: 待指派
priority: P2
status: Planned
estimate: 1人时
dependencies:
  - TASK-204
source:
  gpt: ../cards-gpt/CARD-230-Console-Json-ChangeMessage-Verification.md
  opus: ../cards-opus/TFI-MVP-230-export-verification.md
---

一、合并策略
- 采纳：无需改导出器；验证 Console/JSON 含 CHANGE；避免硬编码 nodeId，容忍时间戳差异。
 - 口径修正：Console 标签为中文+emoji（来源 MessageType.displayName），断言时不依赖固定文案；JSON 断言基于 `messages[].type == "CHANGE"`。

二、开发/测试/验收（可勾选）
- ☑ 集成用例：start→track→修改→stop→导出；断言 Console/JSON 中的 CHANGE。
- ☑ 输出片段差异定位明确；展示稳定。
- ☐ Console：使用正则匹配 `<obj>.<field>: <old> → <new>` 格式；标签断言基于 `getDisplayLabel()` 或 `type==CHANGE` 而非固定字符串。
- ☐ JSON：断言 `messages[].type=="CHANGE"` 且 `content` 符合格式；threadId 在 COMPAT 模式容忍数值/字符串双口径。

三、冲突与建议
- 无；格式细则以 TASK-263 为准（转义→截断、空值 null）。
