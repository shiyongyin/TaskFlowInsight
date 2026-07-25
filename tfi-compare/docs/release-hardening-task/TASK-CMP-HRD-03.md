# TASK-CMP-HRD-03：建立父子 Context 本层解析与 tracking prerequisite

> **定位**：让 Flow/Compare starter、Tracking 与 Ops 的条件和工厂参数都只使用当前 BeanFactory，并关闭显式 tracking 静默 back-off。
> **deliveryStatus**：`COMPLETE`
> **reviewStatus**：`PASS`
> **依赖**：`TASK-CMP-HRD-02` review PASS
> **后续**：`TASK-CMP-HRD-04`
> **红队来源**：XRT-03、XRT-06、XRT-09、XRT-10、生产 MUST-2

---

## 一、核心（设计时填）

### 背景

`SearchStrategy.CURRENT` 只限制 Boot condition 搜索范围，不限制普通 `@Bean` 工厂参数解析。Spring Framework 会在本层没有唯一
候选时枚举祖先 BeanFactory；父层不同 bean name 的 `@Primary` Runtime/Operations 仍可能进入子层 facade、health 或 tracking。
只修改条件注解会产生“能启动但对象图跨层”的假修复。

此外，`tfi.compare.tracking.enabled=true` 且缺少 optional Flow starter 时，当前 class-level `@ConditionalOnClass` 会让配置静默
back off；starter validator 又通过固定 Ops bean name/FQCN 判断合法 decorator。本卡用当前 BeanFactory 显式 lookup、缺类 guard 和
HRD-02 的 typed decorator contract 同时闭合三项问题。

### 目标（DoD）

- [x] Flow/Compare starter、Tracking/Ops 的 context-owned `ConditionalOnBean/MissingBean` 全部显式 `SearchStrategy.CURRENT`。
- [x] 所有 context-owned 工厂参数通过当前 `ListableBeanFactory` 先证明 local candidate，再做本地类型选择；不借父 bean。
- [x] 父子默认图、不同名 parent primary、不同名 custom Runtime 都保持本层身份和 policy。
- [x] facade、tracking、observed、health 均消费本层 selected Operations/Runtime/Registry；父 meter 不因子调用增长。
- [x] tracking 默认 false 且缺 Flow 可以启动；tracking=true 且缺 Flow 以固定根异常 fail-fast。
- [x] Flow aspect 只取得当前 BeanFactory 的 masker/evaluator/delegate；parent aspect/delegate 不抑制或进入 child。
- [x] tracking=true 但本层 annotation disabled、Flow auto-config excluded 或只有 parent aspect 时，以固定根异常 fail-fast。
- [x] parent/child 中实际调用 Spring proxy 的 `@TfiTask(deepTracking=true)`，业务方法与本层 delegate 均恰好执行一次。
- [x] starter validator 不读取 Ops FQCN 或固定 bean name；一个合法 decorator 必须实现 typed contract 并直接委托本层 Engine。
- [x] Ops owner-local validator 位于 `ObservationConfiguration` 生效范围内，按 `CompareOperationsDecorator` 接口证明 selected
  Operations 是本层 decorator、delegate 是本层 Engine；JDK interface proxy 也必须通过。
- [x] child/parent 任一关闭不破坏仍存活 Context；Core `ProviderRegistry` provider/generation 不变。
- [x] 在任何 starter runtime Java/resource 改动前，以唯一 quality-infra slice 冻结 Checkstyle/PMD predecessor authority；默认 verify
  启用模块自有 JaCoCo、blocking SpotBugs 和
  zero-new static ratchet，不能继承 root `jacoco.skip=true` 或只生成非阻断报告。
- [x] 新增 prerequisite auto-configuration 以包级无参构造器满足 PMD zero-new；不得为新文件预埋 future baseline、suppression、
  dummy state 或伪依赖。
