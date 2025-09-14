# DEV-011: TaskContext实现 - AI开发提示词

## 第一阶段：需求澄清提示词

```markdown
你是一名资深的Java并发编程专家，精通线程安全和高性能设计。现在需要你评审TaskContext的实现需求。

**输入材料：**
- 任务卡：docs/task/v1.0.0-mvp/api-implementation/TASK-011-TaskContextImplementation.md
- 设计文档：docs/develop/v1.0.0-mvp/design/api-implementation/DEV-011-TaskContext实现.md
- 依赖接口：TaskNode、ThreadContext、Message的定义

**评审重点：**
1. TaskContext接口设计的完整性
2. 线程安全机制的充分性
3. 资源管理（AutoCloseable）的正确性
4. 父子任务关系管理的合理性
5. 流式API设计的易用性
6. 异常处理和降级机制

**需要澄清的问题：**
- TaskContext与ThreadContext的职责边界
- 子任务嵌套深度限制
- 消息缓冲机制和内存控制
- 关闭状态下的行为规范
- 并发访问的同步策略

**输出格式：**
生成问题清单：docs/task/v1.0.0-mvp/api-implementation/DEV-011-Questions.md

如果需求清晰，输出："需求已100%明确，可以进入实现阶段"
```

## 第二阶段：代码实现提示词

```markdown
你是一名Java高性能编程专家，精通并发编程和内存优化。实现TaskContext任务上下文管理。

**角色定位：** 并发编程专家 + 性能优化专家 + API设计专家

**实现目标：**
1. TaskContext接口定义
2. TaskContextImpl完整实现
3. NullTaskContext空对象实现
4. 线程安全保证
5. 资源自动管理

**核心功能实现：**

### 1. TaskContext接口
```java
package com.syy.taskflowinsight.api;

public interface TaskContext extends AutoCloseable {
    // 任务标识
    String getTaskId();
    String getTaskName();
    
    // 生命周期
    TaskStatus getStatus();
    long getStartTime();
    long getDuration();
    
    // 消息记录（链式调用）
    TaskContext message(String message);
    TaskContext debug(String message);
    TaskContext warn(String message);
    TaskContext error(String message, Throwable t);
    
    // 子任务管理
    TaskContext startSubTask(String name);
    void endSubTask();
    
    // 资源管理
    void end();
    @Override
    void close(); // 自动调用end()
    
    // 查询接口
    TaskContext getParent();
    List<TaskContext> getChildren();
    List<Message> getMessages();
}
```

### 2. TaskContextImpl实现要点

**线程安全策略：**
- 使用volatile保证状态可见性
- CopyOnWriteArrayList管理子任务列表
- 原子操作保证状态转换
- 双重检查锁定优化性能

**异常安全实现：**
```java
@Override
public TaskContext message(String message) {
    if (isClosed()) {
        return this; // 静默返回，不抛异常
    }
    
    try {
        Message msg = Message.create(message, MessageType.INFO);
        taskNode.addMessage(msg);
    } catch (Throwable t) {
        handleInternalError("Failed to add message", t);
    }
    return this; // 支持链式调用
}
```

**资源管理实现：**
```java
@Override
public void close() {
    if (closed.compareAndSet(false, true)) {
        try {
            // 1. 结束所有未完成的子任务
            closeChildren();
            
            // 2. 更新任务状态
            if (taskNode.getStatus() == TaskStatus.RUNNING) {
                taskNode.end();
            }
            
            // 3. 从父任务移除
            if (parent != null) {
                parent.removeChild(this);
            }
            
            // 4. 清理线程上下文
            threadContext.popTask();
            
        } catch (Throwable t) {
            handleInternalError("Error during close", t);
        }
    }
}
```

### 3. NullTaskContext实现
```java
public enum NullTaskContext implements TaskContext {
    INSTANCE;
    
    @Override
    public TaskContext message(String message) {
        return this; // 所有操作都返回自身
    }
    
    @Override
    public void close() {
        // 空操作，安全关闭
    }
    // ... 其他方法返回默认值
}
```

**性能优化要点：**
1. 懒加载子任务列表
2. 消息批量处理
3. 避免频繁的时间戳获取
4. 使用对象池复用Message对象

**输出要求：**
1. TaskContext.java接口
2. TaskContextImpl.java实现
3. NullTaskContext.java空对象
4. 性能测试基准代码
```

## 第三阶段：测试设计提示词

```markdown
你是一名测试专家，精通并发测试和性能测试。为TaskContext设计全面的测试用例。

**测试范围：**

### 1. 功能测试
```java
@Test
void testTaskLifecycle() {
    // 创建、执行、关闭完整流程
}

@Test
void testMessageRecording() {
    // 各类消息记录测试
}

