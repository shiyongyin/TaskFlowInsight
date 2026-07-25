# TASK-EXP-04：将 Map 主入口迁移到 Canonical V2

> **定位**：建立唯一 canonical V2 projection owner，并让现有 `MapExporter.export(Session)` 直接发布该契约。
> **状态**：完成（2026-07-11；61 focused / 694 Core / API / 7/7；100/100）
> **审核状态**：审核通过（2026-07-11；1 MUST + 1 SHOULD 已修复，0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-EXP-03`、已接受 `G3`；后续 `TASK-EXP-05`、`TASK-EXP-06`
> **架构来源**：export-snapshot Task 5/7 经 ADR-008 修订后的 Map 主入口与 canonical projection 边界

---

## 一、核心（设计时填）

### 背景

实施前 `MapExporter` 递归读取 mutable `TaskNode`/`Message`，自行构建统计和
`TaskDurationCache`，并发布 V1 key/omission/alias identity。ADR-008 已决定 4.0 的现有 Map/JSON 主入口
直接发布唯一 canonical V2，禁止隐藏 V1 adapter 或并行 `exportV1/exportV2` API。若本卡只在 Map 内临时
构建 V2，EXP-05 的 JSON 必然复制或搬迁 schema；因此 canonical tree owner 必须在本卡先落到 model package。

### 目标（DoD）

- [x] `MapExporter.export(Session)` 是唯一 public Map 入口：null 返回 empty Map，非 null 恰好捕获一次。
- [x] 新增唯一 package-private `CanonicalExportV2Projection.create(SessionExportSnapshot)`，不读取 mutable model。
- [x] `SessionExportSnapshot.toCanonicalV2()` 仅委托该 owner；不新增 public schema class 或 exporter V1/V2 overload。
- [x] Map 主入口返回 `schemaVersion=Integer(2)` 的 exact canonical tree，所有 nullable/empty key 始终存在。
- [x] admitted scalar 保留精确 Java 类型；non-finite/unsupported 使用 ADR-008 可逆 tagged map。
- [x] canonical nested Map/List 深度不可修改且保留合法 null；task projection 使用显式栈，无递归。
- [x] capture/limit/lock/projection failure 在返回 Map 前原样传播，不产生 partial projection。
- [x] Map 不再 import/read `TaskNode`、`Message`、`TaskDurationCache`；statistics 只取 snapshot。

### Exact Canonical V2 Schema

```text
schemaVersion: Integer(2)
captureEpochMillis: Long
session: { id, name, status, threadId, threadName: String,
           startEpochMillis: Long, endEpochMillis: Long|null, durationNanos: Long|null }
statistics: { totalTasks, maxDepth, totalMessages: Integer }
rootTask: Task
truncated: Boolean

Task: { id, name, path, threadName: String, depth, sequence: Integer, status: String,
        startEpochMillis: Long, endEpochMillis: Long|null, durationNanos: Long|null,
        selfDurationNanos, accumulatedDurationNanos: Long,
        messages: List<Message>, attributes: Map<String, canonical value>, tags: List<String>,
        children: List<Task>, childrenTruncated: Boolean }

Message: { type: String|null, displayLabel: String, severity: String enum name,
           customLabel: String|null, content: String, timestampEpochMillis: Long, threadName: String }
