package com.syy.taskflowinsight.spi;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider Registry 的唯一可变状态与生命周期 owner。
 *
 * <p>该类型保持 package-private，避免在 {@link ProviderRegistry} 之外形成第二套公共控制面。所有状态发布
 * 都在线性化锁内完成，但 Provider 的 {@code priority()/equals()} 和 ServiceLoader 构造在锁外执行，
 * 防止第三方代码阻塞 Registry 生命周期或重入同一状态机。
 */
final class ProviderRegistryEngine {

    private static final Logger logger = LoggerFactory.getLogger(ProviderRegistry.class);
    /** 单个epoch允许追踪的Provider类型上限，防止外部类型输入使Registry状态无界增长。 */
    private static final int MAX_PROVIDER_TYPES = 64;
    /** 单类显式注册容量，保证冻结快照的选择成本有界。 */
    private static final int MAX_REGISTERED_PROVIDERS_PER_TYPE = 64;
    /** 发布冲突只做有限重试，避免Registry持续变更时调用线程活锁。 */
    private static final int MAX_PUBLICATION_ATTEMPTS = 3;
    /** 单类缓存的ClassLoader identity上限，约束动态加载环境中的缓存基数。 */
    private static final int MAX_CACHED_LOADERS_PER_TYPE = 8;
    /** 单次ServiceLoader扫描容量，限制第三方声明造成的内存与排序开销。 */
    private static final int MAX_DISCOVERED_PROVIDERS_PER_SCAN = 64;
    private static final Object NO_PROVIDER = new Object();
    private static final Object SCAN_FAILURE = new Object();
    private static final ClassLoader defaultLoader = ProviderRegistry.class.getClassLoader();
    private static final String FROZEN_MESSAGE = "Provider registry is frozen after runtime start";
    private static final String TYPE_CAPACITY_MESSAGE =
        "Provider registry supports at most 64 provider types per epoch";
    private static final String REGISTRATION_CAPACITY_MESSAGE =
        "Provider type supports at most 64 registered instances";
    private static final String LOADER_CAPACITY_MESSAGE =
        "Provider type supports at most 8 cached ClassLoader identities";
    private static final String DECLARATION_CAPACITY_MESSAGE =
        "Provider scan supports at most 64 declarations per type and ClassLoader";
    private static final String SCAN_CONFLICT_MESSAGE =
        "Provider registry changed during provider scan after 3 attempts";
    private static final String RESOLUTION_CONFLICT_MESSAGE =
        "Provider registry changed during provider resolution after 3 attempts";

    private final Object lifecycleLock = new Object();
    private final Map<Class<?>, List<Candidate>> registrations = new HashMap<>();
    private final Map<LoaderKey, List<Candidate>> serviceLoaderCache = new HashMap<>();
    private final Map<Class<?>, ClassLoader> effectiveLoaders = new HashMap<>();
    private final ResolutionCache resolvedProviders = new ResolutionCache();
    private final Set<Class<?>> knownProviderTypes = new HashSet<>();
    private Set<String> allowedProviders;
    private WhitelistState whitelistState = WhitelistState.UNSET;
    private Set<String> runtimeAllowedProviders;
    /** clearAll边界的世代标识，用于拒绝跨重置发布的扫描结果。 */
    private long registryEpoch;
    /** 同一epoch内的状态版本，用于检测锁外回调期间发生的注册变化。 */
    private long generation;
    /** 首次解析后进入冻结期，确保后续无锁读取基于稳定选择事实。 */
    private boolean runtimeStarted;

    <T> void register(Class<T> providerType, T provider) {
        if (providerType == null || provider == null) {
            logger.warn("Attempted to register null provider, ignoring");
            return;
        }

        // 先快速拒绝已冻结 Registry，避免无意义地执行第三方 priority 回调；发布前仍会二次校验。
        requireMutable();
        int priority = priorityOf(provider);
        synchronized (lifecycleLock) {
            requireMutableLocked();
            if (!isAllowedLocked(provider)) {
                logger.warn("Provider {} is not in allowed list, skip registration",
                    provider.getClass().getName());
                return;
            }
            ensureTypeCapacityLocked(providerType);
            List<Candidate> current = registrations.getOrDefault(providerType, List.of());
            if (current.size() >= MAX_REGISTERED_PROVIDERS_PER_TYPE) {
                throw new IllegalStateException(REGISTRATION_CAPACITY_MESSAGE);
            }

            List<Candidate> next = new ArrayList<>(current);
            int insertionPoint = 0;
            while (insertionPoint < next.size() && next.get(insertionPoint).priority() >= priority) {
                insertionPoint++;
            }
            next.add(insertionPoint, new Candidate(provider, Source.REGISTERED, priority,
                generation, provider.getClass().getClassLoader()));
            registrations.put(providerType, List.copyOf(next));
            knownProviderTypes.add(providerType);
            generation++;
        }
        logger.info("Registered {} provider: {} (priority={})", providerType.getSimpleName(),
            provider.getClass().getSimpleName(), priority);
    }

