# TASK-CMP-HRD-02：修复真实 Boot observation 顺序并建立 typed decorator 合同

> **定位**：让普通 Boot discovery 在 Registry 就绪后创建唯一 observed Operations，并用 typed contract 取代跨模块实现猜测。
> **deliveryStatus**：`COMPLETE`
> **reviewStatus**：`PASS`
> **依赖**：`TASK-CMP-HRD-01` review PASS
> **后续**：`TASK-CMP-HRD-03`
> **红队来源**：XRT-02、XRT-06、XRT-10 的跨模块 decorator 协议部分

---

## 一、核心（设计时填）

### 背景

`CompareObservationAutoConfiguration` 当前只排在 Compare starter 之后。Boot 3.5 的 Registry auto-configuration 尚未创建
`MeterRegistry` 时，nested condition 已求值并永久 back off；应用最终有 Registry 和 health，但 primary
`CompareOperations` 仍是裸 Engine。现有 Ops 测试手工注册 Registry，无法复现真实顺序。

完整 Boot discovery 需要 Compare starter + Ops，不能放进明确禁止 Compare starter 依赖的 `tfi-ops-spring`。本卡把真实组合测试
放入已聚合这些制品的 `tfi-all`，Ops 继续只持有 owner-local/back-off 测试。同时新增最小 typed decorator 合同，供 HRD-03
移除 starter 对 Ops bean name/FQCN 的反射判断。

### 目标（DoD）

- [x] Ops auto-configuration 同时排在 Compare starter 与 `CompositeMeterRegistryAutoConfiguration` 之后。
- [x] 排序使用两个固定字符串 FQCN；Ops POM 不新增 Compare starter 依赖或 Enforcer 豁免。
- [x] `CompareOperationsDecorator` 只增加 `CompareOperations delegate()`，不增加 Registry、lookup、KernelDiff 或运行时构建职责。
- [x] 新 public interface 在 HRD-01 的 4-space/120-char Checkstyle + static ratchet 下产生 0 new/increased finding；本卡禁止 static refresh。
- [x] `ObservedCompareOperations` 实现 typed decorator，`delegate()` 精确返回构造时的最终 Engine。
- [x] `tfi-all` 新增真实 `@EnableAutoConfiguration` 测试，不手工声明任何 `MeterRegistry` bean。
- [x] 最终 context 中 primary Operations 为唯一 observed decorator，基础 Engine 只有一份且只委托一次。
- [x] 一次受 policy 上界约束的 direct compare 产生四类 meter，计数与低基数 tag key 精确匹配 canonical result。
- [x] 无 Registry 与缺 Compare artifact 两类 back-off 仍由 Ops owning tests 证明。
- [x] focused tests 和 `tfi-ops-spring,tfi-all` verify 通过，不依靠 no-test 宽容参数。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 真实 Boot 顺序 | 高 | 条件只求值一次，排序是 XRT-02 根因修复 |
| typed delegate identity | 高 | 后续 validator 不能依赖 bean name 或 Ops FQCN |
| meter 行为 | 高 | primary 与四类 meter 必须在同一真实启动图成立 |
| optional 边界 | 高 | Ops 缺 Compare、宿主缺 Registry 时仍安全 back off |
| 生产改动 | 低 | 限于一条 ordering、一个最小 contract 和已有 decorator |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| Registry 时序 | `afterName` 排在 Composite Registry 后 | Boot condition 在解析期一次求值 | ObjectProvider 延迟重包装 |
| 真实测试 owner | `tfi-all` | 已聚合 starter/ops/actuator，且不破坏 Ops optional 边界 | Ops test dependency + Enforcer 豁免 |
| 测试模型 | nested Boot config + `@EnableAutoConfiguration` | 覆盖真实 discovery 和 Registry 创建链 | `AutoConfigurations.of`、手工 Registry |
| 跨模块身份 | `CompareOperationsDecorator#delegate()` | 最小 typed 事实可证明只包装最终 Engine | bean name、反射 FQCN、通用 wrapper manager |

