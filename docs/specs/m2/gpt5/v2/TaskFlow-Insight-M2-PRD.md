Title: TaskFlow Insight — M2 PRD（最佳合成稿）
Version/Owner/Status/Date
- Version: v2.0.0-M2
- Owner: PM（产品经理）/SA（开发架构师）
- Status: Draft-for-Dev (evidence-based)
- Date: 2025-09-10

1. 背景与目标
- 一句话定位：围绕“任务树 + 执行流程洞察 + 变更追踪”，以最小侵入、低开销的方式提供对象状态变化的捕获、对比、审计与回放能力。
- M2 主题：ChangeTracking。M0 先闭环“显式 API + 标量 Diff + 任务树联动 + Console/JSON”；M1 扩展集合/Map、注解/AOP、检索/时间线、SPI；M2 引入回放、适配外部存储与可视化增强。

2. 范围与边界
- In-Scope（M2）
  - 变更捕获接入：显式 API（M0），注解/AOP（M1）。
  - 字段级 Diff：标量/字符串/日期（M0）；集合/Map 摘要变更（M1）；嵌套深度 M0≤2 / M1≤3 / M2≤5。
  - 事件模型与存储：线程内存事件（M0）；内存全局索引与文件导出（M1，可选）；外部持久化适配点（M2）。
  - 检索与时间线：按对象/字段/时间段（M1）；字段轨迹回放（M2）。
  - 审计导出：Console/JSON（M0），文件滚动/归档（M1）。
  - 与任务树联动：变更聚合到 TaskNode（M0），统计聚合（M1）。
- Out-of-Scope（M2）
  - 跨系统一致性、统一权限中心、复杂多租计费、跨语言 Agent、实时流处理、AI 异常检测。

3. 用户画像与关键场景
- 开发工程师：排查数据异常，确认“哪一步改变了什么值”。
- 架构/性能工程师：将对象状态演化与关键路径/热点耗时对齐分析。
- 审计/合规：导出变更流水满足审计留痕（非强一致）。

4. 需求列表
| ID | Must/Should | 描述 | 业务动机 | 验收标准（量化） |
|---|---|---|---|---|
| M2-F001 | Must | 显式 API：`track/trackAll/getChanges/clearAllTracking` | 最小代价接入 | 用例对 2 字段修改，getChanges 返 2 条；单次检测 P95 ≤ 200μs@2字段；整体 P95 CPU ≤ 3% |
| M2-F002 | Must | 标量字段 Diff（含 String/Date） | 精确可读 | 100 次检测正确率 100%；无异常；空值/类型变化处理稳健 |
| M2-F003 | Must | 与任务树联动（写入 CHANGE 消息） | 一屏观测 | Console/JSON 含“变更段”，映射到正确 TaskNode 路径 |
| M2-F004 | Should | 集合/Map 摘要变更（增/删/替换） | 常见结构覆盖 | 针对 List 增删改各 20 次，准确率 ≥ 95%，超阈值跳过并告警 |
| M2-F005 | Should | 脱敏+白名单（字段通配） | 降低隐私风险 | 命中规则输出“***”；非白名单字段不采集；敏感项覆盖率≥95% |
| M2-F006 | Should | 内存检索与时间线 | 事后分析 | 按对象/字段/时间过滤；1k 条 ≤ 50ms，10k 条 ≤ 200ms |
| M2-F007 | Should | 审计导出 JSON/文本（滚动/归档） | 合规与传递 | 生成 `sessionId-timestamp.json`；大小/滚动可配；失败降级到 Console |
| M2-F008 | Should | 注解/AOP：`@TrackChanges` / 参数 `@Track` | 降改造成本 | 样例可用；禁用零侵入；开销≤5%；异常不影响业务 |
| M2-F009 | Should | 可插拔策略 SPI（Diff/Mask/Serialize） | 定制扩展 | ServiceLoader 注入；多实现按优先级选取；覆盖率>80%用例 |
| M2-F010 | Should | 字段轨迹回放 | 时间旅行 | 返回 (timestamp,value) 序列；窗口 1k 条 ≤ 100ms |

