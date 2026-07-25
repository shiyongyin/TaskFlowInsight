# TASK-GRD-04：建立解析器驱动的 V1 精确 Golden 契约

> **定位**：在导出重构前冻结 Console、JSON、Map V1 的合法输入域行为。
> **状态**：完成
> **审核状态**：审核通过（2026-07-11；并发编译阻断解除后 focused 6/6 与 runtime dependency fresh 通过）
> **依赖**：`TASK-GRD-01` | 后续 `TASK-EXP-00` 至 `TASK-EXP-10`
> **架构来源**：contract guardrails Task 4 / `0A.1`

---

## 一、核心（设计时填）

### 背景

三个 exporter 当前分别遍历可变模型，字段、值类型和消息类型已经漂移。快照迁移前必须先以真实输出冻结 V1 合法域；字符串 `contains` 不能证明 JSON 可解析或 Map Java 类型/identity 保持。

### 目标（DoD）

- [x] 创建唯一 `ExportCompatibilityFixture`，供 Console/JSON/Map 契约共享。
- [x] 创建 `ExportV1GoldenTests` 与五份精确 golden：JSON compat/enhanced、Map value、Map types、Console。
- [x] JSON 使用 Jackson parser 比较语义树；Jackson 仅为 test scope。
- [x] Map 断言 key、层级、Java scalar type、null/缺省与 alias identity。
- [x] Console 断言完整文本和旧 boolean 映射，不引入“Console V2”。
- [x] 本卡只覆盖 G3 批准前的 under-limit 合法域；over-limit/非有限数由 G3/Export 卡补齐。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| V1 精确兼容 | 高 | 快照迁移的回归基线 |
| 真实解析/类型 | 高 | 禁止 contains 假断言 |
| 测试复用 | 中 | fixture 与 golden 只能有一套所有者 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| JSON 校验 | Jackson parser + canonical tree | 证明输出合法且语义精确 | 字符串 contains |
| Map 校验 | 值与 runtime type/identity 同时断言 | V1 Java contract 不只是序列化文本 | 仅比较 `toString()` |
| Console 契约 | V1 文本 + additive options 后续演进 | 计划没有 Console V2 schema | 使用“Console V2”术语 |

### 跨卡不变量

- V1 在本 portfolio 中不退役，不创建 retirement ledger。
- G3 未接受前，不把 NaN/Infinity、unsupported object 或 text limit 行为固化为合法契约。
- Export 专项必须复用本卡 fixture/golden，不得创建平行副本。

## 二、执行（设计时填）

### 前置准备

记录当前三类 exporter 的 public overload、null 行为、字段顺序和旧 boolean 行为。

### 核心步骤

1. 在 core test scope 添加 `jackson-databind`，生产 dependency tree 不得出现 Jackson。
2. 创建 `tfi-flow-core/src/test/java/com/syy/taskflowinsight/exporter/ExportCompatibilityFixture.java`。
3. 创建 `ExportV1GoldenTests.java`，同一 Session fixture 驱动三个 exporter。
4. 创建 `src/test/resources/golden/export/` 下五份 golden 资源。
5. 同时断言 facade 异常 fallback：Console `false`、JSON `{}`、Map empty。
6. 运行：

```bash
./mvnw -pl tfi-flow-core -Dtest=ExportV1GoldenTests test
./mvnw -pl tfi-flow-core dependency:tree -Dscope=runtime
```

### 审核检查点

- [ ] CP-1：JSON 每个输出均通过 parser。
- [ ] CP-2：Map identity/type 断言不是字符串化比较。
- [ ] CP-3：golden 覆盖 null、空集合、嵌套树、消息和属性。
- [ ] CP-4：G3 异常域未提前固化。

### 回滚边界

本卡只添加 characterization 证据。若发现现有输出互相矛盾，记录在 G3/ADR-008，不在本卡改变生产行为。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只冻结现状合法域。
- [x] **认知负担**：一个 fixture、一套测试、五份资源。
- [x] **比例失调**：重点放在 parser/type/identity。
- [x] **ROI**：为高风险导出重构提供可机器验证护栏。
- [x] **洁癖检测**：不调整 formatter 结构。
- [x] **局部 vs 全局**：三个格式共享同一基准 Session。
- [x] **过度设计**：未创建通用 golden 框架。

