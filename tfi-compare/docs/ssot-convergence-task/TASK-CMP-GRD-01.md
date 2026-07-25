# TASK-CMP-GRD-01：固化3.0 API inventory与五类breaking manifest

> **定位**：在任何runtime符号变化前，建立可复现、逐member、双向校验的4.0兼容事实源。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：accepted `CMP_G1/G2/G7`；ADR-011、ADR-014
> **后续**：`TASK-CMP-GRD-02`
> **架构来源**：总体设计§13.1、§13.4、W0；INDEX§5.1、§7
> **消费不变量**：19、20、21

---

## 一、核心（设计时填）

### 背景

3.0 baseline与当前源码都有175个public顶层FQN，但现有`ApiSurfaceCompatibilityTests`只做存在性和方法数量下限断言，
无法批准4.0删除，也看不到type hierarchy、nested type和行为破坏。本卡先机械抽取完整member inventory，再用唯一
`breaking-changes-v4.json`登记所有允许变化；不修改生产源码，也不把主版本号当作删除授权。

### 目标（DoD）

- [x] baseline JAR/POM checksum通过，inventory覆盖public/protected type、constructor、method、field/constant、nested type和type hierarchy。
- [x] 新增唯一五类manifest，entry包含stable id、before、after、replacement、reason、owner task、consumer test。
- [x] API ABI变化才允许携带精确japicmp exclusion；包级exclusion与未登记差异使构建失败。
- [x] Compare拥有独立`api-compat` profile，3.0与当前API双向差异均可机器解析。
- [x] `ApiSurfaceCompatibilityTests`不再要求全部旧符号永久存在，改为inventory/manifest双向合同。
- [x] 本卡不修改`src/main/java`、下游生产源码或runtime resource。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 基线可信与逐member完整性 | 高 | 后续每项删除都依赖此证据 |
| manifest双向校验 | 高 | 防止漏登记或陈旧条目 |
| CI接线 | 中 | W0先让门禁可运行，W7再收紧全部质量配置 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 兼容载体 | 单一五类JSON manifest | 同时覆盖ABI之外的资源/配置/schema/行为 | 多份互不校验的清单 |
| API扫描 | japicmp + 自有inventory contract | japicmp判ABI，contract补nested/type hierarchy与owner映射 | 只数public方法 |
| 删除授权 | exact symbol entry | 可审计、可映射消费者 | 包名含`internal`即允许删除 |

## 二、执行（设计时填）

### 前置准备

实施前记录工作树状态；只读取以下基线，不重新打包或覆盖：

```bash
(cd .mvn/api-baseline && shasum -a 256 -c SHA256SUMS)
jar tf .mvn/api-baseline/repository/com/syy/tfi-compare/3.0.0/tfi-compare-3.0.0.jar
```

### 文件与职责

