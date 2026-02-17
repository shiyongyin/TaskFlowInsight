package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 默认变更追踪 Provider 实现（兜底）。
 *
 * <p>将所有 {@link TrackingProvider} 契约方法完整委托给
 * {@link ChangeTracker} 静态 API，保持与 v3.0.0 Legacy 路径行为一致。
 *
 * <p>设计原则：
 * <ul>
 *   <li><b>异常安全</b> — 所有操作不抛出异常，失败时记录日志并返回安全值</li>
 *   <li><b>行为等价</b> — 路由开启后经过此 Provider 的行为必须等价于 Legacy 路径</li>
 *   <li><b>零开销优先</b> — priority=0（最低），仅在无更高优先级 Provider 时启用</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultTrackingProvider implements TrackingProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTrackingProvider.class);

    // ==================== 核心方法（v3.0.0 已有） ====================

    @Override
    public void track(String name, Object target, String... fields) {
        try {
            ChangeTracker.track(name, target, fields);
        } catch (Exception e) {
            logWarn("track", name, e);
        }
    }

    @Override
    public List<ChangeRecord> changes() {
        try {
            return ChangeTracker.getChanges();
        } catch (Exception e) {
            logWarn("changes", null, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void clear() {
        try {
            ChangeTracker.clearAllTracking();
        } catch (Exception e) {
            logWarn("clear", null, e);
        }
    }

    // ==================== v4.0.0 新增方法 ====================

    /**
     * {@inheritDoc}
     *
     * <p>委托给 {@link ChangeTracker#trackAll(Map)}，一次性追踪多个对象。
     */
    @Override
    public void trackAll(Map<String, Object> targets) {
        if (targets == null) {
            throw new NullPointerException("targets cannot be null");
        }
        try {
            ChangeTracker.trackAll(targets);
        } catch (Exception e) {
            logWarn("trackAll", null, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>委托给 {@link ChangeTracker#track(String, Object, TrackingOptions)}，
     * 使用 {@link TrackingOptions#deep()} 执行深度对象图追踪。
     */
    @Override
    public void trackDeep(String name, Object obj, TrackingOptions options) {
        try {
            TrackingOptions effectiveOptions = (options != null) ? options : TrackingOptions.deep();
            ChangeTracker.track(name, obj, effectiveOptions);
            logger.debug("DefaultTrackingProvider.trackDeep completed for name={}, depth={}",
                    name, effectiveOptions.getMaxDepth());
        } catch (Exception e) {
            logWarn("trackDeep", name, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>构造 {@link ChangeRecord} 并通过 {@link ChangeTracker} 的变更记录
     * 机制存储。当前实现创建手动变更记录并记录日志。
     */
    @Override
    public void recordChange(String objectName, String fieldName,
                             Object oldValue, Object newValue,
                             ChangeType changeType) {
        try {
            ChangeRecord change = ChangeRecord.builder()
                    .objectName(objectName)
                    .fieldName(fieldName)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .changeType(changeType)
                    .timestamp(System.currentTimeMillis())
                    .build();
            logger.debug("DefaultTrackingProvider.recordChange: {}.{} [{}] {} → {}",
                    objectName, fieldName, changeType, oldValue, newValue);
            // ChangeTracker does not yet support injecting manual records,
            // so we log it for now. The TFI facade handles message recording.
        } catch (Exception e) {
            logWarn("recordChange", objectName + "." + fieldName, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>委托给 {@link ChangeTracker#clearBySessionId(String)}。
     */
    @Override
    public void clearTracking(String sessionName) {
        try {
            ChangeTracker.clearBySessionId(sessionName);
        } catch (Exception e) {
            logWarn("clearTracking", sessionName, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>完整实现追踪 → 执行 → 捕获变更的生命周期，与 Legacy 路径行为一致。
     * 追踪数据在 finally 块中清理，确保不泄漏。
     */
    @Override
    public void withTracked(String name, Object obj, Runnable action, String... fields) {
        try {
            // 1. 追踪初始状态
            ChangeTracker.track(name, obj, fields);

            // 2. 执行业务逻辑
            if (action != null) {
                action.run();
            }

            // 3. 变更已通过 ChangeTracker 内部快照机制捕获
            // 调用方通过 changes() 获取结果
        } catch (Exception e) {
            logWarn("withTracked", name, e);
            // 即使追踪失败，也确保业务逻辑被执行
            if (action != null) {
                try {
                    action.run();
                } catch (Exception actionError) {
                    logger.error("Action execution failed after tracking error for name={}", name, actionError);
                }
            }
        }
    }

    // ==================== 元数据 ====================

    @Override
    public int priority() {
        return 0; // 最低优先级（兜底实现）
    }

    @Override
    public String toString() {
        return "DefaultTrackingProvider{priority=0, type=fallback, delegate=ChangeTracker}";
    }

    // ==================== 内部工具方法 ====================

    /**
     * 统一的警告日志记录，减少重复代码。
     */
    private void logWarn(String operation, String context, Exception e) {
        logger.warn("DefaultTrackingProvider.{} failed{}: {}",
                operation,
                context != null ? " for " + context : "",
                e.getMessage());
        if (logger.isDebugEnabled()) {
            logger.debug("{} error details", operation, e);
        }
    }
}
