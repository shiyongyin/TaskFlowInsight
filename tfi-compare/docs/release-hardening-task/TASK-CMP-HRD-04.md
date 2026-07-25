# TASK-CMP-HRD-04：退役无 owner enable key 并闭合 starter/profile 合同

> **定位**：删除 `tfi.enabled` 的错误 Compare alias 和官方 no-op 配置，同时让 Compare starter 成为可直接消费的标准 Boot starter。
> **deliveryStatus**：`COMPLETE`
> **reviewStatus**：`PASS`
> **依赖**：`TASK-CMP-HRD-03` review PASS
> **后续**：`TASK-CMP-HRD-06`
> **红队来源**：XRT-04、XRT-08

---

## 一、核心（设计时填）

### 背景

Compare starter 当前把 `tfi.enabled` 与 `tfi.change-tracking.enabled` 都解释为 `tfi.compare.enabled`。但当前 Flow starter 只绑定
`tfi.context.*`/`tfi.security.*` 并条件读取 `tfi.annotation.enabled`；`TfiFlow` 的 enable 状态只有程序化静态 API。
因此 `tfi.enabled` 不是 Flow Spring key，而是无 owner 的 retired/no-op 配置。官方 profiles 同时写两个 legacy key，已经触发
Compare alias conflict。

同时，名为 starter 的 `tfi-compare-spring-starter` 只传递 `spring-boot-autoconfigure`，测试 classpath 掩盖了标准宿主依赖；
Quick Start 仍展示 3.0/已删除入口。本卡删除 no-op key、迁移三种 profile，并固定“标准 Boot starter”消费合同。

### 目标（DoD）

- [x] `TfiComparePropertyAliases.ALIASES` 删除 `tfi.enabled -> tfi.compare.enabled`，保留唯一 enable alias。
- [x] `tfi.enabled` 单独或与 canonical/alias 共存时均不影响 Compare policy，也不产生 alias warning。
- [x] 43 个 baseline key 精确分区为 9 aliases + 29 direct removals + 5 prior entries。
- [x] examples 三份 YAML 不含 `tfi.enabled`、`tfi.change-tracking.*` 或无 owner 的 cleanup/cache/debug key。
- [x] default/dev/prod 分别按固定矩阵启动真实应用 Context，断言 Environment、properties 和 Policy，而非只扫文本。
- [x] `tfi-compare-spring-starter` compile 依赖标准 `spring-boot-starter`，不依赖 Ops/all/examples。
- [x] HRD-03 建立的 starter JaCoCo、blocking SpotBugs 与 predecessor zero-new ratchet 保持启用；本卡不得 refresh/rewrite baseline。
- [x] root/中文 Quick Start、operations/design 文档只展示 4.0 pure Compare 与 Compare starter 入口。
- [x] 当前 consumer 文档树不再推荐已删除的 `@EnableTfi`、无 owner 的 `tfi.enabled` 或 `tfi.change-tracking.*`；旧 key 只允许在
  `docs/MIGRATION_GUIDE_v3_to_v4.md` 的显式迁移上下文出现。
- [x] CONFIG/BEHAVIOR manifest 的 owner、计数和 consumer method 精确更新；不再宣称 multiple aliases 可构造。
- [x] starter/examples/all focused tests 与 owning verify 通过，无 no-test 宽容参数。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 配置事实 | 高 | 不得为无 reader 的 key 虚构 Flow owner |
| profile 矩阵 | 高 | 三种官方姿态必须完整、可启动、可断言 |
| starter 消费合同 | 高 | 测试 classpath 不能定义生产依赖 |
| 迁移兼容 | 高 | 保留一个语义/类型精确的 enable alias |
| 新抽象 | 无 | 不增加 alias registry、precedence layer 或 Flow binding |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| `tfi.enabled` | retired/no-op，退出全部官方配置 | 当前没有 Spring reader/owner | 虚构 Flow owner、last-wins |
| legacy Compare enable | 只保留 `tfi.change-tracking.enabled` | 类型和语义可精确迁移 | 删除所有 alias |
| profile tests | 单测试类启动三个独立真实 Context | 一份 XML 同时保留三组结果，避免多次 Maven 覆盖 | 三次运行同名 Surefire report |
| starter 依赖 | `spring-boot-starter` compile | 名称与直接消费语义一致 | 继续依赖 test classpath、重命名制品 |

