# TASK-CMP-COL-02：修正Set与Entity identity/content语义

> **定位**：统一keyed Entity在List、Set、Map value三条路径的配对与深比较，关闭ID-equals吞字段差异。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-COL-01`
> **后续**：`TASK-CMP-TRK-01`
> **架构来源**：总体设计§9.5、§9.8、W3；ADR-012
> **消费不变量**：8-10、19-20

---

## 一、核心（设计时填）

### 背景

当前entity ID或`equals()`可能同时承担配对与内容相等，Set无序/含null时也存在不确定路径，keyed List MOVE与字段变化可能互相覆盖。
本卡把identity严格限制为候选配对，配对成功后仍用同一kernel深比较内容；同一规则同时覆盖List、Set和Map value。

### 目标（DoD）

- [x] Entity/ValueObject互斥；无Key、Key非scalar/不可访问、冲突annotation得到typed E/W语义。
- [x] ID/key/非scalar`equals()`只建立候选配对，配对后继续字段深比较；三条容器路径均可见字段变化。
- [x] keyed List唯一key可发布MOVE及字段变化；duplicate/unresolved key为W2201，不覆盖元素。
- [x] Set含null、混合类型、重复canonical identity和不同迭代顺序时结果确定且不丢变化。
- [x] 未标注POJO/ValueObject即使`equals()==true`仍比较字段；只有closed scalar或typed comparator可终局相等。
- [x] inheritance/shadow字段顺序稳定，不使用`getId/toString/identityHashCode` fallback。
- [x] Compare/all/examples/JMH的entity/set/keyed-list测试与直接实例化消费者同步迁移。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| identity/content分离 | 高 | 防止ID-equals漏字段 |
| ambiguity与确定性 | 高 | 不覆盖、不依赖迭代顺序 |
| 三路径一致性 | 高 | List/Set/Map不能各自解释Entity |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| Entity identity | KRN exact key只配对 | 身份相同不等于内容相同 | ID相同即EQUAL |
| keyed List | unique key MOVE + deep diff | 同时表达位置和字段变化 | 只MOVE或只按index |
| ambiguity | typed limitation +保留可确认变化 | 不猜测一对一关系 | first-wins覆盖 |

## 二、执行（设计时填）

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 重写 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/SetCompareStrategy.java` | deterministic bounded pairing |
| 重写 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/compare/list/EntityListStrategy.java` | unique-key MOVE + deep content |
| 收窄 | `tracking/compare/entity/EntityChangeGroup.java`、`EntityListDiffResult.java`、`EntityOperation.java` | 只表达新合同或exact removal |
| 修改 | `tracking/compare/MapCompareStrategy.java`、`list/ListCompareExecutor.java` | 复用统一entity pairing，不复制identity规则 |
| 修改 | `registry/ObjectTypeResolver.java`、`ValueObjectStrategyResolver.java`及六个descriptor annotation解析 | 互斥/Key/field顺序/extension优先级 |
| 迁移测试 | Compare/all的Set、Entity、EntityList、MapEntity、ReferenceSemantic测试族 | 删除旧degrade/equals/fallback断言 |
| 迁移消费者 | examples `Demo05_CollectionEntities.java`、`Demo06_SetCollectionEntities.java`、`Demo07_MapCollectionEntities.java`及entity/MapSet JMH | 三路径目标合同 |
| 新增测试 | `SetEntityComparisonPropertyTests.java`、`EntityIdentityContentContractTests.java`、`KeyedListMoveContractTests.java` | property、三路径、MOVE/ambiguity |

未写模块前缀的路径均位于`tfi-compare/src/main/java/com/syy/taskflowinsight/`。

### 核心步骤

1. 建descriptor truth table，先验证Entity/ValueObject/Key/ShallowReference组合与inheritance/shadow顺序。
2. 实现统一candidate pairing helper，输入为exact key facts，输出确定候选/ambiguity；不得调用用户equals作终局判断。
3. Set和keyed List复用该helper；Map value遇到entity时复用同一深比较plan。
4. 发布MOVE与字段change时保持path/side/ordering稳定；duplicate/unresolved保留所有可确认变化和omitted counters。
5. 迁移all/examples/JMH与manifest，翻转C-03剩余部分；用property tests跨插入顺序和重复运行比较canonical bytes。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=SetEntityComparisonPropertyTests,EntityIdentityContentContractTests,KeyedListMoveContractTests test
./mvnw -pl tfi-compare,tfi-all,tfi-examples -am -Dtest=SetEntityComparisonPropertyTests,EntityConsumerContractTests,EntityExamplesContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：ID-equals但字段不同在List/Set/Map value三路径都返回字段change。
- [x] CP-2：duplicate/unresolved/null/mixed输入不覆盖元素，结果与迭代顺序无关。
- [x] CP-3：只有closed scalar/typed comparator可终局equals；无getId/toString/hash fallback。
- [x] CP-4：旧entity/list degrade与白盒消费者已迁移，W3全消费者为绿。

### 禁止范围与回滚

不新增通用matching pipeline、编辑距离或heuristic rename。回滚按COL-02 -> COL-01逆序，必须同时恢复三路径消费者；
不得回滚KRN exact key wire或让不同容器使用不同identity定义。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只处理Set/keyed Entity和三路径一致性。
- [x] **认知负担**：一个candidate pairing helper对应真实复用，不引入handler链。
- [x] **比例失调**：identity/content与ambiguity占主体。
- [x] **ROI**：关闭红队指出的ID-equals漏差异路径。
- [x] **洁癖检测**：不追求全局最优matching。
- [x] **局部 vs 全局**：统一List/Set/Map value语义，减少后续分叉。
- [x] **过度设计**：不新增可扩展matching SPI或表达式语言。

**结论**：设计通过；完成后W3整体才可判绿。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 旧Entity白盒测试 | 迁移旧degrade/equals/fallback断言 | 改为6个公开typed结果合同 | 私有字符串key helper已删除，恢复它们会重建第二套identity规则 |
| Entity结果聚合 | 按typed path聚合 | 内部使用完整`EntityKeySegment`，字符串仅作展示 | Review证明不同类型identity可投影成相同`entity[1]`，不能用展示文本分组 |
| API兼容profile | 本卡breaking纳入统一门禁 | COL-02 exclusion生效；profile仍被前置Wave未登记删除阻断 | 不在本卡扩展manifest掩盖KRN/POL owner缺口；SpotBugs保持0 |

### 检查点结果

- [x] CP-1：List、Set、Map value三路径的ID-equals字段差异合同全部通过。
- [x] CP-2：duplicate/unresolved/null/mixed及插入顺序性质合同通过，W2201不覆盖唯一兄弟变化。
- [x] CP-3：候选解析只消费exact typed key；业务`getId/toString/hashCode/equals`不承担终局相等。
- [x] CP-4：旧白盒和Demo06字符串identity链已迁移，Compare/all/examples/JMH与package闭集为绿。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | 三路径identity/content、MOVE、W2201、mixed Set及typed聚合合同；Compare全量2,321/2,321 |
| 完整性 | 25 /25 | Compare/all/examples/JMH、Demo05/06/07、manifest与直接实例化消费者均闭合 |
| 可维护性 | 24 /25 | descriptor/candidate owner唯一，旧字符串helper删除；既有`EntityListDiffResult`热点未越界重构 |
| 风险控制 | 24 /25 | 核心/消费者/bench/package/SpotBugs均通过；api-compat仅剩前置Wave manifest缺口 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| P1/MUST | COL02-R01 | typed keyParts经display path反解析，转义`]`会截断组件 | `EntityListDiffResult.java:277` | 直接消费`EntityKeySegment.components()`；特殊字符合同18/18通过 |
| P1/MUST | COL02-R02 | 不同typed identity可因相同`entity[1]`展示文本被合并 | `EntityListDiffResult.java:225` | 内部按完整typed segment分组，字符串仅保留为兼容投影 |
| P2/SHOULD | COL02-R03 | `getKeyParts`错误承诺反射`@Key`声明顺序 | `EntityChangeGroup.java:155` | 改为路径identity组件顺序并说明非反射来源 |
| P3/STYLE | COL02-R04 | Entity消费者测试残留可执行FQCN且活跃API注释tag不完整 | `EntityFilterBranchTests.java:3` | 改用imports；活跃六类Javadoc扫描无violation |

### 验证证据（2026-07-14）

| 命令/门禁 | 结果 |
|---|---|
| 任务卡核心命令 | 退出0；12 tests（Set property 5、identity/content 3、keyed List 4） |
| Compare/all/examples消费者命令 | 退出0；5 + 3 + 3 tests，7个reactor项目成功 |
| `./mvnw -pl tfi-compare test` | 退出0；2,321 tests，无失败、错误或跳过 |
| Entity聚合与分支回归 | 退出0；特殊key与typed碰撞合同18 tests，聚合/分支组合82 tests |
| 七模块`-Pbench` test-compile | 退出0；JMH与bench test源码编译通过 |
| 七模块消费者package | 退出0；all、ops、examples及依赖闭集成功 |
| manifest/inventory/planning/API removal | 退出0；40 tests |
| `./mvnw -pl tfi-compare -Papi-compat verify -DskipTests` | SpotBugs 0；COL-02两个exclusion生效；前置Wave未登记删除使japicmp整体退出1 |
| Javadoc/FQCN审查 | 本卡触及六个public实现类型无violation；目标消费者无可执行FQCN |
| 架构启发式自检 | 无MUST；两个既有大类热点登记为SHOULD，本卡未新增抽象层或扩展重构范围 |

审查结论：2个P1、1个P2和1个P3均已关闭，无遗留MUST；DoD与CP全部通过，未启动`CMP-TRK-01`。
