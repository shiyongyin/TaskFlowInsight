package com.syy.taskflowinsight.tracking.compare;

/**
 * 补丁格式枚举
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public enum PatchFormat {
    /**
     * JSON Patch格式 (RFC 6902)
     */
    JSON_PATCH,
    
    /**
     * JSON Merge Patch格式 (RFC 7396)
     */
    MERGE_PATCH,
    
    /**
     * 自定义格式
     */
    CUSTOM
}