| 动作 | 精确路径 | 职责 |
|---|---|---|
| 新增 | `tfi-compare/src/test/resources/compatibility/current-api-inventory-v3.json` | checksum绑定的逐member baseline inventory |
| 新增 | `tfi-compare/src/test/resources/compatibility/breaking-changes-v4.json` | API/RESOURCE/CONFIG/SCHEMA/BEHAVIOR唯一manifest |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareApiInventoryContractTests.java` | baseline/current/member/owner闭集校验 |
| 新增测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareBreakingChangeManifestTests.java` | 五类entry schema、双向映射、精确exclusion校验 |
| 修改测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/api/ApiSurfaceCompatibilityTests.java` | 删除存在性/方法数量只增假门禁，改为委托新contract |
| 修改 | `tfi-compare/pom.xml` | 增加Compare独立japicmp execution/profile及baseline坐标 |
| 修改 | `.github/workflows/tfi-compare-ci.yml` | API job执行checksum、contract和`-Papi-compat` |

### 核心步骤

1. 从checksum固定JAR生成canonical member signature，排序不得依赖JAR entry或reflection返回顺序。
2. 从当前编译产物生成同形inventory；每个member必须映射到INDEX中的owner task。
3. 定义manifest闭集schema；未知kind、重复id、空replacement/reason/consumerTest、无owner卡一律失败。
4. 配置japicmp只接受manifest中`kind=API`的exact member/type exclusion，并验证exclusion确实对应真实差异。
5. 改造现有API测试与CI；构造“源码多一项”和“manifest多一项”两个负向fixture证明双向失败。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests,CompareBreakingChangeManifestTests,ApiSurfaceCompatibilityTests test
./mvnw -pl tfi-compare -Papi-compat verify -DskipTests
./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：baseline SHA与INDEX记录一致，测试不从开发者`~/.m2`随机解析同GAV。
- [x] CP-2：175个顶层类型之外，public/protected member、nested type和type hierarchy都进入inventory。
- [x] CP-3：manifest任一侧多项、重复或无consumer test均失败。
- [x] CP-4：无包级japicmp exclusion，无生产代码改动。

### 禁止范围与回滚

禁止分类未来未设计的替代API、删除旧符号或更改Gate。失败时整体回退本卡test/POM/CI增量；baseline与用户既有文件不变。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：仅建立兼容证据，不提前实施4.0语义。
- [x] **认知负担**：一个inventory格式、一个breaking manifest，无第二台账。
- [x] **比例失调**：主要篇幅用于逐member与双向失败合同。
- [x] **ROI**：消除175个public类型被包级删除或方法计数掩盖的风险。
- [x] **洁癖检测**：不整理生产package或测试命名。
- [x] **局部 vs 全局**：每个符号必须有后继owner和consumer test。
- [x] **过度设计**：复用japicmp与JUnit，不建自定义兼容平台。

**结论**：设计通过；只能作为W0首卡实施。

## 四、反馈（实现过程中回填）

### TDD证据

| 行为 | 阶段 | 命令 | 退出码 | 关键结果 |
|---|---|---|---:|---|
| 固定基线inventory资源存在且绑定3.0 SHA | RED | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 1 | 测试编译通过；仅因`current-api-inventory-v3.json`缺失在第30行按预期失败 |
| 固定基线inventory资源存在且绑定3.0 SHA | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 0 | 1个测试通过；资源版本与SHA绑定成功 |
| 175个public顶层类型均有分类、owner、层级与逐member结构 | RED | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 1 | 2个测试运行；仅因types实际0、期望175失败 |
| 175个public顶层类型均有分类、owner、层级与逐member结构 | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 0 | ASM无类初始化生成inventory；2个测试通过，0失败/跳过 |
| inventory与固定JAR双向逐项一致，额外/缺失声明均失败 | RED | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 1 | 新validator API尚未实现，testCompile仅报告`repositoryRoot/validateExact`缺失 |
| inventory与固定JAR双向逐项一致，额外/缺失声明均失败 | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 0 | 5个测试通过；missing/unexpected/changed逐项对账 |
| 唯一manifest资源声明accepted policy与五类闭集 | RED | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 1 | 1个测试运行；仅因`breaking-changes-v4.json`缺失失败 |
| 唯一manifest资源声明accepted policy与五类闭集 | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 0 | 1个测试通过；accepted token与五类闭集已固定 |
| manifest拒绝未知kind、重复stable id与空replacement | RED | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 1 | testCompile仅因新`CompareBreakingManifest` validator未实现失败 |
| manifest拒绝未知kind、重复stable id与空replacement | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 0 | 4个测试通过；根/entry字段、id/kind、必填项均严格校验 |
| Compare拥有独立protected级api-compat profile | RED | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 1 | 5个测试中仅profile缺失断言失败 |
| Compare拥有独立protected级api-compat profile | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 0 | 5个测试通过；protected/source/binary阻断配置生效 |
| 未登记ABI差异由真实japicmp阻断 | RED | `./mvnw -pl tfi-compare -Papi-compat verify -DskipTests` | 1 | 精确报告4个nested-depth常量与3个SPI `priority()`声明删除 |
| 7项ABI变化逐项manifest/exclusion后japicmp通过 | GREEN | `./mvnw -pl tfi-compare -Papi-compat verify -DskipTests` | 0 | exact exclusions生效；SpotBugs 0，既有Checkstyle/PMD按模块基线非阻断 |
| manifest/POM exact双向对账且owner/consumer可解析 | RED | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 1 | testCompile仅因`validateRepository`尚未实现失败 |
| manifest/POM exact双向对账且owner/consumer可解析 | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 0 | 10个测试通过；orphan/POM owner/任务卡/consumer负向fixture均阻断 |
| 旧API测试退役growth-only并委托机器contract | RED | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 1 | 6个测试中仅旧源码仍含count/growth断言失败 |
| 旧API测试退役growth-only并委托机器contract | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests,CompareBreakingChangeManifestTests,ApiSurfaceCompatibilityTests test` | 0 | 17个测试通过，0失败/错误/跳过 |
| CI执行checksum、三个contract与japicmp | RED | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 1 | 11个测试中仅workflow缺checksum命令失败 |
| CI执行checksum、三个contract与japicmp | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 0 | 11个测试通过；baseline路径变化也进入workflow触发范围 |
| inventory分类/member kind闭集且owner任务可解析 | RED | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 1 | testCompile仅因`validateSchema`尚未实现失败 |
| inventory分类/member kind闭集且owner任务可解析 | GREEN | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 0 | 9个测试通过；四类member、分类闭集、owner与负向fixture均验证 |
| 当前编译产物进入同形inventory | RED | `./mvnw -pl tfi-compare -Dtest=CompareApiInventoryContractTests test` | 1 | testCompile仅因`current(Path,ObjectMapper)`缺失失败 |
| 当前编译产物进入同形inventory | GREEN | 同上 | 0 | 10个测试通过；已登记常量删除证明读取的是`target/classes`而非baseline |
| 陈旧exclusion对应真实差异 | RED | `./mvnw -pl tfi-compare -Dtest=CompareBreakingChangeManifestTests test` | 1 | testCompile仅因真实删除validator缺失失败 |
| 陈旧exclusion对应真实差异 | GREEN | 同上 | 0 | 12个测试通过；额外POM/manifest项必须在baseline存在且在current缺失 |
| consumerTest必须可执行 | RED | 同上 | 1 | 13个测试中仅普通`void` helper被错误接受的断言失败 |
| consumerTest必须可执行 | GREEN | 同上 | 0 | 13个测试通过；消费者源码必须有JUnit 5 executable注解 |
| japicmp直接读取固定baseline JAR | RED | 同上 | 1 | 14个测试中仅POM仍用GAV dependency失败；报告`oldJar`位于`~/.m2` |
| japicmp直接读取固定baseline JAR | GREEN | 同上 | 0 | 14个测试通过；报告`oldJar`指向仓库`.mvn/api-baseline` |

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| current inventory | 3.0/current同形抽取 | 首轮仅抽取baseline，审查后补`target/classes`扫描 | 防止自有合同只验证静态资源 |
| exclusion真实性 | manifest/POM/真实差异三方闭环 | 首轮仅对账manifest/POM，审查后加入baseline/current声明差 | 同步改两份配置不能证明差异真实存在 |
| consumer证据 | consumer test可解析 | 首轮任意同名`void`方法可通过，审查后要求JUnit 5 executable注解 | 下游test class不在Compare测试classpath，只能安全校验源码 |
| baseline输入 | 固定仓库JAR | 首轮GAV最终命中`~/.m2`，审查后改为直接file input | repository声明不足以证明实际oldJar来源 |

