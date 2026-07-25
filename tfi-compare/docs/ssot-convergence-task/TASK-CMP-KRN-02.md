# TASK-CMP-KRN-02：收敛Diff、Key、Pattern与旧owner

> **定位**：让snapshot之后只有一个lossless differ/key/pattern owner，并原子退役cache、dedup和runtime benchmark伪组件。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-KRN-01`
> **后续**：`TASK-CMP-COL-01`
> **架构来源**：总体设计§9.3、§9.5-9.8、W2；ADR-012
> **消费不变量**：6-11、19-21

---

## 一、核心（设计时填）

### 背景

当前`DiffFacade`、detector/service、snapshot providers、path cache和semantic dedup形成多条执行图；entity/map key还可能依赖
`toString/hashCode`或value tolerance。Ops把`PathMatcherCacheInterface`和runtime`BenchmarkRunner`暴露成健康事实。
本卡建立唯一package-private differ、exact key wire和构建期typed pattern，同时删除旧owner并迁移Ops/all/examples消费者。

### 目标（DoD）

- [x] Runtime只持有一个package-private differ；strategy/SPI/facade不能选择或new第二个diff graph。
- [x] key/address identity使用type-tagged exact wire、unsigned bytes排序，不受locale、value tolerance或custom comparator影响。
- [x] include/exclude复用一个stateless typed pattern compiler，非法输入build失败；无regex/LRU/preload/clear/fallback-to-literal。
- [x] semantic dedup不按叶子value删除真实record；C-04翻转为lossless事实。
- [x] numeric/temporal equality只来自Policy，system default timezone和多套tolerance owner清零。
- [x] 六个descriptor annotation合同固定；`TfiTrack`只由W4删除，本卡不得提前触碰。
- [x] Ops runtime benchmark/path-cache endpoint原子删除或适配，all/examples/detector/path白盒同步迁移，全消费者编译为绿。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 唯一diff/key owner | 高 | 防止入口间语义分叉 |
| Pattern与去重安全 | 高 | 防ReDoS、漏变化和第二cache |
| Ops/消费者闭合 | 高 | 删除`PathMatcherCacheInterface`不能留下红窗 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| key wire | typed exact canonical bytes | address identity不能受display/value容差影响 | `toString/hashCode` |
| pattern | Policy build期有界编译 | runtime无cache/mutation/ReDoS | regex + timeout或LRU |
| performance owner | build-time benchmark | runtime endpoint不是算法控制面 | 迁移BenchmarkRunner到Ops继续运行 |

## 二、执行（设计时填）

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/internal/CompareDiffer.java` | 唯一package-private diff编排 |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/ssot/key/EntityKeyWire.java`、`KeyComponent.java` | exact key encoding/order |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/snapshot/filter/PathPattern.java`、`PathPatternCompiler.java` | stateless bounded grammar |
| 删除/收窄 | `tracking/detector/DiffFacade.java`、`DiffDetector*.java`、`tracking/snapshot/SnapshotProviders.java`、provider选择态 | 无第二owner；保留项只能无状态委托 |
| 删除 | `tracking/path/CaffeinePathMatcherCache.java`、`PathMatcherCacheInterface.java`、`PathCache.java`、semantic`PathDeduplicator.java`及配置 | cache/dedup旧owner退役 |
| 修改 | `tracking/precision/**`、`EnhancedDateCompareStrategy.java`、numeric/temporal比较代码 | Policy唯一容差与timezone-independent语义 |
| 修改 | `annotation/DiffIgnore.java`、`DiffInclude.java`、`Entity.java`、`Key.java`、`ShallowReference.java`、`ValueObject.java` | 六个descriptor闭集；其余annotation按manifest处置 |
| 删除/适配消费者 | `tfi-ops-spring/src/main/java/com/syy/taskflowinsight/actuator/SecureTfiEndpoint.java`；`tfi-ops-spring/src/main/java/com/syy/taskflowinsight/performance/`下的`BenchmarkRunner.java`、`BenchmarkEndpoint.java`、`BenchmarkReport.java`、`BenchmarkResult.java`与`dashboard/PerformanceDashboard.java` | 不再发布runtime cache/benchmark伪事实 |
| 迁移消费者 | Compare/all detector/path/snapshot tests；examples `FilterBenchmarks`与相关JMH | 新owner合同 |
| 新增测试 | `CompareDiffOwnerArchitectureTests`、`EntityKeyWireContractTests`、`PathPatternContractTests`、`NumericTemporalContractTests` | owner、wire、grammar、equality |

### 核心步骤

