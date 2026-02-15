package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.TFIAwareExecutor;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 异步上下文传播演示
 * 
 * 演示三种异步上下文传播模式：
 * 1. SafeContextManager.executeAsync() - 推荐方式
 * 2. TFIAwareExecutor包装 - 装饰器模式
 * 3. 手动wrapRunnable/wrapCallable - 灵活控制
 */
@Component
public class AsyncPropagationDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncPropagationDemo.class);
    
    private final SafeContextManager contextManager = SafeContextManager.getInstance();
    
    /**
     * 演示SafeContextManager.executeAsync()的使用
     */
    public void demonstrateExecuteAsync() {
        logger.info("========== SafeContextManager.executeAsync() 演示 ==========");
        
        TFI.startSession("async-demo");
        try {
            // 记录主线程上下文信息
            ManagedThreadContext mainContext = ManagedThreadContext.current();
            logger.info("主线程上下文ID: {}", mainContext != null ? mainContext.getContextId() : "none");
            
            TFI.run("main-task", () -> {
                
                // 使用executeAsync执行异步任务
                CompletableFuture<String> future = contextManager.executeAsync("async-computation", () -> {
                    // 异步线程中验证上下文传播
                    ManagedThreadContext asyncContext = ManagedThreadContext.current();
                    String contextId = asyncContext != null ? asyncContext.getContextId() : "none";
                    logger.info("异步线程上下文ID: {}", contextId);
                    
                    // 在异步任务中继续创建任务
                    TFI.run("async-computation", () -> {
                        TFI.message("开始异步计算", "PROCESS");
                        
                        // 模拟计算工作
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        TFI.message("异步计算完成", "PROCESS");
                    });
                    
                    return "计算结果：42";
                });
                
                // 等待异步任务完成
                try {
                    String result = future.get(5, TimeUnit.SECONDS);
                    TFI.message("收到异步结果: " + result, "INFO");
                } catch (Exception e) {
                    logger.error("异步任务执行失败", e);
                }
                
                logger.info("executeAsync演示完成");
            });
        } catch (Exception e) {
            logger.error("executeAsync演示失败", e);
        } finally {
            TFI.endSession();
        }
    }
    
    /**
     * 演示TFIAwareExecutor的使用
     */
    public void demonstrateTFIAwareExecutor() {
        logger.info("========== TFIAwareExecutor 演示 ==========");
        
        // 创建TFI感知的线程池
        TFIAwareExecutor executor = TFIAwareExecutor.newFixedThreadPool(2);
        
        TFI.startSession("executor-demo");
        try {
            TFI.run("executor-task", () -> {
                
                ManagedThreadContext mainContext = ManagedThreadContext.current();
                logger.info("主线程上下文ID: {}", mainContext != null ? mainContext.getContextId() : "none");
                
                // 提交多个任务到TFI感知的线程池
                CompletableFuture<Void> task1 = CompletableFuture.runAsync(() -> {
                    ManagedThreadContext context = ManagedThreadContext.current();
                    logger.info("Task1线程上下文ID: {}", context != null ? context.getContextId() : "none");
                    
                    TFI.run("parallel-task-1", () -> {
                        TFI.message("Task1 executing", "PROCESS");
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        TFI.message("Task1 completed", "PROCESS");
                    });
                }, executor);
                
                CompletableFuture<String> task2 = CompletableFuture.supplyAsync(() -> {
                    ManagedThreadContext context = ManagedThreadContext.current();
                    logger.info("Task2线程上下文ID: {}", context != null ? context.getContextId() : "none");
                    
                    TFI.run("parallel-task-2", () -> {
                        TFI.message("Task2 executing", "PROCESS");
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        TFI.message("Task2 completed", "PROCESS");
                    });
                    
                    return "Task2 result";
                }, executor);
                
                // 等待所有任务完成
                try {
                    CompletableFuture.allOf(task1, task2).get(5, TimeUnit.SECONDS);
                    String task2Result = task2.get();
                    TFI.message("所有并行任务完成，Task2结果: " + task2Result, "INFO");
                } catch (Exception e) {
                    logger.error("并行任务执行失败", e);
                }
                
                logger.info("TFIAwareExecutor演示完成");
            });
        } catch (Exception e) {
            logger.error("TFIAwareExecutor演示失败", e);
        } finally {
            executor.shutdown();
            TFI.endSession();
        }
    }
    
    /**
     * 演示手动包装Runnable/Callable的使用
     */
    public void demonstrateManualWrapping() {
        logger.info("========== 手动包装 演示 ==========");
        
        ExecutorService standardPool = Executors.newFixedThreadPool(2);
        
        TFI.startSession("manual-demo");
        try {
            TFI.run("manual-task", () -> {
                
                ManagedThreadContext mainContext = ManagedThreadContext.current();
                logger.info("主线程上下文ID: {}", mainContext != null ? mainContext.getContextId() : "none");
                
                // 手动包装Runnable
                Runnable wrappedRunnable = contextManager.wrapRunnable(() -> {
                    ManagedThreadContext context = ManagedThreadContext.current();
                    logger.info("包装Runnable线程上下文ID: {}", context != null ? context.getContextId() : "none");
                    
                    TFI.run("wrapped-runnable", () -> {
                        TFI.message("Wrapped Runnable executing", "PROCESS");
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        TFI.message("Wrapped Runnable completed", "PROCESS");
                    });
                });
                
                // 提交包装后的任务到标准线程池
                CompletableFuture<Void> future1 = CompletableFuture.runAsync(wrappedRunnable, standardPool);
                
                // 手动包装Callable并转为Supplier
                CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
                    try {
                        return contextManager.wrapCallable(() -> {
                            ManagedThreadContext context = ManagedThreadContext.current();
                            logger.info("包装Callable线程上下文ID: {}", context != null ? context.getContextId() : "none");
                            
                            TFI.run("wrapped-callable", () -> {
                                TFI.message("Wrapped Callable executing", "PROCESS");
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                TFI.message("Wrapped Callable completed", "PROCESS");
                            });
                            
                            return "Callable result";
                        }).call();
                    } catch (Exception e) {
                        logger.error("包装Callable执行失败", e);
                        return "error";
                    }
                }, standardPool);
                
                // 等待所有任务完成
                try {
                    CompletableFuture.allOf(future1, future2).get(5, TimeUnit.SECONDS);
                    String result = future2.get();
                    TFI.message("手动包装任务完成，结果: " + result, "INFO");
                } catch (Exception e) {
                    logger.error("手动包装任务执行失败", e);
                }
                
                logger.info("手动包装演示完成");
            });
        } catch (Exception e) {
            logger.error("手动包装演示失败", e);
        } finally {
            standardPool.shutdown();
            TFI.endSession();
        }
    }
    
    /**
     * 运行所有演示
     */
    public void runAllDemos() {
        logger.info("开始异步上下文传播演示...");
        
        demonstrateExecuteAsync();
        demonstrateTFIAwareExecutor();
        demonstrateManualWrapping();
        
        logger.info("所有异步上下文传播演示完成！");
    }
}