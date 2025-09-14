package com.syy.taskflowinsight.tracking.model;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import com.syy.taskflowinsight.tracking.ChangeType;

/**
 * 变更记录数据模型
 * 记录对象字段的变更信息，包括字段名、新旧值、变更类型等元数据
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public final class ChangeRecord {
    
    /** 对象名称，如 "Order", "User" */
    private final String objectName;
    
    /** 字段名称，如 "status", "amount" */
    private final String fieldName;
    
    /** 旧值的原始对象（可能为null） */
    private final Object oldValue;
    
    /** 新值的原始对象（可能为null） */
    private final Object newValue;
    
    /** 变更时间戳（毫秒） */
    @Builder.Default
    private final long timestamp = System.currentTimeMillis();
    
    /** 会话ID（可选） */
    private final String sessionId;
    
    /** 任务路径（可选），如 "MainTask/SubTask" */
    private final String taskPath;
    
    /** 变更类型：CREATE、UPDATE、DELETE */
    private final ChangeType changeType;
    
    /** 值的完全限定类名（FQCN），如 "java.lang.String" */
    private final String valueType;
    
    /** 值的分类（可选）：STRING、NUMBER、BOOLEAN、DATE */
    private final String valueKind;
    
    /** 值的字符串表示（用于展示，经过转义和截断处理） */
    private final String valueRepr;
    
    /** 旧值的字符串表示（增强模式，经过转义和截断处理） */
    private final String reprOld;
    
    /** 新值的字符串表示（增强模式，经过转义和截断处理） */
    private final String reprNew;
    
    // Explicit getters for IDE compatibility (in case Lombok annotation processing fails)
    public String getObjectName() { return objectName; }
    public String getFieldName() { return fieldName; }
    public Object getOldValue() { return oldValue; }
    public Object getNewValue() { return newValue; }
    public long getTimestamp() { return timestamp; }
    public String getSessionId() { return sessionId; }
    public String getTaskPath() { return taskPath; }
    public ChangeType getChangeType() { return changeType; }
    public String getValueType() { return valueType; }
    public String getValueKind() { return valueKind; }
    public String getValueRepr() { return valueRepr; }
    public String getReprOld() { return reprOld; }
    public String getReprNew() { return reprNew; }
    
    /**
     * 简化构造函数，使用当前时间戳
     * 
     * @param objectName 对象名称
     * @param fieldName 字段名称
     * @param oldValue 旧值
     * @param newValue 新值
     * @param changeType 变更类型
     * @return ChangeRecord 实例
     */
    public static ChangeRecord of(String objectName, String fieldName, Object oldValue, Object newValue, ChangeType changeType) {
        return ChangeRecord.builder()
            .objectName(objectName)
            .fieldName(fieldName)
            .oldValue(oldValue)
            .newValue(newValue)
            .changeType(changeType)
            .build();
    }
}