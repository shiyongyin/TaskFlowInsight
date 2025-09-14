# TaskFlow Insight — M1 PRD（产品需求文档）

## 文档信息
| 属性 | 值 |
|------|-----|
| **Version** | v2.0.0-M1 |
| **Owner** | PM（产品）/SA（架构） |
| **Status** | Final-Draft |
| **Date** | 2025-01-11 |

---

## 1. 执行摘要

### 1.1 产品定位
在M0基础上，提升变更追踪的"对比广度、输出灵活性、可查询性"，为Spring Boot应用提供企业级的变更追踪能力。

### 1.2 M1主题
**Enhanced Change Tracking** - 增强型变更追踪

### 1.3 核心价值
从"能用"到"好用"，支持复杂对象结构、灵活输出格式、自定义比较规则。

---

## 2. 背景与现状

### 2.1 M0完成情况（95.6%达成）
| 功能模块 | 完成状态 | 证据 |
|---------|---------|------|
| 显式API | ✅ 完成 | track/trackAll/getChanges/clearAllTracking |
| 标量Diff | ✅ 完成 | DiffDetector支持所有标量类型 |
| 任务树联动 | ✅ 完成 | CHANGE消息自动写入TaskNode |
| Console/JSON | ✅ 完成 | 导出格式验证通过 |
| 性能指标 | ✅ 超标完成 | P95=3.50μs（目标200μs），CPU<3% |
| 线程隔离 | ✅ 完成 | ThreadLocal完整清理机制 |

### 2.2 用户反馈与痛点
- **对比能力不足**：不支持嵌套对象、集合、Map
- **输出格式固定**：无法自定义变更消息格式
- **比较规则单一**：无法处理精度、容差等特殊场景
- **缺少查询能力**：变更记录只能实时查看，无法回溯

### 2.3 竞品分析（vs JaVers）
| 能力 | TaskFlowInsight(M0) | JaVers | M1目标 |
|------|-------------------|--------|--------|
| 嵌套对象 | ❌ | ✅ 完整递归 | ✅ 扁平化路径 |
| 集合对比 | ❌ | ✅ 3种算法 | ✅ 摘要对比 |
| 自定义比较 | ❌ | ✅ Comparator | ✅ 策略链 |
| 输出格式 | 固定 | 模板化 | ✅ 模板+Formatter |
| 查询能力 | ❌ | ✅ Repository | ✅ 内存查询 |

## 3. 用户画像与场景

### 3.1 目标用户
- **主要用户**：Spring Boot应用开发者（80%）
- **次要用户**：架构师、运维工程师（15%）
- **其他用户**：审计人员、产品经理（5%）

### 3.2 核心场景
| 场景 | 用户故事 | M1解决方案 |
|------|---------|-----------|
| 复杂对象追踪 | 作为开发者，我需要追踪Order对象内部的Customer、Address等嵌套对象变化 | 嵌套扁平化，如order.customer.name |
| 集合变更审计 | 作为审计员，我需要知道权限列表具体增加或删除了哪些项 | 集合摘要：新增2项[ADMIN,EDITOR] |
| 业务语义展示 | 作为产品经理，我希望看到"订单状态：待支付→已支付"而不是"status: 1→2" | 自定义Formatter+模板 |
| 精度容差处理 | 作为开发者，金额0.01的差异应该被忽略 | 自定义比较器：numberTolerance |
| 变更历史查询 | 作为运维，我需要查询过去10分钟某个对象的所有变更 | 内存Store+Query API |

## 4. M1功能需求（按优先级）

### 4.1 P0 - 核心功能（Must Have）

