package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.MessageType;
import com.syy.taskflowinsight.enums.TaskStatus;
import com.syy.taskflowinsight.model.Message;
import com.syy.taskflowinsight.model.TaskNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskNode 类单元测试 - 任务节点核心功能验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>基于任务节点的树形结构特性设计层次化测试</li>
 *   <li>使用状态机验证方法测试任务生命周期状态转换</li>
 *   <li>通过边界值测试验证输入参数的合法性检查</li>
 *   <li>采用并发测试验证多线程环境下的线程安全性</li>
 *   <li>使用不变性测试确保对象状态的一致性和完整性</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>节点创建：</strong>根节点创建、子节点创建、节点ID唯一性</li>
 *   <li><strong>树形结构：</strong>父子关系、路径生成、深度计算、根节点定位</li>
 *   <li><strong>状态管理：</strong>RUNNING→COMPLETED/FAILED状态转换、重复操作异常</li>
 *   <li><strong>消息功能：</strong>info/error消息添加、异常消息处理、消息列表管理</li>
 *   <li><strong>输入验证：</strong>null/empty/whitespace参数的异常处理</li>
 *   <li><strong>时间戳管理：</strong>创建时间、完成时间、持续时间计算</li>
 *   <li><strong>并发安全：</strong>多线程消息添加的线程安全性验证</li>
 *   <li><strong>对象行为：</strong>equals/hashCode/toString方法的正确实现</li>
 * </ul>
 * 
 * <h2>测试场景：</h2>
 * <ul>
 *   <li><strong>基础功能：</strong>节点创建、属性设置、状态初始化</li>
 *   <li><strong>树形操作：</strong>3层嵌套结构（root→child→grandchild）</li>
 *   <li><strong>状态转换：</strong>完成/失败状态及重复操作异常处理</li>
 *   <li><strong>消息处理：</strong>多条消息添加、异常消息提取</li>
 *   <li><strong>并发测试：</strong>10线程×100消息的并发写入</li>
 *   <li><strong>复杂树结构：</strong>多分支多层级树形结构验证</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>节点属性正确：</strong>ID唯一、名称正确、路径准确、深度计算无误</li>
 *   <li><strong>状态转换有效：</strong>状态按预期转换，重复操作正确抛出异常</li>
 *   <li><strong>树结构完整：</strong>父子关系正确、根节点定位准确、叶子节点识别无误</li>
 *   <li><strong>消息记录准确：</strong>消息类型、内容、时间戳都正确记录</li>
 *   <li><strong>并发安全可靠：</strong>多线程环境下消息不丢失、计数准确</li>
 *   <li><strong>异常处理健壮：</strong>无效输入正确抛出异常并包含明确错误信息</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
class TaskNodeTest {
    
    private TaskNode rootTask;
    private TaskNode childTask;
    private TaskNode grandChildTask;
    
    @BeforeEach
    void setUp() {
        rootTask = new TaskNode("Root Task");
        childTask = rootTask.createChild("Child Task");
        grandChildTask = childTask.createChild("GrandChild Task");
    }
    
    @Test
    void testCreateRootTask() {
        TaskNode root = new TaskNode("Test Root");
        
        assertNotNull(root.getNodeId());
        assertEquals("Test Root", root.getTaskName());
        assertEquals("Test Root", root.getTaskPath());
        assertNull(root.getParent());
        assertTrue(root.isRoot());
        assertTrue(root.isLeaf());
        assertEquals(0, root.getDepth());
        assertEquals(TaskStatus.RUNNING, root.getStatus());
        assertNotNull(root.getThreadName());
        assertTrue(root.getCreatedMillis() > 0);
        assertTrue(root.getCreatedNanos() > 0);
    }
    
