---
id: TASK-203
title: ChangeTracker（线程隔离追踪管理器，合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 4人时
dependencies:
  - TASK-201
  - TASK-202
source:
  gpt: ../cards-gpt/CARD-203-ChangeTracker-ThreadLocal.md
  opus: ../cards-opus/TFI-MVP-203-changetracker-threadlocal.md
spec_ref: ../../../specs/m2/final/TaskFlow-Insight-M2-Design.md#模块设计
---

一、合并策略与差异评估
- 采纳（GPT）：ThreadLocal 上下文；track/trackAll/getChanges/clearAll；三处清理点；定时清理器默认关闭；与 TFI.stop/withTracked 协同。
- 采纳（OPUS）：卡片元数据、覆盖率≥80%、DOR/DOD 口径；并发/隔离测试要求。
- 舍弃/调整：
  - 在 track 即时对比并写 pendingChanges 的方案（OPUS示例）不符合“track 捕获 before、getChanges 捕获 after”的设计，改为在 getChanges 时对比与刷新基线。
  - stop 行号依赖删除（以方法尾部为锚）。
  - CPU/“查询≤200μs”作为报告项或措辞更新。

二、开发目标（可勾选）
- ☑ ThreadLocal<Map<String, SnapshotEntry>> 维护快照基线；四个 API 完整。
- ☑ getChanges：采集 after → DiffDetector → 返回增量 → 更新基线。
- ☑ 清理：stop/close/endSession 三处触发；定时清理可选（默认关闭）。
- ☑ 元数据：sessionId/taskPath、valueReprMaxLength 透传。

三、开发清单（可勾选）
- ☑ 新增 tracking/ChangeTracker.java（幂等/并发安全）。
- ☑ 日志：WARN/DEBUG；异常交由 TFI.handleInternalError 处理。
- ☑ 同名 track 覆盖并 WARN。

四、测试要求（可勾选）
- ☐ 单元：track→修改→getChanges；多次 getChanges 仅增量；clearAll 立刻空。
- ☑ 并发：多线程隔离；TFIAwareExecutor 子线程归属正确（配合 TASK-221）。
- ☑ 生命周期：三处清理点后 getChanges 为空，幂等无异常。

五、关键指标（可勾选）
- ☐ 无内存泄漏（长时运行/HeapDump）。
- ☑ 清理路径稳定，线程池复用无串扰。

六、验收标准（可勾选）
- ☑ 单/集成测试通过；接口与文档一致。
- ☑ 与 TFI 新 API（TASK-210/211）与 stop 集成（TASK-204）协同正常。

七、风险评估（可勾选）
- ☑ ThreadLocal 泄漏：三处清理 +（可选）定时器兜底。
- ☑ 快照膨胀：clear 接口与 stop/endSession 清理。

八、核心技术设计（必读）与骨架
```java
final class ChangeTracker {
  static final ThreadLocal<Map<String, SnapshotEntry>> TL = ThreadLocal.withInitial(HashMap::new);
  static void track(String name, Object target, String... fields) { /* capture before */ }
  static List<ChangeRecord> getChanges() { /* capture after, diff, update baseline */ }
  static void clearAllTracking(){ TL.get().clear(); }
}
```

九、DOR/DOD（整合）
- DOR：TASK-201/202 完成；需求齐备。
- DOD：覆盖≥80%；并发/清理验证；日志/异常策略达成。

十、冲突与建议（需拍板）
- 冲突：是否保留 pendingChanges ThreadLocal 存放增量？建议：不保留，getChanges 即时计算 + 更新基线，减少冗余状态。
- 确认：不保留，getChanges 即时计算 + 更新基线，减少冗余状态

十一、引用
- GPT：cards-gpt/CARD-203-ChangeTracker-ThreadLocal.md
- OPUS：cards-opus/TFI-MVP-203-changetracker-threadlocal.md
- 设计：specs/m2/final/TaskFlow-Insight-M2-Design.md
