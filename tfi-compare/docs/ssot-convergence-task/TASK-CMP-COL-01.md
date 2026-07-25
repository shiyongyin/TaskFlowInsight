# TASK-CMP-COL-01：修正Map与普通List无损确定性语义

> **定位**：先关闭present-null、覆盖entry和普通List路由漏报，为keyed Entity配对提供稳定容器主干。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-KRN-02`
> **后续**：`TASK-CMP-COL-02`
> **架构来源**：总体设计§9.5、W3；ADR-012
> **消费不变量**：8-10、19-20

---

## 一、核心（设计时填）

### 背景

当前Map会混淆absent与present-null并尝试heuristic rename；List可在LCS/Levenshtein/AsSet/auto-route和采样路径间切换，
导致同一输入语义依配置或规模变化。本卡固定Map REMOVE+ADD/MODIFY与普通List ordered-index语义，删除lossy路由；
Set和keyed Entity MOVE在后继卡统一处理。

### 目标（DoD）

- [x] Map精确区分absent、present-null、null key/value、mixed/addressable/unaddressable key，不覆盖合法entry。
- [x] 不做heuristic key rename；不同key固定REMOVE+ADD，same key value继续深比较。
- [x] 普通List按index比较，null/重复值/嵌套container不漏报且结果确定。
- [x] 删除LCS、Levenshtein、AsSet、auto-route、sampling和K-pairs degradation生产路径及对应配置/behavior。
- [x] summary只能作为bounded representation，不能短路元素比较或证明EQUAL。
- [x] Map/List结果在重复运行、不同插入顺序适用场景和hash seed下canonical bytes稳定。
- [x] Compare/all/examples/JMH消费者随卡迁移且全消费者编译为绿。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| Map存在性与无覆盖 | 高 | 防止真实entry消失 |
| List单一语义 | 高 | 删除按规模切换结果的路由 |
| 消费者/性能证据 | 中 | 删除lossy捷径后仍需同轴观测 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| Map key变化 | REMOVE + ADD | 无法可靠推断rename意图 | 相似值启发式rename |
| 普通List | ordered index | 顺序是List语义 | AsSet/LCS自动选择 |
| 超限 | limitation + partial | 真值可见 | sampling后声称complete |

## 二、执行（设计时填）

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 重写 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/MapCompareStrategy.java` | present-null、exact key、REMOVE/ADD/MODIFY |
| 重写 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/list/ListCompareExecutor.java`、`SimpleListStrategy.java` | ordered index唯一普通List路径 |
| 收窄 | `tracking/compare/CollectionCompareStrategy.java`、`ArrayCompareStrategy.java` | 共享ledger/path/result，不自建路由 |
| 删除 | `tracking/algo/seq/LongestCommonSubsequence.java`、`tracking/algo/edit/LevenshteinEditDistance.java`、`compare/list/LcsListStrategy.java`、`LevenshteinListStrategy.java`、`AsSetListStrategy.java`、`CompareRoutingProperties.java` | lossy/多路由退役 |
| 删除/收窄 | `tracking/rename/RenameHeuristics.java`、`tracking/summary/CollectionSummary.java`、`SummaryInfo.java` | rename删除；summary仅结果fact |
| 迁移测试 | Compare/all中Map、普通List、routing/degradation/summary测试族 | 目标合同替代旧算法白盒 |
| 迁移消费者 | examples `Demo04_Collections.java`、`Demo05_ListCollectionEntities.java`、`Demo07_MapCollectionEntities.java`及Map/List JMH | 新容器合同 |
| 新增测试 | `MapListComparisonPropertyTests.java`、`MapPresenceContractTests.java`、`OrderedListContractTests.java` | property/present-null/index语义 |

上述未写模块前缀的生产路径均位于`tfi-compare/src/main/java/com/syy/taskflowinsight/`，测试按同package镜像。

### 核心步骤

1. 建Map presence/key addressability truth table，覆盖null、mixed key、duplicate canonical key和unaddressable输入。
2. 用KRN-02 exact key wire排序/配对；不可配对项保留container-level change/limitation，不覆盖其他entry。
3. 固定普通List index plan并复用全请求ledger；删除所有规模/flag自动路由和sample shortcut。
4. 将summary降为ValueSnapshot representation，达到预算时由reducer产生partial。
5. 迁移all/examples/JMH和manifest，翻转C-03的Map/普通List部分；归档删除shortcut前后同轴报告但不设新SLA。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=MapListComparisonPropertyTests,MapPresenceContractTests,OrderedListContractTests test
./mvnw -pl tfi-compare,tfi-all,tfi-examples -am -Dtest=MapListComparisonPropertyTests,MapListConsumerContractTests,MapListExamplesContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package
```

### 审核检查点

- [ ] CP-1：present-null与absent不混淆，任何unaddressable项不覆盖合法entry。
- [ ] CP-2：普通List只有ordered index路径；旧LCS/Levenshtein/AsSet/route零生产引用。
- [ ] CP-3：summary/sample/hash不证明equal，预算限制进入typed limitation。
- [ ] CP-4：all/examples/JMH无旧strategy直接实例化或config依赖。