- [x] owning-module focused tests、consumer tests 与 verify 通过，无 no-test 宽容参数。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 本层依赖解析 | 高 | condition 与 factory injection 必须使用同一 scope |
| 对象身份 | 高 | parent primary、custom name 和 observed selection 不能交叉拼图 |
| 显式意图失败 | 高 | tracking=true 缺依赖必须固定 fail-fast |
| owner-local validation | 高 | starter 验证 typed graph，Ops 验证自己的 decorator |
| starter 质量门禁 | 高 | 抽取后的 composition owner 不能落在 Compare strict gate 之外 |
| 新抽象 | 低 | 只复用 typed decorator；不新增 registrar、manager 或 context registry |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 条件查找 | `SearchStrategy.CURRENT` | 与 context-local owner 一致 | 默认 ALL |
| 工厂参数 | local names check + local type selection | 阻止不同名 parent primary 注入 | 普通参数注入、bean-name fallback |
| helper | starter package-private static；Ops private static | 几行真实重复比跨 optional 模块公共 abstraction 更小 | registrar/lookup manager |
| tracking 缺 Flow | 独立无 typed Flow 引用的 prerequisite auto-config | enabled=true 是明确用户意图 | class-level 静默 back-off |
| decorator 验证 | `CompareOperationsDecorator#delegate()` identity | 行为能力比实现名稳定 | Ops FQCN/固定 bean name |
| starter quality | predecessor zero-new baseline + module-owned coverage/SpotBugs | 修改前先冻结现有债务，之后只允许不增加 | 只上传报告、事后重录基线 |

## 二、执行（设计时填）

### 前置准备

1. HRD-02 必须为 `COMPLETE/PASS`，typed decorator contract 已存在。
2. 在修改任何 starter runtime Java 或 `src/main/resources` 前，先完成“质量 authority 首个 slice”。bootstrap 前唯一允许的改动白名单是：
   starter Checkstyle config、starter POM 的 quality-only wiring、baseline enforcer 及其 tests、build configuration contract、baseline/inventory
   provenance；不得夹带 auto-configuration 行为。authority 写入后禁止 `--write-baseline`、`--refresh-baseline` 或再次 add。
3. 先用父层不同名 `@Primary` bean 复现子层错误注入；父子使用相同 bean name 的测试不能作为 RED，因为名称遮蔽会隐藏问题。
4. 记录 Core `ProviderRegistry` provider 与 generation；所有 parent/child 启停前后必须相同。

### 文件与职责

