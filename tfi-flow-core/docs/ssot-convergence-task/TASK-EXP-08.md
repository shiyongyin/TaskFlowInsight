# TASK-EXP-08：按 4.0 边界精确删除 TaskDurationCache

> **定位**：删除最后一个 mutable-tree export 例外，并以现有 architecture owner 和合法深树门禁证明 formatter 只消费 snapshot。
> **状态**：完成（2026-07-11；33 focused / 95 Export owner / 707 Core / API / 43 tfi-all / 7/7；100/100）
> **审核状态**：审核通过（0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-EXP-04` 至 `TASK-EXP-07`、`TASK-GRD-06`、accepted G1/G3；后续 `TASK-EXP-09`
> **架构来源**：ADR-005 direct removal、ADR-008 V2-only与[当前架构 SSOT](../design-doc.md)的 Export 边界。

---

## 一、核心（设计已确认）

### 背景

EXP-03..07 已让 Console、Map、JSON 只在 public Session 边界捕获一次 snapshot，并以显式 stack/frame 完成
Console TREE/SIMPLE、canonical V2 projection 与 JSON encoding。`TaskDurationCache` 已无 formatter、Provider、
facade 或下游 production 消费者，却仍作为 public 类直接读取 bare mutable `TaskNode`，成为唯一 ownership 例外。

旧卡的 3.1 deprecate/3.3 remove、四项 ACTIVE ledger、10,000-depth V1 formatter 路线已被正式决策淘汰：
G1 是 `BREAKING_MAJOR_4_DIRECT_REMOVAL`，G3 是 Map/JSON V2-only，framework 最大合法深度是 1000。本卡不再
保留“以后删除”状态，直接按 4.0 精确删除；原 `TASK-EXP-10` 职责并入本卡并取消执行。

### 范围

**In scope：**

- 删除 `TaskDurationCache.java` 与 `TaskDurationCacheTest.java`。
- 对本地 `3.0.0` baseline 的 3 个真实 ABI symbols 增加 exact manifest entries 与 exact POM exclusions。
- 扩展现有 `TaskTreeGateArchitectureTests`，证明 cache/source reference 消失且 snapshot owners 不退化。
- 新增 1000-depth current public routes regression；第 1001 层必须截断。
- 同步 `EXP-04/09/10`、`CTX-07`、`DOC-01` 与 `INDEX.md` 的 owner/dependency 表述。

**Out of scope：**

- 不修改 Console、Map、JSON、snapshot、capturer、canonical projection production 实现。
- 不恢复 V1 Map/JSON route，不修改 V2 schema、Console semantics、budgets、locks、facade fallback。
- 不新增 replacement API、deprecated shell、no-op adapter、traversal framework 或 runtime feature flag。
- 不运行或宣称 Portfolio root gate；该门禁仍归 `TASK-DOC-01`。

### 目标（Definition of Done）

- [x] cache production source 与 owning test source 均不存在。
- [x] production/test Java 无 cache type/member consumer；历史 manifest/task/docs 文本按各自职责保留。
- [x] breaking manifest/POM 只新增 3 个 baseline symbols/exclusions，双向集合精确且无 wildcard。
- [x] `getSelfDurationMillis(TaskNode)` 随 class 消失，但不登记为 baseline breaking symbol。
- [x] 三个 manifest entries 的 `replacement=null`，迁移说明只指向 Session snapshot boundary。
- [x] 现有 AST architecture gate 继续证明 capturer/projection/formatter ownership 唯一且无递归。
- [x] snapshot、Console TREE/SIMPLE、Map V2、JSON String/Writer 在合法 1000-depth chain 上 bounded 完成；
  第 1001 层不可见且 truncation evidence 存在。
- [x] focused、Core `clean verify`、API compatibility、owner downstream 与七项目 consumer package 全部通过。
- [x] 五份受保护计划 SHA 不变，任务卡、架构证据与 `INDEX.md` 同步回填。

### 精确 breaking inventory

只登记以下 3 个 symbol；仓库 manifest 格式不带返回类型：

```text
com.syy.taskflowinsight.exporter.TaskDurationCache
com.syy.taskflowinsight.exporter.TaskDurationCache#from(com.syy.taskflowinsight.model.TaskNode)
com.syy.taskflowinsight.exporter.TaskDurationCache#getAccumulatedDurationMillis(com.syy.taskflowinsight.model.TaskNode)
```

统一 metadata：

| 字段 | 值 |
|------|----|
| `change` | `REMOVED` |
| `replacement` | `null` |
| `ownerTask` | `TASK-EXP-08` |
| `compatibilityTest` | `com.syy.taskflowinsight.architecture.TaskTreeGateArchitectureTests#legacyDurationCacheIsRemovedAndFormattersRemainSnapshotOnly` |
| `approvedBy` | `ADR-005` |
| `evidence` | `JAPICMP`, `CONSUMER_COMPILE` |

