# ADR-009: Session 兼容 Bridge

Status: ACCEPTED

SESSION_BRIDGE_STATUS=ACCEPTED
SESSION_BRIDGE_DECISION=MANAGER_CONTEXT_ADAPTER_WITH_EXTERNAL_TERMINAL_RELEASE

## Intent

定义 `Session.getCurrent/activate/deactivate` 与新 Context owner 的兼容桥，保证公共静态入口继续可用，
但不让 legacy registry 成为第二个运行时当前态 SSOT。

## Context

G1 已选择 4.0 breaking-major，因此保留 Session 静态入口不是对全部 3.x 表面的兼容承诺。该入口
作为 G2 最新所有权模型中的受控 adapter，必须把当前态、活动索引和终态释放统一委托给
`SafeContextManager/ManagedThreadContext`。Bridge 是 G2 的派生结果，不是独立 gate。

## Decision

随 ADR-006 的 `ONE_CONTEXT_PER_SESSION_LINKED_CHILD` 同步接受：

- `MANAGER_CONTEXT_ADAPTER_WITH_EXTERNAL_TERMINAL_RELEASE`：legacy 静态 API 仅适配 manager context；
  外部 owner 负责 Session terminal publish/release，child/adapter 不创建第二 registry 或重复终止。

## Consequences

- CTX-02 与依赖 Session bridge 的迁移可以消费该决策，但仍须先完成 G1 的 4.0 任务卡修订。
- Session 静态入口是最新契约中明确保留的 adapter，不代表其他 3.x API 获得源码兼容承诺；其
  identity、计数、清理和终态均从唯一 manager owner 派生。
- adapter 必须区分 external terminal release，避免 deactivate/child close 提前完成 Session。
- 若后续决定从 4.0 最终契约删除该 adapter，必须先用新 ADR 修订 G2 派生关系，不能仅凭 G1 删除。

## Rollback

运行时消费前如需改变 bridge，必须与 G2 一起通过新 ADR 修订。消费后回滚仍不得恢复强引用全局
registry；必须验证 current identity、一次注销、owner/child close 顺序与泄漏清理。

## Verification

1. `AdrDecisionContractTests` 强制本 ADR 与 ADR-006 推荐 G2 双向同步。
2. Session/Context 测试验证 legacy API 与 manager current 返回同一 Session identity。
3. 终态与并发测试验证 adapter 不重复完成、不重复注销且 external owner 最终释放。
