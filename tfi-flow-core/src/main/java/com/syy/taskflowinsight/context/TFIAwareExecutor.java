package com.syy.taskflowinsight.context;

import java.util.concurrent.*;

/**
 * TaskFlowInsight感知的线程池执行器
 * 自动处理上下文传播的ExecutorService装饰器
 * 
 * 设计原则：
 * - 装饰器模式：包装现有ExecutorService
 * - 透明传播：自动捕获和恢复上下文
 * - 零侵入：不修改业务代码
 * - 性能优化：仅在有上下文时进行传播
 */
public class TFIAwareExecutor implements ExecutorService {
    
    private final ExecutorService delegate;
    private final SafeContextManager contextManager;
    
    /**
     * 创建TFI感知的执行器
     * 
     * @param delegate 被装饰的执行器
     */
    public TFIAwareExecutor(ExecutorService delegate) {
        this.delegate = delegate;
        this.contextManager = SafeContextManager.getInstance();
    }
    
    /**
     * 创建默认的TFI感知线程池
     * 
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 线程存活时间
     * @param unit 时间单位
     * @return TFI感知的线程池
     */
    public static TFIAwareExecutor newThreadPool(int corePoolSize, int maximumPoolSize, 
                                                long keepAliveTime, TimeUnit unit) {
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            corePoolSize, maximumPoolSize, keepAliveTime, unit,
            new LinkedBlockingQueue<>(),
            new TFIThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return new TFIAwareExecutor(threadPool);
    }
    
    /**
     * 创建固定大小的TFI感知线程池
     * 
     * @param nThreads 线程数
     * @return TFI感知的线程池
     */
    public static TFIAwareExecutor newFixedThreadPool(int nThreads) {
        return new TFIAwareExecutor(Executors.newFixedThreadPool(nThreads, new TFIThreadFactory()));
    }
    
    @Override
    public void execute(Runnable command) {
        delegate.execute(contextManager.wrapRunnable(command));
    }
    
    @Override
    public void shutdown() {
        delegate.shutdown();
    }
    
    @Override
    public java.util.List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }
    
    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }
    
    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }
    
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
    
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(contextManager.wrapCallable(task));
    }
    
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(contextManager.wrapRunnable(task), result);
    }
    
    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(contextManager.wrapRunnable(task));
    }
    
    @Override
    public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) 
            throws InterruptedException {
        return delegate.invokeAll(wrapCallables(tasks));
    }
    
    @Override
    public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, 
                                                  long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(wrapCallables(tasks), timeout, unit);
    }
    
    @Override
    public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) 
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapCallables(tasks));
    }
    
    @Override
    public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) 
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapCallables(tasks), timeout, unit);
    }
    
    /**
     * 包装Callable集合
     */
    private <T> java.util.Collection<? extends Callable<T>> wrapCallables(
            java.util.Collection<? extends Callable<T>> tasks) {
        return tasks.stream()
                   .map(contextManager::wrapCallable)
                   .toList();
    }
    
    /**
     * TFI专用线程工厂
     */
    private static class TFIThreadFactory implements ThreadFactory {
        private static final java.util.concurrent.atomic.AtomicLong COUNTER =
            new java.util.concurrent.atomic.AtomicLong();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "TFI-Pool-" + COUNTER.incrementAndGet());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
