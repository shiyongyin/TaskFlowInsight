## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../cards-final/CARD-221-Context-Propagation-Executor.md
- AI Guide：../cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java#com.syy.taskflowinsight.context.TFIAwareExecutor
  - src/main/java/com/syy/taskflowinsight/context/SafeContextManager.java#wrapRunnable(java.lang.Runnable), wrapCallable(java.util.concurrent.Callable)
- 工程操作规范：../开发工程师提示词.txt

## 3) GOALS（卡片→可执行目标）
- 业务目标：在异步线程中保持上下文与变更归属正确，验证 CHANGE 挂载到子任务 TaskNode，且不重复输出。
- 技术目标：使用现有 TFIAwareExecutor 包装线程池，编写集成测试覆盖 10~16 线程并发场景。

## 4) SCOPE
- In Scope：
  - [ ] 新增集成测试用例，使用 TFIAwareExecutor.newFixedThreadPool
  - [ ] 每个子任务：start("sub-i") → track/修改 → stop；主线程导出断言归属
- Out of Scope：
  - [ ] 修改 TFIAwareExecutor 行为

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 受影响模块与文件：仅新增测试文件。
2. 新建测试类与方法：
```java
class ContextPropagationIT {
  @Test void childTasks_have_own_CHANGE_messages() { /* 实现见补丁 */ }
}
```
3. 自测：多次运行用例，确保无随机失败。
4. 文档：卡片勾选；如遇歧义记录在“冲突与建议”。

## 6) DELIVERABLES（输出必须包含）
- 测试代码：ContextPropagationIT.java（完整内容）。
- 文档：卡片勾选。

## 7) API & MODELS（必须具体化）
- 使用 API：TFI.start/TFI.stop + track/getChanges（卡210）；导出：TFI.exportToJson/ConsoleExporter。

## 8) DATA & STORAGE
- 无。

## 9) PERFORMANCE & RELIABILITY
- 稳定性：10~16 线程并发无交叉污染与随机失败。

## 10) TEST PLAN（可运行、可断言）
- 集成测试：
  - [ ] 使用 TFIAwareExecutor 提交 N 个子任务；每个子任务创建不同对象并修改；stop 后断言子节点 messages 中存在 CHANGE 且不重复

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：归属正确且不重复输出
- [ ] 文档：卡片勾选
- [ ] 观测：无随机失败

## 12) RISKS & MITIGATIONS
- 线程复用造成串扰 → 220/262 清理保障。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 无。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 并发参数 N 的基线值（建议 8/16 各一组）。

