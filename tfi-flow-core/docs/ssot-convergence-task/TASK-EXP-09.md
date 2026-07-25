# TASK-EXP-09：发布 Export 当前 SSOT 与全链路证据

> **定位**：把 EXP-01..08 的实际代码事实发布到模块 current docs，并关闭 Export wave 的文档与消费者门禁。
> **状态**：完成（2026-07-11；100/100）
> **审核状态**：审核通过（2026-07-11；0 unresolved MUST / SHOULD）
> **依赖**：`TASK-EXP-08` 完成并通过 Code Review；后续 `TASK-BLD-01/02`、`TASK-TST-01`、`TASK-DOC-01`
> **架构来源**：ADR-005、ADR-008、EXP-01..08 完成证据、
> `docs/superpowers/specs/2026-07-11-exp-09-export-ssot-convergence-design.md`

---

## 一、核心

### 背景

生产实现已经只有一条 Export 路线，但四份模块文档仍保留 JSON `COMPAT/ENHANCED`、递归 Map、
`TaskDurationCache` formatter owner、V1 顶层 Map 示例和手写测试/覆盖率快照。继续只追加新段落会让同一文件
同时给出旧、新两种答案，因此本卡必须原位删除冲突表述，而不是再建平行文档。

受保护 3.x 源计划曾要求创建 `tfi-flow-core/docs/deprecation-policy.md`，但该文件从未存在，其前置
`deprecations.json`/ACTIVE/E7 路线已被 ADR-005 的 4.0 direct removal 否决。为保持单一政策 owner，本卡不创建
该文件：ADR-005 唯一决定政策，breaking manifest 唯一拥有 exact symbols，PRD/index 只提供迁移方向与链接。

### 目标（DoD）

- [x] `design-doc.md` 精确描述 gate、capturer、snapshot、projection、Console/Map/JSON 的当前 ownership 与失败边界。
- [x] `prd.md` 固定 Console non-schema、Map/JSON canonical V2-only、预算/截断、facade fallback 与迁移方向。
- [x] `test-plan.md` 以不变量和可复制命令替换旧 exporter 用例数、JSON modes、cache test 与 V1 Map 断言。
- [x] `index.md` 成为模块文档入口，不保存专家评分、手写测试/覆盖率/report/source-count 快照。
- [x] ADR-008 decision lines 逐字不变，只追加 EXP-01..09 implementation/verification evidence。
- [x] `deprecation-policy.md`、deprecation ledger/test 保持不存在；TASK-DOC-01 不再把该失效路径列为输入。
- [x] 文档没有 Console V1/V2 schema、Map/JSON runtime V1、cache ACTIVE/E7 maturity 现在时叙述。
- [x] Export focused、Core、API、owner downstream 与包含 examples 的 consumer package 全部通过。
- [x] `INDEX.md` 将唯一下一张更新为 `BLD-01`，但不运行或宣称 DOC-01 Portfolio root gate。

### 单一所有权

| 事实 | 唯一 owner |
|------|------------|
| 4.0 兼容与删除政策 | `docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md` |
| Export snapshot/schema decision | `docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md` |
| exact breaking symbols | `src/test/resources/compatibility/breaking-changes-v4.json` |
| exact japicmp exclusions | `pom.xml`，由 manifest contract 双向校验 |
| 当前 Export 架构 | `docs/design-doc.md` |
| 用户合同与迁移方向 | `docs/prd.md` |
| 验收不变量与命令 | `docs/test-plan.md` |
| 模块文档导航 | `docs/index.md` |

### 不可改变的契约

