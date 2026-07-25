package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.list.ListCompareExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 比较兼容门面。
 *
 * <p>该类只保留批量与三方比较的历史编排表面，所有单次比较都委托构造期注入的同一个runtime。
 * 扩展注册属于{@link CompareRuntime.Builder}冻结职责，Service不持有registry或执行期mutation。</p>
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
public class CompareService {
    
    private static final Logger logger = LoggerFactory.getLogger(CompareService.class);
    /** 无显式注入时复用的纯Java冻结对象图。 */
    private static final CompareRuntime DEFAULT_RUNTIME = CompareRuntime.defaults();
    /** 兼容静态门面复用的无状态Service，避免每次路由失败时重新包装同一runtime。 */
    private static final CompareService DEFAULT_SERVICE = new CompareService(DEFAULT_RUNTIME);
    /** 当前门面唯一委托的runtime，构造后不可替换。 */
    private final CompareRuntime runtime;
    /** 缓存runtime发布的唯一engine引用，避免每次调用重新解析对象图。 */
    private final CompareEngine compareEngine;

    /**
     * 创建复用纯Java默认runtime的无状态兼容门面。
     */
    public CompareService() {
        this(DEFAULT_RUNTIME);
    }

    /**
     * 创建只委托指定冻结对象图的兼容门面。
     *
     * @param runtime 已完成全部策略与policy校验的runtime
     */
    public CompareService(CompareRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.compareEngine = runtime.engine();
    }

    /**
     * 使用指定列表执行器构造并立即冻结独立runtime。
     *
     * <p>该兼容入口仍通过{@link CompareRuntime.Builder}构图，不能直接创建Engine。</p>
     *
     * @param executor 列表比较执行器
     */
    public CompareService(ListCompareExecutor executor) {
        this(CompareRuntime.builder().listCompareExecutor(executor).build());
    }

    /**
     * 创建默认CompareService实例
     * @param options 比较选项
     * @return CompareService实例
     */
    public static CompareService createDefault(CompareOptions options) {
        Objects.requireNonNull(options, "options");
        return DEFAULT_SERVICE;
    }

    /**
     * 返回共享默认runtime的无状态兼容门面。
     *
     * @return 可在线程间共享的默认Service
     */
    public static CompareService defaults() {
        return DEFAULT_SERVICE;
    }

    /**
     * 使用当前runtime policy默认选项比较两个对象。
     *
     * @param obj1 变更前对象，可为null
     * @param obj2 变更后对象，可为null
     * @return canonical比较结果
     */
    public CompareResult compare(Object obj1, Object obj2) {
        return compareEngine.compare(obj1, obj2);
    }
    
    /**
     * 使用显式不可变选项比较两个对象。
     *
     * @param obj1 变更前对象，可为null
     * @param obj2 变更后对象，可为null
     * @param options 必须在当前runtime policy范围内
     * @return canonical比较结果
     */
    public CompareResult compare(Object obj1, Object obj2, CompareOptions options) {
        return compareEngine.compare(obj1, obj2, options);
    }
    
    /**
     * 按输入顺序使用当前runtime默认选项执行批量比较。
     *
     * @param pairs 待比较对象对；null或空集合返回空结果
     * @return 与输入顺序一致的结果列表
     */
    public List<CompareResult> compareBatch(List<Pair<Object, Object>> pairs) {
        return compareBatch(pairs, CompareOptions.defaults(runtime.policy()));
    }
    
    /**
     * 批量比较（带选项）。
     *
     * <p>批量入口不拥有并行策略；它按序复用同一个immutable Engine，避免宿主并发成为第二执行语义owner。</p>
     *
     * @param pairs 待比较对象对；null或空集合返回空结果
     * @param options 所有对象对共享且必须在runtime policy范围内的选项
     * @return 与输入顺序一致的结果列表
     */
    public List<CompareResult> compareBatch(List<Pair<Object, Object>> pairs, CompareOptions options) {
        if (pairs == null || pairs.isEmpty()) {
            return Collections.emptyList();
        }
        if (options == null) {
            throw new CompareInputException(InputViolation.NULL_OPTIONS);
        }
        final CompareOptions effectiveOpts = options;
        // 宿主并发不由单次CompareOptions控制；保持输入顺序并复用同一immutable engine。
        return pairs.stream()
                .map(p -> compare(p.getLeft(), p.getRight(), effectiveOpts))
                .collect(Collectors.toList());
    }
    
    /**
     * 使用当前runtime默认选项进行两次比较并检测三方值冲突。
     *
     * @param base 基准对象
     * @param left 左侧对象
     * @param right 右侧对象
     * @return 保留两侧change与冲突事实的合并结果
     */
    public MergeResult compareThreeWay(Object base, Object left, Object right) {
        return compareThreeWay(base, left, right, CompareOptions.defaults(runtime.policy()));
    }
    
    /**
     * 使用同一显式选项进行两次比较并检测三方值冲突。
     *
     * @param base 基准对象
     * @param left 左侧对象
     * @param right 右侧对象
     * @param options 两次比较共享且必须在runtime policy范围内的选项
     * @return 保留两侧change与冲突事实的合并结果
     */
    public MergeResult compareThreeWay(Object base, Object left, Object right, CompareOptions options) {
        // 比较base->left的变更
        CompareResult leftChanges = compare(base, left, options);
        // 比较base->right的变更
        CompareResult rightChanges = compare(base, right, options);
        
        // 检测冲突
        List<MergeConflict> conflicts = detectConflicts(leftChanges, rightChanges);
        
        return MergeResult.builder()
            .base(base)
            .left(left)
            .right(right)
            .leftChanges(leftChanges.getChanges())
            .rightChanges(rightChanges.getChanges())
            .conflicts(conflicts)
            .merged(null)
            .autoMergeSuccessful(false)
            .build();
    }
    
    // ========== 内部方法 ==========

    /**
     * 检测冲突（委派到 {@link ThreeWayMergeService} 的同名逻辑）。
     */
    private List<MergeConflict> detectConflicts(CompareResult leftChanges,
                                                CompareResult rightChanges) {
        // 内联保留以保持 compareThreeWay 的向后兼容
        List<MergeConflict> conflicts = new ArrayList<>();

        Map<String, FieldChange> leftMap = leftChanges.getChanges().stream()
            .collect(Collectors.toMap(FieldChange::getFieldName, c -> c, (a, b) -> a));

        for (FieldChange rightChange : rightChanges.getChanges()) {
            FieldChange leftChange = leftMap.get(rightChange.getFieldName());
            if (leftChange != null) {
                if (!Objects.equals(leftChange.afterValue(), rightChange.afterValue())) {
                    conflicts.add(MergeConflict.builder()
                        .fieldName(rightChange.getFieldName())
                        .leftValue(leftChange.afterValue().orElse(null))
                        .rightValue(rightChange.afterValue().orElse(null))
                        .conflictType(ConflictType.VALUE_CONFLICT)
                        .build());
                }
            }
        }

        return conflicts;
    }

}
