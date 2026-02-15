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
 * <p>使用示例：
 * <pre>{@code
 * // 渲染比较结果为Markdown格式
 * CompareResult result = TFI.compare(oldOrder, newOrder);
 * String markdown = TFI.render(result, "standard");
 *
 * // 自定义渲染风格
 * String detailed = TFI.render(result, RenderStyle.DETAILED);
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public interface RenderProvider {

    /**
     * 渲染结果对象
     *
     * <p>支持的渲染风格:
     * <ul>
     *   <li>"simple" - 简洁模式，仅显示关键变更</li>
     *   <li>"standard" - 标准模式，包含字段路径和新旧值</li>
     *   <li>"detailed" - 详细模式，包含完整元数据和上下文</li>
     * </ul>
     *
     * @param result 要渲染的结果对象（通常是CompareResult或Session）
     * @param style 渲染风格（可以是String类型的"simple"/"standard"/"detailed"，
     *              或具体的RenderStyle对象）
     * @return 渲染后的字符串表示（Markdown/HTML/纯文本等），如果失败返回降级文本
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
