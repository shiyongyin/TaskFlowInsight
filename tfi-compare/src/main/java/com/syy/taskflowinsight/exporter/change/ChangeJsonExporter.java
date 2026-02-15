package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 变更记录JSON导出器
 * 按照VIP-006要求，确保结构稳定，支持COMPAT和ENHANCED模式
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-10
 */
public class ChangeJsonExporter implements ChangeExporter {
    
    /** 导出模式 */
    public enum ExportMode {
        COMPAT,     // 兼容模式：简单结构
        ENHANCED    // 增强模式：包含元数据和额外字段
    }
    
    /** 敏感字段关键词 */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "secret", "token", "key", "credential", "auth"
    );
    
    /** 脱敏替换文本 */
    private static final String MASKED_VALUE = "[MASKED]";
    
    private final ExportMode mode;
    
    public ChangeJsonExporter() {
        this(ExportMode.COMPAT);
    }
    
    public ChangeJsonExporter(ExportMode mode) {
        this.mode = mode != null ? mode : ExportMode.COMPAT;
    }
    
    @Override
    public String format(List<ChangeRecord> changes) {
        return format(changes, ExportConfig.DEFAULT);
    }
    
    @Override
    public String format(List<ChangeRecord> changes, ExportConfig config) {
        if (changes == null) {
            return "{\"changes\":[]}";
        }
        
        StringWriter writer = new StringWriter();
        
        try {
            writer.write('{');
            
            // 根据模式添加不同的结构
            if (mode == ExportMode.ENHANCED) {
                // 增强模式：包含元数据
                writer.write("\"metadata\":{");
                writer.write("\"timestamp\":\"");
                writer.write(Instant.now().toString());
                writer.write("\",");
                writer.write("\"version\":\"2.1.0\",");
                writer.write("\"count\":");
                writer.write(String.valueOf(changes.size()));
                writer.write("},");
            }
            
            // 变更数据
            writer.write("\"changes\":[");
            
            for (int i = 0; i < changes.size(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                writeChangeRecord(writer, changes.get(i), config);
            }
            
            writer.write("]}");
            
            return writer.toString();
            
        } catch (Exception e) {
            // 输出结构稳定：失败时返回错误信息但保持JSON格式
            return "{\"error\":\"Export failed: " + escapeJsonString(e.getMessage()) + "\"}";
        }
    }
    
    /**
     * 写入单个变更记录
     */
    private void writeChangeRecord(StringWriter writer, ChangeRecord change, ExportConfig config) {
        writer.write('{');
        
        // 基本字段（稳定结构）
        writeField(writer, "type", change.getChangeType().name(), false);
        writeField(writer, "object", change.getObjectName(), true);
        writeField(writer, "field", change.getFieldName(), true);
        
        // 值字段处理
        if (mode == ExportMode.ENHANCED) {
            // 增强模式：分别提供新旧值
            Object oldValue = change.getOldValue();
            Object newValue = change.getNewValue();
            
            if (oldValue != null) {
                writeField(writer, "oldValue", 
                    needsMasking(change.getFieldName(), config) ? MASKED_VALUE : oldValue, true);
            } else {
                writeField(writer, "oldValue", null, true);
            }
            
            if (newValue != null) {
                writeField(writer, "newValue", 
                    needsMasking(change.getFieldName(), config) ? MASKED_VALUE : newValue, true);
            } else {
                writeField(writer, "newValue", null, true);
            }
            
            // 字符串表示
            if (change.getReprOld() != null) {
                writeField(writer, "reprOld", 
                    needsMasking(change.getFieldName(), config) ? MASKED_VALUE : change.getReprOld(), true);
            }
            if (change.getReprNew() != null) {
                writeField(writer, "reprNew", 
                    needsMasking(change.getFieldName(), config) ? MASKED_VALUE : change.getReprNew(), true);
            }
            
            // 额外元数据
            if (change.getValueKind() != null) {
                writeField(writer, "valueKind", change.getValueKind(), true);
            }
            if (change.getValueType() != null) {
                writeField(writer, "valueType", change.getValueType(), true);
            }
            if (change.getSessionId() != null) {
                writeField(writer, "sessionId", change.getSessionId(), true);
            }
            if (change.getTaskPath() != null) {
                writeField(writer, "taskPath", change.getTaskPath(), true);
            }
        } else {
            // 兼容模式：简单值表示
            String valueRepr = getSimpleValueRepr(change, config);
            writeField(writer, "value", valueRepr, true);
        }
        
        // 时间戳（可选）
        if (config.isShowTimestamp()) {
            writeField(writer, "timestamp", change.getTimestamp(), true);
        }
        
        writer.write('}');
    }
    
    /**
     * 获取简单值表示（兼容模式）
     */
    private String getSimpleValueRepr(ChangeRecord change, ExportConfig config) {
        if (needsMasking(change.getFieldName(), config)) {
            return MASKED_VALUE;
        }
        
        // 优先使用已经处理过的repr
        if (change.getValueRepr() != null) {
            return change.getValueRepr();
        }
        
        // 根据变更类型选择值
        switch (change.getChangeType()) {
            case CREATE:
                return change.getNewValue() != null ? String.valueOf(change.getNewValue()) : "null";
            case DELETE:
                return change.getOldValue() != null ? String.valueOf(change.getOldValue()) : "null";
            case UPDATE:
            default:
                Object newValue = change.getNewValue();
                return newValue != null ? String.valueOf(newValue) : "null";
        }
    }
    
    /**
     * 写入字段
     */
    private void writeField(StringWriter writer, String name, Object value, boolean needComma) {
        if (needComma) {
            writer.write(',');
        }
        
        writer.write('"');
        writer.write(name);
        writer.write("\":");
        
        if (value == null) {
            writer.write("null");
        } else if (value instanceof String) {
            writer.write('"');
            writer.write(escapeJsonString((String) value));
            writer.write('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            writer.write(String.valueOf(value));
        } else {
            writer.write('"');
            writer.write(escapeJsonString(String.valueOf(value)));
            writer.write('"');
        }
    }
    
    /**
     * 检查字段是否需要脱敏
     */
    private boolean needsMasking(String fieldName, ExportConfig config) {
        if (config.isIncludeSensitiveInfo()) {
            return false;
        }
        
        if (fieldName == null) {
            return false;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lowerFieldName::contains);
    }
    
    /**
     * JSON字符串转义
     */
    private String escapeJsonString(String str) {
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
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}