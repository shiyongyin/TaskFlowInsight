# ADR-008: Export Snapshot、值域与 Schema

Status: ACCEPTED

G3_STATUS=ACCEPTED
G3_DECISION=V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES

## Intent

确定一次导出的线性化快照、合法标量域、文本上限与特殊值 wire 语义，使 Console/JSON/Map formatter
不再直接遍历可变模型，并让 4.0 只发布一个 canonical V2 机器契约。

## Context

GRD-04 已记录 under-limit 的 V1 Console、JSON compat/enhanced 与 Map value/type/identity，但 G1 已选择
4.0 breaking-major，用户进一步明确选择 V2-only。这些 golden 只作为历史行为证据，不再约束 4.0
输出。collections、maps、arrays、任意对象、NaN/Infinity 和 over-limit 仍需由无回调规则显式处理。

## Decision

接受 `V2_ONLY_CALLBACK_FREE_SCALARS_WITH_TAGGED_SPECIAL_VALUES`：

- 一次导出在线性化 gate 内捕获一份深不可变 snapshot；formatter 捕获后不得读取可变模型。
- 当 callback-free aggregate text estimate 不超过 `MAX_EXPORT_TEXT_CHARS=10_000_000` UTF-16
  code units 时，精确保留 null 以及 String、Boolean、Character、Byte、Short、Integer、Long、
  finite Float/Double、BigInteger、BigDecimal 的值和 canonical V2 类型语义。
- 上限 `limit` 成功；`limit + 1` 在任何 projection/output 前抛出
  `IllegalStateException("Export text character limit exceeded: " + limit)`，不截断、不部分输出。
  手工构造的顶层 snapshot 对同类非法输入使用 `IllegalArgumentException`。
- containers、arrays、enums、任意 Number 子类及任意对象不执行迭代或回调，只记录 class-name metadata。
- JSON 与 Map 只发布同一棵 canonical V2 projection，并包含 `schemaVersion=2`；非有限 Float/Double
  与 unsupported value 使用可逆 tagged object，不再维护 V1 的分叉表示。
- 4.0 中现有 JSON/Map 主入口和 `ExportProvider` 直接消费 V2，不新增与主入口竞争的平行 V1/V2 API。
- Console 保留为供人阅读的 snapshot-only 诊断输出，但不再声明 V1 schema 或字节级兼容；Console
  与 JSON/Map 共享值域、预算和无部分输出约束。
- direct `JsonExporter.export(Session)` 必须在 legacy rendering-error catch 之前捕获，故 capture/limit/lock
  失败原样传播；`TfiFlow` facade 仍分别收敛为 Console `false`、JSON `{}`、Map empty。

EXP-02/03/04/05/06 与 GRD-04 的 V1 兼容断言必须先修订为 V2-only 路线，不能直接消费旧任务卡。

## Consequences

- EXP-02/03/04/05/06 在 V2-only 任务卡修订完成前保持阻塞；GRD-04 golden 降级为历史证据。
- JSON/Map 只有一个 canonical V2 schema；不得保留隐藏 V1 adapter 或分别构建两棵字段树。
- Console 是非 schema 化诊断文本，不得以 “Console V2 schema” 名义复制 JSON/Map 字段模型。
- snapshot capture 必须可回滚、释放锁，并允许失败后继续 mutation；所有格式共享同一 snapshot owner。
- 任何安全修正都必须证明无 callback、无 partial writer/console/map projection。

## Rollback

被运行时卡消费前如需改变 schema 路线，必须通过新 ADR 修订。消费后回滚必须同时恢复 capture gate、
三个 formatter 与 facade fallback，并以 canonical V2、limit 边界和失败后 mutation 测试证明没有留下
混合 traversal 或隐藏 V1 路径。

## Verification

1. JSON 与 Map 对同一 snapshot 生成相同 canonical V2 tree，并精确验证 `schemaVersion=2`、字段、类型
   与 tagged special values。
2. 架构测试证明生产 formatter、Provider 和 facade 不再引用 V1 adapter，历史 V1 golden 不作为 4.0
   兼容门禁。
3. `limit-1/limit/limit+1` 验证边界、零 partial output/projection、direct JSON 异常传播与 facade fallback。
4. 并发测试验证一次快照、锁释放、失败后 mutation；Console 测试验证无回调且不声明 schema 兼容。

## Implementation Evidence

截至 2026-07-11，Decision 中的实施前阻塞已由修订后的 EXP-01..08 逐项消费；Consequences 第一项描述的是
当时的硬停止条件，不是当前仍阻塞状态。当前实现证据如下：

- EXP-01 在 Session 内建立唯一 package-private `TaskTreeMutationGate`；mutation 使用 read side，capture 使用
  有限等待的公平 write side，permit 不逃逸。
- EXP-02 建立 public `SessionExportSnapshot` 与 package-private `SessionSnapshotCapturer`；capturer 在取得
  write lock 后各采样一次 wall/monotonic clock，以显式栈冻结有限、深度不可变、callback-free 的语义树。
- EXP-03/07 让 Console 只读 snapshot 并迭代渲染；`ConsoleStyle` 只选择 TREE/SIMPLE，`showTimestamp`
  只控制消息时间戳，Console 始终是非 schema 诊断文本。
- EXP-04/05/06 建立 package-private `CanonicalExportV2Projection` 作为唯一字段树 owner；Map 与 JSON
  只发布 canonical V2，并以同一 prebuilt snapshot 的 Map equality 与 JSON parser-tree equality 验证 parity。
- EXP-08 删除旧 mutable-tree cache source/test；本地 `3.0.0` baseline 的 class 与两个真实 methods 由
  ADR-005 breaking manifest/POM exact 登记，未发布方法没有伪造成 removal。
- EXP-09 把当前 owner、失败语义、V2-only、Console non-schema 与 direct-removal 事实发布到 module
  design/PRD/test/index；长期模块文档不保存某次测试、覆盖率或静态分析数字。

可复制验证分为以下 owner families；fresh 数值结果记录在 `TASK-EXP-09.md`，不复制到本 ADR：

```bash
./mvnw -pl tfi-flow-core \
  -Dtest=AdrDecisionContractTests,BreakingChangeManifestTests,TaskTreeGateArchitectureTests test
./mvnw -pl tfi-flow-core \
  -Dtest=TaskTreeMutationGateTests,TaskTreeCaptureConcurrencyTests,SessionExportSnapshotTests,SessionSnapshotCapturerTests,CanonicalExportV2ProjectionTests,ConsoleExporterTest,ConsoleExporterOptionsTests,MapExporterTest,JsonExporterTest,ExportV1GoldenTests,ExportV2ContractTests,ExportSnapshotDeepTreeTests test
./mvnw -pl tfi-flow-core clean verify
./mvnw -pl tfi-flow-core -Papi-compat verify -DskipTests
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

Repository Portfolio `./mvnw clean verify` 不属于该局部证据；只由 `TASK-DOC-01` 在 Build/Test/Docs 前置完成后运行。
