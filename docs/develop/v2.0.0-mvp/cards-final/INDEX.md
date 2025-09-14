Title: M2-M0 合并任务卡索引（按依赖顺序）

说明
- 列出 cards-final 中所有合并卡片，按依赖拓扑排序。
- 每项给出主文件（CARD-xxx-*.md）与 OPUS 同名别名（TFI-MVP-*.md）。

依赖顺序
1) TASK-201 — ChangeRecord 与 ObjectSnapshot
   - File: `CARD-201-ChangeRecord-and-ObjectSnapshot.md`
   - Alias: `TFI-MVP-201-changerecord-objectsnapshot.md`
   - Depends: —

2) TASK-202 — DiffDetector（标量）
   - File: `CARD-202-DiffDetector-Scalar.md`
   - Alias: `TFI-MVP-202-diffdetector-scalar.md`
   - Depends: TASK-201

3) TASK-203 — ChangeTracker（线程隔离）
   - File: `CARD-203-ChangeTracker-ThreadLocal.md`
   - Alias: `TFI-MVP-203-changetracker-threadlocal.md`
   - Depends: TASK-201, TASK-202

4) TASK-210 — TFI APIs（track/trackAll/getChanges/clearAllTracking）
   - File: `CARD-210-TFI-APIs-Track-GetClear.md`
   - Alias: `TFI-MVP-210-tfi-apis.md`
   - Depends: TASK-203

5) TASK-204 — 与 TFI.stop 集成（写入 CHANGE）
   - File: `CARD-204-TFI-Stop-Integration.md`
   - Alias: `TFI-MVP-204-tfi-stop-integration.md`
   - Depends: TASK-203 (建议在 TASK-210 之后执行)

6) TASK-240 — 配置键与默认值（Spring Properties）
   - File: `CARD-240-M0-Config-Keys-and-Defaults.md`
   - Alias: `TFI-MVP-240-config-defaults.md`
   - Depends: —

7) TASK-220 — ManagedThreadContext 关闭清理
   - File: `CARD-220-ManagedThreadContext-Cleanup.md`
   - Alias: `TFI-MVP-220-context-cleanup.md`
   - Depends: TASK-203

8) TASK-221 — 上下文传播（TFIAwareExecutor）验证
   - File: `CARD-221-Context-Propagation-Executor.md`
   - Alias: `TFI-MVP-221-context-propagation.md`
   - Depends: TASK-203

9) TASK-230 — 导出验证：Console/JSON 含 CHANGE
   - File: `CARD-230-Console-Json-ChangeMessage-Verification.md`
   - Alias: `TFI-MVP-230-export-verification.md`
   - Depends: TASK-204

10) TASK-264 — 反射元数据缓存验证
    - File: `CARD-264-Reflection-Metadata-Cache.md`
    - Alias: `TFI-MVP-264-cache-verification.md`
    - Depends: —

11) TASK-260 — DiffDetector 标量对比单测
    - File: `CARD-260-Unit-DiffDetector-Scalar.md`
    - Alias: `TFI-MVP-260-unit-tests.md`
    - Depends: TASK-202

12) TASK-262 — 生命周期清理测试（stop/close/endSession）
    - File: `CARD-262-Lifecycle-Cleanup.md`
    - Alias: `TFI-MVP-262-lifecycle-tests.md`
    - Depends: TASK-203, TASK-220

13) TASK-263 — 变更消息格式测试
    - File: `CARD-263-Message-Format.md`
    - Alias: `TFI-MVP-263-message-format.md`
    - Depends: TASK-204

14) TASK-261 — 并发隔离与归属正确性测试
    - File: `CARD-261-Concurrency-Isolation.md`
    - Alias: `TFI-MVP-261-concurrency-tests.md`
    - Depends: TASK-203, TASK-221

15) TASK-250 — 基准测试（JMH/微基准）
    - File: `CARD-250-Benchmarks-JMH-or-Micro.md`
    - Alias: `TFI-MVP-250-benchmarks.md`
    - Depends: —（建议在核心联调后执行）

16) TASK-251 — 自适应截断与水位（推迟到 M1）
    - File: `CARD-251-Adaptive-Truncation-Watermark.md`
    - Alias: `TFI-MVP-251-adaptive-truncation.md`
    - Depends: —（M1 执行）

17) TASK-270 — ChangeTrackingDemo（显式 + 便捷 API）
    - File: `CARD-270-ChangeTracking-Demo.md`
    - Alias: `TFI-MVP-270-demo.md`
    - Depends: TASK-204, TASK-211

