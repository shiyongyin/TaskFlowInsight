Title: TaskFlow Insight — M1/M2 需求与规划（Spring Boot 组件化）
Version/Owner/Status/Date
- Version: v2.0.0-M1/M2
- Owner: Product/Architecture
- Status: Draft → Ready for Execution
- Date: 2025-09-11

1. 背景与目标
- M0 已完成：显式 API、标量 Diff、与任务树联动、Console/JSON 导出、线程隔离与清理、演示与测试。
- M1 目标：在保持 M0 兼容的前提下，提升“对比广度 + 输出灵活 + 可查询”，并提供 Spring Boot 一致的配置与装配体验。新增能力默认关闭、按需启用，有严格护栏保障性能与内存。
- M2 目标：将能力产品化为 Spring Boot Starter，对外作为可重用组件，提供自动装配、可选端点/指标、（可选）持久化与回放/可视化适配点。

2. 产品形态（面向 M2）
- 产物：Spring Boot Starter + AutoConfiguration + 可选子模块
  - core：会话/任务树/消息、TFI 门面、变更追踪核心
  - format-compare（M1）：模板/Formatter、比较/归一化、路径匹配与缓存、集合/Map 摘要
  - store（可选）：InMemory ChangeStore + Query（ring buffer + TTL）
  - export（可选）：文件导出（JSON/JSONL）+ 滚动/归档
  - aop（可选）：@TrackChanges/@Track（默认关闭）
  - actuator/metrics（可选）：只读诊断端点 & Micrometer 计数（默认关闭）
  - spring-boot-autoconfigure（M2 核心）：AutoConfiguration、@ConfigurationProperties、条件装配
  - spring-boot-starter（打包依赖，简化接入）

3. 阶段范围
- M0（已完成）：显式 API、标量 Diff、任务树联动、Console/JSON、线程隔离与清理
- M1（当前里程碑）：嵌套扁平化、集合/Map 摘要、可定制输出（模板/Formatter）、自定义比较器与归一化（按属性/类型/全局）、护栏/指标、（可选）内存查询与本地导出、Spring Boot 整合
- M2（下一里程碑）：组件化发布（Starter）、（可选）端点/指标、注解/AOP 产品化、持久化仓库与时间线（可选）、回放与可视化适配点（可选）

4. M1 需求（按优先级）
4.1 P0（Must）
- 嵌套对象扁平化对比（默认关闭）
  - 将嵌套 Bean 递归为路径字段（示例：address.city），遇循环引用跳过；路径白/黑名单；最大深度限制（默认 2）。
  - 配置：
    - tfi.change-tracking.nested.enabled=false
    - tfi.change-tracking.max-depth=2
    - tfi.change-tracking.whitelist=/regex or ant*
    - tfi.change-tracking.blacklist=/regex or ant*
  - 降级：超深/循环/黑名单 → 跳过并计数（WARN/DEBUG）。

- 集合/Map 摘要对比（默认关闭）
  - List/Set：输出 size 与增删替计数；Map：键增删计数 + 同键值变化摘要；不逐项深度 Diff。
  - 变更类型：COL_ADD/COL_REMOVE/COL_REPLACE。
  - 配置：
    - tfi.change-tracking.collection.enabled=false
    - tfi.change-tracking.collection.max-size=100
    - tfi.change-tracking.collection.depth=1
  - 降级：超阈值仅摘要 + WARN。

- 自定义消息格式（Formatter + 模板）
  - 可插拔 ChangeMessageFormatter；模板占位符统一为 #[...]：#[object]、#[field]、#[old]、#[new]、#[type]、#[time]、#[valueKind]；
  - 解析链：按属性模板 > Bean Formatter > 全局模板 > 默认格式；Console/flushChanges 委托 Formatter。
  - 配置（与 PRD 对齐，归入 change-tracking）：
    - tfi.change-tracking.format.enabled=false
    - tfi.change-tracking.format.template="#[object].#[field]: #[old] → #[new]"
    - tfi.change-tracking.format.templates.{path}="[标签] #[old] => #[new]" | {bean: beanName}

