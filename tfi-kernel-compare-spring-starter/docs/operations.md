# TFI Kernel Compare Spring Starter 运维说明

## 1. 启动失败

`KCS_E_1001` 表示 classpath 同时存在不兼容制品。先运行 Starter 的 `dependency:tree`，删除旧 `tfi-compare`、Flow Core、
旧 Starter、Ops、All 或 Examples，再检查 shaded/重复的
`com/syy/taskflowinsight/tracking/compare/CompareRuntime.class`。不要通过调整依赖顺序掩盖冲突。

`KCS_E_1002` 表示当前 ApplicationContext 的 owner 或派生 Bean 图不唯一。检查 local KernelConfig/Runtime、
ComparePolicy/Runtime、Kernel SPI 与派生 Bean，不要用 `@Primary` 让歧义继续运行。

`KCS_E_1003` 表示配置绑定或 Core 边界校验失败。按错误中的 canonical property path 修正配置；错误不会输出
业务值。

`KCS_E_1101` 表示 AOP feature dependency、固定 Advisor 或当前 context 的 Spring auto-proxy creator 不完整。确认应用显式
添加 BOM 管理版本的 `spring-boot-starter-aop`，没有排除 Boot AOP auto-configuration，并且只在
`tfi.kernel-compare.enabled=true` 时开启 AOP；不要通过手工替换保留名 Advisor 绕过校验。

`KCS_E_1102` 表示 tracked 方法的静态元数据或动态 target 非法。检查 public 方法上的 operation grammar、参数 target
位置/唯一性、接口与实现双声明一致性以及调用实参非 null。异常故意不回显 annotation 值，需结合声明类、方法和参数索引定位。

## 2. Context 关闭

每个 context 都有独立 `tfiKernelRuntimeRetirement`。Spring 通过 dependent-bean 关系先调用 retirement，再销毁实际 local
KernelRuntime 和全部 local FlowSink。retirement 调用 terminal `KernelRuntime.close()`，等待已经登记的同步 Sink publish
返回并拒绝新发布；随后 Spring 对 Runtime 的重复 close 是幂等路径。

close 不等待或取消普通业务 action，不跨线程清理 ThreadLocal，也不替应用停止 executor。应用仍须停止接收新业务、
按自身超时策略等待 executor，并保证 Stage 使用 try-with-resources 正常收束。不要注册 JVM shutdown hook 再次协调
Starter 内部 Bean。

## 3. FlowSink 生产合同

默认没有 FlowSink，也没有日志、文件、HTTP、MQ 或数据库出境。声明 local FlowSink 表示应用主动接入，并须由数据/安全
owner 批准字段、目的地、权限、加密、留存和事故响应。

每个生产 Sink 必须满足：

- `accept` 同步且有有限完成时间，所有下游调用配置明确 timeout；
- 不从 `accept` 回调重入同一 Runtime 的 `close()`；
- destroy 后再次 accept 视为生命周期错误并立即失败；
- 自己拥有 retention/cleanup，不依赖 Starter flush、后台队列或重试。

## 4. 关闭后仍有 Sink 调用

该现象属于生命周期合同违反。确认 retirement Bean 存在且依赖实际 local Runtime Bean 名与每个 local Sink；检查
custom Runtime 是否被多个 context 复用，以及应用 executor 是否在 context 外继续提交任务。再核对 Sink 的最大调用时长、
下游 timeout 和跨线程 join/wait；Starter 只保证 Runtime 的同步 publish barrier，不能中断任意宿主回调。

## 5. AOP 记录与事务观察

AOP 默认关闭。开启后，每个合法 invocation 只执行一次业务 action；有预算时按注解参数顺序形成 summary Record。Kernel
disabled、Stage 已无剩余编码预算或单个 Record 被预算拒绝都不得阻止或替换业务返回。

`KCOMPARE_ACTION_ERROR_V1` 只表示业务 action（包括其内部事务 advisor 的 commit）抛出了 Throwable。data 只包含 schema
版本、method operation 和异常类名，故意不含 message、stack、cause、target 或业务值。该 code 不得用于 Compare、projection
或 record 设施故障；排障时应以原业务异常为 primary，fatal 记录/close 故障只可能出现在其 suppressed 列表中。

固定 Advisor 顺序通常让新事务在成功 commit 后 capture；但加入已有 REQUIRED 事务时，capture 会早于外层 completion，外层
之后仍可能 rollback。因此 AOP Record 只陈述当时的内存观察，不证明最终数据库提交。需要提交事实的系统必须由事务资源或
outbox 等应用级机制提供权威证据，不能把本 Starter Record 当作审计提交回执。

## 6. 性能基线复现

使用 Starter `perf` profile 生成 `target/aop-jmh-results.json` 和 `target/aop-jmh-baseline.properties`。基线固定为 direct、
1 target summary-only 和 8 targets summary-only，并记录 JDK/JVM、OS/CPU 与 JMH fork/warmup/measurement 参数。结果标记为
`INITIAL_NO_THRESHOLD`；环境、JVM 或 revision 变化后应重新生成，且不得把一次本机数据直接解释为已接受的生产预算。

用于防止已完成优化回退的 allocation 硬门禁使用另一组固定 TYPICAL/CHANGED workload：

```bash
./mvnw -pl tfi-kernel-compare-spring-starter -am \
  -Pperf -Dtfi.perf.enabled=true -Dtfi.perf.strict=true \
  -Dtest=CompareAllocationBenchmarkRunnerTest,CompareAllocationGatePolicyTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=CompareAllocationPerfGateIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

它生成 `target/compare-allocation-jmh-results.json` 和 `target/compare-allocation-gate.properties`。三个预算为：

| 场景 | 最大分配 |
|---|---:|
| `compareOnly` | 45,000 B/op |
| `oneTargetSummaryOnly` | 55,000 B/op |
| `eightTargetsSummaryOnly` | 380,000 B/op |

这些上限只保护相同 JDK 21、相同固定 workload 的分配回归，不是业务接口 QPS、P95/P99 或堆容量承诺。门禁按
2 forks x 5 measurement 的最大 `B/op` 裁决，mean 只作诊断。失败时先检查 JMH JSON 的 `gc.alloc.rate.norm`、properties
中的 `meanBytesPerOp` / `maxObservedBytesPerOp` / `maxBytesPerOp` 和 fork 的 CodeSource；不要直接调高阈值。只有确认是
对象布局或工具链造成的可解释变化，并取得 owner 审查后，才能连同基线证据修改预算。

修改 Compare Core 的缓存、快照、路径或 canonical 热路径时，还必须在仓库根目录执行：

```bash
python3 -m unittest scripts/tests/test_check_compare_shared_sources.py
python3 scripts/check_compare_shared_sources.py
```

第二条命令只检查 `config/compare-shared-source-contract.tsv` 中显式列出的文件；它不允许自动复制整个模块，也不改变
`tfi-compare-core` 与完整版 `tfi-compare` 互不依赖、互斥消费的架构。
