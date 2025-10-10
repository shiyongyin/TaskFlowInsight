package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.springframework.stereotype.Component;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerElementEvent;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerType;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ElementOperation;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;

import java.util.*;

/**
 * Levenshtein列表比较策略
 * 基于编辑距离算法，支持移动检测
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Component("levenshteinListStrategy")
public class LevenshteinListStrategy implements ListCompareStrategy {
    
    private static final int MAX_SIZE_THRESHOLD = 500;
    
    /**
     * 编辑操作类型
     */
    private enum Operation {
        MATCH,    // 匹配（无变更）
        INSERT,   // 插入
        DELETE,   // 删除
        REPLACE   // 替换
    }
    
    /**
     * 编辑距离结果
     */
    private static class EditDistanceResult {
        private final int distance;
        private final Operation[][] operations;
        private final List<?> list1;
        private final List<?> list2;
        
        public EditDistanceResult(int distance, Operation[][] operations, List<?> list1, List<?> list2) {
            this.distance = distance;
            this.operations = operations;
            this.list1 = list1;
            this.list2 = list2;
        }
        
        public int getDistance() { return distance; }
        public Operation[][] getOperations() { return operations; }
        public List<?> getList1() { return list1; }
        public List<?> getList2() { return list2; }
    }
    
    @Override
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        if (list1 == list2) {
            return CompareResult.identical();
        }
        
        if (list1 == null || list2 == null) {
            return CompareResult.ofNullDiff(list1, list2);
        }
        
        // 注意：大小检查已由ListCompareExecutor处理，这里直接执行算法
        
        // 计算编辑距离
        EditDistanceResult editResult = calculateEditDistance(list1, list2);
        
        // 生成变更记录
        List<FieldChange> changes;
        if (options.isDetectMoves()) {
            changes = processWithMoveDetection(editResult, options);
        } else {
            changes = processWithoutMoves(editResult, options);
        }
        
        // 按照下标排序，确保输出顺序一致
        // 相同索引时，按操作类型排序
        changes.sort((a, b) -> {
            Integer indexA = extractIndex(a.getFieldName());
            Integer indexB = extractIndex(b.getFieldName());
            if (indexA != null && indexB != null) {
                int cmp = indexA.compareTo(indexB);
                if (cmp != 0) {
                    return cmp;
                }
                // 相同索引，按操作类型排序：DELETE < MOVE < UPDATE < CREATE
                return getChangeTypePriority(a.getChangeType()) -
                       getChangeTypePriority(b.getChangeType());
            }
            return a.getFieldName().compareTo(b.getFieldName());
        });

        return CompareResult.builder()
            .object1(list1)
            .object2(list2)
            .changes(changes)
            .identical(changes.isEmpty())
            .algorithmUsed("LEVENSHTEIN")
            .build();
    }
    
    /**
     * 计算编辑距离
     */
    private EditDistanceResult calculateEditDistance(List<?> list1, List<?> list2) {
        int m = list1.size();
        int n = list2.size();
        
        // 动态规划表
        int[][] dp = new int[m + 1][n + 1];
        Operation[][] operations = new Operation[m + 1][n + 1];
        
        // 初始化边界
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
            operations[i][0] = Operation.DELETE;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
            operations[0][j] = Operation.INSERT;
        }
        
        // 填充DP表
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                Object item1 = list1.get(i - 1);
                Object item2 = list2.get(j - 1);
                
                if (Objects.equals(item1, item2)) {
                    dp[i][j] = dp[i - 1][j - 1];
                    operations[i][j] = Operation.MATCH;
                } else {
                    // 选择成本最低的操作
                    int deleteCost = dp[i - 1][j] + 1;
                    int insertCost = dp[i][j - 1] + 1;
                    int replaceCost = dp[i - 1][j - 1] + 1;
                    
                    if (deleteCost <= insertCost && deleteCost <= replaceCost) {
                        dp[i][j] = deleteCost;
                        operations[i][j] = Operation.DELETE;
                    } else if (insertCost <= replaceCost) {
                        dp[i][j] = insertCost;
                        operations[i][j] = Operation.INSERT;
                    } else {
                        dp[i][j] = replaceCost;
                        operations[i][j] = Operation.REPLACE;
                    }
                }
            }
        }
        
        return new EditDistanceResult(dp[m][n], operations, list1, list2);
    }
    
    /**
     * 不带移动检测的处理
     */
    private List<FieldChange> processWithoutMoves(EditDistanceResult result, CompareOptions options) {
        List<FieldChange> changes = new ArrayList<>();
        Operation[][] operations = result.getOperations();
        List<?> list1 = result.getList1();
        List<?> list2 = result.getList2();
        
        // 回溯操作路径
        int i = list1.size();
        int j = list2.size();
        
        while (i > 0 || j > 0) {
            Operation op = operations[i][j];
            
            switch (op) {
                case DELETE:
                    Object delVal = list1.get(i - 1);
                    changes.add(FieldChange.builder()
                        .fieldName("[" + (i - 1) + "]")
                        .oldValue(delVal)
                        .newValue(null)
                        .changeType(ChangeType.DELETE)
                        .elementEvent(
                            com.syy.taskflowinsight.tracking.compare.ContainerEvents
                                .listRemove(i - 1, resolveEntityKey(delVal))
                        )
                        .build());
                    i--;
                    break;
                    
                case INSERT:
                    Object insVal = list2.get(j - 1);
                    changes.add(FieldChange.builder()
                        .fieldName("[" + (j - 1) + "]")
                        .oldValue(null)
                        .newValue(insVal)
                        .changeType(ChangeType.CREATE)
                        .elementEvent(
                            com.syy.taskflowinsight.tracking.compare.ContainerEvents
                                .listAdd(j - 1, resolveEntityKey(insVal))
                        )
                        .build());
                    j--;
                    break;
                    
                case REPLACE:
                    Object oldVal = list1.get(i - 1);
                    Object newVal = list2.get(j - 1);
                    changes.add(FieldChange.builder()
                        .fieldName("[" + (i - 1) + "]")
                        .oldValue(oldVal)
                        .newValue(newVal)
                        .changeType(ChangeType.UPDATE)
                        .elementEvent(
                            com.syy.taskflowinsight.tracking.compare.ContainerEvents
                                .listModify(i - 1, resolveEntityKey(newVal != null ? newVal : oldVal), null)
                        )
                        .build());
                    i--;
                    j--;
                    break;
                    
                case MATCH:
                    // 无变更
                    i--;
                    j--;
                    break;
            }
        }
        
        // 反转结果，因为是从后往前回溯的
        Collections.reverse(changes);
        return changes;
    }
    
    /**
     * 带移动检测的处理
     */
    private List<FieldChange> processWithMoveDetection(EditDistanceResult result, CompareOptions options) {
        List<?> list1 = result.getList1();
        List<?> list2 = result.getList2();
        
        // 首先识别可能的移动操作
        Set<Object> movedItems = identifyMovedItems(list1, list2);
        
        // 基于移动检测生成变更记录
        List<FieldChange> changes = new ArrayList<>();
        
        // 建立元素位置映射
        Map<Object, Integer> list1Positions = buildPositionMap(list1);
        Map<Object, Integer> list2Positions = buildPositionMap(list2);
        
        // 处理删除和移动
        for (int i = 0; i < list1.size(); i++) {
            Object item = list1.get(i);
            if (!list2Positions.containsKey(item)) {
                // 元素被删除
                changes.add(FieldChange.builder()
                    .fieldName("[" + i + "]")
                    .oldValue(item)
                    .newValue(null)
                    .changeType(ChangeType.DELETE)
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents
                            .listRemove(i, resolveEntityKey(item))
                    )
                    .build());
            } else if (movedItems.contains(item)) {
                // 元素被移动
                int newPosition = list2Positions.get(item);
                changes.add(FieldChange.builder()
                    .fieldName("[" + i + "]")
                    .oldValue(item)
                    .newValue(item)
                    .changeType(ChangeType.MOVE)
                    .fieldPath("[" + newPosition + "]")
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents
                            .listMove(i, newPosition, resolveEntityKey(item))
                    )
                    .build());
            }
        }
        
        // 处理新增
        for (int j = 0; j < list2.size(); j++) {
            Object item = list2.get(j);
            if (!list1Positions.containsKey(item)) {
                // 元素被新增
                changes.add(FieldChange.builder()
                    .fieldName("[" + j + "]")
                    .oldValue(null)
                    .newValue(item)
                    .changeType(ChangeType.CREATE)
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents
                            .listAdd(j, resolveEntityKey(item))
                    )
                    .build());
            }
        }
        
        return changes;
    }
    
    /**
     * 从字段名中提取索引
     * 例如: "[1]" -> 1
     */
    private Integer extractIndex(String fieldName) {
        if (fieldName != null && fieldName.startsWith("[") && fieldName.endsWith("]")) {
            try {
                return Integer.parseInt(fieldName.substring(1, fieldName.length() - 1));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        return null;
    }

    /**
     * 获取变更类型的优先级（用于排序）
     * DELETE < MOVE < UPDATE < CREATE
     */
    private int getChangeTypePriority(ChangeType type) {
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

    /**
     * 识别移动的元素
     */
    private Set<Object> identifyMovedItems(List<?> list1, List<?> list2) {
        Set<Object> moved = new HashSet<>();
        Map<Object, Integer> pos1 = buildPositionMap(list1);
        Map<Object, Integer> pos2 = buildPositionMap(list2);
        
        for (Object item : pos1.keySet()) {
            if (pos2.containsKey(item)) {
                Integer oldPos = pos1.get(item);
                Integer newPos = pos2.get(item);
                // 简单启发式：位置发生变化且元素在两个列表中都唯一
                if (!oldPos.equals(newPos) && 
                    Collections.frequency(list1, item) == 1 && 
                    Collections.frequency(list2, item) == 1) {
                    moved.add(item);
                }
            }
        }
        
        return moved;
    }
    
    /**
     * 构建元素位置映射（处理重复元素时取第一次出现的位置）
     */
    private Map<Object, Integer> buildPositionMap(List<?> list) {
        Map<Object, Integer> positions = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            positions.putIfAbsent(item, i); // 只记录第一次出现的位置
        }
        return positions;
    }
    
    @Override
    public boolean supportsMoveDetection() {
        return true;
    }
    
    @Override
    public String getStrategyName() {
        return "LEVENSHTEIN";
    }
    
    @Override
    public int getMaxRecommendedSize() {
        return MAX_SIZE_THRESHOLD;
    }

    private String resolveEntityKey(Object val) {
        if (val == null) return null;
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(val);
        return EntityKeyUtils.UNRESOLVED.equals(key) ? null : key;
    }
}
