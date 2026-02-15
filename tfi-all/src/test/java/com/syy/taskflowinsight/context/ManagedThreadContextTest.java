package com.syy.taskflowinsight.context;

import com.syy.taskflowinsight.model.Session;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ManagedThreadContext 单元测试 - 托管线程上下文内部逻辑验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>专注于单个ManagedThreadContext类的内部逻辑验证</li>
 *   <li>使用try-with-resources模式验证AutoCloseable资源管理</li>
 *   <li>通过任务栈操作测试验证层次化任务管理</li>
 *   <li>采用状态转换测试确保生命周期管理的正确性</li>
 *   <li>使用边界条件测试验证异常情况下的健壮性</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>基础属性：</strong>上下文ID、线程信息、时间戳、会话关联</li>
 *   <li><strong>任务栈管理：</strong>startTask/endTask、任务深度、路径生成</li>
 *   <li><strong>属性存储：</strong>setAttribute/getAttribute、多类型数据支持</li>
 *   <li><strong>快照创建：</strong>createSnapshot、上下文状态保存</li>
 *   <li><strong>会话生命周期：</strong>会话创建、结束、状态同步</li>
 *   <li><strong>资源管理：</strong>close操作、资源清理、多次关闭安全性</li>
 *   <li><strong>异常处理：</strong>无会话操作、空栈操作、关闭后操作</li>
 *   <li><strong>状态验证：</strong>toString表示、closed状态检查</li>
 * </ul>
 * 
 * <h2>任务栈管理机制：</h2>
 * <ul>
 *   <li><strong>层次结构：</strong>root → child1 → child2 形成任务调用栈</li>
 *   <li><strong>路径生成：</strong>自动生成"root/child1/child2"格式的任务路径</li>
 *   <li><strong>深度跟踪：</strong>实时维护任务栈深度计数</li>
 *   <li><strong>栈操作：</strong>支持任务开始（压栈）和结束（出栈）</li>
 * </ul>
 * 
 * <h2>测试场景：</h2>
 * <ul>
 *   <li><strong>创建验证：</strong>基础属性、会话关联、初始状态</li>
 *   <li><strong>任务操作：</strong>3层嵌套任务的开始、结束、状态管理</li>
 *   <li><strong>属性管理：</strong>字符串、整数、对象等不同类型数据存储</li>
 *   <li><strong>快照功能：</strong>当前状态快照创建和信息验证</li>
 *   <li><strong>生命周期：</strong>会话结束、上下文关闭、资源清理</li>
 *   <li><strong>错误处理：</strong>无会话操作、空栈操作、重复关闭</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>属性正确：</strong>所有基础属性都准确设置和获取</li>
 *   <li><strong>栈管理有效：</strong>任务栈操作正确，深度计算准确</li>
 *   <li><strong>存储可靠：</strong>属性存储和检索功能完全正常</li>
 *   <li><strong>快照完整：</strong>快照包含完整的上下文状态信息</li>
 *   <li><strong>生命周期正确：</strong>会话和上下文状态转换符合预期</li>
 *   <li><strong>资源安全：</strong>资源能够正确清理，关闭操作安全</li>
 *   <li><strong>异常健壮：</strong>异常情况下正确抛出预期异常</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
class ManagedThreadContextTest {
    
    @BeforeEach
    void cleanup() {
        // 确保测试前清理状态
        ManagedThreadContext current = ManagedThreadContext.current();
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }
    
    @AfterEach
    void cleanupAfter() {
        // 确保测试后清理状态
        ManagedThreadContext current = ManagedThreadContext.current();
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }
    
    @Test
    @DisplayName("基础上下文创建和属性")
    void testBasicContextCreation() {
        try (ManagedThreadContext context = ManagedThreadContext.create("testRoot")) {
            // 验证基础属性
            assertNotNull(context.getContextId());
            assertEquals(Thread.currentThread().getId(), context.getThreadId());
            assertEquals(Thread.currentThread().getName(), context.getThreadName());
            assertFalse(context.isClosed());
            assertTrue(context.getElapsedNanos() >= 0);
            
            // 验证会话已创建
            Session session = context.getCurrentSession();
            assertNotNull(session);
            assertEquals("testRoot", session.getRootTask().getTaskName());
            assertTrue(session.isActive());
            
            // 验证任务栈初始状态
            assertEquals(1, context.getTaskDepth()); // 仅有根任务
            assertEquals("testRoot", context.getCurrentTask().getTaskName());
        }
    }
    
    @Test
    @DisplayName("任务栈操作")
    void testTaskStackOperations() {
        try (ManagedThreadContext context = ManagedThreadContext.create("root")) {
            
            // 开始子任务
            TaskNode child1 = context.startTask("child1");
            assertNotNull(child1);
            assertEquals("root/child1", child1.getTaskPath());
            assertEquals(2, context.getTaskDepth());
            assertEquals(child1, context.getCurrentTask());
            
            // 开始孙任务
            TaskNode child2 = context.startTask("child2");
            assertEquals("root/child1/child2", child2.getTaskPath());
            assertEquals(3, context.getTaskDepth());
            assertEquals(child2, context.getCurrentTask());
            
            // 结束当前任务
            TaskNode ended = context.endTask();
            assertEquals(child2, ended);
            assertTrue(ended.getStatus().isSuccessful());
            assertEquals(2, context.getTaskDepth());
            assertEquals(child1, context.getCurrentTask());
            
            // 再结束一个
            context.endTask();
            assertEquals(1, context.getTaskDepth());
            assertEquals("root", context.getCurrentTask().getTaskName());
        }
    }
    