## 二、执行（设计时填）

### 前置准备

1. HRD-03 必须为 `COMPLETE/PASS`。
2. 保留当前 prod profile Context error 作为 RED；不得先删配置再补测试。
3. 使用源码确认没有 Flow Spring binder 读取 `tfi.enabled`；若实现期间出现新 reader，停止并回到设计评审。

### 固定 profile 期望矩阵

| profile | active profiles | `tfi.annotation.enabled` | Compare enabled | tracking enabled | max result value chars |
|---|---|---:|---:|---:|---:|
| default | `[default]` | `true` | `true` | `true` | `4096` |
| dev | `[dev]` | `true` | `true` | `true` | `8192` |
| prod | `[prod]` | `false` | `false` | `false` | `8192` |

prod 只关闭 Spring annotation、Compare 与 Compare tracking；它不得调用 `TfiFlow.disable()`，也不改变纯 Java静态 facade。

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 修改 | `tfi-compare-spring-starter/src/main/java/com/syy/taskflowinsight/compare/spring/TfiComparePropertyAliases.java` | 删除无 owner alias，保留 typed conflict |
| 修改 | `tfi-compare-spring-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | 只描述唯一 enable alias |
| 修改 | `tfi-compare-spring-starter/pom.xml` | 用 `spring-boot-starter` 取代 direct autoconfigure 生产依赖；保留 HRD-03 module-owned quality executions |
| 修改测试 | `tfi-compare-spring-starter/src/test/java/com/syy/taskflowinsight/compare/spring/CompareAutoConfigurationContractTests.java` | 9/29/5 分区、POM starter dependency、metadata |
| 修改测试 | `tfi-compare-spring-starter/src/test/java/com/syy/taskflowinsight/compare/spring/CompareConfigurationContractTests.java` | retired key、canonical/single-alias 同异值行为 |
| 修改配置 | `tfi-examples/src/main/resources/application{,-dev,-prod}.yml` | 精确实现上表，删除所有 legacy/no-op key |
| 新增测试 | `tfi-examples/src/test/java/com/syy/taskflowinsight/demo/CompareProfilesStartupContractTests.java` | 三个真实 Context + structured YAML 合同 |
| 修改兼容 | `tfi-compare/src/test/resources/compatibility/breaking-changes-v4.json` | CONFIG-0013/0014、BEHAVIOR-0043 |
| 修改文档合同 | `tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/CompareDocumentationContractTests.java` | 扫描当前 consumer 文档闭集，拒绝已删除入口和旧配置建议 |
| 修改 Quick Start | `README.md`、`README.zh-CN.md`、`tfi-examples/src/main/java/com/syy/taskflowinsight/demo/chapters/SpringIntegrationChapter.java` | 4.0 pure/starter 依赖与入口 |
| 修改 all 文档 | `tfi-all/docs/development-design.md`、`tfi-all/docs/product-prd.md`、`tfi-all/docs/operations-manual.md` | 4.0 配置 owner 与 starter 前置；不再推荐已删除入口 |
| 修改 examples 文档 | `tfi-examples/docs/design-doc.md`、`tfi-examples/docs/ops-doc.md`、`tfi-examples/docs/project-overview/design-doc.md`、`tfi-examples/docs/project-overview/prd.md`、`tfi-examples/docs/project-overview/ops-doc.md` | 固化 profile/canonical config；不再推荐迁移 alias |
| 修改 Compare SSOT | `tfi-compare/docs/design-doc.md` | 固化无 owner key、唯一 enable alias 与标准 starter 边界 |
| 修改迁移文档 | `docs/MIGRATION_GUIDE_v3_to_v4.md` | 只在显式 3.x -> 4.0 对照中保留旧 token，并给出 canonical replacement |
| 原则上不改 | `docs/product/**`、历史红队/完成报告 | 历史背景不作为当前实现 SSOT |

### 核心步骤

1. 从 `TfiComparePropertyAliases.ALIASES` 删除唯一条目 `tfi.enabled -> tfi.compare.enabled`；不得在 resolver 其他位置读取它。
2. metadata 对 `tfi.compare.enabled` 只声明 `tfi.change-tracking.enabled` 是启动期 alias；明确 `tfi.enabled` 不属于 Compare 配置。
3. exact partition 固定为：`EXPECTED_ALIASES` 删除 `tfi.enabled`（10 -> 9）；`REMOVED_LEGACY_KEYS` 增加它（28 -> 29）；
   与 5 个 prior entries 的并集仍精确等于 43-key baseline。
4. `CompareConfigurationContractTests` 固定覆盖：
   - 仅 `tfi.enabled=false`：Context 成功，Compare policy 使用默认 `true`，alias warning=0；
   - `tfi.enabled=false + tfi.compare.enabled=true/false`：均成功且只取 canonical；
   - `tfi.enabled + tfi.change-tracking.enabled`：只取后者；
   - canonical 与唯一 alias 同值：成功且 warning 恰为 1；异值：固定 fail-fast；
   - 删除无法由最终 alias map 构造的 `multipleAliasesForOneCanonicalKeyFailStartup`，不得新增可变 alias framework 伪造负例。
5. 三份 YAML 固定为：
   - base：annotation/compare/tracking 全为 true，不写 max chars；
   - dev：显式 annotation/compare/tracking=true，`max-result-value-chars=8192`；
   - prod：显式 annotation/compare/tracking=false，`max-result-value-chars=8192`；
   - 三份均删除 `tfi.enabled`、整个 `tfi.change-tracking`、`cleanup-interval-minutes`、`max-cached-classes`、`debug-logging`。
6. `CompareProfilesStartupContractTests` 固定为四个方法：default/dev/prod 各启动并关闭一个
   `SpringApplicationBuilder(TaskFlowInsightApplication.class).web(WebApplicationType.NONE)`，并调用
   `run("--spring.profiles.active=<profile>")` 启动 Context；command-line property 必须覆盖外部 `SPRING_PROFILES_ACTIVE`。
   第四个方法用
   `YamlPropertySourceLoader` 结构化读取三份资源并断言禁用 key 缺失。禁止纯字符串 contains 扫描。
7. 每个 profile 方法断言 active profiles、`tfi.annotation.enabled` Environment 值、`TfiCompareProperties`、`ComparePolicy` 和 tracking
   bean presence 与上表一致。`StaticAndSpringCompareContractTests` 继续证明 retired system/property flags 不改变静态 TFI runtime。
8. starter POM 用 compile-scope `org.springframework.boot:spring-boot-starter` 取代 direct
   `spring-boot-autoconfigure`；保留 optional Flow starter、optional configuration processor，以及 HRD-03 的 JaCoCo/SpotBugs/static
   ratchet。Enforcer 禁止 Ops/all/examples 的规则不变。POM/resource changes 后只运行默认只读 ratchet；禁止
   `--add-module`、`--write-baseline` 或 `--refresh-baseline`。
9. Quick Start 固定给出两个入口：pure Java `com.syy:tfi-compare:4.0.0`；Spring Boot
   `com.syy:tfi-compare-spring-starter:4.0.0`。删除 3.0 版本和已移除的 `@EnableTfi`。
10. `CompareDocumentationContractTests#currentConsumerDocsUseOnlyV4EntrypointsAndConfiguration` 扫描 `README.md`、
    `README.zh-CN.md`、`tfi-all/docs/**/*.md`、`tfi-examples/docs/**/*.md`、`tfi-compare/docs/design-doc.md` 与
    `SpringIntegrationChapter.java`，要求不存在 `@EnableTfi`、`tfi.enabled`、`tfi.change-tracking.`、YAML key
    `change-tracking:` 或 `<version>3.0.0</version>`。这些 current roots 不允许用“迁移说明”豁免；确需解释旧 key 的内容统一移到
    `docs/MIGRATION_GUIDE_v3_to_v4.md`。合同必须至少以当前四个遗漏文件
    `tfi-all/docs/development-design.md`、`tfi-all/docs/product-prd.md`、
    `tfi-examples/docs/project-overview/design-doc.md`、`tfi-examples/docs/project-overview/prd.md` 作为 RED，避免只修原文件表已列路径。
11. migration guide 可出现旧 token，但每个旧示例必须位于明确的 3.x/retired/migration 段落并在同一小节给出
    `tfi.compare.*`、`tfi.compare.tracking.enabled` 或 Boot auto-configuration replacement；不得把旧配置继续写成当前 Quick Start。
12. manifest 精确更新：
    - `CMP-BRK-CONFIG-0013`：9 aliases，owner `CMP-HRD-04`，consumer 保持
      `com.syy.taskflowinsight.compare.spring.CompareAutoConfigurationContractTests#legacyMetadataKeysAreExhaustivelyPartitionedIntoAliasesAndDirectRemovals`；
    - `CONFIG-0014`：29 removals，明确 `tfi.enabled` 在 4.0 无 Spring owner且 Compare 不绑定，同一 consumer；
    - `BEHAVIOR-0043`：只承诺 canonical-vs-single-alias typed conflict/conversion failure，owner `CMP-HRD-04`，consumer 保持
      `com.syy.taskflowinsight.compare.spring.CompareConfigurationContractTests#canonicalAndDifferentAliasFailStartup`。