    <T> boolean unregister(Class<T> providerType, T provider) {
        if (providerType == null || provider == null) {
            return false;
        }

        boolean removed = false;
        while (!removed) {
            List<Candidate> snapshot;
            synchronized (lifecycleLock) {
                requireMutableLocked();
                snapshot = registrations.get(providerType);
            }
            if (snapshot == null || snapshot.isEmpty()) {
                return false;
            }

            // ArrayList.remove 的 equals 可能执行第三方代码，因此先在锁外确定匹配位置。
            int matchedIndex = matchingIndex(snapshot, provider);
            synchronized (lifecycleLock) {
                requireMutableLocked();
                if (registrations.get(providerType) != snapshot) {
                    continue;
                }
                if (matchedIndex < 0) {
                    return false;
                }
                List<Candidate> next = new ArrayList<>(snapshot);
                next.remove(matchedIndex);
                if (next.isEmpty()) {
                    registrations.remove(providerType);
                } else {
                    registrations.put(providerType, List.copyOf(next));
                }
                generation++;
            }
            removed = true;
        }
        logger.info("Unregistered {} provider: {}", providerType.getSimpleName(),
            provider.getClass().getSimpleName());
        return true;
    }

    <T> T resolve(Class<T> providerType) {
        if (providerType == null) {
            return null;
        }

        Object selected = resolvedProviders.fastSelection(providerType);
        if (selected != null) {
            return selected == NO_PROVIDER || selected == SCAN_FAILURE
                ? null : providerType.cast(selected);
        }
        selected = resolveInternal(providerType);
        return selected == NO_PROVIDER || selected == SCAN_FAILURE
            ? null : providerType.cast(selected);
    }

    private Object resolveInternal(Class<?> providerType) {
        for (int attemptNumber = 1; attemptNumber <= MAX_PUBLICATION_ATTEMPTS; attemptNumber++) {
            ResolutionAttempt attempt;
            synchronized (lifecycleLock) {
                ensureTypeCapacityLocked(providerType);
                if (!runtimeStarted) {
                    runtimeAllowedProviders = effectiveAllowedProvidersLocked();
                    runtimeStarted = true;
                }
                ClassLoader classLoader = effectiveLoaderLocked(providerType);
                LoaderKey loaderKey = loaderKey(providerType, classLoader);
                ResolutionKey resolutionKey = resolutionKey(providerType, classLoader);
                Object resolved = resolvedProviders.get(resolutionKey);
                if (resolved != null) {
                    return resolved;
                }
                List<Candidate> registered = registeredCandidatesLocked(providerType);
                List<Candidate> cached = null;
                if (registered.isEmpty()) {
                    cached = serviceLoaderCache.get(loaderKey);
                    if (cached == null) {
                        ensureLoaderCapacityLocked(providerType, classLoader);
                    }
                }
                attempt = new ResolutionAttempt(registryEpoch, generation, runtimeAllowedProviders,
                    classLoader, loaderKey, resolutionKey, registered, cached);
            }

            ScanResult scan = null;
            List<Candidate> candidates = attempt.registeredCandidates();
            if (candidates.isEmpty()) {
                candidates = attempt.cachedCandidates();
                if (candidates == null) {
                    scan = scan(providerType, attempt.classLoader(), attempt.allowed());
                    if (scan.successful()) {
                        candidates = scan.providers();
                    }
                }
            }
            Object selected = scan != null && !scan.successful()
                ? SCAN_FAILURE : selectedValue(candidates);

            synchronized (lifecycleLock) {
                if (!runtimeStarted || registryEpoch != attempt.epoch()
                    || generation != attempt.generation()
                    || effectiveLoaderLocked(providerType) != attempt.classLoader()) {
                    continue;
                }
                Object published = resolvedProviders.get(attempt.resolutionKey());
                if (published != null) {
                    return published;
                }
                if (attempt.registeredCandidates().isEmpty()
                    && attempt.cachedCandidates() == null) {
                    ensureTypeCapacityLocked(providerType);
                    ensureLoaderCapacityLocked(providerType, attempt.classLoader());
                    // 失败也保留空候选键，使对应 ClassLoader identity 继续受统一容量预算约束。
                    serviceLoaderCache.put(attempt.loaderKey(), scan.providers());
                    knownProviderTypes.add(providerType);
                }
                resolvedProviders.publish(attempt.resolutionKey(), selected);
                return selected;
            }
        }
        throw new IllegalStateException(RESOLUTION_CONFLICT_MESSAGE);
    }

