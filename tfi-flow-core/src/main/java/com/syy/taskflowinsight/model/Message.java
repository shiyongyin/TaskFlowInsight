package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageSeverity;
import com.syy.taskflowinsight.enums.MessageType;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息记录类
 * 记录任务执行过程中的信息和错误消息，采用不可变设计
 * 
 * <p>设计原则：
 * <ul>
 *   <li>不可变对象 - 业务字段均为final；messageId 延迟生成后即固定，对外表现为不可变</li>
 *   <li>双时间戳 - 毫秒级显示时间，纳秒级精确时间</li>
 *   <li>工厂方法 - 通过静态方法创建实例</li>
 *   <li>UUID标识 - 首次访问时才生成，确保消息唯一性的同时避免热路径开销</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
public final class Message {
    
    // 延迟生成：messageId 仅用于 equals/hashCode，绝大多数消息无需该 ID，
    // 推迟到首次访问再生成可避免每条消息都调用 SecureRandom（UUID.randomUUID）
    private volatile String messageId;
    private final MessageType type;          // 可能为null（使用自定义标签时）
    private final MessageSeverity severity;
    private final String content;
    private final long timestampMillis;
    private final long timestampNanos;
    private final String threadName;
    private final String customLabel;        // 自定义标签，可能为null
    
    /**
     * 私有构造函数（MessageType版本），强制使用工厂方法创建实例
     * 
     * @param type 消息类型
     * @param content 消息内容
     * @param severity 消息严重程度；与业务类型正交，不用于改变 V1 导出结构
     */
    private Message(MessageType type, String content, MessageSeverity severity) {
        this.type = Objects.requireNonNull(type, "Message type cannot be null");
        this.severity = Objects.requireNonNull(severity, "Message severity cannot be null");
        this.content = Objects.requireNonNull(content, "Message content cannot be null");
        this.customLabel = null;
        
        this.timestampMillis = System.currentTimeMillis();
        this.timestampNanos = System.nanoTime();
        this.threadName = Thread.currentThread().getName();
    }
    
    /**
     * 私有构造函数（自定义标签版本），强制使用工厂方法创建实例
     * 
     * @param customLabel 自定义标签
     * @param content 消息内容
     * @param severity 消息严重程度；自定义标签入口固定为 INFO
     */
    private Message(String customLabel, String content, MessageSeverity severity) {
        this.type = null;
        this.severity = Objects.requireNonNull(severity, "Message severity cannot be null");
        this.content = Objects.requireNonNull(content, "Message content cannot be null");
        this.customLabel = Objects.requireNonNull(customLabel, "Custom label cannot be null");
        
        this.timestampMillis = System.currentTimeMillis();
        this.timestampNanos = System.nanoTime();
        this.threadName = Thread.currentThread().getName();
    }
    
