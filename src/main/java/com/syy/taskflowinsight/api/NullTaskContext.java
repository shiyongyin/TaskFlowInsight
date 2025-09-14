package com.syy.taskflowinsight.api;

/**
 * 空任务上下文实现
 * 使用空对象模式，提供安全的无操作实现
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
final class NullTaskContext implements TaskContext {
    
    /**
     * 单例实例
     */
    static final NullTaskContext INSTANCE = new NullTaskContext();
    
    /**
     * 私有构造函数，防止外部实例化
     */
    private NullTaskContext() {
        // 单例模式
    }
    
    @Override
    public TaskContext message(String message) {
        return this;
    }
    
    @Override
    public TaskContext debug(String message) {
        return this;
    }
    
    @Override
    public TaskContext warn(String message) {
        return this;
    }
    
    @Override
    public TaskContext error(String message) {
        return this;
    }
    
    @Override
    public TaskContext error(String message, Throwable throwable) {
        return this;
    }
    
    @Override
    public TaskContext attribute(String key, Object value) {
        return this;
    }
    
    @Override
    public TaskContext tag(String tag) {
        return this;
    }
    
    @Override
    public TaskContext success() {
        return this;
    }
    
    @Override
    public TaskContext fail() {
        return this;
    }
    
    @Override
    public TaskContext fail(Throwable throwable) {
        return this;
    }
    
    @Override
    public TaskContext subtask(String taskName) {
        return this;
    }
    
    @Override
    public boolean isClosed() {
        return false;
    }
    
    @Override
    public String getTaskName() {
        return "";
    }
    
    @Override
    public String getTaskId() {
        return "";
    }
    
    @Override
    public void close() {
        // 空操作
    }
    
    @Override
    public String toString() {
        return "NullTaskContext";
    }
}