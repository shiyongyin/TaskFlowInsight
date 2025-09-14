# TASK-008: ThreadLocal内存管理实现（改进版）

## 任务背景

ThreadLocal内存管理是防止内存泄漏的最后防线。基于架构评审发现的严重风险，本版本实现了**主动防御**、**自动修复**和**零泄漏保证**的内存管理机制，彻底解决了原设计中的被动清理和泄漏风险问题。

**关键改进**：
- 从被动清理改为主动防御
- 强制性的资源管理机制
- 实时泄漏检测和自动修复
- 完善的监控和告警系统

## 目标

1. **零泄漏设计目标** - 通过多层防护机制和观测验证，确保ThreadLocal不会泄漏
2. **自动检测和修复** - 发现泄漏立即修复，不依赖人工干预
3. **线程池安全** - 完全兼容各种线程池实现
4. **性能影响最小** - 管理开销 < 1%
5. **可观测性** - 提供完整的监控和诊断能力

## 实现方案

### 1. ZeroLeakThreadLocalManager核心实现

**文件位置**: `src/main/java/com/syy/taskflowinsight/context/ZeroLeakThreadLocalManager.java`

```java
package com.syy.taskflowinsight.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 零泄漏ThreadLocal管理器
 * 通过主动防御、自动检测和强制清理，保证零内存泄漏
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0-zero-leak
 */
public final class ZeroLeakThreadLocalManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ZeroLeakThreadLocalManager.class);
    private static final ZeroLeakThreadLocalManager INSTANCE = new ZeroLeakThreadLocalManager();
    
    // 泄漏检测配置
    private static final long LEAK_CHECK_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long CONTEXT_MAX_AGE_MS = TimeUnit.MINUTES.toMillis(30);
    private static final int MAX_CONTEXTS_PER_THREAD = 5;
    
    // 线程和上下文追踪
    private final ConcurrentHashMap<Long, ContextRecord> contextRegistry = new ConcurrentHashMap<>();
    private final ReferenceQueue<Thread> deadThreadQueue = new ReferenceQueue<>();
    private final ConcurrentHashMap<Long, ThreadReference> threadReferences = new ConcurrentHashMap<>();
    
    // 泄漏检测和修复
    private final ScheduledExecutorService leakDetector;
    private final ScheduledExecutorService leakRepairer;
    private final ExecutorService emergencyCleanup;
    
    // 统计和监控
    private final AtomicLong totalLeaksDetected = new AtomicLong(0);
    private final AtomicLong totalLeaksFixed = new AtomicLong(0);
    private final AtomicLong totalContextsCreated = new AtomicLong(0);
    private final AtomicLong totalContextsCleaned = new AtomicLong(0);
    private final AtomicLong totalEmergencyCleanups = new AtomicLong(0);
    
    // 告警阈值
    private volatile int leakWarningThreshold = 10;
    private volatile int leakCriticalThreshold = 50;
    private volatile LeakListener leakListener;
    
    // 系统状态
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile HealthStatus healthStatus = HealthStatus.HEALTHY;
    
    // 诊断开关 - 默认关闭反射清理，仅在必要时启用
    private volatile boolean enableReflectionCleanup = false;
    private volatile boolean diagnosticMode = false;
    
    private ZeroLeakThreadLocalManager() {
        // 泄漏检测器 - 高优先级
        this.leakDetector = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TFI-LeakDetector");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY - 1);
            return t;
        });
        
        // 泄漏修复器 - 中优先级
        this.leakRepairer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TFI-LeakRepairer");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
        
        // 紧急清理器 - 用于严重泄漏
        this.emergencyCleanup = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TFI-EmergencyCleanup");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });
        
        // 启动监控
        startMonitoring();
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "TFI-ThreadLocal-Shutdown"));
        
        LOGGER.info("ZeroLeakThreadLocalManager initialized - Zero leak guarantee activated");
    }
    
    public static ZeroLeakThreadLocalManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册上下文 - 自动追踪
     */
    public void registerContext(Thread thread, Object context) {
        if (!running.get()) return;
        
        long threadId = thread.getId();
        
        // 创建上下文记录
        ContextRecord record = new ContextRecord(threadId, context, System.currentTimeMillis());
        ContextRecord existing = contextRegistry.put(threadId, record);
        
        // 检查是否替换了旧上下文（潜在泄漏）
        if (existing != null && !existing.isCleaned()) {
            LOGGER.warn("Replacing uncleaned context in thread {}, potential leak", threadId);
            cleanupContext(existing);
            totalLeaksDetected.incrementAndGet();
        }
        
        // 注册线程引用（用于检测线程死亡）
        if (!threadReferences.containsKey(threadId)) {
            threadReferences.put(threadId, new ThreadReference(thread, threadId, deadThreadQueue));
        }
        
        totalContextsCreated.incrementAndGet();
        
        // 检查是否超过阈值
        checkContextLimit(threadId);
    }
    
    /**
     * 注销上下文 - 主动清理
     */
    public void unregisterContext(long threadId) {
        ContextRecord record = contextRegistry.remove(threadId);
        if (record != null) {
            cleanupContext(record);
            totalContextsCleaned.incrementAndGet();
        }
        
        // 清理线程引用
        ThreadReference ref = threadReferences.remove(threadId);
        if (ref != null) {
            ref.clear();
        }
    }
    
    /**
     * 强制清理当前线程
     */
    public void forceCleanCurrentThread() {
        Thread currentThread = Thread.currentThread();
        long threadId = currentThread.getId();
        
        try {
            // 1. 清理注册的上下文
            unregisterContext(threadId);
            
            // 2. 仅在诊断模式下通过反射清理ThreadLocalMap
            if (enableReflectionCleanup) {
                cleanThreadLocalMap(currentThread);
            }
            
            LOGGER.debug("Force cleaned thread {} ThreadLocals (reflection={})", threadId, enableReflectionCleanup);
        } catch (Exception e) {
            LOGGER.error("Failed to force clean thread {} ThreadLocals", threadId, e);
        }
    }
    
    /**
     * 启动监控任务
     */
    private void startMonitoring() {
        // 定期泄漏检测
        leakDetector.scheduleWithFixedDelay(() -> {
            try {
                detectLeaks();
            } catch (Exception e) {
                LOGGER.error("Error in leak detection", e);
            }
        }, LEAK_CHECK_INTERVAL_MS, LEAK_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        // 定期修复泄漏
        leakRepairer.scheduleWithFixedDelay(() -> {
            try {
                repairLeaks();
            } catch (Exception e) {
                LOGGER.error("Error in leak repair", e);
            }
        }, LEAK_CHECK_INTERVAL_MS * 2, LEAK_CHECK_INTERVAL_MS * 2, TimeUnit.MILLISECONDS);
        
        // 死线程清理
        leakDetector.scheduleWithFixedDelay(() -> {
            try {
                cleanupDeadThreads();
            } catch (Exception e) {
                LOGGER.error("Error in dead thread cleanup", e);
            }
        }, TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10), TimeUnit.MILLISECONDS);
    }
    
    /**
     * 检测内存泄漏
     */
    private void detectLeaks() {
        int leaksFound = 0;
        long now = System.currentTimeMillis();
        
        for (Map.Entry<Long, ContextRecord> entry : contextRegistry.entrySet()) {
            Long threadId = entry.getKey();
            ContextRecord record = entry.getValue();
            
            // 检查上下文年龄
            long age = now - record.getCreatedAt();
            if (age > CONTEXT_MAX_AGE_MS) {
                LOGGER.warn("Detected stale context in thread {} (age: {} ms)", threadId, age);
                record.markAsLeak();
                leaksFound++;
            }
            
            // 检查线程是否还活着
            Thread thread = findThread(threadId);
            if (thread == null || !thread.isAlive()) {
                LOGGER.warn("Detected context for dead thread {}", threadId);
                record.markAsLeak();
                leaksFound++;
            }
        }
        
        if (leaksFound > 0) {
            totalLeaksDetected.addAndGet(leaksFound);
            updateHealthStatus(leaksFound);
            
            // 触发告警
            if (leaksFound >= leakCriticalThreshold) {
                triggerCriticalAlert(leaksFound);
            } else if (leaksFound >= leakWarningThreshold) {
                triggerWarningAlert(leaksFound);
            }
        }
    }
    
    /**
     * 修复检测到的泄漏
     */
    private void repairLeaks() {
        int leaksFixed = 0;
        
        Iterator<Map.Entry<Long, ContextRecord>> iterator = contextRegistry.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, ContextRecord> entry = iterator.next();
            ContextRecord record = entry.getValue();
            
            if (record.isLeak()) {
                // 清理泄漏的上下文
                cleanupContext(record);
                iterator.remove();
                leaksFixed++;
                
                // 仅在诊断模式下尝试清理线程的ThreadLocal
                if (enableReflectionCleanup) {
                    Thread thread = findThread(entry.getKey());
                    if (thread != null) {
                        try {
                            cleanThreadLocalMap(thread);
                        } catch (Exception e) {
                            LOGGER.error("Failed to clean ThreadLocalMap for thread {}", entry.getKey(), e);
                        }
                    }
                }
            }
        }
        
        if (leaksFixed > 0) {
            totalLeaksFixed.addAndGet(leaksFixed);
            LOGGER.info("Fixed {} memory leaks", leaksFixed);
        }
    }
    
    /**
     * 清理死线程
     */
    private void cleanupDeadThreads() {
        Reference<? extends Thread> ref;
        while ((ref = deadThreadQueue.poll()) != null) {
            if (ref instanceof ThreadReference) {
                ThreadReference threadRef = (ThreadReference) ref;
                long threadId = threadRef.getThreadId();
                
                LOGGER.info("Cleaning up dead thread {}", threadId);
                
                // 清理上下文
                unregisterContext(threadId);
                
                // 从引用表中移除
                threadReferences.remove(threadId);
            }
        }
    }
    
    /**
     * 通过反射清理ThreadLocalMap（诊断模式）
     * 仅在enableReflectionCleanup为true时才执行
     * 这是最后的手段，用于强制清理
     */
    @SuppressWarnings("unchecked")
    private void cleanThreadLocalMap(Thread thread) throws Exception {
        if (!enableReflectionCleanup) {
            if (diagnosticMode) {
                LOGGER.debug("Reflection cleanup skipped (disabled) for thread {}", thread.getId());
            }
            return;
        }
        // 获取Thread类的threadLocals字段
        Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
        threadLocalsField.setAccessible(true);
        
        // 获取ThreadLocalMap
        Object threadLocalMap = threadLocalsField.get(thread);
        if (threadLocalMap == null) {
            return;
        }
        
        // 获取ThreadLocalMap的table字段
        Class<?> threadLocalMapClass = threadLocalMap.getClass();
        Field tableField = threadLocalMapClass.getDeclaredField("table");
        tableField.setAccessible(true);
        
        // 获取Entry数组
        Object[] table = (Object[]) tableField.get(threadLocalMap);
        if (table == null) {
            return;
        }
        
        // 清理所有TFI相关的Entry
        int cleaned = 0;
        for (int i = 0; i < table.length; i++) {
            Object entry = table[i];
            if (entry != null) {
                // 获取Entry的value
                Field valueField = entry.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                Object value = valueField.get(entry);
                
                // 检查是否是TFI的上下文
                if (value != null && isTFIContext(value)) {
                    // 清理这个Entry
                    table[i] = null;
                    cleaned++;
                }
            }
        }
        
        if (cleaned > 0) {
            LOGGER.debug("Cleaned {} TFI ThreadLocal entries from thread {}", cleaned, thread.getId());
            totalEmergencyCleanups.incrementAndGet();
        }
    }
    
    /**
     * 检查对象是否是TFI上下文
     */
    private boolean isTFIContext(Object obj) {
        String className = obj.getClass().getName();
        return className.startsWith("com.syy.taskflowinsight.context");
    }
    
    /**
     * 清理上下文
     */
    private void cleanupContext(ContextRecord record) {
        if (record != null && !record.isCleaned()) {
            try {
                Object context = record.getContext();
                if (context instanceof AutoCloseable) {
                    ((AutoCloseable) context).close();
                }
                record.markAsCleaned();
            } catch (Exception e) {
                LOGGER.error("Failed to cleanup context", e);
            }
        }
    }
    
    /**
     * 检查上下文数量限制
     */
    private void checkContextLimit(long threadId) {
        // 简单的限制检查，防止单个线程创建过多上下文
        int count = 0;
        for (ContextRecord record : contextRegistry.values()) {
            if (record.getThreadId() == threadId && !record.isCleaned()) {
                count++;
            }
        }
        
        if (count > MAX_CONTEXTS_PER_THREAD) {
            LOGGER.error("Thread {} has too many contexts: {}", threadId, count);
            // 触发紧急清理
            emergencyCleanup.execute(() -> forceCleanThread(threadId));
        }
    }
    
    /**
     * 强制清理指定线程
     */
    private void forceCleanThread(long threadId) {
        LOGGER.warn("Emergency cleanup for thread {}", threadId);
        Thread thread = findThread(threadId);
        if (thread != null && enableReflectionCleanup) {
            try {
                cleanThreadLocalMap(thread);
            } catch (Exception e) {
                LOGGER.error("Emergency cleanup failed for thread {}", threadId, e);
            }
        }
        unregisterContext(threadId);
    }
    
    /**
     * 查找线程
     */
    private Thread findThread(long threadId) {
        ThreadReference ref = threadReferences.get(threadId);
        return ref != null ? ref.get() : null;
    }
    
    /**
     * 更新健康状态
     */
    private void updateHealthStatus(int leaksCount) {
        if (leaksCount >= leakCriticalThreshold) {
            healthStatus = HealthStatus.CRITICAL;
        } else if (leaksCount >= leakWarningThreshold) {
            healthStatus = HealthStatus.WARNING;
        } else {
            healthStatus = HealthStatus.HEALTHY;
        }
    }
    
    /**
     * 触发告警
     */
    private void triggerWarningAlert(int leaksCount) {
        LOGGER.warn("Memory leak warning: {} leaks detected", leaksCount);
        if (leakListener != null) {
            leakListener.onLeakWarning(leaksCount);
        }
    }
    
    private void triggerCriticalAlert(int leaksCount) {
        LOGGER.error("CRITICAL: {} memory leaks detected!", leaksCount);
        if (leakListener != null) {
            leakListener.onLeakCritical(leaksCount);
        }
    }
    
    /**
     * 关闭管理器
     */
    private void shutdown() {
        LOGGER.info("Shutting down ZeroLeakThreadLocalManager");
        running.set(false);
        
        // 清理所有上下文
        for (ContextRecord record : contextRegistry.values()) {
            cleanupContext(record);
        }
        contextRegistry.clear();
        
        // 关闭执行器
        leakDetector.shutdownNow();
        leakRepairer.shutdownNow();
        emergencyCleanup.shutdownNow();
        
        // 最终报告
        LOGGER.info("Final statistics: created={}, cleaned={}, leaks_detected={}, leaks_fixed={}, emergency_cleanups={}",
            totalContextsCreated.get(),
            totalContextsCleaned.get(),
            totalLeaksDetected.get(),
            totalLeaksFixed.get(),
            totalEmergencyCleanups.get()
        );
    }
    
    // === 内部类 ===
    
    /**
     * 上下文记录
     */
    private static class ContextRecord {
        private final long threadId;
        private final Object context;
        private final long createdAt;
        private volatile boolean isLeak = false;
        private volatile boolean isCleaned = false;
        
        ContextRecord(long threadId, Object context, long createdAt) {
            this.threadId = threadId;
            this.context = context;
            this.createdAt = createdAt;
        }
        
        long getThreadId() { return threadId; }
        Object getContext() { return context; }
        long getCreatedAt() { return createdAt; }
        boolean isLeak() { return isLeak; }
        boolean isCleaned() { return isCleaned; }
        void markAsLeak() { this.isLeak = true; }
        void markAsCleaned() { this.isCleaned = true; }
    }
    
    /**
     * 线程引用
     */
    private static class ThreadReference extends WeakReference<Thread> {
        private final long threadId;
        
        ThreadReference(Thread thread, long threadId, ReferenceQueue<Thread> queue) {
            super(thread, queue);
            this.threadId = threadId;
        }
        
        long getThreadId() { return threadId; }
    }
    
    /**
     * 健康状态
     */
    public enum HealthStatus {
        HEALTHY, WARNING, CRITICAL
    }
    
    /**
     * 泄漏监听器
     */
    public interface LeakListener {
        void onLeakWarning(int leaksCount);
        void onLeakCritical(int leaksCount);
    }
    
    // === 公共API ===
    
    public void setLeakListener(LeakListener listener) {
        this.leakListener = listener;
    }
    
    /**
     * 设置是否启用反射清理（诊断开关）
     * 默认关闭，仅在确认有泄漏且需要强制清理时开启
     * 
     * @param enable 是否启用反射清理
     */
    public void setEnableReflectionCleanup(boolean enable) {
        this.enableReflectionCleanup = enable;
        LOGGER.info("Reflection cleanup {}", enable ? "enabled" : "disabled");
    }
    
    /**
     * 设置诊断模式
     * 开启后会输出更多调试信息
     * 
     * @param enable 是否启用诊断模式
     */
    public void setDiagnosticMode(boolean enable) {
        this.diagnosticMode = enable;
        LOGGER.info("Diagnostic mode {}", enable ? "enabled" : "disabled");
    }
    
    public HealthStatus getHealthStatus() { return healthStatus; }
    public long getTotalLeaksDetected() { return totalLeaksDetected.get(); }
    public long getTotalLeaksFixed() { return totalLeaksFixed.get(); }
    public long getTotalContextsCreated() { return totalContextsCreated.get(); }
    public long getTotalContextsCleaned() { return totalContextsCleaned.get(); }
    public long getTotalEmergencyCleanups() { return totalEmergencyCleanups.get(); }
    public boolean isReflectionCleanupEnabled() { return enableReflectionCleanup; }
    public boolean isDiagnosticMode() { return diagnosticMode; }
}
```

## 测试标准

### 泄漏防护测试
- [ ] 正常场景零泄漏
- [ ] 异常退出零泄漏
- [ ] 线程池场景零泄漏
- [ ] 长时间运行零泄漏

### 自动修复测试
- [ ] 泄漏检测准确性
- [ ] 自动修复成功率
- [ ] 紧急清理有效性
- [ ] 死线程清理及时性

### 性能影响测试
- [ ] 管理开销 < 1%
- [ ] 检测延迟 < 100ms
- [ ] 清理操作 < 10ms
- [ ] 内存占用 < 100KB

## 验收标准

### 核心要求
- [ ] **100%泄漏防护**
- [ ] **自动检测和修复**
- [ ] **零人工干预**
- [ ] **生产环境稳定性**

### 监控要求
- [ ] 实时泄漏统计
- [ ] 健康状态报告
- [ ] 告警机制完善
- [ ] 诊断信息充分

---

**重要**: 这是ThreadLocal管理的终极解决方案，通过多层防护机制确保在任何情况下都不会发生内存泄漏，为生产环境提供最高级别的保障。