    @Test
    void testCreateChildTask() {
        TaskNode parent = new TaskNode("Parent");
        TaskNode child = parent.createChild("Child");
        
        assertNotNull(child.getNodeId());
        assertEquals("Child", child.getTaskName());
        assertEquals("Parent/Child", child.getTaskPath());
        assertEquals(parent, child.getParent());
        assertFalse(child.isRoot());
        assertTrue(child.isLeaf());
        assertEquals(1, child.getDepth());
        assertEquals(TaskStatus.RUNNING, child.getStatus());
        
        // 验证父节点已包含子节点
        assertEquals(1, parent.getChildren().size());
        assertEquals(child, parent.getChildren().get(0));
        assertFalse(parent.isLeaf());
    }
    
    @Test
    void testTaskPathGeneration() {
        assertEquals("Root Task", rootTask.getTaskPath());
        assertEquals("Root Task/Child Task", childTask.getTaskPath());
        assertEquals("Root Task/Child Task/GrandChild Task", grandChildTask.getTaskPath());
    }
    
    @Test
    void testTreeStructure() {
        // 根节点
        assertTrue(rootTask.isRoot());
        assertFalse(rootTask.isLeaf());
        assertEquals(0, rootTask.getDepth());
        assertEquals(rootTask, rootTask.getRoot());
        
        // 子节点
        assertFalse(childTask.isRoot());
        assertFalse(childTask.isLeaf());
        assertEquals(1, childTask.getDepth());
        assertEquals(rootTask, childTask.getRoot());
        
        // 孙子节点
        assertFalse(grandChildTask.isRoot());
        assertTrue(grandChildTask.isLeaf());
        assertEquals(2, grandChildTask.getDepth());
        assertEquals(rootTask, grandChildTask.getRoot());
    }
    