**结论**：设计通过；Guardrail 阶段 DoD 仅要求 under-limit 合法域通过。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| Jackson 数字节点规范化 | 直接比较 canonical tree | 首次 GREEN 中 `IntNode(0)` 与 `LongNode(0)` 文本相同但节点不等；JSON 易变值统一写为整数节点，Map value 经序列化后重新解析 | Map 的精确 Java 类型已由独立 types tree 锁定，值树不应重复承载 Java 装箱类型语义 |
| 实施范围 | 仅增加 characterization 证据 | 未修改 Console/JSON/Map 生产实现；新增 test-scope 依赖、两个测试类和五份 golden | 遵守回滚边界，不在本卡修正已有格式差异 |

### 实施与验证记录

- RED：`./mvnw -pl tfi-flow-core -Dtest=ExportV1GoldenTests test`，6 个测试中 2 个非资源场景通过，
  4 个精确比对因 `/golden/export/*` 缺失报错，确认测试会对缺失基线失败。
- GREEN：同一 focused 命令通过，`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`。
- Runtime dependency：`./mvnw -pl tfi-flow-core dependency:tree -Dscope=runtime` 仅包含
  `org.slf4j:slf4j-api:2.0.17:compile`，未出现 Jackson。
- 覆盖内容：同一 completed Session 包含两个有序 child、PROCESS/ALERT 消息、六类合法属性、null、
  两个有序 tag；同时验证 exporter null 语义和 `DefaultExportProvider` 的旧 boolean 映射。

### 检查点结果

- [x] CP-1：compat/enhanced 输出均由 Jackson `readTree` 解析后与完整 golden tree 比较。
- [x] CP-2：Map value tree、递归 `Class.getName()` type tree 分别精确比较，并断言
  `task == tasks[0]`、Integer/Long/null 叶类型。
- [x] CP-3：唯一 fixture 覆盖 null、空消息集合、两层嵌套树、PROCESS/ALERT、属性与有序 tag。
- [x] CP-4：人工范围审核确认未加入 collection/map/array/arbitrary object、NaN/Infinity 或 over-limit 场景。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | parser tree、完整文本、runtime type 与 alias identity 分维度精确验证；focused tests 6/6 通过 |
| 完整性 | 25 /25 | 五份 golden、两种 JSON 模式、Map、Console、null/fallback 与 boolean 入口均覆盖 |
| 可维护性 | 25 /25 | 单一 fixture/归一化 owner；中文类注释说明共享夹具和分离值/类型契约的原因 |
| 风险控制 | 25 /25 | 无生产行为改动；Jackson 未进入 runtime；G3 未批准异常域未提前固化 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST | - | 快速审查未发现阻断问题 | `ExportCompatibilityFixture.java` / `ExportV1GoldenTests.java` | 无需修复 |

### 残余风险

- 本卡冻结的是当前 under-limit V1 合法域；G3 相关非有限数、任意对象和超限行为仍必须由后续 Export 卡处理。
- Golden 证明当前工作树行为，不替代 `TASK-GRD-01` 中尚未发布到外部仓库的 `3.0.0` 基线证据。

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。首次验证曾被在途 PRV-03 测试编译错误阻断；该并发阻断解除后，focused golden 6/6
fresh 通过，runtime dependency 也证明 Jackson 未进入生产范围。

### 当前直接证据

- 五份声明的 golden 资源均存在；测试以 Jackson tree 比较 JSON，以独立 value/type tree 和 `isSameAs`
  验证 Map scalar type 与 alias identity，并完整比较 Console 文本。
- `./mvnw -pl tfi-flow-core dependency:tree -Dscope=runtime`：仅 `slf4j-api:2.0.17`，Jackson 未进入 runtime。
- `./mvnw -pl tfi-flow-core -Dtest=ExportV1GoldenTests test` 在 testCompile 阶段失败：
  `ProviderRegistryEpochConcurrencyTests.java:102` 调用不存在的 `withRootCauseMessage(String)`；
  `ExportV1GoldenTests` 未开始执行。
- 上述并发错误修正后重跑同一 focused 命令：6/6 通过，`BUILD SUCCESS`。

### 时态消歧与解除条件

- “V1 在本 portfolio 中不退役”已被后继接受的 G3 `V2_ONLY` 决策替代；本卡当前角色是迁移前历史
  characterization，不是 4.0 最终发布契约。
- V1 golden 在 Export V2 迁移完成前继续作为历史行为证据；后继卡必须明确决定其保留或替换方式。

## 六、完成审核

### 审核结论

**审核通过。** parser-driven golden 6/6 与 Core runtime dependency 边界 fresh 通过。
