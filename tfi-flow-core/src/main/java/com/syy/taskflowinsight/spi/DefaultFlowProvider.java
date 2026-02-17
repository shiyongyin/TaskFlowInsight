package com.syy.taskflowinsight.spi;

import com.syy.taskflowinsight.context.ManagedThreadContext;
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
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
public class DefaultFlowProvider implements FlowProvider {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFlowProvider.class);

    @Override
    public String startSession(String name) {
        try {
            // create() 内部已调用 startSession()，直接获取已创建的会话即可
            ManagedThreadContext context = ManagedThreadContext.create(name);
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
            if (context != null) {
                return context.startTask(name);
            }
            return null;
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
                    // TaskNode.addMessage(String, String) 方法签名：(label, content)
                    if (label != null && !label.isEmpty()) {
                        task.addMessage(label, content);
                    } else {
                        task.addMessage("INFO", content);
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

    @Override
    public int priority() {
        return 0; // 最低优先级（兜底实现）
    }

    @Override
    public String toString() {
        return "DefaultFlowProvider{priority=0, type=fallback}";
    }
}
