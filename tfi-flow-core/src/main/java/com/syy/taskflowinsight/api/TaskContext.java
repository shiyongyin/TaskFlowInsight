package com.syy.taskflowinsight.api;

/**
 * 任务上下文接口
 * 提供链式调用支持的任务操作接口
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public interface TaskContext extends AutoCloseable {
    
    /**
     * 记录信息消息
     * 
     * @param message 消息内容
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext message(String message);
    
    /**
     * 记录调试消息
     * 
     * @param message 消息内容
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext debug(String message);
    
    /**
     * 记录警告消息
     * 
     * @param message 消息内容
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext warn(String message);
    
    /**
     * 记录错误消息
     * 
     * @param message 消息内容
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext error(String message);
    
    /**
     * 记录错误消息和异常
     * 
     * @param message 消息内容
     * @param throwable 异常对象
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext error(String message, Throwable throwable);
    
    /**
     * 添加任务属性
     * 
     * @param key 属性键
     * @param value 属性值
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext attribute(String key, Object value);
    
    /**
     * 添加任务标签
     * 
     * @param tag 标签内容
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext tag(String tag);
    
    /**
     * 标记任务成功
     * 
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext success();
    
    /**
     * 标记任务失败
     * 
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext fail();
    
    /**
     * 标记任务失败并记录异常
     * 
     * @param throwable 异常对象
     * @return 当前TaskContext实例，支持链式调用
     */
    TaskContext fail(Throwable throwable);
    
    /**
     * 创建子任务
     * 
     * @param taskName 子任务名称
     * @return 新的TaskContext实例
     */
    TaskContext subtask(String taskName);
    
    /**
     * 检查任务是否已关闭
     * 
     * @return true 如果任务已关闭
     */
    boolean isClosed();
    
    /**
     * 获取任务名称
     * 
     * @return 任务名称
     */
    String getTaskName();
    
    /**
     * 获取任务ID
     * 
     * @return 任务ID
     */
    String getTaskId();
    
    /**
     * 关闭任务上下文
     * 实现AutoCloseable接口，支持try-with-resources
     */
    @Override
    void close();
}