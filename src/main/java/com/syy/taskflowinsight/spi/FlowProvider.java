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
     * Provider优先级（数值越大优先级越高）
     * <p>Spring实现通常返回Integer.MAX_VALUE，ServiceLoader实现返回0</p>
     *
     * @return 优先级值，默认0
     */
    default int priority() {
        return 0;
    }
}
