# TASK-BLD-02：删除 Core Compare Defaults 副本

> **定位**：删除 Core 中零消费者的 Compare 默认值副本，使 Core 与 Compare 各自只有一个默认值 owner。
> **状态**：完成（2026-07-11；48 focused / 707 Core / API / Compare / 7/7；100/100）
> **审核状态**：通过（0 unresolved MUST / SHOULD）
> **依赖**：`TASK-BLD-01`、`TASK-GRD-05`、`TASK-GRD-06`
> **架构来源**：master Task 2 / `B2`、ADR-005与[当前架构 SSOT](../design-doc.md)的配置所有权边界。

---

## 一、核心（设计已完成）

### 背景

Core `com.syy.taskflowinsight.internal.ConfigDefaults` 是 Compare
`com.syy.taskflowinsight.config.resolver.ConfigDefaults` 的历史复制品；六个生产模块对 Core FQCN 的引用为 0，
Compare 自有 class 则有真实生产消费者。Core 当前有效默认值只来自 `FlowConfigDefaults`，继续保留复制品只会发布
第二套无 owner 的默认值表面。

旧卡要求 3.x ledger `REMOVED`，与 accepted G1 `BREAKING_MAJOR_4_DIRECT_REMOVAL` 冲突。本卡固定使用唯一
`breaking-changes-v4.json`：新增 outer/nested 两个 exact CLASS entries，不创建 ledger，不使用 `internal.*`。

### 唯一完成态

- Core 只发布 `FlowConfigDefaults` 的 5 个 flow-owned constants。
- Compare 只由 `config.resolver.ConfigDefaults` 拥有 compare/tracking defaults；该 source 与行为不改。
- Core `internal.ConfigDefaults` 和 `internal.ConfigDefaults$Keys` 均不存在。
- 不保留 deprecated alias、facade、转发类、共享 defaults 或 replacement API。

### 范围

**In scope：**

- 删除 Core `ConfigDefaults.java` 与 `ConfigDefaultsTest.java`。
- 迁移仍编译依赖旧 class 的 Core owner/removal tests。
- breaking manifest 与 Core api-compat POM 各增加两个 exact CLASS symbols。
- 让 CLASS removal 可携带专用 `PUBLIC_CONSTANT_MANIFEST` evidence，同时不放宽 METHOD/FIELD/schema/resource 规则。
- 保留 79 行历史 public constant manifest，由两个 class policy 精确消费其中 74 行。
- 更新 Core package/current docs、本卡、`INDEX.md` 与 `.execution/*`。

**Out of scope：**

- 不修改 `FlowConfigDefaults` 的名称、类型、值或生产消费者。
- 不修改 Compare `ConfigDefaults`、配置键、resolver、默认行为或依赖方向。
- 不修改 accepted ADR、受保护 plans、CI、schema、Provider/Context/Export 行为或 TST-01 的 11 个测试文件。
- 不运行或宣称 Portfolio 最终 root gate；TST-01/DOC-01 继续拥有该门禁。

### 目标（Definition of Done）

- [x] Core source、clean class output 和 public API 中两个旧 class symbols 均不存在。
- [x] 六个生产模块对 Core `internal.ConfigDefaults` FQCN 的引用为 0。
- [x] `FlowConfigDefaults` public fields 精确为 5 个，名称、类型和值不变。
- [x] Compare `ConfigDefaults.java` SHA-256 保持
  `be458fd95f0ad75dd01fa1e61511a1f76f749e3bd9de231c8d3bcc48ff3d9182`，Compare tests 通过。
- [x] breaking manifest 只新增两个 `TASK-BLD-02` CLASS entries，总数 55 -> 57。
- [x] Core POM 只新增两个 exact class exclusions，无 wildcard/orphan。
- [x] 两个 entries 均为 `replacement=null`，evidence 精确为
  `JAPICMP/PUBLIC_CONSTANT_MANIFEST/CONSUMER_COMPILE`。
- [x] `public-constants.properties` 保持 79 行，SHA-256 保持
  `b88ec3f4b57a26ea8ea188656afb0f0020c7d38194211bf48265dc7076e0dd06`；当前 Core 只要求 5 个 Flow constants。
