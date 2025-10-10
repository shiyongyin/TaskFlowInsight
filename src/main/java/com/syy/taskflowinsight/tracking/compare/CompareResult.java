package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 比较结果（v3.1.0-P1 增强）
 * 
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 * @since 2025-01-13
 */
@Data
@Builder
public class CompareResult {
    
    private Object object1;
    private Object object2;
    
    @Builder.Default
    private List<FieldChange> changes = Collections.emptyList();

    private boolean identical;
    private Double similarity;
    private String report;
    private String patch;

    /**
     * 包含重复key的实体key集合（Entity列表比较专用）
     * 如果为空，表示无重复key（正常1:1比较）
     * @since v3.0.0
     */
    @Builder.Default
    private Set<String> duplicateKeys = Collections.emptySet();
    
    @Builder.Default
    private Instant compareTime = Instant.now();
    
    private long compareTimeMs;

    /**
     * 性能降级原因（仅数据透传，不在策略层打点）
     * 由上游执行器（如 ListCompareExecutor）在检测到 PerfGuard/规模等降级时填充，
     * 由 Facade/Engine 层统一上报指标。
     * @since v3.0.0-M2
     */
    @Builder.Default
    private java.util.List<String> degradationReasons = java.util.Collections.emptyList();

    /**
     * 使用的算法名称（M3新增）
     * <p>
     * 记录实际使用的比较算法/策略，用于指标统计和诊断。
     * 例如："LCS", "LEVENSHTEIN", "SIMPLE", "ENTITY"
     * </p>
     * @since v3.0.0-M3
     */
    private String algorithmUsed;
    
    /**
     * 创建相同结果
     */
    public static CompareResult identical() {
        return CompareResult.builder()
            .identical(true)
            .similarity(1.0)
            .changes(Collections.emptyList())
            .build();
    }
    
    /**
     * 创建null差异结果
     */
    public static CompareResult ofNullDiff(Object obj1, Object obj2) {
        return CompareResult.builder()
            .object1(obj1)
            .object2(obj2)
            .identical(false)
            .similarity(0.0)
            .build();
    }
    
    /**
     * 创建类型差异结果
     */
    public static CompareResult ofTypeDiff(Object obj1, Object obj2) {
        return CompareResult.builder()
            .object1(obj1)
            .object2(obj2)
            .identical(false)
            .similarity(0.0)
            .build();
    }
    
    /**
     * 获取变更数量
     */
    public int getChangeCount() {
        return changes != null ? changes.size() : 0;
    }
    
    /**
     * 是否有变更
     */
    public boolean hasChanges() {
        return !identical && getChangeCount() > 0;
    }
    
    /**
     * 获取相似度百分比
     */
    public double getSimilarityPercent() {
        return similarity != null ? similarity * 100 : 0;
    }

    /**
     * 是否存在重复key场景
     * @since v3.0.0
     */
    public boolean hasDuplicateKeys() {
        return duplicateKeys != null && !duplicateKeys.isEmpty();
    }

    // ========== P1-T3 新增查询方法 ==========

