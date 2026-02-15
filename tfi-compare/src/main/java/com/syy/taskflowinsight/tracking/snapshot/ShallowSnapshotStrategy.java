package com.syy.taskflowinsight.tracking.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 浅快照策略实现
 * 委托给现有的ObjectSnapshot实现
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component
public class ShallowSnapshotStrategy implements SnapshotStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ShallowSnapshotStrategy.class);
    
    @Override
    public Map<String, Object> capture(String objectName, Object target, SnapshotConfig config, String... fields) {
        if (target == null) {
            return Collections.emptyMap();
        }
        
        try {
            return ObjectSnapshot.capture(objectName, target, fields);
        } catch (Exception e) {
            logger.error("Shallow snapshot failed for object: {}", objectName, e);
            return Collections.emptyMap();
        }
    }
    
    @Override
    public String getName() {
        return "shallow";
    }
    
    @Override
    public void validateConfig(SnapshotConfig config) {
        // 浅快照不需要特殊配置验证
    }
}