| ID | 功能 | 用户价值 | 验收标准 |
|----|------|---------|---------|
| M1-F001 | 嵌套对象扁平化 | 支持复杂业务对象 | • 递归深度可配（默认2层）<br>• 循环引用自动跳过（定义为“cut”：当在当前 DFS 路径上按引用再次遇到同一对象时，终止对该对象的二次展开；兄弟分支继续处理；记录首个环路路径 DEBUG 日志，计入 nested.cycle.skipCount）<br>• 超深限制计入 nested.depth.limitCount（WARN 聚合）<br>• 路径白/黑名单过滤<br>• 性能P95≤5% |
| M1-F002 | 集合/Map摘要 | 精确识别集合变化 | • List/Set显示增删数量<br>• Map显示key变化<br>• 超阈值自动降级为 size-only（任一侧 size > collection.max-size 时，仅输出大小变化，不再提供增删计数与示例项）<br>• 不做深度逐项对比<br>• （可选）示例项展示：通过 `collection.examples.enabled=false` 开关手工开启；`collection.examples.topN` 默认 3；`collection.examples.item-max-length` 默认 128；`collection.examples.line-max-length` 默认 512；排序按 valueRepr + `sort-mode`（默认 STRING，大小写不敏感）；Set 亦按此排序；Map 示例仅展示 key 的新增/删除，同 key 值变化仅计数 |
| M1-F003 | 自定义消息格式 | 满足不同展示需求 | • 模板变量替换<br>• Formatter接口<br>• 按路径/类型配置<br>• 优先级链解析 |
| M1-F004 | 自定义比较规则 | 处理特殊比较逻辑 | • 数值容差<br>• 时间精度<br>• 字符串归一化<br>• Context感知 |
| M1-F005 | 护栏与监控 | 保障系统稳定性 | • 内存/CPU限制<br>• 降级机制<br>• 关键指标计数<br>• 可观测性 |
| M1-F009 | Spring集成 | 开箱即用、可配置 | • AutoConfiguration<br>• @ConfigurationProperties<br>• 条件装配（ConditionalOn*）<br>• 提供 Starter 包<br>• 默认关闭新能力，开启后按配置生效 |

### 4.2 P1 - 增强功能（Should Have）

| ID | 功能 | 用户价值 | 验收标准 |
|----|------|---------|---------|
| M1-F006 | 内存查询Store | 会话内变更检索 | • 基于 Caffeine（maximumSize+expireAfterWrite）<br>• TTL 自动清理（弱一致读）<br>• 按对象/字段/时间查询<br>• 1k条≤50ms |
| M1-F007 | 文件导出 | 持久化审计记录 | • JSON/JSONL格式（包含元数据：profile、tfiVersion、timestamp、host、instanceId/pid；导入验证预留至 M2）<br>• 自动滚动归档<br>• 路径安全校验<br>• 失败降级Console |


### 4.3 P2 - 可选功能（Could Have）

| ID | 功能 | 用户价值 | 验收标准 |
|----|------|---------|---------|
| M1-F008 | 注解支持 | 声明式配置 | • @TrackChanges<br>• @CompareWith<br>• @FormatTemplate<br>• 默认关闭 |

## 5. 非功能需求

### 5.1 性能要求
| 指标 | M0基线 | M1目标 | 降级阈值 |
|------|--------|--------|---------|
| 2字段 track P95 | 3.50μs | ≤200μs | 500μs |
| 嵌套（深度2）P95 | N/A | ≤500μs | 1ms |
| 集合示例（100项）P95 | N/A | ≤2ms | 5ms |
| CPU增量 | <3% | ≤5% | 10% |
| 内存增量 | <10MB | ≤50MB | 100MB |
| 嵌套深度 | N/A | 2层 | 5层 |
| 集合大小 | N/A | 100项 | 1000项 |

> 说明：
> • perf profile：重场景（嵌套/集合摘要等）以 P95/P99 验证，固定硬件/核数、统一 JVM 参数、预热 3–5 轮；默认不在常规 CI 执行；
> • CI 轻量 perf：在 CI 中跑小规模微基准（例如 2 字段 P95 ≤ 2ms）作为回归栅栏，防止性能明显退化。
> • Profile 与性能：除非特别说明，以上指标针对 balanced Profile；full Profile 允许最高 +20% 放宽；minimal 预期优于 balanced。

### 5.2 可靠性要求
- **降级策略**：超限自动降级，不影响主流程
- **护栏执行**：
  - 配置期：装配时进行静态校验，超出建议阈值输出 WARN（包含路径与建议值）；
  - 运行期：超限即时触发强制降级/短路（计数可观测），确保不会突破资源边界。
- **隔离机制**：异常不向业务冒泡
- **资源控制**：Ring Buffer + TTL 防止内存泄漏
- **兼容性**：所有新增能力默认关闭；默认配置下 Console/JSON 输出与 M0 保持一致；提供灰度与回滚开关（按能力逐项启停）。
- **安全边界**：Store/Query 仅用于会话/进程内调试，非长期合规审计；文件导出默认关闭，路径需校验可写且非系统目录，失败降级 Console 并记录 ERROR。

### 5.3 易用性要求
- **M0兼容模式零配置**：默认仅启用 M0 能力以保证兼容与性能；M1 新能力（嵌套/集合摘要/模板/比较/存储/导出）默认关闭，需按配置显式开启。
- **渐进式增强**：新功能默认关闭
- **向后兼容**：M0 API完全兼容

