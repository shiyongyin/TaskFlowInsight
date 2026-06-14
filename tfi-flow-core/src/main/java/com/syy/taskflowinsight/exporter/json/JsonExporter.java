package com.syy.taskflowinsight.exporter.json;

import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.exporter.TaskDurationCache;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * JSON导出器
 * 将会话信息导出为标准JSON格式，不依赖第三方库
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-08
 */
public class JsonExporter {

    private static final int DEFAULT_SEQUENCE = 0;
    
    /**
     * 导出模式
     */
    public enum ExportMode {
        COMPAT,   // 兼容模式：毫秒时间戳，简洁字段
        ENHANCED  // 增强模式：纳秒精度，包含统计信息
    }
    
    private final ExportMode mode;
    
    /**
     * 默认构造函数，使用COMPAT模式
     */
    public JsonExporter() {
        this(ExportMode.COMPAT);
    }
    
    /**
     * 指定模式的构造函数
     * 
     * @param mode 导出模式
     */
    public JsonExporter(ExportMode mode) {
        this.mode = mode != null ? mode : ExportMode.COMPAT;
    }
    
    /**
     * 导出会话为JSON字符串
     * 
     * @param session 要导出的会话
     * @return JSON字符串
     */
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
    
    /**
     * 导出会话到Writer（流式输出）
     * 
     * @param session 要导出的会话
     * @param writer 输出流
     * @throws IOException 如果写入失败
     */
    public void export(Session session, Writer writer) throws IOException {
        if (session == null) {
            writer.write("{\"error\":\"No session data available\"}");
            writer.flush();
            return;
        }
        
        writer.write('{');
        
        // Session基本信息
        writeField(writer, "sessionId", session.getSessionId(), false);
        // 使用Session记录的线程信息，threadId保持为数字（兼容示例）
        writeField(writer, "threadId", toJsonThreadId(session.getThreadId()), true);
        writeField(writer, "threadName", session.getThreadName(), true);
        writeField(writer, "status", session.getStatus(), true);
        
        // 时间信息
        if (mode == ExportMode.COMPAT) {
            writeField(writer, "createdAt", session.getCreatedMillis(), true);
            writeField(writer, "endedAt", session.getCompletedMillis(), true);
            writeField(writer, "durationMs", session.getDurationMillis(), true);
        } else {
            // 增强模式：使用纳秒精度
            writeField(writer, "createdAtNanos", session.getCreatedNanos(), true);
            writeField(writer, "endedAtNanos", session.getCompletedNanos(), true);
            writeField(writer, "durationNanos", session.getDurationNanos(), true);
        }
        
        // 根任务节点
        TaskNode root = session.getRootTask();
        TaskDurationCache durationCache = TaskDurationCache.from(root);
        if (root != null) {
            writer.write(",\"root\":");
            writeTaskNode(root, writer, durationCache);
            
            // 增强模式：添加统计信息
            if (mode == ExportMode.ENHANCED) {
                writer.write(",\"statistics\":{");
                int[] stats = calculateStats(root);
                writeField(writer, "totalTasks", stats[0], false);
                writeField(writer, "maxDepth", stats[1], true);
                writeField(writer, "totalMessages", stats[2], true);
                writer.write('}');
            }
        } else {
            writer.write(",\"root\":null");
        }
        
        writer.write('}');
        writer.flush();
    }
    
    /**
     * 写入TaskNode为JSON
     */
    private void writeTaskNode(TaskNode node, Writer writer, TaskDurationCache durationCache) throws IOException {
        if (node == null) {
            writer.write("null");
            return;
        }
        
        writer.write('{');
        
        // 基本信息
        writeField(writer, "nodeId", node.getNodeId(), false);
        writeField(writer, "name", node.getTaskName(), true);
        writeField(writer, "depth", node.getDepth(), true);
        writeField(writer, "sequence", DEFAULT_SEQUENCE, true);  // 兼容字段：TaskNode 暂不维护同级序号
        writeField(writer, "taskPath", node.getTaskPath(), true);
        
        // 时间信息
        if (mode == ExportMode.COMPAT) {
            writeField(writer, "startMillis", node.getCreatedMillis(), true);
            writeField(writer, "endMillis", node.getCompletedMillis(), true);
            writeField(writer, "durationMs", node.getDurationMillis(), true); // 兼容字段（自身）
            writeField(writer, "selfDurationMs", node.getSelfDurationMillis(), true);
            writeField(writer, "accDurationMs", durationCache.getAccumulatedDurationMillis(node), true);
        } else {
            writeField(writer, "startNanos", node.getCreatedNanos(), true);
            writeField(writer, "endNanos", node.getCompletedNanos(), true);
            writeField(writer, "durationNanos", node.getDurationNanos(), true);
            // 额外提供毫秒口径的自/累计，便于消费
            writeField(writer, "selfDurationMs", node.getSelfDurationMillis(), true);
            writeField(writer, "accDurationMs", durationCache.getAccumulatedDurationMillis(node), true);
        }
        
        // 状态信息
        writeField(writer, "status", node.getStatus(), true);
        writeField(writer, "isActive", node.getStatus() == TaskStatus.RUNNING, true);  // 根据状态判断是否活跃
        
        // 消息列表
        writer.write(",\"messages\":");
        writeMessages(node.getMessages(), writer);

        Map<String, Object> attributes = node.getAttributes();
        if (!attributes.isEmpty()) {
            writer.write(",\"attributes\":");
            writeAttributes(attributes, writer);
        }

        List<String> tags = node.getTags();
        if (!tags.isEmpty()) {
            writer.write(",\"tags\":");
            writeTags(tags, writer);
        }
        
        // 子节点列表
        writer.write(",\"children\":");
        writeChildren(node.getChildren(), writer, durationCache);
        
        writer.write('}');
    }
    