    /**
     * 按变更类型过滤（v3.1.0-P1）
     * <p>
     * 支持可变参数，可一次查询多个类型。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 单类型查询
     * List<FieldChange> creates = result.getChangesByType(ChangeType.CREATE);
     *
     * // 多类型查询
     * List<FieldChange> mutations = result.getChangesByType(
     *     ChangeType.CREATE, ChangeType.DELETE
     * );
     *
     * // 无参数查询（返回所有变更）
     * List<FieldChange> all = result.getChangesByType();
     * }</pre>
     *
     * @param types 变更类型（可变参数），为空时返回所有变更
     * @return 匹配的变更列表（不可变副本）
     * @since v3.1.0-P1
     */
    public List<FieldChange> getChangesByType(ChangeType... types) {
        if (changes == null) {
            return Collections.emptyList();
        }
        if (types == null || types.length == 0) {
            return Collections.unmodifiableList(changes);
        }

        Set<ChangeType> typeSet = Set.of(types);
        return changes.stream()
            .filter(c -> typeSet.contains(c.getChangeType()))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 获取所有引用变更（v3.1.0-P1）
     * <p>
     * 依赖 P1-T2 Reference Semantic，返回所有标记为 referenceChange=true 的变更。
     * </p>
     * <p>
     * <strong>注意</strong>：此方法依赖 P1-T2 尚未实现的 referenceChange 字段，
     * 当前返回空列表作为占位实现。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * List<FieldChange> refChanges = result.getReferenceChanges();
     * for (FieldChange change : refChanges) {
     *     System.out.println("Reference changed from " +
     *         change.getOldEntityKey() + " to " + change.getNewEntityKey());
     * }
     * }</pre>
     *
     * @return 引用变更列表（不可变副本）
     * @since v3.1.0-P1
     */
    public List<FieldChange> getReferenceChanges() {
        if (changes == null) {
            return Collections.emptyList();
        }
        return changes.stream()
            .filter(FieldChange::isReferenceChange)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 获取所有容器元素变更（v3.1.0-P1）
     * <p>
     * 依赖 P1-T1 Container Events，返回所有 elementEvent 非 null 的变更。
     * </p>
     * <p>
     * <strong>注意</strong>：此方法依赖 P1-T1 尚未实现的 elementEvent 字段，
     * 当前返回空列表作为占位实现。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * List<FieldChange> containerChanges = result.getContainerChanges();
     * for (FieldChange change : containerChanges) {
     *     ContainerElementEvent event = change.getElementEvent();
     *     System.out.println("Container: " + event.getContainerType());
     *     System.out.println("Operation: " + event.getOperation());
     *     System.out.println("Index: " + event.getIndex());
     * }
     * }</pre>
     *
     * @return 容器变更列表（不可变副本）
     * @since v3.1.0-P1
     */
    public List<FieldChange> getContainerChanges() {
        if (changes == null) {
            return Collections.emptyList();
        }
        return changes.stream()
            .filter(FieldChange::isContainerElementChange)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 按对象路径分组（v3.1.0-P1）
     * <p>
     * 将变更按对象路径前缀分组，便于查看每个对象的所有变更。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * Map<String, List<FieldChange>> byObject = result.groupByObject();
     *
     * // 输出：
     * // "order" -> [order.status, order.amount]
     * // "order.customer" -> [order.customer.name]
     * // "items[0]" -> [items[0].price, items[0].quantity]
     *
     * byObject.forEach((obj, objChanges) -> {
     *     System.out.println("Object: " + obj + ", Changes: " + objChanges.size());
     * });
     * }</pre>
     *
     * @return Map<对象路径, 变更列表>（不可变副本）
     * @since v3.1.0-P1
     */
    public Map<String, List<FieldChange>> groupByObject() {
        if (changes == null) {
            return Collections.emptyMap();
        }
        Map<String, List<FieldChange>> grouped = changes.stream()
            .collect(Collectors.groupingBy(
                this::extractObjectPath,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    Collections::unmodifiableList
                )
            ));
        return Collections.unmodifiableMap(grouped);
    }

    /**
     * 按属性名分组（v3.1.0-P1）
     * <p>
     * 将变更按字段名分组，便于查看所有对象的同名字段变更。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * Map<String, List<FieldChange>> byProperty = result.groupByProperty();
     *
     * // 输出：
     * // "price" -> [items[0].price, items[1].price]
     * // "status" -> [order.status]
     *
     * byProperty.get("price").forEach(change -> {
     *     System.out.println(change.getFieldPath() + ": " +
     *         change.getOldValue() + " -> " + change.getNewValue());
     * });
     * }</pre>
     *
     * @return Map<属性名, 变更列表>（不可变副本）
     * @since v3.1.0-P1
     */
    public Map<String, List<FieldChange>> groupByProperty() {
        if (changes == null) {
            return Collections.emptyMap();
        }
        Map<String, List<FieldChange>> grouped = changes.stream()
            .collect(Collectors.groupingBy(
                FieldChange::getFieldName,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    Collections::unmodifiableList
                )
            ));
        return Collections.unmodifiableMap(grouped);
    }

    /**
     * 按容器操作分组（v3.1.0-P1）
     * <p>
     * 仅针对容器变更，按 ElementOperation 分组。
     * 依赖 P1-T1 Container Events。
     * </p>
     * <p>
     * <strong>注意</strong>：此方法依赖 P1-T1 尚未实现的容器事件体系，
     * 当前返回空 Map 作为占位实现。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * Map<ElementOperation, List<FieldChange>> byOp = result.groupByContainerOperation();
     *
     * // 输出：
     * // ADD -> [items[2], items[3]]
     * // MODIFY -> [items[0].price, items[1].quantity]
     * // REMOVE -> [items[4]]
     *
     * long addedCount = byOp.getOrDefault(ElementOperation.ADD, Collections.emptyList()).size();
     * System.out.println("Added: " + addedCount);
     * }</pre>
     *
     * @return Map<操作类型, 变更列表>（不可变副本）
     * @since v3.1.0-P1
     */
    public Map<FieldChange.ElementOperation, List<FieldChange>> groupByContainerOperationTyped() {
        List<FieldChange> containerChanges = getContainerChanges();
        if (containerChanges.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<FieldChange.ElementOperation, List<FieldChange>> grouped = containerChanges.stream()
            .filter(fc -> fc.getElementEvent() != null && fc.getElementEvent().getOperation() != null)
            .collect(Collectors.groupingBy(
                fc -> fc.getElementEvent().getOperation(),
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    Collections::unmodifiableList
                )
            ));
        return Collections.unmodifiableMap(grouped);
    }

    /**
     * 按容器操作分组（强类型，主API）
     */
    public Map<FieldChange.ElementOperation, List<FieldChange>> groupByContainerOperation() {
        return groupByContainerOperationTyped();
    }

    

    /**
     * 过滤指定容器类型的变更（强类型版）
     * @param types 容器类型可变参数（为空时返回所有容器变更）
     * @return 匹配容器类型的变更列表
     * @since v3.1.0-P1
     */
    public List<FieldChange> getContainerChangesByType(FieldChange.ContainerType... types) {
        List<FieldChange> container = getContainerChanges();
        if (container.isEmpty()) {
            return Collections.emptyList();
        }
        if (types == null || types.length == 0) {
            return container;
        }
        java.util.Set<FieldChange.ContainerType> set = java.util.Set.of(types);
        return container.stream()
            .filter(fc -> fc.getElementEvent() != null && set.contains(fc.getElementEvent().getContainerType()))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 按变更类型统计数量（v3.1.0-P1）
     * <p>
     * 返回每种 ChangeType 的变更数量。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * Map<ChangeType, Long> counts = result.getChangeCountByType();
     *
     * // 输出：
     * // CREATE -> 5
     * // UPDATE -> 8
     * // DELETE -> 2
     *
     * long createCount = counts.getOrDefault(ChangeType.CREATE, 0L);
     * System.out.println("Created: " + createCount);
     * }</pre>
     *
     * @return Map<变更类型, 数量>（不可变副本）
     * @since v3.1.0-P1
     */
    public Map<ChangeType, Long> getChangeCountByType() {
        if (changes == null) {
            return Collections.emptyMap();
        }
        return changes.stream()
            .collect(Collectors.groupingBy(
                FieldChange::getChangeType,
                Collectors.counting()
            ));
    }

    /**
     * 按容器操作分组（字符串键，向后兼容某些调用方）
     * <p>
     * 建议使用 {@link #groupByContainerOperation()} 获取强类型结果；
     * 本方法仅做兼容封装。
     * </p>
     * <p>
     * 迁移声明：计划于 v3.2.0 移除该方法，请迁移至强类型版本。
     * </p>
     * @since v3.1.0-P1
     */
    @Deprecated
    public Map<String, List<FieldChange>> groupByContainerOperationAsString() {
        Map<FieldChange.ElementOperation, List<FieldChange>> typed = groupByContainerOperation();
        if (typed.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<FieldChange>> out = new java.util.LinkedHashMap<>();
        typed.forEach((k, v) -> out.put(k.name(), v));
        return Collections.unmodifiableMap(out);
    }

    /**
     * 美化输出（v3.1.0-P1）
     * <p>
     * 生成包含统计摘要的格式化报告，包含：
     * <ul>
     *   <li>总变更数</li>
     *   <li>按类型分类统计</li>
     *   <li>引用变更数（如有）</li>
     *   <li>容器操作统计（如有）</li>
     * </ul>
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * System.out.println(result.prettyPrint());
     *
     * // 输出示例：
     * // === Change Summary ===
     * // Total: 15 changes
     * //
     * //   CREATE: 5
     * //   UPDATE: 8
     * //   DELETE: 2
     * //
     * // Reference Changes: 3
     * //
     * // Container Operations:
     * //   ADD: 4
     * //   MODIFY: 3
     * //   REMOVE: 1
     * }</pre>
     *
     * @return 格式化的摘要字符串
     * @since v3.1.0-P1
     */
    public String prettyPrint() {
        if (changes == null || changes.isEmpty()) {
            return "=== Change Summary ===\nNo changes detected";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Change Summary ===\n");
        sb.append("Total: ").append(changes.size()).append(" changes\n\n");

        // 按类型统计
        Map<ChangeType, Long> byType = getChangeCountByType();
        if (!byType.isEmpty()) {
            byType.forEach((type, count) ->
                sb.append("  ").append(type).append(": ").append(count).append("\n")
            );
        }

        // 引用变更统计
        long refChanges = getReferenceChanges().size();
        if (refChanges > 0) {
            sb.append("\nReference Changes: ").append(refChanges).append("\n");
        }

        // 容器操作统计
        Map<FieldChange.ElementOperation, List<FieldChange>> containerOps = groupByContainerOperation();
        if (!containerOps.isEmpty()) {
            sb.append("\nContainer Operations:\n");
            containerOps.forEach((op, list) ->
                sb.append("  ").append(op.name()).append(": ").append(list.size()).append("\n")
            );
        }

        return sb.toString();
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从 FieldChange 提取对象路径
     *
     * @param change 字段变更
     * @return 对象路径（如 "order", "items[0]", "customer"）
     */
    private String extractObjectPath(FieldChange change) {
        // 优先使用容器事件的实体键或索引
        if (change.isContainerElementChange() && change.getElementEvent() != null) {
            String entityKey = change.getElementEvent().getEntityKey();
            if (entityKey != null) {
                return entityKey;
            }
            Integer index = change.getElementEvent().getIndex();
            if (index != null) {
                String base = change.getFieldName() != null ? change.getFieldName() : "list";
                return base + "[" + index + "]";
            }
        }

        // 使用 fieldPath（嵌套对象）
        String path = change.getFieldPath() != null
            ? change.getFieldPath() : change.getFieldName();

        // 提取第一级路径（如 "order.customer.name" -> "order"）
        int dotIndex = path.indexOf('.');
        return dotIndex > 0 ? path.substring(0, dotIndex) : path;
    }
}
