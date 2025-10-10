package com.syy.taskflowinsight.tracking.compare.entity;

import com.syy.taskflowinsight.tracking.compare.FieldChange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 实体变更组
 * <p>
 * 表示单个实体的所有字段级变更。每个变更组对应一个实体，
 * 包含该实体的操作类型（新增/修改/删除）以及所有字段的变更记录。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * EntityChangeGroup group = EntityChangeGroup.builder()
 *     .entityKey("entity[1001]")
 *     .operation(EntityOperation.MODIFY)
 *     .addChange(FieldChange.builder()
 *         .fieldPath("entity[1001].name")
 *         .oldValue("Alice")
 *         .newValue("Bob")
 *         .build())
 *     .build();
 *
 * int count = group.getChangeCount();  // 1
 * List<FieldChange> nameChanges = group.getFieldChanges("name");
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since v3.0.0
 */
public class EntityChangeGroup {

    private final String entityKey;
    private final EntityOperation operation;
    private final Class<?> entityClass;
    private final Object oldEntity;
    private final Object newEntity;
    private final List<String> keyParts;
    private final Integer oldIndex;
    private final Integer newIndex;
    private final List<Integer> oldIndexes;
    private final List<Integer> newIndexes;
    /** 是否存在位置移动（List 专用），当检测到 MOVE 或 oldIndex/newIndex 不等时为 true */
    private final boolean moved;
    /** 是否发生回退（例如缺少容器事件时基于路径解析/列表映射推断） */
    private final boolean degraded;
    private final List<FieldChange> changes;

    private EntityChangeGroup(Builder builder) {
        this.entityKey = Objects.requireNonNull(builder.entityKey, "Entity key cannot be null");
        this.operation = Objects.requireNonNull(builder.operation, "Operation cannot be null");
        this.entityClass = builder.entityClass;
        this.oldEntity = builder.oldEntity;
        this.newEntity = builder.newEntity;
        this.keyParts = builder.keyParts != null ? Collections.unmodifiableList(new ArrayList<>(builder.keyParts)) : Collections.emptyList();
        this.oldIndex = builder.oldIndex;
        this.newIndex = builder.newIndex;
        this.oldIndexes = builder.oldIndexes != null ? Collections.unmodifiableList(new ArrayList<>(builder.oldIndexes)) : null;
        this.newIndexes = builder.newIndexes != null ? Collections.unmodifiableList(new ArrayList<>(builder.newIndexes)) : null;
        this.changes = Collections.unmodifiableList(new ArrayList<>(builder.changes));
        this.moved = builder.moved;
        this.degraded = builder.degraded;
    }

    /**
     * 获取实体键（如 "entity[1001]" 或 "entity[1001:US]"）
     *
     * @return 实体键字符串
     */
    public String getEntityKey() {
        return entityKey;
    }

    /**
     * 获取实体操作类型
     *
     * @return 操作类型（ADD/MODIFY/DELETE）
     */
    public EntityOperation getOperation() {
        return operation;
    }

    /**
     * 获取实体类型（可选，可能为 null）
     *
     * @return 实体的 Class 对象，如果无法推断则为 null
     */
    public Class<?> getEntityClass() {
        return entityClass;
    }

    /**
     * 获取旧实体对象（仅在可用时提供）
     *
     * @return 旧实体对象，可能为 null
     */
    public Object getOldEntity() {
        return oldEntity;
    }

    /**
     * 获取新实体对象（仅在可用时提供）
     *
     * @return 新实体对象，可能为 null
     */
    public Object getNewEntity() {
        return newEntity;
    }

    /**
     * 获取所有字段级变更（不可变列表）
     *
     * @return 字段变更列表
     */
    public List<FieldChange> getChanges() {
        return changes;
    }

    /** 是否包含移动（MOVE）语义或索引变更 */
    public boolean isMoved() {
        return moved;
    }

    /** 是否处于降级模式（缺少结构化事件信息而回退推断） */
    public boolean isDegraded() {
        return degraded;
    }

    /**
     * 获取主键分片（顺序与@Key声明一致）。可能为空列表。
     */
    public List<String> getKeyParts() {
        return keyParts;
    }

    /**
     * 获取旧列表索引（不存在时为null）。
     * <p>
     * 仅当实体在两侧均为单实例（1:1 场景）时填充。
     * 重复 key 场景下为 null，此时使用 {@link #getOldIndexes()} 获取索引列表。
     * </p>
     */
    public Integer getOldIndex() {
        return oldIndex;
    }

