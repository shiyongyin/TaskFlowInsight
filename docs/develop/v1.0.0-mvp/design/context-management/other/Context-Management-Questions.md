# 上下文管理模块问题清单（评审版）

> 目的：在编码前将需求与设计100%对齐，消除歧义与潜在实现冲突。以下问题按优先级与主题分组，需逐条确认或给出决策。

## 一、数据模型与API对齐（高优先级）
- Q1-Session构造差异：设计稿中的`ManagedThreadContext.startSession()`使用`new Session(threadId)`并允许`session.setRoot(TaskNode)`，而现有实现`Session.create(String rootTaskName)`在构造时即创建根任务，且无`setRoot`接口。决定：
  - 方案A：沿用现有`Session`模型，`startSession(rootTaskName)`强制要求根任务名，返回`Session`并将其激活；`ManagedThreadContext`不再直接操控`Session`内部结构（只读`getRootTask()`）。
  - 方案B：扩展`Session`以支持“空根+后置设置根任务”（新增`setRootTask`），与设计稿一致。
  - 请选择其一，并给出根任务命名策略（默认名？由调用方显式传入？）。

- Q2-TaskNode API差异：设计稿示例里存在`new TaskNode(taskName, depth)`, `task.addChild(child)`, `task.isActive()`, `task.stop()`；而现有实现为`new TaskNode(parent, taskName)`，状态为`RUNNING/COMPLETED/FAILED`，无`stop()`、无显式`addChild`（构造时自动挂载）。决定：
  - 是否统一为现有`TaskNode`模型：用`status.isActive()`等价，结束用`complete()`；嵌套通过`createChild()`或`new TaskNode(parent, name)`；删除`stop()`概念？

- Q3-标识语义：设计稿多处使用“threadId(long)”；现有`Session`使用`threadId`字符串。决定采用统一类型与命名规范（建议统一为`long threadId`，外部展示再转字符串）。

## 二、ManagedThreadContext职责边界（高优先级）
- Q4-上下文栈策略：设计稿包含“会话栈+任务栈”。结合现有`Session`已内置根任务，是否仍需要“会话栈”？是否只允许每线程唯一活动会话（更贴近MVP）？如支持嵌套会话，请定义：
  - 嵌套合法性与典型场景；
  - 关闭外层会话时内层会话处理规则；
  - 与`Session.getCurrent()/activate()/deactivate()`的关系与冲突避免。

- Q5-ThreadLocal唯一真源：`ManagedThreadContext`设计内置`ThreadLocal`，`SafeContextManager`又维护`InheritableThreadLocal`。决定：
  - 单一来源（推荐）：仅`SafeContextManager`持有`InheritableThreadLocal`并管理生命周期，`ManagedThreadContext`不再自持`ThreadLocal`；或
  - 双持有：请定义二者同步与一致性策略，避免悬挂引用与重复清理。

- Q6-强制资源管理：是否强制所有使用通过`try-with-resources(ManagedThreadContext)`？对于框架封装（如`SafeContextManager.executeInContext/executeAsync`）是否隐藏这一细节，由管理器代为创建/关闭？请明确“直接`ManagedThreadContext.create()`的使用禁用/允许范围”。

## 三、异步与线程池传播（高优先级）
- Q7-快照语义：设计稿两种表述不一致：
  - 一处快照包含`Session/TaskNode`对象本身并在新线程“恢复”；
  - 另一处强调“避免跨线程共享可变状态”，建议仅传递标识或副本。请明确：
    - 快照包含哪些字段？（建议：`contextId`, `sessionId`, `taskPath`/`taskId`，以及只读元数据）
    - “恢复”后的上下文应是“新上下文+关联ID”，还是“深拷贝可见的只读视图”？

- Q8-线程池包装重复：设计稿同时出现`SafeThreadPoolExecutor`与`TFIAwareThreadPool`两套包装。最终收敛为哪一个？或保留一个“装饰器”层（`ContextAwareRunnable/Callable`）+ 不侵入线程池实现？

- Q9-InheritableThreadLocal边界：在MVP中，是否默认禁用ITL继承，仅支持“显式快照传播+装饰器”以规避线程池/虚拟线程问题？若保留ITL，仅用于直接`new Thread()`场景？请给出开关注解策略或配置项。

