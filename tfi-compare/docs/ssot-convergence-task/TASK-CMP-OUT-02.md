# TASK-CMP-OUT-02：统一formatter并删除非目标输出

> **定位**：让JSON/Map/Markdown/Console只格式化canonical projection，并精确删除CSV/XML/Streaming旧表面。
> **状态**：已完成
> **审核状态**：已完成
> **依赖**：`TASK-CMP-OUT-01`
> **后续**：`TASK-CMP-SPR-01`
> **架构来源**：总体设计§11.4、W5；ADR-014
> **消费不变量**：16-20

---

## 一、核心（设计时填）

### 背景

旧formatter各自遍历change/result、持有`ExportConfig`和mask规则，Streaming实现还会关闭调用方拥有的流。
本卡把目标表面收敛为JSON/Map/Markdown/Console，Render SPI改为typed projection + render options；CSV/XML/Streaming和
formatter私有`MaskRuleMatcher`按exact manifest删除，并同步迁移`TFI`、all tests与examples。

### 目标（DoD）

- [x] JSON/Map/Markdown/Console均只接收`CompareProjection`，不接收业务对象、raw result或mutable ExportConfig。
- [x] `RenderProvider`签名为typed projection + immutable`RenderOptions`，默认实现共享OUT-01 factory/formatters。
- [x] `RenderOptions`只控制Markdown/Console布局，不改变schema字段、masking或ValueSnapshot语义。
- [x] JSON String/Writer/OutputStream内容一致；成功只flush不close，I/O失败原样传播。
- [x] Markdown/Console escaping/redaction/value representation与machine projection一致，深度边界不递归读业务图。
- [x] CSV/XML/Streaming、pretty mode、mutable default和`MaskRuleMatcher`逐type/member exact删除并提供replacement。
- [x] all render facade、ServiceLoader contract、examples与所有跨格式golden同步迁移，W5全消费者为绿。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| formatter只读projection | 高 | 防止第二schema/mask owner |
| stream ownership与I/O | 高 | 调用方资源不能被库关闭 |
| exact removal/消费者 | 高 | 4.0破坏必须可审计 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 目标格式 | JSON/Map/Markdown/Console | machine + 人读最小闭集 | 继续维护七套规则 |
| Render SPI | Projection + RenderOptions | 类型和安全边界明确 | `Object result,Object style` |
| stream owner | caller owns close | 与常规library合同一致 | try-with-resources关闭外部流 |

## 二、执行（设计时填）

### 文件与接口

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 重写 | `tfi-compare/src/main/java/com/syy/taskflowinsight/exporter/change/ChangeJsonExporter.java`、`ChangeMapExporter.java` | canonical projection JSON/Map入口 |
| 重写 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/render/MarkdownRenderer.java`、`ChangeReportRenderer.java`、`exporter/change/ChangeConsoleExporter.java` | projection-only diagnostic formatters |
| 新增 | `tfi-compare/src/main/java/com/syy/taskflowinsight/tracking/render/RenderOptions.java` | immutable diagnostic layout options |
| 修改 | `tfi-compare/src/main/java/com/syy/taskflowinsight/spi/RenderProvider.java`、`DefaultRenderProvider.java`及ServiceLoader contract | typed render SPI |
| 删除 | `exporter/change/ChangeCsvExporter.java`、`ChangeXmlExporter.java`、`StreamingChangeExporter.java`、`tracking/render/MaskRuleMatcher.java` | 非目标格式与私有mask owner |
| 删除/收窄 | `exporter/change/ChangeExporter.java`及nested`ExportConfig`、旧format helpers | exact manifest或immutable compat mapper |
| 修改消费者 | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java`、`TfiProviderDelegate.java`及`exporter/change/**`、`tracking/render/**` tests | typed provider/projection调用 |
| 修改消费者 | `tfi-examples/src/main/java/**`、`src/test/java/**`中的render/export示例 | 目标四格式 |
| 新增测试 | `CompareFormatterContractTests.java`、`CompareStreamOwnershipTests.java`、`CompareOutputRemovalContractTests.java` | parity/escaping/flush/close/removal |

