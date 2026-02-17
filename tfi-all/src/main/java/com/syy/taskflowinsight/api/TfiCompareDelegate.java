package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.util.DiagnosticLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate handling Compare and Render operations for the {@link TFI} facade.
 *
 * <p>Package-private: only used by {@code TFI} and its test companions.
 * Encapsulates compare, comparator, render, ensureCompareService,
 * lookupMarkdownRenderer, and parseStyle logic.
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
final class TfiCompareDelegate {

    private TfiCompareDelegate() {
        throw new AssertionError("delegate class");
    }

    // ==================== compare ====================

    /**
     * Compare two objects with zero-config defaults.
     *
     * @see TFI#compare(Object, Object)
     */
    static com.syy.taskflowinsight.tracking.compare.CompareResult compare(Object a, Object b) {
        try {
            if (!isFacadeEnabled()) {
                return com.syy.taskflowinsight.tracking.compare.CompareResult.identical();
            }

            if (a == b) {
                return com.syy.taskflowinsight.tracking.compare.CompareResult.identical();
            }

            if (a == null || b == null) {
                return com.syy.taskflowinsight.tracking.compare.CompareResult.ofNullDiff(a, b);
            }

            if (!a.getClass().equals(b.getClass())) {
                return com.syy.taskflowinsight.tracking.compare.CompareResult.ofTypeDiff(a, b);
            }

            if (com.syy.taskflowinsight.config.TfiFeatureFlags.isRoutingEnabled()) {
                com.syy.taskflowinsight.spi.ComparisonProvider provider = TfiProviderDelegate.getComparisonProvider();
                return provider.compare(a, b);
            } else {
                com.syy.taskflowinsight.tracking.compare.CompareService svc = ensureCompareService();
                if (svc == null) {
                    DiagnosticLogger.once(
                            "TFI-DIAG-006",
                            "CompareFallback",
                            "CompareService not available (fallback initialization failed)",
                            "Check fallback initialization logs"
                    );
                    return com.syy.taskflowinsight.tracking.compare.CompareResult.ofTypeDiff(a, b);
                }
                return svc.compare(a, b, com.syy.taskflowinsight.tracking.compare.CompareOptions.DEFAULT);
            }

        } catch (Throwable t) {
            TFI.handleInternalError("Failed to compare objects", t, TFI.ErrorLevel.WARN);
            return com.syy.taskflowinsight.tracking.compare.CompareResult.ofTypeDiff(a, b);
        }
    }

    // ==================== comparator ====================

    /**
     * Create a fluent comparator builder.
     *
     * @see TFI#comparator()
     */
    static ComparatorBuilder comparator() {
        try {
            if (!isFacadeEnabled()) {
                return ComparatorBuilder.disabled();
            }

            if (com.syy.taskflowinsight.config.TfiFeatureFlags.isRoutingEnabled()) {
                com.syy.taskflowinsight.spi.ComparisonProvider provider = TfiProviderDelegate.getComparisonProvider();
                return new ComparatorBuilder(null, provider);
            }

            com.syy.taskflowinsight.tracking.compare.CompareService svc = ensureCompareService();
            if (svc == null) {
                TFI.logger.warn("CompareService not available for comparator()");
            }
            return new ComparatorBuilder(svc);
        } catch (Throwable t) {
            TFI.handleInternalError("Failed to create comparator builder", t, TFI.ErrorLevel.WARN);
            return new ComparatorBuilder(null);
        }
    }

    // ==================== render ====================

    /**
     * Render a CompareResult to Markdown.
     *
     * @see TFI#render(com.syy.taskflowinsight.tracking.compare.CompareResult, Object)
     */
    static String render(com.syy.taskflowinsight.tracking.compare.CompareResult result, Object style) {
        try {
            if (!isFacadeEnabled()) {
                return "# Facade Disabled\n\n"
                        + "Rendering is disabled by configuration (tfi.api.facade.enabled=false).\n"
                        + "This is typically used for emergency troubleshooting.\n";
            }

            if (result == null) {
                return "# No Result\n\nCompare result is null.\n";
            }

            if (com.syy.taskflowinsight.config.TfiFeatureFlags.isRoutingEnabled()) {
                com.syy.taskflowinsight.spi.RenderProvider provider = TfiProviderDelegate.getRenderProvider();
                if (provider != null) {
                    com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult diffResult =
                            com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult.from(result);
                    return provider.render(diffResult, style);
                }
            }

            com.syy.taskflowinsight.tracking.render.RenderStyle renderStyle = parseStyle(style);
            com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult diffResult =
                    com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult.from(result);

            com.syy.taskflowinsight.tracking.render.MarkdownRenderer renderer = lookupMarkdownRenderer();
            if (renderer == null) {
                DiagnosticLogger.once(
                        "TFI-DIAG-007",
                        "RenderFallback",
                        "MarkdownRenderer not available (Spring Bean lookup failed and fallback initialization failed)",
                        "Check Spring container configuration or review fallback initialization logs"
                );
                return "# Compare Result\n\n"
                        + "Changes: " + result.getChangeCount() + "\n"
                        + "Identical: " + result.isIdentical() + "\n";
            }

            return renderer.render(diffResult, renderStyle);

        } catch (Throwable t) {
            TFI.handleInternalError("Failed to render result", t, TFI.ErrorLevel.WARN);
            return "# Render Error\n\nFailed to render comparison result.\n";
        }
    }

