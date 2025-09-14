## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-261-Concurrency-Isolation.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java#com.syy.taskflowinsight.context.TFIAwareExecutor
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.{start,stop,track,withTracked}
  - src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java#THREAD_SNAPSHOTS(ThreadLocal)
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）

## 3) GOALS（卡片→可执行目标）
- 业务目标：10~16 线程并发场景下，每子任务独立 start/stop，归属正确、不重复、无交叉污染。

## 4) SCOPE
- In Scope：
  - [ ] 并发集成测试；CountDownLatch 同起跑；每线程产出各自 CHANGE。
- Out of Scope：
  - [ ] 修改核心实现。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新增 IT：`src/test/java/com/syy/taskflowinsight/concurrency/IsolationIntegrationTests.java`。
2. 使用 `TFIAwareExecutor.newFixedThreadPool(16)`；主线程 start 后提交 N 个子任务；子任务内 track→修改→stop。
3. 导出并断言每子任务的 CHANGE 与其任务名/路径对应；无跨任务消息。

## 6) DELIVERABLES（输出必须包含）
- 测试：并发隔离集成测试；
- 文档：卡片回填。

## 7) API & MODELS（必须具体化）
- `TFI.start/stop/withTracked/track/getChanges`；`TFIAwareExecutor.newFixedThreadPool`。

## 8) DATA & STORAGE
- N/A。

## 9) PERFORMANCE & RELIABILITY
- 稳定：无随机失败；执行时间可控；无死锁。

## 10) TEST PLAN（可运行、可断言）
- 集成：
  - [ ] 16 线程；
  - [ ] 每任务 1~2 个字段变更；
  - [ ] CHANGE 仅在所属任务节点出现；
  - [ ] 无重复输出。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：并发隔离通过；
- [ ] 文档：结论回填；
- [ ] 观测：无异常日志；
- [ ] 性能：可接受；
- [ ] 风险：无串扰。

## 12) RISKS & MITIGATIONS
- 竞态：使用闩锁协调；任务中尽量避免共享可变状态。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 与实现一致：仅验证。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 线程任务数与字段数量是否需要参数化？默认固定值即可。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-261-concurrency-isolation.md