### 检查点结果

- [x] CP-1：checksum全通过；japicmp XML的`oldJar`为仓库`.mvn/api-baseline`绝对路径。
- [x] CP-2：175个顶层类型、2,422个member；含185 constructor、259 field、1,878 method、100 nested type。
- [x] CP-3：额外/缺失inventory、重复id、未知kind、空字段、orphan exclusion、虚假owner/consumer/差异均有负向测试。
- [x] CP-4：7个member-exact exclusion，无通配符/包级排除；本卡未修改生产源码或runtime resource。

### 分层验证

| 命令 | 退出码 | 结果 |
|---|---:|---|
| `(cd .mvn/api-baseline && shasum -a 256 -c SHA256SUMS)` | 0 | 11个固定baseline artifact全部`OK` |
| focused三个compatibility contracts | 0 | 25 tests，0 failure/error/skip |
| `./mvnw -pl tfi-compare clean verify` | 0 | 3,592 tests；SpotBugs 0；Checkstyle/PMD按模块既有非阻断baseline解释 |
| `./mvnw -pl tfi-compare -Papi-compat verify -DskipTests` | 0 | protected source/binary检查通过，报告绑定仓库固定JAR |
| `./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package` | 0 | 7个reactor项目编译/打包成功；不作为targeted test证据 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | baseline/current字节码事实、manifest/POM/真实差异三方闭环，四类负向审查问题均修复 |
| 完整性 | 25 /25 | 175 type、2,422 member、五类schema、CI/profile/消费者编译证据齐全 |
| 可维护性 | 23 /25 | 单一schema与中文边界注释清楚；ASM helper为558行启发式热点，但拆分会增加当前线性流程跳转 |
| 风险控制 | 25 /25 | checksum固定、secure XML、exact exclusion、JUnit consumer、无production/runtime改动 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| P1/MUST | GRD01-R01 | 自有inventory未扫描当前编译产物 | `CompareApiInventory.java:74` | 已补current ASM投影与负向证明 |
| P1/MUST | GRD01-R02 | manifest/POM可同步登记不存在的陈旧exclusion | `CompareBreakingManifest.java:97` | 已与baseline/current真实声明双向校验 |
| P1/MUST | GRD01-R03 | 任意普通`void` helper可冒充consumer test | `CompareBreakingManifest.java:208` | 已要求JUnit 5 executable注解 |
| P1/MUST | GRD01-R04 | japicmp old JAR实际来自开发者`~/.m2` | `tfi-compare/pom.xml:152` | 已改仓库固定JAR file input并核对XML |
| 结论 | - | MUST 4项，已修复4项；无遗留P0/P1 | - | 审核通过 |
