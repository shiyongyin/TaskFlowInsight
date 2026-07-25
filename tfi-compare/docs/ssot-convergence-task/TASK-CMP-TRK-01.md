# TASK-CMP-TRK-01：建立TrackingBatchScope与action恰好一次

> **定位**：让final executor成为唯一业务action sequencing owner，消除provider故障路径的重复执行。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-COL-02`
> **后续**：`TASK-CMP-TRK-02`
> **架构来源**：总体设计§10.1-10.2、W4；ADR-013 `CMP_G4/G5`
> **消费不变量**：12-14、19-20

---

## 一、核心（设计时填）

### 背景

现有`DefaultTrackingProvider.withTracked()`可在action抛错后再次调用action，`TrackingOptions`又复制Compare配置和失效性能开关。
本卡把provider收窄为`begin(targets, CompareOptions)`，由final`TrackingExecutor`唯一编排validate/begin/action/capture/close；
参数合法后的普通非fatal基础设施故障不能改变action恰好一次，业务异常保持同一实例传播。

### 目标（DoD）

- [x] `TrackingProvider.begin(List<Target>, CompareOptions)`返回线程封闭`TrackingBatchScope`，SPI不再拥有可覆盖`withTracked`。
- [x] `TrackingExecutor`提供single/multi-target execute，参数在provider/action前一次性校验，非法时两者均执行0次。
- [x] 合法参数下，begin null/普通异常、capture失败和普通close失败时action仍恰好1次；业务异常同一实例传播且不重试。
- [x] fatal在action前发生时原样传播且不承诺action执行；部分slot创建后fatal会逆序关闭已建slot。
- [x] baseline与after+diff各共享一份fresh phase-global ledger，action wall time不计入deadline。
- [x] batch只capture一次、slot按输入顺序返回、逆序幂等close；安全`toString()`不泄漏target/name/result。
- [x] Tracking SPI只接收Policy校验后的`CompareOptions`；旧`TrackingOptions`删除或只作单向immutable compat mapping。
- [x] `tfi-all` tracking facade/tests同步迁移，无fallback action、静默吞异常或第二sequencing owner。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| action exactly-once | 高 | 业务正确性硬约束 |
| failure/close/fatal矩阵 | 高 | 防止异常遮蔽和资源泄漏 |
| phase budget与多target | 高 | 不能按target重置限制 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| action owner | final TrackingExecutor | provider无法重试业务action | 每个provider自带wrapper |
| scope | batch-level AutoCloseable | 多target共享真实phase预算 | ThreadLocal baseline |
| close普通故障 | 规范化且不覆盖primary | 业务结果/异常优先 | close异常替换业务异常 |

## 二、执行（设计时填）

### 文件与接口

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/TrackingExecutor.java` | final validate/begin/action/capture/close owner；nested Target/Item/Execution/Action |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/TrackingBatchScope.java` | single-capture、ordered slots、idempotent close |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/spi/TrackingProvider.java`、`DefaultTrackingProvider.java` | typed begin，移除action wrapper |
| 删除/收窄 | `tfi-compare/src/main/java/com/syy/taskflowinsight/api/TrackingOptions.java`、`TrackingStatistics.java` | manifest决定exact removal或单向Options mapping |
| 收窄 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/ChangeTracker.java` | W4过渡compat只委托executor；global query由TRK-02删除 |
| 修改消费者 | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`、`TfiProviderDelegate.java` | 通过Core Registry解析provider并交final executor |
| 迁移测试 | Compare/all的TrackingProvider、ChangeTracker、withTracked、batch、exception测试族 | exactly-once public contract |
| 新增测试 | `TrackingExecutorContractTests.java`、`TrackingFailureMatrixTests.java`、`TrackingPhaseBudgetContractTests.java` | single/multi/failure/fatal/budget |

### 核心步骤

1. 按总体设计异常矩阵建立参数非法、begin null/throw、action throw、capture throw、close普通/fatal的表驱动红测。
2. 实现防御复制的Target列表和batch scope状态机；target数、name长度、duplicate name在begin前校验。
3. 实现final executor，保证action调用点只有一个；normal/fatal close遵循Java suppressed语义和设计规范化边界。
4. 让default provider建立baseline slots并在capture阶段复用after+diff ledger；duration只合计两段基础设施时间且overflow-safe。
5. 删除SPI `withTracked`并收窄Options/compat；迁移all facade和manifest，翻转C-06。
6. 用AST/architecture test检查生产代码除executor外无`action.run/call/invoke` tracking sequencing副本。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=TrackingExecutorContractTests,TrackingFailureMatrixTests,TrackingPhaseBudgetContractTests test
./mvnw -pl tfi-compare,tfi-all -am -Dtest=TrackingExecutorContractTests,TfiTrackingFacadeContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：合法参数的所有普通非fatal基础设施分支action调用计数精确为1。
- [x] CP-2：业务/fatal异常保持同一实例；close只按合同suppressed，不替换primary。
- [x] CP-3：两份phase ledger跨全部target共享，action时间未计入。
- [x] CP-4：provider/facade/compat无第二action wrapper或ThreadLocal baseline。

### 禁止范围与回滚

