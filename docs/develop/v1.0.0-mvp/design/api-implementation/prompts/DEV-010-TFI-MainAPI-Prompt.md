# DEV-010: TFI主API实现 - AI开发提示词

## 第一阶段：需求澄清提示词

```markdown
你是一名资深Java API架构师，拥有15年高并发系统设计经验。现在需要你评审并澄清TFI主API的实现需求。

**输入材料：**
- 任务卡：docs/task/v1.0.0-mvp/api-implementation/TASK-010-TFI-MainAPI.md
- 设计文档：docs/develop/v1.0.0-mvp/design/api-implementation/DEV-010-TFI主API实现.md
- 现有实现：ManagedThreadContext、SafeContextManager、TaskNode、Message
- 注意：MessageType只有INFO和ERROR两种类型

**评审要求：**
1. 验证API设计的完整性和一致性
2. 检查异常安全机制是否充分
3. 评估性能目标（<5%CPU开销）的可行性
4. 确认与现有模块的集成点
5. 识别潜在的线程安全问题

**输出格式：**
生成问题清单文件：docs/task/v1.0.0-mvp/api-implementation/DEV-010-Questions.md

问题清单结构：
# DEV-010 需求澄清问题清单

## 高优先级问题
1. [问题描述]
   - 影响：[影响范围]
   - 建议：[解决建议]

## 中优先级问题
...

## 低优先级问题
...

## 结论
- [ ] 需求100%明确
- [ ] 可以进入实现阶段

如果没有问题，直接输出："需求已100%明确，可以进入实现阶段"
```

## 第二阶段：代码实现提示词

```markdown
你是一名资深Java开发工程师，精通Spring Boot和高性能编程。现在需要你实现TFI主API类。

**角色定位：** Java性能优化专家 + API设计专家 + 异常处理专家

**实现目标：**
1. 创建TFI主类，提供静态方法门面
2. 实现异常安全机制（Fail-Silent模式）
3. 集成ContextManager进行底层操作
4. 支持系统启用/禁用控制
5. 确保高性能（禁用<0.1微秒，启用<1微秒）

**技术约束：**
- Java 21
- Spring Boot 3.5.x
- 使用SLF4J进行日志记录
- 委托给现有实现，不重复造轮子
- 线程安全，使用volatile保证可见性

**代码规范：**
- 中文注释，简洁清晰
- KISS原则，避免过度设计
- 完整的JavaDoc
- 异常处理覆盖所有公共方法

**实现步骤：**
Step 1: 实现TFI主类框架
```java
package com.syy.taskflowinsight.api;

public final class TFI {
    // 1. 静态字段定义
    // 2. 私有构造函数
    // 3. 系统控制方法
    // 4. 任务管理方法
    // 5. 消息记录方法
    // 6. 查询和导出方法
    // 7. 内部辅助方法
}
```

Step 2: 实现异常安全机制
- 每个公共方法都有try-catch包装
- 使用SLF4J logger记录错误
- 返回NullTaskContext.INSTANCE而非null
- 底层抛出的异常在门面层捕获

Step 3: 实现性能优化
- volatile标志位快速检查
- 禁用状态立即返回
- 避免不必要的对象创建

Step 4: 实现TaskContext接口和实现类
- TaskContext接口定义
- TaskContextImpl实现类
- NullTaskContext空对象实现

**输出要求：**
1. 完整的TFI.java源代码
2. TaskContext接口及实现类
3. 关键设计决策说明
4. 性能优化要点

**质量标准：**
- 所有公共方法都有异常处理
- 代码覆盖率>80%（现实目标）
- 性能满足相对指标（<5%开销）
- 与现有代码完全兼容
```

## 第三阶段：测试设计提示词

```markdown
你是一名测试架构师，精通JUnit5和性能测试。为TFI主API设计全面的测试用例。

**测试范围：**
1. 功能测试
   - 所有公共API方法
   - 正常路径和边界条件
   - 参数验证（null、空字符串、超长输入）

2. 异常测试
   - 内部组件异常模拟
   - 异常不影响业务验证
   - 错误恢复机制测试

3. 并发测试
   - 多线程同时调用
   - 启用/禁用并发切换
   - 上下文隔离验证

4. 性能测试
   - 启用状态CPU开销<5%
   - 禁用状态开销<0.1%
   - 内存占用评估

**测试代码结构：**
```java
@SpringBootTest
class TFITest {
    // 功能测试
    @Test
    void testStartTask() { }
    
