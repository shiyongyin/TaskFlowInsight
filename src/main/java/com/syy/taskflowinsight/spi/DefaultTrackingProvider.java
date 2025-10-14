package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 默认变更追踪Provider实现（兜底）
 *
 * <p>委托给现有{@link ChangeTracker}实现，priority=0（最低优先级）。
 * <p>异常安全：所有操作不抛出异常，失败时记录日志并返回安全值。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultTrackingProvider implements TrackingProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTrackingProvider.class);

    @Override
    public void track(String name, Object target, String... fields) {
        try {
            ChangeTracker.track(name, target, fields);
        } catch (Exception e) {
            logger.warn("DefaultTrackingProvider.track failed for name={}: {}",
                name, e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Track error details", e);
            }
        }
    }

    @Override
    public List<ChangeRecord> changes() {
        try {
            return ChangeTracker.getChanges();
        } catch (Exception e) {
            logger.warn("DefaultTrackingProvider.changes failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Changes retrieval error details", e);
            }
            return Collections.emptyList();
        }
    }

    @Override
    public void clear() {
        try {
            ChangeTracker.clearAllTracking();
        } catch (Exception e) {
            logger.warn("DefaultTrackingProvider.clear failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Clear error details", e);
            }
        }
    }

    @Override
    public int priority() {
        return 0; // 最低优先级（兜底实现）
    }

    @Override
    public String toString() {
        return "DefaultTrackingProvider{priority=0, type=fallback}";
    }
}
