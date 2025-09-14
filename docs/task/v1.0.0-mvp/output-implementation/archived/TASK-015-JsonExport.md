# TASK-015: JSON序列化导出实现

## 任务背景

JSON导出是TaskFlow Insight的核心功能之一，它将复杂的任务树结构序列化为标准的JSON格式，便于数据存储、传输和与其他系统集成。JSON导出器需要完整地保留任务树的层级关系、时间信息、状态数据和消息内容，同时要确保输出的JSON格式规范、紧凑且易于解析。

## 目标

1. 实现完整的JSON序列化导出器，支持Session和TaskNode的完整序列化
2. 生成符合JSON标准的输出格式，确保数据完整性和一致性
3. 提供灵活的导出配置选项，支持不同场景的需求
4. 实现高效的序列化算法，确保大数据量时的性能表现
5. 支持数据压缩和格式化输出选项
6. 提供JSON Schema定义，便于数据验证和集成
7. 确保字符转义和Unicode支持的正确性

## 实现方案

### 15.1 JsonExporter核心实现

```java
package com.syy.taskflowinsight.export;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.enums.SessionStatus;

import java.util.List;
import java.io.StringWriter;
import java.io.Writer;
import java.io.IOException;

/**
 * JSON序列化导出器
 * 将TaskFlow Insight数据结构序列化为JSON格式
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class JsonExporter {
    
    private static final String JSON_VERSION = "1.0.0";
    private final JsonExportConfig config;
    
    public JsonExporter() {
        this(JsonExportConfig.defaultConfig());
    }
    
    public JsonExporter(JsonExportConfig config) {
        this.config = config != null ? config : JsonExportConfig.defaultConfig();
    }
    
    /**
     * 导出Session到JSON字符串
     * 
     * @param session 会话对象
     * @return JSON字符串
     */
    public String export(Session session) {
        if (session == null) {
            return createErrorJson("Session is null");
        }
        
        try {
            StringWriter writer = new StringWriter();
            export(session, writer);
            return writer.toString();
        } catch (IOException e) {
            // StringWriter通常不会抛出IOException，但为了API一致性
            return createErrorJson("Export failed: " + e.getMessage());
        } catch (Exception e) {
            return createErrorJson("Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * 导出Session到Writer
     * 
     * @param session 会话对象
     * @param writer 输出Writer
     * @throws IOException 写入异常
     */
    public void export(Session session, Writer writer) throws IOException {
        if (session == null) {
            writer.write(createErrorJson("Session is null"));
            return;
        }
        
        JsonWriter jsonWriter = new JsonWriter(writer, config);
        
        try {
            writeSession(jsonWriter, session);
        } catch (Exception e) {
            // 如果序列化过程中出错，尝试写入错误信息
            jsonWriter.reset();
            jsonWriter.writeErrorObject("Serialization failed: " + e.getMessage());
        }
        
        if (config.isFormattedOutput()) {
            writer.write("\n");
        }
    }
    
    /**
     * 序列化Session对象
     * 
     * @param writer JSON写入器
     * @param session 会话对象
     * @throws IOException 写入异常
     */
    private void writeSession(JsonWriter writer, Session session) throws IOException {
        writer.beginObject();
        
        // 元数据
        writer.name("$schema").value("https://taskflowinsight.com/schema/v1.0.0/session.json");
        writer.name("version").value(JSON_VERSION);
        writer.name("exportTime").value(System.currentTimeMillis());
        
        // 会话基本信息
        writer.name("sessionId").value(session.getSessionId());
        writer.name("threadId").value(session.getThreadId());
        writer.name("status").value(session.getStatus().toString());
        writer.name("createdAt").value(session.getCreatedAt());
        writer.name("endedAt").value(session.getEndedAt());
        writer.name("durationMs").value(session.getDurationMillis());
        
        // 统计信息（可选）
        if (config.isIncludeStatistics()) {
            writeSessionStatistics(writer, session);
        }
        
        // 任务树
        writer.name("taskTree");
        TaskNode root = session.getRoot();
        if (root != null) {
            writeTaskNode(writer, root, 0);
        } else {
            writer.nullValue();
        }
        
        writer.endObject();
    }
    
    /**
     * 序列化会话统计信息
     * 
     * @param writer JSON写入器
     * @param session 会话对象
     * @throws IOException 写入异常
     */
    private void writeSessionStatistics(JsonWriter writer, Session session) throws IOException {
        TaskNode root = session.getRoot();
        if (root == null) {
            writer.name("statistics").nullValue();
            return;
        }
        
        SessionStatistics stats = calculateSessionStatistics(root);
        
        writer.name("statistics");
        writer.beginObject();
        writer.name("totalTasks").value(stats.totalTasks);
        writer.name("completedTasks").value(stats.completedTasks);
        writer.name("failedTasks").value(stats.failedTasks);
        writer.name("runningTasks").value(stats.runningTasks);
        writer.name("maxDepth").value(stats.maxDepth);
        writer.name("totalMessages").value(stats.totalMessages);
        writer.name("errorMessages").value(stats.errorMessages);
        writer.name("warningMessages").value(stats.warningMessages);
        
        if (stats.totalTasks > 0) {
            writer.name("successRate").value((double) stats.completedTasks / stats.totalTasks);
        }
        
        writer.endObject();
    }
    
    /**
     * 序列化任务节点
     * 
     * @param writer JSON写入器
     * @param node 任务节点
     * @param depth 当前深度
     * @throws IOException 写入异常
     */
    private void writeTaskNode(JsonWriter writer, TaskNode node, int depth) throws IOException {
        // 检查深度限制
        if (config.getMaxDepth() > 0 && depth > config.getMaxDepth()) {
            writer.beginObject();
            writer.name("error").value("Maximum depth exceeded");
            writer.name("maxDepth").value(config.getMaxDepth());
            writer.endObject();
            return;
        }
        
        writer.beginObject();
        
        // 节点基本信息
        writer.name("nodeId").value(node.getNodeId());
        writer.name("name").value(node.getName());
        writer.name("depth").value(node.getDepth());
        writer.name("status").value(node.getStatus().toString());
        
        // 时间信息
        writer.name("timing");
        writeTaskTiming(writer, node);
        
        // 消息列表
        if (config.isIncludeMessages()) {
            writer.name("messages");
            writeTaskMessages(writer, node);
        }
        
        // 子节点列表
        writer.name("children");
        writeTaskChildren(writer, node, depth);
        
        // 扩展信息（可选）
        if (config.isIncludeExtendedInfo()) {
            writeTaskExtendedInfo(writer, node);
        }
        
        writer.endObject();
    }
    
    /**
     * 序列化任务时间信息
     * 
     * @param writer JSON写入器
     * @param node 任务节点
     * @throws IOException 写入异常
     */
    private void writeTaskTiming(JsonWriter writer, TaskNode node) throws IOException {
        writer.beginObject();
        
        writer.name("startNano").value(node.getStartNano());
        writer.name("endNano").value(node.getEndNano());
        writer.name("durationNanos").value(node.getDurationNanos());
        writer.name("durationMs").value(node.getDurationMillis());
        
        // 时间戳转换（毫秒精度）
        if (config.isIncludeTimestamps()) {
            long baseTime = System.currentTimeMillis() - (System.nanoTime() / 1_000_000);
            writer.name("startTimestamp").value(baseTime + node.getStartNano() / 1_000_000);
            if (node.getEndNano() > 0) {
                writer.name("endTimestamp").value(baseTime + node.getEndNano() / 1_000_000);
            }
        }
        
        writer.endObject();
    }
    
    /**
     * 序列化任务消息
     * 
     * @param writer JSON写入器
     * @param node 任务节点
     * @throws IOException 写入异常
     */
    private void writeTaskMessages(JsonWriter writer, TaskNode node) throws IOException {
        List<Message> messages = node.getMessages();
        
        // 应用消息过滤和限制
        List<Message> filteredMessages = filterMessages(messages);
        
        writer.beginArray();
        
        for (Message message : filteredMessages) {
            writer.beginObject();
            writer.name("messageId").value(message.getMessageId());
            writer.name("type").value(message.getType().toString());
            writer.name("content").value(message.getContent());
            writer.name("timestampNano").value(message.getTimestampNano());
            writer.name("timestampMillis").value(message.getTimestampMillis());
            
            if (config.isIncludeFormattedTimestamp()) {
                writer.name("formattedTimestamp").value(message.getFormattedTimestamp());
            }
            
            writer.endObject();
        }
        
        // 如果消息被截断，添加说明
        if (messages.size() > filteredMessages.size()) {
            writer.beginObject();
            writer.name("$truncated").value(true);
            writer.name("totalMessages").value(messages.size());
            writer.name("showingMessages").value(filteredMessages.size());
            writer.endObject();
        }
        
        writer.endArray();
    }
    
    /**
     * 序列化子任务节点
     * 
     * @param writer JSON写入器
     * @param node 任务节点
     * @param depth 当前深度
     * @throws IOException 写入异常
     */
    private void writeTaskChildren(JsonWriter writer, TaskNode node, int depth) throws IOException {
        List<TaskNode> children = node.getChildren();
        
        writer.beginArray();
        
        for (TaskNode child : children) {
            writeTaskNode(writer, child, depth + 1);
        }
        
        writer.endArray();
    }
    
    /**
     * 序列化任务扩展信息
     * 
     * @param writer JSON写入器
     * @param node 任务节点
     * @throws IOException 写入异常
     */
    private void writeTaskExtendedInfo(JsonWriter writer, TaskNode node) throws IOException {
        writer.name("extended");
        writer.beginObject();
        
        // 统计信息
        writer.name("childCount").value(node.getChildren().size());
        writer.name("messageCount").value(node.getMessages().size());
        writer.name("isLeaf").value(node.isLeaf());
        writer.name("isRoot").value(node.isRoot());
        
        // 路径信息
        writer.name("pathName").value(node.getPathName());
        
        // 消息统计
        writeMessageStatistics(writer, node);
        
        writer.endObject();
    }
    
    /**
     * 序列化消息统计信息
     * 
     * @param writer JSON写入器
     * @param node 任务节点
     * @throws IOException 写入异常
     */
    private void writeMessageStatistics(JsonWriter writer, TaskNode node) throws IOException {
        List<Message> messages = node.getMessages();
        
        int[] counts = new int[MessageType.values().length];
        for (Message message : messages) {
            counts[message.getType().ordinal()]++;
        }
        
        writer.name("messagesByType");
        writer.beginObject();
        
        for (MessageType type : MessageType.values()) {
            int count = counts[type.ordinal()];
            if (count > 0) {
                writer.name(type.toString().toLowerCase()).value(count);
            }
        }
        
        writer.endObject();
    }
    
    // === 辅助方法 ===
    
    /**
     * 创建错误JSON
     * 
     * @param errorMessage 错误消息
     * @return 错误JSON字符串
     */
    private String createErrorJson(String errorMessage) {
        return String.format("{\"error\":\"%s\",\"timestamp\":%d}", 
                           escapeJsonString(errorMessage), 
                           System.currentTimeMillis());
    }
    
    /**
     * 过滤消息
     * 
     * @param messages 原始消息列表
     * @return 过滤后的消息列表
     */
    private List<Message> filterMessages(List<Message> messages) {
        if (messages.isEmpty()) {
            return messages;
        }
        
        java.util.stream.Stream<Message> stream = messages.stream();
        
        // 类型过滤
        if (config.hasMessageFilter()) {
            stream = stream.filter(msg -> config.getMessageFilter().contains(msg.getType()));
        }
        
        // 数量限制
        if (config.getMaxMessagesPerTask() > 0) {
            stream = stream.limit(config.getMaxMessagesPerTask());
        }
        
        return stream.collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 计算会话统计信息
     * 
     * @param root 根节点
     * @return 统计信息
     */
    private SessionStatistics calculateSessionStatistics(TaskNode root) {
        SessionStatistics stats = new SessionStatistics();
        calculateStatisticsRecursive(root, stats);
        return stats;
    }
    
    /**
     * 递归计算统计信息
     * 
     * @param node 当前节点
     * @param stats 统计信息累积器
     */
    private void calculateStatisticsRecursive(TaskNode node, SessionStatistics stats) {
        stats.totalTasks++;
        stats.maxDepth = Math.max(stats.maxDepth, node.getDepth());
        
        // 状态统计
        TaskStatus status = node.getStatus();
        if (status == TaskStatus.COMPLETED) {
            stats.completedTasks++;
        } else if (status == TaskStatus.FAILED) {
            stats.failedTasks++;
        } else if (status == TaskStatus.RUNNING) {
            stats.runningTasks++;
        }
        
        // 消息统计
        List<Message> messages = node.getMessages();
        stats.totalMessages += messages.size();
        for (Message message : messages) {
            MessageType type = message.getType();
            if (type == MessageType.ERROR) {
                stats.errorMessages++;
            } else if (type == MessageType.WARN) {
                stats.warningMessages++;
            }
        }
        
        // 递归处理子节点
        for (TaskNode child : node.getChildren()) {
            calculateStatisticsRecursive(child, stats);
        }
    }
    
    /**
     * 转义JSON字符串
     * 
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\b", "\\b")
                 .replace("\f", "\\f")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    /**
     * 会话统计信息
     */
    private static class SessionStatistics {
        int totalTasks = 0;
        int completedTasks = 0;
        int failedTasks = 0;
        int runningTasks = 0;
        int maxDepth = 0;
        int totalMessages = 0;
        int errorMessages = 0;
        int warningMessages = 0;
    }
}
```

