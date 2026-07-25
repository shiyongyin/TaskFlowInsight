# TASK-PRV-01：建立类型化 Provider 优先级契约并固化选择语义（P1）

> **定位**：先把五类 Provider 的优先级与仲裁语义变成可测试契约，为后续 Registry 状态收敛提供不变基线。
> **状态**：完成（2026-07-11；100/100；4.0 typed-only contract）
> **审核状态**：审核通过（2026-07-11；focused 35/35，五类 typed contract、来源/优先级/FIFO/Throwable 语义保持）
> **依赖**：前置为 `0A` 绿色契约护栏；不消费 `G5`/`G6`；后续为 `TASK-PRV-02`

---

## 一、核心（设计时填写）

### 背景

当前 `ProviderRegistry` 对 core 的 `FlowProvider`、`ExportProvider` 使用类型判断，对未知 Provider
还会按同名方法反射猜测优先级，而 compare 侧三类 SPI 没有共同的类型化优先级父契约。选择顺序还必须保留
“注册来源优先于 `ServiceLoader`、来源内优先级降序、同优先级 FIFO”的历史行为。本卡只统一优先级
契约和选择证据，不迁移 Registry 状态、不改变 Provider trust、不删除 facade cache。

旧卡要求五个 SPI 保留各自的默认方法，并为未知对象保留 reflective priority，理由是 3.x compatibility
window。accepted G1 与用户均要求直接采用 4.0 最新契约，因此本卡改为一个 `PrioritizedProvider` owner：
子 SPI 不复制默认实现，未知泛型 Provider 只有显式实现该契约才参与数值优先级。

### 范围

**纳入范围**：

- 新增 `PrioritizedProvider`，让五类已知 SPI 继承它，并删除各 SPI 重复的 `priority()` 默认实现。
- 让注册排序与 `ServiceLoader` 排序共享 `priorityOf(Object)`。
- 删除按同名 public `priority()` 猜测能力的反射分支；未知泛型 Provider 未实现契约时按 `0`/FIFO。
- 固化来源优先级、来源内降序和稳定 FIFO。

**不纳入范围**：

- 不创建 `ProviderRegistryEngine`，不实现 runtime freeze、epoch、ClassLoader cache 或 selected cache。
- 不消费或写入 `G5_STATUS`、`G5_DECISION`、`G6_STATUS`、`G6_DECISION`。
- 不修改五个生产 `META-INF/services` 文件，不删除任何 TFI/TfiFlow cache。
- 不实现后续容量限制；但不得引入妨碍后续
  `MAX_PROVIDER_TYPES = 64`、`MAX_REGISTERED_PROVIDERS_PER_TYPE = 64`、
  `MAX_CACHED_LOADERS_PER_TYPE = 8`、`MAX_DISCOVERED_PROVIDERS_PER_SCAN = 64` 的结构。

### 文件清单

**新增**：

- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/PrioritizedProvider.java`
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderSelectionContractTests.java`
- `tfi-compare/src/test/java/com/syy/taskflowinsight/spi/ProviderPriorityContractTests.java`

**修改**：

- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/FlowProvider.java`
- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/ExportProvider.java`
- `tfi-compare/src/main/java/com/syy/taskflowinsight/spi/ComparisonProvider.java`
- `tfi-compare/src/main/java/com/syy/taskflowinsight/spi/TrackingProvider.java`
- `tfi-compare/src/main/java/com/syy/taskflowinsight/spi/RenderProvider.java`
- `tfi-flow-core/src/main/java/com/syy/taskflowinsight/spi/ProviderRegistry.java`
- `tfi-flow-core/src/test/resources/compatibility/breaking-changes-v4.json` 与 Core `pom.xml`（仅当 japicmp 将子接口声明迁移识别为 ABI 删除）
- `tfi-flow-core/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryTest.java`
- `tfi-all/src/test/java/com/syy/taskflowinsight/spi/ProviderRegistryChaosTests.java`

### 类型与方法签名

```java
public interface PrioritizedProvider {
    default int priority() {
        return 0;
    }
}
```

- `public interface FlowProvider extends PrioritizedProvider`
- `public interface ExportProvider extends PrioritizedProvider`
- `public interface ComparisonProvider extends PrioritizedProvider`
- `public interface TrackingProvider extends PrioritizedProvider`
- `public interface RenderProvider extends PrioritizedProvider`
- Registry 内部统一使用 `private static int priorityOf(Object provider)`。
- 未实现 `PrioritizedProvider` 的未知泛型 Provider 优先级固定为 `0`；是否参与仲裁由类型契约决定，
  不再由同名方法巧合决定。
