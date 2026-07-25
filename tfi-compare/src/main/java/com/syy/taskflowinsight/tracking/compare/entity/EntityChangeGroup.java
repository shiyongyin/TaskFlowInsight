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
 * <h2>使用示例</h2>
 * <pre>{@code
 * EntityChangeGroup group = EntityChangeGroup.builder()
 *     .entityKey("entity[1001]")
 *     .operation(EntityOperation.MODIFY)
 *     .addChange(fieldChange)
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

    /** 兼容查询使用的实体键投影；canonical identity仍由FieldChange typed path持有。 */
    private final String entityKey;
    /** 当前分组从底层变更事实归纳出的实体级操作。 */
    private final EntityOperation operation;
    /** 可选实体声明类型；无法从兼容输入可靠确定时保持null。 */
    private final Class<?> entityClass;
    /** 仅供显式Builder消费者携带的旧侧对象，from(CompareResult)不会反向恢复业务对象。 */
    private final Object oldEntity;
    /** 仅供显式Builder消费者携带的新侧对象，from(CompareResult)不会反向恢复业务对象。 */
    private final Object newEntity;
    /** 兼容展示使用的复合键分片，不作为内核重新配对依据。 */
    private final List<String> keyParts;
    /** 唯一配对时的旧侧物理位置；重复key时保持null。 */
    private final Integer oldIndex;
    /** 唯一配对时的新侧物理位置；重复key时保持null。 */
    private final Integer newIndex;
    /** 旧侧重复key的全部物理位置，仅兼容投影使用。 */
    private final List<Integer> oldIndexes;
    /** 新侧重复key的全部物理位置，仅兼容投影使用。 */
    private final List<Integer> newIndexes;
    /** 是否存在位置移动（List 专用），当检测到 MOVE 或 oldIndex/newIndex 不等时为 true */
    private final boolean moved;
    /** 是否发生回退（例如缺少容器事件时基于路径解析/列表映射推断） */
    private final boolean degraded;
    /** 属于同一实体投影的不可变canonical变更事实。 */
    private final List<FieldChange> changes;

    private EntityChangeGroup(Builder builder) {
        this.entityKey = Objects.requireNonNull(builder.entityKey, "Entity key cannot be null");
        this.operation = Objects.requireNonNull(builder.operation, "Operation cannot be null");
        this.entityClass = builder.entityClass;
        this.oldEntity = builder.oldEntity;
        this.newEntity = builder.newEntity;
        this.keyParts = builder.keyParts != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.keyParts))
                : Collections.emptyList();
        this.oldIndex = builder.oldIndex;
        this.newIndex = builder.newIndex;
        this.oldIndexes = builder.oldIndexes != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.oldIndexes)) : null;
        this.newIndexes = builder.newIndexes != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.newIndexes)) : null;
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

    /**
     * 是否包含MOVE语义或可确认的索引变化。
     *
     * @return 存在独立MOVE事实或位置变化时返回true
     */
    public boolean isMoved() {
        return moved;
    }

    /**
     * 是否因缺少typed位置事实而使用兼容推断。
     *
     * @return 使用过兼容索引推断时返回true
     */
    public boolean isDegraded() {
        return degraded;
    }

    /**
     * 获取canonical typed key的组件投影；顺序与路径中的identity组件一致，可能为空列表。
     *
     * <p>这里不承诺反射字段声明顺序，因为结果也可能来自非反射的typed路径。</p>
     *
     * @return 未转义的诊断文本组件；不存在typed identity时返回兼容解析结果或空列表
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
     *
     * @return 唯一配对实体的旧侧索引；不存在或重复key时返回null
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
     *
     * @return 唯一配对实体的新侧索引；不存在或重复key时返回null
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
     * 实体变更兼容投影的建造者；构建时统一冻结集合，防止结果被调用方反向修改。
     *
     * @since v3.0.0
     */
    public static class Builder {
        /** 待构造分组的兼容实体键投影。 */
        private String entityKey;
        /** 待构造分组的实体级操作。 */
        private EntityOperation operation;
        /** 调用方可选提供的实体声明类型。 */
        private Class<?> entityClass;
        /** 调用方显式提供的旧侧实体；不会由结果事实反推。 */
        private Object oldEntity;
        /** 调用方显式提供的新侧实体；不会由结果事实反推。 */
        private Object newEntity;
        /** 用于兼容展示的复合键分片。 */
        private List<String> keyParts;
        /** 唯一key场景的旧侧位置。 */
        private Integer oldIndex;
        /** 唯一key场景的新侧位置。 */
        private Integer newIndex;
        /** 重复key场景的旧侧位置集合。 */
        private List<Integer> oldIndexes;
        /** 重复key场景的新侧位置集合。 */
        private List<Integer> newIndexes;
        /** 待冻结的字段变更事实。 */
        private List<FieldChange> changes = new ArrayList<>();
        /** 是否存在独立MOVE事实或可确认的位置变化。 */
        private boolean moved;
        /** 是否因缺少typed位置事实而使用了兼容推断。 */
        private boolean degraded;

        /**
         * 设置兼容实体键投影；canonical identity仍由typed路径持有。
         *
         * @param entityKey 非null的实体键投影
         * @return 当前建造者
         */
        public Builder entityKey(String entityKey) {
            this.entityKey = entityKey;
            return this;
        }

        /**
         * 设置由底层变更事实归纳出的实体级操作。
         *
         * @param operation 非null的实体操作
         * @return 当前建造者
         */
        public Builder operation(EntityOperation operation) {
            this.operation = operation;
            return this;
        }

        /**
         * 设置可选声明类型；无法可靠确定时应保持null而不是猜测。
         *
         * @param entityClass 实体声明类型；允许为null
         * @return 当前建造者
         */
        public Builder entityClass(Class<?> entityClass) {
            this.entityClass = entityClass;
            return this;
        }

        /**
         * 携带调用方显式提供的旧侧对象，不从快照反向恢复业务对象。
         *
         * @param oldEntity 旧侧实体；允许为null
         * @return 当前建造者
         */
        public Builder oldEntity(Object oldEntity) {
            this.oldEntity = oldEntity;
            return this;
        }

        /**
         * 携带调用方显式提供的新侧对象，不从快照反向恢复业务对象。
         *
         * @param newEntity 新侧实体；允许为null
         * @return 当前建造者
         */
        public Builder newEntity(Object newEntity) {
            this.newEntity = newEntity;
            return this;
        }

        /**
         * 替换分组内的canonical变更事实并防御复制。
         *
         * @param changes 字段变更列表；null按空列表处理
         * @return 当前建造者
         */
        public Builder changes(List<FieldChange> changes) {
            this.changes = changes != null ? new ArrayList<>(changes) : new ArrayList<>();
            return this;
        }

        /**
         * 追加一条已确认的字段变更，不执行额外去重或配对。
         *
         * @param change 字段变更；非null
         * @return 当前建造者
         */
        public Builder addChange(FieldChange change) {
            this.changes.add(change);
            return this;
        }

        /**
         * 设置typed identity的未转义组件投影。
         *
         * @param keyParts identity组件；允许为null并按空列表冻结
         * @return 当前建造者
         */
        public Builder keyParts(List<String> keyParts) {
            this.keyParts = keyParts;
            return this;
        }

        /**
         * 设置唯一配对实体的旧侧位置。
         *
         * @param oldIndex 旧侧索引；重复key或不存在时为null
         * @return 当前建造者
         */
        public Builder oldIndex(Integer oldIndex) {
            this.oldIndex = oldIndex;
            return this;
        }

        /**
         * 设置唯一配对实体的新侧位置。
         *
         * @param newIndex 新侧索引；重复key或不存在时为null
         * @return 当前建造者
         */
        public Builder newIndex(Integer newIndex) {
            this.newIndex = newIndex;
            return this;
        }

        /**
         * 设置重复key在旧侧的全部位置，避免first-wins覆盖事实。
         *
         * @param oldIndexes 旧侧索引集合；非重复场景为null
         * @return 当前建造者
         */
        public Builder oldIndexes(List<Integer> oldIndexes) {
            this.oldIndexes = oldIndexes;
            return this;
        }

        /**
         * 设置重复key在新侧的全部位置，避免first-wins覆盖事实。
         *
         * @param newIndexes 新侧索引集合；非重复场景为null
         * @return 当前建造者
         */
        public Builder newIndexes(List<Integer> newIndexes) {
            this.newIndexes = newIndexes;
            return this;
        }

        /**
         * 标记该分组是否包含可确认的位置变化。
         *
         * @param moved MOVE或索引变化标志
         * @return 当前建造者
         */
        public Builder moved(boolean moved) {
            this.moved = moved;
            return this;
        }

        /**
         * 标记该分组是否使用过兼容索引推断。
         *
         * @param degraded 降级标志
         * @return 当前建造者
         */
        public Builder degraded(boolean degraded) {
            this.degraded = degraded;
            return this;
        }

        /**
         * 冻结当前分组；必填的实体键和操作缺失时拒绝构建。
         *
         * @return 不可变的实体变更分组
         */
        public EntityChangeGroup build() {
            return new EntityChangeGroup(this);
        }
    }

    /**
     * 返回面向诊断的紧凑摘要，不展开实体对象或字段值以避免意外泄露。
     *
     * @return 包含key、操作和变更数量的摘要文本
     */
    @Override
    public String toString() {
        return String.format("EntityChangeGroup{key='%s', operation=%s, changes=%d}",
                entityKey, operation, changes.size());
    }
}
