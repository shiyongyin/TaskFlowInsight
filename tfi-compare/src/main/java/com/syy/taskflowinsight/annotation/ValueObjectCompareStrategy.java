package com.syy.taskflowinsight.annotation;

import lombok.Getter;

/**
 * ValueObject比较策略枚举
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@Getter
public enum ValueObjectCompareStrategy {
    
    /** 自动策略 - 根据对象类型选择合适的比较方式（默认解析为FIELDS） */
    AUTO("Auto"),
    
    /** equals比较 - 使用对象的equals方法 */
    EQUALS("Equals"),
    
    /** 字段比较 - 逐个比较对象的字段值 */
    FIELDS("Fields");
    
    private final String displayName;
    
    ValueObjectCompareStrategy(String displayName) {
        this.displayName = displayName;
    }

}