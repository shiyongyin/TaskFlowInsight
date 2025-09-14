# DEV-014: 控制台输出实现 - AI开发提示词

## 第一阶段：需求澄清提示词

```markdown
你是一名资深Java输出格式化专家，精通控制台树形结构展示和ASCII艺术。现在需要你评审并澄清ConsoleExporter的实现需求。

**输入材料：**
- 任务卡：docs/task/v1.0.0-mvp/output-implementation/TASK-014-ConsoleOutput.md
- 设计文档：docs/develop/v1.0.0-mvp/design/output-implementation/DEV-014-控制台输出实现.md
- 现有实现：src/main/java/com/syy/taskflowinsight/api/ConsoleExporter.java
- 数据模型：Session、TaskNode、Message（MessageType只有INFO和ERROR）
- 项目结构：Spring Boot 3.5.5、Java 21、Maven

**评审要求：**
1. 验证输出格式设计的美观性和可读性
2. 检查ASCII树形绘制算法的正确性
3. 评估性能目标（1000节点<10ms）的可行性
4. 确认与现有数据模型的兼容性
5. 识别特殊字符和对齐问题

**输出格式：**
生成问题清单文件：docs/task/v1.0.0-mvp/output-implementation/DEV-014-Questions.md

问题清单结构：
# DEV-014 需求澄清问题清单

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
你是一名资深Java开发工程师，精通文本格式化和树形数据结构展示。现在需要你优化或重构ConsoleExporter类。

**角色定位：** Java性能优化专家 + 文本格式化专家 + 树形算法专家

**技术约束：**
- Java 21 + Spring Boot 3.5.5
- 不使用第三方格式化库
- 使用StringBuilder进行高效字符串构建
- KISS原则，避免过度设计

**核心需求：**
1. 树形结构展示
   - 使用ASCII字符（├──, └──, │, 空格）绘制树形结构
   - 支持任意深度的嵌套（建议最大深度50）
   - 正确处理最后节点的特殊标记

2. 会话信息格式化
   - 显示Session ID、Thread ID、Status、Duration
   - 使用分隔线增强可读性
   - 时间格式化为可读形式（如：1250ms、2.5s）

3. 任务节点信息
   - 显示任务名称、耗时、状态
   - 缩进表示层级关系
   - 支持任务路径显示（可选）

4. 消息显示
   - [INFO]和[ERROR]标记
   - 消息内容缩进对齐
   - 按时间顺序显示

**性能要求：**
- 1000个节点生成时间 < 10ms
- 内存使用 < 1MB临时缓存
- StringBuilder初始容量优化（建议：节点数 * 100）

**代码规范：**
// 类结构
public class ConsoleExporter {
    // 常量定义
    private static final String BRANCH = "├── ";
    private static final String LAST_BRANCH = "└── ";
    private static final String VERTICAL = "│   ";
    private static final String SPACE = "    ";
    
    // 导出方法
    public String export(Session session) {
        // 参数验证
        // StringBuilder初始化
        // 格式化头部
        // 递归处理任务树
        // 返回结果
    }
    
    // 输出到流
    public void print(Session session) {
        System.out.print(export(session));
    }
    
    public void print(Session session, PrintStream out) {
        out.print(export(session));
    }
    
    // 私有方法
    private void appendHeader(StringBuilder sb, Session session) {}
    private void appendTaskNode(StringBuilder sb, TaskNode node, String prefix, boolean isLast) {}
    private void appendMessages(StringBuilder sb, List<Message> messages, String prefix) {}
    private String formatDuration(long millis) {}
}

**关键算法：递归树遍历**
private void appendTaskNode(StringBuilder sb, TaskNode node, String prefix, boolean isLast) {
    // 1. 绘制当前节点连接线
    sb.append(prefix);
    sb.append(isLast ? LAST_BRANCH : BRANCH);
    
    // 2. 输出节点信息
    sb.append(node.getName());
    sb.append(" (").append(formatDuration(node.getDurationMs()));
    sb.append(", ").append(node.getStatus()).append(")");
    sb.append("\n");
    
    // 3. 输出节点消息
    if (!node.getMessages().isEmpty()) {
        String msgPrefix = prefix + (isLast ? SPACE : VERTICAL);
        appendMessages(sb, node.getMessages(), msgPrefix);
    }
    
    // 4. 递归处理子节点
    List<TaskNode> children = node.getChildren();
    for (int i = 0; i < children.size(); i++) {
        String childPrefix = prefix + (isLast ? SPACE : VERTICAL);
        boolean isLastChild = (i == children.size() - 1);
        appendTaskNode(sb, children.get(i), childPrefix, isLastChild);
    }
}

**优化要点：**
1. StringBuilder预分配：new StringBuilder(estimateSize(session))
2. 字符串常量复用：使用static final
3. 避免字符串拼接：使用append链式调用
4. 缓存格式化结果：时间格式化缓存

**错误处理：**
- null session：返回空字符串或默认消息
- 空任务树：显示"No tasks executed"
- 超长任务名：截断或换行处理

请基于现有代码进行优化，保持向后兼容性。
```

