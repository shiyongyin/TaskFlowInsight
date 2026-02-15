package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 变更记录控制台导出器
 * 按照VIP-006要求，统一消息格式：<obj>.<field>: <old> → <new>
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-10
 */
public class ChangeConsoleExporter implements ChangeExporter {
    
    /** 敏感字段关键词 */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "secret", "token", "key", "credential", "auth"
    );
    
    /** 脱敏替换文本 */
    private static final String MASKED_VALUE = "[MASKED]";
    
    @Override
    public String format(List<ChangeRecord> changes) {
        return format(changes, ExportConfig.DEFAULT);
    }
    
    @Override
    public String format(List<ChangeRecord> changes, ExportConfig config) {
        if (changes == null || changes.isEmpty()) {
            return "=== Change Summary ===\nTotal changes: 0\n";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 头部信息
        sb.append("=== Change Summary ===\n");
        sb.append("Total changes: ").append(changes.size()).append("\n");
        
        if (config.isShowTimestamp() && !changes.isEmpty()) {
            long firstTimestamp = changes.get(0).getTimestamp();
            sb.append("Time: ").append(formatTimestamp(firstTimestamp)).append("\n");
        }
        
        sb.append("\n");
        
        // 变更列表
        for (ChangeRecord change : changes) {
            formatSingleChange(sb, change, config);
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化单个变更记录
     */
    private void formatSingleChange(StringBuilder sb, ChangeRecord change, ExportConfig config) {
        // 变更类型标识
        sb.append("- [").append(change.getChangeType()).append("] ");
        
        // 对象.字段格式
        sb.append(change.getObjectName()).append(".");
        sb.append(change.getFieldName()).append(": ");
        
        // 值的变化表示
        String oldValueRepr = getValueRepr(change, true, config);
        String newValueRepr = getValueRepr(change, false, config);
        
        switch (change.getChangeType()) {
            case CREATE:
                sb.append("null → ").append(newValueRepr);
                break;
            case DELETE:
                sb.append(oldValueRepr).append(" → null");
                break;
            case UPDATE:
                sb.append(oldValueRepr).append(" → ").append(newValueRepr);
                break;
        }
        
        // 时间戳（可选）
        if (config.isShowTimestamp()) {
            sb.append(" @").append(formatTimestamp(change.getTimestamp()));
        }
        
        sb.append("\n");
    }
    
    /**
     * 获取值的字符串表示
     */
    private String getValueRepr(ChangeRecord change, boolean isOldValue, ExportConfig config) {
        Object value = isOldValue ? change.getOldValue() : change.getNewValue();
        
        if (value == null) {
            return "null";
        }
        
        // 检查是否需要脱敏
        String repr;
        if (needsMasking(change.getFieldName(), config)) {
            repr = MASKED_VALUE;
        } else {
            // 优先使用ChangeRecord中的repr字段，因为它已经过转义和截断处理
            if (isOldValue && change.getReprOld() != null) {
                repr = change.getReprOld();
            } else if (!isOldValue && change.getReprNew() != null) {
                repr = change.getReprNew();
            } else if (change.getValueRepr() != null) {
                repr = change.getValueRepr();
            } else {
                repr = String.valueOf(value);
            }
        }
        
        // 长度限制
        if (repr.length() > config.getMaxValueLength()) {
            repr = repr.substring(0, config.getMaxValueLength() - 3) + "...";
        }
        
        return repr;
    }
    
    /**
     * 检查字段是否需要脱敏
     */
    private boolean needsMasking(String fieldName, ExportConfig config) {
        if (config.isIncludeSensitiveInfo()) {
            return false; // 配置允许显示敏感信息
        }
        
        if (fieldName == null) {
            return false;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lowerFieldName::contains);
    }
    
    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        return DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timestamp));
    }
}