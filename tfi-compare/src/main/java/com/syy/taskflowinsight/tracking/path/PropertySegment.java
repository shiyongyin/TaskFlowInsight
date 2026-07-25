package com.syy.taskflowinsight.tracking.path;

import java.util.List;
import java.util.Objects;

/**
 * Java property名称形成的稳定路径段；名称保持原样，不做locale或大小写归一化。
 *
 * @param name 声明字段名；不能为空且不会作为display path提前编码
 * @since 4.0.0
 */
public record PropertySegment(String name) implements PathSegment {

    public PropertySegment {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("property name must not be empty");
        }
    }

    /** @return property路径段的固定kind */
    @Override
    public Kind kind() {
        return Kind.PROPERTY;
    }

    /** @return 固定kind token与原始声明字段名 */
    @Override
    public List<String> canonicalTextFacts() {
        return List.of(kind().wireCode(), name);
    }
}