## 6. 技术方案要点

### 6.1 架构设计
```
┌─────────────────────────────────────┐
│         Application Layer           │
├─────────────────────────────────────┤
│     TFI API (Facade Pattern)        │
├──────────┬──────────┬───────────────┤
│ Tracking │ Format   │  Compare      │
│  Core    │ Engine   │  Strategy     │
├──────────┴──────────┴───────────────┤
│    Storage & Query (Optional)       │
├─────────────────────────────────────┤
│   Spring Boot Integration           │
└─────────────────────────────────────┘
```

### 6.2 关键技术决策
| 决策点 | 方案选择 | 理由 |
|--------|---------|------|
| 嵌套处理 | 扁平化路径 | 避免深度递归性能问题 |
| 集合对比 | 摘要而非逐项 | 平衡信息量与性能 |
| 模板引擎 | 自研轻量级 | 避免重依赖 |
| 存储方案 | Caffeine Cache（maxSize+TTL，CaffeineSpec/Builder 映射） | 简化实现，线程安全、过期与容量可控 |
| 扩展机制 | Strategy + Chain | 灵活且可组合 |
| 文件导出 | 临时文件 + 原子移动；进程内锁 + 可选文件锁 | 保证并发安全与可恢复性，降低损坏风险 |

### 6.3 配置示例
```yaml
tfi:
  change-tracking:
    # 嵌套对象配置
    nested:
      enabled: true
      max-depth: 2
      whitelist: ["order.*", "user.*"]
      blacklist: ["*.password", "*.secret"]
      cycle:
        policy: cut           # cut | marker | error（默认 cut）
        marker: "⟲ #[backRefPath]"  # 当 policy=marker 时的占位文本（见 6.9）
    
    # 集合对比配置
    collection:
      enabled: true
      max-size: 100
      summary-only: true     # 仅摘要；如需示例项，请开启下方 examples.enabled
      degrade:
        enabled: true        # 任一侧 size 超过 max-size 时触发；M1 固定为 size-only 降级
      examples:
        enabled: false     # 手工开启示例项展示；默认关闭
        topN: 3            # 展示前 N 个（固定升序）
        sort-mode: STRING   # STRING | NUMERIC | NATURAL（默认 STRING，NATURAL 可能影响性能）
        item-max-length: 128   # 单个示例项的最大字符串长度
        line-max-length: 512   # 单次输出的最大总长度（超限以 … 结尾）
    
    # 格式化配置（占位符统一使用 #[...]）
    format:
      template: "#[object].#[field]: #[old] → #[new]"
      templates:
        "order.status": "订单状态: #[old] → #[new]"

    # valueRepr 配置（稳定展示标识）
    valueRepr:
      identity-paths:
        "com.example.User": ["id", "username"]
        "com.example.Order": ["id"]
      max-len: 120
      require-identity-paths-in-prod: true  # 生产环境建议开启：关键类型未配置将 WARN
      fail-on-missing: false                # 启动时校验未满足时直接失败；仅针对关键类型（见下文）
    
    # 比较规则配置
    compare:
      enabled: true
      rules:
        "*.price": { numberTolerance: 0.01 }
        "*.timestamp": { timeRounding: "seconds", stringNormalize: false }
      context:
        zone-id: UTC          # 仅用于时间统一到 ISO-8601（M1 不提供 locale 相关比较）
        attributes:
          tenantId: demo      # 透传给自定义 Comparator/Formatter 用于租户特定策略（M1 内置不使用）
          precision: normal   # 内置使用：normal|strict；strict 表示收紧容差而非忽略，见下方 strict-factor
      tolerance:
        strict-factor: 0.01   # 当 precision=strict 时，实际 numberTolerance = 配置值 * strict-factor（默认 0.01）；如需零容差可设为 0.0

    # Store/Query 配置（P1）
    store:
      enabled: false
      caffeine-spec: "maximumSize=10000,expireAfterWrite=10m"  # 可选：使用 CaffeineSpec；或通过 maximumSize/expireAfterWrite 字段由程序构建
      maximum-size: 10000         # 当未提供 caffeine-spec 时生效
      expire-after-write: 10m     # 当未提供 caffeine-spec 时生效
```

