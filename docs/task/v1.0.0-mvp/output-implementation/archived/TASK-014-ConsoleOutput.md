# TASK-014: 控制台树形输出实现

## 任务背景

控制台输出是TaskFlow Insight最重要的用户交互功能之一，它需要将复杂的任务树结构以清晰、直观的方式展示给用户。控制台输出必须支持树形结构可视化、消息展示、性能数据显示，同时要考虑不同终端环境的兼容性和大量数据的展示性能。

## 目标

1. 实现完整的控制台输出器，支持多种输出格式
2. 提供树形结构的可视化展示，清晰显示任务层级关系
3. 支持消息、性能数据、状态信息的格式化输出
4. 提供多种输出样式选择，适配不同使用场景
5. 实现输出内容的过滤和限制功能
6. 确保输出性能优良，支持大量数据的展示
7. 提供颜色输出支持，提升用户体验

## 实现方案

### 14.1 ConsoleExporter核心实现

```java
package com.syy.taskflowinsight.export;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.enums.TaskStatus;

import java.util.List;
import java.util.Arrays;
import java.io.PrintStream;

/**
 * 控制台输出器
 * 提供多种格式的任务树控制台输出功能
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class ConsoleExporter {
    
    // 树形字符常量
    private static final String TREE_BRANCH = "├── ";
    private static final String TREE_LAST_BRANCH = "└── ";
    private static final String TREE_VERTICAL = "│   ";
    private static final String TREE_SPACE = "    ";
    
    // 输出配置
    private final ConsoleOutputConfig config;
    private final ConsoleColorSupport colorSupport;
    
    public ConsoleExporter() {
        this(ConsoleOutputConfig.defaultConfig());
    }
    
    public ConsoleExporter(ConsoleOutputConfig config) {
        this.config = config != null ? config : ConsoleOutputConfig.defaultConfig();
        this.colorSupport = new ConsoleColorSupport(config.isColorEnabled());
    }
    
    /**
     * 导出会话到字符串
     * 
     * @param session 会话对象
     * @return 格式化的输出字符串
     */
    public String export(Session session) {
        if (session == null) {
            return "No session data available";
        }
        
        StringBuilder output = new StringBuilder();
        
        // 输出会话头部信息
        exportSessionHeader(output, session);
        
        // 输出任务树
        TaskNode root = session.getRoot();
        if (root != null) {
            output.append("\n");
            exportTaskTree(output, root, "", true, 0);
        } else {
            output.append("\nNo task data available\n");
        }
        
        // 输出会话尾部信息
        exportSessionFooter(output, session);
        
        return output.toString();
    }
    
    /**
     * 直接打印到控制台
     * 
     * @param session 会话对象
     */
    public void print(Session session) {
        print(session, System.out);
    }
    
    /**
     * 打印到指定输出流
     * 
     * @param session 会话对象
     * @param out 输出流
     */
    public void print(Session session, PrintStream out) {
        String output = export(session);
        out.print(output);
    }
    
    /**
     * 输出会话头部信息
     * 
     * @param output 输出缓冲区
     * @param session 会话对象
     */
    private void exportSessionHeader(StringBuilder output, Session session) {
        String separator = "=".repeat(config.getSeparatorLength());
        
        output.append(colorSupport.header(separator)).append("\n");
        output.append(colorSupport.header("TaskFlow Insight Report")).append("\n");
        output.append(colorSupport.header(separator)).append("\n");
        
        // 基本信息
        output.append("Session ID: ").append(colorSupport.sessionId(session.getSessionId())).append("\n");
        output.append("Thread ID:  ").append(colorSupport.threadId(String.valueOf(session.getThreadId()))).append("\n");
        output.append("Status:     ").append(colorSupport.sessionStatus(session.getStatus().toString())).append("\n");
        output.append("Created:    ").append(colorSupport.timestamp(formatTimestamp(session.getCreatedAt()))).append("\n");
        
        if (session.getEndedAt() > 0) {
            output.append("Ended:      ").append(colorSupport.timestamp(formatTimestamp(session.getEndedAt()))).append("\n");
        }
        
        output.append("Duration:   ").append(colorSupport.duration(formatDuration(session.getDurationMillis()))).append("\n");
        
        // 统计信息
        if (config.isShowStatistics()) {
            exportSessionStatistics(output, session);
        }
    }
    
    /**
     * 输出会话统计信息
     * 
     * @param output 输出缓冲区
     * @param session 会话对象
     */
    private void exportSessionStatistics(StringBuilder output, Session session) {
        TaskNode root = session.getRoot();
        if (root == null) {
            return;
        }
        
        SessionStatistics stats = calculateStatistics(root);
        
        output.append("\n");
        output.append("Statistics:\n");
        output.append("  Total Tasks:    ").append(colorSupport.number(String.valueOf(stats.totalTasks))).append("\n");
        output.append("  Completed:      ").append(colorSupport.success(String.valueOf(stats.completedTasks))).append("\n");
        output.append("  Failed:         ").append(colorSupport.error(String.valueOf(stats.failedTasks))).append("\n");
        output.append("  Max Depth:      ").append(colorSupport.number(String.valueOf(stats.maxDepth))).append("\n");
        output.append("  Total Messages: ").append(colorSupport.number(String.valueOf(stats.totalMessages))).append("\n");
        
        if (stats.totalTasks > 0) {
            double successRate = (double) stats.completedTasks / stats.totalTasks * 100;
            output.append("  Success Rate:   ").append(colorSupport.percentage(String.format("%.1f%%", successRate))).append("\n");
        }
    }
    
    /**
     * 输出任务树
     * 
     * @param output 输出缓冲区
     * @param node 当前节点
     * @param prefix 前缀字符串
     * @param isLast 是否为最后一个子节点
     * @param depth 当前深度
     */
    private void exportTaskTree(StringBuilder output, TaskNode node, String prefix, boolean isLast, int depth) {
        // 检查深度限制
        if (config.getMaxDepth() > 0 && depth > config.getMaxDepth()) {
            output.append(prefix).append(isLast ? TREE_LAST_BRANCH : TREE_BRANCH);
            output.append(colorSupport.warning("... (max depth reached)")).append("\n");
            return;
        }
        
        // 输出当前节点
        exportTaskNode(output, node, prefix, isLast);
        
        // 输出消息
        if (config.isShowMessages()) {
            exportTaskMessages(output, node, prefix, isLast);
        }
        
        // 递归输出子节点
        List<TaskNode> children = node.getChildren();
        if (!children.isEmpty()) {
            String childPrefix = prefix + (isLast ? TREE_SPACE : TREE_VERTICAL);
            
            for (int i = 0; i < children.size(); i++) {
                boolean isLastChild = (i == children.size() - 1);
                exportTaskTree(output, children.get(i), childPrefix, isLastChild, depth + 1);
            }
        }
    }
    
    /**
     * 输出单个任务节点
     * 
     * @param output 输出缓冲区
     * @param node 任务节点
     * @param prefix 前缀字符串
     * @param isLast 是否为最后一个子节点
     */
    private void exportTaskNode(StringBuilder output, TaskNode node, String prefix, boolean isLast) {
        // 树形结构字符
        output.append(prefix).append(isLast ? TREE_LAST_BRANCH : TREE_BRANCH);
        
        // 任务名称
        output.append(colorSupport.taskName(node.getName()));
        
        // 执行时间
        long duration = node.getDurationMillis();
        if (duration >= 0) {
            output.append(" (").append(colorSupport.duration(formatDuration(duration))).append(")");
        }
        
        // 任务状态
        TaskStatus status = node.getStatus();
        if (status != TaskStatus.COMPLETED) {
            output.append(" [").append(colorSupport.taskStatus(status.toString())).append("]");
        }
        
        // 额外信息
        if (config.isShowTaskDetails()) {
            exportTaskDetails(output, node);
        }
        
        output.append("\n");
    }
    
    /**
     * 输出任务详细信息
     * 
     * @param output 输出缓冲区
     * @param node 任务节点
     */
    private void exportTaskDetails(StringBuilder output, TaskNode node) {
        // 子任务数量
        int childCount = node.getChildren().size();
        if (childCount > 0) {
            output.append(" {").append(colorSupport.info(childCount + " children")).append("}");
        }
        
        // 消息数量
        int messageCount = node.getMessages().size();
        if (messageCount > 0) {
            output.append(" {").append(colorSupport.info(messageCount + " messages")).append("}");
        }
        
        // 深度信息（调试用）
        if (config.isDebugMode()) {
            output.append(" {depth: ").append(node.getDepth()).append("}");
        }
    }
    
    /**
     * 输出任务消息
     * 
     * @param output 输出缓冲区
     * @param node 任务节点
     * @param prefix 前缀字符串
     * @param isLast 是否为最后一个子节点
     */
    private void exportTaskMessages(StringBuilder output, TaskNode node, String prefix, boolean isLast) {
        List<Message> messages = node.getMessages();
        if (messages.isEmpty()) {
            return;
        }
        
        // 消息过滤
        List<Message> filteredMessages = filterMessages(messages);
        if (filteredMessages.isEmpty()) {
            return;
        }
        
        String messagePrefix = prefix + (isLast ? TREE_SPACE : TREE_VERTICAL) + "    ";
        
        for (Message message : filteredMessages) {
            output.append(messagePrefix)
                  .append(colorSupport.messageType(message.getType().toString()))
                  .append(": ")
                  .append(colorSupport.messageContent(message.getContent(), message.getType()))
                  .append("\n");
        }
    }
    
    /**
     * 过滤消息
     * 
     * @param messages 原始消息列表
     * @return 过滤后的消息列表
     */
    private List<Message> filterMessages(List<Message> messages) {
        if (!config.hasMessageFilter()) {
            return messages;
        }
        
        return messages.stream()
                .filter(msg -> config.getMessageFilter().contains(msg.getType()))
                .limit(config.getMaxMessagesPerTask())
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 输出会话尾部信息
     * 
     * @param output 输出缓冲区
     * @param session 会话对象
     */
    private void exportSessionFooter(StringBuilder output, Session session) {
        String separator = "=".repeat(config.getSeparatorLength());
        output.append("\n").append(colorSupport.header(separator)).append("\n");
        
        if (config.isShowFooter()) {
            output.append("Generated by TaskFlow Insight v1.0.0\n");
            output.append("Export time: ").append(formatTimestamp(System.currentTimeMillis())).append("\n");
        }
    }
    
    // === 辅助方法 ===
    
    /**
     * 格式化时间戳
     * 
     * @param timestamp 时间戳（毫秒）
     * @return 格式化的时间字符串
     */
    private String formatTimestamp(long timestamp) {
        return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
    
    /**
     * 格式化持续时间
     * 
     * @param durationMs 持续时间（毫秒）
     * @return 格式化的时间字符串
     */
    private String formatDuration(long durationMs) {
        if (durationMs < 0) {
            return "N/A";
        }
        
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.2fs", durationMs / 1000.0);
        } else {
            long minutes = durationMs / 60000;
            long seconds = (durationMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    /**
     * 计算会话统计信息
     * 
     * @param root 根节点
     * @return 统计信息
     */
    private SessionStatistics calculateStatistics(TaskNode root) {
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
        stats.totalMessages += node.getMessages().size();
        
        TaskStatus status = node.getStatus();
        if (status == TaskStatus.COMPLETED) {
            stats.completedTasks++;
        } else if (status == TaskStatus.FAILED) {
            stats.failedTasks++;
        }
        
        // 递归处理子节点
        for (TaskNode child : node.getChildren()) {
            calculateStatisticsRecursive(child, stats);
        }
    }
    
    /**
     * 会话统计信息
     */
    private static class SessionStatistics {
        int totalTasks = 0;
        int completedTasks = 0;
        int failedTasks = 0;
        int maxDepth = 0;
        int totalMessages = 0;
    }
}
```