- 现有 public 签名保持不变：
  `public static <T> void register(Class<T> providerType, T provider)`、
  `public static <T> boolean unregister(Class<T> providerType, T provider)`、
  `public static <T> T lookup(Class<T> providerType)`。

### 必守不变量

1. **来源优先于数值优先级**：任何有效注册候选都先于 `ServiceLoader`；注册候选即使
   `priority() == -1_000`，仍击败默认 `ServiceLoader` 候选。
2. **来源内降序**：只在同一来源内按 `priority()` 从高到低排序。
3. **稳定 FIFO**：同来源、同优先级时，按注册/发现顺序选择，后加入者不得抢占先加入者。
4. **异常降级**：类型化 `priority()` 抛出任意 `Throwable` 时返回 `0`；日志只记录类名和
   异常类型，不读取或渲染用户控制的异常消息。
5. **类型边界**：所有数值优先级只来自 `PrioritizedProvider`；Registry 不反射 `priority()`。
6. **模块边界**：core 测试只能引用 Flow/Export；三类 compare SPI 的共同契约由 `tfi-compare` 测试拥有。

### 目标（DoD）

- [x] 新增上述 `PrioritizedProvider`，五类 SPI 均可由
  `PrioritizedProvider.class.isAssignableFrom(...)` 证明继承关系。
- [x] 五类 SPI 不再复制 `priority()` 默认实现，默认值和语义只由 `PrioritizedProvider` 定义。
- [x] 注册候选严格先于 `ServiceLoader`，来源内优先级降序且同优先级 FIFO。
- [x] `priority()` 抛 `Throwable` 时选择流程继续并按 `0` 仲裁。
- [x] 未实现 `PrioritizedProvider` 的未知泛型 Provider 即使有同名 public 方法也按 `0`/FIFO，显式实现契约后才按数值选择。
- [x] core 没有新增 `tfi-compare` 或 `tfi-all` 依赖。
- [x] 聚焦测试与包含 `tfi-examples` 的 downstream 编译门禁通过。

### 重点分布

| 方向 | 权重 | 说明 |
|---|---:|---|
| 选择契约 | 高 | 后续所有 cache/epoch/trust 变更都必须保持此仲裁顺序 |
| 合约清晰度 | 高 | 类型显式声明能力，不再靠反射同名方法推断 |
| 模块边界 | 中 | core 不得导入 compare 类型 |
| 容量与 epoch | 低 | 本卡不实现，只避免提前制造冲突结构 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|---|---|---|---|
| 共同契约 | 新增 `PrioritizedProvider` | 消除五类已知 SPI 的反射分叉 | 继续逐类型 `instanceof` |
| 泛型 Provider | register/lookup 继续接受；未实现 typed contract 时 priority=0 | 保留泛型 API，同时让能力边界可读、可静态检查 | 按同名方法反射猜测能力 |
| 排序模型 | 来源优先，来源内 priority/FIFO | 保持历史外部行为 | 把所有来源混成一个全局 priority 队列 |
| 异常处理 | 捕获 `Throwable` 并降为 `0` | 防止第三方 Provider 破坏扫描/排序 | 让异常逃逸或只捕获 `Exception` |

### 失败与保护矩阵

| 触发条件 | 预期行为 | 保护证据 |
|---|---|---|
| 类型化 `priority()` 抛出 `AssertionError` | 不向调用者传播，按 `0` 继续 | `ProviderSelectionContractTests` |
| 未知类型有同名 public `priority()` 但未实现契约 | 忽略同名方法并按 `0`/FIFO | generic contract 测试类型 |
| 注册候选为负优先级 | 仍短路 `ServiceLoader` | `registeredNegativePriorityStillBeatsServiceLoaderDefault` |
| 注册候选同优先级 | 返回先注册实例 | `equalRegisteredPriorityIsFifo` |

---

## 二、执行（设计时填写）

### 前置准备

1. 确认 `0A` 绿色基线存在；本卡不得通过修改 ADR Gate token 解除任何门禁。
2. 记录以上文件的 pre-existing diff；只精确暂存本卡路径，禁止 `git add -A`。
3. 五个生产 `META-INF/services` 文件仅保护、不修改。

### 核心步骤

