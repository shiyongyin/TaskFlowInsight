package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认流程管理Provider实现（兜底）
 *
 * <p>使用{@link ManagedThreadContext}进行会话与任务管理，priority=0（最低优先级）。
 * <p>不依赖Spring，纯Java环境可用。
 * <p>异常安全：所有操作不抛出异常，失败时记录日志并返回null/不执行操作。
 *
 * <p>该类同时承担 TfiFlow facade 的默认运行路径。这样做的原因是让
 * {@code TfiFlow.start/stage/stop/message/clear} 和自定义 Provider 走同一套生命周期契约，
 * 避免 facade 里再复制一份 {@link ManagedThreadContext} 分支逻辑。
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultFlowProvider implements FlowProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFlowProvider.class);

    /**
     * 创建无实例状态的默认流程 Provider；会话状态始终由当前线程上下文持有。
     */
    public DefaultFlowProvider() {
    }

    @Override
    public String startSession(String name) {
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null && !context.isClosed()) {
                /*
                 * G2 规定一 Session 一 Context。endSession() 会终止并注销旧 Context，
                 * 所以下一会话必须通过 create() 获得新 owner，不能在旧实例上再次 startSession()。
                 */
                context.endSession();
            }
            context = ManagedThreadContext.create(name);
            Session session = context.getCurrentSession();
            return session != null ? session.getSessionId() : null;
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.startSession failed for name={}: {}",
                name, e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("StartSession error details", e);
            }
            return null;
        }
    }

    @Override
    public void endSession() {
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endSession();
            }
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.endSession failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("EndSession error details", e);
            }
        }
    }

    @Override
    public TaskNode startTask(String name) {
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context == null || context.isClosed()) {
                /*
                 * 保留 TfiFlow.stage/start 的 auto-session 行为。
                 * auto-session 放在默认 Provider 内，是为了让 facade 不再知道底层上下文创建细节。
                 */
                context = ManagedThreadContext.create("auto-session");
            } else if (context.getCurrentSession() == null) {
                context.startSession("auto-session");
            }
            return context.startTask(name);
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.startTask failed for name={}: {}",
                name, e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("StartTask error details", e);
            }
            return null;
        }
    }

    @Override
    public void endTask() {
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                context.endTask();
            }
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.endTask failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("EndTask error details", e);
            }
        }
    }

    @Override
    public void clear() {
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                /*
                 * 不使用 FlowProvider 默认 clear()，因为默认实现只结束任务/会话，
                 * 不能保证释放 SafeContextManager 中的 ThreadLocal 注册。
                 */
                ThreadContext.clear();
            }
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.clear failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Clear error details", e);
            }
        }
    }

    @Override
    public Session currentSession() {
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            return context != null ? context.getCurrentSession() : null;
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.currentSession failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("CurrentSession error details", e);
            }
            return null;
        }
    }

    @Override
    public TaskNode currentTask() {
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            return context != null ? context.getCurrentTask() : null;
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.currentTask failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("CurrentTask error details", e);
            }
            return null;
        }
    }

    @Override
    public void message(String content, String label) {
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                TaskNode task = context.getCurrentTask();
                if (task != null) {
                    if (label != null && !label.isEmpty()) {
                        task.addMessage(content, label);
                    } else {
                        task.addMessage(content, "INFO");
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.message failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Message error details", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>覆盖默认实现以保留 {@link MessageType} 语义：直接调用
     * {@link TaskNode#addMessage(String, MessageType)}，确保下游 {@code Message.getType()}
     * 在 Provider 路径下仍可正确返回类型，而非退化为字符串标签。
     */
    @Override
    public void messageWithType(String content, MessageType type) {
        if (type == null) {
            // 无类型时退回字符串标签路径（与默认实现一致）
            message(content, (String) null);
            return;
        }
        try {
            ManagedThreadContext context = ManagedThreadContext.current();
            if (context != null) {
                TaskNode task = context.getCurrentTask();
                if (task != null) {
                    task.addMessage(content, type);
                }
            }
        } catch (Exception e) {
            logger.warn("DefaultFlowProvider.message(type) failed: {}", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Message(type) error details", e);
            }
        }
    }

    @Override
    public int priority() {
        return 0; // 最低优先级（兜底实现）
    }

    @Override
    public String toString() {
        return "DefaultFlowProvider{priority=0, type=fallback}";
    }
}
