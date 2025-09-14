## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-261-Concurrency-Isolation.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java#com.syy.taskflowinsight.context.TFIAwareExecutor
  - src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java#wrapRunnable, wrapCallable
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：验证多线程并发下追踪数据隔离与归属正确性。
- 技术目标：10~16 线程并发，CountDownLatch 同步起跑，子任务独立 start/stop，导出断言无交叉污染。

## 4) SCOPE
- In Scope：
  - [ ] 新增测试类 ConcurrencyIsolationIT
- Out of Scope：
  - [ ] 修改执行器行为

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 新建测试类位置：`src/test/java/com/syy/taskflowinsight/…`（tests 包结构对齐现有）。
2. 用例：并发 N=10/16；每个子任务修改不同对象字段；stop → 导出 → 断言。
3. 自测：反复运行确保稳定。

## 6) DELIVERABLES（输出必须包含）
- 测试代码：ConcurrencyIsolationIT.java。
- 文档：卡片勾选。

## 7) API & MODELS（必须具体化）
- API：TFI.start/stop、TFI.track；导出：TFI.exportToJson。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 稳定性：无随机失败；执行时间可接受。

## 10) TEST PLAN（可运行、可断言）
- 集成测试：
  - [ ] N 线程并发；每个子任务的 TaskNode.messages 仅包含本子任务的 CHANGE

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：隔离正确
- [ ] 文档：卡片勾选

## 12) RISKS & MITIGATIONS
- 线程复用导致泄漏 → 220/262 清理保障。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要将执行时间/失败重试次数作为测试阈值记录？

