package com.syy.taskflowinsight.context;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 跨线程传播 TFI Context 的唯一 {@link ExecutorService} 装饰器。
 *
 * <p>线程池的容量、队列与拒绝策略由调用方决定，本类只负责完整转发 ExecutorService 合约，
 * 并把任务统一交给 {@link SafeContextManager} 包装。这样 ContextScope 的恢复、异常和清理语义只有
 * 一个实现位置，不会因不同线程池工厂再次漂移。
 *
 * <p>该类型不创建线程池，也不保存任务级可变状态，线程安全能力与 delegate 一致。所有生命周期方法
 * 直接转发，因此关闭 wrapper 会关闭传入的 delegate；调用方应在不再共享该线程池时才关闭 wrapper。
 *
 * @author TaskFlow Insight Team
 * @version 4.0.0
 * @since 2025-01-13
 */
public class ContextPropagatingExecutor implements ExecutorService {
    
    private final ExecutorService delegate;
    private final SafeContextManager contextManager;
    
    private ContextPropagatingExecutor(ExecutorService delegate) {
        this.delegate = delegate;
        this.contextManager = SafeContextManager.getInstance();
    }
    
    /**
     * 把调用方拥有的线程池包装为 Context 传播执行器。
     *
     * <p>重复包装返回原实例，避免叠加两层 ContextScope；关闭返回值会关闭传入线程池。
     *
     * @param executor 非空、由调用方选择策略的底层执行器
     * @return 支持 TFI Context 传播的执行器；已包装时返回同一实例
     * @throws IllegalArgumentException executor 为 null 时抛出
     */
    public static ExecutorService wrap(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }
        if (executor instanceof ContextPropagatingExecutor) {
            return executor;
        }
        return new ContextPropagatingExecutor(executor);
    }
    
    private Runnable wrapTask(Runnable task) {
        return contextManager.wrapRunnable(task);
    }

    private <T> Callable<T> wrapTask(Callable<T> task) {
        return contextManager.wrapCallable(task);
    }
    
    @Override
    public void shutdown() {
        delegate.shutdown();
    }
    
    @Override
    public List<Runnable> shutdownNow() {
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
        return delegate.submit(wrapTask(task));
    }
    
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrapTask(task), result);
    }
    
    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrapTask(task));
    }
    
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) 
            throws InterruptedException {
        List<Callable<T>> wrappedTasks = tasks.stream()
            .map(this::wrapTask)
            .collect(Collectors.toList());
        return delegate.invokeAll(wrappedTasks);
    }
    
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, 
                                          long timeout, TimeUnit unit) 
            throws InterruptedException {
        List<Callable<T>> wrappedTasks = tasks.stream()
            .map(this::wrapTask)
            .collect(Collectors.toList());
        return delegate.invokeAll(wrappedTasks, timeout, unit);
    }
    
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) 
            throws InterruptedException, ExecutionException {
        List<Callable<T>> wrappedTasks = tasks.stream()
            .map(this::wrapTask)
            .collect(Collectors.toList());
        return delegate.invokeAny(wrappedTasks);
    }
    
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, 
                            long timeout, TimeUnit unit) 
            throws InterruptedException, ExecutionException, TimeoutException {
        List<Callable<T>> wrappedTasks = tasks.stream()
            .map(this::wrapTask)
            .collect(Collectors.toList());
        return delegate.invokeAny(wrappedTasks, timeout, unit);
    }
    
    @Override
    public void execute(Runnable command) {
        delegate.execute(wrapTask(command));
    }
}
