package com.syy.taskflowinsight.tracking;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 会话感知的变更追踪器
 * 支持会话级别的变更管理和查询
 * 
 * 核心功能：
 * - 会话级别的变更隔离
 * - 按会话ID查询和清理
 * - 变更历史持久化
 * - 内存优化的LRU缓存
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class SessionAwareChangeTracker {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionAwareChangeTracker.class);
    
    /**
     * 会话变更存储
     * sessionId -> List<ChangeRecord>
     */
    private static final Map<String, List<ChangeRecord>> SESSION_CHANGES = new ConcurrentHashMap<>();
    
    /**
     * 会话元数据
     * sessionId -> SessionMetadata
     */
    private static final Map<String, SessionMetadata> SESSION_METADATA = new ConcurrentHashMap<>();
    
    /**
     * 全局变更历史（用于持久化）
     */
    private static final List<ChangeRecord> GLOBAL_HISTORY = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 读写锁，优化并发性能
     */
    private static final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    /**
     * 最大会话数限制
     */
    private static final int MAX_SESSIONS = 1000;
    
    /**
     * 最大历史记录数
     */
    private static final int MAX_HISTORY_SIZE = 10000;
    
    /**
     * 记录变更到当前会话
     * 
     * @param change 变更记录
     */
    public static void recordChange(ChangeRecord change) {
        String sessionId = getCurrentSessionId();
        if (sessionId == null) {
            logger.debug("No active session, change not recorded");
            return;
        }
        
        rwLock.writeLock().lock();
        try {
            // 添加到会话变更列表
            SESSION_CHANGES.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(change);
            
            // 更新会话元数据
            SessionMetadata metadata = SESSION_METADATA.computeIfAbsent(sessionId, SessionMetadata::new);
            metadata.incrementChangeCount();
            metadata.updateLastActivity();
            
            // 添加到全局历史
            GLOBAL_HISTORY.add(change);
            
            // 检查限制
            enforeLimits();
            
            logger.debug("Recorded change for session {}: {}.{}", 
                sessionId, change.getObjectName(), change.getFieldName());
                
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取当前会话的变更
     * 
     * @return 变更列表
     */
    public static List<ChangeRecord> getCurrentSessionChanges() {
        String sessionId = getCurrentSessionId();
        if (sessionId == null) {
            return Collections.emptyList();
        }
        
        return getSessionChanges(sessionId);
    }
    
    /**
     * 获取指定会话的变更
     * 
     * @param sessionId 会话ID
     * @return 变更列表
     */
    public static List<ChangeRecord> getSessionChanges(String sessionId) {
        rwLock.readLock().lock();
        try {
            List<ChangeRecord> changes = SESSION_CHANGES.get(sessionId);
            return changes != null ? new ArrayList<>(changes) : Collections.emptyList();
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有活跃会话的变更
     * 
     * @return 所有变更
     */
    public static List<ChangeRecord> getAllChanges() {
        rwLock.readLock().lock();
        try {
            return SESSION_CHANGES.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 清理指定会话的变更
     * 
     * @param sessionId 会话ID
     * @return 清理的变更数量
     */
    public static int clearSession(String sessionId) {
        rwLock.writeLock().lock();
        try {
            List<ChangeRecord> removed = SESSION_CHANGES.remove(sessionId);
            SESSION_METADATA.remove(sessionId);
            
            int count = removed != null ? removed.size() : 0;
            if (count > 0) {
                logger.info("Cleared {} changes for session {}", count, sessionId);
            }
            
            return count;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 清理所有会话
     */
    public static void clearAll() {
        rwLock.writeLock().lock();
        try {
            int sessionCount = SESSION_CHANGES.size();
            int changeCount = getAllChanges().size();
            
            SESSION_CHANGES.clear();
            SESSION_METADATA.clear();
            GLOBAL_HISTORY.clear();
            
            logger.info("Cleared all tracking data: {} sessions, {} changes", 
                sessionCount, changeCount);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取会话统计信息
     * 
     * @param sessionId 会话ID
     * @return 会话元数据
     */
    public static SessionMetadata getSessionMetadata(String sessionId) {
        rwLock.readLock().lock();
        try {
            return SESSION_METADATA.get(sessionId);
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有会话的统计信息
     * 
     * @return 会话统计Map
     */
    public static Map<String, SessionMetadata> getAllSessionMetadata() {
        rwLock.readLock().lock();
        try {
            return new HashMap<>(SESSION_METADATA);
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 清理过期会话
     * 
     * @param maxAgeMillis 最大年龄（毫秒）
     * @return 清理的会话数
     */
    public static int cleanupExpiredSessions(long maxAgeMillis) {
        rwLock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            List<String> toRemove = new ArrayList<>();
            
            for (Map.Entry<String, SessionMetadata> entry : SESSION_METADATA.entrySet()) {
                if (now - entry.getValue().getLastActivityTime() > maxAgeMillis) {
                    toRemove.add(entry.getKey());
                }
            }
            
            for (String sessionId : toRemove) {
                clearSession(sessionId);
            }
            
            if (!toRemove.isEmpty()) {
                logger.info("Cleaned up {} expired sessions", toRemove.size());
            }
            
            return toRemove.size();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取当前会话ID
     */
    private static String getCurrentSessionId() {
        ManagedThreadContext context = ManagedThreadContext.current();
        if (context != null && context.getCurrentSession() != null) {
            return context.getCurrentSession().getSessionId();
        }
        return null;
    }
    
    /**
     * 强制执行限制
     */
    private static void enforeLimits() {
        // 限制会话数
        if (SESSION_CHANGES.size() > MAX_SESSIONS) {
            // 移除最老的会话
            String oldestSession = SESSION_METADATA.entrySet().stream()
                .min(Map.Entry.comparingByValue(
                    Comparator.comparing(SessionMetadata::getCreatedTime)))
                .map(Map.Entry::getKey)
                .orElse(null);
            
            if (oldestSession != null) {
                clearSession(oldestSession);
                logger.warn("Removed oldest session {} due to limit", oldestSession);
            }
        }
        
        // 限制历史大小
        if (GLOBAL_HISTORY.size() > MAX_HISTORY_SIZE) {
            int toRemove = GLOBAL_HISTORY.size() - MAX_HISTORY_SIZE;
            GLOBAL_HISTORY.subList(0, toRemove).clear();
            logger.debug("Trimmed {} old history records", toRemove);
        }
    }
    
    /**
     * 会话元数据
     */
    public static class SessionMetadata {
        private final String sessionId;
        private final long createdTime;
        private long lastActivityTime;
        private int changeCount;
        private final Map<String, Integer> objectChangeCounts;
        
        public SessionMetadata(String sessionId) {
            this.sessionId = sessionId;
            this.createdTime = System.currentTimeMillis();
            this.lastActivityTime = createdTime;
            this.changeCount = 0;
            this.objectChangeCounts = new ConcurrentHashMap<>();
        }
        
        public void incrementChangeCount() {
            this.changeCount++;
        }
        
        public void updateLastActivity() {
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public void recordObjectChange(String objectName) {
            objectChangeCounts.merge(objectName, 1, Integer::sum);
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public long getCreatedTime() { return createdTime; }
        public long getLastActivityTime() { return lastActivityTime; }
        public int getChangeCount() { return changeCount; }
        public Map<String, Integer> getObjectChangeCounts() { 
            return new HashMap<>(objectChangeCounts); 
        }
        
        public long getAge() {
            return System.currentTimeMillis() - createdTime;
        }
        
        public long getIdleTime() {
            return System.currentTimeMillis() - lastActivityTime;
        }
    }
}