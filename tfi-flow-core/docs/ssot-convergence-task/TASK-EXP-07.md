# TASK-EXP-07：让 Console Style 与 Timestamp 各自只有一种语义

> **定位**：保留现有 public symbol，但纠正 boolean 名称与行为冲突；style 只归 options，`showTimestamp` 只归时间戳。
> **状态**：完成（2026-07-11；50 focused / 708 Core / API / 43 owner Console / 7/7）
> **审核状态**：审核通过（0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-EXP-03`；与已完成 `TASK-EXP-06` 汇合后进入 `TASK-EXP-08`
> **架构来源**：ADR-008 非 schema Console 决策与[当前架构 SSOT](../design-doc.md)的 Export 边界。

---

## 一、核心（设计已确认）

### 背景

EXP-03 已让 Console 成为单次捕获、snapshot-only、迭代且失败前零输出的非 schema 诊断投影，但 style 与
timestamp 仍由多组 boolean 入口交叉表达。`ExportProvider.exportToConsole(boolean showTimestamp)` 的 Javadoc
声明参数控制时间戳，`DefaultExportProvider` 却用 true/false 选择 TREE/SIMPLE，而且两条路径都没有时间戳。
本卡消除该冲突，不以已被 ADR-008 取消的 Console 字节兼容为理由永久保留错误语义。

### 范围与非目标

**范围内：**

- 新增 `ConsoleExportOptions(ConsoleStyle style, boolean showTimestamp)` 和 nested `ConsoleStyle`。
- 新增 `ConsoleExporter.export(Session, ConsoleExportOptions)`。
- 新增 `ConsoleExporter.print(Session, PrintStream, ConsoleExportOptions)`。
- 让所有现有 boolean 的名称、Javadoc、实现和测试严格一致。
- 让 `TfiFlow` 与 `TFI` 的 routed/legacy Console 路径保持同一 TREE + timestamp 语义。
- 把两个 EXP-03 package-private capturer seams 收敛为一个 options capturer seam。
- 固化四向矩阵、null 顺序、capture-once、无部分输出和 public surface。

**范围外：**

- 不删除、重命名或 deprecate 现有 public symbol，不修改 breaking manifest/japicmp exclusion。
- 不向 `ExportProvider`、`TfiFlow`、`TFI` 增加 options overload；SPI/facade 继续导出 TREE。
- 不定义 Console V1/V2、schema、字段顺序或 byte compatibility。
- 不修改 snapshot、Map/JSON、`TaskDurationCache`、change-tracking 或 facade 的 Session 解析/失败降级规则。
- 不增加 builder、registry、formatter SPI、可变 exporter 字段或第二 renderer。

### 唯一入口矩阵

| 入口 | Style | Timestamp |
|------|-------|-----------|
| `export(session)` | TREE | false |
| `export(session, showTimestamp)` | TREE | `showTimestamp` |
| `exportSimple(session, showTimestamp)` | SIMPLE | `showTimestamp` |
| `export(session, options)` | `options.style()` | `options.showTimestamp()` |
| `print(session)` | TREE | false |
| `printSimple(session)` | SIMPLE | false |
| `print(session, out)` | TREE | false |
| `print(session, out, options)` | `options.style()` | `options.showTimestamp()` |
| `ExportProvider.exportToConsole()` | TREE | false |
| `DefaultExportProvider.exportToConsole(showTimestamp)` | TREE | `showTimestamp` |
| `TfiFlow.exportToConsole()` | TREE | false |
| `TfiFlow.exportToConsole(showTimestamp)` | TREE | `showTimestamp` |
| `TFI.exportToConsole()` | TREE | false |
| `TFI.exportToConsole(showTimestamp)` | TREE | `showTimestamp`，与 routing flag 无关 |

TREE/SIMPLE 的 timestamp token 位置相同：消息 display label 后、右方括号前，格式为
` @<ISO-8601 instant>`。false 时不输出该 token。

### 目标（Definition of Done）

- [x] 新 record 为不可变 public value；null style 抛 `NullPointerException("style")`。
- [x] 两个 options overload 是未排除的 public additions，现有 public symbol 集合一个不少。
- [x] 四向 matrix 全部通过；改变 style 不改变 timestamp presence，改变 timestamp 不改变 style tokens。
- [x] 现有入口与上表逐项一致，不再存在 boolean 同时/暗中选择 style 的路径。
- [x] options 先于 Session 校验；null options 抛 `NullPointerException("options")` 且 capture/write 均为 0。
- [x] 有效 options + null Session 返回 empty，capture 0；非 null Session capture 恰好 1。
- [x] null PrintStream 对非 null Session 仍 capture/render 1 次再丢弃；capture/render failure 零部分输出。
- [x] 一个 package-private capturer seam 分派两个既有 iterative renderer；无 mutable traversal/递归/Cache/schema。
- [x] `DefaultExportProvider` 无 Session 返回 false；有 Session 时 false= TREE/no timestamp，true= TREE/timestamp。
- [x] 历史 golden 只观察默认 TREE/no timestamp，不再断言 boolean true/false 相等或 Provider 选择 SIMPLE。
- [x] focused、Core clean verify、API compatibility、owner-scoped tfi-all Console tests 和七项目 reactor 全部通过。
- [x] 更新架构 README、`INDEX.md` 与 downstream 任务卡中的 Console V1 漂移表述。

### 关键决策

| 决策点 | 选择 | 理由 | 明确拒绝 |
|--------|------|------|----------|
| boolean 语义 | 只控制 timestamp | 名称、Javadoc、行为同义 | 用 boolean 选择 style |
| style 语义 | 只由 `ConsoleStyle` 控制 | 两维正交、组合完整 | 再增 boolean 或隐式推断 |
| 旧 public API | 保留并做显式 adapter | 本地 3.0.0 基线含这些 symbol | 未登记删除 |
| Console contract | snapshot-only human diagnostic | ADR-008 明确边界 | Console V1/V2/schema/byte contract |
| capture owner | 一个 options seam | 单一入口、少重复 | tree/simple 各自捕获 |
| Provider | 固定 TREE，boolean 控制 timestamp | 与 SPI 签名/Javadoc 一致 | 保留 true=tree/false=simple |

### 精确失败语义

| 场景 | 结果 | Capture | Output |
|------|------|--------:|-------:|
| `new ConsoleExportOptions(null, x)` | NPE `style` | 0 | 0 |
| options overload 的 options 为 null | NPE `options`，即使 Session 也为 null | 0 | 0 |
| 有效 options、Session 为 null | empty/no-op | 0 | 0 |
| 非 null Session、capturer 为 null | NPE `capturer` | 0 | 0 |
| capturer 返回 null | NPE `capturer returned null snapshot` | 1 | 0 |
| capture/render failure | 原异常传播 | 1 | 0 |
| 非 null Session、PrintStream 为 null | 完成 render 后丢弃 | 1 | 0 |
| Provider 无 Session | false | 0 | 0 |

## 二、执行计划

### 文件与所有权

| 动作 | 精确路径 | 责任 |
|------|----------|------|
| 新增 | `src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExportOptions.java` | 两维不可变 options |
| 修改 | `src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java` | public adapters、唯一 capturer seam、两个既有 renderer |
| 修改 | `src/main/java/com/syy/taskflowinsight/spi/DefaultExportProvider.java` | boolean 严格映射 TREE + timestamp |
| 修改 | `src/main/java/com/syy/taskflowinsight/spi/ExportProvider.java` | Console Javadoc 与实际行为对齐 |
| 修改 | `src/main/java/com/syy/taskflowinsight/api/TfiFlow.java` | facade Javadoc 与 SPI 的 TREE 语义对齐 |
| 修改 | `tfi-all/src/main/java/com/syy/taskflowinsight/api/TFI.java` | legacy route 固定 TREE，routing 不再改变 style |
| 新增测试 | `src/test/java/com/syy/taskflowinsight/exporter/text/ConsoleExporterOptionsTests.java` | options matrix/null/capture/failure |
| 新增测试 | `src/test/java/com/syy/taskflowinsight/spi/DefaultExportProviderExportTests.java` | 真实 Provider route 与 boolean 语义 |
| 修改测试 | `src/test/java/com/syy/taskflowinsight/exporter/text/ConsoleExporterTest.java` | EXP-03 seam 和旧入口回归迁移 |
| 修改测试 | `src/test/java/com/syy/taskflowinsight/exporter/ExportV1GoldenTests.java` | 删除失效的 true=false/Provider style 断言 |
| 修改测试 | `src/test/java/com/syy/taskflowinsight/architecture/TaskTreeGateArchitectureTests.java` | 唯一 seam、公开表面、无第二 traversal |
| 修改测试 | `tfi-all/src/test/java/com/syy/taskflowinsight/api/TFIPhase2RoutingTest.java` | legacy route TREE/timestamp 回归 |
| 修改文档 | `docs/product/architecture/README.md`、本卡、`INDEX.md` | 当前架构、状态与 fresh evidence |
| 修正文档 | `TASK-EXP-09.md`、`TASK-EXP-10.md`、`TASK-DOC-01.md` | 删除 downstream Console V1 漂移 |

### 精确 API

```java
public record ConsoleExportOptions(
        ConsoleStyle style,
        boolean showTimestamp) {
    public ConsoleExportOptions {
        Objects.requireNonNull(style, "style");
    }

    public enum ConsoleStyle {
        TREE,
        SIMPLE
    }
}

