# tfi-kernel-compare 设计文档

## 1. 文档定位

本文是 `tfi-kernel-compare` 当前模块架构 SSOT，描述 KCS-04/05 已实现并由测试约束的 summary 与 canonical detail 能力。
跨模块最终合同以
[ADR-016](../../docs/adr/ADR-016-TFI-Kernel-Compare-Core-Composition.md)
为准。Bridge 完整不等于组合 Starter 或发布 Gate 完整，也不得由本文推导出 Compare Core 已 `BASELINED`。

## 2. 职责边界

Bridge 只完成四件事：

1. 调用宿主注入的 `CompareOperations`；
2. 把已有 `CompareResult` 的低敏机器事实映射为 Kernel summary；
3. 通过 Core canonical projection 产生可选的已脱敏 change detail；
4. 调用当前 `Stage.record(...)` 并返回比较是否执行和 Record 接纳程度。

Bridge 不负责业务 action、真值重判、Spring/AOP、Sink、线程、指标、缓存、持久化或重试。生产代码运行依赖只有
`tfi-kernel` 与 `tfi-compare-core`，Maven Enforcer 同时禁止旧 TFI shell、Spring、Jackson、Caffeine、
Micrometer 和 AspectJ 的传递进入。

```text
host-selected CompareOperations
            |
            v
KernelCompareRecorder -----> CompareResult
            |                     |
            |                     v
            |              CompareSummaryMapper
            |                     |
            |                     v
            |              summary MESSAGE
            |
            +--> CompareProjectionFactory
                       |
                       v
              ProjectionNodeDataConverter
                       |
                       v
              canonical CHANGE prefix
```

`tfi-kernel` 与 `tfi-compare-core` 永不互相依赖；组合只存在于本 leaf artifact。

## 3. 公共 API

模块只允许四个 public 类型：

| 类型 | 职责 |
|---|---|
| `KernelCompareRecorder` | 容量短路、执行 Compare Port、规划并写入 summary/detail |
| `KernelCompareRecordPolicy` | 0..32 的 integration detail 前缀上限，默认 0 |
| `CompareRecordResult` | 同一次执行的 CompareResult 身份、可用与已记录 change 计数 |
| `CompareRecordStatus` | 跳过、summary、detail 接纳程度的六态闭集 |

`CompareSummaryMapper` 与 `ProjectionNodeDataConverter` 必须保持 package-private。公共签名、record component 和 enum 顺序由
`KernelCompareArchitectureContractTest` 白名单固定；禁止新增 bridge SPI、factory、overload 或可变 context。

## 4. 输入与执行语义

构造依赖、`stage` 和 `result` 为 null 时使用固定参数名抛出 `NullPointerException`。operation 先 `trim()`，
随后必须匹配 `[a-z][a-z0-9._-]{0,127}`；不隐式 lower-case，不把分隔符反向解析为业务结构。非法 operation
或 policy 在 Compare 和 Kernel 副作用前以 `KCS_E_1201` 拒绝。Recorder 构造时同样拒绝
`MaskingPolicy.includesSensitiveValues()==true`，且错误文本不展开策略或业务值。

`compareAndRecord(...)` 的唯一前置短路是 `stage.remainingEncodedBytes() <= 0`：此时比较 0 次并返回
`SKIPPED_NO_RECORDING_CAPACITY`。其余路径调用无 options 的 Compare Port 恰好一次。

`record(...)` 已持有 CompareResult，因此不会返回 SKIPPED；它先完成 detail planning，再尝试 summary。summary 拒绝后不写
detail 并返回 `EXECUTED_NOT_RECORDED`；CompareResult 实例与 outcome/completion 始终保持不变。

## 5. Summary 合同

Record 固定为：

```text
type = MESSAGE
code = KCOMPARE_SUMMARY_V1
text = null
```

data 按以下顺序插入，optional 缺失时不放置占位 null：

```text
schemaVersion
operation
outcome
completion
availableChangeCount
problemCodeCounts
limitationCodeCounts
rootAlgorithmId                    optional
appliedAlgorithmCount
effectivePolicyFingerprint         optional
similarityAlgorithmId              optional pair
similarityValue                    optional pair
durationNanos
comparedNodes
consumedElements
retainedResultChars
omittedPaths
omittedChanges
omittedProblems
omittedLimitations
configuredDetailLimit
plannedDetailCount
detailState
```

outcome/completion 直接使用 Core enum `name()`，不从 change 或 `isIdentical()` 重判。problem/limitation 只按
`wireCode()` 聚合，并按 code 升序输出。root、applied count、fingerprint 和计数直接来自 `CompareDiagnostics`；
similarity algorithm/value 必须同时存在或同时缺失。

Summary 禁止读取或输出 change path、before/after value、业务对象、problem/limitation path、异常 message、stack
或 applied algorithm 全列表。最坏合法 shape 由真实 Kernel 默认 2 KiB 单 Record 预算验收。

## 6. Canonical Detail 与失败域

默认 `maxRecordedChanges=0` 或结果无 change 时：

```text
configuredDetailLimit = 0
plannedDetailCount = 0
detailState = NOT_REQUESTED
```

此路径不创建 projection。非零 policy 且存在 change 时，Recorder 只调用一次：

```text
CompareProjectionFactory.create(result, empty metadata, safe masking, default options)
  -> root OBJECT 中唯一的 changes ARRAY
  -> first min(available, maxRecordedChanges) ProjectionNode
  -> package-private converter
  -> JDK LinkedHashMap / ArrayList / scalar / null
```

projection 与全部计划节点转换必须在 summary 前完成。成功时 summary 使用 `detailState=READY`，随后按 canonical 顺序写：

```text
type = CHANGE
code = KCOMPARE_CHANGE_V1
text = null
data = schemaVersion, operation, changeIndex, change
```

integration limit 或首次 Kernel 拒绝只保留已接纳前缀并返回 `RECORDED_PARTIAL_DETAILS`；首次拒绝后不再调用
`Stage.record`。projection/schema/converter 的普通 `RuntimeException` 或非 fatal `Error` 不回退读取 raw change，而是写
`detailState=FAILED` summary 并返回 `RECORDED_DETAIL_FAILURE`。`VirtualMachineError`、`ThreadDeath` 与 `LinkageError`
原实例传播，异常 message、stack 与 cause 不进入 Record。

converter 只依赖 `ProjectionNode` 的六种 Kind，不引用 `FieldChange`、typed path 或业务对象，不实现第二套 masking。

## 7. 状态与并发

`CompareRecordResult` 保持以下不变量：

- 只有 SKIPPED 的 `compareResult` 为空，且两个 change 计数固定为 0；
- 其他状态保留原始 CompareResult 实例，`availableChanges` 等于 Core 当前 change 数；
- `recordedChanges` 位于 0..available，summary、detail failure 和未记录状态固定为 0；
- 全量 detail 必须覆盖全部非空 available changes；integration limit 或 Kernel 拒绝产生的 partial detail 必须是严格前缀；
- detail planning 普通失败时已记录数固定为 0，CompareResult 真值不变。

Recorder 字段不可变、方法只使用局部状态，可在线程间共享。Stage 仍是 owner-thread only；Bridge 不创建线程、队列、
ThreadLocal、Registry 或生命周期回调。

## 8. 发布边界

KCS-04/05 形成完整阶段 C，可由 KCS-06 的程序化 Starter 直接消费。模块仍受后续 Spring 生命周期、consumer、全 Reactor
与发布 owner Gate 约束；根 README 与发布制品清单在 KCS-10 前不得推荐本 artifact。
