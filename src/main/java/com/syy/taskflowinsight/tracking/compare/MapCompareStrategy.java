package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.rename.RenameHeuristics;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshot;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.util.DiagnosticLogger;
import com.syy.taskflowinsight.tracking.ssot.path.PathUtils;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

import static com.syy.taskflowinsight.tracking.compare.CompareConstants.*;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerElementEvent;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ContainerType;
import com.syy.taskflowinsight.tracking.compare.FieldChange.ElementOperation;

/**
 * Map比较策略（支持 Entity key/value 深度对比）
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class MapCompareStrategy implements DetailedCompareStrategy<Map<?, ?>> {

    private static final Logger logger = LoggerFactory.getLogger(MapCompareStrategy.class);

    @Override
    public CompareResult compare(Map<?, ?> map1, Map<?, ?> map2, CompareOptions options) {
        if (map1 == map2) {
            return CompareResult.identical();
        }
        
        if (map1 == null || map2 == null) {
            return CompareResult.ofNullDiff(map1, map2);
        }
        
        List<FieldChange> changes = new ArrayList<>();

        // 检查是否启用重命名检测
        boolean enableRenameDetection = shouldEnableRenameDetection(map1, map2);

        if (enableRenameDetection) {
            // 首先检测可能的键重命名
            Map<Object, Object> renames = detectKeyRenames(map1, map2);

            // 处理重命名、新增、删除和更新
            processChangesWithRenames(map1, map2, renames, changes);
        } else {
            // 使用原有逻辑（传递 options 以支持 Entity 特性）
            processChangesSimple(map1, map2, changes, options);
        }
        
        // 构建结果
        CompareResult.CompareResultBuilder builder = CompareResult.builder()
            .object1(map1)
            .object2(map2)
            .changes(changes)
            .identical(changes.isEmpty());
        
        // 计算相似度
        if (options.isCalculateSimilarity()) {
            double similarity = calculateMapSimilarity(map1, map2);
            builder.similarity(similarity);
        }
        
        // 生成报告
        if (options.isGenerateReport()) {
            String report = generateMapReport(map1, map2, changes, options);
            builder.report(report);
        }
        
        return builder.build();
    }
    
    @Override
    public String getName() {
        return "MapCompare";
    }
    
    @Override
    public boolean supports(Class<?> type) {
        return Map.class.isAssignableFrom(type);
    }

    @Override
    public List<ChangeRecord> generateDetailedChangeRecords(
            String objectName,
            String fieldName,
            Map<?, ?> oldValue,
            Map<?, ?> newValue,
            String sessionId,
            String taskPath) {

        List<ChangeRecord> detailedChanges = new ArrayList<>();

        if (oldValue == null && newValue == null) {
            return detailedChanges;
        }

        Map<?, ?> map1 = oldValue != null ? oldValue : Collections.emptyMap();
        Map<?, ?> map2 = newValue != null ? newValue : Collections.emptyMap();

        // 获取所有键的并集
        Set<Object> allKeys = new HashSet<>();
        allKeys.addAll(map1.keySet());
        allKeys.addAll(map2.keySet());

        // 为每个键的变更生成一个ChangeRecord
        for (Object key : allKeys) {
            Object oldVal = map1.get(key);
            Object newVal = map2.get(key);

            ChangeType changeType;
            if (oldVal == null && newVal != null) {
                changeType = ChangeType.CREATE;
            } else if (oldVal != null && newVal == null) {
                changeType = ChangeType.DELETE;
            } else if (!Objects.equals(oldVal, newVal)) {
                changeType = ChangeType.UPDATE;
            } else {
                continue; // 无变化，跳过
            }

            String keyFieldName = fieldName + "." + key;
            ChangeRecord keyChange = ChangeRecord.builder()
                .objectName(objectName)
                .fieldName(keyFieldName)
                .changeType(changeType)
                .oldValue(oldVal)
                .newValue(newVal)
                .sessionId(sessionId)
                .taskPath(taskPath)
                .valueType(newVal != null ? newVal.getClass().getName() :
                    (oldVal != null ? oldVal.getClass().getName() : "Object"))
                .valueKind("MAP_ENTRY")
                .build();
            detailedChanges.add(keyChange);
        }

        return detailedChanges;
    }
    
    private double calculateMapSimilarity(Map<?, ?> map1, Map<?, ?> map2) {
        if (map1.isEmpty() && map2.isEmpty()) {
            return 1.0;
        }
        
        Set<Object> allKeys = new HashSet<>();
        allKeys.addAll(map1.keySet());
        allKeys.addAll(map2.keySet());
        
        int sameValues = 0;
        for (Object key : allKeys) {
            if (Objects.equals(map1.get(key), map2.get(key))) {
                sameValues++;
            }
        }
        
        return (double) sameValues / allKeys.size();
    }
    
    private String generateMapReport(Map<?, ?> map1, Map<?, ?> map2,
                                    List<FieldChange> changes,
                                    CompareOptions options) {
        StringBuilder report = new StringBuilder();
        
        if (options.getReportFormat() == ReportFormat.MARKDOWN) {
            report.append("## Map Comparison\n\n");
            report.append("| Key | Old Value | New Value | Change |\n");
            report.append("|-----|-----------|-----------|--------|\n");
            
            for (FieldChange change : changes) {
                report.append("| ").append(change.getFieldName())
                      .append(" | ").append(change.getOldValue())
                      .append(" | ").append(change.getNewValue())
                      .append(" | ").append(change.getChangeType())
                      .append(" |\n");
            }
            
            report.append("\n**Total changes:** ").append(changes.size()).append("\n");
        } else {
            report.append("Map Comparison:\n");
            for (FieldChange change : changes) {
                report.append("  ").append(change.getFieldName())
                      .append(": ").append(change.getOldValue())
                      .append(" -> ").append(change.getNewValue())
                      .append(" (").append(change.getChangeType()).append(")\n");
            }
            report.append("Total changes: ").append(changes.size()).append("\n");
        }
        
        return report.toString();
    }

    // ========== P1-T1: 容器事件填充辅助 ==========

    private FieldChange.FieldChangeBuilder attachMapEvent(FieldChange.FieldChangeBuilder builder,
                                                          Object mapKey,
                                                          ChangeType changeType) {
        ElementOperation op = switch (changeType) {
            case CREATE -> ElementOperation.ADD;
            case DELETE -> ElementOperation.REMOVE;
            case UPDATE -> ElementOperation.MODIFY;
            case MOVE -> ElementOperation.MOVE;
        };
        return builder.elementEvent(
            com.syy.taskflowinsight.tracking.compare.ContainerEvents
                .mapEvent(op, mapKey, null, null)
        );
    }

    /**
     * 贴上 MAP 容器事件，同时设置 propertyPath（用于值对象的内部字段变更）。
     */
    private FieldChange.FieldChangeBuilder attachMapEvent(FieldChange.FieldChangeBuilder builder,
                                                          Object mapKey,
                                                          ChangeType changeType,
                                                          String propertyPath) {
        ElementOperation op = switch (changeType) {
            case CREATE -> ElementOperation.ADD;
            case DELETE -> ElementOperation.REMOVE;
            case UPDATE -> ElementOperation.MODIFY;
            case MOVE -> ElementOperation.MOVE;
        };
        return builder.elementEvent(
            com.syy.taskflowinsight.tracking.compare.ContainerEvents
                .mapEvent(op, mapKey, null, propertyPath)
        );
    }

    /** 带 entityKey 的 MAP 容器事件（用于 Entity 值）。 */
    private FieldChange.FieldChangeBuilder attachMapEventWithEntity(FieldChange.FieldChangeBuilder builder,
                                                                    Object mapKey,
                                                                    ChangeType changeType,
                                                                    String entityKey,
                                                                    String propertyPath) {
        ElementOperation op = switch (changeType) {
            case CREATE -> ElementOperation.ADD;
            case DELETE -> ElementOperation.REMOVE;
            case UPDATE -> ElementOperation.MODIFY;
            case MOVE -> ElementOperation.MOVE;
        };
        return builder.elementEvent(
            com.syy.taskflowinsight.tracking.compare.ContainerEvents
                .mapEvent(op, mapKey, entityKey, propertyPath)
        );
    }

    private String resolveEntityKey(Object v1, Object v2) {
        Object pick = v2 != null ? v2 : v1;
        if (pick == null) return null;
        String key = EntityKeyUtils.computeStableKeyOrUnresolved(pick);
        return EntityKeyUtils.UNRESOLVED.equals(key) ? null : key;
    }
    
    /**
     * 检查是否应该启用重命名检测（PR-3: 排除 Entity keys）
     */
    private boolean shouldEnableRenameDetection(Map<?, ?> map1, Map<?, ?> map2) {
        // 如果有 Entity keys，不启用 rename（Entity keys 不参与字符串相似度 rename）
        if (detectEntityKeys(map1, map2)) {
            return false;
        }

        // 检查候选配对数K>1000降级（仅计算非 Entity keys）
        Set<Object> deletedKeys = new HashSet<>(map1.keySet());
        deletedKeys.removeAll(map2.keySet());

        Set<Object> addedKeys = new HashSet<>(map2.keySet());
        addedKeys.removeAll(map1.keySet());

        int candidatePairs = deletedKeys.size() * addedKeys.size();
        if (candidatePairs > MAP_CANDIDATE_PAIRS_DEGRADATION_THRESHOLD) {
            // 候选配对数过多，降级
            DiagnosticLogger.once(
                "MAP-003",
                "CandidatePairsDegraded",
                "Candidate pairs " + candidatePairs + " > " + MAP_CANDIDATE_PAIRS_DEGRADATION_THRESHOLD,
                "Disable rename detection and fallback to simple CREATE/DELETE"
            );
            return false;
        }

        return true;
    }
    
    /**
     * 检测键重命名（M3增强：使用RenameHeuristics + 候选对数控制）
     */
    private Map<Object, Object> detectKeyRenames(Map<?, ?> map1, Map<?, ?> map2) {
        Map<Object, Object> renames = new HashMap<>();

        // 找出删除和新增的键
        Set<Object> deletedKeys = new HashSet<>(map1.keySet());
        deletedKeys.removeAll(map2.keySet());

        Set<Object> addedKeys = new HashSet<>(map2.keySet());
        addedKeys.removeAll(map1.keySet());

        // 对每个删除的键，寻找最相似的新增键
        for (Object deletedKey : deletedKeys) {
            Object deletedValue = map1.get(deletedKey);

            Object bestMatch = null;
            double bestSimilarity = 0.0;

            for (Object addedKey : addedKeys) {
                Object addedValue = map2.get(addedKey);

                // 值必须完全一致
                if (Objects.equals(deletedValue, addedValue)) {
                    double similarity = calculateKeySimilarity(deletedKey, addedKey);

                    if (similarity >= MAP_KEY_RENAME_SIMILARITY_THRESHOLD && similarity > bestSimilarity) {
                        bestSimilarity = similarity;
                        bestMatch = addedKey;
                    }
                }
            }

            if (bestMatch != null) {
                renames.put(deletedKey, bestMatch);
                addedKeys.remove(bestMatch); // 避免一对多映射
            }
        }

        return renames;
    }
    
    /**
     * 计算键相似度（M3增强：使用RenameHeuristics统一算法）
     */
    private double calculateKeySimilarity(Object key1, Object key2) {
        if (Objects.equals(key1, key2)) {
            return 1.0;
        }

        String str1 = String.valueOf(key1);
        String str2 = String.valueOf(key2);

        // M3: 使用抽离的RenameHeuristics计算相似度
        RenameHeuristics.Options opts = RenameHeuristics.Options.defaults();
        double similarity = RenameHeuristics.similarity(str1, str2, opts);

        // 超阈值降级（返回-1.0），视为不相似
        return similarity >= 0 ? similarity : 0.0;
    }
    
    /**
     * 处理简单变更（增强支持 Entity key/value）
     */
    private void processChangesSimple(Map<?, ?> map1, Map<?, ?> map2, List<FieldChange> changes, CompareOptions options) {
        // 检测是否有 Entity keys（仅一次）
        boolean hasEntityKeys = detectEntityKeys(map1, map2);

        if (hasEntityKeys) {
            // Entity key 场景：使用特殊处理
            processChangesWithEntityKeys(map1, map2, changes, options);
            return;
        }

        // 原有逻辑：非 Entity key 场景
        Set<Object> allKeys = new HashSet<>();
        allKeys.addAll(map1.keySet());
        allKeys.addAll(map2.keySet());

        // 比较每个键的值
        for (Object key : allKeys) {
            Object value1 = map1.get(key);
            Object value2 = map2.get(key);

            // 检测 value 是否为 Entity（Entity 需要深度对比，不能仅依赖 equals()）
            boolean isEntityValue = isEntity(value1) || isEntity(value2);

            if (isEntityValue) {
                // Entity value 深度对比（即使 equals() 返回 true 也要对比）
                if (value1 != null && value2 != null) {
                    String pathPrefix = PathUtils.buildMapValuePath(String.valueOf(key));
                    List<FieldChange> deepChanges = compareEntityDeep(value1, value2, pathPrefix, key);
                    changes.addAll(deepChanges);
                } else if (!Objects.equals(value1, value2)) {
                    // 一侧为 null，简单处理
                    ChangeType changeType = (value1 == null) ? ChangeType.CREATE : ChangeType.DELETE;
                    FieldChange change = attachMapEventWithEntity(
                        FieldChange.builder()
                            .fieldName(String.valueOf(key))
                            .oldValue(value1)
                            .newValue(value2)
                            .changeType(changeType),
                        key,
                        changeType,
                        resolveEntityKey(value1, value2),
                        null
                    ).build();
                    changes.add(change);
                }
            } else if (!Objects.equals(value1, value2)) {
                // 非 Entity value，原有逻辑
                ChangeType changeType;
                if (value1 == null) {
                    changeType = ChangeType.CREATE;
                } else if (value2 == null) {
                    changeType = ChangeType.DELETE;
                } else {
                    changeType = ChangeType.UPDATE;
                }

                FieldChange change = attachMapEvent(
                    FieldChange.builder()
                        .fieldName(String.valueOf(key))
                        .oldValue(value1)
                        .newValue(value2)
                        .changeType(changeType),
                    key,
                    changeType
                ).build();
                changes.add(change);
            }
        }
    }

    /**
     * 检测是否有 Entity keys
     */
    private boolean detectEntityKeys(Map<?, ?> map1, Map<?, ?> map2) {
        // 检查 map1 的第一个 key
        if (!map1.isEmpty()) {
            Object firstKey = map1.keySet().iterator().next();
            if (isEntity(firstKey) || hasKeyFields(firstKey)) {
                return true;
            }
        }
        // 检查 map2 的第一个 key
        if (!map2.isEmpty()) {
            Object firstKey = map2.keySet().iterator().next();
            if (isEntity(firstKey) || hasKeyFields(firstKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理 Entity keys 场景（@Key 相同 vs @Key 改变）
     */
    private void processChangesWithEntityKeys(Map<?, ?> map1, Map<?, ?> map2,
                                              List<FieldChange> changes,
                                              CompareOptions options) {
        // 构建 @Key → 对象映射
        Map<String, Object> oldKeyMap = buildEntityKeyMap(map1.keySet());
        Map<String, Object> newKeyMap = buildEntityKeyMap(map2.keySet());

        Set<String> processedKeys = new HashSet<>();

        // 处理 @Key 相同的场景
        for (Map.Entry<String, Object> entry : oldKeyMap.entrySet()) {
            String stableKey = entry.getKey();
            Object oldKeyObj = entry.getValue();
            Object newKeyObj = newKeyMap.get(stableKey);

            if (newKeyObj != null) {
                // @Key 相同
                processedKeys.add(stableKey);

                // 比较 value
                Object oldValue = map1.get(oldKeyObj);
                Object newValue = map2.get(newKeyObj);

                if (!Objects.equals(oldValue, newValue)) {
                    String valuePath = buildMapPath(oldKeyObj);
                    if (isEntity(oldValue) || isEntity(newValue)) {
                        // Entity value 深度对比
                        if (oldValue != null && newValue != null) {
                            List<FieldChange> deepChanges = compareEntityDeep(oldValue, newValue, valuePath, oldKeyObj);
                            changes.addAll(deepChanges);
                        } else {
                            ChangeType changeType = (oldValue == null) ? ChangeType.CREATE : ChangeType.DELETE;
                            FieldChange change = attachMapEvent(
                                FieldChange.builder()
                                    .fieldName(valuePath)
                                    .oldValue(oldValue)
                                    .newValue(newValue)
                                    .changeType(changeType),
                                oldKeyObj,
                                changeType
                            ).build();
                            changes.add(change);
                        }
                    } else {
                        // 非 Entity value，简单 UPDATE
                        FieldChange change = attachMapEvent(
                            FieldChange.builder()
                                .fieldName(valuePath)
                                .oldValue(oldValue)
                                .newValue(newValue)
                                .changeType(ChangeType.UPDATE),
                            oldKeyObj,
                            ChangeType.UPDATE
                        ).build();
                        changes.add(change);
                    }
                }

                // 如果开关打开，比较 key 的非 @Key 属性
                if (options.isTrackEntityKeyAttributes()) {
                    String keyPath = buildMapKeyAttrPath(oldKeyObj);
                    List<FieldChange> keyAttrChanges =
                        compareEntityKeyAttributes(oldKeyObj, newKeyObj, keyPath, options);
                    changes.addAll(keyAttrChanges);
                }
            }
        }

        // 处理 @Key 改变或新增/删除
        for (Map.Entry<String, Object> entry : oldKeyMap.entrySet()) {
            String stableKey = entry.getKey();
            if (!processedKeys.contains(stableKey)) {
                // DELETE (@Key 改变或真删除)
                Object oldKeyObj = entry.getValue();
                Object oldValue = map1.get(oldKeyObj);
                String deletePath = buildMapPath(oldKeyObj);
                FieldChange change = attachMapEventWithEntity(
                    FieldChange.builder()
                        .fieldName(deletePath)
                        .oldValue(oldValue)
                        .newValue(null)
                        .changeType(ChangeType.DELETE),
                    oldKeyObj,
                    ChangeType.DELETE,
                    resolveEntityKey(oldValue, null),
                    null
                ).build();
                changes.add(change);
            }
        }

        for (Map.Entry<String, Object> entry : newKeyMap.entrySet()) {
            String stableKey = entry.getKey();
            if (!processedKeys.contains(stableKey)) {
                // CREATE (@Key 改变或真新增)
                Object newKeyObj = entry.getValue();
                Object newValue = map2.get(newKeyObj);
                String createPath = buildMapPath(newKeyObj);
                FieldChange change = attachMapEventWithEntity(
                    FieldChange.builder()
                        .fieldName(createPath)
                        .oldValue(null)
                        .newValue(newValue)
                        .changeType(ChangeType.CREATE),
                    newKeyObj,
                    ChangeType.CREATE,
                    resolveEntityKey(null, newValue),
                    null
                ).build();
                changes.add(change);
            }
        }
    }
    
    /**
     * 处理包含重命名的变更（增强支持 Entity value）
     */
    private void processChangesWithRenames(Map<?, ?> map1, Map<?, ?> map2,
                                          Map<Object, Object> renames,
                                          List<FieldChange> changes) {
        Set<Object> processedKeys1 = new HashSet<>();
        Set<Object> processedKeys2 = new HashSet<>();

        // 处理重命名
        for (Map.Entry<Object, Object> rename : renames.entrySet()) {
            Object oldKey = rename.getKey();
            Object newKey = rename.getValue();
            Object value = map1.get(oldKey);

            FieldChange renameChange = attachMapEvent(
                FieldChange.builder()
                    .fieldName(String.valueOf(oldKey))
                    .oldValue(value)
                    .newValue(value)
                    .changeType(ChangeType.MOVE) // 使用MOVE类型表示重命名
                    .fieldPath(String.valueOf(newKey)), // 使用fieldPath存储新键名
                newKey,
                ChangeType.MOVE
            ).build();
            changes.add(renameChange);

            processedKeys1.add(oldKey);
            processedKeys2.add(newKey);
        }

        // 处理其他变更
        Set<Object> allKeys = new HashSet<>();
        allKeys.addAll(map1.keySet());
        allKeys.addAll(map2.keySet());

        for (Object key : allKeys) {
            if (processedKeys1.contains(key) || processedKeys2.contains(key)) {
                continue; // 已经作为重命名处理过
            }

            Object value1 = map1.get(key);
            Object value2 = map2.get(key);

            // 检测 value 是否为 Entity（Entity 需要深度对比，不能仅依赖 equals()）
            boolean isEntityValue = isEntity(value1) || isEntity(value2);

            if (isEntityValue) {
                // Entity value 深度对比（即使 equals() 返回 true 也要对比）
                if (value1 != null && value2 != null) {
                    String pathPrefix = PathUtils.buildMapValuePath(String.valueOf(key));
                    List<FieldChange> deepChanges = compareEntityDeep(value1, value2, pathPrefix, key);
                    changes.addAll(deepChanges);
                } else if (!Objects.equals(value1, value2)) {
                    // 一侧为 null，简单处理
                    ChangeType changeType = (value1 == null) ? ChangeType.CREATE : ChangeType.DELETE;
                    FieldChange change = attachMapEventWithEntity(
                        FieldChange.builder()
                            .fieldName(String.valueOf(key))
                            .oldValue(value1)
                            .newValue(value2)
                            .changeType(changeType),
                        key,
                        changeType,
                        resolveEntityKey(value1, value2),
                        null
                    ).build();
                    changes.add(change);
                }
            } else if (!Objects.equals(value1, value2)) {
                // 非 Entity value，原有逻辑
                ChangeType changeType;
                if (value1 == null) {
                    changeType = ChangeType.CREATE;
                } else if (value2 == null) {
                    changeType = ChangeType.DELETE;
                } else {
                    changeType = ChangeType.UPDATE;
                }

                FieldChange change = attachMapEvent(
                    FieldChange.builder()
                        .fieldName(String.valueOf(key))
                        .oldValue(value1)
                        .newValue(value2)
                        .changeType(changeType),
                    key,
                    changeType
                ).build();
                changes.add(change);
            }
        }
    }

    // ==================== PR-3: Entity key/value 深度对比支持 ====================

    /**
     * 检测对象是否为 Entity
     */
    private boolean isEntity(Object obj) {
        if (obj == null) return false;
        return obj.getClass().isAnnotationPresent(Entity.class);
    }

    /**
     * 检测对象是否有 @Key 字段（P3.1 统一到 EntityKeyUtils SSOT）
     */
    private boolean hasKeyFields(Object obj) {
        if (obj == null) return false;
        return !EntityKeyUtils.collectKeyFields(obj.getClass()).isEmpty();
    }

    /**
     * 格式化 key 用于路径显示（统一使用 EntityKeyUtils SSOT）
     */
    private String formatKey(Object key) {
        if (key == null) return "null";
        if (isEntity(key)) {
            String stableKey = EntityKeyUtils.computeStableKeyOrUnresolved(key);
            return "KEY:" + stableKey;
        }
        return String.valueOf(key);
    }

    /**
     * 构建 Map 值路径（统一使用 PathUtils）
     */
    private String buildMapPath(Object key) {
        // For map values, use the formatted display key
        String displayKey = formatKey(key);
        return PathUtils.buildMapValuePath(displayKey);
    }

    /**
     * 构建 Map 键属性路径（统一使用 PathUtils）
     */
    private String buildMapKeyAttrPath(Object key) {
        // P1: 支持 @Key 字段但无 @Entity 注解的对象（使用 KEY: 前缀）
        String stableKey = EntityKeyUtils.computeStableKeyOrUnresolved(key);
        if (!EntityKeyUtils.UNRESOLVED.equals(stableKey)) {
            // 对象有 @Key 字段（无论是否有 @Entity 注解）
            return PathUtils.buildMapKeyAttrPath(stableKey);
        }
        // 非实体 key，无属性
        return PathUtils.buildMapValuePath(String.valueOf(key));
    }

    /**
     * 构建 Entity key 映射（@Key → 对象，统一使用 EntityKeyUtils SSOT）
     */
    private Map<String, Object> buildEntityKeyMap(Set<?> keys) {
        Map<String, Object> map = new HashMap<>();
        for (Object key : keys) {
            if (isEntity(key) || hasKeyFields(key)) {
                String stableKey = EntityKeyUtils.computeStableKeyOrUnresolved(key);
                map.put(stableKey, key);
            }
        }
        return map;
    }

    /**
     * Entity value 深度对比（延迟快照，异常回退）
     */
    private List<FieldChange> compareEntityDeep(Object v1, Object v2, String pathPrefix, Object mapKey) {
        List<FieldChange> changes = new ArrayList<>();
        try {
            // 创建深度快照配置
            SnapshotConfig config = new SnapshotConfig();
            config.setMaxDepth(10);
            ObjectSnapshotDeep snapshotStrategy = new ObjectSnapshotDeep(config);
            // 使用空集合而非 null，避免 NPE
            Set<String> emptySet = Collections.emptySet();
            Map<String, Object> snap1 = snapshotStrategy.captureDeep(v1, 10, emptySet, emptySet);
            Map<String, Object> snap2 = snapshotStrategy.captureDeep(v2, 10, emptySet, emptySet);

            // 使用 DiffDetector 对比快照
            List<com.syy.taskflowinsight.tracking.model.ChangeRecord> records =
                com.syy.taskflowinsight.tracking.detector.DiffFacade.diff(pathPrefix, snap1, snap2);

            // 转换为 FieldChange
            for (com.syy.taskflowinsight.tracking.model.ChangeRecord rec : records) {
                FieldChange fc = attachMapEventWithEntity(
                    FieldChange.builder()
                        .fieldName(rec.getFieldName())
                        .fieldPath(pathPrefix + "." + rec.getFieldName())
                        .oldValue(rec.getOldValue())
                        .newValue(rec.getNewValue())
                        .changeType(rec.getChangeType()),
                    mapKey,
                    ChangeType.UPDATE,
                    resolveEntityKey(v1, v2),
                    rec.getFieldName()
                ).build();
                changes.add(fc);
            }
        } catch (Exception e) {
            // 深度对比失败，回退到简单 UPDATE
            DiagnosticLogger.once(
                "MAP-001",
                "EntityDeepCompareFailed",
                e.getMessage(),
                "Fallback to simple UPDATE for " + pathPrefix
            );
            logger.debug("Entity deep compare failed for {}: {}", pathPrefix, e.getMessage());

            FieldChange fallback = attachMapEventWithEntity(
                FieldChange.builder()
                    .fieldName(pathPrefix)
                    .oldValue(v1)
                    .newValue(v2)
                    .changeType(ChangeType.UPDATE),
                mapKey,
                ChangeType.UPDATE,
                resolveEntityKey(v1, v2),
                null
            ).build();
            changes.add(fallback);
        }
        return changes;
    }

    /**
     * Entity key 非 @Key 属性对比（仅当开关打开且 @Key 相同）
     */
    private List<FieldChange> compareEntityKeyAttributes(Object k1, Object k2,
                                                         String pathPrefix,
                                                         CompareOptions options) {
        List<FieldChange> changes = new ArrayList<>();
        if (!options.isTrackEntityKeyAttributes()) {
            return changes; // 开关关闭，不追踪
        }

        try {
            // 获取所有非 @Key 字段
            List<Field> keyFields = EntityKeyUtils.collectKeyFields(k1.getClass());
            Set<String> keyFieldNames = new HashSet<>();
            for (Field f : keyFields) {
                keyFieldNames.add(f.getName());
            }

            // 对比所有字段
            for (Field f : k1.getClass().getDeclaredFields()) {
                if (keyFieldNames.contains(f.getName())) {
                    continue; // 跳过 @Key 字段
                }

                f.setAccessible(true);
                Object val1 = f.get(k1);
                Object val2 = f.get(k2);

                if (!Objects.equals(val1, val2)) {
                    FieldChange fc = attachMapEvent(
                        FieldChange.builder()
                            .fieldName(f.getName())
                            .fieldPath(pathPrefix + "." + f.getName())
                            .oldValue(val1)
                            .newValue(val2)
                            .changeType(ChangeType.UPDATE),
                        pathPrefix,
                        ChangeType.UPDATE
                    ).build();
                    changes.add(fc);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to compare Entity key attributes for {}: {}", pathPrefix, e.getMessage());
        }

        return changes;
    }
}
