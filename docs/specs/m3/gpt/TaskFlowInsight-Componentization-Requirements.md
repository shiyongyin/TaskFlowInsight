# TaskFlowInsight 业务能力组件化需求（M3）

## 文档概览
- 名称：TaskFlowInsight 业务能力组件化需求
- 目标：将现有“可用雏形”进化为“标准业务能力组件”，实现稳定、易用、可运维、可发布、可扩展，并保留完整功能
- 方法：分阶段迭代（M0–M4），每阶段可验收、可回退、可度量（KPI）

## 范围与非目标
- 范围
  - 组件工程化（模块化、自动装配、配置元数据、发布与合规）
  - 接入体验（Starter、AOP、异步传播、最小注入 API）
  - 稳定性与性能（缓存有界化、默认开关、限时限量）
  - 可观测与运维（指标、面板、Runbook、告警建议）
  - 扩展与生态（SPI、TestKit、OTel/日志对齐）
  - AI 融合（离线总结、告警解释、接入/调优建议）
- 非目标
  - 重写全部内部实现
  - UI 控制台/多语言 SDK（可列长期规划）
  - 分布式缓存引入（按指标再决策）

## 现状评估（结合当前仓库）
- 稳定性
  - `PathMatcherCache` 使用 `ConcurrentHashMap` 迭代删除不安全；`patternCache` 无界且与“LRU”描述不符
  - 配置类重复（`ChangeTrackingProperties` 与 `ChangeTrackingPropertiesV2`），混用 `System.setProperty` 注入
- 易用性
  - 无独立 `starter`；缺自动化接入（Web/Tx/Service/Async）
  - 无“阶段标记”最小注入 API（无法低侵入表达多环节语义）
- 可观测
  - Micrometer 指标较全，但无统一面板；`/actuator/tfi` 与 `taskflow` 端点命名/暴露不一致
  - 无 Runbook（回退、限时限量、脱敏策略）
- 工程与发布
  - 缺 `spring-configuration-metadata.json`、CI 质量门禁、LICENSE/SCM/CHANGELOG/发布流程
- 扩展与 AI
  - 无 SPI 与 TestKit 归档；AI 需要标准 JSON 导出与接口层

## 总体设计
- 模块拆分：`tfi-core`（核心、无 Spring 依赖）+ `tfi-spring-boot-starter`（装配/拦截/配置）+ `tfi-examples`（示例）
- 配置统一：收敛到 `ChangeTrackingPropertiesV2`，移除旧类与 `System.setProperty` 路径
- 端点统一：仅保留 `/actuator/tfi`，默认不暴露；与 `management.endpoints.web.exposure.include` 文档一致
- 缓存有界：`PathMatcherCache` 基于 Caffeine 的双缓存（pattern/result），限容量/TTL/统计/驱逐回调
- 接入路径：
  - 自动化：Web Filter/HandlerInterceptor、`@Transactional`/Service AOP、TaskDecorator/@Async 传播
  - 最小注入：【修正】`TFI.stage("环节名")`（返回Stage实现AutoCloseable，块级1行，捕获异常并记录阶段失败/耗时）+ `@TfiTask`/`@TfiTrack`（支持SpEL、采样、超时、字段黑/白名单）
- 可观测：统一 `tfi.*` 指标字典、Grafana 面板 JSON、告警建议与 Runbook
- 工程化：CI/CD（构建/测试/覆盖率/静态检查/依赖扫描）、SemVer/CHANGELOG、LICENSE 与 POM 元数据
- 扩展与 AI：SPI/TestKit、OTel/MDC 对齐；标准 JSON 导出 → 离线报告/告警解释/接入建议

