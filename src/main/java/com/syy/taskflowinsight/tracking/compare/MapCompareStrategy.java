package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;

import java.util.*;

/**
 * Map比较策略
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class MapCompareStrategy implements CompareStrategy<Map<?, ?>> {
    
    @Override
    public CompareResult compare(Map<?, ?> map1, Map<?, ?> map2, CompareOptions options) {
        if (map1 == map2) {
            return CompareResult.identical();
        }
        
        if (map1 == null || map2 == null) {
            return CompareResult.ofNullDiff(map1, map2);
        }
        
        List<FieldChange> changes = new ArrayList<>();
        
        // 获取所有键
        Set<Object> allKeys = new HashSet<>();
        allKeys.addAll(map1.keySet());
        allKeys.addAll(map2.keySet());
        
        // 比较每个键的值
        for (Object key : allKeys) {
            Object value1 = map1.get(key);
            Object value2 = map2.get(key);
            
            if (!Objects.equals(value1, value2)) {
                ChangeType changeType;
                if (value1 == null) {
                    changeType = ChangeType.CREATE;
                } else if (value2 == null) {
                    changeType = ChangeType.DELETE;
                } else {
                    changeType = ChangeType.UPDATE;
                }
                
                FieldChange change = FieldChange.builder()
                    .fieldName(String.valueOf(key))
                    .oldValue(value1)
                    .newValue(value2)
                    .changeType(changeType)
                    .build();
                changes.add(change);
            }
        }
        
        // 构建结果
        CompareResult.CompareResultBuilder builder = CompareResult.builder()
            .object1(map1)
            .object2(map2)
            .changes(changes)
            .identical(changes.isEmpty());
        
        // 计算相似度
        if (options.isCalculateSimilarity()) {
            double similarity = calculateMapSimilarity(map1, map2);
            builder.similarity(similarity);
        }
        
        // 生成报告
        if (options.isGenerateReport()) {
            String report = generateMapReport(map1, map2, changes, options);
            builder.report(report);
        }
        
        return builder.build();
    }
    
    @Override
    public String getName() {
        return "MapCompare";
    }
    
    @Override
    public boolean supports(Class<?> type) {
        return Map.class.isAssignableFrom(type);
    }
    
    private double calculateMapSimilarity(Map<?, ?> map1, Map<?, ?> map2) {
        if (map1.isEmpty() && map2.isEmpty()) {
            return 1.0;
        }
        
        Set<Object> allKeys = new HashSet<>();
        allKeys.addAll(map1.keySet());
        allKeys.addAll(map2.keySet());
        
        int sameValues = 0;
        for (Object key : allKeys) {
            if (Objects.equals(map1.get(key), map2.get(key))) {
                sameValues++;
            }
        }
        
        return (double) sameValues / allKeys.size();
    }
    
    private String generateMapReport(Map<?, ?> map1, Map<?, ?> map2,
                                    List<FieldChange> changes,
                                    CompareOptions options) {
        StringBuilder report = new StringBuilder();
        
        if (options.getReportFormat() == ReportFormat.MARKDOWN) {
            report.append("## Map Comparison\n\n");
            report.append("| Key | Old Value | New Value | Change |\n");
            report.append("|-----|-----------|-----------|--------|\n");
            
            for (FieldChange change : changes) {
                report.append("| ").append(change.getFieldName())
                      .append(" | ").append(change.getOldValue())
                      .append(" | ").append(change.getNewValue())
                      .append(" | ").append(change.getChangeType())
                      .append(" |\n");
            }
            
            report.append("\n**Total changes:** ").append(changes.size()).append("\n");
        } else {
            report.append("Map Comparison:\n");
            for (FieldChange change : changes) {
                report.append("  ").append(change.getFieldName())
                      .append(": ").append(change.getOldValue())
                      .append(" -> ").append(change.getNewValue())
                      .append(" (").append(change.getChangeType()).append(")\n");
            }
            report.append("Total changes: ").append(changes.size()).append("\n");
        }
        
        return report.toString();
    }
}