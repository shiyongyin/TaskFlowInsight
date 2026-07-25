# ADR-010: Nested Depth 能力处置

Status: ACCEPTED

G4_STATUS=ACCEPTED
G4_DECISION=DELETE_DISCONNECTED_NESTED_DEPTH

## Intent

处理当前嵌套深度配置与真实任务生命周期脱节的问题，避免维护一个看似可配置、实际不控制核心路径的
诊断状态源。

## Context

`NestedStageTracker/ZeroLeakThreadLocalManager` 维护嵌套诊断深度，但目标 Context/task stack 已由
`ManagedThreadContext` 持有。若两套深度计数不能建立身份和生命周期对应关系，保留配置会误导调用方。

## Decision

接受 `DELETE_DISCONNECTED_NESTED_DEPTH`：

- 在 4.0 删除与真实 task stack 断开的 nested-depth 能力、配置和镜像计数。
- 深度、LIFO 与清理约束只由 `ManagedThreadContext` 的真实 task stack 提供；诊断读取必须从该
  owner 派生，不能维护第二份 ThreadLocal 状态。

选择删除是因为独立深度计数没有可证明的业务身份对应关系，保留它只会让配置看似有效却不约束真实
任务生命周期。若未来确需公开深度读视图，必须通过新 ADR 从唯一 task stack 派生。

## Consequences

- CTX-05 可以消费该决策，但必须先修订为 G1 的 4.0 breaking 路线。
- 删除必须精确列出 API、配置键和测试迁移，不得以包级 japicmp exclusion 掩盖未声明符号。
- 运行时只保留一个 task stack/depth 状态源，诊断 facade 不再维护断开的镜像计数。

## Rollback

能力删除前如需改变方向，必须通过新 ADR 修订。删除批次后若需恢复读视图，只能从唯一 task stack
派生；禁止恢复独立 ThreadLocal/registry。回滚需验证嵌套、异常、清理和深度上限路径。

## Verification

1. `AdrDecisionContractTests` 验证 G4 唯一且属于闭集。
2. CTX-05 精确 API/config inventory、breaking 清单与 japicmp 证据验证删除边界。
3. Context 嵌套测试验证唯一 stack 的深度、LIFO、异常清理和无残留状态。
