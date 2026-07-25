package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.ContextPropagatingExecutor;
import com.syy.taskflowinsight.context.SafeContextManager;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.model.TaskNode;
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
 * 2. ContextPropagatingExecutor.wrap() - 装饰器模式
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
                    ManagedThreadContext asyncContext = ManagedThreadContext.current();
                    String contextId = asyncContext != null ? asyncContext.getContextId() : "none";
                    TaskNode asyncTask = asyncContext != null ? asyncContext.getCurrentTask() : null;
                    logger.info("异步线程上下文ID: {}, 当前任务: {}, 路径: {}",
                            contextId,
                            asyncTask != null ? asyncTask.getTaskName() : "none",
                            asyncTask != null ? asyncTask.getTaskPath() : "none");

                    /*
                     * executeAsync 已拥有同名真实任务；直接记录消息可避免示例误导使用者再创建
                     * 一层重复节点，使展示出的路径与 API 参数保持一一对应。
                     */
                    TFI.message("开始异步计算", "PROCESS");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    TFI.message("异步计算完成", "PROCESS");
                    
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
     * 演示 canonical Executor 装饰器的使用。
     *
     * <p>线程池策略由业务侧选择，TFI 只包装传播职责，避免框架隐式决定队列与拒绝策略。
     */
    public void demonstrateContextPropagatingExecutor() {
        logger.info("========== ContextPropagatingExecutor 演示 ==========");
        
        ExecutorService executor = ContextPropagatingExecutor.wrap(Executors.newFixedThreadPool(2));
        
        TFI.startSession("executor-demo");
        try {
            TFI.run("executor-task", () -> {
                
                ManagedThreadContext mainContext = ManagedThreadContext.current();
                logger.info("主线程上下文ID: {}", mainContext != null ? mainContext.getContextId() : "none");
                
                // 提交多个任务到 Context 传播执行器
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
                
                logger.info("ContextPropagatingExecutor演示完成");
            });
        } catch (Exception e) {
            logger.error("ContextPropagatingExecutor演示失败", e);
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
        demonstrateContextPropagatingExecutor();
        demonstrateManualWrapping();
        
        logger.info("所有异步上下文传播演示完成！");
    }
}
