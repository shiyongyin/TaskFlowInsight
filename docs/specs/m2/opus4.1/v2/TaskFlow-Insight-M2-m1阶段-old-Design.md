# TaskFlow Insight — M2‑m1 设计说明（Design）

## 文档信息
| 属性 | 值 |
|------|-----|
| Version | v2.0.0‑M2‑m1 |
| Owner | 架构/研发 |
| Status | Draft‑for‑Review |
| Date | 2025‑01‑11 |

---

## 1. 概述

本设计说明基于《TaskFlow‑Insight‑M2‑m1阶段‑PRD.md》的需求，结合当前代码实现（M0 基线与部分 M1 能力），给出 M2‑m1 阶段落地方案。目标是在保持 M0 兼容与性能护栏的前提下，完成 PRD 的 P0 能力并提供 P1 的可选增强。

范围（M2‑m1）：
- P0：嵌套对象扁平化（含循环处理与深度限制）、集合/Map 摘要（含降级与示例项排序）、自定义消息格式（模板）、自定义比较规则（简化 Context）、护栏与监控、Spring 集成基础与完善（Profile/Deep‑Merge）。
- P1：内存 Store+Query、文件导出（JSON/JSONL，元数据字段）。

非目标（留待 M2‑m2 或后续）：逐项深度集合 Diff、导入校验、Locale/重音折叠比较、复杂 Comparator/Repository。

---

## 2. 系统架构

逻辑分层（与 PRD 6.1 一致）：

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

关键模块：
- Tracking Core：对象快照/对比/结果写入（现有 `ChangeTracker`、`DiffDetector`、`ObjectSnapshot`）。
- Format Engine：模板化格式输出（新增轻量模板引擎与选择器）。
- Compare Strategy：数值/时间/字符串的规范化与容差（简化 Context）。
- Storage & Query：可选的内存存储与查询（Caffeine），文件导出。
- Spring Integration：AutoConfiguration、@ConfigurationProperties、Profiles（Deep‑Merge）、监控与护栏。

---

## 3. 模块设计

### 3.1 Tracking Core（变更追踪内核）

现状：
- `ChangeTracker`：线程级快照管理（ThreadLocal），对比与刷新；已在 `TFI.stop()` 等处对接。
- `DiffDetector`：按字段字典序对比，当前支持标量与 Date 归一化；不含嵌套/集合摘要。
- `ObjectSnapshot`：仅采集标量字段、简易反射缓存（上限 1024 类），字符串转义与截断（默认 8192）。

设计变更：
- 类关系与职责：
  - 采用组合而非继承：新增 `ObjectSnapshotDeep` 与现有 `ObjectSnapshot` 并列，`ChangeTracker`/`DiffDetector` 通过组合调用两者；
  - `ObjectSnapshot` 继续负责“标量浅快照”；`ObjectSnapshotDeep` 负责“嵌套展平 + 集合摘要”；避免静态继承污染与二义性。
- 嵌套扁平化：提供 `ObjectSnapshotDeep.captureDeep(String rootName, Object target, DeepConfig cfg) : Map<String,Object>`，输出点分路径 `a.b.c`；
  - DFS 实现：
    - 入口：从 `target` 入栈（path=""），节点进入前先做“白/黑名单”预判（Ant 匹配，命中黑名单则剪枝，命中白名单才继续展开；若白名单为空，默认允许）；
    - 类型分派：
      - 标量/枚举/日期：终止展开，放入 `path`→值；
      - Bean（getter/字段可反射）：对子属性入栈；
      - 数组/集合/Map：不展开为索引路径，委托 `CollectionSummary` 生成摘要对象，写入 `path`→摘要（见 3.2）；
      - 二进制（byte[]/ByteBuffer）：写入长度/可选短哈希摘要；不直接支持 InputStream（单次消费流），如需支持需调用方先读取为 byte[] 或自定义提取器计算摘要。
    - 子属性遍历顺序（确定性保证）：
      - Bean 子属性默认基于 `getDeclaredFields()`（含父类，最多 2 层）收集；按“字段名字典序（NAME_ASC）”排序后遍历，确保跨 JVM 一致；
      - 当某些字段不可访问且需回退到 getter 时，同样按方法名（去除 `get/is` 前缀）字典序排序；
      - 可配置：`tfi.nested.bean.field-order: NAME_ASC|DECLARED`（默认 NAME_ASC）。
    - 深度限制：遇到 `depth > cfg.maxDepth` 立即剪枝并累计 `nested.depth.limitCount`；
    - 循环检测（路径栈法）：使用 `Set<Object> currentPath = Collections.newSetFromMap(new IdentityHashMap<>())`；
      - 进入节点：`if (!currentPath.add(node))` → 触发循环策略（cut/marker/error）；
      - 退出节点：`currentPath.remove(node)`（放在 finally 中确保异常也清理）。
    - 局部提交（明确实现）：
      - 单线程 DFS：为每个节点维护局部 `Map<String,Object> localBuf`，节点退出且未异常时一次性 `result.putAll(localBuf)`；
      - 嵌套结构：使用 `Deque<Map<String,Object>> bufStack` 对齐 DFS 入栈/出栈；兄弟分支互不影响；
      - 异常清理：`try { ... } finally { bufStack.pop(); currentPath.remove(node); }` 确保异常路径不合并；
      - 并发说明：
        - 默认并行度：`tfi.nested.parallelism: 2`（0=关闭）；可自适应：`min(availableProcessors/2, configured)`；
        - 池：使用独立 `ForkJoinPool`（命名 `TFI-Deep-DFS`），不使用全局 Common 池，避免抢占业务线程；
        - 任务分片：按“子节点分片”fork/join，每个子分支写入独立子缓冲区；在父节点处“按子属性字典序”合并，保持确定性；
        - 粒度控制：`tfi.nested.parallel.leaf-threshold: 8`（子节点数小于阈值则串行处理，减少调度开销）；
        - 均衡性：依赖 Fork/Join 的 work‑stealing；对超大分支可二次分裂；
        - 可配置确定性：`tfi.nested.deterministic-merge: true|false`（默认 true；false 时按完成先后合并，吞吐更高但顺序不稳定）。
  - 白/黑名单匹配时机：在“节点进入”时执行（遍历中）；对于未进入的分支不做反射与子节点入栈，降低开销；匹配器使用 `PathMatcherCache`（LRU）。
    - PathMatcherCache 大小：默认最大条目 `1000`，可配置 `tfi.change-tracking.path-matcher-cache.max-size: 1000`；达到上限时 LRU 淘汰；统计命中/编译失败并暴露指标，避免 Pattern 无限增长导致 OOM。
    - Pattern 编译失败降级策略：默认回退为“字面量相等匹配（literal）”，即按原始字符串执行精确匹配；可配置策略 `tfi.change-tracking.path-matcher.compile-failure-policy: literal|skip`（skip=忽略该规则，视为不匹配）。
    - ReDoS 防护：
      - 语法限制：使用 Ant 风格通配（`*`, `**`, `?`）的有限状态匹配器，避免复杂正则；
      - 上限控制：`tfi.change-tracking.path-matcher.max-pattern-length: 512`，`max-wildcards-per-pattern: 32` 超限拒绝；
      - 白名单预编译：`tfi.change-tracking.path-matcher.preload: ["order/**","user/*/name"]`；启动时预编译热点，降低冷启动抖动。
  - Map/集合：在 DFS 中不再展开子元素，统一交由 `CollectionSummary` 生成摘要（保证稳定性与性能一致性）。
