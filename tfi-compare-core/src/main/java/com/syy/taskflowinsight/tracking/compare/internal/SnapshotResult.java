package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 单侧对象图捕获后形成的请求内部有界事实。
 *
 * <p>该类型与ledger、reducer同属compare kernel，避免基础snapshot包反向依赖compare真值类型。
 * 它不保存业务对象、Throwable或display path，后续diff只能基于typed事实判定。</p>
 */
final class SnapshotResult {

    /** 按确定遍历顺序保留的typed路径和值事实。 */
    private final Map<ComparePath, ValueSnapshot> values;

    /** 当前单侧捕获是否完整；预算或可隔离分支失败为PARTIAL。 */
    private final CompareCompletion completion;

    /** 非预期snapshot能力故障，不包含异常message或Throwable。 */
    private final List<CompareProblem> problems;

    /** depth、deadline和预算等预期执行边界。 */
    private final List<CompareLimitation> limitations;

    /** cycle出现路径到当前ancestor路径的typed引用，不保存对象身份。 */
    private final Map<ComparePath, ComparePath> cycleReferences;

    /** List与Set中使用exact key形成的逻辑Entity成员根。 */
    private final Set<ComparePath> entityRoots;

    /** keyed List逻辑Entity根到当前侧物理Index路径，用于独立发布MOVE。 */
    private final Map<ComparePath, ComparePath> entityPositions;

    /** Set容器到未标注复杂成员的完整canonical分组，不进入公共member path。 */
    private final Map<ComparePath, CanonicalSetSnapshot> setSnapshots;

    private SnapshotResult(
            Map<ComparePath, ValueSnapshot> values,
            CompareCompletion completion,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            Map<ComparePath, ComparePath> cycleReferences,
            Set<ComparePath> entityRoots,
            Map<ComparePath, ComparePath> entityPositions,
            Map<ComparePath, CanonicalSetSnapshot> setSnapshots) {
        this.values = freezeOwnedOrderedMap(values);
        this.completion = Objects.requireNonNull(completion, "completion");
        this.problems = List.copyOf(problems);
        this.limitations = List.copyOf(limitations);
        this.cycleReferences = freezeOwnedOrderedMap(cycleReferences);
        this.entityRoots = Set.copyOf(entityRoots);
        this.entityPositions = freezeOwnedOrderedMap(entityPositions);
        this.setSnapshots = freezeOwnedOrderedMap(setSnapshots);
    }

    /** 接管 capture 局部事实；调用方在返回后不得再保留或修改这些 Map。 */
    static SnapshotResult fromOwnedFacts(
            Map<ComparePath, ValueSnapshot> values,
            CompareCompletion completion,
            List<CompareProblem> problems,
            List<CompareLimitation> limitations,
            Map<ComparePath, ComparePath> cycleReferences,
            Set<ComparePath> entityRoots,
            Map<ComparePath, ComparePath> entityPositions,
            Map<ComparePath, CanonicalSetSnapshot> setSnapshots) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(problems, "problems");
        Objects.requireNonNull(limitations, "limitations");
        Objects.requireNonNull(cycleReferences, "cycleReferences");
        Objects.requireNonNull(entityRoots, "entityRoots");
        Objects.requireNonNull(entityPositions, "entityPositions");
        Objects.requireNonNull(setSnapshots, "setSnapshots");
        if (completion == CompareCompletion.DISABLED) {
            throw new IllegalArgumentException("snapshot completion cannot be disabled");
        }
        return new SnapshotResult(
                values,
                completion,
                problems,
                limitations,
                cycleReferences,
                entityRoots,
                entityPositions,
                setSnapshots);
    }

    private static <K, V> Map<K, V> freezeOwnedOrderedMap(final Map<K, V> source) {
        return source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(source);
    }

    Map<ComparePath, ValueSnapshot> values() {
        return values;
    }

    CompareCompletion completion() {
        return completion;
    }

    List<CompareProblem> problems() {
        return problems;
    }

    List<CompareLimitation> limitations() {
        return limitations;
    }

    Map<ComparePath, ComparePath> cycleReferences() {
        return cycleReferences;
    }

    Set<ComparePath> entityRoots() {
        return entityRoots;
    }

    Map<ComparePath, ComparePath> entityPositions() {
        return entityPositions;
    }

    Map<ComparePath, CanonicalSetSnapshot> setSnapshots() {
        return setSnapshots;
    }
}
