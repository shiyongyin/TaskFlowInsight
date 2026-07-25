package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Map entry的稳定scalar地址。
 *
 * <p>摘要只能描述值规模，不能证明key身份；因此动态地址必须持有EXACT snapshot，且绝不保留原始key对象。</p>
 *
 * @param key 不持有业务对象的exact scalar key事实
 * @since 4.0.0
 */
public record MapKeySegment(ValueSnapshot key) implements PathSegment {

    public MapKeySegment {
        Objects.requireNonNull(key, "key");
        if (!key.isExactScalar()) {
            throw new IllegalArgumentException("map key identity must be exact");
        }
    }

    /** @return Map动态地址的固定kind */
    @Override
    public Kind kind() {
        return Kind.MAP_KEY;
    }

    /** @return 固定kind、值类型与exact scalar key的有序事实 */
    @Override
    public List<String> canonicalTextFacts() {
        List<String> facts = new ArrayList<>(2 + key.canonicalTextFacts().size());
        facts.add(kind().wireCode());
        facts.add(key.typeCode());
        facts.addAll(key.canonicalTextFacts());
        return List.copyOf(facts);
    }
}
