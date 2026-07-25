package com.syy.tfi.kernel;

import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.Record;
import com.syy.tfi.kernel.model.StageNode;
import java.util.List;

/** 固定 {@code tfi-flow/1} 字段顺序的零反射 JSON writer。 */
final class SessionJsonWriter {
    private SessionJsonWriter() {
    }

    static String write(FlowSession session) {
        StringBuilder output = new StringBuilder();
        output.append("{\"schema\":\"tfi-flow/1\",\"sessionId\":");
        DataCodec.appendString(output, session.sessionId());
        output.append(",\"parentSessionId\":");
        appendNullableString(output, session.parentSessionId());
        output.append(",\"name\":");
        DataCodec.appendString(output, session.name());
        output.append(",\"status\":");
        DataCodec.appendString(output, session.status().name());
        output.append(",\"startMs\":").append(session.startMs());
        output.append(",\"durMs\":").append(session.durMs());
        output.append(",\"truncated\":").append(session.truncated());
        output.append(",\"incompleteReasons\":");
        appendStrings(output, session.incompleteReasons());
        output.append(",\"attrs\":");
        DataCodec.appendValue(output, session.attrs());
        output.append(",\"root\":");
        appendStage(output, session.root());
        return output.append('}').toString();
    }

    private static void appendStage(StringBuilder output, StageNode stage) {
        output.append("{\"name\":");
        DataCodec.appendString(output, stage.name());
        output.append(",\"status\":");
        DataCodec.appendString(output, stage.status().name());
        output.append(",\"startMs\":").append(stage.startMs());
        output.append(",\"durMs\":").append(stage.durMs());
        output.append(",\"attrs\":");
        DataCodec.appendValue(output, stage.attrs());
        output.append(",\"records\":[");
        for (int index = 0; index < stage.records().size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            appendRecord(output, stage.records().get(index));
        }
        output.append("],\"children\":[");
        for (int index = 0; index < stage.children().size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            appendStage(output, stage.children().get(index));
        }
        output.append("]}");
    }

    private static void appendRecord(StringBuilder output, Record record) {
        output.append("{\"type\":");
        DataCodec.appendString(output, record.type().name());
        output.append(",\"code\":");
        DataCodec.appendString(output, record.code());
        output.append(",\"text\":");
        appendNullableString(output, record.text());
        output.append(",\"data\":");
        DataCodec.appendValue(output, record.data());
        output.append(",\"atMs\":").append(record.atMs()).append('}');
    }

    private static void appendStrings(StringBuilder output, List<String> values) {
        output.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            DataCodec.appendString(output, values.get(index));
        }
        output.append(']');
    }

    private static void appendNullableString(StringBuilder output, String value) {
        if (value == null) {
            output.append("null");
        } else {
            DataCodec.appendString(output, value);
        }
    }
}

/** Console 只服务人读调试，不形成机器兼容合同。 */
final class ConsoleRenderer {
    private ConsoleRenderer() {
    }

    static String render(FlowSession session) {
        StringBuilder output = new StringBuilder();
        output.append("\uD83D\uDCCB ").append(session.name()).append(" [").append(session.status()).append("] ")
                .append(session.durMs()).append("ms\n");
        appendContents(output, session.root(), "");
        return output.toString();
    }

    private static void appendContents(StringBuilder output, StageNode stage, String prefix) {
        int remaining = stage.attrs().size() + stage.records().size() + stage.children().size();
        for (String key : stage.attrs().keySet()) {
            appendTreePrefix(output, prefix, --remaining == 0)
                    .append("@ ").append(key).append('\n');
        }
        for (Record record : stage.records()) {
            appendTreePrefix(output, prefix, --remaining == 0)
                    .append("\uD83D\uDCAC ").append(record.type()).append('/').append(record.code());
            if (record.text() != null) {
                output.append(": ").append(record.text());
            }
            output.append('\n');
        }
        for (StageNode child : stage.children()) {
            boolean last = --remaining == 0;
            appendTreePrefix(output, prefix, last).append("\uD83D\uDD27 ").append(child.name()).append(" [")
                    .append(child.status()).append("] ").append(child.durMs()).append("ms\n");
            appendContents(output, child, prefix.concat(last ? "    " : "\u2502   "));
        }
    }

    private static StringBuilder appendTreePrefix(StringBuilder output, String prefix, boolean last) {
        return output.append(prefix).append(last ? "\u2514\u2500\u2500 " : "\u251C\u2500\u2500 ");
    }
}
