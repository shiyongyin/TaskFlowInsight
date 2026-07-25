package com.syy.taskflowinsight.tracking.compare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 三方合并服务
 *
 * <p>从 {@link CompareService} 拆分而来，负责三方比较（base → left / base → right）
 * 的冲突检测与自动合并逻辑。
 *
 * <p>设计原则：
 * <ul>
 *   <li>无状态 — 依赖外部 {@link CompareService} 执行两两比较</li>
 *   <li>单一职责 — 仅负责冲突检测与合并决策</li>
 * </ul>
 *
 * @author Expert Panel - Senior Developer
 * @since 3.0.0
 */
public class ThreeWayMergeService {

    private static final Logger logger = LoggerFactory.getLogger(ThreeWayMergeService.class);

    private final CompareService compareService;

    /**
     * 构造三方合并服务。
     *
     * @param compareService 用于执行两两比较的服务
     */
    public ThreeWayMergeService(CompareService compareService) {
        this.compareService = compareService;
    }

    /**
     * 执行三方比较与合并。
     *
     * @param base    基准版本
     * @param left    左分支版本
     * @param right   右分支版本
     * @param options 比较选项
     * @return 合并结果（含冲突列表）
     */
    public MergeResult merge(Object base, Object left, Object right, CompareOptions options) {
        // 比较 base→left 的变更
        CompareResult leftChanges = compareService.compare(base, left, options);
        // 比较 base→right 的变更
        CompareResult rightChanges = compareService.compare(base, right, options);

        // 检测冲突
        List<MergeConflict> conflicts = detectConflicts(leftChanges, rightChanges);

        return MergeResult.builder()
                .base(base)
                .left(left)
                .right(right)
                .leftChanges(leftChanges.getChanges())
                .rightChanges(rightChanges.getChanges())
                .conflicts(conflicts)
                .merged(null)
                .autoMergeSuccessful(false)
                .build();
    }

    /**
     * 检测两侧变更的冲突。
     *
     * <p>冲突条件：同一字段被 left 和 right 同时修改为不同的值。
     *
     * @param leftResult  左侧比较结果
     * @param rightResult 右侧比较结果
     * @return 冲突列表
     */
    List<MergeConflict> detectConflicts(CompareResult leftResult, CompareResult rightResult) {
        List<MergeConflict> conflicts = new ArrayList<>();

        Map<String, FieldChange> leftMap = leftResult.getChanges().stream()
                .collect(Collectors.toMap(FieldChange::getFieldName, c -> c, (a, b) -> a));

        for (FieldChange rightChange : rightResult.getChanges()) {
            FieldChange leftChange = leftMap.get(rightChange.getFieldName());
            if (leftChange != null) {
                // 同一字段被两边修改
                if (!Objects.equals(leftChange.afterValue(), rightChange.afterValue())) {
                    conflicts.add(MergeConflict.builder()
                            .fieldName(rightChange.getFieldName())
                            .leftValue(leftChange.afterValue().orElse(null))
                            .rightValue(rightChange.afterValue().orElse(null))
                            .conflictType(ConflictType.VALUE_CONFLICT)
                            .build());
                }
            }
        }

        return conflicts;
    }

}
