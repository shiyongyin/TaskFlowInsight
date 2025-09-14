package com.syy.taskflowinsight.tracking;

/**
 * 变更类型枚举
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
public enum ChangeType {
    /** 创建：字段从null变为有值 */
    CREATE,
    /** 更新：字段值发生变化 */
    UPDATE,
    /** 删除：字段从有值变为null */
    DELETE
}