- Diff 扩展：`DiffDetector` 对 before/after 的“展平快照”（标量 + 扁平嵌套 + 集合摘要字段）进行字典序对比，补充 `valueKind` 与 `valueRepr`；
  - `valueKind`：SCALAR/ENUM/NULL/MISSING/COLLECTION/ARRAY/MAP/NESTED/CYCLE/BINARY；
  - `valueRepr`：用于展示/排序的稳定字符串，遵循配置的 identity‑paths 和 ISO‑8601 时间统一；
  - 简化 Context：时间按 `zoneId` 统一；字符串仅支持 trim/lowercase；数值容差受 `precision` 和 `strict-factor` 影响（见 3.3）。

统一入口：
- `SnapshotFacade.capture(rootName, target, cfg)`：根据配置（`nested.enabled` 与 `maxDepth>0`）选择 `ObjectSnapshot` 或 `ObjectSnapshotDeep`；对上层保持单一 API。

核心类/方法（新增/修改）：
- `ObjectSnapshotDeep`（新）：封装 DFS 展开、循环检测、白/黑名单、depth 计数与统计；
- `CollectionSummary`（新）：提供集合/Map 摘要、降级、示例项提取与排序；
- `ValueReprUtil`（新）：统一生成 valueRepr，支持 identity‑paths 与长度上限；
- `PathMatcherCache`（新）：LRU 缓存 pattern 字符串 → Pattern；
- `DiffDetector`（改）：接收“展平后快照”，构造 `ChangeRecord` 并填充 `valueKind/valueRepr`；
- `ChangeTracker`（保留）：接口不变；内部依旧以“展平后字段集”作为对比输入。

### 3.2 集合/Map 摘要

目标：
- 列表/集合：报告新增/删除数量，必要时示例项（Top‑N）；不做逐项深度 Diff；
- Map：报告 key 增删数量；示例仅展示 key；
- 降级：当任一侧 size > `collection.max-size` 或触发护栏，摘要降级为 size‑only。

算法：
- 稳定键（stableKey）生成：统一使用 `ValueReprUtil.stableKeyOf(value)`，避免直接 `toString()` 不稳定；
  - 标量/枚举：规范化字符串；数字：`BigDecimal` 统一（去除尾随 0）再转字符串；日期：ISO‑8601（按 `zoneId`）；
  - Bean：优先 identity‑paths（如 id/code/name），否则回退 `ClassSimpleName`；
  - 注意：stableKey 用于摘要与排序，不改变业务对比语义；
  - 回退冲突规避：当缺少 identity‑paths 时，不直接使用 `ClassSimpleName`，而是生成 `ClassSimpleName#h{fnv1a32}` 的结构化稳定哈希键（见下文“ValueRepr 与 stableKey 细节”）。