    @Test
    void testMessageRecording() { }
    
    // 异常测试
    @Test
    void testExceptionSafety() { }
    
    // 并发测试
    @Test
    void testConcurrentAccess() { }
    
    // 性能测试
    @PerformanceTest
    void testPerformanceOverhead() { }
}
```

**特殊测试场景：**
1. 禁用状态下的快速返回
2. 启用/禁用状态切换
3. 内存泄漏检测
4. 长时间运行稳定性

**输出要求：**
1. 完整的TFITest.java
2. 性能基准测试代码
3. 测试报告模板
4. 覆盖率要求验证

**验收标准：**
- 代码覆盖率>90%
- 所有测试通过
- 性能指标达标
- 无内存泄漏
```

## 第四阶段：代码审查提示词

```markdown
你是一名代码审查专家。请审查TFI主API的实现代码。

**审查维度：**

1. 代码质量
   - [ ] 命名规范性
   - [ ] 注释完整性
   - [ ] 代码可读性
   - [ ] KISS原则遵循

2. 异常安全
   - [ ] 所有公共方法都有异常处理
   - [ ] 异常不影响业务逻辑
   - [ ] 错误日志记录适当
   - [ ] 资源正确释放

3. 性能优化
   - [ ] 禁用状态快速返回
   - [ ] 避免不必要的对象创建
   - [ ] 无阻塞操作
   - [ ] 内存使用合理

4. 线程安全
   - [ ] volatile使用正确
   - [ ] 无竞态条件
   - [ ] 上下文正确隔离
   - [ ] 并发测试充分

5. API设计
   - [ ] 接口简洁易用
   - [ ] 方法命名直观
   - [ ] 参数合理
   - [ ] 返回值一致

**审查输出：**
1. 问题列表（按严重程度分类）
2. 改进建议
3. 最佳实践推荐
4. 审查通过确认

**判定标准：**
- 无严重问题：通过
- 有建议改进：条件通过
- 有严重问题：需要修复
```

## 综合执行提示词（一次性完成所有阶段）

```markdown
你是TaskFlowInsight项目的首席开发工程师，需要完成TFI主API的完整实现。

**执行流程：**

## Phase 1: 需求分析（10分钟）
阅读TASK-010和DEV-010文档，理解需求并列出关键实现点：
- 核心功能清单
- 性能要求
- 异常处理策略
- 集成点确认

## Phase 2: 代码实现（30分钟）
基于需求实现代码：
1. TFI.java - 主API类
2. TaskContext.java - 任务上下文接口
3. TaskContextImpl.java - 实现类
4. NullTaskContext.java - 空对象模式

关键代码片段：
```java
// TFI主类核心结构
public final class TFI {
    private static volatile boolean globalEnabled = false;
    private static final ContextManager contextManager = ContextManager.getInstance();
    
    // 异常安全的任务开始
    public static TaskContext start(String taskName) {
        if (!isEnabled()) return NullTaskContext.INSTANCE;
        try {
            // 实现逻辑
        } catch (Throwable t) {
            handleInternalError("Failed to start task", t);
            return NullTaskContext.INSTANCE;
        }
    }
}
```

## Phase 3: 测试编写（20分钟）
编写测试用例覆盖：
- 功能测试（15个用例）
- 异常测试（5个用例）
- 并发测试（3个用例）
- 性能测试（2个用例）

## Phase 4: 自检与优化（10分钟）
- 运行测试验证
- 性能指标检查
- 代码审查
- 文档完善

**最终交付物：**
1. 源代码文件（4个）
2. 测试代码文件（1个）
3. 实现总结报告
4. 性能测试结果

**质量要求：**
- 代码可编译运行
- 测试全部通过
- 性能达标（<5%开销）
- 异常安全保证
```