### 15.2 JsonWriter自定义JSON写入器

```java
package com.syy.taskflowinsight.export;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 自定义JSON写入器
 * 提供格式化和压缩输出支持
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
final class JsonWriter {
    
    private enum Context { OBJECT, ARRAY }
    
    private final Writer writer;
    private final JsonExportConfig config;
    private final Deque<Context> contextStack = new ArrayDeque<>();
    private final Deque<Boolean> firstElementStack = new ArrayDeque<>();
    
    private int indentLevel = 0;
    private boolean pendingName = false;
    private String currentName;
    
    JsonWriter(Writer writer, JsonExportConfig config) {
        this.writer = writer;
        this.config = config;
    }
    
    /**
     * 重置写入器状态
     */
    void reset() throws IOException {
        contextStack.clear();
        firstElementStack.clear();
        indentLevel = 0;
        pendingName = false;
        currentName = null;
    }
    
    /**
     * 开始对象
     */
    JsonWriter beginObject() throws IOException {
        beforeValue();
        writer.write('{');
        contextStack.push(Context.OBJECT);
        firstElementStack.push(true);
        indentLevel++;
        return this;
    }
    
    /**
     * 结束对象
     */
    JsonWriter endObject() throws IOException {
        if (contextStack.isEmpty() || contextStack.peek() != Context.OBJECT) {
            throw new IllegalStateException("Not in object context");
        }
        
        indentLevel--;
        contextStack.pop();
        firstElementStack.pop();
        
        if (config.isFormattedOutput()) {
            writer.write('\n');
            writeIndent();
        }
        writer.write('}');
        return this;
    }
    
    /**
     * 开始数组
     */
    JsonWriter beginArray() throws IOException {
        beforeValue();
        writer.write('[');
        contextStack.push(Context.ARRAY);
        firstElementStack.push(true);
        indentLevel++;
        return this;
    }
    
    /**
     * 结束数组
     */
    JsonWriter endArray() throws IOException {
        if (contextStack.isEmpty() || contextStack.peek() != Context.ARRAY) {
            throw new IllegalStateException("Not in array context");
        }
        
        indentLevel--;
        contextStack.pop();
        firstElementStack.pop();
        
        if (config.isFormattedOutput()) {
            writer.write('\n');
            writeIndent();
        }
        writer.write(']');
        return this;
    }
    
    /**
     * 写入属性名
     */
    JsonWriter name(String name) throws IOException {
        if (contextStack.isEmpty() || contextStack.peek() != Context.OBJECT) {
            throw new IllegalStateException("Not in object context");
        }
        
        beforeName();
        writeStringLiteral(name);
        writer.write(':');
        if (config.isFormattedOutput()) {
            writer.write(' ');
        }
        pendingName = true;
        currentName = name;
        return this;
    }
    
    /**
     * 写入字符串值
     */
    JsonWriter value(String value) throws IOException {
        beforeValue();
        if (value == null) {
            writer.write("null");
        } else {
            writeStringLiteral(value);
        }
        return this;
    }
    
    /**
     * 写入数字值
     */
    JsonWriter value(long value) throws IOException {
        beforeValue();
        writer.write(String.valueOf(value));
        return this;
    }
    
    /**
     * 写入数字值
     */
    JsonWriter value(double value) throws IOException {
        beforeValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            writer.write("null");
        } else {
            writer.write(String.valueOf(value));
        }
        return this;
    }
    
    /**
     * 写入布尔值
     */
    JsonWriter value(boolean value) throws IOException {
        beforeValue();
        writer.write(value ? "true" : "false");
        return this;
    }
    
    /**
     * 写入null值
     */
    JsonWriter nullValue() throws IOException {
        beforeValue();
        writer.write("null");
        return this;
    }
    
    /**
     * 写入错误对象
     */
    JsonWriter writeErrorObject(String errorMessage) throws IOException {
        beginObject();
        name("error").value(errorMessage);
        name("timestamp").value(System.currentTimeMillis());
        endObject();
        return this;
    }
    
    // === 私有辅助方法 ===
    
    /**
     * 值写入前的准备工作
     */
    private void beforeValue() throws IOException {
        if (pendingName) {
            pendingName = false;
        } else if (!contextStack.isEmpty()) {
            Context context = contextStack.peek();
            if (context == Context.ARRAY) {
                beforeArrayElement();
            } else {
                throw new IllegalStateException("Unexpected value in object context");
            }
        }
    }
    
    /**
     * 属性名写入前的准备工作
     */
    private void beforeName() throws IOException {
        if (!firstElementStack.peek()) {
            writer.write(',');
        } else {
            firstElementStack.pop();
            firstElementStack.push(false);
        }
        
        if (config.isFormattedOutput()) {
            writer.write('\n');
            writeIndent();
        }
    }
    
    /**
     * 数组元素写入前的准备工作
     */
    private void beforeArrayElement() throws IOException {
        if (!firstElementStack.peek()) {
            writer.write(',');
        } else {
            firstElementStack.pop();
            firstElementStack.push(false);
        }
        
        if (config.isFormattedOutput()) {
            writer.write('\n');
            writeIndent();
        }
    }
    
    /**
     * 写入缩进
     */
    private void writeIndent() throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            writer.write(config.getIndentString());
        }
    }
    
    /**
     * 写入字符串字面量
     */
    private void writeStringLiteral(String str) throws IOException {
        writer.write('"');
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    writer.write("\\\"");
                    break;
                case '\\':
                    writer.write("\\\\");
                    break;
                case '\b':
                    writer.write("\\b");
                    break;
                case '\f':
                    writer.write("\\f");
                    break;
                case '\n':
                    writer.write("\\n");
                    break;
                case '\r':
                    writer.write("\\r");
                    break;
                case '\t':
                    writer.write("\\t");
                    break;
                default:
                    if (c < 0x20 || (c >= 0x7f && c < 0xa0)) {
                        writer.write(String.format("\\u%04x", (int) c));
                    } else {
                        writer.write(c);
                    }
                    break;
            }
        }
        
        writer.write('"');
    }
}
```

