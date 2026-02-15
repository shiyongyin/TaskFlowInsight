package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.List;
import java.util.Map;

/**
 * 变更追踪服务提供接口（SPI）
 *
 * <p>提供对象变更追踪能力，包括追踪、查询和清理变更记录。
 *
 * <p>优先级规则：
 * <ul>
 *   <li>Spring Bean（最高优先级）</li>
 *   <li>手动注册（TFI.registerTrackingProvider）</li>
 *   <li>ServiceLoader发现（META-INF/services）</li>
 *   <li>兜底实现（返回空列表）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public interface TrackingProvider {

    /**
     * 追踪对象的指定字段（浅层追踪）
     *
     * @param name 追踪目标名称
     * @param target 目标对象
     * @param fields 要追踪的字段名称（可选，为空则追踪所有字段）
     */
    void track(String name, Object target, String... fields);

    /**
     * 获取当前会话的所有变更记录
     *
     * @return 变更记录列表（never null）
     */
    List<ChangeRecord> changes();

    /**
     * 清空所有追踪记录
     */
    void clear();

    // ========== v4.0.0 新增方法 ==========

    /**
     * 批量追踪多个对象
     *
     * <p>用途：一次性追踪多个相关对象，提高效率
     *
     * @param targets 对象映射（key=对象名称, value=对象实例）
     * @throws NullPointerException 如果 targets 为 null
     * @since 4.0.0
     */
    default void trackAll(Map<String, Object> targets) {
        if (targets == null) {
            throw new NullPointerException("targets cannot be null");
        }
        targets.forEach((name, obj) -> track(name, obj));
    }

    /**
     * 深度追踪对象（包括嵌套对象）
     *
     * <p>与 {@link #track} 的区别：
     * <ul>
     *   <li>track: 仅追踪标量字段（String, int, Date等）</li>
     *   <li>trackDeep: 递归追踪嵌套对象字段</li>
     * </ul>
     *
     * @param name 对象名称
     * @param obj 要追踪的对象
     * @since 4.0.0
     */
    default void trackDeep(String name, Object obj) {
        trackDeep(name, obj, null);
    }

    /**
     * 深度追踪对象（带选项）
     *
     * @param name 对象名称
     * @param obj 要追踪的对象
     * @param options 追踪选项（maxDepth, ignoreFields等），null 使用默认
     * @since 4.0.0
     */
    default void trackDeep(String name, Object obj, TrackingOptions options) {
        // 默认实现：回退到浅层追踪
        track(name, obj);
    }

    /**
     * 获取所有追踪会话的变更记录
     *
     * <p>与 {@link #changes()} 的区别：
     * <ul>
     *   <li>changes: 仅返回当前会话的变更</li>
     *   <li>getAllChanges: 返回所有会话的变更</li>
     * </ul>
     *
     * @return 所有变更记录列表，如果无变更返回空列表
     * @since 4.0.0
     */
    default List<ChangeRecord> getAllChanges() {
        // 默认实现：回退到当前会话变更
        return changes();
    }

    /**
     * 开始一个新的追踪会话
     *
     * <p>用途：隔离不同阶段的变更追踪
     * <p>例如：分别追踪"数据加载"、"业务处理"、"数据保存"阶段的变更
     *
     * @param sessionName 会话名称（用于区分）
     * @since 4.0.0
     */
    default void startTracking(String sessionName) {
        // 默认实现：无操作（不支持会话隔离）
    }

    /**
     * 手动记录一个变更
     *
     * <p>用途：记录非对象字段的变更（如数据库状态、外部系统调用）
     *
     * <p>示例：
     * <pre>{@code
     * provider.recordChange("database", "connection_count", 10, 20, ChangeType.UPDATE);
     * }</pre>
     *
     * @param objectName 对象名称
     * @param fieldName 字段名称
     * @param oldValue 旧值
     * @param newValue 新值
     * @param changeType 变更类型（CREATE, UPDATE, DELETE）
     * @since 4.0.0
     */
    default void recordChange(String objectName, String fieldName,
                              Object oldValue, Object newValue,
                              ChangeType changeType) {
        // 默认实现：无操作（不支持手动记录）
    }

    /**
     * 清除指定会话的追踪数据
     *
     * @param sessionName 会话名称
     * @since 4.0.0
     */
    default void clearTracking(String sessionName) {
        // 默认实现：回退到清除所有追踪
        clear();
    }

    /**
     * 在作用域内追踪对象
     *
     * <p>典型用法：
     * <pre>{@code
     * User user = getUser();
     * provider.withTracked("user", user, () -> {
     *     user.setName("new name");
     *     // 其他修改...
     * }, "name", "age");
     * // 作用域结束后，自动捕获变更
     * }</pre>
     *
     * @param name 对象名称
     * @param obj 要追踪的对象
     * @param action 要执行的操作
     * @param fields 要追踪的字段
     * @since 4.0.0
     */
    default void withTracked(String name, Object obj, Runnable action, String... fields) {
        track(name, obj, fields);  // 追踪初始状态
        try {
            if (action != null) {
                action.run();
            }
        } finally {
            // 注意：默认实现可能无法正确捕获变更
            // 子类应提供完整的快照前后比对实现
        }
    }

    /**
     * Provider优先级（数值越大优先级越高）
     * <p>Spring实现通常返回Integer.MAX_VALUE，ServiceLoader实现返回0</p>
     *
     * @return 优先级值，默认0
     */
    default int priority() {
        return 0;
    }
}
