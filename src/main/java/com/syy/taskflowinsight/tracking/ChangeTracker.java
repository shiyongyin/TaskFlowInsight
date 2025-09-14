package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * 变更追踪器
 * 使用ThreadLocal维护线程级别的对象快照和变更记录
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
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
    
    /** 最大追踪对象数（VIP-003要求） */
    private static final int MAX_TRACKED_OBJECTS = 1000;
    
    /**
     * 快照条目，包含基线快照和相关元数据
     */
    private static class SnapshotEntry {
        final String name;
        final Map<String, Object> baseline;
        final String[] fields;
        final WeakReference<Object> targetRef;
        final long timestamp;
        
        SnapshotEntry(String name, Map<String, Object> baseline, String[] fields, Object target) {
            this.name = name;
            this.baseline = baseline;
            this.fields = fields;
            this.targetRef = new WeakReference<>(target);
            this.timestamp = System.currentTimeMillis();
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
            
            // 捕获基线快照
            Map<String, Object> baseline = ObjectSnapshot.capture(name, target, fields);
            
            // 存储快照条目
            snapshots.put(name, new SnapshotEntry(name, baseline, fields, target));
            
            logger.debug("Tracked object '{}' with {} fields", name, baseline.size());
            
        } catch (Exception e) {
            logger.warn("Failed to track object '{}': {}", name, e.getMessage());
        }
    }
    
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
                Map<String, Object> currentSnapshot = ObjectSnapshot.capture(name, target, snapshotEntry.fields);
                
                // 对比差异
                List<ChangeRecord> changes = DiffDetector.diff(name, snapshotEntry.baseline, currentSnapshot);
                
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
}
