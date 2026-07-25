# TASK-DOC-01：收敛架构文档、CI 与最终完成证据

> **定位**：将最终代码事实写回唯一架构入口，并执行整个 portfolio 的完成审计。
> **状态**：完成（2026-07-12；98/100）
> **审核状态**：审核通过（2026-07-12；fresh 文档/结构/Maven 证据与最终独立复核均完成，0 unresolved MUST / SHOULD）
> **依赖**：`TASK-BLD-01`、`TASK-BLD-02`、`TASK-TST-01`、`TASK-PRV-06`、`TASK-EXP-09`、生命周期/Context 非延后卡均已完成
> **架构来源**：master Task 4 / `B4` / Wave 5；`TASK-CTX-07` 消费同一最终证据

---

## 一、核心（设计时填）

### 背景

当前 AGENTS 架构路径失效，历史架构资料与实现冲突，模块文档维护了已漂移的质量数字，Core workflow
仍用 `continue-on-error` 忽略 Checkstyle/PMD 命令失败。本卡把 `design-doc.md` 固化为当前 Core 架构
SSOT、`index.md` 作为导航，并以生成物而非手工数字提供质量证据。PMD findings 继续遵循父 POM 已接受的
report baseline；本卡不把该 baseline 误写为零告警或 Maven finding hard gate。

### 目标（DoD）

- [x] `AGENTS.md` 指向 `tfi-flow-core/docs/design-doc.md`，历史 `docs/product/architecture/**` 明确仅作背景。
- [x] design/PRD/test/ops 描述最终 lifecycle、Provider、Export、metrics 与删除语义；compatibility policy 与 exact removals 分别链接 ADR-005 和 breaking manifest，不创建第二 policy 文档。
- [x] 删除长期文档中的手工测试数量、覆盖率百分比和静态 finding 快照，改为 CI 产物与查询方式；
      POM 内机器阈值保持唯一 owner。
- [x] core CI 无 `continue-on-error: true`，上传 SpotBugs、Checkstyle、PMD 报告并保留 14 天。
- [x] 全部 downstream 命令包含 `tfi-examples`。
- [x] API/resource/schema/架构 focused gate、Core API profile、下游 package、全仓 clean verify 全部通过。
- [x] 将 API/package/root 与 B1-B4 完成证据交接给 `TASK-CTX-07`；CTX-07 在本卡完成后独立关闭，
      不存在 E7 maturity rerun。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 文档与代码一致 | 高 | 最终 SSOT 只写机器可证不变量 |
| CI evidence | 高 | workflow 不忽略分析命令失败；PMD finding policy 精确继承父级 baseline |
| 完成审计 | 高 | 逐项证明而非“未发现问题” |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 当前架构 SSOT | `tfi-flow-core/docs/design-doc.md` | 跟随模块版本与实现 | 继续维护三套架构入口 |
| 质量数据 | CI artifact | 自动生成、不会手工漂移 | Markdown 中固定数字 |
| Console 命名 | snapshot-only 诊断文本 + 显式 style/timestamp options | ADR-008 不定义 Console schema | “Console V1/V2” |

### 跨卡不变量

- 文档只描述已交付行为，不把 PROPOSED 分支写成现状。
- G6 保持已接受的 `VERSIONED_TRUST_CORRECTION`，Provider SSOT 以唯一 Registry engine 为准。
- `TaskDurationCache` 只由 `TASK-EXP-08` 按 4.0 exact removal 删除；本卡只消费其完成证据，不保留 E7 状态。

## 二、执行（设计时填）

### 前置准备

汇总每张任务卡的 DoD、偏差、代码行号和 review 回填；未完成项不能在文档中宣称完成。

### 核心步骤

1. 修改 `AGENTS.md`、两份历史 product README、Core `design-doc.md`、`index.md`、`prd.md`、
   `test-plan.md`、`ops-doc.md`，并给历史 research 增加 superseded banner；直接链接 ADR-005 与
   breaking manifest，且不创建 `deprecation-policy.md`。
2. 修改 `.github/workflows/tfi-flow-core-ci.yml`：删除两个 `continue-on-error`，上传
   `spotbugsXml.xml`、`checkstyle-result.xml`、`pmd.xml`，artifact 名为
   `tfi-flow-core-static-analysis-java21`，保留 14 天。
