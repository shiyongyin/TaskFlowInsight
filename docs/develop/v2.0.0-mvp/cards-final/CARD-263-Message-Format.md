---
id: TASK-263
title: 变更消息格式测试（合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 1人时
dependencies:
  - TASK-204
source:
  gpt: ../cards-gpt/CARD-263-Message-Format.md
  opus: ../cards-opus/TFI-MVP-263-message-format.md
---

一、合并策略
- 采纳：格式 `<Object>.<field>: <old> → <new>`；先转义后截断；空值 null；正则断言；Console/JSON 一致。
 - 口径修正：Console 标签为中文+emoji（`MessageType.getDisplayName()`），测试不应硬编码英文 `[CHANGE]`；JSON 用 `type=="CHANGE"` 判断。

二、开发/测试/验收（可勾选）
- ☑ 正则匹配通过；特殊字符场景（换行/引号）稳定；与导出一致。
 - ☐ Console：断言 `message.getDisplayLabel()` 或 `message.isChange()`；不依赖具体文案。
 - ☐ JSON：断言 `messages[].type=="CHANGE"` 与内容格式。

三、冲突与建议
- 无。
