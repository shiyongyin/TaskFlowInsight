# TASK-EXP-06：关闭同快照 Map/JSON Parity 与唯一 Schema Owner 门禁

> **定位**：不再新增任何 V2 API；只证明 EXP-04/05 的现有 Map/JSON 主入口可由同一个预构建
> `SessionExportSnapshot` 驱动，并把唯一 canonical V2 schema owner 固化为架构门禁。
> **状态**：完成（2026-07-11；42 focused / 696 Core / API / 9 tfi-all / 7/7；100/100）
> **审核状态**：审核通过（2026-07-11；1 SHOULD 已修复，0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-EXP-04`、`TASK-EXP-05`、已接受 `G3`；本卡完成时的下一张为 `TASK-EXP-07`，现已汇合并进入 `TASK-EXP-08`
> **架构来源**：ADR-008 V2-only 决策；EXP-04 canonical projection owner；EXP-05 JSON V2-only encoder

---

## 一、核心（已确认）

### 背景

EXP-04 已建立唯一 package-private `CanonicalExportV2Projection`，并让 `MapExporter.export(Session)`
单次捕获后直接返回 `snapshot.toCanonicalV2()`。EXP-05 已让 `JsonExporter` 的 String/Writer 主入口各单次
捕获，并只编码同一 delegate 返回的 tree。旧版 EXP-06 仍计划 additive `exportV2(...)`、snapshot overload 和
V1 主入口保留，已被 ADR-008 与 EXP-04/05 的完成事实整体取代。

本卡不再迁移 runtime。它只关闭尚未集中证明的最后两个断言：Map 与 JSON 可消费同一个 snapshot identity；
production 中只有一个 schema tree builder，且 exporter 不重新出现 V1/V2 并行入口。

### 范围与非目标

**范围内：**

- 用两个 test-source access bridge 调用 Map/JSON 已有的 package-private capturer seam。
- 用同一个 prebuilt snapshot 和同一个 counting capturer 生成 Map 与 JSON。
- Map 与 `snapshot.toCanonicalV2()` 做 exact Java tree equality；JSON 与该 tree 的 test-scope Jackson 编码做
  parser-tree equality。
- 在 snapshot 捕获后、两个 exporter 调用前修改原 Session，证明 parity 不依赖二次 capture。
- 固化唯一 schema literal owner、唯一 projection delegate、现有 public surface 和 forbidden parallel API。

**范围外：**

- 不修改任何 `src/main` 文件，不新增 public/package-private production overload。
- 不新增 `exportV1`、`exportV2`、schema registry、DTO hierarchy 或第二个 projection builder。
- 不改变 canonical key、顺序、nullable/empty、runtime type、tagged value、JSON escaping 或 failure 语义。
- 不恢复 V1 golden 的 runtime 约束，不改 `ExportProvider`、Console 或 `TaskDurationCache`。
- 不把完整 `tfi-all` 的 Console/change-tracking 既有失败纳入本卡。

### 唯一验证路径

```text
Session --capture exactly once--> prebuilt SessionExportSnapshot
  |                                   |
  | mutate original Session           +--> snapshot.toCanonicalV2() --> expected Map
  |                                   |
  +--> Map test access --> existing capturer seam --> same snapshot --> actual Map
  +--> JSON test access -> existing capturer seam --> same snapshot --> actual JSON

