# ADR-013: Compare Tracking、Provider与Spring组合

Status: ACCEPTED
Date: 2026-07-12

CMP_G4_STATUS=ACCEPTED
CMP_G4_DECISION=EXPLICIT_TRACKING_SCOPE_NO_COMPARE_THREADLOCAL
CMP_G5_STATUS=ACCEPTED
CMP_G5_DECISION=CORE_REGISTRY_ONLY_STATELESS_ADAPTERS
CMP_G6_STATUS=ACCEPTED
CMP_G6_DECISION=PURE_KERNEL_NEW_COMPARE_STARTER_EXTERNAL_OPS_CANONICAL_PROJECTION

Supersedes:

- [ADR-004](ADR-004-Global-Guardrails-and-Error-Handling.md) 的Tracking ThreadLocal、错误处理、metrics与自动降级部分。

## Intent

确定Tracking action生命周期、Core Provider Registry边界和Spring模块组合，使业务action只由一个final executor包装，
static TFI与Spring context各自使用正确作用域的runtime，同时保持纯Compare内核不依赖Spring、Micrometer或Actuator。

## Context

现状Tracking provider可重跑业务action，Compare维护ThreadLocal/static history；`tfi-all`又缓存或fallback构造provider。
把Spring bean注册进Core JVM级Registry会与`FREEZE_AT_FIRST_RESOLUTION`及多ApplicationContext冲突。Compare本体还包含
大量auto-configuration、metrics和health代码，模块边界与运行时owner不一致。

## Decision

### CMP_G4：显式batch scope

- final `TrackingExecutor`是唯一action-sequencing owner；provider SPI只负责
  `begin(List<Target>, CompareOptions)`并返回线程封闭的`TrackingBatchScope`。
- 所有target/name/options/action在provider或action执行前一次性校验。输入非法时抛typed input exception，action执行0次。
- 参数合法后，普通非fatal Tracking基础设施成功或失败时action恰好执行1次；业务异常原样传播且不重试。
  fatal错误原样传播，发生在action前时不承诺action执行。
- baseline阶段的全部target共享第一份fresh phase ledger，after+diff共享第二份；target切换不能重置budget，业务action wall time不计入。
- scope只允许一次capture，close逆序且幂等。普通close故障被规范化且不覆盖业务primary，fatal遵循Java try-with-resources
  suppressed语义。
- Compare不保留current-thread baseline、session static map或全局changes查询；Tracking scope不拥有Core Session/Task终态。

### CMP_G5：Core Registry唯一Provider owner

- Core [ADR-007](ADR-007-TFI-Provider-Selection-And-Mutation.md)继续独占candidate、priority、selection、freeze、epoch与trust owner。
- `tfi-all`、Compare SPI adapter和static facade无selected cache、无fallback provider实例图、无独立ServiceLoader选择状态。
- 首次resolution后的register/unregister/load mutation异常原样传播；adapter不得catch `Throwable`吞掉freeze或配置错误。
- built-in Comparison/Tracking provider共享一个static-final immutable default runtime；Spring runtime不进入Core Registry。

### CMP_G6：纯内核、新Starter、外置Ops

- `tfi-compare`生产依赖白名单为`tfi-flow-core`、SLF4J、JDK和provided Lombok，不含Spring、Micrometer、Actuator、Jackson或Caffeine。
- 新建`tfi-compare-spring-starter`，显式拥有properties、auto-configuration、context-local `ComparePolicy/Runtime/Engine`、
  Tracking integration和projection beans；不依赖宿主component scan。
- starter禁止调用Core Registry的`register/unregister/loadProviders/clearAll`，因此Registry预冻结、多个context并存或关闭一个context
  都不会改变其他runtime。
- static `TFI`继续走Core Registry的JVM default provider；注入式Spring `CompareOperations`只使用当前context runtime。
  两条入口的配置差异属于显式behavior contract，不伪装成同一全局实例。
