package com.syy.taskflowinsight.tracking.projection;

/**
 * 单次projection调用的非敏感资源选项。
 *
 * <p>该类型只控制固定metadata字段的单值预算，不允许改变schema、masking或比较语义。</p>
 *
 * @param maxMetadataChars 每个metadata exact值允许的最大UTF-16 code unit数，0表示全部OMITTED
 * @since 4.0.0
 */
public record ProjectionOptions(int maxMetadataChars) {

    /** metadata单字段的安全默认预算。 */
    public static final int DEFAULT_MAX_METADATA_CHARS = 256;

    /** 代码与后续Spring binder均不可越过的固定hard ceiling。 */
    public static final int MAX_METADATA_CHARS = 1_024;

    /**
     * 校验单次调用只能在hard ceiling内选择预算。
     */
    public ProjectionOptions {
        if (maxMetadataChars < 0 || maxMetadataChars > MAX_METADATA_CHARS) {
            throw new IllegalArgumentException("maxMetadataChars exceeds projection ceiling");
        }
    }

    /**
     * 返回唯一的纯Java投影默认值。
     *
     * @return metadata预算为256的不可变options
     */
    public static ProjectionOptions defaults() {
        return new ProjectionOptions(DEFAULT_MAX_METADATA_CHARS);
    }
}
