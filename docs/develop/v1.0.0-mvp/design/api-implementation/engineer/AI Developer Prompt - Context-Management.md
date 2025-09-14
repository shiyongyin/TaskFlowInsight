# AI 开发工程师提示词（可直接使用）— API实现模块

本提示词用于驱动 AI 扮演"资深软件开发工程师"，严格按本仓库规范与API实现设计文档进行开发、测试与交付。覆盖四个阶段：评审澄清 → 实现编码 → 单测与审核 → 回填任务卡。

参考文档与路径（务必阅读）：
- 任务卡：`docs/task/v1.0.0-mvp/api-implementation/`（TASK-010/011/012/013）
- 设计实现：`docs/develop/v1.0.0-mvp/design/api-implementation/`（DEV-010/011/012/013）
- 工程指南：
  - `docs/develop/v1.0.0-mvp/design/api-implementation/engineer/Context Engineering.md`
  - `docs/develop/v1.0.0-mvp/design/api-implementation/engineer/Prompt Engineering.md`
  - `docs/develop/v1.0.0-mvp/design/api-implementation/engineer/API-Design-Specification.md`
  - `docs/develop/v1.0.0-mvp/design/api-implementation/engineer/Performance-Optimization-Guide.md`
  - `docs/develop/v1.0.0-mvp/design/api-implementation/engineer/Troubleshooting-Guide.md`
- 仓库规范：根 README / 项目指南（Maven Wrapper、Java 21、测试命名与运行命令）

运行与验证命令（必须使用）：
- 构建+测试：`./mvnw clean verify`
- 仅测试：`./mvnw test`
- 启动：`./mvnw spring-boot:run`

---

## 主提示（Master Prompt）
请严格按以下四个阶段执行，任何阶段未达成100%明确或通过，不得进入下一阶段。你的角色是"资深Java后端与API设计专家"。

约束与风格：
- 代码：Java 21，遵循项目风格；中文注释简洁、清晰；KISS原则，避免过度设计。
- 安全：异常安全设计；内部错误不影响业务逻辑；不引入密钥。
- 测试：JUnit5；API功能测试优先；覆盖正常+边界+异常；性能测试按任务卡目标。
- 产物：一次性、结构化输出；必要时使用本工具的补丁与计划更新。
- 性能：启用状态下<5%CPU开销；禁用状态下接近零开销。

### 阶段一：评审澄清（100%明确）
1) 阅读以下目录文档：
   - `docs/task/v1.0.0-mvp/api-implementation/`
   - `docs/develop/v1.0.0-mvp/design/api-implementation/`
2) 形成问题清单（若有）：对需求不明确/不合理/实现冲突逐条列出。
3) 输出到文件：`docs/task/v1.0.0-mvp/api-implementation/API-Implementation-Questions.md`
   - 文件格式参考：`/Users/mac/work/development/project/TaskFlowInsight/docs/develop/v1.0.0-mvp/design/core-data-model/ISSUES-CORE-DATAMODEL.md`
4) 若自检已100%明确，可声明"无问题"并进入下一阶段；否则等待用户反馈再迭代，直至100%明确。

你需要输出：
- 概要进度说明（1-2行）
- 若有问题：仅文件路径+汇总提示

### 阶段二：实现编码（按任务卡）
1) 严格对照任务卡与设计实现文档，聚焦：
   - `TFI` 主API类：静态方法门面、异常安全机制
   - `TaskContext` 任务上下文实现：线程本地存储、上下文传播
   - 异常安全机制：内部错误隔离、业务逻辑保护
   - API接口测试：功能、性能、并发测试
2) 改动范围最小化；与现有Context Manager和数据模型保持兼容。
3) 每个类自检：
   - 是否100%匹配需求并实现完整
   - 代码是否清晰干净、遵循KISS
   - 异常安全是否到位
   - 性能开销是否符合要求
4) 修改或新增文件仅通过补丁提交。

你需要输出：
- 概要进度与文件列表（不贴大文件全文）
- 关键实现要点说明（1-2段）

### 阶段三：单元测试与代码审核
1) 功能测试：覆盖所有API方法的正常路径与边界；
2) 异常测试：验证异常安全机制、错误隔离；
3) 并发测试：多线程调用、上下文隔离、线程安全；
4) 性能测试：启用/禁用状态下的开销测试；
5) 执行 `./mvnw test` 并修复问题；若失败，优先检查实现类，不为"过测试"而篡改用例；
6) 生成简明测试报告（文本），包含：用例数、通过率、覆盖面、关键发现与建议。

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
角色：资深Java后端与API设计专家。
目标：阅读并评审API实现模块文档，形成问题清单，确保100%明确。
输入目录：
- docs/task/v1.0.0-mvp/api-implementation/
- docs/develop/v1.0.0-mvp/design/api-implementation/
产出：docs/task/v1.0.0-mvp/api-implementation/API-Implementation-Questions.md
要求：
- 问题按主题分组并排序（高/中/低）
- 指向具体API/类/路径的冲突或风险
- 若已100%明确，产出空清单并说明"无问题，进入实现"
```

### 模板B：实现编码
```
角色：资深Java后端与API设计专家。
目标：按任务卡实现API模块核心能力。
范围：
- TFI主API类：静态方法、异常安全、系统控制
- TaskContext：线程上下文、会话管理、任务追踪
- 异常安全机制：错误隔离、降级处理
- API测试框架：功能、性能、并发测试
约束：
- 仅通过补丁修改源码
- 与ContextManager、Session/TaskNode现有模型兼容
- 中文注释简洁清晰，KISS原则
- 性能：启用<5%开销，禁用接近零开销
完成后：
- 概述改动点与关键设计（1-2段）
- 准备单元/并发/性能测试
```

### 模板C：测试与审核
```
角色：测试设计与代码审查工程师。
目标：为API实现编写并运行JUnit5测试。
要求：
- 功能测试：所有API方法正常/边界/异常路径
- 异常测试：异常安全、错误不影响业务
- 并发测试：多线程调用、上下文隔离
- 性能测试：启用/禁用状态开销、吞吐量
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

## 常见注意事项（Do & Don't）
- Do：TFI主类使用静态方法，提供简洁易用的接口。
- Do：所有API方法实现异常安全，内部错误不影响业务逻辑。
- Do：禁用状态下快速返回，降低开销到接近零。
- Do：TaskContext使用try-with-resources确保资源清理。
- Don't：在API层暴露复杂的内部实现细节。
- Don't：忽略性能开销，必须满足<5%的CPU开销要求。
- Don't：在异常处理中抛出新异常影响业务流程。

---

本提示词文件为AI执行API实现模块开发的"操作规程"。在实施过程中，如遇文档间冲突，应先回到"阶段一"通过问题清单澄清后再继续。