### 禁止范围与回滚

本卡不最终定义keyed Entity MOVE或Set配对。回滚必须恢复Map/List owner、消费者、manifest和性能对照；不得只恢复auto-route配置
而不恢复其完整旧执行图，也不得影响KRN-02 exact key/path合同。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只处理Map和普通List，keyed Entity留给唯一后继owner。
- [x] **认知负担**：两种容器各一个显式plan，无自动路由。
- [x] **比例失调**：漏报边界占主体。
- [x] **ROI**：删除多个已知lossy路径并保持确定性。
- [x] **洁癖检测**：不追求最小编辑距离外观。
- [x] **局部 vs 全局**：复用KRN key/path/ledger，不复制规则。
- [x] **过度设计**：无新strategy registry或启发式rename层。

**结论**：设计通过；与COL-02串行修改collection共享热点。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| duplicate canonical Map key | exact key配对且不覆盖entry | budget窗口内识别同typed path重复项，转为W2201并只消费成员预算 | 伪造occurrence path会泄漏不稳定顺序；直接写同path会覆盖真实value |
| unaddressable Map entry预算 | 不阻断addressable sibling | addressable entry按canonical顺序优先，unaddressable entry仍消费`CONTAINER_MEMBER` | 同时满足合法事实可见与request-global `maxElements`记账 |
| 旧summary表面 | 删除或收窄为结果fact | 保留3.x诊断类型；`SUMMARY`兼容令牌完整展开元素，摘要不进入Diff | 避免本卡额外删除公共诊断API，同时关闭summary证明相等路径 |
| 兼容详细Map入口 | 复用统一Map语义 | 不再复制到`LinkedHashMap`，直接消费调用方Map | 复制会调用任意key回调并改变IdentityMap等容器的entry集合 |

### 检查点结果

- [x] CP-1：present-null/absent、null key、duplicate canonical key及unaddressable混合输入均有合同；合法entry不被覆盖。
- [x] CP-2：普通List固定ordered-index；LCS/Levenshtein/AsSet/auto-route在生产代码零引用。
- [x] CP-3：summary与截断表示不证明EQUAL；预算不足发布typed limitation与PARTIAL。
- [x] CP-4：all/examples合同与Map/List JMH已迁移，旧strategy/config生产引用为0。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | Compare全量2310项通过；present-null、duplicate canonical key、ordered-index和budget边界均有回归 |
| 完整性 | 25 /25 | Compare/all/examples/JMH、配置元数据、breaking manifest和删除合同同卡闭合 |
| 可维护性 | 24 /25 | Map/List兼容入口复用唯一request-local内核；未为配对引入第二registry或通用pipeline |
| 风险控制 | 24 /25 | 删除lossy路由，保留typed limitation；兼容详细入口不再触发任意key回调 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| MUST | CR-COL-01-1 | duplicate canonical Map key写入同一typed path会覆盖value | `RequestLocalSnapshot` | 增加有界重复检测，歧义成员只记预算并发布W2201；已补IdentityMap回归 |
| MUST | CR-COL-01-2 | 详细Map兼容入口复制容器会调用key回调并改变entry语义 | `MapCompareStrategy` | 删除Map复制，直接委托唯一内核；已补爆炸key回调回归 |
| SHOULD | CR-COL-01-3 | unaddressable Map entry未消费`maxElements` | `RequestLocalSnapshot`、`TraversalFrame` | 增加非物化成员frame，合法entry优先且计数保持limit合同 |
| SHOULD | CR-COL-01-4 | 改动测试存在可执行FQCN，JMH基础字段和枚举常量缺少语义注释 | Compare/Examples改动文件 | 改用import并补充中文why注释；JMH相关Javadoc扫描无violation |
| INFO | CR-COL-01-5 | Compare Javadoc/Checkstyle仍有大量历史基线 | 模块历史代码 | 本卡不扩散清理；Checkstyle在模块阈值内通过，改动表面无blocking |

### 验证记录

- `./mvnw -pl tfi-compare test`：2310项通过。
- 本卡最终Map/snapshot/property回归38项通过；包含List与兼容入口的审查回归118项通过。
- 跨Compare/all/examples合同：6 + 3 + 2，共11项通过；命令带`-am`以确保消费当前工作区产物。
- `./mvnw -pl tfi-examples -am -Pbench -DskipTests test-compile`：JMH及bench测试源集编译通过。
- `./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package`：七模块闭集打包通过。
- breaking manifest中本卡登记API 53、BEHAVIOR 6、CONFIG 7、RESOURCE 1；退役类型、配置和生产引用扫描为0。
- `checkstyle:check`在模块既有阈值内通过；Javadoc启发式扫描的本卡JMH/摘要改动无violation。
- `-Papi-compat verify -DskipTests`仍受历史W2欠账阻断：`CompareDiffer`既有SpotBugs解析告警及KRN删除API未完整进入旧baseline manifest；
  本卡未修改baseline或把这些条目误归`CMP-COL-01`，本卡manifest合同自身通过。

**Review结论**：审查发现均已闭环，无遗留in-scope MUST；`CMP-COL-02`可直接启动。
