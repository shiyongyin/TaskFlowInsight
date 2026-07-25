package com.syy.taskflowinsight.tracking.path;

import java.util.List;

/**
 * List或数组的非负位置；十进制事实固定且不受locale影响。
 *
 * @param index 容器中的非负物理位置
 * @since 4.0.0
 */
public record IndexSegment(int index) implements PathSegment {

    public IndexSegment {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
    }

    /** @return 有序容器位置的固定kind */
    @Override
    public Kind kind() {
        return Kind.INDEX;
    }

    /** @return 固定kind token与不受locale影响的十进制位置 */
    @Override
    public List<String> canonicalTextFacts() {
        return List.of(kind().wireCode(), Integer.toString(index));
    }
}
