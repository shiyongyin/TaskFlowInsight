# 开发规范和标准 v1.0.0-MVP

## 概述

本文档定义 TaskFlow Insight MVP 版本开发过程中的技术规范、代码标准和最佳实践，确保代码质量和团队协作效率。

## 代码规范

### 1. 基础规范

#### 命名规范

```java
// 类名：使用 PascalCase
public class TaskFlowInsight { }
public class ContextManager { }

// 接口名：使用 PascalCase，可以 I 开头（可选）
public interface TaskContext { }
public interface ISessionManager { } // 可选风格

// 方法名：使用 camelCase，动词开头
public void startTask(String name) { }
public TaskNode getCurrentTask() { }
public boolean isActive() { }

// 变量名：使用 camelCase
private String taskName;
private long startTimeNanos;
private List<TaskNode> childNodes;

// 常量：使用 UPPER_SNAKE_CASE
public static final int MAX_DEPTH = 100;
public static final String DEFAULT_SESSION_ID = "default";

// 包名：使用 lowercase，层级清晰
com.syy.taskflowinsight.api
com.syy.taskflowinsight.core.context
com.syy.taskflowinsight.model
```

#### 文件组织

```
src/main/java/com/syy/taskflowinsight/
├── api/              # 公开API接口
│   ├── TFI.java
│   └── TaskContext.java
├── core/             # 核心实现
│   ├── context/      # 上下文管理
│   ├── session/      # 会话管理
│   └── timer/        # 计时器
├── model/            # 数据模型
│   ├── Session.java
│   ├── TaskNode.java
│   └── Message.java
├── exporter/         # 导出功能
│   ├── ConsoleExporter.java
│   └── JsonExporter.java
└── util/             # 工具类
    └── TimeUtils.java
```

### 2. 代码风格

#### 代码格式

```java
// 类定义：注释 + 注解 + 修饰符 + 类名
/**
 * TaskFlow Insight 主入口类
 * 提供静态方法访问核心功能
 * 
 * @author TaskFlow Team
 * @since 1.0.0
 */
@Component
public final class TFI {
    
    // 常量定义在最前面
    private static final Logger LOGGER = LoggerFactory.getLogger(TFI.class);
    private static final int MAX_DEPTH = 100;
    
    // 静态字段
    private static volatile boolean enabled = true;
    
    // 实例字段
    private final ContextManager contextManager;
    
    // 构造函数
    public TFI(ContextManager contextManager) {
        this.contextManager = Objects.requireNonNull(contextManager);
    }
    
    // 公开方法
    public static TaskContext start(String taskName) {
        validateTaskName(taskName);
        return getInstance().doStart(taskName);
    }
    
    // 私有方法
    private TaskContext doStart(String taskName) {
        return contextManager.startTask(taskName);
    }
    
    // 工具方法
    private static void validateTaskName(String taskName) {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("Task name cannot be null or empty");
        }
    }
}
```

#### 方法设计原则

```java
public class MethodDesignExamples {
    
    // 好的方法设计：单一职责，参数清晰
    public TaskNode createTaskNode(String name, int depth) {
        validateName(name);
        validateDepth(depth);
        
        return new TaskNode(name, depth, System.nanoTime());
    }
    
    // 避免：参数过多
    public TaskNode createTaskNode(String name, int depth, long startTime, 
                                 TaskStatus status, List<Message> messages, 
                                 Map<String, Object> attributes) {
        // 太多参数，使用Builder模式
    }
    
    // 推荐：使用Builder模式处理复杂对象
    public static class TaskNodeBuilder {
        private String name;
        private int depth;
        private long startTime = System.nanoTime();
        private TaskStatus status = TaskStatus.RUNNING;
        
        public TaskNodeBuilder name(String name) {
            this.name = name;
            return this;
        }
        
        public TaskNode build() {
            return new TaskNode(name, depth, startTime, status);
        }
    }
    
    // 好的异常处理
    public void stopTask() {
        try {
            TaskNode current = getCurrentTask();
            if (current == null) {
                throw new IllegalStateException("No active task to stop");
            }
            current.stop();
        } catch (Exception e) {
            LOGGER.warn("Failed to stop task: {}", e.getMessage(), e);
            throw new TaskFlowException("Stop task failed", e);
        }
    }
}
```

### 3. 注释规范

#### JavaDoc 注释

