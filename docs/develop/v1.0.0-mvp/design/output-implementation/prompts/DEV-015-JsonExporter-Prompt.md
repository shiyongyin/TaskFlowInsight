# DEV-015: JSON导出实现 - AI开发提示词

## 第一阶段：需求澄清提示词

```markdown
你是一名资深JSON序列化专家，精通JSON规范和高性能序列化技术。现在需要你评审并澄清JsonExporter的实现需求。

**输入材料：**
- 任务卡：docs/task/v1.0.0-mvp/output-implementation/TASK-015-JsonExport.md
- 设计文档：docs/develop/v1.0.0-mvp/design/output-implementation/DEV-015-JSON导出实现.md
- 现有实现：src/main/java/com/syy/taskflowinsight/api/JsonExporter.java
- 数据模型：Session、TaskNode、Message（MessageType只有INFO和ERROR）
- 项目结构：Spring Boot 3.5.5、Java 21、不使用Jackson等第三方JSON库

**评审要求：**
1. 验证JSON格式符合RFC 7159标准
2. 检查特殊字符转义的完整性
3. 评估性能目标（1000节点<20ms）的可行性
4. 确认数据序列化的完整性
5. 识别循环引用和内存问题风险

**输出格式：**
生成问题清单文件：docs/task/v1.0.0-mvp/output-implementation/DEV-015-Questions.md

问题清单结构：
# DEV-015 需求澄清问题清单

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
你是一名资深Java开发工程师，精通JSON序列化和流式处理。现在需要你优化或重构JsonExporter类。

**角色定位：** Java性能优化专家 + JSON规范专家 + 流式处理专家

**技术约束：**
- Java 21 + Spring Boot 3.5.5
- 不使用第三方JSON库（Jackson、Gson等）
- 手动实现JSON序列化
- 支持Writer流式输出
- KISS原则，避免过度设计

**核心需求：**

1. 数据结构映射
   - Session → JSON对象（sessionId、threadId、status、timestamps、root）
   - TaskNode → JSON对象（nodeId、name、hierarchy、timing、status、messages、children）
   - Message → JSON对象（type、content、timestamp）
   - 枚举 → JSON字符串

2. 导出模式
   - COMPAT模式（默认）：兼容模式，毫秒时间戳，简洁字段名
   - ENHANCED模式：增强模式，纳秒精度，包含统计信息

3. 特殊字符转义
   - 双引号：\" 
   - 反斜杠：\\
   - 换行符：\n
   - 回车符：\r
   - 制表符：\t
   - 退格符：\b
   - 换页符：\f
   - Unicode字符：\uXXXX

**性能要求：**
- 1000个节点序列化 < 20ms
- 内存使用 < 2MB
- 支持10000+节点流式处理

**代码规范：**
```java
public class JsonExporter {
    
    public enum ExportMode {
        COMPAT,   // 兼容模式
        ENHANCED  // 增强模式
    }
    
    private final ExportMode mode;
    
    // 构造函数
    public JsonExporter() {
        this(ExportMode.COMPAT);
    }
    
    public JsonExporter(ExportMode mode) {
        this.mode = mode;
    }
    
    // 导出为字符串
    public String export(Session session) {
        if (session == null) {
            return "{\"error\":\"No session data available\"}";
        }
        
        StringWriter writer = new StringWriter();
        try {
            export(session, writer);
            return writer.toString();
        } catch (IOException e) {
            // StringWriter不会抛出IOException，但需要处理
            return "{\"error\":\"Export failed: " + escape(e.getMessage()) + "\"}";
        }
    }
    
    // 流式导出
    public void export(Session session, Writer writer) throws IOException {
        if (session == null) {
            writer.write("{\"error\":\"No session data available\"}");
            return;
        }
        
        writer.write('{');
        writeSessionFields(session, writer);
        writer.write('}');
        writer.flush();
    }
    
    // 私有方法
    private void writeSessionFields(Session session, Writer writer) throws IOException {
        writeField(writer, "sessionId", session.getSessionId(), false);
        writeField(writer, "threadId", session.getThreadId(), true);
        writeField(writer, "status", session.getStatus(), true);
        
        if (mode == ExportMode.COMPAT) {
            writeField(writer, "createdAt", session.getCreatedAt(), true);
            writeField(writer, "endedAt", session.getEndedAt(), true);
            writeField(writer, "durationMs", session.getDurationMs(), true);
        } else {
            // ENHANCED模式的额外字段
            writeField(writer, "createdAtNanos", session.getCreatedAtNanos(), true);
            writeField(writer, "endedAtNanos", session.getEndedAtNanos(), true);
            writeField(writer, "durationNanos", session.getDurationNanos(), true);
        }
        
        // 写入根节点
        writer.write(",\"root\":");
        writeTaskNode(session.getRoot(), writer);
    }
    