- List/Set：计算 beforeKeys/afterKeys 为 `Set<String>`；新增=`afterKeys - beforeKeys`，删除=`beforeKeys - afterKeys`；
- Map：比较 `before.keySet()` 与 `after.keySet()`（非标量 key 先转 stableKey）；
- 示例项：从新增/删除中各取 `examples.topN`；排序：`sort-mode=STRING|NUMERIC|NATURAL`（默认 STRING，NATURAL 启动 WARN）。
- 降级：任一侧 size > `collection.max-size` 或触发护栏 → `mode=size-only`。

类与职责：
- `CollectionSummary`：
  - `summarize(before, after, config) -> Summary`（含 counts、examples、degradeFlag）；
  - 统一排序器 `ExampleSorter`（支持三种 sort‑mode）。

注：NATURAL 排序需要将元素分解为“数字/非数字”片段并进行多轮比较，CPU 与内存开销显著高于 STRING；仅在展示稳定性有强需求时使用，默认 STRING；启用时在启动输出 WARN。

ValueRepr 与 stableKey 细节：
- `ValueReprUtil.valueReprOf(v)`：标量/枚举→字符串；日期→ISO‑8601（按 `zoneId`）；Bean→identity‑paths 否则 `ClassSimpleName`；统一转义与截断（默认 120，可配）。
- `ValueReprUtil.stableKeyOf(v)`（摘要/排序用）：优先 identity‑paths；若缺失则生成“结构化稳定哈希”`ClassSimpleName#h{fnv1a32}`：提取 Bean 可读标量字段（字段名排序）→ 规范串 `name=value;` → FNV‑1a 32 位哈希（实现内置）→ hex 串；BINARY 用 `Binary#len:{N}` 或附加短哈希。

FNV‑1a 32 位（参考实现）：
```java
static int fnv1a32(byte[] bytes) {
  int hash = 0x811C9DC5; // offset basis
  for (byte b : bytes) {
    hash ^= (b & 0xFF);
    hash *= 0x01000193; // FNV prime
  }
  return hash; // as unsigned 32-bit for hex
}
```
说明：非加密哈希，存在碰撞但概率低；仅用于摘要/排序键，不参与业务判等。

### 3.3 Compare Strategy（比较策略）

简化 Context（M2‑m1）：
- `DiffContext`：仅支持 `zoneId` 与 `attributes.precision`；
- `precision=strict`：有效容差 = 配置容差 × `compare.tolerance.strict-factor`（默认 0.01），0.0 表示零容差；
- 字符串：可选 trim+lowercase；不使用 Collator/ICU4J。

实现要点与接口：
- `DiffContext`（Lombok `@Builder`）：
  ```java
  @lombok.Value @lombok.Builder
  public class DiffContext {
    java.time.ZoneId zoneId; // 必填，默认 UTC
    java.util.Map<String,Object> attributes; // precision=normal|strict 等
  }
  ```
  - `CompareService`（新）：
  ```java
  public interface CompareService {
    Object normalize(Object v, DiffContext ctx);
    java.math.BigDecimal normalizeNumber(Number n);
    java.time.Instant normalizeTemporal(Object temporal, DiffContext ctx);
    String normalizeString(String s, DiffContext ctx);

    boolean equalsWithTolerance(Object a, Object b, DiffContext ctx);
    default boolean equalsNumber(Number a, Number b, java.math.BigDecimal tolerance) { /* impl */ }

    java.math.BigDecimal effectiveTolerance(java.math.BigDecimal configured, DiffContext ctx);
  }
  ```
规则解析与集成：
- `CompareRule`：numberTolerance(BigDecimal)、timeRounding(ChronoUnit)、stringNormalize(booleans)。
- `RuleRepository`：`CompareRule resolve(String path, Class<?> type)`；
  - 解析优先级：byPath > byBeanName > byType > default；
  - Bean 名来源：Spring 环境下通过 `ApplicationContext` 解析注入点/目标对象的 beanName（无法解析时跳过该层）；
  - 复杂度控制：默认仅启用 byPath 与 byType 两层，byBeanName 层默认关闭（`tfi.compare.rule.enable-bean-name-layer: false`）以降低运行时开销；
  - 缓存策略：Caffeine 缓存解析结果与 Pattern 编译（`maximumSize: 2000`，`expireAfterAccess: 10m`；可通过 `tfi.compare.rule.cache.maximum-size`、`tfi.compare.rule.cache.expire-after-access` 配置）；
  - 热更新：监听 `EnvironmentChangeEvent`/配置中心刷新时清空缓存；不保证强一致，刷新后新解析命中新配置；
  - 诊断：提供端点 `GET /actuator/tfi/compare/rule` 返回命中层级、来源与缓存命中信息。
- `CompareService` 使用示例：
  ```java
  CompareRule rule = ruleRepo.resolve(fieldPath, valueType);
  BigDecimal tol = effectiveTolerance(rule.getNumberTolerance(), ctx); // ctx.attributes.precision 影响
  boolean eq = equalsWithTolerance(oldV, newV, ctx);
  ```
- `DiffDetector`：在取出 before/after 值后调用 `CompareService.equalsWithTolerance` 判断相等；不相等才产生 `ChangeRecord`；`valueRepr` 由 `ValueReprUtil` 生成。

### 3.4 Format Engine（模板与 Formatter）

模板语法：统一使用 `#[...]` 占位（#[object]/#[field]/#[old]/#[new]/#[valueKind]/#[time]/#[type]）。

