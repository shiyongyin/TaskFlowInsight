package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.model.ChangeRecord;

import java.util.List;

/**
 * 变更追踪服务提供接口（SPI）
 *
 * <p>提供对象变更追踪能力，包括追踪、查询和清理变更记录。
 *
 * <p>优先级规则：
 * <ul>
 *   <li>Spring Bean（最���优先级）</li>
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
     * 追踪对象的指定字段
     *
     * @param name 追踪目标名称
     * @param target 目标对象
     * @param fields 要追踪的字段名称（可选，为空则追踪所有字段）
     */
    void track(String name, Object target, String... fields);

    /**
     * 获取所有变更记录
     *
     * @return 变更记录列表（never null）
     */
    List<ChangeRecord> changes();

    /**
     * 清空所有追踪记录
     */
    void clear();

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
