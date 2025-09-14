---
id: TASK-264
title: 反射元数据缓存验证（合并版）
owner: 待指派
priority: P2
status: Planned
estimate: 1人时
dependencies: []
source:
  gpt: ../cards-gpt/CARD-264-Reflection-Metadata-Cache.md
  opus: ../cards-opus/TFI-MVP-264-cache-verification.md
---

一、合并策略
- 采纳：ConcurrentHashMap + 容量上限；computeIfAbsent 单点构建；命中/构建计数可选；并发正确性。

二、开发/测试/验收（可勾选）
- ☑ 循环访问同类字段，测量首轮 vs 后续；填满上限后行为验证；并发下正确性。

三、冲突与建议
- 无；M0 不引第三方缓存（如 Caffeine）。