### 6.4 路径命名规范（新增）
- fieldPath 为点分路径（不含根对象名），示例：`address.city`；最终输出由 `#[object].#[field]` 组合。
- 数组/集合不展开为索引路径；集合/Map 仅输出摘要信息（size/增删计数/可选 Top‑N 示例项）。
- 分隔符固定为 `.`；模板中不支持表达式，仅占位替换。

### 6.5 匹配与优先级规则（新增）
- 路径匹配默认使用 Ant 风格（`*`、`**`），可选正则；大小写敏感；
- 解析优先级：byPath > byBeanName > byType > default；白名单优先于黑名单；
- 缓存：缓存的是“pattern 字符串 → 编译后 Pattern 对象”的 LRU（默认 256，可配）；同时缓存 path→resolver（已解析策略/模板）上限默认 1024，可配；过大将降低命中率并增加内存占用，不建议随意调大。

### 6.6 模板与时间/数值格式（新增）
- 模板占位：`#[object]、#[field]、#[old]、#[new]、#[type]、#[time]、#[valueKind]`；
- 时间格式默认 ISO‑8601（UTC，可配时区）；
- 字符串归一化（可选）：trim/忽略大小写/空白规范化；
- 数值容差默认绝对容差（单位与字段语义一致），可按路径覆写；
- 禁止在模板中使用表达式或脚本，仅允许占位替换。

• #[valueKind] 定义：
- 取值：SCALAR、ENUM、NULL、MISSING、COLLECTION、ARRAY、MAP、NESTED、CYCLE、BINARY；
- 规则：按值的结构类型判定；当循环占位启用且命中时为 CYCLE；数组与集合分别标注为 ARRAY 与 COLLECTION；
- 用途：模板侧可基于 `#[valueKind]` 选择条件片段或不同占位渲染路径。

• BINARY 与 MISSING 定义：
- BINARY：`byte[]`、`ByteBuffer`、`InputStream` 等二进制类型。M1 仅输出摘要（长度/可选短哈希），不渲染内容；差异摘要示例：`Binary length changed from X to Y`。
- MISSING：字段缺失（未提供/无法访问/被过滤）；不同于 NULL（字段存在但值为 null）。区分意义：可表达“字段不存在” vs “字段被置空”的语义差异。

### 6.7 集合示例项规则（新增）
- 开关：`collection.examples.enabled=false`（默认关闭，手工开启；若 `summary-only=true` 则无效）；
- Top‑N：`collection.examples.topN`（默认 3），按 valueRepr + `sort-mode` 排序输出（默认 STRING，大小写不敏感；NATURAL 需解析片段，可能影响性能）；
- 截断：单项 `item-max-length`（默认 128），总行 `line-max-length`（默认 512），超限以 `…` 结尾；
- Map：仅展示 key 的新增/删除；同 key 值变化仅计数；
- 复杂对象：示例项使用字符串化（转义+截断），不逐项深度对比，以保障性能；
 - 性能提示：当 `sort-mode=NATURAL` 时，启动时输出一次 WARN 日志（logger: tfi.collection.examples）提示自然排序可能带来额外 CPU 开销；建议仅在调试/复核时启用。

• valueRepr（用于排序与展示的稳定字符串）定义：
- 标量：数字/布尔/字符串转为规范化字符串；枚举使用 `name()`；日期时间统一为 ISO‑8601（受 `zoneId` 影响）；
- Bean/对象：仅使用确定性标识字段（配置的 identityPaths，如 `id`/`code`/`name`）；若不存在则尝试 `getId()/id/name`；否则回退为 `ClassSimpleName`（不含不稳定的 identityHex）；
- Map/集合：用于示例预览时可输出 JSON 风格短预览（键名排序、受长度限制），仅为展示用途；
- 排序模式（`collection.examples.sort-mode`）：
  - STRING（默认）：按 valueRepr 字符串不区分大小写升序；
  - NUMERIC：当双方均为纯数字（可含前导符号/小数点）时按数值升序，否则回退 STRING；
  - NATURAL：对由数字与非数字片段组成的字符串进行自然排序（可能带来额外 CPU 开销）。
- 可配置：`valueRepr.identity-paths`（按类型配置标识字段），`valueRepr.max-len`（默认 120）。

注：valueRepr 仅用于展示与排序，不改变对比语义。

• 生产环境建议：
- 为关键业务类型（如聚合根）明确配置 `valueRepr.identity-paths`，避免回退到 `ClassSimpleName` 受混淆影响导致不稳定；
- 可开启 `valueRepr.require-identity-paths-in-prod: true` 启用强校验；未配置的关键类型将记录 WARN；
- 若设置 `valueRepr.fail-on-missing=true`，则在启动阶段直接失败并输出缺失列表（见下文）。

