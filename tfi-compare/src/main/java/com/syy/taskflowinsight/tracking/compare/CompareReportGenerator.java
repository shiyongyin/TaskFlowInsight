package com.syy.taskflowinsight.tracking.compare;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 比较报告生成器
 *
 * <p>从 {@link CompareService} 拆分而来，负责将 {@link FieldChange} 列表
 * 转换为多种格式的报告（Text / Markdown / JSON / HTML）以及补丁（JSON Patch / Merge Patch）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>无状态 — 所有方法均为纯函数，线程安全</li>
 *   <li>单一职责 — 仅负责格式化输出，不涉及比较逻辑</li>
 * </ul>
 *
 * @author Expert Panel - Senior Developer
 * @since 3.0.0
 */
public final class CompareReportGenerator {

    private CompareReportGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ──────────────────────────────────────────────────────────────
    //  报告生成
    // ──────────────────────────────────────────────────────────────

    /**
     * 根据选项生成报告。
     *
     * @param changes 变更列表
     * @param options 比较选项（决定输出格式）
     * @return 格式化的报告字符串
     */
    public static String generateReport(List<FieldChange> changes, CompareOptions options) {
        ReportFormat format = options.getReportFormat();

        if (changes.isEmpty()) {
            return "No changes detected.";
        }

        return switch (format) {
            case MARKDOWN -> generateMarkdownReport(changes);
            case JSON -> generateJsonReport(changes);
            case HTML -> generateHtmlReport(changes);
            default -> generateTextReport(changes);
        };
    }

    /**
     * 生成纯文本报告。
     *
     * @param changes 变更列表
     * @return 文本格式报告
     */
    public static String generateTextReport(List<FieldChange> changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Change Report:\n");
        sb.append("-".repeat(50)).append("\n");

        for (FieldChange change : changes) {
            sb.append(String.format("%-20s: %s -> %s (%s)\n",
                    change.getFieldName(),
                    change.getOldValue(),
                    change.getNewValue(),
                    change.getChangeType()));
        }

        sb.append("-".repeat(50)).append("\n");
        sb.append("Total changes: ").append(changes.size()).append("\n");

        return sb.toString();
    }

    /**
     * 生成 Markdown 格式报告。
     *
     * @param changes 变更列表
     * @return Markdown 格式报告
     */
    public static String generateMarkdownReport(List<FieldChange> changes) {
        StringBuilder md = new StringBuilder();
        md.append("# Change Report\n\n");
        md.append("| Field | Old Value | New Value | Type |\n");
        md.append("|-------|-----------|-----------|------|\n");

        for (FieldChange change : changes) {
            md.append("| ").append(change.getFieldName())
                    .append(" | ").append(change.getOldValue())
                    .append(" | ").append(change.getNewValue())
                    .append(" | ").append(change.getChangeType())
                    .append(" |\n");
        }

        md.append("\n**Total changes:** ").append(changes.size()).append("\n");

        return md.toString();
    }

