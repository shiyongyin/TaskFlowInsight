# TASK-CMP-TRK-02：接入TfiTask hook并退役session伪事实

> **定位**：在Flow真实stage内调用唯一TrackingExecutor，并原子删除Compare ThreadLocal、session store与虚假Ops能力。
> **状态**：已完成
> **审核状态**：已通过
> **依赖**：`TASK-CMP-TRK-01`
> **后续**：`TASK-CMP-OUT-01`
> **架构来源**：总体设计§10.3、W4；ADR-013 `CMP_G4/G6`
> **消费不变量**：12-15、19-20

---

## 一、核心（设计时填）

### 背景

Compare当前有独立deep-tracking aspect、ThreadLocal和`SessionAwareChangeTracker`全局map，Ops把这些未证明由主链维护的数据当作
session事实。Core `TfiTask`已经拥有sampling/condition/stage激活。本卡在Flow starter增加0..1最小delegate hook，
由Compare临时实现并调用TRK-01 executor；同时删除`TfiTrack`、global store及依赖它们的Ops/all能力，不返回伪零值。

### 目标（DoD）

- [x] Flow starter拥有`TfiTaskDeepTrackingDelegate`单方法hook；无delegate直接proceed一次，多delegate启动失败。
- [x] `TfiAnnotationAspect`只在sampling/condition通过且stage已激活时调用delegate，Flow disabled同样跳过。
- [x] Compare临时delegate只调用一次`TrackingExecutor.execute`，不新增第二AOP/advice或重复sampling。
- [x] TfiTask字段按总体设计§10.3映射并在context启动前校验；参数名使用声明ordinal`arg-N`，不依赖`-parameters`。
- [x] `TfiTrack` type/member/Javadoc exact removal，替代路径只有`TfiTask(deepTracking=true)`与显式executor。
- [x] 删除Compare tracking ThreadLocal、`SessionAwareChangeTracker`和global changes/session查询。
- [x] Ops endpoint/stats/health及all/examples消费者同步删除或改为真实result/diagnostics，不返回伪零/空session。
- [x] W4结束时Flow/Compare/Ops/all/examples定向测试和全部消费者compile为绿。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| Flow/Compare生命周期边界 | 高 | Core继续拥有stage和sampling |
| global伪事实退役 | 高 | 防止Ops发布不存在的状态 |
| 消费者原子迁移 | 高 | SessionAwareChangeTracker有多处编译期消费者 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 声明式入口 | Flow 0..1 delegate | 保持Context/stage owner单一 | Compare第二aspect |
| TfiTrack | 4.0 exact removal | 无reader/advice/example，激活会新增未审功能 | 新建SpEL reader |
| Ops session数据 | 删除虚假能力 | Compare无持久化history owner | 迁移global map到Ops |

## 二、执行（设计时填）