| 动作 | 精确路径/范围 | 职责 |
|---|---|---|
| 修改 | `tfi-compare-spring-starter/src/main/java/com/syy/taskflowinsight/compare/spring/TfiCompareAutoConfiguration.java` | CURRENT conditions、starter local lookup、factory 参数改造 |
| 修改 | `tfi-compare-spring-starter/pom.xml` | 显式启用模块 JaCoCo/check threshold、blocking SpotBugs、verify static ratchet |
| 新增质量配置 | `tfi-compare-spring-starter/config/checkstyle/checkstyle.xml` | module-owned 4-space/120-char Checkstyle authority；禁止 suppression |
| 修改 | `tfi-compare-spring-starter/src/main/java/com/syy/taskflowinsight/compare/spring/TfiCompareTrackingAutoConfiguration.java` | local Runtime/provider/delegate lookup |
| 新增 | `tfi-compare-spring-starter/src/main/java/com/syy/taskflowinsight/compare/spring/TfiCompareTrackingPrerequisiteAutoConfiguration.java` | tracking=true 缺 Flow 的无链接 fail-fast guard |
| 修改 | `tfi-compare-spring-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | guard、base、tracking discovery |
| 修改 | `tfi-compare-spring-starter/src/main/java/com/syy/taskflowinsight/compare/spring/TfiCompareCompositionValidator.java` | typed decorator/local identity，删除 Ops FQCN/name 协议 |
| 修改 | `tfi-flow-spring-starter/src/main/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfiguration.java` | CURRENT conditions 与 local masker/evaluator/delegate provider |
| 扩展测试 | `tfi-flow-spring-starter/src/test/java/com/syy/taskflowinsight/config/ContextMonitoringAutoConfigurationTest.java` | parent bean 不参与 child condition/provider |
| 修改 | `tfi-ops-spring/src/main/java/com/syy/taskflowinsight/ops/compare/CompareObservationAutoConfiguration.java` | CURRENT conditions、Ops local lookup、owner-local validator |
| 修改测试 | `tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareSpringRemovalContractTests.java` | imports exact 合同改为 base、prerequisite、tracking 三项；资源行顺序不承担执行顺序 |
| 新增测试 | `tfi-compare-spring-starter/src/test/java/com/syy/taskflowinsight/compare/spring/CompareStarterBuildConfigurationContractTests.java` | POM 覆盖率/SpotBugs/ratchet 与 baseline module 闭集 |
| 新增集成测试 | `tfi-all/src/test/java/com/syy/taskflowinsight/api/CompareFlowContextIsolationIntegrationTests.java` | parent/child proxy deep tracking 与三种 inactive-aspect 负例 |
| 修改工具/测试 | `scripts/enforce_static_analysis_baseline.py`、`scripts/tests/test_enforce_static_analysis_baseline.py` | 新增一次性、仅允许 missing module 的 `--add-module` structured merge；默认 verify 仍只读 |
| 修改 current facts | `.mvn/static-analysis-baseline.json`、`tfi-compare/src/test/resources/compatibility/current-resource-inventory-v3.json`、`tfi-compare/src/test/java/com/syy/taskflowinsight/compatibility/CompareResourceInventory.java` | 冻结 starter predecessor findings；更新 asset SHA/owner，复用 HRD-01 hardening owner parser |
| 修改 SSOT | `tfi-compare/docs/design-doc.md` §10-11 | 固化 CURRENT condition + local BeanFactory lookup、interface decorator validation 与缺 Flow fail-fast 边界 |
| 扩展测试 | `tfi-compare-spring-starter/src/test/java/com/syy/taskflowinsight/compare/spring/CompareContextIsolationTests.java` | default/different-name-primary/custom/close/Registry identity |
| 扩展测试 | `tfi-compare-spring-starter/src/test/java/com/syy/taskflowinsight/compare/spring/TfiTaskDeepTrackingDelegateContractTests.java` | local tracking、缺 Flow default/fail-fast |
| 扩展测试 | `tfi-compare-spring-starter/src/test/java/com/syy/taskflowinsight/compare/spring/CompareAutoConfigurationContractTests.java` | prerequisite 构造器与 `@Bean` guard 精确签名合同 |
| 扩展测试 | `tfi-ops-spring/src/test/java/com/syy/taskflowinsight/ops/compare/CompareOpsAutoConfigurationContractTests.java` | parent-only back-off、local decorator/health/Registry identity、JDK interface proxy |
| 复用测试 | `tfi-all/src/test/java/com/syy/taskflowinsight/api/CompareOpsBootAutoConfigurationIntegrationTests.java` | 真实 Boot 组合回归 |

### 质量 authority 首个 slice

1. 在任何 runtime Java/resource 改动前，先新增 starter-owned Checkstyle config 并让 POM `configLocation` 精确指向它。配置必须固定
   UTF-8、4-space `Indentation`、120-char `LineLength`，并启用 naming、unused/redundant import、NeedBraces、whitespace、public
   MissingJavadocMethod；禁止 SuppressionFilter/SuppressionXpathFilter、文件/包 exclusion 或 severity ignore。该质量配置与 POM wiring
   是 bootstrap 基础设施，不得混入 context 行为改动。
2. 实现并通过 `--add-module` 的脚本单测。该模式必须要求恰好一个 `--module`、合法 `--owner-task`、非空 `--reason` 与两个 exact
   `--config-file`；目标 module
   已存在、reports 缺失/畸形、baseline 有重复时都失败。它只能结构化地把 missing module 和当前 Checkstyle/PMD fingerprint entries
   合并进已存在 JSON 并保持全局排序，同时新增一条 `moduleBootstraps` provenance；不得改写其他 module entries、`moduleEvidence` 或
   `changes`。
   `moduleBootstraps` 是按 module 排序的唯一数组，每项 schema 固定为
   `module/ownerTask/reason/tools/configFiles`，其中 `tools` 恰有 `checkstyle/pmd`，每项恰有
   `findingCount/fingerprintSha256`。每个 tool 先用现有 `read_module_reports` 聚合为 `(module,path,rule)->count`，再按
   `module|path|rule|count\n` UTF-8/LF 行排序；`findingCount=sum(count)`，`fingerprintSha256=SHA-256(concatenated rows)`。该 hash 不复用
   `moduleEvidence.finding_evidence()` 的 line/column/severity 算法。bootstrap 数值必须与新增到 baseline `tools` 的该 module entries
   按同一算法重算后完全相等；默认 verify 只验证 provenance 与 frozen baseline entries 一致，**不**要求后续 current report 与 bootstrap
   fingerprint 完全相同，因此允许 finding 减少但拒绝新增/increase。`configFiles` 恰为 repo PMD ruleset 与 starter Checkstyle config 的
   repository-relative path/SHA；默认 verify 必须阻断 config missing/hash drift。
   baseline 必须已由 HRD-01 升级为 schema v2，且 `moduleBootstraps=[]`、Compare `configAuthorities` 有效；`--add-module` 只原子追加
   starter bootstrap，不执行 schema migration、不修改 Compare authority。默认 verify 对 v1、缺少 v2 字段或 bootstrap 重复都失败；
   历史 Compare `moduleEvidence/changes/configAuthorities` 原样保留。
3. 先保存 inherited Google config 的 characterization（当前 Checkstyle 452、PMD 141）到 card feedback；随后在仅包含上述完整
   quality-infra 白名单、尚无 runtime 改动的 tree 运行 bootstrap：
   ```bash
   ./mvnw -pl tfi-compare-spring-starter -am -DskipTests install
   ./mvnw -pl tfi-compare-spring-starter clean test checkstyle:checkstyle pmd:pmd spotbugs:spotbugs
   python3 scripts/enforce_static_analysis_baseline.py \
     --module tfi-compare-spring-starter \
     --add-module --owner-task CMP-HRD-03 \
     --config-file config/pmd/ruleset.xml \
     --config-file tfi-compare-spring-starter/config/checkstyle/checkstyle.xml \
     --reason "Freeze extracted Compare starter predecessor findings before context hardening"
   ```
   merge 后 `.mvn/static-analysis-baseline.json.modules` 必须包含 starter 恰一次，starter 的两个 tool entries 和唯一 bootstrap 与本轮
   predecessor reports 双向相等；其他 module 的 JSON subtree byte-for-byte 等价。bootstrap 后禁止再运行 add；随后删除 `target/` 也
   不得再生成 authority。禁止用 2-space 代码、suppression 或事后放宽 baseline 绕过 zero-new。
   baseline 只能记录 bootstrap 时报告中真实存在的 predecessor finding，不得预登记后续新文件或 future fingerprint；本卡唯一新增的
   prerequisite 生产类必须通过后文构造器合同实现自身 PMD zero-new。
   脚本测试必须证明 report -> aggregated entries -> bootstrap hash 可重算，并证明修改 line/column 而 module/path/rule/count 不变时该
   bootstrap hash 不变；它与 moduleEvidence 的 line-level evidence 是两个明确不同的合同。
4. starter POM 设置 `jacoco.skip=false`，显式拥有 `jacoco-maven-plugin:check@verify`。阈值取卡激活时 root effective POM 的现行值，
   只写在 starter POM，不在任务文档或测试重复数值；合同只验证 POM 有唯一、可解析、非零的 module-owned threshold。
5. starter POM 显式设置 SpotBugs `failOnError=true` 并绑定 `spotbugs-check@verify`；使用 `exec-maven-plugin@verify` 只读执行
   `python3 scripts/enforce_static_analysis_baseline.py --module tfi-compare-spring-starter`。verify 参数中出现
   `--add-module`、`--write-baseline` 或 `--refresh-baseline` 必须由合同直接失败。
6. 更新 current resource inventory 中 baseline/enforcer 的 SHA 和 `targetTask=CMP-HRD-03`；`requireOwner` 保持 HRD-01 的
   `release-hardening-task` 唯一命中合同，不扩大到任意 docs；inventory contract 还必须验证 starter bootstrap 的 owner/reason/tool
   schema、与 baseline entries 的聚合一致性，以及 duplicate/missing bootstrap 失败。每个修改 starter source 的 GREEN 都必须先执行
   `clean test checkstyle:checkstyle pmd:pmd` 重新生成两个 report，再立即执行只读 ratchet；最终 verify 也执行。出现新 fingerprint 或 count
   increase 必须修代码，不得 refresh authority。

### 当前层 lookup 的唯一实现

在 `TfiCompareAutoConfiguration` 增加 package-private static 方法；Ops 配置内私有复制相同短逻辑，不创建跨模块 helper：

```java
static <T> T requireLocalBean(ListableBeanFactory beanFactory, Class<T> type) {
    if (beanFactory.getBeanNamesForType(type, true, false).length == 0) {
        throw new IllegalStateException("Missing local bean: " + type.getName());
    }
    return beanFactory.getBean(type);
}
```

先用 `getBeanNamesForType` 证明当前工厂存在候选；随后 `getBean(type)` 保留本层 `@Primary` 语义。本层无候选时不得调用
`getBean(type)` 回退父层；本层多个候选且无 primary 时按 Spring 原异常失败，不新增 first/last-wins。

### 工厂方法精确改造

| 当前方法 | 调整后参数 | 方法内必须取得的 local bean |
|---|---|---|
| `tfiComparePolicy(TfiCompareProperties, Environment)` | `ListableBeanFactory, Environment` | `TfiCompareProperties` |
| `tfiCompareRuntime(ComparePolicy)` | `ListableBeanFactory` | `ComparePolicy` |
| `tfiCompareEngine(CompareRuntime)` | `ListableBeanFactory` | `CompareRuntime` |
| `tfiCompareMaskingPolicy(TfiCompareProperties)` | `ListableBeanFactory` | `TfiCompareProperties` |
| `tfiListDiffFacade(CompareOperations, MaskingPolicy)` | `ListableBeanFactory` | selected `CompareOperations`、`MaskingPolicy` |
| `tfiCompareCompositionValidator(ListableBeanFactory)` | 不变 | validator 自己枚举 local beans |
| `tfiCompareTrackingProvider(CompareRuntime)` | `ListableBeanFactory` | `CompareRuntime` |
| `tfiTaskDeepTrackingDelegate(TrackingProvider, CompareRuntime, ListableBeanFactory)` | `ListableBeanFactory` | `TrackingProvider`、`CompareRuntime` |
| `observedCompareOperations(CompareEngine, MeterRegistry)` | `ListableBeanFactory` | `CompareEngine`、selected `MeterRegistry` |
| `compareHealthIndicator(CompareRuntime, CompareOperations)` | `ListableBeanFactory` | `CompareRuntime`、selected `CompareOperations` |

`Environment` 和 `ListableBeanFactory` 是当前容器基础设施参数，可以继续由框架注入；表中领域 bean 不允许普通参数注入。

### 核心步骤

1. starter 五组 `@ConditionalOnMissingBean`、Tracking 的 runtime exists/delegate missing、Ops 两组 exists 和两个 named missing
   条件全部显式 `search=SearchStrategy.CURRENT`。HRD-02 的 ordering 不得回退。
2. 按上表改造所有工厂方法。local selected Operations/Registry 必须在取得后用于构造 bean，不能只做检查再继续使用原参数。
3. starter composition validator 使用当前 `ListableBeanFactory#getBeansOfType`，固定不变量：
   - 恰有一个 Runtime，其 Policy/Engine 与本层公开 bean 身份一致；恰有一个 safe MaskingPolicy；
   - Operations 只能为一项 Engine，或 Engine + 一个 `CompareOperationsDecorator`；
   - 两项时 decorator `delegate()` 必须与 Engine 同一，selected Operations 必须与 decorator 同一；
   - 不读取 `observedCompareOperations` 名称，不比较 Ops class name，不用反射。