@Test
void testSubTaskManagement() {
    // 子任务创建、嵌套、关闭
}

@Test
void testChainedAPICalls() {
    // 链式调用测试
}
```

### 2. 异常测试
```java
@Test
void testClosedTaskBehavior() {
    // 关闭后的操作行为
}

@Test
void testExceptionIsolation() {
    // 内部异常不影响外部
}

@Test
void testResourceCleanup() {
    // 异常情况下的资源清理
}
```

### 3. 并发测试
```java
@Test
void testConcurrentMessageAddition() {
    // 多线程同时添加消息
}

@Test
void testConcurrentSubTaskCreation() {
    // 并发创建子任务
}

@Test
void testThreadSafety() {
    // 线程安全性验证
}
```

### 4. 性能测试
```java
@PerformanceTest
void testMessageRecordingPerformance() {
    // 目标：<100ns per message
}

@PerformanceTest
void testTaskCreationOverhead() {
    // 目标：<1μs per task
}

@PerformanceTest
void testMemoryFootprint() {
    // 内存占用评估
}
```

### 5. 边界测试
- 超长消息处理
- 深度嵌套任务（>100层）
- 大量子任务（>10000个）
- 空值和null参数

**测试数据准备：**
```java
@BeforeEach
void setUp() {
    // 初始化测试环境
    TFI.enable();
    contextManager = ContextManager.getInstance();
}

@AfterEach
void tearDown() {
    // 清理测试数据
    TFI.disable();
    contextManager.clear();
}
```

**输出要求：**
1. TaskContextImplTest.java
2. 性能基准测试报告
3. 并发测试场景文档
4. 测试覆盖率报告（>95%）
```

## 第四阶段：优化与重构提示词

```markdown
你是一名代码优化专家。审查并优化TaskContext的实现。

**优化维度：**

### 1. 性能优化
- [ ] 减少对象创建
- [ ] 优化锁竞争
- [ ] 缓存常用数据
- [ ] 批量操作优化

### 2. 内存优化
- [ ] 对象池化Message
- [ ] 弱引用管理子任务
- [ ] 及时释放资源
- [ ] 避免内存泄漏

### 3. 代码质量
- [ ] 提取公共方法
- [ ] 简化复杂逻辑
- [ ] 改进命名
- [ ] 增强可读性

### 4. 扩展性改进
- [ ] 接口扩展点预留
- [ ] 插件机制支持
- [ ] 自定义处理器
- [ ] 事件通知机制

**重构建议：**
1. 将消息处理逻辑抽取为独立组件
2. 使用建造者模式创建TaskContext
3. 引入状态机管理任务状态
4. 实现观察者模式支持扩展

**输出要求：**
1. 优化后的代码
2. 性能对比报告
3. 重构说明文档
4. 最佳实践指南
```

## 综合执行提示词（完整实现）

```markdown
你是TaskFlowInsight的核心开发者，负责实现TaskContext任务上下文管理。

**完整实现流程：**

## Phase 1: 接口设计（15分钟）
设计TaskContext接口，确保：
- 流式API支持
- 资源自动管理
- 线程安全保证
- 扩展性预留

## Phase 2: 核心实现（40分钟）
实现三个关键类：
1. TaskContext接口
2. TaskContextImpl实现类
   - 线程安全机制
   - 异常处理
   - 资源管理
   - 性能优化
3. NullTaskContext空对象

关键代码示例：
```java
public class TaskContextImpl implements TaskContext {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final TaskNode taskNode;
    private final ThreadContext threadContext;
    private final CopyOnWriteArrayList<TaskContext> children;
    
    @Override
    public TaskContext message(String message) {
        if (closed.get()) return this;
        
        try {
            Message msg = messagePool.acquire();
            msg.setContent(message);
            msg.setType(MessageType.INFO);
            msg.setTimestamp(System.currentTimeMillis());
            taskNode.addMessage(msg);
        } catch (Throwable t) {
            // 静默处理，不影响业务
            logError("Failed to add message", t);
        }
        return this;
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // 安全关闭逻辑
            closeChildren();
            updateStatus();
            cleanupResources();
        }
    }
}
```

## Phase 3: 测试验证（25分钟）
编写测试用例：
- 20个功能测试
- 10个异常测试
- 5个并发测试
- 3个性能测试

## Phase 4: 优化调整（10分钟）
- 性能分析与优化
- 内存使用优化
- 代码审查
- 文档完善

**交付标准：**
1. 可编译运行的代码
2. 测试覆盖率>95%
3. 性能达标（<100ns/操作）
4. 零内存泄漏
5. 完整的JavaDoc

**最终输出：**
- TaskContext.java
- TaskContextImpl.java
- NullTaskContext.java
- TaskContextImplTest.java
- 实现报告.md
```