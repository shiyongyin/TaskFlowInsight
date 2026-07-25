package com.syy.taskflowinsight.tracking.projection;

import java.util.Objects;
import java.util.Optional;

/**
 * 单次projection调用可附带的固定元数据闭集。
 *
 * <p>不接受任意labels，避免高基数或敏感字段绕过同一masking与字符预算。</p>
 *
 * @param sessionId 可选Core session标识；默认安全策略固定脱敏
 * @param taskId 可选Core task标识；默认安全策略固定脱敏
 * @param operationName 可选调用操作名；仍需执行完整内容检测
 * @since 4.0.0
 */
public record ProjectionMetadata(
        Optional<String> sessionId,
        Optional<String> taskId,
        Optional<String> operationName) {

    /**
     * 校验并冻结三个可选字符串，不执行截断或隐式trim。
     */
    public ProjectionMetadata {
        sessionId = requirePresentValues(sessionId, "sessionId");
        taskId = requirePresentValues(taskId, "taskId");
        operationName = requirePresentValues(operationName, "operationName");
    }

    /**
     * 创建不污染纯Compare结果的空元数据。
     *
     * @return 三个字段均缺失的不可变metadata
     */
    public static ProjectionMetadata empty() {
        return new ProjectionMetadata(Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Optional<String> requirePresentValues(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(item -> Objects.requireNonNull(item, name + " value"));
        return value;
    }

    /**
     * 只输出字段存在性，防止日志在factory脱敏前泄漏元数据。
     *
     * @return 不含任何metadata原值的安全摘要
     */
    @Override
    public String toString() {
        return "ProjectionMetadata{sessionIdPresent=" + sessionId.isPresent()
                + ", taskIdPresent=" + taskId.isPresent()
                + ", operationNamePresent=" + operationName.isPresent() + '}';
    }
}
