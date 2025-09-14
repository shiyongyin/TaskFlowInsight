## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-220-ManagedThreadContext-Cleanup.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext.close()
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.clearAllTracking()
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：确保 ManagedThreadContext#close() 的 finally 调用 TFI.clearAllTracking()；与 stop/endSession 清理一致且幂等。
- 技术目标：
  - 关闭后 getChanges() 为空；多次 close 幂等；无 NPE。

## 4) SCOPE
- In Scope：
  - [x] 在 `ManagedThreadContext.close()` finally 中调用 `TFI.clearAllTracking()`（现实现已包含，需验证）。
- Out of Scope：
  - [ ] HeapDump 等非必要重型检测。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 代码审查现有 close 实现（已在 finally 调用 `TFI.clearAllTracking()`）；保留并补充注释以标注与三处清理策略一致性。
2. 测试：构造上下文→开始任务→追踪→close→断言清空；重复 close 幂等。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：必要注释；无语义更改。
- 测试：生命周期清理测试覆盖 close 路径（可与 262 复用）。
- 文档：卡片回填。

## 7) API & MODELS（必须具体化）
- 接口：`public void ManagedThreadContext.close()`。
- 异常策略：清理异常仅 DEBUG，不冒泡。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 可靠性：多次 close 幂等；线程池复用场景无残留。

## 10) TEST PLAN（可运行、可断言）
- 集成：
  - [x] 创建 context→start→track→close；close 后 `TFI.getChanges()` 返回空集合；
  - [x] 多次 close 不抛异常。

## 11) ACCEPTANCE（核对清单，默认空）
- [x] 功能：三处清理一致；
- [ ] 文档：卡片更新；
- [ ] 观测：无 WARN/ERROR 级别异常；
- [ ] 性能：close 开销可接受；
- [ ] 风险：无泄漏。

## 12) RISKS & MITIGATIONS
- 泄漏：遗漏 clearAllTracking → 已在 finally 覆盖；测试兜底。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 现实现已符合卡片；仅补充测试与注释。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要在 Close 时额外输出统计日志？当前仅 DEBUG。
- 责任人/截止日期/所需产物：待指派 / 本卡周期 / 测试+注释。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-220-managedthreadcontext-cleanup.md