- [x] CTX-05 的四个 Core FIELD entries/exclusions 保持原 owner 和内容。
- [x] focused、Core clean verify、Core API、Compare tests 与七模块 consumer package 全部通过。
- [x] 当前 package/design/ops 文档不再宣称 Core 保留 compatibility alias。
- [x] 本卡完成回填并同步 `INDEX.md`；下一张唯一变为 `TASK-TST-01`。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 模块所有权 | 高 | Core/Compare 各自只有一个 defaults owner |
| 精确删除证据 | 高 | 两个 class 同时满足 ABI、常量与 consumer compile |
| 历史证据保留 | 高 | 79 行 constant manifest 和 CTX-05 FIELD entries 不得被抹掉 |
| 运行时行为 | 低 | 本卡应为零行为迁移，Compare/Flow production 不改 |

### 关键决策

| 决策点 | 唯一选择 | 理由 | 明确否决 |
|--------|----------|------|----------|
| Core 副本 | 4.0 exact direct removal | 零生产消费者；G1 已接受 | deprecated alias / 延后删除 |
| 默认值 owner | Core `FlowConfigDefaults`；Compare 自有 `ConfigDefaults` | 领域边界与依赖方向明确 | 跨模块共享巨型 defaults |
| breaking 粒度 | outer + nested 两个 CLASS | ADR-005/GRD-05 明确要求 | `internal.*` 或 74 个重复 FIELD entries |
| 常量历史 | 79 行文件原样保留 | 这是 baseline 证据，不是当前 surface | 删除 74 行后重置基线 |
| replacement | `null` | 两个现有 owner 均不与旧 Core FQCN 语义等价 | 伪造迁移入口 |
| CTX-05 entries | 原样保留 | 记录先发生的四个字段删除 | 改 owner 或合并进 class entry |

### 精确 Breaking Inventory

```text
com.syy.taskflowinsight.internal.ConfigDefaults
com.syy.taskflowinsight.internal.ConfigDefaults$Keys
```

两个 entry 均固定：`kind=CLASS`、`change=REMOVED`、`replacement=null`、
`ownerTask=TASK-BLD-02`、`approvedBy=ADR-005`，`japicmpExclusion` 与 symbol 相同；
`compatibilityTest` 指向
`FlowCoreArchitectureBoundaryTest#corePublishesOnlyFlowOwnedDefaults`。

### 跨卡不变量

- 只删除上述两个 Core class symbols；不改变 Flow/Compare 默认值。
- 现有 55 个 manifest entries 和 POM exclusions 的 owner/reason 不被本卡重写。
- 不创建 `deprecations.json`、`DeprecationLedgerTests` 或任何第二 policy。
- 若实施期间出现新的真实生产消费者，整卡停止，不引入 adapter。

## 二、执行（设计已完成）

### 前置准备

1. 验证 Compare `ConfigDefaults.java` SHA-256 为
   `be458fd95f0ad75dd01fa1e61511a1f76f749e3bd9de231c8d3bcc48ff3d9182`，
   `public-constants.properties` SHA-256 为
   `b88ec3f4b57a26ea8ea188656afb0f0020c7d38194211bf48265dc7076e0dd06`；任一不符即停止。
2. fresh baseline：Core 32 + Compare 20 = 52/52 focused tests 通过。
3. 结构事实：Core FQCN 生产引用 0；constant manifest 49 outer + 25 Keys + 5 Flow = 79 行；
   breaking manifest 当前 55 entries。

### 文件与职责

| 动作 | 精确路径 | 职责 |
|------|----------|------|
| 删除 | `src/main/java/com/syy/taskflowinsight/internal/ConfigDefaults.java` | 删除 Core 复制品 |
| 删除 | `src/test/java/com/syy/taskflowinsight/internal/ConfigDefaultsTest.java` | 删除复制品 owner test |
| 修改 | `src/test/java/com/syy/taskflowinsight/architecture/FlowCoreArchitectureBoundaryTest.java` | source/class absence + 5-field owner |
| 修改 | `src/test/java/com/syy/taskflowinsight/internal/FlowConfigDefaultsTest.java` | 精确 Core fields，不反射旧 class |
| 修改 | `src/test/java/com/syy/taskflowinsight/context/NestedDepthRemovalTests.java` | 保留 CTX-05 语义，移除 B2 class 编译依赖 |
| 修改 | `src/test/java/com/syy/taskflowinsight/compatibility/BreakingChangeManifestTests.java` | exact-two CLASS 与 evidence 闭集 |
| 修改 | `src/test/java/com/syy/taskflowinsight/compatibility/PublicConstantCompatibilityTests.java` | exact-two holder policy 与 5 current fields |
| 修改 | `src/test/resources/compatibility/breaking-changes-v4.json` | 两个 removal entries |
| 只读 | `src/test/resources/compatibility/public-constants.properties` | 79 行历史证据必须不变 |
| 修改 | `pom.xml` | 两个 exact japicmp exclusions |
| 修改文档 | `internal/package-info.java`、`docs/design-doc.md`、`docs/ops-doc.md` | 当前 owner 事实 |
| 回填 | 本卡、`INDEX.md`、`.execution/*` | 状态、证据、审查与下一张 |

