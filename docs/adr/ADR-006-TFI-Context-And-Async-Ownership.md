# ADR-006: Context 与异步所有权

Status: ACCEPTED

G2_STATUS=ACCEPTED
G2_DECISION=ONE_CONTEXT_PER_SESSION_LINKED_CHILD

## Intent

确定 Session、线程 Context、异步传播和 child task 的所有权模型，使当前态、关闭责任与恢复语义
只有一个权威来源，避免多个 ThreadLocal/registry 对同一 Session 作出冲突判断。

## Context

目标架构要求 `SafeContextManager` 成为 Context 当前态、活动索引与调度 owner，
`ManagedThreadContext` 持有一个 Session 和一个 LIFO task stack。异步执行需要在父任务与子任务之间
保留可追溯关系，同时不能让恢复出来的 child context 终止父 Session。

## Decision

接受 `ONE_CONTEXT_PER_SESSION_LINKED_CHILD`：

- 一个 Session 对应一个 owner context；异步恢复创建与父链关联的 child context。
- child 只释放自身传播/任务状态，Session 终态仍由外部 owner 发布。
- `SafeContextManager` 是 Context registry、current ThreadLocal 与调度生命周期的唯一 owner；
  `ManagedThreadContext` 只持有该 Session 的 LIFO task stack。

ADR-009 的 Session adapter 是本决策的派生结果，必须同步接受，不能成为独立第七个 Gate。

## Consequences

- LFC-03/04/05 与 CTX-01..04/06 可以消费该所有权模型，但仍须满足各自其他依赖和 G1 的 4.0
  任务卡修订要求。
- ADR-009 同步接受其唯一派生 bridge，不能成为独立第七个用户选择。
- `SafeContextManager` 继续是唯一 Context registry/ThreadLocal/scheduler owner；兼容 facade 只能委托。
- child context 的恢复、关闭和异常路径必须证明不会重复完成或提前释放父 Session。

## Rollback

运行时迁移前如需改变模型，必须通过新 ADR 修订本决策并同步修订 ADR-009。迁移后回滚仍必须保持
单一 owner，且先通过生命周期回归、活动计数与泄漏检测测试；禁止重新引入第二套
current-context ThreadLocal。

## Verification

1. `AdrDecisionContractTests` 验证 G2 与 ADR-009 bridge 双向同步。
2. 生命周期测试验证一次终态发布、一次注销和 child close 不终止父 Session。
3. 异步传播测试验证嵌套、异常、取消和线程复用后 Context 均恢复且无泄漏。
