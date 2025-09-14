package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.List;

/**
 * 变更记录导出器接口
 * 将ChangeRecord列表导出为不同格式
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-10
 */
public interface ChangeExporter {
    
    /**
     * 将变更记录列表格式化为字符串
     * 
     * @param changes 变更记录列表
     * @return 格式化后的字符串
     */
    String format(List<ChangeRecord> changes);
    
    /**
     * 将变更记录列表格式化为字符串（带配置）
     * 
     * @param changes 变更记录列表
     * @param config 导出配置
     * @return 格式化后的字符串
     */
    default String format(List<ChangeRecord> changes, ExportConfig config) {
        return format(changes);
    }
    
    /**
     * 导出配置类
     */
    class ExportConfig {
        public static final ExportConfig DEFAULT = new ExportConfig();
        
        private boolean showTimestamp = false;
        private boolean prettyPrint = true;
        private boolean includeSensitiveInfo = false;
        private int maxValueLength = 100;
        
        // Getters and setters
        public boolean isShowTimestamp() { return showTimestamp; }
        public void setShowTimestamp(boolean showTimestamp) { this.showTimestamp = showTimestamp; }
        
        public boolean isPrettyPrint() { return prettyPrint; }
        public void setPrettyPrint(boolean prettyPrint) { this.prettyPrint = prettyPrint; }
        
        public boolean isIncludeSensitiveInfo() { return includeSensitiveInfo; }
        public void setIncludeSensitiveInfo(boolean includeSensitiveInfo) { this.includeSensitiveInfo = includeSensitiveInfo; }
        
        public int getMaxValueLength() { return maxValueLength; }
        public void setMaxValueLength(int maxValueLength) { this.maxValueLength = maxValueLength; }
    }
}