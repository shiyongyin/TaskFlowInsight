package com.syy.taskflowinsight.exporter.text;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;
import com.syy.taskflowinsight.model.SessionExportSnapshot.MessageSnapshot;
import com.syy.taskflowinsight.model.SessionExportSnapshot.TaskSnapshot;

import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.SIMPLE;
import static com.syy.taskflowinsight.exporter.text.ConsoleExportOptions.ConsoleStyle.TREE;

/**
 * 将 Session 的单份不可变快照投影为供人阅读的控制台诊断文本。
 *
 * <p>本类不定义机器 schema，也不拥有 mutable task tree 遍历；所有非空 Session 的 public 入口先完成
 * 一次 {@link SessionExportSnapshot} 捕获和整体字符串渲染，再执行输出，从而避免同次诊断混入多个时点或
 * 在捕获失败时留下部分字节。
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

    /*
     * 本类必须保持无状态：导出选项一律走参数传递。
     * 历史版本曾把 showTimestamp 存为可变实例字段，共享实例被多线程并发调用时
     * 互相污染输出。
     */

    /**
     * 创建无状态控制台导出器；每次导出的样式均由方法参数决定。
     */
    public ConsoleExporter() {
    }

    /**
     * 导出会话为 TREE 诊断文本，不显示消息时间戳。
     *
     * <p>session 为 null 时返回空字符串。
     *
     * @param session 要导出的会话
     * @return 格式化的字符串输出
     */
    public String export(Session session) {
        return export(session, new ConsoleExportOptions(TREE, false));
    }

    /**
     * 导出会话为 TREE 诊断文本。
     *
     * <p>该入口固定使用 emoji 树状风格，{@code showTimestamp} 只控制消息时间戳。
     *
     * @param session 要导出的会话
     * @param showTimestamp 是否显示消息时间戳
     * @return 格式化的字符串输出
     */
    public String export(Session session, boolean showTimestamp) {
        return export(session, new ConsoleExportOptions(TREE, showTimestamp));
    }

    /**
     * 按显式选项导出会话诊断文本。
     *
     * @param session 要导出的会话
     * @param options 样式与时间戳选项，不可为 null
     * @return 完整诊断文本；session 为 null 时返回空字符串
     * @throws NullPointerException options 为 null 时抛出
     */
    public String export(Session session, ConsoleExportOptions options) {
        return export(session, options, SessionExportSnapshot::capture);
    }

    /**
     * 通过唯一包内 capturer seam 固定一次捕获边界，避免 style 分支各自捕获或暴露 public hook。
     */
    String export(
            Session session,
            ConsoleExportOptions options,
            Function<Session, SessionExportSnapshot> capturer) {
        Objects.requireNonNull(options, "options");
        if (session == null) {
            return "";
        }
        Objects.requireNonNull(capturer, "capturer");
        SessionExportSnapshot snapshot = Objects.requireNonNull(
                capturer.apply(session), "capturer returned null snapshot");
        return switch (options.style()) {
            case TREE -> renderTree(snapshot, options.showTimestamp());
            case SIMPLE -> renderSimple(snapshot, options.showTimestamp());
        };
    }

    private String renderTree(SessionExportSnapshot snapshot, boolean showTimestamp) {
        StringBuilder out = new StringBuilder(INITIAL_CAPACITY);
        appendSnapshotHeader(out, snapshot);
        appendSnapshotSessionRoot(out, snapshot, showTimestamp);

        TaskSnapshot root = snapshot.root();
        ArrayDeque<TreeFrame> pending = new ArrayDeque<>();
        pushTreeChildren(pending, root, "");
        while (!pending.isEmpty()) {
            TreeFrame frame = pending.pop();
            if (frame.truncation()) {
                out.append(frame.prefix()).append(LAST_BRANCH)
                        .append("... (children truncated at depth ")
                        .append(frame.task().depth()).append(")\n");
                continue;
            }
            appendSnapshotTaskLine(out, frame, showTimestamp);
            String childPrefix = frame.prefix() + (frame.last() ? SPACE : VERTICAL);
            pushTreeChildren(pending, frame.task(), childPrefix);
        }

        out.append(SEPARATOR_LINE).append("\n");
        return out.toString();
    }

    private void appendSnapshotHeader(StringBuilder out, SessionExportSnapshot snapshot) {
        out.append(SEPARATOR_LINE).append("\n");
        out.append("TaskFlow Insight Report\n");
        out.append(SEPARATOR_LINE).append("\n");
        out.append("Session: ").append(snapshot.sessionId()).append("\n");
        out.append("Thread:  ").append(snapshot.threadId()).append(" (")
                .append(snapshot.threadName()).append(")\n");
        out.append("Status:  ").append(snapshot.status()).append("\n");
        if (snapshot.durationMillis() != null) {
            out.append("Duration: ").append(formatDuration(snapshot.durationMillis())).append("\n");
        }
        out.append("\n");
    }

    private void appendSnapshotSessionRoot(
            StringBuilder out, SessionExportSnapshot snapshot, boolean showTimestamp) {
        TaskSnapshot root = snapshot.root();
        out.append(ICON_SESSION).append(root.taskName())
                .append(" [").append(snapshot.status()).append("]");
        if (snapshot.durationMillis() != null) {
            out.append(" (").append(formatDuration(snapshot.durationMillis())).append(")");
        }
        out.append("\n");

        List<MessageSnapshot> messages = root.messages();
        boolean hasFollowingLine = !root.children().isEmpty() || root.childrenTruncated();
        for (int index = 0; index < messages.size(); index++) {
            boolean last = index == messages.size() - 1 && !hasFollowingLine;
            appendSnapshotTreeMessage(out, messages.get(index), "", last, showTimestamp);
        }
    }

    private void appendSnapshotTaskLine(
            StringBuilder out, TreeFrame frame, boolean showTimestamp) {
        TaskSnapshot task = frame.task();
        out.append(frame.prefix()).append(frame.last() ? LAST_BRANCH : BRANCH)
                .append(ICON_TASK).append(task.taskName())
                .append(" [").append(task.status()).append("]")
                .append(" (").append(formatDuration(task.accumulatedDurationMillis())).append(")\n");

        String childPrefix = frame.prefix() + (frame.last() ? SPACE : VERTICAL);
        boolean hasFollowingLine = !task.children().isEmpty() || task.childrenTruncated();
        List<MessageSnapshot> messages = task.messages();
        for (int index = 0; index < messages.size(); index++) {
            boolean last = index == messages.size() - 1 && !hasFollowingLine;
            appendSnapshotTreeMessage(
                    out, messages.get(index), childPrefix, last, showTimestamp);
        }
    }

    private void appendSnapshotTreeMessage(
            StringBuilder out,
            MessageSnapshot message,
            String prefix,
            boolean last,
            boolean showTimestamp) {
        out.append(prefix).append(last ? LAST_BRANCH : BRANCH).append(ICON_MESSAGE)
                .append("[").append(message.displayLabel());
        appendTimestamp(out, message, showTimestamp);
        out.append("] ").append(message.content()).append("\n");
    }

    private void pushTreeChildren(
            ArrayDeque<TreeFrame> pending, TaskSnapshot parent, String childPrefix) {
        // 截断帧先入栈，确保 LIFO 顺序下它在所有可见 children 之后输出。
        if (parent.childrenTruncated()) {
            pending.push(new TreeFrame(parent, childPrefix, true, true));
        }
        List<TaskSnapshot> children = parent.children();
        for (int index = children.size() - 1; index >= 0; index--) {
            boolean last = index == children.size() - 1 && !parent.childrenTruncated();
            pending.push(new TreeFrame(children.get(index), childPrefix, last, false));
        }
    }

    private String renderSimple(SessionExportSnapshot snapshot, boolean showTimestamp) {
        StringBuilder out = new StringBuilder(INITIAL_CAPACITY);
        appendSnapshotHeader(out, snapshot);

        ArrayDeque<SimpleFrame> pending = new ArrayDeque<>();
        pending.push(new SimpleFrame(snapshot.root(), false));
        while (!pending.isEmpty()) {
            SimpleFrame frame = pending.pop();
            TaskSnapshot task = frame.task();
            if (frame.truncation()) {
                out.append(INDENT_UNIT.repeat(task.depth() + 1))
                        .append("... (children truncated at depth ")
                        .append(task.depth()).append(")\n");
                continue;
            }
            appendSnapshotSimpleTask(out, task, showTimestamp);
            pushSimpleChildren(pending, task);
        }

        out.append(SEPARATOR_LINE).append("\n");
        return out.toString();
    }

    private void appendSnapshotSimpleTask(
            StringBuilder out, TaskSnapshot task, boolean showTimestamp) {
        String indent = INDENT_UNIT.repeat(task.depth());
        out.append(indent).append(task.taskName()).append(" (")
                .append(formatDuration(task.accumulatedDurationMillis())).append(", self ")
                .append(formatDuration(task.selfDurationMillis())).append(", ")
                .append(task.status()).append(")\n");

        for (MessageSnapshot message : task.messages()) {
            out.append(indent).append("    |- [").append(message.displayLabel());
            appendTimestamp(out, message, showTimestamp);
            out.append("] ").append(message.content()).append("\n");
        }
    }

    private void appendTimestamp(
            StringBuilder out, MessageSnapshot message, boolean showTimestamp) {
        if (showTimestamp) {
            out.append(" @").append(formatTimestamp(message.timestampMillis()));
        }
    }

    private void pushSimpleChildren(ArrayDeque<SimpleFrame> pending, TaskSnapshot parent) {
        // 与 tree renderer 保持同一截断顺序，避免 marker 出现在可见 child 之前。
        if (parent.childrenTruncated()) {
            pending.push(new SimpleFrame(parent, true));
        }
        List<TaskSnapshot> children = parent.children();
        for (int index = children.size() - 1; index >= 0; index--) {
            pending.push(new SimpleFrame(children.get(index), false));
        }
    }

    private record TreeFrame(TaskSnapshot task, String prefix, boolean last, boolean truncation) {
    }

    private record SimpleFrame(TaskSnapshot task, boolean truncation) {
    }

    /**
     * 输出会话到标准输出，固定使用 TREE 且不显示消息时间戳。
     *
     * @param session 要输出的会话
     */
    public void print(Session session) {
        String output = export(session);
        System.out.print(output);
    }

    /**
     * 导出会话为简化缩进格式的字符串（无 emoji、无树形连接线）。
     *
     * @param session       要导出的会话
     * @param showTimestamp 是否显示消息时间戳
     * @return 简化格式的字符串输出
     */
    public String exportSimple(Session session, boolean showTimestamp) {
        return export(session, new ConsoleExportOptions(SIMPLE, showTimestamp));
    }

    /**
     * 输出会话到标准输出，固定使用 SIMPLE 且不显示消息时间戳。
     *
     * @param session 要输出的会话
     */
    public void printSimple(Session session) {
        String output = exportSimple(session, false);
        System.out.print(output);
    }

    /**
     * 输出会话到指定流，固定使用 TREE 且不显示消息时间戳。
     *
     * <p>即使 {@code out} 为 null，非空 Session 仍完成一次捕获与渲染；这样所有 public Session 路径
     * 共享相同的捕获失败语义，只在最终写入阶段丢弃文本。
     *
     * @param session 要输出的会话
     * @param out 输出流
     */
    public void print(Session session, PrintStream out) {
        String output = export(session);
        if (out != null) {
            out.print(output);
        }
    }

    /**
     * 按显式选项输出会话到指定流。
     *
     * <p>先捕获并渲染完整文本，再判断输出流是否存在；因此 null 输出流不会成为跳过捕获失败的隐藏开关。
     *
     * @param session 要输出的会话
     * @param out 输出流，可为 null
     * @param options 样式与时间戳选项，不可为 null
     * @throws NullPointerException options 为 null 时抛出
     */
    public void print(Session session, PrintStream out, ConsoleExportOptions options) {
        String output = export(session, options);
        if (out != null) {
            out.print(output);
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
        return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(millis));
    }

}