### 验证命令

```bash
./mvnw -pl tfi-examples,tfi-all -am -DskipTests install

./mvnw -pl tfi-compare \
  -Dtest=com.syy.taskflowinsight.architecture.CompareDocumentationContractTests \
  test

./mvnw -pl tfi-compare-spring-starter clean \
  -Dtest=com.syy.taskflowinsight.compare.spring.CompareAutoConfigurationContractTests,com.syy.taskflowinsight.compare.spring.CompareConfigurationContractTests \
  test

./mvnw -pl tfi-examples clean \
  -Dtest=com.syy.taskflowinsight.demo.CompareProfilesStartupContractTests,com.syy.taskflowinsight.demo.DemoControllerTest \
  test

./mvnw -pl tfi-all \
  -Dtest=com.syy.taskflowinsight.api.StaticAndSpringCompareContractTests \
  test

./mvnw -pl tfi-compare,tfi-compare-spring-starter,tfi-examples,tfi-all -am verify
```

### 审核检查点

- [ ] CP-1：生产 resolver/metadata/examples/docs 不再把 `tfi.enabled` 当 Compare 或 Flow Spring key。
- [ ] CP-2：9/29/5 分区与 43-key baseline 闭合；manifest stable ID/owner/consumer 精确。
- [ ] CP-3：单一 alias 的同异值与 retired key/canonical/alias 共存矩阵全部有断言。
- [ ] CP-4：一个 Surefire XML 中 default/dev/prod 三个真实启动测试均非零并符合固定矩阵。
- [ ] CP-5：starter artifact 传递标准 Boot starter；Quick Start 无 3.0、`@EnableTfi` 或隐含宿主前置。
- [ ] CP-6：starter 默认 verify 仍执行 module-owned coverage、blocking SpotBugs 与 predecessor zero-new ratchet；static baseline 无本卡 diff。
- [ ] CP-7：当前 consumer 文档闭集合同通过；在 consumer 文档范围内，旧入口/key 只存在于带 canonical replacement 的
  migration guide 或历史目录。

