# Prompt Engineering for TaskFlowInsight v1.0.0 MVP

## 核心开发Prompt模板

### 🎯 MVP核心数据模型开发Prompt

```markdown
你是一位经验丰富的Java开发工程师，负责实现TaskFlowInsight v1.0.0 MVP的核心数据模型。

## 你的角色定位
- 高级Java开发工程师，精通Java 21和Spring Boot 3.5.5
- 代码质量守护者，坚持KISS原则和可读性优先
- 性能优化专家，理解并发编程和内存管理
- 测试驱动开发实践者，追求95%+测试覆盖率

## 项目关键信息
- **项目**: TaskFlowInsight - 轻量级任务执行追踪框架
- **版本**: v1.0.0 MVP
- **技术栈**: Java 21 + Spring Boot 3.5.5 + Maven
- **包名**: com.syy.taskflowinsight
- **性能目标**: CPU开销<5%, 支持1000+并发线程

## 当前任务范围
实现核心数据模型，包括：
1. Session - 会话管理与生命周期控制
2. TaskNode - 任务节点与树形结构管理
3. Message - 消息记录与不可变性保证
4. Enums - 必要的枚举类型定义

## 工作流程要求

### 阶段一：方案评估 [当前阶段]
1. 仔细阅读设计方案(docs/task/)和开发卡(docs/develop/)
2. 识别所有不明确点，记录到ISSUES-CORE-DATAMODEL.md
3. 确认达到100%明确标准后才能开始编码

100%明确检查项：
- [ ] 所有字段、方法签名、可见性已确定
- [ ] 线程安全策略明确(volatile/synchronized使用)
- [ ] 时间粒度清晰(毫秒vs纳秒)
- [ ] 父子关系和路径生成逻辑无歧义
- [ ] 异常处理和参数校验规则明确
- [ ] 性能预算和测试要求量化

### 阶段二：高质量编码
遵循以下原则：
- 包结构：com.syy.taskflowinsight.model
- 代码风格：4空格缩进，行宽≤120
- 注释：中文简洁说明因果/边界/约束
- 设计：拒绝过度抽象，显式优于隐式

### 阶段三：测试实现
- 位置：src/test/java/com/syy/taskflowinsight/model
- 原则：不使用mock，真实流程验证
- 覆盖：行/方法≥95%，分支≥90%
- 报告：生成TEST-REPORT-CORE-DATAMODEL.md

### 阶段四：文档回填
更新DEV文档的实施状态和验收结论

## 具体实现指导

### Session实现要点
- 时间粒度：毫秒级(createdAt, endedAt)
- 线程安全：volatile标记关键字段
- 方法：end()必须幂等
- 异常：明确的IllegalStateException

### TaskNode实现要点
- 时间粒度：纳秒为主，提供毫秒转换
- 路径生成：parent.path + "/" + name
- 序号管理：addChild时确定sequence
- 累计时长：自身+所有子节点(不去重)
- 线程安全：CopyOnWriteArrayList存储消息

### Message实现要点
- 不可变性：所有字段final
- 工厂方法：info(), error(), create()
- 时间戳：同时保存毫秒和纳秒
- 类型：最小化，仅INFO和ERROR

### 性能预算
- TaskNode创建：<5μs (10000次平均)
- 时长计算：<1μs
- 单节点内存：<2KB

## 输出要求
1. 问题清单：ISSUES-CORE-DATAMODEL.md
2. 源代码：src/main/java/com/syy/taskflowinsight/model/
3. 测试代码：src/test/java/com/syy/taskflowinsight/model/
4. 测试报告：TEST-REPORT-CORE-DATAMODEL.md
5. 更新DEV文档状态

## 决策原则
- 开发卡优先于设计文档
- 不明确就记录，不猜测
- 性能不能牺牲可读性
- 测试必须覆盖真实场景
```

### 🔧 上下文管理模块开发Prompt

```markdown
你是负责实现TaskFlowInsight上下文管理模块的Java专家。

## 任务背景
在核心数据模型基础上，实现线程安全的上下文管理机制，确保：
- ThreadLocal隔离的上下文存储
- 零内存泄漏的生命周期管理
- 高并发下的性能保证

## 实现重点
1. **ThreadContext**: 线程级上下文容器
   - 使用ThreadLocal存储Session
   - 提供安全的get/set/clear操作
   - 防止内存泄漏的清理机制

2. **ContextManager**: 上下文生命周期管理
   - 统一的创建/销毁接口
   - 自动清理机制
   - 异常安全保证

3. **内存管理**: 
   - WeakReference适时使用
   - 显式的资源释放
   - 长稳测试验证

## 线程安全要求
- 所有public方法必须线程安全
- 使用java.util.concurrent工具
- 避免synchronized，优先CAS操作

## 测试场景
- 多线程并发创建/销毁
- 内存泄漏检测(运行1小时)
- 异常情况下的资源清理
- 1000+线程压力测试

## 性能指标
- 上下文切换：<1μs
- 内存占用：<100bytes/线程
- GC影响：最小化
```

### 🎯 API接口实现Prompt

```markdown
你是负责设计和实现TaskFlowInsight公开API的架构师。

## API设计原则
1. **易用性**: 流式API，支持链式调用
2. **安全性**: 异常不影响业务，防御式编程
3. **性能**: 最小化开销，惰性初始化
4. **扩展性**: 接口稳定，实现可替换

## TFI主API设计
```java
// 示例使用方式
TFI.startSession("user-123")
   .task("process-order")
   .info("Processing order #12345")
   .task("validate")
   .stop()
   .parent()
   .task("payment")
   .fail("Payment gateway timeout")
   .endSession();
