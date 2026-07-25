package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.spi.ComparisonProvider;
import com.syy.taskflowinsight.spi.ExportProvider;
import com.syy.taskflowinsight.spi.FlowProvider;
import com.syy.taskflowinsight.spi.ProviderRegistry;
import com.syy.taskflowinsight.spi.RenderProvider;
import com.syy.taskflowinsight.spi.TrackingProvider;

/**
 * TFI Provider 注册与查找委托（包级私有）。
 *
 * <p>处理 v4.0.0 SPI Provider 的注册、解析和 ClassLoader 加载。所有 Provider 的选择状态由
 * {@link ProviderRegistry} 唯一持有，facade 只负责路由。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
final class TfiProviderDelegate {

    private TfiProviderDelegate() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 注册方法 ====================

    static void registerComparisonProvider(ComparisonProvider provider) {
        ProviderRegistry.register(ComparisonProvider.class, provider);
        TFI.logger.info("Registered custom ComparisonProvider: {}", provider.getClass().getSimpleName());
    }

    static void registerTrackingProvider(TrackingProvider provider) {
        ProviderRegistry.register(TrackingProvider.class, provider);
        TFI.logger.info("Registered custom TrackingProvider: {}", provider.getClass().getSimpleName());
    }

    static void registerFlowProvider(FlowProvider provider) {
        ProviderRegistry.register(FlowProvider.class, provider);
        TFI.logger.info("Registered custom FlowProvider: {}", provider.getClass().getSimpleName());
    }

    static void registerRenderProvider(RenderProvider provider) {
        ProviderRegistry.register(RenderProvider.class, provider);
        TFI.logger.info("Registered custom RenderProvider: {}", provider.getClass().getSimpleName());
    }

    static void registerExportProvider(ExportProvider provider) {
        ProviderRegistry.register(ExportProvider.class, provider);
        TFI.logger.info("Registered custom ExportProvider: {}", provider.getClass().getSimpleName());
    }

    static void loadProviders(ClassLoader cl) {
        ProviderRegistry.loadProviders(
                cl,
                FlowProvider.class,
                ExportProvider.class,
                ComparisonProvider.class,
                TrackingProvider.class,
                RenderProvider.class);
        TFI.logger.info("Loaded providers from custom ClassLoader: {}", cl.getClass().getName());
    }

    // ==================== Provider 解析方法 ====================

    static ComparisonProvider getComparisonProvider() {
        return ProviderRegistry.resolve(ComparisonProvider.class);
    }

    static TrackingProvider getTrackingProvider() {
        return ProviderRegistry.resolve(TrackingProvider.class);
    }

    static FlowProvider getFlowProvider() {
        return ProviderRegistry.resolve(FlowProvider.class);
    }

    static RenderProvider getRenderProvider() {
        return ProviderRegistry.resolve(RenderProvider.class);
    }

    static ExportProvider getExportProvider() {
        return ProviderRegistry.resolve(ExportProvider.class);
    }
}