```java
/**
 * 任务上下文接口，提供任务级别的操作方法
 * 
 * <p>使用示例：
 * <pre>{@code
 * TaskContext task = TFI.start("myTask");
 * task.message("Processing...");
 * TFI.stop();
 * }</pre>
 * 
 * @author TaskFlow Team
 * @since 1.0.0
 * @see TFI#start(String)
 */
public interface TaskContext {
    
    /**
     * 添加信息消息到当前任务
     * 
     * @param content 消息内容，不能为 null
     * @return 当前任务上下文，支持链式调用
     * @throws IllegalArgumentException 如果 content 为 null
     * @throws IllegalStateException 如果任务已结束
     */
    TaskContext message(String content);
    
    /**
     * 获取任务执行持续时间
     * 
     * @return 持续时间，单位毫秒；如果任务未结束，返回当前持续时间
     */
    long getDurationMs();
}
```

#### 行内注释

```java
public class CommentExamples {
    
    public void processTask() {
        // 验证任务状态
        if (!isTaskValid()) {
            return;
        }
        
        // 记录开始时间（使用纳秒精度）
        long startNano = System.nanoTime();
        
        try {
            // 执行核心业务逻辑
            executeBusinessLogic();
        } catch (BusinessException e) {
            // 记录异常但不阻断流程
            LOGGER.warn("Business logic failed: {}", e.getMessage());
        } finally {
            // 确保清理资源
            cleanup();
        }
    }
    
    // FIXME: 临时解决方案，需要重构
    private void temporaryWorkaround() {
        // TODO: 实现更优雅的解决方案
    }
}
```

## 性能规范

### 1. 性能目标

```java
/**
 * 性能基准和目标
 */
public class PerformanceTargets {
    
    // API 调用开销目标
    public static final long START_STOP_MAX_NANOS = 1_000;      // 1微秒
    public static final long MESSAGE_MAX_NANOS = 500;           // 0.5微秒
    public static final double CPU_OVERHEAD_MAX_PERCENT = 5.0;  // 5%
    
    // 内存使用目标
    public static final long BASE_MEMORY_MB = 5;                // 5MB基础占用
    public static final long PER_NODE_BYTES = 1024;            // 每节点1KB
    
    // 并发性能目标  
    public static final int MAX_CONCURRENT_THREADS = 1000;     // 1000并发线程
    public static final long CONTEXT_SWITCH_MAX_NANOS = 100;   // 100纳秒上下文切换
}
```

### 2. 性能优化技巧

```java
public class PerformanceOptimizations {
    
    // 1. 对象池化避免GC压力
    private static final ThreadLocal<Deque<TaskNode>> NODE_POOL = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(16));
    
    public TaskNode acquireNode(String name, int depth) {
        Deque<TaskNode> pool = NODE_POOL.get();
        TaskNode node = pool.pollFirst();
        if (node == null) {
            node = new TaskNode();
        }
        return node.reset(name, depth);
    }
    
    public void releaseNode(TaskNode node) {
        node.clear();
        NODE_POOL.get().offerFirst(node);
    }
    
    // 2. 延迟计算，避免不必要的操作
    public class LazyComputationExample {
        private String expensiveResult;
        
        public String getExpensiveResult() {
            if (expensiveResult == null) {
                expensiveResult = computeExpensiveOperation();
            }
            return expensiveResult;
        }
    }
    
    // 3. 使用位运算优化状态检查
    private static final int STATUS_RUNNING = 1 << 0;  // 0001
    private static final int STATUS_COMPLETED = 1 << 1; // 0010
    private static final int STATUS_ERROR = 1 << 2;     // 0100
    
    private int status = STATUS_RUNNING;
    
    public boolean isCompleted() {
        return (status & STATUS_COMPLETED) != 0;
    }
    
    // 4. 避免装箱/拆箱
    private long[] timings = new long[1000];  // 而不是 List<Long>
    
    // 5. 字符串拼接优化
    public String formatMessage(String prefix, Object value) {
        // 好的做法：使用StringBuilder
        return new StringBuilder(prefix.length() + 32)
                .append(prefix)
                .append(": ")
                .append(value)
                .toString();
        
        // 避免：字符串连接
        // return prefix + ": " + value;  // 创建多个临时对象
    }
}
```

### 3. 内存管理

```java
public class MemoryManagement {
    
    // 1. 合理的集合初始化容量
    public TaskNode(String name) {
        this.children = new ArrayList<>(8);      // 预期子任务数
        this.messages = new ArrayList<>(4);      // 预期消息数
        this.attributes = new HashMap<>(4);      // 预期属性数
    }
    
    // 2. 及时清理大对象
    public void cleanup() {
        if (largeDataStructure != null) {
            largeDataStructure.clear();
            largeDataStructure = null;
        }
    }
    
    // 3. 使用弱引用避免内存泄漏
    private final Map<String, WeakReference<Session>> sessionCache = 
        new ConcurrentHashMap<>();
    
    // 4. 限制集合大小
    private static final int MAX_MESSAGES = 1000;
    
    public void addMessage(Message message) {
        if (messages.size() >= MAX_MESSAGES) {
            messages.remove(0); // 移除最旧的消息
        }
        messages.add(message);
    }
}
```