### 固定实施顺序

1. RED ownership：增加旧 class/source absence 与 5-field owner 断言。
2. RED compatibility：expected manifest set 增加两个 class，确认 manifest/POM 缺口。
3. GREEN source：删除 Core source/test，迁移两个依赖旧 class 的 Core tests；Compare production 0 修改。
4. GREEN evidence：登记两个 manifest/POM symbols；只拆分 CLASS/METHOD evidence validation。
5. FOCUSED：ownership、constant、manifest、nested-depth、Compare resolver 全绿。
6. REGRESSION：Core clean verify、Core API、Compare full tests、七模块 consumer package。
7. REVIEW：API/constant/module/test/doc 五视角关闭 MUST/SHOULD，再完成两次任务卡回填。

### CLASS constant evidence 规则

- CLASS：必须 `JAPICMP + CONSUMER_COMPILE`；允许可选 `PUBLIC_CONSTANT_MANIFEST`。
- METHOD：规则不变，禁止 `PUBLIC_CONSTANT_MANIFEST`。
- FIELD：规则不变，必须 `PUBLIC_CONSTANT_MANIFEST`，removal 还需 ABI evidence。
- SCHEMA/RESOURCE：规则不变，禁止 ABI exclusion。

不得以 BLD-02 为理由放宽 exact symbol、owner task、compatibility test、POM 双向对账或 evidence allowed set。

### 验收命令