3. 执行 contract/architecture focused gate 与 Core API profile。
4. 执行完整下游编译（含 examples）和全仓 clean verify。
5. 执行 owner 搜索：facade Provider cache、`THREAD_SESSIONS`、第二 Context scheduler 与所有 production
   `TaskDurationCache` 引用必须为零；`resolvedProviders` 必须只由 `ProviderRegistryEngine` 持有。

```bash
./mvnw -pl tfi-flow-core,tfi-all -am test \
  -Dtest=BuildConfigurationContractTests,AdrDecisionContractTests,BreakingChangeManifestTests,PublicConstantCompatibilityTests,CoreServiceLoaderContractTests,ExportV2ContractTests,FlowCoreArchitectureBoundaryTest,ProviderRegistryArchitectureTests,TaskTreeGateArchitectureTests,TFIArchitectureTest,TFIOwnerProviderTests,AllProviderServiceLoaderContractTests,TfiRoutingGoldenTest \
  -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
./mvnw clean verify
```

### 审核检查点

- [x] CP-1：架构 SSOT 每个所有权声明有测试/搜索证据。
- [x] CP-2：所有文档无静态质量数字和失效链接。
- [x] CP-3：CI 上传三份报告，workflow 不含 `continue-on-error`；PMD finding policy 明示为父级 baseline。
- [x] CP-4：全消费者命令包含 examples。
- [x] CP-5：EXP-08 direct removal、DOC evidence 与后继 CTX-07 final audit 的顺序准确；E7/EXP-10 只以取消合同出现。

### 回滚边界

本卡不修改运行时行为。任一完成审计失败时，回到对应 owner 卡修复；禁止修改文档或搜索模式来掩盖失败。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只收敛事实文档与完成证据。
- [x] **认知负担**：一个设计 SSOT、一个入口。
- [x] **比例失调**：机器证据高于文字润色。
- [x] **ROI**：消除架构/CI 漂移。
- [x] **洁癖检测**：不重写历史背景文档。
- [x] **局部 vs 全局**：覆盖所有模块消费者与所有权搜索。
- [x] **过度设计**：复用 Maven/CI 产物，不建文档生成平台。

**结论**：设计通过，全部前置已有真实证据；按 implementation plan 执行并以 fresh gate 决定能否完成。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 历史入口 | 原闭集只列 Core 长期文档 | 增加两份 product README 的最小历史背景 banner | 否则历史 README 仍自称 4.0 当前权威入口 |
| PMD 语义 | “静态分析不可软失败”宽泛表述 | workflow 不忽略命令失败；findings 保持父 POM report baseline | 避免把 1178 条既有 baseline 伪称为零或静默升级 POM policy |
| API CI baseline | API step 复用 setup-java Maven cache | 独立 hard-gate job 使用 `${{ runner.temp }}/tfi-api-baseline-m2`；未发布 baseline 时稳定 fail closed | cache 中来源不明的本地构件可能造成偶然假绿；W0 只豁免本机实施，不授权伪报 clean-CI 成功 |
| 关闭顺序 | DOC 内关闭 CTX-07 | DOC 先交接通用证据，CTX-07 后继独立关闭 | 消除 DOC ↔ CTX 循环依赖 |

### 检查点结果

- [x] CP-1：81 条 focused contract/architecture 与精确 owner 搜索共同证明 Context、Provider、Export
      owner；`resolvedProviders` 只命中 `ProviderRegistryEngine`。
- [x] CP-2：闭集相对链接 0 broken；长期五文档的测试数、覆盖率、finding、体积和性能快照搜索为 0。
- [x] CP-3：workflow YAML 与三 job dependency 解析通过；固定 artifact 三路径、名称、`if: always()`、
      `if-no-files-found: error` 和 14 天保留均通过结构化断言，Core workflow 无 `continue-on-error`。