• fail-on-missing 行为：
- 失败时机：应用启动（Spring Context 刷新前后任一初始化阶段均可，推荐在 AutoConfiguration 校验阶段）；
- 判定范围：默认仅检查标注 `@jakarta.persistence.Entity` 或 `@org.springframework.data.mongodb.core.mapping.Document` 的类；
- 错误信息：`Missing identity-paths for types: [com.foo.A, com.bar.B]`（JSON 数组形式，便于定位）；

• 关键类型判定扩展：
- 配置扩展：
  - `valueRepr.key-type-annotations`: ["com.my.Annot1", "com.my.Annot2"]
  - `valueRepr.key-type-packages`: ["com.my.domain", "org.acme.model"]（前缀匹配）
- SPI 扩展：`KeyTypeResolver` 接口允许以编程方式判定关键类型；多个 resolver 结果取并集。

### 6.8 Context 感知比较（新增）
- 定义：比较过程接收 `DiffContext`（TFI 内部上下文，而非 Spring ApplicationContext），用于指导规范化与容差策略。
- 最小字段（M1）：`zoneId`、`attributes: Map<String,Object>`（允许传入 actorId/tenantId/redactionProfile/precision 等）。
- 场景：
  - 日期时间：按 `zoneId` 统一到 ISO‑8601 再比较；
  - 数值：从 `attributes.precision` 选择容差策略（normal/strict），并应用 `compare.tolerance.strict-factor` 调整容差（strict 下 = 配置容差 × strict-factor）。
- 字符串：仅支持简单规范化（可选 trim/小写），不进行 locale/重音折叠（避免 Collator/ICU4J 性能损耗）。
- API 形态（概念）：`diff(a, b, DiffContext ctx)`；默认提供时间相关的 Context 感知比较能力。
- 非目标（M1）：不直接访问 Spring 容器中的 Bean；locale 相关比较留待 M2。

• attributes 约定（M1）：
- precision：`normal|strict`（默认 normal）。`strict` 表示收紧容差（实际容差 = 配置容差 × `compare.tolerance.strict-factor`）；如需零容差可将 strict-factor 设为 0.0；
- tenantId：字符串，透传给自定义 Comparator/Formatter，以便在多租户场景下做差异化 redaction/格式化；内置策略不使用；
- 其他 attributes 均为扩展点，自定义策略可自由约定并读取。

### 6.9 循环引用处理（新增）
- 判定：使用基于引用的检测（Identity）；仅将“当前 DFS 路径上的重复出现”视为环（允许同一对象在不同分支被重复访问）。
- 策略：`nested.cycle.policy = cut | marker | error`（默认 cut）。
  - cut：第二次访问同一对象即终止展开（不影响兄弟分支）。
  - marker：在该节点输出占位（`nested.cycle.marker`），不再向下展开。
  - error：记录 ERROR 并中止本次字段展开（不建议生产使用）。
- 日志（DEBUG）：`Cycle detected: path=A.b.c -> backRef=A`（仅记录首个环路路径）；
- 计数：`nested.cycle.skipCount` 统计被 cut/marker 的次数，用于护栏与监控。

• 性能实现说明：
- 使用 `IdentityHashMap<Object, Integer>` 维护“当前 DFS 路径计数”，push/pop O(1)；深度为 2–5 的常见场景开销可控；
- 仅在“路径进入/退出”时更新计数并判定是否构成环，避免 O(n²) 扫描；

• marker 变量来源与格式：
- `#[backRefPath]`：指向首次出现该对象的路径（点分），如 `order.customer`；
- `#[currentPath]`：当前展开路径，如 `order.customer.address`；
- 可选 `#[backRefDepth]`：从 `currentPath` 回退到 `backRefPath` 的步数（整数）；
- 示例：`marker: "⟲ #[backRefPath]"` 输出 `⟲ order.customer`。