### 文件与接口

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 | `tfi-flow-spring-starter/src/main/java/com/syy/taskflowinsight/aspect/TfiTaskDeepTrackingDelegate.java` | 0..1 execute hook及Invocation接口 |
| 修改 | `tfi-flow-spring-starter/src/main/java/com/syy/taskflowinsight/aspect/TfiAnnotationAspect.java` | sampling/condition/stage内唯一delegate调用 |
| 修改 | `tfi-flow-spring-starter/src/main/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfiguration.java` | zero/one bean wiring与multi-bean fail-fast |
| 新增临时实现 | `tfi-compare/src/main/java/com/syy/taskflowinsight/aspect/DefaultTfiTaskDeepTrackingDelegate.java` | TfiTask -> CompareOptions -> TrackingExecutor |
| 删除 | `tfi-compare/src/main/java/com/syy/taskflowinsight/aspect/TfiDeepTrackingAspect.java`、`annotation/TfiTrack.java` | 不保留第二advice/no-op表面 |
| 删除/收窄 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/SessionAwareChangeTracker.java`、`ChangeTracker.java` | global/thread-local query清零；必要compat只委托executor |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/config/DeepTrackingAutoConfiguration.java`、`ChangeTrackingAutoConfiguration.java` | 临时delegate bean；W6再迁新starter |
| 修改/删除消费者 | Ops `TfiEndpoint.java`、`TfiAdvancedEndpoint.java`、`actuator/support/TfiHealthCalculator.java`、`TfiStatsAggregator.java`及相关tests | 删除session/global change能力或改真实diagnostics |
| 修改消费者 | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`及tracking/actuator tests，examples tracking demos | explicit executor/TfiTask路径 |
| 修改构建 | `tfi-compare/pom.xml` | W4临时optional依赖flow starter；W6原子反转依赖owner |
| 新增测试 | Flow `TfiTaskDeepTrackingDelegateContractTests`；Compare `TfiTaskTrackingIntegrationContractTests`；Ops `CompareTrackingEndpointContractTests` | activation/once/failure/consumer闭集 |

Ops完整文件前缀固定为`tfi-ops-spring/src/main/java/com/syy/taskflowinsight/actuator/`；support文件位于其`support/`子目录。

### 核心步骤

1. 先在Flow starter写无delegate/单delegate/多delegate、sampling/condition/disabled/业务异常合同测试。
2. 增加最小hook并在已激活stage内委托；Invocation只允许proceed一次并原样返回/抛出。
3. 实现Compare临时delegate，启动期校验TfiTask字段，调用TRK-01 executor；publication普通失败不覆盖业务成功/异常。
4. 删除旧Compare aspect和`TfiTrack`，登记API/BEHAVIOR replacement；全仓搜索不得有reader/advice/SpEL残留。
5. 删除ThreadLocal/session map/global query；同步迁移Ops/all/examples与白盒测试，删除无法提供真实数据的endpoint字段/操作。
6. 验证W4临时依赖无环，并记录W6迁移/回滚要求；翻转session/tracking characterization。

### 验证命令

```bash
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all -am -Dtest=TfiTaskDeepTrackingDelegateContractTests,TfiTaskTrackingIntegrationContractTests,CompareTrackingEndpointContractTests,TfiTrackingFacadeContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples -am -DskipTests package
! rg -n 'TfiTrack|SessionAwareChangeTracker' tfi-compare/src/main/java tfi-ops-spring/src/main/java tfi-all/src/main/java tfi-examples/src/main/java
! rg -n 'ThreadLocal<' tfi-compare/src/main/java/com/syy/taskflowinsight/tracking tfi-compare/src/main/java/com/syy/taskflowinsight/spi tfi-compare/src/main/java/com/syy/taskflowinsight/aspect -g '!**/monitoring/**'
```

### 审核检查点

- [x] CP-1：Flow aspect是唯一sampling/stage owner，业务Invocation和Tracking action均恰好一次。
- [x] CP-2：无第二advice、SpEL、Compare task-depth或tracking ThreadLocal。
- [x] CP-3：Ops/all无session global store编译依赖或伪零/空能力。
- [x] CP-4：hook临时实现/依赖的W6迁移和逆序回滚边界有contract test。

### 禁止范围与回滚

不在本卡新建Compare starter、metrics SPI或session history sink。回滚必须同时撤销hook、临时依赖、delegate、store删除及消费者；
不得恢复`TfiTrack`为新功能，也不得只恢复Ops endpoint而无真实state owner。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只连接既有TfiTask生命周期并删除伪事实。
- [x] **认知负担**：一个单方法hook，无handler list/context。
- [x] **比例失调**：生命周期和消费者闭合占主体。
- [x] **ROI**：消除第二AOP owner与全局session虚假观测。
- [x] **洁癖检测**：不迁移无真实需求的history store。
- [x] **局部 vs 全局**：Flow、Compare、Ops、all在同Wave闭合。
- [x] **过度设计**：hook是依赖反转的有理由例外，不泛化middleware。

**结论**：设计通过；W6只能迁移delegate实现，不得重新解释TfiTask语义。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| hook唯一性 | `ObjectProvider#getIfAvailable()`用于0..1装配 | 改为枚举最多两个bean并显式拒绝多owner，`@Primary`也不能消除歧义 | Spring默认primary选择会让两个delegate静默变成一条隐式路由，违反0..1合同 |
| 旧tracker处置 | `ChangeTracker`可按必要性收窄 | `ChangeTracker`及其异常、`SessionAwareChangeTracker`和metadata全部exact removal | 已无真实消费者；保留兼容壳只会继续暗示global lifecycle存在 |
| Ops残留 | 迁移endpoint/stats/health直接消费者 | 额外删除无生产reader的`SessionIdMasker`、`EndpointAccessLog`、Secure端点Micrometer依赖、访问日志状态和5个伪配置键 | 审查发现这些残留仍暗示session/history能力，按4.0直接删除原则闭合 |
| API兼容门禁 | 本卡exclusion应生效 | 本卡type/member exclusion全部生效；Japicmp整体仍因前置Wave未登记删除退出1 | 与COL-02/TRK-01已记录基线一致，本卡不越权登记其他owner的历史删除 |
| 全reactor回归 | W4消费者闭集为绿 | Core/Flow/Compare/Ops全绿；`tfi-all`全量1767项中32项仍为W5输出/query/reference/masking/collection旧断言 | 失败不触及本卡路径，保留给已分配的后续Wave，避免污染W4语义 |
| 长命令反馈 | 常规等待测试结束 | 30秒说明当前测试类；约55秒检查Surefire更新时间与JVM，确认完成后回收退出码 | manifest单类约79秒无持续控制台输出，需要主动区分计算、卡死与尾输出延迟 |

