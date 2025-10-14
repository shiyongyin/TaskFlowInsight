package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;

/**
 * SPI接口: 对象比较能力提供者
 *
 * <p>设计原则:
 * <ul>
 *   <li>通过ServiceLoader发现实现类</li>
 *   <li>支持优先级仲裁 (数值越大优先级越高)</li>
 *   <li>Spring环境和纯Java环境均可使用</li>
 * </ul>
 *
 * <p>Provider发现顺序:
 * <pre>
 * 1. Spring Bean (如果存在) - 优先级通常为200
 * 2. 手动注册 (TFI.registerComparisonProvider)
 * 3. ServiceLoader自动发现 (META-INF/services)
 * 4. 兜底实现 (DefaultComparisonProvider)
 * </pre>
 *
 * <p>使用示例:
 * <pre>{@code
 * // 实现自定义Provider
 * public class MyComparisonProvider implements ComparisonProvider {
 *     @Override
 *     public CompareResult compare(Object before, Object after) {
 *         // 自定义比较逻辑
 *         return new CompareResult(...);
 *     }
 *
 *     @Override
 *     public int priority() {
 *         return 100; // 高于默认实现(0), 低于Spring Bean(200)
 *     }
 * }
 *
 * // 配置ServiceLoader (META-INF/services/com.syy.taskflowinsight.spi.ComparisonProvider):
 * com.example.MyComparisonProvider
 * }</pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 * @see java.util.ServiceLoader
 */
public interface ComparisonProvider {

    /**
     * 比较两个对象的差异
     *
     * @param before 变更前对象 (可以为null)
     * @param after 变更后对象 (可以为null)
     * @return 比较结果，包含差异详情; 如果无法比较返回empty结果 (never null)
     */
    CompareResult compare(Object before, Object after);

    /**
     * 比较两个对象（带选项）
     * <p>
     * 默认实现会回退到无参 compare(before, after)。
     * Provider 可覆盖以支持更丰富的 CompareOptions 路由。
     * </p>
     *
     * @param before 变更前对象
     * @param after 变更后对象
     * @param options 比较选项（不可变对象）
     * @return 比较结果
     */
    default CompareResult compare(Object before, Object after, CompareOptions options) {
        return compare(before, after);
    }

    /**
     * 计算两个对象的相似度 (可选实现)
     *
     * @param obj1 对象1
     * @param obj2 对象2
     * @return 相似度值 [0.0, 1.0], 1.0表示完全相同
     */
    default double similarity(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) {
            return 1.0;
        }
        if (obj1 == null || obj2 == null) {
            return 0.0;
        }
        return obj1.equals(obj2) ? 1.0 : 0.0;
    }

    /**
     * 三方合并与冲突检测 (可选实现)
     *
     * @param base 基准版本
     * @param left 左分支版本
     * @param right 右分支版本
     * @return 合并结果
     * @throws UnsupportedOperationException 如果不支持三方合并
     */
    default Object threeWayMerge(Object base, Object left, Object right) {
        throw new UnsupportedOperationException("threeWayMerge not implemented by " + getClass().getName());
    }

    /**
     * Provider优先级 (数值越大优先级越高)
     *
     * <p>推荐优先级范围:
     * <ul>
     *   <li>0: 默认实现</li>
     *   <li>1-99: 用户自定义实现</li>
     *   <li>100-199: 框架扩展实现</li>
     *   <li>200+: Spring Bean实现</li>
     * </ul>
     *
     * @return 优先级值，默认为0
     */
    default int priority() {
        return 0;
    }
}