4. 只在 `CompareObservationAutoConfiguration.ObservationConfiguration` 内增加一个
   `SmartInitializingSingleton` owner-local validator，使它与 local Engine + Registry 的 observation 生效条件完全一致；无 local Registry
   时不得创建该 validator，也不得破坏 Ops back-off。validator 通过当前 `ListableBeanFactory` 枚举本层
   `CompareOperationsDecorator`，要求恰有一项；selected `CompareOperations` 与该 interface bean 同一，`delegate()` 与本层 Engine
   同一。禁止按 `ObservedCompareOperations` concrete type、bean name、target class 或 FQCN 查找。错误前缀固定
   `Invalid TFI Compare observation composition: `。
5. 新增 `TfiCompareTrackingPrerequisiteAutoConfiguration`：
   - `@AutoConfiguration(before = TfiCompareTrackingAutoConfiguration.class)`；
   - `@ConditionalOnProperty(prefix="tfi.compare.tracking", name="enabled", havingValue="true")`；
   - `@ConditionalOnMissingClass("com.syy.taskflowinsight.aspect.TfiTaskDeepTrackingDelegate")`；
   - 类和方法签名不得 import/reference 任何 Flow 类型；
   - 类保持 `public`，且必须显式声明唯一的包级无参构造器，源码 token 固定如下；`/* default */` 是 PMD
     `CommentDefaultAccessModifier` 的必要合同，包级可见性使该构造器不等同于编译器生成的 public 默认构造器，从而同时关闭
     `AtLeastOneConstructor` 与 `UnnecessaryConstructor`，Spring 仍负责反射实例化：
     ```java
     /* default */ TfiCompareTrackingPrerequisiteAutoConfiguration() {
         // 由 Spring 负责实例化，包级可见性避免应用代码直接构造。
     }
     ```
   - 唯一 guard 工厂方法必须按以下精确签名注册；不得改为类实现接口、省略 `@Bean`、改名或返回其他类型。
     public Javadoc 必须用中文说明它只把用户显式启用 tracking 但缺 Flow starter 的错误前置到容器启动期：
     ```java
     @Bean
     public SmartInitializingSingleton tfiCompareTrackingPrerequisiteGuard() {
         return () -> {
             throw new IllegalStateException(
                     "tfi.compare.tracking.enabled=true requires tfi-flow-spring-starter");
         };
     }
     ```
   - `CompareAutoConfigurationContractTests` 通过反射证明该类恰有一个无参构造器，且不是 public/protected/private；同时证明恰有
     一个名为 `tfiCompareTrackingPrerequisiteGuard`的 public 无参方法，具有 `@Bean`且返回类型精确为
     `SmartInitializingSingleton`。PMD report + 只读 ratchet 证明新文件没有任何 fingerprint。禁止改成 public 空构造器、
     删除构造器、添加 suppression/dummy member，或为该文件增补 baseline。
