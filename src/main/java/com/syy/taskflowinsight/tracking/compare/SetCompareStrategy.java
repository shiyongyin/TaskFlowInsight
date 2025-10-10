package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.util.DiagnosticLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Set集合比较策略（支持 Set<Entity> 排序委托）
 * <p>
 * 当 Set 元素为 @Entity 时，按 @Key 排序转为 List，委托 EntityListStrategy 对比。
 * 非 Entity Set 保持原有增删逻辑。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class SetCompareStrategy implements DetailedCompareStrategy<Set<?>> {

    private static final Logger logger = LoggerFactory.getLogger(SetCompareStrategy.class);
    private final EntityListStrategy entityListStrategy;

    /**
     * 构造函数（默认创建 EntityListStrategy）
     */
    public SetCompareStrategy() {
        this.entityListStrategy = new EntityListStrategy();
    }

    /**
     * 构造函数（支持注入 EntityListStrategy）
     */
    public SetCompareStrategy(EntityListStrategy entityListStrategy) {
        this.entityListStrategy = entityListStrategy;
    }

    @Override
    public CompareResult compare(Set<?> set1, Set<?> set2, CompareOptions options) {
        if (set1 == set2) {
            return CompareResult.identical();
        }

        if (set1 == null || set2 == null) {
            return CompareResult.ofNullDiff(set1, set2);
        }

        // 检测是否为 Entity Set
        if (isEntitySet(set1, set2)) {
            return compareEntitySet(set1, set2, options);
        }

        // 非 Entity Set：原有简单增删逻辑
        return compareSimpleSet(set1, set2, options);
    }

    /**
     * Entity Set 比较（排序 + 重复键检测 + 委托）
     */
    private CompareResult compareEntitySet(Set<?> set1, Set<?> set2, CompareOptions options) {
        try {
            // 排序转 List（稳定排序）
            List<?> list1 = toStablySortedList(set1);
            List<?> list2 = toStablySortedList(set2);

            // 检测重复键
            detectDuplicateKeys(list1, options, "set1");
            detectDuplicateKeys(list2, options, "set2");

            // 构建紧凑→命名映射
            java.util.Map<String,String> compactToNamed = new java.util.HashMap<>();
            for (Object e : list1) { buildKeyMapping(e, compactToNamed); }
            for (Object e : list2) { buildKeyMapping(e, compactToNamed); }

            // 委托 EntityListStrategy
            CompareResult delegated = entityListStrategy.compare((List<Object>) list1, (List<Object>) list2, options);

            // 转换路径中的紧凑键为命名键（满足 entity[id=100].* 期望），并将事件统一映射为 SET 容器事件
            java.util.List<FieldChange> transformed = new java.util.ArrayList<>();
            if (delegated.getChanges() != null) {
                for (FieldChange fc : delegated.getChanges()) {
                    String inner = (fc.getFieldPath() != null ? fc.getFieldPath() : fc.getFieldName());
                    String t = transformEntityKeyInPath(inner, compactToNamed);

                    // 统一事件为 SET：保留 operation/propertyPath/duplicateKey/entityKey
                    FieldChange.ContainerElementEvent src = fc.getElementEvent();
                    FieldChange.ContainerElementEvent mapped = null;
                    if (src != null) {
                        // 仅对非 MODIFY 事件进行 LIST->SET 映射；
                        // 对 MODIFY（内部字段变更）保持原事件以确保 propertyPath 兼容性
                        if (src.getOperation() != FieldChange.ElementOperation.MODIFY) {
                            FieldChange.ElementOperation op = src.getOperation();
                            String entityKey = src.getEntityKey();
                            String propertyPath = src.getPropertyPath();
                            boolean dup = src.isDuplicateKey();
                            mapped = com.syy.taskflowinsight.tracking.compare.ContainerEvents
                                .setEvent(op, entityKey, propertyPath, dup);
                        }
                    }
                    FieldChange copy = FieldChange.builder()
                        .fieldName(t)
                        .fieldPath(t)
                        .oldValue(fc.getOldValue())
                        .newValue(fc.getNewValue())
                        .changeType(fc.getChangeType())
                        .valueType(fc.getValueType())
                        .collectionChange(fc.isCollectionChange())
                        .collectionDetail(fc.getCollectionDetail())
                        .elementEvent(mapped != null ? mapped : fc.getElementEvent())
                        .build();
                    transformed.add(copy);
                }
            }

            // 返回结果（保留原 Set 引用）
            return CompareResult.builder()
                .object1(set1)
                .object2(set2)
                .changes(transformed)
                .identical(delegated.isIdentical())
                .similarity(delegated.getSimilarity())
                .report(delegated.getReport())
                .patch(delegated.getPatch())
                .build();

        } catch (IllegalArgumentException e) {
            // 严格模式异常：直接抛出，不降级
            throw e;
        } catch (Exception e) {
            // 其他异常：降级到简单 Set
            DiagnosticLogger.once(
                "SET-001",
                "EntitySetCompareFailed",
                e.getMessage(),
                "Fallback to simple Set comparison"
            );
            logger.debug("Entity Set compare failed, fallback to simple Set", e);
            return compareSimpleSet(set1, set2, options);
        }
    }

    /**
     * 简单 Set 比较（原有逻辑）
     */
    private CompareResult compareSimpleSet(Set<?> set1, Set<?> set2, CompareOptions options) {
        List<FieldChange> changes = new ArrayList<>();

        // 分析集合变化
        Set<Object> added = new HashSet<>(set2);
        added.removeAll(set1);

        Set<Object> removed = new HashSet<>(set1);
        removed.removeAll(set2);

        // 为每个新增元素生成一个变更记录
        for (Object addedElement : added) {
            FieldChange change = FieldChange.builder()
                .fieldName("element")
                .oldValue(null)
                .newValue(addedElement)
                .changeType(ChangeType.CREATE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .setEvent(FieldChange.ElementOperation.ADD, resolveEntityKey(addedElement), null, false)
                )
                .build();
            changes.add(change);
        }

        // 为每个删除元素生成一个变更记录
        for (Object removedElement : removed) {
            FieldChange change = FieldChange.builder()
                .fieldName("element")
                .oldValue(removedElement)
                .newValue(null)
                .changeType(ChangeType.DELETE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .setEvent(FieldChange.ElementOperation.REMOVE, resolveEntityKey(removedElement), null, false)
                )
                .build();
            changes.add(change);
        }

        // 构建结果
        boolean isIdentical = added.isEmpty() && removed.isEmpty();
        CompareResult.CompareResultBuilder builder = CompareResult.builder()
            .object1(set1)
            .object2(set2)
            .changes(changes)
            .identical(isIdentical);

        // 计算相似度
        if (options.isCalculateSimilarity()) {
            double similarity = calculateSimilarity(set1, set2);
            builder.similarity(similarity);
        }

        // 生成报告
        if (options.isGenerateReport()) {
            String report = generateSetReport(set1, set2, added, removed, options);
            builder.report(report);
        }

        return builder.build();
    }

    @Override
    public String getName() {
        return "SetCompare";
    }

    @Override
    public boolean supports(Class<?> type) {
        return Set.class.isAssignableFrom(type);
    }

    @Override
    public List<ChangeRecord> generateDetailedChangeRecords(
            String objectName,
            String fieldName,
            Set<?> oldValue,
            Set<?> newValue,
            String sessionId,
            String taskPath) {

        List<ChangeRecord> detailedChanges = new ArrayList<>();

        if (oldValue == null && newValue == null) {
            return detailedChanges;
        }

        Set<?> set1 = oldValue != null ? oldValue : Collections.emptySet();
        Set<?> set2 = newValue != null ? newValue : Collections.emptySet();

        // 检测是否为 Entity Set
        if (isEntitySet(set1, set2)) {
            try {
                // 排序转 List
                List<?> list1 = toStablySortedList(set1);
                List<?> list2 = toStablySortedList(set2);

                // 检测重复键（宽松模式，仅诊断）
                CompareOptions looseOptions = CompareOptions.builder().strictDuplicateKey(false).build();
                detectDuplicateKeys(list1, looseOptions, "set1");
                detectDuplicateKeys(list2, looseOptions, "set2");

                // 预构建紧凑键 → 命名键 映射，用于将 entity[compact] 转为 entity[named]
                java.util.Map<String,String> compactToNamed = new java.util.HashMap<>();
                for (Object e : list1) { buildKeyMapping(e, compactToNamed); }
                for (Object e : list2) { buildKeyMapping(e, compactToNamed); }

                // 委托 EntityListStrategy 比较
                CompareResult result = entityListStrategy.compare((List<Object>) list1, (List<Object>) list2, looseOptions);

                // 将 FieldChange 转换为 ChangeRecord（valueKind 标注为 SET_ENTITY），并将路径中的紧凑键替换为命名键
                for (FieldChange fc : result.getChanges()) {
                    String innerPath = (fc.getFieldPath() != null ? fc.getFieldPath() : fc.getFieldName());
                    String transformedInner = transformEntityKeyInPath(innerPath, compactToNamed);
                    String fullFieldName = fieldName + "." + transformedInner;
                    ChangeRecord rec = ChangeRecord.builder()
                        .objectName(objectName)
                        .fieldName(fullFieldName)
                        .changeType(fc.getChangeType())
                        .oldValue(fc.getOldValue())
                        .newValue(fc.getNewValue())
                        .sessionId(sessionId)
                        .taskPath(taskPath)
                        .valueType(fc.getNewValue() != null ? fc.getNewValue().getClass().getName() :
                                  (fc.getOldValue() != null ? fc.getOldValue().getClass().getName() : "Object"))
                        .valueKind("SET_ENTITY")
                        .build();
                    detailedChanges.add(rec);
                }

                return detailedChanges;

            } catch (Exception e) {
                // 降级到简单 Set
                logger.debug("Entity Set generateDetailedChangeRecords failed, fallback", e);
            }
        }

        // 非 Entity Set：原有逻辑
        Set<Object> added = new HashSet<>(set2);
        added.removeAll(set1);

        Set<Object> removed = new HashSet<>(set1);
        removed.removeAll(set2);

        // 为每个新增元素生成一个ChangeRecord
        for (Object addedElement : added) {
            String uniqueFieldName = fieldName + ".element[" + addedElement + "]";
            ChangeRecord elementChange = ChangeRecord.builder()
                .objectName(objectName)
                .fieldName(uniqueFieldName)
                .changeType(ChangeType.CREATE)
                .oldValue(null)
                .newValue(addedElement)
                .sessionId(sessionId)
                .taskPath(taskPath)
                .valueType(addedElement.getClass().getName())
                .valueKind("ELEMENT")
                .build();
            detailedChanges.add(elementChange);
        }

        // 为每个删除元素生成一个ChangeRecord
        for (Object removedElement : removed) {
            String uniqueFieldName = fieldName + ".element[" + removedElement + "]";
            ChangeRecord elementChange = ChangeRecord.builder()
                .objectName(objectName)
                .fieldName(uniqueFieldName)
                .changeType(ChangeType.DELETE)
                .oldValue(removedElement)
                .newValue(null)
                .sessionId(sessionId)
                .taskPath(taskPath)
                .valueType(removedElement.getClass().getName())
                .valueKind("ELEMENT")
                .build();
            detailedChanges.add(elementChange);
        }

        return detailedChanges;
    }

    /**
     * 构建紧凑键到命名键的映射（compact → named），例如："1001" → "id=1001"。
     */
    private void buildKeyMapping(Object entity, java.util.Map<String,String> map) {
        if (entity == null) return;
        String compact = com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.computeCompactKeyOrUnresolved(entity);
        String named = com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.computeStableKeyOrUnresolved(entity);
        if (!com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.UNRESOLVED.equals(compact)
            && !com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils.UNRESOLVED.equals(named)) {
            map.putIfAbsent(compact, named);
        }
    }

    /**
     * 将 entity[compact]（含可选 #idx）转换为 entity[named]（保持 #idx 后缀）。
     */
    private String transformEntityKeyInPath(String path, java.util.Map<String,String> compactToNamed) {
        if (path == null) return null;
        int lb = path.indexOf('[');
        int rb = path.indexOf(']');
        if (path.startsWith("entity[") && lb >= 0 && rb > lb) {
            String inside = path.substring(lb + 1, rb);
            String base = inside;
            String dup = "";
            int hashIdx = inside.indexOf('#');
            if (hashIdx >= 0) {
                base = inside.substring(0, hashIdx);
                dup = inside.substring(hashIdx); // include #
            }
            String named = compactToNamed.get(base);
            if (named != null) {
                String replaced = "entity[" + named + dup + "]";
                return replaced + (rb + 1 < path.length() ? path.substring(rb + 1) : "");
            }
        }
        return path;
    }

    private ChangeType calculateChangeType(Set<?> set1, Set<?> set2) {
        if (set1.isEmpty() && !set2.isEmpty()) {
            return ChangeType.CREATE;
        } else if (!set1.isEmpty() && set2.isEmpty()) {
            return ChangeType.DELETE;
        } else {
            return ChangeType.UPDATE;
        }
    }

    private double calculateSimilarity(Set<?> set1, Set<?> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0;
        }

        Set<Object> union = new HashSet<>(set1);
        union.addAll(set2);

        Set<Object> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        return (double) intersection.size() / union.size();
    }

    private String resolveEntityKey(Object val) {
        if (val == null) return null;
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(val);
        return EntityKeyUtils.UNRESOLVED.equals(key) ? null : key;
    }

    private String generateSetReport(Set<?> set1, Set<?> set2,
                                    Set<Object> added, Set<Object> removed,
                                    CompareOptions options) {
        StringBuilder report = new StringBuilder();

        if (options.getReportFormat() == ReportFormat.MARKDOWN) {
            report.append("## Set Comparison\n\n");
            report.append("| Metric | Value |\n");
            report.append("|--------|-------|\n");
            report.append("| Original Size | ").append(set1.size()).append(" |\n");
            report.append("| New Size | ").append(set2.size()).append(" |\n");
            report.append("| Added | ").append(added.size()).append(" |\n");
            report.append("| Removed | ").append(removed.size()).append(" |\n");

            if (!added.isEmpty()) {
                report.append("\n### Added Elements\n");
                added.forEach(e -> report.append("- ").append(e).append("\n"));
            }

            if (!removed.isEmpty()) {
                report.append("\n### Removed Elements\n");
                removed.forEach(e -> report.append("- ").append(e).append("\n"));
            }
        } else {
            report.append("Set Comparison:\n");
            report.append("  Original size: ").append(set1.size()).append("\n");
            report.append("  New size: ").append(set2.size()).append("\n");
            report.append("  Added: ").append(added.size()).append(" elements\n");
            report.append("  Removed: ").append(removed.size()).append(" elements\n");

            if (!added.isEmpty()) {
                report.append("  Added elements: ").append(added).append("\n");
            }

            if (!removed.isEmpty()) {
                report.append("  Removed elements: ").append(removed).append("\n");
            }
        }

        return report.toString();
    }

    // ==================== PR-2: Set<Entity> 排序委托支持 ====================

    /**
     * 检测是否为 Entity Set
     */
    private boolean isEntitySet(Set<?> set1, Set<?> set2) {
        // 任一集合非空且包含 Entity 元素
        if (!set1.isEmpty()) {
            Object sample = set1.iterator().next();
            if (isEntity(sample)) return true;
        }
        if (!set2.isEmpty()) {
            Object sample = set2.iterator().next();
            if (isEntity(sample)) return true;
        }
        return false;
    }

    /**
     * 检测对象是否为 Entity
     */
    private boolean isEntity(Object obj) {
        if (obj == null) return false;
        return obj.getClass().isAnnotationPresent(Entity.class) || hasKeyFields(obj);
    }

    /**
     * 检测对象是否有 @Key 字段
     */
    private boolean hasKeyFields(Object obj) {
        if (obj == null) return false;
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(obj);
        return !EntityKeyUtils.UNRESOLVED.equals(key);
    }

    /**
     * Set 转稳定排序的 List
     * <p>
     * 排序规则：
     * 1. 优先按稳定键字典序
     * 2. 键相同时按 System.identityHashCode 升序（保证稳定）
     * </p>
     */
    private List<?> toStablySortedList(Set<?> set) {
        try {
            return set.stream()
                .sorted((a, b) -> {
                    String keyA = EntityKeyUtils.computeStableKeyOrUnresolved(a);
                    String keyB = EntityKeyUtils.computeStableKeyOrUnresolved(b);
                    int cmp = keyA.compareTo(keyB);
                    if (cmp != 0) return cmp;
                    // 键相同时按 identityHashCode 升序（保证稳定输出）
                    return Integer.compare(System.identityHashCode(a), System.identityHashCode(b));
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            DiagnosticLogger.once(
                "SET-003",
                "SortingFailed",
                e.getMessage(),
                "Fallback to simple Set comparison"
            );
            throw new RuntimeException("Failed to sort Entity Set", e);
        }
    }

    /**
     * 检测重复 @Key
     * <p>
     * 排序后检查相邻元素，相同稳定键视为重复。
     * </p>
     */
    private void detectDuplicateKeys(List<?> sortedList, CompareOptions options, String setName) {
        if (sortedList.size() < 2) return;

        Map<String, List<Object>> keyToObjects = new LinkedHashMap<>();
        for (Object obj : sortedList) {
            String key = EntityKeyUtils.computeStableKeyOrUnresolved(obj);
            keyToObjects.computeIfAbsent(key, k -> new ArrayList<>()).add(obj);
        }

        // 检查重复
        List<String> duplicates = keyToObjects.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        if (!duplicates.isEmpty()) {
            String message = String.format(
                "Found %d duplicate @Key(s) in %s: %s. Check equals/hashCode implementation.",
                duplicates.size(), setName, duplicates
            );

            if (options.isStrictDuplicateKey()) {
                // 严格模式：抛异常
                throw new IllegalArgumentException(message);
            } else {
                // 宽松模式：诊断
                DiagnosticLogger.once(
                    "SET-002",
                    "DuplicateEntityKey",
                    message,
                    "Continuing comparison with duplicate keys (strict mode disabled)"
                );
            }
        }
    }
}
