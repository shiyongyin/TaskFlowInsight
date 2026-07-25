package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.SimilarityScore;
import com.syy.taskflowinsight.tracking.compare.internal.ValueSnapshotFormatter;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import com.syy.taskflowinsight.util.DiagnosticLogger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体列表比较结果
 * <p>
 * 提供实体级别的变更视图，将底层的字段级变更（FieldChange）
 * 按实体键分组，并提供丰富的查询、过滤和统计功能。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 从 CompareResult 转换
 * CompareResult result = listCompareExecutor.compare(oldList, newList, options);
 * EntityListDiffResult diffResult = EntityListDiffResult.from(result);
 *
 * // 查询各类操作
 * List<EntityChangeGroup> added = diffResult.getAddedEntities();
 * List<EntityChangeGroup> modified = diffResult.getModifiedEntities();
 * List<EntityChangeGroup> deleted = diffResult.getDeletedEntities();
 *
 * // 统计信息
 * String summary = diffResult.getSummary();
 * // "Total: 3 entities changed (Added: 1, Modified: 1, Deleted: 1)"
 *
 * // 按键查询
 * Optional<EntityChangeGroup> group = diffResult.getGroupByKey("entity[1001]");
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public class EntityListDiffResult {

    /** 按首次canonical变更顺序冻结的实体级兼容投影。 */
    private final List<EntityChangeGroup> groups;
    /** 原始typed结果，保留completion、problem与limitation等不可丢失事实。 */
    private final CompareResult originalResult;
    /** 为查询效率建立的不可变操作索引，不是第二份变更真值。 */
    private final Map<EntityOperation, List<EntityChangeGroup>> operationGroups;
    /** 从groups一次计算出的只读计数投影。 */
    private final Statistics statistics;

    private EntityListDiffResult(Builder builder) {
        this.groups = Collections.unmodifiableList(new ArrayList<>(builder.groups));
        this.originalResult = builder.originalResult;

        // 按操作类型分组（不可变副本）
        this.operationGroups = groups.stream()
                .collect(Collectors.groupingBy(
                        EntityChangeGroup::getOperation,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)
                ));

        // 计算统计信息
        this.statistics = new Statistics(groups);
    }

    /**
     * 获取所有变更组（不可变列表）
     *
     * @return 实体变更组列表
     */
    public List<EntityChangeGroup> getGroups() {
        return groups;
    }

    /**
     * 获取新增的实体组
     *
     * @return 新增实体列表（不可变）
     */
    public List<EntityChangeGroup> getAddedEntities() {
        List<EntityChangeGroup> result = operationGroups.get(EntityOperation.ADD);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 获取修改的实体组
     *
     * @return 修改实体列表（不可变）
     */
    public List<EntityChangeGroup> getModifiedEntities() {
        List<EntityChangeGroup> result = operationGroups.get(EntityOperation.MODIFY);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 获取删除的实体组
     *
     * @return 删除实体列表（不可变）
     */
    public List<EntityChangeGroup> getDeletedEntities() {
        List<EntityChangeGroup> result = operationGroups.get(EntityOperation.DELETE);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 根据实体键获取变更组
     *
     * @param entityKey 实体键（如 "entity[1001]"）
     * @return Optional 包含的 EntityChangeGroup，如果不存在则为空
     */
    public Optional<EntityChangeGroup> getGroupByKey(String entityKey) {
        return groups.stream()
                .filter(g -> g.getEntityKey().equals(entityKey))
                .findFirst();
    }

    /**
     * 是否有变更
     *
     * @return true 如果存在任何实体变更
     */
    public boolean hasChanges() {
        return !groups.isEmpty();
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息对象
     */
    public Statistics getStatistics() {
        return statistics;
    }

    /**
     * 顶层是否相同（直通原始 CompareResult）
     *
     * @return true 如果原始比较结果标记为相同
     */
    public boolean isIdentical() {
        return originalResult != null && originalResult.isIdentical();
    }

    /**
     * 顶层相似度（直通原始 CompareResult）
     *
     * @return 相似度（0-1），不存在时返回 null
     */
    public Double getSimilarity() {
        return originalResult == null
                ? null
                : originalResult.similarity().map(SimilarityScore::value).orElse(null);
    }

    /**
     * 获取摘要信息
     * <p>
     * 返回格式：Total: X entities changed (Added: A, Modified: M, Deleted: D)
     * </p>
     *
     * @return 摘要字符串
     */
    public String getSummary() {
        if (!hasChanges()) {
            return "No changes detected";
        }
        return String.format("Total: %d entities changed (Added: %d, Modified: %d, Deleted: %d)",
                statistics.getTotalEntities(),
                statistics.getAddedCount(),
                statistics.getModifiedCount(),
                statistics.getDeletedCount());
    }

    /**
     * 获取原始比较结果
     * <p>
     * 提供访问底层 CompareResult 的途径，便于调试和高级用法。
     * </p>
     *
     * @return 原始的 CompareResult 对象
     */
    public CompareResult getOriginalResult() {
        return originalResult;
    }

    /**
     * 从 CompareResult 创建 EntityListDiffResult（仅基于路径推断）
     * <p>
     * 此方法仅依赖 FieldChange 中的路径信息（fieldPath/fieldName）
     * 来提取实体键并分组。适用于大多数场景。
     * </p>
     *
     * @param result 原始比较结果
     * @return 实体列表差异结果
     */
    public static EntityListDiffResult from(CompareResult result) {
        return from(result, null, null);
    }

    /**
     * 从 CompareResult 创建 EntityListDiffResult（可选提供旧/新列表增强推断）
     * <p>
     * 当提供oldList和newList时，会填充oldIndex/newIndex或oldIndexes/newIndexes。
     * 对于重复@Key场景（同一key在某侧出现多次），使用复数属性记录所有索引。
     * </p>
     *
     * @param result  原始比较结果
     * @param oldList 旧列表（可选，用于索引推断）
     * @param newList 新列表（可选，用于索引推断）
     * @return 实体列表差异结果
     */
    public static EntityListDiffResult from(CompareResult result, List<?> oldList, List<?> newList) {
        if (result == null || result.getChanges() == null || result.getChanges().isEmpty()) {
            return empty();
        }

        // 展示文本可能折叠类型事实，内部必须按完整typed identity分组并保持首次出现顺序。
        Map<EntityGroupKey, List<FieldChange>> byEntity = result.getChanges().stream()
                .collect(Collectors.groupingBy(
                        EntityListDiffResult::resolveEntityGroupKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 预处理映射（O(n)）
        Map<String, Integer> oldKeyIndexTemp;
        Map<String, Integer> newKeyIndexTemp;
        Map<String, List<Integer>> oldKeyIndexesTemp;
        Map<String, List<Integer>> newKeyIndexesTemp;

        try {
            oldKeyIndexTemp = KeyIndexMapper.buildKeyIndexMap(oldList);
            newKeyIndexTemp = KeyIndexMapper.buildKeyIndexMap(newList);
            oldKeyIndexesTemp = KeyIndexMapper.buildKeyIndexesMap(oldList);
            newKeyIndexesTemp = KeyIndexMapper.buildKeyIndexesMap(newList);
        } catch (Exception e) {
            // 索引映射构建失败，记录诊断并继续（所有索引将为 null）
            DiagnosticLogger.once(
                "LIST-001",
                "IndexMapBuildFailed",
                e.getMessage(),
                "Check @Key annotations"
            );
            oldKeyIndexTemp = new HashMap<>();
            newKeyIndexTemp = new HashMap<>();
            oldKeyIndexesTemp = new HashMap<>();
            newKeyIndexesTemp = new HashMap<>();
        }

        // 转为final变量供lambda使用
        final Map<String, Integer> oldKeyIndex = oldKeyIndexTemp;
        final Map<String, Integer> newKeyIndex = newKeyIndexTemp;
        final Map<String, List<Integer>> oldKeyIndexes = oldKeyIndexesTemp;
        final Map<String, List<Integer>> newKeyIndexes = newKeyIndexesTemp;

        // 构建结果
        Builder builder = new Builder().originalResult(result);

        byEntity.forEach((groupKey, changeList) -> {
            String entityKey = groupKey.displayKey();
            // 以首条变更的容器事件或 ChangeType 为主导（决定实体级操作）
            FieldChange firstChange = changeList.get(0);
            EntityOperation inferredOp = inferOperation(firstChange);
            Optional<EntityKeySegment> typedKey = Optional.ofNullable(groupKey.segment());

            List<String> parts;
            String rawKey;
            if (typedKey.isPresent()) {
                // typed组件才是identity真值；字符串投影含转义分隔符，反向解析会丢失组件边界。
                parts = diagnosticKeyParts(typedKey.orElseThrow());
                rawKey = renderRawEntityKey(parts);
            } else {
                // 兼容旧调用方直接构造的display path，不把该解析结果提升为canonical identity。
                parts = parseKeyParts(entityKey);
                rawKey = extractRawKey(entityKey);
            }

            // 提取索引信息（封装复杂逻辑）
            IndexInfo indexInfo = IndexInfo.extract(
                    rawKey, changeList,
                    oldKeyIndex, newKeyIndex,
                    oldKeyIndexes, newKeyIndexes
            );

            // 构建实体变更组
            EntityChangeGroup.Builder groupBuilder = EntityChangeGroup.builder()
                    .entityKey(entityKey)
                    .operation(inferredOp)
                    .keyParts(parts)
                    .changes(changeList)
                    .oldIndex(indexInfo.oldIndex())
                    .newIndex(indexInfo.newIndex())
                    .oldIndexes(indexInfo.oldIndexes())
                    .newIndexes(indexInfo.newIndexes())
                    .moved(indexInfo.moved())
                    .degraded(indexInfo.degraded());

            builder.addGroup(groupBuilder.build());
        });

        return builder.build();
    }

    /**
     * 创建空结果
     *
     * @return 空的 EntityListDiffResult
     */
    public static EntityListDiffResult empty() {
        return builder().build();
    }

    /**
     * 从canonical side path读取Entity分组键；无typed identity时只保留安全路径投影。
     *
     * <p>该方法刻意不解析{@link FieldChange#getFieldPath()}：display path会隐藏动态key，
     * 反向解析既无法恢复类型，也会重新引入{@code entity[index]}伪身份。</p>
     *
     * @param fc 字段变更对象
     * @return typed identity与兼容展示文本组成的内部聚合键
     */
    private static EntityGroupKey resolveEntityGroupKey(FieldChange fc) {
        if (fc == null) {
            return new EntityGroupKey(null, "unknown");
        }
        EntityKeySegment segment = entityKeySegment(fc).orElse(null);
        String displayKey = segment == null ? fc.getFieldPath() : renderEntityKey(segment);
        return new EntityGroupKey(segment, displayKey == null ? "unknown" : displayKey);
    }

    /**
     * 内部聚合键同时保留typed identity和兼容展示文本，防止同文本的不同类型事实被覆盖。
     *
     * @param segment canonical Entity identity；兼容字符串输入时为null
     * @param displayKey 对外保留的字符串键投影
     */
    private record EntityGroupKey(EntityKeySegment segment, String displayKey) {
    }

    private static Optional<EntityKeySegment> entityKeySegment(FieldChange change) {
        return change.after()
                .or(() -> change.before())
                .stream()
                .flatMap(side -> side.path().segments().stream())
                .filter(EntityKeySegment.class::isInstance)
                .map(EntityKeySegment.class::cast)
                .findFirst();
    }

    private static String renderEntityKey(EntityKeySegment segment) {
        return PathUtils.buildEntityPath(renderRawEntityKey(diagnosticKeyParts(segment)));
    }

    private static List<String> diagnosticKeyParts(EntityKeySegment segment) {
        return segment.components().stream()
                .map(ValueSnapshotFormatter::diagnosticText)
                .toList();
    }

    private static String renderRawEntityKey(List<String> parts) {
        return parts.stream()
                .map(PathUtils::escape)
                .collect(Collectors.joining(":"));
    }

    /**
     * 解析实体键的分片（从 entity[part1:part2] 中提取并按未转义冒号拆分）
     */
    static List<String> parseKeyParts(String entityKey) {
        String raw = extractRawKey(entityKey);
        if (raw.isEmpty()) return Collections.emptyList();

        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ':') {
                parts.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString());
        return parts;
    }

    /**
     * 提取{@code entity[...]}中的完整键投影，不解释或删除任何伪后缀。
     *
     * <p>重复key已由内核发布W2201，投影层不能再通过{@code #idx}猜测一对一身份。</p>
     *
     * @param entityKey 实体键，如{@code entity[1]}
     * @return 完整键内容；非标准格式保持原值
     */
    static String extractRawKey(String entityKey) {
        if (entityKey == null) return "";
        int lb = entityKey.indexOf('[');
        int rb = entityKey.indexOf(']');
        if (lb >= 0 && rb > lb) {
            return entityKey.substring(lb + 1, rb);
        }
        // 非Entity兼容分组仍以原投影为键，但不会进入Entity candidate pairing。
        return entityKey;
    }

    /** 从canonical ChangeKind收窄为实体级操作，避免读取已删除的容器事件旁路。 */
    private static EntityOperation inferOperation(FieldChange firstChange) {
        if (firstChange == null) {
            return EntityOperation.MODIFY;
        }
        return switch (firstChange.kind()) {
            case ADD -> EntityOperation.ADD;
            case REMOVE -> EntityOperation.DELETE;
            case MODIFY, MOVE, NULLNESS, TYPE_MISMATCH -> EntityOperation.MODIFY;
        };
    }

    /**
     * 创建 Builder 实例
     *
     * @return Builder 对象
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 兼容实体聚合结果的建造者；仅负责冻结投影，不参与canonical变更归并。
     *
     * @since v3.0.0
     */
    public static class Builder {
        /** 构造完成后会防御复制的实体分组。 */
        private List<EntityChangeGroup> groups = new ArrayList<>();
        /** 与分组一同保留的原始typed结果。 */
        private CompareResult originalResult;

        /**
         * 替换实体分组并立即防御复制，避免调用方后续修改输入集合。
         *
         * @param groups 待冻结的实体分组；null按空列表处理
         * @return 当前建造者
         */
        public Builder groups(List<EntityChangeGroup> groups) {
            this.groups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
            return this;
        }

        /**
         * 追加一个已经归纳完成的实体分组。
         *
         * @param group 实体分组；非null
         * @return 当前建造者
         */
        public Builder addGroup(EntityChangeGroup group) {
            this.groups.add(group);
            return this;
        }

        /**
         * 保留canonical原始结果，使completion、problem和limitation不会在兼容投影中丢失。
         *
         * @param originalResult 原始比较结果；手工构造兼容视图时允许为null
         * @return 当前建造者
         */
        public Builder originalResult(CompareResult originalResult) {
            this.originalResult = originalResult;
            return this;
        }

        /**
         * 冻结当前兼容投影及其索引。
         *
         * @return 不可变的实体列表差异结果
         */
        public EntityListDiffResult build() {
            return new EntityListDiffResult(this);
        }
    }

    /**
     * 统计信息
     * <p>
     * 统计值只从已冻结分组派生，避免与canonical变更事实形成可变的第二数据源。
     * </p>
     *
     * @since v3.0.0
     */
    public static class Statistics {
        /** 参与实体级聚合的唯一实体总数。 */
        private final int totalEntities;
        /** 仅出现在新侧的实体数量。 */
        private final int addedCount;
        /** 两侧均存在且包含确定变更的实体数量。 */
        private final int modifiedCount;
        /** 仅出现在旧侧的实体数量。 */
        private final int deletedCount;
        /** 所有实体分组内保留的字段变更总数。 */
        private final int totalChanges;

        Statistics(List<EntityChangeGroup> groups) {
            this.totalEntities = groups.size();
            this.addedCount = (int) groups.stream()
                    .filter(g -> g.getOperation() == EntityOperation.ADD)
                    .count();
            this.modifiedCount = (int) groups.stream()
                    .filter(g -> g.getOperation() == EntityOperation.MODIFY)
                    .count();
            this.deletedCount = (int) groups.stream()
                    .filter(g -> g.getOperation() == EntityOperation.DELETE)
                    .count();
            this.totalChanges = groups.stream()
                    .mapToInt(EntityChangeGroup::getChangeCount)
                    .sum();
        }

        /**
         * 获取总实体数（发生变更的实体数量）
         *
         * @return 参与聚合的实体分组数量
         */
        public int getTotalEntities() {
            return totalEntities;
        }

        /**
         * 获取新增实体数量
         *
         * @return 操作为ADD的实体分组数量
         */
        public int getAddedCount() {
            return addedCount;
        }

        /**
         * 获取修改实体数量
         *
         * @return 操作为MODIFY的实体分组数量
         */
        public int getModifiedCount() {
            return modifiedCount;
        }

        /**
         * 获取删除实体数量
         *
         * @return 操作为DELETE的实体分组数量
         */
        public int getDeletedCount() {
            return deletedCount;
        }

        /**
         * 获取总字段变更数量
         *
         * @return 所有实体分组保留的字段变更数之和
         */
        public int getTotalChanges() {
            return totalChanges;
        }
    }

    /**
     * 实体键到索引的映射构建器（内部使用）
     */
    private static class KeyIndexMapper {

        /**
         * 构建 key -> [index1, index2, ...] 映射（支持重复键）
         */
        static Map<String, List<Integer>> buildKeyIndexesMap(List<?> list) {
            Map<String, List<Integer>> map = new HashMap<>();
            if (list == null) return map;

            for (int i = 0; i < list.size(); i++) {
                Object obj = list.get(i);
                if (obj == null) continue;

                // 同时支持紧凑键与命名键，便于兼容不同来源的路径
                String compact = EntityKeyUtils.computeCompactKeyOrUnresolved(obj);
                String named = EntityKeyUtils.computeStableKeyOrUnresolved(obj);

                if (compact != null && !EntityKeyUtils.UNRESOLVED.equals(compact)) {
                    map.computeIfAbsent(compact, k -> new ArrayList<>()).add(i);
                }
                if (named != null && !EntityKeyUtils.UNRESOLVED.equals(named)) {
                    map.computeIfAbsent(named, k -> new ArrayList<>()).add(i);
                }
            }
            return map;
        }

        /**
         * 构建 key -> index 映射（单实例场景，重复键会覆盖）
         */
        static Map<String, Integer> buildKeyIndexMap(List<?> list) {
            Map<String, List<Integer>> indexesMap = buildKeyIndexesMap(list);
            Map<String, Integer> map = new HashMap<>();

            indexesMap.forEach((key, indexes) -> {
                if (!indexes.isEmpty()) {
                    map.put(key, indexes.get(0)); // 取第一个索引
                }
            });

            return map;
        }
    }

    /**
     * 索引信息封装（内部使用）
     */
    private static Integer pathIndex(ComparePath path) {
        return path.segments().stream()
                .filter(IndexSegment.class::isInstance)
                .map(IndexSegment.class::cast)
                .map(IndexSegment::index)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private record IndexInfo(
            Integer oldIndex,
            Integer newIndex,
            List<Integer> oldIndexes,
            List<Integer> newIndexes,
            boolean moved,
            boolean degraded
    ) {

        /**
         * 从变更列表和映射表中提取索引信息
         */
        static IndexInfo extract(
                String rawKey,
                List<FieldChange> changeList,
                Map<String, Integer> oldKeyIndex,
                Map<String, Integer> newKeyIndex,
                Map<String, List<Integer>> oldKeyIndexes,
                Map<String, List<Integer>> newKeyIndexes
        ) {
            // 检测重复键
            List<Integer> oldIdxList = oldKeyIndexes.getOrDefault(rawKey, Collections.emptyList());
            List<Integer> newIdxList = newKeyIndexes.getOrDefault(rawKey, Collections.emptyList());
            boolean multipleOld = oldIdxList.size() > 1;
            boolean multipleNew = newIdxList.size() > 1;

            boolean hasEntityKeyEvent = changeList.stream()
                    .filter(Objects::nonNull)
                    .flatMap(fc -> fc.before().or(() -> fc.after()).stream())
                    .flatMap(side -> side.path().segments().stream())
                    .anyMatch(EntityKeySegment.class::isInstance);

            // 扫描事件索引/移动信息
            Integer evOldIdx = null;
            Integer evNewIdx = null;
            boolean movedByEvent = false;

            for (FieldChange fc : changeList) {
                if (fc == null) continue;
                Integer beforeIndex = fc.before().map(side -> pathIndex(side.path())).orElse(null);
                Integer afterIndex = fc.after().map(side -> pathIndex(side.path())).orElse(null);
                if (fc.kind() == ChangeKind.MOVE) {
                    evOldIdx = beforeIndex;
                    evNewIdx = afterIndex;
                    movedByEvent = evOldIdx != null && evNewIdx != null && !evOldIdx.equals(evNewIdx);
                    break;
                }
                if (evOldIdx == null && beforeIndex != null) {
                    evOldIdx = beforeIndex;
                }
                if (evNewIdx == null && afterIndex != null) {
                    evNewIdx = afterIndex;
                }
            }

            // 构建结果
            if (multipleOld || multipleNew) {
                // 重复 key 场景：使用索引列表
                logDuplicateKeyWarning(rawKey);
                return new IndexInfo(
                        null,
                        null,
                        oldIdxList.isEmpty() ? null : oldIdxList,
                        newIdxList.isEmpty() ? null : newIdxList,
                        false,
                        !hasEntityKeyEvent
                );
            } else {
                // 1:1 场景：使用单索引
                Integer oi = evOldIdx;
                Integer ni = evNewIdx;
                boolean usedEventIndex = (oi != null || ni != null);

                if (!usedEventIndex) {
                    // 回退：基于 old/new 列表映射
                    if (oi == null) oi = oldKeyIndex.getOrDefault(rawKey, null);
                    if (ni == null) ni = newKeyIndex.getOrDefault(rawKey, null);
                }

                // 计算移动标志
                boolean moved = movedByEvent || (oi != null && ni != null && !oi.equals(ni));

                // 降级���记：索引来源于列表映射而非事件，且没有实体键
                boolean degraded = !usedEventIndex && !hasEntityKeyEvent;

                return new IndexInfo(oi, ni, null, null, moved, degraded);
            }
        }

        /**
         * 记录重复键警告（仅记录一次）
         */
        private static void logDuplicateKeyWarning(String rawKey) {
            DiagnosticLogger.once(
                    "LIST-002",
                    "DuplicateKeyInList",
                    "Duplicate @Key detected for " + rawKey,
                    "Check equals/hashCode against @Key semantics"
            );
        }
    }
}
