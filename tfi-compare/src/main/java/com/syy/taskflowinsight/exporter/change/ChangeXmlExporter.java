package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

/**
 * XML格式变更导出器
 * 将变更记录导出为XML格式
 * 
 * 特性：
 * - 标准XML格式
 * - 自动转义特殊字符
 * - 支持命名空间
 * - 可选的Schema验证
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class ChangeXmlExporter implements ChangeExporter {
    
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String ROOT_ELEMENT = "changeRecords";
    private static final String NAMESPACE = "http://taskflowinsight.syy.com/changes";
    
    @Override
    public String format(List<ChangeRecord> changes) {
        return format(changes, ExportConfig.DEFAULT);
    }
    
    @Override
    public String format(List<ChangeRecord> changes, ExportConfig config) {
        StringWriter writer = new StringWriter();
        
        // XML头部
        writer.write(XML_HEADER);
        writer.write("\n");
        
        // 根元素
        writer.write("<");
        writer.write(ROOT_ELEMENT);
        writer.write(" xmlns=\"");
        writer.write(NAMESPACE);
        writer.write("\"");
        
        if (config.isShowTimestamp()) {
            writer.write(" timestamp=\"");
            writer.write(Instant.now().toString());
            writer.write("\"");
        }
        
        writer.write(" count=\"");
        writer.write(String.valueOf(changes.size()));
        writer.write("\">\n");
        
        // 变更记录
        for (ChangeRecord change : changes) {
            writeChangeRecord(writer, change, config);
        }
        
        // 根元素结束
        writer.write("</");
        writer.write(ROOT_ELEMENT);
        writer.write(">");
        
        return writer.toString();
    }
    
    private void writeChangeRecord(StringWriter writer, ChangeRecord change, ExportConfig config) {
        writer.write("  <changeRecord");
        
        // 属性
        writer.write(" type=\"");
        writer.write(change.getChangeType().name());
        writer.write("\"");
        
        writer.write(" object=\"");
        writer.write(escapeXml(change.getObjectName()));
        writer.write("\"");
        
        writer.write(" field=\"");
        writer.write(escapeXml(change.getFieldName()));
        writer.write("\"");
        
        if (config.isShowTimestamp() && change.getTimestamp() > 0) {
            writer.write(" timestamp=\"");
            writer.write(String.valueOf(change.getTimestamp()));
            writer.write("\"");
        }
        
        writer.write(">\n");
        
        // 旧值
        if (change.getOldValue() != null) {
            writer.write("    <oldValue>");
            writer.write(escapeXml(formatValue(change.getOldValue(), config)));
            writer.write("</oldValue>\n");
        }
        
        // 新值
        if (change.getNewValue() != null) {
            writer.write("    <newValue>");
            writer.write(escapeXml(formatValue(change.getNewValue(), config)));
            writer.write("</newValue>\n");
        }
        
        // 会话ID和任务路径（可选）
        if (change.getSessionId() != null) {
            writer.write("    <sessionId>");
            writer.write(escapeXml(change.getSessionId()));
            writer.write("</sessionId>\n");
        }
        
        if (change.getTaskPath() != null) {
            writer.write("    <taskPath>");
            writer.write(escapeXml(change.getTaskPath()));
            writer.write("</taskPath>\n");
        }
        
        writer.write("  </changeRecord>\n");
    }
    
    private String formatValue(Object value, ExportConfig config) {
        if (value == null) {
            return "";
        }
        
        String str = value.toString();
        
        // 限制长度
        if (config.getMaxValueLength() > 0 && str.length() > config.getMaxValueLength()) {
            str = str.substring(0, config.getMaxValueLength()) + "...";
        }
        
        // 脱敏处理
        if (!config.isIncludeSensitiveInfo()) {
            str = maskSensitiveInfo(str);
        }
        
        return str;
    }
    
    private String maskSensitiveInfo(String value) {
        // 简单的脱敏逻辑
        if (value.matches(".*\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b.*")) {
            // 信用卡号
            return value.replaceAll("\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}", "****-****-****");
        }
        return value;
    }
    
    /**
     * 转义XML特殊字符
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        
        StringBuilder escaped = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '&':
                    escaped.append("&amp;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&apos;");
                    break;
                default:
                    if (c < 0x20) {
                        // 转义控制字符
                        escaped.append("&#").append((int) c).append(";");
                    } else {
                        // 保留Unicode字符不转义
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }
}