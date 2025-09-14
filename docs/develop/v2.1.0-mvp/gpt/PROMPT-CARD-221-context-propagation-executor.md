## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-221-Context-Propagation-Executor.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java#com.syy.taskflowinsight.context.TFIAwareExecutor
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext.createSnapshot()/restoreFromSnapshot(...)
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.{start,stop,track,withTracked}
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：验证 TFIAwareExecutor 上下文传播正确，子线程 start/stop 与 CHANGE 归属正确且不重复。
- 技术目标：
  - 并发 10~16 线程稳定通过；不修改 TFIAwareExecutor 实现（装饰器 + wrapRunnable/Callable）。

## 4) SCOPE
- In Scope：
  - [ ] 集成测试覆盖：主线程 start → 提交子任务 → 子线程 track/修改 → stop → 导出断言归属。
- Out of Scope：
  - [ ] 修改 TFIAwareExecutor 代码（仅使用现实现验）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 编写 IT：使用 `TFIAwareExecutor.newFixedThreadPool(n)`，n=10~16；主线程构造上下文后提交子任务。
2. 子任务：调用 `TFI.withTracked(...)` 或 `TFI.track+TFI.getChanges`；在 stop 后检查 CHANGE 归属到各自 TaskNode。
3. 断言：无重复输出；无交叉污染；执行时间合理。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：新增集成测试类，路径建议：`src/test/java/com/syy/taskflowinsight/context/TFIAwareExecutorIntegrationTests.java`。
- 测试：并发稳定性；归属正确性；去重验证。
- 文档：卡片回填测试结论要点。

## 7) API & MODELS（必须具体化）
- Executor 创建：`TFIAwareExecutor.newFixedThreadPool(int nThreads)`。
- 任务 API：`TFI.start/stop/withTracked/track/getChanges`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 并发稳定：无随机失败；线程池复用无泄漏。

## 10) TEST PLAN（可运行、可断言）
- 集成：
  - [x] 并发 10~16 线程；
  - [x] 每子任务独立 start/stop；
  - [x] CHANGE 仅在所属任务节点出现；
  - [ ] 与 withTracked 并用不重复输出。

## 11) ACCEPTANCE（核对清单，默认空）
- [x] 功能：传播与归属正确；
- [ ] 文档：结论回填；
- [x] 观测：无异常日志；
- [ ] 性能：运行时间可接受；
- [x] 风险：稳定无随机失败。

## 12) RISKS & MITIGATIONS
- 竞态：确保 latch 同起跑；任务中尽量避免长阻塞；适当超时。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 现实现满足需求；无需改 TFIAwareExecutor。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 线程池大小与任务量是否需要参数化？默认使用 10/16 验证。
- 责任人/截止日期/所需产物：待指派 / 本卡周期 / IT + 结论。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-221-context-propagation-executor.md