## 【修正】API 契约
- `TFI.stage(String name)`
  - 签名：`public static Stage stage(String name)`；`final class Stage implements AutoCloseable`
  - 语义：进入记录阶段开始；`close()` 记录结束与耗时；阶段异常时记录异常摘要并标记阶段失败（不抛出二次异常）
  - 约束：最大嵌套深度（默认 10，可配 `tfi.stage.max-depth`）；单阶段超时（默认 5000ms，可配 `tfi.stage.timeout-ms`，仅告警）
  - 指标：`tfi.stage.duration.seconds`（秒，支持毫秒精度）、`tfi.stage.error.count`
  - **验收标准**：P99延迟 < 50μs，支持嵌套调用最大深度10层，异常安全不影响业务逻辑
- `@TfiTask`
  - 示例：`@TfiTask(value="order:{#order.id}", sampling=1.0, timeoutMs=1000)`
  - 作用：方法入口/退出自动创建/关闭任务，异常自动标记失败
  - 规则：SpEL 基于方法参数；`sampling` 为 0–1 软采样；`timeoutMs` 为软超时仅告警
- `@TfiTrack`
  - 示例：`@TfiTrack(fields={"userId"}, includes={"*.id"}, excludes={"*.password"}, sampling=0.1)`
  - 作用：在方法入参与返回值处进行对象追踪与差异输出
  - 规则：includes/excludes 为路径模式；默认采样 0（关闭）

## 【新增】标准 JSON 契约（会话导出）
- 根结构
  - `{ version:"1.0", sessionId, startTime, endTime, durationMs, stages:[...], changes:[...], metrics:{...} }`
- stages[]
  - `{ name, path, start, end, durationMs, error?, attributes? }`
- changes[]
  - 复用 ChangeRecord 字段并增加 `taskPath`
- metrics
  - 关键 `tfi.*` 指标快照（单位/口径见“指标单位/口径”）
- 版本策略
  - 新增字段向后兼容；字段删除/重命名须 MAJOR 版本
- 示例（简版）
  - 省略具体 JSON：包含 2 个 stage、2 条 change、metrics 快照（用于开发对照）

## 功能需求
- Starter 自动装配
  - Web 会话/任务自动化（入口/出口）；可继承 `traceId` 作为 `sessionId`
  - Tx/Service AOP：入/出任务边界、异常标记失败、可选参数/返回值快照（按注解启用）
  - 异步传播：`TaskDecorator` 与 `@Async` 专用执行器自动恢复上下文；线程池可配
- 最小注入 API【修正】
  - `try (Stage s = TFI.stage("校验")) { ... }`、`try (Stage s = TFI.stage("查库")) { ... }`（Stage实现AutoCloseable，自动收尾/异常记录/阶段耗时；最大嵌套与超时可配）
  - `@TfiTask("order:{#order.id}")`、`@TfiTrack`（SpEL/采样/超时/字段黑白名单）
- 缓存与匹配
  - `PathMatcherCache` 使用 Caffeine：`maximumSize`、`expireAfterAccess`（可配）、结果缓存与模式缓存分离
  - 【新增】配置键：`tfi.matcher.impl=caffeine|legacy`（默认 caffeine）、`tfi.matcher.pattern.max-size=1000`、`tfi.matcher.result.max-size=5000`、`tfi.matcher.result.ttl=10m`
  - 暴露命中率、容量、驱逐速率指标；支持安全预加载（默认关闭）；提供 `tfi.matcher.impl=legacy` 回退
- Actuator 与导出
  - `/actuator/tfi`：系统状态、变更摘要、上下文统计、健康指示（默认关闭）
  - 标准 JSON 导出：会话树+阶段耗时+变更+指标快照（带版本/时间戳，默认脱敏）
  - 【修正】端点默认关闭：`management.endpoint.tfi.enabled=false`；`management.endpoints.web.exposure.include` 默认不包含 `tfi`；`/actuator/taskflow` 到 `/actuator/tfi` 提供 1 小版本重定向；启用需要显式配置
- 配置元数据
  - `META-INF/spring-configuration-metadata.json`：描述/默认/示例全覆盖；IDE 联想友好
