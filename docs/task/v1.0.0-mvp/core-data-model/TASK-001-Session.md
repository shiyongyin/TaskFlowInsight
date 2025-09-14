# TASK-001: Session会话模型实现

**任务ID**: TASK-001  
**任务类别**: 核心数据模型  
**优先级**: P0 (最高)  
**预估工期**: 1天  
**依赖任务**: 无  
**负责人**: 核心开发工程师  

## 📋 任务背景

根据架构师的技术规格，Session是TaskFlow Insight的核心数据模型之一，表示一个完整的任务执行上下文。每个线程可以有多个历史会话，但同时只有一个活跃会话。Session是整个任务追踪系统的顶层容器，包含从根任务开始到结束的完整执行树。

**为什么需要Session？**
- 提供任务执行的完整上下文边界
- 支持跨线程的会话查询和历史回放
- 作为JSON导出和控制台输出的数据源
- 支持多会话管理（每线程保留最近N个会话）

## 🎯 任务目标

实现完整的Session会话模型，包括：

1. **基础数据结构**: sessionId、threadId、时间戳、状态等核心字段
2. **生命周期管理**: 会话的创建、运行、结束状态转换
3. **业务方法**: 时长计算、状态查询等核心业务逻辑
4. **线程安全**: 支持跨线程安全读取
5. **序列化支持**: 为JSON导出提供序列化基础

## 🛠️ 具体做法

### 1. 创建Session核心类

**文件位置**: `src/main/java/com/syy/taskflowinsight/model/Session.java`

```java
package com.syy.taskflowinsight.model;

import java.util.UUID;
import java.util.Objects;

/**
 * 任务会话，表示一个完整的任务执行上下文
 * 从根任务开始到结束的完整执行树
 * 
 * 线程安全：读操作线程安全，写操作仅在创建线程内进行
 */
public final class Session {
    
    // 基础标识信息
    private final String sessionId;        // UUID，全局唯一标识
    private final long threadId;           // 线程ID
    private final String threadName;       // 线程名称（用于调试）
    
    // 时间信息  
    private final long createdAt;          // 创建时间(毫秒)，使用System.currentTimeMillis()
    private volatile long endedAt;         // 结束时间(毫秒), 0表示进行中
    
    // 任务树根节点
    private volatile TaskNode root;        // 根任务节点，会话的入口点，使用volatile保证可见性
    
    // 会话状态
    private volatile SessionStatus status; // 会话状态，使用volatile保证可见性
    
    /**
     * 构造函数 - 创建新会话
     * @param threadId 线程ID
     */
    public Session(long threadId) {
        this.sessionId = UUID.randomUUID().toString();
        this.threadId = threadId;
        this.threadName = Thread.currentThread().getName();
        this.createdAt = System.currentTimeMillis();
        this.endedAt = 0L;  // 0表示进行中
        this.status = SessionStatus.RUNNING;
        // root在第一个任务创建时设置
    }
    
    /**
     * 结束会话
     * 只能调用一次，重复调用无效
     */
    public synchronized void end() {
        if (endedAt == 0L) {  // 防止重复结束
            this.endedAt = System.currentTimeMillis();
            this.status = SessionStatus.COMPLETED;
        }
    }
    
    /**
     * 设置根任务节点
     * @param root 根任务节点
     */
    public void setRoot(TaskNode root) {
        this.root = Objects.requireNonNull(root, "root cannot be null");
    }
    
    /**
     * 检查会话是否活跃（未结束）
     * @return true 如果会话还在进行中
     */
    public boolean isActive() {
        return endedAt == 0L;
    }
    
    /**
     * 获取会话持续时间
     * @return 持续时间(毫秒)，如果未结束则返回当前持续时间
     */
    public long getDurationMs() {
        if (isActive()) {
            return System.currentTimeMillis() - createdAt;
        } else {
            return endedAt - createdAt;
        }
    }
    
    // === Getter方法 ===
    public String getSessionId() { return sessionId; }
    public long getThreadId() { return threadId; }
    public String getThreadName() { return threadName; }
    public long getCreatedAt() { return createdAt; }
    public long getEndedAt() { return endedAt; }
    public TaskNode getRoot() { return root; }
    public SessionStatus getStatus() { return status; }
    
    // === 对象基础方法 ===
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Session)) return false;
        Session session = (Session) obj;
        return Objects.equals(sessionId, session.sessionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
    
    @Override
    public String toString() {
        return String.format("Session{id=%s, thread=%d, status=%s, duration=%dms}", 
                           sessionId, threadId, status, getDurationMs());
    }
}
```

### 2. 创建SessionStatus枚举

**文件位置**: `src/main/java/com/syy/taskflowinsight/model/SessionStatus.java`