1. **先写 core 选择测试**

   在 `ProviderSelectionContractTests` 覆盖：

   - `coreProviderTypesUseTheTypedPriorityContract`
   - `registeredNegativePriorityStillBeatsServiceLoaderDefault`
   - `equalRegisteredPriorityIsFifo`
   - 类型化 `priority()` 抛 `AssertionError` 时不抛出。
   - unknown generic 类型即使有 public `priority()` 也不获得隐式优先级；显式实现 typed contract 后才参与排序。

2. **写 downstream SPI 所有权测试**

   `ProviderPriorityContractTests#compareArtifactProvidersUseTheCorePriorityContract` 只验证
   `ComparisonProvider`、`TrackingProvider`、`RenderProvider` 可赋值给 `PrioritizedProvider`。

3. **执行红灯**

```bash
./mvnw -pl tfi-flow-core,tfi-compare,tfi-all -am \
  -Dtest=ProviderSelectionContractTests,ProviderPriorityContractTests,ProviderRegistryTest,ProviderRegistryChaosTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

   预期仅因 `PrioritizedProvider` 尚不存在而编译失败；core 测试不得导入 compare 类型。

4. **实现类型与统一优先级函数**

   - 新建 `PrioritizedProvider`。
   - 五类 SPI `extends PrioritizedProvider`，删除各自重复的默认实现。
   - 注册排序和 `ServiceLoader` 排序只调用 `priorityOf(Object)`。
   - `priorityOf` 只识别 `PrioritizedProvider` 并捕获 `Throwable`；其他对象返回 `0`。
   - 删除 `java.lang.reflect.Method` 和任何 `getMethod("priority")` 路径。
   - 若 japicmp 报告 Core 两个子接口方法声明迁移，则按 G1 登记 exact manifest/POM，不使用通配排除。

5. **执行绿灯与 downstream 门禁**

```bash
./mvnw -pl tfi-flow-core,tfi-compare,tfi-all -am \
  -Dtest=ProviderSelectionContractTests,ProviderPriorityContractTests,ProviderRegistryTest,ProviderRegistryChaosTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

### 审核检查点

- [x] CP-1：五类已知 SPI 均走类型化路径，Registry 中不存在 priority 反射路径。
- [x] CP-2：注册来源先于 `ServiceLoader`，不能用更高的 service priority 反转来源顺序。
- [x] CP-3：相同 priority 的排序稳定，FIFO 测试使用实例同一性断言。
- [x] CP-4：`Throwable` 保护不调用 `Throwable#getMessage()`。
- [x] CP-5：core 生产/测试均无 compare 类型导入。
- [x] CP-6：未修改 Gate token、生产 service resource、facade cache 或公共签名。

### 回滚边界

- 本卡是独立可回滚的 P1 批次；在 `TASK-PRV-02` 开始前，可整体撤销新增接口、五类继承、
  Registry 统一优先级函数和两组新测试。
- 一旦 `TASK-PRV-02`/后续卡依赖 `PrioritizedProvider`，不得只回滚接口或单个 SPI；必须按
  `TASK-PRV-06` → `TASK-PRV-05` → `TASK-PRV-04` → `TASK-PRV-03` → `TASK-PRV-02` → 本卡逆序回滚。
- 不以修改已有测试期望来“回滚”选择契约；回滚必须恢复整批代码与测试。

---

## 三、自省（设计完成后、实现前填写）

- **目标偏离**：通过。本卡只建立 typed priority 与历史选择契约，没有提前实现 mutation/trust/cache。
- **认知负担**：通过。只增加一个最小父接口与一个内部函数，不增加第二 Registry API。
- **比例失调**：通过。主要篇幅用于来源优先、priority、FIFO、异常与泛型兼容。
- **ROI**：通过。以单一 typed contract 消除五类已知 SPI 的反射分叉和重复默认实现，为后续四卡提供稳定基线。
- **洁癖检测**：通过。不重命名现有 SPI，不重写完整 Registry，不清理无关注释。
- **局部与全局**：通过。core/compare 测试所有权与 master 模块边界一致。
- **过度设计**：通过。未提前引入 epoch、lease、cache key 或容量容器。
- **门禁一致性**：通过。本卡不代替用户接受 `G5` 或 `G6`。

**结论**：设计已按用户确认的 4.0 最新契约修订，可按 TDD 直接实施。

---

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 | 影响/处置 |
|---|---|---|---|---|
| 4.0 契约路线 | 旧卡保留五个重复默认方法与反射兼容 | 只保留一个 `PrioritizedProvider`，删除反射猜测 | 用户已确认 direct-removal/V2-only，不保留 3.x 窗口 | 任务卡在实现前完成修订；无 legacy facade/ledger |
| japicmp 处置 | 若声明迁移被识别为删除则登记 exact exclusion | 无新增不兼容项，现有 manifest/POM 未改 | 继承后的方法仍属于公共 API | `-Papi-compat verify` 通过 |

