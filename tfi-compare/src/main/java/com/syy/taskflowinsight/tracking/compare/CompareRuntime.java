package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.list.SimpleListStrategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 冻结一次比较对象图的不可变运行时。
 *
 * <p>Builder是policy、strategy、property comparator与engine的唯一组装边界。runtime只发布同一个
 * engine实例，不提供在线注册；配置变化必须构造新runtime，避免并发请求观察到半更新registry。</p>
 *
 * @since 4.0.0
 */
public final class CompareRuntime {

    /** 延迟初始化默认图，避免未使用比较能力时提前构造策略对象。 */
    private static final class DefaultHolder {
        /** 静态入口、SPI与兼容Service共同复用的唯一默认冻结图。 */
        private static final CompareRuntime INSTANCE = CompareRuntime.builder().build();
    }

    /** 当前对象图唯一的语义与资源边界。 */
    private final ComparePolicy policy;
    /** 当前对象图唯一的比较执行入口。 */
    private final CompareEngine engine;
    /** exact target class对应的冻结strategy注册。 */
    private final Map<Class<?>, StrategyRegistration<?>> strategies;
    /** exact declared field对应的冻结property comparator注册。 */
    private final Map<PropertySelector, ComparatorRegistration> comparators;

    private CompareRuntime(
            ComparePolicy policy,
            CompareEngine engine,
            Map<Class<?>, StrategyRegistration<?>> strategies,
            Map<PropertySelector, ComparatorRegistration> comparators) {
        this.policy = policy;
        this.engine = engine;
        this.strategies = Map.copyOf(strategies);
        this.comparators = Map.copyOf(comparators);
    }

    /**
     * 创建尚未冻结的对象图构造器。
     *
     * @return 默认绑定{@link ComparePolicy#defaults()}的builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回纯Java默认冻结图。
     *
     * <p>所有无显式装配的入口共享该实例，避免Service、Provider与facade各自建立第二套策略图。</p>
     *
     * @return 进程内共享的不可变runtime
     */
    public static CompareRuntime defaults() {
        return DefaultHolder.INSTANCE;
    }

    /**
     * 获取当前 runtime 的唯一 policy。
     *
     * @return 构造 runtime 时冻结的 policy
     */
    public ComparePolicy policy() {
        return policy;
    }

    /**
     * 获取当前 runtime 的唯一 engine 实例。
     *
     * @return 与该 runtime 同生命周期的 engine
     */
    public CompareEngine engine() {
        return engine;
    }

    Map<PropertySelector, ComparatorRegistration> comparators() {
        return comparators;
    }

    /** 对象图冻结前的唯一可变注册入口。 */
    public static final class Builder {

        /** 待冻结的policy，默认来自唯一纯Java默认值源。 */
        private ComparePolicy policy = ComparePolicy.defaults();
        /** 按注册顺序暂存的strategy事实；build时统一检查重复。 */
        private final List<StrategyRegistration<?>> strategies = new ArrayList<>();
        /** 按注册顺序暂存的comparator事实；build时统一检查重复。 */
        private final List<ComparatorRegistration> comparators = new ArrayList<>();
        /** 成功build后置true，阻止继续修改同一个builder。 */
        private boolean built;
        /** 兼容Spring装配的列表执行器也必须在runtime冻结时确定。 */
        private ListCompareExecutor listCompareExecutor = createDefaultListExecutor();

        /**
         * 指定整个runtime共享的policy。
         *
         * @param value 已验证的不可变policy，不能为空
         * @return 当前builder
         */
        public Builder policy(ComparePolicy value) {
            ensureOpen();
            if (value == null) {
                throw new CompareInputException(InputViolation.INVALID_INPUT_SHAPE);
            }
            policy = value;
            return this;
        }

        /**
         * 注册exact target-class strategy；选择后失败不得fallback。
         *
         * @param targetClass exact运行时类型
         * @param algorithmId 版本化算法身份
         * @param strategy 线程安全、确定且nonblocking的策略
         * @param <T> 目标类型
         * @return 当前builder
         */
        public <T> Builder registerStrategy(
                Class<T> targetClass,
                AlgorithmId algorithmId,
                CompareStrategy<T> strategy) {
            ensureOpen();
            strategies.add(new StrategyRegistration<>(
                    Objects.requireNonNull(targetClass, "targetClass"),
                    Objects.requireNonNull(algorithmId, "algorithmId"),
                    Objects.requireNonNull(strategy, "strategy")));
            return this;
        }

        /**
         * 注册exact declared-field comparator；selector与ID共同进入runtime fingerprint事实。
         *
         * @param selector exact字段selector
         * @param algorithmId 版本化算法身份
         * @param comparator 线程安全、确定且nonblocking的字段比较器
         * @return 当前builder
         */
        public Builder registerComparator(
                PropertySelector selector,
                AlgorithmId algorithmId,
                PropertyComparator comparator) {
            ensureOpen();
            comparators.add(new ComparatorRegistration(
                    Objects.requireNonNull(selector, "selector"),
                    Objects.requireNonNull(algorithmId, "algorithmId"),
                    Objects.requireNonNull(comparator, "comparator")));
            return this;
        }

