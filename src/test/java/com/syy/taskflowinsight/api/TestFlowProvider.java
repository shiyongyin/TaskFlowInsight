package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import com.syy.taskflowinsight.spi.FlowProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试专用 FlowProvider 实现
 */
public class TestFlowProvider implements FlowProvider {

    private final List<String> methodCalls = new CopyOnWriteArrayList<>();
    private Session currentSession;
    private TaskNode currentTask;
    private final List<TaskNode> taskStack = new ArrayList<>();

    @Override
    public String startSession(String sessionName) {
        methodCalls.add("startSession:" + sessionName);
        // Session 构造函数是 private，这里简化返回 null
        // 在真实的 Provider 实现中会使用 ManagedThreadContext 创建 Session
        String sessionId = "test-session-" + System.currentTimeMillis();
        return sessionId;
    }

    @Override
    public void endSession() {
        methodCalls.add("endSession");
        currentSession = null;
    }

    @Override
    public Session currentSession() {
        methodCalls.add("currentSession");
        return currentSession;
    }

    @Override
    public TaskNode startTask(String taskName) {
        methodCalls.add("startTask:" + taskName);
        currentTask = new TaskNode(taskName);
        taskStack.add(currentTask);
        return currentTask;
    }

    @Override
    public void endTask() {
        methodCalls.add("endTask");
        if (!taskStack.isEmpty()) {
            taskStack.remove(taskStack.size() - 1);
        }
        currentTask = taskStack.isEmpty() ? null : taskStack.get(taskStack.size() - 1);
    }

    @Override
    public TaskNode currentTask() {
        methodCalls.add("currentTask");
        return currentTask;
    }

    @Override
    public List<TaskNode> getTaskStack() {
        methodCalls.add("getTaskStack");
        return new ArrayList<>(taskStack);
    }

    @Override
    public void message(String content, String label) {
        methodCalls.add("message:" + label);
    }

    @Override
    public void clear() {
        methodCalls.add("clear");
        currentSession = null;
        currentTask = null;
        taskStack.clear();
    }

    // 测试断言辅助方法

    public boolean wasMethodCalled(String methodPrefix) {
        return methodCalls.stream().anyMatch(c -> c.startsWith(methodPrefix));
    }

    public int getMethodCallCount(String methodPrefix) {
        return (int) methodCalls.stream().filter(c -> c.startsWith(methodPrefix)).count();
    }

    public void reset() {
        methodCalls.clear();
        currentSession = null;
        currentTask = null;
        taskStack.clear();
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
