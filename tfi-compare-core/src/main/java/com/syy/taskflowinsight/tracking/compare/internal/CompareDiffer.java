package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareProblem;
import com.syy.taskflowinsight.tracking.compare.CompareProblemCode;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * request-local typed snapshot之间的唯一差异编排owner。
 *
 * <p>该类型保持package-private且不进入Runtime字段或公共SPI；它只能消费同一请求的snapshot、
 * ledger和accumulator，避免调用方重新选择diff graph或重置预算。</p>
 *
 * @since 4.0.0
 */
final class CompareDiffer {

    private CompareDiffer() {
    }

    static void diff(
            SnapshotResult beforeSnapshot,
            SnapshotResult afterSnapshot,
            CompareRequestState state,
            CompareOptions options,
            Function<ComparePath, Optional<Boolean>> equalityOverride) {
        Map<ComparePath, ValueSnapshot> before = beforeSnapshot.values();
        Map<ComparePath, ValueSnapshot> after = afterSnapshot.values();
        pairEntities(beforeSnapshot, afterSnapshot, state);
        Set<ComparePath> paths = new TreeSet<>(ComparePath.canonicalOrder());
        paths.addAll(before.keySet());
        paths.addAll(after.keySet());
        for (ComparePath path : paths) {
            if (state.deadlineReached()) {
                addLimitation(state, CompareLimitationCode.DEADLINE_REACHED, path);
                break;
            }
            boolean admitted = state.admit(
                    BudgetEvent.DIFF_NODE,
                    () -> appendChange(
                            path,
                            beforeSnapshot,
                            afterSnapshot,
                            state.accumulator(),
                            options,
                            equalityOverride));
            if (!admitted) {
                addLimitation(state, CompareLimitationCode.NODE_BUDGET_REACHED, path);
                break;
            }
            if (state.deadlineReached()) {
                addLimitation(state, CompareLimitationCode.DEADLINE_REACHED, path);
                break;
            }
        }
    }

    /**
     * 对两侧共有的exact Entity identity逐个消费PAIR_CANDIDATE，并独立发布List位置变化。
     *
     * <p>候选只决定哪两个逻辑根可以继续由typed path深比较；这里不读取业务equals，也不会因为
     * 位置相同而跳过后续字段diff。</p>
     */
    private static void pairEntities(
            SnapshotResult beforeSnapshot,
            SnapshotResult afterSnapshot,
            CompareRequestState state) {
        if (beforeSnapshot.entityRoots().isEmpty() || afterSnapshot.entityRoots().isEmpty()) {
            // 非 Entity 图没有候选配对事实，避免为每个 target 创建空排序集合。
            return; // NOPMD - 无候选时立即返回是该热路径避免分配空排序集合的前提。
        }
        Set<ComparePath> commonRoots = new TreeSet<>(ComparePath.canonicalOrder());
        commonRoots.addAll(beforeSnapshot.entityRoots());
        commonRoots.retainAll(afterSnapshot.entityRoots());
        for (ComparePath logicalRoot : commonRoots) {
            if (state.deadlineReached()) {
                addLimitation(state, CompareLimitationCode.DEADLINE_REACHED, logicalRoot);
                return;
            }
            boolean admitted = state.admit(
                    BudgetEvent.PAIR_CANDIDATE,
                    () -> appendMoveIfNeeded(
                            logicalRoot, beforeSnapshot, afterSnapshot, state.accumulator()));
            if (!admitted) {
                addLimitation(state, CompareLimitationCode.NODE_BUDGET_REACHED, logicalRoot);
                return;
            }
        }
    }

