# TASK-GRD-06：建立 4.0 精确 Breaking Change Manifest

> **定位**：让 4.0 的每项公共 API、常量、schema 或 JAR 资源破坏都有唯一、精确、可机器验证的授权记录。
> **状态**：完成（原 3.x 弃用成熟度方案已由 G1 否决）
> **审核状态**：审核通过（2026-07-11；focused 19/19、japicmp、七模块 consumer package 复验通过）
> **依赖**：`TASK-GRD-02`、`TASK-GRD-05`、`TASK-GRD-07`、`TASK-GRD-08`、`TASK-GRD-09`
> **架构来源**：ADR-005 `BREAKING_MAJOR_4_DIRECT_REMOVAL`；替代旧 `deprecations.json` 设计

---

## 一、核心（设计时填）

### 背景

G1 已取消 N/N+1 弃用窗口，但 major 版本不能成为无边界删除授权。旧 GRD-06 的
`deprecatedSince/removeNotBefore/ACTIVE/REMOVED` 模型继续存在会暗示 3.x 生命周期仍有效。本卡改为
4.0 单次 breaking manifest：只回答“改了什么、为什么、由谁负责、用什么证据验证”。

### 目标（DoD）

- [x] 创建 `src/test/resources/compatibility/breaking-changes-v4.json`，初始 `entries` 为空。
- [x] 创建 `BreakingChangeManifestTests`，严格拒绝未知字段、未知枚举、重复/模糊 symbol 和缺失证据。
- [x] manifest kind 闭集为 `CLASS/METHOD/FIELD/SCHEMA/RESOURCE`，change 闭集为
  `REMOVED/SIGNATURE_CHANGED/VALUE_CHANGED/REPLACED`。
- [x] JVM ABI entry 与 core `api-compat` profile 的 exact japicmp exclusion 一一对应，双向无孤儿。
- [x] `SCHEMA/RESOURCE` 不得伪造 japicmp exclusion，分别要求 `SCHEMA_CONTRACT/JAR_CONTRACT` 证据。
- [x] `PublicConstantCompatibilityTests` 只从新 manifest 读取精确 FIELD/CLASS breaking 授权，并将
  `RemovalPolicy/readRemovalPolicy` 改名为 `BreakingPolicy/readBreakingPolicy`。
- [x] 每个 entry 必须引用存在的 owner task、可反射定位的测试方法和 `approvedBy=ADR-005`。
- [x] focused tests、严格 API gate 和六模块消费者源码编译全部通过。

### 范围

**In-scope**：breaking manifest schema、严格 parser、japicmp exclusion 对账、公共常量删除授权适配。

**Out-of-scope**：添加任何实际 breaking entry、删除运行时 API、修改 V2 schema、实施 CTX/EXP/PRV 卡。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 精确授权 | 高 | major 版本只允许 manifest 中逐项声明的破坏 |
| 证据闭环 | 高 | ABI、常量、schema、资源使用各自真实门禁 |
| 单一语义 | 高 | 删除 3.x maturity/status 概念，避免双重政策 |

### Manifest 合约

根对象仅允许：`schemaVersion=1`、`baselineVersion=3.0.0`、`targetVersion=4.0.0`、
`policy=BREAKING_MAJOR_4_DIRECT_REMOVAL`、`entries`。

每个 entry 仅允许：`symbol`、`kind`、`change`、`replacement`（可为 null）、`ownerTask`、
`compatibilityTest`、`approvedBy`、`reason`、`japicmpExclusion`（可为 null）、`evidence`。

`evidence` 闭集：`JAPICMP`、`PUBLIC_CONSTANT_MANIFEST`、`SCHEMA_CONTRACT`、`JAR_CONTRACT`、
`CONSUMER_COMPILE`。symbol 与 exclusion 禁止 `*`、`..`、包级前缀或尾随 `.`。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 机器资产 | `breaking-changes-v4.json` | 直接表达 4.0 事实 | 继续复用 3.x deprecation ledger |
| 初始内容 | 空 entries | 不替后续卡预授权删除 | 预填计划中可能删除的 symbol |
| ABI 对账 | manifest ↔ exact POM exclusion | 保持 japicmp 硬失败且允许已声明破坏 | 关闭 japicmp 或包级 exclusion |
| 非 ABI 证据 | 按 kind 使用专门门禁 | japicmp 不理解 wire/resource/常量值 | 所有破坏都伪装成 ABI |

