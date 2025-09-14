# 输出实现模块 - 上下文工程指南

## 概述
本文档为输出实现模块提供上下文管理最佳实践，确保ConsoleExporter和JsonExporter在实现过程中正确处理数据上下文、输出流管理和资源释放。

## 核心上下文管理原则

### 1. 数据上下文完整性
输出实现模块需要保证从Session到TaskNode的完整数据链路：
- **Session上下文**：保持会话信息的完整性（sessionId、threadId、status、时间戳）
- **TaskNode树上下文**：维护父子关系、层级深度、遍历顺序
- **Message上下文**：保留消息类型、时间戳、关联任务节点

### 2. 输出流上下文管理
正确管理不同类型的输出目标：
```java
// 控制台输出上下文
public class ConsoleExporter {
    private final PrintStream output;
    private final StringBuilder buffer;
    private final String linePrefix;  // 当前行前缀上下文
    
    // 维护树遍历上下文
    private void exportNode(TaskNode node, String prefix, boolean isLast) {
        // 保存当前上下文
        String currentPrefix = prefix;
        // 递归时传递新上下文
        String childPrefix = prefix + (isLast ? "    " : "│   ");
        // ...
    }
}

// JSON输出上下文
public class JsonExporter {
    private final Writer writer;
    private int indentLevel;  // 缩进层级上下文
    private boolean firstElement;  // 数组/对象元素位置上下文
    
    // 维护JSON结构上下文
    private void writeObject(Map<String, Object> obj) {
        pushContext();  // 进入新的对象上下文
        // ...
        popContext();   // 退出对象上下文
    }
}
```

### 3. 递归遍历上下文保护
在树形结构递归遍历时保护上下文状态：
```java
// 上下文状态保护模式
private void traverseTree(TaskNode node, Context ctx) {
    // 保存当前上下文状态
    Context.State saved = ctx.save();
    try {
        // 修改上下文进行当前节点处理
        ctx.depth++;
        ctx.path.append("/" + node.getName());
        
        // 处理当前节点
        processNode(node, ctx);
        
        // 递归处理子节点
        for (TaskNode child : node.getChildren()) {
            traverseTree(child, ctx);
        }
    } finally {
        // 恢复上下文状态
        ctx.restore(saved);
    }
}
```

## 输出实现特定上下文模式

### 1. ConsoleExporter上下文管理

#### 树形绘制上下文
```java
public class TreeContext {
    private final Deque<Boolean> isLastStack = new ArrayDeque<>();  // 每层是否为最后节点
    private final StringBuilder prefixBuilder = new StringBuilder(); // 前缀累积器
    private int currentDepth = 0;  // 当前深度
    
    public void enterNode(boolean isLast) {
        isLastStack.push(isLast);
        currentDepth++;
        updatePrefix();
    }
    
    public void exitNode() {
        isLastStack.pop();
        currentDepth--;
        updatePrefix();
    }
    
    private void updatePrefix() {
        prefixBuilder.setLength(0);
        for (Boolean last : isLastStack) {
            prefixBuilder.append(last ? "    " : "│   ");
        }
    }
}
```

#### 输出缓冲上下文
```java
public class OutputContext {
    private final StringBuilder buffer;
    private final int maxBufferSize = 8192;  // 8KB缓冲区
    private final PrintStream output;
    
    public void append(String text) {
        buffer.append(text);
        if (buffer.length() > maxBufferSize) {
            flush();
        }
    }
    
    public void flush() {
        output.print(buffer.toString());
        buffer.setLength(0);
    }
}
```

### 2. JsonExporter上下文管理

#### JSON结构上下文
```java
public class JsonContext {
    private final Writer writer;
    private final Deque<JsonScope> scopeStack = new ArrayDeque<>();
    private int indentLevel = 0;
    
    enum JsonScope {
        OBJECT, ARRAY, ROOT
    }
    
    public void beginObject() throws IOException {
        writer.write('{');
        scopeStack.push(JsonScope.OBJECT);
        indentLevel++;
    }
    
    public void endObject() throws IOException {
        indentLevel--;
        writer.write('}');
        scopeStack.pop();
    }
    
    public void writeIndent() throws IOException {
        writer.write('\n');
        for (int i = 0; i < indentLevel * 2; i++) {
            writer.write(' ');
        }
    }
}
```

#### 流式输出上下文
```java
public class StreamContext {
    private final Writer writer;
    private final boolean prettyPrint;
    private boolean needsComma = false;
    
    public void writeField(String name, Object value) throws IOException {
        if (needsComma) {
            writer.write(',');
        }
        if (prettyPrint) {
            writeIndent();
        }
        writer.write('"' + escape(name) + '":');
        writeValue(value);
        needsComma = true;
    }
    
    private String escape(String str) {
        // JSON字符串转义上下文处理
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
```

## 资源管理与异常上下文

### 1. 资源自动管理
```java
// 使用try-with-resources确保资源释放
public String exportToFile(Session session, String filename) {
    try (FileWriter writer = new FileWriter(filename);
         BufferedWriter buffered = new BufferedWriter(writer)) {
        
        JsonExporter exporter = new JsonExporter();
        exporter.export(session, buffered);
        return filename;
        
    } catch (IOException e) {
        // 异常上下文记录
        log.error("Failed to export session {} to file {}", 
                  session.getSessionId(), filename, e);
        throw new ExportException("Export failed", e);
    }
}
```

