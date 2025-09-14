# DEV-010 TFI 主 API 实现

## 1. 开发目标
- [ ] 主 API 范围清单（以具体接口签名列出）
  - [ ] com.syy.taskflowinsight.api.TFI#track(String name, Object target, String... fields)
  - [ ] com.syy.taskflowinsight.api.TFI#trackAll(Map<String,Object> targets)
  - [ ] com.syy.taskflowinsight.api.TFI#getChanges() : List<ChangeRecord>
  - [ ] com.syy.taskflowinsight.api.TFI#clearAllTracking()
  - [ ] com.syy.taskflowinsight.api.TFI#withTracked(String name, Object target, Runnable body, String... fields)
  - [ ] com.syy.taskflowinsight.api.TFI#withTracked(String name, Object target, Callable<T> body, String... fields)
- [ ] 领域模型/标识约定一致（枚举/ID 规则）
  - [ ] ChangeType枚举：CREATE/UPDATE/DELETE
  - [ ] MessageType.CHANGE消息类型
  - [ ] sessionId/taskPath标识规则
- [ ] 与变更跟踪/任务树联动点（事件/回调/埋点）
  - [ ] TFI.stop()方法第194-204行集成getChanges()
  - [ ] ManagedThreadContext.close()清理钩子
  - [ ] TaskNode.addMessage(String, MessageType.CHANGE)

## 2. 开发清单（可勾选）
- [ ] API 契约（OpenAPI/DTO/错误码）→ src/main/java/com/syy/taskflowinsight/tracking/model/ChangeRecord.java
- [ ] 控制器/服务/仓储骨架 → com.syy.taskflowinsight.tracking包
  - [ ] ChangeTracker.java（ThreadLocal管理）
  - [ ] DiffDetector.java（差异检测）
  - [ ] ObjectSnapshot.java（快照采集）
- [ ] 幂等/重试/死信 → clearAllTracking()保证幂等性
- [ ] 限流/超时/熔断 → tfi.change-tracking.enabled配置开关
- [ ] 文档与示例 → src/main/java/com/syy/taskflowinsight/demo/ChangeTrackingDemo.java

## 3. 测试要求（可勾选）
- 单测
  - [ ] 覆盖率 ≥ 80%
  - [ ] 异常/边界用例（null参数、disabled状态、ThreadLocal清理）
- 集成
  - [ ] 端到端用例（track -> stop -> Console输出CHANGE消息）
  - [ ] 回归清单通过（TFITest.java、TFIIntegrationTest.java）
- 性能
  - [ ] 关键接口 P95 ≤ 200μs（2字段对比）
  - [ ] 并发与限流策略验证（TFIConcurrencyTest.java）

## 4. 关键指标（可勾选）
- [ ] 可用性 ≥ 99.9%
- [ ] 错误率 ≤ 0.1%
- [ ] 依赖超时/降级命中率（暂无外部依赖）

## 5. 验收标准（可勾选）
- [ ] OpenAPI ↔ 实现一致（方法签名匹配）
- [ ] 回滚/灰度预案演练记录
  - [ ] tfi.change-tracking.enabled=false验证
  - [ ] TFI.disable()全局开关验证
- [ ] 观测指标/告警规则就绪（P95延迟 > 500μs告警）

## 6. 风险评估（可勾选）
- [ ] 兼容性（新增API不影响现有功能）
- [ ] 性能/容量（ThreadLocal内存占用，5分钟定时清理）
- [ ] 安全合规（反射setAccessible权限控制、8192字符截断）
- [ ] 依赖可用性（无外部依赖，仅内部模块）

## 7. 参考与证据
- 源：/Users/mac/work/development/project/TaskFlowInsight/docs/task/v2.0.0-mvp/
  - change-tracking-core/TASK-201-ChangeRecord-and-ObjectSnapshot.md
  - change-tracking-core/TASK-202-DiffDetector-Scalar.md
  - change-tracking-core/TASK-203-ChangeTracker-ThreadLocal.md
  - change-tracking-core/TASK-204-TFI-Stop-Integration.md
  - api-implementation/TASK-210-TFI-APIs-Track-GetClear.md
  - api-implementation/TASK-211-TFI-Convenience-WithTracked.md
- 代码：src/main/java/com/syy/taskflowinsight/
  - api/TFI.java#194-204（stop方法集成点）
  - tracking/（新增包）
  - context/ManagedThreadContext.java（清理钩子）
- 变更记录：v2.0.0-mvp MVP版本新增变更追踪功能