本卡不接Flow aspect、不删除`TfiTrack`和session store、不迁Spring模块。回滚必须同时恢复SPI/executor/all facade与C-06测试；
不得只恢复provider `withTracked`而保留executor，避免两个action owner并存。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只建立tracking核心生命周期与facade消费。
- [x] **认知负担**：executor与scope分别表达顺序和资源生命周期。
- [x] **比例失调**：failure matrix和exactly-once占主体。
- [x] **ROI**：直接修复业务action可能执行两次的P1级风险。
- [x] **洁癖检测**：不提前处理Spring/Ops package。
- [x] **局部 vs 全局**：Flow hook和manual API都将复用同一executor。
- [x] **过度设计**：无handler chain、middleware或tracking metrics SPI。

**结论**：设计通过；TRK-02必须复用executor，禁止新增第二advice sequencing。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| facade直接合同 | 复用任务卡列出的消费者测试 | 新增`TfiTrackingFacadeContractTests`七条合同 | 原验收命令引用的类不存在；补齐直接证据后再迁旧routing断言，避免从实现反推合同 |
| `TrackingOptions`处置 | exact removal或单向compat mapping | 保留类型，实例仅持有一个immutable `CompareOptions` | inventory要求保留兼容表面；删除会扩大breaking，保留旧字段状态又会形成第二options owner |
| default runtime | provider建立canonical baseline | 两个内置provider均复用`DefaultCompareRuntimeHolder.INSTANCE` | 独立runtime字段会让默认入口出现不同Policy/extension graph |
| legacy facade字段 | 迁移`withTracked`消费者 | 保留source签名并将参数命名为`ignoredFields`，不参与Policy构造 | raw字段无法无歧义映射到冻结runtime，临时编译会形成第二equality domain |
| API兼容profile | 本卡breaking纳入统一门禁 | 本卡类型兼容且不在失败清单；profile仍被前序Wave未登记删除阻断 | 不跨owner补登记来掩盖历史缺口；SpotBugs与本卡japicmp证据单独保持为绿 |
| 长命令反馈 | 执行manifest追踪门禁 | 单类耗时64.42秒且日志稀疏 | 已在仓库规则记录：60秒内反馈，无输出时检查进程与Surefire报告，避免用户无状态等待 |

### 检查点结果

- [x] CP-1：begin null/普通异常、capture普通异常与普通close异常均执行action一次；typed输入在action前拒绝。
- [x] CP-2：业务异常、begin fatal及action/capture/close fatal的primary/suppressed身份合同全部通过。
- [x] CP-3：node与element ledger均跨target共享；确定性合同证明action耗时不消耗after phase deadline。
- [x] CP-4：SPI只保留typed begin，final executor唯一调用action；facade与compat不创建第二Policy或baseline owner。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | exactly-once、输入0副作用、failure/fatal/suppressed及phase budget合同28/28 |
| 完整性 | 25 /25 | Compare、all facade、旧routing/Options消费者、manifest与七模块package闭合 |
| 可维护性 | 24 /25 | executor/scope/provider职责唯一，compat仅单向映射；session/global历史表面按owner留给TRK-02 |
| 风险控制 | 24 /25 | Compare全量、消费者、package、SpotBugs及本卡japicmp均通过；整体api-compat仍有前序Wave缺口 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| P1/MUST | TRK01-R01 | provider的typed输入拒绝被当作普通基础设施失败，可能继续执行action | `TrackingExecutor.java:136` | 单独原样传播`CompareInputException`；越界options合同证明provider后action 0次 |
| P1/MUST | TRK01-R02 | scope冻结阶段fatal不在清理边界内，已建立baseline slot可能滞留引用 | `TrackingBatchSupport.java:48` | 整个slot创建与scope冻结纳入同一fatal边界，逆序close后原样抛出 |
| P1/MUST | TRK01-R03 | facade旧字段参数可能被解释成无效typed path或第二份per-call Policy | `TFI.java:977` | 参数明确为`ignoredFields`且不参与Policy；新增facade合同与`CMP-BRK-BEHAVIOR-0030` |
| P2/SHOULD | TRK01-R04 | target/name上限、disabled runtime、部分slot fatal和close fatal缺直接合同 | `TrackingExecutorContractTests.java:82` | 补齐上限/disabled及failure matrix合同，focused集合28/28通过 |

### 验证证据（2026-07-14）

| 命令/门禁 | 结果 |
|---|---|
| Tracking focused合同 | 退出0；28 tests，覆盖executor/failure/fatal/phase/options/legacy SPI |
| `./mvnw -pl tfi-compare test` | 退出0；2,332 tests，无失败、错误或跳过 |
| manifest合同 | 退出0；15 tests；最终回填复跑耗时68.17秒 |
| inventory/manifest/planning回填门禁 | 退出0；30/30（10 + 15 + 5），BUILD SUCCESS |
| Compare/all消费者 | Compare 9/9；all 113项通过，1项既有跳过 |
| 七模块`-DskipTests package` | 退出0；reactor闭集全部成功 |
| SpotBugs | 0 bugs / 0 errors |
| japicmp定向核验 | `TrackingOptions`、`DefaultTrackingProvider`、`TrackingProvider`均binary/source compatible；无`CLASS_NOW_NOT_EXTENDABLE` |
| `-Papi-compat verify -DskipTests` | 本卡类型不在失败清单；整体仍由前序Wave未登记删除阻断 |
| Javadoc/FQCN审查 | 本卡public API无blocking violation；模块历史存量578；生产代码无可执行FQCN |

审查结论：3个P1/MUST与1个P2/SHOULD均已关闭，无遗留MUST；DoD、CP及回填门禁全部通过，`CMP-TRK-02`已按依赖顺序直接启动。