actual Map == expected Map
parse(actual JSON) == parse(test-scope Jackson(expected Map))
```

test access bridge 只存在于 `src/test/java`。它们接收同一个
`Function<Session, SessionExportSnapshot>`，因此 contract test 同时证明：两个 seam 合计调用 capturer 恰好两次，
每次返回相同 snapshot identity；若任一 exporter 绕过 seam 重新捕获，post-capture mutation 会进入输出并使测试失败。

### 目标（Definition of Done）

- [x] 新增 `MapExporterTestAccess` 与 `JsonExporterTestAccess`，且只存在于 test source。
- [x] `ExportV2ContractTests` 对同一个 prebuilt snapshot 完成 Map/JSON parity，capturer 合计调用恰好两次。
- [x] 捕获后的 Session mutation 不进入 Map、JSON 或 expected canonical tree。
- [x] Map 与 canonical projection exact equal；Map 的 admitted scalar Java runtime types 保持不变。
- [x] JSON parser tree 与 test-scope Jackson 对同一 projection 的编码相等；`Character` 只在 JSON 中归一为 string。
- [x] parity fixture 同时覆盖 nullable/empty、severity、真实 sibling sequence、tag、Character、BigInteger、
  BigDecimal、non-finite 和 unsupported tagged value，且不调用用户回调。
- [x] production Java 中字符串 literal `"schemaVersion"` 的唯一 owner 是
  `model/CanonicalExportV2Projection.java`。
- [x] `SessionExportSnapshot.toCanonicalV2()` 仍只调用一次 owner `create(...)`；Map/JSON 不含 schema literal。
- [x] Map/JSON public API 不新增 `SessionExportSnapshot` 参数，不出现 `exportV1/exportV2` 或第二 schema class。
- [x] EXP-04/05 的 projection、exporter、golden、架构、API、tfi-all JSON 和七项目 consumer 门禁全部通过。
- [x] 本卡的 production diff 为零；breaking manifest、japicmp exclusions 和历史 V1 resources 均不变。

### Parity 比较规则

Canonical Map 是类型权威，JSON 是该 Map tree 的 wire encoding，不能直接比较 Java Map leaf 与 `JsonNode` leaf：

| 值 | Map contract | JSON parity oracle |
|----|--------------|--------------------|
| Character | Java `Character` | 单字符 JSON string |
| Byte/Short/Integer/Long/BigInteger | 精确 Java runtime type | JSON integer node |
| Float/Double/BigDecimal | 精确 Java runtime type/value | JSON number node |
| null/empty container | key 始终存在 | JSON null / empty object / empty array |
| non-finite/unsupported | projection 已生成的 tagged Map | 相同 tagged JSON object |

因此 JSON expected 必须由 test-scope Jackson 编码**同一个 canonical Map**后再 parse；不得手写第二套 expected JSON、
不得分别 capture 后忽略 `captureEpochMillis`，也不得用字符串 contains 代替整棵 parser tree equality。

### 关键决策

| 决策点 | 选择 | 理由 | 明确拒绝 |
|--------|------|------|----------|
| same-snapshot 注入 | test-source access bridge 调用现有 capturer seam | 无 production surface；可传同一 identity | public/package-private snapshot overload |
| parity oracle | Map exact equality + JSON parser-tree equality | 同时保护 Java types 与 JSON wire semantics | raw Map/JsonNode equality、字段抽样 |
| capture 证明 | 一个 counting Function 被两个 bridge 复用 | 合计 2 次且恒返同一 snapshot | 分别创建两个 capturer、分别 capture |
| 防重捕获 | capture 后 mutation 再调用 exporter | 对绕过 seam 的实现形成确定性失败 | 只比较稳定完成态 Session |
| 架构 owner | 扫描全部 Core production Java schema literal | 防止未来在任意包复制字段树 | 只检查 Map/JSON 两个已知文件 |
| public surface | 保持 EXP-04/05 现状 | G1/G3 已确定唯一主入口 | additive `exportV2` 与 retained V1 |

## 二、执行计划

### 文件与所有权

| 动作 | 精确路径 | 责任 |
|------|----------|------|
| 新增测试桥 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/map/MapExporterTestAccess.java` | test-only 调用 Map capturer seam |
| 新增测试桥 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/json/JsonExporterTestAccess.java` | test-only 调用 JSON String capturer seam |
| 新增测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/ExportV2ContractTests.java` | 同一 snapshot 的完整 Map/JSON parity |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/architecture/TaskTreeGateArchitectureTests.java` | 唯一 literal owner 与无平行 public API 最终门禁 |
| 修改文档 | `docs/product/architecture/README.md` | 记录 EXP-06 parity/owner 已闭环 |
| 修改文档 | `tfi-flow-core/docs/ssot-convergence-task/INDEX.md` | 回填 EXP-06 状态、证据和下一张卡 |

`src/main`、POM、manifest、accepted ADR、当前架构 SSOT 与 V1 resources 不在修改清单中。

### Test-only bridge 精确接口

```java
public static Map<String, Object> MapExporterTestAccess.export(
        Session session,
        Function<Session, SessionExportSnapshot> capturer)

