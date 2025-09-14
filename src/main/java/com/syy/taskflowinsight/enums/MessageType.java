package com.syy.taskflowinsight.enums;

/**
 * 消息类型枚举
 * 定义业务流程跟踪过程中的消息类型，面向业务而非技术日志
 * 
 * <p>级别设计：
 * <ul>
 *   <li>业务流程 (级别1) - 业务执行步骤和状态</li>
 *   <li>核心指标 (级别2) - 关键业务指标数据</li>
 *   <li>变更记录 (级别3) - 业务数据变更记录</li>
 *   <li>异常提示 (级别4) - 业务异常和问题提示</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-06
 */
public enum MessageType {
    
    /** 业务流程 - 记录业务执行步骤和状态 */
    PROCESS("业务流程", 1),
    /** 核心指标 - 记录关键业务指标数据 */
    METRIC("核心指标", 2),
    /** 变更记录 - 记录业务数据变更情况 */
    CHANGE("✏\uFE0F变更记录", 3),
    /** 异常提示 - 记录业务异常和问题提示 */
    ALERT("⚠\uFE0F异常提示", 4);

    private final String displayName;
    private final int level;
    
    /**
     * 构造函数
     * 
     * @param displayName 显示名称
     * @param level 消息级别（数字越大优先级越高）
     */
    MessageType(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }
    
    /**
     * 获取显示名称
     * 
     * @return 消息类型的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取消息级别
     * 
     * @return 消息级别（数字越大优先级越高）
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * 判断是否为异常提示类型
     * 
     * @return true 如果是异常提示类型
     */
    public boolean isAlert() { return this == ALERT; }
    
    /**
     * 判断是否为业务流程类型
     * 
     * @return true 如果是业务流程类型
     */
    public boolean isProcess() { return this == PROCESS; }

    /**
     * 判断是否为核心指标类型
     * 
     * @return true 如果是核心指标类型
     */
    public boolean isMetric() { return this == METRIC; }

    /**
     * 判断是否为变更记录类型
     * 
     * @return true 如果是变更记录类型
     */
    public boolean isChange() { return this == CHANGE; }

    /** 是否为重要级别（变更记录及以上） */
    public boolean isImportant() { return this.level >= 3; }
    
    /**
     * 比较消息类型的级别
     * 
     * @param other 要比较的消息类型
     * @return 正数如果当前级别更高，负数如果更低，0如果相等
     * @throws IllegalArgumentException 如果other为null
     */
    public int compareLevel(MessageType other) {
        if (other == null) {
            throw new IllegalArgumentException("Other message type cannot be null");
        }
        return Integer.compare(this.level, other.level);
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