    /**
     * 获取新列表索引（不存在时为null）。
     * <p>
     * 仅当实体在两侧均为单实例（1:1 场景）时填充。
     * 重复 key 场景下为 null，此时使用 {@link #getNewIndexes()} 获取索引列表。
     * </p>
     */
    public Integer getNewIndex() {
        return newIndex;
    }

    /**
     * 获取旧列表索引列表（重复 key 场景）。
     * <p>
     * 仅当实体在 oldList 侧出现多次（或 newList 侧也出现多次）时填充。
     * 索引按出现顺序排列。1:1 场景下为 null。
     * CREATE 操作（仅 newList 侧存在）时为 null。
     * </p>
     *
     * @return 旧列表索引列表（不可变），重复 key 场景才非空
     */
    public List<Integer> getOldIndexes() {
        return oldIndexes;
    }

    /**
     * 获取新列表索引列表（重复 key 场景）。
     * <p>
     * 仅当实体在 newList 侧出现多次（或 oldList 侧也出现多次）时填充。
     * 索引按出现顺序排列。1:1 场景下为 null。
     * DELETE 操作（仅 oldList 侧存在）时为 null。
     * </p>
     *
     * @return 新列表索引列表（不可变），重复 key 场景才非空
     */
    public List<Integer> getNewIndexes() {
        return newIndexes;
    }

    /**
     * 是否包含字段级变更
     *
     * @return true 如果有字段变更，false 如果为空（如整体删除/新增）
     */
    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    /**
     * 获取字段变更数量
     *
     * @return 字段变更的数量
     */
    public int getChangeCount() {
        return changes.size();
    }

    /**
     * 获取特定字段的变更
     * <p>
     * 根据字段名称（不含路径前缀）查找匹配的 FieldChange。
     * 匹配逻辑：优先使用 fieldPath，回退到 fieldName，
     * 如果路径以 ".字段名" 结尾或等于字段名，则认为匹配。
     * </p>
     *
     * @param fieldName 字段名称（如 "name"）
     * @return 匹配的字段变更列表
     */
    public List<FieldChange> getFieldChanges(String fieldName) {
        return changes.stream()
                .filter(c -> {
                    String path = c.getFieldPath() != null ? c.getFieldPath() : c.getFieldName();
                    return path != null && (path.endsWith("." + fieldName) || path.equals(fieldName));
                })
                .toList();
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
     * EntityChangeGroup 建造者
     */
    public static class Builder {
        private String entityKey;
        private EntityOperation operation;
        private Class<?> entityClass;
        private Object oldEntity;
        private Object newEntity;
        private List<String> keyParts;
        private Integer oldIndex;
        private Integer newIndex;
        private List<Integer> oldIndexes;
        private List<Integer> newIndexes;
        private List<FieldChange> changes = new ArrayList<>();
        private boolean moved;
        private boolean degraded;

        public Builder entityKey(String entityKey) {
            this.entityKey = entityKey;
            return this;
        }

        public Builder operation(EntityOperation operation) {
            this.operation = operation;
            return this;
        }

        public Builder entityClass(Class<?> entityClass) {
            this.entityClass = entityClass;
            return this;
        }

        public Builder oldEntity(Object oldEntity) {
            this.oldEntity = oldEntity;
            return this;
        }

        public Builder newEntity(Object newEntity) {
            this.newEntity = newEntity;
            return this;
        }

        public Builder changes(List<FieldChange> changes) {
            this.changes = changes != null ? new ArrayList<>(changes) : new ArrayList<>();
            return this;
        }

        public Builder addChange(FieldChange change) {
            this.changes.add(change);
            return this;
        }

        public Builder keyParts(List<String> keyParts) {
            this.keyParts = keyParts;
            return this;
        }

        public Builder oldIndex(Integer oldIndex) {
            this.oldIndex = oldIndex;
            return this;
        }

        public Builder newIndex(Integer newIndex) {
            this.newIndex = newIndex;
            return this;
        }

        public Builder oldIndexes(List<Integer> oldIndexes) {
            this.oldIndexes = oldIndexes;
            return this;
        }

        public Builder newIndexes(List<Integer> newIndexes) {
            this.newIndexes = newIndexes;
            return this;
        }

        public Builder moved(boolean moved) {
            this.moved = moved;
            return this;
        }

        public Builder degraded(boolean degraded) {
            this.degraded = degraded;
            return this;
        }

        public EntityChangeGroup build() {
            return new EntityChangeGroup(this);
        }
    }

    @Override
    public String toString() {
        return String.format("EntityChangeGroup{key='%s', operation=%s, changes=%d}",
                entityKey, operation, changes.size());
    }
}