6. Flow `ContextMonitoringAutoConfiguration` 的 SafeSpEL、masker、aspect 三个 missing condition 全部使用 CURRENT。aspect 工厂方法只接收
   当前 `ListableBeanFactory`，先 local lookup SafeSpEL/Masker，再用该 factory 的 local
   `getBeanProvider(TfiTaskDeepTrackingDelegate.class)` 构造 aspect；禁止普通领域参数或注入 ancestor-aware ObjectProvider。
7. `TfiCompareTrackingAutoConfiguration` 新增唯一
   `@Bean public SmartInitializingSingleton tfiCompareTrackingAspectGuard(ListableBeanFactory beanFactory)`。singleton callback 要求当前 factory
   恰有一个 local `TfiAnnotationAspect`，否则固定抛出
   `tfi.compare.tracking.enabled=true requires one local active TfiAnnotationAspect`。不得按 parent bean、bean name 或反射 FQCN 放行。
8. imports 固定 `containsExactly` base auto-config、prerequisite guard、typed Tracking auto-config；同步修改
   `CompareSpringRemovalContractTests` 的现有两项 exact 断言。资源行顺序只定义 discovery 闭集，执行顺序以
   `@AutoConfiguration(before=...)` metadata 为准。不得恢复第二个 aspect 或 runtime fallback。
