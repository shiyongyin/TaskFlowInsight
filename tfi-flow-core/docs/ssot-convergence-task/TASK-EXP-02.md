# TASK-EXP-02：建立深度不可变 Session 快照与线性化时钟

> **定位**：在 `TASK-EXP-01` 的 write-lock 线性化点，一次捕获有界、深度不可变且可公开支持的语义快照。
> **状态**：完成（2026-07-11；33 snapshot / 57 focused / 678 Core / API / 7/7；100/100）
> **审核状态**：审核通过（2026-07-11；2 项 Important 已修复，0 unresolved MUST / SHOULD）
> **依赖**：前置 `TASK-EXP-01`、`TASK-GRD-05`、`TASK-LFC-06`（Lifecycle `L6` / `MessageSeverity`）、已接受 `G3`；后续 `TASK-EXP-03`
> **架构来源**：export-snapshot Task 3（`E1b`）；ADR-008；master G3/export constraints

---

## 一、核心（设计时填）

### 背景

formatter 若直接读取 mutable `Session`/`TaskNode`，统计、时长与树内容可能来自不同时间点。只做浅 copy 或在锁内调用任意 `toString()`/container iterator，又会把用户代码带入全树 write lock。本卡在唯一 capture callback 内各采样一次 wall/monotonic clock，以精确 immutable scalar whitelist、tagged marker 和四维预算构建 `SessionExportSnapshot`，任何 entry/text 超限都在顶层快照或输出出现前原子失败。

### 目标（DoD）

- [x] 新增受支持 public `SessionExportSnapshot` 及 `Limits`、`Statistics`、`TaskSnapshot`、`MessageSnapshot`、`NumberKind`、`NonFiniteNumber`、`UnsupportedValue`。
- [x] 新增 package-private `SessionSnapshotCapturer` 与 `CaptureClock` test seam；全部 mutable model 读取、DFS、clock sampling 与 value freezing 只在该类。
- [x] `captureMillis`、`captureNanos` 都在 write lock 获得后各调用一次；capture 不请求 Session monitor。
- [x] `maxDepth/maxNodes/maxPayloadEntries/maxTextChars` 同时约束 captured 与手工构造的顶层快照。
- [x] depth/node 只截断 children；payload/text 超限整次失败，无 partial snapshot、projection 或 output。
- [x] G3 admitted exact scalar 的值与 canonical V2 类型语义保持；非有限数与 unsupported value 使用 immutable markers；捕获期间无用户回调。
- [x] nested compact constructors 与 top-level graph validator 完整拒绝局部 tuple、时间、DAG、duplicate ID、path、statistics、duration 与 terminal graph 矛盾。
- [x] L6 `MessageSeverity` 在快照中冻结；后续唯一 canonical V2 projection 输出 enum name。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 不可变/无回调捕获 | 高 | write lock 内只能执行有限、framework-owned 读取 |
| 图与时间不变量 | 高 | supported public constructor 不得产生矛盾实例 |
| 预算/原子失败 | 高 | 捕获工作和可投影输入必须有界 |
| V2 值域语义 | 高 | bounded legal domain 内保留值与 canonical 类型，特殊值统一 tagged |
| API 形状 | 中 | public records 支持，capturer/projection machinery 隐藏 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 公开边界 | immutable public records + package-private capturer | 同时支持预构建快照与隐藏 mutable traversal | 公开 traversal/schema helper |
| clock 位置 | `session.captureExport(() -> ...)` 内各采样一次 | 捕获时间属于真实线性化点 | lock 前采样或节点间重复采样 |
| 值冻结 | exact runtime class whitelist | 不触发 subclass/user callback | `instanceof Number` 或 generic `String.valueOf` |
| 深度/节点耗尽 | 仅 child traversal truncation | 保留已纳入的完整节点 | 静默丢 message/attribute/tag |
| payload/text 耗尽 | stable exception，整体失败 | 避免部分语义输出 | 截断 payload 或 formatter 自行决定 |
| V2 schema | 本卡不构建 Map | snapshot 是语义 API，schema 由后续唯一 projection 拥有 | 在 record 内混入 Map builder/DFS |

