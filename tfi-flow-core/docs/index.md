# tfi-flow-core 文档入口

> **定位**：tfi-flow-core 当前文档导航 | **版本轴**：`4.0.0-SNAPSHOT`
> **规则**：本页只指向事实 owner，不复制架构正文、schema 字段树、breaking symbol 清单或构建结果。

---

## 1. 模块边界

`tfi-flow-core` 是 TaskFlowInsight 的纯 Java 执行流内核，管理 Session、Task、Message、Context、Provider 与
Export。生产运行时只依赖 SLF4J，不依赖 Spring、Micrometer、Caffeine 或 compare/tracking 实现。

| 责任 | 当前边界 |
|------|----------|
| API | `TfiFlow`/`TaskContext` 提供 facade 与 scope；facade 不拥有 Provider cache |
| Context | `SafeContextManager` 是当前态、registry、scheduler 与 metrics 的唯一 owner |
| Provider | `ProviderRegistryEngine` 是 candidate、selected、epoch、trust policy 与 capacity 的唯一 owner |
| Model | `Session`/`TaskNode`/`Message` 表达执行树与状态机 |
| Export | 一次捕获 `SessionExportSnapshot`；Console 为诊断文本，Map/JSON 为 canonical V2 |

详细职责和依赖方向只在[开发设计文档](design-doc.md)维护。

---

## 2. 当前文档

| 文档 | 唯一责任 |
|------|----------|
| [开发设计文档](design-doc.md) | 当前模块架构、owner、并发、数据流与失败边界 |
| [产品需求文档](prd.md) | 用户可见合同、非功能边界与迁移方向 |
| [测试方案](test-plan.md) | 不变量、owner suite、失败矩阵与可复制命令 |
| [运维文档](ops-doc.md) | 构建、发布、监控、容量与故障处理 |

逐卡实施状态、fresh 数字证据和独立审核结论由
[SSOT 收敛任务索引](ssot-convergence-task/INDEX.md)维护，不写回长期模块文档。

---

## 3. 决策与机器契约 SSOT

| 范围 | 唯一 owner |
|------|------------|
| 4.0 兼容与删除政策 | [ADR-005](../../docs/adr/ADR-005-TFI-Flow-Core-Compatibility-Policy.md) |
| Context/异步所有权 | [ADR-006](../../docs/adr/ADR-006-TFI-Context-And-Async-Ownership.md) |
| Provider 选择、变更与信任 | [ADR-007](../../docs/adr/ADR-007-TFI-Provider-Selection-And-Mutation.md) |
| Export snapshot、值域与 schema | [ADR-008](../../docs/adr/ADR-008-TFI-Export-Snapshot-And-Schema.md) |
| Session compatibility bridge | [ADR-009](../../docs/adr/ADR-009-TFI-Session-Compatibility-Bridge.md) |
| Nested-depth 处置 | [ADR-010](../../docs/adr/ADR-010-TFI-Nested-Depth.md) |
| 4.0 exact removals | [breaking manifest](../src/test/resources/compatibility/breaking-changes-v4.json) |
| 实施与审核状态 | [任务卡索引](ssot-convergence-task/INDEX.md) |

ADR 决定政策和行为边界；breaking manifest 决定精确删除集合；POM japicmp exclusions 必须与 manifest 双向一致。
本模块不创建第二份 deprecation policy、removal ledger 或嵌套 ADR。

---

## 4. Export 当前合同

- 非空 Session 的每个 direct public export 只捕获一份有界、深度不可变的 `SessionExportSnapshot`。
- Console 是 snapshot-only TREE/SIMPLE 人类诊断文本；`ConsoleStyle` 只决定 style，`showTimestamp` 只决定时间戳。
- Map 与 JSON 只发布同一棵 canonical V2；runtime 不存在 V1 route、JSON mode 或第二字段树。
- `SessionSnapshotCapturer` 是唯一 mutable-tree Export reader；`CanonicalExportV2Projection` 是唯一 schema builder。
- capturer 在 write lock 内冻结 raw values，释放锁后才组装并校验 public snapshot；锁外不重读 mutable model。
- 旧 `TaskDurationCache` 已在 4.0 删除；exact baseline removals 只查询 breaking manifest，不在本页复制。

内部所有权见[开发设计文档 · Exporter 层](design-doc.md#35-exporter-层)，用户合同见
[PRD · F4](prd.md#f4导出功能)，验收与失败矩阵见[测试方案 · Export](test-plan.md#三export-验收合同)。

---

## 5. 质量证据入口

```bash
# Core 当前行为与模块质量门禁
./mvnw -pl tfi-flow-core clean verify

# 3.0 baseline API diff 与 exact breaking classification
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests

# 全消费者 package，固定包含 examples
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

生成证据位于 `tfi-flow-core/target/`：Surefire reports、JaCoCo site、Checkstyle、SpotBugs 与 PMD；
`api-compat` profile 成功时另生成 `target/japicmp/` reports，并以 Maven build result 判定兼容门禁。
profile 的 3.0 输入来自 checksum-protected `.mvn/api-baseline`；它是可复现构建输入，不是外部发布证明。
本页不保存某次运行的测试、覆盖率、静态分析或源码数量。

Repository Portfolio 结果必须由同一 checkout 的 `./mvnw clean verify` 证明；模块门禁通过不能外推为
Portfolio 完成。某次执行是否完成只查询任务索引，不在本页复制。

---

## 6. 维护规则

- 当前架构、public contract 或 ownership 改变时，先更新对应 ACCEPTED ADR 与机器门禁，再更新开发设计文档。
- CI/本地运行数字从当次 artifact 和 `target/` 报告读取；长期文档不保存测试数、覆盖率或 finding 数。
- 实施进度、偏差和独立审核结论只从[任务卡索引](ssot-convergence-task/INDEX.md)读取。