未写完整前缀的Compare生产路径均位于`tfi-compare/src/main/java/com/syy/taskflowinsight/`。

### 核心步骤

1. 先写同一projection跨四格式golden和String/Writer/OutputStream byte parity、flush/close/failure tests。
2. 改Render SPI和default provider；所有入口先构造或接收OUT-01 projection，再纯格式化。
3. 迁移Markdown/Console escaping/layout，删除私有mask规则与ValueRepr重新解释。
4. 删除CSV/XML/Streaming和mutable ExportConfig/pretty表面，逐member登记API/SCHEMA/BEHAVIOR replacement。
5. 迁移TFI/all/examples/ServiceLoader tests；删除旧白盒或改为public contract，不保留反射调用私有formatter。
6. 翻转C-05剩余stream/format部分，运行全格式golden与owner零引用搜索。

### 验证命令

```bash
./mvnw -pl tfi-compare -Dtest=CompareFormatterContractTests,CompareStreamOwnershipTests,CompareOutputRemovalContractTests,CompareMaskingGoldenTests test
./mvnw -pl tfi-compare,tfi-all,tfi-examples -am -Dtest=CompareFormatterContractTests,TfiRenderFacadeContractTests,CompareExamplesOutputContractTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples -am -DskipTests package
```

### 审核检查点

- [x] CP-1：formatter/SPI无raw result/business object输入和私有schema/mask owner。
- [x] CP-2：JSON三入口内容一致，只flush不close；I/O异常原样传播。
- [x] CP-3：CSV/XML/Streaming/MaskRuleMatcher零生产和消费者引用，manifest replacement完整。
- [x] CP-4：all/examples/ServiceLoader/golden迁移完成，W5全消费者为绿。

### 禁止范围与回滚

不在本卡迁Spring配置、metrics或Registry。回滚必须把projection与全部formatter/consumer按OUT-02 -> OUT-01逆序恢复；
禁止只恢复旧格式而继续使用不兼容的新mask/schema，也禁止formatter重新读取raw result。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只关闭formatter和输出兼容面。
- [x] **认知负担**：四个格式共享一个projection，RenderOptions只管布局。
- [x] **比例失调**：安全边界、I/O与消费者占主体。
- [x] **ROI**：删除三套维护成本高且漂移的格式owner。
- [x] **洁癖检测**：保留真实使用的JSON/Map/Markdown/Console，不追求统一成单一文本格式。
- [x] **局部 vs 全局**：为Spring/Ops提供稳定projection/result边界。
- [x] **过度设计**：不新增formatter pipeline或plugin registry。

**结论**：设计通过；完成后W5整体才可判绿。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 过期输出表面 | 删除CSV/XML/Streaming、mutable ExportConfig与私有mask owner | 同步删除遗漏的`CompareReportGenerator`、`ReportFormat`、`PatchFormat`、`CompareResult.prettyPrint()`及examples旧`withReport`文案 | accepted目标只有JSON/Map/Markdown/Console；按用户指令直接删除过期路径，避免保留含混/no-op兼容入口 |
| Console值表示 | 诊断格式与machine projection一致 | Review发现`Map.toString()`无法canonical escaping；改为逐change复用JSON writer，并更新golden | 换行会破坏一行一条边界，`{key=value}`也不是machine JSON，必须由唯一writer负责值表示 |
| examples脱敏夹具 | 以敏感Map key验证输出 | 改用两个合法Luhn值验证内容检测 | typed grammar不把raw `MAP_KEY`冒充`PROPERTY:password`；测试应服从已冻结grammar，不能为夹具修改生产语义 |
| Reactor命令边界 | 单模块定向测试 | 所有验收统一使用`-am` | 单模块会加载本地仓库中的陈旧上游Flow artifact，不是当前工作树的有效消费者环境 |
| manifest扫描耗时 | 每个断言重复全仓扫描 | 共享一次inventory，约77.2秒降至约0.95秒 | 确定性合同不应重复执行相同I/O，否则会被误判为挂起 |
| japicmp与执行反馈 | 直接等待兼容命令输出 | 原始输出约3.8万行并最终因前序Wave历史删除失败；后续大输出落报告，只回传退出码和本卡定向查询，10秒轮询、60秒检查进程/Surefire/JVM | 大量输出阻塞了会话反馈；长命令必须区分“仍在推进”和“真实卡住”，不能让用户无状态等待 |
| 当前任务负例 | 固定替换OUT-02 headline制造不一致 | 切换SPR-01后替换不再命中；改为动态解析当前headline并替换成另一已知任务 | 规划合同必须随任务推进仍能制造真实负例，不能把某张任务卡编码成测试默认值 |

