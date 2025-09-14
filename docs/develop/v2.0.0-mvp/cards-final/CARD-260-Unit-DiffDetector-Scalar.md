---
id: TASK-260
title: DiffDetector 标量对比单测（合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 1人时
dependencies:
  - TASK-202
source:
  gpt: ../cards-gpt/CARD-260-Unit-DiffDetector-Scalar.md
  opus: ../cards-opus/TFI-MVP-260-unit-tests.md
---

一、合并策略
- 采纳：等价类+边界；日期基于时间戳；valueRepr 转义→截断；复杂对象不参与。
- 采纳：覆盖率≥80%。

二、开发/测试/验收（可勾选）
- ☑ null/类型变化/相等/不等 × STRING/NUMBER/BOOLEAN/DATE 用例矩阵。
- ☑ 断言 changeType/valueRepr/valueType(可选)/数量与顺序。

三、冲突与建议
- 无。

