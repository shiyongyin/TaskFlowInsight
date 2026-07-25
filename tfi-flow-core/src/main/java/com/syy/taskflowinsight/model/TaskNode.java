package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.internal.FlowConfigDefaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 任务节点类
 * 表示任务执行树中的单个节点，支持父子关系和状态管理
 * 
 * <p>设计原则：
 * <ul>
 *   <li>树形结构 - 支持父子节点关系</li>
 *   <li>不可变路径 - taskPath在构造时计算并固定</li>
 *   <li>线程安全 - 对内部列表的写操作均 synchronized，读操作返回防御性快照</li>
 *   <li>状态管理 - 支持RUNNING → COMPLETED/FAILED状态转换</li>
 *   <li>双时间戳 - 毫秒级显示时间，纳秒级精确时间</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public final class TaskNode {
    
    // 延迟生成：仅在首次访问（equals/hashCode/导出）时才生成 UUID，避免热路径上的 SecureRandom 开销
    private volatile String nodeId;
    private final String taskName;
    private final String taskPath;
    // 注意：纯 Java 核心模块已移除 Jackson 注解
    private final TaskNode parent;
    private final long createdMillis;
    private final long createdNanos;
    private final String threadName;
    private final TaskTreeMutationGate mutationGate;

    // 改用 ArrayList + synchronized 写：消息/子节点为执行期高频追加、导出期一次性读取，
    // CopyOnWriteArrayList 的每次写全量复制会带来 O(n²) 开销
    private final List<TaskNode> children;
    private final List<Message> messages;
    private final Map<String, Object> attributes;
    private final List<String> tags;
    
    private volatile TaskStatus status;
    private volatile Long completedMillis;
    private volatile Long completedNanos;

    // 消息数量达到软上限后置位，保证哨兵告警只追加一次（由本实例锁保护）
    private boolean messageCapWarned;
    
    /**
     * 创建根节点
     * 
     * @param taskName 任务名称
     */
    public TaskNode(String taskName) {
        this(null, taskName, new TaskTreeMutationGate());
    }
    
    /**
     * 创建子节点
     * 
     * @param parent 父节点
     * @param taskName 任务名称
     */
    public TaskNode(TaskNode parent, String taskName) {
        this(parent, taskName, parent == null ? new TaskTreeMutationGate() : parent.mutationGate);
    }

    /**
     * 为 Session 根节点绑定其全树共享 gate。
     *
     * @param taskName 根任务名称
     * @param mutationGate Session 拥有的全树变更 gate
     */
    TaskNode(String taskName, TaskTreeMutationGate mutationGate) {
        this(null, taskName, mutationGate);
    }

    private TaskNode(TaskNode parent, String taskName, TaskTreeMutationGate mutationGate) {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("Task name cannot be null or empty");
        }
        
        this.taskName = taskName.trim();
        this.parent = parent;
        this.taskPath = computeTaskPath();
        
        // 使用 System.currentTimeMillis 避免 Instant 对象创建的额外开销
        this.createdMillis = System.currentTimeMillis();
        this.createdNanos = System.nanoTime();
        this.threadName = Thread.currentThread().getName();
        this.mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
        
        this.children = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.attributes = new LinkedHashMap<>();
        this.tags = new ArrayList<>();
        this.status = TaskStatus.RUNNING;
        
        // 将自己添加到父节点的子列表中
        if (parent != null) {
            parent.attachChild(this);
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
     * <p>子节点由构造函数唯一创建（{@code parent.addChild(this)}），不存在重复添加，
     * 因此无需 {@code contains} 去重检查；写操作以父节点为锁，保证与读快照的可见性。
     *
     * @param child 子节点
     */
    private void attachChild(TaskNode child) {
        mutationGate.mutate(() -> attachChildLocked(child));
    }

    private synchronized void attachChildLocked(TaskNode child) {
        if (child != null) {
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
    public Message addInfo(String content) {
        Message message = Message.info(content);
        return mutationGate.mutate(() -> appendMessageLocked(message));
    }
    
    /**
     * 添加调试消息
     * @param content 调试消息内容
     * @return 创建的消息对象
     */
    public Message addDebug(String content) {
        Message message = Message.debug(content);
        return mutationGate.mutate(() -> appendMessageLocked(message));
    }
    
    /**
     * 添加错误消息
     * 
     * @param content 错误消息内容
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果content为null或空字符串
     */
    public Message addError(String content) {
        Message message = Message.error(content);
        return mutationGate.mutate(() -> appendMessageLocked(message));
    }
    
    /**
     * 添加警告消息
     * @param content 警告消息内容
     * @return 创建的消息对象
     */
    public Message addWarn(String content) {
        Message message = Message.warn(content);
        return mutationGate.mutate(() -> appendMessageLocked(message));
    }
    
    /**
     * 添加指定类型的消息
     * 
     * @param content 消息内容
     * @param type 消息类型
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果参数为null或空字符串
     */
    public Message addMessage(String content, MessageType type) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Message type cannot be null");
        }
        
        Message message = Message.withType(content, type);
        return mutationGate.mutate(() -> appendMessageLocked(message));
    }
    
    /**
     * 添加自定义标签消息
     * 
     * @param content 消息内容
     * @param customLabel 自定义标签
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果参数为null或空字符串
     */
    public Message addMessage(String content, String customLabel) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        if (customLabel == null || customLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Custom label cannot be null or empty");
        }
        
        Message message = Message.withLabel(content, customLabel);
        return mutationGate.mutate(() -> appendMessageLocked(message));
    }
    
    /**
     * 根据异常添加错误消息
     * 
     * @param throwable 异常对象
     * @return 创建的消息对象
     * @throws IllegalArgumentException 如果throwable为null
     */
    public Message addError(Throwable throwable) {
        Message message = Message.error(throwable);
        return mutationGate.mutate(() -> appendMessageLocked(message));
    }

    /**
     * 统一的消息追加入口，带数量软上限。
     *
     * <p>messages 列表无界增长是长驻会话下的 OOM 隐患（业务循环中每次迭代记一条消息）。
     * 达到 {@link FlowConfigDefaults#MAX_MESSAGES_PER_NODE} 后丢弃后续消息，
     * 并追加一条哨兵告警提示数据被截断。调用方必须持有本实例锁。
     *
     * @param message 已构造并通过参数校验的消息
     * @return 入参消息对象（超限被丢弃时同样返回，保持原方法签名契约）
     */
    synchronized Message appendMessageLocked(Message message) {
        if (messages.size() >= FlowConfigDefaults.MAX_MESSAGES_PER_NODE) {
            if (!messageCapWarned) {
                messageCapWarned = true;
                messages.add(Message.warn("Message limit (" + FlowConfigDefaults.MAX_MESSAGES_PER_NODE
                    + ") reached for task '" + taskName + "', subsequent messages dropped"));
            }
            return message;
        }
        messages.add(message);
        return message;
    }

    /**
     * 添加结构化任务属性。
     *
     * @param key 属性键
     * @param value 属性值，可为 null
     * @return 当前任务节点
     * @throws IllegalArgumentException 如果 key 为 null 或空字符串
     */
    public TaskNode addAttribute(String key, Object value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Attribute key cannot be null or empty");
        }
        String normalizedKey = key.trim();
        return mutationGate.mutate(() -> addAttributeLocked(normalizedKey, value));
    }

    private synchronized TaskNode addAttributeLocked(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    /**
     * 添加结构化任务标签。
     *
     * @param tag 标签
     * @return 当前任务节点
     * @throws IllegalArgumentException 如果 tag 为 null 或空字符串
     */
    public TaskNode addTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag cannot be null or empty");
        }
        String normalizedTag = tag.trim();
        return mutationGate.mutate(() -> addTagLocked(normalizedTag));
    }

    private synchronized TaskNode addTagLocked(String normalizedTag) {
        if (!tags.contains(normalizedTag)) {
            tags.add(normalizedTag);
        }
        return this;
    }
    
    /**
     * 标记任务为完成状态
     * 
     * @throws IllegalStateException 如果任务不在RUNNING状态
     */
    public void complete() {
        mutationGate.mutate(() -> completeLocked());
    }

    private synchronized void completeLocked() {
        if (!tryCompleteLocked()) {
            throw new IllegalStateException(
                    "Cannot complete task that is not running. Current status: " + status);
        }
    }

    /**
     * 尝试标记任务为完成状态（幂等）。
     *
     * <p>与 {@link #complete()} 不同，任务已终止时不抛异常而是返回 {@code false}，
     * 供并发终止场景（如上下文清理与业务线程竞争收尾）安全调用。
     *
     * @return true 如果本次调用完成了 RUNNING → COMPLETED 转换
     */
    public boolean tryComplete() {
        return mutationGate.mutate(this::tryCompleteLocked);
    }

    synchronized boolean tryCompleteLocked() {
        if (status != TaskStatus.RUNNING) {
            return false;
        }

        // 先写时间戳后写 status：status 的 volatile 写保证无锁读方看到 COMPLETED 时时间戳必已就绪
        this.completedMillis = System.currentTimeMillis();
        this.completedNanos = System.nanoTime();
        this.status = TaskStatus.COMPLETED;
        return true;
    }

    /**
     * 获取自身执行时长（纳秒）
     * 若未完成，返回当前时间与开始时间的差值
     *
     * @return 当前节点自身耗时，单位为纳秒
     */
    public long getSelfDurationNanos() {
        if (completedNanos != null) {
            return completedNanos - createdNanos;
        }
        return System.nanoTime() - createdNanos;
    }

    /**
     * 获取自身执行时长（毫秒）
     *
     * @return 当前节点自身耗时，单位为毫秒
     */
    public long getSelfDurationMillis() {
        return getSelfDurationNanos() / 1_000_000;
    }

    /**
     * 获取累计执行时长（纳秒）= 自身 + 所有子节点累计
     *
     * @return 当前节点及全部后代节点耗时之和，单位为纳秒
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
     *
     * @return 当前节点及全部后代节点耗时之和，单位为毫秒
     */
    public long getAccumulatedDurationMillis() {
        return getAccumulatedDurationNanos() / 1_000_000;
    }
    
    /**
     * 标记任务为失败状态
     * 
     * @throws IllegalStateException 如果任务不在RUNNING状态
     */
    public void fail() {
        mutationGate.mutate(() -> failLocked(null));
    }

    /**
     * 尝试标记任务为失败状态（幂等）。
     *
     * @return true 如果本次调用完成了 RUNNING → FAILED 转换
     */
    public boolean tryFail() {
        return mutationGate.mutate(() -> {
            return tryFailLocked();
        });
    }

    synchronized boolean tryFailLocked() {
        return tryFailLocked(null);
    }

    synchronized boolean tryFailLocked(Message errorMessage) {
        if (status != TaskStatus.RUNNING) {
            return false;
        }

        if (errorMessage != null) {
            appendMessageLocked(errorMessage);
        }

        // 先写时间戳后写 status，理由同 tryComplete()
        this.completedMillis = System.currentTimeMillis();
        this.completedNanos = System.nanoTime();
        this.status = TaskStatus.FAILED;
        return true;
    }

    /**
     * 记录由 Session 终态发布的错误，然后幂等尝试收尾根任务。
     *
     * <p>Session 与 root 的生命周期允许短暂分离：root 可先独立终止，但随后的 Session 错误仍是
     * 必须保留的诊断事实。两步必须在同一 node monitor 内完成，且不得重新进入 tree gate。
     *
     * @param errorMessage 已完成校验的 Session 错误消息
     */
    synchronized void recordSessionFailureLocked(Message errorMessage) {
        appendMessageLocked(Objects.requireNonNull(errorMessage, "errorMessage"));
        tryFailLocked();
    }

    /**
     * 尝试标记任务为失败状态并添加错误消息（幂等）。
     *
     * <p>任务已终止时不添加消息、不抛异常，避免在已完成的任务上留下"幽灵"错误记录。
     *
     * @param errorContent 错误消息内容
     * @return true 如果本次调用完成了状态转换并记录了消息
     * @throws IllegalArgumentException 如果errorContent为null或空字符串
     */
    public boolean tryFail(String errorContent) {
        Message errorMessage = Message.error(errorContent);
        return mutationGate.mutate(() -> tryFailLocked(errorMessage));
    }

    /**
     * 标记任务为失败状态并添加错误消息
     *
     * <p>先校验状态再产生副作用：任务已终止时直接抛异常，不会残留错误消息。
     *
     * @param errorContent 错误消息内容
     * @throws IllegalStateException 如果任务不在RUNNING状态
     * @throws IllegalArgumentException 如果errorContent为null或空字符串
     */
    public void fail(String errorContent) {
        Message errorMessage = Message.error(errorContent);
        mutationGate.mutate(() -> failLocked(errorMessage));
    }

    /**
     * 根据异常标记任务为失败状态
     *
     * <p>先校验状态再产生副作用：任务已终止时直接抛异常，不会残留错误消息。
     *
     * @param throwable 异常对象
     * @throws IllegalStateException 如果任务不在RUNNING状态
     * @throws IllegalArgumentException 如果throwable为null
     */
    public void fail(Throwable throwable) {
        Message errorMessage = Message.error(throwable);
        mutationGate.mutate(() -> failLocked(errorMessage));
    }

    private synchronized void failLocked(Message errorMessage) {
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException("Cannot fail task that is not running. Current status: " + status);
        }
        tryFailLocked(errorMessage);
    }
    
    /**
     * 获取节点唯一标识
     *
     * <p>采用延迟初始化：UUID 在首次调用时通过双重检查锁生成并缓存，
     * 之后多次调用返回同一值（保证 equals/hashCode 与序列化的稳定性）。
     *
     * @return 节点ID（UUID 字符串）
     */
    public String getNodeId() {
        String id = nodeId;
        if (id == null) {
            synchronized (this) {
                id = nodeId;
                if (id == null) {
                    id = UUID.randomUUID().toString();
                    nodeId = id;
                }
            }
        }
        return id;
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
     * 获取子节点列表（只读快照）
     * 
     * @return 子节点的不可修改列表
     */
    public synchronized List<TaskNode> getChildren() {
        return Collections.unmodifiableList(new ArrayList<>(children));
    }

    /**
     * 返回 capture 线性化点内稳定的 child 数量，避免节点预算耗尽前复制整份 children。
     *
     * <p>仅同包 capturer 在持有全树 write gate 时调用；同步只保护本节点容器，不获取 tree gate。
     *
     * @return 当前 child 数量
     */
    synchronized int captureChildCount() {
        return children.size();
    }

    /**
     * 按稳定序号读取 capture 所需 child，使遍历工作量受 maxNodes 约束。
     *
     * @param index 已在 capture 时读取范围内的 child 序号
     * @return 对应 child
     */
    synchronized TaskNode captureChildAt(int index) {
        return children.get(index);
    }
    
    /**
     * 获取消息列表（只读快照）
     * 
     * @return 消息的不可修改列表
     */
    public synchronized List<Message> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /**
     * 获取任务属性（只读快照）。
     *
     * @return 任务属性的不可修改映射
     */
    public synchronized Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /**
     * 获取任务标签（只读快照）。
     *
     * @return 任务标签的不可修改列表
     */
    public synchronized List<String> getTags() {
        return Collections.unmodifiableList(new ArrayList<>(tags));
    }

    /**
     * 在复制 payload 前返回三类容器的稳定数量，供 capturer 原子预检总预算。
     *
     * <p>调用方持有全树 write gate，因此数量预检与随后的防御性复制之间不存在合法 mutation。
     *
     * @return messages、attributes、tags 的当前数量
     */
    synchronized CapturePayloadSizes capturePayloadSizes() {
        return new CapturePayloadSizes(messages.size(), attributes.size(), tags.size());
    }

    /** capture 专用容器计数，不暴露 mutable collection 或锁能力。 */
    record CapturePayloadSizes(int messages, int attributes, int tags) {
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
    public synchronized boolean isLeaf() {
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
        return Objects.equals(getNodeId(), taskNode.getNodeId());
    }
    
    @Override
    public int hashCode() {
        // 直接取 nodeId 的 hashCode，避免 Objects.hash 的 varargs 数组分配
        return getNodeId().hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("TaskNode{path='%s', status=%s, thread='%s'}", 
                taskPath, status, threadName);
    }
}
