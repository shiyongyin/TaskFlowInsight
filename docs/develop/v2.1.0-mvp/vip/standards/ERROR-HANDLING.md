# 错误分级标准（Error Handling）

目标：统一等级→处理→指标→降级/熔断开关，覆盖门面/上下文/快照/导出关键点。

## 等级定义与处理
- WARN（可恢复、单元失败）
  - 触发：对象快照失败、对象被 GC、导出某单元失败
  - 处理：跳过当前单元；记录结构化日志与 `tfi.errors.total+1`
  - 影响：主流程继续；不触发降级
- ERROR（功能异常、需自愈）
  - 触发：`ThreadLocal` 残留、上下文不一致、基线丢失、反射缓存异常
  - 处理：记录日志与指标；清理上下文（三处清理）；必要时警报
  - 影响：功能局部失效但整体可用；不自动禁用
- FATAL（系统性风险、需熔断）
  - 触发：内存涨幅异常、频发 OOM、命中率过低、驱逐过高等策略阈值
  - 处理：设置降级状态 `tfi.degraded.state=1`；自动禁用 `tfi.change-tracking.enabled=false`
  - 影响：变更追踪关闭，门面快速返回；人工干预/恢复

## 日志与指标（最小集）
- 日志字段：`level, action, sessionId, taskId, threadId, timestamp, reason`
- 指标：
  - `tfi.errors.total{level=warn|error|fatal}`
  - `tfi.degraded.state`（0/1）
  - 可选：异常 Top-N 分类计数

## 关键点约束
- 门面（TFI）
  - 禁用态快速返回；所有异常 `handleInternalError(...)` 捕获，避免泄漏到调用方
  - `stop/endSession/clear` 必须幂等；失败也不影响主流程
- 上下文/线程（ManagedThreadContext/TFIAwareExecutor）
  - 任何异常路径均清理 ThreadLocal，避免跨线程污染
  - 传播仅传 id/元数据，不传对象引用
- 快照/差异（ObjectSnapshot/DiffDetector）
  - 快照失败仅 WARN；`repr` 统一“先转义后截断（8192）”
- 导出（Console/JSON/Map）
  - 输出结构稳定；失败记录 WARN 并继续导出其他节点

## 自动禁用与恢复策略
- 自动禁用：FATAL → `tfi.change-tracking.enabled=false`，并置位 `tfi.degraded.state=1`
- 恢复策略：默认人工；可配置“恢复评估”定时任务（命中率↑、驱逐↓）