## 二、执行（设计时填）

### 前置准备

1. HRD-01 必须为 `COMPLETE/PASS`。
2. 先新增 `tfi-all` 真实 Boot 测试并取得 RED：最终 context 有 `MeterRegistry` 和 health，但 primary Operations 仍为 Engine。
3. 不得为取得 RED 修改 Ops POM、手工放置 `SimpleMeterRegistry` 或直接 import 两个 auto-configuration 类。

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 新增 API | `tfi-compare/src/main/java/com/syy/taskflowinsight/api/CompareOperationsDecorator.java` | 跨模块最小 delegate identity contract |
| 修改 | `tfi-ops-spring/src/main/java/com/syy/taskflowinsight/ops/compare/ObservedCompareOperations.java` | 实现 contract 并返回 exact Engine delegate |
| 修改 | `tfi-ops-spring/src/main/java/com/syy/taskflowinsight/ops/compare/CompareObservationAutoConfiguration.java` | 增加 Composite Registry ordering |
| 新增测试 | `tfi-all/src/test/java/com/syy/taskflowinsight/api/CompareOpsBootAutoConfigurationIntegrationTests.java` | 真实 discovery、primary、delegate 与四类 meter |
| 扩展测试 | `tfi-ops-spring/src/test/java/com/syy/taskflowinsight/ops/compare/ObservedCompareOperationsContractTests.java` | delegate identity、一次委托、异常和 metrics failure |
| 复用测试 | `tfi-ops-spring/src/test/java/com/syy/taskflowinsight/ops/compare/CompareOpsAutoConfigurationContractTests.java` | 无 Registry、FilteredClassLoader 缺 Compare、imports owner |
| 修改 SSOT | `tfi-compare/docs/design-doc.md` §10-11 | 记录 typed decorator 只服务一次 delegate identity，不是通用 wrapper/extension chain |
| 原则上不改 | `tfi-ops-spring/src/main/java/com/syy/taskflowinsight/ops/compare/CompareMetrics.java` | 当前 result projection 正确；测试发现真实缺陷时退回设计评审 |

### 核心步骤

1. 新增 public `CompareOperationsDecorator extends CompareOperations`：
   - 只声明 `CompareOperations delegate()`；
   - Javadoc 明确它只供 composition validation 证明一个 wrapper 委托同一执行图；
   - 禁止默认实现、递归链、unwrap utility、Registry 或 runtime lookup。
   这是“单实现接口”的刻意例外：唯一理由是 Compare starter 与 optional Ops 之间需要 typed identity，不能反向依赖实现类；
   若 HRD-03 不再需要跨模块验证，该接口应随本卡一起删除，而不是保留为未来扩展点。
   interface 与 `delegate()` 都必须有解释上述边界的中文 Javadoc，使用 4-space/120-char module config，不能用 suppression、2-space
   缩进或 baseline refresh 消除新 finding。
2. `ObservedCompareOperations` 实现该接口，`delegate()` 返回 private final `engine`。不得暴露 metrics 或增加 setter。
3. `CompareObservationAutoConfiguration` 固定为：
   ```java
   @AutoConfiguration(afterName = {
       "com.syy.taskflowinsight.compare.spring.TfiCompareAutoConfiguration",
       "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"
   })
   ```
   两个 FQCN 均使用字符串，禁止新增 Compare starter 编译依赖。
4. `CompareOpsBootAutoConfigurationIntegrationTests` 固定使用 nested
   `@SpringBootConfiguration(proxyBeanMethods=false)` + `@EnableAutoConfiguration`，并由
   `ApplicationContextRunner.withUserConfiguration(TestApplication.class)` 启动。禁止：
   - `AutoConfigurations.of(TfiCompareAutoConfiguration.class, CompareObservationAutoConfiguration.class)`；
   - 任何 `@Bean MeterRegistry` 或 `SimpleMeterRegistry`；
   - 只断言 bean 存在而不执行 compare。