```java
package com.syy.taskflowinsight.model;

/**
 * 会话状态枚举
 */
public enum SessionStatus {
    
    /**
     * 运行中 - 会话正在执行，有活动任务
     */
    RUNNING,
    
    /**
     * 已完成 - 会话正常结束，所有任务都已完成
     */
    COMPLETED,
    
    /**
     * 错误结束 - 会话因异常而结束
     */
    ERROR
}
```

### 3. 创建工具类（如需要）

**文件位置**: `src/main/java/com/syy/taskflowinsight/util/SessionUtils.java`

```java
package com.syy.taskflowinsight.util;

import com.syy.taskflowinsight.model.Session;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Session相关工具类
 */
public final class SessionUtils {
    
    private SessionUtils() {} // 工具类禁止实例化
    
    /**
     * 生成简短的会话ID（用于日志显示）
     * @param session 会话对象
     * @return 8位短ID
     */
    public static String getShortId(Session session) {
        if (session == null || session.getSessionId() == null) {
            return "unknown";
        }
        String fullId = session.getSessionId();
        return fullId.length() > 8 ? fullId.substring(0, 8) : fullId;
    }
    
    /**
     * 格式化会话信息（用于调试）
     * @param session 会话对象
     * @return 格式化字符串
     */
    public static String formatSessionInfo(Session session) {
        if (session == null) {
            return "Session{null}";
        }
        
        return String.format("Session{id=%s, thread=%s(%d), status=%s, duration=%dms}",
            getShortId(session),
            session.getThreadName(),
            session.getThreadId(),
            session.getStatus(),
            session.getDurationMs()
        );
    }
}
```

## 🧪 测试标准

### 1. 单元测试文件

**文件位置**: `src/test/java/com/syy/taskflowinsight/model/SessionTest.java`

```java
package com.syy.taskflowinsight.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    
    private Session session;
    private long threadId;
    
    @BeforeEach
    void setUp() {
        threadId = Thread.currentThread().getId();
        session = new Session(threadId);
    }
    
    @Test
    void testSessionCreation() {
        // 验证基础属性
        assertNotNull(session.getSessionId());
        assertEquals(threadId, session.getThreadId());
        assertNotNull(session.getThreadName());
        assertTrue(session.getCreatedAt() > 0);
        assertEquals(0L, session.getEndedAt());
        assertEquals(SessionStatus.RUNNING, session.getStatus());
        
        // 验证初始状态
        assertTrue(session.isActive());
        assertTrue(session.getDurationMs() >= 0);
        assertNull(session.getRoot()); // 初始时没有根节点
    }
    
    @Test
    void testSessionEnd() {
        // 记录结束前时间
        long beforeEnd = System.currentTimeMillis();
        
        // 结束会话
        session.end();
        
        // 验证结束状态
        assertFalse(session.isActive());
        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertTrue(session.getEndedAt() >= beforeEnd);
        assertTrue(session.getEndedAt() >= session.getCreatedAt());
        
        // 验证持续时间计算
        long expectedDuration = session.getEndedAt() - session.getCreatedAt();
        assertEquals(expectedDuration, session.getDurationMs());
    }
    
    @Test
    void testSessionEndIdempotent() {
        // 第一次结束
        session.end();
        long firstEndTime = session.getEndedAt();
        SessionStatus firstStatus = session.getStatus();
        
        // 等待一小段时间再次结束
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 第二次结束 - 应该无效
        session.end();
        
        // 验证状态未改变
        assertEquals(firstEndTime, session.getEndedAt());
        assertEquals(firstStatus, session.getStatus());
    }
    
    @Test
    void testSetRoot() {
        // 创建模拟的TaskNode（简化版本，实际依赖TASK-002）
        TaskNode mockRoot = new TaskNode("root", 0);
        
        // 设置根节点
        session.setRoot(mockRoot);
        
        // 验证设置成功
        assertEquals(mockRoot, session.getRoot());
    }
    
    @Test
    void testSetRootNull() {
        // 验证null检查
        assertThrows(NullPointerException.class, () -> {
            session.setRoot(null);
        });
    }
    
    @Test
    void testDurationCalculation() throws InterruptedException {
        // 记录开始时间
        long start = session.getCreatedAt();
        
        // 等待一段时间
        Thread.sleep(100);
        
        // 验证运行中的持续时间
        long runningDuration = session.getDurationMs();
        assertTrue(runningDuration >= 100);
        assertTrue(runningDuration < 200); // 合理的上界
        
        // 结束会话
        session.end();
        
        // 验证结束后的持续时间
        long completedDuration = session.getDurationMs();
        assertEquals(session.getEndedAt() - start, completedDuration);
        assertTrue(completedDuration >= 100);
    }
    
    @Test
    void testEqualsAndHashCode() {
        // 创建另一个会话
        Session other = new Session(threadId);
        
        // 验证不同会话不相等（UUID不同）
        assertNotEquals(session, other);
        assertNotEquals(session.hashCode(), other.hashCode());
        
        // 验证相同对象相等
        assertEquals(session, session);
        assertEquals(session.hashCode(), session.hashCode());
        
        // 验证null和其他类型对象
        assertNotEquals(session, null);
        assertNotEquals(session, "not a session");
    }
    
    @Test
    void testToString() {
        String str = session.toString();
        
        // 验证包含关键信息
        assertTrue(str.contains("Session"));
        assertTrue(str.contains(session.getSessionId()));
        assertTrue(str.contains(String.valueOf(session.getThreadId())));
        assertTrue(str.contains(session.getStatus().toString()));
        assertTrue(str.contains(session.getDurationMs() + "ms"));
    }
    
    @Test
    void testConcurrentRead() throws InterruptedException {
        // 这是一个并发读取的基础测试
        // 更完整的并发测试在集成测试中进行
        
        final boolean[] success = {true};
        
        // 启动一个线程读取会话信息
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    String id = session.getSessionId();
                    boolean active = session.isActive();
                    long duration = session.getDurationMs();
                    
                    // 验证读取的数据一致性
                    assertNotNull(id);
                    assertTrue(duration >= 0);
                }
            } catch (Exception e) {
                success[0] = false;
            }
        });
        
        reader.start();
        
        // 主线程修改会话状态
        Thread.sleep(50);
        session.end();
        
        reader.join();
        assertTrue(success[0], "Concurrent read should succeed");
    }
}
```

