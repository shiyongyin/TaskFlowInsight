package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

/**
 * 宿主已经选定的比较执行边界。
 *
 * <p>该接口只为 Spring Ops 装饰同一个 {@code CompareEngine} 提供稳定接缝，不承载运行时构建、
 * Registry 查找或通用中间件能力。纯 Java 调用仍可直接使用 Engine，避免形成第二套执行图。</p>
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
