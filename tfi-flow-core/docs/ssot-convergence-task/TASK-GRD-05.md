# TASK-GRD-05：保护公共编译期常量

> **定位**：为 API diff 无法完整表达的 public constant 值建立精确 manifest 契约。
> **状态**：完成
> **审核状态**：审核通过（2026-07-11；双向常量 manifest 与受控改值负向 fixture fresh 通过）
> **依赖**：`TASK-GRD-01` | 后续 `TASK-GRD-06`、`TASK-BLD-02`
> **架构来源**：contract guardrails Task 5 / `0A.1`

---

## 一、核心（设计时填）

### 背景

public static final 常量的名称可能保持不变而值发生变化，二进制调用方又可能已经内联旧值。仅靠 class ABI diff 不能保护这些语义；删除 `internal.ConfigDefaults` 前也需要明确哪些常量属于受支持表面。

### 目标（DoD）

- [x] 生成并维护精确的 public constant manifest（owner、field、descriptor、value）。
- [x] `PublicConstantCompatibilityTests` 反射当前类并与 manifest 双向比较。
- [x] 新增、删除、改名、改 descriptor、改值均使测试失败。
- [x] manifest 不以整包 wildcard 排除 `internal`。
- [x] `ConfigDefaults` 的计划删除只允许通过 `TASK-BLD-02` 的两个精确政策例外。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 常量语义 | 高 | 值变化可能绕过 ABI 检查 |
| 双向完整性 | 高 | 防 manifest 漏项与陈旧项 |
| 删除治理 | 中 | 为 B2 精确例外提供证据 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 契约格式 | 机器可解析的精确 manifest | 可稳定 diff 和逐项审核 | Javadoc 手工列表 |
| 扫描范围 | 全部 public static final 编译期常量 | 不依赖人工挑选 | 只扫描已知类 |

### 跨卡不变量

- constant manifest 与 japicmp/ledger 互补，不能相互替代。
- 任何删除例外必须精确到 class/field，禁止 `internal.*`。

## 二、执行（设计时填）

### 前置准备

以 `3.0.0` 发布基线和当前源码分别枚举公共常量，确认差异来源。

### 核心步骤

1. 创建 `tfi-flow-core/src/test/resources/compatibility/public-constants.json`（或计划指定的精确资源名）。
2. 创建 `PublicConstantCompatibilityTests`：验证 modifier、字段类型、descriptor 与序列化值。
3. 实现双向集合比较：源码多一项或 manifest 多一项都失败。
4. 加入可控负向 fixture，证明改值会失败。
5. 运行：

```bash
./mvnw -pl tfi-flow-core -Dtest=PublicConstantCompatibilityTests test
```

### 审核检查点

- [ ] CP-1：覆盖 inherited/nested public constants 的既定范围。
- [ ] CP-2：值比较保留数值/字符串类型，不统一字符串化。
- [ ] CP-3：无 package-wide exclusion。
- [ ] CP-4：B2 两个候选删除项可被精确定位。

### 回滚边界

本卡不修改常量值。若 manifest 揭示既有漂移，先记录并走兼容决策，不自动把当前值认定为新基线。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只建立常量证据。
- [x] **认知负担**：一个 manifest 与一个参数化测试。
- [x] **比例失调**：重点为双向完整性和精确值。
- [x] **ROI**：填补编译期内联常量盲区。
- [x] **洁癖检测**：不改常量命名。
- [x] **局部 vs 全局**：为 ledger/B2 提供共同基线。
- [x] **过度设计**：不构建通用 API schema 平台。

**结论**：设计通过，待确认后实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 发布基线来源 | 从外部仓库解析 `3.0.0` JAR | 使用用户已批准的本机 `~/.m2` 3.0 JAR；生成器输出 80 项 | Maven Central 不存在该 artifact；沿用 GRD-01 已记录的用户豁免，不能把本机 JAR 宣称为外部不可变证据 |
| 当前 additive 常量 | manifest 以 3.0 输出为起点 | 显式追加 `FlowConfigDefaults.MAX_EXPORT_DEPTH` 与 `MAX_MESSAGES_PER_NODE`，manifest 共 82 项 | 两项属于用户要求保留的既有安全改动；双向门禁要求当前新增 public 常量也必须经过审核后入表 |
| Review 修正 | 生成器/测试直接落地 | 保留字符串首尾空格；生成器同时拒绝新增或缺失已审查 holder | 防止 String 值被测试预处理篡改，并防止错误/不完整 JAR 静默生成残缺 manifest |

