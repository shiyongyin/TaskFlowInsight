package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.projection.ProjectionNode;
import com.syy.taskflowinsight.tracking.projection.internal.ProjectionFrame;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/** projection JSON的package-private显式frame实现，避免形成第二public编码入口。 */
final class CanonicalProjectionJsonWriter {

    /** uppercase hex确保unpaired surrogate wire跨平台一致。 */
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private CanonicalProjectionJsonWriter() {
    }

    static void write(ProjectionNode root, Appendable output) throws IOException {
        Sink sink = new Sink(output);
        Deque<Frame> frames = new ArrayDeque<>();
        writeValue(root, 1, frames, sink);
        while (!frames.isEmpty()) {
            Frame frame = frames.peek();
            if (frame.complete()) {
                sink.append(frame.object ? '}' : ']');
                frames.pop();
                continue;
            }
            if (frame.index > 0) {
                sink.append(',');
            }
            ProjectionNode child;
            if (frame.object) {
                ProjectionNode.Member member = frame.node.members().get(frame.index++);
                writeString(member.name(), sink);
                sink.append(':');
                child = member.value();
            } else {
                child = frame.node.elements().get(frame.index++);
            }
            writeValue(child, frame.depth + 1, frames, sink);
        }
    }

    private static void writeValue(
            ProjectionNode node,
            int depth,
            Deque<Frame> frames,
            Sink sink) throws IOException {
        switch (node.kind()) {
            case OBJECT -> startContainer(node, depth, true, frames, sink);
            case ARRAY -> startContainer(node, depth, false, frames, sink);
            case STRING -> writeString((String) node.scalarValue(), sink);
            case BOOLEAN, NUMBER -> sink.append(node.scalarValue().toString());
            case NULL -> sink.append("null");
        }
    }

    private static void startContainer(
            ProjectionNode node,
            int depth,
            boolean object,
            Deque<Frame> frames,
            Sink sink) throws IOException {
        if (depth > ProjectionFrame.MAX_SCHEMA_DEPTH) {
            throw new IllegalArgumentException("projection schema depth exceeds 16");
        }
        sink.append(object ? '{' : '[');
        frames.push(new Frame(node, depth, object));
    }

    private static void writeString(String value, Sink sink) throws IOException {
        sink.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> sink.append("\\\"");
                case '\\' -> sink.append("\\\\");
                case '\b' -> sink.append("\\b");
                case '\f' -> sink.append("\\f");
                case '\n' -> sink.append("\\n");
                case '\r' -> sink.append("\\r");
                case '\t' -> sink.append("\\t");
                default -> {
                    if (current < 0x20 || isUnpairedSurrogate(value, index)) {
                        writeUnicodeEscape(current, sink);
                    } else {
                        sink.append(current);
                        if (Character.isHighSurrogate(current)) {
                            sink.append(value.charAt(++index));
                        }
                    }
                }
            }
        }
        sink.append('"');
    }

    private static boolean isUnpairedSurrogate(String value, int index) {
        char current = value.charAt(index);
        if (Character.isHighSurrogate(current)) {
            return index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1));
        }
        return Character.isLowSurrogate(current);
    }

    private static void writeUnicodeEscape(char value, Sink sink) throws IOException {
        sink.append("\\u");
        sink.append(HEX[(value >>> 12) & 0xF]);
        sink.append(HEX[(value >>> 8) & 0xF]);
        sink.append(HEX[(value >>> 4) & 0xF]);
        sink.append(HEX[value & 0xF]);
    }

    private static final class Frame {

        /** 当前container节点。 */
        private final ProjectionNode node;

        /** 当前节点从root计数的深度。 */
        private final int depth;

        /** true表示对象，false表示数组。 */
        private final boolean object;

        /** 下一个待写member或element位置。 */
        private int index;

        private Frame(ProjectionNode node, int depth, boolean object) {
            this.node = node;
            this.depth = depth;
            this.object = object;
        }

        private boolean complete() {
            return object ? index >= node.members().size() : index >= node.elements().size();
        }
    }

    private static final class Sink {

        /** 调用方输出，不由writer关闭。 */
        private final Appendable output;

        /** 已写UTF-16 code unit数，用long避免累加溢出。 */
        private long chars;

        private Sink(Appendable output) {
            this.output = output;
        }

        private void append(char value) throws IOException {
            reserve(1);
            output.append(value);
        }

        private void append(CharSequence value) throws IOException {
            reserve(value.length());
            output.append(value);
        }

        private void reserve(int added) {
            chars = Math.addExact(chars, added);
            if (chars > ProjectionFrame.MAX_PROJECTION_TEXT_CHARS) {
                throw new IllegalArgumentException("projection JSON exceeds hard ceiling");
            }
        }
    }
}
