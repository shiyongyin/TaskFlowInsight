---
id: TASK-270
title: ChangeTrackingDemo（显式 + 便捷 API，合并版）
owner: 待指派
priority: P2
status: Planned
estimate: 2人时
dependencies:
  - TASK-204
  - TASK-211
source:
  gpt: ../cards-gpt/CARD-270-ChangeTracking-Demo.md
  opus: ../cards-opus/TFI-MVP-270-demo.md
---

一、合并策略
- 采纳：在 demo 包增加示例；显式 API 与 withTracked 并排演示；Console/JSON 展示 CHANGE。
- 说明：withTracked 已在 finally 刷写并清理，因此 stop 不重复输出这些变更。

二、开发/测试/验收（可勾选）
- ☑ Demo 可运行；输出含 CHANGE；JSON 导出符合预期；注释清楚。

三、冲突与建议
- 无。

