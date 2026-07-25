package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity声明类型与有序scalar components组成的稳定地址，不保留entity实例。
 *
 * @param declaringType 稳定声明类型编码，不从业务对象的展示文本推导
 * @param components 非空、有序且全部为exact scalar的复合key事实
 * @since 4.0.0
 */
public record EntityKeySegment(String declaringType, List<ValueSnapshot> components) implements PathSegment {

    public EntityKeySegment {
        Objects.requireNonNull(declaringType, "declaringType");
        if (declaringType.isEmpty()) {
            throw new IllegalArgumentException("declaringType must not be empty");
        }
        components = List.copyOf(components);
        if (components.isEmpty()
                || components.stream().anyMatch(value -> !value.isExactScalar())) {
            throw new IllegalArgumentException("entity key components must be non-empty exact facts");
        }
    }

    /** @return entity复合key地址的固定kind */
    @Override
    public Kind kind() {
        return Kind.ENTITY_KEY;
    }

    /** @return 固定kind、声明类型与有序exact scalar key components */
    @Override
    public List<String> canonicalTextFacts() {
        List<String> facts = new ArrayList<>();
        facts.add(kind().wireCode());
        facts.add(declaringType);
        for (ValueSnapshot component : components) {
            facts.add(component.typeCode());
            facts.addAll(component.canonicalTextFacts());
        }
        return List.copyOf(facts);
    }
}
