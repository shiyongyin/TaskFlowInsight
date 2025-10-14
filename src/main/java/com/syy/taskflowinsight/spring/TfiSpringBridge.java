package com.syy.taskflowinsight.spring;

import com.syy.taskflowinsight.spi.*;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.CompareService;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.render.MarkdownRenderer;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import com.syy.taskflowinsight.tracking.compare.entity.EntityListDiffResult;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.config.TfiFeatureFlags;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.exporter.text.ConsoleExporter;
import com.syy.taskflowinsight.exporter.json.JsonExporter;
import com.syy.taskflowinsight.exporter.map.MapExporter;
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
        registerExportProvider();

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

    /**
     * 注册 ExportProvider 适配器
     * <p>ExportProvider 使用导出器类，无需依赖 Spring Bean</p>
     */
    private void registerExportProvider() {
        SpringExportProviderAdapter adapter = new SpringExportProviderAdapter();
        ProviderRegistry.register(ExportProvider.class, adapter);
        logger.info("Registered SpringExportProviderAdapter (priority={})", adapter.priority());
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
        public void trackAll(java.util.Map<String, Object> targets) {
            try {
                if (targets == null) {
                    throw new NullPointerException("targets cannot be null");
                }
                // 委托给 ChangeTracker.trackAll()
                delegate.trackAll(targets);
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.trackAll failed", e);
                // 异常安全：不向上抛
            }
        }

        @Override
        public void trackDeep(String name, Object obj) {
            trackDeep(name, obj, null);
        }

        @Override
        public void trackDeep(String name, Object obj, com.syy.taskflowinsight.api.TrackingOptions options) {
            try {
                // 深度追踪：使用 TrackingOptions.deep() 或自定义 options
                com.syy.taskflowinsight.api.TrackingOptions trackOptions =
                    options != null ? options : com.syy.taskflowinsight.api.TrackingOptions.deep();
                delegate.track(name, obj, trackOptions);
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.trackDeep failed", e);
                // 异常安全：不向上抛
            }
        }

        @Override
        public List<ChangeRecord> getAllChanges() {
            try {
                // 注意：ChangeTracker.getChanges() 返回当前线程的所有变更
                // 在单线程场景下等同于 getAllChanges()
                // 多线程场景需要聚合所有线程的变更（未实现）
                return delegate.getChanges();
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.getAllChanges failed", e);
                return java.util.Collections.emptyList();
            }
        }

        @Override
        public void startTracking(String sessionName) {
            try {
                // ChangeTracker 是 ThreadLocal 的，会话管理由 FlowProvider 负责
                // 这里仅记录日志，实际会话隔离需要 ManagedThreadContext 支持
                logger.debug("SpringTrackingProviderAdapter.startTracking called with sessionName: {}", sessionName);
                // 可选：将 sessionName 存入 ThreadLocal 用于后续查询
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.startTracking failed", e);
            }
        }

        @Override
        public void recordChange(String objectName, String fieldName,
                                  Object oldValue, Object newValue,
                                  ChangeType changeType) {
            try {
                // ChangeTracker 没有直接的 recordChange 方法
                // 需要手动构建 ChangeRecord 并添加到当前线程的变更列表
                // 这里提供简化实现：记录日志
                logger.debug("SpringTrackingProviderAdapter.recordChange: object={}, field={}, old={}, new={}, type={}",
                    objectName, fieldName, oldValue, newValue, changeType);

                // 完整实现需要：
                // 1. 获取当前 ThreadLocal 的 changes 列表
                // 2. 创建 ChangeRecord 对象
                // 3. 添加到列表中
                // 由于 ChangeTracker 是 package-private，这里暂不实现
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.recordChange failed", e);
            }
        }

        @Override
        public void clearTracking(String sessionName) {
            try {
                if (sessionName != null && !sessionName.isEmpty()) {
                    // 委托给 ChangeTracker.clearBySessionId()
                    delegate.clearBySessionId(sessionName);
                } else {
                    // 空 sessionName：清除所有追踪
                    delegate.clearAllTracking();
                }
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.clearTracking failed", e);
                // 异常安全：不向上抛
            }
        }

        @Override
        public void withTracked(String name, Object obj, Runnable action, String... fields) {
            try {
                // 完整实现：前后快照比对
                // 1. 拍摄前快照
                delegate.track(name, obj, fields);

                // 2. 执行操作
                if (action != null) {
                    action.run();
                }

                // 3. 拍摄后快照并比对（自动触发）
                // ChangeTracker 会在 getChanges() 时自动比对快照
                // 因此无需额外代码
            } catch (Exception e) {
                logger.error("SpringTrackingProviderAdapter.withTracked failed", e);
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

        // 缓存任务栈，失效条件：startTask/endTask
        private volatile List<TaskNode> cachedTaskStack;
        private volatile int cachedTaskDepth = -1;

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
                // 失效缓存
                cachedTaskStack = null;
                cachedTaskDepth = -1;
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
                    // 失效缓存
                    cachedTaskStack = null;
                    cachedTaskDepth = -1;
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
        public void clear() {
            try {
                // 先结束所有嵌套任务
                while (currentTask() != null) {
                    endTask();
                }
                // 再结束会话
                if (currentSession() != null) {
                    endSession();
                }
                // 清理 ThreadLocal（防止内存泄漏）
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context != null) {
                    context.close();
                }
                // 清空缓存
                cachedTaskStack = null;
                cachedTaskDepth = -1;
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.clear failed", e);
                // 异常安全：保证清理继续
            }
        }

        @Override
        public List<TaskNode> getTaskStack() {
            try {
                TaskNode current = currentTask();
                if (current == null) {
                    return java.util.List.of(); // 空栈
                }

                // 计算当前深度
                int currentDepth = calculateDepth(current);

                // 缓存命中（任务栈深度未变）
                if (cachedTaskStack != null && cachedTaskDepth == currentDepth) {
                    return cachedTaskStack; // ✅ O(1) 缓存命中
                }

                // 缓存未命中，重新构建
                java.util.LinkedList<TaskNode> stack = new java.util.LinkedList<>();
                while (current != null) {
                    stack.addFirst(current); // O(1) 头部插入
                    current = current.getParent();
                }

                cachedTaskStack = java.util.List.copyOf(stack);
                cachedTaskDepth = currentDepth;
                return cachedTaskStack;
            } catch (Exception e) {
                logger.error("SpringFlowProviderAdapter.getTaskStack failed", e);
                return java.util.List.of(); // 异常降级：返回空列表
            }
        }

        /**
         * 计算任务深度（用于缓存失效判断）
         */
        private int calculateDepth(TaskNode task) {
            int depth = 0;
            while (task != null) {
                depth++;
                task = task.getParent();
            }
            return depth;
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

    /**
     * Spring ExportProvider 适配器
     * <p>委托给 ConsoleExporter, JsonExporter, MapExporter 实现</p>
     */
    private static class SpringExportProviderAdapter implements ExportProvider {

        @Override
        public boolean exportToConsole(boolean showTimestamp) {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context == null || context.getCurrentSession() == null) {
                    logger.debug("No active session, cannot export to console");
                    return false;
                }

                Session session = context.getCurrentSession();
                ConsoleExporter exporter = new ConsoleExporter();
                String output = exporter.export(session, showTimestamp);
                System.out.println(output);
                return true;
            } catch (Exception e) {
                logger.error("SpringExportProviderAdapter.exportToConsole failed", e);
                return false;
            }
        }

        @Override
        public String exportToJson() {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context == null || context.getCurrentSession() == null) {
                    logger.debug("No active session, cannot export to JSON");
                    return "{}";
                }

                Session session = context.getCurrentSession();
                JsonExporter exporter = new JsonExporter();
                return exporter.export(session);
            } catch (Exception e) {
                logger.error("SpringExportProviderAdapter.exportToJson failed", e);
                return "{}"; // 异常降级：返回空 JSON
            }
        }

        @Override
        public java.util.Map<String, Object> exportToMap() {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context == null || context.getCurrentSession() == null) {
                    logger.debug("No active session, cannot export to Map");
                    return java.util.Collections.emptyMap();
                }

                Session session = context.getCurrentSession();
                return MapExporter.export(session);
            } catch (Exception e) {
                logger.error("SpringExportProviderAdapter.exportToMap failed", e);
                return java.util.Collections.emptyMap(); // 异常降级：返回空 Map
            }
        }

        @Override
        public int priority() {
            // Spring Bean 优先级：200
            return 200;
        }

        @Override
        public String toString() {
            return "SpringExportProviderAdapter{priority=200}";
        }
    }
}