public String ConsoleExporter.export(
        Session session,
        ConsoleExportOptions options)

public void ConsoleExporter.print(
        Session session,
        PrintStream out,
        ConsoleExportOptions options)

String ConsoleExporter.export(
        Session session,
        ConsoleExportOptions options,
        Function<Session, SessionExportSnapshot> capturer)
```

不新增其他 overload、factory、constant、builder 或 public helper。

### TDD 批次

1. **RED-A（已有 API）**：断言 `export(session, true)` 的 TREE 消息包含 timestamp、false 不含；断言
   Provider true/false 均为 TREE，只有 true 含 timestamp。确认当前实现以内容差异失败。
2. **RED-B（新 API）**：用反射调用预期 options type/overload，确认因 type/method missing 形成断言失败，
   不能以 test compilation error 作为 RED。
3. **GREEN-1**：新增 record 和单一 options capturer seam；public old/new adapters 全部按矩阵委托。
4. **GREEN-2**：TREE message renderer 接受 `showTimestamp`，与 SIMPLE 使用相同 token 位置/formatter；
   `DefaultExportProvider` 改为 TREE + boolean timestamp。
5. **REFACTOR**：反射测试改为强类型；移除两个旧 capturer seams，更新 EXP-03 tests/architecture gate。
6. **REGRESSION**：历史 default Console characterization、API、Core、tfi-all Console 与 7/7 reactor。
7. **REVIEW RED-C/GREEN-C**：审查发现 `TFI` legacy route 仍用 `showTimestamp` 选择 TREE/SIMPLE；先以真实
   routing-disabled facade 测试形成断言 RED，再让 legacy 只负责 Session 解析并复用 TREE options。

### 审核检查点

- [x] CP-1：ConsoleExporter/SPI 入口矩阵 10/10 有直接断言；facade routed/legacy 映射另有直接回归。
- [x] CP-2：TREE/SIMPLE timestamp token 位置和 ISO formatter 相同。
- [x] CP-3：一个 prebuilt snapshot、一个 counting capturer；非空 1、null 0、mutation 不泄漏。
- [x] CP-4：options/style null 的异常类型、消息、优先级和零输出精确。
- [x] CP-5：null sink 仍 capture 1；capture/render failure 所有 sink 0 bytes。
- [x] CP-6：Provider 测试走 Registry -> FlowProvider.currentSession() -> DefaultExportProvider，无 reflection。
- [x] CP-7：Console public 方法/构造器、options record/enum 集合精确；无额外 overload。
- [x] CP-8：生产 Console 无 mutable model、Cache、schema/V1/V2 token，无递归/第二 traversal。
- [x] CP-9：manifest、japicmp exclusions、ADR token、计划 SHA 与历史 golden resource 未改。

### 验收命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ConsoleExporterOptionsTests,DefaultExportProviderExportTests test
./mvnw -pl tfi-flow-core \
  -Dtest=ConsoleExporterOptionsTests,DefaultExportProviderExportTests,ConsoleExporterTest,ExportV1GoldenTests,TaskTreeGateArchitectureTests test
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-all -am \
  -Dtest=ConsoleExporterTest,ConsoleExporterTests,ConsoleExporterAdditionalCoverageTest,ConsoleExporterCustomLabelTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

诊断债务命令单独运行，不属于 EXP-07 completion gate：

```bash
./mvnw -pl tfi-all -am -Dtest=ExportVerificationIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