### 15.3 JsonExportConfig配置类

```java
package com.syy.taskflowinsight.export;

import com.syy.taskflowinsight.enums.MessageType;
import java.util.Set;
import java.util.EnumSet;

/**
 * JSON导出配置
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class JsonExportConfig {
    
    private final boolean formattedOutput;
    private final boolean includeMessages;
    private final boolean includeStatistics;
    private final boolean includeExtendedInfo;
    private final boolean includeTimestamps;
    private final boolean includeFormattedTimestamp;
    
    private final int maxDepth;
    private final int maxMessagesPerTask;
    private final String indentString;
    
    private final Set<MessageType> messageFilter;
    
    private JsonExportConfig(Builder builder) {
        this.formattedOutput = builder.formattedOutput;
        this.includeMessages = builder.includeMessages;
        this.includeStatistics = builder.includeStatistics;
        this.includeExtendedInfo = builder.includeExtendedInfo;
        this.includeTimestamps = builder.includeTimestamps;
        this.includeFormattedTimestamp = builder.includeFormattedTimestamp;
        
        this.maxDepth = builder.maxDepth;
        this.maxMessagesPerTask = builder.maxMessagesPerTask;
        this.indentString = builder.indentString;
        
        this.messageFilter = builder.messageFilter != null ? 
                            EnumSet.copyOf(builder.messageFilter) : null;
    }
    
    /**
     * 创建默认配置
     * 
     * @return 默认配置
     */
    public static JsonExportConfig defaultConfig() {
        return new Builder().build();
    }
    
    /**
     * 创建紧凑配置（最小化输出）
     * 
     * @return 紧凑配置
     */
    public static JsonExportConfig compactConfig() {
        return new Builder()
                .formattedOutput(false)
                .includeExtendedInfo(false)
                .includeTimestamps(false)
                .includeFormattedTimestamp(false)
                .maxMessagesPerTask(5)
                .build();
    }
    
    /**
     * 创建完整配置（包含所有信息）
     * 
     * @return 完整配置
     */
    public static JsonExportConfig verboseConfig() {
        return new Builder()
                .formattedOutput(true)
                .includeMessages(true)
                .includeStatistics(true)
                .includeExtendedInfo(true)
                .includeTimestamps(true)
                .includeFormattedTimestamp(true)
                .maxDepth(-1)
                .maxMessagesPerTask(-1)
                .build();
    }
    
    // Getter方法
    public boolean isFormattedOutput() { return formattedOutput; }
    public boolean isIncludeMessages() { return includeMessages; }
    public boolean isIncludeStatistics() { return includeStatistics; }
    public boolean isIncludeExtendedInfo() { return includeExtendedInfo; }
    public boolean isIncludeTimestamps() { return includeTimestamps; }
    public boolean isIncludeFormattedTimestamp() { return includeFormattedTimestamp; }
    
    public int getMaxDepth() { return maxDepth; }
    public int getMaxMessagesPerTask() { return maxMessagesPerTask; }
    public String getIndentString() { return indentString; }
    
    public Set<MessageType> getMessageFilter() { return messageFilter; }
    public boolean hasMessageFilter() { return messageFilter != null && !messageFilter.isEmpty(); }
    
    /**
     * 配置构建器
     */
    public static class Builder {
        private boolean formattedOutput = false;
        private boolean includeMessages = true;
        private boolean includeStatistics = true;
        private boolean includeExtendedInfo = false;
        private boolean includeTimestamps = false;
        private boolean includeFormattedTimestamp = false;
        
        private int maxDepth = 100;
        private int maxMessagesPerTask = 50;
        private String indentString = "  ";
        
        private Set<MessageType> messageFilter;
        
        public Builder formattedOutput(boolean formatted) {
            this.formattedOutput = formatted;
            return this;
        }
        
        public Builder includeMessages(boolean include) {
            this.includeMessages = include;
            return this;
        }
        
        public Builder includeStatistics(boolean include) {
            this.includeStatistics = include;
            return this;
        }
        
        public Builder includeExtendedInfo(boolean include) {
            this.includeExtendedInfo = include;
            return this;
        }
        
        public Builder includeTimestamps(boolean include) {
            this.includeTimestamps = include;
            return this;
        }
        
        public Builder includeFormattedTimestamp(boolean include) {
            this.includeFormattedTimestamp = include;
            return this;
        }
        
        public Builder maxDepth(int depth) {
            this.maxDepth = depth;
            return this;
        }
        
        public Builder maxMessagesPerTask(int max) {
            this.maxMessagesPerTask = max;
            return this;
        }
        
        public Builder indentString(String indent) {
            this.indentString = indent != null ? indent : "  ";
            return this;
        }
        
        public Builder messageFilter(Set<MessageType> filter) {
            this.messageFilter = filter;
            return this;
        }
        
        public JsonExportConfig build() {
            return new JsonExportConfig(this);
        }
    }
}
```

