---
id: TASK-204
title: 与 TFI.stop 集成（写入 CHANGE，合并版）
owner: 待指派
priority: P1
status: Planned
estimate: 2人时
dependencies:
  - TASK-203
  - TASK-201
source:
  gpt: ../cards-gpt/CARD-204-TFI-Stop-Integration.md
  opus: ../cards-opus/TFI-MVP-204-tfi-stop-integration.md
spec_ref: ../../../specs/m2/final/TaskFlow-Insight-M2-Design.md#实现映射与集成点
---

一、合并策略与差异评估
- 采纳（GPT）：stop 尾部统一 flush（getChanges → 写 CHANGE → clearAll，try/finally 保护）；无变更不输出；与 withTracked 互斥（去重通过“已清理”）。
- 采纳（OPUS）：卡片元数据、覆盖率≥80%、DOR/DOD；格式化辅助函数建议。
- 舍弃/调整：
  - 新增 MessageType.CHANGE 的建议已不需要（枚举已存在）。
  - ThreadLocal 标记 changesProcessed 的方案改为“withTracked 已清理，因此 stop 无待刷变更”；无需额外标记。
  - 代码行号引用删除（以方法尾部为锚）。

二、开发目标（可勾选）
- ☑ 在 TFI.stop() 尾部：getChanges → 写入当前 TaskNode 的 CHANGE → clearAllTracking（try/finally）。
- ☑ 消息格式：`<Object>.<field>: <old> → <new>`（先转义后截断）。

三、开发清单（可勾选）
- ☑ 修改 api/TFI.java#stop()；新增格式化函数（单点）。
- ☑ 错误处理：handleInternalError 记录，业务不受影响。

四、测试要求（可勾选）
- ☑ 显式 API 流程：start→track→修改→stop 后 Console/JSON 出现规范 CHANGE。
- ☑ 多子任务分别 stop：归属各自 TaskNode；跨 start 的归属以刷新时刻为准。
- ☐ 与 withTracked 并用不重复输出。

五、关键指标/验收/风险（可勾选）
- ☐ 停止延迟小，无阻塞；异常不破坏主流程。
- ☑ 集成测试通过；导出结果稳定；无重复输出。
- ☑ 空上下文/NPE 防御；大量变更时依赖上游截断与配置。

六、冲突与建议（需拍板）
- 冲突：是否引入 ThreadLocal 标记避免重复？建议：不引入，依赖 withTracked finally 清理的既定语义。
- 确认：不引入，依赖 withTracked finally 清理的既定语义

七、引用
- GPT：cards-gpt/CARD-204-TFI-Stop-Integration.md
- OPUS：cards-opus/TFI-MVP-204-tfi-stop-integration.md
- 设计：specs/m2/final/TaskFlow-Insight-M2-Design.md
