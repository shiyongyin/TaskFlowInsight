# TASK-017: 内存泄漏检测机制

## 背景

在多线程环境下，ThreadLocal使用不当容易引起内存泄漏。特别是在Web应用中，线程池复用导致ThreadLocal数据无法及时清理。TaskFlowInsight需要建立完整的内存泄漏检测和预防机制，确保长期运行的稳定性。

## 目标

### 主要目标
1. **ThreadLocal泄漏检测**：实时监控ThreadLocal变量的生命周期
2. **自动清理机制**：线程结束或长期不活跃时自动清理资源
3. **内存泄漏预警**：达到阈值时主动告警和处理
4. **泄漏根因分析**：提供详细的内存泄漏诊断信息

### 次要目标
1. **内存使用统计**：实时统计各组件的内存使用情况
2. **历史趋势分析**：内存使用的时间序列分析
3. **配置化阈值**：支持运行时调整检测参数

## 实现方案

### 1. 内存泄漏检测器
```java
public final class MemoryLeakDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryLeakDetector.class);
    
    // 内存监控配置
    private static final long LEAK_DETECTION_INTERVAL_MS = 60_000; // 1分钟检测一次
    private static final long INACTIVE_THRESHOLD_MS = 300_000;     // 5分钟无活动视为不活跃
    private static final long MEMORY_WARNING_THRESHOLD_MB = 10;    // 10MB告警阈值
    private static final int MAX_INACTIVE_THREADS = 100;           // 最大不活跃线程数
    
    private final ScheduledExecutorService detectionExecutor;
    private final ConcurrentHashMap<Long, ThreadMemoryInfo> threadMemoryMap;
    private final List<MemoryLeakListener> listeners;
    private final AtomicBoolean isDetectionEnabled;
    
    private void performLeakDetection() {
        if (!isDetectionEnabled.get()) {
            return;
        }
        
        try {
            long currentTime = System.currentTimeMillis();
            Set<Long> deadThreads = new HashSet<>();
            long totalMemoryUsage = 0;
            int inactiveThreadCount = 0;
            
            for (Map.Entry<Long, ThreadMemoryInfo> entry : threadMemoryMap.entrySet()) {
                Long threadId = entry.getKey();
                ThreadMemoryInfo memInfo = entry.getValue();
                
                // 检测线程是否已死亡
                if (isThreadDead(threadId)) {
                    deadThreads.add(threadId);
                    notifyDeadThreadDetected(threadId, memInfo);
                    continue;
                }
                
                // 检测长期不活跃线程
                if (currentTime - memInfo.getLastActiveTime() > INACTIVE_THRESHOLD_MS) {
                    inactiveThreadCount++;
                    if (inactiveThreadCount > MAX_INACTIVE_THREADS) {
                        notifyInactiveThreadsExceeded(threadId, memInfo);
                    }
                }
                
                totalMemoryUsage += memInfo.getMemoryUsage();
            }
            
            // 清理死亡线程的内存记录
            deadThreads.forEach(this::cleanupDeadThread);
            
            // 检测总内存使用量
            if (totalMemoryUsage > MEMORY_WARNING_THRESHOLD_MB * 1024 * 1024) {
                notifyMemoryThresholdExceeded(totalMemoryUsage, threadMemoryMap.size());
            }
            
        } catch (Exception e) {
            LOGGER.error("Error during memory leak detection", e);
        }
    }
}
```

### 2. 线程内存信息追踪
```java
public static final class ThreadMemoryInfo {
    private final long threadId;
    private final String threadName;
    private final long createdTime;
    private volatile long lastActiveTime;
    private volatile long memoryUsage;
    private volatile int sessionCount;
    private volatile int taskNodeCount;
    private final AtomicLong totalApiCalls;
    private final Set<String> activeSessions;
    
    public void updateActivity() {
        this.lastActiveTime = System.currentTimeMillis();
        this.totalApiCalls.incrementAndGet();
    }
    
    public void updateMemoryUsage(long memoryDelta) {
        this.memoryUsage += memoryDelta;
    }
    
    public void addSession(String sessionId) {
        activeSessions.add(sessionId);
        sessionCount++;
    }
    
    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
        sessionCount = Math.max(0, sessionCount - 1);
    }
}
```

