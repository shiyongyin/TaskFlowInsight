package com.syy.taskflowinsight.tracking.snapshot;

/**
 * 浅引用字段的快照模式
 * 控制 @ShallowReference 标注字段的快照生成策略
 *
 * <p>示例配置：
 * <pre>
 * tfi.change-tracking.snapshot.shallow-reference-mode=VALUE_ONLY
 * </pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0 (P2-2)
 */
public enum ShallowReferenceMode {

    /**
     * 仅保存值（默认）
     * 保持向后兼容，仅记录字段的直接值或 toString() 结果
     *
     * 适用场景：
     * - 性能优先
     * - 简单引用关系
     * - 向后兼容需求
     */
    VALUE_ONLY("仅保存字段值，不提取复合键"),

    /**
     * 复合键字符串格式
     * 提取所有 @Key 字段，生成 "[key1=val1,key2=val2]" 格式的字符串
     *
     * 适用场景：
     * - 日志记录
     * - 调试输出
     * - 人类可读性优先
     */
    COMPOSITE_STRING("生成字符串格式的复合键摘要"),

    /**
     * 复合键 Map 格式
     * 提取所有 @Key 字段，保存为 Map<String, Object> 结构
     *
     * 适用场景：
     * - 结构化数据消费
     * - 渲染层处理
     * - 精确键匹配
     */
    COMPOSITE_MAP("生成 Map 格式的复合键结构");

    private final String description;

    ShallowReferenceMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否需要提取复合键
     *
     * @return true 如果需要遍历继承链提取@Key字段
     */
    public boolean requiresKeyExtraction() {
        return this != VALUE_ONLY;
    }

    /**
     * 从字符串解析模式（大小写不敏感）
     *
     * @param value 模式字符串
     * @return 对应的枚举值，无效时返回 VALUE_ONLY
     */
    public static ShallowReferenceMode fromString(String value) {
        if (value == null || value.isEmpty()) {
            return VALUE_ONLY;
        }

        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 无效值时返回默认
            return VALUE_ONLY;
        }
    }
}