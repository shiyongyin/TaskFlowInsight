package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 计算Compare结果schema中稳定文本事实的UTF-16成本。
 *
 * <p>该类型无状态，只固化formatter无关的计量规则；预算准入和omitted状态仍由
 * {@link CompareResultAccumulator}唯一持有，避免成本算法变成第二结果owner。</p>
 */
final class ResultFactCost {

    private ResultFactCost() {
    }

    static long change(FieldChange change) {
        long cost = change.kind().name().length();
        if (change.before().isPresent()) {
            cost = saturatingAdd(cost, 1L + side(change.before().orElseThrow()));
        }
        if (change.after().isPresent()) {
            cost = saturatingAdd(cost, 1L + side(change.after().orElseThrow()));
        }
        return cost;
    }

    static long issue(
            String wireCode,
            CompareStage stage,
            Optional<ComparePath> path) {
        long cost = factSequence(List.of(wireCode, stage.name()));
        if (path.isPresent() && path.orElseThrow().canonicalFactCost() > 0) {
            cost = saturatingAdd(cost, 1L + path.orElseThrow().canonicalFactCost());
        }
        return cost;
    }

    static long saturatingAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long side(ChangeSide side) {
        return saturatingAdd(side.path().canonicalFactCost(), 1L + value(side.value()));
    }

    private static long value(ValueSnapshot value) {
        List<String> facts = new ArrayList<>();
        facts.add(value.representation().name());
        facts.add(value.typeCode());
        facts.addAll(value.canonicalTextFacts());
        value.omissionReason().ifPresent(reason -> facts.add(reason.name()));
        return factSequence(facts);
    }

    private static long factSequence(List<String> facts) {
        long cost = Math.max(0, facts.size() - 1);
        for (String fact : facts) {
            cost = saturatingAdd(cost, fact.length());
        }
        return cost;
    }
}
