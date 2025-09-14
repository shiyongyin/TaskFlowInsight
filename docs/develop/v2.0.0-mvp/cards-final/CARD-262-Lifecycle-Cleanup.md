---
id: TASK-262
title: 生命周期清理测试（stop/close/endSession，合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 1人时
dependencies:
  - TASK-203
  - TASK-220
source:
  gpt: ../cards-gpt/CARD-262-Lifecycle-Cleanup.md
  opus: ../cards-opus/TFI-MVP-262-lifecycle-tests.md
---

一、合并策略
- 采纳：三处清理一致且幂等；getChanges 为空；无 NPE；长时运行无泄漏。

二、开发/测试/验收（可勾选）
- ☑ 依次触发三处清理并断言空集合；计数器/HeapDump 观测。

三、冲突与建议
- 无。