## 二、执行（设计时填）

### 前置准备

- 当前版本精确为 `4.0.0-SNAPSHOT`，ADR-005 与 ADR-008 均为 ACCEPTED。
- GRD-02/05/07 的 API、常量和消费者门禁保持硬失败。

### 核心步骤

1. 新增 `breaking-changes-v4.json`，写入严格根 metadata 与空 `entries`。
2. 新增 `BreakingChangeManifestTests.java`：
   - Jackson record parser 开启 unknown-property failure；
   - 验证闭集、唯一 exact symbol、非空 reason/owner/test/approval；
   - 用安全配置的 JDK XML parser 读取 core POM 的 `api-compat` profile；
   - 对账 manifest 中非空 `japicmpExclusion` 与 POM `<exclude>`，拒绝 wildcard 和孤儿。
3. 按 kind/change 验证证据：
   - CLASS/METHOD 的 REMOVED/SIGNATURE_CHANGED：`JAPICMP + CONSUMER_COMPILE` 且 exclusion 非空；
   - FIELD：必须含 `PUBLIC_CONSTANT_MANIFEST`，ABI 破坏另含 `JAPICMP + CONSUMER_COMPILE`；
   - SCHEMA：exclusion 必须为空且含 `SCHEMA_CONTRACT`；
   - RESOURCE：exclusion 必须为空且含 `JAR_CONTRACT`。
4. 将 `PublicConstantCompatibilityTests#readRemovalPolicy()` 与内部 `RemovalPolicy` 分别改名为
   `readBreakingPolicy()`、`BreakingPolicy`；改为读取 breaking manifest，只接受 exact `REMOVED`
   FIELD/CLASS 且 evidence 包含 `PUBLIC_CONSTANT_MANIFEST` 的条目。
5. 添加临时目录负向 fixtures：unknown field、duplicate symbol、wildcard exclusion、evidence mismatch、
   orphan POM exclusion；先 RED，再实现最小 parser 使其 GREEN。

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=BreakingChangeManifestTests,PublicConstantCompatibilityTests test
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
rg -n 'deprecations\.json' \
  tfi-flow-core/src/test/java/com/syy/taskflowinsight/compatibility