### 6.10 预设配置 Profile（新增）
- 目标：降低配置复杂度，提供“开箱即用”的预设开关组合；通过 `tfi.profile` 选择，显式属性优先级高于 Profile。
- 选择：`tfi.profile = minimal | balanced | full`（默认 minimal，等价于 M0 兼容模式）。
- 建议：生产环境优先选择 balanced（在保障性能与可读性间取平衡）。
- 语义：
  - minimal（默认，仅 M0 兼容）：
    - nested.enabled=false；collection.enabled=false；
    - format.template="#[object].#[field]: #[old] → #[new]"；compare.enabled=true（仅基础规则）、context 仅 zoneId；
    - store/export 默认关闭；
  - balanced（推荐生产）：
    - nested.enabled=true，max-depth=2；collection.enabled=true 且 summary-only=true、degrade.enabled=true、examples.enabled=false；
    - compare.rules 支持常见数值/时间；
  - full（调试）：
    - 启用所有 M1 能力（包含内存查询与导出），但遵循护栏（degrade、max-size、max-depth）。

• 自定义 Profiles（可选，标准 YAML 缩进）：
```yaml
tfi:
  profile: balanced
  profiles:
    minimal:
      change-tracking:
        nested:
          enabled: false
        collection:
          enabled: false
    balanced:
      change-tracking:
        nested:
          enabled: true
          max-depth: 2
        collection:
          enabled: true
          summary-only: true
    full:
      change-tracking:
        nested:
          enabled: true
          max-depth: 3
        collection:
          enabled: true
          examples:
            enabled: true
```

• 合并与优先级（Deep Merge）：
- 合并策略：对映射（Map）采用深度合并；同一路径上的标量与对象以“显式配置覆盖 Profile，Profile 覆盖默认值”；列表（List）采用替换而非拼接；
- 优先级：显式配置 > 自定义 profiles > 内置 profiles > 默认值；
- 示例：当 `profile=balanced` 且用户设置 `nested.max-depth=5` 时，其余 balanced 配置（如 examples.enabled=false）保持不变，仅该路径被覆盖；
- 不存在的 profile：启动失败并输出 ERROR，指明非法值与可选集合；
- 不支持 Profile 继承/引用链，避免循环与解析不确定性。

• 生命周期：
- Profile 在应用启动时解析与生效；不支持运行时热切换；切换需重启应用（缓存、Store、编译 Pattern 等均与 Profile 绑定）。

• Null 合并语义：
- Null 视为“未设置”，不参与覆盖（即不会将已有值覆盖为 null）；如需显式清空由后续版本提供专门语义。

• 护栏优先级：
- 护栏（max-depth/max-size/degrade 等）优先于 Profile 与显式配置，防止不当配置突破安全边界。

• 性能与实现：
- Profile 合并在启动时执行一次，结果缓存复用，不在运行中重复合并；深层合并路径有诊断日志（DEBUG）。

## 7. 里程碑计划

| 阶段 | 时间 | 交付物 | 验收标准 |
|------|------|--------|---------|
| Week 1 | 1.13-1.17 | • Spring集成基础（AutoConfig + @ConfigurationProperties）<br>• 配置体系（Profile + Deep Merge）<br>• 嵌套扁平化<br>• 基础模板 | 功能可用，测试通过 |
| Week 2 | 1.20-1.24 | • 集合摘要<br>• 比较策略 | 性能达标，配置生效 |
| Week 3 | 1.27-1.31 | • 内存Store<br>• 文件导出 | 查询可用，导出正常 |
| Week 4 | 2.3-2.7 | • Spring集成完善（高级特性）<br>• 文档完善 | 集成测试，文档齐全 |

## 8. 成功指标

### 8.1 业务指标
- **功能覆盖率**：支持80%常见对象结构
- **性能满足率**：95%场景满足性能要求
- **用户满意度**：NPS > 40

### 8.2 技术指标
- **代码覆盖率**：核心模块 > 85%
- **性能基准**：全部通过
- **缺陷密度**：< 3/KLOC

### 8.3 采纳指标
- **集成项目数**：> 5个
- **社区反馈**：Issue响应 < 48h
- **文档完整度**：100%功能有文档

## 9. 风险与对策

| 风险 | 影响 | 概率 | 对策 |
|------|------|------|------|
| 性能退化 | 高 | 中 | • 严格护栏<br>• 降级机制<br>• 性能基准 |
| 配置复杂 | 中 | 高 | • 默认配置<br>• 配置模板<br>• 详细文档 |
| 兼容性问题 | 高 | 低 | • 充分测试<br>• 灰度发布<br>• 版本管理 |
| 需求蔓延 | 中 | 中 | • 严格范围控制<br>• 增量交付 |
| 集合示例项带来额外开销 | 中 | 中 | • 默认关闭 `collection.examples.enabled=false`<br>• 启用后受 `topN/item-max-length/line-max-length` 限制<br>• 仅在调试/复核时短期开启；生产建议保持摘要模式 |
| 文件导出并发 | 中 | 中 | • 进程内基于路径的锁（StripedLock）<br>• 先写临时文件再原子移动（atomic rename）<br>• 跨进程使用文件锁（FileChannel.tryLock），失败降级为带 UUID 的 JSONL 追加 |

