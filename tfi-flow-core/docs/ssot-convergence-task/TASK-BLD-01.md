# TASK-BLD-01：让仓库 Parent 成为构建配置 SSOT

> **定位**：统一仓库插件版本和通用默认，同时保留 Core 严格门禁，并关闭标准 skip-tests install 回归。
> **状态**：完成（2026-07-11）
> **书面规格**：[当前架构 SSOT](../design-doc.md)与[任务索引](INDEX.md)
> **审核状态**：审核通过（4/4 focused、711 Core、双 API、标准 install、七模块构建配置门禁；100/100）
> **依赖**：`TASK-LFC-01..06`、`TASK-CTX-01..06`、`TASK-PRV-01..06`、`TASK-EXP-00..09`，均已完成
> **架构来源**：受保护 master Task 1 / `B1` / Wave 5；当前事实对 B1 文件范围作必要扩展

---

## 一、核心

### 背景

实施前 Root 与 Core 分别直接继承 Spring Boot parent `3.5.5`，其他业务模块继承
`com.syy:taskflowinsight-parent:${revision}`。Root/Core 当时重复维护质量插件，且版本已经漂移：Root/Core 的
Checkstyle 分别为 `3.4.0/3.6.0`，SpotBugs 分别为 `4.8.6.2/4.8.6.6`。

Core 改继承 Repository Parent 时，Maven 默认合并可能静默引入 Root 的 model/demo coverage exclusions、宽松
Checkstyle/SpotBugs 参数或重复 JaCoCo execution。另一个已复现事实是：标准
`./mvnw -DskipTests install` 会在 tfi-all 用空 coverage 数据执行 JaCoCo `0.50` 门禁并失败。JaCoCo 的 `skip`
只消费 `jacoco.skip`，且 parent active profile 无法覆盖 child POM 中显式的 `jacoco.skip=false`。

因此本卡必须同时修改 Root、Core、tfi-all 三份 POM，并新增一个独立构建配置契约测试。只改 Root/Core 会保留
GRD-01 唯一失败，不属于完成。

### 唯一目标

Root 成为插件版本和通用默认的唯一 owner；Core 只声明纯 Java 边界、严格质量门禁、JMH/API 兼容等模块差异；
tfi-all 只补齐与 Core 相同的 skip-tests coverage 生命周期规则。正常 verify 仍执行 coverage，只有
`-DskipTests=true` 时跳过 JaCoCo instrumentation/report/check。

### 目标（DoD）

- [x] `tfi-flow-core` 精确继承 `com.syy:taskflowinsight-parent:${revision}`，`relativePath` 为 `../pom.xml`。
- [x] Root `pluginManagement` 唯一管理 Enforcer `3.4.1`、Checkstyle `3.6.0`、SpotBugs `4.8.6.6`、
  JaCoCo `0.8.12`、Surefire `3.5.3`；Root JaCoCo execution ID 精确为 `prepare-agent/report/check`。
- [x] Core 正常构建固定 `jacoco.skip=false`，effective JaCoCo 只有上述 3 个 execution、0 个继承 exclusion，
  instruction `0.80`、branch `0.70`。
- [x] Core Enforcer 继续精确禁止 Spring、Spring Boot、Micrometer、Caffeine，且 `fail=true`。
- [x] Core Checkstyle 使用模块自定义配置并 `failsOnError=true`；SpotBugs 保持
  `effort=Max/threshold=High/failOnError=true`；两者不继承 Root 宽松参数或过滤器。
- [x] 删除无效 `includeTestSourceRoots`，源码与 effective POM 均不存在该参数，verify 不再产生 unknown-parameter 告警。
- [x] Core 删除本地 flatten plugin 声明，只继承 Root 的 `flatten/flatten.clean` executions。
- [x] Core early JAR、benchmark 默认 exclusion、`api-compat`、`perf` 和 JMH annotation processor 行为保持不变。
- [x] Core 与 tfi-all 都声明唯一 profile `skip-coverage-when-tests-skipped`：仅在
  `skipTests=true` 时设置 `jacoco.skip=true`；正常 test/verify 不跳过 coverage。