## 四、ZeroLeakThreadLocalManager范围与安全（高优先级）
- Q10-清理对象范围：反射清理`ThreadLocalMap`仅应针对TFI持有的`ThreadLocal`键值。请确认：
  - 我们是否只管理本模块创建的`ThreadLocal`？如何识别“TFI相关Entry”？是否引入命名/封装标记？
  - 生产环境默认关闭反射清理，仅诊断场景人工开启，是否作为“非常用工具”而非主流程？

- Q11-Java 21强封装：通过反射访问`Thread.threadLocals`在Java 21需要`--add-opens`等JVM参数。MVP是否允许要求运维加 JVM opens？若不允许，反射清理需降级为“可用即用”的辅助能力并带自检能力。

- Q12-重复清理职责：`SafeContextManager`与`ZeroLeakThreadLocalManager`在“泄漏检测/修复”上有重叠。建议职责划分：
  - `SafeContextManager`：生命周期与使用面（创建/关闭/传播）
  - `ZeroLeakThreadLocalManager`：底层ThreadLocal观测与诊断（非强依赖）
  - 是否接受该拆分？或合并为一个组件以简化？

## 五、监控、配置与运维（中优先级）
- Q13-监控落地方式：文档提及Actuator端点与Prometheus导出，但未给出具体Endpoint契约。MVP是否需要实现以下只读端点？
  - `/actuator/taskflow/contexts`（上下文计数与指标）
  - `/actuator/taskflow/health`（健康状态/泄漏等级）
  - `/actuator/taskflow/cleanup`（触发清理）
  若需要，请给出响应JSON字段定义与安全策略（仅dev/prod受限）。

- Q14-配置项命名：存在`taskflow.context-manager.*`与`taskflow.threadlocal-manager.*`两个命名空间。是否确认两者并行存在？配置优先级与默认值从何处加载（`application.yml` vs. env）？

## 六、性能目标与测试口径（中优先级）
- Q15-性能指标可达性：`上下文创建 < 1μs`、`任务操作 < 100ns`在Java中非常激进（受分配/日志/计时影响）。是否将其作为“目标值”而非“硬性验收”？能否用“吞吐/Percentile”指标替代硬阈值？

- Q16-24小时压测：作为验收条款是否缩减为“可配置长时间稳定性测试脚本+报告说明”，CI阶段使用缩短版（例如30~60分钟）？

- Q17-测试基准方法：是否提供统一的Benchmark基类与度量方式（JMH或基于`System.nanoTime()`的轻量测试）？避免测试因JIT/GC抖动而误报。

## 七、虚拟线程与JDK依赖（中优先级）
- Q18-虚拟线程支持范围：MVP是否必须实现`StructuredTaskScope`集成与虚拟线程传播？若非必须，可以先提供“显式快照+虚拟线程任务包装”的最小可用实现，后续增强。

- Q19-Java模块化：是否需要`module-info.java`及`--add-opens`策略说明？若采用反射清理/虚拟线程API，需要明确模块边界与启动参数。

## 八、日志与告警（低优先级）
- Q20-日志级别：设计稿中多处`LOGGER.warn`/`error`，MVP是否规定默认日志级别与限流策略（避免在高并发泄漏检测时刷屏）？

- Q21-告警集成：`LeakListener`是否需要默认实现（对接Spring事件或简单回调）？告警触发后是否需要“抑制窗口”（同一问题N秒只告警一次）？

## 九、对外API与使用方式（确认项）
- Q22-推荐用法：是否明确“业务侧只通过`SafeContextManager`使用”，而不直接操作`ManagedThreadContext`？是否提供`@WithContext`之类的注解或AOP建议做法（非MVP也请定方向）？

- Q23-异常策略：当业务侧未关闭任务/会话时，是否由管理器在`close()`中强制结束为`COMPLETED`或`FAILED`？若为`FAILED`，是否需要错误消息来源策略？

---

请逐条确认或给出取舍意见。一旦确认，我将：
- 更新设计细节与契约（接口、快照字段、配置项）；
- 以“单一ThreadLocal真源+显式快照传播”为主线编写实现；
- 同步调整测试计划与性能口径；
- 产出后续的《Context Engineering.md》与《Prompt Engineering.md》（生成至`docs/develop/v1.0.0-mvp/design/context-management/engineer/`）。

