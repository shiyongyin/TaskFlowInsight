package com.syy.taskflowinsight.tracking.compare;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 比较算法的版本化身份。
 *
 * <p>结果schema、运行时注册和指标标签必须共享同一身份类型，否则算法语义升级时会出现无法追溯的字符串分叉。
 * 具体grammar由该值对象统一收口，运行时只负责注册唯一性和选择。</p>
 *
 * @param value canonical算法标识
 */
public record AlgorithmId(String value) {

    private static final int MAX_ENCODED_LENGTH = 128;
    private static final Pattern GRAMMAR = Pattern.compile(
            "[a-z0-9][a-z0-9._-]*:[a-z0-9][a-z0-9._-]*:v[1-9][0-9]*");

    /** 校验算法身份满足固定 grammar 与编码长度上限。 */
    public AlgorithmId {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_ENCODED_LENGTH || !GRAMMAR.matcher(value).matches()) {
            throw new IllegalArgumentException("AlgorithmId does not match the canonical grammar");
        }
    }

    /**
     * 从 canonical 文本创建算法身份。
     *
     * @param value 满足 {@code namespace:name:vN} grammar 的文本
     * @return 已验证的算法身份
     */
    public static AlgorithmId of(String value) {
        return new AlgorithmId(value);
    }
}
