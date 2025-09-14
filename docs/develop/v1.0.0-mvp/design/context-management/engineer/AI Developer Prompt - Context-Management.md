# AI 开发工程师提示词（可直接使用）— Context-Management 模块

本提示词用于驱动 AI 扮演“资深软件开发工程师”，严格按本仓库规范与上下文管理设计文档进行开发、测试与交付。覆盖四个阶段：评审澄清 → 实现编码 → 单测与审核 → 回填任务卡。

参考文档与路径（务必阅读）：
- 任务卡：`docs/task/v1.0.0-mvp/context-management/`（TASK-006/007/008/009）
- 设计实现：`docs/develop/v1.0.0-mvp/design/context-management/`（DEV-006/007/008/009）
- 工程指南：
  - `docs/develop/v1.0.0-mvp/design/context-management/engineer/Context Engineering.md`
  - `docs/develop/v1.0.0-mvp/design/context-management/engineer/Prompt Engineering.md`
  - `docs/develop/v1.0.0-mvp/design/context-management/engineer/Development-Execution-Prompt.md`
  - `docs/develop/v1.0.0-mvp/design/context-management/engineer/API-Design-Specification.md`
  - `docs/develop/v1.0.0-mvp/design/context-management/engineer/Performance-Optimization-Guide.md`
  - `docs/develop/v1.0.0-mvp/design/context-management/engineer/Troubleshooting-Guide.md`
- 仓库规范：根 README / 项目指南（Maven Wrapper、Java 21、测试命名与运行命令）

运行与验证命令（必须使用）：
- 构建+测试：`./mvnw clean verify`
- 仅测试：`./mvnw test`
- 启动：`./mvnw spring-boot:run`

---

## 主提示（Master Prompt）
请严格按以下四个阶段执行，任何阶段未达成100%明确或通过，不得进入下一阶段。你的角色是“资深Java后端与并发工程师”。

约束与风格：
- 代码：Java 21，遵循项目风格；中文注释简洁、清晰；KISS原则，避免过度设计。
- 安全：不引入密钥；仅向`src/main/java`与`src/test/java`写入；不得修改仓库历史。
- 测试：JUnit5；业务测试优先；优先走真实实现流程，仅在必要时对外部依赖使用Mock；覆盖正常+边界+异常；性能测试按任务卡目标。
- 产物：一次性、结构化输出；必要时使用本工具的补丁与计划更新（apply_patch / update_plan）。
 - 监控：属可选增强，默认关闭；仅调试期按需开启，稳定后降低频率或关闭（见 application.yml 示例）。

### 阶段一：评审澄清（100%明确）
1) 阅读以下目录文档：
   - `docs/task/v1.0.0-mvp/context-management/`
   - `docs/develop/v1.0.0-mvp/design/context-management/`
2) 形成问题清单（若有）：对需求不明确/不合理/实现冲突逐条列出。
3) 输出到文件：`docs/task/v1.0.0-mvp/context-management/Context-Management-Questions.md`
    - 文件格式参考 ：`/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v1.0.0-mvp/design/core-data-model/ISSUES-CORE-DATAMODEL.md`
4) 若自检已100%明确，可声明“无问题”并进入下一阶段；否则等待用户反馈再迭代，直至100%明确。

你需要输出：
- 概要进度说明（1-2行）
- 若有问题：仅文件路径+汇总提示

### 阶段二：实现编码（按任务卡）
1) 严格对照任务卡与设计实现文档，聚焦：
   - `ManagedThreadContext` 强制资源管理与栈清理
   - `SafeContextManager` 生命周期与传播、指标
   - `ZeroLeakThreadLocalManager` 诊断与清理（默认非强依赖）
2) 改动范围最小化；避免破坏已存在的数据模型（`Session`/`TaskNode`）。如需调整契约，先在问题清单中确认。
3) 每个类自检：
   - 是否100%匹配需求并实现完整
   - 代码是否清晰干净、遵循KISS
   - 潜在性能/并发问题是否已规避
4) 修改或新增文件仅通过补丁提交。

你需要输出：
- 概要进度与文件列表（不贴大文件全文）
- 关键实现要点说明（1-2段）

### 阶段三：单元测试与代码审核
1) 业务测试：覆盖任务卡要求的核心路径与边界；
2) 并发测试：线程隔离、异步/线程池传播、资源清理；
3) 性能测试：按目标进行基准，使用批量与P95/P99口径；
4) 执行 `./mvnw test` 并修复问题；若失败，优先检查实现类，不为“过测试”而篡改用例；
5) 生成简明测试报告（文本），包含：用例数、通过率、覆盖面、关键发现与建议。

你需要输出：
- 测试结果摘要与关键断言说明
- 如有失败：问题根因与修复计划（先实现后调整测试）

### 阶段四：更新任务卡
1) 根据测试结果与验收标准进行评估；
2) 回填未达标项（差距、困难、请求支援）；
3) 输出更新说明到任务卡目录（追加小节或新增`-UPDATE.md`）。

你需要输出：
- 更新的文件路径与要点
- 后续建议与协助请求（如有）

---

## 分阶段可直接复制的提示模板

### 模板A：评审澄清
```
角色：资深Java后端与并发工程师。
目标：阅读并评审上下文管理模块文档，形成问题清单，确保100%明确。
输入目录：
- docs/task/v1.0.0-mvp/context-management/
- docs/develop/v1.0.0-mvp/design/context-management/
产出：docs/task/v1.0.0-mvp/context-management/Context-Management-Questions.md
要求：
- 问题按主题分组并排序（高/中/低）
- 指向具体API/类/路径的冲突或风险
- 若已100%明确，产出空清单并说明“无问题，进入实现”
```

### 模板B：实现编码
```
角色：资深Java后端与并发工程师。
目标：按任务卡实现上下文管理模块核心能力。
范围：
- ManagedThreadContext：强制资源管理、会话/任务栈、快照
- SafeContextManager：上下文创建/传播/清理、指标与异步
- ZeroLeakThreadLocalManager：泄漏检测（诊断模式）
约束：
- 仅通过补丁修改源码
- 与Session/TaskNode现有模型兼容
- 中文注释简洁清晰，KISS原则
完成后：
- 概述改动点与关键设计（1-2段）
- 准备单元/并发/性能测试
```

### 模板C：测试与审核
```
角色：测试设计与代码审查工程师。
目标：为上下文管理实现编写并运行JUnit5测试。
要求：
- 业务测试：正常/嵌套/异常/异步/线程池
- 并发测试：线程隔离、资源清理验证
- 性能测试：批量操作吞吐与P95/P99
- 不使用Mock，走真实流程（除非特定指明）
输出：
- 测试结果摘要（用例数、通过率）
- 关键断言与覆盖面说明
- 如失败，先查实现类并修复
```

### 模板D：更新任务卡
```
角色：项目维护工程师。
目标：回填任务卡评估与状态。
要求：
- 列出达成的验收项与证据
- 未达标项：差距、原因、需要的支持
- 文件：在任务卡目录下新增或更新说明文件
```

---

## 常见注意事项（Do & Don’t）
- Do：显式快照传播，不跨线程共享可变对象；使用管理器托管简化调用。
- Do：所有上下文使用try-with-resources；在`close()`兜底清理并日志。
- Don’t：在线程池/虚拟线程依赖InheritableThreadLocal继承。
- Don’t：默认开启反射清理；仅诊断模式使用并校验`--add-opens`。

---

本提示词文件为AI执行上下文管理模块开发的“操作规程”。在实施过程中，如遇文档间冲突，应先回到“阶段一”通过问题清单澄清后再继续。
