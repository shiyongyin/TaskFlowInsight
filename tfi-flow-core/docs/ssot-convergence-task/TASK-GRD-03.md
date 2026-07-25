# TASK-GRD-03：保护 ServiceLoader 运行时契约

> **定位**：把五类 Provider 的 JAR service resource 与无参可构造性纳入精确契约测试。
> **状态**：完成（2026-07-10）
> **审核状态**：审核通过（2026-07-11；实际 JAR、精确资源数、类型与公共构造器均 fresh 验证）
> **依赖**：`TASK-GRD-01` | 后续 `TASK-PRV-01` 至 `TASK-PRV-06`
> **架构来源**：contract guardrails Task 3 / `0A.1`

---

## 一、核心（设计时填）

### 背景

Provider 兼容性不仅是 class ABI，还包括 `META-INF/services` 文件、默认实现数量和公共无参构造器。当前 core 只打包 Flow/Export 两类资源，而聚合 artifact 需要五类默认 Provider；普通 japicmp 无法证明这些运行时契约。

### 目标（DoD）

- [x] core JAR 精确包含 Flow/Export 两个 service resource，各自只有一个正确默认实现。
- [x] `TaskFlowInsight` JAR 精确包含 Flow、Export、Comparison、Tracking、Render 五类默认声明。
- [x] 每个默认实现可通过公共无参构造器实例化，且类型匹配。
- [x] 测试从实际构建 JAR 读取资源，不只读取源码目录。
- [x] 手工注册优先于 ServiceLoader 的选择语义仍由 Provider 专项卡负责，本卡未修改选择算法。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 打包资源 | 高 | service 文件是运行时 ABI |
| 精确数量 | 高 | 重复默认声明会改变选择结果 |
| 职责边界 | 中 | 本卡只刻画，不重写 Registry |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 证据对象 | 构建后的 JAR | 验证真实发布物 | 只检查 `src/main/resources` |
| 断言强度 | exact-one + exact class | 防重复与错误实现 | 仅断言非空 |

### 跨卡不变量

- 默认 Provider service resource 不得由 facade fallback 替代。
- Provider trust/whitelist 行为等待 G6 与 `TASK-PRV-03`，本卡不提前选择分支。

## 二、执行（设计时填）

### 前置准备

确认五类 Provider 接口及默认实现的发布坐标；Jackson 等无关依赖不得进入生产范围。

### 核心步骤

1. 创建 `CoreServiceLoaderContractTests`，构建并读取 core JAR 的两个 service 文件。
2. 创建或补齐 `AllProviderServiceLoaderContractTests`，读取 `TaskFlowInsight` JAR 的五类 service 文件。
3. 对每个声明断言：文件存在、无空行/重复、exact-one、类可加载、实现接口、公共无参构造成功。
4. 运行：