9. `CompareStarterBuildConfigurationContractTests` 必须同时解析 starter POM 与 static baseline，证明 module-owned JaCoCo/SpotBugs/ratchet
   真实绑定 default verify、starter 在 baseline modules 中恰一次、verify 不含任何 baseline 写参数；并解析 module Checkstyle config，
   断言 4-space/120-char 与上述 required checks，拒绝 suppression/exclusion。只存在 report 文件或 workflow upload step 不算门禁。
10. 更新当前 Compare design SSOT，明确 condition search 与普通 factory resolution 是两个独立边界；parent 不得补足 child graph，
   Ops validator 只在 observation config 生效并按 decorator interface 工作，tracking=true 缺 Flow 固定 fail-fast、默认 false 则 back off。

### 必须测试的方法矩阵

| 测试方法 | 必须证明 |
|---|---|
| `parentAndChildDefaultGraphsRemainLocal` | parent depth=3、child depth=7；所有 graph bean 本地存在且身份不同 |
| `differentNamedPrimaryParentBeansCannotEnterChildGraph` | 父层不同名 primary Policy/Runtime/Operations/Masking 不进入子 facade/Engine |
| `differentNamedCustomRuntimesRemainLocal` | `parentRuntime`/`childRuntime` 不同名，Engine 分别由本层 Runtime 导出 |
| `trackingBacksOffWhenRuntimeExistsOnlyInParent` | 子层 provider/delegate 均为零，不借父层 |
| `trackingUsesChildGraphDespitePrimaryParentBeans` | 父 provider 调用数为零，子 policy 上界生效 |
| `trackingDisabledWithoutFlowStarts` | FilteredClassLoader 缺 Flow 且默认 false 时启动成功 |
| `trackingEnabledWithoutFlowFailsFast` | 根异常消息与上述固定文本完全相等 |
| `trackingEnabledWithAnnotationDisabledFailsFast` | Flow class 存在但本层 aspect 未激活，命中 local active-aspect 固定错误 |
| `trackingEnabledWithFlowAutoConfigurationExcludedFailsFast` | 只有 classpath/parent bean 不足以证明本层 advice 生效 |
| `parentAspectAndDelegateCannotEnterChild` | child conditions/provider 只读取 local BeanFactory，parent 调用数为零 |
| `proxiedDeepTrackingUsesExactlyOneLocalDelegate` | parent/child proxy 分别只调用本层 delegate 一次，业务方法不重复执行 |
| `prerequisiteUsesPackagePrivateConstructionBoundary` | guard 恰有一个包级无参构造器；Spring 可实例化，应用侧无 public/protected/private 构造入口 |
| `opsBacksOffWhenPrerequisitesExistOnlyInParent` | parent 有 Runtime/Engine/Registry，child 无 local 时无 observed/health |
| `opsUsesChildDependenciesDespitePrimaryParentBeans` | observed/health 使用子 Runtime/Engine/Registry，父 meter 不增长 |
| `jdkProxyDecoratorIsValidatedByInterface` | JDK proxy 只暴露 `CompareOperationsDecorator`/`CompareOperations` 接口时，validator 仍按接口取得同一 decorator 与 delegate；不得要求 concrete target type |
| `closingEitherContextDoesNotAffectTheOther` | 两种关闭顺序均保持另一层可比较，Core Registry 不变 |

