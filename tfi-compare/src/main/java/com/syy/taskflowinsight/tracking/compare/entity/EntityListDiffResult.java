package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;

import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体列表比较结果
 * <p>
 * 提供实体级别的变更视图，将底层的字段级变更（FieldChange）
 * 按实体键分组，并提供丰富的查询、过滤和统计功能。
 * </p>
 *
 * <h3>使用示例</h3>
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

    private final List<EntityChangeGroup> groups;
    private final CompareResult originalResult;
    private final Map<EntityOperation, List<EntityChangeGroup>> operationGroups;
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
        return originalResult != null ? originalResult.getSimilarity() : null;
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
     * 当提供 oldList 和 newList 时，会自动填充索引元数据（oldIndex/newIndex 或 oldIndexes/newIndexes）。
     * 对于重复 @Key 场景（同一 key 在某侧出现多次），使用 oldIndexes/newIndexes 记录所有索引。
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

        // 按实体键分组变更（保持插入顺序）
        Map<String, List<FieldChange>> byEntity = result.getChanges().stream()
                .collect(Collectors.groupingBy(
                        EntityListDiffResult::resolveEntityKey,
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
            com.syy.taskflowinsight.util.DiagnosticLogger.once(
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

        byEntity.forEach((entityKey, changeList) -> {
            // 以首条变更的容器事件或 ChangeType 为主导（决定实体级操作）
            FieldChange firstChange = changeList.get(0);
            EntityOperation inferredOp = inferOperation(firstChange);
            List<String> parts = parseKeyParts(entityKey);
            String rawKey = extractRawKey(entityKey);

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
     * 从路径提取实体键
     * <p>
     * 路径格式示例：
     * <ul>
     *   <li>"entity[1001].name" → "entity[1001]"</li>
     *   <li>"entity[1001:US].price" → "entity[1001:US]"</li>
     *   <li>"entity[1001]" → "entity[1001]"（整体删除/新增）</li>
     *   <li>"product.name" → "product"（降级处理）</li>
     * </ul>
     * </p>
     *
     * @param path 字段路径或字段名
     * @return 实体键字符串
     */
    private static String extractEntityKeyFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        // 优先使用 PathUtils.parse 统一解析（支持转义与多前缀）
        PathUtils.KeyFieldPair pair = PathUtils.parse(path);
        if (pair != null && pair.key() != null && !"-".equals(pair.key())) {
            return pair.key();
        }
        // 降级处理：非 entity/map 路径，使用第一个点之前的部分
        int dotIndex = path.indexOf('.');
        if (dotIndex > 0) {
            return path.substring(0, dotIndex);
        }
        // 最后回退：返回原路径
        return path;
    }

    /**
     * 解析用于分组的实体键，支持多层降级策略。
     * <p>
     * <b>降级路径</b>（按优先级）:
     * <ol>
     *   <li>✅ <b>P0 优先</b>：容器事件提供的 {@code entityKey}（来自 P1-T1 迁移策略）
     *       <ul>
     *         <li>{@link com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy}</li>
     *         <li>{@link com.syy.taskflowinsight.tracking.compare.SetCompareStrategy}</li>
     *         <li>{@link com.syy.taskflowinsight.tracking.compare.MapCompareStrategy}</li>
     *       </ul>
     *   </li>
     *   <li>⚠️ <b>P1 降级</b>：容器事件提供的 {@code index}（无 entityKey 时）
     *       <ul>
     *         <li>生成通用键 {@code "entity[<index>]"}</li>
     *       </ul>
     *   </li>
     *   <li>⚠️ <b>P2 降级</b>：路径解析（用于未迁移策略）
     *       <ul>
     *         <li>{@link com.syy.taskflowinsight.tracking.compare.CollectionCompareStrategy}
     *             - 通用 Collection 比较，生成 {@code fieldName="collection"}</li>
     *         <li>提取标准格式 {@code entity[key]} 中的 key，或返回原 fieldName</li>
     *       </ul>
     *   </li>
     * </ol>
     * </p>
     *
     * <p><b>示例</b>:
     * <pre>
     * // 案例1: P1-T1 已迁移策略（优先级最高）
     * FieldChange fc1 = FieldChange.builder()
     *     .elementEvent(ContainerEvents.listAdd(0, "order[O123]"))
     *     .build();
     * resolveEntityKey(fc1); // → "order[O123]"
     *
     * // 案例2: 未迁移策略（降级到路径解析）
     * FieldChange fc2 = FieldChange.builder()
     *     .fieldName("collection")
     *     .collectionChange(true)
     *     .build();
     * resolveEntityKey(fc2); // → "collection"（修复后）
     * </pre>
     * </p>
     *
     * @param fc 字段变更对象
     * @return 实体键字符串（用于分组）
     */
    private static String resolveEntityKey(FieldChange fc) {
        // 1) P0 优先：容器事件直接提供实体键
        if (fc != null && fc.isContainerElementChange()) {
            FieldChange.ContainerElementEvent ev = fc.getElementEvent();
            if (ev != null && ev.getEntityKey() != null) {
                return ev.getEntityKey();
            }
            // P1 降级：若无实体键但有索引，则拼接通用实体键表达
            if (ev != null && ev.getIndex() != null) {
                return "entity[" + ev.getIndex() + "]";
            }
        }
        // 2) P2 降级：路径解析（兼容未迁移策略，如 CollectionCompareStrategy）
        String path = fc != null ? fc.getFieldPath() : null;
        if (path == null || path.isEmpty()) {
            path = fc != null ? fc.getFieldName() : null;
        }
        return extractEntityKeyFromPath(path);
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
     * 提取 entity[...] 中括号内的原始键（兼容重复key的#idx后缀）
     * 返回纯净的key，移除#idx后缀（用于索引推断）
     *
     * @param entityKey 实体键，如 "entity[1]" 或 "entity[1#0]"
     * @return 纯净key，如 "1"；对于非标准格式（如 "collection"）返回原值
     */
    static String extractRawKey(String entityKey) {
        if (entityKey == null) return "";
        int lb = entityKey.indexOf('[');
        int rb = entityKey.indexOf(']');
        if (lb >= 0 && rb > lb) {
            String key = entityKey.substring(lb + 1, rb);

            // ✅ 移除 #idx 后缀（用于重复key场景的索引推断）
            if (key.contains("#")) {
                key = key.substring(0, key.indexOf('#'));
            }

            return key;
        }
        // ✅ 修复：对于非标准路径（如 "collection"），返回原值而非空字符串
        // 用于支持未迁移策略（如 CollectionCompareStrategy）的降级场景
        return entityKey;
    }

    /**
     * 转换 ChangeType 到 EntityOperation
     * <p>
     * 映射规则：
     * <ul>
     *   <li>CREATE → ADD</li>
     *   <li>DELETE → DELETE</li>
     *   <li>UPDATE → MODIFY</li>
     *   <li>MOVE → MODIFY</li>
     * </ul>
     * </p>
     *
     * @param changeType 变更类型
     * @return 实体操作类型
     */
    private static EntityOperation toOperation(ChangeType changeType) {
        return switch (changeType) {
            case CREATE -> EntityOperation.ADD;
            case DELETE -> EntityOperation.DELETE;
            case UPDATE, MOVE -> EntityOperation.MODIFY;
        };
    }

    /**
     * 从 FieldChange 推断实体操作类型：优先容器事件的 ElementOperation，其次基于 ChangeType。
     */
    private static EntityOperation inferOperation(FieldChange firstChange) {
        if (firstChange != null && firstChange.isContainerElementChange()) {
            FieldChange.ElementOperation op = firstChange.getContainerOperation();
            if (op != null) {
                return switch (op) {
                    case ADD -> EntityOperation.ADD;
                    case REMOVE -> EntityOperation.DELETE;
                    case MODIFY, MOVE -> EntityOperation.MODIFY;
                };
            }
        }
        return toOperation(firstChange != null ? firstChange.getChangeType() : ChangeType.UPDATE);
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
     * EntityListDiffResult 建造者
     */
    public static class Builder {
        private List<EntityChangeGroup> groups = new ArrayList<>();
        private CompareResult originalResult;

        public Builder groups(List<EntityChangeGroup> groups) {
            this.groups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
            return this;
        }

        public Builder addGroup(EntityChangeGroup group) {
            this.groups.add(group);
            return this;
        }

        public Builder originalResult(CompareResult originalResult) {
            this.originalResult = originalResult;
            return this;
        }

        public EntityListDiffResult build() {
            return new EntityListDiffResult(this);
        }
    }

    /**
     * 统计信息
     * <p>
     * 提供实体级别和字段级别的变更统计。
     * </p>
     */
    public static class Statistics {
        private final int totalEntities;
        private final int addedCount;
        private final int modifiedCount;
        private final int deletedCount;
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
         */
        public int getTotalEntities() {
            return totalEntities;
        }

        /**
         * 获取新增实体数量
         */
        public int getAddedCount() {
            return addedCount;
        }

        /**
         * 获取修改实体数量
         */
        public int getModifiedCount() {
            return modifiedCount;
        }

        /**
         * 获取删除实体数量
         */
        public int getDeletedCount() {
            return deletedCount;
        }

        /**
         * 获取总字段变更数量
         */
        public int getTotalChanges() {
            return totalChanges;
        }
    }

    /**
     * 实体键到索引的映射构建���（内部使用）
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

            // 事件能力探测
            boolean hasEntityKeyEvent = changeList.stream().anyMatch(fc ->
                    fc != null && fc.isContainerElementChange() &&
                            fc.getElementEvent() != null && fc.getElementEvent().getEntityKey() != null);

            // 扫描事件索引/移动信息
            Integer evOldIdx = null;
            Integer evNewIdx = null;
            boolean movedByEvent = false;

            for (FieldChange fc : changeList) {
                if (fc == null || !fc.isContainerElementChange()) continue;
                FieldChange.ContainerElementEvent ev = fc.getElementEvent();
                if (ev == null) continue;

                if (ev.getOperation() == FieldChange.ElementOperation.MOVE) {
                    // 优先使用 MOVE 的 old/new index
                    if (ev.getOldIndex() != null || ev.getNewIndex() != null) {
                        evOldIdx = ev.getOldIndex();
                        evNewIdx = ev.getNewIndex();
                        movedByEvent = evOldIdx != null && evNewIdx != null && !evOldIdx.equals(evNewIdx);
                        break; // MOVE 信息足够，提前结束
                    }
                }

                // 非 MOVE：尝试读取 index
                if (evOldIdx == null && evNewIdx == null) {
                    if (ev.getOldIndex() != null || ev.getNewIndex() != null) {
                        evOldIdx = ev.getOldIndex();
                        evNewIdx = ev.getNewIndex();
                    } else if (ev.getIndex() != null) {
                        evOldIdx = ev.getIndex();
                        evNewIdx = ev.getIndex();
                    }
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
            com.syy.taskflowinsight.util.DiagnosticLogger.once(
                    "LIST-002",
                    "DuplicateKeyInList",
                    "Duplicate @Key detected for " + rawKey,
                    "Check equals/hashCode against @Key semantics"
            );
        }
    }
}