## 第三阶段：单元测试提示词

```markdown
你是一名资深测试工程师，精通JUnit 5和测试驱动开发。现在需要你为ConsoleExporter编写完整的测试套件。

**测试框架：** JUnit 5 + AssertJ（如可用）

**测试策略：**
1. 单元测试 - 隔离测试各个方法
2. 集成测试 - 测试完整导出流程
3. 性能测试 - 验证性能指标
4. 边界测试 - 极端条件验证

**测试类结构：**
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsoleExporterTest {
    
    private ConsoleExporter exporter;
    private Session testSession;
    
    @BeforeEach
    void setUp() {
        exporter = new ConsoleExporter();
        testSession = createTestSession();
    }
    
    // ========== 基础功能测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("应该正确导出简单会话")
    void shouldExportSimpleSession() {
        // Given
        Session session = createSimpleSession();
        
        // When
        String result = exporter.export(session);
        
        // Then
        assertThat(result).contains("Session:");
        assertThat(result).contains("└── Main Task");
        // 验证格式正确性
        verifyTreeStructure(result);
    }
    
    @Test
    @Order(2)
    @DisplayName("应该正确处理null会话")
    void shouldHandleNullSession() {
        // When
        String result = exporter.export(null);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty().or().contains("No session");
    }
    
    // ========== 树形结构测试 ==========
    
    @Test
    @Order(10)
    @DisplayName("应该正确绘制多层嵌套结构")
    void shouldDrawNestedStructure() {
        // Given - 创建3层嵌套，每层2个子节点
        Session session = createNestedSession(3, 2);
        
        // When
        String result = exporter.export(session);
        
        // Then
        // 验证缩进正确
        assertThat(countOccurrences(result, "│   ")).isGreaterThan(0);
        assertThat(countOccurrences(result, "├── ")).isGreaterThan(0);
        assertThat(countOccurrences(result, "└── ")).isGreaterThan(0);
        
        // 验证层级关系
        String[] lines = result.split("\n");
        verifyIndentation(lines);
    }
    
    @Test
    @Order(11)
    @DisplayName("应该正确标记最后节点")
    void shouldMarkLastNode() {
        // Given
        TaskNode root = new TaskNode("root");
        root.addChild(new TaskNode("child1"));
        root.addChild(new TaskNode("child2"));
        root.addChild(new TaskNode("lastChild"));
        
        Session session = new Session();
        session.setRoot(root);
        
        // When
        String result = exporter.export(session);
        
        // Then
        assertThat(result).contains("└── lastChild");
        assertThat(result).doesNotContain("├── lastChild");
    }
    
    // ========== 消息格式化测试 ==========
    
    @Test
    @Order(20)
    @DisplayName("应该正确格式化INFO和ERROR消息")
    void shouldFormatMessages() {
        // Given
        TaskNode node = new TaskNode("task");
        node.addMessage(new Message(MessageType.INFO, "Info message"));
        node.addMessage(new Message(MessageType.ERROR, "Error occurred"));
        
        Session session = new Session();
        session.setRoot(node);
        
        // When
        String result = exporter.export(session);
        
        // Then
        assertThat(result).contains("[INFO] Info message");
        assertThat(result).contains("[ERROR] Error occurred");
    }
    
    // ========== 性能测试 ==========
    
    @Test
    @Order(30)
    @DisplayName("应该在10ms内处理1000个节点")
    void shouldHandle1000NodesWithinTimeLimit() {
        // Given
        Session session = createLargeSession(1000);
        
        // When
        long startTime = System.nanoTime();
        String result = exporter.export(session);
        long duration = (System.nanoTime() - startTime) / 1_000_000; // 转换为毫秒
        
        // Then
        assertThat(duration).isLessThan(10);
        assertThat(result).isNotEmpty();
        System.out.println("处理1000个节点耗时: " + duration + "ms");
    }
    
    @Test
    @Order(31)
    @DisplayName("内存使用应该小于1MB")
    void shouldUseMemoryEfficiently() {
        // Given
        Session session = createLargeSession(1000);
        Runtime runtime = Runtime.getRuntime();
        
        // When
        runtime.gc(); // 强制GC
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        
        String result = exporter.export(session);
        
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memUsed = (memAfter - memBefore) / 1024 / 1024; // MB
        
        // Then
        assertThat(memUsed).isLessThan(1);
        System.out.println("内存使用: " + memUsed + "MB");
    }
    
    // ========== 边界条件测试 ==========
    
    @Test
    @Order(40)
    @DisplayName("应该处理超长任务名")
    void shouldHandleLongTaskNames() {
        // Given
        String longName = "A".repeat(200);
        TaskNode node = new TaskNode(longName);
        Session session = new Session();
        session.setRoot(node);
        
        // When
        String result = exporter.export(session);
        
        // Then
        assertThat(result).contains(longName.substring(0, 50)); // 至少包含前50个字符
    }
    
    @Test
    @Order(41)
    @DisplayName("应该处理深度嵌套（50层）")
    void shouldHandleDeepNesting() {
        // Given
        Session session = createDeepSession(50);
        
        // When/Then - 不应抛出StackOverflowError
        assertDoesNotThrow(() -> {
            String result = exporter.export(session);
            assertThat(result).isNotEmpty();
        });
    }
    
    // ========== 辅助方法 ==========
    
    private Session createSimpleSession() {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID().toString());
        session.setThreadId(1L);
        session.setStatus(SessionStatus.COMPLETED);
        
        TaskNode root = new TaskNode("Main Task");
        root.setStatus(TaskStatus.COMPLETED);
        root.setDurationMs(100);
        session.setRoot(root);
        
        return session;
    }
    
    private Session createNestedSession(int depth, int width) {
        Session session = new Session();
        session.setRoot(createTaskTree(depth, width, "root"));
        return session;
    }
    
    private TaskNode createTaskTree(int depth, int width, String name) {
        TaskNode node = new TaskNode(name);
        if (depth > 0) {
            for (int i = 0; i < width; i++) {
                node.addChild(createTaskTree(depth - 1, width, name + "-" + i));
            }
        }
        return node;
    }
    
    private void verifyTreeStructure(String output) {
        // 验证基本树形结构元素存在
        assertTrue(output.contains("├── ") || output.contains("└── "));
    }
    
    private void verifyIndentation(String[] lines) {
        // 验证缩进递增规律
        // 实现缩进验证逻辑
    }
}

**测试数据构建器：**
class TestDataBuilder {
    static Session sessionWithTasks(int count) { /* ... */ }
    static Session sessionWithDepth(int depth) { /* ... */ }
    static Session sessionWithMessages(MessageType... types) { /* ... */ }
}

**断言辅助类：**
class ConsoleOutputAssert {
    static void assertValidTreeStructure(String output) { /* ... */ }
    static void assertProperAlignment(String output) { /* ... */ }
}

**覆盖率要求：**
- 行覆盖率 ≥ 95%
- 分支覆盖率 ≥ 90%
- 方法覆盖率 = 100%

请生成完整的测试代码，确保所有边界条件都被覆盖。
```

