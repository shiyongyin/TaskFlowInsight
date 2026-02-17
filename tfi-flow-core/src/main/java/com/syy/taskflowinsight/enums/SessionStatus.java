package com.syy.taskflowinsight.enums;

/**
 * 会话状态枚举
 * 定义Session对象的生命周期状态，采用最小化设计
 * 
 * <p>状态转换规则：
 * <ul>
 *   <li>RUNNING → COMPLETED (正常完成)</li>
 *   <li>RUNNING → ERROR (异常终止)</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public enum SessionStatus {
    
    /**
     * 运行中状态 - 会话正在执行，有活动任务
     * 这是会话的初始状态和唯一活跃状态
     */
    RUNNING,
    
    /**
     * 已完成状态 - 会话正常结束，所有任务都已完成
     * 这是正常结束的终止状态
     */
    COMPLETED,
    
    /**
     * 错误状态 - 会话因异常而终止
     * 这是异常结束的终止状态
     */
    ERROR;
    
    /**
     * 判断是否为活跃状态（可以继续执行任务）
     * 
     * @return true 如果会话处于活跃状态
     */
    public boolean isActive() {
        return this == RUNNING;
    }
    
    /**
     * 判断是否为终止状态（不能继续执行任务）
     * 
     * @return true 如果会话已经终止
     */
    public boolean isTerminated() {
        return this == COMPLETED || this == ERROR;
    }
    
    /**
     * 判断会话是否正常完成
     * 
     * @return true 如果会话正常完成
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }
    
    /**
     * 判断会话是否异常结束
     * 
     * @return true 如果会话异常结束
     */
    public boolean isError() {
        return this == ERROR;
    }
    
    /**
     * 验证状态转换是否有效
     * 
     * @param target 目标状态
     * @return true 如果可以转换到目标状态
     * @throws IllegalArgumentException 如果target为null
     */
    public boolean canTransitionTo(SessionStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("Target status cannot be null");
        }
        
        switch (this) {
            case RUNNING:
                return target == COMPLETED || target == ERROR;
            case COMPLETED:
            case ERROR:
                return false; // 终止状态不能转换
            default:
                return false;
        }
    }
}