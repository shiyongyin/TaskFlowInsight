package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.monitoring.DegradationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * 变更追踪器
 * 使用 {@link ThreadLocal} 维护线程级别的对象快照和变更记录。
 * 
 * <p><b>线程安全：</b>所有公开的静态方法均为线程安全，每个线程拥有独立的快照存储。
 * 不同线程的追踪数据完全隔离，无需额外同步。</p>
 * 
 * <p><b>清理要求：</b>在线程池或长时间存活的线程中使用时，<strong>必须</strong>在任务结束后
 * 调用 {@link #clearAllTracking()} 以释放 ThreadLocal 资源，防止内存泄漏。
 * 推荐在 {@code try-finally} 块中调用：</p>
 * <pre>{@code
 * try {
 *     ChangeTracker.track("order", order, "status", "amount");
 *     // ... business logic ...
 *     List<ChangeRecord> changes = ChangeTracker.getChanges();
 * } finally {
 *     ChangeTracker.clearAllTracking();
 * }
 * }</pre>
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 * @see #clearAllTracking()
 */
public final class ChangeTracker {
    
    /**
     * 变更追踪异常
     */
    public static class TrackingException extends RuntimeException {
        public TrackingException(String message) {
            super(message);
        }
        
        public TrackingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(ChangeTracker.class);
    
    /** 最大追踪对象数（VIP-003要求，可通过 -Dtfi.change-tracking.max-tracked-objects 覆盖） */
    private static volatile int MAX_TRACKED_OBJECTS =
        Integer.getInteger("tfi.change-tracking.max-tracked-objects", 1000);
    
    /**
     * 快照条目，包含基线快照和相关元数据
     */
    private static class SnapshotEntry {
        final String name;
        final Map<String, Object> baseline;
        final String[] fields;
        final WeakReference<Object> targetRef;
        final long timestamp;
        final TrackingOptions options;
        
        SnapshotEntry(String name, Map<String, Object> baseline, String[] fields, Object target) {
            this.name = name;
            this.baseline = baseline;
            this.fields = fields;
            this.targetRef = new WeakReference<>(target);
            this.timestamp = System.currentTimeMillis();
            this.options = null; // 兼容现有调用
        }
        
        SnapshotEntry(String name, Map<String, Object> baseline, String[] fields, Object target, TrackingOptions options) {
            this.name = name;
            this.baseline = baseline;
            this.fields = fields;
            this.targetRef = new WeakReference<>(target);
            this.timestamp = System.currentTimeMillis();
            this.options = options;
        }
    }
    
    /** 线程本地的快照存储 - 使用LinkedHashMap保持插入顺序 */
    private static final ThreadLocal<Map<String, SnapshotEntry>> THREAD_SNAPSHOTS = 
        ThreadLocal.withInitial(LinkedHashMap::new);
    
    private ChangeTracker() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * 追踪对象的指定字段
     * 
     * @param name 对象名称（用于标识）
     * @param target 目标对象
     * @param fields 要追踪的字段名，如果为空则追踪所有标量字段
     */
    public static void track(String name, Object target, String... fields) {
        if (name == null || target == null) {
            logger.debug("Skip tracking: name={}, target={}", name, target);
            return;
        }
        
        // 检查降级状态：如果完全禁用，直接返回
        if (DegradationContext.isDisabled()) {
            logger.debug("Tracking disabled due to degradation level: {}", DegradationContext.getCurrentLevel());
            return;
        }
        
        long startTime = System.nanoTime(); // 性能监控埋点
        try {
            Map<String, SnapshotEntry> snapshots = THREAD_SNAPSHOTS.get();
            
            // 检查是否超过最大对象数限制
            if (snapshots.size() >= MAX_TRACKED_OBJECTS && !snapshots.containsKey(name)) {
                // 删除最早的条目（LinkedHashMap保持插入顺序）
                Iterator<Map.Entry<String, SnapshotEntry>> iterator = snapshots.entrySet().iterator();
                if (iterator.hasNext()) {
                    Map.Entry<String, SnapshotEntry> oldest = iterator.next();
                    iterator.remove();
                    logger.warn("Reached max tracked objects limit ({}), removed oldest: {}", 
                        MAX_TRACKED_OBJECTS, oldest.getKey());
                }
            }
            
            // 同名覆盖警告
            if (snapshots.containsKey(name)) {
                logger.debug("Overwriting existing tracked object: {}", name);
            }
            
            // 捕获基线快照（统一通过 SnapshotProvider，以便后续 A/B）
            Map<String, Object> baseline = com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
                .captureBaseline(name, target, fields);
            
            // 存储快照条目
            snapshots.put(name, new SnapshotEntry(name, baseline, fields, target));
            
            logger.debug("Tracked object '{}' with {} fields", name, baseline.size());
            
        } catch (Exception e) {
            logger.warn("Failed to track object '{}': {}", name, e.getMessage());
        } finally {
            // 记录操作性能（如果启用了监控）
            DegradationContext.recordOperationIfEnabled("track", startTime);
        }
    }
    
    /**
     * 使用配置选项追踪对象
     * 
     * @param name 对象名称（用于标识）
     * @param target 目标对象
     * @param options 追踪配置选项
     */
    public static void track(String name, Object target, TrackingOptions options) {
        if (name == null || target == null || options == null) {
            logger.debug("Skip tracking: name={}, target={}, options={}", name, target, options);
            return;
        }
        
        try {
            Map<String, SnapshotEntry> snapshots = THREAD_SNAPSHOTS.get();
            
            // 检查是否超过最大对象数限制
            if (snapshots.size() >= MAX_TRACKED_OBJECTS && !snapshots.containsKey(name)) {
                // 删除最早的条目（LinkedHashMap保持插入顺序）
                Iterator<Map.Entry<String, SnapshotEntry>> iterator = snapshots.entrySet().iterator();
                if (iterator.hasNext()) {
                    Map.Entry<String, SnapshotEntry> oldest = iterator.next();
                    iterator.remove();
                    logger.warn("Reached max tracked objects limit ({}), removed oldest: {}", 
                        MAX_TRACKED_OBJECTS, oldest.getKey());
                }
            }
            
            // 同名覆盖警告
            if (snapshots.containsKey(name)) {
                logger.debug("Overwriting existing tracked object: {}", name);
            }
            
            // 根据配置选择快照策略（统一通过 SnapshotProvider）
            Map<String, Object> baseline = com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
                .captureWithOptions(name, target, options);
            
            // 存储快照条目
            snapshots.put(name, new SnapshotEntry(name, baseline, new String[0], target, options));
            
            logger.debug("Tracked object '{}' with {} fields using {}", name, baseline.size(), options.getDepth());
            
        } catch (Exception e) {
            logger.warn("Failed to track object '{}': {}", name, e.getMessage());
        }
    }
    
    // 统一由 SnapshotProvider 承担捕获逻辑
    
    /**
     * 批量追踪多个对象
     * 
     * @param targets 对象名称到对象的映射
     */
    public static void trackAll(Map<String, Object> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        
        for (Map.Entry<String, Object> entry : targets.entrySet()) {
            track(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * 获取所有追踪对象的变更记录
     * 捕获当前快照，与基线对比，返回增量变更，并更新基线
     * 
     * @return 变更记录列表
     */
    public static List<ChangeRecord> getChanges() {
        // 检查降级状态：如果完全禁用，返回空列表
        if (DegradationContext.isDisabled()) {
            logger.debug("getChanges() disabled due to degradation level: {}", DegradationContext.getCurrentLevel());
            return Collections.emptyList();
        }
        
        long startTime = System.nanoTime(); // 性能监控埋点
        
        Map<String, SnapshotEntry> snapshots = THREAD_SNAPSHOTS.get();
        
        if (snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<ChangeRecord> allChanges = new ArrayList<>();
        
        // 遍历所有追踪的对象
        Iterator<Map.Entry<String, SnapshotEntry>> iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SnapshotEntry> entry = iterator.next();
            String name = entry.getKey();
            SnapshotEntry snapshotEntry = entry.getValue();
            
            try {
                // 获取目标对象
                Object target = snapshotEntry.targetRef.get();
                if (target == null) {
                    // 对象已被GC回收
                    logger.debug("Tracked object '{}' has been garbage collected", name);
                    iterator.remove();
                    continue;
                }
                
                // 捕获当前快照
                Map<String, Object> currentSnapshot;
                if (snapshotEntry.options != null) {
                    // 使用配置选项
                    currentSnapshot = com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
                        .captureWithOptions(name, target, snapshotEntry.options);
                } else {
                    // 使用传统方式
                    currentSnapshot = com.syy.taskflowinsight.tracking.snapshot.SnapshotProviders.get()
                        .captureBaseline(name, target, snapshotEntry.fields);
                }
                
                // 为字段级注解提供类上下文（使 Detector 可解析字段注解）
                com.syy.taskflowinsight.tracking.detector.DiffDetector.setCurrentObjectClass(target.getClass());
                // 对比差异（统一入口门面）
                List<ChangeRecord> changes = com.syy.taskflowinsight.tracking.detector.DiffFacade
                    .diff(name, snapshotEntry.baseline, currentSnapshot);
                
                if (!changes.isEmpty()) {
                    allChanges.addAll(changes);
                    
                    // 更新基线为当前快照
                    snapshotEntry.baseline.clear();
                    snapshotEntry.baseline.putAll(currentSnapshot);
                    
                    logger.debug("Detected {} changes for object '{}'", changes.size(), name);
                }
                
            } catch (Exception e) {
                logger.debug("Failed to get changes for object '{}': {}", name, e.getMessage());
            }
        }
        
        // 记录操作性能
        DegradationContext.recordOperationIfEnabled("getChanges", startTime);
        
        return allChanges;
    }
    
    /**
     * 清理当前线程的所有追踪数据
     * 三处清理统一，必须幂等（VIP-003要求）
     */
    public static void clearAllTracking() {
        try {
            Map<String, SnapshotEntry> snapshots = THREAD_SNAPSHOTS.get();
            int count = snapshots.size();
            
            // 清理Map内容
            if (!snapshots.isEmpty()) {
                snapshots.clear();
            }
            
            // 彻底移除ThreadLocal，避免在线程池中残留Map对象
            THREAD_SNAPSHOTS.remove();
            
            if (count > 0) {
                logger.debug("Cleared {} tracked objects from thread {}", 
                    count, Thread.currentThread().getName());
            }
        } catch (Exception e) {
            // 清理失败不应该影响主流程
            logger.warn("Failed to clear tracking: {}", e.getMessage());
        } finally {
            // 清理降级上下文
            DegradationContext.clear();
        }
    }
    
    /**
     * 获取当前追踪的对象数量（用于测试和监控）
     * 
     * @return 追踪的对象数量
     */
    public static int getTrackedCount() {
        Map<String, SnapshotEntry> snapshots = THREAD_SNAPSHOTS.get();
        return snapshots != null ? snapshots.size() : 0;
    }
    
    /**
     * 获取最大追踪对象数限制
     * 
     * @return 最大追踪对象数
     */
    public static int getMaxTrackedObjects() {
        return MAX_TRACKED_OBJECTS;
    }
    
    /**
     * 清理特定会话的追踪数据
     * 
     * @param sessionId 会话ID
     */
    public static void clearBySessionId(String sessionId) {
        // MVP阶段暂不实现会话级别清理
        // Phase 2+功能：需要维护会话ID到追踪对象的映射
        logger.debug("Session-based clearing not yet implemented for session: {}", sessionId);
        // 临时处理：清理所有追踪数据
        clearAllTracking();
    }
    
    /**
     * 记录操作性能（内部方法，用于监控埋点）
     * 只有在启用监控时才会记录，确保零开销
     */
    private static void recordOperationPerformance(String operationType, long startTimeNanos) {
        try {
            // 通过Spring容器查找性能监控器（如果存在且启用）
            // 这样避免了强依赖，保持ChangeTracker的独立性
            long durationNanos = System.nanoTime() - startTimeNanos;
            long durationMs = durationNanos / 1_000_000;
            
            // 只在Debug级别记录详细性能信息
            if (logger.isDebugEnabled() && durationMs > 100) { // 超过100ms才记录
                logger.debug("Operation '{}' took {}ms", operationType, durationMs);
            }
            
            // PerformanceMonitor 集成点：如需精细化监控，可在此处扩展
            // 当前通过 logger.debug 提供基本性能观测
            
        } catch (Exception e) {
            // 监控失败不应影响主业务逻辑
            logger.trace("Failed to record operation performance: {}", e.getMessage());
        }
    }
}
