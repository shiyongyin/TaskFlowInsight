# TASK-CMP-OUT-01：建立canonical projection与masking

> **定位**：在任何formatter迁移前建立唯一、不可变、已脱敏的发布树和machine schema。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-TRK-02`
> **后续**：`TASK-CMP-OUT-02`
> **架构来源**：总体设计§11.1-11.3、§12.1、W5；ADR-014
> **消费不变量**：5、16-19、20

---

## 一、核心（设计时填）

### 背景

现有JSON/Map/XML/CSV/Markdown/Console/Streaming各自解释字段和值，mask规则也互相分叉，mutable export default还能全局关闭保护。
本卡建立`CompareProjectionFactory`和safe-default`MaskingPolicy`，所有machine/diagnostic输出只能消费同一棵immutable tree；
formatter迁移和旧格式删除由后继卡完成。

### 目标（DoD）

- [x] `CompareProjectionFactory`是Result + Metadata -> masked immutable tree唯一owner；formatter不得读业务对象/raw result。
- [x] schema v1字段、顺序、ValueSnapshot wire、similarity optional和omitted counters按总体设计§11.3固定。
- [x] JSON parser tree与Map tree基于同一projection exact parity，Map深度不可修改。
- [x] safe masking floor同时应用field/path规则与内置Luhn支付卡/SSN内容检测；所有EXACT scalar完整扫描。
- [x] 动态Map/Set/entity segment不进入code/Spring raw pattern；敏感key/component/metadata与普通value同样redact。
- [x] 多个值mask为同一token时使用`maskedOccurrence`稳定区分，不合并change、不泄漏raw/hash。
- [x] include-sensitive只允许显式单次projection调用传入代码构造immutable policy；Spring/config/annotation/global default不能开启。
- [x] projection构建和encoder使用显式frame与hard depth/total-char预算，不在深图上递归或重新读取业务图。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 唯一schema/projection | 高 | 输出一致性的结构基础 |
| masking安全floor | 高 | 防止跨格式敏感泄漏 |
| bounded wire/parity | 高 | machine consumer可复制且有界 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 发布边界 | immutable projection | Result是进程内事实，不是安全视图 | formatter直接serialize result |
| masking | typed rules + bounded built-in detector | 安全默认不可被弱配置覆盖 | 每格式regex与mutable flag |
| machine格式 | one tree, JSON/Map parity | schema只有一个owner | 两套builder保持“相似” |

## 二、执行（设计时填）

### 文件与接口

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/projection/CompareProjection.java`、`ProjectionNode.java` | immutable canonical tree |
| 新增 | 同目录`CompareProjectionFactory.java`、`ProjectionMetadata.java` | 唯一result->tree owner与bounded metadata |
| 新增 | 同目录`MaskingPolicy.java`、`ProjectionOptions.java`、`MaskedOccurrence.java` | safe floor、单次options、collision-safe ordering |
| 新增internal | `tracking/projection/internal/SensitiveValueDetector.java`、`ProjectionFrame.java` | bounded Luhn/SSN和iterative construction |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/exporter/change/CanonicalChangeJsonEncoder.java`、`CanonicalChangeMapEncoder.java` | 只编码同一projection |
| 修改过渡 | `ChangeJsonExporter.java`、`ChangeMapExporter.java`、`tracking/render/MarkdownRenderer.java`、`ChangeConsoleExporter.java` | W5内先委托projection；OUT-02完成API收口 |
| 新增测试资源 | `tfi-compare/src/test/resources/golden/compare-projection-v1/` | schema、masking、wire、escaping golden |
| 新增测试 | `CompareProjectionSchemaContractTests.java`、`CompareProjectionParityTests.java`、`CompareMaskingGoldenTests.java`、`ProjectionBoundednessTests.java` | schema/parity/security/depth |

### 核心步骤

1. 先以parser tree冻结schema field/order/type和ValueSnapshot tagged wire；invalid result/projection tuple构造失败。
2. 实现immutable projection tree和explicit frame factory，使用ProjectionOptions/metadata预算并保留omitted facts。
3. 实现MaskingPolicy构造期规则校验与full-scan detector，覆盖Luhn 13-19位边界、SSN命中/不命中和超限OMITTED。
4. 实现JSON/Map encoder对同一prebuilt projection的exact parity，禁止Jackson生产依赖。
5. 让四个保留formatter临时委托projection，删除/禁用其私有mask判断入口但`MaskRuleMatcher`最终删除留给OUT-02。
6. 翻转C-05的mask/schema部分，并给manifest的SCHEMA/BEHAVIOR entry绑定golden test。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareProjectionSchemaContractTests,CompareProjectionParityTests,CompareMaskingGoldenTests,ProjectionBoundednessTests test
./mvnw -pl tfi-compare,tfi-all,tfi-examples -am -Dtest=CompareProjectionParityTests,ProjectionConsumerContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：JSON/Map只接收同一prebuilt projection且parser tree exact相等。
- [x] CP-2：所有EXACT scalar完整扫描，metadata/key/member/component无旁路。
- [x] CP-3：include-sensitive无Spring/config/annotation/global入口，safe floor不可弱化。
- [x] CP-4：projection/encoder显式frame且受depth/total-char硬上限；formatter不读业务对象。

### 禁止范围与回滚

本卡不删除CSV/XML/Streaming或最终改Render SPI。回滚必须同时撤projection、两个encoder、四个临时delegate和golden；
不得让新masking与旧schema tree混用。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只建立发布安全边界，不提前处理全部formatter API。
- [x] **认知负担**：Result、Projection、Formatter三层各自单责。
- [x] **比例失调**：schema/masking/boundedness占主体。
- [x] **ROI**：关闭跨格式mask漂移和raw result发布风险。
- [x] **洁癖检测**：不新增mask detector SPI或任意regex语言。
- [x] **局部 vs 全局**：W5/W6所有输出与Ops只消费同一tree/facts。
- [x] **过度设计**：一个factory、两个编码器对应真实JSON/Map变体。

**结论**：设计通过；OUT-02只能消费projection，不能重建字段树。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 过期输出测试清理 | 翻转C-05的mask/schema断言 | 直接删除旧Markdown私有布局、旧`statistics`树、system-property masking和mutable include-sensitive断言；保留canonical projection公共合同 | 旧断言与OUT-01已冻结的唯schema/safe floor冲突，保留会重新制造歧义 |
| 长时间门禁反馈 | 长命令持续等待完成 | 60秒无新输出时检查Surefire报告与JVM；本次manifest合同实际耗69.52秒后正常完成 | 区分确定性长扫描与真实挂起，避免用户在无反馈状态下等待 |
| Compare全量命令边界 | 直接执行单模块测试 | 无`-am`时Surefire discovery加载本地旧Flow starter并缺少`TfiTaskDeepTrackingDelegate`；最终统一使用reactor命令 | Compare测试临时依赖当前Flow hook，单模块本地仓库快照不是有效验收环境 |
| Surefire数量取证 | 汇总报告目录 | 未clean目录保留已删除测试的旧XML；最终只统计本次命令时间窗内更新的报告 | 防止把历史51 failures/2 errors误报为当前结果 |
| 旧输出配置残留 | 本卡建立projection安全边界 | `include-sensitive-info`仍只服务CSV/XML/Streaming，不能进入canonical policy；exact removal留给OUT-02 | OUT-02明确拥有非目标格式、mutable ExportConfig和配置表面删除，禁止跨卡抢跑 |

### 检查点结果

- [x] CP-1：JSON/Map对同一prebuilt projection做parser-tree equality；四个formatter的projection入口均不重建schema。
- [x] CP-2：field/path、Luhn/SSN、metadata、Map key、Set member、Entity component及mask碰撞合同通过，无raw/hash旁路。
- [x] CP-3：只有`MaskingPolicy.explicitlyIncludeSensitiveValues()`可逐次opt-in；旧mutable配置在四个迁移入口被忽略。
- [x] CP-4：factory校验与两个encoder均使用显式frame和固定depth/text ceiling；长typed path不形成递归schema深度。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | schema、ValueSnapshot wire、parity、masking、collision与boundedness合同26/26通过 |
| 完整性 | 25 /25 | JSON/Map/Markdown/Console、golden、C-05、manifest及all/examples消费者均已迁移到本卡边界 |
| 可维护性 | 24 /25 | projection/masking owner唯一、adapter单向且无可执行FQCN；最终旧SPI/format表面按计划由OUT-02删除 |
| 风险控制 | 25 /25 | 全reactor 3054项、消费者定向6项和七模块package均通过；长门禁与陈旧报告已有明确诊断规则 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| 无MUST | OUT01-R00 | 未发现P0/P1/MUST；旧CSV/XML/Streaming和mutable配置属于OUT-02已登记删除范围 | - | 保持任务边界并直接激活OUT-02 |
| P2/SHOULD | OUT01-R01 | INDEX表格已激活OUT-02，但顶部实施状态仍指向OUT-01，形成双重当前任务 | `INDEX.md:5` | 同步为`IN_PROGRESS_W5_CMP_OUT_02`并重跑规划门禁 |

### 验证证据（2026-07-14）

| 命令/门禁 | 结果 |
|---|---|
| projection schema/parity/masking/boundedness定向命令 | 退出0；26/26 |
| Compare/all/examples parity+consumer命令 | 退出0；6/6，七模块reactor成功 |
| `./mvnw -q -pl tfi-compare -am test` | 退出0；Core 727、Flow starter 122、Compare 2205，共3054项且0 failure/error |
| `./mvnw -pl tfi-all,tfi-ops-spring,tfi-examples -am -DskipTests package` | 退出0；7个reactor模块全部SUCCESS |
| projection golden与characterization/planning | golden相关22/22、C-05与规划合同9/9通过；新增当前任务标题/表格一致性负例 |
| breaking manifest owner/consumer解析 | 退出0；schema与behavior entry均可解析到可执行consumer test |
| Javadoc/FQCN/MUST扫描 | 新增代码无blocking violation；枚举/基础字段逐项注释；触及范围可执行FQCN和raw formatter旁路均为0 |

审查结论：1个P2已关闭，无P0/P1/MUST遗留；8项DoD与CP-1至CP-4全部通过。OUT-01完成后按依赖顺序直接激活`CMP-OUT-02`。