所有 local ownership 断言先使用当前 BeanFactory 的 `containsLocalBean`/`getBeanNamesForType`；普通 `context.getBean(type)`
只能在 local existence 已证明后用于身份比较。

### 验证命令

```bash
python3 -m unittest scripts.tests.test_enforce_static_analysis_baseline

./mvnw -pl tfi-flow-spring-starter,tfi-compare-spring-starter,tfi-ops-spring,tfi-all -am -DskipTests install

./mvnw -pl tfi-flow-spring-starter clean \
  -Dtest=com.syy.taskflowinsight.config.ContextMonitoringAutoConfigurationTest \
  test

./mvnw -pl tfi-compare-spring-starter clean \
  -Dtest=com.syy.taskflowinsight.compare.spring.CompareContextIsolationTests,com.syy.taskflowinsight.compare.spring.TfiTaskDeepTrackingDelegateContractTests,com.syy.taskflowinsight.compare.spring.CompareAutoConfigurationContractTests,com.syy.taskflowinsight.compare.spring.CompareConfigurationContractTests,com.syy.taskflowinsight.compare.spring.CompareStarterBuildConfigurationContractTests \
  test checkstyle:checkstyle pmd:pmd

python3 scripts/enforce_static_analysis_baseline.py --module tfi-compare-spring-starter

./mvnw -pl tfi-ops-spring clean \
  -Dtest=com.syy.taskflowinsight.ops.compare.CompareOpsAutoConfigurationContractTests,com.syy.taskflowinsight.ops.compare.CompareOpsHealthContractTests \
  test

./mvnw -pl tfi-all \
  -Dtest=com.syy.taskflowinsight.api.CompareFlowContextIsolationIntegrationTests,com.syy.taskflowinsight.api.CompareOpsBootAutoConfigurationIntegrationTests,com.syy.taskflowinsight.api.StaticAndSpringCompareContractTests,com.syy.taskflowinsight.api.CompareOpsConsumerContractTests \
  test

./mvnw -pl tfi-flow-spring-starter,tfi-compare-spring-starter,tfi-ops-spring,tfi-all -am verify
```

### 审核检查点

- [ ] CP-1：Flow/Compare/Ops context-owned conditions 为 CURRENT；所有领域 factory/provider 参数按表改为 local lookup。
- [ ] CP-2：不同名 parent primary 和不同名 custom Runtime 测试通过，不依赖同名遮蔽。
- [ ] CP-3：tracking default/missing、enabled/missing 和 enabled/inactive-local-aspect 三组控制均成立，错误消息固定。
- [ ] CP-4：starter/ops validator 只使用 typed/local identity，无 Ops FQCN、固定 observed bean name 或跨层 lookup。
- [ ] CP-5：父/子关闭隔离、meter 隔离与 Core Registry generation 不变均有直接证据。
- [ ] CP-6：starter predecessor static authority 先于生产改动；default verify 的 coverage/SpotBugs/zero-new ratchet 均由 starter POM 拥有，
  baseline 未事后 refresh。
- [ ] CP-7：prerequisite 构造器反射合同通过；其 PMD finding 为零，baseline 中没有该新文件或任何 future fingerprint。
- [ ] CP-8：parent/child 实际 proxy deep tracking 只使用本层 aspect/delegate，业务方法恰执行一次。

### 禁止范围与回滚

