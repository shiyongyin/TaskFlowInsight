package com.syy.taskflowinsight.tracking.query;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通用适配器：FieldChange → 结构化事件视图
 * <p>
 * 提供后置视图转换，不修改核心引擎/模型。
 * 输出格式：List&lt;Map&lt;String,Object&gt;&gt; 或 JSON 字符串。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.1.0-P1
 * @since 2025-10-08
 */
public final class ChangeAdapters {

    private static final List<Customizer> CUSTOMIZERS = new CopyOnWriteArrayList<>();

    private ChangeAdapters() {
    }

    /**
     * 将 FieldChange 列表转换为结构化事件视图（P1-T2 增强：支持引用变更检测）
     *
     * @param objectName 对象名称（上下文）
     * @param changes    FieldChange 列表
     * @return 结构化事件列表，每个事件为 Map&lt;String,Object&gt;
     */
    public static List<Map<String, Object>> toTypedView(String objectName, List<FieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> events = new ArrayList<>(changes.size());
        Instant timestamp = Instant.now();

        for (FieldChange change : changes) {
            Map<String, Object> event = new HashMap<>();
            event.put("object", objectName != null ? objectName : "Unknown");
            event.put("path", change.getFieldPath() != null ? change.getFieldPath() : change.getFieldName());
            event.put("timestamp", timestamp);

            if (change.isReferenceChange() && change.getReferenceDetail() != null) {
                // 映射为 reference_change 事件
                event.put("kind", "reference_change");
                event.put("details", change.getReferenceDetail().toMap());
            } else {
                // 原有逻辑：映射 ChangeType → kind（保持向后兼容）
                switch (change.getChangeType()) {
                    case CREATE -> event.put("kind", "entry_added");
                    case DELETE -> event.put("kind", "entry_removed");
                    case UPDATE -> event.put("kind", "entry_updated");  // 保持原有映射
                    case MOVE -> event.put("kind", "entry_moved");
                }

                // 详情
                Map<String, Object> details = new HashMap<>();
                if (change.getOldValue() != null) {
                    details.put("oldEntryValue", change.getOldValue());
                }
                if (change.getNewValue() != null) {
                    details.put("newEntryValue", change.getNewValue());
                }
                if (change.getValueType() != null) {
                    details.put("valueType", change.getValueType());
                }
                event.put("details", details);
            }

            events.add(event);
        }

        return events;
    }

    /**
     * 将结构化事件视图转换为 JSON 字符串（手写简单格式）
     *
     * @param objectName 对象名称
     * @param changes    FieldChange 列表
     * @return JSON 字符串
     */
    public static String toTypedJson(String objectName, List<FieldChange> changes) {
        List<Map<String, Object>> events = toTypedView(objectName, changes);
        if (events.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(toJsonObject(events.get(i)));
        }
        json.append("]");
        return json.toString();
    }

    /**
     * 自定义扩展点
     */
    public interface Customizer {
        /**
         * 自定义事件视图
         *
         * @param events 事件列表
         * @param source 原始 CompareResult
         */
        void customize(List<Map<String, Object>> events, CompareResult source);
    }

    /**
     * 注册自定义器（线程安全）
     *
     * @param customizer 自定义器
     */
    public static void registerCustomizer(Customizer customizer) {
        if (customizer != null) {
            CUSTOMIZERS.add(customizer);
        }
    }

    /**
     * 应用所有自定义器
     *
     * @param events 事件列表
     * @param source 原始 CompareResult
     */
    static void applyCustomizers(List<Map<String, Object>> events, CompareResult source) {
        for (Customizer customizer : CUSTOMIZERS) {
            try {
                customizer.customize(events, source);
            } catch (Exception e) {
                // 静默失败，不影响主流程
            }
        }
    }

    // ========== P1-T2: 引用变更检测辅助方法 ==========

    /**
     * 检测 FieldChange 是否为引用变更
     * <p>
     * 判断依据：
     * 1. oldValue 和 newValue 都是 Map 类型（快照格式）
     * 2. Map 中的值看起来像引用标识（如 "Customer[C1]" 或 "Customer@1a2b3c"）
     * </p>
     *
     * @param change FieldChange 对象
     * @return true 如果检测为引用变更
     */
    // 无启发式：严格依赖 FieldChange.isReferenceChange() / ReferenceDetail

    // ========== 内部辅助方法 ==========

    /**
     * 将 Map 转换为 JSON 对象字符串（简单实现）
     */
    private static String toJsonObject(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(toJsonValue(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 将值转换为 JSON 格式
     */
    private static String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapValue = (Map<String, Object>) value;
            return toJsonObject(mapValue);
        }
        if (value instanceof Instant) {
            return "\"" + value.toString() + "\"";
        }
        // 默认转字符串
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    /**
     * JSON 字符串转义
     */
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