其中已知的 change-tracking 内容失败必须按精确测试名和实际结果报告，不得伪装成 EXP-07 style/timestamp 失败，
也不得为使其变绿而扩大本卡范围。

### 回滚边界

失败时删除 `ConsoleExportOptions` 与两个 overload，恢复本卡对 boolean adapter、Provider、测试和文档的修改；
保留 EXP-03 snapshot-only renderer、EXP-06 Map/JSON 门禁、manifest、ADR、计划和历史资源。不得留下部分入口走
options、部分入口自行选择 renderer 的混合状态。

## 三、自省（实施前）

- [x] **无歧义**：style 与 timestamp 分属不同类型/字段，入口矩阵逐项唯一。
- [x] **目标偏离**：只修 Console 选择语义，不修改机器 schema、snapshot 或 change-tracking。
- [x] **认知负担**：一个 record、一个 enum、一个 seam；没有 builder/strategy/pipeline。
- [x] **兼容边界**：保留全部 public symbol，只纠正 ADR-008 已允许变化的非 schema 文本行为。
- [x] **局部与全局**：Provider 只适配，不拥有 renderer；EXP-03 的 snapshot owner 不变。
- [x] **失败边界**：validation/capture/render/write 顺序精确，null sink 不成为隐藏 skip 开关。
- [x] **架构防卫**：无共享可变 Context、无新抽象接口、无 catch-all 业务映射、无基础设施复制。
- [x] **可回滚**：新 API 与 adapter 可整体回退，不影响 Map/JSON/ADR/manifest。