public static String JsonExporterTestAccess.export(
        Session session,
        Function<Session, SessionExportSnapshot> capturer)
```

两个类均为 `public final` test utility，构造器 private。方法只做一行委托，不捕获、不投影、不编码、不处理异常，
避免 test helper 成为第三套行为实现。

### TDD 批次

1. RED：新增两个 bridge 的最小实现，先调用 public Session 主入口并忽略传入 capturer；新增 parity test，
   以 capturer count 为 0 和 post-capture mutation 泄漏形成确定性失败。
2. GREEN：bridge 改为调用所在 package 的既有 capturer seam；production code 不变，parity test 通过。
3. RED/GREEN：增加唯一 schema owner/public surface 架构断言；临时向 exporter 加入 schema literal 与
   `exportV2` token 做 mutation run，确认门禁失败后恢复 production source。
4. REGRESSION：运行 canonical projection、Map、JSON、V1 historical resource、架构、API、tfi-all JSON 与
   七项目 consumer 门禁。

### 验收命令

```bash
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'Status: ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_STATUS=ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_DECISION=V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES' \
  docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
./mvnw -pl tfi-flow-core -Dtest=ExportV2ContractTests test
./mvnw -pl tfi-flow-core \
  -Dtest=ExportV2ContractTests,CanonicalExportV2ProjectionTests,MapExporterTest,JsonExporterTest,ExportV1GoldenTests,TaskTreeGateArchitectureTests test
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-all -am \
  '-Dtest=JsonExporterTest,JsonExporterTests,ExportVerificationIT#testJsonExportContainsChangeMessages' \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：G3 exact token 3/3、ADR contract 4/4、五份计划 SHA 5/5 与 INDEX 一致。
- [x] CP-2：parity 使用一个 prebuilt snapshot、一个 counting capturer；调用 2 次且 mutation 不泄漏。
- [x] CP-3：Map exact tree/type 与 JSON parser tree 都以同一个 `toCanonicalV2()` 结果为 oracle。
- [x] CP-4：fixture 覆盖 null/empty、message/sequence、admitted scalar 与两类 tagged special value。
- [x] CP-5：Core production schema literal owner 1/1；delegate 1 次；exporter schema literal 0。
- [x] CP-6：Map/JSON 无 `exportV1/exportV2`、无 public snapshot overload；EXP-06 production diff 0。
- [x] CP-7：focused、Core clean verify、API、tfi-all JSON 与七项目 consumer 全部 fresh 通过。

### 回滚边界

失败时只删除两个 test access bridge、`ExportV2ContractTests`，并回退本卡新增的架构断言和文档；
EXP-04/05 的 canonical owner、Map/JSON runtime、breaking bookkeeping 与历史 V1 resources 保持不动。
不得以回滚为由添加 snapshot overload、分别 capture 后归一时间、删除现有 V2-only 门禁或恢复 V1 runtime。

## 三、自省（实施前）