- 文档与样例
  - Quick Start、完整配置与示例、端点说明、指标字典、FAQ/Troubleshooting、Runbook（回退、限时限量、脱敏）

## 【修正】默认值表（保守策略）
- `tfi.enabled=false`  # 默认关闭，需显式启用
- `tfi.change-tracking.enabled=false`  # 默认关闭，避免性能影响
- `tfi.web.auto-session.enabled=false`  # 自动Web会话默认关闭
- `tfi.tx.auto-task.enabled=false`  # 自动事务任务默认关闭
- `tfi.async.propagation.enabled=false`  # 异步传播默认关闭
- `tfi.stage.max-depth=10`
- `tfi.stage.timeout-ms=5000`  # 调整为5秒，避免频繁告警
- `tfi.metrics.percentiles.enabled=false`  # 分位数默认关闭，减少开销
- `tfi.matcher.impl=caffeine`  # 默认使用Caffeine实现
- `tfi.matcher.pattern.max-size=1000`
- `tfi.matcher.result.max-size=5000`
- `tfi.matcher.result.ttl=10m`
- `management.endpoint.tfi.enabled=false`  # 端点默认不启用

## 【修正】指标单位/口径（与Micrometer对齐）
- **时间指标**：duration类型统一使用`seconds`单位，支持毫秒精度（如`tfi.stage.duration.seconds`）；内部采集纳秒需转换
- **比率指标**：命中率以[0,1]浮点表示；容量/计数为整数
- **分位数**：p50/p95/p99默认关闭；开启需显式配置`tfi.metrics.percentiles.enabled=true`
- **标签控制**：限制高基数标签，默认最大1000个不同值，可配置`tfi.metrics.tags.max-cardinality=1000`


## 非功能需求（NFR）
- 性能：典型场景 P95 开销 ≤ 5%；`PathMatcher` 匹配 P99 < 1μs；缓存命中率 ≥ 95%（模式缓存）≥ 99%（结果缓存）；`TFI.stage()` API P99 < 50μs
- 稳定性：并发/长压 2h 无泄漏；异步传播正确；关键路径有回退与降级
- 安全：全链路脱敏（日志/导出/指标统一字典），端点最小暴露与鉴权指引；默认关闭高开销/侵入能力
- 兼容性：Boot 2/3 自动装配双栈通过；JDK 17/21 构建与测试通过；旧配置/端点有一版弃用期与迁移文档
- 可运维：指标→面板→告警→Runbook 闭环；一键 Kill‑switch 与模块级开关

## 配置规范（示例）
- 全局：`tfi.enabled=false`（默认关闭，需显式启用）、`tfi.change-tracking.enabled=false`
- 自动接入：`tfi.web.auto-session.enabled=false`、`tfi.tx.auto-task.enabled=false`、`tfi.async.propagation.enabled=false`
- 端点控制：`management.endpoint.tfi.enabled=false`、`management.endpoints.web.exposure.include`默认不包含`tfi`
- 快照/追踪：`tfi.change-tracking.snapshot.max-depth`、`max-elements`、`value-repr-max-length`、`includes/excludes`
- 匹配器：`tfi.matcher.impl=caffeine`（默认）、`tfi.matcher.pattern.max-size=1000`、`tfi.matcher.result.max-size=5000`、`tfi.matcher.result.ttl=10m`、`tfi.matcher.backup-impl=legacy`（回退选项）
- 端点：`management.endpoints.web.exposure.include`（默认不包含tfi，需显式添加）、`management.endpoint.tfi.enabled=false`（默认关闭）

## 【修正】监控与告警（量化指标）
- 核心指标（统一.seconds单位）
  - `tfi.change.tracking.count`/`duration.seconds`、`tfi.snapshot.creation.count`/`duration.seconds`
  - `tfi.path.match.count`/`duration.seconds`/`hit.ratio`（[0,1]）、缓存容量与驱逐计数
  - `tfi.stage.duration.seconds`、`tfi.stage.error.count`
  - `tfi.error.count`（按类型标签）、健康评分 `tfi.health.score`（[0,1]）
