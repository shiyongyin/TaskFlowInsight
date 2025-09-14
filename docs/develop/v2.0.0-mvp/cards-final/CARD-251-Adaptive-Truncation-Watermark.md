---
id: TASK-251
title: 自适应截断与水位策略验证（M1，合并版）
owner: 待指派
priority: P3
status: Deferred
estimate: 2人时
dependencies: []
source:
  gpt: ../cards-gpt/CARD-251-Adaptive-Truncation-Watermark.md
  opus: ../cards-opus/TFI-MVP-251-adaptive-truncation.md
---

一、合并策略
- 推迟到 M1；依赖变更存储/计数能力定义水位。（M0 仅固定 8192 截断）

二、开发/测试/验收（可勾选）
- ☐ 明确水位口径（会话/全局）；阈值 80%/90% 收紧至 2048/512，回落恢复。
- ☐ 构造高水位场景验证；日志/指标可读；参数可配。

三、冲突与建议
- 无（按设计推迟）。

