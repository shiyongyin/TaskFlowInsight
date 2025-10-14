package com.syy.taskflowinsight.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider注册与查找中心
 *
 * <p>负责管理所有SPI Provider的注册、查找和优先级仲裁。
 * 支持三种Provider来源：
 * <ol>
 *   <li>Spring Bean (优先级最高，通常200+)</li>
 *   <li>手动注册 (TFI.registerXxxProvider，优先级1-199)</li>
 *   <li>ServiceLoader自动发现 (优先级0)</li>
 * </ol>
 *
 * <p>线程安全：所有public方法都是线程安全的。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class ProviderRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ProviderRegistry.class);

    /**
     * 注册表：providerType → List<Provider实例>
     * List按priority降序排序 (数值越大越靠前)
     */
    private static final Map<Class<?>, List<Object>> registeredProviders = new ConcurrentHashMap<>();

    /**
     * ServiceLoader缓存：避免重复扫描META-INF/services
     */
    private static final Map<Class<?>, List<Object>> serviceLoaderCache = new ConcurrentHashMap<>();

    /**
     * 允许的Provider白名单（可选）。
     *
     * <p>匹配规则：
     * <ul>
     *   <li>精确类名匹配（例如：com.example.FooProvider）</li>
     *   <li>包前缀匹配（以<code>.*</code>结尾，例如：com.example.providers.*）</li>
     * </ul>
     * 未配置或集合为空时表示不启用白名单。
     */
    private static volatile Set<String> allowedProviders;

    /**
     * 兜底Provider缓存
     */
    private static volatile ComparisonProvider defaultComparisonProvider;

    /**
     * 手动注册Provider
     *
     * @param providerType Provider接口类型
     * @param provider Provider实例
     * @param <T> Provider类型
     */
    public static <T> void register(Class<T> providerType, T provider) {
        if (providerType == null || provider == null) {
            logger.warn("Attempted to register null provider, ignoring");
            return;
        }

        if (!isAllowed(provider)) {
            logger.warn("Provider {} is not in allowed list, skip registration", provider.getClass().getName());
            return;
        }

        registeredProviders.compute(providerType, (key, existingList) -> {
            List<Object> newList = new ArrayList<>(existingList != null ? existingList : Collections.emptyList());
            newList.add(provider);

            // 按priority降序排序 (高优先级在前)
            newList.sort((a, b) -> {
                int priorityA = getPriority(a);
                int priorityB = getPriority(b);
                return Integer.compare(priorityB, priorityA); // 降序
            });

            logger.info("Registered {} provider: {} (priority={})",
                providerType.getSimpleName(),
                provider.getClass().getSimpleName(),
                getPriority(provider));

            return Collections.unmodifiableList(newList);
        });
    }

    /**
     * 取消注册指定Provider实例。
     *
     * @param providerType Provider接口类型
     * @param provider Provider实例
     * @param <T> Provider类型
     * @return 是否移除成功
     */
    public static <T> boolean unregister(Class<T> providerType, T provider) {
        if (providerType == null || provider == null) {
            return false;
        }

        final boolean[] removed = {false};
        registeredProviders.compute(providerType, (key, existingList) -> {
            if (existingList == null || existingList.isEmpty()) {
                return existingList;
            }

            List<Object> newList = new ArrayList<>(existingList);
            removed[0] = newList.remove(provider);
            if (newList.isEmpty()) {
                return null; // 从Map中移除该key
            }
            return Collections.unmodifiableList(newList);
        });

        if (removed[0]) {
            logger.info("Unregistered {} provider: {}", providerType.getSimpleName(), provider.getClass().getSimpleName());
        }
        return removed[0];
    }

    /**
     * 查找Provider (按优先级顺序)
     *
     * @param providerType Provider接口类型
     * @param <T> Provider类型
     * @return Provider实例，如果找不到返回null
     */
    public static <T> T lookup(Class<T> providerType) {
        if (providerType == null) {
            return null;
        }

        // 1. 先查手动注册的Provider (包括Spring Bean)
        List<Object> registered = registeredProviders.get(providerType);
        if (registered != null && !registered.isEmpty()) {
            @SuppressWarnings("unchecked")
            T provider = (T) registered.get(0); // 已按priority排序，取第一个
            return provider;
        }

        // 2. 再查ServiceLoader自动发现的Provider
        List<Object> fromServiceLoader = loadFromServiceLoader(providerType);
        if (!fromServiceLoader.isEmpty()) {
            @SuppressWarnings("unchecked")
            T provider = (T) fromServiceLoader.get(0);
            return provider;
        }

        // 3. 返回null，由调用方决定兜底策略
        return null;
    }

    /**
     * 从ServiceLoader加载Provider (带缓存)
     */
    private static <T> List<Object> loadFromServiceLoader(Class<T> providerType) {
        return serviceLoaderCache.computeIfAbsent(providerType, key -> {
            List<Object> providers = new ArrayList<>();
            try {
                ServiceLoader<T> loader = ServiceLoader.load(providerType);
                loader.forEach(provider -> {
                    if (!isAllowed(provider)) {
                        logger.warn("Filtered out disallowed provider from ServiceLoader: {}",
                            provider.getClass().getName());
                        return;
                    }
                    providers.add(provider);
                    logger.debug("Discovered {} provider from ServiceLoader: {} (priority={})",
                        providerType.getSimpleName(),
                        provider.getClass().getSimpleName(),
                        getPriority(provider));
                });

                // 按priority排序
                providers.sort((a, b) -> Integer.compare(getPriority(b), getPriority(a)));

            } catch (ServiceConfigurationError e) {
                logger.error("ServiceLoader failed to load {}: {}",
                    providerType.getSimpleName(), e.getMessage());
            }

            return Collections.unmodifiableList(providers);
        });
    }

    /**
     * 使用自定义ClassLoader加载所有Provider类型
     *
     * @param cl 自定义ClassLoader
     */
    public static void loadProviders(ClassLoader cl) {
        if (cl == null) {
            logger.warn("Cannot load providers with null ClassLoader");
            return;
        }

        try {
            loadProvidersForType(ComparisonProvider.class, cl);
            loadProvidersForType(TrackingProvider.class, cl);
            loadProvidersForType(FlowProvider.class, cl);
            loadProvidersForType(RenderProvider.class, cl);
            logger.info("Loaded providers from custom ClassLoader: {}", cl.getClass().getName());
        } catch (Throwable t) {
            logger.warn("Failed to load providers from ClassLoader {}: {}",
                cl.getClass().getName(), t.getMessage());
        }
    }

    /**
     * 为指定类型加载Provider（使用自定义ClassLoader）
     */
    private static <T> void loadProvidersForType(Class<T> type, ClassLoader cl) {
        try {
            ServiceLoader<T> loader = ServiceLoader.load(type, cl);
            List<Object> providers = new ArrayList<>();

            for (T provider : loader) {
                if (!isAllowed(provider)) {
                    logger.warn("Filtered out disallowed provider from custom ClassLoader: {}",
                        provider.getClass().getName());
                    continue;
                }
                providers.add(provider);
                logger.debug("Discovered {} provider from ClassLoader: {} (priority={})",
                    type.getSimpleName(),
                    provider.getClass().getSimpleName(),
                    getPriority(provider));
            }

            if (!providers.isEmpty()) {
                // 按priority排序并缓存
                providers.sort((a, b) -> Integer.compare(getPriority(b), getPriority(a)));
                serviceLoaderCache.put(type, Collections.unmodifiableList(providers));
            }
        } catch (Throwable t) {
            logger.warn("Failed to load {} from ClassLoader: {}",
                type.getSimpleName(), t.getMessage());
        }
    }

    /**
     * 获取Provider的priority值
     */
    private static int getPriority(Object provider) {
        try {
            if (provider instanceof ComparisonProvider) {
                return ((ComparisonProvider) provider).priority();
            }
            if (provider instanceof TrackingProvider) {
                return ((TrackingProvider) provider).priority();
            }
            if (provider instanceof FlowProvider) {
                return ((FlowProvider) provider).priority();
            }
            if (provider instanceof RenderProvider) {
                return ((RenderProvider) provider).priority();
            }
        } catch (Throwable t) {
            logger.warn("Failed to get priority from {}: {}",
                provider.getClass().getName(), t.getMessage());
        }
        return 0;
    }

    /**
     * 获取默认ComparisonProvider (懒加载单例)
     */
    public static ComparisonProvider getDefaultComparisonProvider() {
        if (defaultComparisonProvider == null) {
            synchronized (ProviderRegistry.class) {
                if (defaultComparisonProvider == null) {
                    defaultComparisonProvider = new DefaultComparisonProvider();
                    logger.info("Initialized default ComparisonProvider");
                }
            }
        }
        return defaultComparisonProvider;
    }

    /**
     * 清空所有注册 (测试用)
     */
    public static void clearAll() {
        registeredProviders.clear();
        serviceLoaderCache.clear();
        defaultComparisonProvider = null;
        logger.info("Cleared all registered providers");
    }

    /**
     * 获取所有已注册的Provider (调试用)
     */
    public static Map<Class<?>, List<Object>> getAllRegistered() {
        return Collections.unmodifiableMap(registeredProviders);
    }

    /**
     * 配置允许的Provider白名单（测试或非Spring环境使用）。
     *
     * <p>支持精确类名和包前缀（以.*结尾）。传入null或空集合表示禁用白名单。</p>
     */
    public static void setAllowedProviders(Collection<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            allowedProviders = null;
        } else {
            allowedProviders = Collections.unmodifiableSet(new HashSet<>(allowed));
        }
        // 清空缓存，确保下次加载生效
        serviceLoaderCache.clear();
    }

    /**
     * 从系统属性读取白名单配置（key: tfi.spi.allowedProviders，逗号分隔）。
     * 若已通过 setAllowedProviders 设置，则以显式设置优先。
     */
    private static Set<String> loadAllowedFromSystemPropertyIfAbsent() {
        if (allowedProviders != null) {
            return allowedProviders;
        }
        String prop = System.getProperty("tfi.spi.allowedProviders");
        if (prop == null || prop.trim().isEmpty()) {
            return null;
        }
        String[] parts = prop.split(",");
        Set<String> set = new HashSet<>();
        for (String p : parts) {
            if (p != null) {
                String v = p.trim();
                if (!v.isEmpty()) {
                    set.add(v);
                }
            }
        }
        if (set.isEmpty()) {
            return null;
        }
        allowedProviders = Collections.unmodifiableSet(set);
        return allowedProviders;
    }

    /**
     * 判断provider是否在白名单中（未配置白名单则始终允许）。
     */
    private static boolean isAllowed(Object provider) {
        Set<String> allowed = allowedProviders != null ? allowedProviders : loadAllowedFromSystemPropertyIfAbsent();
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        String name = provider.getClass().getName();
        if (allowed.contains(name)) {
            return true;
        }
        // 包前缀匹配：前缀以".*"结尾
        for (String rule : allowed) {
            if (rule.endsWith(".*")) {
                String prefix = rule.substring(0, rule.length() - 2);
                if (name.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
