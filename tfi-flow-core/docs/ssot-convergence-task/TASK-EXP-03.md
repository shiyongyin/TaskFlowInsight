# TASK-EXP-03：将 Console 收敛为快照诊断文本

> **定位**：让 Console 每次导出只消费一份 `SessionExportSnapshot`，并保持其“供人阅读、非 schema”边界。
> **状态**：完成（2026-07-11；23 Console / 71 focused / 688 Core / API / 7/7；100/100）
> **审核状态**：审核通过（2026-07-11；3 项 SHOULD 已修复，0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-EXP-02`；后续 `TASK-EXP-04`、`TASK-EXP-05`、`TASK-EXP-07`
> **架构来源**：export-snapshot Task 4（`E1c`）经 ADR-008 修订后的 Console 边界

---

## 一、核心（设计时填）

### 背景

实施前 `ConsoleExporter` 直接递归读取 mutable `Session`/`TaskNode`/`Message`，并自行构建
`TaskDurationCache`。同一次输出可能混入多个时点的状态，Console 也因此成为第二个任务树遍历 owner。
ADR-008 已明确 4.0 只为 JSON/Map 定义 canonical V2；Console 仅是 snapshot-only 的人类可读诊断文本，
不存在 Console V1/V2 schema 或字节兼容承诺。历史 Console golden 只用于观察迁移漂移，不是本卡门禁。

### 目标（DoD）

- [x] `ConsoleExporter` 的现有 public methods/signatures 保持不变；不新增公开 options、schema 或版本入口。
- [x] 每条非 null public Session 路径恰好捕获一次；null 在 capturer 前返回且捕获次数为 0。
- [x] package-private capturer seam 可注入预构建快照；捕获后的 model mutation 不影响输出。
- [x] 所有 private formatter helper 只消费 `SessionExportSnapshot`、`TaskSnapshot`、`MessageSnapshot`
  或纯值 frame，不读取 mutable `Session`、`TaskNode`、`Message`、`TaskDurationCache`。
- [x] tree/simple 两种诊断文本使用显式栈迭代渲染，保留任务、消息、状态、耗时与现有 boolean 可观察行为。
- [x] capture/limit/lock/render failure 在完整字符串形成前原样传播；`System.out`/`PrintStream` 不产生部分字节。
- [x] Console 不投影 canonical V2 Map/JSON 字段树，不执行 attribute/payload 回调，不修改 Provider/facade fallback。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 单次线性化捕获 | 高 | 每个非空入口只能看到一个冻结时点 |
| mutable traversal 退役 | 高 | Console 不再拥有模型遍历与耗时计算 |
| 原子失败 | 高 | 捕获或渲染失败时不得先写输出 |
| 文本延续性 | 中 | 尽量保留现有诊断形态，但不建立 schema/byte contract |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| Console 合约 | snapshot-only human-readable diagnostic | 符合 ADR-008，避免复制机器 schema | Console V1/V2 schema |
| 捕获 seam | package-private `Function<Session, SessionExportSnapshot>` | 可精确验证 null、单次捕获与冻结输入 | static mock、公开 capture hook |
| 遍历方式 | 显式 LIFO frame，按原顺序压栈 | 深树不依赖 Java 调用栈 | snapshot 上继续递归 |
| 输出时机 | 先构建完整 String，再调用 stream | capture/render failure 零部分输出 | 边遍历边 print |
| 历史 golden | 非阻塞 characterization | 提示意外漂移但不承诺 4.0 字节 | 作为发布兼容门禁 |

### 不可改变的契约

- public 签名、null String 结果和 `DefaultExportProvider` 的 tree/simple 映射保持现状。
- `export(Session, boolean)` 的 boolean 继续不影响 tree 文本；`exportSimple` 的 boolean 继续控制消息时间戳。
- 本卡不新增 style/timestamp API；该能力仍由 `TASK-EXP-07` 拥有。
- snapshot 值域、预算、截断 marker 与锁语义由 `TASK-EXP-02` 拥有；Console 只读冻结字段。
- canonical V2 Map/JSON projection 仍由 `TASK-EXP-04..06` 拥有。

## 二、执行（设计时填）

### 文件与方法

| 动作 | 精确路径 | 类型/方法 |
|------|----------|-----------|
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/exporter/text/ConsoleExporter.java` | public 委托、两个 package-private capturer seams、snapshot-only iterative renderers |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/text/ConsoleExporterTest.java` | capture count/null/frozen input/failure/no-output/deep-tree 测试 |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/architecture/TaskTreeGateArchitectureTests.java` | Console 无 mutable import/helper/递归回归门禁 |
| 修改 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/ExportV1GoldenTests.java` | 把 V1 golden 定位改为历史 characterization，不增加 4.0 gate |

### 核心步骤

1. 先增加失败测试，覆盖非空捕获一次、null 捕获零次、预构建 snapshot 隔离后续 mutation、
   捕获异常原样传播、print 零字节、深树不递归溢出。
2. 固定 package-private seams；二者先做 null guard，再调用 capturer 恰好一次：

```java
String export(Session session, boolean showTimestamp,
        Function<Session, SessionExportSnapshot> capturer)
String exportSimple(Session session, boolean showTimestamp,
        Function<Session, SessionExportSnapshot> capturer)