5. 正向测试精确断言：一个 `MeterRegistry`、一个 `CompareHealthIndicator`、一个 `CompareEngine`、一个
   `ObservedCompareOperations`、两个 `CompareOperations`；按类型选择结果与 observed 同一；
   `((CompareOperationsDecorator) selected).delegate()` 与 Engine 同一。
6. 测试内定义两字段 record，before/after 两字段均不同。选项必须写为：
   ```java
   CompareRuntime runtime = context.getBean(CompareRuntime.class);
   CompareOptions options = CompareOptions.builder(runtime.policy())
           .maxChangeDetails(1)
           .build();
   CompareResult result = context.getBean(CompareOperations.class).compare(before, after, options);
   ```
   先断言 `result.getCompletion()==CompareCompletion.PARTIAL`、limitations 包含
   `CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED`、`omittedChanges()>0`，再核对 meter。
7. meter 验证固定为：request counter=1、duration timer count=1；每个 result issue 对应一个 issue meter；
   `kind=change` 的 omitted counter 等于 `result.getDiagnostics().omittedChanges()`。tag-key 集合必须精确为：
   - request/duration：`rootAlgorithmId,outcome,completion`；
   - issue：common + `kind,code,stage`；
   - omitted：common + `kind`。
8. Ops owning tests继续验证无 Registry 时无 observed、有 health；FilteredClassLoader 缺 Compare 时无 observed/health；
   `AutoConfiguration.imports` 仍只登记 `CompareObservationAutoConfiguration`。不把这些测试搬到 `tfi-all`。
9. 更新当前 Compare design SSOT：`CompareOperationsDecorator` 只允许 optional Ops 的单层 decorator，`delegate()` 必须返回当前
   Context 的唯一 Engine；明确禁止递归 wrapper chain、通用 unwrap API、Registry/lookup 或未来 KernelDiff 扩展职责。
10. 生成 fresh Compare Checkstyle/PMD report 后运行 normal
    `python3 scripts/enforce_static_analysis_baseline.py --module tfi-compare`；必须 0 new/increased。禁止
    `--refresh-baseline`、`--write-baseline` 或修改 static authority。

### 验证命令

```bash
./mvnw -pl tfi-all -am -DskipTests install

./mvnw -pl tfi-ops-spring clean \
  -Dtest=com.syy.taskflowinsight.ops.compare.CompareOpsAutoConfigurationContractTests,com.syy.taskflowinsight.ops.compare.ObservedCompareOperationsContractTests,com.syy.taskflowinsight.ops.compare.CompareOpsHealthContractTests \
  test

./mvnw -pl tfi-all clean \
  -Dtest=com.syy.taskflowinsight.api.CompareOpsBootAutoConfigurationIntegrationTests,com.syy.taskflowinsight.api.CompareOpsConsumerContractTests \
  test

./mvnw -pl tfi-ops-spring,tfi-all -am verify
python3 scripts/enforce_static_analysis_baseline.py --module tfi-compare
```

### 审核检查点

- [x] CP-1：真实 Boot test 通过 discovery 获得 Registry，测试配置没有手工 Registry bean 或 direct auto-config import。
- [x] CP-2：primary 为 observed，typed delegate 为唯一 Engine，一次调用只委托一次。
- [x] CP-3：四类 meter 的计数/tag keys 与 canonical result 精确匹配。
- [x] CP-4：无 Registry、无 Compare 两个 back-off 控制仍通过。
- [x] CP-5：Ops POM 没有 Compare starter 依赖/豁免；无延迟 lookup、global holder 或 double observation。
- [x] CP-6：Compare normal static ratchet 0-new/0-increase，baseline/config/inventory 无本卡 diff。

### 禁止范围与回滚

