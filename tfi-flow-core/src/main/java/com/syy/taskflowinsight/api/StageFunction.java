package com.syy.taskflowinsight.api;

/**
 * Stage函数式接口
 * 
 * <p>用于支持函数式风格的Stage操作：
 * <pre>{@code
 * String result = TFI.stage("数据处理", stage -> {
 *     stage.message("开始处理");
 *     // 业务逻辑
 *     return "处理结果";
 * });
 * }</pre>
 * 
 * @param <T> 返回值类型
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
@FunctionalInterface
public interface StageFunction<T> {
    
    /**
     * 在给定的Stage上下文中执行操作
     * 
     * @param stage Stage上下文
     * @return 执行结果
     * @throws Exception 如果执行过程中发生异常
     */
    T apply(TaskContext stage) throws Exception;
}