class entry 使用 `kind=CLASS`，两个 method entries 使用 `kind=METHOD`；`japicmpExclusion` 与各自 symbol 完全相同。

### 关键决策

| 决策点 | 选择 | 理由 | 明确拒绝 |
|--------|------|------|----------|
| cache 处置 | EXP-08 内 4.0 exact removal | accepted G1、零 production consumer、消除唯一 mutable owner | retain/deprecate/defer |
| breaking 数量 | 3 | `3.0.0` `javap` 的真实 public surface | 把未发布 `getSelfDurationMillis` 登记成第 4 项 |
| replacement | `null` | 无 bare `TaskNode` drop-in replacement | 伪造签名不等价替代 |
| architecture owner | 扩展现有 `TaskTreeGateArchitectureTests` | 避免第二套边界规则 | 新建平行 architecture class |
| deep-tree 深度 | 1000 + 第 1001 层截断 | framework hard limit | 非法 10,000-depth、提高 `-Xss` |
| formatter production | 0 修改 | 当前已经 snapshot-only/iterative | 为制造 diff 重写 renderer |

### 失败语义

| 场景 | 结果 |
|------|------|
| baseline/manifest symbol 集不等于 3 | focused manifest gate 失败，停止删除 |
| manifest 与 POM exclusion 不一一对应 | API/manifest gate 失败 |
| cache source/test 或 Java consumer 残留 | architecture gate 失败 |
| 任一 current public route 在合法深树溢出/超时/越界泄漏 | deep-tree gate 失败 |
| consumer 因删除无法编译 | 七项目 package gate 失败，不得宣称无消费者 |
| 任一门禁失败 | source/test + manifest/POM + tests/docs 整体回滚，不留混合态 |

本卡不包含运行时控制循环、配置、队列、数据库或外部依赖；可观测证据全部位于 build-time tests、API diff 与 consumer compile。

## 二、执行（已完成）

### 文件与所有权

| 动作 | 精确路径 | 责任 |
|------|----------|------|
| 删除 | `src/main/java/com/syy/taskflowinsight/exporter/TaskDurationCache.java` | 删除 mutable-tree exception |
| 删除 | `src/test/java/com/syy/taskflowinsight/exporter/TaskDurationCacheTest.java` | 删除已取消 API 的 owner test |
| 修改 | `src/test/resources/compatibility/breaking-changes-v4.json` | 3 个 exact removal entries |
| 修改 | `pom.xml` | 3 个 exact japicmp exclusions |
| 修改测试 | `src/test/java/com/syy/taskflowinsight/compatibility/BreakingChangeManifestTests.java` | expected exact set + null replacements |
| 修改测试 | `src/test/java/com/syy/taskflowinsight/architecture/TaskTreeGateArchitectureTests.java` | source/reference absence + existing owners |
| 新增测试 | `src/test/java/com/syy/taskflowinsight/exporter/ExportSnapshotDeepTreeTests.java` | 1000-depth current public routes |
| 修改文档 | `docs/product/architecture/README.md`、本卡、`INDEX.md` 与 downstream owner cards | direct-removal current route |

