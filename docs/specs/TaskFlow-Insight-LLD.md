# TaskFlow Insight（TFI）— 详细设计（Low‑Level Design）

## 1. 数据模型
- Session：`sessionId(UUID), threadId(long), createdAt(ms), endedAt(ms?), root(Node)`
- Node：`id, name, startMillis, endMillis?, startNano, selfDurationNs, accDurationNs, status(RUNNING|DONE), cpuNs?, messages[], children[]`
- Message：`type(FLOW|METRIC|EXCEPTION|CHANGE), ts, level, content, kv{}`

## 2. JSON Schema（摘要）
- Node：`{"id":"string","name":"string","startMillis":0,"endMillis":0,"selfDurationNs":0,"accDurationNs":0,"status":"RUNNING|DONE","cpuNs":0,"messages":[...],"children":[...]}`
- Session：`{"sessionId":"uuid","threadId":123,"createdAt":0,"endedAt":0,"root":{Node}}`

## 3. 线程模型与上下文
- ThreadLocal<Context>：线程内上下文，包含：
  - `stack: ArrayDeque<Node>`（任务栈，线程内无锁）
  - `recentSessions: Deque<Session>`（最近 N 个会话，环形缓存）
  - `root: Node`（当前根）
- 全局索引：`ConcurrentMap<Long, Deque<Session>> allSessions`，只读跨线程导出。

## 4. 核心算法
- start(name)：
  - 构造 Node（记录 `startMillis`/`startNano`，`status=RUNNING`）。
  - 若栈非空，挂入父节点 `children`；否则设为 `root` 并确保创建/续用当前 Session。
  - `stack.push(node)`。
- stop([endCtx, strategyId])：
  - `stack.pop()` 得到当前节点；`selfDurationNs = nowNano - startNano`；
  - 回溯累计：将 `selfDurationNs` 累加到父级及其祖先的 `accDurationNs`（或只累加到父级，父级在自身 `stop` 时合并子树累计）；
  - 叶子：`accDurationNs=selfDurationNs`；父节点最终 `accDurationNs=self + Σ(children.acc)`；
  - 若栈空（根完成）：封装 Session（`endedAt`），加入 `recentSessions` 与 `allSessions`，并 `ThreadLocal.remove()`。
- 消息：
  - `append(type, content)` 添加到当前节点；以 `maxMessagesPerTask` 限流并写入“截断提示”。
  - 变更记录：`stop(endCtx, strategyId)` 与外部 `ObjectComparatorUtil` 对接生成 `CHANGE` 消息。

## 5. 清理与采样
- 自动清理：守护线程定期扫描 `allSessions`，仅清理 `endTime>0 && now-endTime>cleanupInterval` 的会话；首轮不延迟。
- 上限：`maxMessagesPerTask、maxSubtasksPerTask、maxDepth、maxSessionsPerThread`；超限截断并记录提示。
- 采样：会话级 `samplingRate (0~1)`；异常链路强制保留；支持关键流程白名单。

## 6. 导出与打印
- 文本树（人读）：
  - 行格式：`名称（累计ms） | self=自身ms | 状态 | 消息数`；消息行：`|- 类型: 内容`；
  - 支持 `maxDepth/maxNodes` 限制，触发时在根插入“导出截断”消息。
- JSON（机读）：
  - `exportJson(sessionId[, maxDepth, maxNodes])` 返回完整或受限树；字段对齐模型。
- 检索：
  - `printTree()`（当前线程最近会话）、`printTree(threadId)`、`printTree(sessionId)`；
  - `recentSessions(threadId, limit)` 返回最近会话 ID 列表。

## 7. 配置项
- `cleanup-interval=PT5M`、`max-messages-per-task=1000`、`max-subtasks-per-task=200`、`max-depth=100`、`max-sessions-per-thread=10`、`sampling-rate=1.0`、`enable-cpu=false`。
- Spring `@ConfigurationProperties` 或静态 `Config` 支持运行时覆盖。

## 8. 可观测性与集成
- Micrometer（可选）：`tfi_task_count、tfi_tree_depth、tfi_acc_duration_hist、tfi_truncation_count、tfi_cleanup_count`。
- MDC：注入 `sessionId、threadId、taskPath`；便于跨日志关联。
- OpenTelemetry（M3）：根/子节点 → Span；属性含 `sessionId/taskPath/accDurationMs`；采样协调说明。

## 9. 性能与内存
- 结构：`ArrayDeque/ArrayList`，线程内无锁；跨线程读采用快照或节点级同步短临界区。
- 复杂度：每次 `stop()` O(depth) 回溯；深度默认 100 可控；必要时导出时做单次后序求和替代回溯。
- 内存：通过会话历史 N、深度、子任务、消息上限与采样控制上界；根完成即释放 ThreadLocal。

## 10. 错误处理与降级
- 空栈 `stop()`：记录 `warn` 并忽略。
- CPU 采集失败：自动关闭 CPU 采集并记录一次性 `warn`。
- 导出超限：写入“导出截断”提示，不阻断业务。

## 11. 测试策略
- 单测：嵌套时长一致性（运行中/结束）、多会话历史与检索、消息截断、清理策略（进行中保护）、并发读写一致性、性能开销评估。
- 长稳：24h 压力测试验证内存曲线与 ThreadLocal 清理。

## 12. 迁移与风险
- 迁移：新代码优先作用域式 API；旧链路通过薄适配逐步替换；`ObjectComparatorUtil` 通过 `stop(endObj, strategyId)` 注入变更。
- 风险：大树/高并发内存压力（以采样/上限/深度限制缓解）、统计偏差（统一口径与兜底）、线程池泄漏（根结束即 remove）。

## 13. 并发读取一致性（新增：不可变快照 COW）
- 冻结策略：当会话结束时，将 `Session.root` 转换为不可变树结构（深拷贝一次），后续跨线程读取零锁、零复制。
- 运行中快照：导出/打印前，针对当前可变树做受限深度/节点数的浅拷贝快照，拷贝窗口受控，避免长临界区。
- 权衡说明：结束会话一次性开销换取后续多次读取无锁；运行中读取不阻塞写路径。

## 14. 扩展点（新增：SPI 与导出）
- 变更追踪：
  - 接口：`interface ChangeDetector { List<Message> detect(Object before, Object after, String strategyId); }`
  - 装配：`ServiceLoader<ChangeDetector>`；默认实现可桥接 `ObjectComparatorUtil`，也可自定义。
- 导出适配：
  - 接口：`interface Exporter { boolean supports(String format); String export(Session s, ExportOptions o); }`
  - JSON 为内置实现；OTLP/OTel 适配器放入 M2 单独模块。

## 15. 配置校验与诊断（新增）
- 校验规则：`0<=samplingRate<=1`、`maxDepth<=1000`、`maxMessagesPerTask<=100000`（参考值）等；越界回退默认并告警。
- 启动诊断：打印关键配置、生效阈值与潜在风险提示（如深度过大、清理周期过短）。
- 运行诊断：暴露指标（截断次数、清理次数、失败导出次数等），便于阈值调优。

## 16. 测试与验证（新增）
- 极限压测：万线程/千层嵌套的上限验证与退让策略（采样/截断）。
- 兼容性：JDK 8/11/17/21，Spring Boot 2.x/3.x 集成验证。
- 并发一致性：结束会话快照读取与运行中快照读取结果一致性校验（在阈值内）。