- JSON/Map 只发布 canonical V2；不存在 runtime V1 route、V1 ledger 或第二 schema tree。
- Console 是 snapshot-only 人类诊断文本；style 只由 `ConsoleStyle` 决定，`showTimestamp` 只控制时间戳。
- mutation 使用 Session-owned gate read side；capture 使用公平 write side，取得锁后才采样两种 clock。
- `SessionSnapshotCapturer` 是唯一 mutable-tree Export reader；`CanonicalExportV2Projection` 是唯一 schema builder。
- 默认预算精确为 depth 1000、nodes 100000、payload entries 1000000、UTF-16 text chars 10000000。
- `TaskDurationCache` 完成态是 source/test absent + 三个 exact manifest entries/exclusions；不存在 ACTIVE ledger 或 E7。
- package-private gate/capturer/projection 不进入 manifest/japicmp exclusion。
- 长期文档不手写测试总数、coverage percentage、CI report count 或完成评分。

## 二、执行边界

### 文件责任

| 动作 | 文件 | 责任 |
|------|------|------|
| 修改 | `tfi-flow-core/docs/design-doc.md` | 当前 architecture/data flow/ownership/failure semantics |
| 修改 | `tfi-flow-core/docs/prd.md` | 4.0 public Export contract 与 migration direction |
| 修改 | `tfi-flow-core/docs/test-plan.md` | machine-verifiable acceptance 与可复制 gates |
| 修改 | `tfi-flow-core/docs/index.md` | 当前模块文档入口与 SSOT links |
| 修改 | `docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md` | append-only implementation evidence |
| 修改 | `TASK-DOC-01.md` | 删除不存在的 policy path，保留 Portfolio owner 边界 |
| 回填 | 本卡与 `INDEX.md` | 设计/实施/审核/fresh evidence 状态 |

**明确不创建：**`tfi-flow-core/docs/deprecation-policy.md`、任何 migration/policy 新文件、第二 ADR、第二 manifest。

**明确不修改：**production/test Java、POM、breaking manifest、CI workflow、五份受保护 source plans、
`docs/product/architecture/**` 历史背景树。

### 当前实现事实

```text
Session / TaskNode mutation --read lock-->
    package-private TaskTreeMutationGate
SessionExportSnapshot.capture --write lock-->
    package-private SessionSnapshotCapturer
        -> immutable SessionExportSnapshot
             |-> Console TREE/SIMPLE diagnostic text
             `-> package-private CanonicalExportV2Projection
                    |-> Map canonical V2
                    `-> JSON canonical V2 encoding
```

- capture 最多等待 write lock 30 秒，超时/中断/失败都释放 gate；中断状态恢复。
- depth/node 超限发布 truncation；payload/text 超限在 projection/output 前原子失败。
- Console 先形成完整 String 再写 sink；Map 不暴露 partial projection；JSON Writer 在 capture/projection 后首次写入。
- direct exporter 与 `TfiFlow` facade 的异常边界必须分别写，禁止泛化为“所有 Exporter 捕获异常返回空结果”。
- cache exact removal 数量只从 manifest 读取；模块文档不复制 symbol 清单。

### 文档 RED / GREEN

实施前先用精确搜索证明旧表述存在，形成文档 RED：

```bash
rg -n 'COMPAT|ENHANCED|TaskDurationCache|TaskDurationCacheTest|map.get\("sessionName"\)|递归转换任务树|exportInternal' \
  tfi-flow-core/docs/design-doc.md tfi-flow-core/docs/prd.md \
  tfi-flow-core/docs/test-plan.md tfi-flow-core/docs/index.md
rg -n '测试用例总数|测试通过率|指令覆盖率|分支覆盖率|个用例|454' \
  tfi-flow-core/docs/test-plan.md tfi-flow-core/docs/index.md
```

GREEN 不是简单“零匹配”：历史 V1 characterization、cache removal migration 与 exact negative assertions 可以出现，
但必须带明确历史/absence 语义；禁止 runtime/current/future-maturity 解释。实施计划必须为允许的历史引用使用
精确上下文检查，不能用宽泛关键词扫描误杀正确说明。

### Fresh 验收命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=AdrDecisionContractTests,BreakingChangeManifestTests,TaskTreeGateArchitectureTests test

