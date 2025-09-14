# V210-051: ThreadLocal 管理统一与零泄漏保障

- 优先级：P0  
- 预估工期：M（3–4天）  
- Phase：M1 P0  
- Owner：待定  
- 前置依赖：上下文组件可用（`ManagedThreadContext/SafeContextManager/ZeroLeakThreadLocalManager`）

## 背景
现有与新能力可能产生多套 ThreadLocal 使用路径，若不统一会导致泄漏与不可控的生命周期。需确立唯一入口与清理策略。

## 目标
- 以 `ManagedThreadContext/SafeContextManager` 为唯一上下文锚点；
- 引入 `ThreadLocalManager`（门面），统一注册/清理/快照与异步传播包装；
- 禁止新增裸用 ThreadLocal；
- 提供 try-with-resources 使用范式与示例。

## 实现要点
- 包与类：`context.ThreadLocalManager`（门面）
- 职责：
  - 上下文创建/关闭（AutoCloseable）；
  - 任务/会话与上下文绑定；
  - 提供 `TFIAwareExecutor` 包装异步链路；
  - 与 `ZeroLeakThreadLocalManager` 对接泄漏检测/清理；
- 与 Starter 集成：在 AutoConfiguration 中暴露 Bean，并在文档中声明“唯一入口”。

## 测试要点
- 单测：创建/关闭、嵌套上下文、异常路径清理；
- 集成：与变更追踪链路联动；
- 长稳：一小时级健康验证，确保无积累性泄漏。

## 验收标准
- [ ] 所有新代码路径不再直接使用 ThreadLocal；
- [ ] 门面提供清晰 API 与示例；
- [ ] Starter 注入默认实现；
- [ ] 长稳与泄漏检测用例通过。

## 对现有代码的影响（Impact）
- 影响级别：低到中（取决于零散 ThreadLocal 用法数量）。
- 行为：推荐/重构将零散 ThreadLocal 使用迁移到统一门面；已有 `ManagedThreadContext`/`SafeContextManager` 可直接复用，改动量可控。
- 测试：新增统一管理用例与长稳验证；原有上下文用例可保持。
