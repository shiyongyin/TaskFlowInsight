# TASK-GRD-07：在 Core 变更时编译全部下游消费者

> **定位**：用源码 reactor compile 捕获 split-package、package-private 跨 JAR 与资源/schema 兼容问题。
> **状态**：完成
> **审核状态**：审核通过（2026-07-11；标准目标模块加 `-am` 七模块 package 7/7 复验成功）
> **依赖**：`TASK-GRD-01` 至 `TASK-GRD-05`；若 G1 已接受则同时依赖 `TASK-GRD-06`
> **架构来源**：contract guardrails Task 7 / `0A.2`；已修正原计划遗漏的 `tfi-examples`

---

## 一、核心（设计时填）

### 背景

`tfi-all` 跨 JAR 访问 core package-private 类型，examples 也直接导入 Context/Executor 类型；单独 core ABI diff 捕获不到这些源码耦合。原计划的“全部消费者”命令漏掉 `tfi-examples`，本卡将其纳入唯一全链路门禁。

### 目标（DoD）

- [x] core CI 在 core 变化时编译 core、starter、compare、ops、all、examples 及其依赖。
- [x] 命令使用源码 reactor `-am`，不依赖陈旧本地 artifact。
- [x] `tfi-all` 的 `TFI` ABI profile 同时阻断二进制/源码破坏且不忽略 missing classes。
- [x] core/all 相关 workflow path filters 覆盖 core、compare、`.mvn`、根 POM 与 workflow 文件。
- [x] compatibility job 没有 `continue-on-error`。

### 重点分布

| 方向 | 权重 | 说明 |
|------|------|------|
| 全消费者覆盖 | 高 | `tfi-examples` 不得再次漏出 |
| 源码边界 | 高 | 捕获 package-private/split-package 隐式契约 |
| CI 触发 | 中 | 门禁必须在 core 改动时实际运行 |

### 关键决策

| 决策点 | 选择 | 理由 | 否定的备选 |
|--------|------|------|-----------|
| 下游集合 | core,starter,compare,ops,all,examples | examples 直接调用 core API | 沿用遗漏 examples 的旧命令 |
| 检查方式 | reactor source package | 防陈旧本地 artifact 掩盖问题 | 逐模块引用本地安装包 |

### 跨卡不变量

- `tfi-all` artifactId 保持 `TaskFlowInsight`。
- 该门禁是所有跨模块卡的全局 DoD，任何任务卡不得删减模块集合。

## 二、执行（设计时填）

### 前置准备

Guardrail 1-5 绿色；如执行 ledger test，G1/GRD-06 也必须绿色。

### 核心步骤

1. 修改 `.github/workflows/tfi-flow-core-ci.yml`，增加完整消费者编译命令。
2. 修改 `.github/workflows/tfi-all-ci.yml` path filters 与兼容 job。
3. 加固 `tfi-all/pom.xml` 的 `api-compat` profile。
4. 使用唯一标准命令：

```bash
./mvnw -pl \
  tfi-flow-core,tfi-flow-spring-starter,tfi-compare,tfi-ops-spring,tfi-all,tfi-examples \
  -am -DskipTests package
```

5. 同时运行 core/all API profile 与资源/schema tests。

### 审核检查点

- [ ] CP-1：命令明确包含 `tfi-examples`。
- [ ] CP-2：CI path filter 在 core 变化时触发兼容 job。
- [ ] CP-3：TFI profile 的 exact include 保持 `com.syy.taskflowinsight.api.TFI`。
- [ ] CP-4：无 soft failure 和 missing-class 宽松项。

### 回滚边界

若某下游暴露真实不兼容，停止对应运行时卡并修订契约；禁止把失败模块从集合中删除来恢复绿色。

## 三、自省（设计完成后、实现前填）

- [x] **目标偏离**：只建立跨模块编译门禁。
- [x] **认知负担**：一条权威模块列表。
- [x] **比例失调**：全消费者和 CI 触发为高权重。
- [x] **ROI**：覆盖 ABI 工具看不到的真实源码耦合。
- [x] **洁癖检测**：不借机重构 examples。
- [x] **局部 vs 全局**：所有跨模块卡共用。
- [x] **过度设计**：未建立重复的模块矩阵脚本。

**结论**：设计通过，并显式修正 `tfi-examples` 漏项。

## 四、反馈（实现过程中回填）

### 偏差记录