**结论**：设计已按用户确认实施；Console style/timestamp 与 facade routing 维度已互相独立。

## 四、实施反馈

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| facade fallback | 不改变 legacy fallback | 只改 `TFI` legacy Console 的 style adapter，不改 Session 解析或失败降级 | 审查搜索发现原分支仍用 `showTimestamp` 选择 TREE/SIMPLE，直接违反本卡无歧义目标 |
| 测试补强 | 按原 47 focused 收口 | 新增 print/no-arg Provider/原子输出 3 条测试，最终 50 focused | CP-1/CP-5 必须直接覆盖新增 public adapters |

### TDD 证据

- RED-A：3 tests / 2 assertion failures；TREE true 缺消息时间戳，Provider false 错选 SIMPLE。GREEN-A：26/26。
- RED-B：1 assertion failure，根因为 `ClassNotFoundException: ConsoleExportOptions`，不是编译失败。GREEN-B：33/33。
- REVIEW RED-C：首次测试因缺少 `MessageType` import 形成编译错误，未计为 RED；修正测试后 1/1 因 legacy false
  缺 TREE 根 token 断言失败。GREEN-C：1/1；完整 `TFIPhase2RoutingTest` 为 59 run / 58 pass / 1 skipped。

### 检查点结果

- [x] CP-1：options 四向矩阵与四个 print adapter 位于 `ConsoleExporterOptionsTests:22/46`；Provider no-arg/boolean
  位于 `DefaultExportProviderExportTests:29/49`；legacy facade 回归位于 `TFIPhase2RoutingTest:174`。
