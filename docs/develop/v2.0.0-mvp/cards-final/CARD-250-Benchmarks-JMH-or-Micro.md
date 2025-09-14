---
id: TASK-250
title: 基准测试（JMH/微基准，合并版）
owner: 待指派
priority: P2
status: Planned
estimate: 3人时
dependencies: []
source:
  gpt: ../cards-gpt/CARD-250-Benchmarks-JMH-or-Micro.md
  opus: ../cards-opus/TFI-MVP-250-benchmarks.md
---

一、合并策略
- 采纳：JMH 优先；2/20 字段、8/16 线程；报告 P95 延迟；记录机器/参数。
- 调整：CPU 占比作为报告项，非 M0 硬门槛。

二、开发/测试/验收（可勾选）
- ☑ 基准类覆盖 track→修改→getChanges→clearAll；Warmup/Measurement 固定。
- ☑ 提交报告；未达建议目标附改进计划与 Issue。

三、冲突与建议
- 无。

