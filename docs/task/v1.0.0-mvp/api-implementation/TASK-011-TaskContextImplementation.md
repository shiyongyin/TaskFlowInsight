# TASK-011: TaskContext任务上下文实现

## 任务概述

TaskContext是TaskFlowInsight的核心用户接口，提供任务级别的操作和封装。它负责为单个任务提供完整的生命周期管理、消息记录、子任务创建等功能，是用户与TFI系统交互的主要入口。

## 需求分析

1. 实现TaskContext接口，提供完整的任务级别操作
2. 集成TaskNode和ThreadContext，实现底层数据管理
3. 提供流式API接口，支持方法链调用
4. 确保线程安全和异常安全
5. 支持自动资源管理（AutoCloseable）

## 技术实现

### 1. TaskContext接口定义
```java
/**
 * 任务上下文接口 - 单个任务的生命周期管理
 * 
 * TaskContext提供任务级别的操作和封装，是用户与TFI系统交互的主要接口。
 * 每个TaskContext实例对应一个TaskNode，提供任务的完整生命周期管理。
 */
public interface TaskContext extends AutoCloseable {
    
    /**
     * 获取任务ID
     * @return 任务唯一标识符
     */
    String getTaskId();
    
    /**
     * 获取任务名称
     * @return 任务名称
     */
    String getTaskName();
    
    /**
     * 获取任务嵌套深度
     * @return 任务深度（根任务为1）
     */
    int getDepth();
    
    /**
     * 获取任务状态
     * @return 任务当前状态
     */
    TaskStatus getStatus();
    
    /**
     * 获取任务开始时间
     * @return 开始时间（纳秒时间戳）
     */
    long getStartTime();
    
    /**
     * 获取任务结束时间
     * @return 结束时间（纳秒时间戳），未结束返回0
     */
    long getEndTime();
    
    /**
     * 获取任务持续时间
     * @return 持续时间（纳秒），未结束返回当前持续时间
     */
    long getDuration();
    
    /**
     * 添加消息到当前任务
     * @param message 消息内容
     * @return 返回this，支持链式调用
     */
    TaskContext message(String message);
    
    /**
     * 添加格式化消息到当前任务
     * @param format 格式字符串
     * @param args 格式参数
     * @return 返回this，支持链式调用
     */
    TaskContext message(String format, Object... args);
    
    /**
     * 创建并开始子任务
     * @param subTaskName 子任务名称
     * @return 子任务的TaskContext
     */
    TaskContext startSubTask(String subTaskName);
    
    /**
     * 结束当前任务
     */
    void end();
    
    /**
     * 关闭资源（实现AutoCloseable）
     */
    @Override
    void close();
    
    /**
     * 获取父任务
     * @return 父任务的TaskContext，如果是根任务返回null
     */
    TaskContext getParent();
    
    /**
     * 获取所有子任务
     * @return 子任务列表的只读副本
     */
    List<TaskContext> getChildren();
    
    /**
     * 获取任务的所有消息
     * @return 消息列表的只读副本
     */
    List<Message> getMessages();
}
```

