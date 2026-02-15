package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.*;

/**
 * 变更记录Map导出器
 * 按照VIP-006要求，保持与其他导出器的一致性，主要用于内部结构验证
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-10
 */
public class ChangeMapExporter {
    
    /** 敏感字段关键词 */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "secret", "token", "key", "credential", "auth"
    );
    
    /** 脱敏替换文本 */
    private static final String MASKED_VALUE = "[MASKED]";
    
    /**
     * 将变更记录列表导出为Map结构
     * 
     * @param changes 变更记录列表
     * @return Map结构的导出结果
     */
    public static Map<String, Object> export(List<ChangeRecord> changes) {
        return export(changes, ChangeExporter.ExportConfig.DEFAULT);
    }
    
    /**
     * 将变更记录列表导出为Map结构（带配置）
     * 
     * @param changes 变更记录列表
     * @param config 导出配置
     * @return Map结构的导出结果
     */
    public static Map<String, Object> export(List<ChangeRecord> changes, ChangeExporter.ExportConfig config) {
        if (changes == null) {
            changes = Collections.emptyList();
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 基本统计信息
        result.put("count", changes.size());
        
        if (!changes.isEmpty()) {
            result.put("timestamp", changes.get(0).getTimestamp());
        }
        
        // 变更列表
        List<Map<String, Object>> changeList = new ArrayList<>();
        for (ChangeRecord change : changes) {
            changeList.add(exportSingleChange(change, config));
        }
        result.put("changes", changeList);
        
        // 统计信息
        Map<String, Object> statistics = calculateStatistics(changes);
        result.put("statistics", statistics);
        
        return result;
    }
    
    /**
     * 导出单个变更记录为Map
     */
    private static Map<String, Object> exportSingleChange(ChangeRecord change, ChangeExporter.ExportConfig config) {
        Map<String, Object> changeMap = new LinkedHashMap<>();
        
        // 基本字段
        changeMap.put("type", change.getChangeType().name());
        changeMap.put("object", change.getObjectName());
        changeMap.put("field", change.getFieldName());
        
        // 值处理
        boolean shouldMask = needsMasking(change.getFieldName(), config);
        
        if (change.getOldValue() != null) {
            changeMap.put("oldValue", shouldMask ? MASKED_VALUE : change.getOldValue());
        }
        
        if (change.getNewValue() != null) {
            changeMap.put("newValue", shouldMask ? MASKED_VALUE : change.getNewValue());
        }
        
        // 字符串表示
        if (change.getValueRepr() != null) {
            changeMap.put("valueRepr", shouldMask ? MASKED_VALUE : change.getValueRepr());
        }
        
        if (change.getReprOld() != null) {
            changeMap.put("reprOld", shouldMask ? MASKED_VALUE : change.getReprOld());
        }
        
        if (change.getReprNew() != null) {
            changeMap.put("reprNew", shouldMask ? MASKED_VALUE : change.getReprNew());
        }
        
        // 元数据
        if (change.getValueKind() != null) {
            changeMap.put("valueKind", change.getValueKind());
        }
        
        if (change.getValueType() != null) {
            changeMap.put("valueType", change.getValueType());
        }
        
        // 上下文信息
        if (change.getSessionId() != null) {
            changeMap.put("sessionId", change.getSessionId());
        }
        
        if (change.getTaskPath() != null) {
            changeMap.put("taskPath", change.getTaskPath());
        }
        
        // 时间戳
        changeMap.put("timestamp", change.getTimestamp());
        
        return changeMap;
    }
    
    /**
     * 计算统计信息
     */
    private static Map<String, Object> calculateStatistics(List<ChangeRecord> changes) {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        // 按类型统计
        Map<String, Integer> typeCount = new HashMap<>();
        typeCount.put("CREATE", 0);
        typeCount.put("UPDATE", 0);
        typeCount.put("DELETE", 0);
        
        // 按对象统计
        Set<String> uniqueObjects = new HashSet<>();
        
        // 按字段统计
        Set<String> uniqueFields = new HashSet<>();
        
        for (ChangeRecord change : changes) {
            // 类型统计
            String type = change.getChangeType().name();
            typeCount.put(type, typeCount.get(type) + 1);
            
            // 对象统计
            if (change.getObjectName() != null) {
                uniqueObjects.add(change.getObjectName());
            }
            
            // 字段统计
            if (change.getFieldName() != null) {
                uniqueFields.add(change.getFieldName());
            }
        }
        
        stats.put("byType", typeCount);
        stats.put("objectCount", uniqueObjects.size());
        stats.put("fieldCount", uniqueFields.size());
        stats.put("totalChanges", changes.size());
        
        // 对象和字段列表
        stats.put("objects", new ArrayList<>(uniqueObjects));
        stats.put("fields", new ArrayList<>(uniqueFields));
        
        return stats;
    }
    
    /**
     * 检查字段是否需要脱敏
     */
    private static boolean needsMasking(String fieldName, ChangeExporter.ExportConfig config) {
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
     * 按对象名分组导出变更记录
     * 
     * @param changes 变更记录列表
     * @return 按对象名分组的Map
     */
    public static Map<String, List<Map<String, Object>>> exportGroupedByObject(List<ChangeRecord> changes) {
        return exportGroupedByObject(changes, ChangeExporter.ExportConfig.DEFAULT);
    }
    
    /**
     * 按对象名分组导出变更记录（带配置）
     * 
     * @param changes 变更记录列表
     * @param config 导出配置
     * @return 按对象名分组的Map
     */
    public static Map<String, List<Map<String, Object>>> exportGroupedByObject(List<ChangeRecord> changes, 
                                                                               ChangeExporter.ExportConfig config) {
        if (changes == null) {
            return Collections.emptyMap();
        }
        
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        
        for (ChangeRecord change : changes) {
            String objectName = change.getObjectName() != null ? change.getObjectName() : "unknown";
            
            grouped.computeIfAbsent(objectName, k -> new ArrayList<>())
                   .add(exportSingleChange(change, config));
        }
        
        return grouped;
    }
}