## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-262-Lifecycle-Cleanup.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.stop(), com.syy.taskflowinsight.api.TFI.endSession()
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext.close()
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：stop/close/endSession 三处清理一致且幂等，防止泄漏与串扰。
- 技术目标：为三处清理编写集成测试并断言 getChanges 返回空、重复调用幂等。

## 4) SCOPE
- In Scope：
  - [ ] 新增 LifecycleCleanupIT 用例
- Out of Scope：
  - [ ] 业务代码修改（如已在 204/220/210 完成）

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 测试类：LifecycleCleanupIT。
2. 用例：
   - stop 后 getChanges 空；
   - ManagedThreadContext.close() 后 getChanges 空；
   - TFI.endSession() 后兜底清理（如未清理时）。
3. 自测：`./mvnw test`。

## 6) DELIVERABLES（输出必须包含）
- 测试代码：LifecycleCleanupIT.java。
- 文档：卡片勾选。

## 7) API & MODELS（必须具体化）
- API：TFI.stop/TFI.endSession，ManagedThreadContext.close。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 幂等与稳定。

## 10) TEST PLAN（可运行、可断言）
- 集成测试：
  - [ ] 三处清理后 getChanges 为空；重复调用无异常

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：一致清理与幂等
- [ ] 文档：卡片勾选

## 12) RISKS & MITIGATIONS
- 某处未集成清理 → 定位并在对应卡补齐。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] endSession 的清理触发点是否已集成？（若未集成需在 210/204 加入）