- 告警建议（具体阈值）
  - 模式缓存命中率 < 0.9（90%）
  - 结果缓存命中率 < 0.95（95%）
  - 缓存驱逐率 > 20次/分钟
  - 活跃会话数 > 10000个
  - API延迟P99 > 100ms持续5分钟
  - 高基数标签 > 1000个不同值

## 【新增】统一脱敏策略
- 敏感字段字典（password/secret/token/key/credential…）统一应用于日志/导出/指标
- 支持白名单放行；默认启用脱敏

## 运行手册（Runbook）
- 回退与降级
  - 全局：`tfi.enabled=false`
  - 模块：`tfi.change-tracking.enabled=false`、`tfi.web/tx/async.enabled=false`
  - 匹配：`tfi.matcher.backup-impl=legacy`（回退旧实现）
  - 指标降噪：关闭分位、移除高基数标签、降采样
- 故障定位
  - 面板→告警→`/actuator/tfi`→日志（含 `sessionId/taskPath`）→开关回退

## 工程与发布
- CI/CD
  - `./mvnw clean verify`、JaCoCo（≥80%）、SpotBugs/Checkstyle/Spotless、OWASP 依赖扫描
  - 【新增】最小 CI 产物清单：
    - GitHub Actions：`ci.yml`（mvnw verify）、`coverage.yml`（JaCoCo 80% 阈值）、`static.yml`（SpotBugs/Checkstyle/Spotless）、`deps.yml`（OWASP）
    - 文档产物：`LICENSE`、`CHANGELOG.md`、`pom.xml` License/SCM/Developers
  - 兼容矩阵：Boot 2/3 × JDK 17/21；`ApplicationContextRunner` 轻装配测试
- 发布与合规
  - `LICENSE`（Apache 2.0）、POM License/SCM/Developers、`CHANGELOG`、SemVer 策略、发布到私服/中央仓

## SPI 与 TestKit
- SPI：`Store`/`Exporter`/`PathMatcher` 扩展点与优先级规则；示例扩展工程
- TestKit：JUnit5 Extension 与断言工具（会话/任务/变更列表/阶段耗时验证）

## AI 融合
- 数据出口：标准 JSON（会话树/阶段/变更/指标）默认脱敏
- 能力：离线事故总结/性能瓶颈解释/排查建议；告警附解释；接入/脱敏/采集范围/调优建议原型
- 护栏：只读、二次脱敏、采样、审计留痕；人工确认

## 里程碑与交付
- M0 稳定化（1 周）
  - Caffeine 有界化 `PathMatcherCache`；配置收敛（仅 V2）；端点统一 `/actuator/tfi`
  - 【修正】TFI.stage() API实现（返回Stage实现AutoCloseable）
  - 交付：代码与测试、性能回归报告、更新文档
  - 验收：P95 变化 ≤ 5%；模式缓存命中率 ≥ 95%，结果缓存 ≥ 99%；TFI.stage() P99 < 50μs；无回归
- M1 易用化（1 周）
  - `tfi-spring-boot-starter`、Web/Tx/Service AOP、异步传播、`TFI.stage`、`@TfiTask/@TfiTrack`
  - `spring-configuration-metadata.json` 与 Quick Start
  - 验收：首个服务接入 0.5–1.5 人日；无行为破坏
- M2 运维化（1 周）
  - 指标字典、Grafana 面板 JSON、告警建议；Runbook
  - 验收：面板→告警→Runbook 闭环；试点 MTTR 下降 30%+
- M3 发布化（1 周）
  - CI 门禁、SemVer/CHANGELOG、LICENSE/SCM、发布流程；兼容矩阵通过
  - 验收：管道绿；制品可发布；迁移指南有效
- M4 AI 增强（1–2 周，可并行）
  - 离线报告/告警解释；接入建议原型
  - 验收：对历史问题给出有用报告；严格脱敏

