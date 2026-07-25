package com.syy.taskflowinsight.tracking.determinism;

import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.List;
import java.util.Optional;

/**
 * 按canonical typed path稳定排序变更事实。
 *
 * <p>排序必须复用内核地址事实，不能把安全display path重新解析为业务key；
 * 否则动态key会被占位符折叠，locale或字符串语法也可能改变结果顺序。
 * 该实现无缓存、无clear入口，避免排序阶段形成第二个path owner。</p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M1
 * @since 2025-10-04
 */
public final class StableSorter {
    private StableSorter() {
    }

    /**
     * 对变更事实进行typed稳定排序。
     *
     * @param changes 待排序的canonical变更事实
     * @return 不修改输入的不可变有序列表
     */
    public static List<FieldChange> sortByFieldChange(List<FieldChange> changes) {
        return changes.stream().sorted(StableSorter::compareChanges).toList();
    }

    private static int compareChanges(FieldChange left, FieldChange right) {
        int compared = ComparePath.canonicalOrder().compare(primaryPath(left), primaryPath(right));
        if (compared != 0) {
            return compared;
        }
        compared = compareOptionalPath(left.before(), right.before());
        if (compared != 0) {
            return compared;
        }
        compared = compareOptionalPath(left.after(), right.after());
        return compared != 0 ? compared : Integer.compare(kindOrder(left.kind()), kindOrder(right.kind()));
    }

    private static ComparePath primaryPath(FieldChange change) {
        return change.after().or(change::before).orElseThrow().path();
    }

    private static int compareOptionalPath(
            Optional<ChangeSide> left,
            Optional<ChangeSide> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return Boolean.compare(left.isPresent(), right.isPresent());
        }
        return ComparePath.canonicalOrder().compare(
                left.orElseThrow().path(), right.orElseThrow().path());
    }

    private static int kindOrder(ChangeKind kind) {
        return switch (kind) {
            case ADD -> 0;
            case REMOVE -> 1;
            case MODIFY -> 2;
            case MOVE -> 3;
            case NULLNESS -> 4;
            case TYPE_MISMATCH -> 5;
        };
    }
}
