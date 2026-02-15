package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认渲染Provider实现（兜底）
 *
 * <p>使用{@link MarkdownRenderer}进行结果渲染，priority=0（最低优先级）。
 * <p>支持style参数为String类型（"simple"/"standard"/"detailed"）或RenderStyle对象。
 * <p>异常安全：渲染失败时返回降级文本。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultRenderProvider implements RenderProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRenderProvider.class);

    private final MarkdownRenderer renderer;

    public DefaultRenderProvider() {
        this.renderer = new MarkdownRenderer();
    }

    @Override
    public String render(Object result, Object style) {
        try {
            if (result == null) {
                return "[null]";
            }

            // 解析style参数
            RenderStyle renderStyle = parseStyle(style);

            // 检查result类型（MarkdownRenderer只支持EntityListDiffResult）
            if (result instanceof com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult) {
                return renderer.render(
                    (com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult) result,
                    renderStyle);
            } else {
                // 其他类型降级处理
                return String.format("Result type %s (rendering not supported for this type)",
                    result.getClass().getSimpleName());
            }

        } catch (Exception e) {
            // 此处 result 必定非 null（null 已在方法开头 return）
            logger.warn("DefaultRenderProvider.render failed for result type={}: {}",
                result.getClass().getName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Render error details", e);
            }

            // 降级：返回简要文本
            return String.format("[Render failed: %s]", result.getClass().getSimpleName());
        }
    }

    /**
     * 解析style参数为RenderStyle对象
     */
    private RenderStyle parseStyle(Object style) {
        if (style == null) {
            return RenderStyle.standard(); // 默认标准风格
        }

        if (style instanceof RenderStyle) {
            return (RenderStyle) style;
        }

        if (style instanceof String) {
            String styleStr = (String) style;
            switch (styleStr.toLowerCase()) {
                case "simple":
                    return RenderStyle.simple();
                case "detailed":
                    return RenderStyle.detailed();
                case "standard":
                default:
                    return RenderStyle.standard();
            }
        }

        // 无法识别的类型，使用默认
        logger.debug("Unknown style type: {}, using standard", style.getClass().getName());
        return RenderStyle.standard();
    }

    @Override
    public int priority() {
        return 0; // 最低优先级（兜底实现）
    }

    @Override
    public String toString() {
        return "DefaultRenderProvider{priority=0, type=fallback}";
    }
}
