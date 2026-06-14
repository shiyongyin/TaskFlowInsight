package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.exporter.TaskDurationCache;
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

    // ASCII 树形绘制字符（emoji 树状风格）
    private static final String SEPARATOR_LINE = "=".repeat(50);
    private static final String BRANCH = "├── ";
    private static final String LAST_BRANCH = "└── ";
    private static final String VERTICAL = "│   ";
    private static final String SPACE = "    ";

    // emoji 图标
    private static final String ICON_SESSION = "\uD83D\uDCCB ";
    private static final String ICON_TASK = "\uD83D\uDD27 ";
    private static final String ICON_MESSAGE = "\uD83D\uDCAC ";

    // 简化缩进字符（可选简化模式）
    private static final String INDENT_UNIT = "    ";
    private static final int INITIAL_CAPACITY = 1024;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault());

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
        
        TaskNode root = session.getRootTask();
        StringBuilder sb = new StringBuilder(INITIAL_CAPACITY);
        
        // 构建输出
        appendHeader(sb, session);
        
        if (root != null) {
            TaskDurationCache durationCache = TaskDurationCache.from(root);

            // 默认使用 emoji 树状风格输出
            appendSessionRoot(sb, session);
            List<TaskNode> rootChildren = root.getChildren();
            for (int i = 0; i < rootChildren.size(); i++) {
                boolean isLast = (i == rootChildren.size() - 1);
                appendTaskNode(sb, rootChildren.get(i), "", isLast, durationCache);
            }
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
     * 导出会话为简化缩进格式的字符串（无 emoji、无树形连接线）。
     *
     * @param session       要导出的会话
     * @param showTimestamp 是否显示时间戳
     * @return 简化格式的字符串输出
     */
    public String exportSimple(Session session, boolean showTimestamp) {
        if (session == null) {
            return "";
        }
        this.showTimestamp = showTimestamp;

        TaskNode root = session.getRootTask();

        StringBuilder sb = new StringBuilder(INITIAL_CAPACITY);
        appendHeader(sb, session);

        if (root != null) {
            appendTaskNodeSimple(sb, root, 0, TaskDurationCache.from(root));
        } else {
            sb.append("No tasks executed\n");
        }

        sb.append(SEPARATOR_LINE).append("\n");
        return sb.toString();
    }

    /**
     * 输出会话到标准输出（简化格式，无时间戳）
     *
     * @param session 要输出的会话
     */
    public void printSimple(Session session) {
        System.out.print(exportSimple(session, false));
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
        return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(millis));
    }
    
    /**
     * 输出会话根节点行（emoji 树状风格）。
     *
     * <p>格式示例：{@code 📋 订单处理 [COMPLETED] (1000ms)}
     *
     * @param sb      StringBuilder
     * @param session 会话对象
     */
    private void appendSessionRoot(StringBuilder sb, Session session) {
        sb.append(ICON_SESSION);
        sb.append(session.getRootTask().getTaskName());
        sb.append(" [").append(session.getStatus()).append("]");
        if (session.getDurationMillis() != null) {
            sb.append(" (").append(formatDuration(session.getDurationMillis())).append(")");
        }
        sb.append("\n");

        // 输出根任务自身的消息
        List<Message> rootMessages = session.getRootTask().getMessages();
        boolean hasChildren = !session.getRootTask().getChildren().isEmpty();
        for (int i = 0; i < rootMessages.size(); i++) {
            Message msg = rootMessages.get(i);
            boolean isLastMsg = (i == rootMessages.size() - 1) && !hasChildren;
            sb.append(isLastMsg ? LAST_BRANCH : BRANCH);
            sb.append(ICON_MESSAGE);
            sb.append("[").append(msg.getDisplayLabel()).append("] ");
            sb.append(msg.getContent()).append("\n");
        }
    }

    /**
     * emoji 树状风格的任务节点输出（带 ├──/└── 连接线和 emoji 图标）。
     *
     * <p>格式示例：
     * <pre>
     * ├── 🔧 验证库存 [COMPLETED] (200ms)
     * │   └── 💬 [INFO] 库存充足
     * </pre>
     *
     * @param sb     StringBuilder
     * @param node   任务节点
     * @param prefix 当前行的前缀字符串（由上层递归传入）
     * @param isLast 是否为同级最后一个节点
     */
    private void appendTaskNode(StringBuilder sb, TaskNode node,
                                String prefix, boolean isLast, TaskDurationCache durationCache) {
        // 绘制连接线 + emoji + 任务名 + 状态 + 耗时
        sb.append(prefix);
        sb.append(isLast ? LAST_BRANCH : BRANCH);
        sb.append(ICON_TASK);
        sb.append(node.getTaskName());
        sb.append(" [").append(node.getStatus()).append("]");
        long accMs = durationCache.getAccumulatedDurationMillis(node);
        sb.append(" (").append(formatDuration(accMs)).append(")");
        sb.append("\n");

        // 计算子节点使用的 prefix
        String childPrefix = prefix + (isLast ? SPACE : VERTICAL);

        // 输出消息
        List<Message> messages = node.getMessages();
        List<TaskNode> children = node.getChildren();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            boolean isLastItem = (i == messages.size() - 1) && children.isEmpty();
            sb.append(childPrefix);
            sb.append(isLastItem ? LAST_BRANCH : BRANCH);
            sb.append(ICON_MESSAGE);
            sb.append("[").append(msg.getDisplayLabel()).append("] ");
            sb.append(msg.getContent()).append("\n");
        }

        // 递归处理子节点
        for (int i = 0; i < children.size(); i++) {
            boolean childIsLast = (i == children.size() - 1);
            appendTaskNode(sb, children.get(i), childPrefix, childIsLast, durationCache);
        }
    }

    /**
     * 简化样式的任务节点输出（使用缩进而不是树形连接线）
     * 
     * @param sb StringBuilder
     * @param node 任务节点
     * @param depth 当前深度
     */
    private void appendTaskNodeSimple(StringBuilder sb, TaskNode node, int depth, TaskDurationCache durationCache) {
        // 生成缩进
        String indent = INDENT_UNIT.repeat(depth);
        
        // 输出节点信息
        sb.append(indent);
        sb.append(node.getTaskName());
        
        // 添加累计/自身时间和状态信息
        long selfMs = node.getSelfDurationMillis();
        long accMs = durationCache.getAccumulatedDurationMillis(node);
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
            appendTaskNodeSimple(sb, child, depth + 1, durationCache);
        }
    }
}