### 检查点结果

- [x] CP-1：四个formatter与typed SPI只接收`CompareProjection`/`RenderOptions`；生产源码无raw result、业务对象或私有mask owner入口。
- [x] CP-2：JSON String/Writer/OutputStream parity、flush/not-close及原始I/O异常合同通过；Console特殊字符与machine change JSON exact相等。
- [x] CP-3：旧type/member在生产、消费者和最终JAR中零引用；API 0357-0360、RESOURCE/SCHEMA 0003及POM exact exclusion双向合同通过。
- [x] CP-4：all/examples/ServiceLoader/golden完成迁移；定向消费者、Compare全量测试和七模块package全部通过。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | projection-only、JSON三入口、stream ownership、masking与Console escaping合同全部通过 |
| 完整性 | 25 /25 | 四个目标格式、typed SPI、facade、ServiceLoader、examples、golden与过期表面删除均已闭合 |
| 可维护性 | 24 /25 | schema/mask/value escaping均复用canonical owner；实现类、枚举值和基础字段注释完整，无可执行FQCN |
| 风险控制 | 24 /25 | 全量测试与package为绿；repo级japicmp仍受前序Wave未登记历史删除阻断，但本卡符号已被exact排除 |

### Code-Review回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| HIGH/MUST | OUT02-R01 | Console使用`Map.toString()`，控制字符可破坏行边界，且change不是canonical JSON表示 | `ChangeConsoleExporter.java:40` | 新增公开特殊字符合同，逐change复用`CanonicalProjectionJsonWriter`；RED复现后GREEN，golden同步 |
| P2/SHOULD | OUT02-R02 | Console类注释仍声称“只迭代Map tree”，与修复后的Map头部+JSON change实现漂移 | `ChangeConsoleExporter.java:11` | 修正为职责与复用原因，不复述代码步骤 |

### 验证证据（2026-07-14）

| 命令/门禁 | 结果 |
|---|---|
| formatter/stream/removal/masking focused命令 | 退出0；24/24，0 failure/error |
| Console RED-GREEN与output characterization | RED稳定复现非JSON输出；修复后单测1/1、golden 2/2 |
| Compare/all/examples consumer命令 | 退出0；formatter 3/3、all facade 3/3、examples 1/1，七模块reactor成功 |
| `./mvnw -q -pl tfi-compare -am test` | 退出0；Core 727、Flow starter 122、Compare 2073，共2922项且0 failure/error |
| 七模块`-DskipTests package` | 退出0；全部模块构建成功 |
| manifest/POM双向合同与removal合同 | 退出0；15/15 + 3/3；生产源码、消费者、最终JAR和`CompareResult`均无过期符号 |
| formatter/golden/规划状态联合回归 | 首次暴露硬编码负例1项失败；修复后退出0，11/11 |
| Javadoc/FQCN/架构快检 | 本卡16个输出/SPI文件0 blocking violation；枚举值/基础字段逐项注释；无可执行FQCN、大类、长方法或深嵌套 |
| japicmp定向取证 | 命令约25秒后因前序Wave历史删除失败；3596行落盘报告中本卡output/report/patch符号为0，证明本卡exact exclusions生效 |

审查结论：2项finding全部关闭，无P0/P1/MUST遗留；7项DoD与CP-1至CP-4全部通过。按依赖顺序直接激活`CMP-SPR-01`。