### 实施与验证记录

- RED：`./mvnw -pl tfi-flow-core -Dtest=PublicConstantCompatibilityTests test`，3 个测试中负向改值
  fixture 通过，另 2 个因缺失 `/compatibility/public-constants.properties` 报错。
- 基线生成：隔离运行 `PublicConstantManifestGenerator` 读取本地批准的
  `tfi-flow-core-3.0.0.jar`，只发现三个已审查 holder，命令退出 0；输出与 `javap -public -constants` 对照一致。
- GREEN：focused 命令通过，`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`。
- 负向证据：`NegativeConstantFixture.VALUE` 实际为 `1`，以 manifest 预期 `2` 调用同一比较器，
  测试要求并观察到 `AssertionError`，证明只改值也会阻断。

### 检查点结果

- [x] CP-1：扫描 `target/classes` 的全部 class，并通过 `Class#getFields()` 覆盖 inherited public field；
  nested `ConfigDefaults$Keys` 作为独立 owner 入表。
- [x] CP-2：manifest 按 boolean/byte/char/short/int/long/float/double/String 分型解析，值比较不统一字符串化。
- [x] CP-3：无 package wildcard；未来 removal policy 只接受无 `*`、无 `..`、非包尾点的精确 CLASS/FIELD symbol。
- [x] CP-4：manifest 和测试分别精确定位 `ConfigDefaults#FIELD` 与 `ConfigDefaults$Keys#FIELD`，
  为 B2 两个 class 级政策例外提供边界。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | 当前 82 项 owner/field/type/value 双向一致；受控改值负向 fixture 可阻断 |
| 完整性 | 25 /25 | baseline 生成、nested/inherited 扫描、排序去重、add/delete/rename/type/value 全覆盖 |
| 可维护性 | 25 /25 | 单一 properties manifest；生成器中文 Javadoc 说明隔离加载与 holder 审查原因 |
| 风险控制 | 25 /25 | 不改生产常量；未知/缺失 holder 阻断；删除例外精确且 package wildcard 禁止 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| SHOULD | GRD05-R1 | 整行 `trim()` 会破坏 String 常量首尾空格 | `PublicConstantCompatibilityTests.java` | 已改为仅过滤 blank/comment，不改值 |
| SHOULD | GRD05-R2 | holder 检查只拒绝新增，未拒绝错误 JAR 中的 holder 缺失 | `PublicConstantManifestGenerator.java` | 已改为集合精确相等 |
| MUST | - | 修正后快速复审未发现阻断问题 | 两个兼容测试类与 manifest | 无需进一步修复 |

### 残余风险

- 本机 `3.0.0` JAR 仍不是外部仓库可重新解析的不可变发布证据；正式发布后必须重新生成并核对 manifest。
- `ConfigDefaults` 与 `ConfigDefaults$Keys` 仍在当前源码中；只有 B2 对应的两个精确 class 级政策例外成熟后才允许移除。

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。当前 manifest 与编译产物仍执行双向集合和值/类型比较，受控改值 fixture 可证明单纯值漂移也会
阻断；breaking policy 只消费带 `PUBLIC_CONSTANT_MANIFEST` evidence 的精确 CLASS/FIELD 删除。

### 当前直接证据

- `./mvnw -pl tfi-flow-core -Dtest=PublicConstantCompatibilityTests test`：4/4 通过。
- 测试扫描 `target/classes` 并通过 `Class#getFields()` 覆盖 inherited public fields；nested holder 作为
  独立 owner 入表。
- manifest 保留 boolean/byte/char/short/int/long/float/double/String 类型解析，String 值不做整体 trim。
- 受控 `NegativeConstantFixture.VALUE` 期望 2、实际 1，测试断言同一比较器抛出包含字段和值的
  `AssertionError`。
- 当前 manifest 为 76 行；数量较历史 82 项减少是已授权 breaking manifest 消费后的结果，fresh 双向测试通过，
  不能继续把历史数量 82 当作持续不变量。

## 六、完成审核

### 审核结论

**审核通过。** 公共常量双向 manifest、排序去重与受控改值负向 fixture 均通过。