组件：
- `TemplateEngine`（新）：
  - 仅占位替换，不含脚本；
  - 模板选择：按路径优先级（byPath > byBeanName > byType > default）。
- `FormatService`（新）：
  - `formatChange(ChangeRecord, Template)` 生成最终消息；
  - 统计 `tfi.format.template.miss.count`。

### 3.5 Storage & Query（P1，可选）

设计：
- 接口 `ChangeStore`：`put(ChangeRecord)`、`query(filter)`、`evict(policy)`；
- 实现 `InMemoryChangeStore`：Caffeine 支持 maximumSize+expireAfterWrite（支持 `caffeine-spec` 或编程构建）；
- 查询维度：时间窗口、sessionId、objectName、fieldPath、changeType；
- 指标：put/get/eviction 计数，耗时直方图（可选）。

### 3.6 Export（P1，可选）

目标：
- JSON/JSONL 文件导出：包含元数据 `profile/tfiVersion/timestamp/host/instanceId(pid)`；
- 并发安全：StripedLock（进程内）、临时文件写入后原子重命名、跨进程 FileLock 失败则退化为 UUID JSONL 追加；
- 导入校验：M2 预留，不在 M2‑m1 实现。

类：
- `FileExporter`（新）：`export(Session|List<ChangeRecord>, ExportOptions)`。
并发控制细节：
- StripedLock：条带数可配，默认 `max(64, availableProcessors*4)`（建议上限 4096）；根据 `abs(hash(path)) % stripes` 选带；
  - 配置：`tfi.export.lock.stripes: 256`
- 单路径单锁：写入顺序为“获取带锁 → 写临时文件 → 原子移动 → 释放锁”；
- 文件锁：可选 `FileChannel.tryLock()`；失败降级为追加写入 UUID JSONL；
- 死锁/活锁预防：
  - 强制单锁：使用 `ThreadLocal<Set<Integer>> heldStripes` 跟踪当前线程持有的条带，检测到重入获取时直接回退（追加模式）或抛出受检异常并 WARN；
  - 有界重试：`tryLock` 采用指数退避 + 抖动（如 10ms → 50ms → 200ms，随机 ±20%），最大重试 3 次；超过后回退为追加模式并打点（`tfi.export.retry.fallback.count`）。
- 碎片治理：提供滚动归档与可选压缩；文档化清理脚本（运维侧）。
- 同卷原子移动：临时文件创建在目标目录中，确保 `Files.move(ATOMIC_MOVE)` 在同一文件系统内；跨卷场景回退为 `copy+fsync+rename` 并 WARN。

Fork/Join 嵌套调用风险与防护：
- 风险：在 DFS 任务中访问目标对象的 getter 若内部再次触发 TFI（如 TFI.track），可能导致 F/J 池饥饿或死锁；
- 防护：
  - 禁止嵌套（默认）：进入 DFS 时设置线程本地“reentry guard”，在 DFS 内部调用 TFI 直接抛出受检异常并记录 `tfi.nested.call.block.count`；
  - 受控阻塞：如确需访问阻塞型资源，使用 `ForkJoinPool.ManagedBlocker` 包裹；
  - 文档约束：对外明确 getter 中不要进行跟踪或重计算逻辑。

### 3.7 Spring Integration（集成与配置）

现状：
- `ContextMonitoringAutoConfiguration` 已注册 `ChangeTrackingProperties` 并应用到 `TFI` 与 `ObjectSnapshot`。

扩展：
- 新增 `ChangeTrackingProperties` 嵌套配置：
  - `nested`（maxDepth/whitelist/blacklist/cycle.policy/marker），
  - `collection`（maxSize/summaryOnly/examples.topN|sortMode|maxLen|lineMaxLen/degrade.enabled），
  - `format`（template/templates），
  - `compare`（rules/context.zoneId/attributes.precision、tolerance.strictFactor），
  - `valueRepr`（identityPaths/maxLen/require‑identity‑paths‑in‑prod/fail‑on‑missing/key‑type‑annotations/key‑type‑packages），
  - `store`（enabled、caffeine‑spec、maximum‑size、expire‑after‑write），
  - `export`（enabled、path、rollingPolicy、fileLock 等）。
- 预设 Profile（minimal/balanced/full）与 Deep‑Merge：
  - Map 深度合并、List 替换、null 视为未设置；
  - 优先级：显式配置 > 自定义 profiles > 内置 profiles > 默认；
  - 生命周期：仅启动解析；不支持热切换；切换需重启；缓存/Store 清空并重建；
  - 护栏为硬上限（统一口径）：任何来源（显式/自定义/内置 profile）的值，最终都会被护栏夹取到允许范围内。