- `tfi-ops-spring`拥有metrics、health、endpoint与observed decorator；Compare内核不发布metrics SPI或自动resource degradation。
- Flow starter只增加0/1 `TfiTaskDeepTrackingDelegate`最小hook；Compare starter条件实现该hook，不新增第二个AOP/advice。
- `@TfiTrack`按exact manifest删除；替代路径为`@TfiTask(deepTracking=true)`或显式`TrackingExecutor`。
- canonical projection、masking、config alias和质量门禁的派生G6合同由ADR-014持有；CMP_G6 machine status只在本ADR出现。

## Consequences

- Spring注入入口与static TFI可以使用不同immutable runtime，但都不拥有共享mutation或第二Provider Registry。
- 旧Compare auto-config、metrics split package、Ops对Compare内部tracking类的依赖及`tfi-all` facade必须按同Wave消费者矩阵迁移。
- starter创建前，root reactor、Boot3 imports、configuration metadata、context tests与直接消费者都属于同一纵向切片。
- 自动degradation scheduler/config/API按manifest删除；Ops只能观测明确结果，不能在Compare内部替调用方修改options。

## Non-goals

- 不修改Core ADR-006/007，不用`clearAll()`作为Spring context生命周期钩子。
- 不提供跨context runtime热切换、全局Spring provider注册、第二套tracking advice或session history store。
- 本ADR不定义projection wire与CI实现，相关合同见ADR-014。

## Rollback

Tracking Wave回滚必须同时撤销executor/scope、provider adapter和所有消费者，且保持action exactly-once。
Starter extraction回滚只能恢复一个context-local composition owner，不能把Spring bean注册进Core Registry。
Provider回滚不得恢复facade cache/fallback；任何分支变化必须通过新ADR同步修订CMP_G4/G5/G6。

## Verification

1. 表驱动测试覆盖参数非法、begin/capture/close普通失败、业务异常、fatal、single/multi-target和两份phase ledger。
2. Provider contract tests覆盖priority、ServiceLoader、freeze、null、typed exception及所有facade观察同一Core selected identity。
3. context tests覆盖无component scan、custom runtime、Registry预冻结、两个context顺序/并行并存和关闭其中一个。
4. ArchUnit/Enforcer验证Compare生产依赖白名单；owner搜索证明Spring/Micrometer/Actuator/ApplicationContext不在纯内核。
5. Flow integration验证sampling/condition/stage后只调用一次delegate，业务异常路径不capture/publish且action不重跑。

## Links

- [Compare 当前架构 SSOT](../../tfi-compare/docs/design-doc.md)
- [ADR-006](ADR-006-TFI-Context-And-Async-Ownership.md)
- [ADR-007](ADR-007-TFI-Provider-Selection-And-Mutation.md)
- [ADR-011](ADR-011-Compare-Compatibility-And-Result-Truth.md)
- [ADR-012](ADR-012-Compare-Kernel-And-Collection-Semantics.md)
- [ADR-014](ADR-014-Compare-Projection-Config-And-Quality.md)

## Implementation Evidence

- [当前架构 SSOT](../../tfi-compare/docs/design-doc.md)记录 Tracking action、Core ProviderRegistry 与 context-local Spring Runtime 边界。
- [TrackingFailureMatrixTests](../../tfi-compare/src/test/java/com/syy/taskflowinsight/tracking/TrackingFailureMatrixTests.java)
  验证 action 次数、provider 故障与异常传播矩阵。
- [CompareContextIsolationTests](../../tfi-compare-spring-starter/src/test/java/com/syy/taskflowinsight/compare/spring/CompareContextIsolationTests.java)
  验证多个 Spring context 与 JVM Registry 相互隔离。
- [ObservedCompareOperationsContractTests](../../tfi-ops-spring/src/test/java/com/syy/taskflowinsight/ops/compare/ObservedCompareOperationsContractTests.java)
  验证 Ops 只装饰当前 Engine 且不改写结果。
- [实施任务索引](../../tfi-compare/docs/ssot-convergence-task/INDEX.md)保存 TRK/SPR/OPS owner、消费者与完成证据。
