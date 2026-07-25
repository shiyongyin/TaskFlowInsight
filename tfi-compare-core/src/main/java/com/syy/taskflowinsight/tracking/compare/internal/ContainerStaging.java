package com.syy.taskflowinsight.tracking.compare.internal;

import com.syy.taskflowinsight.tracking.compare.CompareLimitation;
import com.syy.taskflowinsight.tracking.compare.CompareLimitationCode;
import com.syy.taskflowinsight.tracking.compare.CompareStage;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 无序容器的请求局部 bounded staging 边界。
 *
 * <p>只有完整且未超限的成员列表会返回给配对逻辑；deadline 或 overflow 会清空全部已读成员，
 * 发布 typed limitation，并避免无序迭代的任意前 N 项成为业务事实。</p>
 */
final class ContainerStaging {

    private ContainerStaging() {
    }

    /**
     * 有界读取 Map entry；最后一个槽位只确认 overflow。
     *
     * @param map 待读取的单侧 Map
     * @param pendingLimit 含 overflow sentinel 的最大读取次数
     * @param parent 当前容器 frame
     * @param state 当前请求状态
     * @param limitations 当前 snapshot limitation 集
     * @param values 当前单侧 snapshot facts
     * @return 带闭集状态的有界成员
     */
    /* default */ static Batch<Map.Entry<?, ?>> map(
            final Map<?, ?> map,
            final int pendingLimit,
            final TraversalFrame parent,
            final CompareRequestState state,
            final List<CompareLimitation> limitations,
            final Map<ComparePath, ValueSnapshot> values) {
        return stage(
                map.entrySet().iterator(), pendingLimit, parent, state, limitations, values);
    }

    /**
     * 有界读取 Set member；最后一个槽位只确认 overflow。
     *
     * @param set 待读取的单侧 Set
     * @param parent 当前容器 frame
     * @param state 当前请求状态
     * @param limitations 当前 snapshot limitation 集
     * @param values 当前单侧 snapshot facts
     * @return 带闭集状态的有界成员
     */
    /* default */ static Batch<?> set(
            final Set<?> set,
            final TraversalFrame parent,
            final CompareRequestState state,
            final List<CompareLimitation> limitations,
            final Map<ComparePath, ValueSnapshot> values) {
        return stage(
                set.iterator(), state.pendingContainerFrameLimit(),
                parent, state, limitations, values);
    }

    private static <T> Batch<T> stage(
            final Iterator<? extends T> iterator,
            final int pendingLimit,
            final TraversalFrame parent,
            final CompareRequestState state,
            final List<CompareLimitation> limitations,
            final Map<ComparePath, ValueSnapshot> values) {
        final List<T> staged = new ArrayList<>(pendingLimit);
        Status status = Status.COMPLETE;
        while (status == Status.COMPLETE
                && staged.size() < pendingLimit
                && iterator.hasNext()) {
            if (state.deadlineReached()) {
                status = Status.DEADLINE;
            } else {
                staged.add(iterator.next());
            }
        }
        if (status == Status.COMPLETE && staged.size() == pendingLimit) {
            status = Status.OVERFLOW;
        }

        if (status != Status.COMPLETE) {
            final CompareLimitationCode code = status == Status.DEADLINE
                    ? CompareLimitationCode.DEADLINE_REACHED
                    : CompareLimitationCode.COLLECTION_LIMIT_REACHED;
            addLimitation(limitations, code, parent);
            discardIncomplete(values, parent, state);
            staged.clear();
        }
        return new Batch<>(status, staged);
    }

    private static void addLimitation(
            final List<CompareLimitation> limitations,
            final CompareLimitationCode code,
            final TraversalFrame parent) {
        final CompareLimitation limitation = new CompareLimitation(
                code, CompareStage.SNAPSHOT, Optional.of(parent.path()));
        if (!limitations.contains(limitation)) {
            limitations.add(limitation);
        }
    }

    private static void drainFrames(final CompareRequestState state) {
        while (state.hasFrames()) {
            final TraversalFrame pending = state.pollFrame();
            if (pending.exit()) {
                state.leaveActivePath(pending.value());
            }
        }
    }

    /* default */ static void discardIncomplete(
            final Map<ComparePath, ValueSnapshot> values,
            final TraversalFrame parent,
            final CompareRequestState state) {
        values.remove(parent.path());
        drainFrames(state);
    }

    /** bounded staging 的闭集状态。 */
    /* default */ enum Status {
        /** 输入在预算和 deadline 内完整读取。 */
        COMPLETE,
        /** overflow sentinel 已确认仍有超出预算的成员。 */
        OVERFLOW,
        /** staging 期间请求 deadline 已到达。 */
        DEADLINE
    }

    /**
     * staging 状态与仅在完整时可消费的成员。
     *
     * @param status 本次 staging 的闭集状态
     * @param members 完整输入成员；非完整状态固定为空
     */
    /* default */ record Batch<T>(Status status, List<T> members)
            implements Iterable<T> {

        Batch {
            // stage 创建后不再持有或修改该列表；转移所有权可避免 bounded hot path 的二次复制。
            members = Collections.unmodifiableList(members);
        }

        /* default */ boolean complete() {
            return status == Status.COMPLETE;
        }

        /* default */ void ifComplete(final Runnable action) {
            if (complete()) {
                action.run();
            }
        }

        @Override
        public Iterator<T> iterator() {
            return members.iterator();
        }
    }
}