./mvnw -pl tfi-flow-core \
  -Dtest=TaskTreeMutationGateTests,TaskTreeCaptureConcurrencyTests,SessionExportSnapshotTests,SessionSnapshotCapturerTests,CanonicalExportV2ProjectionTests,ConsoleExporterTest,ConsoleExporterOptionsTests,MapExporterTest,JsonExporterTest,ExportV1GoldenTests,ExportV2ContractTests,ExportSnapshotDeepTreeTests test

./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests

./mvnw -pl tfi-all -am \
  -Dtest=ConsoleExporterTest,ConsoleExporterTests,ConsoleExporterAdditionalCoverageTest,ConsoleExporterCustomLabelTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

禁止运行 `./mvnw clean verify` repository Portfolio root gate；该命令只由 `TASK-DOC-01` 在 BLD/TST 前置完成后执行。

### 审核检查点

- [x] CP-1：四份模块文档每个 current Export 声明都能映射到 source、test、ADR 或 manifest owner。
- [x] CP-2：ADR-008 三个 decision lines 与实施前逐字一致，新增内容仅为 evidence。
- [x] CP-3：无 policy/migration 新文件；ADR-005、ADR-008、manifest 的 ownership 没有复制。
- [x] CP-4：Console、V2 schema、预算、失败、fallback 与 cache absence 没有第二种解释。
- [x] CP-5：test/index 无手写 current quality totals；relative links、placeholder、trailing whitespace 检查通过。
- [x] CP-6：focused、Core、API、owner downstream、consumer package 与五份 protected SHA 全部通过。
- [x] CP-7：INDEX 下一张为 BLD-01，DOC-01 Portfolio gate 仍未宣称执行。

### 回滚边界

本卡为单个文档原子批次。任一 owner test、API、Core、downstream 或 lint 失败时，整体恢复四份模块文档、
ADR evidence 与任务状态；禁止留下部分 V1/部分 V2 文档。不得通过恢复 runtime V1、cache、ledger，修改 ADR token、
扩大 manifest/POM exclusion 或运行 Portfolio gate 来制造绿色。

## 三、自省

- [x] **无歧义**：ADR-005 管政策、ADR-008 管 Export decision、manifest 管 exact symbols；没有派生 policy 文件。
- [x] **目标偏离**：只修改 current docs/evidence，不修改生产行为、测试行为、POM、manifest 或 CI。
- [x] **认知负担**：四份模块文档各有单一责任，index 只导航，不复制正文。
- [x] **局部与全局**：EXP-09 关闭 Export wave；全模块架构、CI 与 Portfolio root gate 仍归 DOC-01。
- [x] **过度设计**：不创建 schema registry、文档生成器、第二 ADR、第二 manifest 或 migration 文档树。
- [x] **可验证性**：每个 current 声明都有 owner gate；数字证据保留在任务卡/CI，不写入长期模块文档。

**结论**：设计与实施已按 `.execution/EXP-09-implementation-plan.md` 完成。四份 current module docs、ADR-008
evidence、唯一 policy/schema/removal owner 与全链路局部门禁均已闭环；未创建第二 policy/migration 文档，也未修改
production/test Java、POM、manifest、CI 或受保护源计划。

## 四、反馈

### 偏差记录

| 偏差点 | 设计 | 实际 | 原因 |
|--------|------|------|------|
| 受保护 3.x policy 路径 | 创建 `deprecation-policy.md` | 文件继续不存在 | ADR-005 已接受 4.0 direct removal；创建同名文件会产生第二政策 owner |
| 文档批次 | 原位删除冲突并按 owner 重写 | 四份模块文档与 ADR evidence 一次收敛 | 无偏差 |
| 审查修正 | 按八类风险审查 | 关闭 1 MUST + 4 SHOULD | 消除状态、锁语义、JSON mode、exact inventory 与链接表达歧义 |
| 运行范围 | Core/API/owner/downstream 局部门禁 | 全部执行；Portfolio root gate 未执行 | Portfolio gate 仍只归 `TASK-DOC-01` |

