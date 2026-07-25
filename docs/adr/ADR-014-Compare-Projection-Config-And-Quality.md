# ADR-014: Compare Projection、配置与质量门禁

Status: ACCEPTED
Date: 2026-07-12

CMP_G7_STATUS=ACCEPTED
CMP_G7_DECISION=MODULE_STRICT_GATES_NAVIGATION_ONLY_INDEX

Supersedes:

- [ADR-002](ADR-002-Diff-Output-Model-ValueRepr-Stability.md) 的output/value representation部分。
- [ADR-004](ADR-004-Global-Guardrails-and-Error-Handling.md) 的metrics、performance与文档门禁部分。

## Intent

把Compare结果投影、masking、Spring配置和质量证据分别收敛到唯一owner，并建立模块级阻断门禁与导航型长期文档，
防止formatter、binder、CI和历史文档重新形成平行事实源。

## Context

现状存在JSON/Map/XML/CSV/Markdown/Console/Streaming多套字段树与mask规则，11个binder、43项metadata、12处`@Value`
和system/env读取互相分叉。Compare没有独立japicmp，existing API存在性测试与4.0删除目标冲突；strict routing perf workflow
也未被早期规划识别。长期模块文档还复制测试数量、覆盖率和版本叙事。

本ADR承接ADR-013已接受CMP_G6中的canonical projection/config派生合同，但不重复声明CMP_G6 machine status。

## Decision

### Canonical projection与masking

- immutable `CompareResult`、可选bounded metadata、immutable `MaskingPolicy`和`ProjectionOptions`只经一个
  `CompareProjectionFactory`生成一棵immutable projection。
- JSON与Map编码同一canonical tree并做parser-tree parity；Markdown/Console只读该projection。formatter不得读取业务对象、
  raw result或建立私有mask规则。
- value/path/type facts使用总体设计§11.3/§12.3的bounded representation、canonical path fact cost和total budget；
  JSON escaping不反向改变Compare path limit触发点。
- 默认masking是安全floor：field/path规则与内置Luhn支付卡、SSN内容检测共同生效；所有将以EXACT发布的scalar必须完整扫描。
  include-sensitive只允许显式projection调用逐次传入代码构造的immutable policy，不能由Spring配置、annotation或global default开启。
- JSON/Map/Markdown/Console是目标owner集合；CSV/XML/Streaming等旧public表面必须由G1 exact inventory逐项登记后删除，
  不能仅凭本ADR概括删除。
- Writer/OutputStream入口只flush不close调用方资源；I/O故障原样传播且不伪报成功，失败后stream是否可用由具体实现和调用方决定。

### 配置唯一owner

- 纯Java `ComparePolicy`是comparison semantics/default/hard ceiling owner，`CompareOptions`只能在Policy范围内选择或收紧。
- Spring只绑定`tfi.compare.*`并构造context-local immutable objects；旧key、alias、default与behavior变化进入G1 manifest。
- canonical key与alias先typed bind：同值时canonical胜出并最多告警一次；异值、多alias冲突或转换失败均启动失败，
  不按PropertySource顺序last-wins。
- `RenderOptions`只影响Markdown/Console布局，不能选择schema字段、改变masking或重新定义comparison semantics。
- 自动degradation、parallel/perf fallback和no-op key删除，不迁移成第二Policy owner。

### CMP_G7：模块严格门禁与导航文档

- Compare模块拥有独立japicmp、JaCoCo、SpotBugs、Checkstyle/PMD delta、ArchUnit、ServiceLoader、schema parity和consumer门禁。
- W0先建立3.0 inventory、manifest、当前behavior characterization、跨格式golden、static-analysis checksum baseline及两张追踪矩阵。
- `.github/workflows/perf-gate.yml`及其JMH runner/tests进入CI inventory。现有routing `<5%` strict gate在用户确认的设计修订
  及其accepted ADR显式替代前保持blocking；W1/W6同Wave保持job可执行并重建同轴证据。
- 没有批准SLA的新算法基准只报告变化；性能阈值不能覆盖正确性失败，也不能由任务卡自行放宽既有blocking gate。
- 每个Wave同时关闭owner、直接消费者和相关测试，并具有绿色可回滚出口；最终运行全消费者compile和portfolio verify。
- `tfi-compare/docs/design-doc.md`是完成后的当前架构SSOT，`index.md`只导航；长期文档不复制测试数量、覆盖率、评分或某次报告快照。
- 任务INDEX保存Gate、Wave、矩阵和状态；实施任务卡必须晚于INDEX盘点复核，且无权改变ADR或总体设计。

## Consequences

- W5负责projection/masking/output纵向迁移，W6负责starter/config/Ops抽取，W7负责build/CI/docs收口；消费者与golden随owner同Wave迁移。
- Compare内私有formatter regex、multiple schema tree、mutable export defaults和配置静态owner必须退役。
- 任何mask规则缩窄、schema字段变化、alias行为或strict gate变化都属于显式security/schema/config/quality决策，不能顺手发生。

## Non-goals

- 不在长期文档承诺当前测试数、覆盖率数值或未批准的性能SLA。
- 不新增在线配置中心、HTTP管理面、持久化、后台cleanup或shutdown flush协议。
- 不创建第二projection、第二mask detector SPI或任意用户正则执行器。

## Rollback

Output回滚必须同时恢复projection与全部formatter/golden，禁止混用新masking和旧schema。配置回滚必须保持单一Policy owner；
starter/CI回滚不得删除manifest证据或静默降级strict gate。发布后的schema/API恢复通过新版本adapter，不覆盖既有发布物。

## Verification

1. JSON/Map same-tree parity与跨格式golden覆盖masking、ValueSnapshot、metadata、omitted counters和escaping边界。
2. configuration contract tests覆盖canonical/alias同值、冲突、转换失败、safe masking floor、多bean和context isolation。
3. Maven/CI门禁验证japicmp exact manifest、module coverage/static analysis、ServiceLoader、Boot resources及全部消费者。
4. perf workflow在W1/W6保持可执行；任何替代决策包含可复现实验、噪声区间、replacement owner和回滚条件。
5. 文档合同测试验证design-doc为SSOT、index只导航，INDEX/任务卡不复制或改写accepted Gate token。

## Links

- [Compare 当前架构 SSOT](../../tfi-compare/docs/design-doc.md)
- [ADR-011](ADR-011-Compare-Compatibility-And-Result-Truth.md)
- [ADR-012](ADR-012-Compare-Kernel-And-Collection-Semantics.md)
- [ADR-013](ADR-013-Compare-Tracking-Provider-And-Spring-Composition.md)
- [第二轮红队报告](../../tfi-compare/docs/convergence-review/second-red-team-review.md)

## Implementation Evidence

- [当前架构 SSOT](../../tfi-compare/docs/design-doc.md)记录 canonical projection、safe masking、配置 owner 与质量边界。
- [CompareProjectionSchemaContractTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/projection/CompareProjectionSchemaContractTests.java)
  验证唯一 projection schema 与有界字段树。
- [CompareBuildConfigurationContractTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/CompareBuildConfigurationContractTests.java)
  验证模块 POM、CI、API 与消费者门禁配置。
- [CompareDocumentationContractTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/architecture/CompareDocumentationContractTests.java)
  验证长期文档职责、导航、失效入口和相对链接。
- [实施任务索引](../../tfi-compare/docs/ssot-convergence-task/INDEX.md)保存 OUT/SPR/OPS/QLT/DOC owner 与完成证据。