## 10. 依赖与约束

### 10.1 依赖
- Spring Boot 3.5.5+
- Java 21+
- 现有M0代码基础
- Caffeine 3.1.x（由 Spring Boot BOM 管理；如独立使用请固定 3.1.x 版本）

### 10.2 约束
- 不引入重量级依赖（如JaVers）
- 保持与M0的API兼容性
- 新功能默认关闭

## 11. 验收标准汇总

✅ **功能验收**
- [ ] 嵌套对象扁平化正常工作
- [ ] 集合/Map摘要准确
- [ ] 自定义格式生效
- [ ] 比较规则可配置
- [ ] 查询和导出可用

✅ **性能验收**
- [ ] 2 字段 track P95 ≤ 200μs（perf profile，固定环境，含 warm‑up）
- [ ] CPU 增量 ≤ 5%
- [ ] 内存可控无趋势性增长（护栏/降级计数可观察）

✅ **质量验收**
- [ ] 单元测试覆盖 > 85%
- [ ] 集成测试全部通过
- [ ] 文档完整可用

✅ **交付验收**
- [ ] 代码合并主干
- [ ] 发布说明完整
- [ ] Demo可运行

## 12. 附录

### 12.1 术语表
- **扁平化(Flattening)**：将嵌套对象转换为点分路径
- **摘要(Summary)**：集合变化的统计信息
- **护栏(Guard Rail)**：防止资源过度使用的限制机制
- **降级(Degradation)**：超限时的简化处理策略

### 12.2 参考资料
- JaVers官方文档
- Spring Boot配置指南
- 性能测试基准报告

### 12.3 相关文档
- M0验收报告
- M2规划草案
- 技术设计文档

---
*本文档为M1阶段的正式PRD，后续修改需经过评审流程*

### 12.4 示例与排错（新增）
- Starter 示例工程：演示“嵌套 + 集合摘要 + 模板 + 比较 + Query/Export”的最小配置与扩展 Bean 注入；
- 官方配置模板与常见问题排查：
  - 路径优先级冲突（byPath vs byType）、Pattern 未命中时默认行为、模板占位非法/为空处理；
  - 导出路径不可写/权限不足/空间不足的回退策略。

### 12.5 Out-of-Scope（新增）
- 深度容器逐项 Diff（如 List 移动识别/Levenshtein）、持久化仓库（SQL/NoSQL）与回放/可视化、查询语言均不在 M1 范围，留待 M2 或后续版本；
- 若需要复杂 Diff 引擎，建议在 M2 评估可插拔适配方案（如 javers-adapter）。

### 12.6 验收与测试建议（新增）
- 循环引用：覆盖自引用与 A→B→A；验证无栈溢出、兄弟分支继续展开、首个环路路径 DEBUG 可见；`policy=marker` 时节点输出占位且不再展开。
- 集合降级：当 size ≤ max-size 时输出增删计数与（启用时）示例项；当任一侧 size > max-size 且开启 degrade 时，仅输出 `Collection size changed from X to Y`。
- 示例项互斥：`summary-only=true` 时即使 `examples.enabled=true` 也不输出示例项（验证优先级约束）。
- valueRepr 与排序：包含带 id 与不带 id 的 Bean；验证在 `sort-mode=STRING/NUMERIC/NATURAL` 下排序稳定；长度截断与转义生效。
- Context 感知：不同 `zoneId` 下同一瞬时时间被判等；`attributes.precision` 能切换数值容差策略；字符串仅验证 trim/小写规范化可控。
- valueKind：覆盖 SCALAR/ENUM/COLLECTION/ARRAY/MAP/NESTED/NULL/MISSING/CYCLE/BINARY 的判定与模板渲染路径。
- 生产稳定性：`valueRepr.require-identity-paths-in-prod=true` 时，未配置的关键类型触发 WARN（或按 fail-on-missing 行为）；
- Profile：`tfi.profile` 选择 minimal/balanced/full 生效；验证“显式配置 > profile > 默认值”的优先级与 Deep Merge 语义（仅覆盖指定路径，其余保留）。
- Profile 生命周期：运行时不支持热切换；切换需重启；缓存与 Store 随 Profile 清空并重建（不做迁移）。
- 性能/Profile：性能用例需在 balanced 与 full 下分别验证（full 允许 +20% 放宽）；CI 仅对 balanced 做基线校验，full 建议线下/基准环境验证。
- 导出元数据：导出文件必须包含 profile、tfiVersion、timestamp、host、instanceId/pid；导入端验证为 M2 预留能力（M1 不实现）。

