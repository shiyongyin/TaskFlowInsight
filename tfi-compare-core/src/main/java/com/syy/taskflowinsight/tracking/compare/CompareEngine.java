package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.TrackingBatchScope;
import com.syy.taskflowinsight.tracking.TrackingExecutor;
import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;
import com.syy.taskflowinsight.tracking.compare.internal.CompareResultReducer;
import com.syy.taskflowinsight.tracking.compare.internal.RequestLocalCompareKernel;
import com.syy.taskflowinsight.tracking.determinism.StableSorter;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 冻结运行时对象图中的比较执行引擎。
 * <p>
 * 轻量级执行引擎，负责：
 * 1. 快速路径检查（相同引用、null、类型不匹配）
 * 2. 特殊路由（List → ListCompareExecutor）
 * 3. 策略解析与执行
 * 4. 结果排序（唯一调用 StableSorter.sortByFieldChange 的位置）
 * 5. 发布 canonical 结果
 * </p>
 * <p>
 * 该类型只能由{@link CompareRuntime.Builder}装配。策略选择只读取冻结映射并按固定优先级计算，
 * 不保存解析缓存，避免共享runtime在并发执行期间产生可变状态。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @version 3.0.0-M2
 * @since 2025-10-04
 */
public final class CompareEngine implements CompareOperations {

    private static final Logger logger = LoggerFactory.getLogger(CompareEngine.class);

    /** 当前引擎唯一的语义默认值与资源边界。 */
    private final ComparePolicy policy;
    /** KRN-02删除旧选择态前保留的冻结兼容引用；direct built-in List不再委托它。 */
    private final ListCompareExecutor listCompareExecutor;
    /** exact扩展与内建类型对应的冻结策略表。 */
    private final Map<Class<?>, CompareStrategy<?>> customStrategies;
    /** exact声明字段对应的冻结比较器；执行失败不得回落到默认equals语义。 */
    private final Map<PropertySelector, PropertyComparator> propertyComparators;
    /** 用户显式注册的exact target闭集，用于区分custom callback与built-in兼容路由。 */
    private final Set<Class<?>> customStrategyTargets;
    /** runtime冻结的语义摘要计划；每次调用只叠加已验证Options。 */
    private final CompareSemanticFingerprint semanticFingerprint;

    /** 仅允许runtime冻结过程创建Engine，防止Service或Provider形成第二套执行图。 */
    CompareEngine(ComparePolicy policy,
                  ListCompareExecutor listCompareExecutor,
                  Map<Class<?>, CompareStrategy<?>> customStrategies,
                  Map<PropertySelector, PropertyComparator> propertyComparators) {
        this(
                policy,
                listCompareExecutor,
                customStrategies,
                propertyComparators,
                Set.of());
    }

    /** Runtime冻结时额外传入custom target事实，执行期只读且不做策略猜测。 */
    CompareEngine(ComparePolicy policy,
                  ListCompareExecutor listCompareExecutor,
                  Map<Class<?>, CompareStrategy<?>> customStrategies,
                  Map<PropertySelector, PropertyComparator> propertyComparators,
                  Set<Class<?>> customStrategyTargets) {
        this(
                policy,
                listCompareExecutor,
                customStrategies,
                propertyComparators,
                customStrategyTargets,
                List.of());
    }

    /** Runtime冻结时同时传入extension身份，避免从实现类名或实例hash推导语义。 */
    CompareEngine(ComparePolicy policy,
                  ListCompareExecutor listCompareExecutor,
                  Map<Class<?>, CompareStrategy<?>> customStrategies,
                  Map<PropertySelector, PropertyComparator> propertyComparators,
                  Set<Class<?>> customStrategyTargets,
                  List<CompareSemanticFingerprint.ExtensionFact> fingerprintExtensions) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.listCompareExecutor = listCompareExecutor;
        this.customStrategies = customStrategies != null ? Map.copyOf(customStrategies) : Map.of();
        this.propertyComparators = propertyComparators != null ? Map.copyOf(propertyComparators) : Map.of();
        this.customStrategyTargets = customStrategyTargets != null
                ? Set.copyOf(customStrategyTargets) : Set.of();
        this.semanticFingerprint = new CompareSemanticFingerprint(
                policy, fingerprintExtensions != null ? fingerprintExtensions : List.of());

