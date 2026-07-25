package com.syy.taskflowinsight.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Provider 注册、发现与选择的唯一公共入口。
 *
 * <p>公共类型只保留稳定 API，全部可变状态由唯一的 package-private {@link ProviderRegistryEngine}
 * 持有。候选严格分为注册与 {@link ServiceLoader} 两个来源：注册来源存在有效候选时直接返回，每个来源
 * 内部按 {@link PrioritizedProvider#priority()} 降序选择，同优先级保持 FIFO。
 *
 * <p>首次非 null lookup 标志运行期开始；此后注册、注销、白名单和显式 ClassLoader 加载都会被拒绝。
 * 该冻结边界让一次运行期只观察一个 Provider 配置世代，避免 facade cache 与 Registry mutation 交叉。
 *
 * <p>线程安全：所有 public 方法均可并发调用；{@link #clearAll()} 只允许在外部 Session/Task scope
 * 已静默时执行，因为 freeze 分支没有跨 epoch 的 Provider lease。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class ProviderRegistry {

    private static final ProviderRegistryEngine engine = new ProviderRegistryEngine();

    /**
     * 创建无状态 Registry 门面实例。
     *
     * <p>实例不持有任何 Registry 状态；保留该构造器仅用于维持既有公共表面。</p>
     */
    public ProviderRegistry() {
    }

    /**
     * 在运行期开始前注册 Provider。
     *
     * @param providerType Provider 接口类型；null 时保持历史静默语义
     * @param provider Provider 实例；null 时保持历史静默语义
     * @param <T> Provider 类型
     * @throws IllegalStateException Registry 已进入运行期，或类型/实例容量达到上限
     */
    public static <T> void register(Class<T> providerType, T provider) {
        engine.register(providerType, provider);
    }

    /**
     * 在运行期开始前注销指定 Provider 实例。
     *
     * @param providerType Provider 接口类型；null 时返回 false
     * @param provider Provider 实例；null 时返回 false
     * @param <T> Provider 类型
     * @return 找到并移除实例时返回 true，否则返回 false
     * @throws IllegalStateException Registry 已进入运行期
     */
    public static <T> boolean unregister(Class<T> providerType, T provider) {
        return engine.unregister(providerType, provider);
    }

    /**
     * 按来源优先、来源内 priority/FIFO 规则解析类型化 Provider。
     *
     * <p>首个非 null 调用会冻结本 epoch 的配置；Provider 或空结果都由 Registry 缓存为本 epoch 的唯一选择。</p>
     *
     * @param providerType 实现类型化优先级契约的 Provider 接口；null 时返回 null 且不启动运行期
     * @param <T> Provider 类型
     * @return 本 epoch 选中的唯一 Provider；没有有效候选时返回 null
     * @throws IllegalStateException 类型、ClassLoader 或声明容量达到上限，或三次并发发布均冲突
     * @since 4.0.0
     */
    public static <T extends PrioritizedProvider> T resolve(Class<T> providerType) {
        return engine.resolve(providerType);
    }

    /**
     * 按来源优先、来源内 priority/FIFO 规则解析任意兼容 Provider 类型。
     *
     * @param providerType Provider 接口类型；null 时返回 null 且不启动运行期
     * @param <T> Provider 类型
     * @return 本 epoch 选中的唯一 Provider；没有有效候选时返回 null
     * @throws IllegalStateException 类型、ClassLoader 或声明容量达到上限，或三次并发发布均冲突
     * @deprecated 从 4.0.0 起，类型化 SPI 请使用 {@link #resolve(Class)}；泛型兼容调用仍共享同一选择缓存
     */
    @Deprecated(since = "4.0.0")
    public static <T> T lookup(Class<T> providerType) {
        return engine.resolve(providerType);
    }

    /**
     * 在运行期开始前使用指定 ClassLoader 预加载 Provider。
     *
     * <p>扫描和 Provider 构造在生命周期锁外完成，只有完整成功的非空批次才会原子发布。每个类型最后一次
     * 成功的非空加载会成为其 effective ClassLoader；空扫描不会替换已有 loader。</p>
     *
     * @param classLoader 自定义 ClassLoader；null 时保持历史静默语义
     * @param providerTypes 要加载的类型；null/空数组默认加载 Flow 与 Export
     * @throws IllegalStateException Registry 已进入运行期，类型/ClassLoader/声明容量达到上限，或三次并发发布均冲突
     */
    @SafeVarargs
    public static void loadProviders(ClassLoader classLoader, Class<?>... providerTypes) {
        engine.loadProviders(classLoader, providerTypes);
    }

    /**
     * 在外部静默条件下开启新的 Registry epoch。
     *
     * <p>该操作原子清除注册、发现和类型预算，重开启动期并各推进一次 epoch/generation；已配置白名单
     * 会保留。调用方必须先关闭所有 Session/Task scope，本 API 不提供 active-scope live reset。</p>
     */
    public static void clearAll() {
        engine.clearAll();
    }

    /**
     * 返回 Registry 配置世代。
     *
     * @return 单调递增的 generation
     */
    public static long getGeneration() {
        return engine.generation();
    }

    /**
     * 返回当前注册实例的只读快照。
     *
     * @return key 与每个实例列表均不可修改的快照
     */
    public static Map<Class<?>, List<Object>> getAllRegistered() {
        return engine.registeredProviders();
    }

    /**
     * 在运行期开始前配置 Provider 白名单。
     *
     * <p>精确类名和以 {@code .*} 结尾的包前缀受支持；null/空集合显式禁用白名单并覆盖同名系统属性。
     * 变更会清除发现缓存并剔除不再允许的已注册实例，确保 trust 配置与后续首次 resolution 属于同一世代。</p>
     *
     * @param allowed 允许的实现类全名或包前缀；null/空集合表示禁用
     * @throws IllegalStateException Registry 已进入运行期
     */
    public static void setAllowedProviders(Collection<String> allowed) {
        engine.setAllowedProviders(allowed);
    }
}
