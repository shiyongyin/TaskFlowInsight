package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

/**
 * 宿主选定的嵌入式比较执行 Port。
 *
 * <p>{@code CompareEngine} 是默认实现，并由 {@code CompareRuntime} 作为 policy、扩展与执行图的唯一构建 owner。
 * 纯 Java bridge 或宿主框架依赖本 Port，不依赖 engine internal，也不得借此引入 Registry 查找或第二套执行图。</p>
 *
 * <p>{@code CompareOperationsDecorator} 只保留给旧 Ops 的单层装饰合同；新的组合 Starter 直接注入宿主选定的
 * 本 Port，不启用该 decorator。</p>
 *
 * @since 4.0.0
 */
public interface CompareOperations {

    /**
     * 使用当前运行时默认选项比较两个对象。
     *
     * @param before 变更前对象，可为 {@code null}
     * @param after 变更后对象，可为 {@code null}
     * @return canonical 比较结果
     */
    CompareResult compare(Object before, Object after);

    /**
     * 使用显式且已受当前 policy 上界约束的选项比较两个对象。
     *
     * @param before 变更前对象，可为 {@code null}
     * @param after 变更后对象，可为 {@code null}
     * @param options 单次调用选项，不可为 {@code null}
     * @return canonical 比较结果
     */
    CompareResult compare(Object before, Object after, CompareOptions options);
}