### 检查点结果

- [x] CP-1：通过。五类 SPI 继承 `PrioritizedProvider`；`ProviderRegistry.java:328` 仅按类型读取优先级，
  production source 无 `getMethod("priority")` 或 `getPriority` 残留。
- [x] CP-2：通过。`ProviderRegistry.java:156` 先完整消费注册来源，只有无有效候选才在
  `ProviderRegistry.java:169` 进入 `ServiceLoader`；负优先级契约测试通过。
- [x] CP-3：通过。Core 与 tfi-all 两组 FIFO 测试均使用 `isSameAs`/`assertSame` 验证实例同一性。
- [x] CP-4：通过。`ProviderRegistry.java:334` 捕获 `Throwable` 后只读取 Provider 类名与异常类名；
  `MessageSensitiveError` 令 `getMessage()`/`toString()` 抛错，focused test 仍通过。
- [x] CP-5：通过。Core main/test 对 `ComparisonProvider`、`TrackingProvider`、`RenderProvider` 的搜索为零；
  下游继承关系由 `ProviderPriorityContractTests` 在 compare 模块验证。
- [x] CP-6：通过。G5/G6 exact token、五个生产 service resource、facade cache 与三个 Registry public
  签名均未由本卡修改；japicmp 与七模块 consumer compile 通过。

---

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|---|---:|---|
| 正确性 | 25/25 | focused 35/35；来源、priority、FIFO、Throwable 与泛型边界均有契约测试 |
| 完整性 | 25/25 | 7/7 DoD、6/6 CP；Core clean verify 573/573；七模块 package 7/7 |
| 可维护性 | 25/25 | 一个类型化能力 owner；删除五份重复默认实现和 Registry 反射分支；中文 why 注释完整 |
| 风险控制 | 25/25 | japicmp、Checkstyle 0、SpotBugs 0、JaCoCo 与模块边界搜索均通过 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|---|---|---|---|---|
| INFO | PRV01-R1 | `ProviderRegistry` 为既有 468 行静态状态类 | `ProviderRegistry.java:29` | 低于 500 行阈值；本卡不提前拆 `ProviderRegistryEngine`，留给 PRV-02/04 按所有权迁移 |
| INFO | PRV01-R2 | Javadoc 扫描器报告既有 `Default*Provider` override 缺注释 | Core/Compare `Default*Provider` | 本卡新增/触达 public contract 无 blocking 缺口；不做跨卡注释清扫 |

### 最终结论

新增 `PrioritizedProvider` 作为五类 SPI 的唯一优先级契约，并让 Registry 只按该类型仲裁；注册来源优先、
来源内降序、同优先级 FIFO 与失败降级语义均已固化。fresh 证据为 focused 35/35、Core clean verify
573/573、Checkstyle 0、SpotBugs 0、JaCoCo、japicmp 和七模块 package 7/7。Code Review 无遗留
MUST/SHOULD；允许进入 `TASK-PRV-02`。

## 六、完成审核

### 审核结论

**审核通过。** 五类 Provider 仍共享唯一类型化优先级契约，来源优先、来源内降序、FIFO、Throwable
降级和未知泛型边界均由 current tests 直接证明。

### Fresh 证据（2026-07-11）

- 原卡 focused reactor 命令 35/35：Core 20、Compare 1、tfi-all 14，零失败、错误或跳过。
- 五类 SPI 当前均 `extends PrioritizedProvider`，只有父接口定义一次 `default int priority()`。
- Core/Compare Provider production source 无 `getMethod("priority")`、`java.lang.reflect.Method` 或
  `getPriority` 路径；Core main/test 无三类 Compare SPI 导入。
- negative priority 仍由注册来源击败 ServiceLoader；同优先级使用实例同一性证明 FIFO；
  message-sensitive `Throwable` 测试证明失败降级不读取异常消息。
- 同一当前工作树的七模块 package、Core verify 与 japicmp 已在本轮通过。

### 时态消歧

- 卡片实施时 `priorityOf` 位于 `ProviderRegistry`；后继 PRV-02 已将状态与仲裁实现迁入
  `ProviderRegistryEngine`，facade 只委托。持续不变量是 typed priority 和选择语义，不是私有方法的文件位置。
- 历史 Core 573 测试数已演进为本轮 611；测试总数不是公开契约。
