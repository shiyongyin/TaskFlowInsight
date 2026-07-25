# TASK-CMP-RES-01：建立结果真值模型与单调reducer

> **定位**：消除failure/disabled/partial被伪装成相同的根因，并同步迁移所有结果消费者。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-GRD-02`
> **后续**：`TASK-CMP-POL-01`
> **架构来源**：总体设计§8、W1；ADR-011 `CMP_G2`
> **消费不变量**：1-5、19-20

---

## 一、核心（设计时填）

### 背景

现有`CompareResult`以changes和similarity推断状态，并持有before/after对象；不同入口还会把disabled/provider failure压成
identical。本卡建立Outcome + Completion、typed problem/limitation、bounded ValueSnapshot和唯一单调reducer，随后在同卡迁移
Compare、`tfi-all`和examples的直接结果消费者，确保W1没有白盒测试红窗。

### 目标（DoD）

- [x] 新增`CompareOutcome`、`CompareCompletion`、typed code/problem/limitation/diagnostics、`SimilarityScore`和`ValueSnapshot`。
- [x] 新增`AlgorithmId`、`CompareStage`及唯一`ComparePath/PathSegment`不可变值合同，供本卡结果模型和后续kernel共同消费。
- [x] `CompareResult`与`FieldChange/ChangeSide`不可变、防御复制、有界且不保留根对象或任意Throwable。
- [x] 唯一reducer按aggregate facts归并；差异后任意非fatal problem/limitation保持`DIFFERENT + PARTIAL`。
- [x] null/type mismatch产生root change；disabled为`INDETERMINATE + DISABLED`；`DIFFERENT`不允许empty changes。
- [x] `hasChanges()`、setter、raw old/new和similarity sentinel按manifest exact处置；替代query语义单一。
- [x] Compare内部、`tfi-all` facade/tests、examples/JMH的结果消费在本卡同步迁移并保持全部消费者编译为绿。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 真值归并 | 高 | 本卡存在的核心理由 |
| 不可变有界事实 | 高 | 安全、并发和输出的共同前提 |
| 消费者同步 | 高 | 避免W1到W7的白盒红窗 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 状态模型 | Outcome与Completion正交 | 区分事实结论与执行完整性 | 单boolean identical |
| reducer输入 | O(1) monotonic facts + bounded details | detail被省略也不丢真值 | 用changes/issues列表反推 |
| 值模型 | tagged bounded immutable snapshot | 不泄漏业务对象且可确定编码 | raw Object或toString截断 |

## 二、执行（设计时填）

### 文件与接口

| 动作 | 精确路径/范围 | 类型/职责 |
|---|---|---|
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareOutcome.java` | `EQUAL/DIFFERENT/INDETERMINATE` |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareCompletion.java` | `COMPLETE/PARTIAL/FAILED/DISABLED` |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/AlgorithmId.java`、`CompareStage.java` | versioned算法ID grammar与issue stage闭集 |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/path/ComparePath.java`、`PathSegment.java`及闭集segment值类型 | result/kernel共用的不可变canonical path数据模型 |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/`下的`CompareProblemCode.java`、`CompareLimitationCode.java`、`CompareProblem.java`、`CompareLimitation.java`、`CompareDiagnostics.java`、`SimilarityScore.java`、`ValueSnapshot.java`、`ChangeSide.java`、`ChangeKind.java` | 总体设计§8闭集值类型 |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/CompareResult.java`、`FieldChange.java` | immutable result/change与明确query |
| 新增internal | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/internal/CompareResultAccumulator.java`、`CompareResultReducer.java` | 唯一aggregate/reducer owner |
| 修改范围 | Compare API/builder/strategy/query/projector/export/render/config | 只消费新真值，不自行拼状态 |
| 修改消费者 | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TfiCompareDelegate.java`、`tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`及`tfi-all/src/test/java/com/syy/taskflowinsight/tracking/compare/`下的result白盒测试 | failure/disabled/root/query迁移 |
| 修改消费者 | `tfi-examples/src/main/java/**`、`src/jmh/java/**`中的compare结果使用 | 新query和bounded value使用 |
| 新增测试 | `CompareResultTruthContractTests`、`CompareReducerPermutationTests`、`ValueSnapshotBoundaryTests`、`AlgorithmIdValueContractTests`、`ComparePathValueContractTests` | 状态表、顺序无关、有界值与基础typed identity/path合同 |

公开构造器/方法必须以总体设计§8的草图和W0 manifest为准；若W0对上述FQN给出冲突处置，本卡立即阻塞并回到设计修订，
不得临时保留第二套result adapter。

### 核心步骤

1. 先实现AlgorithmId grammar、CompareStage、canonical path和闭集enum/value records的构造器不变量，所有collection执行`List.copyOf`等防御复制。
2. 实现accumulator保留槽、omitted counters和顺序无关reducer；problem/limitation明细达到上限后仍更新aggregate facts。
3. 迁移null/type/disabled/failure/similarity入口，删除所有`changes.isEmpty()`推断equal的生产逻辑。
4. 迁移query/projector/formatter临时消费；W5前旧formatter可读新result，但不得建立第二状态模型。
5. 同步修改all/examples/JMH和manifest consumerTest；将C-01 characterization翻转为目标合同。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareResultTruthContractTests,CompareReducerPermutationTests,ValueSnapshotBoundaryTests test
./mvnw -pl tfi-compare,tfi-all,tfi-examples -am -Dtest=CompareResultTruthContractTests,TfiCompareResultContractTests,CompareExamplesResultContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：合法状态组合只有总体设计§8闭集；构造器拒绝其他组合。
- [x] CP-2：先差异后故障、先故障后差异和details满容量的最终状态一致。
- [x] CP-3：result/change/toString不泄漏raw值、对象、异常message或mutable collection。
- [x] CP-4：all/examples无旧setter、sentinel、`hasChanges()`或failure-to-identical依赖。

### 禁止范围与回滚

本卡不建立Policy/Runtime、Registry选择或Spring模块边界。回滚必须同时恢复result owner、全部直接消费者、C-01测试和manifest条目；
禁止只恢复facade sentinel或保留双状态模型。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只处理结果事实与直接消费者。
- [x] **认知负担**：两个正交enum和两个typed issue类型对应真实不变量。
- [x] **比例失调**：reducer与消费者闭合占主要篇幅。
- [x] **ROI**：关闭最严重的false-equal路径。
- [x] **洁癖检测**：不借机重写collection/kernel/output。
- [x] **局部 vs 全局**：为W2-W6提供唯一结果合同。
- [x] **过度设计**：无visitor/pipeline/通用issue继承层。

**结论**：设计通过；与`CMP-POL-01`串行，不能并行修改Engine/result热点。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| W0 compatibility disposition | 以inventory owner与manifest双向合同确定本卡处置 | inventory将result/change全表面标为`INTERNAL_EXPOSED / CMP-RES-01`；manifest只登记已实际删除项并随owner卡增长 | 任务卡步骤5与回滚条款要求本卡同步登记真实breaking；不存在预置条目不等于冲突，禁止项是删除后仍未登记或预登记尚未删除项 |
| typed dependency owner | 本卡先实现总体设计§8的完整result值类型 | 用户批准将`AlgorithmId`、`CompareStage`与canonical `ComparePath`值合同前移RES-01；POL/KRN保留运行时职责 | 修复依赖倒置且不改变Gate、参数矩阵、Wave顺序或退出语义 |
| TDD与验证 | 从`CompareResultTruthContractTests`最小状态组合开始RED | owner修订完成，先执行规划追踪合同 | 公共构造依赖已具备唯一owner，可进入行为TDD |
| RED-1 AlgorithmId canonical value | 最短合法ID通过`of`进入不可变值对象 | focused test在`testCompile`失败，缺少`AlgorithmId`类型 | 预期API-shape RED；未出现无关失败 |
| facade/builder failure语义 | disabled/provider failure不再返回identical或伪造type mismatch | 新增reducer窄入口并迁移`ComparatorBuilder`、`TfiCompareDelegate`及all/examples消费者合同 | 禁用表示未执行；同类型服务缺失不能证明相等或类型不同 |
| examples值事实 | 示例合同最初用两个String值验证差异 | 改用type mismatch根变更并断言两侧`type-metadata` | 默认String策略不保证值差异；type mismatch是本卡冻结合同且不会泄漏原值 |

### 检查点结果

- [x] CP-1：`CompareResultTruthContractTests`覆盖合法/非法状态组合，23/23通过。
- [x] CP-2：`CompareReducerPermutationTests`覆盖顺序与明细容量，5/5通过。
- [x] CP-3：有界snapshot、防御复制和安全`toString`合同通过；Javadoc扫描0 violation。
- [x] CP-4：all/examples消费者合同通过；48个FieldChange API删除与1个behavior翻转均已exact登记。

### Typed owner修订决策（已批准）

| 类型/职责 | 建议owner | 后续卡保留职责 | 原因 |
|---|---|---|---|
| `AlgorithmId`值类型及§7 grammar/128字符边界 | `CMP-RES-01` | `CMP-POL-01`负责runtime注册唯一性、selection与hard ceiling | `SimilarityScore`和`CompareDiagnostics`的公开构造先依赖该类型，不能依赖后置卡 |
| `CompareStage`闭集enum | `CMP-RES-01` | 各后续执行卡只消费 | problem/limitation schema在本卡冻结，stage不能后补或使用String占位 |
| `tracking/path/ComparePath`、`PathSegment`及闭集segment值类型 | `CMP-RES-01` | `CMP-KRN-01`负责parent+segment working set、预算消费与snapshot接线 | 统一替代§8草图中的未分配`ChangePath`；result与kernel不能长期存在双path模型 |

已同步修订：本卡文件清单和focused tests、`CMP-POL-01`与`CMP-KRN-01`文件清单/步骤、总体设计§8类型名、
INDEX owner矩阵。保持16卡顺序、Gate token、参数矩阵及W1/W2退出条件不变。

拒绝的替代方案：重排POL/KRN到RES之前会破坏既定串行顺序；使用`String`、临时nested type或
`ChangePath + ComparePath`双模型会违反typed schema、唯一owner和无临时adapter约束。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | 六种真值组合、permutation、disabled/failure和root change合同均通过 |
| 完整性 | 25 /25 | Compare、all、examples/JMH、manifest与japicmp exclusion同卡闭合 |
| 可维护性 | 24 /25 | 唯一reducer与有界值模型消除双真值；旧query/output过渡接口留给后续owner |
| 风险控制 | 25 /25 | 3,597模块测试、跨模块合同、clean package、api-compat与SpotBugs均通过 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| 无MUST/P0/P1 | RES01-R00 | findings-first审查未发现阻断缺陷 | result/reducer/facade/consumer范围 | 无需修复；保留后续Policy/Output owner边界 |

### 验证证据（2026-07-13）

| 命令/门禁 | 结果 |
|---|---|
| 任务卡核心合同命令 | 退出0；42 tests |
| Compare/all/examples跨模块合同命令 | 退出0；27 tests |
| 任务卡消费者package命令 | 退出0；7个reactor项目成功 |
| `./mvnw -pl tfi-compare test` | 退出0；3,597 tests |
| `./mvnw clean package -DskipTests` | 退出0；7个reactor项目clean compile/package成功 |
| `./mvnw -pl tfi-compare -Papi-compat verify -DskipTests` | 退出0；japicmp成功、SpotBugs 0；Checkstyle/PMD维持模块既有baseline |
| Javadoc checker（本卡internal包） | 0 violation；7条非阻断启发式warning |

审查结论：DoD与CP全部通过，0个MUST/P0/P1，未启动`CMP-POL-01`。