### 检查点结果

- [x] CP-1：Flow是唯一advice/sampling/stage owner；0/1/multi/primary-multi及action once合同通过。
- [x] CP-2：生产源码中无第二advice、`TfiTrack`、Compare tracking ThreadLocal或参数名依赖，两个任务卡`rg`均零结果。
- [x] CP-3：Ops只发布单次`ContextMetrics`事实；session/history helper、写操作、伪零字段和无reader配置已删除。
- [x] CP-4：Flow hook与Compare临时delegate的模块边界、无第二aspect及W6迁移边界均由auto-configuration/removal合同锁定。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | sampling/condition/disabled、ordinal映射、启动校验、业务/fatal/publication矩阵与primary多owner合同通过 |
| 完整性 | 25 /25 | Flow、Compare、Ops、all、examples消费者闭合；旧API、store、helper、metadata及构建产物均无残留 |
| 可维护性 | 25 /25 | 一个Flow hook、一个final executor路径、一个Context事实源；无no-op、global map或假兼容层 |
| 风险控制 | 24 /25 | focused/full/clean package/manifest均通过；仅保留已分配给后续Wave的all与Japicmp历史基线 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| P1/MUST | TRK02-R01 | `getIfAvailable()`可在两个delegate中静默选择`@Primary`，破坏0..1 owner合同 | `TfiAnnotationAspect.java:122` | 显式枚举并拒绝多bean；新增primary多owner启动失败合同，Flow全量122/122 |
| P1/MUST | TRK02-R02 | Ops仍保留伪session配置、孤立helper、未使用Micrometer依赖和write-only访问日志状态 | `CompareTrackingEndpointContractTests.java:80` | 直接删除2个生产类型、1个过期测试、5个配置键及无效依赖/状态；Ops全量69/69 |
| P2/SHOULD | TRK02-R03 | 触及实现存在可执行FQCN、基础数值字段无逐项语义注释及旧auto-config Javadoc缺合同tag | `SecureTfiEndpoint.java:57` | 改用import，补中文意图注释和触及类型Javadoc；定向checker无blocking violation |

### 验证证据（2026-07-14）

| 命令/门禁 | 结果 |
|---|---|
| W4 expanded focused合同 | 退出0；46 tests（Flow/config 20、Compare 13、Ops 6、all facade 7） |
| compatibility manifest/removal | 退出0；19/19（15 + 4）；manifest单类79.24秒 |
| 任务卡/INDEX规划追踪门禁 | 退出0；5/5，状态、owner、测试映射与后继激活一致 |
| `./mvnw -pl tfi-flow-spring-starter -am test` | 退出0；Core 727/727、Flow 122/122 |
| `./mvnw -pl tfi-compare -am test` | 退出0；Compare 2251/2251（最终manifest另行复跑） |
| `./mvnw -pl tfi-ops-spring test` | 退出0；69/69 |
| all Secure endpoint回归 | 退出0；22/22 |
| 七模块`clean package -DskipTests` | 退出0；空target重编译后7个reactor项目全部成功 |
| clean jar内容核验 | 0个已删class、0个已删配置键进入`tfi-ops-spring`产物 |
| 两条任务卡`rg`及扩展Ops/FQCN扫描 | 目标源码均零结果；JSON资源均可解析 |
| `-Papi-compat verify -DskipTests` | 本卡exclusion生效；整体由前置Wave历史删除基线退出1 |
| Javadoc/FQCN审查 | 触及实现无blocking violation、无可执行FQCN；枚举/基础字段逐项语义注释符合本卡约束 |

审查结论：2个P1/MUST与1个P2/SHOULD均已关闭，无遗留MUST；DoD与CP全部通过，`CMP-OUT-01`已按依赖顺序直接激活。