    @Test
    void testCreateTaskWithNullName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TaskNode((String) null)
        );
        assertEquals("Task name cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testCreateTaskWithEmptyName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TaskNode("")
        );
        assertEquals("Task name cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testCreateChildWithNullName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> rootTask.createChild(null)
        );
        assertEquals("Task name cannot be null or empty", exception.getMessage());
    }
    
    @Test
    void testTaskNameTrimming() {
        TaskNode task = new TaskNode("  Test Task  ");
        assertEquals("Test Task", task.getTaskName());
        assertEquals("Test Task", task.getTaskPath());
    }
    
    @Test
    void testAddInfoMessage() {
        Message message = rootTask.addInfo("Test info message");
        
        assertEquals(MessageType.PROCESS, message.getType());
        assertEquals("Test info message", message.getContent());
        assertTrue(message.isProcess());
        
        assertEquals(1, rootTask.getMessages().size());
        assertEquals(message, rootTask.getMessages().get(0));
    }
    
    @Test
    void testAddErrorMessage() {
        Message message = rootTask.addError("Test error message");
        
        assertEquals(MessageType.ALERT, message.getType());
        assertEquals("Test error message", message.getContent());
        assertTrue(message.isAlert());
        
        assertEquals(1, rootTask.getMessages().size());
        assertEquals(message, rootTask.getMessages().get(0));
    }
    
    @Test
    void testAddErrorMessageFromThrowable() {
        RuntimeException exception = new RuntimeException("Test exception");
        Message message = rootTask.addError(exception);
        
        assertEquals(MessageType.ALERT, message.getType());
        assertEquals("Test exception", message.getContent());
        assertTrue(message.isAlert());
        
        assertEquals(1, rootTask.getMessages().size());
        assertEquals(message, rootTask.getMessages().get(0));
    }
    
    @Test
    void testAddMultipleMessages() {
        Message info1 = rootTask.addInfo("Info 1");
        Message error1 = rootTask.addError("Error 1");
        Message info2 = rootTask.addInfo("Info 2");
        
        assertEquals(3, rootTask.getMessages().size());
        assertEquals(info1, rootTask.getMessages().get(0));
        assertEquals(error1, rootTask.getMessages().get(1));
        assertEquals(info2, rootTask.getMessages().get(2));
    }
    
    @Test
    void testCompleteTask() {
        assertEquals(TaskStatus.RUNNING, rootTask.getStatus());
        assertNull(rootTask.getCompletedMillis());
        assertNull(rootTask.getCompletedNanos());
        assertNull(rootTask.getDurationMillis());
        assertNull(rootTask.getDurationNanos());
        
        rootTask.complete();
        
        assertEquals(TaskStatus.COMPLETED, rootTask.getStatus());
        assertNotNull(rootTask.getCompletedMillis());
        assertNotNull(rootTask.getCompletedNanos());
        assertNotNull(rootTask.getDurationMillis());
        assertNotNull(rootTask.getDurationNanos());
        assertTrue(rootTask.getDurationMillis() >= 0);
        assertTrue(rootTask.getDurationNanos() >= 0);
    }
    
    @Test
    void testFailTask() {
        assertEquals(TaskStatus.RUNNING, rootTask.getStatus());
        
        rootTask.fail();
        
        assertEquals(TaskStatus.FAILED, rootTask.getStatus());
        assertNotNull(rootTask.getCompletedMillis());
        assertNotNull(rootTask.getCompletedNanos());
        assertNotNull(rootTask.getDurationMillis());
        assertNotNull(rootTask.getDurationNanos());
    }
    
    @Test
    void testFailTaskWithMessage() {
        rootTask.fail("Task failed due to error");
        
        assertEquals(TaskStatus.FAILED, rootTask.getStatus());
        assertEquals(1, rootTask.getMessages().size());
        
        Message message = rootTask.getMessages().get(0);
        assertEquals(MessageType.ALERT, message.getType());
        assertEquals("Task failed due to error", message.getContent());
    }
    
    @Test
    void testFailTaskWithThrowable() {
        RuntimeException exception = new RuntimeException("Test exception");
        rootTask.fail(exception);
        
        assertEquals(TaskStatus.FAILED, rootTask.getStatus());
        assertEquals(1, rootTask.getMessages().size());
        
        Message message = rootTask.getMessages().get(0);
        assertEquals(MessageType.ALERT, message.getType());
        assertEquals("Test exception", message.getContent());
    }
    
    @Test
    void testCompleteAlreadyCompletedTask() {
        rootTask.complete();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rootTask.complete()
        );
        assertTrue(exception.getMessage().contains("Cannot complete task that is not running"));
        assertTrue(exception.getMessage().contains("COMPLETED"));
    }
    
    @Test
    void testFailAlreadyFailedTask() {
        rootTask.fail();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rootTask.fail()
        );
        assertTrue(exception.getMessage().contains("Cannot fail task that is not running"));
        assertTrue(exception.getMessage().contains("FAILED"));
    }
    
    @Test
    void testCompleteAlreadyFailedTask() {
        rootTask.fail();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rootTask.complete()
        );
        assertTrue(exception.getMessage().contains("Cannot complete task that is not running"));
        assertTrue(exception.getMessage().contains("FAILED"));
    }
    
    @Test
    void testFailAlreadyCompletedTask() {
        rootTask.complete();
        
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rootTask.fail()
        );
        assertTrue(exception.getMessage().contains("Cannot fail task that is not running"));
        assertTrue(exception.getMessage().contains("COMPLETED"));
    }
    
    @Test
    void testGetChildrenReturnsUnmodifiableList() {
        var children = rootTask.getChildren();
        
        assertEquals(1, children.size());
        assertEquals(childTask, children.get(0));
        
        // 尝试修改返回的列表应该抛出异常
        assertThrows(UnsupportedOperationException.class, () -> children.clear());
        assertThrows(UnsupportedOperationException.class, () -> children.add(new TaskNode("New Child")));
    }
    
    @Test
    void testGetMessagesReturnsUnmodifiableList() {
        rootTask.addInfo("Test message");
        var messages = rootTask.getMessages();
        
        assertEquals(1, messages.size());
        
        // 尝试修改返回的列表应该抛出异常
        assertThrows(UnsupportedOperationException.class, () -> messages.clear());
        assertThrows(UnsupportedOperationException.class, () -> messages.add(Message.info("New message")));
    }
    
    @Test
    void testUniqueNodeIds() {
        TaskNode task1 = new TaskNode("Task 1");
        TaskNode task2 = new TaskNode("Task 2");
        
        assertNotEquals(task1.getNodeId(), task2.getNodeId());
    }
    
    @Test
    void testEquals() {
        TaskNode task1 = new TaskNode("Test");
        TaskNode task2 = new TaskNode("Test");
        
        assertEquals(task1, task1); // 相同对象
        assertNotEquals(task1, task2); // 不同对象（不同ID）
        assertNotEquals(task1, null);
        assertNotEquals(task1, "String");
    }
    
    @Test
    void testHashCode() {
        TaskNode task1 = new TaskNode("Test");
        TaskNode task2 = new TaskNode("Test");
        
        assertEquals(task1.hashCode(), task1.hashCode()); // 一致性
        assertNotEquals(task1.hashCode(), task2.hashCode()); // 不同对象
    }
    
    @Test
    void testToString() {
        String toString = rootTask.toString();
        
        assertTrue(toString.contains("TaskNode"));
        assertTrue(toString.contains(rootTask.getTaskPath()));
        assertTrue(toString.contains(rootTask.getStatus().toString()));
        assertTrue(toString.contains(rootTask.getThreadName()));
    }
    
    @Test
    void testThreadSafety() throws InterruptedException {
        final int threadCount = 10;
        final int messagesPerThread = 100;
        
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    rootTask.addInfo("Message from thread " + threadIndex + ", iteration " + j);
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertEquals(threadCount * messagesPerThread, rootTask.getMessages().size());
    }
    
    @Test
    void testTimestampConsistency() {
        long beforeMillis = System.currentTimeMillis();
        long beforeNanos = System.nanoTime();
        
        TaskNode task = new TaskNode("Test Task");
        
        long afterMillis = System.currentTimeMillis();
        long afterNanos = System.nanoTime();
        
        // 验证创建时间在合理范围内
        assertTrue(task.getCreatedMillis() >= beforeMillis);
        assertTrue(task.getCreatedMillis() <= afterMillis);
        assertTrue(task.getCreatedNanos() >= beforeNanos);
        assertTrue(task.getCreatedNanos() <= afterNanos);
    }
    
    @Test
    void testComplexTreeStructure() {
        // 创建更复杂的树结构
        TaskNode level1_1 = rootTask.createChild("Level1-1");
        TaskNode level1_2 = rootTask.createChild("Level1-2");
        TaskNode level2_1 = level1_1.createChild("Level2-1");
        TaskNode level2_2 = level1_1.createChild("Level2-2");
        TaskNode level3_1 = level2_1.createChild("Level3-1");
        
        // 验证路径
        assertEquals("Root Task/Level1-1", level1_1.getTaskPath());
        assertEquals("Root Task/Level1-2", level1_2.getTaskPath());
        assertEquals("Root Task/Level1-1/Level2-1", level2_1.getTaskPath());
        assertEquals("Root Task/Level1-1/Level2-2", level2_2.getTaskPath());
        assertEquals("Root Task/Level1-1/Level2-1/Level3-1", level3_1.getTaskPath());
        
        // 验证深度
        assertEquals(1, level1_1.getDepth());
        assertEquals(1, level1_2.getDepth());
        assertEquals(2, level2_1.getDepth());
        assertEquals(2, level2_2.getDepth());
        assertEquals(3, level3_1.getDepth());
        
        // 验证根节点
        assertEquals(rootTask, level3_1.getRoot());
        
        // 验证子节点数量 (rootTask在setUp中已有childTask，现在又添加了level1_1和level1_2，总共3个)
        assertEquals(3, rootTask.getChildren().size());
        assertEquals(2, level1_1.getChildren().size());
        assertEquals(0, level1_2.getChildren().size());
        assertEquals(1, level2_1.getChildren().size());
        assertEquals(0, level2_2.getChildren().size());
        assertEquals(0, level3_1.getChildren().size());
    }
}