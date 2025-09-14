---
id: TASK-210
title: TFI 新增 4 个核心 API（合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 2人时
dependencies:
  - TASK-203
source:
  gpt: ../cards-gpt/CARD-210-TFI-APIs-Track-GetClear.md
  opus: ../cards-opus/TFI-MVP-210-tfi-apis.md
spec_ref: ../../../specs/m2/final/TaskFlow-Insight-M2-Design.md#实现映射与集成点
---

一、合并策略与差异评估
- 采纳：新增 4 个静态方法委托 ChangeTracker；双层开关（全局 TFI 开关 + tfi.change-tracking.enabled）。
- 采纳：异常通过 handleInternalError 处理；Javadoc 完整；覆盖率≥80%。
- 调整：CPU 作为报告项；无持久化相关指标。

二、开发目标（可勾选）
- ☑ public static void track(String name, Object target, String... fields)
- ☑ public static void trackAll(Map<String,Object> targets)
- ☑ public static List<ChangeRecord> getChanges()
- ☑ public static void clearAllTracking()

三、开发清单（可勾选）
- ☑ 参数校验/开关快速返回；try/catch 代理 ChangeTracker 调用。
- ☐ Javadoc：行为/参数/禁用时表现。

四、测试要求/关键指标/验收/风险（可勾选）
- ☑ 禁用状态快速返回；异常不冒泡；调用开销低。
- ☑ 与 ChangeTracker 协作正确；文档一致；异常分支覆盖。
- ☐ 风险：门面遗漏异常分支 → 单测覆盖。

五、引用
- GPT：cards-gpt/CARD-210-TFI-APIs-Track-GetClear.md
- OPUS：cards-opus/TFI-MVP-210-tfi-apis.md
- 设计：specs/m2/final/TaskFlow-Insight-M2-Design.md
