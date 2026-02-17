package com.syy.taskflowinsight.actuator.support;

import java.time.Instant;

/**
 * 端点访问日志条目。
 *
 * <p>记录每次端点调用的操作名称和时间戳，用于统计
 * 端点使用频率和最近访问情况。</p>
 *
 * @author TaskFlow Insight Team
 * @since 3.0.0
 */
public class EndpointAccessLog {

    /** 访问的操作名称 */
    private final String operation;

    /** 访问时间 */
    private final Instant timestamp;

    /**
     * 创建访问日志条目。
     *
     * @param operation 操作名称
     * @param timestamp 访问时间
     */
    public EndpointAccessLog(String operation, Instant timestamp) {
        this.operation = operation;
        this.timestamp = timestamp;
    }

    /**
     * 获取操作名称。
     *
     * @return 操作名称
     */
    public String getOperation() {
        return operation;
    }

    /**
     * 获取访问时间。
     *
     * @return 访问时间
     */
    public Instant getTimestamp() {
        return timestamp;
    }
}
