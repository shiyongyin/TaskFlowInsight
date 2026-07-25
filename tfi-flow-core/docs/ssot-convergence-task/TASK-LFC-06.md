# TASK-LFC-06：统一 debug 与 warn 消息语义

> **定位**：为 Java model 增加显式 severity，并让 `TaskContext` 与 direct factory 产出一致，同时冻结 V1 wire shape。
> **状态**：完成（2026-07-10）
> **审核状态**：审核通过（2026-07-11；factory 映射、入口等价与 V1 wire 边界 fresh 验证）
> **依赖**：前置 `G0-green`；不依赖 L2-L5/C1-C6，可与 `TASK-LFC-01`、`TASK-LFC-02` 分批执行。
> **架构来源**：master Wave 1；lifecycle/context 计划 `L6`。

---

## 一、核心（设计时填）

### 背景

`TaskContextImpl.debug/warn` 当前写成带 `[DEBUG]`/`[WARN]` 前缀的 info message，而 `Message.debug/warn` 使用不同 `MessageType`。这使同一语义因入口不同而产生不同 model 和 wire 内容。本卡引入独立 `MessageSeverity`，但 V1 JSON/Map 的 keys、type 表示与无 `severity` 字段契约完全不变。

### 输入、输出与不可变契约

- 固定映射：`info -> PROCESS/INFO`，`debug -> METRIC/DEBUG`，`warn -> ALERT/WARN`，两种 `error -> ALERT/ERROR`，`withType/withLabel -> INFO`。
- `TaskContextImpl.debug/warn` 保持 closed/null/blank/trim/catch-`Throwable` guards，只把 successful write 改为 `addDebug/addWarn`。
- V1 JSON 继续写 `MessageType.name()`；Map 继续写 `MessageType.toString()` 的现有本地化值。
- V1 JSON object/Map entry 都不得出现 `severity` key；severity 仅是 Java model addition。
- 局部架构禁令：不得新增第二个 Context/Session/Provider-owner `ThreadLocal`、第二个 context registry 或第二个 cleanup scheduler；不得借本卡引入第二套 exporter schema/traversal。

### 目标（DoD）

- [x] 新增 public `MessageSeverity { INFO, DEBUG, WARN, ERROR }`。
- [x] `Message` 以 immutable field 存储 severity，并公开 `public MessageSeverity getSeverity()`。
- [x] 所有 public factory 的 category/severity 精确符合固定表。
- [x] `TaskContextImpl` 与 direct factory 除实例标识和 timestamp 外 recursive-equal，内容无文本 severity prefix。
- [x] V1 JSON/Map keys/type values 不变且没有 `severity` key。
- [x] core + `tfi-all` 聚焦测试全部绿色。

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| severity 表达 | 独立 enum/field | category 与 severity 是不同维度 | 继续用内容前缀编码 |
| V1 schema | 不暴露 severity | 本卡是 model correction，不是 schema version | 给 V1 新增字段 |
| TaskContext guard | 全量保留 | 兼容现有容错 | 直接无条件调用 factory |

## 二、执行（设计时填）

### 前置 Gate

只消费 `G0-green`。无需 G1/G2/G4，不得在本卡写 deprecation ledger 或 ADR：

