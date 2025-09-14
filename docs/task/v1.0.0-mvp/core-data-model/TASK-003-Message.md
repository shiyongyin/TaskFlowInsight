# TASK-003: Message 消息模型实现

## 任务背景

Message是TaskFlow Insight中用于记录任务执行过程中产生的日志、调试信息、状态变更等消息的核心数据模型。每个TaskNode可以包含多个Message，用于详细记录任务执行的详细信息。Message需要支持不同类型的消息（如INFO、DEBUG、ERROR等），并提供高精度的时间戳记录。

## 目标

1. 实现Message数据模型类，支持消息内容、类型、时间戳等核心属性
2. 设计合理的Message类型枚举，覆盖常见的消息场景
3. 提供线程安全的Message创建和访问机制
4. 确保Message对象的不可变性，避免并发修改问题
5. 支持消息的高效存储和检索

## 实现方案

### 3.1 Message类设计

```java
package com.syy.taskflowinsight.model;

import java.util.Objects;

/**
 * 任务执行过程中的消息记录
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class Message {
    
    private final String messageId;      // 消息ID (UUID)
    private final String content;        // 消息内容
    private final MessageType type;      // 消息类型
    private final long timestampNano;    // 时间戳(纳秒精度)
    private final long timestampMillis;  // 时间戳(毫秒精度，用于显示)
    
    /**
     * 构造函数
     * 
     * @param messageId 消息ID
     * @param content 消息内容
     * @param type 消息类型
     * @param timestampNano 纳秒时间戳
     * @param timestampMillis 毫秒时间戳
     */
    public Message(String messageId, String content, MessageType type, 
                   long timestampNano, long timestampMillis) {
        this.messageId = Objects.requireNonNull(messageId, "messageId cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.timestampNano = timestampNano;
        this.timestampMillis = timestampMillis;
    }
    
    /**
     * 创建新的Message实例
     * 
     * @param content 消息内容
     * @param type 消息类型
     * @return Message实例
     */
    public static Message create(String content, MessageType type) {
        long currentNano = System.nanoTime();
        long currentMillis = System.currentTimeMillis();
        String messageId = java.util.UUID.randomUUID().toString();
        return new Message(messageId, content, type, currentNano, currentMillis);
    }
    
    /**
     * 创建INFO类型消息
     * 
     * @param content 消息内容
     * @return Message实例
     */
    public static Message info(String content) {
        return create(content, MessageType.INFO);
    }
    
    /**
     * 创建DEBUG类型消息
     * 
     * @param content 消息内容
     * @return Message实例
     */
    public static Message debug(String content) {
        return create(content, MessageType.DEBUG);
    }
    
    /**
     * 创建ERROR类型消息
     * 
     * @param content 消息内容
     * @return Message实例
     */
    public static Message error(String content) {
        return create(content, MessageType.ERROR);
    }
    
    /**
     * 创建WARN类型消息
     * 
     * @param content 消息内容
     * @return Message实例
     */
    public static Message warn(String content) {
        return create(content, MessageType.WARN);
    }
    
    // Getters
    public String getMessageId() { return messageId; }
    public String getContent() { return content; }
    public MessageType getType() { return type; }
    public long getTimestampNano() { return timestampNano; }
    public long getTimestampMillis() { return timestampMillis; }
    
    /**
     * 获取相对于基准时间的纳秒偏移量
     * 
     * @param baseNano 基准时间(纳秒)
     * @return 偏移量(纳秒)
     */
    public long getRelativeNanos(long baseNano) {
        return timestampNano - baseNano;
    }
    
    /**
     * 获取格式化的时间戳字符串
     * 
     * @return 格式化时间戳
     */
    public String getFormattedTimestamp() {
        return java.time.Instant.ofEpochMilli(timestampMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Message message = (Message) obj;
        return Objects.equals(messageId, message.messageId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }
    
    @Override
    public String toString() {
        return String.format("Message{id='%s', type=%s, content='%s', time=%s}", 
                messageId.substring(0, 8), type, content, getFormattedTimestamp());
    }
}
```

### 3.2 MessageType枚举设计

```java
package com.syy.taskflowinsight.model;

/**
 * 消息类型枚举
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public enum MessageType {
    
    /**
     * 信息类消息 - 一般性信息记录
     */
    INFO("INFO", 1),
    
    /**
     * 调试类消息 - 用于调试和排查问题
     */
    DEBUG("DEBUG", 0),
    
    /**
     * 警告类消息 - 需要注意但不影响正常执行
     */
    WARN("WARN", 2),
    
    /**
     * 错误类消息 - 执行过程中发生的错误
     */
    ERROR("ERROR", 3),
    
    /**
     * 性能类消息 - 性能相关的度量信息
     */
    PERFORMANCE("PERFORMANCE", 1),
    
    /**
     * 状态变更消息 - 任务状态变化记录
     */
    STATE_CHANGE("STATE_CHANGE", 1);
    
    private final String displayName;
    private final int level;  // 0-DEBUG, 1-INFO/PERF/STATE, 2-WARN, 3-ERROR
    
    MessageType(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getLevel() {
        return level;
    }
    
    /**
     * 判断是否为错误级别消息
     * 
     * @return true if error level
     */
    public boolean isError() {
        return level >= 3;
    }
    
    /**
     * 判断是否为警告及以上级别消息
     * 
     * @return true if warning level or above
     */
    public boolean isWarnOrAbove() {
        return level >= 2;
    }
    
    /**
     * 判断是否为调试级别消息
     * 
     * @return true if debug level
     */
    public boolean isDebug() {
        return level == 0;
    }
}
```

