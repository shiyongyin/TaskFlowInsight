Title: M2-M0 开发者速查表（变更追踪）

一、API 与刷新策略
- 显式 API：`TFI.track(...)` + 业务修改 + `TFI.stop()`
  - 刷新点：`TFI.stop()` 尾部统一 `getChanges()` → 写入当前 TaskNode 的 `MessageType.CHANGE` → `clearAllTracking()`。
- 便捷 API：`TFI.withTracked(name, target, Runnable/Callable, fields...)`
  - 刷新点：finally 内即时 `getChanges()` → 写入 CHANGE → `clearAllTracking()`；不吞业务异常。
  - 与 stop 的关系：withTracked 已清理，stop 不会重复输出这些变更。

二、清理触发点（全部幂等）
- `TFI.stop()` → 清理本次任务的追踪
- `ManagedThreadContext.close()` → finally 清理
- `TFI.endSession()` → 结束会话时清理

三、快照/对比范围（M0）
- 纳入：标量/字符串/日期
- 排除：复杂对象/集合/Map（M1 再做摘要/策略）
- Date：深拷贝为不可变值

四、消息格式与截断
- 统一格式：`<Object>.<field>: <old> → <new>`
- 先转义后截断：默认 8192 字符，上溢追加 `... (truncated)`
- 空值：`null`

五、配置（Spring）
- 前缀：`tfi.change-tracking.*`（`ChangeTrackingProperties`）
- 关键项（M0）：
  - `enabled=false`（独立于 TFI 全局开关）
  - `value-repr-max-length=8192`
  - `cleanup-interval-minutes=5`（定时清理器默认关闭）
- Fallback：`System.getProperty` 仅在无 Spring 环境下使用（如基准测试）。

六、性能与缓存
- 基准门槛：以延迟为主（建议：2 字段 P95 ≤ 200μs）；CPU 占比为报告项。
- 反射缓存：`ConcurrentHashMap` + `computeIfAbsent`，设置容量上限（达到上限拒绝新增或日志告警）；不引第三方依赖（M0）。

七、常见问题
- 为什么 stop 没有输出？
  - 可能使用了 withTracked 且已在 finally 刷新并清理，stop 不再重复输出。
- 为什么复杂对象字段没被比较？
  - M0 仅支持标量/字符串/日期；集合/Map 与对象摘要将于 M1 实现。