    void loadProviders(ClassLoader classLoader, Class<?>... requestedTypes) {
        if (classLoader == null) {
            logger.warn("Cannot load providers with null ClassLoader");
            return;
        }
        List<Class<?>> providerTypes = distinctProviderTypes(requestedTypes);
        int loadedTypeCount = -1;
        for (int attempt = 1;
                attempt <= MAX_PUBLICATION_ATTEMPTS && loadedTypeCount < 0;
                attempt++) {
            Set<String> allowed;
            long attemptEpoch;
            long attemptGeneration;
            synchronized (lifecycleLock) {
                requireMutableLocked();
                for (Class<?> providerType : providerTypes) {
                    ensureLoaderCapacityLocked(providerType, classLoader);
                }
                allowed = effectiveAllowedProvidersLocked();
                attemptEpoch = registryEpoch;
                attemptGeneration = generation;
            }

            Map<LoaderKey, List<Candidate>> discovered = new LinkedHashMap<>();
            Map<Class<?>, ClassLoader> effectiveUpdates = new LinkedHashMap<>();
            for (Class<?> providerType : providerTypes) {
                ScanResult scan = scan(providerType, classLoader, allowed);
                if (!scan.successful()) {
                    return;
                }
                if (!scan.providers().isEmpty()) {
                    discovered.put(new LoaderKey(attemptEpoch, providerType,
                        new LoaderIdentity(classLoader)), scan.providers());
                    effectiveUpdates.put(providerType, classLoader);
                }
            }

            synchronized (lifecycleLock) {
                requireMutableLocked();
                if (registryEpoch != attemptEpoch || generation != attemptGeneration) {
                    continue;
                }
                ensureTypeCapacityLocked(effectiveUpdates.keySet().toArray(Class<?>[]::new));
                for (Class<?> providerType : effectiveUpdates.keySet()) {
                    ensureLoaderCapacityLocked(providerType, classLoader);
                }
                if (discovered.isEmpty()) {
                    return;
                }
                serviceLoaderCache.putAll(discovered);
                effectiveLoaders.putAll(effectiveUpdates);
                knownProviderTypes.addAll(effectiveUpdates.keySet());
                generation++;
                loadedTypeCount = discovered.size();
            }
        }
        if (loadedTypeCount < 0) {
            throw new IllegalStateException(SCAN_CONFLICT_MESSAGE);
        }
        logger.info("Loaded {} provider types from custom ClassLoader: {}", loadedTypeCount,
            classLoader.getClass().getName());
    }

    private static List<Class<?>> distinctProviderTypes(Class<?>[] requestedTypes) {
        Class<?>[] providerTypes = requestedTypes == null || requestedTypes.length == 0
            ? new Class<?>[] {FlowProvider.class, ExportProvider.class}
            : requestedTypes.clone();
        Set<Class<?>> distinct = new LinkedHashSet<>();
        for (Class<?> providerType : providerTypes) {
            if (providerType != null) {
                distinct.add(providerType);
            }
        }
        return List.copyOf(distinct);
    }

    void clearAll() {
        synchronized (lifecycleLock) {
            registryEpoch++;
            generation++;
            knownProviderTypes.clear();
            registrations.clear();
            serviceLoaderCache.clear();
            effectiveLoaders.clear();
            resolvedProviders.clear();
            runtimeAllowedProviders = null;
            runtimeStarted = false;
        }
        logger.info("Cleared all registered providers");
    }