`TaskDurationCacheTest` 中 JSON canonical nanos 与 Console task output 两条断言已由各自 exporter owner tests 覆盖，
不迁移到新测试类。

### TDD 批次

1. **RED-A ownership**：新增 cache source/reference absence 断言，确认因 source 仍存在而失败。
2. **RED-B classification**：expected manifest set 加入 3 symbols，确认因 manifest/POM 尚未登记而失败。
3. **GREEN-A removal**：删除 cache source/test；不修改 formatter production。
4. **GREEN-B classification**：增加 3 manifest entries/exclusions，使 manifest 与 API profile 同时通过。
5. **DEEP**：新增 1000-depth + truncation public-route test；若当前 production 已通过，保持 production diff 为删除-only。
6. **REGRESSION**：运行 snapshot/Console/Map/JSON、Core、API、owner downstream 与七项目 package。
7. **REVIEW**：按 API/architecture/Javadoc/test 视角关闭 MUST/SHOULD，再回填本卡和 `INDEX.md`。

### Deep-tree 精确矩阵

同一合法 chain 依次验证：

| Route | 必须验证 |
|-------|----------|
| `SessionExportSnapshot.capture` | `truncated=true`，最大可见深度 1000 |
| Console TREE | 深度 1000 可见、1001 不可见、truncation marker 存在 |
| Console SIMPLE | 同上 |
| `MapExporter.export` | canonical V2 truncation field 与 visible depth 精确 |
| `JsonExporter.export` | parser tree 与 Map 相同 truncation/depth |
| `JsonExporter.export(session, writer)` | 独立捕获后仍满足相同 schema/depth/truncation；不比较 capture timestamp |

使用非抢占式 JUnit timeout，不切换 Session 所在线程，不使用 package-private V1 route。

### 验收命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=AdrDecisionContractTests,BreakingChangeManifestTests,TaskTreeGateArchitectureTests,ExportSnapshotDeepTreeTests test
./mvnw -pl tfi-flow-core \
  -Dtest=SessionExportSnapshotTests,SessionSnapshotCapturerTests,CanonicalExportV2ProjectionTests,ConsoleExporterTest,ConsoleExporterOptionsTests,MapExporterTest,JsonExporterTest,ExportV1GoldenTests,ExportV2ContractTests test
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-all -am \
  -Dtest=ConsoleExporterTest,ConsoleExporterTests,ConsoleExporterAdditionalCoverageTest,ConsoleExporterCustomLabelTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：baseline `javap` 与 manifest/POM 新增集合精确为 3，`getSelfDurationMillis` 不在 breaking inventory。
- [x] CP-2：cache source/test 和 Java consumer 为 0；manifest/docs 历史文本不被误判为 consumer。
- [x] CP-3：现有 AST gate 继续锁定唯一 capturer/projection、formatter 无 mutable traversal/递归。
- [x] CP-4：1000-depth 六条 current public routes bounded 完成，第 1001 层统一截断。
- [x] CP-5：三个 entries/exclusions exact 一一对应，无 wildcard/orphan，replacement 均为 null。
- [x] CP-6：V2/schema/Console/capture/API/downstream gates 全部通过，production formatter diff 为 0。
- [x] CP-7：EXP-10 取消、后继依赖与 INDEX 状态没有 E7/maturity 残留歧义。

### 回滚边界

发布前失败时整体恢复两个 cache 文件，删除 3 个 manifest entries/POM exclusions/expected symbols，回退 removal/deep
assertions 与相关 current-state 文档。不得只恢复 source 而保留 breaking 授权，也不得删除授权以绕过 API gate。

## 三、自省（设计与实施完成）