```bash
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=CoreServiceLoaderContractTests,AllProviderServiceLoaderContractTests,ExportV1GoldenTests,PublicConstantCompatibilityTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 目标文件与签名

| 动作 | 文件 | 精确接口/测试 |
|---|---|---|
| 创建 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/enums/MessageSeverity.java` | `public enum MessageSeverity { INFO, DEBUG, WARN, ERROR }` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/Message.java` | `info(String)`、`debug(String)`、`warn(String)`、`error(String)`、`error(Throwable)`、`withType(String, MessageType)`、`withLabel(String, String)`、`public MessageSeverity getSeverity()` |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/api/TaskContextImpl.java` | `public TaskContext debug(String content)`、`public TaskContext warn(String content)` |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/MessageTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/TaskNodeTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/api/TaskContextImplTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/json/JsonExporterTest.java`、`tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/map/MapExporterTest.java` | factory matrix、entry equivalence、V1 schema |
| 修改测试 | `tfi-all/src/test/java/com/syy/taskflowinsight/api/TaskContextImplTest.java` | cross-artifact compatibility |

### 核心步骤

1. 先写映射表参数化测试，并补两条 `TaskContextImpl` vs direct factory recursive comparison，忽略 `timestampMillis`/`timestampNanos`。
2. 新增 enum 与 immutable field；所有构造/factory 必须显式赋 severity，`withType/withLabel` 固定 `INFO`。
3. 仅把 `TaskContextImpl.debug/warn` 的 successful write 从 `addInfo("[DEBUG] ...")`/`addInfo("[WARN] ...")` 改成 `addDebug/addWarn`。
4. 扩展 JSON/Map tests：direct 与 TaskContext entry type 相同、content 无 prefix、无 `severity` key。
5. 验证 JSON type 为 `PROCESS/METRIC/ALERT/ALERT`；Map type 保持现有本地化 `MessageType.toString()`，不得归一化为 enum name。

### 验证命令

```bash
./mvnw -pl tfi-flow-core -Dtest=MessageTest,TaskNodeTest,TaskContextImplTest test
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=MessageTest,TaskNodeTest,TaskContextImplTest,JsonExporterTest,MapExporterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### 风险与回滚边界

| 风险 | 控制 | 局部回滚 |
|---|---|---|
| V1 accidental field addition | parsed object/map key absence test | 回退 exporter 变更；model 可单独评估 |
| Map type 被改成 enum name | 精确本地化值断言 | 回退 formatter 修改 |
| guard 被弱化 | closed/null/blank/catch tests | 回退 `TaskContextImpl` 两个方法 |
| factory 漏赋 severity | 全 factory matrix | 回退 enum/model 变更 |

### 审核检查点

- [x] CP-1：固定 category/severity 表逐行通过。
- [x] CP-2：TaskContext 与 direct factory 等价。
- [x] CP-3：V1 无 `severity` key 且 type 表示不变。
- [x] CP-4：closed/null/blank/trim/catch-`Throwable` 行为未弱化。
- [x] CP-5：无第二 schema owner、traversal、`ThreadLocal`、registry 或 scheduler。

## 三、自省（设计完成后、实现前填）

| 维度 | 结论 | 依据 |
|---|---|---|
| 目标偏离 | 无 | model semantics 与 V1 compatibility 均明确 |
| 认知负担 | 适度增加 | 一个 enum 取代内容前缀隐式编码 |
| 比例失调 | 无 | wire compatibility 与 model mapping 同权 |
| ROI | 正向 | 消除入口相关语义漂移 |
| 洁癖检测 | 通过 | 不改 exporter traversal 或 V2 |
| 局部与全局 | 一致 | 独立 P0 batch，可单独回滚 |
| 过度设计 | 无 | 四值 enum，无 hierarchy/strategy |

**结论**：设计已消费 `G0-green` 并完成实施与验证；V1 shape 全程作为不可协商回退边界。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | V1 影响 |
|---|---|---|---|---|
| severity 存储 | immutable field | `MessageSeverity` final field；所有构造路径显式非空赋值 | category 与 severity 是正交语义，显式赋值可让遗漏在编译/测试阶段暴露 | 无；V1 exporter 不读取该字段 |
| exporter | 仅测试强化，无新 key | JSON/Map 生产 exporter 零修改，仅新增解析后精确断言 | V1 wire shape 已冻结，本卡只修正 Java model 与入口一致性 | 无字段、key 或 type 表示变化 |
| recursive comparison | 忽略两个 timestamp | 额外忽略 `messageId` | AssertJ 递归比较会触发延迟 ID getter；两个独立消息实例本就应有不同 ID，该字段不属于入口语义等价条件 | 无 |