```

### 审核检查点

- [x] CP-1：manifest 初始为空，不包含推测性删除授权。
- [x] CP-2：每个 japicmp exclusion 有且只有一个 manifest owner，反向亦成立。
- [x] CP-3：常量、schema、resource 使用匹配其真实语义的证据，不借用错误工具。
- [x] CP-4：所有 symbol/exclusion 精确，无 wildcard/package exclusion。
- [x] CP-5：负向 fixtures 证明 unknown/duplicate/evidence/orphan 都会阻断。

### Failure Modes & Safeguards

| 失败 | 保障措施 |
|------|----------|
| manifest 预先批准未来删除 | 初始 entries 必须为空；owner task 与代码变更同批添加 |
| POM exclusion 掩盖未声明破坏 | 双向集合相等校验，任一 orphan 失败 |
| schema/resource 被 japicmp 假保护 | kind-specific evidence，禁止非 ABI exclusion |
| 公共常量删除绕过 manifest | 常量测试只消费 exact FIELD/CLASS + 专用 evidence |
| parser 静默接受新字段 | record strict binding + unknown-property 负向测试 |

### 回滚边界

失败时整体回退 manifest、parser test 与常量测试适配；保持 japicmp 无 exclusion 的硬失败状态。禁止通过
删除负向测试、允许 unknown field 或放宽 wildcard 恢复绿色。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只建立 breaking 授权机制，不实施任何删除。
- [x] **认知负担**：一个 manifest 取代 maturity ledger，字段直接对应业务问题。
- [x] **比例失调**：精确授权与证据对账占主要设计篇幅。
- [x] **ROI**：防止 major 版本下的无审计删除与门禁关闭。
- [x] **洁癖检测**：不扫描或登记 private/package-private 实现细节。
- [x] **局部 vs 全局**：复用 GRD-02/05/07，不创建平行 ABI、常量或消费者门禁。
- [x] **过度设计**：使用 JSON + 测试，不引入治理服务、插件或通用规则引擎。

**架构防卫自检**：无共享可变 Context、无新生产抽象、无异常吞没；测试流程为固定解析→校验→对账。

**内外对照**：内部复用 japicmp、公共常量 manifest 和消费者 reactor；设计遵循 major release 仍需
显式 breaking change inventory 的通用实践。未做实时外部检索，实施前若调整 japicmp exclusion 语法，
以插件 0.24.2 实际负向实验为准。

**结论**：设计通过；已按 TDD 完成实施与门禁验证。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| kind-specific evidence | 校验每类必需证据 | 同时拒绝该 kind 无法提供真实证明的额外证据 | 快速 review 发现“正确证据 + 无效证据”会掩盖审计语义错误 |
| owner task | 引用存在的任务卡 | 先校验 exact task ID，再检查文件存在 | 防止路径别名绕回同一任务卡，保持 owner 唯一可读 |

### 检查点结果

- [x] CP-1：`breaking-changes-v4.json` 根 metadata 精确，`entries=[]`。
- [x] CP-2：安全 XML parser 精确读取 `api-compat` japicmp exclusions；双向 orphan 与重复 owner 均阻断。
- [x] CP-3：FIELD、SCHEMA、RESOURCE 分别只接受常量、schema、JAR 专用证据；无效额外证据同样阻断。
- [x] CP-4：ABI symbol 必须越过类名边界，owner task、symbol、exclusion 均拒绝 wildcard、包前缀或路径别名。
- [x] CP-5：15 个 manifest tests 覆盖 unknown、duplicate、wildcard、evidence、owner/test/ADR 与双向 orphan。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | 严格 record parser、kind/change/evidence 闭集与 japicmp 双向对账均由负向测试证明 |
| 完整性 | 25 /25 | focused 19 tests、strict API gate、七模块 reactor package 全部通过 |
| 可维护性 | 24 /25 | 单一 manifest 替代 3.x policy；测试类集中表达完整合约，代价是文件超过 500 行启发式阈值 |
| 风险控制 | 25 /25 | 空初始清单不预授权删除，安全 XML、exact symbol/owner 与专用证据全部 fail-closed |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST | CR-01 | SCHEMA/RESOURCE 可在专用证据外夹带无效 ABI 证据 | `BreakingChangeManifestTests.java:360` | 已增加 allowed evidence 闭集与负向测试 |
| MUST | CR-02 | ownerTask 路径别名可绕回已存在任务卡 | `BreakingChangeManifestTests.java:452` | 已限制 exact task ID 并增加负向测试 |
| 通过 | - | 最终快速 MUST review 未发现遗留缺陷 | manifest、两份兼容测试 | 无遗留项 |

## 六、完成审核（2026-07-11，待复验）

### 最终结论

**审核通过**。初审时 strict API/consumer gate 被 PRV-03 在途状态阻断；共享树稳定后复验，focused、
japicmp 与七模块 consumer package 全部成功，阻断已解除。

### 当前直接证据

- `BreakingChangeManifestTests,PublicConstantCompatibilityTests`：19/19 fresh 通过。
- 当前 manifest metadata 精确为 schema 1、baseline 3.0.0、target 4.0.0、direct-removal policy；共有
  50 条 entry（CLASS 5、FIELD 6、METHOD 39），owner 仅为 CTX-03..06。
- compatibility 测试源码中 `deprecations.json`、`readRemovalPolicy`、`RemovalPolicy` 均无匹配。
- “初始 entries 为空”是机制落地时的阶段值；后继 owner task 已合法同批填充 entry，持续约束是每条 entry
  精确、可定位、证据匹配且与 POM exclusion 双向一致。

### 复验结果

- Core `-Papi-compat verify -DskipTests` 成功，japicmp/Checkstyle/SpotBugs/coverage gate 均通过。
- 标准 consumer package 形成七模块 reactor，7/7 `SUCCESS`。

## 六、完成审核

### 审核结论

**审核通过。** manifest focused 19/19、japicmp 与七模块 consumer package 均通过。