### 禁止范围与回滚

本卡不新增 Flow Spring enable binding，不调用 `TfiFlow.enable/disable()`，不使用 PropertySource 顺序，不删除唯一合法 Compare alias。
回滚必须同时恢复 alias/metadata/POM/manifest/tests/docs/profiles；只恢复 alias 会重新使官方配置冲突。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只修配置事实、官方 profile 和 starter 消费合同。
- [x] **认知负担**：删除无 owner key和一个隐含依赖，不增加配置抽象。
- [x] **比例失调**：真实启动与 artifact contract 高于文字迁移。
- [x] **ROI**：一张卡关闭官方启动失败和 starter test-classpath 假设。
- [x] **洁癖检测**：不删除全部 alias，不为未来 Flow binding 预留 no-op key。
- [x] **局部 vs 全局**：starter、manifest、profiles、Quick Start 与 consumer 同卡闭合。
- [x] **过度设计**：未增加 precedence layer、alias registry 或 profile framework。

**结论**：设计通过；删除无 owner key并补标准 starter 依赖是关闭 XRT-04/XRT-08 的最小充分边界。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 启动期静态状态 | profile 测试只验证配置矩阵 | 同时发现 `TfiSwitchDemo` 被组件扫描并在每次启动修改 JVM 静态状态 | 真实 Context RED 暴露隐藏副作用；移出 Spring 扫描后保留独立 main 演示 |
| starter 消费合同 | POM 切换到标准 Boot starter | 增加 build contract 并以 compile dependency tree 复核 | 防止测试 classpath 再次掩盖生产依赖 |
| 注释扫描 | 复检本卡 Java 改动 | `TfiSwitchDemo` 0 violation；examples 全模块仍有 333 个既有 violation | 历史注释债不属于本卡，未扩张范围 |