        Builder listCompareExecutor(ListCompareExecutor value) {
            ensureOpen();
            if (value == null) {
                throw new CompareInputException(InputViolation.INVALID_INPUT_SHAPE);
            }
            listCompareExecutor = value;
            return this;
        }

        /**
         * 校验全部注册事实并一次性冻结对象图。
         *
         * <p>重复检查延迟到冻结点，保证配置来源可以先独立收集，再得到一次确定、完整的失败反馈。
         * 只有成功构造runtime后才关闭builder；失败以typed异常返回且不会发布半成品对象图。</p>
         *
         * @return 只读runtime
         */
        public CompareRuntime build() {
            ensureOpen();
            if (strategies.size() + comparators.size() > policy.maxRegisteredExtensions()) {
                throw new CompareInputException(InputViolation.EXTENSION_LIMIT_EXCEEDED);
            }

            Set<AlgorithmId> algorithmIds = new HashSet<>();
            Map<Class<?>, StrategyRegistration<?>> strategiesByTarget = new LinkedHashMap<>();
            Map<PropertySelector, ComparatorRegistration> comparatorsBySelector = new LinkedHashMap<>();
            List<CompareSemanticFingerprint.ExtensionFact> fingerprintExtensions = new ArrayList<>();

            for (StrategyRegistration<?> registration : strategies) {
                if (!algorithmIds.add(registration.algorithmId)
                        || strategiesByTarget.putIfAbsent(registration.targetClass, registration) != null) {
                    throw new CompareInputException(InputViolation.DUPLICATE_EXTENSION);
                }
                fingerprintExtensions.add(CompareSemanticFingerprint.ExtensionFact.strategy(
                        registration.targetClass, registration.algorithmId));
            }
            for (ComparatorRegistration registration : comparators) {
                if (!algorithmIds.add(registration.algorithmId)
                        || comparatorsBySelector.putIfAbsent(registration.selector, registration) != null) {
                    throw new CompareInputException(InputViolation.DUPLICATE_EXTENSION);
                }
                fingerprintExtensions.add(CompareSemanticFingerprint.ExtensionFact.comparator(
                        registration.selector, registration.algorithmId));
            }

            Map<Class<?>, CompareStrategy<?>> strategyImplementations = builtInStrategies();
            strategiesByTarget.forEach((target, registration) ->
                    strategyImplementations.put(target, registration.strategy));
            Map<PropertySelector, PropertyComparator> comparatorImplementations = new LinkedHashMap<>();
            comparatorsBySelector.forEach((selector, registration) ->
                    comparatorImplementations.put(selector, registration.comparator));
            CompareEngine engine = new CompareEngine(
                    policy,
                    listCompareExecutor,
                    Map.copyOf(strategyImplementations),
                    Map.copyOf(comparatorImplementations),
                    Set.copyOf(strategiesByTarget.keySet()),
                    List.copyOf(fingerprintExtensions));

            CompareRuntime runtime = new CompareRuntime(
                    policy,
                    engine,
                    strategiesByTarget,
                    comparatorsBySelector);
            built = true;
            return runtime;
        }

        private void ensureOpen() {
            if (built) {
                throw new CompareInputException(InputViolation.INVALID_INPUT_SHAPE);
            }
        }

        private static ListCompareExecutor createDefaultListExecutor() {
            return new ListCompareExecutor(List.of(
                    new SimpleListStrategy(),
                    new EntityListStrategy()));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Map<Class<?>, CompareStrategy<?>> builtInStrategies() {
            Map<Class<?>, CompareStrategy<?>> builtIns = new LinkedHashMap<>();
            builtIns.put(Set.class, new SetCompareStrategy());
            builtIns.put(Collection.class, new CollectionCompareStrategy());
            builtIns.put(Map.class, new MapCompareStrategy());
            builtIns.put(Object[].class, new ArrayCompareStrategy());
            return builtIns;
        }
    }

    /** 冻结前保留strategy身份与实现的完整注册事实。 */
    private static final class StrategyRegistration<T> {

        /** exact路由键；禁止按父类或接口做隐式扩散。 */
        private final Class<T> targetClass;
        /** 跨extension类别参与全局去重的版本化身份。 */
        private final AlgorithmId algorithmId;
        /** 构图后由唯一engine持有的线程安全实现。 */
        private final CompareStrategy<T> strategy;

        private StrategyRegistration(
                Class<T> targetClass,
                AlgorithmId algorithmId,
                CompareStrategy<T> strategy) {
            this.targetClass = targetClass;
            this.algorithmId = algorithmId;
            this.strategy = strategy;
        }
    }

    /** 冻结前保留字段selector、算法身份与实现的完整注册事实。 */
    static final class ComparatorRegistration {

        /** exact declared-field路由键，避免继承层级导致选择漂移。 */
        private final PropertySelector selector;
        /** 与strategy共享全局唯一空间的版本化身份。 */
        private final AlgorithmId algorithmId;
        /** 后续编译不可变比较计划时使用的字段比较实现。 */
        private final PropertyComparator comparator;

        private ComparatorRegistration(
                PropertySelector selector,
                AlgorithmId algorithmId,
                PropertyComparator comparator) {
            this.selector = selector;
            this.algorithmId = algorithmId;
            this.comparator = comparator;
        }
    }
}
