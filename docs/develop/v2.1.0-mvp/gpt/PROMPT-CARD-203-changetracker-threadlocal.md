## 1) SYSTEM
你是资深 Java 开发工程师与 AI 结对编程引导者。你需要基于下述“上下文与证据”，按步骤完成实现并给出可提交的变更（代码/测试/文档）。

## 2) CONTEXT & SOURCES（务必具体）
- 任务卡：../../v2.0.0-mvp/cards-final/CARD-203-ChangeTracker-ThreadLocal.md
- AI Guide：../../v2.0.0-mvp/cards-final/AI-DEVELOPMENT-GUIDE.md
- 相关代码：
  - src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java#com.syy.taskflowinsight.tracking.ChangeTracker.{track,trackAll,getChanges,clearAllTracking}
  - src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java#com.syy.taskflowinsight.tracking.detector.DiffDetector.diff(String,Map,Map)
  - src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java#com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot.capture(String,Object,String...)
  - src/main/java/com/syy/taskflowinsight/api/TFI.java#com.syy.taskflowinsight.api.TFI.{track,trackAll,getChanges,clearAllTracking,withTracked}
  - src/main/java/com/syy/taskflowinsight/context/ManagedThreadContext.java#com.syy.taskflowinsight.context.ManagedThreadContext.{endSession,startTask,endTask}
  - src/main/java/com/syy/taskflowinsight/context/TFIAwareExecutor.java#com.syy.taskflowinsight.context.TFIAwareExecutor
- 相关配置：
  - src/main/java/com/syy/taskflowinsight/config/ChangeTrackingProperties.java#tfi.change-tracking.enabled, valueReprMaxLength, cleanupIntervalMinutes
- 工程操作规范：../../开发工程师提示词.txt（必须遵循）
- 历史提示词风格参考：../../v1.0.0-mvp/design/api-implementation/prompts/DEV-010-TFI-MainAPI-Prompt.md

## 3) GOALS（卡片→可执行目标）
- 业务目标：提供线程隔离的对象变更追踪，支持 track/trackAll/getChanges/clearAllTracking，三处清理（stop/close/endSession）一致。
- 技术目标：
  - ThreadLocal<Map<String,SnapshotEntry>> 维护基线快照；getChanges 即时 capture-after+diff+更新基线。
  - 并发隔离、幂等清理；异常交由 TFI.handleInternalError，内部仅 DEBUG。

## 4) SCOPE
- In Scope：
  - [ ] 同名 track 覆盖并 WARN；WeakReference 保存 target 防泄漏。
  - [ ] getChanges 返回增量并更新基线；空时返回空集合。
  - [ ] clearAllTracking 使用 ThreadLocal.remove() 避免线程池残留。
- Out of Scope：
  - [ ] 定时清理器（默认关闭）。

## 5) CODING PLAN（按《开发工程师提示词.txt》的动作顺序）
1. 校核 ChangeTracker 现有实现是否完全符合卡片；必要时补强日志级别、ThreadLocal.remove()、WeakReference 与注释。
2. 方法签名参考（已存在）：
```java
public final class ChangeTracker {
  public static void track(String name, Object target, String... fields);
  public static void trackAll(Map<String,Object> targets);
  public static java.util.List<ChangeRecord> getChanges();
  public static void clearAllTracking();
}
```
3. 与 TFI API 协同：TFI.stop()/endSession()/ManagedThreadContext.close() 三处清理生效；withTracked finally 清理避免重复输出。
4. 自测：`./mvnw test`；编写并运行并发/生命周期测试（参见 221/262）。

## 6) DELIVERABLES（输出必须包含）
- 代码改动：完善/注释 ChangeTracker；确保 remove() 与 WeakReference 使用；WARN/DEBUG 语义一致。
- 测试：
  - 并发隔离：不同线程各自追踪，互不串扰；
  - 生命周期：stop/close/endSession 三处后 getChanges 为空、幂等；
  - API 流程：track→修改→getChanges 仅增量，重复 getChanges 不重复产出。
- 文档：卡片回填说明；README 片段补充清理语义。
- 回滚/灰度：禁用开关 `TFI.setChangeTrackingEnabled(false)` 路径验证。
- 观测：WARN（同名覆盖）、DEBUG（捕获/对比异常）。

## 7) API & MODELS（必须具体化）
- 接口：详见上文签名；不新增对外 API。
- 异常映射：所有异常在内部 DEBUG；TFI 门面负责对外 handleInternalError。

## 8) DATA & STORAGE
- 无落盘；ThreadLocal 生命周期管理。

## 9) PERFORMANCE & RELIABILITY
- 性能预算：仅标量快照；增量对比；不维护 pendingChanges 快照，避免冗余状态。
- 可靠性：ThreadLocal.remove() 防线程池残留；WeakReference 避免目标长期持有。

## 10) TEST PLAN（可运行、可断言）
- 单元：
  - [ ] track→修改→getChanges；
  - [ ] 多次 getChanges 仅增量；
  - [ ] clearAllTracking 立即清空。
- 集成：
  - [ ] TFIAwareExecutor 子线程归属；
  - [ ] stop/close/endSession 三处清理后为空。
- 性能：
  - [ ] 简单循环 100 次，记录 P95（可选）。

## 11) ACCEPTANCE（核对清单，默认空）
- [ ] 功能：四 API 均符合预期；并发隔离；清理幂等。
- [ ] 文档：卡片/README 更新。
- [ ] 观测：WARN/DEBUG 语义正确。
- [ ] 性能：路径稳定、无内存泄漏。
- [ ] 风险：线程池复用无串扰。

## 12) RISKS & MITIGATIONS
- 泄漏风险：未 remove 的 ThreadLocal → 使用 remove() 而非 clear()。
- 膨胀风险：基线快照过大 → 仅标量/按字段追踪。

## 13) DIFFERENCES & SUGGESTIONS（文档 vs 代码冲突）
- 现实现与卡片一致：getChanges 即时计算 + 更新基线，未持久化 pendingChanges；保持此决策。

## 14) OPEN QUESTIONS & ACTIONS（必须列出）
- [ ] 是否需要对同名 track 的覆盖行为增加计数指标？当前仅 WARN。
- 责任人/截止日期/所需产物：待指派 / 本卡周期 / 代码+测试+说明。

> 生成到：/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v2.1.0-mvp/gpt/PROMPT-CARD-203-changetracker-threadlocal.md

