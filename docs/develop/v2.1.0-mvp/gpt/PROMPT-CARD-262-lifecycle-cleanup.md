## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-262-Lifecycle-Cleanup.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.{stop,endSession,clearAllTracking}
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext.close()
  - src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java#com.syy.taskflowinsight.tracking.ChangeTracker.clearAllTracking()
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：验证 stop/close/endSession 三处清理一致且幂等；后续 getChanges 为空；长时运行无泄漏。

## 4) SCOPE
- In Scope：
  - [ ] 三处清理触发后的行为验证；异常仅 DEBUG。
- Out of Scope：
  - [ ] HeapDump 等重型验证（可选）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 增加 IT：构建追踪后依次调用 stop/close/endSession；在每一步后断言 `TFI.getChanges()` 为空。
2. 重复调用清理，验证幂等。

## 6) DELIVERABLES（输出必须包含）
- 测试：`src/test/java/com/syy/taskflowinsight/lifecycle/CleanupIntegrationTests.java`（建议路径）。
- 文档：卡片回填。

## 7) API & MODELS（必须具体化）
- `TFI.stop()` / `TFI.endSession()` / `ManagedThreadContext.close()`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 清理路径稳定；线程池复用不串扰；无 NPE。

## 10) TEST PLAN（可运行、可断言）
- 集成：
  - [ ] 依次触发三处清理并断言空集合；
  - [ ] 重复调用幂等；
  - [ ] 观测 DEBUG 日志无 ERROR。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：三处清理一致；
- [ ] 文档：结论回填；
- [ ] 观测：无 ERROR；
- [ ] 性能：N/A；
- [ ] 风险：无泄漏。

## 12) RISKS & MITIGATIONS
- 误判：测试前先 clear 确保基线一致。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 现实现符合要求；仅验证。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要在长时压测下收集更详细的 GC/内存数据？本卡可选。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-262-lifecycle-cleanup.md