    // ==================== ensureCompareService ====================

    static com.syy.taskflowinsight.tracking.compare.CompareService ensureCompareService() {
        if (TFI.compareService != null) {
            return TFI.compareService;
        }

        synchronized (TFI.class) {
            if (TFI.compareService == null) {
                try {
                    TFI.logger.info("CompareService creating fallback instance");

                    List<com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy> strategies = new ArrayList<>();

                    try {
                        strategies.add(new com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy());
                    } catch (Exception e) {
                        TFI.logger.warn("Failed to init SimpleListStrategy: {}", e.getMessage());
                    }
                    try {
                        strategies.add(new com.syy.taskflowinsight.tracking.compare.list.AsSetListStrategy());
                    } catch (Exception e) {
                        TFI.logger.warn("Failed to init AsSetListStrategy: {}", e.getMessage());
                    }
                    try {
                        strategies.add(new com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy());
                    } catch (Exception e) {
                        TFI.logger.warn("Failed to init EntityListStrategy: {}", e.getMessage());
                    }
                    try {
                        strategies.add(new com.syy.taskflowinsight.tracking.compare.list.LevenshteinListStrategy());
                    } catch (Exception e) {
                        TFI.logger.warn("Failed to init LevenshteinListStrategy: {}", e.getMessage());
                    }

                    if (strategies.isEmpty()) {
                        TFI.logger.error("No list compare strategies available");
                        return null;
                    }

                    TFI.compareService = new com.syy.taskflowinsight.tracking.compare.CompareService();
                    TFI.logger.info("CompareService fallback instance created with {} strategies", strategies.size());

                } catch (Exception e) {
                    TFI.logger.error("Failed to initialize CompareService: {}", e.getMessage());
                    if (TFI.logger.isDebugEnabled()) {
                        TFI.logger.debug("CompareService initialization error details", e);
                    }
                }
            }
        }

        return TFI.compareService;
    }

    // ==================== lookupMarkdownRenderer ====================

    static com.syy.taskflowinsight.tracking.render.MarkdownRenderer lookupMarkdownRenderer() {
        if (TFI.markdownRenderer != null) {
            return TFI.markdownRenderer;
        }

        synchronized (TFI.class) {
            if (TFI.markdownRenderer == null) {
                try {
                    TFI.logger.info("MarkdownRenderer creating fallback instance");
                    TFI.markdownRenderer = new com.syy.taskflowinsight.tracking.render.MarkdownRenderer();
                    TFI.logger.info("MarkdownRenderer fallback instance created");

                } catch (Exception e) {
                    TFI.logger.error("Failed to initialize MarkdownRenderer: {}", e.getMessage());
                    if (TFI.logger.isDebugEnabled()) {
                        TFI.logger.debug("MarkdownRenderer initialization error details", e);
                    }
                }
            }
        }

        return TFI.markdownRenderer;
    }

    // ==================== parseStyle ====================

    static com.syy.taskflowinsight.tracking.render.RenderStyle parseStyle(Object style) {
        if (style == null) {
            return com.syy.taskflowinsight.tracking.render.RenderStyle.standard();
        }

        if (style instanceof com.syy.taskflowinsight.tracking.render.RenderStyle) {
            return (com.syy.taskflowinsight.tracking.render.RenderStyle) style;
        }

        if (style instanceof String) {
            String alias = ((String) style).trim().toLowerCase();
            switch (alias) {
                case "simple":
                    return com.syy.taskflowinsight.tracking.render.RenderStyle.simple();
                case "standard":
                    return com.syy.taskflowinsight.tracking.render.RenderStyle.standard();
                case "detailed":
                    return com.syy.taskflowinsight.tracking.render.RenderStyle.detailed();
                default:
                    DiagnosticLogger.once(
                            "TFI-DIAG-005",
                            "RenderStyleFallback",
                            "Unknown render style alias '" + alias + "'",
                            "Use simple/standard/detailed or provide RenderStyle object directly"
                    );
                    return com.syy.taskflowinsight.tracking.render.RenderStyle.standard();
            }
        }

        DiagnosticLogger.once(
                "TFI-DIAG-005",
                "RenderStyleFallback",
                "Unsupported render style type '" + style.getClass().getName() + "'",
                "Use simple/standard/detailed string alias or provide RenderStyle object directly"
        );
        return com.syy.taskflowinsight.tracking.render.RenderStyle.standard();
    }

    // ==================== private helper ====================

    private static boolean isFacadeEnabled() {
        return com.syy.taskflowinsight.config.TfiFeatureFlags.isFacadeEnabled();
    }
}