## 测试规范

### 1. 测试分层

```java
// 1. 单元测试：测试单个类/方法
@ExtendWith(MockitoExtension.class)
class TaskContextTest {
    
    @Mock
    private TaskNode mockNode;
    
    @Test
    void testMessageAdding() {
        TaskContext context = new TaskContext(mockNode);
        
        context.message("test message");
        
        verify(mockNode).addMessage(any(Message.class));
    }
    
    @Test
    void testDurationCalculation() {
        when(mockNode.getSelfDurationMs()).thenReturn(100L);
        TaskContext context = new TaskContext(mockNode);
        
        long duration = context.getDurationMs();
        
        assertEquals(100L, duration);
    }
}

// 2. 集成测试：测试组件间交互
@SpringBootTest
class TFIIntegrationTest {
    
    @Test
    void testFullWorkflow() {
        TFI.start("parentTask");
        TFI.message("parent message");
        
        TFI.start("childTask");
        TFI.message("child message");
        TFI.stop();
        
        TFI.stop();
        
        Session session = TFI.getCurrentSession();
        assertNotNull(session);
        assertEquals("parentTask", session.getRoot().getName());
    }
}

// 3. 性能测试
@Test
void testPerformanceOverhead() {
    int iterations = 10000;
    
    // 基准测试
    long baselineTime = measureBaseline(iterations);
    
    // TFI测试  
    long tfiTime = measureWithTFI(iterations);
    
    // 验证性能开销 < 5%
    double overhead = (double)(tfiTime - baselineTime) / baselineTime;
    assertTrue(overhead < 0.05, "Performance overhead too high: " + overhead);
}
```

### 2. 测试数据准备

```java
public class TestDataFactory {
    
    public static Session createTestSession() {
        Session session = new Session(Thread.currentThread().getId());
        session.setRoot(createTestTaskNode("root", 0));
        return session;
    }
    
    public static TaskNode createTestTaskNode(String name, int depth) {
        TaskNode node = new TaskNode(name, depth);
        node.addMessage(new Message(MessageType.INFO, "test message"));
        return node;
    }
    
    public static List<TaskNode> createNestedTaskTree(int depth, int childrenPerLevel) {
        List<TaskNode> nodes = new ArrayList<>();
        TaskNode root = createTestTaskNode("root", 0);
        nodes.add(root);
        
        createChildren(root, depth - 1, childrenPerLevel, nodes);
        return nodes;
    }
    
    private static void createChildren(TaskNode parent, int remainingDepth, 
                                     int childrenPerLevel, List<TaskNode> allNodes) {
        if (remainingDepth <= 0) return;
        
        for (int i = 0; i < childrenPerLevel; i++) {
            TaskNode child = createTestTaskNode("child-" + i, parent.getDepth() + 1);
            parent.addChild(child);
            allNodes.add(child);
            
            createChildren(child, remainingDepth - 1, childrenPerLevel, allNodes);
        }
    }
}
```

### 3. 测试覆盖率要求

```yaml
# jacoco 配置要求
coverage:
  minimum: 80%           # 最低覆盖率
  excluded:
    - "**/*Test.class"   # 排除测试类
    - "**/*Exception.class"  # 排除异常类
  
  rules:
    - element: CLASS
      minimum: 70%       # 类级别最低70%
    - element: METHOD
      minimum: 80%       # 方法级别最低80%
    - element: LINE
      minimum: 85%       # 行级别最低85%
```

## 错误处理规范

### 1. 异常设计

```java
// 1. 自定义异常层次
public class TaskFlowException extends RuntimeException {
    public TaskFlowException(String message) {
        super(message);
    }
    
    public TaskFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class TaskFlowConfigurationException extends TaskFlowException {
    public TaskFlowConfigurationException(String message) {
        super("Configuration error: " + message);
    }
}

// 2. 异常处理策略
public class ExceptionHandlingExample {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandlingExample.class);
    
    public void publicApiMethod() {
        try {
            internalOperation();
        } catch (Exception e) {
            // 记录内部错误，但不暴露给用户
            LOGGER.error("Internal TFI error: {}", e.getMessage(), e);
            
            // 根据策略决定是否重新抛出
            if (isUserError(e)) {
                throw new TaskFlowException("Invalid operation", e);
            }
            // 内部错误静默处理，不影响业务
        }
    }
    
    // 3. 参数验证
    public TaskContext start(String taskName) {
        // 快速失败，立即检查参数
        Objects.requireNonNull(taskName, "taskName cannot be null");
        if (taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("taskName cannot be empty");
        }
        
        return doStart(taskName);
    }
    
    // 4. 资源清理
    public void operationWithCleanup() {
        Resource resource = null;
        try {
            resource = acquireResource();
            processResource(resource);
        } catch (Exception e) {
            LOGGER.error("Operation failed: {}", e.getMessage());
            throw new TaskFlowException("Operation failed", e);
        } finally {
            // 确保资源清理
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    LOGGER.warn("Failed to close resource: {}", e.getMessage());
                }
            }
        }
    }
}
```

