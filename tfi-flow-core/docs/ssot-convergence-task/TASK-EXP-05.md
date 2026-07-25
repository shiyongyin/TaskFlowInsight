# TASK-EXP-05：将 JSON 主入口迁移到 Canonical V2

> **定位**：删除 JSON V1 模式分叉，使现有 JSON 主入口只编码 `SessionExportSnapshot.toCanonicalV2()` 返回的唯一 schema tree。
> **状态**：完成（2026-07-11；118 focused / 694 Core / API / 11 tfi-all passed / 7/7；100/100）
> **审核状态**：审核通过（2026-07-11；1 MUST + 1 SHOULD 已修复，0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-EXP-03`、`TASK-EXP-04`、已接受 `G1` 与 `G3`；后续 `TASK-EXP-06`
> **架构来源**：ADR-005 的 4.0 精确删除政策、ADR-008 的 V2-only canonical schema 决策

---

## 一、核心（已确认）

### 背景

实施前的 `JsonExporter` 发布 `COMPAT`/`ENHANCED` 两套 V1 schema，直接遍历 mutable
`Session`/`TaskNode`/`Message`，自行计算 statistics，并通过递归 writer 和任意对象字符串化形成第二套导出语义。
EXP-04 已建立唯一 canonical V2 projection owner；继续维护 JSON V1 或在 JSON 内复制 V2 schema 都会重新引入歧义。

### 唯一成功路径

```text
Session
  -> SessionExportSnapshot.capture(session) exactly once
  -> snapshot.toCanonicalV2()
  -> iterative JSON encoder
  -> Canonical V2 JSON
```

非 null 的 String/Writer 主入口都必须严格使用该路径。捕获完成后不得再读取 mutable
`Session`、`TaskNode` 或 `Message`；JSON exporter 不拥有 schema、statistics 或 special-value 规则。

### 唯一公开 API

保留且只保留：

```java
public JsonExporter()
public String export(Session session)
public void export(Session session, Writer writer) throws IOException
```

4.0 精确删除：

```text
com.syy.taskflowinsight.exporter.json.JsonExporter$ExportMode
com.syy.taskflowinsight.exporter.json.JsonExporter#JsonExporter(
    com.syy.taskflowinsight.exporter.json.JsonExporter$ExportMode)