### 检查点结果

| 检查点 | 验证动作 | 状态 | 证据 |
|---|---|---|---|
| CP-1 | factory matrix | 通过 | `MessageTest` 覆盖 7 个 public factory，固定 category/severity 全表通过 |
| CP-2 | recursive comparison | 通过 | Core 与 `tfi-all` 的 debug/warn 均与 direct factory 比较；仅忽略实例 ID 和两个时间戳 |
| CP-3 | parsed JSON/Map assertions | 通过 | JSON 精确为 enum name，Map 精确为既有本地化值；两者均断言无 `severity` key |
| CP-4 | guard regressions | 通过 | `TaskContextImplTest` 既有 closed/null/blank/trim/异常安全用例与新增等价用例同批绿色 |
| CP-5 | architecture review | 通过 | 生产只新增 enum/field 并替换两个 successful write；无 exporter、owner、registry、scheduler 改动 |

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 必填证据 |
|---|---|---|
| 正确性 | 25 /25 | factory matrix、TaskContext/direct equivalence、Core 113 + `tfi-all` 93 聚焦测试 |
| 完整性 | 25 /25 | 7 个 factories、TaskNode 四入口、JSON/Map、Core/`tfi-all` 全覆盖 |
| 可维护性 | 25 /25 | 四值 enum + immutable 非空字段；中文 Javadoc 解释 category/severity 分维原因与 V1 边界 |
| 风险控制 | 25 /25 | Core 527 tests verify、japicmp、V1 无 key 精确断言、七模块消费者构建均通过 |

**总分：100 / 100。**

### 代码审查回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 | 复验命令 |
|---|---|---|---|---|---|
| 通过 | LFC06-REV-01 | 快速 MUST 审查未发现明确缺陷；factory 赋值、guard、V1 schema 与架构禁令均满足 | LFC-06 目标文件 | 无需修复 | 聚焦测试 + Core verify + API/消费者门禁 |
| 说明 | LFC06-REV-02 | Javadoc 启发式扫描确认新增 public enum/getter 合格；全 Core 报告含既有注释债，不在本卡扩散处理 | `MessageSeverity.java`、`Message.java` | 保持最小范围 | `check_javadoc_style.py --repo tfi-flow-core` |

### 最终交付回填

| 项目 | 回填内容 |
|---|---|
| mapping 实际结果 | `info=PROCESS/INFO`、`debug=METRIC/DEBUG`、`warn=ALERT/WARN`、两种 `error=ALERT/ERROR`、`withType/withLabel=INFO` |
| V1 diff 结论 | 生产 exporter 零修改；JSON/Map 均无 `severity` key，JSON type 仍为 enum name，Map type 仍为本地化 `toString()` |
| 验证证据 | Core verify：527 tests、Checkstyle 0、SpotBugs 0、JaCoCo 门禁通过；聚焦测试 Core 113 + `tfi-all` 93；japicmp 与七模块 package 均成功 |
| 回滚点 | 可独立回退 `MessageSeverity`、`Message.severity/getSeverity`、两个 TaskContext successful write 及对应测试；无需触碰 exporter |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。Java model 的 category/severity 已分维，TaskContext debug/warn 与 direct factory 语义一致；
V1 JSON/Map 未暴露 severity，既有 type 表示保持不变。

### 当前直接证据

- 跨 core/all focused reactor：Core 113 + all 93 tests，全部通过。
- `MessageTest` 参数化覆盖七个 public factory 的固定 category/severity 表；severity 为 final immutable field。
- JSON tests 精确断言 enum name 与无 `[DEBUG]/[WARN]` 前缀；Map tests 保留本地化 type；两者均断言无
  `severity` key。
- Console/JSON/Map production exporter 不读取 `MessageSeverity`，本卡未建立第二 schema/traversal owner。

## 六、完成审核

### 审核结论

**审核通过。** Debug/Warn factory 映射、入口等价与 exporter wire 边界回归通过。