### 2. 日志规范

```java
public class LoggingStandards {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingStandards.class);
    
    public void demonstrateLoggingLevels() {
        // ERROR: 系统错误，需要立即关注
        LOGGER.error("Failed to start task '{}': {}", taskName, e.getMessage(), e);
        
        // WARN: 警告信息，系统可以继续运行
        LOGGER.warn("Task depth {} exceeds recommended limit {}", depth, RECOMMENDED_DEPTH);
        
        // INFO: 关键业务信息
        LOGGER.info("Session {} completed with {} tasks in {}ms", 
                    sessionId, taskCount, duration);
        
        // DEBUG: 详细调试信息
        LOGGER.debug("Creating task node: name={}, depth={}, parent={}", 
                     name, depth, parentId);
        
        // TRACE: 最详细的跟踪信息
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Task stack state: {}", taskStack);
        }
    }
    
    // 结构化日志
    public void structuredLogging() {
        MDC.put("sessionId", session.getSessionId());
        MDC.put("threadId", String.valueOf(session.getThreadId()));
        
        try {
            LOGGER.info("Processing task: {}", taskName);
            // 业务逻辑
        } finally {
            MDC.clear();
        }
    }
}
```

## 版本控制规范

### 1. Git 工作流

```bash
# 分支命名规范
main                    # 主分支，稳定版本
develop                 # 开发分支
feature/task-tracking   # 功能分支
bugfix/memory-leak     # 缺陷修复分支
hotfix/security-fix    # 紧急修复分支

# 提交信息规范
feat: 添加基础任务追踪功能
fix: 修复ThreadLocal内存泄漏问题
docs: 更新API文档
test: 添加并发安全测试用例
refactor: 重构上下文管理器
perf: 优化任务节点创建性能
```

### 2. 代码审查检查清单

```markdown
## Code Review Checklist

### 功能性
- [ ] 代码实现了需求规格中的功能
- [ ] 边界条件处理正确
- [ ] 错误处理适当
- [ ] API 设计合理

### 质量性
- [ ] 代码风格符合规范
- [ ] 命名清晰明确
- [ ] 注释充分且准确
- [ ] 无重复代码

### 性能性
- [ ] 无明显性能问题
- [ ] 内存使用合理
- [ ] 算法复杂度适当
- [ ] 并发安全性

### 测试性
- [ ] 单元测试覆盖充分
- [ ] 测试用例有意义
- [ ] 边界测试完整
- [ ] 性能测试通过
```

## 发布规范

### 1. 版本发布检查清单

```markdown
## Release Checklist v1.0.0-MVP

### 代码质量
- [ ] 所有单元测试通过 (>80% 覆盖率)
- [ ] 集成测试通过
- [ ] 性能测试达标
- [ ] 内存泄漏测试通过
- [ ] 代码审查完成

### 文档
- [ ] API 文档更新
- [ ] 使用指南完整
- [ ] 变更日志更新
- [ ] README 更新

### 构建和部署
- [ ] Maven 构建成功
- [ ] JAR 包签名
- [ ] 依赖检查通过
- [ ] 许可证检查通过

### 验收
- [ ] 功能验收测试通过
- [ ] 性能验收测试通过
- [ ] 用户验收测试通过
```

### 2. 发布流程

```bash
# 1. 准备发布分支
git checkout -b release/v1.0.0-mvp develop

# 2. 更新版本号
mvn versions:set -DnewVersion=1.0.0-mvp

# 3. 运行完整测试套件
mvn clean test

# 4. 构建发布包
mvn clean package -P release

# 5. 部署到测试环境
# 执行验收测试

# 6. 合并到主分支
git checkout main
git merge --no-ff release/v1.0.0-mvp

# 7. 创建发布标签
git tag -a v1.0.0-mvp -m "Release version 1.0.0-mvp"

# 8. 推送到远程仓库
git push origin main
git push origin v1.0.0-mvp
```

这套开发规范确保了 MVP 版本的代码质量和团队协作效率，为后续版本的开发奠定了坚实的基础。