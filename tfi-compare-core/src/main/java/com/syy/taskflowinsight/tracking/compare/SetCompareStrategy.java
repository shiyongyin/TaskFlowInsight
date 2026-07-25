package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.compare.internal.RequestLocalCompareKernel;
import com.syy.taskflowinsight.tracking.compare.list.EntityListStrategy;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.path.EntityKeySegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Set比较的无状态兼容入口。
 *
 * <p>scalar、Entity与未标注复杂成员都由唯一request-local内核逐项解释；本类型不再通过首项采样、
 * HashSet removeAll、identity hash排序或失败fallback建立第二套Set真值模型。</p>
 *
 * @author TaskFlow Insight Team
 * @version 4.0.0
 * @since 2025-01-13
 */
public class SetCompareStrategy implements DetailedCompareStrategy<Set<?>> {

    /** 无状态兼容构造；所有语义依赖均属于冻结Runtime。 */
    public SetCompareStrategy() {
    }

    /**
     * 保留旧注入签名，但不保存或调用Entity List策略。
     *
     * <p>Set与List共享内核identity规则，却拥有不同容器合同；保存该策略会重新引入“Set排序转List”
     * 的错误执行图，因此这里只校验历史调用参数。</p>
     *
     * @param entityListStrategy 历史构造参数，仅用于兼容直接实例化消费者
     */
    public SetCompareStrategy(EntityListStrategy entityListStrategy) {
        Objects.requireNonNull(entityListStrategy, "entityListStrategy");
    }

    /**
     * 通过request-local内核比较两个Set，确保无序成员与Entity成员共享同一identity/content合同。
     *
     * @param set1 旧侧Set；允许为null
     * @param set2 新侧Set；允许为null
     * @param options 本次比较的冻结选项；非null
     * @return 保留typed路径、问题与限制事实的比较结果
     */
    @Override
    public CompareResult compare(Set<?> set1, Set<?> set2, CompareOptions options) {
        if (set1 == set2) {
            return CompareResult.identical();
        }
        if (set1 == null || set2 == null) {
            return CompareResult.ofNullDiff(set1, set2);
        }
        // 兼容入口必须进入同一个内核，才能共享typed identity、预算和ambiguity语义。
        return RequestLocalCompareKernel.compareObjects(
                set1, set2, options, options.getPolicy());
    }

    /**
     * 返回历史策略注册名；该名称仅用于兼容选择，不代表独立语义owner。
     *
     * @return 固定的Set策略名称
     */
    @Override
    public String getName() {
        return "SetCompare";
    }

    /**
     * 判断声明类型能否由该兼容入口接收，不对运行时成员做首项采样。
     *
     * @param type 待判断的声明类型；允许为null
     * @return type为Set或其子类型时返回true
     */
    @Override
    public boolean supports(Class<?> type) {
        return type != null && Set.class.isAssignableFrom(type);
    }

    /**
     * 将canonical Set变更投影为旧版ChangeRecord列表。
     *
     * <p>投影保留内核已经确认的成员路径，不重新配对Set成员；否则兼容API会形成第二套真值。</p>
     *
     * @param objectName 旧版记录中的对象名称；允许为null
     * @param fieldName 旧版记录的字段前缀；null表示不追加前缀
     * @param oldValue 旧侧Set；null按空Set投影
     * @param newValue 新侧Set；null按空Set投影
     * @param sessionId 兼容追踪会话标识；允许为null
     * @param taskPath 兼容任务路径；允许为null
     * @return 不可变的详细变更记录列表
     */
    @Override
    public List<ChangeRecord> generateDetailedChangeRecords(
            String objectName,
            String fieldName,
            Set<?> oldValue,
            Set<?> newValue,
            String sessionId,
            String taskPath) {
        if (oldValue == null && newValue == null) {
            return List.of();
        }
        Set<?> before = oldValue == null ? Set.of() : oldValue;
        Set<?> after = newValue == null ? Set.of() : newValue;
        CompareRuntime runtime = CompareRuntime.defaults();
        CompareResult result = compare(
                before, after, CompareOptions.defaults(runtime.policy()));
        List<ChangeRecord> records = new ArrayList<>(result.getChanges().size());
        for (FieldChange change : result.getChanges()) {
            String projectedPath = fieldName == null
                    ? change.getFieldPath()
                    : fieldName + change.getFieldPath();
            boolean entityMember = change.before()
                    .or(() -> change.after())
                    .orElseThrow()
                    .path()
                    .segments()
                    .stream()
                    .anyMatch(EntityKeySegment.class::isInstance);
            records.add(ChangeRecord.builder()
                    .objectName(objectName)
                    .fieldName(projectedPath)
                    .changeType(change.getChangeType())
                    .oldValue(change.beforeValue().orElse(null))
                    .newValue(change.afterValue().orElse(null))
                    .sessionId(sessionId)
                    .taskPath(taskPath)
                    .valueType(change.getValueType())
                    .valueKind(entityMember ? "SET_ENTITY" : "SET_MEMBER")
                    .build());
        }
        return List.copyOf(records);
    }
}
