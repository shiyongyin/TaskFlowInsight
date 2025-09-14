package com.syy.taskflowinsight.tracking.snapshot;

import java.util.Map;
import java.util.Set;

/**
 * 快照策略接口
 * 定义了快照捕获的标准契约
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public interface SnapshotStrategy {
    
    /**
     * 捕获对象快照
     * 
     * @param objectName 对象名称
     * @param target 目标对象
     * @param config 快照配置
     * @param fields 要捕获的字段（可选）
     * @return 快照结果映射
     */
    Map<String, Object> capture(String objectName, Object target, SnapshotConfig config, String... fields);
    
    /**
     * 获取策略名称
     * 
     * @return 策略名称
     */
    String getName();
    
    /**
     * 策略是否支持异步执行
     * 
     * @return true表示支持异步
     */
    default boolean supportsAsync() {
        return false;
    }
    
    /**
     * 验证配置是否有效
     * 
     * @param config 配置对象
     * @throws IllegalArgumentException 如果配置无效
     */
    default void validateConfig(SnapshotConfig config) {
        // 子类可以覆盖实现具体验证逻辑
    }
}