### 2. TaskContextImpl实现类
```java
/**
 * TaskContext接口的默认实现
 * 提供完整的任务上下文功能，包括生命周期管理、消息记录、子任务创建等
 */
public class TaskContextImpl implements TaskContext {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskContextImpl.class);
    
    private final TaskNode taskNode;
    private final ThreadContext threadContext;
    private final TaskContextImpl parent;
    private final List<TaskContextImpl> children = new CopyOnWriteArrayList<>();
    private volatile boolean closed = false;
    
    /**
     * 构造函数
     * @param taskNode 关联的TaskNode
     * @param threadContext 线程上下文
     * @param parent 父任务上下文
     */
    public TaskContextImpl(TaskNode taskNode, ThreadContext threadContext, TaskContextImpl parent) {
        this.taskNode = Objects.requireNonNull(taskNode, "TaskNode cannot be null");
        this.threadContext = Objects.requireNonNull(threadContext, "ThreadContext cannot be null");
        this.parent = parent;
        
        // 如果有父任务，将自己添加到父任务的子任务列表中
        if (parent != null) {
            parent.addChild(this);
        }
    }
    
    @Override
    public String getTaskId() {
        return taskNode.getTaskId();
    }
    
    @Override
    public String getTaskName() {
        return taskNode.getTaskName();
    }
    
    @Override
    public int getDepth() {
        return taskNode.getDepth();
    }
    
    @Override
    public TaskStatus getStatus() {
        if (closed) {
            return TaskStatus.COMPLETED;
        }
        return taskNode.isCompleted() ? TaskStatus.COMPLETED : TaskStatus.RUNNING;
    }
    
    @Override
    public long getStartTime() {
        return taskNode.getStartTime();
    }
    
    @Override
    public long getEndTime() {
        return taskNode.getEndTime();
    }
    
    @Override
    public long getDuration() {
        return taskNode.getDuration();
    }
    
    @Override
    public TaskContext message(String message) {
        if (closed) {
            LOGGER.debug("Attempted to add message to closed task: {}", getTaskName());
            return this;
        }
        
        if (message != null) {
            Message msg = new Message(message, System.currentTimeMillis(), MessageType.INFO);
            taskNode.addMessage(msg);
        }
        
        return this;
    }
    
    @Override
    public TaskContext message(String format, Object... args) {
        if (closed) {
            LOGGER.debug("Attempted to add formatted message to closed task: {}", getTaskName());
            return this;
        }
        
        try {
            String formattedMessage = String.format(format, args);
            return message(formattedMessage);
        } catch (Exception e) {
            LOGGER.warn("Failed to format message '{}': {}", format, e.getMessage());
            return message("Failed to format message: " + format);
        }
    }
    
    @Override
    public TaskContext startSubTask(String subTaskName) {
        if (closed) {
            LOGGER.debug("Attempted to start subtask on closed task: {}", getTaskName());
            return new DisabledTaskContext(subTaskName);
        }
        
        if (subTaskName == null || subTaskName.trim().isEmpty()) {
            subTaskName = "unnamed-subtask";
        }
        
        try {
            // 创建子任务节点
            TaskNode subTaskNode = new TaskNode(subTaskName, getDepth() + 1, System.nanoTime());
            
            // 将子任务添加到当前任务
            taskNode.addChild(subTaskNode);
            
            // 将子任务压入线程上下文栈
            threadContext.pushTask(subTaskNode);
            
            // 创建子任务上下文
            TaskContextImpl subTaskContext = new TaskContextImpl(subTaskNode, threadContext, this);
            
            LOGGER.debug("Started subtask '{}' under parent '{}'", subTaskName, getTaskName());
            
            return subTaskContext;
            
        } catch (Exception e) {
            LOGGER.error("Failed to start subtask '{}': {}", subTaskName, e.getMessage());
            return new DisabledTaskContext(subTaskName);
        }
    }
    
    @Override
    public void end() {
        if (closed) {
            return;
        }
        
        try {
            // 确保所有子任务都已结束
            for (TaskContextImpl child : children) {
                if (!child.closed) {
                    child.end();
                }
            }
            
            // 完成当前任务节点
            taskNode.complete(System.nanoTime());
            
            // 从线程上下文栈中弹出当前任务
            TaskNode poppedTask = threadContext.popTask();
            if (poppedTask != taskNode) {
                LOGGER.warn("Task stack inconsistency: expected '{}', got '{}'", 
                    getTaskName(), poppedTask != null ? poppedTask.getTaskName() : "null");
            }
            
            closed = true;
            
            LOGGER.debug("Ended task: {} (duration: {} ns)", getTaskName(), getDuration());
            
        } catch (Exception e) {
            LOGGER.error("Error ending task '{}': {}", getTaskName(), e.getMessage());
        }
    }
    
    @Override
    public void close() {
        end();
    }
    
    @Override
    public TaskContext getParent() {
        return parent;
    }
    
    @Override
    public List<TaskContext> getChildren() {
        return Collections.unmodifiableList(new ArrayList<>(children));
    }
    
    @Override
    public List<Message> getMessages() {
        return Collections.unmodifiableList(taskNode.getMessages());
    }
    
    /**
     * 内部方法：添加子任务
     */
    private void addChild(TaskContextImpl child) {
        children.add(child);
    }
    
    /**
     * 获取关联的TaskNode（内部使用）
     */
    public TaskNode getTaskNode() {
        return taskNode;
    }
    
    /**
     * 检查任务是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }
    
    @Override
    public String toString() {
        return String.format("TaskContext{name='%s', id='%s', depth=%d, status=%s}", 
            getTaskName(), getTaskId(), getDepth(), getStatus());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskContextImpl that = (TaskContextImpl) o;
        return Objects.equals(taskNode.getTaskId(), that.taskNode.getTaskId());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(taskNode.getTaskId());
    }
}
```

