# TASK-CMP-POL-01：建立Policy、Options与Runtime唯一入口

> **定位**：冻结比较语义、资源上限和对象图构造，使所有入口共享同一immutable Engine合同。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-RES-01`
> **后续**：`TASK-CMP-KRN-01`
> **架构来源**：总体设计§7 CMP_G3、§9.1、§12、W1；ADR-012
> **消费不变量**：7、10-11、18-21

---

## 一、核心（设计时填）

### 背景

现有mutable`CompareOptions`同时承载report、patch、parallel、degradation和类型强制，多处默认值还来自Spring/system property。
Engine、Service、strategy和provider可以各自构造执行图。本卡建立immutable`ComparePolicy`、可收紧的`CompareOptions`、
`CompareRuntime.Builder`和typed输入异常，迁移全部factory/provider/facade/example入口；Spring旧表面只允许临时单向映射，W6再抽取。

### 目标（DoD）

- [x] `ComparePolicy.defaults()`是比较语义、默认值与hard ceiling唯一owner；参数精确使用总体设计§12.3 accepted矩阵。
- [x] immutable`CompareOptions`只能在Policy范围内选择/收紧；所有非法值在执行/provider/action前抛typed`CompareInputException`。
- [x] `CompareRuntime.Builder`是唯一graph construction/freeze入口，`CompareEngine`只持有final immutable依赖且线程安全。
- [x] root fast path固定为validate -> disabled -> identity/null/type -> plan；只有JVM identity可直接证明非scalar equal。
- [x] extension使用唯一、合法、版本化`AlgorithmId`和`PropertySelector`；重复/非法注册在build时失败。
- [x] report/patch/parallel/perf fallback/type forcing等旧options按manifest删除或单向映射，无第二默认值owner。
- [x] Compare SPI、all facade、examples/JMH和strict perf workflow在本卡同步迁移并保持绿色。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 配置与graph唯一owner | 高 | 后续kernel隔离的前提 |
| 输入/extension合同 | 高 | 禁止运行中猜测或fallback |
| 消费者与perf连续性 | 高 | W1改变入口但不放宽existing gate |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 默认值owner | 纯Java ComparePolicy | static与Spring复用同一语义 | binder/system property各自默认 |
| runtime更新 | build新immutable runtime | 并发可推理，无半更新 | 在线修改strategy registry |
| 扩展失败 | typed problem，不fallback | 避免同请求语义漂移 | catch后默认equals |

## 二、执行（设计时填）

### 文件与接口

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/ComparePolicy.java` | defaults、允许范围、hard ceilings、compiled patterns |
| 重写 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareOptions.java` | immutable per-call选择；删除公开mutable default实例 |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareRuntime.java` | `builder()`、`policy()`、`engine()`与build freeze |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareEngine.java` | final runtime components、`compare(before,after[,options])`入口 |
| 收窄 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareService.java`、`tfi-compare/src/main/java/com/syy/taskflowinsight/api/ComparatorBuilder.java`、`tfi-compare/src/main/java/com/syy/taskflowinsight/api/builder/DiffBuilder.java` | 无状态compat adapter或exact removal |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/`下的`CompareInputException.java`、`InputViolation.java`、`PropertySelector.java` | typed input和extension selector；复用RES-01冻结的`AlgorithmId` |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/spi/ComparisonProvider.java`、`DefaultComparisonProvider.java` | typed before/after/options与共享default runtime |
| 修改消费者 | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`、`TfiCompareDelegate.java`、`TfiProviderDelegate.java` | 无fallback graph；保留W6前Registry边界 |
| 修改消费者 | examples main/JMH及perf report生成路径 | 新Options/Runtime API且报告同轴 |
| 新增测试 | `ComparePolicyContractTests`、`CompareRuntimeContractTests`、`CompareInputValidationTests`、`CompareExtensionContractTests` | 参数、freeze、并发、selection |

### 核心步骤

1. 先实现Policy/Options构造器和§12.3 limit-1/limit/limit+1参数测试；所有overflow-safe校验在graph/action前完成。
2. 复用RES-01的AlgorithmId grammar，实现PropertySelector field解析和runtime内AlgorithmId唯一注册；build后不允许mutation。
3. 让Engine从Runtime导出并固定root fast path；Service/builder/SPI只委托，不构造fallback graph。
4. 按§12.1矩阵逐字段迁移旧Options，并为每个删除/行为变化写manifest与consumer test。
5. 迁移all/examples/JMH；重新生成routing同轴报告并运行strict gate，禁止改变阈值或baseline口径。
6. 翻转与Policy/Options相关的W0 characterization；Spring旧config只保留单向mapping，W6前不得改变模块owner。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=ComparePolicyContractTests,CompareRuntimeContractTests,CompareInputValidationTests,CompareExtensionContractTests test
./mvnw -pl tfi-compare,tfi-all,tfi-examples -am -Dtest=CompareRuntimeContractTests,TfiCompareFacadeContractTests,CompareExamplesPolicyContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-all -am -Dtest=TfiRoutingPerfGateTests -Dit.test=TfiRoutingPerfGateIT verify -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Dtfi.perf.enabled=true -Dtfi.perf.strict=true
./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：defaults/hard ceilings只有Policy owner，Options不能扩大上限或安全语义。
- [x] CP-2：同一Runtime并发调用无可变字段；build后extension mutation失败。
- [x] CP-3：Engine/Service/SPI/facade无fallback graph、第二registry或`catch(Throwable)`。
- [x] CP-4：strict perf gate仍blocking且报告可比较，所有API/config/behavior变化已登记。

### 禁止范围与回滚

本卡不实现snapshot/diff算法、不引入`CompareOperations`、不抽Spring模块。回滚必须同时恢复Policy/Options/Runtime、facade/examples、
perf报告和manifest；不得留下新Engine消费旧mutable Options的混合状态。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只冻结配置/graph/入口，不提前实现W2算法。
- [x] **认知负担**：Policy、Options、Runtime三者分别表达允许范围、请求选择、冻结对象图。
- [x] **比例失调**：唯一owner和消费者迁移占主体。
- [x] **ROI**：删除多默认值和fallback graph，支撑后续并发正确性。
- [x] **洁癖检测**：不新建pipeline或通用mutable context。
- [x] **局部 vs 全局**：为static、Spring和Tracking提供同一语义源。
- [x] **过度设计**：Runtime只有builder/freeze/engine职责；`CompareOperations`延后到第二实现出现的W6。

**结论**：设计通过；完成后W1整体才可判绿。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| Registry热路径 | 不改变Core Registry实现 | 在Core Registry内发布冻结provider快照，冻结后读取无锁 | 首轮同轴JMH回归8.01%；优化仍由Registry独占选择与freeze，不在facade建立第二缓存 |
| SPI options重载 | 所有实现显式消费options | 为保留既有ABI方法形状暂留default，但默认实现抛typed输入异常 | 禁止自定义provider静默丢弃显式options；需要消费options的实现必须override |
| SPI merge占位 | 收窄SPI为typed compare | 删除始终抛异常的`threeWayMerge`并登记`CMP-BRK-API-0202` | 未完成的merge合同不应被宣传为provider能力 |

### 检查点结果

- [x] CP-1：Policy合同覆盖accepted边界，Options只能等于或收紧runtime policy。
- [x] CP-2：Runtime只发布final冻结依赖；并发、重复注册和build后mutation合同通过。
- [x] CP-3：Service、SPI、facade共享`CompareRuntime.defaults()`；构图只允许经过Builder。
- [x] CP-4：strict gate回归4.72%，低于5%；API/BEHAVIOR变化均由manifest精确登记。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | Policy/Runtime/Input/Extension合同17/17，全量Compare测试3,565/3,565 |
| 完整性 | 25 /25 | SPI、facade、examples/JMH、manifest、japicmp和跨模块消费者同卡闭合 |
| 可维护性 | 24 /25 | 唯一Policy/Runtime owner、typed失败与中文意图注释已落地；request-local kernel由后续卡负责 |
| 风险控制 | 25 /25 | strict perf、Core Registry回归、API兼容、消费者测试及七模块package全部通过 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| P1/MUST | POL01-R01 | SPI三参默认实现可静默丢弃options，且保留始终失败的merge占位能力 | `ComparisonProvider.java`、`DefaultComparisonProviderContractTests.java` | 默认实现改为typed拒绝；provider显式override；删除merge并以`CMP-BRK-API-0202`精确登记 |

### 验证证据（2026-07-13）

| 命令/门禁 | 结果 |
|---|---|
| Policy/Runtime/Input/Extension核心合同 | 退出0；17 tests |
| `ProviderResolutionCacheTests` | 退出0；7 tests |
| `./mvnw -pl tfi-compare test` | 退出0；3,565 tests |
| `./mvnw -pl tfi-compare -Papi-compat -DskipTests verify` | 退出0；japicmp成功、SpotBugs 0；Checkstyle/PMD维持模块既有baseline |
| Compare/all/examples跨模块合同命令 | 退出0；9 tests，7个reactor项目成功 |
| `ComparePlanningTraceabilityTests` | 退出0；5 tests，完成状态与任务依赖矩阵一致 |
| strict routing perf gate | 退出0；147.35 ns/op对140.71 ns/op，回归4.72% `<5%` |
| 消费者package与examples `-Pbench` | 退出0；7个reactor项目成功，JMH源码编译通过 |
| Javadoc checker与FQCN扫描（本卡文件） | 无blocking注释违规；枚举常量、基础类型字段逐项注释；可执行类型引用使用import与短名 |

审查结论：1个P1/MUST已关闭，无P0、无遗留P1；DoD与CP全部通过，未启动`CMP-KRN-01`。