- Deep‑Merge 实现（`ProfileMerger`）：
  ```java
  enum TypeConflictPolicy { REPLACE, ERROR }

  Map<String,Object> deepMerge(Map<String,Object> base, Map<String,Object> overlay, TypeConflictPolicy policy) {
    for (var e : overlay.entrySet()) {
      var k = e.getKey(); var v = e.getValue();
      if (v == null) continue; // null 视为未设置
      Object bv = base.get(k);
      if (v instanceof Map && bv instanceof Map) {
        base.put(k, deepMerge((Map) bv, (Map) v, policy));
      } else if (v instanceof Map && !(bv instanceof Map)) {
        if (policy == TypeConflictPolicy.ERROR) throw new IllegalArgumentException("Deep-merge type conflict for key=" + k);
        base.put(k, v); // REPLACE（默认）
      } else if (!(v instanceof Map) && bv instanceof Map) {
        if (policy == TypeConflictPolicy.ERROR) throw new IllegalArgumentException("Deep-merge type conflict for key=" + k);
        base.put(k, v); // REPLACE（默认）
      } else {
        base.put(k, v); // List/标量替换
      }
    }
    return base;
  }
  ```
- 护栏优先级实现：合并完成后，`GuardRailsNormalizer` 对 `max-depth/max-size/strict-factor` 等进行夹取（clamp）与 WARN 记录；运行时由各组件再次校验。有效阈值= min(合并结果, 护栏上限)。

### 3.9 Guard Rails（护栏）实现与切入点

配置期校验（`GuardRailsValidator`）：
- 触发：`AutoConfiguration` 的 `@PostConstruct` 中；
- 行为：
  - 值域检查（如 strict‑factor ∈ [0,1]、max‑depth 合理区间）；
  - fail‑on‑missing：扫描关键类型（@Entity/@Document/扩展注解/包前缀/SPI），必要时抛出 `TfiConfigException(TFI‑1001)`；
  - NATURAL 排序：若启用，输出一次 WARN。

运行期强制（`GuardRailsEnforcer`）：
- 集合摘要：若 `size > max-size` → 强制 `mode=size-only` 并递增 `tfi.collection.summary.degrade.count`；
- 嵌套展开：若 `depth > max-depth` 或命中循环策略→ 剪枝并累加计数；
- 模板缺失：回退到默认模板并递增 `tfi.format.template.miss.count`。

切入点（保证覆盖）：
- `ObjectSnapshotDeep.captureDeep(..)`：深度/循环/名单护栏；
- `CollectionSummary.summarize(..)`：size 超限降级护栏；
- `FormatService.formatChange(..)`：模板 miss 护栏；
- `ChangeStore.put/query`（P1）：容量/TTL 护栏；
- 导出 `FileExporter.export(..)`：路径安全/锁获取护栏。

通知机制：
- Micrometer 指标计数；可选聚合 WARN（周期性汇总日志）；暴露 `DegradeNotificationListener` SPI，用于在降级时回调（默认空实现）。
- 校验：
  - `require‑identity‑paths‑in‑prod` 与 `fail‑on‑missing`（仅 @Entity/@Document/扩展注解/包前缀/SPI 判定的关键类型）；
  - NATURAL sort 选择时启动 WARN（logger: `tfi.collection.examples`）。

### 3.8 监控与日志

日志：统一 SLF4J；错误码参见 8.3。关键 WARN：
- NATURAL 排序启用；
- Field cache 上限触发；
- Profile/Schema 校验警告；
- Collection degrade 触发汇总（可选）。

Metrics（Micrometer，点分隔命名）：
- `tfi.diff.count`
- `tfi.diff.nested.cycle.skip.count`
- `tfi.diff.nested.depth.limit.count`
- `tfi.collection.summary.degrade.count`
- `tfi.collection.examples.output.count`
- `tfi.format.template.miss.count`
- `tfi.pathmatcher.cache.size` / `tfi.pathmatcher.cache.hit.count` / `tfi.pathmatcher.cache.miss.count` / `tfi.pathmatcher.cache.eviction.count`
- `tfi.pathmatcher.compile.failure.count`
- `tfi.compare.rule.cache.size` / `tfi.compare.rule.cache.hit.count` / `tfi.compare.rule.cache.miss.count` / `tfi.compare.rule.cache.eviction.count`
- `tfi.compare.tolerance.strict.applied.count`
- `tfi.store.put.count` / `tfi.store.get.count` / `tfi.store.eviction.count`
- `tfi.export.success.count` / `tfi.export.failure.count`
- `tfi.perf.diff.timer`
埋点位置：
- `ChangeTracker.getChanges()`：`tfi.diff.count`、`tfi.perf.diff.timer`；
- `ObjectSnapshotDeep`：`tfi.diff.nested.cycle.skip.count`、`tfi.diff.nested.depth.limit.count`；
- `CollectionSummary`：`tfi.collection.summary.degrade.count`、`tfi.collection.examples.output.count`；
- `FormatService`：`tfi.format.template.miss.count`；
- `CompareService`：`tfi.compare.tolerance.strict.applied.count`；
- `PathMatcherCache`：`tfi.pathmatcher.cache.*`；
- `RuleRepository`：`tfi.compare.rule.cache.*`；
- `InMemoryChangeStore`：`tfi.store.put.count/get.count/eviction.count`；
- `FileExporter`：`tfi.export.success.count/failure.count`。

---

## 4. 接口设计

### 4.1 Java API（Facade & Services）

保留现有 Facade：`com.syy.taskflowinsight.api.TFI`
- 新增便捷 Diff：
  - `List<ChangeRecord> diff(Object before, Object after)`（内部构造临时名称并走展平与对比）；
  - `List<ChangeRecord> diff(Object before, Object after, DiffContext ctx)`；