### 检查点结果

- [x] CP-1：resolver 仅保留 9 个 typed alias；metadata 明确全局 enable key 不属于 Compare，consumer 文档扫描为零命中。
- [x] CP-2：9 aliases + 29 removals + 5 prior 精确闭合 43-key baseline；manifest 三项 owner/consumer 合同通过。
- [x] CP-3：retired key、canonical、唯一 alias 的独立/同值/异值/转换失败矩阵由 starter contract 覆盖。
- [x] CP-4：同一轮 Surefire 中 default/dev/prod 三个真实 Context 与结构化 YAML 共 `4/4`，`DemoControllerTest` `4/4`。
- [x] CP-5：starter build contract `5/5` 且 dependency tree 确认为 `spring-boot-starter:3.5.5:compile`；Quick Start 为 4.0 双入口。
- [x] CP-6：starter 全量 `42/42`；JaCoCo、blocking SpotBugs 与只读 ratchet 生效，最终 `118 <= 141`，baseline 未改写。
- [x] CP-7：Compare 文档合同 `8/8`、all 静态/Spring 隔离合同 `1/1`，当前 consumer 文档无退役 token。

### 需要人类确认

- 当前无；实现未引入新的配置 owner、发布授权或风险接受决策。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | typed alias、retired key、三 profile 与静态状态隔离合同全部通过 |
| 完整性 | 25 /25 | focused、文档、consumer 与 8 模块 reactor verify 全部通过 |
| 可维护性 | 25 /25 | 删除无 owner key 和组件副作用；未增加 alias registry、precedence layer 或新运行图 |
| 风险控制 | 25 /25 | 质量 ratchet 未放宽/重录；prod 关闭姿态与 starter 生产依赖有可执行证据 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| HIGH | HRD04-REV-01 | `TfiSwitchDemo` 作为组件会在普通应用启动时修改 JVM 静态状态 | `TfiSwitchDemo.java:16` | 已移出组件扫描并由真实 profile 回归关闭 |
| INFO | HRD04-REV-02 | examples 全量 Javadoc 扫描存在 333 个历史 violation | `tfi-examples/src/main/java` | 本卡触及文件为 0，历史债不扩张本卡范围 |
| PASS | HRD04-REV-03 | 最终复审 `0 MUST / 0 HIGH`；8 模块 verify 通过 | 本卡全部改动 | 通过 |