- [x] **无歧义**：cache 在 4.0 只有“由 EXP-08 删除”一种状态；EXP-10 明确取消。
- [x] **目标偏离**：只消除最后一个 mutable export owner，不修改 schema/renderer/facade。
- [x] **认知负担**：复用已有 architecture owner，只新增一个行为型 deep-tree suite。
- [x] **比例失调**：主要设计篇幅用于 exact ABI、ownership 与合法边界。
- [x] **ROI**：删除无生产消费者的 API/source/test，并减少未来维护面。
- [x] **洁癖检测**：不重写已满足门禁的 formatter，不为“统一命名”改无关代码。
- [x] **局部与全局**：EXP-09 接管 current module docs，DOC-01 仍拥有 Portfolio root gate。
- [x] **过度设计**：无 adapter、factory、registry、pipeline、runtime flag 或第二 manifest。

**架构防卫自检**：无共享可变 Context、无新生产抽象、无 catch-all、无基础设施复制；删除 batch 是固定的
source -> classification -> verification 顺序。

**结论**：设计与实施均按 `.execution/EXP-08-implementation-plan.md` 完成。cache source/test、三项 breaking
classification、六 public-route deep-tree gate 与全链路验证均已闭环；未修改六个 formatter/snapshot production 文件。

## 四、反馈（实施时回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| breaking inventory | class + baseline 两 methods，共 3 项 | 精确 3 项；self-duration 未登记 | 无偏差 |
| production 范围 | 只删除 cache；formatter/snapshot 0 修改 | cache source/test 删除；六个保护 SHA 6/6 一致 | 无偏差 |
| deep-tree | 合法 depth 1000，六 public routes，第 1001 层截断 | 1/1；同线程非抢占式完成，JSON 只比较摘要 | 无偏差 |

### 检查点结果

- [x] CP-1：本地 `3.0.0` `javap` 只有 class、`from(TaskNode)`、`getAccumulatedDurationMillis(TaskNode)`；jq 为 3 行。
- [x] CP-2：两个文件 absent；Java type/member consumer 搜索零匹配。
- [x] CP-3：`TaskTreeGateArchitectureTests` 13/13；唯一 capturer/projection 与 formatter snapshot-only 断言通过。
- [x] CP-4：`ExportSnapshotDeepTreeTests` 1/1；depth 1000 可见、`level-1001` 不可见、六 route 均截断。
- [x] CP-5：`BreakingChangeManifestTests` 15/15；三项均 `REMOVED/null/ADR-005` 且 POM exact 对账。
- [x] CP-6：focused 33/33、Export owner 95/95、Core 707/707、API、tfi-all 43/43、consumer 7/7。
- [x] CP-7：EXP-10 保持永久取消；EXP-09 直接消费本卡，INDEX 无 E7/ACTIVE maturity 依赖。

## 五、总结

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | baseline/manifest/POM 精确 3 项；self-duration 负向检查；API profile 通过 |
| 完整性 | 25/25 | 六 public routes、95 owner regression、707 Core、43 downstream、7/7 consumer |
| 可维护性 | 25/25 | 删除无消费者 API；复用现有 architecture owner；无 replacement/第二 traversal 抽象 |
| 风险控制 | 25/25 | RED->GREEN、null replacement、双向对账、六 production SHA、五计划 SHA |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| - | - | 未发现 MUST/SHOULD；既有大 owner test 不拆分，避免制造第二 owner | architecture/compatibility/deep-tree batch | 无需修改 |

## 六、完成审核

**审核通过。** cache source/test 已删除，manifest/POM 精确登记 baseline 三项，architecture/deep-tree gate 与
focused 33/33、Export owner 95/95、Core 707/707、API、tfi-all 43/43、consumer 7/7 均 fresh 通过。
Checkstyle 0、SpotBugs 0/0、JaCoCo 通过；六个 production SHA 与五份保护计划 SHA 全部未变。
Portfolio root gate 未运行且仍归 `TASK-DOC-01`，不影响本卡独立完成审核。