```

不保留 `V1`、`COMPAT`、`ENHANCED`、隐藏 adapter、别名 constructor、版本 overload 或另一棵 schema tree。
`ChangeJsonExporter.ExportMode` 属于 compare domain，不在本卡范围内。

### 目标（Definition of Done）

- [x] 两个非 null Session 路径各捕获恰好一次，并只编码 `toCanonicalV2()` 的返回值。
- [x] 输出是 parser 可解析的 exact canonical V2；顶层 key 顺序固定为
  `schemaVersion,captureEpochMillis,session,statistics,rootTask,truncated`。
- [x] canonical tree 中所有 nullable/empty key 都保留；字符串按 JSON 标准转义。
- [x] encoder 使用 iterator frame 显式栈处理 Map/List，1000 层合法树不发生 `StackOverflowError`；
  容器遍历状态只随深度增长，不预展开 sibling/value steps。
- [x] `Character` 精确编码为单字符 JSON string；`NonFiniteNumber` 与 `UnsupportedValue` 只编码 projection
  已生成的 tagged map，不调用用户代码。
- [x] null Session 精确返回/写入 `{"error":"No session data available"}`，capturer 调用次数为 0。
- [x] capture/limit/lock/projection failure 从 direct exporter 原样传播；Writer 在此类失败时长度为 0。
- [x] Writer I/O failure 原样传播；String 的历史渲染 fallback 只允许包围 capture/projection 之后的编码阶段。
- [x] `TfiFlow` facade 继续把 exporter failure 转换为 `{}`，且后续正常 export 可恢复。
- [x] JSON production code 不依赖 Jackson，不读取 mutable model，不访问 `TaskDurationCache`，没有递归 helper。
- [x] `ExportMode` 与 mode constructor 的删除在 manifest、manifest contract test 和 japicmp 中逐符号登记。
- [x] Core、API compatibility、目标 `tfi-all` 测试和七模块消费者编译全部通过。

### Exact Canonical V2 Contract

JSON 必须逐值编码 `snapshot.toCanonicalV2()` 返回的 Map/List tree，不得重命名、补算、遗漏或重排字段。
schema 由 `CanonicalExportV2Projection` 唯一拥有，概要为：

```text
schemaVersion
captureEpochMillis
session
statistics
rootTask
truncated
```

特殊值只允许以下 wire form：

```json
{"kind":"nonFiniteNumber","numberType":"FLOAT|DOUBLE","value":"NaN|Infinity|-Infinity"}
{"kind":"unsupported","className":"fully.qualified.Class"}
```

encoder 只接受 canonical projection 实际产生的 `Map<String,Object>`、`List<?>`、String、Character、Boolean、
Byte、Short、Integer、Long、有限 Float/Double、BigInteger、BigDecimal 和 null。`Character` 是 ADR-008 admitted
scalar：Map 保留 Java `Character`，JSON 将其编码为单字符 string。出现上述闭集之外的 key/value 类型必须失败，
不得 generic `String.valueOf`、调用用户代码或静默跳过。

### 失败顺序

| 场景 | Direct String | Direct Writer | `TfiFlow` |
|------|---------------|---------------|-----------|
| null Session | exact error JSON | exact error JSON | 既有无 Session fallback |
| capture/limit/lock failure | 原异常实例传播 | 原异常实例传播，0 bytes | `{}` |
| projection failure | 原异常实例传播 | 原异常实例传播，0 bytes | `{}` |
| Writer I/O failure | 不适用 | `IOException` 传播 | facade 既有策略 |
| post-projection String encoding failure | ADR-008 历史 escaped error JSON fallback | 不吞异常 | `{}` |

String 路径顺序必须是 null guard → capture → projection → 进入 encoding fallback；Writer 路径必须是
null guard → capture → projection → first write。不得把 capture/projection 放入 String fallback catch。
捕获锁的等待策略仍由 EXP-01 的唯一 mutation/capture gate 拥有：生产 capture timeout 固定为 30 秒，interrupt
恢复中断标记；mutation 为保证 terminal completion 不设 timeout。JSON 层不得复制或改写该策略，只能在首次输出前
原样传播 capture timeout/interrupt failure。

## 二、执行计划

### 文件与所有权

| 动作 | 精确路径 | 责任 |
|------|----------|------|
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/exporter/json/JsonExporter.java` | 删除模式 API；单次捕获；迭代编码 canonical tree |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/json/JsonExporterTest.java` | exact V2、parser、capture/failure、tag、深度测试 |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/architecture/TaskTreeGateArchitectureTests.java` | 禁止 mutable import/traversal/cache/递归和第二 schema owner |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/ExportV1GoldenTests.java` | 退役 JSON V1 runtime 断言，保留历史资源 |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TfiFlowTest.java` | facade JSON 断言迁移到 canonical V2 |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TfiFlowProviderPathTest.java` | failure `{}` 与恢复语义 |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/integration/FlowLifecycleIntegrationTest.java` | E2E JSON 断言迁移到 canonical V2 |
| 修改（全量补漏） | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/TaskDurationCacheTest.java` | JSON duration 断言迁移到 canonical nanos 字段 |
| 修改 | `tfi-flow-core/src/test/resources/compatibility/breaking-changes-v4.json` | 两项精确 4.0 删除 |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/compatibility/BreakingChangeManifestTests.java` | 精确删除 expected set |
| 修改 | `tfi-flow-core/pom.xml` | 两项 exact japicmp exclusion |
| 修改 | `tfi-all/src/test/java/com/syy/taskflowinsight/exporter/json/JsonExporterTest.java` | 删除 mode constructor，迁移 V2 断言 |
| 修改 | `tfi-all/src/test/java/com/syy/taskflowinsight/exporter/json/JsonExporterTests.java` | 迁移 V2 断言 |
| 修改 | `tfi-all/src/test/java/com/syy/taskflowinsight/integration/ExportVerificationIT.java` | 迁移集成输出断言 |
| 修改（消费者补漏） | `tfi-all/src/test/java/com/syy/taskflowinsight/api/JsonExportValidationTest.java` | JSON/Map 验证迁移到 canonical V2 |
| 修改 | `docs/product/architecture/README.md` | 记录 JSON V2-only 与精确删除 |
| 修改 | `tfi-flow-core/docs/ssot-convergence-task/INDEX.md` | 回填 EXP-05 状态、证据和下一张卡 |