### 有界合法域与无回调规则

- 默认预算：root depth `0`，included root 消耗一个 node；`MAX_EXPORT_DEPTH` 沿用现值，新增 `MAX_EXPORT_NODES=100_000`、`MAX_EXPORT_PAYLOAD_ENTRIES=1_000_000`、`MAX_EXPORT_TEXT_CHARS=10_000_000L`。
- admitted exact classes：`String`、`Boolean`、`Character`、`Byte`、`Short`、`Integer`、`Long`、finite `Float`、finite `Double`、`BigInteger`、`BigDecimal`。
- exact non-finite `Float`/`Double` 转 `NonFiniteNumber`；subclass、enum、array、container、arbitrary `Number` 和其他对象转只含 `getClass().getName()` 的 `UnsupportedValue`。
- write lock 内禁止 `toString()`、`String.valueOf()`、`iterator()`、用户 `hashCode/equals`、reflection accessor 或任何回调。
- aggregate text 以 UTF-16 `String.length()` 单位估算；wrapper 用固定 worst-case width；exact big number 用 bitLength/precision/scale/sign 的 callback-free upper bound。

## 二、执行（设计时填）

### 前置准备

在写任何 red test 前，以下命令必须全部退出 `0`；本卡不得修改 ADR 状态：

```bash
test -s tfi-flow-core/src/main/java/com/syy/taskflowinsight/enums/MessageSeverity.java
rg -n 'public enum MessageSeverity' \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/enums/MessageSeverity.java
rg -n 'MessageSeverity getSeverity\(\)' \
  tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/Message.java
./mvnw -pl tfi-flow-core -Dtest=AdrDecisionContractTests test
rg -x 'Status: ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_STATUS=ACCEPTED' docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
rg -x 'G3_DECISION=V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES' \
  docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md
```

### 文件与接口

| 动作 | 精确路径 | 类型/方法 |
|------|----------|-----------|
| 新增 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/SessionExportSnapshot.java` | `capture(Session)`、`capture(Session, Limits)`、`isFrozenAttributeValue(Object)` 及 nested records/enums |
| 新增 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/SessionSnapshotCapturer.java` | `capture(Session, Limits, CaptureClock)`、`captureWhileWriteLocked(...)`、`freezeAttribute(Object)`、package-private `CaptureClock.currentTimeMillis()`/`nanoTime()`/`system()`、iterative DFS frames |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/TaskNode.java` | package-private synchronized capture count/index seam，预算预检前不复制完整容器 |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/model/package-info.java` | 记录 capturer 与 supported records 共 package 的封装理由 |
| 修改 | `tfi-flow-core/src/main/java/com/syy/taskflowinsight/internal/FlowConfigDefaults.java` | 三个新增 public budgets |
| 修改 | `tfi-flow-core/src/test/resources/compatibility/public-constants.properties` | 精确类型和值 manifest |
| 新增测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/SessionExportSnapshotTests.java` | public/nested constructor invariants |
| 新增测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/model/SessionSnapshotCapturerTests.java` | capture clock/DFS/budget/value freezing |
| 修改测试 | `tfi-flow-core/src/test/java/com/syy/taskflowinsight/architecture/TaskTreeGateArchitectureTests.java` | clock 采样与 payload/children 有界读取的 AST 约束 |
| 修改文档 | `docs/product/architecture/README.md` | 指向 4.0 export snapshot SSOT，并标注 formatter 迁移边界 |

### 公开类型与方法精确形状