- 自定义比较器与归一化（按属性/类型/全局）
  - ComparatorStrategy & Normalization：数字容差、时间对齐（秒/毫秒）、字符串规范化（trim/case/空白）。
  - 解析链：byPath > byBeanName > byType > default；提供 CompareContext（object、path、kind、配置…）。
  - 配置（与 PRD 对齐，归入 change-tracking）：
    - tfi.change-tracking.compare.enabled=false
    - tfi.change-tracking.compare.rules.byPath.{path}.{numberTolerance|timeRounding|stringNormalize|bean}
    - tfi.change-tracking.compare.rules.byType.{class}.{numberTolerance|timeRounding|stringNormalize|bean}
    - tfi.change-tracking.compare.tolerance.strict-factor=0.01   # strict 模式下的容差收紧系数

- 护栏与最小指标（计数，默认不暴露敏感值）
  - perSession.maxChanges、store.maxChanges、ttl；计数：nested.skipped、collection.truncated、limits.hit、export.failure 等。

4.2 P1（Should）
- InMemory ChangeStore + Query（只读）
  - 会话内索引与 timeline；ring buffer + TTL；弱一致读；默认关闭。
  - 配置：tfi.store.enabled=false、tfi.store.maxChanges、tfi.store.perSession.max、tfi.store.ttl。

- 文件导出（滚动/归档，默认关闭）
  - reports/<sessionId>-<ts>.json|jsonl；路径校验、滚动策略；失败降级 Console。
  - 配置：tfi.export.file.enabled=false、tfi.export.file.dir、tfi.export.file.rollover.*。

4.3 P2（Could）
- 注解/AOP（默认关闭）：@TrackChanges/@Track；方法前后快照/对比/清理；Starter 条件装配。
- 只读查询端点（默认关闭）：Actuator/REST；仅计数/摘要，不含敏感明文。
- 脱敏（内部系统可选）：规则命中输出“***”，默认关闭。

5. M1 验收标准（DOD）
- 功能：嵌套扁平化与集合摘要在开启后正确工作；模板/Formatter 与 Comparator/Normalization 可按属性/类型生效；默认关闭时与 M0 一致。
- 性能：开启 nested.depth=2 与集合摘要后，写路径 P95 CPU ≤ 5%；内存无趋势性上升；降级与护栏生效（计数可见）。
- 集成：Spring Boot AutoConfiguration 生效；@ConfigurationProperties 配置覆盖；Bean 注入策略与优先级解析正确；无与用户上下文冲突。
- 组件化阶段澄清：M1 提供 AutoConfiguration 与配置属性支持；M2 提供 Starter 打包与更丰富的可选端点/指标，二者阶段性目标不同但配置前缀保持一致。
- 测试：单元（路径/集合/比较/模板/缓存）+ 集成（Boot 上下文）+ 并发/寿命 + perf profile（P95/P99）。
- 文档与演示：功能指南、属性字典、示例配置、Demo 场景（嵌套/集合摘要/模板/比较器/查询/导出）。

6. M2 需求（组件化 & 产品化）
- Spring Boot 组件化发布
  - 模块化：core、format-compare、store、export、aop、actuator/metrics、autoconfigure、starter。
  - AutoConfiguration.imports 与条件装配；`spring-configuration-metadata.json`；测试基于 ApplicationContextRunner。
- 可选端点/指标（默认关闭）
  - Actuator 端点：只读计数（不含敏感值）；Micrometer 计数；按配置开启。
- 可选增强
  - 注解/AOP 产品化、持久化仓库（SQL/NoSQL）与回放/可视化适配点、javers-adapter（可插拔 diff 引擎）。
- 兼容性
  - 默认关闭新能力；Console/JSON 向后兼容；需要时为 JSON 增加 schemaVersion。

8. 错误码分类（与 PRD 对齐）
- 1xxx 配置错误：如 MISSING_IDENTITY_PATHS、INVALID_PROFILE_NAME、PROFILE_DEEP_MERGE_CONFLICT、PATTERN_COMPILE_ERROR、TEMPLATE_PLACEHOLDER_INVALID、CONFIG_SCHEMA_VALIDATION_FAILED
- 2xxx 运行时错误：如 DIFF_RUNTIME_FAILURE
- 3xxx 资源错误：如 EXPORT_FILE_LOCK_FAILED、EXPORT_ATOMIC_MOVE_FAILED
- 4xxx 业务错误：预留（如 IMPORT_PROFILE_MISMATCH，M2）