    /**
     * 写入消息列表
     */
    private void writeMessages(List<Message> messages, Writer writer) throws IOException {
        writer.write('[');
        if (messages != null && !messages.isEmpty()) {
            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                writeMessage(messages.get(i), writer);
            }
        }
        writer.write(']');
    }
    
    /**
     * 写入单个消息
     */
    private void writeMessage(Message message, Writer writer) throws IOException {
        writer.write('{');
        boolean first = true;
        if (message.getType() != null) {
            writeField(writer, "type", message.getType(), false);
            first = false;
        }
        if (message.getCustomLabel() != null) {
            writeField(writer, "label", message.getCustomLabel(), !first);
            first = false;
        }
        writeField(writer, "content", message.getContent(), !first);
        writeField(writer, "timestamp", message.getTimestampMillis(), true);
        writer.write('}');
    }

    /**
     * 写入任务属性。
     */
    private void writeAttributes(Map<String, Object> attributes, Writer writer) throws IOException {
        writer.write('{');
        int index = 0;
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            writeField(writer, entry.getKey(), entry.getValue(), index > 0);
            index++;
        }
        writer.write('}');
    }

    /**
     * 写入任务标签。
     */
    private void writeTags(List<String> tags, Writer writer) throws IOException {
        writer.write('[');
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writeValue(writer, tags.get(i));
        }
        writer.write(']');
    }
    
    /**
     * 写入子节点列表
     */
    private void writeChildren(List<TaskNode> children, Writer writer, TaskDurationCache durationCache)
            throws IOException {
        writer.write('[');
        if (children != null && !children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                writeTaskNode(children.get(i), writer, durationCache);
            }
        }
        writer.write(']');
    }
    
    /**
     * 写入字段
     */
    private void writeField(Writer writer, String name, Object value, boolean needComma) throws IOException {
        if (needComma) {
            writer.write(',');
        }
        writer.write('"');
        writer.write(name);
        writer.write("\":");
        writeValue(writer, value);
    }
    
    /**
     * 写入值
     */
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

    /**
     * 将线程 ID 转为兼容 JSON 值：纯数字保留为 number，否则按字符串输出。
     */
    private Object toJsonThreadId(String threadId) {
        if (threadId == null || threadId.isEmpty()) {
            return threadId;
        }
        int start = threadId.charAt(0) == '-' ? 1 : 0;
        if (start == threadId.length()) {
            return threadId;
        }
        for (int i = start; i < threadId.length(); i++) {
            if (!Character.isDigit(threadId.charAt(i))) {
                return threadId;
            }
        }
        return Long.parseLong(threadId);
    }
    
    /**
     * JSON字符串转义
     * 
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    private String escape(String str) {
        if (str == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder(str.length() + 16);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    // 处理不可打印字符和Unicode字符
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else if (c >= 0x80 && c < 0xA0) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    /**
     * 计算统计信息
     * 
     * @param root 根节点
     * @return [totalTasks, maxDepth, totalMessages]
     */
    private int[] calculateStats(TaskNode root) {
        int[] stats = new int[3];
        if (root != null) {
            calculateStatsRecursive(root, 0, stats);
        }
        return stats;
    }
    
    private void calculateStatsRecursive(TaskNode node, int depth, int[] stats) {
        if (node == null) {
            return;
        }
        
        stats[0]++; // 累计总任务数
        stats[1] = Math.max(stats[1], depth); // 更新最大深度
        stats[2] += node.getMessages().size(); // 累计总消息数
        
        for (TaskNode child : node.getChildren()) {
            calculateStatsRecursive(child, depth + 1, stats);
        }
    }
}
