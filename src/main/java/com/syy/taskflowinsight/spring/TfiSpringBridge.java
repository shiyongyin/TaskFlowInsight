package com.syy.taskflowinsight.spring;

import com.syy.taskflowinsight.spi.*;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.config.TfiFeatureFlags;
import com.syy.taskflowinsight.enums.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.util.List;

/**
 * TFI Spring 桥接配置
 *
 * <p>负责将 Spring Bean 适配并注册为 Provider，实现 Spring 环境下的自动桥接。
 *
 * <p>桥接模式：
 * <ul>
 *   <li>auto（默认）：注册 Spring 适配器，保留 ServiceLoader 与兜底</li>
 *   <li>spring-only：仅注册 Spring 适配器</li>
 *   <li>service-loader-only：不注册 Spring 适配器（纯 ServiceLoader）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
@Configuration
@ConditionalOnProperty(name = "tfi.api.routing.enabled", havingValue = "true")
public class TfiSpringBridge {

    private static final Logger logger = LoggerFactory.getLogger(TfiSpringBridge.class);

    @Autowired(required = false)
    private CompareService compareService;

    @Autowired(required = false)
    private ChangeTracker changeTracker;

    @Autowired(required = false)
    private MarkdownRenderer markdownRenderer;

    /**
     * 在 Spring 容器启动后注册适配器
     */
    @PostConstruct
    public void registerProviders() {
        String providerMode = TfiFeatureFlags.getRoutingProviderMode();
        logger.info("TFI Spring Bridge initializing with provider-mode: {}", providerMode);

        // service-loader-only 模式：不注册 Spring 适配器
        if ("service-loader-only".equalsIgnoreCase(providerMode)) {
            logger.info("Provider mode is 'service-loader-only', skipping Spring Bean registration");
            return;
        }

        // auto 或 spring-only 模式：注册 Spring 适配器
        registerComparisonProvider();
        registerTrackingProvider();
        registerRenderProvider();
        registerFlowProvider();

        logger.info("TFI Spring Bridge initialization completed");
    }

    /**
     * 注册 ComparisonProvider 适配器
     */
    private void registerComparisonProvider() {
        if (compareService != null) {
            SpringComparisonProviderAdapter adapter = new SpringComparisonProviderAdapter(compareService);
            ProviderRegistry.register(ComparisonProvider.class, adapter);
            logger.info("Registered SpringComparisonProviderAdapter (priority={})", adapter.priority());
        } else {
            logger.debug("CompareService bean not found, skipping ComparisonProvider registration");
        }
    }

    /**
     * 注册 TrackingProvider 适配器
     */
    private void registerTrackingProvider() {
        if (changeTracker != null) {
            SpringTrackingProviderAdapter adapter = new SpringTrackingProviderAdapter(changeTracker);
            ProviderRegistry.register(TrackingProvider.class, adapter);
            logger.info("Registered SpringTrackingProviderAdapter (priority={})", adapter.priority());
        } else {
            logger.debug("ChangeTracker bean not found, skipping TrackingProvider registration");
        }
    }

    /**
     * 注册 RenderProvider 适配器
     */
    private void registerRenderProvider() {
        if (markdownRenderer != null) {
            SpringRenderProviderAdapter adapter = new SpringRenderProviderAdapter(markdownRenderer);
            ProviderRegistry.register(RenderProvider.class, adapter);
            logger.info("Registered SpringRenderProviderAdapter (priority={})", adapter.priority());
        } else {
            logger.debug("MarkdownRenderer bean not found, skipping RenderProvider registration");
        }
    }

    /**
     * 注册 FlowProvider 适配器
     * <p>FlowProvider 使用 ManagedThreadContext 静态方法，无需依赖 Spring Bean</p>
     */
    private void registerFlowProvider() {
        SpringFlowProviderAdapter adapter = new SpringFlowProviderAdapter();
        ProviderRegistry.register(FlowProvider.class, adapter);
        logger.info("Registered SpringFlowProviderAdapter (priority={})", adapter.priority());
    }

    // ==================== 适配器实现 ====================

    /**
     * Spring ComparisonProvider 适配器
     * <p>委托给 CompareService 实现</p>
     */
    private static class SpringComparisonProviderAdapter implements ComparisonProvider {
        private final CompareService delegate;

        public SpringComparisonProviderAdapter(CompareService delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompareResult compare(Object before, Object after) {
            try {
                // 委托 CompareService（无选项重载）
                return delegate.compare(before, after);
            } catch (Exception e) {
                logger.error("SpringComparisonProviderAdapter.compare failed", e);
                // 异常降级：返回空结果
                return CompareResult.builder()
                    .object1(before)
                    .object2(after)
                    .changes(java.util.Collections.emptyList())
                    .identical(false)
                    .build();
            }
        }

        @Override
        public CompareResult compare(Object before, Object after, com.syy.taskflowinsight.tracking.compare.CompareOptions options) {
            try {
                com.syy.taskflowinsight.tracking.compare.CompareOptions opts =
                    options != null ? options : com.syy.taskflowinsight.tracking.compare.CompareOptions.DEFAULT;
                return delegate.compare(before, after, opts);
            } catch (Exception e) {
                logger.error("SpringComparisonProviderAdapter.compare(options) failed", e);
                return CompareResult.builder()
                    .object1(before)
                    .object2(after)
                    .changes(java.util.Collections.emptyList())
                    .identical(false)
                    .build();
            }
        }

        @Override
        public int priority() {
            // Spring Bean 优先级：200（高于 ServiceLoader 的 0）
            return 200;
        }

        @Override
        public String toString() {
            return "SpringComparisonProviderAdapter{priority=200, delegate=" + delegate.getClass().getSimpleName() + "}";
        }
    }

    /**
     * Spring TrackingProvider 适配器
     * <p>委托给 ChangeTracker 实现</p>
     */
    private static class SpringTrackingProviderAdapter implements TrackingProvider {
        private final ChangeTracker delegate;

        public SpringTrackingProviderAdapter(ChangeTracker delegate) {
            this.delegate = delegate;
        }

        @Override
        public void track(String name, Object target, String... fields) {
            try {
                delegate.track(name, target, fields);
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.track failed", e);
                // 异常安全：不向上抛
            }
        }

        @Override
        public List<ChangeRecord> changes() {
            try {
                return delegate.getChanges();
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.changes failed", e);
                return java.util.Collections.emptyList();
            }
        }

        @Override
        public void clear() {
            try {
                delegate.clearAllTracking();
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.clear failed", e);
                // 异常安全：不向上抛
            }
        }

        @Override
        public int priority() {
            // Spring Bean 优先级：200
            return 200;
        }

        @Override
        public String toString() {
            return "SpringTrackingProviderAdapter{priority=200, delegate=" + delegate.getClass().getSimpleName() + "}";
        }
    }

    /**
     * Spring RenderProvider 适配器
     * <p>委托给 MarkdownRenderer 实现</p>
     */
    private static class SpringRenderProviderAdapter implements RenderProvider {
        private final MarkdownRenderer delegate;

        public SpringRenderProviderAdapter(MarkdownRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public String render(Object result, Object style) {
            try {
                // 1. 验证 result 类型
                if (result == null) {
                    logger.debug("SpringRenderProviderAdapter: result is null, returning empty string");
                    return "";
                }
                if (!(result instanceof EntityListDiffResult)) {
                    logger.warn("SpringRenderProviderAdapter: result is not EntityListDiffResult (got {}), returning toString()",
                            result.getClass().getName());
                    return result.toString();
                }
                EntityListDiffResult diffResult = (EntityListDiffResult) result;

                // 2. 解析 style 参数
                RenderStyle renderStyle = parseStyle(style);

                // 3. 委托给 MarkdownRenderer
                return delegate.render(diffResult, renderStyle);

            } catch (Exception e) {
                logger.error("SpringRenderProviderAdapter.render failed", e);
                // 异常降级：返回基础文本
                return "# Render Failed\n\nError: " + e.getMessage();
            }
        }

        /**
         * 解析 style 参数
         * <p>支持 String ("simple"/"standard"/"detailed") 或 RenderStyle 对象</p>
         */
        private RenderStyle parseStyle(Object style) {
            if (style == null) {
                return RenderStyle.standard(); // 默认标准样式
            }

            if (style instanceof RenderStyle) {
                return (RenderStyle) style;
            }

            if (style instanceof String) {
                String styleStr = ((String) style).toLowerCase();
                switch (styleStr) {
                    case "simple":
                        return RenderStyle.simple();
                    case "detailed":
                        return RenderStyle.detailed();
                    case "standard":
                    default:
                        return RenderStyle.standard();
                }
            }

            logger.debug("SpringRenderProviderAdapter: unrecognized style type {}, using standard",
                    style.getClass().getName());
            return RenderStyle.standard();
        }

        @Override
        public int priority() {
            // Spring Bean 优先级：200（高于 ServiceLoader 的 0）
            return 200;
        }

        @Override
        public String toString() {
            return "SpringRenderProviderAdapter{priority=200, delegate=" + delegate.getClass().getSimpleName() + "}";
        }
    }

    /**
     * Spring FlowProvider 适配器
     * <p>委托给 ManagedThreadContext 实现流程管理</p>
     */
    private static class SpringFlowProviderAdapter implements FlowProvider {

        @Override
        public String startSession(String name) {
            try {
                Session session = ManagedThreadContext.current() != null
                    ? ManagedThreadContext.current().startSession(name)
                    : ManagedThreadContext.create(name).getCurrentSession();
                return session != null ? session.getSessionId() : null;
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.startSession failed", e);
                return null;
            }
        }

        @Override
        public void endSession() {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context != null) {
                    context.endSession();
                }
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.endSession failed", e);
                // 异常安全：不向上抛
            }
        }

        @Override
        public TaskNode startTask(String name) {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context == null) {
                    logger.debug("No active context, cannot start task: {}", name);
                    return null;
                }
                return context.startTask(name);
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.startTask failed", e);
                return null;
            }
        }

        @Override
        public void endTask() {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context != null) {
                    context.endTask();
                }
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.endTask failed", e);
                // 异常安全：不向上抛
            }
        }

        @Override
        public Session currentSession() {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                return context != null ? context.getCurrentSession() : null;
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.currentSession failed", e);
                return null;
            }
        }

        @Override
        public TaskNode currentTask() {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                return context != null ? context.getCurrentTask() : null;
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.currentTask failed", e);
                return null;
            }
        }

        @Override
        public void message(String content, String label) {
            try {
                TaskNode task = currentTask();
                if (task != null) {
                    if (label != null && !label.isEmpty()) {
                        task.addMessage(content, label);
                    } else {
                        task.addMessage(content, MessageType.PROCESS);
                    }
                }
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.message failed", e);
                // 异常安全：不向上抛
            }
        }

        @Override
        public int priority() {
            // Spring Bean 优先级：200
            return 200;
        }

        @Override
        public String toString() {
            return "SpringFlowProviderAdapter{priority=200}";
        }
    }
}