## 第四阶段：性能优化提示词

```markdown
你是一名Java性能优化专家。ConsoleExporter当前性能未达标，需要优化。

**当前性能指标：**
- 1000节点处理时间：12ms（目标<10ms）
- 内存使用：1.2MB（目标<1MB）

**性能分析结果：**
1. StringBuilder频繁扩容（30%时间）
2. 字符串拼接开销（20%时间）
3. 递归调用开销（15%时间）
4. 格式化操作（10%时间）

**优化策略：**

1. StringBuilder容量优化
```java
private static int estimateCapacity(Session session) {
    int nodeCount = countNodes(session.getRoot());
    int avgLineLength = 80; // 平均每行80字符
    int linesPerNode = 2; // 每个节点约2行
    return nodeCount * avgLineLength * linesPerNode;
}

public String export(Session session) {
    StringBuilder sb = new StringBuilder(estimateCapacity(session));
    // ...
}
```

2. 字符串常量池优化
```java
private static final class StringCache {
    static final String[] INDENTS = new String[50];
    static {
        for (int i = 0; i < INDENTS.length; i++) {
            INDENTS[i] = SPACE.repeat(i);
        }
    }
    
    static String getIndent(int level) {
        return level < INDENTS.length ? INDENTS[level] : SPACE.repeat(level);
    }
}
```

3. 减少方法调用开销
```java
// 内联小方法
@CompilerControl(CompilerControl.Mode.INLINE)
private void appendBranch(StringBuilder sb, boolean isLast) {
    sb.append(isLast ? LAST_BRANCH : BRANCH);
}
```

4. 批量append优化
```java
// 避免
sb.append(prefix).append(branch).append(name).append(" (").append(duration).append(")");

