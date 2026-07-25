# TASK-CMP-KRN-01：建立请求局部Snapshot、Path与预算ledger

> **定位**：替换会泄漏请求状态和静默截断的snapshot主干，为所有算法提供同一有界遍历事实。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-POL-01`
> **后续**：`TASK-CMP-KRN-02`
> **架构来源**：总体设计§9.1-9.4、§12.3、W2；ADR-012
> **消费不变量**：5-8、10-11、20

---

## 一、核心（设计时填）

### 背景

`ObjectSnapshotDeep`把options写入实例字段并使用static深度，路径处理会复制/拼接字符串，node和element预算还可能在不同strategy
中重置。本卡建立显式frame、request-local state、共享parent+segment路径和精确budget ledger；达到限制必须产生typed limitation，
不能用截断/hash/sample证明相等。

### 目标（DoD）

- [x] Compare Runtime的snapshot主路径使用显式frame deque，不依赖JVM递归、static stack depth或ThreadLocal。
- [x] `CompareRequestState`只在单次调用创建，持有visited/frame/deadline/ledger/result accumulator引用且不对外暴露。
- [x] `maxComparedNodes`只消费`SNAPSHOT_NODE/DIFF_NODE/PAIR_CANDIDATE`，`maxElements`只消费`CONTAINER_MEMBER`。
- [x] ledger先检查后消费：limit成功，第limit+1事件在callback前停止，counter保持limit。
- [x] typed path使用parent reference + one segment共享；只有保留change/issue时编码并计入path/result预算。
- [x] cycle/shared DAG/depth/deadline/reflection失败均按ADR-011 reducer形成明确结果，不静默跳过。
- [x] 同一Runtime并发比较无交叉污染，all/examples snapshot/path消费者同步迁移且全消费者编译为绿。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 请求隔离 | 高 | 消除并发污染和泄漏 |
| 预算消费点 | 高 | S-01闭环必须成为代码合同 |
| 路径结构共享 | 高 | 控制深图working set并保持确定性 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 遍历 | 显式frame deque | 深度可控、无StackOverflow | 递归 + stack guard |
| visited | 当前path identity stack +必要pair memo | cycle与shared DAG语义可区分 | 全局seen即equal |
| path | immutable parent+typed segment | 避免nodes×full-path复制 | 遍历中拼display string |

## 二、执行（设计时填）

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/internal/CompareRequestState.java` | 单请求visited/frame/deadline/ledger owner |
| 新增 | 同目录`BudgetLedger.java`、`BudgetEvent.java`、`TraversalFrame.java` | 消费事件与显式遍历frame |
| 新增 | 同目录`SnapshotResult.java`、`RequestLocalSnapshot.java`、`RequestLocalCompareKernel.java` | package-private snapshot facts、迭代捕获与Engine单向接线 |
| 修改 | 同目录`CompareResultAccumulator.java`、新增`ResultFactCost.java`、`ResultIssuePathFit.java` | path/result字符预算、omitted诊断、稳定事实成本与issue路径三态准入合同 |
| 复用 | RES-01交付的`tracking/path/ComparePath.java`、`PathSegment.java`及闭集segment值类型 | typed shared path；本卡只接入request-local working set与预算 |
| 重写 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/snapshot/ObjectSnapshotDeep.java`、`ObjectSnapshot.java`、`SnapshotFacade.java` | request-local iterative capture |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareEngine.java`、`CompareRuntime.java` | 每次调用创建state并传递唯一ledger |
| 迁移测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/snapshot/**`、`tracking/path/**` | 黑盒合同替代实例字段/反射白盒 |
| 迁移消费者 | `tfi-all/src/test/java/com/syy/taskflowinsight/tracking/snapshot/**`、`tfi-all/src/test/java/com/syy/taskflowinsight/tracking/path/**`，`tfi-examples/src/jmh/java/com/syy/taskflowinsight/benchmark/FilterBenchmarks.java` | 新snapshot/path contract |
| 新增测试 | `CompareBudgetLedgerContractTests`、`CompareRequestIsolationTests`、`SnapshotPathBoundaryTests` | limit、并发、结构共享 |

### 核心步骤

1. 先为四类budget event和limit-1/limit/limit+1写表驱动红测；identity/null/type fast path不得错误消费node。
2. 将已冻结typed segment与canonical cost模型接入request state，验证动态key不调用`toString/hashCode`且working set不复制display path。
3. 用显式frame迁移root/object/container capture；每个callback前检查deadline与预算。
4. 将cycle、reflection和concurrent modification事实交给已有accumulator/reducer，不在snapshot层拼最终状态。
5. 迁移白盒测试到public/package-private contract；删除static depth/options字段和反射reset helper。
6. 迁移all/examples consumers并翻转C-02中snapshot部分；旧选择态最终删除由KRN-02完成。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareBudgetLedgerContractTests,CompareRequestIsolationTests,SnapshotPathBoundaryTests test
./mvnw -pl tfi-compare,tfi-all,tfi-examples -am -Dtest=CompareRequestIsolationTests,SnapshotConsumerContractTests,FilterBenchmarkContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：请求状态无static/ThreadLocal/实例可变字段，Runtime并发结果一致。
- [x] CP-2：四类event只在ADR-012定义的位置消费，limit边界无off-by-one。
- [x] CP-3：path working set为parent+segment共享，业务key不触发用户callback。
- [x] CP-4：达到任何限制都可见且不证明equal；消费者已同步迁移。

### 禁止范围与回滚

不在本卡删除全部Diff/cache/selection owner，不实现collection配对算法。回滚必须同时恢复snapshot state、Engine接线、测试和消费者；
不得保留新ledger外包旧static snapshot的混合路径。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：聚焦遍历状态、预算和path，不跨入collection语义。
- [x] **认知负担**：request state为用途单一的package-private状态，不是service locator。
- [x] **比例失调**：隔离/预算/path占主体。
- [x] **ROI**：关闭并发污染、无标记截断和深图内存放大。
- [x] **洁癖检测**：不按类大小机械拆分。
- [x] **局部 vs 全局**：所有后续strategy共享同一ledger/path。
- [x] **过度设计**：无pipeline/handler，frame与ledger对应必要机制。

**结论**：设计通过；KRN-02必须基于本卡当前文件重读后实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| `SnapshotResult`分层 | 放在`tracking.snapshot` | 收回`tracking.compare.internal` | ArchUnit证明snapshot底层包会反向依赖Compare真值类型；该事实只服务请求内核 |
| 旧Snapshot表面 | 原位重写三个legacy snapshot类 | Runtime/Engine单向接入`RequestLocalSnapshot`；旧公开表面暂留 | `SnapshotProviders`选择态与直接legacy消费者由KRN-02收敛；本卡不得提前删除其owner |
| 容器执行 | 兼容strategy外包ledger | built-in List/Map/Set/Collection/array直接走typed kernel | 外包只计一个DIFF_NODE会漏掉真实path判定，且可能重新进入旧snapshot owner |
| 结果预算 | 只接入path working set | 同步接入`maxPathEncodedChars/maxResultTotalChars`与omitted诊断 | 只有结果准入点才能保证超长path/value不逃逸并保留差异anchor |

### 检查点结果

- [x] CP-1：32个并发调用共享同一immutable Runtime，均得到独立`6/0`节点/元素诊断。
- [x] CP-2：四类事件表驱动边界与Engine limit+1合同通过，拒绝事件不执行callback且counter停在limit。
- [x] CP-3：frame共享同一`ComparePath`引用；Map/Set复杂identity不调用业务`toString/hashCode`。
- [x] CP-4：depth/deadline/budget/result/reflection/cycle/隐藏字段均发布typed issue，消费者闭集与七模块package通过。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | request isolation、budget/path/result、cycle/reflection与保守真值合同；全量3,607/3,607 |
| 完整性 | 24 /25 | Runtime、all、examples/JMH闭合；旧Snapshot公开兼容表面按owner留给KRN-02 |
| 可维护性 | 24 /25 | state/frame/ledger/accumulator职责单一，新内核类均低于500行；既有Engine热点不越界重构 |
| 风险控制 | 25 /25 | clean verify、API inventory/manifest、ArchUnit、三道任务卡门禁及七模块bench/package均通过 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| P1/MUST | KRN01-R01 | 重复W2104可绕过`maxIssues`共享上限 | `CompareResultAccumulator.java:183` | 仅首个容量告警使用保留槽；新增上限合同并关闭 |
| P1/MUST | KRN01-R02 | 父子类同名字段会覆盖同一typed path并可能误判相等 | `RequestLocalSnapshot.java:159` | 拒绝歧义字段并发布一次`SNAPSHOT_FAILED`；新增回归合同 |
| P2/SHOULD | KRN01-R03 | 精确重复issue在去重前被计为omitted并虚构W2104 | `CompareResultAccumulator.java:149` | 容量判断前短路完全相同的typed issue；新增幂等合同 |
| P3/STYLE | KRN01-R04 | Snapshot缓存/过滤调用残留可执行FQCN | `CompareCacheConfig.java:54` | 改为import与短名；本卡生产作用域扫描无可执行FQCN |
| P1/MUST | KRN01-R05 | issue路径准入用`Optional`返回null表达拒绝，违反返回合同并触发SpotBugs HIGH | `CompareResultAccumulator.java:451` | 抽出`ResultIssuePathFit`显式表达准入与可选路径；移除null sentinel并关闭 |

### 验证证据（2026-07-13）

| 命令/门禁 | 结果 |
|---|---|
| 任务卡核心命令 | 退出0；40 tests（ledger 4、snapshot/path 17、request isolation 19） |
| Compare/all/examples消费者命令 | 退出0；19 + 1 + 1 tests，7个reactor项目成功 |
| 七模块消费者package | 退出0；7个reactor项目成功 |
| `./mvnw -pl tfi-compare clean verify` | 退出0；3,607 tests；模块既有PMD 8,149条，按当前POM保持非阻断 |
| `./mvnw -pl tfi-compare -Papi-compat verify -DskipTests` | 退出0；japicmp成功，SpotBugs 0 |
| API inventory/manifest + ArchUnit | 退出0；24 + 11 tests |
| examples `-Pbench` test-compile | 退出0；7个reactor项目成功，JMH/bench源码编译通过 |
| `ComparePlanningTraceabilityTests` | 退出0；5 tests，完成状态与依赖矩阵一致 |
| Javadoc/FQCN/复杂度自检 | 活跃类型无blocking注释违规；字段/枚举逐项注释；新内核类均低于500行；可执行FQCN为0 |

审查结论：3个P1、1个P2与1个P3均已关闭，无遗留MUST；DoD与CP全部通过，未启动`CMP-KRN-02`。