9. 进度评估与风险（工期）
- M1 综合范围（含嵌套/集合摘要/模板/比较/护栏/配置/集成）建议 4–5 周；P1（Store/Export）可并行推进 1–2 周（可拆至 M1 尾或 M2 前期）。
- AOP/注解能力：M1 定义为 P2（Could Have，默认关闭），M2 阶段产品化为可选模块更合理；若提前至 M1‑P1 需压缩其他范围与性能验证时间。

7. 配置草案（示例，按 PRD 对齐）
```yaml
tfi:
  enabled: true
  change-tracking:
    nested:
      enabled: false
      max-depth: 2
      whitelist: []   # e.g. ["address.*", "order.*"]
      blacklist: []   # e.g. ["*.password", "*.secret*"]
    collection:
      enabled: false
      max-size: 100
      track-depth: 1
    format:
      enabled: false
      template: "#[object].#[field]: #[old] → #[new]"
      templates:
        "order.status": "[状态] #[old] -> #[new]"
    compare:
      enabled: false
      rules:
        byPath:
          "order.amount": { numberTolerance: 0.01 }
          "order.createdAt": { timeRounding: seconds }
        byType:
          "java.math.BigDecimal": { numberTolerance: 0.001 }
      tolerance:
        strict-factor: 0.01
    store:
      enabled: false
      maxChanges: 100000
      perSession:
        max: 10000
      ttl: PT10M
    export:
      enabled: false
      dir: "/var/log/tfi"
      rollover:
        sizeMB: 32
        cron: "0 0 * * * *" # hourly

  # 预设 Profile（与 PRD 对齐）
  profile: balanced   # minimal | balanced | full
  profiles:
    minimal:
      change-tracking:
        nested: { enabled: false }
        collection: { enabled: false }
    balanced:
      change-tracking:
        nested: { enabled: true, max-depth: 2 }
        collection: { enabled: true, max-size: 100 }
    full:
      change-tracking:
        nested: { enabled: true, max-depth: 3 }
        collection: { enabled: true }
        store: { enabled: true }
```

8. SPI 与扩展点
- ComparatorStrategy、Normalization、ChangeMessageFormatter、DiffStrategy（集合摘要）
- 解析优先级：byPath > byBeanName > byType > default；支持 @Order/@Priority
- Bean/SPI 双通道装配，允许替换默认实现

9. 风险与对策
- 性能与内存放大（嵌套/集合）：严格护栏（maxDepth/maxSize/maxChanges/ttl）、降级（摘要/跳过）与计数；默认关闭；perf profile 采样 P95/P99。
- 配置误用（路径/模板/规则）：提供示例与默认保守值；按路径细化而非全局一刀切；支持回滚。
- 输出兼容：默认输出不变；模板灰度；JSON schemaVersion（如需）。
- 范围蔓延：避免深度容器逐项 Diff；将 JaVers 作为可选引擎适配，不在 M1 内自研完全体。

10. 交付节奏与工期（参考）
- M1（3–4 周）
  - W1：模板化输出 + 嵌套扁平化（maxDepth/白黑名单/循环检测）
  - W2：集合/Map 摘要 + 比较/归一化 + 护栏/计数
  - W3：InMemory Store + Query（可选）与文件导出（可选）；并发/寿命/Perf；文档与 Demo
  - W4：收尾与性能整饰（可并行推进 AOP/端点 PoC）
- M2（3–6 周，组件化）
  - AutoConfiguration + Starter 发布、Actuator/Metrics 可选、AOP 产品化、（可选）持久化仓库 + 回放/可视化适配点

11. 验收快照
- 默认关闭时与 M0 一致；开启后按配置生效；有可观测的计数与降级；Perf P95≤5%。
- Spring Boot 集成：引入 starter → 打开若干开关 → 即可获得嵌套/摘要/模板/比较等能力。
- 文档：功能指南、属性字典、最佳实践与排错；示例工程与 Demo 可运行。
