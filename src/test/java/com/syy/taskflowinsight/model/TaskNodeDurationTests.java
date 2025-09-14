package com.syy.taskflowinsight.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 TaskNode 的自身/累计时长计算口径
 */
class TaskNodeDurationTests {

    @Test
    void selfAndAccumulatedDurationsAreConsistent() throws Exception {
        TaskNode root = new TaskNode("root");
        Thread.sleep(5);
        TaskNode child = new TaskNode(root, "child");
        Thread.sleep(5);
        child.complete();
        Thread.sleep(5);
        root.complete();

        long rootSelf = root.getSelfDurationMillis();
        long childSelf = child.getSelfDurationMillis();
        long childAcc = child.getAccumulatedDurationMillis();
        long rootAcc = root.getAccumulatedDurationMillis();

        // 子节点累计=自身
        assertThat(childAcc).isEqualTo(childSelf);
        // 根节点累计=根自身+子累计（允许1ms误差，因为时间测量可能有精度差异）
        assertThat(rootAcc).isBetween(rootSelf + childAcc - 1, rootSelf + childAcc + 1);
        // 自身/累计应为非负
        assertThat(rootSelf).isGreaterThanOrEqualTo(0);
        assertThat(rootAcc).isGreaterThanOrEqualTo(rootSelf);
    }
}

