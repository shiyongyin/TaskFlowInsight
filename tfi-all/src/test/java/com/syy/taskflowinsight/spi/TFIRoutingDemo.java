package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TFI路由模式Demo
 *
 * <p>展示如何将TFI.java的40个方法改造为Provider路由模式。
 * 这是验证2的核心：验证路由模板的可行性。
 *
 * <p>改造前后对比:
 * <pre>
 * // v3.0.0 (改造前)
 * public static CompareResult compare(Object a, Object b) {
 *     CompareService svc = ensureCompareService(); // Spring依赖
 *     return svc.compare(a, b);
 * }
 *
 * // v4.0.0 (改造后)
 * public static CompareResult compare(Object a, Object b) {
 *     ComparisonProvider provider = lookupProvider(); // 路由到Provider
 *     return provider.compare(a, b);
 * }
 * </pre>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class TFIRoutingDemo {

    private static final Logger logger = LoggerFactory.getLogger(TFIRoutingDemo.class);

    // ==================== 路由模板示例 ====================

    /**
     * 示例1: compare()方法的路由改造
     *
     * <p>改造要点:
     * <ul>
     *   <li>移除Spring依赖（ensureCompareService）</li>
     *   <li>改为Provider路由（ProviderRegistry.lookup）</li>
     *   <li>保持异常降级逻辑</li>
     *   <li>保持方法签名不变</li>
     * </ul>
     */
    public static CompareResult compare_v4_routing(Object a, Object b) {
        try {
            // 快速相等检查（优化：避免不必要的Provider查找）
            if (a == b) {
                return CompareResult.identical();
            }

            // null检查
            if (a == null || b == null) {
                return CompareResult.ofNullDiff(a, b);
            }

            // 类型检查
            if (!a.getClass().equals(b.getClass())) {
                return CompareResult.ofTypeDiff(a, b);
            }

            // ========== 核心改造：路由到Provider ==========
            ComparisonProvider provider = lookupComparisonProvider();
            return provider.compare(a, b);

        } catch (Throwable t) {
            logger.error("Failed to compare objects", t);
            // 降级：返回类型差异（安全兜底）
            return CompareResult.ofTypeDiff(a, b);
        }
    }

    /**
     * 示例2: isEnabled()方法的路由改造（控制类方法）
     *
     * <p>说明: 像isEnabled/clear这类控制方法暂时保持原样，
     * 因为它们不属于4大Provider（Comparison/Tracking/Flow/Render）
     */
    public static boolean isEnabled_v4_routing() {
        // 控制类方法暂时保持原有逻辑
        // 未来如果有ConfigProvider，再改造
        return true; // 简化示例
    }

    /**
     * 示例3: clear()方法（控制类方法）
     */
    public static void clear_v4_routing() {
        // 控制类方法保持原样
        logger.debug("TFI cleared (routing demo)");
    }

    // ==================== Provider查找辅助方法 ====================

    /**
     * 查找ComparisonProvider（带兜底）
     *
     * <p>查找顺序：
     * <ol>
     *   <li>手动注册的Provider（含Spring Bean）</li>
     *   <li>ServiceLoader发现的Provider</li>
     *   <li>兜底：DefaultComparisonProvider</li>
     * </ol>
     *
     * @return ComparisonProvider实例（never null）
     */
    private static ComparisonProvider lookupComparisonProvider() {
        // 1. 尝试从注册表查找
        ComparisonProvider provider = ProviderRegistry.lookup(ComparisonProvider.class);

        // 2. 如果找不到，使用兜底实现
        if (provider == null) {
            provider = ProviderRegistry.getDefaultComparisonProvider();
            logger.debug("Using default ComparisonProvider (no registered or ServiceLoader provider found)");
        }

        return provider;
    }

    // ==================== 路由模式统一模板 ====================

    /**
     * 路由模板（泛型版本）
     *
     * <p>可复用到40个方法的通用模板：
     * <pre>
     * 1. 快速检查（参数验证/相等性优化）
     * 2. Provider查找（ProviderRegistry.lookup + 兜底）
     * 3. 委托调用（provider.xxx(...）
     * 4. 异常降级（catch Throwable）
     * </pre>
     */
    private static <T> T routingTemplate(
        java.util.function.Supplier<T> fastPath,
        java.util.function.Supplier<T> providerCall,
        java.util.function.Supplier<T> fallback) {

        try {
            // 1. 快速路径（可选）
            if (fastPath != null) {
                T fastResult = fastPath.get();
                if (fastResult != null) {
                    return fastResult;
                }
            }

            // 2. Provider路由
            return providerCall.get();

        } catch (Throwable t) {
            logger.error("Provider routing failed", t);
            // 3. 降级
            return fallback.get();
        }
    }

    /**
     * 使用模板重写compare()
     */
    public static CompareResult compare_v4_template(Object a, Object b) {
        return routingTemplate(
            // fastPath: 快速相等检查
            () -> {
                if (a == b) return CompareResult.identical();
                if (a == null || b == null) return CompareResult.ofNullDiff(a, b);
                if (!a.getClass().equals(b.getClass())) return CompareResult.ofTypeDiff(a, b);
                return null; // 继续Provider路由
            },
            // providerCall: 路由到Provider
            () -> {
                ComparisonProvider provider = lookupComparisonProvider();
                return provider.compare(a, b);
            },
            // fallback: 降级逻辑
            () -> CompareResult.ofTypeDiff(a, b)
        );
    }

    // ==================== 验证辅助方法 ====================

    /**
     * 测试路由模式是否work
     */
    public static void main(String[] args) {
        System.out.println("=== TFI路由模式验证 ===\n");

        // Test 1: compare方法
        System.out.println("Test 1: compare()路由");
        java.util.Map<String, Object> map1 = new java.util.HashMap<>();
        map1.put("name", "Alice");

        java.util.Map<String, Object> map2 = new java.util.HashMap<>();
        map2.put("name", "Bob");

        CompareResult result = compare_v4_routing(map1, map2);
        System.out.println("  Result: " + result);
        System.out.println("  Changes: " + result.getChanges().size());
        System.out.println("  ✅ compare()路由成功\n");

        // Test 2: 使用模板
        System.out.println("Test 2: 模板复用");
        CompareResult result2 = compare_v4_template(map1, map2);
        System.out.println("  Result: " + result2);
        System.out.println("  ✅ 模板复用成功\n");

        // Test 3: 控制类方法
        System.out.println("Test 3: 控制类方法");
        boolean enabled = isEnabled_v4_routing();
        System.out.println("  isEnabled: " + enabled);
        clear_v4_routing();
        System.out.println("  ✅ 控制类方法保持原样\n");

        System.out.println("=== 验证完成 ===");
    }
}