## 测试标准

### 15.1 功能测试要求

1. **序列化正确性测试**
   - 验证所有字段的正确序列化
   - 验证嵌套结构的完整性
   - 验证特殊字符和Unicode的处理
   - 验证null值的正确处理

2. **JSON格式验证**
   - 验证输出的JSON格式符合标准
   - 验证JSON Schema的正确性
   - 验证字符转义的正确性
   - 验证数字和布尔值格式

3. **配置选项测试**
   - 验证各种导出配置的生效
   - 验证过滤器功能的正确性
   - 验证格式化输出和压缩输出
   - 验证深度和消息限制

4. **边界情况测试**
   - 空Session和空TaskNode的处理
   - 极大数据量的序列化
   - 极深嵌套结构的处理
   - 异常情况下的错误JSON生成

### 15.2 性能测试要求

1. **序列化性能**
   - 1000个任务的序列化时间 < 100ms
   - 深度嵌套（100层）的序列化性能
   - 大量消息（10000条）的序列化性能

2. **内存使用测试**
   - 序列化过程的内存峰值控制
   - 字符串构建的内存效率
   - 大数据量时的内存稳定性

3. **输出大小测试**
   - 压缩输出与格式化输出的大小对比
   - 不同配置下的输出大小差异

## 验收标准

