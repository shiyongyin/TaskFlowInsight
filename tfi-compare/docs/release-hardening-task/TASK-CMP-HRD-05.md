# TASK-CMP-HRD-05：最小最终集成验证

> **deliveryStatus**：`COMPLETE`
>
> **reviewStatus**：`PASS`
>
> **依赖**：`TASK-CMP-HRD-01..04/06..08` 均为 `COMPLETE/PASS`
>
> **后续**：验证通过后允许进入 Compare Core 抽取的实施授权卡；实际发布审批仍由外部发布流程负责

## 一、范围决策

本卡只回答一个问题：前七张 owner 卡完成后，当前 `tfi-compare` 代码和最终 Maven 制品是否仍有可复现的
MUST/HIGH 问题。

原 05A/05B 方案把最终集成验证扩展成 assignment、双角色、Markdown terminal report 和多份状态副本组成的
发布治理系统。该方案超出最小生产中间件验证目标，现由本卡取代，不再作为工程完成或后续抽取的硬门。

以下现有能力可以继续用于真正的发布操作，但不属于本卡 DoD：

- `scripts/prepare_tfi_compare_release_evidence.sh` 的外部 assignment/policy 模式；
- 由发布负责人和独立复核人执行的双人发布审批；
- 生产 PGP/Sigstore signer、仓库 staging、push/tag/deploy。

## 二、目标（DoD）

- [x] HRD-01..04/06..08 均为 `COMPLETE/PASS`，各自负责的运行时、Spring、配置、容量、性能、制品和安全合同已关闭。
- [x] HRD-07 已证明最终制品闭集、`3.0 -> 4.0 -> 3.0` 语义回滚和 mixed-version 精确拒绝。
- [x] HRD-08 已证明 SBOM、license、CVE、secret canary、toolchain/provenance 合同及全 Reactor 验证。
- [x] 当前发布加固模块集合执行一次 `clean verify`，所有模块成功；尚未完成 EXT-02B/04 的
  `tfi-compare-core` 不冒充本卡发布制品。
- [x] 当前 `4.0.0` 打包制品通过隔离仓库 publish-layout、回滚和兼容矩阵验证。
- [x] 最终 findings-first 审查为 0 unresolved MUST/HIGH；只记录真实代码或构建问题。

## 三、最小验证命令

```bash
./mvnw -pl tfi-kernel,tfi-flow-core,tfi-compare,tfi-flow-spring-starter,\
tfi-compare-spring-starter,tfi-ops-spring,tfi-all,tfi-examples -am clean verify

bash scripts/verify_tfi_compare_artifact_consumers.sh \
  --fixture publish-layout --version 4.0.0
```

定向排障可以运行更小测试集，但不得用定向结果替代上述两个最终门禁。

## 四、边界

### In scope

- 修复上述两个命令暴露的编译、测试、制品、API、依赖、安全或兼容问题；
- 保留前七张卡已经证明有效的测试与发布检查；
- 对本卡状态和必要证据做一次最终回填。

### Out of scope

- 新增状态机、报告 parser、gate registry、XRT registry 或验证器的验证器；
- 新增外部 identity、assignment、policy、签名人或审批结论；
- publish、staging、push、tag、deploy；
- 为追求形式完整而修改运行时、公共 API、性能阈值或历史基线。

## 五、停止与回退

- 验证发现真实问题时，只修复可复现根因并增加最小回归测试；不得扩大治理范围。
- 失败属于外部仓库凭据、签名或审批时，记录为发布操作前置，不阻塞本卡工程结论。
- 若最终门禁无法稳定复现，本卡保持 `IN_PROGRESS/PENDING`，不得填写 `COMPLETE/PASS`。

## 六、完成记录

| 项目 | 结果 |
|---|---|
| 发布加固模块集合 `clean verify` | `PASS`；9/9 Reactor 模块 `BUILD SUCCESS`，2026-07-18，耗时 02:56 |
| 最终制品隔离验证 | `PASS`；`PUBLISH_LAYOUT_OK`、`ROLLBACK_LAYOUT_OK`、41 项 `COMPATIBILITY_MATRIX_OK` |
| Code Review | `PASS`；0 unresolved MUST/HIGH；修复 1 个 `ContainerEvents` PMD 新指纹且未修改基线 |