```java
public record SessionExportSnapshot(
        long captureMillis,
        long captureNanos,
        String sessionId,
        String sessionName,
        String threadId,
        String threadName,
        SessionStatus status,
        long createdMillis,
        long createdNanos,
        Long completedMillis,
        Long completedNanos,
        Long durationMillis,
        Long durationNanos,
        Limits limits,
        TaskSnapshot root,
        Statistics statistics,
        boolean truncated) {

    public static SessionExportSnapshot capture(Session session) {
        return capture(session, Limits.defaults());
    }

    public static SessionExportSnapshot capture(
            Session session, Limits limits) {
        return SessionSnapshotCapturer.capture(
                session, limits, CaptureClock.system());
    }

    public record Limits(int maxDepth, int maxNodes,
            int maxPayloadEntries, long maxTextChars) {
        public static Limits defaults() {
            return new Limits(
                    FlowConfigDefaults.MAX_EXPORT_DEPTH,
                    FlowConfigDefaults.MAX_EXPORT_NODES,
                    FlowConfigDefaults.MAX_EXPORT_PAYLOAD_ENTRIES,
                    FlowConfigDefaults.MAX_EXPORT_TEXT_CHARS);
        }
    }

    public record Statistics(
            int totalTasks, int maxDepth, int totalMessages) {}

    public record TaskSnapshot(
            String nodeId, String taskName, String taskPath,
            int depth, int sequence, String threadName,
            TaskStatus status, long createdMillis, long createdNanos,
            Long completedMillis, Long completedNanos,
            Long durationMillis, Long durationNanos,
            long selfDurationMillis, long selfDurationNanos,
            long accumulatedDurationMillis,
            long accumulatedDurationNanos,
            List<MessageSnapshot> messages,
            Map<String, Object> attributes,
            List<String> tags, List<TaskSnapshot> children,
            boolean childrenTruncated) {}

    public record MessageSnapshot(
            String wireType, String displayLabel,
            MessageSeverity severity, String customLabel,
            String content, long timestampMillis,
            long timestampNanos, String threadName) {}

    public enum NumberKind { FLOAT, DOUBLE }

    public record NonFiniteNumber(
            NumberKind numberKind, String value) {}

    public record UnsupportedValue(String className) {}
}
```

`Limits` 要求 `0 <= maxDepth <= FlowConfigDefaults.MAX_EXPORT_DEPTH`、
`0 < maxNodes <= MAX_EXPORT_NODES`、
`0 < maxPayloadEntries <= MAX_EXPORT_PAYLOAD_ENTRIES`、
`0 < maxTextChars <= MAX_EXPORT_TEXT_CHARS`；`defaults()` 返回四个 framework defaults。
`MessageSnapshot` 的 `wireType`/`customLabel` 可为 null，`displayLabel`、`severity`、`content`、
`threadName` 必须非 null。

### 核心步骤

1. 先写 constructor/capturer red tests。至少覆盖 defensive copy、raw mutable/BigInteger subclass rejection、null root、局部 task/session terminal tuple、terminal Session + RUNNING root、root completion after Session、DAG/duplicate IDs/path、depth/sequence、statistics/truncation、accumulated duration、capture bounds、message/task/session monotonic bounds、wall clock exact subtraction、severity freezing、无回调 hostile values。
2. 所有 nested compact constructor 先验证本地不变量并 defensive copy：list 用 `List.copyOf`；attribute map 用 `Collections.unmodifiableMap(new LinkedHashMap<>(attributes))` 以保留合法 null value。attribute key 非 null，value 必须通过 exact-class predicate。
3. 顶层 compact constructor 用 iterative stack 做完整 graph validation：

```text
sessionName == root.taskName
root.depth == 0 && root.sequence == 0 && root.taskPath == root.taskName
captureNanos >= session.createdNanos && root.createdNanos >= session.createdNanos
RUNNING session/task 的 completed/duration tuple 全 null
terminal tuple 全 non-null，nanos 与 wall millis 都是 overflow-checked exact subtraction
terminal Session 必须有 terminal root，root.completedNanos <= session.completedNanos
task/message/child 所有 monotonic 时间不晚于 capture 且不早于 owner/parent
identity set 拒绝 DAG；value set 拒绝 duplicate nodeId
child depth/sequence/path/createdNanos 必须由 parent/list index 推导
accumulatedNanos == self + visible children accumulated
statistics 与 visible tree 精确相等；truncated 等于任一 visible node childrenTruncated
attached Limits 对手工图同样生效
```