    /**
     * 生成 JSON 格式报告。
     *
     * @param changes 变更列表
     * @return JSON 格式报告
     */
    public static String generateJsonReport(List<FieldChange> changes) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"changes\": [\n");

        for (int i = 0; i < changes.size(); i++) {
            FieldChange change = changes.get(i);
            json.append("    {\n");
            json.append("      \"field\": \"").append(change.getFieldName()).append("\",\n");
            json.append("      \"old\": ").append(toJsonValue(change.getOldValue())).append(",\n");
            json.append("      \"new\": ").append(toJsonValue(change.getNewValue())).append(",\n");
            json.append("      \"type\": \"").append(change.getChangeType()).append("\"\n");
            json.append("    }");
            if (i < changes.size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("  ],\n");
        json.append("  \"total\": ").append(changes.size()).append("\n");
        json.append("}");

        return json.toString();
    }

    /**
     * 生成 HTML 格式报告。
     *
     * @param changes 变更列表
     * @return HTML 格式报告
     */
    public static String generateHtmlReport(List<FieldChange> changes) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"change-report\">\n");
        html.append("  <h2>Change Report</h2>\n");
        html.append("  <table>\n");
        html.append("    <thead>\n");
        html.append("      <tr><th>Field</th><th>Old Value</th><th>New Value</th><th>Type</th></tr>\n");
        html.append("    </thead>\n");
        html.append("    <tbody>\n");

        for (FieldChange change : changes) {
            html.append("      <tr>");
            html.append("<td>").append(change.getFieldName()).append("</td>");
            html.append("<td>").append(change.getOldValue()).append("</td>");
            html.append("<td>").append(change.getNewValue()).append("</td>");
            html.append("<td>").append(change.getChangeType()).append("</td>");
            html.append("</tr>\n");
        }

        html.append("    </tbody>\n");
        html.append("  </table>\n");
        html.append("  <p>Total changes: ").append(changes.size()).append("</p>\n");
        html.append("</div>");

        return html.toString();
    }

    // ──────────────────────────────────────────────────────────────
    //  补丁生成
    // ──────────────────────────────────────────────────────────────

    /**
     * 根据选项生成补丁。
     *
     * @param changes 变更列表
     * @param options 比较选项（决定补丁格式）
     * @return 补丁字符串
     */
    public static String generatePatch(List<FieldChange> changes, CompareOptions options) {
        if (options.getPatchFormat() == PatchFormat.JSON_PATCH) {
            return generateJsonPatch(changes);
        } else {
            return generateMergePatch(changes);
        }
    }

    /**
     * 生成 JSON Patch (RFC 6902)。
     *
     * @param changes 变更列表
     * @return JSON Patch 格式字符串
     */
    public static String generateJsonPatch(List<FieldChange> changes) {
        StringBuilder patch = new StringBuilder();
        patch.append("[\n");

        for (int i = 0; i < changes.size(); i++) {
            FieldChange change = changes.get(i);
            String path = "/" + change.getFieldName().replace('.', '/');

            switch (change.getChangeType()) {
                case CREATE:
                    patch.append("  {\"op\":\"add\",\"path\":\"").append(path)
                            .append("\",\"value\":").append(toJsonValue(change.getNewValue())).append("}");
                    break;
                case UPDATE:
                    patch.append("  {\"op\":\"replace\",\"path\":\"").append(path)
                            .append("\",\"value\":").append(toJsonValue(change.getNewValue())).append("}");
                    break;
                case DELETE:
                    patch.append("  {\"op\":\"remove\",\"path\":\"").append(path).append("\"}");
                    break;
                case MOVE:
                    // MOVE 在 JSON Patch 中表示为 remove + add 组合
                    patch.append("  {\"op\":\"move\",\"from\":\"").append(path)
                            .append("\",\"path\":\"").append(path).append("\"}");
                    break;
            }

            if (i < changes.size() - 1) patch.append(",");
            patch.append("\n");
        }

        patch.append("]");
        return patch.toString();
    }

    /**
     * 生成 Merge Patch (RFC 7396)。
     *
     * @param changes 变更列表
     * @return Merge Patch 格式字符串
     */
    @SuppressWarnings("unchecked")
    public static String generateMergePatch(List<FieldChange> changes) {
        Map<String, Object> patch = new LinkedHashMap<>();

        for (FieldChange change : changes) {
            String[] parts = change.getFieldName().split("\\.");
            Map<String, Object> current = patch;

            for (int i = 0; i < parts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(
                        parts[i],
                        k -> new LinkedHashMap<>()
                );
            }

            String leaf = parts[parts.length - 1];
            switch (change.getChangeType()) {
                case DELETE:
                    current.put(leaf, null);
                    break;
                case CREATE:
                case UPDATE:
                case MOVE:
                    current.put(leaf, change.getNewValue());
                    break;
            }
        }

        return toJsonObject(patch);
    }

    // ──────────────────────────────────────────────────────────────
    //  JSON 序列化工具
    // ──────────────────────────────────────────────────────────────

    /**
     * 将值序列化为 JSON 值字面量。
     */
    static String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        String str = String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + str + "\"";
    }

    /**
     * 将对象序列化为 JSON 对象字符串。
     */
    static String toJsonObject(Object obj) {
        if (obj == null) return "null";

        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":")
                        .append(toJsonObject(entry.getValue()));
            }
            return sb.append("}").toString();
        }

        return toJsonValue(obj);
    }
}
