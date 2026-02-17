package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.model.TaskNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TaskContext接口的默认实现
 * 提供异常安全的任务上下文操作
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
final class TaskContextImpl implements TaskContext {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskContextImpl.class);
    private final TaskNode taskNode;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    /**
     * 构造函数
     * 
     * @param taskNode 任务节点
     */
    TaskContextImpl(TaskNode taskNode) {
        if (taskNode == null) {
            throw new IllegalArgumentException("TaskNode cannot be null");
        }
        this.taskNode = taskNode;
    }
    
    @Override
    public TaskContext message(String message) {
        if (!closed.get() && message != null && !message.trim().isEmpty()) {
            try {
                taskNode.addInfo(message.trim());
            } catch (Throwable t) {
                // 异常安全：消息添加失败不影响调用者
                logger.debug("Failed to add message: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext debug(String message) {
        if (!closed.get() && message != null && !message.trim().isEmpty()) {
            try {
                taskNode.addInfo("[DEBUG] " + message.trim());
            } catch (Throwable t) {
                logger.debug("Failed to add debug message: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext warn(String message) {
        if (!closed.get() && message != null && !message.trim().isEmpty()) {
            try {
                taskNode.addInfo("[WARN] " + message.trim());
            } catch (Throwable t) {
                logger.debug("Failed to add warn message: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext error(String message) {
        if (!closed.get() && message != null && !message.trim().isEmpty()) {
            try {
                taskNode.addError(message.trim());
            } catch (Throwable t) {
                logger.debug("Failed to add error message: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext error(String message, Throwable throwable) {
        if (!closed.get() && message != null) {
            try {
                String errorMessage = message.trim();
                if (throwable != null) {
                    errorMessage += " - " + throwable.getClass().getSimpleName() + ": " + safeMessage(throwable);
                }
                taskNode.addError(errorMessage);
            } catch (Throwable t) {
                logger.debug("Failed to add error with throwable: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext attribute(String key, Object value) {
        if (!closed.get() && key != null && !key.trim().isEmpty()) {
            try {
                // 属性功能暂未实现，记录为消息
                taskNode.addInfo("[ATTR] " + key.trim() + "=" + value);
            } catch (Throwable t) {
                logger.debug("Failed to add attribute: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext tag(String tag) {
        if (!closed.get() && tag != null && !tag.trim().isEmpty()) {
            try {
                // 标签功能暂未实现，记录为消息
                taskNode.addInfo("[TAG] " + tag.trim());
            } catch (Throwable t) {
                logger.debug("Failed to add tag: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext success() {
        if (!closed.get()) {
            try {
                // 手动设置状态为成功
                taskNode.complete();
            } catch (Throwable t) {
                logger.debug("Failed to mark task as success: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext fail() {
        if (!closed.get()) {
            try {
                // 标记失败状态
                taskNode.fail("Task marked as failed");
            } catch (Throwable t) {
                logger.debug("Failed to mark task as failed: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext fail(Throwable throwable) {
        if (!closed.get()) {
            try {
                // 标记失败状态并记录异常
                if (throwable != null) {
                    taskNode.fail(throwable);
                } else {
                    taskNode.fail("Task failed");
                }
            } catch (Throwable t) {
                logger.debug("Failed to mark task as failed with throwable: {}", t.getMessage());
            }
        }
        return this;
    }
    
    @Override
    public TaskContext subtask(String taskName) {
        if (!closed.get() && taskName != null && !taskName.trim().isEmpty()) {
            try {
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context != null) {
                    TaskNode subTask = context.startTask(taskName.trim());
                    return subTask != null ? new TaskContextImpl(subTask) : NullTaskContext.INSTANCE;
                }
                return NullTaskContext.INSTANCE;
            } catch (Throwable t) {
                logger.debug("Failed to create subtask: {}", t.getMessage());
                return NullTaskContext.INSTANCE;
            }
        }
        return NullTaskContext.INSTANCE;
    }
    
    @Override
    public boolean isClosed() {
        return closed.get();
    }
    
    @Override
    public String getTaskName() {
        try {
            return taskNode.getTaskName();
        } catch (Throwable t) {
            logger.debug("Failed to get task name: {}", t.getMessage());
            return "";
        }
    }
    
    @Override
    public String getTaskId() {
        try {
            return taskNode.getNodeId();
        } catch (Throwable t) {
            logger.debug("Failed to get task ID: {}", t.getMessage());
            return "";
        }
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                // 结束任务时，如果状态仍为RUNNING，则设置为完成
                // 如果已经是FAILED状态，保持不变
                if (taskNode.getStatus() == TaskStatus.RUNNING) {
                    taskNode.complete();
                }
                ManagedThreadContext context = ManagedThreadContext.current();
                if (context != null && context.getCurrentTask() == taskNode) {
                    context.endTask();
                }
            } catch (Throwable t) {
                logger.debug("Failed to close task context: {}", t.getMessage());
            }
        }
    }
    
    @Override
    public String toString() {
        return "TaskContext[" + getTaskName() + "]";
    }

    /**
     * 安全获取异常消息（null-safe）
     *
     * @param t 异常对象
     * @return 非 null 的异常描述信息
     */
    private static String safeMessage(Throwable t) {
        if (t == null) {
            return "unknown error";
        }
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }
}