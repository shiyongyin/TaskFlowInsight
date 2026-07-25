# ADR-012: Compare 内核与集合语义

Status: ACCEPTED
Date: 2026-07-12

CMP_G3_STATUS=ACCEPTED
CMP_G3_DECISION=SINGLE_ENGINE_REQUEST_LOCAL_LOSSLESS_KERNEL

Supersedes:

- [ADR-001](ADR-001-CollectionSummary-First-Strategy.md)。
- [ADR-003](ADR-003-PathMatcherCache-Design-and-ReDoS-Protection.md)。
- [ADR-004](ADR-004-Global-Guardrails-and-Error-Handling.md) 的遍历、缓存与性能护栏部分。

## Intent

建立一个纯Java、请求隔离、确定性且有界的Compare内核，使对象与List/Map/Set/entity比较不靠摘要、截断文本、
hash或`equals()`吞掉真实差异，并让所有资源限制具有唯一计数点和可复制边界测试。

## Context

现状存在多个snapshot/diff owner、static Spring selector、可变缓存、递归遍历、LCS矩阵和summary-first分支。
同一配置在不同strategy中可能重置，entity的identity配对还会被误当作内容相等。旧ADR-001/003的MVP取舍不再满足
4.0的no-false-equal目标。

## Decision

- `CompareRuntime.Builder`是唯一对象图构造API，`CompareEngine`是每个immutable runtime的唯一执行入口；
  SPI、service和facade只能无状态委托。
- built-in plan、snapshot、diff和reducer在调用线程同步执行，不创建executor或parallel stream；visited、frame、pair memo、
  deadline、budget和result builder全部request-local。
- traversal使用显式frame deque。logical depth、deadline、node、element、result、path和entity key均受总体设计§12.3的
  accepted default/hard矩阵约束；任务卡和W0不得自行换值。
- `maxComparedNodes`只消费`SNAPSHOT_NODE/DIFF_NODE/PAIR_CANDIDATE`，`maxElements`只消费
  `CONTAINER_MEMBER`。每个ledger先检查后消费，恰好limit成功，存在第`limit+1`个事件时在执行或回调前停止，counter保持limit。
- direct compare使用一份request-global ledger；Tracking的baseline与after+diff使用两份phase-global ledger，具体归属由ADR-013持有。
- path以parent reference + single typed segment结构共享，只在保留change/issue时按canonical path fact cost编码；
  working set不得退化为`nodes * full-path-length`。
- pattern只在immutable policy构建期由stateless compiler编译。grammar有界且非法输入失败，不提供runtime LRU、preload、clear、
  regex入口或fallback-to-literal。
- summary只能表示bounded output fact，不能证明内容相等。长字符串、集合、数组和对象不能用长度、截断、hash或sample证明EQUAL。
- Map使用present-null区分、稳定typed key和REMOVE/ADD；List使用ordered index或唯一key MOVE；Set/entity使用确定性有界配对。
  duplicate/unresolved key形成typed limitation，不覆盖元素。
- key/ID/非scalar `equals()`只能建立候选配对；配对后仍按descriptor深比较内容。只有closed scalar或显式注册的typed comparator
  可以定义终局相等。
- custom strategy/comparator使用runtime内唯一、grammar合法且总编码长度不超过128的versioned `AlgorithmId`；选中后失败形成problem，
  不fallback。扩展必须线程安全、确定、无外部可变状态且nonblocking。
- built-in算法不得分配随输入`n^2`增长的candidate matrix，也不得在预算压力下静默切换为lossy语义。

## Consequences

- `ObjectSnapshotDeep`、`SnapshotProviders`、`DiffFacade`、旧detector/strategy/cache和collection summary路由必须按Wave退役。
- List、Map、Set、entity、path和ValueSnapshot共享同一kernel owner，不能为单一类型保留平行执行图。
- 预算或deadline达到时发布limitation并由ADR-011 reducer归并；内核不创建线程抢占不返回的custom callback。
- 性能改善以有界复杂度、同轴基准和无lossy shortcut为准，不以保持错误捷径速度为目标。

## Non-goals

- 不修改Core Context、Provider Registry或Export ADR。
- 不支持在线修改runtime；变更配置或扩展时创建新runtime并在外层静默替换完整实例。
- 不提供通用pipeline、mutable comparison context、任意表达式语言或全局cache清理API。

## Rollback

任一Wave回滚必须同时恢复该Wave owner、直接消费者和测试，但不得恢复两个并行snapshot/diff owner或让summary重新证明EQUAL。
已发布4.0后的算法语义变化必须提升`AlgorithmId`版本并通过新ADR/版本发布，不能静默复用旧ID。

## Verification

1. contract tests逐一验证四类budget事件、limit-1/limit/limit+1、root identity零计数和direct/Tracking ledger边界。
2. property tests验证List/Map/Set/entity的确定性、对称性、duplicate/unresolved key和ID-equals后字段变化可见。
3. concurrency tests验证同一runtime并发调用互不污染；architecture tests禁止ThreadLocal/static request state、递归执行图和第二owner。
4. heap/结构测试证明path共享且不存在`nodes * path-length`放大；大容器测试证明无`n^2`matrix与静默lossy切换。
5. custom comparator/strategy测试覆盖selection顺序、失败不fallback、AlgorithmId边界和deadline返回后limitation。

## Links

- [Compare 当前架构 SSOT](../../tfi-compare/docs/design-doc.md)
- [ADR-011](ADR-011-Compare-Compatibility-And-Result-Truth.md)
- [ADR-013](ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md)
- [ADR-014](ADR-014-Compare-Projection-Config-And-Quality.md)

## Implementation Evidence

- [当前架构 SSOT](../../tfi-compare/docs/design-doc.md)记录 Runtime、Engine、request-local kernel 与集合/Entity 最终边界。
- [CompareBudgetLedgerContractTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/compare/internal/CompareBudgetLedgerContractTests.java)
  验证唯一预算消费点和边界停止语义。
- [MapListComparisonPropertyTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/compare/MapListComparisonPropertyTests.java)
  与 [SetEntityComparisonPropertyTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/compare/SetEntityComparisonPropertyTests.java)
  验证集合确定性、无损观察与 ambiguity 证据。
- [实施任务索引](../../tfi-compare/docs/ssot-convergence-task/INDEX.md)保存 KRN/COL owner、消费者与完成证据。