- [x] CP-4：consumer package 显式包含 `tfi-examples`，parent + 六模块 reactor 7/7 SUCCESS。
- [x] CP-5：DOC 先完成并交接通用证据；CTX-07 只补 Context 专项，EXP-10 保持永久取消。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | public destructive restore、scoped resume、Writer partial output、fatal/background failure 等边界均逐项对照源码 |
| 完整性 | 25 /25 | 7/7 DoD、5/5 CP；文档、workflow、focused/API/package/root 与交接证据完整 |
| 可维护性 | 25 /25 | 一个 design SSOT、一个导航入口；运行数字来自 reports/artifacts，不在长期文档复制 |
| 风险控制 | 23 /25 | CI cache 假绿已消除并 fail closed；正式外部 `3.0.0` baseline 仍是已接受 W0 残余风险 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST | DOC01-R1 | public restore/scoped resume、JSON Writer partial output 与 clean-CI baseline cache 语义存在歧义 | design/PRD/workflow | 按源码拆分合同；API job 使用隔离 Maven repository fail closed |
| SHOULD | DOC01-R2 | Safe manager background failure、Enforcer/source test、Context scan boundedness 描述过宽 | design/test/ops | 精确到异常类型、owner、3-attempt budget 与 `O(activeContexts)` 当前边界 |
| SHOULD | DOC01-R3 | detector restart 首稿错误要求 disabled → enabled | `ops-doc.md` | 对照 `DetectorRuntime.isLive()` 修正为重新 apply 完整 enabled 配置 |
| 通过 | DOC01-R4 | 最终独立复核 | DOC-01 闭集 | 0 MUST / 0 SHOULD |

## 六、完成审核

**审核结论：审核通过。** 本卡未修改 runtime/test/POM/ADR/manifest；当前架构只由 `design-doc.md`
拥有，历史入口已降级，CI 静态报告和 API fail-closed 边界无歧义。fresh 证据如下：

- DOC focused：Core 52 + `tfi-all` 29，共 81，六模块 reactor SUCCESS。
- 仓库内 checksum 保护的 `3.0.0` baseline 已成为 API profile 的只读输入；Core 与 tfi-all 两组 profile
  均在独立空 Maven repository 中退出 0，japicmp reports 非空，baseline checksum 全部通过。
- consumer package：parent + Core/Starter/Compare/Ops/All/Examples 7/7 SUCCESS。
- Portfolio `./mvnw clean verify`：7/7 SUCCESS；test reports 为 Core 726、Starter 111、Compare 3589、
  Ops 70、`tfi-all` 2881（Surefire 2879 + Failsafe 2；76 skipped）、Examples 50，合计 7427、
  0 failure/error。
- Core reports：Checkstyle 0、SpotBugs 0 bugs / 0 errors、JaCoCo gate 通过；PMD 为父 POM 允许的 1178
  条 report baseline，不解释为零告警。
- Markdown links 0 broken、protected plan SHA 5/5、workflow/upload 结构通过；最终独立复核
  0 unresolved MUST / SHOULD。

API/package/root 与 B1-B4 evidence 已由 `TASK-CTX-07` 消费。正式外部 `3.0.0` 从未发布的来源限制仍需
保留在 provenance 说明中，但不再导致 fresh CI 依赖本机 `~/.m2`；不得恢复 EXP-10/E7 maturity 路线。

### 红队整改增量证据（2026-07-12）

- 生命周期边角：跨线程 `Session.activate()` 与直接 `bindLegacySession` 绕行均 fail fast；终态 Session
  不再从当前态 API 返回。
- Provider 故障路径：扫描失败按 epoch/type/ClassLoader 缓存；同一 epoch 不重复类路径扫描、构造器副作用或日志。
- Export 热路径：写锁内只冻结可变树，快照组装与校验移出临界区；新增 JMH 给出单线程、8 线程共享 gate
  与 7 mutation + 1 capture 的可复现实测。
- 发布门禁：3.0 API baseline 已改为仓库内 checksum 保护的只读输入；全部 workflow 删除软失败；静态分析
  使用逐 fingerprint 非回归 gate。最终静态报告为 Checkstyle 0、SpotBugs 0、PMD 1178 baseline findings。
- 路由 JMH 为 enabled 75.707 ns/op、legacy 76.283 ns/op，回归率 -0.755%；严格性能门禁通过。
- 红队原始 P0/P1 阻塞项均已关闭。`SafeContextManager` 与 `ProviderRegistryEngine` 的体积/复杂度保留为
  非阻塞架构 SHOULD，不在发布前高风险拆分并发状态机。