        logger.debug("CompareEngine initialized with ListExecutor={}",
                listCompareExecutor != null ? "available" : "null");
    }

    /**
     * 使用当前runtime policy默认值执行比较。
     *
     * @param before 变更前对象，可为null
     * @param after 变更后对象，可为null
     * @return canonical比较结果
     */
    @Override
    public CompareResult compare(Object before, Object after) {
        return compare(before, after, CompareOptions.defaults(policy));
    }

    /**
     * 唯一显式options执行入口。
     *
     * <p>校验和disabled必须位于identity之前，否则非法调用或禁用比较会被同引用快速路径伪装成相等。</p>
     *
     * @param before 变更前对象，可为null
     * @param after 变更后对象，可为null
     * @param options 当前调用的不可变选项，不可为null
     * @return canonical比较结果
     */
    @Override
    public CompareResult compare(Object before, Object after, CompareOptions options) {
        if (options == null) {
            throw new CompareInputException(InputViolation.NULL_OPTIONS);
        }
        options.validateAgainst(policy);
        String fingerprint = semanticFingerprint.forOptions(options);
        if (!policy.enabled()) {
            return withFingerprint(CompareResultReducer.disabled(), fingerprint);
        }
        return withFingerprint(executeValidated(before, after, options), fingerprint);
    }

    /**
     * 为内置TrackingProvider建立共享phase预算的batch scope。
     *
     * <p>该入口只承接canonical snapshot/diff能力；业务action仍只存在于final TrackingExecutor。</p>
     *
     * @param targets 已由executor完整校验的有序目标
     * @param options 必须在当前runtime policy内的不可变选项
     * @return 线程封闭的batch scope
     */
    public TrackingBatchScope beginTracking(
            List<TrackingExecutor.Target> targets,
            CompareOptions options) {
        if (options == null) {
            throw new CompareInputException(InputViolation.NULL_OPTIONS);
        }
        options.validateAgainst(policy);
        String fingerprint = semanticFingerprint.forOptions(options);
        // runtime关闭优先于单次请求；启用时使用已验证的request policy落实typed path收紧。
        ComparePolicy effectivePolicy = policy.enabled() ? options.getPolicy() : policy;
        return RequestLocalCompareKernel.openTrackingBatch(
                List.copyOf(targets),
                options,
                effectivePolicy,
                result -> withFingerprint(result, fingerprint));
    }

    /**
     * 执行比较（M2 统一入口，唯一排序点）
     *
     * @param a 第一个对象
     * @param b 第二个对象
     * @param opts 比较选项
     * @return 比较结果
     */
    @SuppressWarnings("unchecked")
    public CompareResult execute(Object a, Object b, CompareOptions opts) {
        return compare(a, b, opts);
    }

    @SuppressWarnings("unchecked")
    private CompareResult executeValidated(Object a, Object b, CompareOptions opts) {
        try {
            // 快速路径检查
            if (a == b) {
                return CompareResult.identical(opts.computeSimilarity());
            }

            if (a == null || b == null) {
                return CompareResult.ofNullDiff(a, b);
            }

            if (!a.getClass().equals(b.getClass())) {
                return CompareResult.ofTypeDiff(a, b);
            }

            CompareStrategy strategy = resolveStrategy(a.getClass());
            if (strategy != null && isCustomStrategyTarget(a.getClass())) {
                CompareResult result = RequestLocalCompareKernel.executeDiff(
                        opts,
                        () -> strategy.compare(a, b, opts));
                return sortResult(result);
            }

            if (isBuiltInContainer(a.getClass())) {
                CompareResult result = RequestLocalCompareKernel.compareObjects(a, b, opts, policy);
                return sortResult(result);
            }

            if (strategy != null) {
                CompareResult result = RequestLocalCompareKernel.executeSnapshotDiff(
                        a,
                        b,
                        opts,
                        policy,
                        () -> strategy.compare(a, b, opts));
                return sortResult(result);
            }

            // Fallback：深度/普通快照 → DiffDetector → FieldChange（不排序，由 Engine 统一排序）
            CompareResult fallback = deepCompareFallback(a, b, opts);
            return sortResult(fallback);

        } catch (Exception e) {
            // 结果与日志都只发布固定分类，避免异常message/stack把业务值带出比较边界。
            logger.error("CompareEngine execution failed for types: {} vs {}",
                a != null ? a.getClass().getSimpleName() : "null",
                b != null ? b.getClass().getSimpleName() : "null");

            return CompareResultReducer.failure(
                    CompareProblemCode.DIFF_FAILED,
                    CompareStage.DIFF);
        }
    }

    /**
     * Fallback 深度比较（Engine 内部实现，策略不打点）。
     */
    private CompareResult deepCompareFallback(Object a, Object b, CompareOptions options) {
        try {
            return RequestLocalCompareKernel.compareObjects(
                    a,
                    b,
                    options,
                    policy,
                    path -> compareRegisteredProperty(a, b, path));
        } catch (Exception e) {
            logger.warn("Deep compare fallback failed");
            return CompareResultReducer.failure(
                    CompareProblemCode.DIFF_FAILED,
                    CompareStage.DIFF);
        }
    }

    /** comparator在对应DIFF_NODE内执行，避免扩展调用脱离deadline与ledger边界。 */
    private Optional<Boolean> compareRegisteredProperty(
            Object left,
            Object right,
            ComparePath path) {
        if (propertyComparators.isEmpty() || path.segmentCount() != 1) {
            return Optional.empty();
        }
        Object segment = path.segments().getFirst();
        if (!(segment instanceof PropertySegment property)) {
            return Optional.empty();
        }
        for (Map.Entry<PropertySelector, PropertyComparator> entry : propertyComparators.entrySet()) {
            PropertySelector selector = entry.getKey();
            if (selector.declaringClass() != left.getClass()
                    || !selector.fieldName().equals(property.name())) {
                continue;
            }
            Field field = selector.resolveField();
            PropertyComparator comparator = entry.getValue();
            if (!comparator.supports(field.getType()) || !field.trySetAccessible()) {
                throw new PropertyComparisonException("registered comparator field is not accessible");
            }
            try {
                return Optional.of(comparator.areEqual(field.get(left), field.get(right), field));
            } catch (IllegalAccessException exception) {
                throw new PropertyComparisonException("registered comparator field access failed", exception);
            }
        }
        return Optional.empty();
    }

    /**
     * 从冻结策略表确定性选择策略。
     *
     * <p>用户扩展只接受exact类型；内建容器策略按Set、Map、Collection顺序匹配，避免接口重叠导致
     * 选择依赖Map迭代顺序。每次计算成本固定且不写缓存，从而保持runtime执行期不可变。</p>
     */
    @SuppressWarnings("unchecked")
    private <T> CompareStrategy<T> resolveStrategy(Class<T> type) {
        CompareStrategy<?> exact = customStrategies.get(type);
        if (exact != null) {
            return (CompareStrategy<T>) exact;
        }
        Class<?> builtInTarget = resolveBuiltInTarget(type);
        return builtInTarget == null ? null : (CompareStrategy<T>) customStrategies.get(builtInTarget);
    }

    private Class<?> resolveBuiltInTarget(Class<?> type) {
        if (Set.class.isAssignableFrom(type)) {
            return Set.class;
        }
        if (Map.class.isAssignableFrom(type)) {
            return Map.class;
        }
        if (Collection.class.isAssignableFrom(type)) {
            return Collection.class;
        }
        if (Object[].class.isAssignableFrom(type)) {
            return Object[].class;
        }
        return null;
    }

    private boolean isCustomStrategyTarget(Class<?> type) {
        return customStrategyTargets.contains(type) || resolveBuiltInTarget(type) == null;
    }

    private boolean isBuiltInContainer(Class<?> type) {
        return type.isArray()
                || Map.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type);
    }

    /**
     * 把当前调用的相等域摘要写回结果，同时保留算法、预算和省略计数。
     * fingerprint在Policy/Options校验后统一附加，避免fast path绕过诊断事实。
     */
    private CompareResult withFingerprint(CompareResult result, String fingerprint) {
        CompareDiagnostics original = result.getDiagnostics();
        CompareDiagnostics diagnostics = new CompareDiagnostics(
                original.durationNanos(),
                original.rootAlgorithmId(),
                original.appliedAlgorithmIds(),
                Optional.of(fingerprint),
                original.comparedNodes(),
                original.consumedElements(),
                original.retainedResultChars(),
                original.omittedPaths(),
                original.omittedChanges(),
                original.omittedProblems(),
                original.omittedLimitations());
        return CompareResult.canonical(
                result.getOutcome(),
                result.getCompletion(),
                result.getChanges(),
                result.getProblems(),
                result.getLimitations(),
                diagnostics,
                result.similarity());
    }

    /**
     * 唯一排序点：对 CompareResult 的 changes 进行稳定排序
     * Package-private以允许CompareService深度比较路径委托排序（M2临时方案，M3将完全门面化）
     */
    CompareResult sortResult(CompareResult result) {
        if (result != null && result.getChanges() != null && !result.getChanges().isEmpty()) {
            List<FieldChange> sortedChanges = StableSorter.sortByFieldChange(result.getChanges());
            result = CompareResult.canonical(
                    result.getOutcome(),
                    result.getCompletion(),
                    sortedChanges,
                    result.getProblems(),
                    result.getLimitations(),
                    result.getDiagnostics(),
                    result.similarity());
        }
        return result;
    }

    // P0修复：删除sortChanges公共方法，确保排序仅在execute路径发生（sortResult内部）
    // 深度比较路径待重构为DeepCompareStrategy后统一由Engine处理

}
