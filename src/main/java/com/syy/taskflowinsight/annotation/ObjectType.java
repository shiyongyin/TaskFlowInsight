package com.syy.taskflowinsight.annotation;

import lombok.Getter;

/**
 * 对象类型枚举
 * 定义系统支持的四种对象类型
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Getter
public enum ObjectType {
    
    /** 实体对象 - 有唯一标识的业务对象 */
    ENTITY("Entity"),
    
    /** 值对象 - 无唯一标识的不可变对象 */
    VALUE_OBJECT("ValueObject"),
    
    /** 基本类型 - 包装类和基本类型 */
    BASIC_TYPE("BasicType"),
    
    /** 集合类型 - 集合、数组等 */
    COLLECTION("Collection");
    
    private final String displayName;
    
    ObjectType(String displayName) {
        this.displayName = displayName;
    }

}