package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;

import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 控制台导出器
 * 将会话信息以ASCII树形格式输出到控制台或字符串
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-08
 */
public class ConsoleExporter {
    
    // ASCII树形绘制字符
    private static final String BRANCH = "├── ";
    private static final String LAST_BRANCH = "└── ";
    private static final String VERTICAL = "│   ";
    private static final String SPACE = "    ";
    private static final String SEPARATOR_LINE = "=".repeat(50);
    private static final String THIN_LINE = "-".repeat(50);
    
    // 简化缩进字符
    private static final String INDENT_UNIT = "    ";
    
    // 输出选项
    private boolean showTimestamp = true;
    
    /**
     * 导出会话为格式化字符串
     * 
     * @param session 要导出的会话
     * @return 格式化的字符串输出
     */
    public String export(Session session) {
        return export(session, true);
    }
    
    /**
     * 导出会话为格式化字符串
     * 
     * @param session 要导出的会话
     * @param showTimestamp 是否显示时间戳
     * @return 格式化的字符串输出
     */
    public String export(Session session, boolean showTimestamp) {
        this.showTimestamp = showTimestamp;
        return exportInternal(session);
    }
    
    /**
     * 内部导出方法
     */
    private String exportInternal(Session session) {
        if (session == null) {
            return "";
        }
        
        // 预估容量：头部(500) + 每个节点约100字符
        int estimatedCapacity = 500;
        TaskNode root = session.getRootTask();
        if (root != null) {
            estimatedCapacity += countNodes(root) * 100;
        }
        
        StringBuilder sb = new StringBuilder(estimatedCapacity);
        
        // 构建输出
        appendHeader(sb, session);
        
        if (root != null) {
            // 恢复为默认的缩进样式（简化输出）
            appendTaskNodeSimple(sb, root, 0);
        } else {
            sb.append("No tasks executed\n");
        }
        
        sb.append(SEPARATOR_LINE).append("\n");
        
        return sb.toString();
    }
    
    /**
     * 输出会话到标准输出
     * 
     * @param session 要输出的会话
     */
    public void print(Session session) {
        System.out.print(export(session));
    }
    
    /**
     * 输出会话到标准输出（简化格式，无时间戳）
     * 
     * @param session 要输出的会话
     */
    public void printSimple(Session session) {
        System.out.print(export(session, false));
    }
    
    /**
     * 输出会话到指定的PrintStream
     * 
     * @param session 要输出的会话
     * @param out 输出流
     */
    public void print(Session session, PrintStream out) {
        if (out != null) {
            out.print(export(session));
        }
    }
    
    /**
     * 添加会话头部信息
     */
    private void appendHeader(StringBuilder sb, Session session) {
        sb.append(SEPARATOR_LINE).append("\n");
        sb.append("TaskFlow Insight Report\n");
        sb.append(SEPARATOR_LINE).append("\n");
        
        sb.append("Session: ").append(session.getSessionId()).append("\n");
        // 使用会话中记录的线程信息，避免跨线程导出不准确
        sb.append("Thread:  ")
          .append(session.getThreadId())
          .append(" (")
          .append(session.getThreadName())
          .append(")\n");
        sb.append("Status:  ").append(session.getStatus()).append("\n");
        
        if (session.getDurationMillis() != null) {
            sb.append("Duration: ").append(formatDuration(session.getDurationMillis())).append("\n");
        }
        
        sb.append("\n");
    }
    
    /**
     * 递归添加任务节点到输出
     * 
     * @param sb StringBuilder
     * @param node 任务节点
     * @param prefix 当前前缀
     * @param isLast 是否是最后一个节点
     */
    private void appendTaskNode(StringBuilder sb, TaskNode node, String prefix, boolean isLast) {
        // 绘制当前节点连接线
        sb.append(prefix);
        sb.append(isLast ? LAST_BRANCH : BRANCH);
        
        // 输出节点信息
        sb.append(node.getTaskName());
        
        // 添加累计/自身时间和状态信息
        long selfMs = node.getSelfDurationMillis();
        long accMs = node.getAccumulatedDurationMillis();
        sb.append(" (")
          .append(formatDuration(accMs)).append(", self ")
          .append(formatDuration(selfMs)).append(", ")
          .append(node.getStatus()).append(")");
        sb.append("\n");
        
        // 准备子节点的前缀
        String childPrefix = prefix + (isLast ? SPACE : VERTICAL);
        
        // 输出消息
        List<Message> messages = node.getMessages();
        if (!messages.isEmpty()) {
            for (Message message : messages) {
                sb.append(childPrefix);
                sb.append("  [").append(message.getDisplayLabel());
                if (showTimestamp) {
                    sb.append(" @").append(formatTimestamp(message.getTimestampMillis()));
                }
                sb.append("] ");
                sb.append(message.getContent()).append("\n");
            }
        }
        
        // 递归处理子节点
        List<TaskNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean isLastChild = (i == children.size() - 1);
            appendTaskNode(sb, children.get(i), childPrefix, isLastChild);
        }
    }
    
    /**
     * 格式化持续时间
     * 
     * @param millis 毫秒数
     * @return 格式化的时间字符串
     */
    private String formatDuration(Long millis) {
        if (millis == null) { return "0ms"; }
        
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            double seconds = millis / 1000.0;
            return String.format("%.1fs", seconds);
        } else {
            double minutes = millis / 60000.0;
            return String.format("%.1fm", minutes);
        }
    }

    /**
     * 格式化时间戳（毫秒）
     */
    private String formatTimestamp(long millis) {
        // 使用ISO-8601简洁时间格式，便于可读与对比
        return DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(millis));
    }
    
    /**
     * 计算节点总数
     * 
     * @param node 根节点
     * @return 节点总数
     */
    private int countNodes(TaskNode node) {
        if (node == null) {
            return 0;
        }
        
        int count = 1;
        for (TaskNode child : node.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }
    
    /**
     * 简化样式的任务节点输出（使用缩进而不是树形连接线）
     * 
     * @param sb StringBuilder
     * @param node 任务节点
     * @param depth 当前深度
     */
    private void appendTaskNodeSimple(StringBuilder sb, TaskNode node, int depth) {
        // 生成缩进
        String indent = INDENT_UNIT.repeat(depth);
        
        // 输出节点信息
        sb.append(indent);
        sb.append(node.getTaskName());
        
        // 添加累计/自身时间和状态信息
        long selfMs = node.getSelfDurationMillis();
        long accMs = node.getAccumulatedDurationMillis();
        sb.append(" (")
          .append(formatDuration(accMs)).append(", self ")
          .append(formatDuration(selfMs)).append(", ")
          .append(node.getStatus()).append(")")
          .append("\n");
        
        // 输出消息（与任务使用相同缩进）
        List<Message> messages = node.getMessages();
        if (!messages.isEmpty()) {
            for (Message message : messages) {
                sb.append(indent);
                sb.append("    |- [").append(message.getDisplayLabel());
                if (showTimestamp) {
                    sb.append(" @").append(formatTimestamp(message.getTimestampMillis()));
                }
                sb.append("] ");
                sb.append(message.getContent()).append("\n");
            }
        }
        
        // 递归处理子节点
        List<TaskNode> children = node.getChildren();
        for (TaskNode child : children) {
            appendTaskNodeSimple(sb, child, depth + 1);
        }
    }
}
