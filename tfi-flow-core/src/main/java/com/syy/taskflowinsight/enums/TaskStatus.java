package com.syy.taskflowinsight.enums;

/**
 * 任务状态枚举
 * 定义TaskNode的执行状态，采用最小化设计
 * 
 * <p>状态转换规则：
 * <ul>
 *   <li>RUNNING → COMPLETED (正常完成)</li>
 *   <li>RUNNING → FAILED (执行失败)</li>
 * </ul>
 * 
 * <p>注意：任务创建时直接处于RUNNING状态，不需要显式启动。
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public enum TaskStatus {
    
    /**
     * 执行中状态 - 任务正在执行
     * 这是任务的初始状态，创建时直接进入此状态
     */
    RUNNING,
    
    /**
     * 已完成状态 - 任务正常完成
     * 这是正常完成的终止状态
     */
    COMPLETED,
    
    /**
     * 执行失败状态 - 任务执行过程中发生错误
     * 这是异常结束的终止状态
     */
    FAILED;
    
    /**
     * 判断是否为活跃状态（任务仍在执行）
     * 
     * @return true 如果任务处于活跃状态
     */
    public boolean isActive() {
        return this == RUNNING;
    }
    
    /**
     * 判断是否为终止状态（任务已经结束）
     * 
     * @return true 如果任务已经终止
     */
    public boolean isTerminated() {
        return this == COMPLETED || this == FAILED;
    }
    
    /**
     * 判断任务是否成功完成
     * 
     * @return true 如果任务成功完成
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
    
    /**
     * 判断任务是否失败
     * 
     * @return true 如果任务执行失败
     */
    public boolean isFailed() {
        return this == FAILED;
    }
    
    /**
     * 验证状态转换是否有效
     * 
     * @param target 目标状态
     * @return true 如果可以转换到目标状态
     * @throws IllegalArgumentException 如果target为null
     */
    public boolean canTransitionTo(TaskStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("Target status cannot be null");
        }
        
        switch (this) {
            case RUNNING:
                return target == COMPLETED || target == FAILED;
            case COMPLETED:
            case FAILED:
                return false; // 终止状态不能转换
            default:
                return false;
        }
    }
}