```

顶层、Session、Task、Message 的 key 均按上述顺序始终存在；`limits`、`captureNanos`、message
`timestampNanos` 不属于 wire tree。`session.name == rootTask.name`，真实 sibling `sequence` 直接来自 snapshot。

### Canonical Special Values

```text
NonFiniteNumber -> { kind: "nonFiniteNumber", numberType: "FLOAT"|"DOUBLE", value: token }
UnsupportedValue -> { kind: "unsupported", className: fully.qualified.Class }
```

null 与 admitted exact scalar 原样进入 Map；不得 generic stringify、coerce 或调用用户回调。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| schema owner | model package 的 package-private projection | EXP-05 可通过 snapshot delegate 复用同一树 | Map/JSON 各自构建 |
| public surface | 保留 `MapExporter.export(Session)`，新增 snapshot canonical delegate | 主入口直接 V2，无并行版本 API | `exportV1/exportV2` overload |
| tree builder | mutable staging + iterative task stack + deep freeze | 兼顾深树、null 和不可修改结果 | 递归、`Map.copyOf`/`List.copyOf` |
| 历史 goldens | 保留资源，退役 V1 Map 运行时断言 | 历史证据不约束 4.0 | 为过旧 golden 保留 adapter |
| EXP-06 | same-snapshot Map/JSON parity 与唯一 owner 终审 | JSON 尚未迁移时不能伪造 parity | 本卡提前改 JSON |

### 不可改变的边界

- `DefaultExportProvider` 和 `TfiFlow` 无 Session 时的 empty Map fallback 不变。
- 本卡不修改 JSON；EXP-05 必须消费 `SessionExportSnapshot.toCanonicalV2()`，不得复制 schema。
- Console 仍是非 schema snapshot 诊断文本；本卡不添加 Console 字段。
- 本卡完成时 `TaskDurationCache` 仍保留；当前删除 owner 已由 accepted G1 精确固定为 `TASK-EXP-08`，
  `TASK-EXP-10` 已取消，不存在后续 maturity batch。

## 二、执行（设计时填）

### 文件与方法

| 动作 | 精确路径 | 类型/方法 |
|------|----------|-----------|
| 新增 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/CanonicalExportV2Projection.java` | package-private iterative builder/deep freezer |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/SessionExportSnapshot.java` | public `toCanonicalV2()` one-line delegate |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/package-info.java` | 唯一 projection owner 说明 |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/exporter/map/MapExporter.java` | public V2 主入口、package-private capturer seam |
| 新增测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/CanonicalExportV2ProjectionTests.java` | exact schema/types/null/tag/deep immutability/deep tree |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/map/MapExporterTest.java` | null/capture once/V2 contract/failure tests |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/architecture/TaskTreeGateArchitectureTests.java` | Map/projection 无 mutable traversal、无递归门禁 |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/ExportV1GoldenTests.java` | 退役 Map V1 runtime gate，保留资源与历史说明 |
| 修改测试（全量补漏） | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TfiFlowTest.java` | facade Map 断言迁移到 canonical V2 顶层 |
| 修改测试（全量补漏） | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TfiFlowProviderPathTest.java` | provider Map 路径迁移到 canonical V2 |
| 修改测试（全量补漏） | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/integration/FlowLifecycleIntegrationTest.java` | E2E Map 断言迁移到 nested session |
| 修改测试（全量补漏） | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/integration/RedTeamRegression2Test.java` | 退役 H15 V1 alias，改守 V2 rootTask/纳秒口径 |
| 修改文档 | `docs/product/architecture/README.md` | 记录 EXP-04 完成时 Map 已收敛、JSON 当时尚待 EXP-05 |

### 核心步骤

1. 先写 RED：主入口 `schemaVersion=2`、capture count/null、exact key/type/null、markers、deep freeze、
   最大深度、capture failure 和 forbidden imports；缺失 public delegate 用反射形成行为 RED，避免编译错误。
2. `MapExporter.export(Session)` 执行 null guard → capturer apply 一次 → `snapshot.toCanonicalV2()`；
   package-private seam 接受 `Function<Session, SessionExportSnapshot>`，不暴露第二 public 入口。
3. projection 使用 staging `LinkedHashMap`/`ArrayList`。task frames 先创建 parent map，再逆序压入 children；
   完成后迭代 deep freeze，使用 `Collections.unmodifiableMap/List` 保留 null。
4. value adapter 只接受 snapshot exact whitelist、`NonFiniteNumber`、`UnsupportedValue`；marker map 也深度不可修改。
5. 删除 Map V1 formatter、`calculateStats` 和 cache；历史 `v1-map*.json` 文件不删除、不更新为 V2。

### 测试与验收命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=CanonicalExportV2ProjectionTests,MapExporterTest,TaskTreeGateArchitectureTests test
./mvnw -pl tfi-flow-core \
  -Dtest=CanonicalExportV2ProjectionTests,MapExporterTest,SessionExportSnapshotTests,SessionSnapshotCapturerTests,ExportV1GoldenTests,TaskTreeGateArchitectureTests test
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：G3 精确 token/contract 在 RED 前通过。
- [x] CP-2：Map Session path capture count 1，null path 0；失败无 projection。
- [x] CP-3：exact keys、null rules、runtime types、severity、sequence 与 tagged values 通过。
- [x] CP-4：同一 snapshot 的 repeated projection value-equal、identity-independent、deeply unmodifiable。
- [x] CP-5：1000 层投影无 stack overflow；statistics/truncation 与 snapshot 一致。
- [x] CP-6：只有 canonical owner 构树；Map/source 无 mutable traversal、V1 adapter 或 cache。
- [x] CP-7：public snapshot addition 由 japicmp 报告为 unexcluded compatible addition。

### 回滚边界

失败时整体回退 projection、snapshot delegate、Map 主入口和本卡测试；保留 EXP-01..03。禁止只恢复
V1 Map formatter、留下 unused canonical owner，或保留 Map-only 私有 schema 让 EXP-05 再建第二棵树。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只迁移 Map 与 canonical foundation，不提前改 JSON。
- [x] **认知负担**：一个 projection owner、一个 snapshot delegate、一个 Map capture seam。
- [x] **比例失调**：测试聚焦 schema/type/null/tagged/deep immutability，不维护 V1 identity 细节。
- [x] **ROI**：EXP-05 可直接消费同一 tree，避免临时双实现和二次迁移。
- [x] **洁癖检测**：未新增 DTO hierarchy、schema registry 或 exporter options。
- [x] **局部 vs 全局**：snapshot 拥有语义，projection 只拥有 wire tree，exporter 只拥有入口。
- [x] **过度设计**：没有平行 public V1/V2 API 或第二 public schema class。

**结论**：设计与 ADR-008 一致；用户已批准按任务包顺序实施，本卡进入测试先行阶段。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 全量测试补漏 | 聚焦测试与原 Map golden 完成 V2 迁移 | Core 首轮发现 5 个跨层 V1 Map 断言并全部迁移 | 旧断言分散在 facade/E2E/red-team，未进入初始 focused 清单 |
| 终态字段证据 | exact schema/type/null | 增加 completed snapshot 精确 epoch/nanos 测试与 mutation RED | RUNNING-only 测试无法区分 millis/nanos 错接 |
| 架构文档路径 | 按仓库架构 README 同步 | 更新实际权威路径 `docs/product/architecture/README.md` | 指南中的 `docs/architecture/README.md` 在仓库中不存在 |

### 检查点结果

- [x] CP-1..7：61 focused、694 Core、API profile、AST owner gate 与 7/7 consumer fresh 通过。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | exact key/type/null/tag、终态 epoch/nanos、capture count、deep freeze 与 1000 层投影均有行为测试 |
| 完整性 | 25/25 | DoD 8/8、CP 7/7；61 focused、694 Core、API profile 与 7/7 consumer package 通过 |
| 可维护性 | 25/25 | 一个 package-private schema owner、一个 snapshot delegate、一个 Map capture seam；task/freeze 均为显式栈 |
| 风险控制 | 25/25 | AST gate 禁止 mutable traversal/递归/cache；V1 runtime assertions 全部退役且历史资源保留 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| SHOULD | EXP04-R1 | RUNNING-only schema 测试无法识别 millis/nanos 字段错接 | `CanonicalExportV2ProjectionTests.java` | 增加 completed snapshot 精确值断言，并以 mutation RED 验证有效性 |
| MUST | EXP04-R2 | Core 全量仍有 4 个顶层 `sessionId` 与 1 个 V1 alias runtime 断言 | 四个 facade/integration 测试文件 | 全部迁移到 V2；失败用例 5/5 与 Core 694/694 通过 |

## 六、完成审核

**审核结论：通过。** 1 MUST + 1 SHOULD 全部闭环，0 unresolved MUST / SHOULD。`MapExporter` 只负责
null guard、单次 snapshot capture 与 delegate；唯一 package-private owner 以显式 task/freeze 栈发布深度不可修改的
canonical V2，不再读取 mutable tree、计算统计或使用 `TaskDurationCache`。

### Fresh 证据（2026-07-11）

- canonical/Map/snapshot/golden/architecture focused 61/61；首轮 RED 22 项中 12 项按预期失败，deep-freeze 独立 RED 1/1。
- Core `clean verify` 694/694；Checkstyle 0、SpotBugs 0/0、JaCoCo gate 通过。
- Core `api-compat` profile 通过；japicmp 将 `toCanonicalV2()` 报告为 public compatible addition。
- 六个消费者模块加 repository parent 的 reactor package 7/7 通过，固定包含 `tfi-examples`。
- 历史 `v1-map*.json` 资源保留；Map V1 golden、facade/E2E 顶层 `sessionId` 与 H15 alias runtime gate 已退役。
