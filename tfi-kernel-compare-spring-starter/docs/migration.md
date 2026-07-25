# TFI Kernel Compare Spring Starter 迁移说明

## 1. 适用范围

该 Starter 面向新的 `tfi-kernel`、`tfi-compare-core` 与 `tfi-kernel-compare` 组合。它不是旧
`tfi-flow-spring-starter` 或 `tfi-compare-spring-starter` 的兼容层，也不会加载旧 Facade、tracking delegate 或
property alias。

迁移时只保留一个组合生态。Starter 同时使用 Maven Enforcer、重复 Core class resource 和旧 shell marker 三层 guard；
旧新 artifact 同时存在时以 `KCS_E_1001` 阻止启动，不能依赖 Maven exclusion 或 classpath 顺序伪装成兼容。

## 2. 依赖迁移

应用只声明：

```xml
<dependency>
    <groupId>com.syy</groupId>
    <artifactId>tfi-kernel-compare-spring-starter</artifactId>
    <version>${tfi.version}</version>
</dependency>
```

程序化路径不需要 AOP。准备使用注解集成的应用必须显式声明项目 BOM 管理版本的 optional feature dependency：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

随后开启 `tfi.kernel-compare.aop.enabled=true`。只开启 property 但未添加该依赖会以 `KCS_E_1101` 阻止启动；
integration disabled 与 AOP enabled 的非法组合仍优先返回 `KCS_E_1003`。

E2 已冻结 `@TfiTracked(operation)` 与参数级 `@TfiTrackTarget(value)` 的完整执行合同。operation 最长 63 个 ASCII 字符，
target 最长 64 个，同一方法至少一个 target 且名称唯一；接口和实现同时声明时必须完全一致。合法调用按参数顺序在一个共享
batch 中执行 baseline -> action -> capture，action 全局恰好一次，返回值和 Throwable 都保持原实例。

action 失败会 best-effort 写 `KCOMPARE_ACTION_ERROR_V1`，只保留 method operation 与异常类名；它不包含异常 message、stack、
cause、target 或业务值。默认 RecordPolicy 是 summary-only。AOP Record 是内存观察而非事务审计：加入已有 REQUIRED 事务时，
外层仍可在 capture 后回滚。

## 3. 配置迁移

旧 `tfi.change-tracking.*` key 不会被读取。应按真实 owner 意图迁移：

| 旧 key | 新 key |
|---|---|
| `tfi.change-tracking.enabled` | `tfi.compare.enabled` |
| `tfi.change-tracking.snapshot.max-depth` | `tfi.compare.max-depth` |
| `tfi.change-tracking.snapshot.max-elements` | `tfi.compare.max-elements` |
| `tfi.change-tracking.snapshot.time-budget-ms` | `tfi.compare.deadline`，改用 Duration |
| `tfi.change-tracking.diff.max-changes-per-object` | `tfi.compare.max-change-details` |
| `tfi.change-tracking.value-repr-max-length` | `tfi.compare.max-result-value-chars` |
| `tfi.change-tracking.numeric.float-tolerance` | `tfi.compare.numeric-absolute-tolerance` |
| `tfi.change-tracking.numeric.relative-tolerance` | `tfi.compare.numeric-relative-tolerance` |
| `tfi.change-tracking.datetime.tolerance-ms` | `tfi.compare.temporal-tolerance`，改用 Duration |

Kernel 记录、Compare 执行和 bridge 写入分别使用 `tfi.kernel.enabled`、`tfi.compare.enabled` 与
`tfi.kernel-compare.enabled`。不要把一个旧总开关机械复制到三个新开关。

安全脱敏只允许通过 `tfi.compare.masking.additional-rules` 增加 typed path 规则；配置层没有减弱默认安全 floor
的入口。

## 4. 自定义 Bean 迁移

- 只调整 Kernel 预算或 SPI：使用 K-DEFAULT；Sampler、IdGenerator、KernelClock 每类最多一个 local Bean。
- 已拥有完整 KernelConfig：使用 K-CONFIG，删除额外 Kernel SPI Bean。
- 已拥有完整 KernelRuntime：使用 K-RUNTIME，删除 local Config 与 Kernel SPI Bean。
- 只调整 Compare Policy：使用 C-POLICY。
- 注册 custom strategy/comparator：由应用构造完整 CompareRuntime，使用 C-RUNTIME。

不要声明自定义 Engine、TrackingExecutor、ProjectionFactory 或 Recorder。它们必须从最终 owner 图派生；
重复声明会以 `KCS_E_1002` 阻止启动。

custom KernelRuntime 一经注册即由当前 ApplicationContext 独占，Bean 名可以自定义；Starter 的 retirement 会在 context
关闭时调用其 `close()`。不得把同一个 Runtime 实例复用到多个 context，也不得在 context 关闭后继续使用。

显式 FlowSink 只允许作为 K-DEFAULT 的 local Bean 接入。生产 Sink 必须定义有限的单次 `accept` 时长和全部下游
timeout，且不得从 `accept` 回调重入同一 Runtime 的 `close()`；Starter 不创建线程、队列或额外 shutdown coordinator。

## 5. 上线检查

1. 运行 `dependency:tree`，确认没有旧 Compare/Flow/Starter/Ops/All/Examples。
2. 启动测试验证 context 只有一个 KernelRuntime、retirement 与 CompareRuntime。
3. 对全部迁移 key 做边界绑定测试，确认 Duration 单位和预算交叉约束。
4. 验证关闭 context 后 Runtime 已 terminal close，迟到 Stage/handle 不再触发 Sink。
5. 若启动出现 `KCS_E_1001`，先删除旧制品并检查 shaded/重复 `CompareRuntime.class`，不要调整 classpath 顺序绕过。
6. AOP 开启时验证 eager/lazy/prototype Bean 的 KCS_E_1102 失败时机，且异常不回显 annotation 值。
7. AOP 开启时验证业务所需事务传播；不要把加入外层 REQUIRED 时生成的 Record 当作最终 commit 证明。
8. 用 `perf` profile 在目标 JDK/OS/CPU 重建三场景原始基线，并由 owner 在 KCS-10 接受绝对/相对预算。
9. 程序化 D2 与完整 AOP E2 都只是 RC 候选；最终发布资格仍需完成 KCS-10 的人工与全 Reactor Gate。
