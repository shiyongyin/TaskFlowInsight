package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;

/**
 * 测试用的ChangeRecord工厂类
 * 简化测试中ChangeRecord的创建
 */
public class TestChangeRecordFactory {

    /**
     * 创建简化版ChangeRecord（用于测试）
     */
    public static ChangeRecord create(String fieldName, Object oldValue, Object newValue, ChangeType changeType) {
        return ChangeRecord.builder()
            .objectName("TestObject")
            .fieldName(fieldName)
            .oldValue(oldValue)
            .newValue(newValue)
            .changeType(changeType)
            .valueType(newValue != null ? newValue.getClass().getName() :
                      oldValue != null ? oldValue.getClass().getName() : "unknown")
            .valueKind(determineKind(newValue != null ? newValue : oldValue))
            .sessionId("test-session")
            .taskPath("test/path")
            .valueRepr(newValue != null ? newValue.toString() :
                      oldValue != null ? oldValue.toString() : null)
            .reprOld(oldValue != null ? oldValue.toString() : null)
            .reprNew(newValue != null ? newValue.toString() : null)
            .build();
    }

    /**
     * 创建带对象名的ChangeRecord
     */
    public static ChangeRecord create(String objectName, String fieldName,
                                     Object oldValue, Object newValue, ChangeType changeType) {
        return ChangeRecord.builder()
            .objectName(objectName)
            .fieldName(fieldName)
            .oldValue(oldValue)
            .newValue(newValue)
            .changeType(changeType)
            .valueType(newValue != null ? newValue.getClass().getName() :
                      oldValue != null ? oldValue.getClass().getName() : "unknown")
            .valueKind(determineKind(newValue != null ? newValue : oldValue))
            .sessionId("test-session")
            .taskPath("test/path")
            .valueRepr(newValue != null ? newValue.toString() :
                      oldValue != null ? oldValue.toString() : null)
            .reprOld(oldValue != null ? oldValue.toString() : null)
            .reprNew(newValue != null ? newValue.toString() : null)
            .build();
    }

    private static String determineKind(Object value) {
        if (value == null) return "NULL";
        if (value instanceof String) return "STRING";
        if (value instanceof Number) return "NUMBER";
        if (value instanceof Boolean) return "BOOLEAN";
        return "OBJECT";
    }
}