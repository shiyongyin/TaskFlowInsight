Title: TaskFlow Insight — M2 PRD（合并定稿）
Version/Owner/Status/Date
- Version: v2.0.0-M2
- Owner: PM（产品）/SA（架构）
- Status: Final-Draft (merged gpt5/v2 + opus4.1/v2)
- Date: 2025-09-10

1. 背景与目标
- 定位：围绕“任务树 + 执行流程洞察 + 变更追踪”，提供对象状态变化的捕获、对比、审计与回放能力。
- M2 主题：ChangeTracking。分三阶段演进：M2-M0 先闭环“显式 API + 标量 Diff + 任务树联动 + Console/JSON”；M2-M1 增强集合/Map、注解/AOP、检索/时间线、SPI、文件导出；M2-M2 增加回放、外部存储适配与可视化增强（REST 可选）。

2. 范围与边界
- In-Scope（M2）
  - 变更捕获：显式 API（M0），注解/AOP（M1）。
  - 字段级 Diff：标量/字符串/日期（M0）；集合/Map 摘要（增/删/替换）（M1）；嵌套深度 M0≤2/M1≤3/M2≤5。
  - 事件模型与存储：线程内事件（M0）；全局内存索引 + 文件导出（M1，可选）；外部存储适配点（M2）。
  - 检索与时间线：对象/字段/时间段过滤（M1）；字段轨迹回放（M2）。
  - 审计导出：Console/JSON（M0），文件滚动与归档（M1）。
  - 与任务树联动：变更聚合到 TaskNode（M0），统计聚合与可视化增强（M1/M2）。
- Out-of-Scope（M2）
  - 跨系统一致性、统一权限中心、复杂多租计费、跨语言 Agent、实时流处理、AI 异常检测。

3. 用户画像与关键场景
- 开发工程师：排查数据异常，定位“何时何地何值被修改”。
- 架构/性能工程师：将状态演化与关键路径/热点耗时对齐分析。
- 运维工程师：生产事故回溯，时间旅行查看对象任意时刻状态（M2）。
- 审计/合规：导出变更流水满足取证（非强一致）。

4. 需求列表（合并）
| ID | Must/Should | 描述 | 业务动机 | 验收标准（量化） |
|---|---|---|---|---|
| M2-F001 | Must | 显式 API：track/trackAll/getChanges/clearAllTracking | 最小代价接入 | 2 字段修改返回 2 条差异；单次检测 P95 ≤ 200μs@2字段；整体 P95 CPU ≤ 3% |
| M2-F002 | Must | 标量字段 Diff（含 String/Date） | 精确可读 | 100 次检测正确率 100%；空值/类型变化稳健，无异常 |
| M2-F003 | Must | 与任务树联动（写入 CHANGE 消息） | 一屏观测 | Console/JSON 含“变更段”，映射到正确 TaskNode 路径 |
| M2-F004 | Should | 集合/Map 摘要变更（增/删/替换） | 常见结构覆盖 | List 增删改各 20 次，准确率 ≥ 95%，超阈值跳过并 WARN |
| M2-F005 | Should | 脱敏 + 白名单（字段通配） | 降低隐私风险 | 命中输出“***”；非白名单不采集；敏感项覆盖率 ≥ 95% |
| M2-F006 | Should | 内存检索与时间线 | 事后分析 | 对象/字段/时间过滤；1k 条 ≤ 50ms，10k 条 ≤ 200ms |
| M2-F007 | Should | 审计导出 JSON/文本（滚动/归档） | 合规与共享 | 生成 sessionId-timestamp.json；滚动可配；失败降级 Console |
| M2-F008 | Should | 注解/AOP：@TrackChanges / 参数 @Track | 降改造成本 | 样例可用；禁用零侵入；开销 ≤ 5%；异常不影响业务 |
| M2-F009 | Should | 可插拔 SPI（Diff/Mask/Serialize） | 定制扩展 | ServiceLoader 注入；按优先级选择；>80% 用例覆盖 |
| M2-F010 | Should | 字段轨迹回放 | 时间旅行 | 返回 (timestamp,value) 序列；窗口 1k 条 ≤ 100ms |