### 2. 异常上下文传播
```java
public class ExportException extends RuntimeException {
    private final String sessionId;
    private final String exportType;
    private final int nodeCount;
    
    public ExportException(String message, Session session, String type, Throwable cause) {
        super(message, cause);
        this.sessionId = session.getSessionId();
        this.exportType = type;
        this.nodeCount = countNodes(session.getRoot());
    }
    
    // 提供丰富的异常上下文信息
    @Override
    public String getMessage() {
        return String.format("%s [session=%s, type=%s, nodes=%d]",
            super.getMessage(), sessionId, exportType, nodeCount);
    }
}
```

## 性能优化上下文

### 1. 字符串构建上下文优化
```java
public class StringBuilderContext {
    private static final int INITIAL_CAPACITY = 4096;
    private final StringBuilder builder;
    
    public StringBuilderContext(int estimatedNodes) {
        // 根据节点数预估容量
        int capacity = Math.max(INITIAL_CAPACITY, estimatedNodes * 100);
        this.builder = new StringBuilder(capacity);
    }
    
    // 复用StringBuilder减少内存分配
    public void reset() {
        builder.setLength(0);
    }
}
```

### 2. 缓存上下文
```java
public class FormatCache {
    // 缓存常用格式字符串
    private static final String[] INDENT_CACHE = new String[20];
    static {
        for (int i = 0; i < INDENT_CACHE.length; i++) {
            INDENT_CACHE[i] = "  ".repeat(i);
        }
    }
    
    // 缓存树形绘制字符
    private static final String BRANCH = "├── ";
    private static final String LAST_BRANCH = "└── ";
    private static final String VERTICAL = "│   ";
    private static final String SPACE = "    ";
    
    public String getIndent(int level) {
        if (level < INDENT_CACHE.length) {
            return INDENT_CACHE[level];
        }
        return "  ".repeat(level);
    }
}
```

## 并发上下文处理

### 1. 线程安全的导出器
```java
public class ThreadSafeExporter {
    // 无状态设计，每次调用创建新的上下文
    public String export(Session session) {
        // 创建线程本地上下文
        ExportContext context = new ExportContext();
        return doExport(session, context);
    }
    
    private String doExport(Session session, ExportContext ctx) {
        // 所有状态都在ctx中，无共享状态
        // ...
    }
}
```

### 2. 并发导出管理
```java
public class ConcurrentExportManager {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    
    public List<CompletableFuture<String>> exportMultiple(List<Session> sessions) {
        return sessions.stream()
            .map(session -> CompletableFuture.supplyAsync(
                () -> exportSession(session), executor))
            .collect(Collectors.toList());
    }
    
    private String exportSession(Session session) {
        // 每个线程独立的导出上下文
        ConsoleExporter exporter = new ConsoleExporter();
        return exporter.export(session);
    }
}
```

## 测试上下文管理

### 1. 测试数据上下文构建
```java
public class TestDataContext {
    public Session createTestSession(int depth, int width) {
        Session session = new Session();
        TaskNode root = createTaskTree(depth, width, "root");
        session.setRoot(root);
        return session;
    }
    
    private TaskNode createTaskTree(int depth, int width, String name) {
        TaskNode node = new TaskNode(name);
        if (depth > 0) {
            for (int i = 0; i < width; i++) {
                TaskNode child = createTaskTree(depth - 1, width, 
                    name + "-child-" + i);
                node.addChild(child);
            }
        }
        return node;
    }
}
```

### 2. 验证上下文
```java
public class ValidationContext {
    public void validateJsonOutput(String json) {
        try {
            // 使用Jackson验证JSON格式
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            
            // 验证必需字段
            assertNotNull(root.get("sessionId"));
            assertNotNull(root.get("root"));
            
            // 验证嵌套结构
            validateTaskNode(root.get("root"));
            
        } catch (Exception e) {
            fail("Invalid JSON output: " + e.getMessage());
        }
    }
}
```

## 最佳实践总结

### DO（推荐做法）
1. ✅ 使用不可变对象传递上下文
2. ✅ 递归时保护和恢复上下文状态
3. ✅ 预分配StringBuilder容量
4. ✅ 使用try-with-resources管理资源
5. ✅ 缓存常用格式字符串
6. ✅ 设计无状态的导出器类

### DON'T（避免做法）
1. ❌ 在递归中修改共享状态
2. ❌ 忘记恢复上下文状态
3. ❌ 频繁的字符串拼接操作
4. ❌ 手动管理资源释放
5. ❌ 在导出器中保存会话状态
6. ❌ 使用全局变量存储输出状态

## 检查清单

### 实现前检查
- [ ] 明确输入数据的完整上下文
- [ ] 设计清晰的上下文传递路径
- [ ] 规划资源管理策略
- [ ] 考虑并发场景的上下文隔离

### 实现中检查
- [ ] 递归调用正确保护上下文
- [ ] 资源使用try-with-resources
- [ ] StringBuilder预分配合适容量
- [ ] 异常包含足够的上下文信息

### 实现后检查
- [ ] 无内存泄漏（资源正确释放）
- [ ] 线程安全（无共享可变状态）
- [ ] 性能达标（缓存和优化生效）
- [ ] 测试覆盖所有上下文场景