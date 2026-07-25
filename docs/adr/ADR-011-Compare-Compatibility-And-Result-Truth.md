# ADR-011: Compare 兼容政策与结果真实性

Status: ACCEPTED
Date: 2026-07-12

CMP_G1_STATUS=ACCEPTED
CMP_G1_DECISION=BREAKING_MAJOR_4_DIRECT_REMOVAL_EXACT_MANIFEST
CMP_G2_STATUS=ACCEPTED
CMP_G2_DECISION=OUTCOME_PLUS_COMPLETION_NO_FALSE_EQUAL

Supersedes:

- [ADR-002](ADR-002-Diff-Output-Model-ValueRepr-Stability.md) 的结果模型部分。
- [ADR-004](ADR-004-Global-Guardrails-and-Error-Handling.md) 的错误与降级语义部分。

## Intent

确定`tfi-compare` 4.0的兼容边界与唯一结果真值模型，使API、resource、config、schema和behavior变化可审计，
并保证失败、禁用、预算耗尽或证据省略不会伪装成相同或无变化。

## Context

仓库已有checksum固定的`tfi-compare:3.0.0` JAR/POM，可作为可复现的兼容输入。现状`CompareResult`保留
before/after对象引用并允许mutable setter，多个facade还会把disabled/provider failure压成identical。单靠主版本升级、
changes是否为空或japicmp都无法表达全部行为破坏。

本ADR接受Compare总体设计中的`CMP_G1/CMP_G2`推荐分支，并继承Core
[ADR-005](ADR-005-TFI-Flow-Core-Compatibility-Policy.md)的4.0 direct-removal原则，但不复制Core符号清单。

## Decision

### CMP_G1：4.0 exact compatibility manifest

- 目标版本允许直接删除不再成立的3.x合同，但每项变化必须进入单一`breaking-changes-v4.json`。
- manifest entry的`kind`闭集为`API/RESOURCE/CONFIG/SCHEMA/BEHAVIOR`，并包含stable id、before、after、
  replacement、reason、owner task和consumer test；API ABI删除才允许附精确japicmp exclusion。
- Compare建立独立japicmp execution；既有`ApiSurfaceCompatibilityTests`必须改为inventory/manifest双向合同，
  不能继续把“旧符号必须存在”当作4.0目标。
- 任何包级exclusion、关闭兼容门禁、只登记方法数量或以“internal”包名推断可删都不构成授权。
- 删除或行为翻转必须与直接消费者及其测试在同一Wave闭合，至少覆盖`tfi-all`、`tfi-ops-spring`和`tfi-examples`。

### CMP_G2：Outcome + Completion

- `CompareResult`使用独立的`CompareOutcome(EQUAL/DIFFERENT/INDETERMINATE)`与
  `CompareCompletion(COMPLETE/PARTIAL/FAILED/DISABLED)`；`EQUAL`只允许与`COMPLETE`组合。
- 唯一request reducer根据单调aggregate facts归并状态。已经确认的差异不会被后到problem、limitation、deadline或容量省略删除；
  此时结果固定为`DIFFERENT + PARTIAL`。
- 尚无确定差异时，执行故障形成`INDETERMINATE + FAILED`，未执行分支或必需证据省略形成
  `INDETERMINATE + PARTIAL`，显式disabled形成`INDETERMINATE + DISABLED`。
- `CMP_E_*`只进入typed problems，`CMP_W_*`只进入typed limitations。首个problem、首个comparison limitation与
  `CMP_W_2104`使用独立保留槽；`DIFFERENT`不得出现空changes。
- null/type mismatch发布root change；result/change只保留bounded immutable facts，不保存根对象、mutable collection、
  arbitrary Throwable或raw value。
- similarity只有在`COMPLETE`且算法实际支持时发布，并携带versioned `AlgorithmId`；缺失不能编码为0或1 sentinel。
- 调用方输入错误使用typed `CompareInputException/InputViolation`在执行前抛出；fatal错误完成必要的request-local清理后原样传播，
  不进入result/reducer。

## Consequences

- W0必须先生成3.0到当前表面的完整planning inventory、五类manifest骨架、行为清单和消费者影响矩阵。
- `CompareResult/FieldChange`及所有formatter、facade、SPI测试必须迁移到同一真值模型，不允许长期adapter继续推导第二套状态。
- `hasChanges()`等含混API按exact inventory删除或改为语义单一的替代方法；不能在任务卡中临时决定兼容姿态。
- 机器schema必须保留outcome、completion、problems、limitations、diagnostics、changes和omitted counters的对应关系。

## Non-goals

- 本ADR不决定collection算法、Tracking生命周期、Spring module或projection字段编码；分别由ADR-012..014持有。
- 本ADR不宣称当前175个public顶层声明的最终分类已经完成；该清单属于INDEX/W0只读盘点产物。
- 接受Gate不授权修改生产代码、POM、CI或runtime resource。

## Rollback

4.0发布前，如需改变兼容或真值分支，必须用新ADR同时修订CMP_G1/CMP_G2与总体设计，并恢复对应consumer tests。
4.0发布后恢复API/schema只能通过新版本adapter，禁止覆盖已发布制品、复用旧版本号或让formatter回到旧真值推断。

## Verification

1. ADR合同测试只允许一个`CMP_G1`和一个`CMP_G2` accepted machine owner，并校验token精确值。
2. 独立japicmp把3.0差异限制在manifest精确symbol内；五类manifest均有“现状多项/清单多项都会失败”的双向测试。
3. reducer表驱动测试覆盖先差异后problem/limitation、反向顺序、disabled、provider failure、fatal和证据省略。
4. 直接消费者编译与行为测试证明`tfi-all`、Ops、examples没有继续依赖旧setter、sentinel或failure-to-identical语义。

## Links

- [Compare 当前架构 SSOT](../../tfi-compare/docs/design-doc.md)
- [第二轮红队报告](../../tfi-compare/docs/convergence-review/second-red-team-review.md)
- [ADR-012](ADR-012-Compare-Kernel-And-Collection-Semantics.md)
- [ADR-013](ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md)
- [ADR-014](ADR-014-Compare-Projection-Config-And-Quality.md)

## Implementation Evidence

- [当前架构 SSOT](../../tfi-compare/docs/design-doc.md)记录 `Outcome + Completion`、canonical result 与有界证据的最终实现。
- [CompareResultTruthContractTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/compare/CompareResultTruthContractTests.java)
  验证合法真值组合、reducer 单调性与省略行为。
- [CompareBreakingChangeManifestTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareBreakingChangeManifestTests.java)
  验证五类 breaking manifest 与固定 API 基线的精确对应。
- [实施任务索引](../../tfi-compare/docs/ssot-convergence-task/INDEX.md)保存 owner、消费者和完成证据；accepted token 仍只由本 ADR 持有。