| 偏差点 | 计划 | 实际 | 原因 |
|--------|------|------|------|
| GRD-03 提前打包 | contract tests 在 test 前生成主 JAR | 原自定义 execution 与默认 jar lifecycle 在 package 重复挂载；改为覆盖 `default-jar` phase | 六模块 package 首次运行暴露真实构建回归，不能通过跳过 package 规避 |
| API job 与覆盖率 | `-DskipTests` 运行 strict japicmp | tfi-all 仍以空数据执行 JaCoCo gate；仅在 `api-compat` profile 设置 `jacoco.skip=true` | 覆盖率已有独立 build-and-test job，API job 不应因刻意跳过测试而产生假失败 |
| 机器清单 Checkstyle | 常量 manifest 一项一行 | 对该精确资源增加 Checkstyle pre-execution exclusion | properties owner/type/value 行不可折叠；Java 和其他资源的 120 列规则未放宽 |

### 实施与验证记录

- `./mvnw -pl tfi-flow-core -Papi-compat verify`：退出 0；Core `488` tests，0 failure/error/skipped，
  Checkstyle、SpotBugs、JaCoCo 与 japicmp 完成。
- `./mvnw -pl tfi-all -am -Papi-compat verify -DskipTests`：退出 0；`TFI` exact include 同时启用
  binary/source break 与 missing-class 阻断。
- 六模块标准命令（含 `tfi-examples`、`-am`、`-DskipTests package`）：退出 0。
- `ruby` YAML parser：两份 workflow 均解析成功。
- 外部基线残余风险不变：命令使用用户批准的本机 `3.0.0` artifact；clean CI 在正式发布前仍会因无法从
  Maven Central 解析 baseline 而失败，该失败不得 soft-fail。

### 检查点结果

- [x] CP-1：workflow 与本地标准命令均精确包含 `tfi-examples`，六模块 reactor package 退出 0。
- [x] CP-2：core/all 的 push、pull_request filters 均包含 core、compare、`.mvn`、根 POM 和两份 workflow。
- [x] CP-3：`includes` 仅保留 `com.syy.taskflowinsight.api.TFI`，未改变 `TaskFlowInsight` artifactId。
- [x] CP-4：API compatibility job 无 `continue-on-error`；source/binary break 为 true，
  `ignoreMissingClasses=false`，无 broad exclude。

## 五、总结（完成后回填）

### 评分

| 维度 | 分数 | 证据 |
|------|------|------|
| 正确性 | 25 /25 | 三条权威 API/源码 reactor 门禁均退出 0；workflow YAML 可解析 |
| 完整性 | 25 /25 | 六模块含 examples、双 workflow filters、strict TFI profile 和硬失败 job 全覆盖 |
| 可维护性 | 25 /25 | workflow 只维护一条权威模块清单；POM 注释解释 JaCoCo/JAR 技术取舍原因 |
| 风险控制 | 25 /25 | 未删失败模块、未 soft-fail、未放宽 ABI；发现的三个构建阻断均根因修复 |

### Code-Review 回填

| 级别 | 编号 | 描述 | 文件:行号 | 处置 |
|------|------|------|-----------|------|
| MUST | GRD07-R1 | GRD-03 自定义 jar execution 破坏 package lifecycle | core/all POM | 已改为覆盖 `default-jar` 并回归通过 |
| MUST | GRD07-R2 | API profile 在 skipTests 时用空 JaCoCo 数据失败 | tfi-all POM | 仅 api-compat profile 跳过 JaCoCo |
| MUST | GRD07-R3 | 常量 manifest 被通用 LineLength 阻断 | core Checkstyle | 精确排除单一机器资源，不放宽 Java 规则 |
| MUST | - | 修正后复审未发现未处置阻断问题 | workflows / POM / Checkstyle | 通过 |

## 六、完成审核（2026-07-11，待复验）

### 最终结论

**审核通过**。初审 testCompile 阻断已解除；标准命令当前覆盖全部目标模块与 parent，七模块均成功，
且没有用 `maven.test.skip` 绕过 testCompile。

### 当前直接证据

- Core CI 的消费者命令精确包含 core、starter、compare、ops、all、examples 和 `-am`。
- Core/all workflow 的 push/pull_request filters 均覆盖 core、compare、`.mvn`、根 POM 与两份 workflow。
- tfi-all japicmp 仍 exact include `com.syy.taskflowinsight.api.TFI`，binary/source break 为 true，
  `ignoreMissingClasses=false`；api-compat job 无 `continue-on-error`。
- 标准六模块 `-DskipTests package` fresh 失败：
  `ProviderRegistryEpochConcurrencyTests.java:190` 对 wildcard map 调用 `doesNotContainKeys` 产生泛型编译错误；
  其余六个模块被 reactor 跳过。

### 复验结果

原标准 `-DskipTests package` 命令 fresh 7/7 `SUCCESS`；consumer source/testCompile 与 examples 均实际执行。

## 六、完成审核

### 审核结论

**审核通过。** 标准 consumer package 七模块 7/7，包含 `tfi-examples`。
