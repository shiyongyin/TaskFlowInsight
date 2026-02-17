package com.syy.taskflowinsight.api;

/**
 * TFI Provider 注册与查找委托（包级私有）。
 *
 * <p>处理 v4.0.0 SPI Provider 的注册、缓存查找和 ClassLoader 加载。
 * 所有 Provider 使用 DCL 缓存，缓存命中后 P95 &lt; 100ns。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
final class TfiProviderDelegate {

    private TfiProviderDelegate() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 注册方法 ====================

    static void registerComparisonProvider(com.syy.taskflowinsight.spi.ComparisonProvider provider) {
        try {
            com.syy.taskflowinsight.spi.ProviderRegistry.register(
                    com.syy.taskflowinsight.spi.ComparisonProvider.class, provider);
            TFI.logger.info("Registered custom ComparisonProvider: {}",
                    provider.getClass().getSimpleName());
        } catch (Throwable t) {
            TFI.handleInternalError("Failed to register ComparisonProvider", t);
        }
    }

    static void registerTrackingProvider(com.syy.taskflowinsight.spi.TrackingProvider provider) {
        try {
            com.syy.taskflowinsight.spi.ProviderRegistry.register(
                    com.syy.taskflowinsight.spi.TrackingProvider.class, provider);
            TFI.logger.info("Registered custom TrackingProvider: {}",
                    provider.getClass().getSimpleName());
        } catch (Throwable t) {
            TFI.handleInternalError("Failed to register TrackingProvider", t);
        }
    }

    static void registerFlowProvider(com.syy.taskflowinsight.spi.FlowProvider provider) {
        try {
            com.syy.taskflowinsight.spi.ProviderRegistry.register(
                    com.syy.taskflowinsight.spi.FlowProvider.class, provider);
            TFI.logger.info("Registered custom FlowProvider: {}",
                    provider.getClass().getSimpleName());
        } catch (Throwable t) {
            TFI.handleInternalError("Failed to register FlowProvider", t);
        }
    }

    static void registerRenderProvider(com.syy.taskflowinsight.spi.RenderProvider provider) {
        try {
            com.syy.taskflowinsight.spi.ProviderRegistry.register(
                    com.syy.taskflowinsight.spi.RenderProvider.class, provider);
            TFI.logger.info("Registered custom RenderProvider: {}",
                    provider.getClass().getSimpleName());
        } catch (Throwable t) {
            TFI.handleInternalError("Failed to register RenderProvider", t);
        }
    }

    static void registerExportProvider(com.syy.taskflowinsight.spi.ExportProvider provider) {
        try {
            com.syy.taskflowinsight.spi.ProviderRegistry.register(
                    com.syy.taskflowinsight.spi.ExportProvider.class, provider);
            TFI.logger.info("Registered custom ExportProvider: {}",
                    provider.getClass().getSimpleName());
        } catch (Throwable t) {
            TFI.handleInternalError("Failed to register ExportProvider", t);
        }
    }

    static void loadProviders(ClassLoader cl) {
        try {
            com.syy.taskflowinsight.spi.ProviderRegistry.loadProviders(cl);
            TFI.logger.info("Loaded providers from custom ClassLoader: {}",
                    cl.getClass().getName());
        } catch (Throwable t) {
            TFI.handleInternalError("Failed to load providers from ClassLoader", t);
        }
    }

    // ==================== 缓存查找方法 ====================

    static com.syy.taskflowinsight.spi.ComparisonProvider getComparisonProvider() {
        if (TFI.cachedComparisonProvider != null) {
            return TFI.cachedComparisonProvider;
        }
        synchronized (TFI.class) {
            if (TFI.cachedComparisonProvider == null) {
                com.syy.taskflowinsight.spi.ComparisonProvider provider =
                        com.syy.taskflowinsight.spi.ProviderRegistry.lookup(
                                com.syy.taskflowinsight.spi.ComparisonProvider.class);
                if (provider == null) {
                    provider = new com.syy.taskflowinsight.spi.DefaultComparisonProvider();
                    TFI.logger.debug("Using default ComparisonProvider");
                } else {
                    TFI.logger.debug("Found ComparisonProvider: {} (priority={})",
                            provider.getClass().getSimpleName(), provider.priority());
                }
                TFI.cachedComparisonProvider = provider;
            }
        }
        return TFI.cachedComparisonProvider;
    }

    static com.syy.taskflowinsight.spi.TrackingProvider getTrackingProvider() {
        if (TFI.cachedTrackingProvider != null) {
            return TFI.cachedTrackingProvider;
        }
        synchronized (TFI.class) {
            if (TFI.cachedTrackingProvider == null) {
                TFI.cachedTrackingProvider = com.syy.taskflowinsight.spi.ProviderRegistry.lookup(
                        com.syy.taskflowinsight.spi.TrackingProvider.class);
                if (TFI.cachedTrackingProvider != null) {
                    TFI.logger.debug("Found TrackingProvider: {} (priority={})",
                            TFI.cachedTrackingProvider.getClass().getSimpleName(),
                            TFI.cachedTrackingProvider.priority());
                }
            }
        }
        return TFI.cachedTrackingProvider;
    }

    static com.syy.taskflowinsight.spi.FlowProvider getFlowProvider() {
        if (TFI.cachedFlowProvider != null) {
            return TFI.cachedFlowProvider;
        }
        synchronized (TFI.class) {
            if (TFI.cachedFlowProvider == null) {
                TFI.cachedFlowProvider = com.syy.taskflowinsight.spi.ProviderRegistry.lookup(
                        com.syy.taskflowinsight.spi.FlowProvider.class);
                if (TFI.cachedFlowProvider != null) {
                    TFI.logger.debug("Found FlowProvider: {} (priority={})",
                            TFI.cachedFlowProvider.getClass().getSimpleName(),
                            TFI.cachedFlowProvider.priority());
                }
            }
        }
        return TFI.cachedFlowProvider;
    }

    static com.syy.taskflowinsight.spi.RenderProvider getRenderProvider() {
        if (TFI.cachedRenderProvider != null) {
            return TFI.cachedRenderProvider;
        }
        synchronized (TFI.class) {
            if (TFI.cachedRenderProvider == null) {
                TFI.cachedRenderProvider = com.syy.taskflowinsight.spi.ProviderRegistry.lookup(
                        com.syy.taskflowinsight.spi.RenderProvider.class);
                if (TFI.cachedRenderProvider != null) {
                    TFI.logger.debug("Found RenderProvider: {} (priority={})",
                            TFI.cachedRenderProvider.getClass().getSimpleName(),
                            TFI.cachedRenderProvider.priority());
                }
            }
        }
        return TFI.cachedRenderProvider;
    }

    static com.syy.taskflowinsight.spi.ExportProvider getExportProvider() {
        if (TFI.cachedExportProvider != null) {
            return TFI.cachedExportProvider;
        }
        synchronized (TFI.class) {
            if (TFI.cachedExportProvider == null) {
                TFI.cachedExportProvider = com.syy.taskflowinsight.spi.ProviderRegistry.lookup(
                        com.syy.taskflowinsight.spi.ExportProvider.class);
                if (TFI.cachedExportProvider != null) {
                    TFI.logger.debug("Found ExportProvider: {} (priority={})",
                            TFI.cachedExportProvider.getClass().getSimpleName(),
                            TFI.cachedExportProvider.priority());
                }
            }
        }
        return TFI.cachedExportProvider;
    }
}