- 保持 `withTracked/track/trackAll/getChanges/clearAllTracking` 兼容。

内部服务（Spring Bean）：
- `ChangeTrackingService`：封装 track/diff/flush；
- `CompareService`：归一化与容差判断；
- `FormatService`：模板格式化；
- `CollectionSummary`、`ValueReprUtil`、`PathMatcherCache` 作为组件或工具类注入；
- `ChangeStore`（可选）：InMemory 实现；
- `FileExporter`（可选）。

### 4.2 配置结构（YAML）

与 PRD 6.3 一致（此处不赘述），新增 `store` 与 `export` 示例；严格遵循 Deep‑Merge 与护栏规则。

### 4.3 Actuator/Endpoint（可选）

- 扩展现有 `TaskflowContextEndpoint`：增加只读统计与健康检查（Profile/Schema/护栏状态）。
- Store 查询端点（P1 可选）：分页拉取最近 N 条变更（受限）。
 - 有效配置端点（新建议）：
   - `GET /actuator/tfi/effective-config?path=order.customer.address`
   - 返回该路径的最终生效配置（合并后的 compare/format/valueRepr/collection/nested）、模板/规则来源（byPath/byType/default）、以及触发的护栏说明；
   - 目的：降低“配置不生效”问题的排查成本。
 - Profile 合并预览端点（新建议）：
  - `GET /actuator/tfi/profile/merged`
  - 返回生效 Profile 名称、合并顺序与 Deep‑Merge 结果摘要（关键阈值与来源）。
  - 规则解析预览端点（新建议）：
    - `GET /actuator/tfi/compare/rule?path=order.amount&type=java.math.BigDecimal`
    - 返回该路径与类型的解析详情（命中的 byPath/byBeanName/byType/default，缓存命中信息）。
  - 缓存状态端点（新建议）：
    - `GET /actuator/tfi/path-matcher/cache`：返回 PathMatcherCache 命中率/大小/淘汰数（摘要）。
    - `GET /actuator/tfi/compare/cache`：返回规则解析缓存命中率/大小/过期设置（摘要）。
  - 预热端点（新建议）：
    - `POST /actuator/tfi/warmup`：预编译/预解析配置中的热点模式与规则（path‑matcher.preload、compare.rule.preload），返回耗时与命中条目数。

---

## 5. 数据模型

核心对象：
- `Session` / `TaskNode` / `Message`（已实现）；
- `ChangeRecord`：新增字段已覆盖（valueKind/valueRepr），用于 Store 与导出；新增建议字段：`Map<String,Object> context`，用于持久化 `DiffContext.attributes`（如 tenantId/actorId/precision 等）。实现建议：在 `DiffDetector` 组装记录时，若提供了 `DiffContext`，则对其 `attributes` 做浅拷贝并用不可变包装后写入 `ChangeRecord.context`（可配置白名单与大小上限）。
  - `Summary`（内部）：集合摘要结果（新增/删除计数、示例项、降级标记）。

上下文字段控制：
- 白名单：`tfi.change-tracking.record.context.whitelist: ["tenantId","actorId","precision"]`
- 大小上限：`tfi.change-tracking.record.context.max-bytes: 4096`（序列化为 JSON 字符串估算大小，超限则截断并记录 WARN）
- 序列化：导出时将 `context` 中的值转为字符串（统一转义+截断），禁止对象图递归，避免循环引用与敏感信息外泄。
  - 现状：项目默认使用内置 JSON 导出器（非 Jackson）。
  - 如通过 REST/Actuator 使用 Jackson 暴露，建议：
    - 对 `oldValue/newValue` 使用 `@JsonIgnore` 或 MixIn，仅暴露 `valueRepr`；
    - 或注册自定义序列化器，将复杂对象统一序列化为受控字符串并实施长度上限；
    - 避免循环：禁用自引用失败并避免双向关系：`objectMapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false)`；
    - 示例：
      ```java
      @JsonIgnore public Object getOldValue();
      @JsonIgnore public Object getNewValue();
      ```

索引建议（Store 维度）：
- key：`sessionId + timestamp`；二级过滤条件 `objectName/fieldPath/changeType`；
- 便于按会话/时间窗口查询。

---

## 6. 异常处理与日志方案

错误码分类（与 PRD 一致）：
- 1xxx 配置错误：TFI‑1001..1006（缺少 identity‑paths、Profile 名非法、Deep‑Merge 冲突、Pattern 编译失败、模板占位符非法、Schema 校验失败）。
- 2xxx 运行时错误：TFI‑2001 DIFF_RUNTIME_FAILURE。
- 3xxx 资源错误：TFI‑3001/3002（导出文件锁/原子移动失败）。
- 4xxx 业务错误：预留（如 Import Profile 不匹配 M2）。

异常与抛出点：
- `TfiConfigException`（extends RuntimeException）：1001/1002/1004/1005/1006；在 `AutoConfiguration` 校验、`TemplateEngine` 解析、`PathMatcherCache` 编译失败时抛出；
- `TfiRuntimeException`：2001；在 `DiffDetector/CompareService` 捕获不可恢复异常时包裹抛出（Facade 层仍兜底记录）；
- `TfiResourceException`：3001/3002；在 `FileExporter` 文件锁/原子移动失败时抛出；
错误信息格式：`"[CODE] message - context={...}"`，上下文含路径/profile/字段名等关键信息。