```bash
./mvnw -pl tfi-flow-core,tfi-all -am \
  -Dtest=CoreServiceLoaderContractTests,AllProviderServiceLoaderContractTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### 审核检查点

- [ ] CP-1：测试检查的是 JAR 内容而非源码路径。
- [ ] CP-2：core 恰好两个、all 恰好五个默认 resource 契约。
- [ ] CP-3：默认实现无反射可访问性绕过。
- [ ] CP-4：未在本卡修改 Registry 选择算法。

### 回滚边界

本卡仅新增 characterization tests/resource 修正。若现有发布物与预期不一致，先记录兼容差异，不在测试中放宽 exact 断言掩盖问题。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只保护 runtime resource。
- [x] **认知负担**：一套参数化断言覆盖五类接口。
- [x] **比例失调**：重点在真实 JAR 与 exact 数量。
- [x] **ROI**：补足 ABI 工具无法覆盖的 ServiceLoader 契约。
- [x] **洁癖检测**：不重构 Provider 代码。
- [x] **局部 vs 全局**：同时覆盖 core 与聚合 artifact。
- [x] **过度设计**：不引入自定义 service parser 框架。

**结论**：设计通过并已按顺序实施。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| 聚合资源现状 | `TaskFlowInsight` JAR 已有五类声明 | clean 基线中没有任何 service resource | 在聚合模块新增五份单行资源，未复制实现类 |
| 测试阶段 JAR | 任务卡命令直接运行 `test` | Maven 默认在 test 之后的 package 阶段才生成 JAR | core/all 在 `process-test-classes` 绑定标准 jar goal，确保测试读取本次构建发布物 |
| 断言方式 | 计划示例使用运行时 `ServiceLoader` | 直接枚举 `JarFile` resource，再加载/实例化声明类 | 防止依赖 classpath 上的资源掩盖当前 JAR 漏打包 |

### 检查点结果

- [x] CP-1：两份测试均从 `${basedir}/target/*.jar` 使用 `JarFile` 读取，clean 红测证明不存在 JAR 时失败。
- [x] CP-2：`jar tf` 复核 core 恰好 2 个、all 恰好 5 个 service resource；每份声明 exact-one。
- [x] CP-3：使用 `Class#getConstructor()` 获取 public 无参构造器并直接 `newInstance()`，未调用 `setAccessible`。
- [x] CP-4：本卡只增加测试阶段打包、聚合资源与 characterization tests，未修改 Registry 选择算法。

### 验证证据

- RED：clean 后运行任务卡命令，`CoreServiceLoaderContractTests` 因找不到实际 core JAR 失败。
- GREEN：任务卡六模块 reactor 命令退出 0；core/all 各 1 test，0 failure/error/skipped。
- 发布物复核：core JAR 仅 Flow/Export；`TaskFlowInsight` JAR 仅 Flow/Export/Comparison/Tracking/Render。
- GRD-07 回归补证：原自定义 `build-contract-test-jar` 与生命周期 `default-jar` 在 `package` 阶段重复挂载
  主产物；已将 core/all 的提前打包 execution 改为覆盖 `default-jar` phase。修正后 Core API verify、
  All-in-One API reactor 和六模块 package 均退出 0，测试仍在 test 前读取同名主 JAR。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | 测试直接读取真实 JAR，资源、类型和构造器均精确断言 |
| 完整性 | 25 /25 | 5/5 DoD、4/4 CP 完成 |
| 可维护性 | 23 /25 | 两模块各自持有小型契约测试；为模块边界保留少量重复 helper |
| 风险控制 | 24 /25 | clean 红测防陈旧 JAR 假绿；未改变 Provider 选择行为 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| 无 MUST | GRD03-REV-01 | 测试证据对象为本次构建 JAR，不依赖源码资源或 classpath 偶然合并 | 两份 contract tests | 通过 |
| 无 MUST | GRD03-REV-02 | 聚合 JAR resource 精确为五类且每类单声明；构造器无反射越权 | `tfi-all/src/main/resources/META-INF/services` | 通过 |
| MUST | GRD03-REV-03 | 自定义 jar execution 导致 package 阶段重复挂载主产物 | core/all POM | 已覆盖 `default-jar` phase 并通过三条 package/verify 回归 |

## 六、完成审核（2026-07-11）

### 审核结论

**审核通过**。当前测试仍直接读取本次构建的 core/all 主 JAR，发布资源数量、声明类、接口类型与公共无参
构造器均为精确断言；提前构建主 JAR 的 execution 仍覆盖 `default-jar`，没有恢复重复主产物挂载。

### 当前直接证据

- 任务卡 reactor 命令 fresh `BUILD SUCCESS`：core/all 各执行 1 个契约测试，均 0 failure/error/skipped。
- core `META-INF/services` 精确为 Flow/Export 两类；all 精确为 Flow/Export/Comparison/Tracking/Render 五类，
  每份资源只有一个默认实现声明。
- 两份测试均使用 `JarFile` 读取 `target/*.jar`，并以 `Class#getConstructor()` 获取 public 无参构造器；
  未使用 `setAccessible`。
- core/all POM 的 `maven-jar-plugin` execution id 均为 `default-jar`，phase 为
  `process-test-classes`，与卡片记录的重复挂载修复一致。

## 六、完成审核

### 审核结论

**审核通过。** 实际 JAR 的 ServiceLoader 资源数量、类型及公共构造器契约均通过。
