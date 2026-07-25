package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.PathSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 比较结果中单个路径变化的不可变、有界证据。
 *
 * <p>该类型位于 producer 与结果真值之间，只保存闭集 {@link ChangeKind} 及两侧
 * {@link ChangeSide}。工厂会立即把输入值收敛为 {@link ValueSnapshot}，避免结果对象长期持有业务对象；
 * side 组合在构造边界统一校验，调用方不能用缺失路径或错误前后态伪造变更种类。</p>
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class FieldChange {

    /** 兼容工厂的单值事实上限，避免旧producer把任意大值带入结果。 */
    private static final int DEFAULT_MAX_VALUE_CHARS = 4096;

    /** 决定before/after合法组合的canonical变更种类。 */
    private final ChangeKind kind;

    /** 变更前有界事实；ADD必须缺失，present-null由ValueSnapshot显式表达。 */
    private final Optional<ChangeSide> before;

    /** 变更后有界事实；REMOVE必须缺失，不能保存原始业务对象。 */
    private final Optional<ChangeSide> after;

    private FieldChange(
            ChangeKind kind,
            Optional<ChangeSide> before,
            Optional<ChangeSide> after) {
        this.kind = kind;
        this.before = before;
        this.after = after;
    }

    /**
     * 构造canonical change并在唯一边界校验side组合，formatter不得再次猜测kind语义。
     *
     * @param kind 变更种类
     * @param before 变更前的有界路径和值事实
     * @param after 变更后的有界路径和值事实
     * @return side组合合法的不可变变更
     */
    public static FieldChange canonical(
            ChangeKind kind,
            Optional<ChangeSide> before,
            Optional<ChangeSide> after) {
        validateSides(kind, before, after);
        return new FieldChange(kind, before, after);
    }

    /**
     * 将调用方值立即收敛为同一路径上的canonical change。
     *
     * <p>该工厂只减少producer迁移样板；状态合法性仍由{@link #canonical(ChangeKind, Optional, Optional)}
     * 唯一校验。输入对象不会进入{@code FieldChange}，未知对象只留下类型元数据。</p>
     *
     * @param kind 非MOVE的变更种类
     * @param path 变更发生的canonical路径
     * @param beforeValue 变更前值；ADD时忽略
     * @param afterValue 变更后值；REMOVE时忽略
     * @return 只包含有界side facts的变更
     */
    public static FieldChange at(
            ChangeKind kind,
            ComparePath path,
            Object beforeValue,
            Object afterValue) {
        Objects.requireNonNull(path, "path");
        Optional<ChangeSide> beforeSide = kind == ChangeKind.ADD
                ? Optional.empty()
                : Optional.of(side(path, beforeValue));
        Optional<ChangeSide> afterSide = kind == ChangeKind.REMOVE
                ? Optional.empty()
                : Optional.of(side(path, afterValue));
        return canonical(kind, beforeSide, afterSide);
    }

    /**
     * 适配仍由快照检测器产生的legacy变更枚举，但不把该枚举保存进结果。
     *
     * <p>MOVE需要两个不同路径，不能通过单路径适配器猜测位置，因此显式拒绝并要求调用{@link #moved}。</p>
     *
     * @param legacyKind 上游检测记录的旧变更类型
     * @param path canonical路径
     * @param beforeValue 变更前值
     * @param afterValue 变更后值
     * @return 已映射到闭集{@link ChangeKind}的canonical变更
     */
    public static FieldChange fromLegacy(
            ChangeType legacyKind,
            ComparePath path,
            Object beforeValue,
            Object afterValue) {
        Objects.requireNonNull(legacyKind, "legacyKind");
        ChangeKind canonicalKind = switch (legacyKind) {
            case CREATE -> ChangeKind.ADD;
            case UPDATE -> ChangeKind.MODIFY;
            case DELETE -> ChangeKind.REMOVE;
            case MOVE -> throw new IllegalArgumentException("MOVE requires distinct canonical paths");
        };
        return at(canonicalKind, path, beforeValue, afterValue);
    }

    /**
     * 构造路径发生变化的MOVE事实，避免把新旧位置压成同一字符串字段。
     *
     * @param beforePath 移动前canonical路径
     * @param beforeValue 移动前值
     * @param afterPath 移动后canonical路径
     * @param afterValue 移动后值
     * @return before/after路径不同的MOVE事实
     */
    public static FieldChange moved(
            ComparePath beforePath,
            Object beforeValue,
            ComparePath afterPath,
            Object afterValue) {
        return canonical(
                ChangeKind.MOVE,
                Optional.of(side(beforePath, beforeValue)),
                Optional.of(side(afterPath, afterValue)));
    }

    private static ChangeSide side(
            ComparePath path,
            Object value) {
        return new ChangeSide(
                Objects.requireNonNull(path, "path"),
                ValueSnapshot.captureSupported(value, DEFAULT_MAX_VALUE_CHARS));
    }

    /** @return 决定两侧合法组合的canonical变更种类 */
    public ChangeKind kind() {
        return kind;
    }

    /** @return 变更前有界side；ADD时为空 */
    public Optional<ChangeSide> before() {
        return before;
    }

    /** @return 变更后有界side；REMOVE时为空 */
    public Optional<ChangeSide> after() {
        return after;
    }

    /** @return 变更前有界值事实；before side缺失时为空 */
    public Optional<ValueSnapshot> beforeValue() {
        return before.map(ChangeSide::value);
    }

    /** @return 变更后有界值事实；after side缺失时为空 */
    public Optional<ValueSnapshot> afterValue() {
        return after.map(ChangeSide::value);
    }

    /**
     * 为尚未迁移的展示器投影稳定路径；动态key只输出kind占位，避免绕过安全toString泄漏事实。
     *
     * @return 兼容展示路径；root返回{@code $}
     */
    public String getFieldPath() {
        ComparePath path = after
                .or(() -> before)
                .orElseThrow()
                .path();
        StringBuilder rendered = new StringBuilder();
        for (PathSegment segment : path.segments()) {
            if (segment instanceof PropertySegment property) {
                if (!rendered.isEmpty()) {
                    rendered.append('.');
                }
                rendered.append(property.name());
            } else if (segment instanceof IndexSegment index) {
                rendered.append('[').append(index.index()).append(']');
            } else {
                rendered.append('[').append(segment.kind().wireCode()).append(']');
            }
        }
        return rendered.isEmpty() ? "$" : rendered.toString();
    }

    public String getFieldName() {
        List<PathSegment> segments = after
                .or(() -> before)
                .orElseThrow()
                .path()
                .segments();
        for (int index = segments.size() - 1; index >= 0; index--) {
            if (segments.get(index) instanceof PropertySegment property) {
                return property.name();
            }
        }
        // 纯容器地址没有字段名；回退安全路径占位，不能展开动态key伪造属性名。
        return getFieldPath();
    }

    public ChangeType getChangeType() {
        return switch (kind) {
            case ADD -> ChangeType.CREATE;
            case REMOVE -> ChangeType.DELETE;
            case MOVE -> ChangeType.MOVE;
            case MODIFY, NULLNESS, TYPE_MISMATCH -> ChangeType.UPDATE;
        };
    }

    public String getValueType() {
        return afterValue().or(this::beforeValue)
                .map(ValueSnapshot::typeCode)
                .orElse("null");
    }

    private static void validateSides(
            ChangeKind kind,
            Optional<ChangeSide> before,
            Optional<ChangeSide> after) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        boolean valid = switch (kind) {
            case ADD -> before.isEmpty() && after.isPresent();
            case REMOVE -> before.isPresent() && after.isEmpty();
            case MODIFY, NULLNESS, TYPE_MISMATCH -> bothSidesHaveSamePath(before, after);
            case MOVE -> bothSidesHaveDifferentPaths(before, after);
        };
        if (!valid) {
            throw new IllegalArgumentException("change sides do not match kind");
        }
    }

    private static boolean bothSidesHaveSamePath(
            Optional<ChangeSide> before,
            Optional<ChangeSide> after) {
        return before.isPresent() && after.isPresent()
                && before.orElseThrow().path().equals(after.orElseThrow().path());
    }

    private static boolean bothSidesHaveDifferentPaths(
            Optional<ChangeSide> before,
            Optional<ChangeSide> after) {
        return before.isPresent() && after.isPresent()
                && !before.orElseThrow().path().equals(after.orElseThrow().path());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FieldChange change)) {
            return false;
        }
        return kind == change.kind
                && before.equals(change.before)
                && after.equals(change.after);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, before, after);
    }

    /**
     * 仅输出变更结构，避免日志通过path或snapshot展开泄漏动态key和exact value。
     *
     * @return 不含任何canonical text fact的安全摘要
     */
    @Override
    public String toString() {
        int beforeSegments = before.map(side -> side.path().segmentCount()).orElse(0);
        int afterSegments = after.map(side -> side.path().segmentCount()).orElse(0);
        return "FieldChange{"
                + "kind=" + kind
                + ", beforePresent=" + before.isPresent()
                + ", afterPresent=" + after.isPresent()
                + ", beforeSegments=" + beforeSegments
                + ", afterSegments=" + afterSegments
                + '}';
    }

    /**
     * 集合变更详情
     */
    @Data
    @Builder
    public static class CollectionChangeDetail {
        /** legacy集合摘要中已保留的新增数量，不参与canonical结果真值。 */
        private int addedCount;

        /** legacy集合摘要中已保留的删除数量，不参与canonical结果真值。 */
        private int removedCount;

        /** legacy集合摘要中已保留的修改数量，不参与canonical结果真值。 */
        private int modifiedCount;

        /** legacy集合摘要中的变更前容量，仅用于兼容展示。 */
        private int originalSize;

        /** legacy集合摘要中的变更后容量，仅用于兼容展示。 */
        private int newSize;

        /**
         * 为发布文档显式声明 builder 类型；成员仍由 Lombok 注入，避免 binary API 出现无 source/Javadoc 类型。
         */
        public static class CollectionChangeDetailBuilder {
        }
    }

    // ========== 内部类与枚举：容器事件 ==========

    /**
     * 容器元素事件详情（v3.1.0-P1）
     * <p>结构化容器变更信息，消除路径解析需求。</p>
     *
     * <p><b>字段说明</b>：
     * <ul>
     *   <li>index: List/Array 索引，Set/Map 为 null</li>
     *   <li>oldIndex/newIndex: MOVE 操作的移动前后位置</li>
     *   <li>entityKey: @Entity 元素的键（如 "order[O1]"），非 Entity 为 null</li>
     *   <li>mapKey: Map 的键对象（任意类型），仅 Map 容器有</li>
     *   <li>propertyPath: MODIFY 操作的具体字段路径（如 "price"）</li>
     * </ul>
     *
     * <p><b>mapKey vs entityKey</b>：
     * 对于 {@code Map<String, Order>}，mapKey 是 Map 的键（如 "order-1"），
     * entityKey 是值对象 Order 的实体键（如 "order[O1]"）。
     *
     * @since v3.1.0-P1
     */
    @Data
    @Builder
    public static class ContainerElementEvent {
        /** 容器类型（LIST/SET/MAP/ARRAY） */
        private ContainerType containerType;

        /** 元素操作（ADD/REMOVE/MODIFY/MOVE） */
        private ElementOperation operation;

        /** List/Array 索引（0-based），Set/Map 为 null */
        private Integer index;

        /** MOVE 操作：旧索引 */
        private Integer oldIndex;

        /** MOVE 操作：新索引 */
        private Integer newIndex;

        /** 实体键（@Entity 元素专用，格式如 "order[O1]"） */
        private String entityKey;

        /** Map 键（Map 专用，任意类型） */
        private Object mapKey;

        /** MODIFY 操作：元素内部字段相对路径（如 "price"） */
        private String propertyPath;

        /** 是否为重复 @Key 场景（Entity 列表专用） */
        private boolean duplicateKey;

        /**
         * 为发布文档显式声明 builder 类型；成员仍由 Lombok 注入，保持既有 fluent API。
         */
        public static class ContainerElementEventBuilder {
        }
    }

    /**
     * 容器类型枚举
     * @since v3.1.0-P1
     */
    public enum ContainerType {
        /** List 接口实现 */
        LIST,
        /** Set 接口实现 */
        SET,
        /** Map 接口实现 */
        MAP,
        /** 数组类型 */
        ARRAY
    }

    /**
     * 元素操作枚举
     * @since v3.1.0-P1
     */
    public enum ElementOperation {
        /** 新增元素 */
        ADD,
        /** 删除元素 */
        REMOVE,
        /** 修改元素属性（元素本身未变，内部字段变更） */
        MODIFY,
        /** 移动元素位置（仅 List，使用 oldIndex/newIndex） */
        MOVE
    }

    /**
     * 引用变更详情（v3.1.0-P1）
     * 记录引用身份变化，而非对象内部属性变化
     * @since v3.1.0-P1
     */
    @Data
    @Builder
    public static class ReferenceDetail {
        /** JSON control character使用大写十六进制，避免平台相关编码差异。 */
        private static final char[] JSON_HEX = "0123456789ABCDEF".toCharArray();

        /** 变更前有界实体键；null表示此前没有关联。 */
        private String oldEntityKey;

        /** 变更后有界实体键；null表示关联已移除。 */
        private String newEntityKey;

        /** 是否跨越null边界；用于区分建立/移除关联与普通引用切换。 */
        private boolean nullReferenceChange;

        /**
         * 为发布文档显式声明 builder 类型；成员仍由 Lombok 注入，保持既有 fluent API。
         */
        public static class ReferenceDetailBuilder {
        }

        /**
         * 导出为Map视图（用于序列化或API对接）
         *
         * @return Map视图，包含标准键：oldKey, newKey, isNullTransition, transitionType
         * @since v3.1.0-P1
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("oldKey", oldEntityKey);
            map.put("newKey", newEntityKey);
            map.put("isNullTransition", nullReferenceChange);

            // 便捷字段：过渡类型
            if (nullReferenceChange) {
                if (oldEntityKey == null) {
                    map.put("transitionType", "ASSOCIATION_ESTABLISHED");  // null → Entity
                } else {
                    map.put("transitionType", "ASSOCIATION_REMOVED");  // Entity → null
                }
            } else {
                map.put("transitionType", "REFERENCE_SWITCHED");  // Entity → Entity
            }

            return map;
        }

        /**
         * 导出为JSON字符串
         *
         * @return JSON表示
         * @since v3.1.0-P1
         */
        public String toJson() {
            String transitionType;
            if (nullReferenceChange) {
                transitionType = oldEntityKey == null
                        ? "ASSOCIATION_ESTABLISHED"
                        : "ASSOCIATION_REMOVED";
            } else {
                transitionType = "REFERENCE_SWITCHED";
            }
            return "{\"oldKey\":" + jsonString(oldEntityKey)
                    + ",\"newKey\":" + jsonString(newEntityKey)
                    + ",\"isNullTransition\":" + nullReferenceChange
                    + ",\"transitionType\":" + jsonString(transitionType) + "}";
        }

        private static String jsonString(String value) {
            if (value == null) {
                return "null";
            }
            StringBuilder output = new StringBuilder(value.length() + 2).append('"');
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                switch (current) {
                    case '"' -> output.append("\\\"");
                    case '\\' -> output.append("\\\\");
                    case '\b' -> output.append("\\b");
                    case '\f' -> output.append("\\f");
                    case '\n' -> output.append("\\n");
                    case '\r' -> output.append("\\r");
                    case '\t' -> output.append("\\t");
                    default -> {
                        if (current < 0x20 || isUnpairedSurrogate(value, index)) {
                            appendUnicodeEscape(output, current);
                        } else {
                            output.append(current);
                            if (Character.isHighSurrogate(current)) {
                                output.append(value.charAt(++index));
                            }
                        }
                    }
                }
            }
            return output.append('"').toString();
        }

        private static boolean isUnpairedSurrogate(String value, int index) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                return index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1));
            }
            return Character.isLowSurrogate(current);
        }

        private static void appendUnicodeEscape(StringBuilder output, char value) {
            output.append("\\u")
                    .append(JSON_HEX[(value >>> 12) & 0xF])
                    .append(JSON_HEX[(value >>> 8) & 0xF])
                    .append(JSON_HEX[(value >>> 4) & 0xF])
                    .append(JSON_HEX[value & 0xF]);
        }
    }
}