## 【修正】KPI 与 ROI（量化目标）
- 接入成本：首服 2–3 人日 → 0.5–1.5 人日；同构链路 < 0.3 人日
- 故障恢复：MTTR 下降 30–60%；首因定位率 +20–40%
- 性能预算：典型 P95 ≤ 5%；PathMatcher模式缓存命中率 ≥ 95%，结果缓存 ≥ 99%；TFI.stage() P99 < 50μs；缓存驱逐率 < 20次/分钟
- 采用率：接入服务数↑、阶段标记覆盖率↑、面板活跃度↑
- 【新增】稳定性：默认关闭策略确保零影响部署，生产环境可用性 > 99.9%

## 风险与缓解
- 语义兼容（高）：通配符边界/特殊字符回归测试；`tfi.matcher.backup-impl=legacy`
- AOP 开销（中）：默认关闭、灰度启用、限时/采样
- 高基数与敏感（中高）：限制标签维度、统一脱敏、端点最小暴露与鉴权
- CI 收敛（中）：先告警后阻断、提供修复脚本
- 端点/配置迁移（中）：别名/重定向一版保留、迁移指南

## 验收标准（Definition of Done）
- 功能：Starter 自动化接入成功；最小注入 API 可用；端点/导出一致
- 非功能：性能/稳定/安全/兼容/运维达成上述 NFR
- 工程：CI 门禁通过；发布与合规齐备
- 文档：Quick Start/配置/端点/指标/Runbook/FAQ 完成
- 【修正】装配矩阵：使用 `ApplicationContextRunner` 验证 Boot 2.7+/3.x × JDK 17/21 的自动装配与端点开关行为；确保默认关闭策略在所有版本中一致

## 回滚策略
- 全局开关：`tfi.enabled=false`
- 模块开关：`tfi.change-tracking.enabled=false`、`tfi.web/tx/async.enabled=false`
- 匹配器回退：`tfi.matcher.backup-impl=legacy`
- 指标降级：分位关闭/标签精简/降采样

## 开放问题
- Boot 2.x 是否长期支持（如主用户群为 Boot 3 可降优先级）
- 是否引入 Spring Cloud 动态刷新（初期建议不引）
- 是否预留多语言生态导出协议（中长期）

## 变更清单（文件/模块）
- 缓存重构：`src/main/java/.../tracking/path/PathMatcherCache.java`
- 配置收敛：移除旧 `ChangeTrackingProperties` 使用点，仅保留 `V2`
- 端点统一：保留 `actuator/TfiEndpoint`，调整/合并 `TaskflowContextEndpoint`
- 配置元数据：`src/main/resources/META-INF/spring-configuration-metadata.json`
- 自动装配：校验 `META-INF/spring/...AutoConfiguration.imports` 与 `spring.factories`
- 模块拆分：新增 `tfi-spring-boot-starter`（拦截器/AOP/异步/注解/`TFI.stage`）
- CI/CD：新增 `.github/workflows/*`（构建/测试/静态检查/依赖扫描）
- 合规发布：`LICENSE`、`pom.xml`（License/SCM/Developers）、`CHANGELOG.md`
- 文档：`README.md`、`docs/operations/runbook.md`、指标面板 JSON、Quick Start 示例工程

---

## 假设与约束（Assumptions & Constraints）
- 假设
  - 主要运行环境为 Java 17+/Spring Boot 3.x；少量项目仍有 Boot 2.x 需求（提供一版兼容）。
  - 使用 Micrometer 作为指标门面；面板使用 Grafana；日志为 SLF4J/Logback。
  - 无分布式缓存/跨语言 SDK 的强制要求（可后续规划）。
- 约束
  - 典型链路 P95 总开销增量 ≤ 5%；PathMatcher 命中率 ≥ 70%。
  - 端点默认不暴露；敏感信息默认脱敏；所有高开销功能默认关闭。
  - 除 M0 外，迭代必须保持向后兼容；配置弃用需提供一版过渡与迁移说明。

