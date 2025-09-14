## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-204-TFI-Stop-Integration.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.stop()
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.flushChangesToCurrentTask()
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.formatChangeMessage(com.syy.taskflowinsight.tracking.model.ChangeRecord)
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.formatValue(Object)
  - src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java#com.syy.taskflowinsight.tracking.ChangeTracker.getChanges()
  - src/main/java/com/syy/taskflowinsight/enums/MessageType.java#com.syy.taskflowinsight.enums.MessageType.CHANGE
- 相关配置：
  - src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java#tfi.change-tracking.enabled, valueReprMaxLength
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：在 TFI.stop 尾部统一 flush（getChanges→写 CHANGE→clearAll），无变更不输出；与 withTracked 互斥（去重通过 finally 已清理）。
- 技术目标：
  - 消息格式固定：`<Object>.<field>: <old> → <new>`；先转义后截断（委托 ObjectSnapshot.repr）。

## 4) SCOPE
- In Scope：
  - [x] 校验并完善 `TFI.stop()` 尾部 flush+clearAll；try/finally 保护。
  - [x] `formatChangeMessage` 与 `formatValue` 的口径与 201/202 对齐（DELETE 场景 old→new 表现由 Diff 提供 new=null）。
- Out of Scope：
  - [ ] 枚举新增（MessageType.CHANGE 已存在）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 校对 `TFI.stop()` 现实现：已在 try 中 `flushChangesToCurrentTask()`，finally 中 `ChangeTracker.clearAllTracking()`；保持语义。
2. 校验 `formatChangeMessage` 是否使用 `ObjectSnapshot.repr` 先转义后截断（当前通过 `formatValue` 委托）。
3. 编写集成测试：start→track→修改→stop 后 Console/JSON 出现规范 CHANGE；多子任务分别 stop 归属正确。
4. 补充 README 片段：说明 stop 触发时机与 withTracked 互斥关系。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：如需，仅为 `TFI.stop()/formatChangeMessage` 的微调与注释补强；默认不改动行为。
- 测试：
  - 集成：多任务/多变更的 CHANGE 展示；
  - 与 withTracked 并用不重复输出（withTracked 已清理，stop 无待刷）。
- 文档：卡片回填与 README 更新。
- 回滚/灰度：通过禁用 `tfi.change-tracking.enabled=false`。
- 观测：异常 `handleInternalError`，不影响 stop 主流程。

## 7) API & MODELS（必须具体化）
- 接口：`public static void TFI.stop()`。
- 消息格式：`<Object>.<field>: <old> → <new>`；值转义+截断由 `ObjectSnapshot.repr` 实现。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 停止延迟：flush 不应阻塞；异常不破坏主流程。

## 10) TEST PLAN（可运行、可断言）
- 集成：
  - [x] start→track→修改→stop → Console/JSON 含 CHANGE；
  - [x] 多子任务分别 stop，归属各自 TaskNode；
  - [ ] withTracked 已清理，stop 不重复输出。

## 11) ACCEPTANCE（核对清单，默认空）
- [x] 功能：flush+clearAll 行为稳定；无重复输出。
- [ ] 文档：README 更新 stop 行为与互斥语义。
- [x] 观测：异常不冒泡。
- [ ] 性能：停止延迟可接受。
- [x] 风险：空上下文/NPE 防御完善。

## 12) RISKS & MITIGATIONS
- 重复输出：与 withTracked 互斥；以 finally 清理语义为准。
- NPE 风险：缺当前任务或会话时跳过写入。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 现实现已符合卡片；保持仅在 stop 尾部 flush，避免早刷导致重复。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要在 stop 返回值上提供“是否有变更刷写”的指示？当前无返回值。
- 责任人/截止日期/所需产物：待指派 / 本卡周期 / 代码+测试+文档。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-204-tfi-stop-integration.md