### 3.3 消息集合管理

```java
package com.syy.taskflowinsight.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 消息集合管理器 - 线程安全的消息存储和查询
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
public final class MessageCollection {
    
    private final CopyOnWriteArrayList<Message> messages;
    
    public MessageCollection() {
        this.messages = new CopyOnWriteArrayList<>();
    }
    
    /**
     * 添加消息
     * 
     * @param message 消息对象
     */
    public void add(Message message) {
        if (message != null) {
            messages.add(message);
        }
    }
    
    /**
     * 获取所有消息 (只读副本)
     * 
     * @return 消息列表
     */
    public List<Message> getAll() {
        return new ArrayList<>(messages);
    }
    
    /**
     * 按类型过滤消息
     * 
     * @param type 消息类型
     * @return 过滤后的消息列表
     */
    public List<Message> getByType(MessageType type) {
        return messages.stream()
                .filter(msg -> msg.getType() == type)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取错误消息
     * 
     * @return 错误消息列表
     */
    public List<Message> getErrors() {
        return messages.stream()
                .filter(msg -> msg.getType().isError())
                .collect(Collectors.toList());
    }
    
    /**
     * 获取警告及以上级别消息
     * 
     * @return 警告及以上消息列表
     */
    public List<Message> getWarningsAndAbove() {
        return messages.stream()
                .filter(msg -> msg.getType().isWarnOrAbove())
                .collect(Collectors.toList());
    }
    
    /**
     * 获取消息总数
     * 
     * @return 消息数量
     */
    public int size() {
        return messages.size();
    }
    
    /**
     * 判断是否为空
     * 
     * @return true if empty
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }
    
    /**
     * 清空所有消息
     */
    public void clear() {
        messages.clear();
    }
    
    /**
     * 获取最新的消息
     * 
     * @param count 获取数量
     * @return 最新消息列表
     */
    public List<Message> getLatest(int count) {
        int size = messages.size();
        if (count >= size) {
            return getAll();
        }
        
        return messages.subList(size - count, size);
    }
}
```

## 测试标准

### 4.1 单元测试要求

1. **Message对象创建测试**
   - 验证不同类型Message的正确创建
   - 验证时间戳的正确性和精度
   - 验证null参数的异常处理

2. **消息类型测试**
   - 验证MessageType枚举的完整性
   - 验证级别判断方法的正确性
   - 验证显示名称的格式

3. **MessageCollection测试**
   - 验证线程安全的消息添加和读取
   - 验证过滤功能的正确性
   - 验证集合操作的性能

4. **时间戳测试**
   - 验证纳秒和毫秒时间戳的一致性
   - 验证相对时间计算的准确性
   - 验证格式化输出的正确性

### 4.2 性能测试要求

1. **消息创建性能**
   - 单次消息创建耗时 < 1微秒
   - 并发创建1000条消息耗时 < 10毫秒

2. **消息存储性能**
   - 添加单条消息耗时 < 0.5微秒
   - 10000条消息存储内存占用 < 2MB

3. **消息查询性能**
   - 类型过滤查询耗时 < 1毫秒 (1000条消息)
   - getAll()操作耗时 < 0.5毫秒 (1000条消息)

### 4.3 并发测试要求

1. **多线程消息添加**
   - 10个线程并发添加消息，无数据丢失
   - 消息顺序与添加时间戳一致

2. **读写并发测试**
   - 写入线程和读取线程并发执行，无阻塞
   - 读取操作获得一致性快照

## 验收标准

### 5.1 功能验收

- [ ] Message类正确实现所有属性和方法
- [ ] MessageType枚举包含所有必要类型和级别判断
- [ ] MessageCollection提供线程安全的消息管理
- [ ] 静态工厂方法正确创建各类型消息
- [ ] 时间戳功能正确实现纳秒和毫秒精度

### 5.2 质量验收

- [ ] 所有类实现final，确保不可变性
- [ ] 空值处理得当，避免NullPointerException
- [ ] 线程安全机制正确实现
- [ ] equals()和hashCode()正确实现
- [ ] toString()提供有意义的输出

### 5.3 性能验收

- [ ] 消息创建性能满足要求 (< 1微秒)
- [ ] 内存占用满足要求 (1000条消息 < 200KB)
- [ ] 并发性能满足要求 (无锁等待)
- [ ] 查询性能满足要求 (< 1毫秒)

### 5.4 测试验收

- [ ] 单元测试覆盖率 ≥ 95%
- [ ] 所有测试用例通过
- [ ] 性能测试通过
- [ ] 并发测试无数据竞争

## 依赖关系

- **前置依赖**: 无
- **后置依赖**: TASK-002 (TaskNode需要使用MessageCollection)
- **相关任务**: TASK-004 (完整的枚举定义)

## 预计工期

- **开发时间**: 1.5天
- **测试时间**: 1天  
- **总计**: 2.5天

## 风险识别

1. **性能风险**: CopyOnWriteArrayList在大量写入时性能下降
   - **缓解措施**: 考虑使用其他并发集合或批量操作优化

2. **内存风险**: 消息对象累积可能导致内存泄漏
   - **缓解措施**: 实现消息数量限制和自动清理机制

3. **时间精度风险**: 不同系统的纳秒精度可能不同
   - **缓解措施**: 提供降级机制和精度检测