日志分级：
- ERROR：启动失败/导出失败/内部不可恢复异常；
- WARN：Profile/NATURAL 排序/护栏超限/FieldCache 上限；
- INFO：配置生效、Profile 合并摘要（一次性）；
- DEBUG：变更明细、循环检测路径、Pattern 编译缓存命中率（可选）。

---

## 7. 性能与扩展性

目标（基于 balanced Profile，务实口径）：
- 2 字段基础场景 P95 ≤ 0.5ms；
- 嵌套（深度 2）P95 ≤ 2ms；
- 集合示例（100 项）P95 ≤ 5ms；full 允许 +20% 放宽；
- CPU 增量 ≤ 5%，内存增量 ≤ 50MB。

关键手段：
- 反射元数据缓存（上限 1024 类，可配）；
- Pattern LRU 缓存；
- `IdentityHashMap` O(1) 路径环检测；
- 集合降级 size‑only；
- 模板仅占位替换；
- Store Caffeine maximumSize+TTL。

测试策略：
- 单元测试：`ObjectSnapshotDeepTests`（深度/循环/白黑名单剪枝/异常清理）、`CollectionSummaryTests`（增删计数/示例项/三种排序/降级）、`CompareServiceTests`（时间统一/字符串规范化/strict‑factor 容差）、`TemplateEngineTests`（占位替换/模板未命中回退）。
- 集成测试：
  - `TFIIntegration`：track/withTracked/flush 与消息写入；
  - Store/Export IT：并发写入 + 原子移动/失败回退；
  - Profile 合并有效性：有效配置端点校验（`/actuator/tfi/effective-config`）；
  - 规则解析：规则预览端点命中层级与缓存行为（`/actuator/tfi/compare/rule`）；
  - 护栏执行：集合降级与深度剪枝的计数与日志；
  - PathMatcherCache/规则缓存：命中率与淘汰计数暴露与阈值校验。
- 性能测试：
  - 基线：JMH/微基准，2 字段/深度 2/集合 100 项；CI 仅跑 balanced 概况，full 在基准环境验证（full 目标 +20% 容忍）。
  - Profile 切换（冷启动合并）耗时评估；
  - 护栏降级收益（size-only 与深度剪枝的 P95 对比）；
  - 规则解析缓存与 PathMatcherCache 命中率与编译次数；
  - StripedLock 条带数（64/256/1024）对吞吐的影响；
  - 大量 Pattern 配置下的内存占用与 LRU 淘汰行为；
  - GC 压力测试：构造大对象图与大集合，观察 GC 次数/停顿时间（G1/ZGC）与内存峰值；目标阈值：G1 单次停顿 < 100ms，ZGC 单次停顿 < 10ms（P95 口径）；
  - 稳定性长跑：长时间运行（≥24h）验证堆稳定性（堆增长率 < 1MB/h）、缓存命中率趋势与指标抖动；

---

## 8. 风险与缓解

- Deep‑Merge 复杂度：边界测试 + Schema 校验；null 视为未设置；
- Profile 测试负担：CI 仅 balanced；full 线下验证；
- 导入能力未就绪：标注为 M2 预留，导出包含元数据；
- NATURAL 排序开销：默认 STRING；选择 NATURAL 时启动 WARN；
- 关键类型判定：提供注解/包前缀/SPI 扩展；
 - 文件导出并发：StripedLock + 原子移动 + 文件锁回退策略。
 - 内存泄漏：`IdentityHashMap` 仅在单次 DFS 栈帧内使用；严格 try/finally 出栈；不缓存对象引用；
 - 目标达成风险：若深度 2 目标（500μs）在特定场景仍偏高，优先通过“名单剪枝 + 模板回落 + 集合 size‑only”降载。
 - Pattern 编译 DoS：限制语法与长度、预编译白名单、统计 `tfi.pathmatcher.compile.failure.count` 并在阈值高时告警；
 - 冷启动抖动：提供启动预热（preload）与 `/actuator/tfi/warmup` 端点，降低首次 miss 带来的延迟尖峰；
 - 内存碎片化：避免通用对象池（小对象交由 TLAB/G1/ZGC 处理），仅对重对象引入复用；减少短期分配（如复用 StringBuilder/临时缓冲）；

---

## 9. PRD → 设计落地映射

| PRD ID | 需求要点 | 设计落地 |
|--------|----------|----------|
| M1‑F001 | 嵌套对象扁平化（深度、循环、名单） | `ObjectSnapshotDeep` DFS 展开；`IdentityHashMap` 环检测；Ant 匹配与 LRU 缓存；统计计数与 DEBUG 路径日志 |
| M1‑F002 | 集合/Map 摘要（降级/示例） | `CollectionSummary` set 差集；size‑only 降级；Top‑N 示例与 `sort-mode=STRING|NUMERIC|NATURAL`；NATURAL 启动 WARN |
| M1‑F003 | 自定义消息格式（模板） | `TemplateEngine` + `FormatService`；按路径优先级选择；仅占位替换；模板 miss 指标 |
| M1‑F004 | 自定义比较规则（Context） | `CompareService`；zoneId 时间统一、字符串 trim/lower、数值容差（precision+strict‑factor） |
| M1‑F005 | 护栏与监控 | 配置期 WARN + 运行期强制降级；Micrometer 指标；NATURAL/缓存上限 WARN |
| M1‑F009 | Spring 集成 | AutoConfig + @ConfigurationProperties 嵌套；Profiles（minimal/balanced/full）与 Deep‑Merge；fail‑on‑missing 校验 |
| M1‑F006 | 内存 Store（P1） | `ChangeStore` + `InMemoryChangeStore`（CaffeineSpec/Builder 映射）；基本查询接口 |
| M1‑F007 | 文件导出（P1） | `FileExporter`；JSON/JSONL，元数据（profile/tfiVersion/timestamp/host/instanceId）与并发安全 |