- [x] **无歧义**：本卡是验证卡，不是第三次 runtime 迁移；production diff 必须为零。
- [x] **职责边界**：snapshot 拥有语义，projection 拥有 schema，Map/JSON 只投影/编码，test bridge 只委托。
- [x] **同源证明**：同一 identity + mutation oracle，不用“同一 Session 大致相等”替代。
- [x] **类型边界**：Map exact Java type 与 JSON wire type 分开断言，不声称 Character 在 JSON 保持 Java 类型。
- [x] **兼容边界**：不新增也不删除 production API；manifest/japicmp exclusions 不变。
- [x] **过度设计**：不新增 DTO、registry、adapter、production test seam 或 schema version router。
- [x] **可回滚**：所有新增实现都在 test source；失败不会留下 runtime 半迁移。

## 四、实施反馈

实施反馈只记录本卡测试门禁与文档的实际偏差；EXP-04/05 的 runtime 历史不在本卡重复归功。

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 旧任务卡 public surface | additive `exportV2` + snapshot overload | 整体删除，production API 零变更 | ADR-008 与 EXP-04/05 已完成唯一 V2 主入口 |
| same-snapshot 跨 package 访问 | 不能新增 production seam | 两个 public test-source access bridge 各一行委托既有 package-private seam | Java package 可见性要求测试桥接，但不应污染 runtime |
| public surface 终审 | 初版集中检查 public 方法 | Code Review 增补 Map/JSON public constructor 集合 | 防止 `JsonExporter(SessionExportSnapshot)` 绕过方法门禁 |

### Code Review 与评分

| 项目 | 结果 |
|------|------|
| MUST / SHOULD | 0 MUST；1 SHOULD 已修复；0 unresolved MUST / SHOULD |
| 正确性 /25 | 25：同一 identity、capturer 2 次、post-capture mutation、Map exact 与 JSON parser-tree parity |
| 完整性 /25 | 25：DoD 11/11、CP 7/7；42 focused、696 Core、API、9 tfi-all、7/7 |
| 可维护性 /25 | 25：两个一行 test bridge、一个 parity oracle、一个集中架构门禁；production 零修改 |
| 风险控制 /25 | 25：public API 与 schema owner 双 mutation 证明，V1 resources/manifest/POM/ADR 均不变 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件 | 处置 |
|------|------|------|------|------|
| SHOULD | EXP06-R1 | 初版最终门禁只检查 public 方法，新增 snapshot constructor 可依赖旧单测才被发现 | `TaskTreeGateArchitectureTests.java` | 集中断言 Map 无 public constructor、JSON 只保留 public no-arg constructor |

## 六、完成审核

**审核结论：通过。** 0 unresolved MUST / SHOULD。EXP-06 没有新增或修改 production 行为；它用两个
test-source bridge 将同一个 prebuilt snapshot 交给 EXP-04/05 的既有 capturer seam，并以 Map exact equality、
JSON parser-tree equality 和 post-capture mutation 证明真正同源。唯一 schema literal owner、无平行 V1/V2 API、
无 public snapshot constructor/method overload 已成为集中架构门禁。后续按顺序进入 EXP-07。

### Fresh 证据（2026-07-11）

- G3 ADR contract 4/4，三个 exact token 命中；五份计划 SHA-256 5/5 与 `INDEX.md` 一致。
- parity RED：test bridge 绕过 seam 时 capturer 为 0，精确失败于期望 2；改为一行 seam delegate 后 1/1 通过。
- public API mutation：临时新增 `JsonExporter.exportV2(Session)` 时架构测试 1/1 按预期失败；恢复后通过。
- schema owner mutation：临时在 `JsonExporter` 新增 `"schemaVersion"` literal 时 owner 集合由 1 变 2，
  架构测试按预期失败；恢复后 owner 1/1。
- canonical/parity/Map/JSON/golden/architecture focused 42/42；Core `clean verify` 696/696。
- Checkstyle 0、SpotBugs 0 bugs / 0 errors、JaCoCo gate 通过；Core `api-compat` profile 通过。
- `tfi-all` JSON consumer 9/9；六个消费者模块加 repository parent 的 package reactor 7/7，包含 examples。
- EXP-06 未留下 `src/main` 修改；POM、manifest、ADR、计划指纹与 V1 resources 未修改。