历史 `v1-*.json` 资源不删除、不改写为 V2；它们只保留迁移前证据，不再约束 4.0 runtime。

### TDD 批次

1. RED：exact canonical keys/order/null、parser validity、String/Writer capture 1 次、null capture 0 次。
2. GREEN：删除 mode API，增加 package-private capturer seam，调用 `snapshot.toCanonicalV2()`。
3. RED：failure identity、Writer 0 bytes、I/O propagation、String fallback boundary、facade `{}` 与恢复。
4. GREEN：固定 capture/project-before-write 顺序，并保持 facade ownership。
5. RED：tagged values 不触发回调、1000 层编码、非法 canonical value fail-fast、JSON 架构禁令。
6. GREEN：用显式 frame stack 编码 Map/List/scalar；删除 V1 formatter/statistics/cache/mutable imports。
7. RED/GREEN：精确 breaking manifest、Core stale tests 与目标 `tfi-all` consumer migration。

### 公共删除登记

两个 manifest entry 均使用 `approvedBy: "ADR-005"`、`ownerTask: "TASK-EXP-05"`、
`evidence: ["JAPICMP", "CONSUMER_COMPILE"]`。reason 必须同时说明 ADR-005 允许 4.0 direct removal，
以及 ADR-008 已决定 JSON V2-only；不得用包级 exclusion 或“主版本允许破坏”代替逐符号登记。

### 验收命令

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'Status: ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_STATUS=ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_DECISION=V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES' \
  docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
./mvnw -pl tfi-flow-core \
  -Dtest=JsonExporterTest,TaskTreeGateArchitectureTests test
./mvnw -pl tfi-flow-core \
  -Dtest=JsonExporterTest,SessionExportSnapshotTests,ExportV1GoldenTests,TfiFlowTest,TfiFlowProviderPathTest,FlowLifecycleIntegrationTest,TaskTreeGateArchitectureTests,BreakingChangeManifestTests test
./mvnw -pl tfi-all -am \
  '-Dtest=JsonExporterTest,JsonExporterTests,ExportVerificationIT#testJsonExportContainsChangeMessages' \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-all -am \
  -Dtest=JsonExportValidationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：任务卡、G3 token 与五份计划指纹一致，无 V1/COMPAT/ENHANCED 实施残留。
- [x] CP-2：String/Writer capture count 为 1，null 为 0；两条路径只调用一次 `toCanonicalV2()`。
- [x] CP-3：parser-backed exact V2、null/empty、escape、Character/tagged-value 和 1000 层测试通过。
- [x] CP-4：capture/projection failure identity、Writer 0 bytes、I/O propagation、String catch boundary 通过。
- [x] CP-5：source 无 mutable traversal、cache、递归、schema literal、sibling 预展开或 production Jackson。
- [x] CP-6：两项 public deletion 在 manifest/test/japicmp 三处 exact match，消费者不再调用旧 constructor。
- [x] CP-7：facade `{}` fallback、recovery、Core clean verify、API 与七模块 consumer gate 通过。

### 回滚边界

失败时只回退 EXP-05 对 JSON exporter、相关测试、breaking bookkeeping 和文档的修改；保留 EXP-01..04
的 snapshot/canonical projection/Map 结果与历史 V1 资源。回滚不得恢复 V1 runtime、`ExportMode`、mode constructor、
mutable traversal 或 JSON 私有 schema；若 V2 主入口无法闭环，EXP-05 保持未完成并阻塞 EXP-06。

## 三、自省（实施前）

- [x] **无歧义**：一个公开构造器、两个公开导出方法、一个 schema owner、一个成功路径。
- [x] **兼容边界**：仅保留确有 facade 契约依据的 null/failure 行为；V1 模式不作为兼容借口保留。
- [x] **职责边界**：snapshot 捕获语义，projection 拥有 schema，JSON exporter 只负责编码。
- [x] **深树安全**：projection 与 encoder 都使用显式栈，不把递归风险转移到 JSON 层。
- [x] **回调安全**：special value 已在 capture/projection 阶段标记，encoder 不观察任意用户对象。
- [x] **过度设计**：不增加 schema registry、DTO hierarchy、版本路由器或 production JSON dependency。