    long generation() {
        synchronized (lifecycleLock) {
            return generation;
        }
    }

    Map<Class<?>, List<Object>> registeredProviders() {
        synchronized (lifecycleLock) {
            Map<Class<?>, List<Object>> snapshot = new LinkedHashMap<>();
            registrations.forEach((type, entries) -> snapshot.put(type,
                entries.stream().map(Candidate::provider).toList()));
            return Collections.unmodifiableMap(snapshot);
        }
    }

    void setAllowedProviders(Collection<String> allowed) {
        Set<String> normalized = normalizeAllowedProviders(allowed);
        List<String> removedProviderTypes = new ArrayList<>();
        synchronized (lifecycleLock) {
            requireMutableLocked();
            allowedProviders = normalized;
            whitelistState = normalized == null ? WhitelistState.DISABLED : WhitelistState.ENABLED;
            serviceLoaderCache.clear();

            Iterator<Map.Entry<Class<?>, List<Candidate>>> entries = registrations.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<Class<?>, List<Candidate>> entry = entries.next();
                List<Candidate> kept = entry.getValue().stream()
                    .filter(candidate -> isAllowed(candidate.provider(), normalized))
                    .toList();
                if (kept.size() == entry.getValue().size()) {
                    continue;
                }
                entry.getValue().stream()
                    .filter(candidate -> !isAllowed(candidate.provider(), normalized))
                    .map(candidate -> candidate.provider().getClass().getName())
                    .forEach(removedProviderTypes::add);
                if (kept.isEmpty()) {
                    entries.remove();
                } else {
                    entry.setValue(List.copyOf(kept));
                }
            }
            generation++;
        }
        removedProviderTypes.forEach(type ->
            logger.warn("Removing registered provider no longer in allowed list: {}", type));
    }

    private ScanResult scan(Class<?> providerType, ClassLoader classLoader, Set<String> allowed) {
        List<ServiceLoader.Provider<?>> declarations = scanDeclarations(providerType, classLoader);
        if (declarations == null) {
            return new ScanResult(List.of(), false);
        }

        List<ServiceLoader.Provider<?>> eligible = new ArrayList<>();
        for (ServiceLoader.Provider<?> declaration : declarations) {
            Class<?> implementationType;
            try {
                implementationType = declaration.type();
            } catch (Throwable failure) {
                logScanFailure(providerType, failure);
                return new ScanResult(List.of(), false);
            }
            if (!isAllowedName(implementationType.getName(), allowed)) {
                logger.warn("Filtered out disallowed provider from ServiceLoader: {}",
                    implementationType.getName());
                continue;
            }
            eligible.add(declaration);
        }

        List<Candidate> discovered = new ArrayList<>();
        for (ServiceLoader.Provider<?> declaration : eligible) {
            Object provider;
            try {
                provider = declaration.get();
            } catch (Throwable failure) {
                logScanFailure(providerType, failure);
                return new ScanResult(List.of(), false);
            }
            int priority = priorityOf(provider);
            discovered.add(new Candidate(provider, Source.SERVICE_LOADER, priority,
                discovered.size(), classLoader));
            logger.debug("Discovered {} provider from ServiceLoader: {} (priority={})",
                providerType.getSimpleName(), provider.getClass().getSimpleName(), priority);
        }
        discovered.sort((left, right) -> {
            int priority = Integer.compare(right.priority(), left.priority());
            return priority != 0 ? priority : Long.compare(left.order(), right.order());
        });
        return new ScanResult(discovered, true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<ServiceLoader.Provider<?>> scanDeclarations(Class<?> providerType,
                                                              ClassLoader classLoader) {
        Iterator<? extends ServiceLoader.Provider<?>> iterator;
        try {
            ServiceLoader<?> loader = ServiceLoader.load((Class) providerType, classLoader);
            iterator = loader.stream().iterator();
        } catch (Throwable failure) {
            logScanFailure(providerType, failure);
            return null;
        }

        List<ServiceLoader.Provider<?>> declarations = new ArrayList<>();
        for (int declarationIndex = 0;
                declarationIndex <= MAX_DISCOVERED_PROVIDERS_PER_SCAN;
                declarationIndex++) {
            boolean hasNext;
            try {
                hasNext = iterator.hasNext();
            } catch (Throwable failure) {
                logScanFailure(providerType, failure);
                return null;
            }
            if (!hasNext) {
                return declarations;
            }
            // 第 65 个声明只确认存在即拒绝，不解析类型或构造任何 Provider。
            if (declarationIndex == MAX_DISCOVERED_PROVIDERS_PER_SCAN) {
                throw new IllegalStateException(DECLARATION_CAPACITY_MESSAGE);
            }
            try {
                declarations.add(iterator.next());
            } catch (Throwable failure) {
                logScanFailure(providerType, failure);
                return null;
            }
        }
        return declarations;
    }

    private static void logScanFailure(Class<?> providerType, Throwable failure) {
        logger.error("ServiceLoader failed while loading {} with {}", providerType.getSimpleName(),
            failure.getClass().getName());
    }

    private List<Candidate> registeredCandidatesLocked(Class<?> providerType) {
        List<Candidate> candidates = registrations.get(providerType);
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream()
            .filter(candidate -> isAllowed(candidate.provider(), runtimeAllowedProviders))
            .toList();
    }

    private static Object selectedValue(List<Candidate> providers) {
        return providers.isEmpty() ? NO_PROVIDER : providers.get(0).provider();
    }

    private static int matchingIndex(List<Candidate> providers, Object provider) {
        for (int index = 0; index < providers.size(); index++) {
            if (provider.equals(providers.get(index).provider())) {
                return index;
            }
        }
        return -1;
    }

    private static int priorityOf(Object provider) {
        if (!(provider instanceof PrioritizedProvider prioritizedProvider)) {
            return 0;
        }
        try {
            return prioritizedProvider.priority();
        } catch (Throwable failure) {
            logger.warn("Provider {} priority() failed with {}; using default 0",
                provider.getClass().getName(), failure.getClass().getName());
            return 0;
        }
    }

    private Set<String> effectiveAllowedProvidersLocked() {
        if (whitelistState == WhitelistState.ENABLED) {
            return allowedProviders;
        }
        if (whitelistState == WhitelistState.DISABLED) {
            return null;
        }
        // 未经 API 显式配置时才读取外部属性；首次 lookup 会把结果复制进 runtime 快照冻结本 epoch。
        return parseAllowedProviders(System.getProperty("tfi.spi.allowedProviders"));
    }

    private boolean isAllowedLocked(Object provider) {
        return isAllowed(provider, effectiveAllowedProvidersLocked());
    }

    private static boolean isAllowed(Object provider, Set<String> allowed) {
        return isAllowedName(provider.getClass().getName(), allowed);
    }

    private static boolean isAllowedName(String className, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        for (String rule : allowed) {
            if (rule != null && matchesAllowedName(rule, className)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAllowedName(String allowed, String className) {
        if (allowed.endsWith(".*")) {
            String packageName = allowed.substring(0, allowed.length() - 2);
            return className.startsWith(packageName + ".");
        }
        return className.equals(allowed);
    }

    private static Set<String> normalizeAllowedProviders(Collection<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(allowed));
    }

    private static Set<String> parseAllowedProviders(String property) {
        if (property == null || property.isBlank()) {
            return null;
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String part : property.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                parsed.add(value);
            }
        }
        return parsed.isEmpty() ? null : Collections.unmodifiableSet(parsed);
    }

    private void requireMutable() {
        synchronized (lifecycleLock) {
            requireMutableLocked();
        }
    }

    private void requireMutableLocked() {
        if (runtimeStarted) {
            throw new IllegalStateException(FROZEN_MESSAGE);
        }
    }

    private void ensureTypeCapacityLocked(Class<?> providerType) {
        if (!knownProviderTypes.contains(providerType) && knownProviderTypes.size() >= MAX_PROVIDER_TYPES) {
            throw new IllegalStateException(TYPE_CAPACITY_MESSAGE);
        }
    }

    private void ensureTypeCapacityLocked(Class<?>[] providerTypes) {
        Set<Class<?>> newTypes = new HashSet<>();
        for (Class<?> providerType : providerTypes) {
            if (providerType != null && !knownProviderTypes.contains(providerType)) {
                newTypes.add(providerType);
            }
        }
        if (knownProviderTypes.size() + newTypes.size() > MAX_PROVIDER_TYPES) {
            throw new IllegalStateException(TYPE_CAPACITY_MESSAGE);
        }
    }

    private void ensureLoaderCapacityLocked(Class<?> providerType, ClassLoader classLoader) {
        LoaderKey requestedKey = loaderKey(providerType, classLoader);
        if (serviceLoaderCache.containsKey(requestedKey)) {
            return;
        }
        long loaderCount = serviceLoaderCache.keySet().stream()
            .filter(key -> key.epoch() == registryEpoch && key.providerType() == providerType)
            .count();
        if (loaderCount >= MAX_CACHED_LOADERS_PER_TYPE) {
            throw new IllegalStateException(LOADER_CAPACITY_MESSAGE);
        }
    }

    private ClassLoader effectiveLoaderLocked(Class<?> providerType) {
        return effectiveLoaders.getOrDefault(providerType, defaultLoader);
    }

    private LoaderKey loaderKey(Class<?> providerType, ClassLoader classLoader) {
        return new LoaderKey(registryEpoch, providerType, new LoaderIdentity(classLoader));
    }

    private ResolutionKey resolutionKey(Class<?> providerType, ClassLoader classLoader) {
        return new ResolutionKey(registryEpoch, providerType, new LoaderIdentity(classLoader));
    }

    private static final class LoaderIdentity {
        private final ClassLoader value;

        private LoaderIdentity(ClassLoader value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LoaderIdentity identity && value == identity.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }

    private record LoaderKey(long epoch, Class<?> providerType, LoaderIdentity loaderIdentity) {
    }

    private record ResolutionKey(long epoch, Class<?> providerType,
                                 LoaderIdentity loaderIdentity) {
    }

    /**
     * Registry唯一的selected cache，同时发布锁内事实与无锁只读快照。
     *
     * <p>首次解析仍由生命周期锁校验epoch、generation和ClassLoader；运行期冻结后这些键不再变化，
     * 因此可按provider type读取不可变快照。这样避免每次facade调用竞争生命周期锁，又不在facade形成第二owner。</p>
     */
    private static final class ResolutionCache extends AbstractMap<ResolutionKey, Object> {

        /** 仅在生命周期锁内修改的完整epoch键事实。 */
        private final Map<ResolutionKey, Object> entries = new HashMap<>();
        /** 每次锁内发布后整体替换的运行期只读选择视图。 */
        private volatile Map<Class<?>, Object> fastSelections = Map.of();

        private Object fastSelection(Class<?> providerType) {
            return fastSelections.get(providerType);
        }

        private void publish(ResolutionKey key, Object provider) {
            entries.put(key, provider);
            Map<Class<?>, Object> next = new HashMap<>(fastSelections);
            next.put(key.providerType(), provider);
            fastSelections = Map.copyOf(next);
        }

        @Override
        public Set<Entry<ResolutionKey, Object>> entrySet() {
            return entries.entrySet();
        }

        @Override
        public Object get(Object key) {
            return entries.get(key);
        }

        @Override
        public void clear() {
            entries.clear();
            fastSelections = Map.of();
        }
    }

    private record Candidate(Object provider, Source source, int priority, long order,
                             ClassLoader classLoader) {
    }

    private record ScanResult(List<Candidate> providers, boolean successful) {
        private ScanResult {
            providers = List.copyOf(providers);
        }
    }

    private record ResolutionAttempt(long epoch, long generation, Set<String> allowed,
                                     ClassLoader classLoader, LoaderKey loaderKey,
                                     ResolutionKey resolutionKey,
                                     List<Candidate> registeredCandidates,
                                     List<Candidate> cachedCandidates) {
    }

    private enum Source {
        /** 调用方在冻结前显式注册，优先级事实已在锁外读取。 */
        REGISTERED,
        /** 由指定ClassLoader的ServiceLoader声明发现。 */
        SERVICE_LOADER
    }

    private enum WhitelistState {
        /** 调用方尚未决定白名单策略，允许回退到系统配置。 */
        UNSET,
        /** 调用方显式关闭白名单约束，不再读取系统配置。 */
        DISABLED,
        /** 调用方显式提供白名单，冻结时保存其不可变快照。 */
        ENABLED
    }
}