本卡不修改 Compare 结果语义、alias/profile、Masking floor 或 KernelDiff 合同；不新增 registrar、context manager、global holder、
ObjectProvider 延迟 fallback 或 bean-name lookup。不得把 starter quality gate 延后到 HRD-05、只上传报告或在生产改动后首次建 baseline。
回滚必须同时回退 conditions、local lookup、guard、validators、imports、父子测试与本卡 quality changes；baseline 回滚必须恢复整个
owner batch，不能只删 module name 留下 fingerprint entries。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只修 context ownership、显式 prerequisite 和 composition identity。
- [x] **认知负担**：使用 Spring 原生 BeanFactory 与 HRD-02 typed contract，无新生命周期框架。
- [x] **比例失调**：不同名 primary 和真实失败测试占主体，不是机械改注解。
- [x] **ROI**：少量 lookup/guard 关闭 hierarchy、silent no-op 与 FQCN 脆弱性。
- [x] **洁癖检测**：不重写全部 auto-config，不新增统一 lookup manager。
- [x] **局部 vs 全局**：starter、Tracking、Ops 分别验证自己 owner，跨模块只共享最小 typed fact。
- [x] **过度设计**：没有 scope、registrar、proxy framework 或未来 KernelDiff hook。

**结论**：设计通过；CURRENT conditions + local factory lookup + typed owner validation 是最小充分边界。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|---|---|---|---|
| context ownership | 按表改造三个 owner 并补完整矩阵 | Compare/Tracking/Flow/Ops 均改为 CURRENT + local lookup，任务矩阵已落地 | 普通工厂参数解析不会继承 condition 的搜索边界 |
| quality predecessor | 在 runtime 改动前冻结 inherited reports | Checkstyle 452、PMD 141、SpotBugs 0；starter tests 24/24 | 2026-07-16 fresh clean characterization，baseline SHA 仍为 HRD-01 authority |
| Flow delegate provider | 直接使用当前 BeanFactory 的 provider | 先冻结当前 BeanFactory 的 local bean names，再提供只枚举该闭集的 ObjectProvider | Spring Beans 6.2.10 的 provider stream 实测会包含 ancestor；直接使用不能满足本层不变量 |

### 检查点结果

- [x] CP-1：Compare/Tracking/Flow/Ops 的条件与领域依赖均使用 CURRENT + local lookup；Flow focused `13/13`。
- [x] CP-2：starter isolation focused 覆盖不同名 parent primary/custom Runtime，精确组 `39/39`。
- [x] CP-3：tracking default、缺 Flow、annotation disabled、Flow auto-config excluded 与 parent-only aspect 均有固定合同。
- [x] CP-4：starter/ops validator 只依赖 `CompareOperationsDecorator` 与本层对象身份；Ops focused `10/10`。
- [x] CP-5：两种关闭顺序、父 meter 不增长及 Core Registry provider/generation 不变均由隔离测试证明。
- [x] CP-6：starter predecessor 为 Checkstyle 452/PMD 141/SpotBugs 0；最终 ratchet `118 <= 141`，verify 门禁通过。
- [x] CP-7：prerequisite 包级构造器反射合同通过，新文件无 PMD fingerprint，baseline 未 refresh/add。
- [x] CP-8：`tfi-all` 实际 parent/child Spring proxy deep tracking 消费测试 `4/4` 通过。

### 需要人类确认

- 当前无；实现未放宽任务卡安全边界、发布判定或质量 authority。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25 /25 | parent/child local identity、固定 fail-fast 与实际 proxy 合同全部通过 |
| 完整性 | 25 /25 | focused、consumer、baseline 单测及 7 模块 reactor verify 全部通过 |
| 可维护性 | 25 /25 | 复用 typed decorator；无新 manager/registry，改动生产类均低于 500 行 |
| 风险控制 | 25 /25 | module-owned JaCoCo/SpotBugs/ratchet 生效，未放宽或重录 authority |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| HIGH | HRD03-REV-01 | 父层 primary `TfiSecurityProperties` 曾进入子 evaluator/masker | `ContextMonitoringAutoConfiguration.java:65` | 已改为 local lookup并以 parent/child 回归关闭 |
| INFO | HRD03-REV-02 | Spring 6.2 provider stream 会枚举 ancestor | `ContextMonitoringAutoConfiguration.java:123` | 冻结 local names 后提供闭集 provider |
| PASS | HRD03-REV-03 | 最终复审 0 MUST / 0 HIGH；Javadoc 本卡范围无 violation | 本卡全部改动 | 通过 |