## 四、实施反馈

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 全量测试补漏 | 初始清单覆盖 JSON、facade 与 E2E 主断言 | 增补 `TaskDurationCacheTest` 与 `JsonExportValidationTest` | 两处仍持有迁移前 JSON/Map 字段断言 |
| `tfi-all` 验收范围 | 运行 JSON consumer 与相关 `ExportVerificationIT` | 只运行 `testJsonExportContainsChangeMessages`；不宣称完整类通过 | 完整类另有两条 Console/change-tracking 既有失败，不属于 JSON V2 卡 |
| encoder traversal state | 显式栈处理深树 | 首版预展开 sibling steps；Code Review 后改为 iterator frame | 避免宽树把 traversal state 放大为 O(payload) |
| Character wire contract | 复用 ADR-008 admitted scalar | 任务卡漏写；实现已按单字符 JSON string 编码，补 mutation-proven 测试和本文精确定义 | JSON 没有独立 Character 类型，必须明确 Map/JSON 的表示差异 |

### Code Review 与评分

| 项目 | 结果 |
|------|------|
| MUST / SHOULD | 1 MUST + 1 SHOULD 均已修复；0 unresolved MUST / SHOULD |
| 正确性 /25 | 25：exact V2、capture/failure、Character/tag、转义、1000 层与 facade recovery 均有测试 |
| 完整性 /25 | 25：DoD 12/12、CP 7/7；Core/API/tfi-all/七模块与两项 breaking bookkeeping 闭环 |
| 可维护性 /25 | 25：一个 projection owner；JSON 只保留 capture/project/iterator-frame encoder 三段职责 |
| 风险控制 /25 | 25：AST 禁令、精确 public 删除、mutation-proven Character 契约与失败前零输出 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件 | 处置 |
|------|------|------|------|------|
| MUST | EXP05-R1 | 首版 encoder 为每个容器预构造全部 sibling steps，宽树额外内存随 payload 增长 | `JsonExporter.java` | 改为 `ObjectFrame`/`ArrayFrame` iterator frame；架构测试禁止恢复 step materialization |
| SHOULD | EXP05-R2 | 任务卡的 encoder scalar 闭集漏写 ADR-008 admitted `Character`，实现与文字可被误判为冲突 | `TASK-EXP-05.md`、`JsonExporterTest.java` | 明确 Map 保留 Character、JSON 编码单字符 string；mutation 移除分支时测试失败，恢复后通过 |

## 六、完成审核

**审核结论：通过。** 1 MUST + 1 SHOULD 全部闭环，0 unresolved MUST / SHOULD。`JsonExporter` 的唯一成功
路径是 `Session -> capture once -> toCanonicalV2() -> iterative encoder -> JSON`，不再拥有 schema、读取 mutable
model 或发布 V1 mode。后继 EXP-06 已按此边界完成 same-snapshot Map/JSON parity 与唯一 owner 最终门禁，且未恢复
additive V2 overload、V1 主入口或第二棵 schema tree。

### Fresh 证据（2026-07-11）

- G3 ADR contract 4/4，三个 exact token 命中；五份计划 SHA-256 逐项等于 `INDEX.md`。
- JSON/架构 focused 24/24；EXP-05 组合门禁 118/118，其中 breaking manifest 15/15、provider path 26/26。
- `tfi-all` JSON consumer 9/9；`JsonExportValidationTest` 2 passed、1 个既有 disabled。诊断性完整
  `ExportVerificationIT` 为 5 run / 3 passed / 2 failed；失败精确为 `testConsoleExportContainsChangeMessages`
  与 `testMultipleTasksWithChanges`，两条 Console/change-tracking 债务不计入 JSON V2 完成证据。
- Core `clean verify` 694/694；Checkstyle 0、SpotBugs 0 bugs / 0 errors、JaCoCo gate 通过。
- Core `api-compat` profile 通过；`javap -public` 只显示无参构造器和两个 export 方法。
- 六个消费者模块加 repository parent 的 reactor package 7/7 通过，固定包含 `tfi-examples`。
