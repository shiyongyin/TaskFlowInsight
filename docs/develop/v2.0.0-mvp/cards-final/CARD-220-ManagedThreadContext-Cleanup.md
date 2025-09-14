---
id: TASK-220
title: ManagedThreadContext 关闭时清理追踪（合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 1人时
dependencies:
  - TASK-203
source:
  gpt: ../cards-gpt/CARD-220-ManagedThreadContext-Cleanup.md
  opus: ../cards-opus/TFI-MVP-220-context-cleanup.md
---

一、合并策略
- 采纳：在 ManagedThreadContext#close() 的 finally 中调用 TFI.clearAllTracking()；三处清理一致（stop/close/endSession）。
- 采纳：覆盖率≥80%、幂等与泄漏验证。

二、开发目标/清单（可勾选）
- ☑ 修改 context/ManagedThreadContext#close()，finally 调用清理；判空防御。
- ☑ 多次 close/清理幂等；注销上下文。

三、测试/指标/验收/风险（可勾选）
- ☑ 关闭后 getChanges() 为空；无 NPE。（HeapDump 暂不作为 M0 必选）
- ☑ 幂等；零串扰、零泄漏。

四、冲突与建议
- 无显著冲突；确保与 endSession 也触发清理。
