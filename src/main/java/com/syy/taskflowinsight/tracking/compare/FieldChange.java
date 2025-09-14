package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import lombok.Builder;
import lombok.Data;

/**
 * 字段变更
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Builder
public class FieldChange {
    
    /**
     * 字段名称
     */
    private String fieldName;
    
    /**
     * 旧值
     */
    private Object oldValue;
    
    /**
     * 新值
     */
    private Object newValue;
    
    /**
     * 变更类型
     */
    private ChangeType changeType;
    
    /**
     * 值类型
     */
    private String valueType;
    
    /**
     * 字段路径（用于嵌套对象）
     */
    private String fieldPath;
    
    /**
     * 是否为集合变更
     */
    private boolean collectionChange;
    
    /**
     * 集合变更详情
     */
    private CollectionChangeDetail collectionDetail;
    
    /**
     * 获取值描述
     */
    public String getValueDescription() {
        if (changeType == ChangeType.DELETE) {
            return String.valueOf(oldValue) + " -> (deleted)";
        } else if (changeType == ChangeType.CREATE) {
            return "(new) -> " + String.valueOf(newValue);
        } else {
            return String.valueOf(oldValue) + " -> " + String.valueOf(newValue);
        }
    }
    
    /**
     * 是否为null变更
     */
    public boolean isNullChange() {
        return (oldValue == null && newValue == null) ||
               (oldValue == null && changeType == ChangeType.CREATE) ||
               (newValue == null && changeType == ChangeType.DELETE);
    }
    
    /**
     * 集合变更详情
     */
    @Data
    @Builder
    public static class CollectionChangeDetail {
        private int addedCount;
        private int removedCount;
        private int modifiedCount;
        private int originalSize;
        private int newSize;
    }
}