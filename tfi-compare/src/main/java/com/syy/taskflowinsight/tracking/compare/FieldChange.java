package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import lombok.Builder;
import lombok.Data;

import java.time.Clock;

/**
 * 字段变更
 *
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@Data
@Builder
public class FieldChange {

    // ========== Clock 基础设施（用于测试时间控制） ==========
    private static Clock clock = Clock.systemUTC();

    public static Clock getClock() {
        return clock;
    }

    public static void setClock(Clock newClock) {
        clock = newClock;
    }
    
    /**
     * 字段名称
     */
    private String fieldName;
    
    /**
     * 旧值
     */
    private Object oldValue;
    
    /**
     * 新值
     */
    private Object newValue;
    
    /**
     * 变更类型
     */
    private ChangeType changeType;
    
    /**
     * 值类型
     */
    private String valueType;
    
    /**
     * 字段路径（用于嵌套对象）
     */
    private String fieldPath;
    
    /**
     * 是否为集合变更
     */
    private boolean collectionChange;
    
    /**
     * 集合变更详情
     */
    private CollectionChangeDetail collectionDetail;

    // ========== P1-T1: 容器事件结构化 ==========

    /**
     * 容器元素事件（List/Set/Map/Array 场景）
     * 非容器场景保持为 null，保证向后兼容
     * @since v3.1.0-P1
     */
    private ContainerElementEvent elementEvent;

    /**
     * 是否为容器元素变更
     *
     * <p><b>使用示例</b>：
     * <pre>{@code
     * if (change.isContainerElementChange()) {
     *     Integer index = change.getContainerIndex();  // 直接访问索引
     *     String entityKey = change.getEntityKey();    // 直接访问实体键
     * }
     * }</pre>
     *
     * @return true 当 elementEvent 非 null
     * @since v3.1.0-P1
     */
    public boolean isContainerElementChange() {
        return elementEvent != null;
    }

    /**
     * 获取容器索引（List/数组专用）
     *
     * <p><b>迁移指南</b>（消除路径解析样板代码）：
     * <pre>{@code
     * // ❌ 旧方式：手动解析路径
     * String path = change.getFieldPath(); // "items[2].price"
     * int index = Integer.parseInt(path.substring(
     *     path.indexOf('[') + 1, path.indexOf(']'))); // 繁琐
     *
     * // ✅ 新方式：直接访问
     * Integer index = change.getContainerIndex(); // 简洁
     * }</pre>
     *
     * @return 索引位置（0-based），非容器或非索引场景返回 null
     * @since v3.1.0-P1
     */
    public Integer getContainerIndex() {
        return elementEvent != null ? elementEvent.getIndex() : null;
    }

    /**
     * 获取实体键（Entity 元素专用）
     * @return 实体键字符串（如 "order[O1]"），非实体场景返回 null
     * @since v3.1.0-P1
     */
    public String getEntityKey() {
        return elementEvent != null ? elementEvent.getEntityKey() : null;
    }

    /**
     * 获取 Map 键（Map 专用）
     * @return 键对象（任意类型），非 Map 场景返回 null
     * @since v3.1.0-P1
     */
    public Object getMapKey() {
        return elementEvent != null ? elementEvent.getMapKey() : null;
    }

    /**
     * 获取容器 Map 键（Map 专用便捷方法，别名）
     * @return 键对象，非 Map 场景返回 null
     */
    public Object getContainerMapKey() {
        return getMapKey();
    }

    /**
     * 获取容器操作类型
     * @return 操作类型，非容器场景返回 null
     */
    public ElementOperation getContainerOperation() {
        return elementEvent != null ? elementEvent.getOperation() : null;
    }

    /**
     * 导出为 Typed 视图（Map 格式，用于序列化或 API 对接）
     * <p>
     * 将容器元素变更转换为标准化的 Map 视图，方便与前端/API 对接。
     * 非容器变更返回 null。
     * </p>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>JSON/XML 导出：直接序列化为标准化格式</li>
     *   <li>前端对接：无需理解 Java 内部类结构</li>
     *   <li>第三方集成：通用 Map 格式易于集成</li>
     * </ul>
     *
     * <h3>视图键集合</h3>
     * <pre>{@code
     * {
     *   "kind": "entry_added|entry_removed|entry_updated|entry_moved",
     *   "object": "订单",
     *   "path": "items[2].price",
     *   "timestamp": "2025-10-08T12:00:00Z",
     *   "details": {
     *     "containerType": "LIST",
     *     "operation": "ADD",
     *     "index": 2,
     *     "entityKey": "item[SKU-123]",
     *     "oldValue": null,
     *     "newValue": {"sku": "SKU-123", "price": 99.99}
     *   }
     * }
     * }</pre>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 示例1: 单个 FieldChange 导出 Map 视图
     * FieldChange change = result.getChanges().get(0);
     * if (change.isContainerElementChange()) {
     *     Map<String, Object> view = change.toTypedView();
     *     System.out.println(view.get("kind"));  // "entry_added"
     *     System.out.println(view.get("path"));  // "items[2].price"
     * }
     *
     * // 示例2: 批量导出（通过 Stream）
     * List<Map<String, Object>> views = result.getChanges().stream()
     *     .map(FieldChange::toTypedView)
     *     .filter(Objects::nonNull)
     *     .collect(Collectors.toList());
     * }</pre>
     *
     * @return Map 视图，null 如果非容器变更
     * @since v3.1.0-P1-Enhanced
     */
    public java.util.Map<String, Object> toTypedView() {
        if (!isContainerElementChange()) {
            return null;  // 非容器变更不生成视图
        }

        java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("kind", toViewKind(elementEvent.getOperation()));
        view.put("object", FieldChange.extractObjectName(fieldPath));
        view.put("path", fieldPath);
        view.put("timestamp", java.time.Instant.now(clock).toString());

        // 详情子映射
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("containerType", elementEvent.getContainerType().name());
        details.put("operation", elementEvent.getOperation().name());
        if (elementEvent.getIndex() != null) {
            details.put("index", elementEvent.getIndex());
        }
        if (elementEvent.getOldIndex() != null) {
            details.put("oldIndex", elementEvent.getOldIndex());
        }
        if (elementEvent.getNewIndex() != null) {
            details.put("newIndex", elementEvent.getNewIndex());
        }
        if (elementEvent.getEntityKey() != null) {
            details.put("entityKey", elementEvent.getEntityKey());
        }
        if (elementEvent.getMapKey() != null) {
            details.put("mapKey", elementEvent.getMapKey());
        }
        if (elementEvent.getPropertyPath() != null && !elementEvent.getPropertyPath().trim().isEmpty()) {
            details.put("propertyPath", elementEvent.getPropertyPath());
        }
        details.put("oldValue", oldValue);
        details.put("newValue", newValue);
        view.put("details", details);

        return view;
    }

    /**
     * 将 ElementOperation 映射为标准化 kind 字符串
     */
    private String toViewKind(ElementOperation operation) {
        switch (operation) {
            case ADD: return "entry_added";
            case REMOVE: return "entry_removed";
            case MODIFY: return "entry_updated";
            case MOVE: return "entry_moved";
            default: throw new IllegalStateException("Unknown operation: " + operation);
        }
    }


    /**
     * 获取值描述
     */
    public String getValueDescription() {
        if (changeType == ChangeType.DELETE) {
            return String.valueOf(oldValue) + " -> (deleted)";
        } else if (changeType == ChangeType.CREATE) {
            return "(new) -> " + String.valueOf(newValue);
        } else {
            return String.valueOf(oldValue) + " -> " + String.valueOf(newValue);
        }
    }
    
    /**
     * 是否为null变更
     */
    public boolean isNullChange() {
        return (oldValue == null && newValue == null) ||
               (oldValue == null && changeType == ChangeType.CREATE) ||
               (newValue == null && changeType == ChangeType.DELETE);
    }
    
    /**
     * 集合变更详情
     */
    @Data
    @Builder
    public static class CollectionChangeDetail {
        private int addedCount;
        private int removedCount;
        private int modifiedCount;
        private int originalSize;
        private int newSize;
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

    // ========== P1-T2: 引用变更语义标记 ==========

    /**
     * 是否为引用变更（@ShallowReference 语义）
     * 当引用标注为 @ShallowReference 且引用键发生变化时为 true
     * @since v3.1.0-P1
     */
    private boolean referenceChange;

    /**
     * 引用变更详情（当 referenceChange=true 时非 null）
     */
    private ReferenceDetail referenceDetail;

    /** 是否为引用变更 */
    public boolean isReferenceChange() {
        return referenceChange;
    }

    /** 获取引用变更详情 */
    public ReferenceDetail getReferenceDetail() {
        return referenceDetail;
    }

    /** 旧引用的实体键（便捷） */
    public String getOldEntityKey() {
        return referenceDetail != null ? referenceDetail.getOldEntityKey() : null;
    }

    /** 新引用的实体键（便捷） */
    public String getNewEntityKey() {
        return referenceDetail != null ? referenceDetail.getNewEntityKey() : null;
    }

    /**
     * 导出引用变更为Typed视图（Map格式）
     *
     * @return Map视图，null如果非引用变更
     * @since v3.1.0-P1
     */
    public java.util.Map<String, Object> toReferenceChangeView() {
        if (!isReferenceChange() || referenceDetail == null) {
            return null;  // 非引用变更不生成视图
        }

        java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("kind", "reference_change");
        view.put("object", FieldChange.extractObjectName(fieldPath != null ? fieldPath : fieldName));
        view.put("path", fieldPath != null ? fieldPath : fieldName);
        view.put("timestamp", java.time.Instant.now(clock).toString());

        // 引用变更详情
        java.util.Map<String, Object> details = referenceDetail.toMap();
        view.put("details", details);

        // 可选：添加原始值的字符串表示（调试用）
        if (oldValue != null) {
            view.put("oldValueRepr", oldValue.toString());
        }
        if (newValue != null) {
            view.put("newValueRepr", newValue.toString());
        }

        return view;
    }

    // 辅助方法：提取对象名（路径首段）
    private static String extractObjectName(String path) {
        if (path == null) {
            return "unknown";
        }
        // 同时处理点号和括号两种情况
        // 例如："items[0].name" -> "items", "items.name" -> "items", "items[0]" -> "items"
        int dotIndex = path.indexOf('.');
        int bracketIndex = path.indexOf('[');

        // 找到第一个分隔符（点号或括号）
        int separatorIndex = -1;
        if (dotIndex > 0 && bracketIndex > 0) {
            separatorIndex = Math.min(dotIndex, bracketIndex);
        } else if (dotIndex > 0) {
            separatorIndex = dotIndex;
        } else if (bracketIndex > 0) {
            separatorIndex = bracketIndex;
        }

        return separatorIndex > 0 ? path.substring(0, separatorIndex) : path;
    }

    /**
     * 引用变更详情（v3.1.0-P1）
     * 记录引用身份变化，而非对象内部属性变化
     * @since v3.1.0-P1
     */
    @Data
    @Builder
    public static class ReferenceDetail {
        private String oldEntityKey;
        private String newEntityKey;
        private boolean nullReferenceChange;

        /**
         * 导出为Map视图（用于序列化或API对接）
         *
         * @return Map视图，包含标准键：oldKey, newKey, isNullTransition, transitionType
         * @since v3.1.0-P1
         */
        public java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
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
         * @throws RuntimeException 序列化失败时
         * @since v3.1.0-P1
         */
        public String toJson() {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.writeValueAsString(toMap());
            } catch (Exception e) {
                throw new RuntimeException("ReferenceDetail JSON serialization failed", e);
            }
        }
    }
}