- [x] CP-2：TREE/SIMPLE 分别在 `ConsoleExporter:194/247` 调用唯一 `appendTimestamp`（`:252`）。
- [x] CP-3：唯一 capturer seam 位于 `ConsoleExporter:97`；null/capture-once/frozen mutation 测试通过。
- [x] CP-4：`ConsoleExporterOptionsTests:86/93` 锁定 `style`/`options` NPE 消息、顺序和 capture 0。
- [x] CP-5：`ConsoleExporterOptionsTests:161/173` 锁定 null sink 仍 capture，以及 options sink 失败 0 bytes。
- [x] CP-6：`DefaultExportProviderExportTests:29` 经过 Registry 与 `FlowProvider.currentSession()`，无 reflection。
- [x] CP-7：`TaskTreeGateArchitectureTests:229` 锁定 8 个 public 方法、无参构造器、record 两字段和 enum 两值。
- [x] CP-8：同一 AST gate 锁定无 mutable model、递归和第二 seam；生产源码 forbidden-token scan 无匹配。
- [x] CP-9：API profile 无旧 symbol 删除或 wildcard exclusion；历史 golden 未改；五份受保护计划 SHA 与 `INDEX.md` 指纹逐项一致。

### Fresh 验证

| 门禁 | 结果 |
|------|------|
| Core focused | 50/50，0 failure/error/skipped |
| Core `clean verify` | 708/708；Checkstyle 0；SpotBugs 0/0；JaCoCo passed |
| API compatibility | passed；旧 Console symbol 无删除 |
| tfi-all owner Console | 43/43 |
| TFI routed/legacy regression | 59 run / 58 pass / 1 existing skipped |
| 七项目 package reactor | 7/7 SUCCESS，包含 `tfi-examples` |
| `ExportVerificationIT` 诊断 | 5 run / 3 pass / 2 known change-tracking failures |
| 最终确定性检查 | SSOT lint 0 ERROR；计划 SHA 5/5；`javap` public surface 与两值 enum 精确匹配 |
| Javadoc / 空白 | targeted Javadoc 0 violations / 4 non-blocking brevity warnings；本卡 scoped trailing whitespace 0 |

诊断失败仍精确为 `testConsoleExportContainsChangeMessages` 与 `testMultipleTasksWithChanges`；输出已变为 TREE，
失败仍由变更值内容断言导致，不是 style/timestamp 回归。

## 六、完成审核

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | style/timestamp 四向矩阵、10 个核心/SPI adapters 与 routed/legacy facade 语义一致 |
| 完整性 | 25/25 | null/capture/write/public surface/API/downstream 门禁全部覆盖 |
| 可维护性 | 25/25 | 一个 immutable record、一个 enum、一个 capturer seam、两个既有 iterative renderer |
| 风险控制 | 25/25 | RED/GREEN、capture failure 0 bytes、API/Golden/SHA 防漂移、诊断债务隔离 |

**总分：100/100。**

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST | CR-EXP07-01 | TFI legacy route 仍用 `showTimestamp` 选择 TREE/SIMPLE | `TFI.java:1206` | TDD 修复为显式 TREE options；legacy regression 通过 |
| SHOULD | CR-EXP07-02 | 四个 print adapters、Provider no-arg 与 options failure 原子性缺少直接断言 | `ConsoleExporterOptionsTests:46/173` | 新增 3 条 focused tests |
| SHOULD | CR-EXP07-03 | `TfiFlow`/`TFI` Javadoc 仍声明默认 SIMPLE，Console 文件有尾随空白 | `TfiFlow.java:425`、`TFI.java:1173` | 文档统一为 TREE/timestamp；scoped whitespace scan 清零 |

审查结束时：0 unresolved MUST / SHOULD。架构/KISS 快检无新增大类、长方法、深分支、额外抽象或第二 traversal。