    private void writeTaskNode(TaskNode node, Writer writer) throws IOException {
        if (node == null) {
            writer.write("null");
            return;
        }
        
        writer.write('{');
        
        // 基本字段
        writeField(writer, "nodeId", node.getNodeId(), false);
        writeField(writer, "name", node.getName(), true);
        writeField(writer, "depth", node.getDepth(), true);
        writeField(writer, "sequence", node.getSequence(), true);
        writeField(writer, "taskPath", node.getTaskPath(), true);
        
        // 时间字段
        if (mode == ExportMode.COMPAT) {
            writeField(writer, "startMillis", node.getStartMillis(), true);
            writeField(writer, "endMillis", node.getEndMillis(), true);
            writeField(writer, "durationMs", node.getDurationMs(), true);
        } else {
            writeField(writer, "startNanos", node.getStartNanos(), true);
            writeField(writer, "endNanos", node.getEndNanos(), true);
            writeField(writer, "durationNanos", node.getDurationNanos(), true);
        }
        
        // 状态字段
        writeField(writer, "status", node.getStatus(), true);
        writeField(writer, "isActive", node.isActive(), true);
        
        // 消息数组
        writer.write(",\"messages\":");
        writeMessages(node.getMessages(), writer);
        
        // 子节点数组
        writer.write(",\"children\":");
        writeChildren(node.getChildren(), writer);
        
        writer.write('}');
    }
    