5. 非功能需求
- 性能预算：
  - M0：单对象≤20字段；一次 track + getChanges P95 CPU ≤ 3%。
  - M1：含集合/Map；同路径 P95 CPU ≤ 5%。
  - 读：1k 条查询 ≤ 50ms；10k 条 ≤ 200ms；回放 1k 条 ≤ 100ms。
- 可靠性：
  - 环形缓冲+会话边界清理；溢出降级为仅消息，不阻塞主流程。
  - 内部异常以 `TFI.handleInternalError` 记录，不向业务冒泡。
- 安全：
  - 默认开启脱敏，白名单控制；导出目录需显式配置且校验可写；Actuator 不输出值，只输出计数与健康。
- 可用性/可维护性：
  - 显式 API 5 分钟接入；注解由 Starter 自动配置（M1）。
  - 模块测试覆盖率 ≥ 80%；端到端用示例工程验证。

6. 成功指标（KPI/北极星）
- 采纳：新增 2 篇最佳实践，Star +100。
- 性能：基准报告满足上述阈值。
- 质量：变更检测缺陷密度 < 5/KLOC。

7. 里程碑与交付
| 里程碑 | 范围 | 核心交付 | 验收标准 |
|---|---|---|---|
| M2-M0（2 周） | 显式 API、标量 Diff、任务树联动、Console/JSON | API/快照/对比、导出集成、样例与单测/基准 | 通过 M2-F001/2/3；性能阈值达标 |
| M2-M1（3 周） | 集合/Map、脱敏/白名单、检索/时间线、注解/AOP、SPI、文件导出 | 策略 SPI、内存索引、注解+切面、导出滚动 | 通过 M2-F004/5/6/7/8/9 |
| M2-M2（3 周） | 回放、外部存储适配点、可视化增强 | 回放 API、存储接口、报告增强 | 通过 M2-F010；回放性能达标 |

8. 风险 & 对策
- 反射/递归开销：字段/深度/集合限流；元数据缓存；可关闭。
- 内存占用：环形缓冲+限水位；完成即清理；超限仅消息降级。
- 隐私泄漏：默认脱敏+白名单；导出需显式启用并校验路径；审计格式不含明文敏感值。
- 耦合膨胀：仅以消息/会话关联，不改写 TaskNode 结构；SPI 解耦。

9. 术语表（Glossary）
- ChangeRecord：单字段变更条目（old/new/type/timestamp/object/field/nodeId/sessionId）。
- ObjectSnapshot：某时刻对象字段取值集合（受白名单/深度限制）。
- Timeline：同对象/字段在时间轴上的值序列。
- Replay：基于 Timeline 重建字段历史状态。
- Session：TFI 的会话（现有 `Session` 模型）。

10. 追溯表（需求 → API/序列/存储）
| 需求ID | API/类 | 时序 | 存储 |
|---|---|---|---|
| M2-F001 | TODO: `api/TFI#track/trackAll/getChanges/clearAllTracking` | 业务前后 → capture/diff | ThreadLocal Map（按会话清理）|
| M2-F002 | TODO: `changes/DiffDetector` | capture(before/after) → compare | 内存 |
| M2-F003 | `model/TaskNode#addMessage`（CHANGE） | `TFI.stop()` 汇总写消息 | Session 归档 |
| M2-F004 | TODO: `changes/CollectionDiffStrategy` | diffCollection/diffMap | 内存（摘要）|
| M2-F005 | TODO: `changes/MaskingStrategy + whitelist` | filter → mask → store | 内存/导出 |
| M2-F006 | TODO: `store/InMemoryChangeStore + ChangeQueryService` | query(object/field/time) | 内存索引 |
| M2-F007 | TODO: `export/ChangeExporter` | export(sessionId) | 本地文件 |
| M2-F008 | TODO: `@TrackChanges/@Track + ChangeTrackingAspect` | AOP 前后快照 | 与 AutoConfiguration 对齐 |
| M2-F009 | TODO: SPI 注册（ServiceLoader） | 策略装配 | SPI 注册表 |
| M2-F010 | TODO: `ChangeReplayService#replay` | build timeline | 时间序列缓存 |

