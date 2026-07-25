package com.syy.taskflowinsight.exporter.json;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.SessionExportSnapshot;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 将 Session 的单份不可变快照编码为 canonical V2 JSON。
 *
 * <p>schema 由 {@link SessionExportSnapshot#toCanonicalV2()} 唯一拥有；本类只负责捕获边界和 JSON 编码。
 * 显式栈保证合法深树不会转化为 JVM 调用栈风险，同时避免引入 production JSON 依赖。
 */
public class JsonExporter {

    private static final String NULL_SESSION_JSON =
            "{\"error\":\"No session data available\"}";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /** 创建只发布 canonical V2 的 JSON exporter。 */
    public JsonExporter() {
    }

    /**
     * 导出 canonical V2 JSON。
     *
     * <p>捕获和投影在历史 String 渲染 fallback 之前完成，因此预算、锁和 projection 失败保持透明。
     *
     * @param session 要导出的 Session；null 保留历史 error JSON
     * @return canonical V2 JSON 或 post-projection 编码失败的 error JSON
     */
    public String export(Session session) {
        return export(session, SessionExportSnapshot::capture);
    }

    /** 包内 seam 只替换 capturer，用于证明每个 public 调用恰好捕获一次。 */
    String export(
            Session session, Function<Session, SessionExportSnapshot> capturer) {
        if (session == null) {
            return NULL_SESSION_JSON;
        }
        Map<String, Object> canonical = captureAndProject(session, capturer);
        StringWriter writer = new StringWriter();
        try {
            writeCanonical(canonical, writer);
            return writer.toString();
        } catch (IOException | RuntimeException failure) {
            return renderingFailure(failure);
        }
    }

    /**
     * 将 canonical V2 JSON 写入调用方 Writer。
     *
     * <p>capture/projection 在首次写入之前完成，因而这些失败不会留下部分 JSON；Writer 自身的 I/O
     * 失败保持原样传播。
     *
     * @param session 要导出的 Session；null 保留历史 error JSON
     * @param writer 调用方拥有的输出 Writer
     * @throws IOException Writer 写入或 flush 失败
     */
    public void export(Session session, Writer writer) throws IOException {
        export(session, writer, SessionExportSnapshot::capture);
    }

    /** 包内 seam 与 String 路径共享同一个捕获、投影和编码顺序。 */
    void export(
            Session session,
            Writer writer,
            Function<Session, SessionExportSnapshot> capturer) throws IOException {
        Objects.requireNonNull(writer, "writer");
        if (session == null) {
            writer.write(NULL_SESSION_JSON);
            writer.flush();
            return;
        }
        Map<String, Object> canonical = captureAndProject(session, capturer);
        writeCanonical(canonical, writer);
        writer.flush();
    }

    private static Map<String, Object> captureAndProject(
            Session session, Function<Session, SessionExportSnapshot> capturer) {
        Objects.requireNonNull(capturer, "capturer");
        SessionExportSnapshot snapshot = Objects.requireNonNull(
                capturer.apply(session), "capturer returned null snapshot");
        return Objects.requireNonNull(
                snapshot.toCanonicalV2(), "canonical projection returned null");
    }

    /** 仅编码 canonical projection 支持的 container/scalar 闭集。 */
    private static void writeCanonical(Object root, Writer writer) throws IOException {
        Deque<WriteFrame> pending = new ArrayDeque<>();
        pending.push(new ValueFrame(root));
        while (!pending.isEmpty()) {
            WriteFrame frame = pending.pop();
            if (frame instanceof ValueFrame value) {
                writeValue(value.value(), writer, pending);
            } else if (frame instanceof ObjectFrame object) {
                writeObjectFrame(object, writer, pending);
            } else {
                writeArrayFrame((ArrayFrame) frame, writer, pending);
            }
        }
    }

    private static void writeValue(
            Object value, Writer writer, Deque<WriteFrame> pending) throws IOException {
        if (value == null) {
            writer.write("null");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            writer.write('{');
            pending.push(new ObjectFrame(map.entrySet().iterator(), true));
            return;
        }
        if (value instanceof List<?> list) {
            writer.write('[');
            pending.push(new ArrayFrame(list.iterator(), true));
            return;
        }
        if (value instanceof String string) {
            writeString(writer, string);
            return;
        }
        if (value instanceof Character character) {
            writeString(writer, character.toString());
            return;
        }
        if (value instanceof Boolean bool) {
            writer.write(bool.toString());
            return;
        }
        if (isCanonicalNumber(value)) {
            writer.write(value.toString());
            return;
        }
        throw new IllegalArgumentException(
                "Unsupported canonical JSON value type: " + value.getClass().getName());
    }

    private static void writeObjectFrame(
            ObjectFrame frame, Writer writer, Deque<WriteFrame> pending) throws IOException {
        if (!frame.entries().hasNext()) {
            writer.write('}');
            return;
        }
        Map.Entry<?, ?> entry = frame.entries().next();
        if (!(entry.getKey() instanceof String key)) {
            throw new IllegalArgumentException("Canonical JSON object key must be a String");
        }
        if (!frame.first()) {
            writer.write(',');
        }
        writeString(writer, key);
        writer.write(':');
        pending.push(new ObjectFrame(frame.entries(), false));
        pending.push(new ValueFrame(entry.getValue()));
    }

    private static void writeArrayFrame(
            ArrayFrame frame, Writer writer, Deque<WriteFrame> pending) throws IOException {
        if (!frame.values().hasNext()) {
            writer.write(']');
            return;
        }
        if (!frame.first()) {
            writer.write(',');
        }
        Object value = frame.values().next();
        pending.push(new ArrayFrame(frame.values(), false));
        pending.push(new ValueFrame(value));
    }

    private static boolean isCanonicalNumber(Object value) {
        Class<?> type = value.getClass();
        if (type == Float.class) {
            return Float.isFinite((Float) value);
        }
        if (type == Double.class) {
            return Double.isFinite((Double) value);
        }
        return type == Byte.class || type == Short.class || type == Integer.class
                || type == Long.class || type == BigInteger.class || type == BigDecimal.class;
    }

    private static void writeString(Writer writer, String value) throws IOException {
        writer.write('"');
        writer.write(escape(value));
        writer.write('"');
    }

    private static String renderingFailure(Exception failure) {
        return "{\"error\":\"Export failed: " + escape(failure.getMessage()) + "\"}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = null;
        int copyStart = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            String replacement = replacement(current);
            if (replacement == null && Character.isHighSurrogate(current)
                    && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                index++;
                continue;
            }
            if (replacement == null && !Character.isSurrogate(current)) {
                continue;
            }
            if (escaped == null) {
                escaped = new StringBuilder(value.length() + 16);
            }
            escaped.append(value, copyStart, index);
            if (replacement != null) {
                escaped.append(replacement);
            } else {
                appendUnicodeEscape(escaped, current);
            }
            copyStart = index + 1;
        }
        return escaped == null ? value : escaped.append(value, copyStart, value.length()).toString();
    }

    private static String replacement(char value) {
        return switch (value) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> value < 0x20 || value >= 0x7f && value < 0xa0
                    || value == '\u2028' || value == '\u2029'
                    ? unicodeEscape(value) : null;
        };
    }

    private static String unicodeEscape(char value) {
        StringBuilder escaped = new StringBuilder(6);
        appendUnicodeEscape(escaped, value);
        return escaped.toString();
    }

    private static void appendUnicodeEscape(StringBuilder target, char value) {
        target.append("\\u")
                .append(HEX_DIGITS[value >>> 12 & 0xf])
                .append(HEX_DIGITS[value >>> 8 & 0xf])
                .append(HEX_DIGITS[value >>> 4 & 0xf])
                .append(HEX_DIGITS[value & 0xf]);
    }

    private sealed interface WriteFrame permits ArrayFrame, ObjectFrame, ValueFrame {
    }

    private record ArrayFrame(Iterator<?> values, boolean first) implements WriteFrame {
    }

    private record ObjectFrame(
            Iterator<? extends Map.Entry<?, ?>> entries,
            boolean first) implements WriteFrame {
    }

    private record ValueFrame(Object value) implements WriteFrame {
    }
}
