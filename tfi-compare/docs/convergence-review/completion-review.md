# TFI-Compare SSOT 收敛完成审核

**审核状态**：PASS  
**审核日期**：2026-07-15  
**审核方式**：在实现与首轮验证完成后进行 inline 独立二次复核  
**审核范围**：Gate、ADR、manifest、架构 owner、两张追踪矩阵、消费者、回滚闭集、长期文档与发布边界

## 1. 结论

审核结论：**0 unresolved MUST/P1**。

当前实现、消费者和文档与 ADR-011..014 的 accepted decision 一致。所有业务与质量门禁均使用当前工作区 fresh 执行；
`CompareCompletionAuditTests` 只在任务卡与 INDEX 原子闭合后成立，因此先以唯一排除项完成预关闭 module/portfolio 验证，
再由终态复验覆盖该合同。任一终态复验失败都必须撤销完成状态并回到对应 owner，不得改门禁制造绿色。

## 2. Fresh Gate 证据

| Gate | 命令/证据 | 结果 |
|---|---|---|
| documentation | `CompareDocumentationContractTests` 与 strict SSOT lint | PASS；长期文档职责、链接、禁用快照和退役入口均闭合 |
| API/manifest | focused inventory/manifest/resource contracts；`-Papi-compat verify -DskipTests` | PASS；Japicmp 与五类 manifest 同步 |
| architecture | `CompareArchitectureContractTests`、`CompareBuildConfigurationContractTests` | PASS；依赖、owner、CI 与 resource 边界成立 |
| module verify | `./mvnw -pl tfi-compare clean verify` | PASS；不排除任何测试，Compare `1106/1106`通过，覆盖率与静态分析门禁通过，SpotBugs为零 |
| targeted consumers | workflow 指定的 Flow/Starter/Ops/All/Examples focused contracts | PASS；当前 reactor 产物上的直接行为消费者通过 |
| all-consumer package | 六个 Compare 相关模块加依赖闭集 `-am -DskipTests package` | PASS；reactor 八项均成功 |
| strict routing perf | fresh JMH 报告后运行 strict gate | PASS；routing `2787.371 ns/op`，legacy `2820.470 ns/op`，相对变化约 `-1.17%` |
| portfolio verify | `./mvnw clean verify` | PASS；不排除任何测试，reactor八项均成功 |

初次直接执行完整 module verify 时，业务、架构与文档合同均通过，仅三个 completion 终态断言按设计失败：本卡状态、
completion review 文件和 INDEX 完成 token。这证明预关闭排除没有隐藏其他测试失败。终态闭合后，planning 合同补充了
`COMPLETE_W7_CMP_DOC_01`无活动任务回归，并从该终态构造实施中场景，继续约束标题与唯一进行中任务一致；最终 module 与
portfolio verify 均在不排除任何测试的条件下通过。

## 3. Gate 与 ADR Owner

- `CMP_G1`、`CMP_G2` 仅由 ADR-011 持有。
- `CMP_G3` 仅由 ADR-012 持有。
- `CMP_G4`、`CMP_G5`、`CMP_G6` 仅由 ADR-013 持有。
- `CMP_G7` 仅由 ADR-014 持有。
- machine status/decision 共保持七组 accepted 值；本任务只追加 `Implementation Evidence`，未改 accepted token。

## 4. 矩阵与 Manifest

- INDEX 的设计追踪矩阵覆盖全部任务卡、核心不变量、合同测试和 manifest kind；planning traceability 与 architecture 合同通过。
- INDEX 的消费者影响矩阵覆盖 Core、Flow starter、Compare、Compare starter、Ops、All、Examples、CI 与 benchmark；targeted consumers
  和 all-consumer package 使用当前 reactor 产物通过。
- `breaking-changes-v4.json` 仍是 API、RESOURCE、CONFIG、SCHEMA、BEHAVIOR 的唯一清单；inventory、coverage、consumer test 与
  Japicmp exclusion 均由双向合同验证。
- DOC 只消费交付证据，不新增 breaking entry；QLT/DOC 不夺取前序 runtime owner。

## 5. Owner 与安全边界

- `ComparePolicy -> CompareRuntime -> CompareEngine -> RequestLocalCompareKernel` 保持单向且每个 Runtime 只有一个 Engine。
- Tracking action 只由 `TrackingExecutor` 编排；provider 只创建 `TrackingBatchScope`。
- Projection 只由 `CompareProjectionFactory` 构造；formatter 不读取 raw result 或业务对象。
- Core `ProviderRegistry` 仍是 JVM provider 唯一 owner；Compare starter 无 register、unregister、load 或 clear mutation。
- Spring Runtime 属于当前 context，Ops 只装饰最终 Engine；指标与 health 不保存业务历史。
- `scoring-report.md` 已删除，生产源码树无第二套 Markdown 入口，长期文档无退役能力或瞬时质量快照。

## 6. Finding 闭环

| 级别 | 编号 | Finding | 处置 |
|---|---|---|---|
| MUST | DOC-R01 | 五份长期文档仍混入旧版本能力、评分和构建快照 | 重写为当前 SSOT、导航、产品、验证和运行单一职责文档；合同与 lint 通过 |
| MUST | DOC-R02 | accepted ADR 缺当前实现证据且总体设计链接失效 | ADR-011..014 追加实现合同与任务索引链接；相对链接合同通过 |
| SHOULD | DOC-R03 | 初稿未显式覆盖验收、应急、迁移和无状态停机边界 | 补齐稳定边界，四份职责文档 strict SSOT lint 无 warning |
| INFO | DOC-R04 | completion 合同依赖自身终态，无法在关闭前随全量 verify 同时为绿 | 采用预关闭唯一排除加终态完整复验，不放宽测试或质量阈值 |

无开放 finding；**0 unresolved MUST/P1**。

## 7. 回滚闭集

回滚闭集已逐卡复核：结果/kernel、集合、Tracking、Projection、Spring、Ops、质量和文档均要求 owner、直接消费者、合同与 manifest
按依赖逆序共同回退。不得只恢复旧 API、平行对象图、弱 masking、Spring Registry mutation 或会改变真值的快捷路径。

DOC 回滚只恢复当前文档与完成状态；若代码、测试或门禁失败，必须回到对应 owner 卡处理，不能通过修改文档宣称完成。

## 8. 发布边界

本任务只完成代码库内实现、验证与文档闭环，**未执行发布或push**，也未创建 release、上传制品或修改远程状态。
