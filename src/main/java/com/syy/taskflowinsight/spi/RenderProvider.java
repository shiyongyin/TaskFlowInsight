package com.syy.taskflowinsight.spi;

/**
 * 结果渲染服务提供接口（SPI）
 *
 * <p>提供结果对象的渲染能力，支持多种渲染风格。
 *
 * <p>优先级规则：
 * <ul>
 *   <li>Spring Bean（最高优先级）</li>
 *   <li>手动注册（TFI.registerRenderProvider）</li>
 *   <li>ServiceLoader发现（META-INF/services）</li>
 *   <li>兜底实现（返回空字符串）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public interface RenderProvider {

    /**
     * 渲染结果对象
     *
     * @param result 要渲染的结果对象
     * @param style 渲染风格（可以是String类型的"simple"/"standard"/"detailed"，
     *              或具体的RenderStyle对象）
     * @return 渲染后的字符串表示，如果失败返回降级文本
     */
    String render(Object result, Object style);

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
