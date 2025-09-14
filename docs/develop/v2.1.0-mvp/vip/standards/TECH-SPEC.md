# 技术规范（TECH-SPEC）

## 1. 范围与目标
- 目标：为 TaskFlowInsight 在 MVP 与后续演进阶段提供统一的技术约束与度量口径，降低返工与不一致风险。
- 覆盖：性能基准、并发安全、错误分级、兼容性、可观测性、配置与回滚。

## 2. 性能基准与衡量方法
- 目标阈值（MVP 必达，常规负载与热点路径）：
  - 快照（ObjectSnapshot）：P50 < 10 μs；P95 < 50 μs
  - 差异检测（DiffDetector）：P50 < 50 μs；P95 < 200 μs
  - 清理（TFI.stop → flush+clear）：P50 < 1 ms；P95 < 5 ms
- 达标率口径：≥ 95% 样本（含典型对象：5/20/100 字段；数组/集合 100/1000 元素）达到目标。
- 测量方法：
  - 单元微基线：JMH 可选（Phase 3）；MVP 阶段以 System.nanoTime 计时+断言，输出 P50/P95 与最大值。
  - 采样与隔离：避免 JVM 暖机干扰；预热 N 次（建议 500+），采样 10000+。
- 报告：将 P50/P95/Max 写入测试输出，或暴露于 Micrometer Gauge。

## 3. 并发安全策略
- ThreadLocal 上下文：
  - 三处清理一致：ManagedThreadContext.close() / TFI.stop() / TFI.endSession() → 幂等；出现异常不影响主流程。
  - 线程池复用：所有 ThreadLocal Map 必须 remove/clear；ManagedThreadContext.close() 内部调用 TFI.clearAllTracking()，ChangeTracker 需 remove ThreadLocal Map。
- 快照追踪：
  - 使用 WeakReference 持有 target，避免泄漏；目标被 GC 回收跳过但不报错。
  - 最大追踪对象数：默认 1000（超限时丢弃最早的或拒绝新增，记录 WARN 指标）。
- 上下文传播：
  - 装饰器 TFIAwareExecutor（或 ContextPropagatingExecutor）包装线程池；禁止跨线程直接传递对象引用（仅传递上下文 id/元数据）。

## 4. 错误分级与降级联动
- 分级：
  - WARN：快照/导出单元失败（对象不支持/转义异常/被 GC），当前单元跳过；指标 +1。
  - ERROR：检测到 ThreadLocal 残留/上下文不一致/基线丢失；记录日志与指标；必要时清理上下文并告警。
  - FATAL：内存使用超过 100MB、频繁 OOM、命中熔断阈值（如命中率 < 20%、驱逐 > 10k/min）→ 自动禁用 change-tracking（tfi.change-tracking.enabled=false）。
- 降级联动：
  - 记录结构化日志（action、sessionId、threadId、timestamp、reason）。
  - 指标：tfi.errors.total、tfi.degraded.state（0/1）。
  - 开关：系统级 tfi.enabled 与功能级 tfi.change-tracking.enabled；禁用后门面快速返回。
- 恢复策略：
  - 默认手动恢复；可配置定时评估（命中率持续>60%、驱逐下降）自动复位。

## 5. 兼容性矩阵与依赖阶段
- JDK：21（LTS）。
- Spring Boot：3.5.x。
- Micrometer：1.12+（最小指标集，标签维度受限）。
- Caffeine：3.x（Phase 2+，按触发条件启用；MVP 默认本地内存）。
- Jackson：2.17+（JSON 导出/可选 Merge/Patch）。
- JMH：Phase 3（系统化基准与 SLA 分级）。

## 6. 可观测性（最小集）
- 计数器：tfi.tracking.total、tfi.changes.total、tfi.errors.total。
- 时延：tfi.snapshot.duration、tfi.diff.duration、tfi.stop.duration。
- Gauge：tfi.tracking.active、degraded.state（0/1）。
- 标签约束：不使用高基数字段；仅 type/operation；必要时采样。

## 7. 配置与回滚
- 最小配置集（MVP）：
  - tfi.enabled、tfi.change-tracking.enabled、tfi.change-tracking.deep-snapshot.enabled
  - tfi.change-tracking.value-repr.max-length（默认 8192）
  - tfi.change-tracking.summary.enabled
- 回滚：FATAL 自动禁用；手动回滚通过开关关闭新功能；保持兼容的旧行为路径。
- 环境：application-dev.yml（最小配置启用）、application-prod.yml（保守开关）。

## 8. 安全与隐私
- 控制台/JSON 导出默认脱敏（敏感词 list：password/secret/token/key）。
- Actuator 端点仅在管理角色下开放；输出结构受限，避免高基数字段。
