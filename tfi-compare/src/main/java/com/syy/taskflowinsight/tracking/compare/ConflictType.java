package com.syy.taskflowinsight.tracking.compare;

/**
 * 冲突类型枚举
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public enum ConflictType {
    /**
     * 值冲突：同一字段被修改为不同的值
     */
    VALUE_CONFLICT,
    
    /**
     * 类型冲突：同一字段被修改为不同的类型
     */
    TYPE_CONFLICT,
    
    /**
     * 删除冲突：一边删除，一边修改
     */
    DELETE_CONFLICT,
    
    /**
     * 添加冲突：同时添加同名字段
     */
    ADD_CONFLICT,
    
    /**
     * 结构冲突：对象结构不兼容
     */
    STRUCTURE_CONFLICT
}