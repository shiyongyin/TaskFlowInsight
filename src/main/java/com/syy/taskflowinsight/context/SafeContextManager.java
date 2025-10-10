package com.syy.taskflowinsight.context;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全上下文管理器
 * 全局单例，负责上下文生命周期管理、泄漏检测和异步传播
 * 
 * 设计原则：
 * - 单例模式：全局唯一实例
 * - 线程安全：所有方法线程安全
 * - 零泄漏：主动检测和清理泄漏的上下文
 * - 异步支持：提供安全的异步任务执行
 * - 监控指标：提供健康状态和性能指标
 */
public final class SafeContextManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SafeContextManager.class);
    
    // 单例实例
    private static final SafeContextManager INSTANCE = new SafeContextManager();
    
    // 线程本地存储（不使用InheritableThreadLocal）
    private static final ThreadLocal<ManagedThreadContext> CONTEXT_LOCAL = new ThreadLocal<>();
    
    // 全局上下文注册表
    private final ConcurrentHashMap<Long, ManagedThreadContext> activeContexts = new ConcurrentHashMap<>();
    
    // 异步执行器
    private final ThreadPoolExecutor asyncExecutor;
    
    // 泄漏检测定时器（懒创建）
    private ScheduledExecutorService leakDetector;
    
    // 监控指标
    private final AtomicLong contextCreatedCount = new AtomicLong();
    private final AtomicLong contextClosedCount = new AtomicLong();
    private final AtomicLong leakDetectedCount = new AtomicLong();
    private final AtomicLong asyncTaskCount = new AtomicLong();
    
    // 泄漏监听器
    private final List<LeakListener> leakListeners = new CopyOnWriteArrayList<>();
    
    // 配置
    private volatile long contextTimeoutMillis = 3600000; // 1小时
    private volatile boolean leakDetectionEnabled = false; // 默认关闭，由配置开启
    private volatile long leakDetectionIntervalMillis = 60000; // 1分钟
    
    /**
     * 私有构造函数
     */
    private SafeContextManager() {
        // 创建异步执行器
        this.asyncExecutor = new ThreadPoolExecutor(
            10, // 核心线程数
            50, // 最大线程数
            60L, TimeUnit.SECONDS, // 空闲线程存活时间
            new LinkedBlockingQueue<>(1000), // 任务队列
            new ThreadFactory() {
                private final AtomicLong counter = new AtomicLong();
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "TFI-Async-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );
        
        // 使用默认配置，通过configureFromTfiConfig()方法统一配置
        // 已移除系统属性读取，完全由TfiConfig管理配置
        logger.debug("SafeContextManager initialized with default configuration: " +
            "contextTimeout={}ms, leakDetectionEnabled={}, leakDetectionInterval={}ms",
            this.contextTimeoutMillis, this.leakDetectionEnabled, this.leakDetectionIntervalMillis);
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "TFI-Shutdown"));
    }
    
    /**
     * 获取单例实例
     * 
     * @return SafeContextManager实例
     */
    public static SafeContextManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 从TfiConfig配置上下文管理器
     * 该方法会覆盖系统属性配置，优先使用TfiConfig
     * 
     * @param config TFI配置对象
     */
    public void configureFromTfiConfig(com.syy.taskflowinsight.config.TfiConfig config) {
        if (config == null || config.context() == null) {
            logger.debug("No context configuration provided, using defaults");
            return;
        }
        
        // 更新配置
        this.contextTimeoutMillis = config.context().maxAgeMillis();
        this.leakDetectionEnabled = config.context().leakDetectionEnabled();
        this.leakDetectionIntervalMillis = config.context().leakDetectionIntervalMillis();
        
        logger.info("SafeContextManager configured from TfiConfig: " +
            "contextTimeout={}ms, leakDetectionEnabled={}, leakDetectionInterval={}ms",
            this.contextTimeoutMillis, this.leakDetectionEnabled, this.leakDetectionIntervalMillis);
        
        // 根据新配置启动或停止泄漏检测
        if (this.leakDetectionEnabled && this.leakDetector == null) {
            startLeakDetection();
        } else if (!this.leakDetectionEnabled && this.leakDetector != null) {
            stopLeakDetection();
        }
    }
    
    /**
     * 获取或创建当前线程的上下文
     * 
     * @return 当前线程的上下文
     */
    public ManagedThreadContext getCurrentContext() {
        ManagedThreadContext context = CONTEXT_LOCAL.get();
        
        // 验证上下文有效性
        if (context != null && context.isClosed()) {
            CONTEXT_LOCAL.remove();
            activeContexts.remove(context.getThreadId());
            return null;
        }
        
        return context;
    }
    
    /**
     * 注册上下文到当前线程
     * 
     * @param context 要注册的上下文
     */
    public void registerContext(ManagedThreadContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        
        long threadId = Thread.currentThread().threadId();
        if (threadId != context.getThreadId()) {
            throw new IllegalStateException("Context thread mismatch");
        }
        
        // 清理旧上下文
        ManagedThreadContext old = CONTEXT_LOCAL.get();
        if (old != null && !old.isClosed()) {
            logger.warn("Replacing unclosed context: {}", old.getContextId());
            old.close();
        }
        
        // 注册新上下文
        CONTEXT_LOCAL.set(context);
        activeContexts.put(threadId, context);
        contextCreatedCount.incrementAndGet();
    }
    
    /**
     * 注销上下文
     * 
     * @param context 要注销的上下文
     */
    public void unregisterContext(ManagedThreadContext context) {
        if (context == null) {
            return;
        }
        
        ManagedThreadContext current = CONTEXT_LOCAL.get();
        if (current == context) {
            CONTEXT_LOCAL.remove();
        }
        
        activeContexts.remove(context.getThreadId());
        contextClosedCount.incrementAndGet();
    }
    
    /**
     * 异步执行任务，自动传播上下文
     * 
     * @param taskName 任务名称
     * @param task 要执行的任务
     * @return 任务Future
     */
    public CompletableFuture<Void> executeAsync(String taskName, Runnable task) {
        return executeAsync(taskName, () -> {
            task.run();
            return null;
        });
    }
    
    /**
     * 异步执行任务，自动传播上下文
     * 
     * @param taskName 任务名称
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return 任务Future
     */
    public <T> CompletableFuture<T> executeAsync(String taskName, Callable<T> task) {
        // 捕获当前上下文快照
        ManagedThreadContext currentContext = getCurrentContext();
        ContextSnapshot snapshot = currentContext != null ? currentContext.createSnapshot() : null;
        
        asyncTaskCount.incrementAndGet();
        
        // 创建CompletableFuture
        CompletableFuture<T> future = new CompletableFuture<>();
        
        // 提交任务到线程池
        asyncExecutor.execute(() -> {
            ManagedThreadContext restoredContext = null;
            try {
                // 恢复上下文
                if (snapshot != null) {
                    restoredContext = snapshot.restore();
                    // 不需要再次启动会话，restore()已经处理了
                }
                
                // 执行任务
                T result = task.call();
                future.complete(result);
                
            } catch (Exception e) {
                logger.error("Async task failed: {}", taskName, e);
                future.completeExceptionally(e);
            } finally {
                // 清理上下文
                if (restoredContext != null) {
                    restoredContext.close();
                }
            }
        });
        
        return future;
    }
    
    /**
     * 装饰Runnable以传播上下文
     * 
     * @param runnable 原始任务
     * @return 装饰后的任务
     */
    public Runnable wrapRunnable(Runnable runnable) {
        ContextSnapshot snapshot = null;
        ManagedThreadContext current = getCurrentContext();
        if (current != null) {
            snapshot = current.createSnapshot();
        }
        
        return new ContextAwareRunnable(runnable, snapshot);
    }
    
    /**
     * 装饰Callable以传播上下文
     * 
     * @param callable 原始任务
     * @param <T> 返回类型
     * @return 装饰后的任务
     */
    public <T> Callable<T> wrapCallable(Callable<T> callable) {
        ContextSnapshot snapshot = null;
        ManagedThreadContext current = getCurrentContext();
        if (current != null) {
            snapshot = current.createSnapshot();
        }
        
        return new ContextAwareCallable<>(callable, snapshot);
    }
    
    /**
     * 启动泄漏检测
     */
    private synchronized void startLeakDetection() {
        if (!leakDetectionEnabled) return;
        if (leakDetector == null || leakDetector.isShutdown()) {
            leakDetector = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TFI-LeakDetector");
                t.setDaemon(true);
                return t;
            });
        }
        leakDetector.scheduleWithFixedDelay(() -> {
            try {
                detectAndCleanLeaks();
            } catch (Exception e) {
                logger.error("Leak detection failed", e);
            }
        }, leakDetectionIntervalMillis, leakDetectionIntervalMillis, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 停止泄漏检测
     */
    private synchronized void stopLeakDetection() {
        if (leakDetector != null && !leakDetector.isShutdown()) {
            logger.info("Stopping leak detection");
            leakDetector.shutdown();
            try {
                if (!leakDetector.awaitTermination(5, TimeUnit.SECONDS)) {
                    leakDetector.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                leakDetector.shutdownNow();
            }
        }
    }
    
    /**
     * 检测并清理泄漏的上下文
     * 对测试可见，用于清理测试环境
     */
    void detectAndCleanLeaks() {
        long now = System.currentTimeMillis();
        List<ManagedThreadContext> leakedContexts = new ArrayList<>();
        
        activeContexts.forEach((threadId, context) -> {
            // 检查线程是否存活
            boolean threadAlive = isThreadAlive(threadId);
            
            // 检查上下文超时
            // 注意：使用纳秒时间计算，但转换为毫秒进行比较
            long contextAgeNanos = System.nanoTime() - context.getCreatedNanos();
            long contextAge = contextAgeNanos / 1_000_000; // 转换为毫秒
            boolean timeout = contextAge > contextTimeoutMillis;
            
            if (!threadAlive || timeout) {
                leakedContexts.add(context);
                
                String reason = !threadAlive ? "dead thread" : "timeout";
                logger.warn("Detected leaked context: {}, reason: {}, age: {}ms",
                    context.getContextId(), reason, contextAge);
                
                leakDetectedCount.incrementAndGet();
            }
        });
        
        // 清理泄漏的上下文
        for (ManagedThreadContext leaked : leakedContexts) {
            try {
                // 先从注册表移除，避免重复清理
                activeContexts.remove(leaked.getThreadId());
                
                // 然后关闭上下文
                if (!leaked.isClosed()) {
                    leaked.close();
                }
            } catch (Exception e) {
                logger.warn("Failed to close leaked context: {}", leaked.getContextId(), e);
            }
            
            // 通知监听器
            notifyLeakListeners(leaked);
        }
    }
    
    /**
     * 检查线程是否存活
     * 
     * @param threadId 线程ID
     * @return true如果线程存活
     */
    private boolean isThreadAlive(long threadId) {
        Thread[] threads = new Thread[Thread.activeCount() * 2];
        int count = Thread.enumerate(threads);
        
        for (int i = 0; i < count; i++) {
            if (threads[i] != null && threads[i].threadId() == threadId) {
                return threads[i].isAlive();
            }
        }
        
        return false;
    }
    
    /**
     * 通知泄漏监听器
     * 
     * @param context 泄漏的上下文
     */
    private void notifyLeakListeners(ManagedThreadContext context) {
        for (LeakListener listener : leakListeners) {
            try {
                listener.onLeakDetected(context);
            } catch (Exception e) {
                logger.warn("Leak listener failed", e);
            }
        }
    }
    
    /**
     * 注册泄漏监听器
     * 
     * @param listener 监听器
     */
    public void registerLeakListener(LeakListener listener) {
        if (listener != null) {
            leakListeners.add(listener);
        }
    }
    
    /**
     * 移除泄漏监听器
     * 
     * @param listener 监听器
     */
    public void unregisterLeakListener(LeakListener listener) {
        leakListeners.remove(listener);
    }
    
    /**
     * 获取活动上下文数量
     *
     * @return 活动上下文数量
     */
    public int getActiveContextCount() {
        return activeContexts.size();
    }

    /**
     * 清理所有活动上下文（仅供测试使用）
     * @deprecated 仅供测试使用，生产环境不应调用
     */
    @Deprecated
    void clearAllContextsForTesting() {
        activeContexts.values().forEach(context -> {
            try {
                if (!context.isClosed()) {
                    context.close();
                }
            } catch (Exception e) {
                logger.debug("Failed to close context during test cleanup: {}", e.getMessage());
            }
        });
        activeContexts.clear();
        CONTEXT_LOCAL.remove();
    }
    
    /**
     * 获取监控指标
     * 
     * @return 监控指标映射
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("contexts.created", contextCreatedCount.get());
        metrics.put("contexts.closed", contextClosedCount.get());
        metrics.put("contexts.active", activeContexts.size());
        metrics.put("contexts.leaked", leakDetectedCount.get());
        metrics.put("async.tasks", asyncTaskCount.get());
        metrics.put("executor.poolSize", asyncExecutor.getPoolSize());
        metrics.put("executor.queueSize", asyncExecutor.getQueue().size());
        return metrics;
    }
    
    /**
     * 关闭管理器
     */
    private void shutdown() {
        logger.info("Shutting down SafeContextManager");
        
        // 关闭所有活动上下文
        activeContexts.values().forEach(context -> {
            try {
                context.close();
            } catch (Exception e) {
                logger.warn("Failed to close context during shutdown", e);
            }
        });
        
        // 关闭执行器
        asyncExecutor.shutdown();
        if (leakDetector != null) {
            leakDetector.shutdown();
        }
        
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
            if (leakDetector != null && !leakDetector.awaitTermination(10, TimeUnit.SECONDS)) {
                leakDetector.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // 配置方法
    
    /**
     * 从TfiConfig应用配置（首选路径）
     * 系统属性读取作为兼容性保持，后续版本将移除
     * 
     * @param timeoutMillis 上下文超时时间
     * @param leakDetectionEnabled 是否启用泄漏检测
     * @param intervalMillis 泄漏检测间隔
     */
    public void applyTfiConfig(long timeoutMillis, boolean leakDetectionEnabled, long intervalMillis) {
        setContextTimeoutMillis(timeoutMillis);
        setLeakDetectionEnabled(leakDetectionEnabled);
        setLeakDetectionIntervalMillis(intervalMillis);
    }
    
    public void setContextTimeoutMillis(long timeoutMillis) {
        this.contextTimeoutMillis = timeoutMillis;
    }
    
    public synchronized void setLeakDetectionEnabled(boolean enabled) {
        this.leakDetectionEnabled = enabled;
        if (enabled) {
            startLeakDetection();
        } else {
            if (leakDetector != null) {
                leakDetector.shutdownNow();
                leakDetector = null;
            }
        }
    }
    
    public synchronized void setLeakDetectionIntervalMillis(long intervalMillis) {
        this.leakDetectionIntervalMillis = intervalMillis;
        if (leakDetectionEnabled) {
            if (leakDetector != null) {
                leakDetector.shutdownNow();
                leakDetector = null;
            }
            startLeakDetection();
        }
    }
    
    /**
     * 泄漏监听器接口
     */
    public interface LeakListener {
        void onLeakDetected(ManagedThreadContext context);
    }
    
    /**
     * 上下文感知的Runnable包装器
     */
    private static class ContextAwareRunnable implements Runnable {
        private final Runnable delegate;
        private final ContextSnapshot snapshot;
        
        ContextAwareRunnable(Runnable delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }
        
        @Override
        public void run() {
            ManagedThreadContext context = null;
            try {
                if (snapshot != null) {
                    context = snapshot.restore();
                }
                delegate.run();
            } finally {
                if (context != null) {
                    context.close();
                }
            }
        }
    }
    
    /**
     * 上下文感知的Callable包装器
     */
    private static class ContextAwareCallable<T> implements Callable<T> {
        private final Callable<T> delegate;
        private final ContextSnapshot snapshot;
        
        ContextAwareCallable(Callable<T> delegate, ContextSnapshot snapshot) {
            this.delegate = delegate;
            this.snapshot = snapshot;
        }
        
        @Override
        public T call() throws Exception {
            ManagedThreadContext context = null;
            try {
                if (snapshot != null) {
                    context = snapshot.restore();
                }
                return delegate.call();
            } finally {
                if (context != null) {
                    context.close();
                }
            }
        }
    }
}