```

3. public `export*` 使用 `SessionExportSnapshot::capture`；`print*` 先得到完整 String，再执行一次输出。
   `print(session, null)` 对非空 Session 仍完成一次捕获/渲染后丢弃文本，使所有非空 public path 语义一致。
4. tree renderer 用 `TreeFrame(task, prefix, last)` 迭代；simple renderer 用 `SimpleFrame` 迭代。
   children 逆序入栈以保持原顺序；`childrenTruncated` 作为最后一个可见诊断行，且参与 sibling lastness。
5. header/task/message 只读 snapshot accessor。Console 不读取 attributes/tags，不调用用户对象方法，
   不构造 `TaskDurationCache`，也不把 `severity` 或 `schemaVersion` 当作机器字段输出。

### 测试与验收命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=ConsoleExporterTest,TaskTreeGateArchitectureTests test
./mvnw -pl tfi-flow-core \
  -Dtest=SessionExportSnapshotTests,SessionSnapshotCapturerTests,ConsoleExporterTest,ExportV1GoldenTests,TaskTreeGateArchitectureTests test
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：六个 public Session 入口的非空捕获为 1；null String 入口捕获为 0。
- [x] CP-2：capturer 返回后修改原 Session，tree/simple 输出仍只含冻结值。
- [x] CP-3：capture failure identity 保持不变，String 未返回，两个 print sink 均为 0 bytes。
- [x] CP-4：深度达到 framework 上限的 snapshot 由迭代 renderer 完成，无递归调用链。
- [x] CP-5：生产 Console source 无 `TaskNode`、`Message`、`TaskDurationCache` import/read。
- [x] CP-6：无 `schemaVersion`/V1/V2 命名承诺；历史 golden 只记 characterization 结果。

### 回滚边界

失败时整体回退本卡的 Console 与测试改动，保留 `TASK-EXP-01/02` 的 gate/snapshot API。禁止保留
“部分 public method 使用 snapshot、部分仍遍历 mutable model”的混合状态；禁止以隐藏 V1 adapter 绕开失败。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只迁移 Console 输入所有权，不提前实现 Map/JSON V2 或 EXP-07 options。
- [x] **认知负担**：两个 capturer seams 加两个小型 frame，不引入 formatter 抽象层。
- [x] **比例失调**：测试集中在 capture-once、snapshot isolation、迭代与原子输出。
- [x] **ROI**：一次移除 Console 的 mutable traversal 与递归栈风险，为 EXP-04..08 建立同一输入边界。
- [x] **洁癖检测**：保留现有诊断形态；不为无 schema 文本做字段模型重排。
- [x] **局部 vs 全局**：冻结、预算、锁继续由 snapshot owner 负责，Console 只投影。
- [x] **过度设计**：没有公开 capturer、V1 adapter、formatter SPI 或第二棵 projection。

**结论**：设计与 ADR-008 一致；用户已批准按任务包顺序直接实施，本卡进入测试先行阶段。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 任务卡合约 | Console V1 字节兼容 | snapshot-only、非 schema 诊断文本 | ADR-008 已接受 V2-only，旧卡在实施前必须改写 |
| RED seam 调用 | 直接调用尚不存在的方法 | 先以反射得到行为 RED，GREEN 后改为同包直接调用 | 避免把编译错误误当作有效 RED |
| 历史 golden | 可选说明性更新 | 更新总说明且文本 characterization 仍通过 | 防止测试注释重新承诺 4.0 字节兼容 |

### 检查点结果

- [x] CP-1：AST 调用图证明六个 public 入口各委托一次；tree/simple seam counter 分别为 1，null 为 0。
- [x] CP-2：tree/simple 均验证 capture 后 mutation 不进入输出。
- [x] CP-3：sentinel failure 保持异常 identity；真实 `limit + 1` 对 `System.out`、指定/null stream 均为 0 bytes。
- [x] CP-4：1000 层边界测试通过；AST 门禁证明所有 private helper 无自递归。
- [x] CP-5：AST import/invocation 门禁和 `rg` source-policy 扫描均无 forbidden 命中。
- [x] CP-6：生产 Console 无 schema/V1/V2 token；历史 golden 只标记为 characterization。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | 23 个 Console tests 覆盖 null、capture once、冻结输入、boolean、深度与失败原子性 |
| 完整性 | 25/25 | DoD 7/7、CP 6/6；71 focused、688 Core、API profile 与 7/7 consumer package 通过 |
| 可维护性 | 25/25 | 两个小型 package-private seams、显式 LIFO frame；无新 public surface 或 formatter abstraction |
| 风险控制 | 25/25 | mutable traversal/递归/Cache 均由 AST gate 禁止；历史 golden 明确降级且仍通过 characterization |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| SHOULD | EXP03-R1 | seam 注释错误强调测试用途，未表达生产捕获边界 | `ConsoleExporter.java:78` | 已改为包内一次捕获与 public hook 边界说明 |
| SHOULD | EXP03-R2 | limit failure 测试复制 `10_000_000` 常量 | `ConsoleExporterTest.java:343` | 已改用 `MAX_EXPORT_TEXT_CHARS` |
| SHOULD | EXP03-R3 | 缺少 framework 最大深度的实际 renderer 证据 | `ConsoleExporterTest.java:375` | 已增加 1000 层 tree/simple 边界测试 |

## 六、完成审核

**审核结论：通过。** 3 项 SHOULD 全部闭环，0 unresolved MUST / SHOULD。`ConsoleExporter` 已成为
单次捕获、snapshot-only、迭代式且失败前零输出的非 schema 诊断投影；公开签名与 Provider 映射未改变。

### Fresh 证据（2026-07-11）

- Console 专项 23/23；snapshot/Console/golden/architecture focused 71/71。
- Core clean verify 688/688；Checkstyle 0、SpotBugs 0/0、JaCoCo gate 通过。
- Core `api-compat` profile 通过；`ConsoleExporter` 公开方法集合未变化。
- 六个消费者模块加 repository parent 的 reactor package 7/7 通过，固定包含 `tfi-examples`。
- `ExportV1GoldenTests` 仅保留历史 characterization 定位；本轮 Console 文本未发生意外漂移。
