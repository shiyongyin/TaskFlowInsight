package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 集合比较策略
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class CollectionCompareStrategy implements CompareStrategy<Collection<?>> {
    
    @Override
    public CompareResult compare(Collection<?> col1, Collection<?> col2, CompareOptions options) {
        if (col1 == col2) {
            return CompareResult.identical();
        }
        
        if (col1 == null || col2 == null) {
            return CompareResult.ofNullDiff(col1, col2);
        }
        
        List<FieldChange> changes = new ArrayList<>();
        
        // 分析集合变化
        Set<Object> set1 = new HashSet<>(col1);
        Set<Object> set2 = new HashSet<>(col2);
        
        // 找出添加的元素
        Set<Object> added = new HashSet<>(set2);
        added.removeAll(set1);
        
        // 找出删除的元素
        Set<Object> removed = new HashSet<>(set1);
        removed.removeAll(set2);
        
        // 创建集合变更详情
        FieldChange.CollectionChangeDetail detail = FieldChange.CollectionChangeDetail.builder()
            .addedCount(added.size())
            .removedCount(removed.size())
            .originalSize(col1.size())
            .newSize(col2.size())
            .build();
        
        // 生成变更记录
        if (!added.isEmpty() || !removed.isEmpty() || col1.size() != col2.size()) {
            FieldChange change = FieldChange.builder()
                .fieldName("collection")
                .oldValue(col1)
                .newValue(col2)
                .changeType(calculateChangeType(col1, col2))
                .collectionChange(true)
                .collectionDetail(detail)
                .build();
            changes.add(change);
        }
        
        // 构建结果
        CompareResult.CompareResultBuilder builder = CompareResult.builder()
            .object1(col1)
            .object2(col2)
            .changes(changes)
            .identical(changes.isEmpty());
        
        // 计算相似度
        if (options.isCalculateSimilarity()) {
            double similarity = calculateSimilarity(set1, set2);
            builder.similarity(similarity);
        }
        
        // 生成报告
        if (options.isGenerateReport()) {
            String report = generateCollectionReport(col1, col2, added, removed, options);
            builder.report(report);
        }
        
        return builder.build();
    }
    
    @Override
    public String getName() {
        return "CollectionCompare";
    }
    
    @Override
    public boolean supports(Class<?> type) {
        return Collection.class.isAssignableFrom(type);
    }
    
    private ChangeType calculateChangeType(Collection<?> col1, Collection<?> col2) {
        if (col1.isEmpty() && !col2.isEmpty()) {
            return ChangeType.CREATE;
        } else if (!col1.isEmpty() && col2.isEmpty()) {
            return ChangeType.DELETE;
        } else {
            return ChangeType.UPDATE;
        }
    }
    
    private double calculateSimilarity(Set<Object> set1, Set<Object> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0;
        }
        
        Set<Object> union = new HashSet<>(set1);
        union.addAll(set2);
        
        Set<Object> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        return (double) intersection.size() / union.size();
    }
    
    private String generateCollectionReport(Collection<?> col1, Collection<?> col2,
                                           Set<Object> added, Set<Object> removed,
                                           CompareOptions options) {
        StringBuilder report = new StringBuilder();
        
        if (options.getReportFormat() == ReportFormat.MARKDOWN) {
            report.append("## Collection Comparison\n\n");
            report.append("| Metric | Value |\n");
            report.append("|--------|-------|\n");
            report.append("| Original Size | ").append(col1.size()).append(" |\n");
            report.append("| New Size | ").append(col2.size()).append(" |\n");
            report.append("| Added | ").append(added.size()).append(" |\n");
            report.append("| Removed | ").append(removed.size()).append(" |\n");
            
            if (!added.isEmpty()) {
                report.append("\n### Added Elements\n");
                added.forEach(e -> report.append("- ").append(e).append("\n"));
            }
            
            if (!removed.isEmpty()) {
                report.append("\n### Removed Elements\n");
                removed.forEach(e -> report.append("- ").append(e).append("\n"));
            }
        } else {
            report.append("Collection Comparison:\n");
            report.append("  Original size: ").append(col1.size()).append("\n");
            report.append("  New size: ").append(col2.size()).append("\n");
            report.append("  Added: ").append(added.size()).append(" elements\n");
            report.append("  Removed: ").append(removed.size()).append(" elements\n");
        }
        
        return report.toString();
    }
}