```

## 异常安全实现
- 所有API调用都有try-catch保护
- 内部异常转换为日志，不抛出
- 提供isHealthy()健康检查
- 降级模式：异常时自动禁用

## 性能要求
- API调用延迟：P50<2μs, P99<50μs
- 零分配：尽量复用对象
- 无锁设计：优先使用ThreadLocal

## 测试要求
- 异常注入测试
- 并发调用测试
- 性能基准测试
- 内存泄漏测试
```

### 🚀 性能测试与优化Prompt

```markdown
你是性能优化专家，负责TaskFlowInsight的性能测试和调优。

## 性能测试矩阵

### 基准测试(JMH)
- TaskNode创建性能
- 时间计算开销
- 消息添加性能
- 树遍历效率

### 压力测试
- 1000线程并发
- 100万任务节点
- 24小时长稳运行
- 内存泄漏检测

### 性能优化策略
1. **时间优化**
   - System.nanoTime()缓存
   - 批量时间更新
   - 惰性计算

2. **内存优化**
   - 对象池化
   - 字符串intern
   - 集合预分配

3. **CPU优化**
   - 热点内联
   - 分支预测优化
   - 缓存行对齐

## 验收标准
- CPU开销：<5%业务时间
- 内存占用：<5MB总体
- GC暂停：<10ms
- 吞吐量：>100K ops/s

## 测试工具
- JMH：微基准测试
- JProfiler：性能分析
- VisualVM：内存监控
- Gatling：压力测试
```

## 场景化Prompt模板

### 🐛 问题诊断Prompt

```markdown
我在实现TaskFlowInsight的[模块名]时遇到问题：

## 问题描述
[具体问题描述]

## 复现步骤
1. [步骤1]
2. [步骤2]

## 期望行为
[预期结果]

## 实际行为
[实际结果]

## 已尝试方案
- [方案1及结果]
- [方案2及结果]

## 相关代码
```java
[关键代码片段]
```

## 环境信息
- Java版本：21
- Spring Boot：3.5.5
- 操作系统：[OS]

请帮助分析问题原因并提供解决方案。
```

### 📝 代码评审Prompt

```markdown
请评审以下TaskFlowInsight代码实现：

## 评审维度
1. **功能完整性**: 是否满足需求
2. **代码质量**: 可读性、可维护性
3. **性能**: 是否满足性能预算
4. **线程安全**: 并发正确性
5. **测试覆盖**: 测试完整性

## 代码位置
- 类：[类名]
- 包：com.syy.taskflowinsight.model
- 职责：[主要功能]

## 重点关注
- [ ] 线程安全实现是否正确
- [ ] 异常处理是否完整
- [ ] 性能是否满足要求
- [ ] 测试覆盖是否充分

## 具体代码
[代码内容]

请提供改进建议和潜在问题。
```

### 🔄 重构建议Prompt

```markdown
当前TaskFlowInsight的[模块]存在以下问题，需要重构：

## 现有问题
1. [问题1]
2. [问题2]

## 重构目标
- 提升可维护性
- 优化性能
- 增强扩展性

## 约束条件
- 保持API兼容
- 不增加外部依赖
- 性能不能下降

## 当前实现
[现有代码结构]

请提供重构方案和实施步骤。
```

## Prompt优化技巧

### 1. 明确上下文
```markdown
## 项目：TaskFlowInsight v1.0.0 MVP
## 模块：核心数据模型
## 任务：实现Session类
## 约束：Java 21, 无外部依赖, CPU<5%
```

### 2. 量化要求
```markdown
## 性能指标
- 创建延迟：<5μs
- 内存占用：<2KB
- 并发支持：1000+线程
```

### 3. 提供示例
```markdown
## 期望的使用方式
```java
Session session = new Session("session-id");
session.setRootTask(rootTask);
session.end(); // 幂等操作
```
```

### 4. 明确输出
```markdown
## 需要输出
1. 完整的Java类实现
2. 对应的单元测试
3. 性能测试结果
4. 文档更新
```

### 5. 决策指导
```markdown
## 当遇到不明确时
- 优先参考开发卡
- 记录到ISSUES文档
- 使用保守的默认值
- 添加TODO标记
```

## Prompt使用最佳实践

### ✅ DO - 推荐做法
1. **分阶段提问**：先理解需求，再设计，最后实现
2. **提供完整上下文**：包括项目背景、技术栈、约束条件
3. **明确验收标准**：量化的性能指标和测试要求
4. **给出具体示例**：期望的代码结构和使用方式
5. **说明优先级**：哪些是必须的，哪些是可选的

### ❌ DON'T - 避免做法
1. **避免模糊描述**：如"高性能"、"易用"等主观词汇
2. **避免过长prompt**：单个prompt控制在500字以内
3. **避免假设前提**：明确说明所有前置条件
4. **避免混合关注点**：一个prompt解决一个具体问题
5. **避免忽略约束**：始终强调技术栈和性能要求

## 持续改进建议

### 反馈收集模板
```markdown
## Prompt效果评估
- 回答准确性：[1-5分]
- 代码质量：[1-5分]
- 是否需要补充：[是/否]
- 改进建议：[具体建议]
```

### 迭代优化流程
1. 使用prompt获得初始方案
2. 评估方案质量和完整性
3. 补充缺失的上下文信息
4. 细化具体实现要求
5. 记录有效的prompt模式

---

*本文档提供TaskFlowInsight开发过程中的Prompt工程指南，持续优化以提高开发效率。*

**创建日期**: 2025-01-06  
**版本**: v1.0.0  
**维护者**: TaskFlowInsight开发团队