    private static void appendMoveIfNeeded(
            ComparePath logicalRoot,
            SnapshotResult beforeSnapshot,
            SnapshotResult afterSnapshot,
            CompareResultAccumulator accumulator) {
        ComparePath beforePosition = beforeSnapshot.entityPositions().get(logicalRoot);
        ComparePath afterPosition = afterSnapshot.entityPositions().get(logicalRoot);
        if (beforePosition == null || afterPosition == null || beforePosition.equals(afterPosition)) {
            return;
        }
        ValueSnapshot beforeValue = beforeSnapshot.values().get(logicalRoot);
        ValueSnapshot afterValue = afterSnapshot.values().get(logicalRoot);
        if (beforeValue == null || afterValue == null) {
            return;
        }
        accumulator.addChange(FieldChange.canonical(
                ChangeKind.MOVE,
                Optional.of(new ChangeSide(beforePosition, beforeValue)),
                Optional.of(new ChangeSide(afterPosition, afterValue))));
    }

    private static void appendChange(
            ComparePath path,
            SnapshotResult beforeSnapshot,
            SnapshotResult afterSnapshot,
            CompareResultAccumulator accumulator,
            CompareOptions options,
            Function<ComparePath, Optional<Boolean>> equalityOverride) {
        Map<ComparePath, ValueSnapshot> before = beforeSnapshot.values();
        Map<ComparePath, ValueSnapshot> after = afterSnapshot.values();
        boolean hasBefore = before.containsKey(path);
        boolean hasAfter = after.containsKey(path);
        if (hasBefore && !hasAfter
                && afterSnapshot.completion() != CompareCompletion.COMPLETE) {
            return;
        }
        if (!hasBefore && hasAfter
                && beforeSnapshot.completion() != CompareCompletion.COMPLETE) {
            return;
        }
        ValueSnapshot beforeValue = before.get(path);
        ValueSnapshot afterValue = after.get(path);
        if (hasBefore && hasAfter) {
            CanonicalSetSnapshot beforeSet = beforeSnapshot.setSnapshots().get(path);
            CanonicalSetSnapshot afterSet = afterSnapshot.setSnapshots().get(path);
            boolean complexSetDifference = beforeSet != null
                    && afterSet != null
                    && beforeSet.canProveDifference(afterSet);
            Optional<Boolean> overridden;
            try {
                overridden = equalityOverride.apply(path);
            } catch (RuntimeException exception) {
                // 扩展失败仅污染当前字段；继续兄弟节点才能保留已知和后续差异。
                accumulator.addProblem(new CompareProblem(
                        CompareProblemCode.DIFF_FAILED,
                        CompareStage.DIFF,
                        Optional.of(path)));
                return;
            }
            if (overridden.isPresent()) {
                if (overridden.orElseThrow()) {
                    return;
                }
            } else if (!complexSetDifference && valuesEqual(beforeValue, afterValue, options)) {
                if (beforeValue.representation() != ValueSnapshot.Representation.EXACT) {
                    // 相同降级表示不等于原始值相等，必须保留证据限制。
                    accumulator.addLimitation(new CompareLimitation(
                            CompareLimitationCode.RESULT_DETAIL_LIMIT_REACHED,
                            CompareStage.DIFF,
                            Optional.of(path)));
                    return;
                }
                if (!Objects.equals(
                        beforeSnapshot.cycleReferences().get(path),
                        afterSnapshot.cycleReferences().get(path))) {
                    // pair memo收敛前，不同回指目标不能靠相同type fact证明相等。
                    accumulator.addProblem(new CompareProblem(
                            CompareProblemCode.DIFF_FAILED,
                            CompareStage.DIFF,
                            Optional.of(path)));
                }
                return;
            }
        }
        ChangeKind kind = hasBefore
                ? (hasAfter ? ChangeKind.MODIFY : ChangeKind.REMOVE)
                : ChangeKind.ADD;
        Optional<ChangeSide> beforeSide = hasBefore
                ? Optional.of(new ChangeSide(path, beforeValue))
                : Optional.empty();
        Optional<ChangeSide> afterSide = hasAfter
                ? Optional.of(new ChangeSide(path, afterValue))
                : Optional.empty();
        accumulator.addChange(FieldChange.canonical(kind, beforeSide, afterSide));
    }

