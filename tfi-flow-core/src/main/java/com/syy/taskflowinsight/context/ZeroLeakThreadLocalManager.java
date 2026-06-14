package com.syy.taskflowinsight.context;


import com.syy.taskflowinsight.internal.FlowConfigDefaults;
import lombok.Getter;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 零泄漏ThreadLocal管理器
 * 作为防止内存泄漏的最后一道防线，提供诊断和清理功能
 * 
 * 设计原则：
 * - 多层防护：注册时检测、定期扫描、死线程清理
 * - 诊断模式：反射清理默认关闭，仅在诊断时启用
 * - 非侵入性：作为可选组件，不影响核心功能
 * - 安全优先：宁可保守也不破坏系统稳定性
 */
public final class ZeroLeakThreadLocalManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ZeroLeakThreadLocalManager.class);
    
    // 单例实例
    private static final ZeroLeakThreadLocalManager INSTANCE = new ZeroLeakThreadLocalManager();
    
    // 嵌套 stage 注册表（CT-006）
    private final ConcurrentHashMap<Long, NestedStageRegistry> nestedStageRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WeakReference<Thread>> nestedStageThreads = new ConcurrentHashMap<>();
    
    // 定时清理器（懒创建，可禁用）
    private ScheduledExecutorService cleanupScheduler;
    private volatile boolean cleanupEnabled = false; // 默认关闭
    
    // 监控指标
    private final AtomicLong registeredCount = new AtomicLong();
    private final AtomicLong unregisteredCount = new AtomicLong();
    private final AtomicLong leaksDetected = new AtomicLong();
    private final AtomicLong leaksCleaned = new AtomicLong();
    private final AtomicLong deadThreadsCleaned = new AtomicLong();
    
    // 配置
    private volatile boolean diagnosticMode = false; // 诊断模式默认关闭
    private volatile boolean reflectionCleanupEnabled = false; // 反射清理默认关闭
    private volatile long contextTimeoutMillis = 3600000; // 1小时
    private volatile long cleanupIntervalMillis = 60000; // 1分钟
    
    // 反射字段缓存
    private static Field threadLocalsField;
    private static Field tableField;
    private static Field valueField;
    private static volatile Boolean reflectionAvailable;
    
    /**
     * 私有构造函数
     */
    private ZeroLeakThreadLocalManager() {
        // 创建清理调度器
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TFI-ThreadLocalCleaner");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        
        // 使用默认配置，完全由 TfiConfig/自动配置驱动，不再读取系统属性
        // 清理任务将在 setCleanupEnabled(true) 或 setCleanupIntervalMillis(..) 时按需启动
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "TFI-ThreadLocalShutdown"));
    }
    
    /**
     * 获取单例实例
     * 
     * @return ZeroLeakThreadLocalManager实例
     */
    public static ZeroLeakThreadLocalManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册上下文
     * 
     * @param thread 线程
     * @param context 上下文对象
     */
    public void registerContext(Thread thread, Object context) {
        if (thread == null || context == null) {
            return;
        }
        
        long threadId = thread.threadId();
        registeredCount.incrementAndGet();
        
        // 诊断模式下记录详细信息
        if (diagnosticMode) {
            logger.debug("Registered context for thread {} (id={}): {}",
                thread.getName(), threadId, context.getClass().getSimpleName());
        }
    }
    
    /**
     * 注销上下文
     * 
     * @param threadId 线程ID
     */
    public void unregisterContext(long threadId) {
        // 同时清理嵌套 stage 记录（CT-006）
        NestedStageRegistry removed = nestedStageRecords.remove(threadId);
        nestedStageThreads.remove(threadId);
        unregisteredCount.incrementAndGet();

        if (diagnosticMode) {
            logger.debug("Unregistered zero-leak diagnostic state for thread id={}, nestedStages={}",
                threadId, removed != null ? removed.size() : 0);
        }
    }
    
    // ==================== 嵌套 Stage 管理（CT-006）====================
    
    /**
     * 注册嵌套stage
     * 
     * @param threadId 线程ID
     * @param stageId stage标识
     * @param depth 嵌套深度
     */
    public void registerNestedStage(long threadId, String stageId, int depth) {
        if (stageId == null || stageId.trim().isEmpty()) {
            return;
        }
        
        if (depth > FlowConfigDefaults.NESTED_STAGE_MAX_DEPTH) {
            logger.warn("Nested stage depth {} exceeds maximum {}, registration ignored", 
                depth, FlowConfigDefaults.NESTED_STAGE_MAX_DEPTH);
            return;
        }
        
        NestedStageRegistry registry = nestedStageRecords.computeIfAbsent(
            threadId, k -> new NestedStageRegistry());
        registry.addStage(stageId, depth);
        nestedStageThreads.putIfAbsent(threadId, new WeakReference<>(Thread.currentThread()));
        
        if (diagnosticMode) {
            logger.debug("Registered nested stage: thread={}, stage={}, depth={}", 
                threadId, stageId, depth);
        }
    }
    
    /**
     * 清理指定深度及以上的嵌套stage
     * 
     * @param threadId 线程ID
     * @param fromDepth 起始深度（包含）
     * @return 清理的stage数量
     */
    public int cleanupNestedStages(long threadId, int fromDepth) {
        NestedStageRegistry registry = nestedStageRecords.get(threadId);
        if (registry == null) {
            return 0;
        }
        
        int cleaned = registry.cleanupFromDepth(fromDepth);
        
        // 如果registry为空，移除整个记录
        if (registry.isEmpty()) {
            nestedStageRecords.remove(threadId);
            nestedStageThreads.remove(threadId);
        }
        
        if (cleaned > 0 && diagnosticMode) {
            logger.debug("Cleaned {} nested stages from depth {} for thread {}", 
                cleaned, fromDepth, threadId);
        }
        
        return cleaned;
    }
    
    /**
     * 批量清理嵌套stage（基于时间或数量阈值）
     * 
     * @return 清理的总数量
     */
    public int cleanupNestedStagesBatch() {
        int totalCleaned = 0;
        int batchSize = FlowConfigDefaults.NESTED_CLEANUP_BATCH_SIZE;
        long now = System.currentTimeMillis();
        
        // 收集需要清理的线程ID
        List<Long> threadsToCleanup = new ArrayList<>();
        
        nestedStageRecords.forEach((threadId, registry) -> {
            if (threadsToCleanup.size() >= batchSize) {
                return; // 达到批次限制
            }
            
            // 检查是否有过期的stage或线程已死
            Thread thread = getThreadById(threadId);
            boolean threadDead = thread == null || !thread.isAlive();
            boolean hasExpiredStages = registry.hasExpiredStages(now, contextTimeoutMillis);
            
            if (threadDead || hasExpiredStages) {
                threadsToCleanup.add(threadId);
            }
        });
        
        // 执行清理
        for (Long threadId : threadsToCleanup) {
            NestedStageRegistry registry = nestedStageRecords.remove(threadId);
            nestedStageThreads.remove(threadId);
            if (registry != null) {
                totalCleaned += registry.size();
            }
        }
        
        if (totalCleaned > 0) {
            logger.info("Batch cleanup: removed {} nested stages from {} threads", 
                totalCleaned, threadsToCleanup.size());
        }
        
        return totalCleaned;
    }
    
    /**
     * 获取嵌套stage状态
     * 
     * @param threadId 线程ID
     * @return stage状态信息
     */
    public NestedStageStatus getNestedStageStatus(long threadId) {
        NestedStageRegistry registry = nestedStageRecords.get(threadId);
        if (registry == null) {
            return new NestedStageStatus(0, 0, Collections.emptyList());
        }
        
        return new NestedStageStatus(
            registry.size(),
            registry.getMaxDepth(),
            registry.getStageIds()
        );
    }
    
    /**
     * 辅助方法：根据线程ID获取线程对象
     */
    private Thread getThreadById(long threadId) {
        WeakReference<Thread> ref = nestedStageThreads.get(threadId);
        if (ref != null) {
            return ref.get();
        }
        ManagedThreadContext current = SafeContextManager.getInstance().getCurrentContext();
        if (current != null && current.getThreadId() == threadId && current.isOwnerThreadAlive()) {
            return Thread.currentThread();
        }
        return null;
    }
    
    /**
     * 启动定期清理任务
     */
    private synchronized void startPeriodicCleanup() {
        if (!cleanupEnabled) {
            return;
        }
        if (cleanupScheduler == null || cleanupScheduler.isShutdown()) {
            cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TFI-ThreadLocalCleaner");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        cleanupScheduler.scheduleWithFixedDelay(this::detectLeaks,
            cleanupIntervalMillis, cleanupIntervalMillis, TimeUnit.MILLISECONDS);
        cleanupScheduler.scheduleWithFixedDelay(this::cleanDeadThreads,
            10000, 10000, TimeUnit.MILLISECONDS);
    }

    /**
     * 启用/禁用清理任务
     */
    public synchronized void setCleanupEnabled(boolean enabled) {
        this.cleanupEnabled = enabled;
        if (enabled) {
            startPeriodicCleanup();
        } else if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
            cleanupScheduler = null;
        }
    }

    /**
     * 设置上下文超时（毫秒）
     */
    public synchronized void setContextTimeoutMillis(long timeoutMillis) {
        this.contextTimeoutMillis = timeoutMillis;
    }

    /**
     * 设置清理调度间隔（毫秒）
     * 若清理已启用，则重启调度器以应用新的间隔
     */
    public synchronized void setCleanupIntervalMillis(long intervalMillis) {
        this.cleanupIntervalMillis = intervalMillis;
        if (cleanupEnabled) {
            if (cleanupScheduler != null) {
                cleanupScheduler.shutdownNow();
                cleanupScheduler = null;
            }
            startPeriodicCleanup();
        }
    }
    
    /**
     * 检测泄漏
     * 
     * @return 检测到的泄漏数量
     */
    public int detectLeaks() {
        long before = safeLongMetric("contexts.leaked");
        SafeContextManager.getInstance().detectAndCleanLeaks();
        long after = safeLongMetric("contexts.leaked");
        long delta = Math.max(0, after - before);
        if (delta > 0) {
            leaksDetected.addAndGet(delta);
        }
        return (int) Math.min(Integer.MAX_VALUE, delta);
    }
    
    /**
     * 清理死线程
     */
    private void cleanDeadThreads() {
        detectLeaks();
    }
    
    /**
     * 清理指定线程的上下文
     * 
     * @param threadId 线程ID
     */
    private void cleanupContext(long threadId) {
        nestedStageRecords.remove(threadId);
        nestedStageThreads.remove(threadId);

        if (diagnosticMode && reflectionCleanupEnabled && isReflectionAvailable()) {
            Thread thread = getThreadById(threadId);
            if (thread != null) {
                attemptReflectionCleanup(thread);
                leaksCleaned.incrementAndGet();
            }
        }
    }
    
    /**
     * 尝试通过反射清理ThreadLocal
     * 仅在诊断模式且明确启用时使用
     * 
     * @param thread 目标线程
     */
    private void attemptReflectionCleanup(Thread thread) {
        if (!isReflectionAvailable()) {
            return;
        }
        
        try {
            Object threadLocalMap = threadLocalsField.get(thread);
            if (threadLocalMap == null) {
                return;
            }
            
            Object[] table = (Object[]) tableField.get(threadLocalMap);
            if (table == null) {
                return;
            }
            
            int cleaned = 0;
            for (int i = 0; i < table.length; i++) {
                Object entry = table[i];
                if (entry != null) {
                    Object value = valueField.get(entry);
                    
                    // 仅清理我们的上下文相关对象
                    if (value instanceof ManagedThreadContext) {
                        table[i] = null;
                        cleaned++;
                    }
                }
            }
            
            if (cleaned > 0) {
                logger.info("Reflection cleanup: removed {} ThreadLocal entries from thread {}",
                    cleaned, thread.getName());
            }
            
        } catch (Exception e) {
            logger.warn("Reflection cleanup failed", e);
        }
    }
    
    /**
     * 启用诊断模式
     * 
     * @param enableReflection 是否同时启用反射清理
     */
    public void enableDiagnosticMode(boolean enableReflection) {
        this.diagnosticMode = true;
        boolean available = enableReflection && isReflectionAvailable();
        this.reflectionCleanupEnabled = available;
        
        if (enableReflection && !available) {
            logger.warn("Reflection cleanup requested but not available. " +
                "Add JVM flag: --add-opens java.base/java.lang=ALL-UNNAMED");
        }
        
        logger.info("Diagnostic mode enabled, reflection cleanup: {}", reflectionCleanupEnabled);
    }

    
    /**
     * 禁用诊断模式
     */
    public void disableDiagnosticMode() {
        this.diagnosticMode = false;
        this.reflectionCleanupEnabled = false;
        logger.info("Diagnostic mode disabled");
    }
    
    /**
     * 获取健康状态
     * 
     * @return 健康状态
     */
    public HealthStatus getHealthStatus() {
        int activeContexts = safeIntMetric("contexts.active");
        long totalLeaks = Math.max(leaksDetected.get(), safeLongMetric("contexts.leaked"));
        
        // 计算健康等级
        HealthLevel level;
        if (totalLeaks == 0 && activeContexts < 100) {
            level = HealthLevel.HEALTHY;
        } else if (totalLeaks < 10 && activeContexts < 500) {
            level = HealthLevel.WARNING;
        } else if (totalLeaks < 100 && activeContexts < 1000) {
            level = HealthLevel.CRITICAL;
        } else {
            level = HealthLevel.EMERGENCY;
        }
        
        return new HealthStatus(level, activeContexts, totalLeaks,
            leaksCleaned.get(), deadThreadsCleaned.get());
    }
    
    /**
     * 获取诊断信息
     * 
     * @return 诊断信息映射
     */
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();
        diagnostics.put("contexts.registered", registeredCount.get());
        diagnostics.put("contexts.unregistered", unregisteredCount.get());
        diagnostics.put("contexts.active", safeIntMetric("contexts.active"));
        diagnostics.put("leaks.detected", Math.max(leaksDetected.get(), safeLongMetric("contexts.leaked")));
        diagnostics.put("leaks.cleaned", leaksCleaned.get());
        diagnostics.put("threads.dead.cleaned", deadThreadsCleaned.get());
        diagnostics.put("diagnostic.mode", diagnosticMode);
        diagnostics.put("reflection.probed", reflectionAvailable != null);
        diagnostics.put("reflection.available", reflectionAvailable);
        diagnostics.put("reflection.enabled", reflectionCleanupEnabled);
        diagnostics.put("health.status", getHealthStatus());
        return diagnostics;
    }

    private static synchronized boolean isReflectionAvailable() {
        if (reflectionAvailable != null) {
            return reflectionAvailable;
        }
        try {
            threadLocalsField = Thread.class.getDeclaredField("threadLocals");
            threadLocalsField.setAccessible(true);

            Class<?> threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            tableField = threadLocalMapClass.getDeclaredField("table");
            tableField.setAccessible(true);

            Class<?> entryClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap$Entry");
            valueField = entryClass.getDeclaredField("value");
            valueField.setAccessible(true);

            reflectionAvailable = true;
            logger.info("Reflection for ThreadLocal cleanup is available");
        } catch (Exception e) {
            reflectionAvailable = false;
            logger.info("Reflection for ThreadLocal cleanup is not available: {}", e.getMessage());
            logger.info("This is expected in Java 9+ without --add-opens java.base/java.lang=ALL-UNNAMED");
        }
        return reflectionAvailable;
    }

    private static long safeLongMetric(String key) {
        SafeContextManager manager = SafeContextManager.getInstance();
        switch (key) {
            case "contexts.created":
                return manager.getContextCreatedCount();
            case "contexts.closed":
                return manager.getContextClosedCount();
            case "contexts.leaked":
                return manager.getLeakDetectedCount();
            case "async.tasks":
                return manager.getAsyncTaskCount();
            default:
                return 0L;
        }
    }

    private static int safeIntMetric(String key) {
        SafeContextManager manager = SafeContextManager.getInstance();
        switch (key) {
            case "contexts.active":
                return manager.getActiveContextCount();
            case "executor.poolSize":
                return manager.getAsyncExecutorPoolSize();
            case "executor.queueSize":
                return manager.getAsyncExecutorQueueSize();
            default:
                return 0;
        }
    }
    
    /**
     * 关闭管理器
     */
    private void shutdown() {
        logger.info("Shutting down ZeroLeakThreadLocalManager");
        
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
        }
        
        try {
            if (cleanupScheduler != null && !cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 最后清理
        nestedStageRecords.clear();
        nestedStageThreads.clear();
    }
    
    /**
     * 健康等级枚举
     */
    @Getter
    public enum HealthLevel {
        HEALTHY("System is healthy"),
        WARNING("Minor leaks detected"),
        CRITICAL("Significant leaks detected"),
        EMERGENCY("Severe memory leak risk");
        
        private final String description;
        
        HealthLevel(String description) {
            this.description = description;
        }

    }

    /**
     * 健康状态
     */
    @Getter
    public static class HealthStatus {
        private final HealthLevel level;
        private final int activeContexts;
        private final long totalLeaks;
        private final long leaksCleaned;
        private final long deadThreadsCleaned;

        /**
         * 构造健康状态快照。
         *
         * @param level 健康等级
         * @param activeContexts 活跃上下文数量
         * @param totalLeaks 总泄漏数
         * @param leaksCleaned 已清理泄漏数
         * @param deadThreadsCleaned 已清理死线程数
         */
        public HealthStatus(HealthLevel level, int activeContexts, long totalLeaks,
                          long leaksCleaned, long deadThreadsCleaned) {
            this.level = level;
            this.activeContexts = activeContexts;
            this.totalLeaks = totalLeaks;
            this.leaksCleaned = leaksCleaned;
            this.deadThreadsCleaned = deadThreadsCleaned;
        }

        @Override
        public String toString() {
            return String.format("HealthStatus{level=%s, active=%d, leaks=%d, cleaned=%d}",
                level, activeContexts, totalLeaks, leaksCleaned);
        }
    }
    
    // ==================== 嵌套 Stage 内部类（CT-006）====================
    
    /**
     * 嵌套Stage注册表
     */
    private static class NestedStageRegistry {
        private final ConcurrentHashMap<String, NestedStageInfo> stages = new ConcurrentHashMap<>();
        
        void addStage(String stageId, int depth) {
            stages.put(stageId, new NestedStageInfo(stageId, depth, System.currentTimeMillis()));
        }
        
        int cleanupFromDepth(int fromDepth) {
            int cleaned = 0;
            Iterator<Map.Entry<String, NestedStageInfo>> iterator = stages.entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<String, NestedStageInfo> entry = iterator.next();
                if (entry.getValue().depth >= fromDepth) {
                    iterator.remove();
                    cleaned++;
                }
            }
            return cleaned;
        }
        
        boolean hasExpiredStages(long now, long timeoutMs) {
            return stages.values().stream()
                .anyMatch(stage -> (now - stage.registeredTime) > timeoutMs);
        }
        
        boolean isEmpty() {
            return stages.isEmpty();
        }
        
        int size() {
            return stages.size();
        }
        
        int getMaxDepth() {
            return stages.values().stream()
                .mapToInt(stage -> stage.depth)
                .max()
                .orElse(0);
        }
        
        List<String> getStageIds() {
            return new ArrayList<>(stages.keySet());
        }
    }
    
    /**
     * 嵌套Stage信息
     */
    private static class NestedStageInfo {
        final String stageId;
        final int depth;
        final long registeredTime;
        
        NestedStageInfo(String stageId, int depth, long registeredTime) {
            this.stageId = stageId;
            this.depth = depth;
            this.registeredTime = registeredTime;
        }
    }
    
    /**
     * 嵌套Stage状态
     */
    @Getter
    public static class NestedStageStatus {
        private final int stageCount;
        private final int maxDepth;
        private final List<String> stageIds;

        /**
         * 构造嵌套 Stage 状态快照。
         *
         * @param stageCount Stage 数量
         * @param maxDepth 最大嵌套深度
         * @param stageIds Stage ID 列表（不可变副本）
         */
        public NestedStageStatus(int stageCount, int maxDepth, List<String> stageIds) {
            this.stageCount = stageCount;
            this.maxDepth = maxDepth;
            this.stageIds = Collections.unmodifiableList(new ArrayList<>(stageIds));
        }
        
        @Override
        public String toString() {
            return String.format("NestedStageStatus{count=%d, maxDepth=%d, stages=%s}",
                stageCount, maxDepth, stageIds);
        }
    }
}
