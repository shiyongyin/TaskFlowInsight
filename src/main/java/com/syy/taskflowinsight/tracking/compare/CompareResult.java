package com.syy.taskflowinsight.tracking.compare;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 比较结果
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Builder
public class CompareResult {
    
    private Object object1;
    private Object object2;
    
    @Builder.Default
    private List<FieldChange> changes = Collections.emptyList();
    
    private boolean identical;
    private Double similarity;
    private String report;
    private String patch;
    
    @Builder.Default
    private Instant compareTime = Instant.now();
    
    private long compareTimeMs;
    
    /**
     * 创建相同结果
     */
    public static CompareResult identical() {
        return CompareResult.builder()
            .identical(true)
            .similarity(1.0)
            .changes(Collections.emptyList())
            .build();
    }
    
    /**
     * 创建null差异结果
     */
    public static CompareResult ofNullDiff(Object obj1, Object obj2) {
        return CompareResult.builder()
            .object1(obj1)
            .object2(obj2)
            .identical(false)
            .similarity(0.0)
            .build();
    }
    
    /**
     * 创建类型差异结果
     */
    public static CompareResult ofTypeDiff(Object obj1, Object obj2) {
        return CompareResult.builder()
            .object1(obj1)
            .object2(obj2)
            .identical(false)
            .similarity(0.0)
            .build();
    }
    
    /**
     * 获取变更数量
     */
    public int getChangeCount() {
        return changes != null ? changes.size() : 0;
    }
    
    /**
     * 是否有变更
     */
    public boolean hasChanges() {
        return !identical && getChangeCount() > 0;
    }
    
    /**
     * 获取相似度百分比
     */
    public double getSimilarityPercent() {
        return similarity != null ? similarity * 100 : 0;
    }
}