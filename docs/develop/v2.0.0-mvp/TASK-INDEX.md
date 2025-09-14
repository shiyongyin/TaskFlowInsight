# TFI v2.0.0-MVP 任务索引（格式固定，内容具体化）

| 任务ID | 标题 | 源文档 | 任务卡 |
|---|---|---|---|
| TASK-201 | ChangeRecord 与 ObjectSnapshot 实现（已完成） | docs/task/v2.0.0-mvp/change-tracking-core/TASK-201-ChangeRecord-and-ObjectSnapshot.md | [cards-final/CARD-201-ChangeRecord-and-ObjectSnapshot.md](cards-final/CARD-201-ChangeRecord-and-ObjectSnapshot.md) |
| TASK-202 | DiffDetector 标量字段对比（已完成） | docs/task/v2.0.0-mvp/change-tracking-core/TASK-202-DiffDetector-Scalar.md | [cards-final/CARD-202-DiffDetector-Scalar.md](cards-final/CARD-202-DiffDetector-Scalar.md) |
| TASK-203 | ChangeTracker 线程隔离管理器（已完成） | docs/task/v2.0.0-mvp/change-tracking-core/TASK-203-ChangeTracker-ThreadLocal.md | [cards-final/CARD-203-ChangeTracker-ThreadLocal.md](cards-final/CARD-203-ChangeTracker-ThreadLocal.md) |
| TASK-204 | TFI.stop 集成变更追踪（已完成） | docs/task/v2.0.0-mvp/change-tracking-core/TASK-204-TFI-Stop-Integration.md | [cards-final/CARD-204-TFI-Stop-Integration.md](cards-final/CARD-204-TFI-Stop-Integration.md) |
| TASK-210 | TFI 新增4个核心API（已完成） | docs/task/v2.0.0-mvp/api-implementation/TASK-210-TFI-APIs-Track-GetClear.md | [cards-final/CARD-210-TFI-APIs-Track-GetClear.md](cards-final/CARD-210-TFI-APIs-Track-GetClear.md) |
| TASK-211 | TFI 便捷API withTracked（已完成） | docs/task/v2.0.0-mvp/api-implementation/TASK-211-TFI-Convenience-WithTracked.md | [cards-final/CARD-211-TFI-Convenience-WithTracked.md](cards-final/CARD-211-TFI-Convenience-WithTracked.md) |
| TASK-220 | ManagedThreadContext 清理钩子（已完成） | docs/task/v2.0.0-mvp/integration/TASK-220-ManagedThreadContext-Cleanup.md | [cards-final/CARD-220-ManagedThreadContext-Cleanup.md](cards-final/CARD-220-ManagedThreadContext-Cleanup.md) |
| TASK-221 | 上下文传播验证（已完成） | docs/task/v2.0.0-mvp/integration/TASK-221-Context-Propagation-Executor.md | [cards-final/CARD-221-Context-Propagation-Executor.md](cards-final/CARD-221-Context-Propagation-Executor.md) |
| TASK-230 | Console/JSON 导出验证（已完成） | docs/task/v2.0.0-mvp/export-verification/TASK-230-Console-Json-ChangeMessage-Verification.md | [cards-final/CARD-230-Console-Json-ChangeMessage-Verification.md](cards-final/CARD-230-Console-Json-ChangeMessage-Verification.md) |
| TASK-240 | M0 配置键与默认值（已完成） | docs/task/v2.0.0-mvp/configuration/TASK-240-M0-Config-Keys-and-Defaults.md | [cards-final/CARD-240-M0-Config-Keys-and-Defaults.md](cards-final/CARD-240-M0-Config-Keys-and-Defaults.md) |
| TASK-250 | 性能基准测试（已交付微基准，JMH 可选） | docs/task/v2.0.0-mvp/performance/TASK-250-Benchmarks-JMH-or-Micro.md | [cards-final/CARD-250-Benchmarks-JMH-or-Micro.md](cards-final/CARD-250-Benchmarks-JMH-or-Micro.md) |
| TASK-251 | 自适应截断水位策略（推迟到 M1） | docs/task/v2.0.0-mvp/performance/TASK-251-Adaptive-Truncation-Watermark.md | [cards-final/CARD-251-Adaptive-Truncation-Watermark.md](cards-final/CARD-251-Adaptive-Truncation-Watermark.md) |
| TASK-260 | DiffDetector 单元测试（已完成） | docs/task/v2.0.0-mvp/testing/TASK-260-Unit-DiffDetector-Scalar.md | [cards-final/CARD-260-Unit-DiffDetector-Scalar.md](cards-final/CARD-260-Unit-DiffDetector-Scalar.md) |
| TASK-261 | 并发隔离测试（已完成） | docs/task/v2.0.0-mvp/testing/TASK-261-Concurrency-Isolation.md | [cards-final/CARD-261-Concurrency-Isolation.md](cards-final/CARD-261-Concurrency-Isolation.md) |
| TASK-262 | 生命周期清理测试（已完成） | docs/task/v2.0.0-mvp/testing/TASK-262-Lifecycle-Cleanup.md | [cards-final/CARD-262-Lifecycle-Cleanup.md](cards-final/CARD-262-Lifecycle-Cleanup.md) |
| TASK-263 | 变更消息格式测试（已完成） | docs/task/v2.0.0-mvp/testing/TASK-263-Message-Format.md | [cards-final/CARD-263-Message-Format.md](cards-final/CARD-263-Message-Format.md) |
| TASK-264 | 反射元数据缓存验证（已完成） | docs/task/v2.0.0-mvp/testing/TASK-264-Reflection-Metadata-Cache.md | [cards-final/CARD-264-Reflection-Metadata-Cache.md](cards-final/CARD-264-Reflection-Metadata-Cache.md) |
| TASK-270 | ChangeTracking Demo（已完成） | docs/task/v2.0.0-mvp/demo/TASK-270-ChangeTracking-Demo.md | [cards-final/CARD-270-ChangeTracking-Demo.md](cards-final/CARD-270-ChangeTracking-Demo.md) |

## 任务依赖关系

### Phase A：核心功能（D+0 ~ D+2）
- TASK-201 → TASK-202 → TASK-203 → TASK-210

### Phase B：集成与配置（D+2 ~ D+3）
- TASK-204（依赖203）
- TASK-211（依赖210）
- TASK-220/221（依赖203）
- TASK-240（独立）

### Phase C：性能与测试（D+3 ~ D+5）
- TASK-250/251（性能测试）
- TASK-260~264（测试套件）
- TASK-230（导出验证）

### Phase D：Demo与文档（D+5 ~ D+7）
- TASK-270（演示所有功能）

## 关键里程碑
- **D+2**：Core/API 可运行，Console 能看到 CHANGE 消息
- **D+3**：性能基准报告生成，2字段 P95 ≤ 200μs
- **D+5**：并发测试通过，无内存泄漏
- **D+7**：Demo 可运行，PR 准备就绪
