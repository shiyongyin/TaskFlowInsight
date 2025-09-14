---
id: TASK-211
title: 便捷 API：TFI.withTracked（合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 2人时
dependencies:
  - TASK-210
source:
  gpt: ../cards-gpt/CARD-211-TFI-Convenience-WithTracked.md
  opus: ../cards-opus/TFI-MVP-211-tfi-withtracked.md
spec_ref: ../../../specs/m2/final/TaskFlow-Insight-M2-Design.md#实现映射与集成点
---

一、合并策略与差异评估
- 采纳：两个重载（Runnable/Callable）；finally 中 getChanges→写 CHANGE→clearAll；不吞业务异常。
- 采纳：覆盖率≥80%、与 stop 去重说明（因已清理，stop 无待刷变更）。
- 调整：无需 ThreadLocal 标记；CPU 作为报告项。

二、开发目标/清单（可勾选）
- ☑ withTracked(String name, Object target, Runnable body, String... fields)
- ☐ <T> T withTracked(String name, Object target, Callable<T> body, String... fields)
- ☑ 格式化单点复用；异常仅记录内部错误。

三、测试/指标/验收/风险（可勾选）
- ☑ 正常/异常路径均完成 diff；与显式 API+stop 等价；不重复输出。
- ☑ 业务异常透明；便捷 API 开销低。

四、引用
- GPT：cards-gpt/CARD-211-TFI-Convenience-WithTracked.md
- OPUS：cards-opus/TFI-MVP-211-tfi-withtracked.md
- 设计：specs/m2/final/TaskFlow-Insight-M2-Design.md