5. 非功能需求（合并）
- 性能预算：
  - M0：单对象≤20字段；一次 track+getChanges P95 CPU ≤ 3%。
  - M1：含集合/Map，P95 CPU ≤ 5%。
  - 读：1k 条查询 ≤ 50ms；10k 条 ≤ 200ms；回放 1k 条 ≤ 100ms。
- 可靠性：
  - 环形缓冲 + 会话边界清理；溢出降级仅消息，不阻塞主流程。
  - 内部异常以 `TFI.handleInternalError` 记录，不向业务冒泡。
  - valueRepr 截断：字符串化值默认最大 8192 字符（可配 `tfi.change-tracking.value-repr-max-length`），超出以 `... (truncated)` 结尾；支持自适应回压（>80%/90% 水位临时降至 2048/512），以效率优先保护系统。
- 安全：
  - 默认脱敏 + 白名单；导出目录显式配置并校验可写；Actuator 仅输出计数/健康，不含值。
- 可用性/可维护性：
  - 显式 API 5 分钟接入；注解由 Starter 自动配置（M1）。
  - 模块覆盖率 ≥ 80%；端到端示例可运行。

6. 成功指标（KPI/北极星）
- 采纳：新增 2 篇最佳实践/样例；Star +100。
- 性能：基准报告满足上述阈值。
- 质量：变更检测缺陷密度 < 5/KLOC。

7. 里程碑与交付
| 里程碑 | 范围 | 核心交付 | 验收标准 |
|---|---|---|---|
| M2-M0（2 周） | 显式 API、标量 Diff、任务树联动、Console/JSON | API/快照/对比、导出集成、样例与单测/基准、README 快速入门、Demo“变更追踪入门”、发布说明 | 通过 M2-F001/2/3；性能阈值达标<br>证据（部分）：<br>• F001 API/引擎：`src/main/java/com/syy/taskflowinsight/api/TFI.java`（track/trackAll/getChanges/clearAllTracking）、`src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java`；测试：`src/test/java/com/syy/taskflowinsight/api/TFIIntegrationTest.java`、`src/test/java/com/syy/taskflowinsight/context/ContextManagementIntegrationTest.java`、`src/test/java/com/syy/taskflowinsight/api/TFIModernAPITest.java`<br>• F002 Diff：`src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java`；测试：`src/test/java/com/syy/taskflowinsight/tracking/detector/DiffDetectorTests.java`<br>• F003 任务树联动（CHANGE 消息）：`TFI.flushChangesToCurrentTask()`、`TFI.withTracked()`（文件：`src/main/java/com/syy/taskflowinsight/api/TFI.java`）；导出验证：`src/test/java/com/syy/taskflowinsight/exporter/text/ConsoleExporterTest.java`、`src/test/java/com/syy/taskflowinsight/api/JsonExportValidationTest.java` |
| M2-M1（3 周） | 集合/Map、脱敏/白名单、检索/时间线、注解/AOP、SPI、文件导出 | 策略 SPI、内存索引、注解+切面、导出滚动、注解示例与检索/时间线文档 | 通过 M2-F004/5/6/7/8/9 |
| M2-M2（3 周） | 回放、外部存储适配点、可视化增强（REST 可选） | 回放 API、存储接口、报告增强、回放与外部存储适配指南、可视化使用指南 | 通过 M2-F010；回放性能达标 |

8. 风险 & 对策（合并）
- 反射/递归开销：字段/深度/集合限流；元数据缓存；可关闭。
- 内存占用：环形缓冲+限水位；完成即清理；超限仅消息降级。
- 隐私泄漏：默认脱敏+白名单；导出需显式开启并校验路径；审计不含敏感明文。
- 设计耦合：仅以消息/会话关联，不改写 TaskNode 结构；SPI 解耦。

9. 术语表（Glossary）
- ChangeRecord：单字段变更条目（old/new/type/timestamp/object/field/nodeId/sessionId）。
- ObjectSnapshot：某时刻对象字段取值集合（受白名单/深度限制）。
- Timeline：同对象/字段在时间轴上的值序列。
- Replay：基于 Timeline 重建字段历史状态。
- Session：TFI 会话（现有 `Session`）。