1. 先建key wire与pattern contract，覆盖BigDecimal scale、float bits、enum type、Unicode、非法wildcard和length边界。
2. 实现唯一differ并接入KRN-01 state/ledger/path；局部problem不阻止兄弟字段，global failure不丢已知差异。
3. 迁移numeric/temporal与六个descriptor annotation选择顺序，selected extension失败不fallback。
4. 删除Diff/Snapshot选择态、regex/cache/dedup owner；`MaskRuleMatcher`明确保留到W5。
5. 同步删除或适配Ops benchmark/path-cache endpoint及测试，性能证据只读W0 build-time结果。
6. 迁移all/examples白盒与manifest，运行owner零引用搜索并翻转C-02余项/C-04。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareDiffOwnerArchitectureTests,EntityKeyWireContractTests,PathPatternContractTests,NumericTemporalContractTests test
./mvnw -pl tfi-compare,tfi-ops-spring,tfi-all,tfi-examples -am -Dtest=CompareDiffOwnerArchitectureTests,CompareKernelEndpointContractTests,KernelConsumerContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：snapshot/diff/path/key各一个owner，旧cache/selection/dedup零生产引用。
- [x] CP-2：key wire不调用用户`toString/hashCode/equals`，ordering与locale/hash seed无关。
- [x] CP-3：`MaskRuleMatcher`未提前删除，`TfiTrack`未被W2认领。
- [x] CP-4：Ops不再编译依赖`PathMatcherCacheInterface`或runtime benchmark类型。

### 禁止范围与回滚

不实现Map/List/Set/entity配对策略，不迁Spring模块。回滚必须恢复唯一differ及其消费者闭集；不得只恢复cache或Ops endpoint而不恢复
对应真实owner，也不得让新key wire和旧dedup并存。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只收敛kernel owner和直接Ops消费者。
- [x] **认知负担**：differ、key wire、pattern compiler各自单责。
- [x] **比例失调**：owner删除和安全语义占主体。
- [x] **ROI**：消除第二执行图、semantic dedup漏报和runtime伪观测。
- [x] **洁癖检测**：不提前删除W4/W5表面。
- [x] **局部 vs 全局**：为四种collection共享统一key/path/diff。
- [x] **过度设计**：不引入cache、表达式语言或通用matcher SPI。

**结论**：设计通过；完成后W2整体才可判绿。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 旧detector兼容投影 | 迁移到唯一内核 | 保留canonical根/深层事实，`ENHANCED`不再生成私有repr | 避免兼容入口恢复第二diff/projection图或按展示值丢记录 |
| legacy path表面 | 删除cache owner | 保留无状态display builder；排序直接消费typed path | tracking仍有字符串兼容调用，W2只删除全局状态和identity职责 |
| 额外全量回归 | 任务卡定向门禁 | `tfi-all -am test`暴露后续卡旧合同；本卡直系92项已迁绿 | 集合、输出、routing等失败归W3-W6，不能在W2越界修复 |

### 检查点结果

- [x] CP-1：canonical snapshot/diff/path/key各一个owner；旧cache/selection/dedup零生产引用。
- [x] CP-2：key wire只消费typed scalar facts并按unsigned bytes排序，合同覆盖回调、locale和浮点边界。
- [x] CP-3：`MaskRuleMatcher`与`TfiTrack`均保留，未提前认领W4/W5职责。
- [x] CP-4：Ops构造与端点不再依赖path cache或runtime benchmark类型。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | 34项内核合同、跨模块合同与Compare全量回归通过 |
| 完整性 | 25 /25 | owner、直接消费者、Ops端点、测试与manifest同卡闭合 |
| 可维护性 | 24 /25 | 无状态兼容委托、typed wire/pattern/path；未为指标洁癖拆分510行snapshot类 |
| 风险控制 | 24 /25 | 删除无界cache/semantic dedup/runtime benchmark；后续集合owner边界明确 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| MUST | CR-1 | 旧Map实现类型被误判为根类型变化 | `DiffDetector` | 复制为统一`LinkedHashMap`后进入唯一runtime，已修复 |
| MUST | CR-2 | 深层变化的value kind可能错误发布为NULL | `DiffDetector`、`ValueKinds` | 改为只读取canonical `ValueSnapshot`，已修复 |
| MUST | CR-3 | display-path排序cache与两个无消费者比较类型形成第二owner | `StableSorter`、detector内部类型 | typed path直排并删除旧类型，已修复 |
| MUST | CR-4 | `PathBuilder`无界转义cache及旧cache白盒仍保活 | `PathBuilder`、All测试 | 删除cache/clear/size与旧白盒，已修复 |
| SHOULD | CR-5 | 实现类、枚举常量、基础字段注释及可执行FQCN不符合约定 | Compare/Ops/All改动文件 | 补充why注释并改用import；当前改动Javadoc blocking为0 |
| INFO | CR-6 | `RequestLocalSnapshot`约510行 | `RequestLocalSnapshot` | 分支浅且容器职责已按方法分开，本卡不做洁癖式拆层 |

### 验证记录

- `tfi-compare`全量：2454项通过。
- 本卡直系回归：Compare同名合同20项；All 92项通过、11项按性能开关跳过。
- DoD-1：34项通过；DoD-2：Compare 12、Ops 2、All 3项通过；DoD-3：7模块打包成功。
- 额外执行`./mvnw -pl tfi-all -am test`时，All旧兼容套件出现76 failures/26 errors；其中本卡直系
  detector/repr/sorter/cache/timezone/path用例已迁移并由上述92项回归覆盖，其余按任务索引归属W3-W6。
- Javadoc启发式扫描仍报告仓库历史欠账；本卡新增/修改生产表面无blocking。生产代码FQCN与退役owner扫描为0。

**Review结论**：本卡审查发现均已闭环，无遗留in-scope MUST；`CMP-COL-01`未启动。