配置复杂度优化（补充）：
- 规则解析默认两层（byPath/byType），byBeanName 层默认关闭并可配置开启，降低运行时分支与匹配成本。

---

## 10. 与现有代码关系（需要修改/新增）

需要修改：
- `DiffDetector`：接入 `CompareService` 与 `ValueReprUtil`，支持 `valueKind/valueRepr`；
- `ObjectSnapshot`：保留原有 API；通过 `SnapshotFacade` 统一选择浅/深快照路径；
- `TFI`：新增 `diff(before, after[, ctx])` 便捷重载；`flushChangesToCurrentTask()` 可委托 `FormatService`；
- `ContextMonitoringAutoConfiguration`：注册新增 Properties 并应用；
 - `ChangeTrackingProperties`：扩展嵌套配置结构（nested/collection/format/compare/valueRepr/store/export）；新增 `profiles/profile` 与 Deep‑Merge 应用；`GuardRailsValidator/Normalizer` 在 AutoConfiguration 中执行。

需要新增：
- `ObjectSnapshotDeep`、`CollectionSummary`、`ValueReprUtil`、`PathMatcherCache`、`CompareService`、`TemplateEngine`、`FormatService`；
- `ChangeStore` 接口与 `InMemoryChangeStore` 实现（P1）；
- `FileExporter`（P1）。

源码映射（TODO 位置指引）：
- `src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshot.java`（保留）
- `src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshotDeep.java`（新增）
- `src/main/java/com/syy/taskflowinsight/tracking/detector/DiffDetector.java`（修改）
- `src/main/java/com/syy/taskflowinsight/tracking/ValueReprUtil.java`（新增）
- `src/main/java/com/syy/taskflowinsight/tracking/compare/CompareService.java`、`RuleRepository.java`（新增）
- `src/main/java/com/syy/taskflowinsight/format/TemplateEngine.java`、`FormatService.java`（新增）
- `src/main/java/com/syy/taskflowinsight/store/InMemoryChangeStore.java`（新增，可选）
- `src/main/java/com/syy/taskflowinsight/exporter/FileExporter.java`（新增，可选）

不变/复用：
- `ChangeTracker`、`Session/TaskNode/Message`、现有导出器（Console/Json/Map）。

---

## 11. 可扩展性（M2‑m2 展望）

- Compare：引入 locale/collation 与 ICU4J（可开关）；
- Collections：提供 `degrade.mode=sample|hash` 等策略；
- Import：导出文件导入校验与回放；
- Repository：持久化存储（SQL/NoSQL）与查询语言；
- Diff 引擎适配：可插拔对接 JaVers 等（adapter）。

---

## 12. 附录

### 12.1 示例：变更记录格式（JSON 片段）
```json
{
  "objectName": "Order",
  "fieldName": "customer.name",
  "oldValue": "Alice",
  "newValue": "Bob",
  "timestamp": 1736563200000,
  "sessionId": "sess-123",
  "taskPath": "PlaceOrder/Pay",
  "changeType": "UPDATE",
  "valueType": "java.lang.String",
  "valueKind": "SCALAR",
  "valueRepr": "Bob"
}
```

### 12.2 示例：文件导出（JSONL 元数据头）
```json
{"tfiVersion":"2.0.0","profile":"balanced","timestamp":1736563200000,"host":"hostA","instanceId":"pid-12345"}
```

### 12.3 依赖版本建议
- Caffeine 3.1.x（与 PRD 一致；若独立管理依赖建议固定在 3.1.x）
- Spring Boot 3.5.x（本仓库 `pom.xml` 已统一管理），Java 21。

### 12.4 实施优先级（建议）
- P0（开发前明确）
  - Fork/Join 池策略与配置（独立池、parallelism、leaf-threshold、deterministic-merge 开关）
  - GC 测试指标（G1 P95<100ms、ZGC P95<10ms）与长跑阈值（≥24h、堆增长<1MB/h）
  - Pattern/规则预编译与 ReDoS 防护（语法限制、长度上限、白名单预编译）
- P1（开发中解决）
  - 确定性保证的实现细节（字段排序、合并顺序）
  - 分片均衡与任务粒度（work‑stealing、leaf-threshold 调优）
  - 规则/路径匹配的复杂度监控与异常治理（compile‑failure 指标与告警）
- P2（优化阶段）
  - 缓存预热与 warmup 端点联动
  - 临时对象分配优化（复用可变缓冲、避免无效装箱）
  - 长期稳定性验证与回归基线构建
