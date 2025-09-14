---
id: TASK-221
title: 上下文传播（TFIAwareExecutor）验证（合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 2人时
dependencies:
  - TASK-203
source:
  gpt: ../cards-gpt/CARD-221-Context-Propagation-Executor.md
  opus: ../cards-opus/TFI-MVP-221-context-propagation.md
---

一、合并策略
- 采纳：TFIAwareExecutor 传播快照；子线程 start/stop；CHANGE 归属正确且不重复。
- 采纳：并发 10~16 线程稳定通过；不修改 TFIAwareExecutor 实现。

二、开发/测试/验收（可勾选）
- ☑ 集成测试：主线程 start → 提交子任务 → 子任务 track/修改 → stop → 导出断言归属。
- ☑ 并发场景无交叉污染、无随机失败（以当前用例稳定通过为准）。

三、冲突与建议
- 无；注意 withTracked 已清理，stop 不重复输出。
