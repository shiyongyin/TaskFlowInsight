# 集成测试计划（Integration Test Plan）

面向 MVP Phase 1，覆盖变更追踪主路径、并发隔离、异常降级与性能基线。以最小配置在 `dev` Profile 下运行，断言结构化输出与指标，避免依赖文案。

## 1. 前置与基线
- Profile 与配置
  - 使用 `application-dev.yml`（最小配置）：
    - `tfi.enabled=true`
    - `tfi.change-tracking.enabled=true`
    - `tfi.change-tracking.value-repr-max-length=8192`
    - 可选：`logging.level.com.syy.taskflowinsight=DEBUG`（问题定位时启用）
- 运行方式
  - `JAVA_TOOL_OPTIONS="-Dspring.profiles.active=dev" ./mvnw test`
  - 或在 IDE 配置测试 VM Options：`-Dspring.profiles.active=dev`
- 判定口径
  - 断言结构化结果（Console/JSON/Map），不依赖自由文案。
  - 无 NPE/跨线程污染；三处清理幂等：`TFI.stop` / `TFI.endSession` / `ManagedThreadContext.close`。
  - 性能基线：非 JMH，采样计时，P50 达标（详见第4节）。

## 2. 场景 1：主路径（跟踪→取变更→导出）
目的：验证 `ChangeTracker` 管道完整性与输出结构一致性。

- 准备
  - `TFI.enable()`；开始会话与任务：`TFI.start("IT-ChangeTracking")`。
  - 执行一次或多次 `ChangeTracker.track(...)` 或通过门面 API 包装（`TFI.run/call`；未来也可用 `TFI.withTracked`）。
- 行为
  - 调用 `ChangeTracker.getChanges()` 获取结构化 `List<ChangeRecord>`。
  - 使用 `TFI.exportToConsole()` 与 `TFI.exportToJson()` 导出。
- 断言
  - 变更记录字段完整：`objectName/fieldName/oldValue/newValue/changeType/timestamp`。
  - Console/JSON/Map 三种导出结构与数量一致（内容不做严格文案匹配）。
  - `TFI.stop()` 后自动 flush 变更到当前任务，再清理追踪数据；再次 `getChanges()` 为空。
- 清理
  - `TFI.endSession()`；`TFI.clear()`；幂等调用不应抛异常。

## 3. 场景 2：并发隔离（10–16 线程）
目的：验证 TFIAwareExecutor 上下文归属、去重与无交叉污染。

- 准备
  - 固定线程池 `nThreads ∈ [10,16]`，包装为 `TFIAwareExecutor`。
  - 每个任务内：`ManagedThreadContext.create(...)` → `startSession` → `start` → `ChangeTracker.track`。
- 行为
  - 多线程并发提交 1000+ 次任务；每个任务生成唯一 `sessionId`/`taskId`。
- 断言
  - 每个线程的变更仅归属其自身 `sessionId`；无跨线程消息；`ThreadLocal` 无残留。
  - 多次调用三处清理（`stop/endSession/close`）均幂等；执行后 `ChangeTracker.getChanges()` 为空。
- 资源
  - 线程池 `shutdown()` 后阻塞等待；确保无泄漏（可选用 `WeakReference` 断言对象可回收）。

## 4. 场景 3：异常与降级（快照失败/对象被 GC）
目的：验证错误分级（WARN/ERROR）与降级开关生效；绝无 NPE。

- 构造
  - 注入/模拟不可序列化字段、循环引用或定制 `ObjectSnapshot` 触发失败。
  - 使用 `WeakReference` 包装目标，强制 GC 后读取。
- 断言
  - 快照失败记录 WARN 日志与计数指标（如 `tfi.errors.total`）；当前单元跳过，主流程不异常。
  - 检测到 `ThreadLocal` 残留或上下文不一致，记 ERROR 并自动清理；可配合“自动禁用”开关测试（若启用）。
  - 全链路无 NPE；`TFI` 门面捕获并吞吐内部异常（日志可选堆栈）。

## 5. 场景 4：性能基线（非 JMH）
目的：在热点路径给出 P50 基线并达标；失败时输出诊断。

- 范围
  - 快照：P50 < 10 μs；P95 < 50 μs
  - Diff：P50 < 50 μs；P95 < 200 μs
  - `TFI.stop` 清理：P50 < 1 ms；P95 < 5 ms
- 方法
  - `System.nanoTime` 计时，预热 500+ 次，采样 10k+ 次；统计 P50/P95/Max。
  - 记录到日志与最小 Micrometer Gauge（可选）。
- 断言
  - P50 达标；若不达标，打印 Top-N 热点（类/字段数/集合大小）与配置建议。

## 6. 指标与日志（最小集）
- 指标
  - 计数：`tfi.tracking.total`、`tfi.changes.total`、`tfi.errors.total`
  - 时延：`tfi.snapshot.duration`、`tfi.diff.duration`、`tfi.stop.duration`
  - Gauge：`tfi.tracking.active`、`tfi.degraded.state(0/1)`
- 日志
  - 结构化字段：`action, sessionId, taskId, threadId, timestamp, reason`
  - 等级口径：WARN（单元失败/被GC）/ ERROR（上下文不一致）/ FATAL（触发熔断）

## 7. 验收与输出物
- 测试通过标准
  - 4 个场景全部通过；P50 达标；无交叉污染；无 NPE。
- 输出物
  - 测试类（JUnit 5）与测试数据；基线报告（P50/P95/Max）
  - 失败样例的日志片段与指标截图（如接入 Micrometer）