    /**
     * 使用MessageType创建消息
     * 
     * @param content 消息内容
     * @param type 消息类型
     * @return 消息实例
     * @throws IllegalArgumentException 如果参数为null或空字符串
     */
    public static Message withType(String content, MessageType type) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Message type cannot be null");
        }
        return new Message(type, content.trim(), MessageSeverity.INFO);
    }
    
    /**
     * 使用自定义标签创建消息
     * 
     * @param content 消息内容
     * @param customLabel 自定义标签
     * @return 消息实例
     * @throws IllegalArgumentException 如果参数为null或空字符串
     */
    public static Message withLabel(String content, String customLabel) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        if (customLabel == null || customLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Custom label cannot be null or empty");
        }
        return new Message(customLabel.trim(), content.trim(), MessageSeverity.INFO);
    }
    
    /**
     * 创建信息类消息
     * 
     * @param content 消息内容
     * @return 信息类消息实例
     * @throws IllegalArgumentException 如果content为null或空字符串
     */
    public static Message info(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        return new Message(MessageType.PROCESS, content.trim(), MessageSeverity.INFO);
    }
    
    /**
     * 创建调试类消息
     * @param content 消息内容
     * @return 调试类消息实例
     */
    public static Message debug(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        return new Message(MessageType.METRIC, content.trim(), MessageSeverity.DEBUG);
    }
    
    /**
     * 创建错误类消息
     * 
     * @param content 错误消息内容
     * @return 错误类消息实例
     * @throws IllegalArgumentException 如果content为null或空字符串
     */
    public static Message error(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        return new Message(MessageType.ALERT, content.trim(), MessageSeverity.ERROR);
    }
    
    /**
     * 创建警告类消息
     * @param content 警告消息内容
     * @return 警告类消息实例
     */
    public static Message warn(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be null or empty");
        }
        // 警告语义应归为 ALERT 级别
        return new Message(MessageType.ALERT, content.trim(), MessageSeverity.WARN);
    }
    
    /**
     * 根据异常创建错误类消息
     * 
     * @param throwable 异常对象
     * @return 错误类消息实例
     * @throws IllegalArgumentException 如果throwable为null
     */
    public static Message error(Throwable throwable) {
        if (throwable == null) {
            throw new IllegalArgumentException("Throwable cannot be null");
        }
        
        String content = throwable.getMessage();
        if (content == null || content.trim().isEmpty()) {
            content = throwable.getClass().getSimpleName();
        }
        
        return new Message(MessageType.ALERT, content.trim(), MessageSeverity.ERROR);
    }
    
    /**
     * 获取消息唯一标识
     *
     * <p>采用延迟初始化：UUID 在首次调用时通过双重检查锁生成并缓存，
     * 之后多次调用返回同一值（保证 equals/hashCode 的稳定性）。
     *
     * @return 消息ID（UUID 字符串）
     */
    public String getMessageId() {
        String id = messageId;
        if (id == null) {
            synchronized (this) {
                id = messageId;
                if (id == null) {
                    id = UUID.randomUUID().toString();
                    messageId = id;
                }
            }
        }
        return id;
    }
    
    /**
     * 获取消息类型
     * 
     * @return 消息类型
     */
    public MessageType getType() {
        return type;
    }

    /**
     * 获取消息严重程度。
     *
     * <p>该值用于 Java model 内的语义判断；V1 JSON/Map exporter 刻意不输出此字段，
     * 以保持既有 wire shape。
     *
     * @return 消息严重程度，始终非 null
     */
    public MessageSeverity getSeverity() {
        return severity;
    }
    
    /**
     * 获取消息内容
     * 
     * @return 消息内容
     */
    public String getContent() {
        return content;
    }
    
    /**
     * 获取创建时间（毫秒时间戳）
     * 
     * @return 毫秒级时间戳
     */
    public long getTimestampMillis() {
        return timestampMillis;
    }
    
    /**
     * 获取创建时间（纳秒时间戳）
     * 
     * @return 纳秒级时间戳
     */
    public long getTimestampNanos() {
        return timestampNanos;
    }
    
    /**
     * 获取创建消息时的线程名称
     * 
     * @return 线程名称
     */
    public String getThreadName() {
        return threadName;
    }
    
    /**
     * 获取自定义标签
     * 
     * @return 自定义标签，如果没有则返回null
     */
    public String getCustomLabel() {
        return customLabel;
    }
    
    /**
     * 判断是否有自定义标签
     * 
     * @return true 如果有自定义标签
     */
    public boolean hasCustomLabel() {
        return customLabel != null;
    }
    
    /**
     * 获取显示标签（优先使用自定义标签，其次使用MessageType描述）
     * 
     * @return 显示标签
     */
    public String getDisplayLabel() {
        if (hasCustomLabel()) {
            return customLabel;
        } else if (type != null) {
            return type.getDisplayName();
        } else {
            return "未知";
        }
    }
    
    /**
     * 判断是否为异常提示消息
     * 
     * @return true 如果是异常提示消息
     */
    public boolean isAlert() {
        return type != null && type.isAlert();
    }
    
    /**
     * 判断是否为业务流程消息
     * 
     * @return true 如果是业务流程消息
     */
    public boolean isProcess() {
        return type != null && type.isProcess();
    }
    
    /**
     * 判断是否为核心指标消息
     * 
     * @return true 如果是核心指标消息
     */
    public boolean isMetric() {
        return type != null && type.isMetric();
    }
    
    /**
     * 判断是否为变更记录消息
     * 
     * @return true 如果是变更记录消息
     */
    public boolean isChange() {
        return type != null && type.isChange();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        Message message = (Message) obj;
        return Objects.equals(getMessageId(), message.getMessageId());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getMessageId());
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s: %s", 
                getDisplayLabel(), 
                threadName, 
                content);
    }
}
