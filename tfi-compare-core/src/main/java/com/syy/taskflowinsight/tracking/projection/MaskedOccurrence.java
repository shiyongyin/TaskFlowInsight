package com.syy.taskflowinsight.tracking.projection;

/**
 * 脱敏后同路径token碰撞时的稳定序号。
 *
 * <p>序号只表达canonical排序中的出现次序，不携带raw值或hash。</p>
 *
 * @param value 从0开始的碰撞序号
 * @since 4.0.0
 */
public record MaskedOccurrence(int value) {

    public MaskedOccurrence {
        if (value < 0) {
            throw new IllegalArgumentException("masked occurrence must not be negative");
        }
    }
}