### 14.2 ConsoleOutputConfig配置类

```java
package com.syy.taskflowinsight.export;

import com.syy.taskflowinsight.enums.MessageType;
import java.util.Set;
import java.util.EnumSet;

/**
 * 控制台输出配置
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class ConsoleOutputConfig {
    
    private final boolean colorEnabled;
    private final boolean showMessages;
    private final boolean showStatistics;
    private final boolean showTaskDetails;
    private final boolean showFooter;
    private final boolean debugMode;
    
    private final int maxDepth;
    private final int maxMessagesPerTask;
    private final int separatorLength;
    
    private final Set<MessageType> messageFilter;
    
    private ConsoleOutputConfig(Builder builder) {
        this.colorEnabled = builder.colorEnabled;
        this.showMessages = builder.showMessages;
        this.showStatistics = builder.showStatistics;
        this.showTaskDetails = builder.showTaskDetails;
        this.showFooter = builder.showFooter;
        this.debugMode = builder.debugMode;
        
        this.maxDepth = builder.maxDepth;
        this.maxMessagesPerTask = builder.maxMessagesPerTask;
        this.separatorLength = builder.separatorLength;
        
        this.messageFilter = builder.messageFilter != null ? 
                            EnumSet.copyOf(builder.messageFilter) : null;
    }
    
    /**
     * 创建默认配置
     * 
     * @return 默认配置
     */
    public static ConsoleOutputConfig defaultConfig() {
        return new Builder().build();
    }
    
    /**
     * 创建简洁配置（适合CI环境）
     * 
     * @return 简洁配置
     */
    public static ConsoleOutputConfig compactConfig() {
        return new Builder()
                .colorEnabled(false)
                .showStatistics(false)
                .showTaskDetails(false)
                .showFooter(false)
                .maxMessagesPerTask(3)
                .build();
    }
    
    /**
     * 创建详细配置（适合开发调试）
     * 
     * @return 详细配置
     */
    public static ConsoleOutputConfig verboseConfig() {
        return new Builder()
                .colorEnabled(true)
                .showMessages(true)
                .showStatistics(true)
                .showTaskDetails(true)
                .showFooter(true)
                .debugMode(true)
                .maxDepth(-1)
                .maxMessagesPerTask(-1)
                .build();
    }
    
    // Getter方法
    public boolean isColorEnabled() { return colorEnabled; }
    public boolean isShowMessages() { return showMessages; }
    public boolean isShowStatistics() { return showStatistics; }
    public boolean isShowTaskDetails() { return showTaskDetails; }
    public boolean isShowFooter() { return showFooter; }
    public boolean isDebugMode() { return debugMode; }
    
    public int getMaxDepth() { return maxDepth; }
    public int getMaxMessagesPerTask() { return maxMessagesPerTask; }
    public int getSeparatorLength() { return separatorLength; }
    
    public Set<MessageType> getMessageFilter() { return messageFilter; }
    public boolean hasMessageFilter() { return messageFilter != null && !messageFilter.isEmpty(); }
    
    /**
     * 配置构建器
     */
    public static class Builder {
        private boolean colorEnabled = isColorSupportedByDefault();
        private boolean showMessages = true;
        private boolean showStatistics = true;
        private boolean showTaskDetails = false;
        private boolean showFooter = true;
        private boolean debugMode = false;
        
        private int maxDepth = 50;
        private int maxMessagesPerTask = 10;
        private int separatorLength = 60;
        
        private Set<MessageType> messageFilter;
        
        public Builder colorEnabled(boolean enabled) {
            this.colorEnabled = enabled;
            return this;
        }
        
        public Builder showMessages(boolean show) {
            this.showMessages = show;
            return this;
        }
        
        public Builder showStatistics(boolean show) {
            this.showStatistics = show;
            return this;
        }
        
        public Builder showTaskDetails(boolean show) {
            this.showTaskDetails = show;
            return this;
        }
        
        public Builder showFooter(boolean show) {
            this.showFooter = show;
            return this;
        }
        
        public Builder debugMode(boolean debug) {
            this.debugMode = debug;
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
        
        public Builder separatorLength(int length) {
            this.separatorLength = Math.max(20, length);
            return this;
        }
        
        public Builder messageFilter(Set<MessageType> filter) {
            this.messageFilter = filter;
            return this;
        }
        
        public Builder messageFilter(MessageType... types) {
            this.messageFilter = EnumSet.copyOf(Arrays.asList(types));
            return this;
        }
        
        public ConsoleOutputConfig build() {
            return new ConsoleOutputConfig(this);
        }
        
        /**
         * 检测系统是否默认支持颜色输出
         * 
         * @return true if color supported by default
         */
        private static boolean isColorSupportedByDefault() {
            // 简单的颜色支持检测逻辑
            String term = System.getenv("TERM");
            String colorTerm = System.getenv("COLORTERM");
            
            // Windows系统检测
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                // Windows 10以上版本支持ANSI颜色
                return System.getProperty("os.version", "").compareTo("10") >= 0;
            }
            
            // Unix系统检测
            return term != null && !term.equals("dumb") || colorTerm != null;
        }
    }
}
```