## 就绪定义与分阶段验收（DoR & Iteration Acceptance）
- Definition of Ready（每项工作在进入迭代前需满足）
  - 需求已在本文件标注范围/目标/验收标准；无二义性。
  - 影响范围（代码、配置、文档、CI）已列出，风险与回退方案明确。
  - 基线指标与观测方式明确（如何测量 P95、命中率、内存）。
- 分阶段验收补充
  - M0：重构前后性能对照报告；缓存功能/边界/并发/长压用例通过；配置/端点一致性测试通过。
  - M1：Starter 在示例与真实样板服务中零/低代码接入成功；AOP/拦截开关有效；`TFI.stage` 可捕获异常与阶段耗时。
  - M2：面板 JSON 可导入；告警策略能触发/恢复；Runbook 覆盖回退/降级/脱敏；结构化日志含 `sessionId/taskPath`。
  - M3：CI 门禁绿；SemVer & CHANGELOG 生效；Boot 2/3 与 JDK 矩阵通过；迁移指南可按步骤完成。
  - M4：AI 离线报告对历史事件有帮助（人工评审通过）；严格脱敏/采样/审计留痕落地。

## KPI 基线与测量计划（Measurement Plan）
- 基线采集（当前主分支）
  - 典型链路 P50/P95/P99、CPU/Heap、PathMatcher 命中率/驱逐率；以 30min 稳态压测获得。
  - 变更追踪/快照/匹配的单项分位时延（Micrometer Timer）。
- 目标值与达成判定
  - P95 总开销增量 ≤ 5%；命中率 ≥ 70%；驱逐率 < 20%/min；错误计数无异常上升。
  - 接入成本（人日）与 MTTR 下降（试点服务对照前后 2 周窗口）。
- 度量方法
  - 通过 Prometheus/Grafana 面板与导出报告；在 PR 合并前附简报。

## 风险台账（Risk Register）
| ID | 风险描述 | 可能性 | 影响 | 等级 | 缓解措施 | Owner |
|----|---------|--------|------|------|----------|-------|
| R1 | PathMatcher 重构语义偏差/抖动 | 中 | 高 | 高 | 回归对照、边界单测、`legacy` 回退开关 | Core |
| R2 | AOP/拦截器带来开销 | 中 | 中 | 中 | 默认关闭、灰度、采样/限时 | Starter |
| R3 | 指标高基数/高开销 | 中 | 中 | 中 | 限制标签、关闭分位、采样 | SRE |
| R4 | 敏感信息外泄 | 低 | 高 | 高 | 统一脱敏字典、默认脱敏、端点最小暴露与鉴权 | Security |
| R5 | 配置/端点不兼容 | 中 | 中 | 中 | 一版弃用期、别名/重定向、迁移指南 | Core |
| R6 | CI 门禁阻断交付 | 中 | 中 | 中 | 先告警后阻断、提供修复脚本 | DevOps |

## 兼容与迁移策略（Backward Compatibility & Migration）
- 配置弃用策略：旧键/类保留 1 个小版本（记录 `@Deprecated` 与日志警告），同时提供迁移对照表与脚本示例。
- 端点迁移：`/actuator/taskflow`（如仍用）提供只读重定向到 `/actuator/tfi`，保留 1 个小版本。
- 语义兼容：匹配器提供 `tfi.matcher.backup-impl=legacy` 回退；默认使用新实现。

## 测试策略与覆盖矩阵（Test Strategy）
- 类别
  - 单元：PathMatcher 边界/通配符/特殊字符/极限长度；快照/对比异常路径；导出与脱敏。
  - 并发：上下文传播、ThreadLocal 泄漏、拦截器并发；长压 2h。
  - 集成：AutoConfiguration 装配矩阵（Boot 2/3 × JDK 17/21）、AOP/拦截开关、端点暴露策略。
  - 性能：重构前后对照；分位时延统计；内存/GC 观测。