### 2. 测试用例覆盖清单

- [ ] **基础构造测试**: 验证Session对象正确创建
- [ ] **状态转换测试**: 验证RUNNING → COMPLETED状态转换
- [ ] **时长计算测试**: 验证运行中和结束后的时长计算
- [ ] **根节点设置测试**: 验证setRoot方法的正确性
- [ ] **异常处理测试**: 验证null参数的异常处理
- [ ] **幂等性测试**: 验证重复调用end()的幂等性
- [ ] **并发读取测试**: 验证跨线程读取的安全性
- [ ] **对象方法测试**: 验证equals、hashCode、toString方法

## ✅ 验收标准

### 1. 功能完整性
- [ ] **Session类实现完整**: 包含所有架构师规定的字段和方法
- [ ] **SessionStatus枚举正确**: 包含RUNNING、COMPLETED、ERROR状态
- [ ] **生命周期管理**: 正确处理创建、运行、结束状态转换
- [ ] **时长计算准确**: 纳秒级精度，误差<1毫秒
- [ ] **线程安全读取**: 支持跨线程安全读取会话信息

### 2. 代码质量
- [ ] **代码规范**: 遵循开发规范，命名清晰，注释完整
- [ ] **异常处理**: 正确处理null参数和边界条件
- [ ] **线程安全**: volatile字段正确使用，同步方法正确实现
- [ ] **内存效率**: 对象大小合理，无不必要的字段
- [ ] **可维护性**: 代码结构清晰，易于理解和扩展

### 3. 测试覆盖
- [ ] **单元测试覆盖率**: > 95%
- [ ] **测试用例完整**: 覆盖所有公开方法和边界条件  
- [ ] **并发测试**: 验证多线程环境下的正确性
- [ ] **性能测试**: 验证对象创建和方法调用的性能开销

### 4. 集成兼容
- [ ] **TaskNode集成**: 能够正确设置和获取根任务节点
- [ ] **序列化兼容**: 为JSON序列化提供必要的getter方法
- [ ] **调试友好**: toString方法提供有用的调试信息
- [ ] **扩展预留**: 设计考虑未来功能扩展的兼容性

### 5. 性能要求
- [ ] **创建开销**: Session对象创建时间 < 10微秒
- [ ] **方法调用开销**: getter方法调用时间 < 100纳秒
- [ ] **内存占用**: 单个Session对象内存占用 < 1KB
- [ ] **GC友好**: 避免不必要的对象创建和大对象分配

## 📝 实现检查清单

在开始实现前，请确认以下要点：

- [ ] 已理解Session在整个系统中的作用和重要性
- [ ] 已仔细阅读架构师的技术规格文档
- [ ] 已了解Task-002 TaskNode的基本接口（避免循环依赖）
- [ ] 已准备好JDK 8+的开发环境
- [ ] 已配置好Maven项目结构

**注意事项**:
1. Session是系统核心，代码质量要求极高
2. 线程安全是重中之重，必须仔细考虑并发场景
3. 性能影响要最小化，避免不必要的计算和对象创建
4. 为后续的JSON序列化和控制台输出提供良好的接口支持

---

**完成此任务后，请更新任务状态并通知相关人员进行代码审查。**