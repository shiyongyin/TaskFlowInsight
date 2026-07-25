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

    /**
     * 为发布文档显式声明 builder 类型；Lombok 继续注入 builder/toBuilder 的原有成员。
     */
    public static class ChangeRecordBuilder {
    }

    // Explicit getters for IDE compatibility (in case Lombok annotation processing fails)
    /** @return 变更所属对象的 process-local 名称 */
    public String getObjectName() { return objectName; }
    /** @return 发生变化的字段名称 */
    public String getFieldName() { return fieldName; }
    /** @return 调用方提供的旧值引用，可为 null */
    public Object getOldValue() { return oldValue; }
    /** @return 调用方提供的新值引用，可为 null */
    public Object getNewValue() { return newValue; }
    /** @return 记录创建时的 wall-clock epoch millis */
    public long getTimestamp() { return timestamp; }
    /** @return 可选会话标识 */
    public String getSessionId() { return sessionId; }
    /** @return 可选任务路径 */
    public String getTaskPath() { return taskPath; }
    /** @return CREATE、UPDATE 或 DELETE 变更类型 */
    public ChangeType getChangeType() { return changeType; }
    /** @return 值的完全限定 Java 类型名称 */
    public String getValueType() { return valueType; }
    /** @return 可选的值分类名称 */
    public String getValueKind() { return valueKind; }
    /** @return 已转义、截断的通用展示文本 */
    public String getValueRepr() { return valueRepr; }
    /** @return 已转义、截断的旧值展示文本 */
    public String getReprOld() { return reprOld; }
    /** @return 已转义、截断的新值展示文本 */
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