本卡不处理父子 Context/local injection、不修改配置 alias、不把 `CompareOperations` 扩为 KernelDiff 合同。
回滚必须同时回退 ordering、typed decorator contract、实现与两模块测试；不得保留反射 FQCN 作为第二验证协议。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只修 Boot 求值顺序和 decorator identity。
- [x] **认知负担**：一个单方法 contract 取代跨模块 bean name/FQCN 约定。
- [x] **比例失调**：真实启动和 meter 行为占主体，生产 ordering 改动保持最小。
- [x] **ROI**：一处 metadata 修复恢复默认观测，一条 typed fact 支撑后续 owner-local validator。
- [x] **洁癖检测**：不重写 metrics/decorator，不统一所有 Ops 配置。
- [x] **局部 vs 全局**：真实组合测试放到正确 consumer owner，不污染 Ops optional 依赖。
- [x] **过度设计**：未增加 wrapper manager、listener、holder 或递归 decorator chain。

**结论**：设计通过；ordering + typed delegate 是关闭 XRT-02 并为 XRT-10 收口的最小边界。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| 实现偏差 | ordering、typed contract、真实 Boot 合同 | 与计划一致，无生产范围偏差 | RED 证明真实 discovery 缺 observed bean；仅增加 ordering 后 GREEN |
| 验证环境 | Reactor 一次通过 | 首次命中上游 IDE 遗留未解决编译字节码 | `tfi-flow-core clean` 后 Maven/Javac focused 6/6；无需源码改动，随后 Reactor 全绿 |

### 检查点结果

- [x] CP-1：`tfi-all` 真实 Boot discovery 由 `@EnableAutoConfiguration` 创建唯一 Registry；测试无手工 Registry 或 direct auto-config import。
- [x] CP-2：primary 与 `ObservedCompareOperations` 同一，typed `delegate()` 与唯一 Engine 同一；Ops once 合同通过。
- [x] CP-3：policy-bounded compare 为 PARTIAL，limitation/omitted 与 request、duration、issue、omitted 四类 meter 精确对应。
- [x] CP-4：无 Registry 时保留 health/裸 Engine，过滤 Compare artifact 时 observed/health 均 back off。
- [x] CP-5：Ops POM 仍只 optional 依赖纯 Compare，Enforcer 禁止 starter/all/examples；无 lookup、holder 或双重观测。
- [x] CP-6：fresh Compare reports 后 normal static ratchet `3771/3771`，未 refresh baseline/config/inventory。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | 真实 Boot RED/GREEN、typed identity、once delegate 与四类 meter 合同均通过 |
| 完整性 | 25 /25 | Ops 13/13、All 2/2、Compare API shape 5/5 及七模块 Reactor verify 全绿 |
| 可维护性 | 25 /25 | 单方法 typed contract 取代 bean name/FQCN 猜测，未引入 wrapper manager 或第二执行图 |
| 风险控制 | 25 /25 | optional back-off、异常透传、metrics failure 隔离、依赖 Enforcer 与 static ratchet 均闭合 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| PASS | HRD02-REV-00 | 本地 findings-first 代码、测试、Javadoc 与架构审查为 0 MUST / 0 HIGH | 本卡全部实现文件 | 无需修复 |

### 最终验证

| Gate | 结果 |
|---|---|
| `tfi-all -am -DskipTests install` | PASS；7 个 Reactor 模块成功 |
| Ops focused | PASS；13/13 |
| All focused | PASS；2/2，含真实 Boot discovery |
| Compare API shape | PASS；5/5 |
| `tfi-ops-spring,tfi-all -am verify` | PASS；7 个 Reactor 模块全部成功 |
| Compare static ratchet | PASS；`3771/3771` |
| Code/architecture review | PASS；0 MUST / 0 HIGH |

### 需要人类确认

无。发布状态仍保持 `NOT_EVALUATED`；按严格串行依赖只开放 `CMP-HRD-03`。
