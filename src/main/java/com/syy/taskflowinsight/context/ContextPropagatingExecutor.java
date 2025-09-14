package com.syy.taskflowinsight.context;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 上下文传播执行器
 * 包装标准ExecutorService，自动传播ManagedThreadContext到异步任务
 * 
 * 特性：
 * - 自动捕获父线程上下文快照
 * - 在子线程恢复上下文
 * - 任务完成后自动清理
 * - 支持所有ExecutorService操作
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class ContextPropagatingExecutor implements ExecutorService {
    
    private final ExecutorService delegate;
    
    private ContextPropagatingExecutor(ExecutorService delegate) {
        this.delegate = delegate;
    }
    
    /**
     * 包装现有的ExecutorService
     * 
     * @param executor 要包装的执行器
     * @return 支持上下文传播的执行器
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
    
    /**
     * 包装Runnable任务，添加上下文传播
     */
    private Runnable wrapTask(Runnable task) {
        // 捕获当前线程的上下文快照
        ManagedThreadContext currentContext = ManagedThreadContext.current();
        ContextSnapshot snapshot = currentContext != null ? currentContext.createSnapshot() : null;
        
        return () -> {
            ManagedThreadContext restoredContext = null;
            try {
                // 恢复上下文
                if (snapshot != null) {
                    restoredContext = ThreadContext.propagate(snapshot);
                }
                // 执行原始任务
                task.run();
            } finally {
                // 清理恢复的上下文
                if (restoredContext != null) {
                    ThreadContext.clear();
                }
            }
        };
    }
    
    /**
     * 包装Callable任务，添加上下文传播
     */
    private <T> Callable<T> wrapTask(Callable<T> task) {
        // 捕获当前线程的上下文快照
        ManagedThreadContext currentContext = ManagedThreadContext.current();
        ContextSnapshot snapshot = currentContext != null ? currentContext.createSnapshot() : null;
        
        return () -> {
            ManagedThreadContext restoredContext = null;
            try {
                // 恢复上下文
                if (snapshot != null) {
                    restoredContext = ThreadContext.propagate(snapshot);
                }
                // 执行原始任务
                return task.call();
            } finally {
                // 清理恢复的上下文
                if (restoredContext != null) {
                    ThreadContext.clear();
                }
            }
        };
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