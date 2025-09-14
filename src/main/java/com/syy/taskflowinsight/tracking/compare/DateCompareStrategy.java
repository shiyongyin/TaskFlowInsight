package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;

import java.util.Date;

/**
 * 日期比较策略
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class DateCompareStrategy implements CompareStrategy<Date> {
    
    private static final long TOLERANCE_MS = 1000; // 1秒容差
    
    @Override
    public CompareResult compare(Date date1, Date date2, CompareOptions options) {
        if (date1 == date2) {
            return CompareResult.identical();
        }
        
        if (date1 == null || date2 == null) {
            return CompareResult.ofNullDiff(date1, date2);
        }
        
        long time1 = date1.getTime();
        long time2 = date2.getTime();
        long diff = Math.abs(time1 - time2);
        
        // 在容差范围内认为相同
        boolean identical = diff <= TOLERANCE_MS;
        
        CompareResult.CompareResultBuilder builder = CompareResult.builder()
            .object1(date1)
            .object2(date2)
            .identical(identical);
        
        if (!identical) {
            FieldChange change = FieldChange.builder()
                .fieldName("date")
                .oldValue(date1)
                .newValue(date2)
                .changeType(ChangeType.UPDATE)
                .build();
            builder.changes(java.util.Collections.singletonList(change));
        }
        
        // 计算相似度（基于时间差）
        if (options.isCalculateSimilarity()) {
            double similarity = calculateTimeSimilarity(diff);
            builder.similarity(similarity);
        }
        
        // 生成报告
        if (options.isGenerateReport()) {
            String report = generateDateReport(date1, date2, diff, options);
            builder.report(report);
        }
        
        return builder.build();
    }
    
    @Override
    public String getName() {
        return "DateCompare";
    }
    
    @Override
    public boolean supports(Class<?> type) {
        return Date.class.isAssignableFrom(type);
    }
    
    private double calculateTimeSimilarity(long diffMs) {
        // 使用指数衰减函数计算相似度
        // 1小时内的差异相似度 > 0.9
        // 1天内的差异相似度 > 0.5
        double hours = diffMs / (1000.0 * 60 * 60);
        return Math.exp(-hours / 24); // 24小时衰减到约0.37
    }
    
    private String generateDateReport(Date date1, Date date2, long diffMs, CompareOptions options) {
        StringBuilder report = new StringBuilder();
        
        long seconds = diffMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (options.getReportFormat() == ReportFormat.MARKDOWN) {
            report.append("## Date Comparison\n\n");
            report.append("| Attribute | Value |\n");
            report.append("|-----------|-------|\n");
            report.append("| Date 1 | ").append(date1).append(" |\n");
            report.append("| Date 2 | ").append(date2).append(" |\n");
            report.append("| Difference | ");
            
            if (days > 0) {
                report.append(days).append(" days");
            } else if (hours > 0) {
                report.append(hours).append(" hours");
            } else if (minutes > 0) {
                report.append(minutes).append(" minutes");
            } else {
                report.append(seconds).append(" seconds");
            }
            report.append(" |\n");
        } else {
            report.append("Date Comparison:\n");
            report.append("  Date 1: ").append(date1).append("\n");
            report.append("  Date 2: ").append(date2).append("\n");
            report.append("  Difference: ");
            
            if (days > 0) {
                report.append(days).append(" days");
            } else if (hours > 0) {
                report.append(hours).append(" hours");
            } else if (minutes > 0) {
                report.append(minutes).append(" minutes");
            } else {
                report.append(seconds).append(" seconds");
            }
            report.append("\n");
        }
        
        return report.toString();
    }
}