package com.syy.taskflowinsight.api.builder;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * standalone环境的最小比较上下文构建器。
 *
 * <p>该构建器只消费调用方显式参数，不读取Spring Environment、系统属性或全局指标。
 * Spring场景由compare starter的typed binder直接构造{@code ComparePolicy -> CompareRuntime}，
 * 避免同一配置再次经过legacy键转换。</p>
 *
 * @since 3.0.0
 */
public final class DiffBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DiffBuilder.class);

    /** 调用方选择的最大逻辑深度；null表示继承policy。 */
    private Integer maxDepth;
    /** 是否启用深比较；只由当前builder调用显式设置。 */
    private Boolean deep;

    private DiffBuilder() {}

    /**
     * 创建不读取外部配置源的Builder。
     *
     * @return 新的线程封闭构建器
     */
    public static DiffBuilder create() { return new DiffBuilder(); }

    /**
     * 显式设置最大深度并启用深比较。
     *
     * @param depth 正整数深度；非正数保持当前选择
     * @return 当前构建器
     */
    public DiffBuilder withMaxDepth(int depth) {
        if (depth > 0) {
            this.maxDepth = depth;
            this.deep = true;
        }
        return this;
    }

    /**
     * 显式选择是否启用深比较。
     *
     * @param enable {@code true}启用，{@code false}关闭
     * @return 当前构建器
     */
    public DiffBuilder withDeepCompare(boolean enable) {
        this.deep = enable;
        return this;
    }

    /**
     * 根据当前显式选择构建不可变上下文。
     *
     * @return 使用唯一默认runtime工厂创建的上下文
     */
    public TfiContext build() {
        CompareOptions.CompareOptionsBuilder ob = CompareOptions.builder();
        boolean finalDeep = deep != null && deep;
        int finalMaxDepth = (maxDepth != null ? maxDepth : CompareOptions.builder().build().maxDepth());
        ob.maxDepth(finalDeep ? finalMaxDepth : 0);
        CompareOptions effective = ob.build();

        if (logger.isInfoEnabled()) {
            logger.info("DiffBuilder.effective: deep={}, maxDepth={}", finalDeep, finalMaxDepth);
        }

        CompareService compareService = CompareService.createDefault(effective);
        return new TfiContext(compareService, effective);
    }
}