### 15.1 功能验收

- [ ] 完整实现Session和TaskNode的JSON序列化
- [ ] 支持多种导出配置和格式选项
- [ ] 正确处理各种数据类型和特殊情况
- [ ] JSON输出格式规范且易于解析
- [ ] 提供JSON Schema定义

### 15.2 质量验收

- [ ] 代码结构清晰，易于维护和扩展
- [ ] 字符转义和Unicode处理正确
- [ ] 错误处理完善，异常安全
- [ ] 配置系统灵活且易用

### 15.3 性能验收

- [ ] 序列化性能满足要求
- [ ] 内存使用合理且稳定
- [ ] 支持大数据量的高效处理

### 15.4 测试验收

- [ ] 单元测试覆盖率 ≥ 95%
- [ ] JSON格式验证测试通过
- [ ] 性能测试满足指标要求
- [ ] 与其他系统的集成测试通过

## 依赖关系

- **前置依赖**: TASK-001, TASK-002, TASK-003, TASK-004 (核心数据模型)
- **后置依赖**: TASK-016 (输出格式测试)
- **相关任务**: TASK-014 (控制台输出), TASK-010 (TFI主API调用导出功能)

## 预计工期

- **开发时间**: 2天
- **测试时间**: 1天
- **总计**: 3天

## 风险识别

1. **性能风险**: 大数据量序列化可能影响性能
   - **缓解措施**: 实现流式序列化和数据限制

2. **内存风险**: 复杂结构可能导致内存占用过高
   - **缓解措施**: 优化字符串构建和内存使用

3. **兼容性风险**: JSON格式的向后兼容性
   - **缓解措施**: 定义稳定的JSON Schema和版本策略