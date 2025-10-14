package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;

/**
 * 流程管理服务提供接口（SPI）
 *
 * <p>提供流程会话与任务节点管理能力。
 *
 * <p>优先级规则：
 * <ul>
 *   <li>Spring Bean（最高优先级）</li>
 *   <li>手动注册（TFI.registerFlowProvider）</li>
 *   <li>ServiceLoader发现（META-INF/services）</li>
 *   <li>兜底实现（返回null/不执行操作）</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public interface FlowProvider {

    /**
     * 开始新会话
     *
     * @param name 会话名称
     * @return 会话ID，如果失败返回null
     */
    String startSession(String name);

    /**
     * 结束当前会话
     */
    void endSession();

    /**
     * 开始新任务
     *
     * @param name 任务名称
     * @return 任务节点对象，如果失败返回null
     */
    TaskNode startTask(String name);

    /**
     * 结束当前任务
     */
    void endTask();

    /**
     * 获取当前会话
     *
     * @return 当前会话对象，如果无会话返回null
     */
    Session currentSession();

    /**
     * 获取当前任务
     *
     * @return 当前任务节点，如果无任务返回null
     */
    TaskNode currentTask();

    /**
     * 向当前任务添加消息
     *
     * @param content 消息内容
     * @param label 消息标签（可选）
     */
    void message(String content, String label);

    /**
     * 清空当前线程的所有上下文（会话+任务栈）
     *
     * <p>实现注意事项：
     * <ul>
     *   <li>关闭当前会话（如果存在）</li>
     *   <li>清空任务栈</li>
     *   <li>释放 ThreadLocal 资源</li>
     *   <li>异常安全：捕获所有异常</li>
     * </ul>
     *
     * <p>默认实现：依次结束所有任务，然后结束会话。
     * 子类应提供更高效的批量清理实现。
     *
     * @since 4.0.0
     */
    default void clear() {
        try {
            // 先结束所有嵌套任务
            while (currentTask() != null) {
                endTask();
            }
            // 再结束会话
            if (currentSession() != null) {
                endSession();
            }
        } catch (Exception e) {
            // 默认实现：忽略异常，保证清理继续
        }
    }

    /**
     * 获取当前线程的任务栈（从根任务到当前任务）
     *
     * <p>返回列表顺序：stack[0] = 根任务, stack[n-1] = 当前任务
     *
     * <p>实现注意事项：
     * <ul>
     *   <li>如果无任务，返回空列表（不返回 null）</li>
     *   <li>返回不可变列表（防止外部修改）</li>
     *   <li>性能优化：子类可缓存结果（当任务栈不变时）</li>
     * </ul>
     *
     * <p>默认实现：递归遍历 parent 构建栈。
     * 时间复杂度 O(depth)，空间复杂度 O(depth)。
     *
     * @return 任务栈列表，从根到叶，如果无任务返回空列表
     * @since 4.0.0
     */
    default java.util.List<TaskNode> getTaskStack() {
        java.util.List<TaskNode> stack = new java.util.ArrayList<>();
        TaskNode current = currentTask();
        while (current != null) {
            stack.add(0, current);  // 头部插入
            current = current.getParent();
        }
        return java.util.List.copyOf(stack);  // 不可变列表
    }

    /**
     * Provider优先级（数值越大优先级越高）
     * <p>Spring实现通常返回Integer.MAX_VALUE，ServiceLoader实现返回0</p>
     *
     * @return 优先级值，默认0
     */
    default int priority() {
        return 0;
    }
}