    @Test
    @DisplayName("属性存储和检索")
    void testAttributeStorage() {
        try (ManagedThreadContext context = ManagedThreadContext.create("test")) {
            // 存储不同类型的属性
            context.setAttribute("stringKey", "stringValue");
            context.setAttribute("intKey", 42);
            context.setAttribute("objectKey", new java.util.ArrayList<>());
            
            // 检索属性
            assertEquals("stringValue", context.getAttribute("stringKey"));
            assertEquals(42, (Integer) context.getAttribute("intKey"));
            assertNotNull(context.getAttribute("objectKey"));
            assertNull(context.getAttribute("nonExistentKey"));
        }
    }
    
    @Test
    @DisplayName("上下文快照创建")
    void testSnapshotCreation() {
        try (ManagedThreadContext context = ManagedThreadContext.create("parent")) {
            context.startTask("child");
            context.setAttribute("testAttr", "value");
            
            ContextSnapshot snapshot = context.createSnapshot();
            
            assertNotNull(snapshot);
            assertEquals(context.getContextId(), snapshot.getContextId());
            assertEquals(context.getCurrentSession().getSessionId(), snapshot.getSessionId());
            assertEquals("parent/child", snapshot.getTaskPath());
            assertTrue(snapshot.hasSession());
            assertTrue(snapshot.hasTask());
            assertTrue(snapshot.getAgeNanos() >= 0);
        }
    }
    
    @Test
    @DisplayName("会话生命周期管理")
    void testSessionLifecycle() {
        try (ManagedThreadContext context = ManagedThreadContext.create("sessionTest")) {
            Session session = context.getCurrentSession();
            String sessionId = session.getSessionId();
            assertTrue(session.isActive());
            
            // 结束会话
            context.endSession();
            assertNull(context.getCurrentSession());
            
            // 验证原会话已终止
            assertFalse(session.isActive());
            assertTrue(session.isTerminated());
        }
    }
    
    @Test
    @DisplayName("错误情况 - 无会话时的任务操作")
    void testTaskOperationWithoutSession() {
        try (ManagedThreadContext context = ManagedThreadContext.create("test")) {
            context.endSession(); // 主动结束会话
            
            // 应该抛出IllegalStateException
            assertThrows(IllegalStateException.class, () -> {
                context.startTask("invalidTask");
            });
        }
    }
    
    @Test
    @DisplayName("错误情况 - 空栈时结束任务")
    void testEndTaskEmptyStack() {
        try (ManagedThreadContext context = ManagedThreadContext.create("test")) {
            // 结束根任务
            context.endTask();
            assertEquals(0, context.getTaskDepth());
            
            // 再次结束应该抛出异常
            assertThrows(IllegalStateException.class, () -> {
                context.endTask();
            });
        }
    }
    
    @Test
    @DisplayName("资源清理验证")
    void testResourceCleanup() {
        String contextId;
        
        // 创建上下文但不在try-with-resources中关闭
        ManagedThreadContext context = ManagedThreadContext.create("cleanup");
        contextId = context.getContextId();
        context.startTask("task1");
        context.startTask("task2");
        
        assertFalse(context.isClosed());
        assertEquals(3, context.getTaskDepth()); // root + task1 + task2
        
        // 手动关闭
        context.close();
        
        // 验证已关闭
        assertTrue(context.isClosed());
        
        // 关闭后的操作应该抛出异常
        assertThrows(IllegalStateException.class, () -> {
            context.startTask("shouldFail");
        });
        
        assertThrows(IllegalStateException.class, () -> {
            context.createSnapshot();
        });
    }
    
    @Test
    @DisplayName("多次关闭安全性")
    void testMultipleCloseSafety() {
        ManagedThreadContext context = ManagedThreadContext.create("multiClose");
        
        // 第一次关闭
        context.close();
        assertTrue(context.isClosed());
        
        // 多次关闭应该安全，不抛异常
        assertDoesNotThrow(() -> {
            context.close();
            context.close();
        });
    }
    
    @Test
    @DisplayName("上下文字符串表示")
    void testToString() {
        try (ManagedThreadContext context = ManagedThreadContext.create("toString")) {
            context.startTask("subTask");
            
            String str = context.toString();
            assertTrue(str.contains("ManagedThreadContext"));
            assertTrue(str.contains(context.getContextId()));
            assertTrue(str.contains(Thread.currentThread().getName()));
            assertTrue(str.contains("taskDepth=2"));
            assertTrue(str.contains("closed=false"));
        }
    }
}