// 优化为
sb.append(prefix);
sb.append(branch);
sb.append(name);
sb.append(" (");
sb.append(duration);
sb.append(")");
```

5. 时间格式化缓存
```java
private static final Map<Long, String> DURATION_CACHE = new ConcurrentHashMap<>();

private String formatDuration(long millis) {
    return DURATION_CACHE.computeIfAbsent(millis, m -> {
        if (m < 1000) return m + "ms";
        if (m < 60000) return String.format("%.1fs", m / 1000.0);
        return String.format("%.1fm", m / 60000.0);
    });
}
```

**JMH基准测试：**
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class ConsoleExporterBenchmark {
    
    private Session smallSession;  // 10节点
    private Session mediumSession; // 100节点
    private Session largeSession;  // 1000节点
    
    @Setup
    public void setup() {
        smallSession = createSession(10);
        mediumSession = createSession(100);
        largeSession = createSession(1000);
    }
    
    @Benchmark
    public String benchmarkSmall() {
        return new ConsoleExporter().export(smallSession);
    }
    
    @Benchmark
    public String benchmarkMedium() {
        return new ConsoleExporter().export(mediumSession);
    }
    
    @Benchmark
    public String benchmarkLarge() {
        return new ConsoleExporter().export(largeSession);
    }
}
```

请实施这些优化并验证性能提升。
```

## 第五阶段：集成验收提示词

```markdown
你是项目验收专员。请对ConsoleExporter进行最终验收。

**验收清单：**

## 功能验收
- [ ] 正确显示Session信息（ID、Thread、Status、Duration）
- [ ] 树形结构显示正确（缩进、连接线）
- [ ] 消息格式化正确（[INFO]、[ERROR]）
- [ ] 支持多种输出方式（String、System.out、PrintStream）
- [ ] 处理边界情况（null、空数据、超长内容）

## 性能验收
- [ ] 1000节点处理时间 < 10ms
- [ ] 内存使用 < 1MB
- [ ] 10000节点可正常处理（不崩溃）

## 代码质量
- [ ] 单元测试覆盖率 ≥ 95%
- [ ] 代码注释完整清晰
- [ ] 符合项目编码规范
- [ ] 无代码异味（SonarQube扫描）

## 输出示例验证
期望输出格式：
```
==================================================
TaskFlow Insight Report
==================================================
Session: 550e8400-e29b-41d4-a716-446655440000
Thread:  1
Status:  COMPLETED
Duration: 1250ms

└── Main Task (1250ms, COMPLETED)
    ├── Database Query (300ms, COMPLETED)
    │     [INFO] Connecting to database
    │     [INFO] Query executed successfully
    ├── Data Processing (800ms, COMPLETED)
    │   ├── Validation (100ms, COMPLETED)
    │   └── Transformation (700ms, COMPLETED)
    └── Response Generation (150ms, COMPLETED)
          [INFO] Response formatted as JSON
==================================================
```

## 集成测试场景
1. Spring Boot应用集成
2. 多线程并发导出
3. 大数据量处理
4. 异常场景恢复

## 文档完整性
- [ ] API文档完整
- [ ] 使用示例完整
- [ ] 性能测试报告
- [ ] 更新任务卡状态

**验收结论：**
- 通过 ✅ / 需改进 ⚠️ / 不通过 ❌

**改进建议：**
[列出需要改进的项目]

**风险提示：**
[列出潜在风险]
```