允许 COMPLETED Session + FAILED root、ERROR Session + COMPLETED root；不添加 wall-clock ordering 推断，因为系统时钟可回拨。

4. `SessionSnapshotCapturer.capture(...)` 必须先 null-check，再进入唯一 write callback：

```java
return session.captureExport(() -> {
    long captureMillis = clock.currentTimeMillis();
    long captureNanos = clock.nanoTime();
    return captureWhileWriteLocked(
            session, limits, captureMillis, captureNanos);
});
```

使用显式 DFS stack + reverse post-order assembly，保留 sibling insertion order。`sessionName` 只取已经冻结的 `root.taskName()`，不得二次读 mutable root。

5. 先将每个 visible node 的 `messages.size()`、`attributes.size()`、`tags.size()` 分别用 `Math.addExact` 累加到 capture-local `long payloadEntries`，再复制 payload。超过限制抛：

```text
Export payload entry limit exceeded: <limit>
Export text character limit exceeded: <limit>
```

capture path 用 `IllegalStateException`；手工构造顶层快照用 `IllegalArgumentException`。limit 成功，limit+1 原子失败，gate 在 `finally` 释放，后续 mutation/capture 必须成功。

6. running self duration 精确为 `max(0, captureNanos - createdNanos)`；terminal self duration为 completed-created；millis 从 frozen nanos `/ 1_000_000` 推导。statistics 与 accumulated duration 只描述 visible nodes。

7. 新增常量并更新精确 manifest：

```java
public static final int MAX_EXPORT_NODES = 100_000;
public static final int MAX_EXPORT_PAYLOAD_ENTRIES = 1_000_000;
public static final long MAX_EXPORT_TEXT_CHARS = 10_000_000L;
```