- [x] `BuildConfigurationContractTests` 锁定 source POM 合同，但不模拟 Maven model resolution 或插件执行。
- [x] Core runtime dependency tree 的外部 runtime artifact 仍精确只有 `org.slf4j:slf4j-api:2.0.17`；Lombok 仍为 provided。
- [x] focused contract、Core clean verify、Core/tfi-all API compatibility、标准 reactor install 与七模块
  `-DskipTests clean verify` 构建配置门禁全部通过。
- [x] 无 `skipTests` 的 Portfolio `clean verify` 已执行并精确归类为 9 failure / 10 error，全部来自未修改的
  `tfi-all` 旧测试合同；解除责任属于 `TASK-TST-01`，最终绿色声明属于 `TASK-DOC-01`。
- [x] `tfi-flow-core/Users/` 删除前只含历史错误 flattened POM；删除后所有验收构建均不再生成该目录树。

### 文件范围

| 动作 | 精确路径 | 责任 |
|------|----------|------|
| 修改 | `pom.xml` | canonical plugin versions、Root defaults、JaCoCo execution IDs |
| 修改 | `tfi-flow-core/pom.xml` | Repository Parent、Core strict overrides、skip-tests coverage profile |
| 修改 | `tfi-all/pom.xml` | skip-tests coverage profile |
| 新增 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/build/BuildConfigurationContractTests.java` | source POM 构建合同 |
| 清理 | `tfi-flow-core/Users/` | 删除唯一历史错误 flatten 输出并验证不再生成 |
| 修改 | `TASK-BLD-01.md`、`TASK-GRD-01.md`、`INDEX.md`、`.execution/*` | BLD 状态、GRD-01 fresh install 复审与任务索引证据 |

明确不修改：CI workflow、production Java、现有业务测试、其他模块 POM、五份受保护 source plans、
`docs/architecture` 或 Core runtime/export/provider/lifecycle 文档。CI `continue-on-error` 仍归 `TASK-DOC-01`。

### 方案选择

| 方案 | 结论 | 原因 |
|------|------|------|
| 只改 Root + Core | 否决 | tfi-all 继续在 `-DskipTests install` 用空数据执行 coverage gate，GRD-01 保持失败 |
| 只在 Root 定义 active profile | 否决 | Maven spike 已证明 parent profile 不能覆盖 child 显式 `jacoco.skip=false` |
| Root + Core + tfi-all，各 coverage owner 使用本地 profile | 采用 | 正常 coverage 不变，skip-tests 生命周期可复现通过，影响面只覆盖两个显式启用 coverage 的模块 |

### 构建 SSOT 所有权

| 配置 | Root owner | Core delta | tfi-all delta |
|------|------------|------------|---------------|
| Enforcer | version `3.4.1` | 4 类 banned dependencies + blocking execution | 无 |
| Checkstyle | version `3.6.0` + repository 宽松默认 | custom rules + strict replacement | 无 |
| SpotBugs | version `4.8.6.6` + repository 宽松默认 | strict replacement，不继承 Root filters | 无 |
| JaCoCo | version `0.8.12` + Root 0.50/default exclusions + 3 named executions | 0 exclusion + 80/70 strict replacement | 使用 Root 0.50/default exclusions |
| Surefire | version `3.5.3` + `tfi.perf.enabled=false` | 继承；Core perf profile 改为 true | 继承 |
| Flatten | version/config/executions | 只继承，不本地重复声明 | 继承 |
| skip-tests coverage | Root 默认 `jacoco.skip=true` | base false + local property profile | base false + local property profile |

### 跨卡不变量

- 本卡不改变 runtime API、schema、dependency graph 或业务行为。
- 不通过降低 Core coverage、关闭 strict failure、恢复 model exclusion 或删除 Enforcer 来完成 parent 迁移。
- 不把 POM 合同塞入 `FlowCoreArchitectureBoundaryTest` 或 `BreakingChangeManifestTests`，避免混合 source boundary、
  breaking inventory 与 build ownership。
- before/after effective POM 是构建证据，不是长期 source SSOT；source POM 与 focused test 才是持久合同。
- 本卡可以运行 Portfolio 形态的 reactor gate，但不得宣称最终 Portfolio 收敛完成；最终所有权仍是 `TASK-DOC-01`。

## 二、执行（书面规格确认后）

### 已捕获基线

- `tfi-flow-core/target/bld-01-effective-core-before.xml`
- `target/bld-01-effective-root-before.xml`
- Core runtime tree：外部 runtime dependency 精确只有 `org.slf4j:slf4j-api:2.0.17`
- 当前独立审核：`36 pass / 1 fail / 6 not applicable`；唯一 fail 是 GRD-01 标准 install 回归

### TDD 与最小实施顺序

1. 新增 `BuildConfigurationContractTests`，先证明当前 POM 因 parent、canonical versions、execution ID、
   invalid parameter、Core-local flatten 和缺少两个 module-local profiles 而 RED。
2. 修改 Root：增加 Enforcer managed version，升级 Checkstyle/SpotBugs canonical versions，并把 Root 首个
   JaCoCo execution 显式命名为 `prepare-agent`；不改变 Root 0.50/exclusion/宽松策略。
3. 修改 Core：切换 parent、删除可继承坐标/版本/flatten 副本，保留 `jmh.version` 与 `jacoco.skip=false`，
   对 Checkstyle/SpotBugs/JaCoCo 使用确定性 replacement，加入本地 skip-tests coverage profile。
4. 修改 tfi-all：保留正常 `jacoco.skip=false`，加入同名、同激活条件、同单一属性的本地 profile。
5. 运行 focused test 取得 GREEN，再生成 Root/Core/Core-skip/Core-perf/tfi-all-skip 五份 after effective POM。
6. 逐项比较 plugin versions、execution count/IDs、threshold、exclusion、strict flags、profiles 和 perf 行为。
7. 运行 Core、API、runtime tree、标准 install 与 BLD reactor gate；失败时只修复本卡 owner，不放宽门禁。
8. 清理前确认历史错误目录只含一份 flattened POM；清理后及全部 Maven gates 后均断言该目录不存在。

### 验收命令

```bash
# 持久 source POM 合同
./mvnw -pl tfi-flow-core -Dtest=BuildConfigurationContractTests test

# after effective models
./mvnw help:effective-pom -Doutput=target/bld-01-effective-root-after.xml
./mvnw -pl tfi-flow-core help:effective-pom \
  -Doutput=target/bld-01-effective-core-after.xml
./mvnw -pl tfi-flow-core -DskipTests=true help:effective-pom \
  -Doutput=target/bld-01-effective-core-skip-tests-after.xml
./mvnw -pl tfi-flow-core -Pperf help:effective-pom \
  -Doutput=target/bld-01-effective-core-perf-after.xml
./mvnw -pl tfi-all -DskipTests=true help:effective-pom \
  -Doutput=target/bld-01-effective-all-skip-tests-after.xml

# module behavior and compatibility
./mvnw -pl tfi-flow-core dependency:tree -Dscope=runtime
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat -DskipTests=true verify
./mvnw -pl tfi-all -am -Papi-compat -DskipTests=true verify

# close GRD-01 and validate the BLD-owned reactor configuration
./mvnw -DskipTests install
./mvnw -DskipTests clean verify

# Portfolio diagnostic; TST-01/DOC-01 own the final green result
./mvnw clean verify
```

Core 的正常测试与 coverage 已由独立 `clean verify` 证明；七模块 `-DskipTests clean verify` 只证明 parent/plugin
迁移后的 reactor 构建模型。无 `skipTests` 的命令在本卡作为 Portfolio 诊断执行，不能把其既有 `tfi-all` 测试债
错误归因给 POM 迁移，也不能提前宣称 Portfolio 完成。

### 审核检查点

- [x] CP-1：Root canonical versions 与 `prepare-agent/report/check` IDs 精确。
- [x] CP-2：Core effective JaCoCo execution 数量为 3、exclusion 数量为 0、threshold 为 0.80/0.70。
- [x] CP-3：Core Checkstyle/SpotBugs effective config 不含 Root loose-only 参数/filter，strict failure 为 true。
- [x] CP-4：Core Enforcer exact banned set 与 fail=true 保持。
- [x] CP-5：default effective `jacoco.skip=false`；Core/tfi-all skip-tests effective 均为 true。
- [x] CP-6：`-Pperf` effective 同时含 Parent Surefire `tfi.perf.enabled=true` 与 Core JMH/compiler activation；
  `-Dtfi.perf.enabled=true` 的既有 Core property activation 保持。
- [x] CP-7：Core effective 继承 Root flatten，source POM 无本地 flatten；无 `includeTestSourceRoots` warning。
- [x] CP-8：runtime tree、Core/API、standard install 与七模块构建配置 gate 全部通过；Portfolio full-test
  diagnostic 的 19 个结果已逐类归入 TST-01，不与 BLD gate 混写。
- [x] CP-9：历史错误 flatten 目录已按内容前置检查清理，后续生命周期未重新生成。

### 回滚边界

Root/Core/tfi-all POM 与 build-contract test 是一个原子批次。任一 effective contract 或 Maven gate 不符合时，整体撤销
本卡实施改动，保持实施前双 Boot parent 状态；不得留下“Core 已继承但严格门禁未证明”或“Root 版本已升级但
tfi-all install 仍失败”的中间形态。回滚不修改受保护计划、runtime Java、CI 或 accepted ADR。

## 三、自省

- [x] **目标偏离**：只统一 build ownership，并关闭由同一 JaCoCo lifecycle 所有权导致的 install 回归。
- [x] **认知负担**：Root 管通用版本/default；只有 Core/tfi-all 两个 coverage owner 保留明确 delta。
- [x] **比例失调**：一个 focused DOM test + 真实 Maven model/gate，未引入 Maven-model 测试依赖或脚本框架。
- [x] **ROI**：消除 Root/Core 版本双源，同时防止 strict gate 静默变宽。
- [x] **洁癖检测**：不整理无关 POM 排版，不迁移 japicmp/JMH/module-specific execution owner。
- [x] **局部 vs 全局**：三份 POM 是关闭当前 install 回归所需的最小完整范围。
- [x] **过度设计**：不创建第二 parent、不新增 Maven extension、不把 effective POM 提交为长期合同。

**结论**：设计范围与实施均已闭环；测试所有权和 Portfolio 最终门禁继续由后继卡处理。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| GRD-01 回填 | 只更新 BLD owner card | 同步更新 GRD-01 fresh install 证据 | 同一 JaCoCo lifecycle 修复直接关闭其当前回归 |
| 历史 flatten 树 | 只验证新输出 | 内容/SHA/symlink 前置确认后一次性删除 `tfi-flow-core/Users/` | 旧绝对路径镜像会继续制造“哪个 POM 有效”的歧义 |
| perf 激活证据 | `-Dtfi.perf.enabled=true` 同时证明 Parent/Core profiles | 改用显式 `-Pperf` 证明双 profile merge | `-D` 只激活 Core property profile；两条入口均保留 |
| reactor gate | 预期 `./mvnw clean verify` 全绿 | BLD 配置 gate 使用七模块 `-DskipTests clean verify`；full-test 命令保留为红色诊断 | 19 个失败可被未修改的 tfi-all 测试独立复现，属于 TST-01/DOC-01，不能通过改 POM 或放宽门禁处理 |
| TST-01 owner 回填 | 保持原 5 个剩余 suite | 增补 6 个 fresh drift 文件，当前待处理总数为 11 | 不回填会让 full-test 失败没有唯一解除路径 |

### 检查点结果

- [x] CP-1：Root versions 为 `3.4.1/3.6.0/4.8.6.6/0.8.12/3.5.3`，IDs 为 3 个精确值。
- [x] CP-2：Core effective 为 3 executions / 0 exclusions / `0.80` / `0.70`。
- [x] CP-3：Checkstyle/SpotBugs strict flags 均为 true，Root loose-only 字段未合并。
- [x] CP-4：Enforcer 四类 banned coordinates 与 `fail=true` 由 source contract 和真实执行共同证明。
- [x] CP-5：Core default false；Core/tfi-all skip variants 均为 true。
- [x] CP-6：`-Pperf` 为 Surefire true / JMH dependency 1 / benchmark exclusion 0。
- [x] CP-7：Core source flatten 0、effective executions 2、invalid Checkstyle parameter 0。
- [x] CP-8：runtime tree、711 Core、双 API、standard validate/install、七模块配置 gate 均成功；full-test 债务精确归类。
- [x] CP-9：错误 flatten 树删除后，所有 lifecycle 与最终 absence gate 均未重新生成。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | source/effective/真实 Maven 三层合同一致；Core 711/711 与 strict gates 通过 |
| 完整性 | 25 /25 | Root/Core/tfi-all、standard install、API、runtime、flatten 与 review 证据逐项闭环 |
| 可维护性 | 25 /25 | Root 唯一版本 owner，Core 只保留 strict delta，两个 coverage owner 的 profile 完全同构 |
| 风险控制 | 25 /25 | 未降低门禁；Portfolio 红色结果逐文件路由，未伪称 full root 绿色 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST | BLD01-R3 | 规格把 BLD 构建配置 gate 与已知 Portfolio full-test debt 混成同一个完成条件 | spec、plan、TASK/INDEX | 已拆成七模块配置 gate 与 TST/DOC final gate；红色结果保留 |
| SHOULD | BLD01-R1 | Core source contract 逐个查找 JaCoCo IDs，但未拒绝额外第四 execution | `BuildConfigurationContractTests.java` | 增加 `containsExactly` inventory；4/4 复验通过 |
| SHOULD | BLD01-R2 | Core profiles 注释仍把整个区域称为 JMH profile | `tfi-flow-core/pom.xml` | 改为 module-specific profiles |
| 结论 | BLD01-R4 | 未发现其他所有权、merge、安全解析、测试职责或注释缺陷 | 三 POM + 新测试 | `0 unresolved MUST / 0 unresolved SHOULD` |

## 六、完成审核

**审核通过。** Root 已是 canonical plugin version/default owner，Core 继承 Repository Parent 且 strict delta 未变宽，
Core/tfi-all 的 skip-tests coverage 生命周期已恢复。Fresh 证据为 focused 4/4、Core 711/711、Checkstyle 0、
SpotBugs 0、JaCoCo 80/70、双 API、标准 validate/install 七模块成功、`-DskipTests clean verify` 七模块成功。

无 `skipTests` 的 `./mvnw clean verify` 已真实执行，结果为 tfi-all 2976 tests / 9 failure / 10 error：5 条旧 V1 Map
断言、7 条已删除 Console 私有方法反射、6 条旧 Context 生命周期断言、1 条 freeze 后 Provider 注册断言。
这些结果可由四条 focused tfi-all 命令稳定复现，未涉及本卡修改的 POM 或新测试；它们已写回 TST-01，最终
Portfolio 绿色仍归 DOC-01。本卡不宣称 full root 绿色，也不隐藏该残余债务。

CI、production Java、既有业务测试、breaking manifest 与五份受保护计划保持不变；历史错误 flatten 树未重新生成。
外部 `3.0.0` artifact 风险仍按用户豁免保留。下一张且唯一下一步是 `TASK-BLD-02`。
