package com.syy.taskflowinsight.spi.fixtures;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.FlowProvider;

/**
 * Loader A 独占资源声明使用的完整测试 Provider。
 *
 * @since 4.0.0
 */
public final class LoaderAFlowProvider implements FlowProvider {

    /** ServiceLoader 使用的公共无参构造器。 */
    public LoaderAFlowProvider() {
    }

    @Override
    public String startSession(String name) {
        return "loader-a";
    }

    @Override
    public void endSession() {
    }

    @Override
    public TaskNode startTask(String name) {
        return null;
    }

    @Override
    public void endTask() {
    }

    @Override
    public Session currentSession() {
        return null;
    }

    @Override
    public TaskNode currentTask() {
        return null;
    }

    @Override
    public void message(String content, String label) {
    }

    @Override
    public int priority() {
        return 1_000;
    }
}
