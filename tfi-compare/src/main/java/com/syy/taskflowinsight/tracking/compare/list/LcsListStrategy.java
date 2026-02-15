package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.algo.seq.LongestCommonSubsequence;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.syy.taskflowinsight.tracking.compare.CompareConstants.STRATEGY_LCS;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerElementEvent;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerType;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ElementOperation;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;

/**
 * LCS（最长公共子序列）列表比较策略
 * <p>
 * 基于LCS算法的列表比较，适用于小规模列表的精确变更追踪。
 * 超过阈值时自动降级，不抛异常。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M3
 * @since 2025-10-05
 */
@Component("lcsListStrategy")
public class LcsListStrategy implements ListCompareStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LcsListStrategy.class);

    /**
     * LCS算法推荐的最大列表大小
     */
    private static final int MAX_RECOMMENDED_SIZE = 300;

    @Override
    public String getStrategyName() {
        return STRATEGY_LCS;
    }

    @Override
    public int getMaxRecommendedSize() {
        return MAX_RECOMMENDED_SIZE;
    }

    @Override
    public boolean supportsMoveDetection() {
        // LCS 策略支持移动检测（基于公共子序列）
        return true;
    }

    @Override
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        if (list1 == list2) {
            return CompareResult.identical();
        }

        if (list1 == null || list2 == null) {
            return CompareResult.ofNullDiff(list1, list2);
        }

        int m = list1.size();
        int n = list2.size();

        // 超阈值检查（由 ListCompareExecutor 负责降级，这里仅标记）
        if (Math.max(m, n) > MAX_RECOMMENDED_SIZE) {
            logger.debug("LCS strategy: list size {}x{} exceeds max {}, degradation should be handled by executor",
                m, n, MAX_RECOMMENDED_SIZE);
            // 返回空结果，降级由执行器处理
            List<String> degradationReasons = new ArrayList<>();
            degradationReasons.add("LCS_SIZE_EXCEEDED:" + Math.max(m, n));
            return CompareResult.builder()
                .object1(list1)
                .object2(list2)
                .changes(new ArrayList<>())
                .identical(false)
                .degradationReasons(degradationReasons)
                .algorithmUsed(STRATEGY_LCS + "_DEGRADED")
                .build();
        }

        // 计算LCS（复用通用算法模块）
        int[][] lcsTable = LongestCommonSubsequence.lcsTable(list1, list2);

        // 生成变更记录
        List<FieldChange> changes = new ArrayList<>();
        extractChanges(list1, list2, lcsTable, changes, options != null && options.isDetectMoves());

        return CompareResult.builder()
            .object1(list1)
            .object2(list2)
            .changes(changes)
            .identical(changes.isEmpty())
            .algorithmUsed(STRATEGY_LCS)
            .build();
    }

    // DP 表由 LongestCommonSubsequence 提供

    /**
     * 从LCS表中提取变更记录
     */
    private void extractChanges(List<?> list1, List<?> list2, int[][] lcsTable, List<FieldChange> changes, boolean detectMoves) {
        int i = list1.size();
        int j = list2.size();

        // 第一遍：基于 LCS 回溯，生成 INSERT/DELETE（与原逻辑一致）
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && Objects.equals(list1.get(i - 1), list2.get(j - 1))) {
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcsTable[i][j - 1] >= lcsTable[i - 1][j])) {
                Object newVal = list2.get(j - 1);
                changes.add(FieldChange.builder()
                    .fieldName(PathUtils.buildListIndexPath(j - 1))
                    .changeType(ChangeType.CREATE)
                    .oldValue(null)
                    .newValue(newVal)
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents
                            .listAdd(j - 1, resolveEntityKey(null, newVal))
                    )
                    .build());
                j--;
            } else if (i > 0) {
                Object oldVal = list1.get(i - 1);
                changes.add(FieldChange.builder()
                    .fieldName(PathUtils.buildListIndexPath(i - 1))
                    .changeType(ChangeType.DELETE)
                    .oldValue(oldVal)
                    .newValue(null)
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents
                            .listRemove(i - 1, resolveEntityKey(oldVal, null))
                    )
                    .build());
                i--;
            }
        }

        // LCS回溯是逆序的，需要反转为自然顺序
        java.util.Collections.reverse(changes);

        // 第二遍：将可能的 DELETE + CREATE（相同元素）折叠为 MOVE，并填充 oldIndex/newIndex（仅 detectMoves=true 时）
        if (detectMoves) {
            coalesceMoves(list1, list2, changes);
        }
    }

    /**
     * 将同一元素的 DELETE + CREATE 折叠为 MOVE（仅处理两侧唯一出现的元素）
     */
    private void coalesceMoves(List<?> list1, List<?> list2, List<FieldChange> changes) {
        if (changes.isEmpty()) return;

        // 统计元素出现次数（用于避免重复元素造成的误判）
        java.util.Map<Object, Integer> freq1 = new java.util.HashMap<>();
        java.util.Map<Object, Integer> freq2 = new java.util.HashMap<>();
        for (Object o : list1) freq1.merge(o, 1, Integer::sum);
        for (Object o : list2) freq2.merge(o, 1, Integer::sum);

        // 建立候选映射：item -> 删除/新增 变更
        java.util.Map<Object, FieldChange> deletes = new java.util.HashMap<>();
        java.util.Map<Object, FieldChange> creates = new java.util.HashMap<>();
        for (FieldChange c : changes) {
            if (c.getChangeType() == ChangeType.DELETE && c.getOldValue() != null) {
                deletes.putIfAbsent(c.getOldValue(), c);
            } else if (c.getChangeType() == ChangeType.CREATE && c.getNewValue() != null) {
                creates.putIfAbsent(c.getNewValue(), c);
            }
        }

        java.util.List<FieldChange> moves = new java.util.ArrayList<>();
        java.util.Set<FieldChange> toRemove = new java.util.HashSet<>();

        for (java.util.Map.Entry<Object, FieldChange> e : deletes.entrySet()) {
            Object item = e.getKey();
            FieldChange del = e.getValue();
            FieldChange add = creates.get(item);
            if (add == null) continue;

            // 仅当元素在两侧都是唯一时，才将其视为移动
            if (freq1.getOrDefault(item, 0) == 1 && freq2.getOrDefault(item, 0) == 1) {
                Integer oldIdx = del.getElementEvent() != null ? del.getElementEvent().getIndex() : null;
                Integer newIdx = add.getElementEvent() != null ? add.getElementEvent().getIndex() : null;
                if (oldIdx != null && newIdx != null && !oldIdx.equals(newIdx)) {
                    moves.add(FieldChange.builder()
                        .fieldName("[" + oldIdx + "]")
                        .oldValue(item)
                        .newValue(item)
                        .changeType(ChangeType.MOVE)
                        .fieldPath("[" + newIdx + "]")
                        .elementEvent(
                            com.syy.taskflowinsight.tracking.compare.ContainerEvents
                                .listMove(oldIdx, newIdx, resolveEntityKey(item, item))
                        )
                        .build());
                    toRemove.add(del);
                    toRemove.add(add);
                }
            }
        }

        if (!moves.isEmpty()) {
            // 移除被折叠的 DELETE/CREATE，并追加 MOVE
            changes.removeIf(toRemove::contains);
            changes.addAll(moves);

            // 稳定排序：按索引 + 类型优先级（与 Levenshtein 对齐）
            changes.sort((a, b) -> {
                Integer ia = extractIndex(a.getFieldName());
                Integer ib = extractIndex(b.getFieldName());
                if (ia != null && ib != null) {
                    int cmp = ia.compareTo(ib);
                    if (cmp != 0) return cmp;
                    return getChangeTypePriority(a.getChangeType()) - getChangeTypePriority(b.getChangeType());
                }
                return a.getFieldName().compareTo(b.getFieldName());
            });
        }
    }

    /**
     * 从字段名中提取索引，例如 "[3]" -> 3
     */
    private Integer extractIndex(String fieldName) {
        if (fieldName != null && fieldName.startsWith("[") && fieldName.endsWith("]")) {
            try {
                return Integer.parseInt(fieldName.substring(1, fieldName.length() - 1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * 变更类型优先级（用于排序）：DELETE < MOVE < UPDATE < CREATE
     */
    private int getChangeTypePriority(com.syy.taskflowinsight.tracking.ChangeType type) {
        switch (type) {
            case DELETE:
                return 1;
            case MOVE:
                return 2;
            case UPDATE:
                return 3;
            case CREATE:
                return 4;
            default:
                return 5;
        }
    }

    private String resolveEntityKey(Object oldVal, Object newVal) {
        Object pick = newVal != null ? newVal : oldVal;
        if (pick == null) return null;
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(pick);
        return EntityKeyUtils.UNRESOLVED.equals(key) ? null : key;
    }
}