### 3. 内存泄漏监听器接口
```java
public interface MemoryLeakListener {
    
    /**
     * 检测到死亡线程仍持有内存资源
     */
    void onDeadThreadDetected(MemoryLeakEvent event);
    
    /**
     * 不活跃线程数量超过阈值
     */
    void onInactiveThreadsExceeded(MemoryLeakEvent event);
    
    /**
     * 总内存使用超过阈值
     */
    void onMemoryThresholdExceeded(MemoryLeakEvent event);
    
    /**
     * 单个线程内存使用异常
     */
    void onThreadMemoryAnomaly(MemoryLeakEvent event);
}

public static final class MemoryLeakEvent {
    private final MemoryLeakType type;
    private final long threadId;
    private final String threadName;
    private final long memoryUsage;
    private final long timestamp;
    private final String details;
    
    // 构造函数和getters
}
```

### 4. 自动清理机制
```java
public final class AutoCleanupManager {
    private final WeakHashMap<Thread, ThreadContext> threadContextMap = new WeakHashMap<>();
    private final ReferenceQueue<ThreadContext> cleanupQueue = new ReferenceQueue<>();
    
    public void registerThreadContext(Thread thread, ThreadContext context) {
        synchronized (threadContextMap) {
            threadContextMap.put(thread, context);
        }
        
        // 创建弱引用以便GC时能够感知
        new ThreadContextWeakReference(context, thread.threadId(), cleanupQueue);
    }
    
    // 弱引用类用于GC时清理
    private static final class ThreadContextWeakReference extends WeakReference<ThreadContext> {
        private final long threadId;
        
        public ThreadContextWeakReference(ThreadContext referent, long threadId, ReferenceQueue<ThreadContext> queue) {
            super(referent, queue);
            this.threadId = threadId;
        }
        
        public long getThreadId() {
            return threadId;
        }
    }
    
    // 定期检查和清理
    private void processCleanupQueue() {
        ThreadContextWeakReference ref;
        while ((ref = (ThreadContextWeakReference) cleanupQueue.poll()) != null) {
            long threadId = ref.getThreadId();
            LOGGER.debug("Auto-cleaning resources for dead thread: {}", threadId);
            
            // 触发清理逻辑
            cleanupDeadThreadResources(threadId);
        }
    }
}
```

## 测试标准

### 检测精度要求
1. **死亡线程检测**：100%准确率，60秒内检出
2. **内存泄漏检测**：>95%准确率，误报率<1%
3. **清理效果验证**：清理后内存使用降低>90%
4. **性能影响**：检测开销<1%总CPU使用

### 压力测试场景
1. **大量短生命周期线程**：1000线程/秒创建销毁
2. **长期运行测试**：48小时连续运行无内存增长
3. **内存压力测试**：堆内存使用率80%下的检测准确性
4. **异常场景**：突发大量线程死亡时的处理能力

## 验收标准

### 功能验收
- [ ] ThreadLocal泄漏检测机制完整
- [ ] 自动清理机制有效工作
- [ ] 内存使用统计准确
- [ ] 泄漏事件监听器机制完善

### 性能验收
- [ ] 检测延迟<60秒
- [ ] 检测准确率>95%
- [ ] 误报率<1%
- [ ] 检测开销<1% CPU

## 依赖关系

### 前置依赖
- TASK-008: ThreadLocal内存管理
- TASK-007: ContextManager实现

### 后置依赖
- TASK-018: 并发压力测试
- TASK-019: 长期稳定性测试

## 实施计划

### 第7周（3天）
- **Day 1**: 内存泄漏检测器核心实现
- **Day 2**: 自动清理机制和监听器
- **Day 3**: 内存统计报告和测试验证

## 交付物

1. **内存泄漏检测器** (`MemoryLeakDetector.java`)
2. **自动清理管理器** (`AutoCleanupManager.java`)
3. **内存使用报告器** (`MemoryUsageReporter.java`)
4. **泄漏事件监听器** (`MemoryLeakListener.java`)
5. **检测配置类** (`LeakDetectionConfig.java`)
6. **单元测试套件** (`MemoryLeakDetectionTest.java`)