### 14.3 ConsoleColorSupport颜色支持类

```java
package com.syy.taskflowinsight.export;

import com.syy.taskflowinsight.enums.MessageType;

/**
 * 控制台颜色支持
 * 提供跨平台的颜色输出功能
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class ConsoleColorSupport {
    
    // ANSI颜色代码
    private static final String RESET = "\u001B[0m";
    private static final String BLACK = "\u001B[30m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    
    // 样式代码
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String ITALIC = "\u001B[3m";
    private static final String UNDERLINE = "\u001B[4m";
    
    private final boolean colorEnabled;
    
    public ConsoleColorSupport(boolean colorEnabled) {
        this.colorEnabled = colorEnabled;
    }
    
    /**
     * 应用颜色格式
     * 
     * @param text 文本内容
     * @param color 颜色代码
     * @return 带颜色的文本
     */
    private String applyColor(String text, String color) {
        if (!colorEnabled || text == null) {
            return text != null ? text : "";
        }
        return color + text + RESET;
    }
    
    // === 语义化颜色方法 ===
    
    public String header(String text) {
        return applyColor(text, BOLD + BLUE);
    }
    
    public String sessionId(String text) {
        return applyColor(text, CYAN);
    }
    
    public String threadId(String text) {
        return applyColor(text, DIM + WHITE);
    }
    
    public String sessionStatus(String text) {
        return applyColor(text, BOLD + GREEN);
    }
    
    public String timestamp(String text) {
        return applyColor(text, DIM + WHITE);
    }
    
    public String duration(String text) {
        return applyColor(text, YELLOW);
    }
    
    public String taskName(String text) {
        return applyColor(text, BOLD + WHITE);
    }
    
    public String taskStatus(String text) {
        // 根据状态类型选择颜色
        if (text.contains("COMPLETED")) {
            return applyColor(text, GREEN);
        } else if (text.contains("FAILED") || text.contains("ERROR")) {
            return applyColor(text, RED);
        } else if (text.contains("RUNNING")) {
            return applyColor(text, YELLOW);
        } else {
            return applyColor(text, DIM + WHITE);
        }
    }
    
    public String messageType(String type) {
        if ("ERROR".equals(type)) {
            return applyColor(type, RED);
        } else if ("WARN".equals(type)) {
            return applyColor(type, YELLOW);
        } else if ("INFO".equals(type)) {
            return applyColor(type, BLUE);
        } else if ("DEBUG".equals(type)) {
            return applyColor(type, DIM + WHITE);
        } else {
            return applyColor(type, PURPLE);
        }
    }
    
    public String messageContent(String content, MessageType type) {
        switch (type) {
            case ERROR:
                return applyColor(content, RED);
            case WARN:
                return applyColor(content, YELLOW);
            case DEBUG:
                return applyColor(content, DIM + WHITE);
            case INFO:
            default:
                return content; // 默认颜色
        }
    }
    
    public String success(String text) {
        return applyColor(text, GREEN);
    }
    
    public String error(String text) {
        return applyColor(text, RED);
    }
    
    public String warning(String text) {
        return applyColor(text, YELLOW);
    }
    
    public String info(String text) {
        return applyColor(text, BLUE);
    }
    
    public String number(String text) {
        return applyColor(text, CYAN);
    }
    
    public String percentage(String text) {
        return applyColor(text, BOLD + YELLOW);
    }
    
    /**
     * 检测颜色支持状态
     * 
     * @return true if colors are enabled
     */
    public boolean isColorEnabled() {
        return colorEnabled;
    }
    
    /**
     * 创建带颜色的进度条
     * 
     * @param progress 进度 (0.0 - 1.0)
     * @param width 进度条宽度
     * @return 带颜色的进度条字符串
     */
    public String progressBar(double progress, int width) {
        if (!colorEnabled) {
            // 简单文本进度条
            int filled = (int) (progress * width);
            return "[" + "=".repeat(filled) + " ".repeat(width - filled) + "]";
        }
        
        int filled = (int) (progress * width);
        String bar = GREEN + "█".repeat(filled) + RESET + 
                    DIM + "░".repeat(width - filled) + RESET;
        return "[" + bar + "]";
    }
}
```

