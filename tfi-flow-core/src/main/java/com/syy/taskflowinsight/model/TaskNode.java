package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.enums.MessageType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务节点类
 * 表示任务执行树中的单个节点，支持父子关系和状态管理
 * 
 * <p>设计原则：
 * <ul>
 *   <li>树形结构 - 支持父子节点关系</li>
 *   <li>不可变路径 - taskPath在构造时计算并固定</li>
 *   <li>线程安全 - 使用CopyOnWriteArrayList和synchronized方法</li>
 *   <li>状态管理 - 支持RUNNING → COMPLETED/FAILED状态转换</li>
 *   <li>双时间戳 - 毫秒级显示时间，纳秒级精确时间</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public final class TaskNode {
    
    private final String nodeId;
    private final String taskName;
    private final String taskPath;
    // 注意：纯 Java 核心模块已移除 Jackson 注解
    private final TaskNode parent;
    private final long createdMillis;
    private final long createdNanos;
    private final String threadName;
    
    private final CopyOnWriteArrayList<TaskNode> children;
    private final CopyOnWriteArrayList<Message> messages;
    
    private volatile TaskStatus status;
    private volatile Long completedMillis;
    private volatile Long completedNanos;
    
    /**
     * 创建根节点
     * 
     * @param taskName 任务名称
     */
    public TaskNode(String taskName) {
        this(null, taskName);
    }
    
    /**
     * 创建子节点
     * 
     * @param parent 父节点
     * @param taskName 任务名称
     */
    public TaskNode(TaskNode parent, String taskName) {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("Task name cannot be null or empty");
        }
        
        this.nodeId = UUID.randomUUID().toString();
        this.taskName = taskName.trim();
        this.parent = parent;
        this.taskPath = computeTaskPath();
        
        // 使用 System.currentTimeMillis 避免 Instant 对象创建的额外开销
        this.createdMillis = System.currentTimeMillis();
        this.createdNanos = System.nanoTime();
        this.threadName = Thread.currentThread().getName();
        
        this.children = new CopyOnWriteArrayList<>();
        this.messages = new CopyOnWriteArrayList<>();
        this.status = TaskStatus.RUNNING;
        
        // 将自己添加到父节点的子列表中
        if (parent != null) {
            parent.addChild(this);
        }
    }
    
    /**
     * 计算任务路径
     * 
     * @return 从根节点到当前节点的路径字符串
     */
    private String computeTaskPath() {
        if (parent == null) {
            return taskName;
        }
        return parent.getTaskPath() + "/" + taskName;
    }
    
    /**
     * 添加子节点到当前节点
     * 
     * @param child 子节点
     */
    private void addChild(TaskNode child) {
        if (child != null && !children.contains(child)) {
            children.add(child);
        }
    }
    
    /**
     * 创建子任务节点
     * 
     * @param taskName 子任务名称
     * @return 新创建的子节点
     * @throws IllegalArgumentException 如果taskName为null或空字符串
     */
    public TaskNode createChild(String taskName) {
        return new TaskNode(this, taskName);
    }
    
    /**
     * 添加信息消息
     * 
     * @param content 消息内容
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果content为null或空字符串
     */
    public synchronized Message addInfo(String content) {
        Message message = Message.info(content);
        messages.add(message);
        return message;
    }
    
    /**
     * 添加调试消息
     * @param content 调试消息内容
     * @return 创建的消息对象
     */
    public synchronized Message addDebug(String content) {
        Message message = Message.debug(content);
        messages.add(message);
        return message;
    }
    
    /**
     * 添加错误消息
     * 
     * @param content 错误消息内容
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果content为null或空字符串
     */
    public synchronized Message addError(String content) {
        Message message = Message.error(content);
        messages.add(message);
        return message;
    }
    
    /**
     * 添加警告消息
     * @param content 警告消息内容
     * @return 创建的消息对象
     */
    public synchronized Message addWarn(String content) {
        Message message = Message.warn(content);
        messages.add(message);
        return message;
    }
    
    /**
     * 添加指定类型的消息
     * 
     * @param content 消息内容
     * @param type 消息类型
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果参数为null或空字符串
     */
    public synchronized Message addMessage(String content, MessageType type) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Message type cannot be null");
        }
        
        Message message = Message.withType(content, type);
        messages.add(message);
        return message;
    }
    
    /**
     * 添加自定义标签消息
     * 
     * @param content 消息内容
     * @param customLabel 自定义标签
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果参数为null或空字符串
     */
    public synchronized Message addMessage(String content, String customLabel) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        if (customLabel == null || customLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Custom label cannot be null or empty");
        }
        
        Message message = Message.withLabel(content, customLabel);
        messages.add(message);
        return message;
    }
    
    /**
     * 根据异常添加错误消息
     * 
     * @param throwable 异常对象
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果throwable为null
     */
    public synchronized Message addError(Throwable throwable) {
        Message message = Message.error(throwable);
        messages.add(message);
        return message;
    }
    
    /**
     * 标记任务为完成状态
     * 
     * @throws IllegalStateException 如果任务不在RUNNING状态
     */
    public synchronized void complete() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException("Cannot complete task that is not running. Current status: " + status);
        }
        
        this.status = TaskStatus.COMPLETED;
        this.completedMillis = System.currentTimeMillis();
        this.completedNanos = System.nanoTime();
    }

    /**
     * 获取自身执行时长（纳秒）
     * 若未完成，返回当前时间与开始时间的差值
     */
    public long getSelfDurationNanos() {
        if (completedNanos != null) {
            return completedNanos - createdNanos;
        }
        return System.nanoTime() - createdNanos;
    }

    /**
     * 获取自身执行时长（毫秒）
     */
    public long getSelfDurationMillis() {
        return getSelfDurationNanos() / 1_000_000;
    }

    /**
     * 获取累计执行时长（纳秒）= 自身 + 所有子节点累计
     */
    public long getAccumulatedDurationNanos() {
        long total = getSelfDurationNanos();
        for (TaskNode child : getChildren()) {
            total += child.getAccumulatedDurationNanos();
        }
        return total;
    }

    /**
     * 获取累计执行时长（毫秒）
     */
    public long getAccumulatedDurationMillis() {
        return getAccumulatedDurationNanos() / 1_000_000;
    }
    
    /**
     * 标记任务为失败状态
     * 
     * @throws IllegalStateException 如果任务不在RUNNING状态
     */
    public synchronized void fail() {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException("Cannot fail task that is not running. Current status: " + status);
        }
        
        this.status = TaskStatus.FAILED;
        Instant now = Instant.now();
        this.completedMillis = now.toEpochMilli();
        this.completedNanos = System.nanoTime();
    }
    
    /**
     * 标记任务为失败状态并添加错误消息
     * 
     * @param errorContent 错误消息内容
     * @throws IllegalStateException 如果任务不在RUNNING状态
     * @throws IllegalArgumentException 如果errorContent为null或空字符串
     */
    public synchronized void fail(String errorContent) {
        addError(errorContent);
        fail();
    }
    
    /**
     * 根据异常标记任务为失败状态
     * 
     * @param throwable 异常对象
     * @throws IllegalStateException 如果任务不在RUNNING状态
     * @throws IllegalArgumentException 如果throwable为null
     */
    public synchronized void fail(Throwable throwable) {
        addError(throwable);
        fail();
    }
    
    /**
     * 获取节点唯一标识
     * 
     * @return 节点ID
     */
    public String getNodeId() {
        return nodeId;
    }
    
    /**
     * 获取任务名称
     * 
     * @return 任务名称
     */
    public String getTaskName() {
        return taskName;
    }
    
    /**
     * 获取任务路径
     * 
     * @return 从根节点到当前节点的路径
     */
    public String getTaskPath() {
        return taskPath;
    }
    
    /**
     * 获取父节点
     * 
     * @return 父节点，如果是根节点则返回null
     */
    public TaskNode getParent() {
        return parent;
    }
    
    /**
     * 获取创建时间（毫秒时间戳）
     * 
     * @return 毫秒级时间戳
     */
    public long getCreatedMillis() {
        return createdMillis;
    }
    
    /**
     * 获取创建时间（纳秒时间戳）
     * 
     * @return 纳秒级时间戳
     */
    public long getCreatedNanos() {
        return createdNanos;
    }
    
    /**
     * 获取创建任务时的线程名称
     * 
     * @return 线程名称
     */
    public String getThreadName() {
        return threadName;
    }
    
    /**
     * 获取当前任务状态
     * 
     * @return 任务状态
     */
    public TaskStatus getStatus() {
        return status;
    }
    
    /**
     * 获取完成时间（毫秒时间戳）
     * 
     * @return 毫秒级时间戳，如果任务未完成则返回null
     */
    public Long getCompletedMillis() {
        return completedMillis;
    }
    
    /**
     * 获取完成时间（纳秒时间戳）
     * 
     * @return 纳秒级时间戳，如果任务未完成则返回null
     */
    public Long getCompletedNanos() {
        return completedNanos;
    }
    
    /**
     * 获取执行时长（毫秒）
     * 
     * @return 执行时长，如果任务未完成则返回null
     */
    public Long getDurationMillis() {
        if (completedMillis == null) {
            return null;
        }
        return completedMillis - createdMillis;
    }
    
    /**
     * 获取执行时长（纳秒）
     * 
     * @return 执行时长，如果任务未完成则返回null
     */
    public Long getDurationNanos() {
        if (completedNanos == null) {
            return null;
        }
        return completedNanos - createdNanos;
    }
    
    /**
     * 获取子节点列表（只读）
     * 
     * @return 子节点的不可修改列表
     */
    public List<TaskNode> getChildren() {
        return Collections.unmodifiableList(new ArrayList<>(children));
    }
    
    /**
     * 获取消息列表（只读）
     * 
     * @return 消息的不可修改列表
     */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }
    
    /**
     * 判断是否为根节点
     * 
     * @return true 如果是根节点
     */
    public boolean isRoot() {
        return parent == null;
    }
    
    /**
     * 判断是否为叶子节点
     * 
     * @return true 如果没有子节点
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }
    
    /**
     * 获取节点深度
     * 
     * @return 从根节点到当前节点的深度（根节点深度为0）
     */
    public int getDepth() {
        int depth = 0;
        TaskNode current = this.parent;
        while (current != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }
    
    /**
     * 获取根节点
     * 
     * @return 根节点
     */
    public TaskNode getRoot() {
        TaskNode current = this;
        while (current.parent != null) {
            current = current.parent;
        }
        return current;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        TaskNode taskNode = (TaskNode) obj;
        return Objects.equals(nodeId, taskNode.nodeId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }
    
    @Override
    public String toString() {
        return String.format("TaskNode{path='%s', status=%s, thread='%s'}", 
                taskPath, status, threadName);
    }
}