### 12.7 错误码表（新增）
• 1xxx 配置错误（Configuration Errors）
- TFI-1001 MISSING_IDENTITY_PATHS：关键类型缺少 `valueRepr.identity-paths`，可配合 `fail-on-missing=true` 触发启动失败。
- TFI-1002 INVALID_PROFILE_NAME：`tfi.profile` 值非法；错误信息包含可选集合。
- TFI-1003 PROFILE_DEEP_MERGE_CONFLICT：深度合并时出现类型冲突（如 List 覆盖 Map）。
- TFI-1004 PATTERN_COMPILE_ERROR：路径匹配 Pattern 编译失败；包含原始 pattern 字符串。
- TFI-1005 TEMPLATE_PLACEHOLDER_INVALID：模板占位符非法或未知；包含占位符名称。
- TFI-1006 CONFIG_SCHEMA_VALIDATION_FAILED：配置不符合 Schema；包含路径与错误详情。

• 2xxx 运行时错误（Runtime Errors）
- TFI-2001 DIFF_RUNTIME_FAILURE：对比过程发生未捕获异常（包含栈与路径）。

• 3xxx 资源错误（Resource Errors）
- TFI-3001 EXPORT_FILE_LOCK_FAILED：文件锁获取失败；包含路径与实例信息。
- TFI-3002 EXPORT_ATOMIC_MOVE_FAILED：临时文件原子移动失败；包含源/目标路径。

• 4xxx 业务错误（Business Errors）
- 预留：例如 IMPORT_PROFILE_MISMATCH（M2）。

### 12.8 监控指标（新增）
- tfi.diff.count：产生的变更总数
- tfi.diff.nested.cycle.skip.count：循环 cut/marker 次数
- tfi.diff.nested.depth.limit.count：深度超限次数
- tfi.collection.summary.degrade.count：集合降级（size-only）次数
- tfi.collection.examples.output.count：示例项输出次数
- tfi.format.template.miss.count：模板未命中回退次数
- tfi.compare.tolerance.strict.applied.count：strict 容差调整应用次数
- tfi.store.put.count：Store 写次数
- tfi.store.get.count：Store 读次数
- tfi.store.eviction.count：Store 淘汰次数
- tfi.export.success.count：导出成功次数
- tfi.export.failure.count：导出失败次数
- tfi.perf.diff.timer：Diff 耗时直方图（可导出 p95/p99）

### 12.9 API 使用示例（新增）
```java
// 1) 基本使用（M0/M1 通用）
Order before = deepCopy(order);
// ... mutate order ...
List<Change> changes = TFI.getChanges(order); // 或 TFI.diff(before, order)

// 2) 传入 DiffContext（M1 时间与容差）
DiffContext ctx = DiffContext.builder()
    .zoneId(ZoneId.of("UTC"))
    .attribute("precision", "strict")
    .build();
List<Change> ctxChanges = TFI.diff(before, order, ctx);

// 3) Spring Boot 集成（application.yml 配置后）
@Autowired TaskFlowInsight tfi;
List<Change> bootChanges = tfi.diff(before, order);
```

实现说明：`DiffContext` 采用 Lombok `@Builder` 与 `@Value`/`@Getter`，统一使用 `DiffContext.builder()` 创建，便于扩展与二进制兼容。

### 12.10 配置校验与 Schema（新增）
- 提供 JSON Schema（草案 draft-07）用于 IDE/CI 校验：`docs/schemas/tfi-change-tracking.schema.json`（文档位置）与 `src/main/resources/META-INF/schemas/tfi-change-tracking.schema.json`（运行时位置，随 Jar 打包）；
- 校验范围：`tfi.change-tracking.*`、`tfi.profile`、`tfi.profiles.*`；
- 护栏校验：`max-depth/max-size` 上限、`sort-mode` 枚举、`strict-factor` 合法区间 [0.0, 1.0]；
- 集成建议：CI 在 balanced Profile 下进行 Schema 校验与最小运行验证；full Profile 在基准环境做扩展验证。