## 测试标准

### 14.1 功能测试要求

1. **基本输出测试**
   - 验证空会话和空任务的处理
   - 验证单任务和多任务的输出格式
   - 验证嵌套任务的树形结构显示
   - 验证消息的正确展示

2. **配置选项测试**
   - 验证各种配置选项的生效
   - 验证颜色开关的正确工作
   - 验证过滤器功能
   - 验证深度限制和消息限制

3. **格式化测试**
   - 验证时间格式化的正确性
   - 验证持续时间格式化
   - 验证特殊字符的转义处理

4. **跨平台测试**
   - Windows、Linux、MacOS环境测试
   - 不同终端环境的兼容性测试
   - 颜色支持检测的准确性

### 14.2 性能测试要求

1. **大数据量测试**
   - 1000个任务的输出性能
   - 深度嵌套（100层）的处理性能
   - 大量消息（10000条）的展示性能

2. **内存使用测试**
   - 输出过程的内存峰值控制
   - 字符串构建的内存效率
   - 无内存泄漏验证

3. **输出速度测试**
   - 控制台输出的速度测试
   - 字符串生成的性能测试

## 验收标准

### 14.1 功能验收

- [ ] 完整实现树形结构的可视化输出
- [ ] 支持多种输出配置和样式选择
- [ ] 正确处理各种边界情况和异常数据
- [ ] 跨平台颜色支持正常工作
- [ ] 消息过滤和限制功能正确