    private static boolean valuesEqual(
            ValueSnapshot before,
            ValueSnapshot after,
            CompareOptions options) {
        if (Objects.equals(before, after)) {
            return true;
        }
        if (before.representation() != ValueSnapshot.Representation.EXACT
                || after.representation() != ValueSnapshot.Representation.EXACT
                || !before.typeCode().equals(after.typeCode())) {
            return false;
        }
        if (before.typeCode().equals("big-decimal")) {
            return decimalValuesEqual(before, after, options);
        }
        if (before.typeCode().equals("float") || before.typeCode().equals("double")) {
            return floatingValuesEqual(before, after, options);
        }
        if (isToleranceTemporal(before.typeCode())) {
            return temporalValuesEqual(before, after, options.temporalTolerance());
        }
        return false;
    }

    private static boolean temporalValuesEqual(
            ValueSnapshot before,
            ValueSnapshot after,
            Duration tolerance) {
        try {
            Duration difference = switch (before.typeCode()) {
                case "date", "instant" -> Duration.between(
                        Instant.parse(before.canonicalTextFacts().getFirst()),
                        Instant.parse(after.canonicalTextFacts().getFirst()));
                case "local-date-time" -> Duration.between(
                        LocalDateTime.parse(before.canonicalTextFacts().getFirst()),
                        LocalDateTime.parse(after.canonicalTextFacts().getFirst()));
                case "duration" -> Duration.parse(before.canonicalTextFacts().getFirst())
                        .minus(Duration.parse(after.canonicalTextFacts().getFirst()));
                default -> throw new IllegalStateException("unsupported temporal type code");
            };
            return absolute(difference).compareTo(tolerance) <= 0;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static Duration absolute(Duration value) {
        return value.isNegative() ? value.negated() : value;
    }

    private static boolean isToleranceTemporal(String typeCode) {
        return typeCode.equals("date")
                || typeCode.equals("instant")
                || typeCode.equals("local-date-time")
                || typeCode.equals("duration");
    }

    private static boolean decimalValuesEqual(
            ValueSnapshot before,
            ValueSnapshot after,
            CompareOptions options) {
        BigDecimal left = decimalValue(before);
        BigDecimal right = decimalValue(after);
        BigDecimal difference = left.subtract(right).abs();
        BigDecimal relative = left.abs().max(right.abs())
                .multiply(BigDecimal.valueOf(options.numericRelativeTolerance()));
        BigDecimal tolerance = options.numericAbsoluteTolerance().max(relative);
        return difference.compareTo(tolerance) <= 0;
    }

    private static boolean floatingValuesEqual(
            ValueSnapshot before,
            ValueSnapshot after,
            CompareOptions options) {
        double left = floatingValue(before);
        double right = floatingValue(after);
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return Double.isNaN(left) && Double.isNaN(right);
        }
        if (Double.isInfinite(left) || Double.isInfinite(right)) {
            return left == right;
        }
        double difference = Math.abs(left - right);
        double relative = options.numericRelativeTolerance()
                * Math.max(Math.abs(left), Math.abs(right));
        double tolerance = Math.max(options.numericAbsoluteTolerance().doubleValue(), relative);
        return difference <= tolerance;
    }

    private static double floatingValue(ValueSnapshot snapshot) {
        String token = snapshot.canonicalTextFacts().getFirst();
        return switch (token) {
            case "nan" -> Double.NaN;
            case "+infinity" -> Double.POSITIVE_INFINITY;
            case "-infinity" -> Double.NEGATIVE_INFINITY;
            default -> snapshot.typeCode().equals("float")
                    ? Float.parseFloat(token)
                    : Double.parseDouble(token);
        };
    }

    private static BigDecimal decimalValue(ValueSnapshot snapshot) {
        List<String> facts = snapshot.canonicalTextFacts();
        return new BigDecimal(new BigInteger(facts.get(0)), Integer.parseInt(facts.get(1)));
    }

    private static void addLimitation(
            CompareRequestState state,
            CompareLimitationCode code,
            ComparePath path) {
        state.accumulator().addLimitation(new CompareLimitation(
                code, CompareStage.DIFF, Optional.of(path)));
    }
}
