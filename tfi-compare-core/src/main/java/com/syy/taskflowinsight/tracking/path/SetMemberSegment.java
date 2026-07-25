package com.syy.taskflowinsight.tracking.path;

import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Set成员的稳定scalar地址；kind独立于Map key，避免跨容器语义合并。
 *
 * @param member 不持有业务对象的exact scalar成员事实
 * @since 4.0.0
 */
public record SetMemberSegment(ValueSnapshot member) implements PathSegment {

    public SetMemberSegment {
        Objects.requireNonNull(member, "member");
        if (!member.isExactScalar()) {
            throw new IllegalArgumentException("set member identity must be exact");
        }
    }

    /** @return Set成员地址的固定kind */
    @Override
    public Kind kind() {
        return Kind.SET_MEMBER;
    }

    /** @return 固定kind、值类型与exact scalar成员的有序事实 */
    @Override
    public List<String> canonicalTextFacts() {
        List<String> facts = new ArrayList<>(2 + member.canonicalTextFacts().size());
        facts.add(kind().wireCode());
        facts.add(member.typeCode());
        facts.addAll(member.canonicalTextFacts());
        return List.copyOf(facts);
    }
}