### 3. TaskStatus枚举
```java
/**
 * 任务状态枚举
 */
public enum TaskStatus {
    /**
     * 任务正在运行
     */
    RUNNING,
    
    /**
     * 任务已完成
     */
    COMPLETED,
    
    /**
     * 任务已取消
     */
    CANCELLED,
    
    /**
     * 任务执行错误
     */
    ERROR
}
```

### 4. 禁用状态TaskContext实现
```java
/**
 * 禁用状态的TaskContext实现
 * 当系统处于异常状态或需要降级时使用
 */
public class DisabledTaskContext implements TaskContext {
    
    private final String taskName;
    private final String taskId;
    private final long startTime;
    
    public DisabledTaskContext(String taskName) {
        this.taskName = taskName != null ? taskName : "disabled-task";
        this.taskId = "disabled-" + System.nanoTime();
        this.startTime = System.nanoTime();
    }
    
    @Override
    public String getTaskId() {
        return taskId;
    }
    
    @Override
    public String getTaskName() {
        return taskName;
    }
    
    @Override
    public int getDepth() {
        return 0;
    }
    
    @Override
    public TaskStatus getStatus() {
        return TaskStatus.COMPLETED;
    }
    
    @Override
    public long getStartTime() {
        return startTime;
    }
    
    @Override
    public long getEndTime() {
        return startTime; // 立即结束
    }
    
    @Override
    public long getDuration() {
        return 0;
    }
    
    @Override
    public TaskContext message(String message) {
        // 不执行任何操作
        return this;
    }
    
    @Override
    public TaskContext message(String format, Object... args) {
        // 不执行任何操作
        return this;
    }
    
    @Override
    public TaskContext startSubTask(String subTaskName) {
        // 返回另一个禁用状态的上下文
        return new DisabledTaskContext(subTaskName);
    }
    
    @Override
    public void end() {
        // 不执行任何操作
    }
    
    @Override
    public void close() {
        // 不执行任何操作
    }
    
    @Override
    public TaskContext getParent() {
        return null;
    }
    
    @Override
    public List<TaskContext> getChildren() {
        return Collections.emptyList();
    }
    
    @Override
    public List<Message> getMessages() {
        return Collections.emptyList();
    }
    
    @Override
    public String toString() {
        return String.format("DisabledTaskContext{name='%s'}", taskName);
    }
}
```

### 5. TaskContextFactory工厂类
```java
/**
 * TaskContext工厂类
 * 提供TaskContext实例的创建和管理
 */
public class TaskContextFactory {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskContextFactory.class);
    
    /**
     * 创建根任务上下文
     * @param taskName 任务名称
     * @param threadContext 线程上下文
     * @return TaskContext实例
     */
    public static TaskContext createRootTask(String taskName, ThreadContext threadContext) {
        if (taskName == null || taskName.trim().isEmpty()) {
            taskName = "root-task";
        }
        
        try {
            TaskNode rootTaskNode = new TaskNode(taskName, 1, System.nanoTime());
            threadContext.pushTask(rootTaskNode);
            
            return new TaskContextImpl(rootTaskNode, threadContext, null);
            
        } catch (Exception e) {
            LOGGER.error("Failed to create root task '{}': {}", taskName, e.getMessage());
            return new DisabledTaskContext(taskName);
        }
    }
    
    /**
     * 创建子任务上下文
     * @param subTaskName 子任务名称
     * @param parent 父任务上下文
     * @return TaskContext实例
     */
    public static TaskContext createSubTask(String subTaskName, TaskContextImpl parent) {
        if (subTaskName == null || subTaskName.trim().isEmpty()) {
            subTaskName = "sub-task";
        }
        
        if (parent == null) {
            LOGGER.error("Cannot create subtask '{}' without parent", subTaskName);
            return new DisabledTaskContext(subTaskName);
        }
        
        try {
            TaskNode subTaskNode = new TaskNode(subTaskName, parent.getDepth() + 1, System.nanoTime());
            parent.getTaskNode().addChild(subTaskNode);
            
            // 获取线程上下文并压入新任务
            ThreadContext threadContext = ContextManager.getInstance().getOrCreateThreadContext();
            threadContext.pushTask(subTaskNode);
            
            return new TaskContextImpl(subTaskNode, threadContext, parent);
            
        } catch (Exception e) {
            LOGGER.error("Failed to create subtask '{}': {}", subTaskName, e.getMessage());
            return new DisabledTaskContext(subTaskName);
        }
    }
    
    /**
     * 创建禁用状态的任务上下文
     * @param taskName 任务名称
     * @return 禁用状态的TaskContext
     */
    public static TaskContext createDisabledTask(String taskName) {
        return new DisabledTaskContext(taskName);
    }
}
```

