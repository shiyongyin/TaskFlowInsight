# ADR-005: TFI Flow Core 兼容与删除政策

Status: ACCEPTED

G1_STATUS=ACCEPTED
G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL

## Intent

为 public API、编译期常量和 V1 schema 建立唯一的弃用与删除政策，避免实现者仅凭“internal”包名
或主版本想象直接删除已经进入发布物的符号。

## Context

当前仓库仍处于 `3.1.0-SNAPSHOT`，但用户已明确选择不兼容旧契约。Maven Central 尚不存在正式
`3.0.0` 基线，本地 JAR 只用于识别旧公共表面，不构成必须保持兼容的外部不可变发布证据。
V1 Console/JSON/Map 是否调整由 G3 单独决定，G1 不顺带授权修改导出语义。

## Decision

接受 `BREAKING_MAJOR_4_DIRECT_REMOVAL`：目标版本转为 `4.0.0-SNAPSHOT`，以最终确认的新契约为准，
不再为 `3.0.0` 保留源码或二进制兼容窗口。删除动作仍必须精确列出符号和影响面，主版本升级不是
跳过设计、测试和迁移记录的授权。

所有依赖 N/N+1 弃用窗口、`since=3.1.0` 或 `removeNotBefore=3.3.0` 的任务卡必须先修订为 4.0
路线；旧任务卡不能直接解释成 breaking-major 删除授权。

## Consequences

- 根 POM、模块版本、CI 基线和发布文档必须统一切换到 4.0 版本轴后，才能实施运行时删除。
- GRD-06、CTX-02/05/07、EXP-08/10 的 3.x 兼容窗口设计失效，修订完成前继续阻塞。
- 任一删除都必须由精确 symbol 清单、消费者编译测试和 japicmp/schema 差异证据共同授权；禁止
  通过关闭兼容门禁掩盖未声明的破坏。
- `ConfigDefaults` 与 `ConfigDefaults$Keys` 的内部政策例外仍需分别精确登记，禁止 `internal.*`。
- G1 不决定 V1 导出 schema、Context ownership 或 Provider 选择，相关行为继续等待各自 Gate。

## Rollback

4.0 删除尚未进入发布物时，可通过新的 ADR 修订恢复 3.x 兼容路线。删除已被 4.0 发布物消费后，
回滚必须发布新版本并恢复必要 adapter；禁止静默改写本文件机器行或复用旧版本号覆盖发布物。

## Verification

1. `AdrDecisionContractTests` 验证 G1 status/decision 唯一且属于闭集。
2. 版本与任务卡校验必须拒绝继续消费 3.1/3.2/3.3 弃用窗口。
3. Core 与 `TFI` japicmp 必须把 3.0 差异限制在显式 breaking 清单内，消费者 reactor 必须按 4.0
   新契约编译通过。
4. 在 G3 接受其他分支前，V1 golden 必须持续通过。