    private void writeMessages(List<Message> messages, Writer writer) throws IOException {
        writer.write('[');
        if (messages != null && !messages.isEmpty()) {
            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) writer.write(',');
                writeMessage(messages.get(i), writer);
            }
        }
        writer.write(']');
    }
    
    private void writeMessage(Message message, Writer writer) throws IOException {
        writer.write('{');
        writeField(writer, "type", message.getType(), false);
        writeField(writer, "content", message.getContent(), true);
        writeField(writer, "timestamp", message.getTimestamp(), true);
        writer.write('}');
    }
    
    private void writeChildren(List<TaskNode> children, Writer writer) throws IOException {
        writer.write('[');
        if (children != null && !children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) writer.write(',');
                writeTaskNode(children.get(i), writer);
            }
        }
        writer.write(']');
    }
    
    // 字段写入辅助方法
    private void writeField(Writer writer, String name, Object value, boolean comma) throws IOException {
        if (comma) writer.write(',');
        writer.write('"');
        writer.write(name);
        writer.write("\":");
        writeValue(writer, value);
    }
    
    private void writeValue(Writer writer, Object value) throws IOException {
        if (value == null) {
            writer.write("null");
        } else if (value instanceof String) {
            writer.write('"');
            writer.write(escape((String) value));
            writer.write('"');
        } else if (value instanceof Number) {
            writer.write(value.toString());
        } else if (value instanceof Boolean) {
            writer.write(value.toString());
        } else if (value instanceof Enum) {
            writer.write('"');
            writer.write(((Enum<?>) value).name());
            writer.write('"');
        } else {
            writer.write('"');
            writer.write(escape(value.toString()));
            writer.write('"');
        }
    }
    
    // JSON字符串转义
    private String escape(String str) {
        if (str == null) return "";
        
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7F) {
                        // Unicode转义
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
```

**优化要点：**
1. 使用Writer而非String拼接
2. 预估StringBuilder容量
3. 避免重复的字符串操作
4. 缓存转义后的常用字符串
5. 批量写入减少IO调用

**错误处理：**
- null session：返回错误JSON
- 序列化异常：捕获并返回错误信息
- 循环引用：检测并避免（使用访问标记）

请基于现有代码进行优化，确保JSON格式正确且性能达标。
```

## 第三阶段：单元测试提示词

```markdown
你是一名资深测试工程师，精通JUnit 5和JSON格式验证。现在需要你为JsonExporter编写完整的测试套件。

**测试框架：** JUnit 5 + AssertJ + 简单JSON解析验证

**测试策略：**
1. 格式验证 - JSON规范合规性
2. 数据完整性 - 所有字段正确序列化
3. 特殊字符 - 转义正确性
4. 性能测试 - 验证性能指标
5. 模式测试 - COMPAT和ENHANCED模式

**测试类结构：**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JsonExporterTest {
    
    private JsonExporter compatExporter;
    private JsonExporter enhancedExporter;
    
    @BeforeEach
    void setUp() {
        compatExporter = new JsonExporter(JsonExporter.ExportMode.COMPAT);
        enhancedExporter = new JsonExporter(JsonExporter.ExportMode.ENHANCED);
    }
    
    // ========== JSON格式测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("应该生成有效的JSON格式")
    void shouldGenerateValidJson() {
        // Given
        Session session = createSimpleSession();
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        assertThat(isValidJson(json)).isTrue();
        
        // 验证可解析性
        assertDoesNotThrow(() -> parseJson(json));
    }
    
    @Test
    @Order(2)
    @DisplayName("应该正确处理null会话")
    void shouldHandleNullSession() {
        // When
        String json = compatExporter.export(null);
        
        // Then
        assertThat(json).isEqualTo("{\"error\":\"No session data available\"}");
        assertThat(isValidJson(json)).isTrue();
    }
    
    // ========== 数据完整性测试 ==========
    
    @Test
    @Order(10)
    @DisplayName("应该序列化所有Session字段")
    void shouldSerializeAllSessionFields() {
        // Given
        Session session = createCompleteSession();
        
        // When
        String json = compatExporter.export(session);
        Map<String, Object> parsed = parseJson(json);
        
        // Then
        assertThat(parsed).containsKeys(
            "sessionId", "threadId", "status",
            "createdAt", "endedAt", "durationMs", "root"
        );
        assertThat(parsed.get("sessionId")).isEqualTo(session.getSessionId());
        assertThat(parsed.get("threadId")).isEqualTo(session.getThreadId());
        assertThat(parsed.get("status")).isEqualTo(session.getStatus().name());
    }
    
    @Test
    @Order(11)
    @DisplayName("应该递归序列化TaskNode树")
    void shouldSerializeTaskNodeTree() {
        // Given
        Session session = createNestedSession(3, 2); // 3层深，每层2个子节点
        
        // When
        String json = compatExporter.export(session);
        Map<String, Object> parsed = parseJson(json);
        
        // Then
        Map<String, Object> root = (Map<String, Object>) parsed.get("root");
        assertThat(root).isNotNull();
        assertThat(root).containsKeys("nodeId", "name", "children", "messages");
        
        List<Map<String, Object>> children = (List<Map<String, Object>>) root.get("children");
        assertThat(children).hasSize(2);
        
        // 验证递归结构
        Map<String, Object> firstChild = children.get(0);
        assertThat(firstChild.get("children")).isInstanceOf(List.class);
    }
    
    @Test
    @Order(12)
    @DisplayName("应该正确序列化Message列表")
    void shouldSerializeMessages() {
        // Given
        TaskNode node = new TaskNode("task");
        node.addMessage(new Message(MessageType.INFO, "Info message"));
        node.addMessage(new Message(MessageType.ERROR, "Error message"));
        
        Session session = new Session();
        session.setRoot(node);
        
        // When
        String json = compatExporter.export(session);
        Map<String, Object> parsed = parseJson(json);
        
        // Then
        Map<String, Object> root = (Map<String, Object>) parsed.get("root");
        List<Map<String, Object>> messages = (List<Map<String, Object>>) root.get("messages");
        
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("type")).isEqualTo("INFO");
        assertThat(messages.get(0).get("content")).isEqualTo("Info message");
        assertThat(messages.get(1).get("type")).isEqualTo("ERROR");
    }
    
    // ========== 特殊字符测试 ==========
    
    @Test
    @Order(20)
    @DisplayName("应该正确转义特殊字符")
    void shouldEscapeSpecialCharacters() {
        // Given
        TaskNode node = new TaskNode("Task with \"quotes\" and \\backslash\\");
        node.addMessage(new Message(MessageType.INFO, "Line1\nLine2\tTabbed"));
        
        Session session = new Session();
        session.setRoot(node);
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertThat(json).contains("\\\"quotes\\\"");
        assertThat(json).contains("\\\\backslash\\\\");
        assertThat(json).contains("Line1\\nLine2\\tTabbed");
        
        // 验证解析后内容正确
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> root = (Map<String, Object>) parsed.get("root");
        assertThat(root.get("name")).isEqualTo("Task with \"quotes\" and \\backslash\\");
    }
    
    @Test
    @Order(21)
    @DisplayName("应该处理Unicode字符")
    void shouldHandleUnicodeCharacters() {
        // Given
        TaskNode node = new TaskNode("任务名称 😀 ñ");
        Session session = new Session();
        session.setRoot(node);
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertThat(isValidJson(json)).isTrue();
        Map<String, Object> parsed = parseJson(json);
        Map<String, Object> root = (Map<String, Object>) parsed.get("root");
        assertThat(root.get("name")).isEqualTo("任务名称 😀 ñ");
    }
    
    // ========== 模式测试 ==========
    
    @Test
    @Order(30)
    @DisplayName("COMPAT模式应该使用毫秒时间戳")
    void compatModeShouldUseMilliseconds() {
        // Given
        Session session = createSimpleSession();
        
        // When
        String json = compatExporter.export(session);
        Map<String, Object> parsed = parseJson(json);
        
        // Then
        assertThat(parsed).containsKeys("createdAt", "endedAt", "durationMs");
        assertThat(parsed).doesNotContainKeys("createdAtNanos", "endedAtNanos", "durationNanos");
    }
    
    @Test
    @Order(31)
    @DisplayName("ENHANCED模式应该包含纳秒精度")
    void enhancedModeShouldUseNanoseconds() {
        // Given
        Session session = createSimpleSession();
        
        // When
        String json = enhancedExporter.export(session);
        Map<String, Object> parsed = parseJson(json);
        
        // Then
        assertThat(parsed).containsKeys("createdAtNanos", "endedAtNanos", "durationNanos");
    }
    
    // ========== 性能测试 ==========
    
    @Test
    @Order(40)
    @DisplayName("应该在20ms内序列化1000个节点")
    void shouldSerialize1000NodesWithinTimeLimit() {
        // Given
        Session session = createLargeSession(1000);
        
        // When
        long startTime = System.nanoTime();
        String json = compatExporter.export(session);
        long duration = (System.nanoTime() - startTime) / 1_000_000; // 毫秒
        
        // Then
        assertThat(duration).isLessThan(20);
        assertThat(json.length()).isGreaterThan(0);
        assertThat(isValidJson(json)).isTrue();
        System.out.println("序列化1000个节点耗时: " + duration + "ms");
    }
    
    @Test
    @Order(41)
    @DisplayName("流式输出应该支持大数据量")
    void shouldSupportStreamingLargeData() throws IOException {
        // Given
        Session session = createLargeSession(10000);
        StringWriter writer = new StringWriter();
        
        // When
        long startTime = System.nanoTime();
        compatExporter.export(session, writer);
        long duration = (System.nanoTime() - startTime) / 1_000_000;
        
        // Then
        String json = writer.toString();
        assertThat(json.length()).isGreaterThan(0);
        assertThat(isValidJson(json)).isTrue();
        System.out.println("流式输出10000个节点耗时: " + duration + "ms");
    }
    
    // ========== 边界条件测试 ==========
    
    @Test
    @Order(50)
    @DisplayName("应该处理空任务树")
    void shouldHandleEmptyTaskTree() {
        // Given
        Session session = new Session();
        session.setSessionId(UUID.randomUUID().toString());
        // root为null
        
        // When
        String json = compatExporter.export(session);
        Map<String, Object> parsed = parseJson(json);
        
        // Then
        assertThat(parsed.get("root")).isNull();
    }
    
    @Test
    @Order(51)
    @DisplayName("应该处理深度嵌套")
    void shouldHandleDeepNesting() {
        // Given
        Session session = createDeepSession(100); // 100层深
        
        // When/Then
        assertDoesNotThrow(() -> {
            String json = compatExporter.export(session);
            assertThat(isValidJson(json)).isTrue();
        });
    }
    
    // ========== 流式输出测试 ==========
    
    @Test
    @Order(60)
    @DisplayName("Writer输出应该与String输出一致")
    void writerOutputShouldMatchStringOutput() throws IOException {
        // Given
        Session session = createCompleteSession();
        
        // When
        String stringOutput = compatExporter.export(session);
        
        StringWriter writer = new StringWriter();
        compatExporter.export(session, writer);
        String writerOutput = writer.toString();
        
        // Then
        assertThat(writerOutput).isEqualTo(stringOutput);
    }
    
    // ========== 辅助方法 ==========
    
    private boolean isValidJson(String json) {
        try {
            parseJson(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private Map<String, Object> parseJson(String json) {
        // 简单的JSON解析实现
        // 实际测试中可以使用更完善的解析器
        return new SimpleJsonParser().parse(json);
    }
    
    private Session createSimpleSession() {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID().toString());
        session.setThreadId(1L);
        session.setStatus(SessionStatus.COMPLETED);
        session.setCreatedAt(System.currentTimeMillis());
        session.setEndedAt(System.currentTimeMillis() + 1000);
        
        TaskNode root = new TaskNode("Main Task");
        root.setStatus(TaskStatus.COMPLETED);
        session.setRoot(root);
        
        return session;
    }
    
    // 简单的JSON解析器（用于测试验证）
    private static class SimpleJsonParser {
        Map<String, Object> parse(String json) {
            // 实现基本的JSON解析逻辑
            // 或使用Java内置的JSON API（如果可用）
            return new HashMap<>();
        }
    }
}
```

**覆盖率要求：**
- 行覆盖率 ≥ 95%
- 分支覆盖率 ≥ 90%
- 方法覆盖率 = 100%

**额外测试场景：**
1. 并发序列化测试
2. 内存泄漏测试
3. 格式兼容性测试（与Jackson等库对比）

请生成完整的测试代码，确保JSON格式正确性和性能达标。
```

## 第四阶段：性能优化提示词

```markdown
你是一名Java性能优化专家。JsonExporter当前性能未达标，需要优化。

**当前性能指标：**
- 1000节点序列化时间：25ms（目标<20ms）
- 内存使用：2.5MB（目标<2MB）
- 10000节点处理：内存溢出

**性能瓶颈分析：**
1. 字符串拼接开销（35%）
2. 转义处理效率低（25%）
3. Writer频繁调用（20%）
4. 对象创建过多（15%）

**优化策略：**

1. Writer缓冲优化
```java
public class BufferedJsonWriter {
    private final Writer underlying;
    private final char[] buffer;
    private int position;
    
    public BufferedJsonWriter(Writer writer) {
        this.underlying = writer;
        this.buffer = new char[8192]; // 8KB缓冲
        this.position = 0;
    }
    
    public void write(String str) throws IOException {
        if (position + str.length() > buffer.length) {
            flush();
        }
        str.getChars(0, str.length(), buffer, position);
        position += str.length();
    }
    
    public void flush() throws IOException {
        if (position > 0) {
            underlying.write(buffer, 0, position);
            position = 0;
        }
    }
}
```

2. 转义优化
```java
private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

private void writeEscapedString(Writer writer, String str) throws IOException {
    writer.write('"');
    
    int last = 0;
    int length = str.length();
    char[] chars = null; // 延迟创建
    
    for (int i = 0; i < length; i++) {
        char c = str.charAt(i);
        String replacement;
        
        if (c < 128) {
            replacement = ESCAPE_TABLE[c]; // 预计算的转义表
            if (replacement == null) continue;
        } else if (c == '\u2028') {
            replacement = "\\u2028";
        } else if (c == '\u2029') {
            replacement = "\\u2029";
        } else {
            continue;
        }
        
        if (last < i) {
            if (chars == null) chars = str.toCharArray();
            writer.write(chars, last, i - last);
        }
        writer.write(replacement);
        last = i + 1;
    }
    
    if (last < length) {
        if (chars == null) chars = str.toCharArray();
        writer.write(chars, last, length - last);
    }
    
    writer.write('"');
}
```

3. 对象池化
```java
private static class StringBuilderPool {
    private static final ThreadLocal<StringBuilder> POOL = ThreadLocal.withInitial(
        () -> new StringBuilder(1024)
    );
    
    public static StringBuilder acquire() {
        StringBuilder sb = POOL.get();
        sb.setLength(0);
        return sb;
    }
}
```

4. 批量写入优化
```java
private void writeFieldBatch(Writer writer, Object... fieldsAndValues) throws IOException {
    for (int i = 0; i < fieldsAndValues.length; i += 2) {
        if (i > 0) writer.write(',');
        writer.write('"');
        writer.write((String) fieldsAndValues[i]);
        writer.write("\":");
        writeValue(writer, fieldsAndValues[i + 1]);
    }
}
```

5. 流式处理优化
```java
public void exportLarge(Session session, OutputStream out) throws IOException {
    // 使用BufferedOutputStream + OutputStreamWriter
    try (BufferedOutputStream bos = new BufferedOutputStream(out, 16384);
         OutputStreamWriter writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
        
        export(session, writer);
    }
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
public class JsonExporterBenchmark {
    
    @Param({"10", "100", "1000", "10000"})
    private int nodeCount;
    
    private Session session;
    private JsonExporter exporter;
    
    @Setup
    public void setup() {
        session = createSession(nodeCount);
        exporter = new JsonExporter();
    }
    
    @Benchmark
    public String benchmarkString() {
        return exporter.export(session);
    }
    
    @Benchmark
    public void benchmarkWriter(Blackhole blackhole) throws IOException {
        StringWriter writer = new StringWriter();
        exporter.export(session, writer);
        blackhole.consume(writer.toString());
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public String benchmarkColdStart() {
        return new JsonExporter().export(session);
    }
}
```

**内存优化：**
1. 使用对象池减少GC压力
2. 流式处理避免完整字符串构建
3. 复用char[]数组
4. 延迟初始化大对象

请实施这些优化并验证性能提升。
```

## 第五阶段：集成验收提示词

```markdown
你是项目验收专员。请对JsonExporter进行最终验收。

**验收清单：**

## 功能验收
- [ ] JSON格式符合RFC 7159标准
- [ ] 完整序列化Session/TaskNode/Message
- [ ] 正确处理特殊字符转义
- [ ] 支持COMPAT和ENHANCED两种模式
- [ ] 支持String和Writer两种输出方式
- [ ] 处理边界情况（null、空数据、循环引用）

## 性能验收
- [ ] 1000节点序列化 < 20ms
- [ ] 内存使用 < 2MB
- [ ] 10000节点流式处理正常
- [ ] 无内存泄漏

## 兼容性验收
- [ ] 输出可被Jackson解析
- [ ] 输出可被Gson解析
- [ ] 输出可被浏览器JSON.parse解析
- [ ] 输出可被Python json.loads解析

## 代码质量
- [ ] 单元测试覆盖率 ≥ 95%
- [ ] 代码注释完整清晰
- [ ] 符合项目编码规范
- [ ] 无代码异味（SonarQube扫描）

## JSON输出示例验证

### COMPAT模式期望输出：
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "threadId": 1,
  "status": "COMPLETED",
  "createdAt": 1693910400000,
  "endedAt": 1693910401250,
  "durationMs": 1250,
  "root": {
    "nodeId": "node-001",
    "name": "Main Task",
    "depth": 0,
    "sequence": 0,
    "taskPath": "Main Task",
    "startMillis": 1693910400000,
    "endMillis": 1693910401250,
    "durationMs": 1250,
    "status": "COMPLETED",
    "isActive": false,
    "messages": [
      {
        "type": "INFO",
        "content": "Task started",
        "timestamp": 1693910400000
      }
    ],
    "children": [
      {
        "nodeId": "node-002",
        "name": "Sub Task",
        "depth": 1,
        "sequence": 0,
        "taskPath": "Main Task/Sub Task",
        "startMillis": 1693910400100,
        "endMillis": 1693910400500,
        "durationMs": 400,
        "status": "COMPLETED",
        "isActive": false,
        "messages": [],
        "children": []
      }
    ]
  }
}
```

### ENHANCED模式额外字段：
- 纳秒精度时间戳
- 统计信息（taskCount、messageCount、maxDepth）
- 性能指标（slowestTask、fastestTask）

## 集成测试场景
1. Spring Boot REST API返回
2. 文件导出功能
3. 日志系统集成
4. 监控系统数据上报

## 验证脚本
```bash
# 使用jq验证JSON格式
echo "$JSON_OUTPUT" | jq . > /dev/null

# 使用Python验证
python3 -c "import json; json.loads('$JSON_OUTPUT')"

# 性能测试
time java -cp target/classes JsonExporterPerformanceTest
```

## 文档完整性
- [ ] API文档完整
- [ ] JSON格式规范文档
- [ ] 性能测试报告
- [ ] 更新任务卡状态

**验收结论：**
- 通过 ✅ / 需改进 ⚠️ / 不通过 ❌

**改进建议：**
[列出需要改进的项目]

**后续优化建议：**
1. 考虑添加JSON Schema验证
2. 支持自定义字段过滤
3. 添加压缩输出选项
4. 实现增量序列化
```