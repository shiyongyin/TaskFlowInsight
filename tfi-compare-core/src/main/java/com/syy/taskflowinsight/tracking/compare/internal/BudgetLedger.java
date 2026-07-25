package com.syy.taskflowinsight.tracking.compare.internal;

import java.util.Objects;

/**
 * 单次比较请求的预算唯一记账器。
 *
 * <p>该对象刻意不做线程安全：它只属于一个同步执行的request。准入与工作回调绑定，确保达到上限后不会误调用
 * 业务扩展，也不会为了探测越界把计数器推进到limit+1。</p>
 */
final class BudgetLedger {

    /** snapshot、diff与候选配对共同使用的节点上限。 */
    private final int maxComparedNodes;

    /** 两侧全部容器成员共同使用的元素上限。 */
    private final int maxContainerMembers;

    /** 已准入的snapshot、diff与候选配对事件总数。 */
    private int comparedNodes;

    /** 已准入的Map entry、Collection element与array element总数。 */
    private int containerMembers;

    BudgetLedger(int maxComparedNodes, int maxContainerMembers) {
        if (maxComparedNodes < 1 || maxContainerMembers < 1) {
            throw new IllegalArgumentException("budget limits must be positive");
        }
        this.maxComparedNodes = maxComparedNodes;
        this.maxContainerMembers = maxContainerMembers;
    }

    boolean admit(BudgetEvent event, Runnable callback) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(callback, "callback");
        return switch (event) {
            case SNAPSHOT_NODE, DIFF_NODE, PAIR_CANDIDATE -> admitComparedNode(callback);
            case CONTAINER_MEMBER -> admitContainerMember(callback);
        };
    }

    int comparedNodes() {
        return comparedNodes;
    }

    int containerMembers() {
        return containerMembers;
    }

    int remainingComparedNodes() {
        return maxComparedNodes - comparedNodes;
    }

    int remainingContainerMembers() {
        return maxContainerMembers - containerMembers;
    }

    private boolean admitComparedNode(Runnable callback) {
        if (comparedNodes == maxComparedNodes) {
            return false;
        }
        comparedNodes++;
        callback.run();
        return true;
    }

    private boolean admitContainerMember(Runnable callback) {
        if (containerMembers == maxContainerMembers) {
            return false;
        }
        containerMembers++;
        callback.run();
        return true;
    }
}
