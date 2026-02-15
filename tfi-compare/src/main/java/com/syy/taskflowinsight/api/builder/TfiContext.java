package com.syy.taskflowinsight.api.builder;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;

/**
 * 不可变上下文：持有 CompareService，并提供 compare 重载。
 * <p>
 * 线程安全声明：本类自身为不可变设计，内部仅保存对 CompareService 的只读引用，
 * 不暴露任何可变状态的 Setter。CompareService 在默认配置下为无状态或线程安全使用，
 * 可在多线程环境中复用同一个 {@code TfiContext} 实例进行 compare 调用。
 * </p>
 * <p>
 * 用法示例：
 * <pre>{@code
 * TfiContext ctx = DiffBuilder.create()
 *     .withMaxDepth(5)
 *     .withDeepCompare(true)
 *     .build();
 * CompareResult r = ctx.compare(a, b);
 * }</pre>
 * </p>
 */
public final class TfiContext {
    private final CompareService compareService;
    private final CompareOptions defaultOptions;

    TfiContext(CompareService svc, CompareOptions defaults) {
        this.compareService = svc;
        this.defaultOptions = (defaults != null ? defaults : CompareOptions.DEFAULT);
    }

    /** 获取内部 CompareService（只读）。 */
    public CompareService compareService() { return compareService; }

    /** 使用默认选项进行比较（取自 DiffBuilder 构建的生效配置）。 */
    public CompareResult compare(Object a, Object b) { return compareService.compare(a, b, defaultOptions); }

    /** 使用自定义选项进行比较。 */
    public CompareResult compare(Object a, Object b, CompareOptions options) { return compareService.compare(a, b, options); }
}