- 覆盖矩阵（示意）
  - 功能点 × 环境/版本 × 正反/边界用例；最少 80% 语句/分支覆盖，关键路径 90%+。

## 依赖与集成（Dependencies & Integration）
- 外部依赖：Spring Boot、Micrometer、Caffeine；（可选）OpenTelemetry、Logback。
- 集成点：Actuator、Micrometer Registry、日志 MDC、线程池 TaskDecorator、`@Async`。
- 兼容性：通过 `ApplicationContextRunner` 验证自动装配在不同 Boot/JDK 矩阵的行为一致。

## 安全与隐私（Security & Privacy）
- 数据治理：敏感字段默认脱敏；统一字典（password/secret/token/key/credential 等）贯穿日志/导出/指标。
- 暴露控制：端点默认关闭；明确网关/鉴权建议（仅内网/VPN/受控来源）。
- SBOM/依赖白名单：CI 扫描（OWASP/Trivy 可选）；LICENSE 校验与报告。
- AI 合规：只读、二次脱敏、采样、审计留痕；禁止自动改配置（需人工确认）。

## 发布与变更治理（Release & Change Governance）
- 版本策略：SemVer（MAJOR 破坏性；MINOR 向后兼容新增；PATCH 修复），在 CHANGELOG 标注。
- 发布节奏：每迭代一个可发布版本；RC 至少 3 天试运行；灰度策略与回滚预案。
- 变更控制：重大变更需 ADR/评审记录（链接 VIP 文档与任务单）。

## 可追溯性与参考（Traceability & References）
- 文档索引：VIP 文档、任务单（如 `docs/task/...`）、架构文档与本需求文档的相互链接。
- 代码映射：本文件“变更清单”章节提供实现到文件级的映射指引。

## 术语表（Glossary）
- Session/TaskPath：一次业务流程与阶段路径标识。
- Stage：块级阶段标记（`TFI.stage`），用于细分同一方法内的业务环节。
- DoR/DoD：就绪/完成定义，用于进入迭代与验收的质量门槛。

## 附录 A：指标目录（Metrics Catalog）
- 变更追踪：`tfi.change.tracking.count`、`tfi.change.tracking.duration`（p50/p95/p99）
- 快照：`tfi.snapshot.creation.count`、`tfi.snapshot.creation.duration`
- 路径匹配：`tfi.path.match.count`、`tfi.path.match.duration`、`tfi.path.match.hit.count`
- 缓存：`tfi.matcher.pattern.cache.size`、`tfi.matcher.result.cache.size`、`tfi.matcher.evictions`
- 错误：`tfi.error.count`、`tfi.error.type{type=...}`
- 健康：`tfi.health.score`

## 附录 B：Grafana 面板构成（示例）
- 概览页：健康评分、总 QPS、P50/P95/P99、错误率
- 追踪页：变更/快照/匹配分位与次数、缓存命中/驱逐、上下文活跃数
- 告警页：命中率低、驱逐高、P95 异常提升、高基数标签

## 附录 C：AI 模板占位（可扩展）
- 事故总结模板：输入（会话树+阶段耗时+异常+指标快照）→ 输出（摘要/疑似根因/排查建议/下一步）
- 性能解释模板：输入（分位+缓存+GC+热点阶段）→ 输出（瓶颈分析与调优建议）
- 接入建议模板：输入（日志/阶段分布）→ 输出（`TFI.stage` 插入点/AOP 切点/脱敏与采集范围建议）

## 质量门禁清单（Quality Gate Checklist）
- [ ] DoR/DoD 符合；风险台账已更新；回退策略已验证
- [ ] KPI 基线与目标差明；测量计划可执行
- [ ] CI 门禁绿（覆盖率/静态检查/依赖扫描）
- [ ] 文档齐备（Quick Start/配置/端点/指标/Runbook/FAQ）
- [ ] 兼容/迁移与弃用策略明确；发布与变更治理生效
