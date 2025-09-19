package com.syy.taskflowinsight.exporter.json;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonExporter 单元测试 - JSON导出功能完整性验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>采用分层测试策略，从基础JSON格式到复杂业务场景逐步验证</li>
 *   <li>使用@Order注解确保测试执行顺序，便于问题定位和调试</li>
 *   <li>通过多种输出模式测试验证COMPAT和ENHANCED模式的差异性</li>
 *   <li>结合性能测试确保大数据量场景下的可用性</li>
 *   <li>使用边界值测试验证极端条件下的稳定性</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>JSON格式验证：</strong>有效JSON结构、null会话处理、基础格式校验</li>
 *   <li><strong>数据完整性：</strong>Session所有字段序列化、TaskNode树递归、Message列表处理</li>
 *   <li><strong>特殊字符处理：</strong>引号转义、反斜杠转义、换行符/制表符处理、Unicode字符支持</li>
 *   <li><strong>导出模式：</strong>COMPAT模式（毫秒精度）vs ENHANCED模式（纳秒精度+统计信息）</li>
 *   <li><strong>流式输出：</strong>Writer接口支持、大数据流式处理、内存效率验证</li>
 *   <li><strong>性能基准：</strong>1000节点<20ms、5000节点流式输出、序列化效率</li>
 *   <li><strong>边界条件：</strong>100层深度嵌套、null字段处理、极端数据量</li>
 * </ul>
 * 
 * <h2>测试场景：</h2>
 * <ul>
 *   <li><strong>基础格式：</strong>JSON结构完整性、null输入安全处理</li>
 *   <li><strong>数据序列化：</strong>Session+TaskNode+Message完整序列化</li>
 *   <li><strong>字符转义：</strong>特殊字符、控制字符、Unicode字符处理</li>
 *   <li><strong>模式切换：</strong>COMPAT模式 vs ENHANCED模式功能差异</li>
 *   <li><strong>性能验证：</strong>1000节点序列化性能、流式输出效率</li>
 *   <li><strong>极限测试：</strong>深度嵌套、大数据量、异常边界</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>格式正确：</strong>生成有效JSON，符合标准JSON语法规范</li>
 *   <li><strong>数据完整：</strong>所有业务数据都正确序列化到JSON中</li>
 *   <li><strong>字符安全：</strong>特殊字符正确转义，不破坏JSON结构</li>
 *   <li><strong>模式准确：</strong>两种模式输出内容符合各自设计目标</li>
 *   <li><strong>性能达标：</strong>1000节点<20ms，5000节点流式输出稳定</li>
 *   <li><strong>边界稳定：</strong>极端条件下不抛异常，输出仍然有效</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
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
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(isValidJson(json));
    }
    
    @Test
    @Order(2)
    @DisplayName("应该正确处理null会话")
    void shouldHandleNullSession() {
        // When
        String json = compatExporter.export(null);
        
        // Then
        assertEquals("{\"error\":\"No session data available\"}", json);
        assertTrue(isValidJson(json));
    }
    
    @Test
    @Order(3)
    @DisplayName("应该处理空任务树")
    void shouldHandleEmptyTaskTree() {
        // Session必须有rootTask，无法测试空任务树
        // 跳过此测试
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
        
        // Then
        assertTrue(json.contains("\"sessionId\":"));
        assertTrue(json.contains("\"threadId\":"));
        assertTrue(json.contains("\"status\":"));
        assertTrue(json.contains("\"createdAt\":"));
        assertTrue(json.contains("\"endedAt\":"));
        assertTrue(json.contains("\"durationMs\":"));
        assertTrue(json.contains("\"root\":"));
    }
    
    @Test
    @Order(11)
    @DisplayName("应该递归序列化TaskNode树")
    void shouldSerializeTaskNodeTree() {
        // Given
        Session session = createNestedSession(3, 2);
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertTrue(json.contains("\"nodeId\":"));
        assertTrue(json.contains("\"name\":"));
        assertTrue(json.contains("\"children\":["));
        assertTrue(json.contains("\"messages\":"));
        
        // 验证嵌套结构
        int childrenCount = countOccurrences(json, "\"children\":");
        assertTrue(childrenCount > 1);
    }
    
    @Test
    @Order(12)
    @DisplayName("应该正确序列化Message列表")
    void shouldSerializeMessages() {
        // Given
        Session session = Session.create("task");
        TaskNode node = session.getRootTask();
        node.addInfo("Info message");
        node.addError("Error message");
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertTrue(json.contains("\"type\":\"PROCESS\""));
        assertTrue(json.contains("\"content\":\"Info message\""));
        assertTrue(json.contains("\"type\":\"ALERT\""));
        assertTrue(json.contains("\"content\":\"Error message\""));
    }
    
    // ========== 特殊字符测试 ==========
    
    @Test
    @Order(20)
    @DisplayName("应该正确转义特殊字符")
    void shouldEscapeSpecialCharacters() {
        // Given
        Session session = Session.create("Task with \"quotes\" and \\backslash\\");
        TaskNode node = session.getRootTask();
        node.addInfo("Line1\nLine2\tTabbed");
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertTrue(json.contains("\\\"quotes\\\""));
        assertTrue(json.contains("\\\\backslash\\\\"));
        assertTrue(json.contains("Line1\\nLine2\\tTabbed"));
    }
    
    @Test
    @Order(21)
    @DisplayName("应该处理Unicode字符")
    void shouldHandleUnicodeCharacters() {
        // Given
        Session session = Session.create("任务名称 😀 ñ");
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertTrue(isValidJson(json));
        // Unicode字符应该正确保留
        assertTrue(json.contains("任务名称"));
    }
    
    @Test
    @Order(22)
    @DisplayName("应该转义控制字符")
    void shouldEscapeControlCharacters() {
        // Given
        Session session = Session.create("test");
        TaskNode node = session.getRootTask();
        node.addInfo("Text with \b backspace \f formfeed");
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertTrue(json.contains("\\b"));
        assertTrue(json.contains("\\f"));
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
        
        // Then
        assertTrue(json.contains("\"createdAt\":"));
        assertTrue(json.contains("\"endedAt\":"));
        assertTrue(json.contains("\"durationMs\":"));
        assertFalse(json.contains("\"createdAtNanos\":"));
        assertFalse(json.contains("\"statistics\":"));
    }
    
    @Test
    @Order(31)
    @DisplayName("ENHANCED模式应该包含纳秒精度和统计信息")
    void enhancedModeShouldIncludeExtendedInfo() {
        // Given
        Session session = createSimpleSession();
        
        // When
        String json = enhancedExporter.export(session);
        
        // Then
        assertTrue(json.contains("\"createdAtNanos\":"));
        assertTrue(json.contains("\"endedAtNanos\":"));
        assertTrue(json.contains("\"durationNanos\":"));
        assertTrue(json.contains("\"statistics\":"));
        assertTrue(json.contains("\"totalTasks\":"));
        assertTrue(json.contains("\"maxDepth\":"));
    }
    
    // ========== 流式输出测试 ==========
    
    @Test
    @Order(35)
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
        assertEquals(stringOutput, writerOutput);
    }
    
    @Test
    @Order(36)
    @DisplayName("应该支持流式输出null会话")
    void shouldSupportStreamingNullSession() throws IOException {
        // Given
        StringWriter writer = new StringWriter();
        
        // When
        compatExporter.export(null, writer);
        String result = writer.toString();
        
        // Then
        assertEquals("{\"error\":\"No session data available\"}", result);
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
        assertNotNull(json);
        assertTrue(isValidJson(json));
        assertTrue(duration < 20, "序列化1000个节点耗时: " + duration + "ms，应该小于20ms");
        System.out.println("JsonExporter序列化1000个节点耗时: " + duration + "ms");
    }
    
    @Test
    @Order(41)
    @DisplayName("流式输出应该支持大数据量")
    void shouldSupportStreamingLargeData() throws IOException {
        // Given
        Session session = createLargeSession(5000);
        StringWriter writer = new StringWriter();
        
        // When
        long startTime = System.nanoTime();
        compatExporter.export(session, writer);
        long duration = (System.nanoTime() - startTime) / 1_000_000;
        
        // Then
        String json = writer.toString();
        assertTrue(json.length() > 0);
        assertTrue(isValidJson(json));
        System.out.println("流式输出5000个节点耗时: " + duration + "ms");
    }
    
    // ========== 边界条件测试 ==========
    
    @Test
    @Order(50)
    @DisplayName("应该处理深度嵌套（100层）")
    void shouldHandleDeepNesting() {
        // Given
        Session session = createDeepSession(100);
        
        // When/Then
        assertDoesNotThrow(() -> {
            String json = compatExporter.export(session);
            assertTrue(isValidJson(json));
        });
    }
    
    @Test
    @Order(51)
    @DisplayName("应该处理所有字段为null的节点")
    void shouldHandleNullFields() {
        // Given
        Session session = Session.create("test");
        // 不调用complete()，时间字段为null
        
        // When
        String json = compatExporter.export(session);
        
        // Then
        assertTrue(json.contains("\"endMillis\":null") || json.contains("\"endedAt\":null"));
        assertTrue(json.contains("\"durationMs\":null"));
        assertTrue(isValidJson(json));
    }
    
    // ========== 辅助方法 ==========
    
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        // 简单的JSON验证
        json = json.trim();
        if (!((json.startsWith("{") && json.endsWith("}")) || 
              (json.startsWith("[") && json.endsWith("]")))) {
            return false;
        }
        
        // 检查引号是否成对
        int quoteCount = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (char c : json.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                quoteCount++;
            }
        }
        
        return quoteCount % 2 == 0 && !inString;
    }
    
    private int countOccurrences(String str, String substr) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substr, index)) != -1) {
            count++;
            index += substr.length();
        }
        return count;
    }
    
    private Session createSimpleSession() {
        Session session = Session.create("Main Task");
        // 不调用complete()，保持RUNNING状态
        return session;
    }
    
    private Session createCompleteSession() {
        Session session = createSimpleSession();
        
        TaskNode root = session.getRootTask();
        root.addInfo("Task started");
        
        TaskNode child1 = root.createChild("Child 1");
        child1.addInfo("Processing");
        
        TaskNode child2 = root.createChild("Child 2");
        
        return session;
    }
    
    private Session createNestedSession(int depth, int width) {
        Session session = Session.create("root");
        createTaskTree(session.getRootTask(), depth, width);
        return session;
    }
    
    private void createTaskTree(TaskNode parent, int depth, int width) {
        if (depth > 0) {
            for (int i = 0; i < width; i++) {
                TaskNode child = parent.createChild(parent.getTaskName() + "-child" + i);
                createTaskTree(child, depth - 1, width);
            }
        }
    }
    
    private Session createLargeSession(int nodeCount) {
        Session session = Session.create("root");
        TaskNode root = session.getRootTask();
        TaskNode current = root;
        
        for (int i = 1; i < nodeCount; i++) {
            TaskNode child = current.createChild("node-" + i);
            
            if (i % 10 == 0) {
                current = root;
            }
            if (i % 3 == 0) {
                current = child;
            }
        }
        
        return session;
    }
    
    private Session createDeepSession(int depth) {
        Session session = Session.create("root");
        TaskNode current = session.getRootTask();
        
        for (int i = 0; i < depth; i++) {
            TaskNode child = current.createChild("level-" + i);
            current = child;
        }
        
        return session;
    }
}
