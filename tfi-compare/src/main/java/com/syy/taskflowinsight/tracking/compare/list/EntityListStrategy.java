package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.syy.taskflowinsight.tracking.compare.CompareConstants.STRATEGY_ENTITY;

/**
 * Entity列表比较策略
 * 专门处理包含@Entity注解对象的列表比较
 *
 * 特点：
 * 1. 通过@Key注解识别相同实体
 * 2. 支持深度比对实体内部字段变化
 * 3. 准确识别新增、删除、更新的实体
 * 4. 支持父类@Key字段继承（P2-1增强）
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
@Component("entityListStrategy")
public class EntityListStrategy implements ListCompareStrategy {

    private static final Logger logger = LoggerFactory.getLogger(EntityListStrategy.class);

    /**
     * 缓存类的@Key字段信息，避免重复反射
     * Key: 实体类Class对象
     * Value: 该类及其父类的所有@Key字段列表（父类字段在前）
     */
    // P3.1: 移除本地缓存，统一使用 EntityKeyUtils 缓存

    @Override
    public String getStrategyName() {
        return STRATEGY_ENTITY;
    }

    @Override
    public int getMaxRecommendedSize() {
        // Entity列表建议限制在100个元素以内，避免性能问题
        return 100;
    }

    @Override
    public boolean supportsMoveDetection() {
        // Entity策略不支持移动检测，因为是基于@Key匹配的
        return false;
    }

    @Override
    public CompareResult compare(List<?> list1, List<?> list2, CompareOptions options) {
        if (list1 == list2) {
            return CompareResult.identical();
        }

        if (list1 == null || list2 == null) {
            return CompareResult.ofNullDiff(list1, list2);
        }

        // 检查是否为Entity列表
        if (!isEntityList(list1) && !isEntityList(list2)) {
            // 如果都不是Entity列表，返回空结果
            return CompareResult.builder()
                .object1(list1)
                .object2(list2)
                .changes(new ArrayList<>())
                .identical(false)
                .algorithmUsed(STRATEGY_ENTITY)
                .build();
        }

        List<FieldChange> changes = new ArrayList<>();
        Set<String> duplicateKeysFound = new HashSet<>();

        // 创建实体映射（基于@Key字段，支持重复key）
        Map<String, List<Object>> entityMap1 = createEntityMap(list1);
        Map<String, List<Object>> entityMap2 = createEntityMap(list2);

        // 找出所有的key
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(entityMap1.keySet());
        allKeys.addAll(entityMap2.keySet());

        // 比较每个实体
        for (String key : allKeys) {
            List<Object> entities1 = entityMap1.getOrDefault(key, Collections.emptyList());
            List<Object> entities2 = entityMap2.getOrDefault(key, Collections.emptyList());

            int count1 = entities1.size();
            int count2 = entities2.size();

            // ✅ 核心逻辑：任一侧出现 >1 次 → 独立的CREATE/DELETE
            if (count1 > 1 || count2 > 1) {
                // 重复key场景：无法确定1:1对应关系，视为独立对象
                handleMultipleInstances(key, entities1, entities2, changes);
                duplicateKeysFound.add(key);

            } else if (count1 == 1 && count2 == 1) {
                // 正常1:1比较
                List<FieldChange> entityChanges = compareEntity(key, entities1.get(0), entities2.get(0));
                changes.addAll(entityChanges);

            } else if (count1 == 1 && count2 == 0) {
                // 删除
                FieldChange change = FieldChange.builder()
                    .fieldName("entity[" + key + "]")
                    .fieldPath("entity[" + key + "]")
                    .oldValue(entities1.get(0))
                    .newValue(null)
                    .changeType(ChangeType.DELETE)
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents
                            .listRemove(null, "entity[" + key + "]")
                    )
                    .build();
                changes.add(change);

            } else if (count1 == 0 && count2 == 1) {
                // 新增
                FieldChange change = FieldChange.builder()
                    .fieldName("entity[" + key + "]")
                    .fieldPath("entity[" + key + "]")
                    .oldValue(null)
                    .newValue(entities2.get(0))
                    .changeType(ChangeType.CREATE)
                    .elementEvent(
                        com.syy.taskflowinsight.tracking.compare.ContainerEvents
                            .listAdd(null, "entity[" + key + "]")
                    )
                    .build();
                changes.add(change);
            }
        }

        return CompareResult.builder()
            .object1(list1)
            .object2(list2)
            .changes(changes)
            .identical(changes.isEmpty())
            .duplicateKeys(duplicateKeysFound)
            .algorithmUsed(STRATEGY_ENTITY)
            .build();
    }

    /**
     * 检查列表是否包含Entity注解的对象
     * P2-1增强：支持检测父类@Key字段，检查前3个非空元素
     */
    private boolean isEntityList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }

        // 检查前3个非空元素
        int checked = 0;
        for (Object item : list) {
            if (item != null) {
                Class<?> itemClass = item.getClass();

                // 检查@Entity注解
                if (itemClass.isAnnotationPresent(Entity.class)) {
                    return true;
                }

                // 检查是否有@Key字段（包括父类）
                List<Field> keyFields = getKeyFields(itemClass);
                if (!keyFields.isEmpty()) {
                    logger.debug("Found {} @Key fields in class hierarchy of {}",
                            keyFields.size(), itemClass.getName());
                    return true;
                }

                // 最多检查3个元素
                if (++checked >= 3) {
                    break;
                }
            }
        }

        return false;
    }

    /**
     * 创建基于@Key的实体映射（支持重复key）
     *
     * 变更说明（v3.0.0）：
     * - 从 Map&lt;String, Object&gt; 改为 Map&lt;String, List&lt;Object&gt;&gt;
     * - 保留所有重复key的实例，不覆盖
     * - 检测到重复key时记录警告日志
     *
     * @param list 实体列表
     * @return key → 实体列表的映射
     */
    private Map<String, List<Object>> createEntityMap(List<?> list) {
        Map<String, List<Object>> entityMap = new LinkedHashMap<>();
        Set<String> duplicateKeys = new HashSet<>();

        for (Object entity : list) {
            if (entity == null) continue;

            String key = extractCompactKeyForPath(entity);
            if (key != null) {
                // 检查是否重复
                if (entityMap.containsKey(key)) {
                    duplicateKeys.add(key);
                    logger.debug("[DUPLICATE_KEY] entity[{}] appears multiple times. " +
                               "This typically indicates one of the following:\n" +
                               "  1. equals()/hashCode() implementation does not match @Key semantics\n" +
                               "  2. Business scenario requires multiple instances (version history, candidates)\n" +
                               "  3. Data quality issue in source data\n" +
                               "These instances will be treated as independent CREATE/DELETE operations.",
                               key);
                }

                // 添加到列表
                entityMap.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
            }
        }

        // 统计日志（汇总WARN，避免过多单条日志）
        if (!duplicateKeys.isEmpty()) {
            logger.warn("[DUPLICATE_KEYS] Found {} keys with duplicate instances: {}. " +
                       "Output changes will use CREATE/DELETE instead of UPDATE. " +
                       "Check equals()/hashCode() implementation if this is unexpected.",
                       duplicateKeys.size(), duplicateKeys);
        }

        return entityMap;
    }

    /**
     * 提取实体的Key值
     * 统一使用 EntityKeyUtils.computeStableKeyOrUnresolved (SSOT)
     * 不再使用 toString() 兜底
     */
    private String extractEntityKey(Object entity) {
        // 命名键（field=value|field=value），供增强测试验证
        return EntityKeyUtils.computeStableKeyOrUnresolved(entity);
    }

    private String extractCompactKeyForPath(Object entity) {
        return EntityKeyUtils.computeCompactKeyOrUnresolved(entity);
    }

    /**
     * 比较两个实体的内部字段
     */
    private List<FieldChange> compareEntity(String key, Object entity1, Object entity2) {
        List<FieldChange> changes = new ArrayList<>();

        // 使用ObjectSnapshotDeep进行深度比较（支持嵌套Entity）
        ObjectSnapshotDeep deepSnapshot = new ObjectSnapshotDeep(new SnapshotConfig());
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .build();

        Map<String, Object> snapshot1 = deepSnapshot.captureDeep(entity1, options);
        Map<String, Object> snapshot2 = deepSnapshot.captureDeep(entity2, options);

        List<ChangeRecord> diffResults = com.syy.taskflowinsight.tracking.detector.DiffFacade
            .diff("entity[" + key + "]", snapshot1, snapshot2);

        // 转换ChangeRecord为FieldChange
        for (ChangeRecord record : diffResults) {
            // 跳过@Key字段的变化（因为Key相同才会进入这个比较）
            if (isKeyField(entity1.getClass(), record.getFieldName())) {
                continue;
            }

            // 在构建 FieldChange 前执行引用变更检测（相对实体的字段路径）
            FieldChange.ReferenceDetail refDetail = null;
            try {
                refDetail = com.syy.taskflowinsight.tracking.detector.DiffFacade.detectReferenceChange(
                    entity1, entity2, record.getFieldName(), record.getOldValue(), record.getNewValue());
            } catch (Throwable ignore) {}

            FieldChange.FieldChangeBuilder builder = FieldChange.builder()
                .fieldName("entity[" + key + "]." + record.getFieldName())
                .fieldPath("entity[" + key + "]." + record.getFieldName())
                .oldValue(record.getOldValue())
                .newValue(record.getNewValue())
                .changeType(record.getChangeType())
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .listModify(null, "entity[" + key + "]", record.getFieldName())
                );

            if (refDetail != null) {
                builder.referenceChange(true).referenceDetail(refDetail);
            }

            FieldChange change = builder.build();
            changes.add(change);
        }

        return changes;
    }

    /**
     * 处理重复key场景
     *
     * 策略：
     * - 无法确定"哪个旧对象对应哪个新对象"
     * - 将所有实例视为独立对象
     * - 新列表中的记录为CREATE，旧列表中的记录为DELETE
     *
     * @param key 重复的实体key
     * @param entities1 旧列表中的所有实例
     * @param entities2 新列表中的所有实例
     * @param changes 变更记录列表（输出参数）
     */
    private void handleMultipleInstances(String key,
                                        List<Object> entities1,
                                        List<Object> entities2,
                                        List<FieldChange> changes) {
        // ✅ 关键修复：只要任一侧存在重复，两侧都使用 #idx 格式（单个实例用 #0）
        // 这样可以保证分组一致性，避免 entity[1] 和 entity[1#0] 分散到不同组
        boolean useIndexedFormat = entities1.size() > 1 || entities2.size() > 1;

        // 记录新列表中的所有实例为CREATE
        for (int i = 0; i < entities2.size(); i++) {
            Object entity = entities2.get(i);

            // 统一使用索引格式（如果任一侧重复）
            String uniquePath = useIndexedFormat
                ? String.format("entity[%s#%d]", key, i)
                : String.format("entity[%s]", key);

            FieldChange change = FieldChange.builder()
                .fieldName(uniquePath)
                .fieldPath(uniquePath)
                .oldValue(null)
                .newValue(entity)
                .changeType(ChangeType.CREATE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .listAdd(null, uniquePath, true)
                )
                .build();
            changes.add(change);
        }

        // 记录旧列表中的所有实例为DELETE
        for (int i = 0; i < entities1.size(); i++) {
            Object entity = entities1.get(i);

            // 统一使用索引格式（如果任一侧重复）
            String uniquePath = useIndexedFormat
                ? String.format("entity[%s#%d]", key, i)
                : String.format("entity[%s]", key);

            FieldChange change = FieldChange.builder()
                .fieldName(uniquePath)
                .fieldPath(uniquePath)
                .oldValue(entity)
                .newValue(null)
                .changeType(ChangeType.DELETE)
                .elementEvent(
                    com.syy.taskflowinsight.tracking.compare.ContainerEvents
                        .listRemove(null, uniquePath, true)
                )
                .build();
            changes.add(change);
        }

        logger.debug("Handled duplicate key '{}': {} old instances, {} new instances (indexed format: {})",
                    key, entities1.size(), entities2.size(), useIndexedFormat);
    }

    /**
     * 检查是否为@Key字段
     * 使用反射检查实体类中该字段是否有@Key注解
     */
    private boolean isKeyField(Class<?> entityClass, String fieldName) {
        if (entityClass == null || fieldName == null) {
            return false;
        }

        try {
            // 查找字段（包括父类字段）
            Field field = findField(entityClass, fieldName);
            if (field != null) {
                return field.isAnnotationPresent(Key.class);
            }
        } catch (Exception e) {
            logger.debug("Failed to check @Key annotation for field: {}", fieldName, e);
        }

        return false;
    }

    /**
     * 查找字段（包括父类字段）
     */
    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取类及其父类的所有@Key字段（P3.1 统一到 EntityKeyUtils SSOT）
     * 使用 EntityKeyUtils 缓存避免重复反射
     * 返回顺序：父类字段在前，子类字段在后
     *
     * @param clazz 实体类
     * @return @Key字段列表（不可变）
     */
    private List<Field> getKeyFields(Class<?> clazz) {
        return EntityKeyUtils.collectKeyFields(clazz);
    }

    // P3.1: clearCache() 和 getCacheSize() 已移除，缓存由 EntityKeyUtils 统一管理

    // ========== 公共工具方法（用于下游兼容性） ==========

    /**
     * 从路径中提取纯净的实体key（移除 #idx 后缀）
     *
     * 用于下游组件解析entity路径时的兼容性处理。
     * 当检测到重复key时，路径格式为 "entity[key#idx]"，此方法提取出纯净的key。
     *
     * @param path 路径，如 "entity[1#0]" 或 "entity[123:US#1]"
     * @return 纯净key，如 "1" 或 "123:US"；如果路径格式不正确返回原始路径
     */
    public static String extractPureEntityKey(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        int start = path.indexOf('[');
        int end = path.indexOf(']');
        if (start >= 0 && end > start) {
            String key = path.substring(start + 1, end);

            // 移除 #idx 后缀
            if (key.contains("#")) {
                key = key.substring(0, key.indexOf('#'));
            }

            return key;
        }
        return path;
    }

    /**
     * 从路径中提取重复索引（如有）
     *
     * 用于识别同一key的多个实例。
     * 当检测到重复key时，路径格式为 "entity[key#idx]"，此方法提取出索引值。
     *
     * @param path 路径，如 "entity[1#0]"
     * @return 索引，如 0；如果无后缀返回 -1
     */
    public static int extractDuplicateIndex(String path) {
        if (path == null || path.isEmpty()) {
            return -1;
        }

        int start = path.indexOf('[');
        int end = path.indexOf(']');
        if (start >= 0 && end > start) {
            String key = path.substring(start + 1, end);

            if (key.contains("#")) {
                String indexPart = key.substring(key.indexOf('#') + 1);
                try {
                    return Integer.parseInt(indexPart);
                } catch (NumberFormatException e) {
                    logger.debug("Failed to parse duplicate index from path: {}", path, e);
                    return -1;
                }
            }
        }
        return -1;
    }

}
