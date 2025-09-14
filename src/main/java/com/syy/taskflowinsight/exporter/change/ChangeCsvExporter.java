package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

/**
 * CSV格式变更导出器
 * 将变更记录导出为CSV格式
 * 
 * 特性：
 * - 标准CSV格式（RFC 4180）
 * - 自动转义特殊字符
 * - 可配置分隔符
 * - Excel兼容模式
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class ChangeCsvExporter implements ChangeExporter {
    
    private static final String DEFAULT_SEPARATOR = ",";
    private static final String DEFAULT_QUOTE = "\"";
    private static final String DEFAULT_LINE_SEPARATOR = "\n";
    
    private String separator = DEFAULT_SEPARATOR;
    private String quote = DEFAULT_QUOTE;
    private String lineSeparator = DEFAULT_LINE_SEPARATOR;
    private boolean includeHeader = true;
    
    @Override
    public String format(List<ChangeRecord> changes) {
        return format(changes, ExportConfig.DEFAULT);
    }
    
    @Override
    public String format(List<ChangeRecord> changes, ExportConfig config) {
        StringWriter writer = new StringWriter();
        
        // 写入头部
        if (includeHeader) {
            writeHeader(writer, config);
        }
        
        // 写入数据行
        for (ChangeRecord change : changes) {
            writeDataRow(writer, change, config);
        }
        
        return writer.toString();
    }
    
    private void writeHeader(StringWriter writer, ExportConfig config) {
        writer.write("Type");
        writer.write(separator);
        writer.write("Object");
        writer.write(separator);
        writer.write("Field");
        writer.write(separator);
        writer.write("Old Value");
        writer.write(separator);
        writer.write("New Value");
        
        if (config.isShowTimestamp()) {
            writer.write(separator);
            writer.write("Timestamp");
        }
        
        writer.write(lineSeparator);
    }
    
    private void writeDataRow(StringWriter writer, ChangeRecord change, ExportConfig config) {
        // 变更类型
        writer.write(escapeField(change.getChangeType().name()));
        writer.write(separator);
        
        // 对象名称
        writer.write(escapeField(change.getObjectName()));
        writer.write(separator);
        
        // 字段名称
        writer.write(escapeField(change.getFieldName()));
        writer.write(separator);
        
        // 旧值
        writer.write(escapeField(formatValue(change.getOldValue(), config)));
        writer.write(separator);
        
        // 新值
        writer.write(escapeField(formatValue(change.getNewValue(), config)));
        
        // 时间戳（可选）
        if (config.isShowTimestamp()) {
            writer.write(separator);
            if (change.getTimestamp() > 0) {
                writer.write(escapeField(Instant.ofEpochMilli(change.getTimestamp()).toString()));
            } else {
                writer.write("");
            }
        }
        
        writer.write(lineSeparator);
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
        if (value.matches(".*\\b\\d{3}-\\d{2}-\\d{4}\\b.*")) {
            // SSN
            return value.replaceAll("\\d{3}-\\d{2}", "***-**");
        }
        return value;
    }
    
    /**
     * 转义CSV字段
     * 根据RFC 4180标准处理特殊字符
     */
    private String escapeField(String field) {
        if (field == null) {
            return "";
        }
        
        // 检查是否需要引号包围
        boolean needsQuoting = false;
        if (field.contains(separator) || 
            field.contains(quote) || 
            field.contains("\n") || 
            field.contains("\r")) {
            needsQuoting = true;
        }
        
        if (needsQuoting) {
            // 转义引号
            String escaped = field.replace(quote, quote + quote);
            // 用引号包围
            return quote + escaped + quote;
        }
        
        return field;
    }
    
    // 配置方法
    
    public ChangeCsvExporter withSeparator(String separator) {
        this.separator = separator;
        return this;
    }
    
    public ChangeCsvExporter withQuote(String quote) {
        this.quote = quote;
        return this;
    }
    
    public ChangeCsvExporter withLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
        return this;
    }
    
    public ChangeCsvExporter withHeader(boolean includeHeader) {
        this.includeHeader = includeHeader;
        return this;
    }
    
    /**
     * 创建Excel兼容的CSV导出器
     */
    public static ChangeCsvExporter forExcel() {
        return new ChangeCsvExporter()
            .withSeparator(",")
            .withQuote("\"")
            .withLineSeparator("\r\n")
            .withHeader(true);
    }
    
    /**
     * 创建TSV（Tab分隔）导出器
     */
    public static ChangeCsvExporter forTsv() {
        return new ChangeCsvExporter()
            .withSeparator("\t")
            .withQuote("\"")
            .withLineSeparator("\n")
            .withHeader(true);
    }
}