```bash
./mvnw -pl tfi-flow-core clean test \
  -Dtest=FlowCoreArchitectureBoundaryTest,FlowConfigDefaultsTest,NestedDepthRemovalTests,\
BreakingChangeManifestTests,PublicConstantCompatibilityTests

./mvnw -pl tfi-flow-core,tfi-compare -am test \
  -Dtest=FlowCoreArchitectureBoundaryTest,FlowConfigDefaultsTest,NestedDepthRemovalTests,\
BreakingChangeManifestTests,PublicConstantCompatibilityTests,ConfigResolverTests \
  -Dsurefire.failIfNoSpecifiedTests=false

./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-compare -am test
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：Core old source/class/FQCN consumer 精确为 0；Flow fields 精确为 5。
- [x] CP-2：BLD-02 manifest/POM 新增集合精确为两个 CLASS symbols，无 wildcard/orphan。
- [x] CP-3：79 行 constant manifest SHA 不变，74 个历史 fields 由 exact-two class policy 消费。
- [x] CP-4：CTX-05 四个 FIELD entries/exclusions 内容、owner、理由不变。
- [x] CP-5：Compare source SHA 不变，真实 owner consumers 和 tests 全绿。
- [x] CP-6：CLASS/METHOD/FIELD evidence 边界未被放宽，受控测试通过。
- [x] CP-7：当前文档、任务卡、INDEX 与 execution memory 只表达一个完成态和一个下一张。

### 失败与回滚边界

任一 consumer、manifest/POM、constant、API 或 Compare gate 失败时，source/test、两个 entries/exclusions、合同测试和
current docs 整体回滚。禁止只恢复 source 而保留 breaking 授权，也禁止删除历史常量或削弱测试来恢复绿色。

## 三、自省（设计完成，实施前）

- [x] **无歧义**：Core/Compare owner、两个删除 symbols、null replacement 和历史证据处理均唯一。
- [x] **目标偏离**：只删除 Core 复制品，不改变任何默认值或运行时路径。
- [x] **认知负担**：减少一个 outer class、一个 nested class 和一个重复测试 owner。
- [x] **比例失调**：主要篇幅用于 module ownership、constant evidence 与 exact compatibility。
- [x] **ROI**：零消费者 public 副本被删除，后续不再同步两份近相同文件。
- [x] **洁癖检测**：不顺带重命名 Compare 配置、整理全部常量或重写广义文档。
- [x] **局部 vs 全局**：保留 CTX-05 历史 entry；TST-01/DOC-01 职责不被抢占。
- [x] **过度设计**：无 alias、facade、factory、interface、pipeline、flag 或新 policy。

**架构防卫自检**：无共享可变 Context、无新抽象、无 catch-all、无基础设施复制；Core 依赖方向不变。

**质量门槛**：合约、失败覆盖、机器验收、范围、架构与决策追溯均已明确。已知不足只有两项且 owner 唯一：
Portfolio 11 个测试文件归 TST-01/DOC-01；本地 3.0 artifact 外部不可重解析风险沿用用户豁免。

**结论**：实施与复审均完成；Core/Compare defaults owner、兼容证据和后续任务均只有一个解释。

## 四、反馈（实施过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 无 | 计划与实际一致 | 计划与实际一致 | - |

### 检查点结果

- [x] CP-1：旧 source/test、clean outer/nested class 均不存在，六模块生产 FQCN 搜索为 0；
  `FlowCoreArchitectureBoundaryTest.java:85` 与 `FlowConfigDefaultsTest.java:22` 锁定 5-field owner。
- [x] CP-2：`breaking-changes-v4.json:633`/`:645` 和 `pom.xml:323`/`:324` 仅登记两个 exact CLASS symbols；
  repository manifest/POM 双向门禁通过，计数 57/57。
- [x] CP-3：常量账为 outer 49 + nested 25 + Flow 5 = 79；SHA-256 为
  `b88ec3f4b57a26ea8ea188656afb0f0020c7d38194211bf48265dc7076e0dd06`；
  `PublicConstantCompatibilityTests.java:41` 的 exact-two class policy 消费 74 个历史 fields。
- [x] CP-4：CTX-05 四个 FIELD entries/exclusions 的 canonical JSON SHA-256 保持
  `22da1b24a1f68d992c74e3300fd143ce3c99fc9be127a8174cc91c5aba054047`。
- [x] CP-5：`tfi-compare/.../ConfigDefaults.java:12` 仍有真实 production consumers，source SHA-256 保持
  `be458fd95f0ad75dd01fa1e61511a1f76f749e3bd9de231c8d3bcc48ff3d9182`；Compare reactor 3/3、模块
  3588 tests 为 0 failure/error。
- [x] CP-6：`BreakingChangeManifestTests.java:336` 只允许 CLASS 增加常量证据，`:513` 的 kind 分支保持
  METHOD/FIELD/SCHEMA/RESOURCE 闭集；16 manifest + 4 constant tests 全绿。
- [x] CP-7：`internal/package-info.java:4`、`design-doc.md:596`、`ops-doc.md:191`、本卡、`INDEX.md` 与
  `.execution/*` 均声明 BLD-02 完成，唯一下一张为 TST-01。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | RED 精确命中旧 source 与缺失 compatibility policy；全部 GREEN 门禁通过 |
| 完整性 | 25/25 | 12/12 DoD、7/7 CP、48 focused、707 Core、API、Compare、7/7 全部关闭 |
| 可维护性 | 25/25 | 删除 outer/nested 重复 owner 与重复测试，不新增 alias/facade/第二 policy |
| 风险控制 | 25/25 | exact manifest/POM、79/74/5 账、三组 SHA、clean class 与消费者门禁均 fail-closed |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| 通过 | BLD02-R0 | API、constant、module、test、doc 五视角审查；范围为 Tasks 1-3 File Map 全集 | 本卡 CP-1..7 | 0 unresolved MUST / SHOULD |

## 六、完成审核

**审核结论：通过。** ownership/compatibility focused 为 Core 28 + Compare 20 = 48/48；Core
`clean verify` 为 707/707，JaCoCo、SpotBugs、Checkstyle 均通过；Core API profile、Compare 全量测试与七模块
consumer package 全部成功。Compare/constant/CTX-05 三组 SHA 与 57/57 manifest/POM 计数保持精确，受保护计划
指纹 5/5 一致，复审为 `0 unresolved MUST / SHOULD`。

Portfolio root gate remains assigned to TST-01/DOC-01. The last fresh diagnostic was
2976 tests / 9 failures / 10 errors in tfi-all, mapped to 11 exact test files.
BLD-02 did not restore any obsolete behavior to make that gate green.
