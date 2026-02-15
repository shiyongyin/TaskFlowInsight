package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * 流式变更导出器基类
 * 支持大数据量的流式处理，避免内存溢出
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public abstract class StreamingChangeExporter {
    
    protected static final int DEFAULT_BUFFER_SIZE = 8192;
    protected static final int FLUSH_THRESHOLD = 100; // 每100条记录刷新一次
    
    /**
     * 流式导出到输出流
     * 
     * @param changes 变更记录迭代器
     * @param output 输出流
     * @param config 导出配置
     * @throws IOException IO异常
     */
    public abstract void exportToStream(
        Iterator<ChangeRecord> changes, 
        OutputStream output, 
        ChangeExporter.ExportConfig config) throws IOException;
    
    /**
     * 流式导出到文件
     * 
     * @param changes 变更记录迭代器
     * @param file 目标文件
     * @param config 导出配置
     * @throws IOException IO异常
     */
    public void exportToFile(
        Iterator<ChangeRecord> changes,
        File file,
        ChangeExporter.ExportConfig config) throws IOException {
        
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, DEFAULT_BUFFER_SIZE)) {
            exportToStream(changes, bos, config);
        }
    }
    
    /**
     * 流式处理变更记录
     * 
     * @param changes 变更记录迭代器
     * @param processor 处理器
     * @param batchSize 批次大小
     */
    public void processInBatches(
        Iterator<ChangeRecord> changes,
        Consumer<ChangeRecord> processor,
        int batchSize) {
        
        int count = 0;
        while (changes.hasNext()) {
            processor.accept(changes.next());
            count++;
            
            if (count % batchSize == 0) {
                // 可以在这里添加批次处理逻辑
                onBatchProcessed(count);
            }
        }
        
        if (count % batchSize != 0) {
            onBatchProcessed(count);
        }
    }
    
    /**
     * 批次处理完成回调
     * 
     * @param totalProcessed 已处理的总数
     */
    protected void onBatchProcessed(int totalProcessed) {
        // 子类可以覆盖此方法
    }
    
    /**
     * XML流式导出器
     */
    public static class StreamingXmlExporter extends StreamingChangeExporter {
        
        @Override
        public void exportToStream(
            Iterator<ChangeRecord> changes,
            OutputStream output,
            ChangeExporter.ExportConfig config) throws IOException {
            
            try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                 BufferedWriter buffered = new BufferedWriter(writer, DEFAULT_BUFFER_SIZE)) {
                
                // 写入XML头部
                buffered.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                buffered.write("<changeRecords xmlns=\"http://taskflowinsight.syy.com/changes\">\n");
                
                int count = 0;
                while (changes.hasNext()) {
                    ChangeRecord change = changes.next();
                    writeXmlRecord(buffered, change, config);
                    count++;
                    
                    // 定期刷新
                    if (count % FLUSH_THRESHOLD == 0) {
                        buffered.flush();
                    }
                }
                
                // 写入尾部
                buffered.write("</changeRecords>\n");
                buffered.flush();
            }
        }
        
        private void writeXmlRecord(BufferedWriter writer, ChangeRecord change, 
                                   ChangeExporter.ExportConfig config) throws IOException {
            writer.write("  <changeRecord");
            writer.write(" type=\"" + change.getChangeType() + "\"");
            writer.write(" object=\"" + escapeXml(change.getObjectName()) + "\"");
            writer.write(" field=\"" + escapeXml(change.getFieldName()) + "\"");
            
            if (config.isShowTimestamp()) {
                writer.write(" timestamp=\"" + change.getTimestamp() + "\"");
            }
            
            writer.write(">\n");
            
            if (change.getOldValue() != null) {
                writer.write("    <oldValue>" + escapeXml(formatValue(change.getOldValue(), config)) + "</oldValue>\n");
            }
            
            if (change.getNewValue() != null) {
                writer.write("    <newValue>" + escapeXml(formatValue(change.getNewValue(), config)) + "</newValue>\n");
            }
            
            writer.write("  </changeRecord>\n");
        }
        
        private String escapeXml(String text) {
            if (text == null) return "";
            return text.replace("&", "&amp;")
                      .replace("<", "&lt;")
                      .replace(">", "&gt;")
                      .replace("\"", "&quot;")
                      .replace("'", "&apos;");
        }
    }
    
    /**
     * CSV流式导出器
     */
    public static class StreamingCsvExporter extends StreamingChangeExporter {
        
        private final String separator;
        private final boolean includeHeader;
        
        public StreamingCsvExporter() {
            this(",", true);
        }
        
        public StreamingCsvExporter(String separator, boolean includeHeader) {
            this.separator = separator;
            this.includeHeader = includeHeader;
        }
        
        @Override
        public void exportToStream(
            Iterator<ChangeRecord> changes,
            OutputStream output,
            ChangeExporter.ExportConfig config) throws IOException {
            
            try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                 BufferedWriter buffered = new BufferedWriter(writer, DEFAULT_BUFFER_SIZE)) {
                
                // 写入CSV头部
                if (includeHeader) {
                    writeHeader(buffered, config);
                }
                
                int count = 0;
                while (changes.hasNext()) {
                    ChangeRecord change = changes.next();
                    writeCsvRecord(buffered, change, config);
                    count++;
                    
                    // 定期刷新
                    if (count % FLUSH_THRESHOLD == 0) {
                        buffered.flush();
                    }
                }
                
                buffered.flush();
            }
        }
        
        private void writeHeader(BufferedWriter writer, ChangeExporter.ExportConfig config) 
            throws IOException {
            
            writer.write("ChangeType" + separator);
            writer.write("ObjectName" + separator);
            writer.write("FieldName" + separator);
            writer.write("OldValue" + separator);
            writer.write("NewValue");
            
            if (config.isShowTimestamp()) {
                writer.write(separator + "Timestamp");
            }
            
            writer.write("\n");
        }
        
        private void writeCsvRecord(BufferedWriter writer, ChangeRecord change,
                                   ChangeExporter.ExportConfig config) throws IOException {
            
            writer.write(escapeCsv(change.getChangeType().toString()) + separator);
            writer.write(escapeCsv(change.getObjectName()) + separator);
            writer.write(escapeCsv(change.getFieldName()) + separator);
            writer.write(escapeCsv(formatValue(change.getOldValue(), config)) + separator);
            writer.write(escapeCsv(formatValue(change.getNewValue(), config)));
            
            if (config.isShowTimestamp()) {
                writer.write(separator + change.getTimestamp());
            }
            
            writer.write("\n");
        }
        
        private String escapeCsv(String value) {
            if (value == null) return "";
            
            // 检查是否需要引用
            if (value.contains(separator) || value.contains("\"") || 
                value.contains("\n") || value.contains("\r")) {
                // 转义引号并用引号包围
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            
            return value;
        }
    }
    
    /**
     * JSON流式导出器（使用JSON Lines格式）
     */
    public static class StreamingJsonExporter extends StreamingChangeExporter {
        
        @Override
        public void exportToStream(
            Iterator<ChangeRecord> changes,
            OutputStream output,
            ChangeExporter.ExportConfig config) throws IOException {
            
            try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                 BufferedWriter buffered = new BufferedWriter(writer, DEFAULT_BUFFER_SIZE)) {
                
                int count = 0;
                while (changes.hasNext()) {
                    ChangeRecord change = changes.next();
                    writeJsonRecord(buffered, change, config);
                    buffered.write("\n"); // JSON Lines格式，每行一个JSON对象
                    count++;
                    
                    // 定期刷新
                    if (count % FLUSH_THRESHOLD == 0) {
                        buffered.flush();
                    }
                }
                
                buffered.flush();
            }
        }
        
        private void writeJsonRecord(BufferedWriter writer, ChangeRecord change,
                                    ChangeExporter.ExportConfig config) throws IOException {
            
            writer.write("{");
            writer.write("\"type\":\"" + change.getChangeType() + "\"");
            writer.write(",\"object\":\"" + escapeJson(change.getObjectName()) + "\"");
            writer.write(",\"field\":\"" + escapeJson(change.getFieldName()) + "\"");
            
            if (change.getOldValue() != null) {
                writer.write(",\"oldValue\":\"" + escapeJson(formatValue(change.getOldValue(), config)) + "\"");
            }
            
            if (change.getNewValue() != null) {
                writer.write(",\"newValue\":\"" + escapeJson(formatValue(change.getNewValue(), config)) + "\"");
            }
            
            if (config.isShowTimestamp()) {
                writer.write(",\"timestamp\":" + change.getTimestamp());
            }
            
            writer.write("}");
        }
        
        private String escapeJson(String value) {
            if (value == null) return "";
            
            return value.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\b", "\\b")
                       .replace("\f", "\\f")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t");
        }
    }
    
    /**
     * 格式化值
     */
    protected static String formatValue(Object value, ChangeExporter.ExportConfig config) {
        if (value == null) {
            return "null";
        }
        
        String str = value.toString();
        
        // 应用最大长度限制
        if (config.getMaxValueLength() > 0 && str.length() > config.getMaxValueLength()) {
            str = str.substring(0, config.getMaxValueLength()) + "...";
        }
        
        // 脱敏处理
        if (!config.isIncludeSensitiveInfo()) {
            str = maskSensitiveInfo(str);
        }
        
        return str;
    }
    
    /**
     * 脱敏敏感信息
     */
    private static String maskSensitiveInfo(String value) {
        // 简单的脱敏逻辑，可以根据需要扩展
        if (value.toLowerCase().contains("password") || 
            value.toLowerCase().contains("secret") ||
            value.toLowerCase().contains("token")) {
            return "***MASKED***";
        }
        return value;
    }
}