### 14.2 质量验收

- [ ] 代码结构清晰，职责明确
- [ ] 输出格式美观，用户体验良好
- [ ] 配置系统灵活，易于扩展
- [ ] 错误处理完善，不会崩溃

### 14.3 性能验收

- [ ] 大数据量输出性能满足要求
- [ ] 内存使用合理且稳定
- [ ] 跨平台性能表现一致

### 14.4 测试验收

- [ ] 单元测试覆盖率 ≥ 90%
- [ ] 跨平台测试通过
- [ ] 性能测试满足指标要求
- [ ] 用户体验测试良好

## 依赖关系

- **前置依赖**: TASK-001, TASK-002, TASK-003, TASK-004 (核心数据模型)
- **后置依赖**: TASK-016 (输出格式测试)
- **相关任务**: TASK-015 (JSON导出), TASK-010 (TFI主API调用输出功能)

## 预计工期

- **开发时间**: 2天
- **测试时间**: 1天
- **总计**: 3天

## 风险识别

1. **跨平台兼容性**: 不同操作系统和终端的差异
   - **缓解措施**: 充分的跨平台测试和兼容性处理

2. **性能风险**: 大量数据输出可能影响性能
   - **缓解措施**: 实现输出限制和优化算法

3. **字符编码问题**: 特殊字符在不同环境下的显示
   - **缓解措施**: 使用标准字符集和转义处理