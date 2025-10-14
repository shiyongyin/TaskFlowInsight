package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.detector.DiffDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认ComparisonProvider实现
 *
 * <p>兜底实现，委托给DiffDetector进行实际比较。
 * 当没有其他Provider可用时使用此实现，保证系统永不crash。
 *
 * <p>特性:
 * <ul>
 *   <li>优先级为0 (最低)</li>
 *   <li>委托给DiffDetector静态方法 (无Spring依赖)</li>
 *   <li>异常安全 (捕获所有异常并返回empty结果)</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultComparisonProvider implements ComparisonProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultComparisonProvider.class);

    /**
     * 比较两个对象
     *
     * @param before 变更前对象
     * @param after 变更后对象
     * @return 比较结果，异常时返回empty结果
     */
    @Override
    public CompareResult compare(Object before, Object after) {
        try {
            // 创建CompareService实例（纯Java环境下的兜底实现）
            // 注意：这是最简单的实现，不使用Spring依赖
            com.syy.taskflowinsight.tracking.compare.CompareService compareService =
                new com.syy.taskflowinsight.tracking.compare.CompareService();

            return compareService.compare(before, after, com.syy.taskflowinsight.tracking.compare.CompareOptions.DEFAULT);

        } catch (Exception e) {
            // 异常降级：记录日志但不抛出异常，保证系统稳定
            logger.error("DefaultComparisonProvider.compare failed for types: {} vs {}",
                before != null ? before.getClass().getName() : "null",
                after != null ? after.getClass().getName() : "null",
                e);

            // 返回空结果而非null，避免NPE
            return CompareResult.builder()
                .object1(before)
                .object2(after)
                .changes(java.util.Collections.emptyList())
                .identical(before == after || (before != null && before.equals(after)))
                .build();
        }
    }

    @Override
    public CompareResult compare(Object before, Object after, CompareOptions options) {
        try {
            com.syy.taskflowinsight.tracking.compare.CompareService compareService =
                new com.syy.taskflowinsight.tracking.compare.CompareService();
            CompareOptions opts = (options != null) ? options : com.syy.taskflowinsight.tracking.compare.CompareOptions.DEFAULT;
            return compareService.compare(before, after, opts);
        } catch (Exception e) {
            logger.error("DefaultComparisonProvider.compare(options) failed for types: {} vs {}",
                before != null ? before.getClass().getName() : "null",
                after != null ? after.getClass().getName() : "null",
                e);

            return CompareResult.builder()
                .object1(before)
                .object2(after)
                .changes(java.util.Collections.emptyList())
                .identical(before == after || (before != null && before.equals(after)))
                .build();
        }
    }

    /**
     * 计算相似度
     *
     * @param obj1 对象1
     * @param obj2 对象2
     * @return 相似度 [0.0, 1.0]
     */
    @Override
    public double similarity(Object obj1, Object obj2) {
        try {
            // 简单实现：基于是否有差异
            CompareResult result = compare(obj1, obj2);
            if (result.isIdentical()) {
                return 1.0;
            }
            // 粗略计算：差异越多相似度越低
            int changeCount = result.getChanges().size();
            if (changeCount == 0) {
                return 1.0;
            }
            // 简化公式：1 / (1 + changeCount)
            return 1.0 / (1.0 + changeCount);

        } catch (Exception e) {
            logger.warn("similarity calculation failed", e);
            return 0.0;
        }
    }

    /**
     * 优先级：0 (最低，兜底实现)
     *
     * @return 0
     */
    @Override
    public int priority() {
        return 0;
    }

    @Override
    public String toString() {
        return "DefaultComparisonProvider{priority=0, type=fallback}";
    }
}