### 测试与验收命令

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=SessionExportSnapshotTests,SessionSnapshotCapturerTests test
./mvnw -pl tfi-flow-core \
  -Dtest=SessionExportSnapshotTests,SessionSnapshotCapturerTests,TaskTreeMutationGateTests,TaskTreeCaptureConcurrencyTests,TaskTreeGateArchitectureTests,PublicConstantCompatibilityTests test
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
```

### 审核检查点

- [x] CP-1：L6 与 G3 精确 token 在 red test 前已通过，卡内未修改 ADR acceptance。
- [x] CP-2：两个 clock read 在 write-lock callback 内且各一次，architecture test 有语法树证据。
- [x] CP-3：depth/node truncation 与 payload/text atomic failure 分离，root depth/count 规则精确。
- [x] CP-4：hostile `toString()`、iterator、BigInteger/BigDecimal subclass 测试证明捕获无用户回调。
- [x] CP-5：limit/limit+1、手工图、gate release、后续 mutation/capture 均有回归测试。
- [x] CP-6：admitted exact scalar 值/canonical V2 类型所需信息完整，severity 只冻结未投影。

### 回滚边界

本卡可整体回退 `SessionExportSnapshot`、`SessionSnapshotCapturer`、`TaskNode` capture-only seam、model package
说明、三个默认预算、constants manifest、两组 snapshot 测试、架构 gate 与架构入口说明；保留 `TASK-EXP-01`
的 gate plumbing。禁止只回退 limits/constructor validator 而留下可构造的无界 public snapshot，也禁止在回滚时
添加 generic formatter fallback。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：本卡只定义语义 snapshot/capture，没有迁移 formatter 或发布 schema。
- [x] **认知负担**：公开 records 较多，但每个类型承载明确 immutable contract；遍历 machinery 保持 package-private。
- [x] **比例失调**：核心篇幅用于 graph invariants、预算与无回调值冻结。
- [x] **ROI**：一次建立 Console/Map/JSON 共用的线性化、可测试输入。
- [x] **洁癖检测**：没有为任意对象递归序列化或未来扩展预留 callback SPI。
- [x] **局部 vs 全局**：消费 L6 severity 与 E1a gate，不重复其所有权。
- [x] **过度设计**：V2 Map schema 和 formatter projection 明确保留给后续唯一拥有者。

**结论**：设计通过；用户的全任务顺序实施授权已满足确认 Gate，且 EXP-01、L6 与 accepted G3 均已满足。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| JVM 空线程名 | `threadName` 只要求非 null | 首轮 compact constructor 使用 `requireText` | review 证明 JVM 合法空名称会使真实 capture 失败；RED 后改为 non-null 并保持原值 |
| 预算预检顺序 | payload size 预检后再复制，node budget 限制 child 读取 | 首轮先调用 public getter 复制完整容器 | review 发现超限输入会在 write lock 内先分配无界副本；增加 package-private count/index seam 与 AST gate |

### 检查点结果

- [x] CP-1：ADR contract 4/4；`Status/G3_STATUS/G3_DECISION` 三个精确 token 与 L6 API 同轮通过。
- [x] CP-2：`TaskTreeGateArchitectureTests` 8/8；`CountingClock` 证明 wall/monotonic 各读取一次且位于 capture callback。
- [x] CP-3：depth/node 只产生 `childrenTruncated`；payload/text 的 limit 与 limit+1 分别成功/原子失败。
- [x] CP-4：hostile iterable、`toString()` 与 big-number subclass 回归通过；capturer 无 generic stringify/iteration fallback。
- [x] CP-5：手工图与 runtime capture 使用相同四维预算；失败后 mutation 和再次 capture 均成功。
- [x] CP-6：11 类 admitted exact scalar、两类 non-finite marker、unsupported marker 与 L6 severity 均有行为断言。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25/25 | graph/time/tuple/statistics/duration、空 JVM thread name 与 exact scalar 行为均由 33 个 snapshot tests 覆盖 |
| 完整性 | 25/25 | DoD 8/8、CP 6/6；57 focused、678 Core、API profile 与 7/7 consumer package 通过 |
| 可维护性 | 25/25 | public immutable records 与 package-private capturer 分权；TaskNode seam 不暴露容器或 gate capability |
| 风险控制 | 25/25 | 四维硬预算、无用户回调、无 partial snapshot、失败释放 gate、AST ownership gate 全部通过 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| Important | CR-EXP02-01 | 合法空 JVM thread name 被 compact constructor 拒绝 | `SessionExportSnapshot.java:93` | 已 RED/GREEN，三层 threadName 仅要求非 null |
| Important | CR-EXP02-02 | payload/node 超限前先复制完整容器，capture work 未真正有界 | `SessionSnapshotCapturer.java:136` | 已 RED/GREEN，先计数预检并按 index 有界读取 child |

## 六、完成审核

**审核结论：审核通过。** 两项 Important 已修复，当前 0 unresolved MUST / SHOULD。public snapshot 深度不可变，
capture 的 clock、DFS、payload 与 value freezing 均收敛到唯一 package-private capturer；TaskNode 只增加不暴露容器或
锁能力的 package-private synchronized 计数/索引 seam。

### Fresh 证据（2026-07-11）

- snapshot 专项 33/33；包含 15 个 public graph tests 与 18 个 runtime capture tests。
- focused gate 57/57；覆盖 mutation/capture concurrency、AST ownership、公开常量和全部 EXP-02 行为。
- Core clean verify 678/678；Checkstyle 0、SpotBugs 0/0、JaCoCo gate 通过。
- Core `api-compat` profile 通过；新增 public records/enums 未破坏既有 API。
- 六个消费者模块加 repository parent 的 reactor package 7/7 通过。

### 架构结论与后继边界

`SessionExportSnapshot.java` 超过 500 行的启发式阈值，但主体是 7 个受支持 record/enum 的字段契约、中文 Javadoc
与 compact constructor；遍历和 mutable ownership 已独立在 capturer，继续拆分只会增加 public API 导航成本，
因此登记为非阻塞 INFO，不做架构洁癖式拆分。Canonical V2 Map/JSON projection 与 Console 迁移仍分别由后继
EXP-03..06 owning cards 实施，本卡没有提前创建 schema tree 或 formatter adapter。