### 检查点结果

- [x] CP-1：design/PRD/test/index 分别只拥有架构、用户合同、验收和导航；current Export 声明均有机器 owner。
- [x] CP-2：ADR-008 `Status`、`G3_STATUS`、`G3_DECISION` 精确 `3/3`，既有 Decision/Consequences/Rollback 未改。
- [x] CP-3：policy/ledger/test 三个废止路径均 absent；exact removal 只由 manifest/POM 双向门禁拥有。
- [x] CP-4：context-aware 搜索未发现 runtime V1、Console schema、JSON mode、cache-current 或 E7 maturity 路线。
- [x] CP-5：SSOT lint `0 ERROR`；相对链接、placeholder、trailing whitespace 与长期质量数字检查通过。
- [x] CP-6：32 contract/architecture、108 Export owner、707 Core、API、43 tfi-all、7/7 consumer 与 SHA 5/5 通过。
- [x] CP-7：任务索引唯一下一张为 `BLD-01`；Portfolio root gate 明确未执行并保留给 `TASK-DOC-01`。

### Fresh 验证

| 门禁 | 结果 |
|------|------|
| ADR/manifest/architecture owners | 32/32，0 failure/error/skipped |
| Export owner regressions | 108/108，0 failure/error/skipped |
| Core `clean verify` | 707/707；Checkstyle 0、SpotBugs 0/0、JaCoCo gate 通过 |
| Core API compatibility | BUILD SUCCESS；japicmp diff/Markdown/XML/HTML 已生成 |
| tfi-all Console owners | Core 24/24；tfi-all 43/43；六模块 reactor SUCCESS |
| Consumer package | 7/7 SUCCESS，明确包含 `tfi-examples` |
| 文档与结构 | SSOT lint 0 ERROR；relative links/placeholder/trailing whitespace 通过 |
| 不变资产 | protected plans SHA 5/5；cache manifest/POM 3/3；self-duration entry 0 |

## 五、总结

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | source/test/ADR/manifest 逐项对照；32 owner gate 与 ADR token 3/3 通过 |
| 完整性 | 25/25 | 四份模块文档、ADR evidence、Core/API/tfi-all/consumer 与任务索引全部闭环 |
| 可维护性 | 25/25 | policy/schema/removal 单一 owner；长期文档删除静态质量快照与 exact inventory 副本 |
| 风险控制 | 25/25 | 108 owner、707 Core、API、7/7 consumer、SHA 5/5；Portfolio gate 未越权运行 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST | DOC-01 | 规格页眉仍写“等待用户确认” | design spec:3 | 改为稳定状态“书面规格已确认” |
| SHOULD | DOC-02 | mutation/capture 30 秒句子主语可能误读 | `design-doc.md:349` | 写明 `readLock.lock()` 无超时与 capture budget 互不复用 |
| SHOULD | DOC-03 | mode constructor 被写成 Map/JSON 共同属性 | `prd.md:120` | 明确只有 JSON 需要该负向约束 |
| SHOULD | DOC-04 | 长期 test plan 点名非 removal symbol | `test-plan.md:188` | 改为规则并把精确判定交还 manifest |
| SHOULD | DOC-05 | 页脚使用不准确的非链接相对路径 | `design-doc.md:650` | 改为可校验的相对 Markdown 链接 |

## 六、完成审核

**审核通过。** 1 个 MUST 与 4 个 SHOULD 已全部关闭，0 unresolved MUST / SHOULD。fresh gates 为
32 contract/architecture、108 Export owner、707 Core、API、Core 24 + tfi-all 43 Console owners 和 7/7 consumer；
SSOT lint `0 ERROR`，受保护计划 SHA `5/5`。通用 lint warning 不涉及未有界 Export 路线：mutation 的无超时语义
用于保全业务终态，capture 仍有 30 秒预算；其余缺题提示属于非运维文档。Portfolio root gate 未运行，仍归
`TASK-DOC-01`。