## 关键特性

### 生命周期管理特性
1. **自动资源管理特性**：实现AutoCloseable，支持try-with-resources
2. **层级管理特性**：支持父子任务关系和嵌套层级管理
3. **状态追踪特性**：完整的任务状态生命周期追踪
4. **时间测量特性**：纳秒精度的时间测量和持续时间计算

### 流式API特性
1. **方法链调用特性**：支持流式编程风格
2. **简洁接口特性**：提供简洁易用的API接口
3. **类型安全特性**：完整的类型安全保障
4. **null安全特性**：防御性编程，处理null输入

### 异常安全特性
1. **异常隔离特性**：内部异常不影响用户代码
2. **降级机制特性**：异常情况下自动降级为禁用状态
3. **资源清理特性**：异常情况下确保资源正确清理
4. **日志记录特性**：完整的异常和操作日志记录

## 验收标准

### 功能验收
- [ ] TaskContext接口完整实现
- [ ] 支持任务生命周期的完整管理
- [ ] 流式API正常工作
- [ ] 父子任务关系正确建立

### 健壮性验收
- [ ] 异常情况下不影响业务逻辑
- [ ] 资源正确清理，无内存泄漏
- [ ] null和边界条件正确处理
- [ ] 并发场景下线程安全

### 性能验收
- [ ] 任务创建和销毁开销小于100纳秒
- [ ] 消息添加开销小于50纳秒
- [ ] 内存使用合理，每个TaskContext小于1KB
- [ ] 支持1000+层级嵌套不出现栈溢出

## 依赖关系

### 前置依赖
- TASK-003: TaskNode任务节点实现
- TASK-004: ThreadContext线程上下文实现
- TASK-005: ContextManager上下文管理器

### 后置依赖
- TASK-012: API异常安全处理实现
- TASK-013: API接口单元测试

## 开发计划

### 分阶段开发计划
- **Day 1**: TaskContext接口和基础实现
- **Day 2**: 禁用状态实现和工厂类
- **Day 3**: 异常处理和性能优化

## 风险评估

### 技术风险
1. **复杂度风险**：任务层级管理复杂，可能出现状态不一致
   - 缓解措施：严格的状态管理和完整的单元测试
2. **性能风险**：深层嵌套可能影响性能
   - 缓解措施：性能基准测试和优化

### 业务风险
1. **API设计风险**：接口设计可能不符合用户习惯
   - 缓解措施：参考业界最佳实践和用户反馈
2. **兼容性风险**：future版本可能需要API变更
   - 缓解措施：保守的API设计和版本兼容策略

## 实现文件

1. **TaskContext接口** (`TaskContext.java`)
2. **TaskContextImpl实现类** (`TaskContextImpl.java`)
3. **TaskStatus枚举** (`TaskStatus.java`)
4. **禁用状态实现** (`DisabledTaskContext.java`)
5. **TaskContext工厂** (`TaskContextFactory.java`)
6. **TaskContext单元测试** (`TaskContextTest.java`)