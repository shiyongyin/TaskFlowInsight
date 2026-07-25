package com.syy.taskflowinsight.spi;

import java.util.Map;

/**
 * 导出服务提供接口（SPI）
 *
 * <p>提供会话数据导出能力（控制台、JSON、Map等格式）
 *
 * <p>优先级规则：
 * <ul>
 *   <li>Spring Bean（最高优先级）</li>
 *   <li>手动注册（TFI.registerExportProvider）</li>
 *   <li>ServiceLoader发现（META-INF/services）</li>
 *   <li>兜底实现（返回空字符串/空Map）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public interface ExportProvider extends PrioritizedProvider {

    /**
     * 导出当前会话到控制台
     *
     * <p>默认格式：文本树形结构，不显示时间戳
     *
     * @since 4.0.0
     */
    default void exportToConsole() {
        exportToConsole(false);
    }

    /**
     * 导出当前会话为 TREE 控制台诊断文本（可选消息时间戳）
     *
     * <p>该参数只控制消息时间戳，不选择 TREE/SIMPLE 样式。
     *
     * @param showTimestamp 是否显示消息时间戳
     * @return 是否导出成功（无会话返回 false）
     * @since 4.0.0
     */
    boolean exportToConsole(boolean showTimestamp);

    /**
     * 导出当前会话为 JSON 字符串
     *
     * <p>JSON 格式示例：
     * <pre>{@code
     * {
     *   "sessionId": "abc123",
     *   "sessionName": "Order Processing",
     *   "startTime": "2025-10-14T10:00:00",
     *   "tasks": [
     *     {
     *       "taskId": "task1",
     *       "taskName": "Validate Order",
     *       "messages": ["Validation started", "Validation passed"]
     *     }
     *   ],
     *   "changes": []
     * }
     * }</pre>
     *
     * @return JSON 字符串，如果无会话返回 "{}"
     * @since 4.0.0
     */
    String exportToJson();

    /**
     * 导出当前会话为 Map 结构
     *
     * <p>Map 结构：
     * <pre>{@code
     * {
     *   "sessionId": "...",
     *   "sessionName": "...",
     *   "startTime": Instant,
     *   "tasks": [
     *     {
     *       "taskId": "...",
     *       "taskName": "...",
     *       "messages": List<String>
     *     }
     *   ],
     *   "changes": List<Map>
     * }
     * }</pre>
     *
     * @return Map 结构，如果无会话返回空 Map
     * @since 4.0.0
     */
    Map<String, Object> exportToMap();

}