10. 追溯表（需求 → API/序列/存储）
| 需求ID | API/类 | 时序 | 存储 |
|---|---|---|---|
| M2-F001 | DONE: `api/TFI#track/trackAll/getChanges/clearAllTracking`（src/main/java/com/syy/taskflowinsight/api/TFI.java） | 业务前后 → capture/diff | ThreadLocal Map（会话清理；ChangeTracker.remove() 已实现）|
| M2-F002 | DONE: `changes/DiffDetector`（src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java） | capture(before/after) → compare | 内存 |
| M2-F003 | `model/TaskNode#addMessage`（CHANGE） | `TFI.stop()` 汇总写消息 | Session 归档 |
| M2-F004 | TODO: `changes/CollectionDiffStrategy` | diffCollection/diffMap | 内存（摘要）|
| M2-F005 | TODO: `changes/MaskingStrategy + whitelist` | filter → mask → store | 内存/导出 |
| M2-F006 | TODO: `store/InMemoryChangeStore + ChangeQueryService` | query(object/field/time) | 内存索引 |
| M2-F007 | TODO: `export/ChangeExporter` | export(sessionId) | 本地文件 |
| M2-F008 | TODO: `@TrackChanges/@Track + ChangeTrackingAspect` | AOP 前后快照 | 与 AutoConfiguration 对齐 |
| M2-F009 | TODO: SPI 注册（ServiceLoader） | 策略装配 | SPI 注册表 |
| M2-F010 | TODO: `ChangeReplayService#replay` | build timeline | 时间序列缓存 |

11. 变更消息规范与展示
- 复用现有 `MessageType.CHANGE`，无需新增枚举。
- 变更消息格式（M0）：`<Object>.<field>: <old> → <new>`，例如：`Order.status: PENDING → PAID`。
- 展示：作为当前 TaskNode 的一条消息输出，Console/JSON 已原生展示 CHANGE 消息；M1 可选增加“变更汇总板块”。

12. 性能验证方法（基准）
- 方法：优先使用 JMH 基准；无 JMH 时用 JUnit 微基准兜底。
- 场景：单对象 2 字段、20 字段、100 次循环；并发 8/16 线程；报告 P95 指标与 CPU 增量。
- 示例（更多见 Design）：跟踪→修改→getChanges→清理 的热路径测量。

13. 立即行动项（M2-M0，一周）
- D+0：确认 CHANGE 复用与消息格式；确定 `tfi.change-tracking` 前缀。
- D+2：提交原型：4 个 API + stop 自动刷新 + ThreadLocal 清理 + demo 演示。
- D+3：提交基准报告（2 字段 P95 ≤ 200μs；写路径 P95 CPU ≤ 3%）。
- D+5：并发/泄漏/边界测试通过；Console/JSON 证据产出。
- D+7：合入主干，启动 M1（集合摘要/注解/SPI/检索/文件导出）。

14. 范围说明与沟通口径（检索/回放）
- M1/M2 的默认形态为“会话/进程级”能力（内存存储），并非“长期可追溯”的审计系统，文档需明确定位为“会话内时间旅行调试”。
- 当需要长期追溯与跨重启保留时，依赖 M2 的外部存储适配；建议采用“异步批量写入 + 本地 WAL（预写日志）”策略提升持久性与恢复能力。

15. M0 快速验证清单（QA Checklist）
| 检查项 | 验证方法 | 预期结果 |
|---|---|---|
| API 可用性 | 调用 4 个核心 API 与 withTracked 便捷 API | 编译通过、运行无异常 |
| 消息集成 | 查看 Console/JSON 导出 | 存在 CHANGE 消息，格式 `<Object>.<field>: <old> → <new>` |
| 性能影响 | 运行 JMH/微基准 | 写路径 P95 CPU ≤ 3%，2 字段 P95 ≤ 200μs |
| 线程安全 | 并发测试（8/16 线程） | 无交叉污染，归属正确 |
| 内存泄漏 | 长时运行 + HeapDump | 无快照残留，水位受控 |
| 异常隔离 | 故意抛异常（withTracked/显式 API） | 主流程不受影响，异常透明 |
