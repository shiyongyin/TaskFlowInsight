# tfi-compare 发布加固任务索引

> **specStatus**：`READY_FOR_IMPLEMENTATION`
>
> **deliveryStatus**：`COMPLETE`
>
> **reviewStatus**：`PASS`
>
> **执行模式**：严格串行；本任务包不授权 publish、push、tag、deploy 或 KernelDiff 实现

本任务包验证 `tfi-compare` 的运行正确性、Spring 组合、配置兼容、容量与保密边界、最终 Maven 制品、
性能和供应链安全。历史 [SSOT 收敛任务包](../ssot-convergence-task/INDEX.md) 保持完成态。

## 1. 最小化决策

`CMP-HRD-05` 只负责最终集成验证。原 05A/05B assignment、双角色、Markdown terminal report 和状态绑定方案
超出“验证 tfi-compare 是否存在真实问题”的目标，已由最小最终集成卡取代。

生产签名、仓库 staging 和组织审批可以在实际发布流程中执行，但不再阻塞工程验证或 Compare Core 抽取授权。
已有 release evidence 工具可以保留供发布人员选择使用，不构成本任务包的完成条件。

## 2. 目标与边界

1. `tfi-kernel` 与 `tfi-compare` 继续作为两个可分别消费的项目/制品；本任务包不合并内核。
2. 当前 `tfi-compare -> tfi-flow-core:compile` 合法；Compare Core 抽取由独立 EXT 任务包实施。
3. `tfi.compare.enabled` 是 Compare 的 canonical 开关；无 owner 的 `tfi.enabled` 不得重新成为 Compare alias。
4. fixed 3.0 API baseline、checksum 和 API inventory 是历史事实，不得为使验证变绿而重录。
5. 不新增 selector、global holder、runtime fallback、第二 snapshot/diff engine 或额外发布状态机。
6. 失败只能通过修复可复现根因关闭，不得删除测试、放宽规则、扩大 exclusion 或降低性能阈值。

抽取 ADR 已通过 `EXTRACTION_ADR_DECOUPLED_FROM_KDF_D1` 与 KDF-D1 解耦。EXT-05 仍持有一次性的
`ADD_CORE_AND_RECONCILE_UNION_ONCE` static baseline authority，仅用于模块 owner 迁移后的 finding union 对账。

## 3. 状态协议

| 字段 | 合法值 |
|---|---|
| `specStatus`（仅 INDEX） | `READY_FOR_RED_TEAM`、`READY_FOR_IMPLEMENTATION` |
| `deliveryStatus` | `PLANNED`、`IN_PROGRESS`、`COMPLETE` |
| `reviewStatus` | `PENDING`、`PASS`、`FAIL` |

machine line 使用以下固定语法：

```text
> **<field>**：`<VALUE>`
```

实现完成后才能设置 `deliveryStatus=COMPLETE`；最终 findings-first 审查没有 unresolved MUST/HIGH 后才能设置
`reviewStatus=PASS`。实际发布 verdict 不在本任务包中生成。

## 4. 严格串行任务

| 卡号 | 标题 | 前置 | 当前状态 |
|---|---|---|---|
| [CMP-HRD-01](TASK-CMP-HRD-01.md) | 退役 legacy snapshot 并关闭强 Class-key cache | 无 | `COMPLETE/PASS` |
| [CMP-HRD-02](TASK-CMP-HRD-02.md) | 修复 Boot observation 顺序并建立 typed decorator | HRD-01 | `COMPLETE/PASS` |
| [CMP-HRD-03](TASK-CMP-HRD-03.md) | 建立父子 Context 本层解析与 tracking prerequisite | HRD-02 | `COMPLETE/PASS` |
| [CMP-HRD-04](TASK-CMP-HRD-04.md) | 退役无 owner enable key 并闭合 starter/profile 合同 | HRD-03 | `COMPLETE/PASS` |
| [CMP-HRD-06](TASK-CMP-HRD-06.md) | 闭合容量与运行时保密边界 | HRD-04 | `COMPLETE/PASS` |
| [CMP-HRD-07](TASK-CMP-HRD-07.md) | 建立最终可发布制品闭集 | HRD-06 | `COMPLETE/PASS` |
| [CMP-HRD-08](TASK-CMP-HRD-08.md) | 建立供应链安全与 provenance 检查 | HRD-07 | `COMPLETE/PASS` |
| [CMP-HRD-05](TASK-CMP-HRD-05.md) | 最小最终集成验证 | HRD-01..04/06..08 | `COMPLETE/PASS` |

```text
CMP-HRD-01 -> 02 -> 03 -> 04 -> 06 -> 07 -> 08 -> 05(COMPLETE/PASS)
                                                     -> EXT-00B implementation authorization
```

## 5. 已关闭的验证面

| 验证面 | Owner 卡 | 结果 |
|---|---|---|
| legacy 类型、缓存和 API baseline | HRD-01 | `COMPLETE/PASS` |
| Boot observation 与 typed decorator | HRD-02 | `COMPLETE/PASS` |
| 父子 Context、tracking 和生命周期隔离 | HRD-03 | `COMPLETE/PASS` |
| 配置 alias、profile 启动和 starter 消费 | HRD-04 | `COMPLETE/PASS` |
| 容量、敏感日志和 21 个性能 workload | HRD-06 | `COMPLETE/PASS` |
| publishable artifacts、回滚和 compatibility matrix | HRD-07 | `COMPLETE/PASS` |
| SBOM、license、CVE、secret canary 和 provenance | HRD-08 | `COMPLETE/PASS` |

## 6. 最终验收入口

```bash
./mvnw -pl tfi-kernel,tfi-flow-core,tfi-compare,tfi-flow-spring-starter,\
tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples -am clean verify

bash scripts/verify_tfi_compare_artifact_consumers.sh \
  --fixture publish-layout --version 4.0.0
```

两个命令必须基于当前最终工作区成功。`tfi-compare-core` 仍由独立 EXT-02B/04 的覆盖率、owner 迁移和
制品门禁负责，不在 HRD-05 中降低阈值或冒充已完成。定向测试只用于排障，不能替代最终门禁。

## 7. 发布边界

本任务包 `COMPLETE/PASS` 只表示当前代码和 Maven 制品未发现 unresolved MUST/HIGH，并允许进入后续抽取授权。
它不代表已经发布，也不授权凭据使用、远程上传、版本标签、部署或 KernelDiff。
