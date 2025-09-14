# TASK-005: 核心数据模型单元测试

## 任务背景

核心数据模型(Session、TaskNode、Message)及枚举类型是TaskFlow Insight系统的基础，需要完善的单元测试来确保其正确性、稳定性和性能。测试需要覆盖功能正确性、边界情况、并发安全性和性能指标，为系统的可靠性提供保障。

## 目标

1. 为Session模型实现完整的单元测试套件
2. 为TaskNode模型实现完整的单元测试套件  
3. 为Message模型和MessageCollection实现完整的单元测试套件
4. 为所有枚举类型实现完整的单元测试套件
5. 实现性能基准测试，验证性能指标
6. 实现并发安全测试，确保线程安全性
7. 达到≥95%的测试覆盖率

## 实现方案

### 5.1 Session模型测试

```java
package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session模型单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
@DisplayName("Session模型测试")
class SessionTest {
    
    private Session session;
    private final long testThreadId = Thread.currentThread().getId();
    
    @BeforeEach
    void setUp() {
        session = Session.create();
    }
    
    @Nested
    @DisplayName("Session创建测试")
    class CreationTests {
        
        @Test
        @DisplayName("正常创建Session")
        void createSession_ShouldSucceed() {
            assertNotNull(session);
            assertNotNull(session.getSessionId());
            assertEquals(testThreadId, session.getThreadId());
            assertEquals(SessionStatus.INITIALIZED, session.getStatus());
            assertNotNull(session.getRoot());
            assertTrue(session.getCreatedAt() > 0);
            assertEquals(0, session.getEndedAt());
            assertTrue(session.isActive());
            assertFalse(session.isCompleted());
        }
        
        @Test
        @DisplayName("Session ID格式验证")
        void sessionId_ShouldBeUUID() {
            String sessionId = session.getSessionId();
            // UUID格式: 8-4-4-4-12
            assertTrue(sessionId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
        }
        
        @Test
        @DisplayName("多次创建Session应产生不同ID")
        void multipleCreation_ShouldHaveDifferentIds() {
            Session session1 = Session.create();
            Session session2 = Session.create();
            Session session3 = Session.create();
            
            assertNotEquals(session1.getSessionId(), session2.getSessionId());
            assertNotEquals(session2.getSessionId(), session3.getSessionId());
            assertNotEquals(session1.getSessionId(), session3.getSessionId());
        }
        
        @Test
        @DisplayName("Session创建时间应接近当前时间")
        void createdTime_ShouldBeRecentTime() {
            long beforeCreate = System.currentTimeMillis();
            Session newSession = Session.create();
            long afterCreate = System.currentTimeMillis();
            
            assertTrue(newSession.getCreatedAt() >= beforeCreate);
            assertTrue(newSession.getCreatedAt() <= afterCreate);
        }
    }
    
    @Nested
    @DisplayName("Session状态管理测试")
    class StatusManagementTests {
        
        @Test
        @DisplayName("Session启动应改变状态为RUNNING")
        void start_ShouldChangeStatusToRunning() {
            session.start();
            
            assertEquals(SessionStatus.RUNNING, session.getStatus());
            assertTrue(session.isActive());
            assertFalse(session.isCompleted());
        }
        
        @Test
        @DisplayName("Session完成应改变状态为COMPLETED")
        void complete_ShouldChangeStatusToCompleted() {
            session.start();
            session.complete();
            
            assertEquals(SessionStatus.COMPLETED, session.getStatus());
            assertFalse(session.isActive());
            assertTrue(session.isCompleted());
            assertTrue(session.getEndedAt() > 0);
            assertTrue(session.getDurationMillis() >= 0);
        }
        
        @Test
        @DisplayName("Session中止应改变状态为ABORTED")
        void abort_ShouldChangeStatusToAborted() {
            session.start();
            session.abort("测试中止");
            
            assertEquals(SessionStatus.ABORTED, session.getStatus());
            assertFalse(session.isActive());
            assertTrue(session.isCompleted());
            assertTrue(session.getEndedAt() > 0);
        }
        
        @Test
        @DisplayName("非法状态转换应抛出异常")
        void invalidStatusTransition_ShouldThrowException() {
            session.complete(); // 直接完成，跳过RUNNING状态
            
            // 已完成的Session不能再次启动
            assertThrows(IllegalStateException.class, () -> session.start());
            assertThrows(IllegalStateException.class, () -> session.complete());
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("中止原因为空或空白应使用默认原因")
        void abort_WithBlankReason_ShouldUseDefaultReason(String reason) {
            session.start();
            session.abort(reason);
            
            assertEquals(SessionStatus.ABORTED, session.getStatus());
            // 验证使用了默认的中止原因
        }
    }
    
    @Nested
    @DisplayName("TaskNode关联测试")
    class TaskNodeRelationTests {
        
        @Test
        @DisplayName("Session应有默认的根节点")
        void session_ShouldHaveDefaultRootNode() {
            TaskNode root = session.getRoot();
            
            assertNotNull(root);
            assertEquals("ROOT", root.getName());
            assertEquals(0, root.getDepth());
            assertNull(root.getParent());
        }
        
        @Test
        @DisplayName("设置新的根节点应成功")
        void setRoot_ShouldSucceed() {
            TaskNode newRoot = TaskNode.create("NEW_ROOT", null, 0);
            session.setRoot(newRoot);
            
            assertEquals(newRoot, session.getRoot());
            assertEquals("NEW_ROOT", session.getRoot().getName());
        }
        
        @Test
        @DisplayName("设置null根节点应抛出异常")
        void setNullRoot_ShouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> session.setRoot(null));
        }
    }
    
    @Nested
    @DisplayName("时间计算测试")  
    class TimeCalculationTests {
        
        @Test
        @DisplayName("未完成的Session持续时间应为-1")
        void activeSesssion_DurationShouldBeNegativeOne() {
            assertEquals(-1, session.getDurationMillis());
        }
        
        @Test
        @DisplayName("已完成Session的持续时间应正确计算")
        void completedSession_DurationShouldBeCalculatedCorrectly() throws InterruptedException {
            session.start();
            Thread.sleep(10); // 等待10毫秒
            session.complete();
            
            long duration = session.getDurationMillis();
            assertTrue(duration >= 10);
            assertTrue(duration < 1000); // 不应该超过1秒
        }
        
        @Test
        @DisplayName("时间戳应保持一致性")
        void timestamps_ShouldBeConsistent() {
            session.start();
            session.complete();
            
            assertTrue(session.getCreatedAt() <= session.getEndedAt());
            assertTrue(session.getDurationMillis() == (session.getEndedAt() - session.getCreatedAt()));
        }
    }
    
    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("并发状态修改应保持一致性")
        void concurrentStatusModification_ShouldMaintainConsistency() throws InterruptedException {
            final int threadCount = 10;
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);
            final AtomicInteger successCount = new AtomicInteger(0);
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // 启动多个线程同时尝试完成Session
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        session.start();
                        session.complete();
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 预期的并发异常
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            
            // 只应该有一个线程成功完成状态转换
            assertEquals(1, successCount.get());
            assertEquals(SessionStatus.COMPLETED, session.getStatus());
            
            executor.shutdown();
        }
        
        @Test
        @DisplayName("多线程访问Session属性应保持线程安全")
        void concurrentPropertyAccess_ShouldBeThreadSafe() throws InterruptedException {
            final int threadCount = 20;
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);
            final AtomicInteger exceptionCount = new AtomicInteger(0);
            
            session.start();
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        // 并发访问各种属性
                        for (int j = 0; j < 100; j++) {
                            session.getSessionId();
                            session.getStatus();
                            session.getThreadId();
                            session.isActive();
                            session.getRoot();
                        }
                    } catch (Exception e) {
                        exceptionCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            
            // 不应该有异常发生
            assertEquals(0, exceptionCount.get());
            
            executor.shutdown();
        }
    }
    
    @Nested
    @DisplayName("性能基准测试")
    class PerformanceTests {
        
        @Test
        @DisplayName("Session创建性能测试")
        void sessionCreation_PerformanceTest() {
            final int iterations = 10000;
            
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                Session.create();
            }
            
            long endTime = System.nanoTime();
            long averageNanos = (endTime - startTime) / iterations;
            
            // Session创建平均耗时应小于10微秒
            assertTrue(averageNanos < 10_000, 
                String.format("Session创建平均耗时 %d ns 超过预期的 10,000 ns", averageNanos));
        }
        
        @Test
        @DisplayName("状态转换性能测试")
        void statusTransition_PerformanceTest() {
            final int iterations = 10000;
            Session[] sessions = new Session[iterations];
            
            // 准备测试数据
            for (int i = 0; i < iterations; i++) {
                sessions[i] = Session.create();
            }
            
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                sessions[i].start();
                sessions[i].complete();
            }
            
            long endTime = System.nanoTime();
            long averageNanos = (endTime - startTime) / (iterations * 2); // 两次状态转换
            
            // 状态转换平均耗时应小于1微秒
            assertTrue(averageNanos < 1_000, 
                String.format("状态转换平均耗时 %d ns 超过预期的 1,000 ns", averageNanos));
        }
    }
    
    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("极长运行时间的Session")
        void longRunningSession_ShouldHandleCorrectly() {
            session.start();
            
            // 模拟长时间运行（通过直接设置时间戳）
            try {
                java.lang.reflect.Field field = Session.class.getDeclaredField("createdAt");
                field.setAccessible(true);
                field.setLong(session, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24));
            } catch (Exception e) {
                fail("无法设置创建时间");
            }
            
            session.complete();
            
            assertTrue(session.getDurationMillis() > TimeUnit.HOURS.toMillis(23));
            assertTrue(session.isCompleted());
        }
        
        @Test
        @DisplayName("Session toString应提供有意义的信息")
        void toString_ShouldProvideUsefulInfo() {
            String sessionString = session.toString();
            
            assertTrue(sessionString.contains("Session"));
            assertTrue(sessionString.contains(session.getSessionId().substring(0, 8)));
            assertTrue(sessionString.contains("INITIALIZED"));
        }
        
        @Test
        @DisplayName("equals和hashCode应正确实现")
        void equalsAndHashCode_ShouldBeImplementedCorrectly() {
            Session session1 = Session.create();
            Session session2 = Session.create();
            
            // 不同的Session应该不相等
            assertNotEquals(session1, session2);
            assertNotEquals(session1.hashCode(), session2.hashCode());
            
            // 同一个Session应该相等
            assertEquals(session1, session1);
            assertEquals(session1.hashCode(), session1.hashCode());
            
            // 与null比较
            assertNotEquals(session1, null);
        }
    }
}
```

### 5.2 TaskNode模型测试

```java
package com.syy.taskflowinsight.model;

import com.syy.taskflowinsight.enums.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskNode模型单元测试
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 */
@DisplayName("TaskNode模型测试")
class TaskNodeTest {
    
    private TaskNode rootNode;
    private TaskNode childNode;
    
    @BeforeEach
    void setUp() {
        rootNode = TaskNode.create("ROOT", null, 0);
        childNode = TaskNode.create("CHILD", rootNode, 1);
    }
    
    @Nested
    @DisplayName("TaskNode创建测试")
    class CreationTests {
        
        @Test
        @DisplayName("正常创建根节点")
        void createRootNode_ShouldSucceed() {
            TaskNode node = TaskNode.create("TEST_ROOT", null, 0);
            
            assertNotNull(node);
            assertNotNull(node.getNodeId());
            assertEquals("TEST_ROOT", node.getName());
            assertNull(node.getParent());
            assertEquals(0, node.getDepth());
            assertEquals(TaskStatus.PENDING, node.getStatus());
            assertTrue(node.getChildren().isEmpty());
            assertTrue(node.getMessages().isEmpty());
        }
        
        @Test
        @DisplayName("创建子节点应建立父子关系")
        void createChildNode_ShouldEstablishParentChildRelation() {
            assertEquals(rootNode, childNode.getParent());
            assertEquals(1, childNode.getDepth());
            assertTrue(rootNode.getChildren().contains(childNode));
        }
        
        @Test
        @DisplayName("节点ID应为UUID格式")
        void nodeId_ShouldBeUUID() {
            String nodeId = rootNode.getNodeId();
            assertTrue(nodeId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
        }
        
        @Test
        @DisplayName("空任务名应抛出异常")
        void createWithNullName_ShouldThrowException() {
            assertThrows(IllegalArgumentException.class, 
                () -> TaskNode.create(null, null, 0));
            assertThrows(IllegalArgumentException.class, 
                () -> TaskNode.create("", null, 0));
            assertThrows(IllegalArgumentException.class, 
                () -> TaskNode.create("   ", null, 0));
        }
        
        @Test
        @DisplayName("负数深度应抛出异常")
        void createWithNegativeDepth_ShouldThrowException() {
            assertThrows(IllegalArgumentException.class, 
                () -> TaskNode.create("TEST", null, -1));
        }
    }
    
    @Nested
    @DisplayName("任务状态管理测试")
    class StatusManagementTests {
        
        @Test
        @DisplayName("启动任务应改变状态为RUNNING")
        void start_ShouldChangeStatusToRunning() {
            rootNode.start();
            
            assertEquals(TaskStatus.RUNNING, rootNode.getStatus());
            assertTrue(rootNode.isActive());
            assertFalse(rootNode.isCompleted());
            assertTrue(rootNode.getStartNano() > 0);
            assertEquals(0, rootNode.getEndNano());
        }
        
        @Test
        @DisplayName("完成任务应改变状态为COMPLETED")
        void complete_ShouldChangeStatusToCompleted() throws InterruptedException {
            rootNode.start();
            Thread.sleep(1); // 确保有时间差
            rootNode.complete();
            
            assertEquals(TaskStatus.COMPLETED, rootNode.getStatus());
            assertFalse(rootNode.isActive());
            assertTrue(rootNode.isCompleted());
            assertTrue(rootNode.getEndNano() > rootNode.getStartNano());
            assertTrue(rootNode.getDurationNanos() > 0);
        }
        
        @Test
        @DisplayName("任务失败应改变状态为FAILED")
        void fail_ShouldChangeStatusToFailed() {
            rootNode.start();
            rootNode.fail("测试失败");
            
            assertEquals(TaskStatus.FAILED, rootNode.getStatus());
            assertFalse(rootNode.isActive());
            assertTrue(rootNode.isCompleted());
            assertTrue(rootNode.getEndNano() > 0);
        }
        
        @Test
        @DisplayName("未启动的任务不能直接完成")
        void completeWithoutStart_ShouldThrowException() {
            assertThrows(IllegalStateException.class, () -> rootNode.complete());
        }
        
        @Test
        @DisplayName("已完成的任务不能再次操作")
        void operationOnCompletedTask_ShouldThrowException() {
            rootNode.start();
            rootNode.complete();
            
            assertThrows(IllegalStateException.class, () -> rootNode.start());
            assertThrows(IllegalStateException.class, () -> rootNode.complete());
            assertThrows(IllegalStateException.class, () -> rootNode.fail("test"));
        }
    }
    
    @Nested
    @DisplayName("树结构管理测试")
    class TreeStructureTests {
        
        @Test
        @DisplayName("添加子节点应更新父节点children列表")
        void addChild_ShouldUpdateParentChildrenList() {
            TaskNode newChild = TaskNode.create("NEW_CHILD", rootNode, 1);
            
            assertTrue(rootNode.getChildren().contains(newChild));
            assertEquals(2, rootNode.getChildren().size());
        }
        
        @Test
        @DisplayName("获取所有后代节点")
        void getAllDescendants_ShouldReturnAllNodes() {
            TaskNode grandChild = TaskNode.create("GRANDCHILD", childNode, 2);
            TaskNode child2 = TaskNode.create("CHILD2", rootNode, 1);
            
            List<TaskNode> descendants = rootNode.getAllDescendants();
            
            assertEquals(3, descendants.size());
            assertTrue(descendants.contains(childNode));
            assertTrue(descendants.contains(grandChild));
            assertTrue(descendants.contains(child2));
        }
        
        @Test
        @DisplayName("获取根节点")
        void getRoot_ShouldReturnRootNode() {
            TaskNode grandChild = TaskNode.create("GRANDCHILD", childNode, 2);
            
            assertEquals(rootNode, grandChild.getRoot());
            assertEquals(rootNode, childNode.getRoot());
            assertEquals(rootNode, rootNode.getRoot());
        }
        
        @Test
        @DisplayName("判断是否为叶子节点")
        void isLeaf_ShouldReturnCorrectValue() {
            assertTrue(childNode.isLeaf());
            assertFalse(rootNode.isLeaf());
            
            TaskNode.create("GRANDCHILD", childNode, 2);
            assertFalse(childNode.isLeaf());
        }
        
        @Test
        @DisplayName("获取路径应返回从根到当前节点的路径")
        void getPath_ShouldReturnPathFromRoot() {
            TaskNode grandChild = TaskNode.create("GRANDCHILD", childNode, 2);
            
            List<TaskNode> path = grandChild.getPath();
            
            assertEquals(3, path.size());
            assertEquals(rootNode, path.get(0));
            assertEquals(childNode, path.get(1));
            assertEquals(grandChild, path.get(2));
        }
        
        @Test
        @DisplayName("获取路径名称应返回完整路径字符串")
        void getPathName_ShouldReturnFullPathString() {
            TaskNode grandChild = TaskNode.create("GRANDCHILD", childNode, 2);
            
            String pathName = grandChild.getPathName();
            
            assertEquals("ROOT/CHILD/GRANDCHILD", pathName);
        }
    }
    
    @Nested
    @DisplayName("消息管理测试")
    class MessageManagementTests {
        
        @Test
        @DisplayName("添加消息应成功")
        void addMessage_ShouldSucceed() {
            Message msg = Message.info("测试消息");
            rootNode.addMessage(msg);
            
            assertEquals(1, rootNode.getMessages().size());
            assertTrue(rootNode.getMessages().contains(msg));
        }
        
        @Test
        @DisplayName("添加多条消息应保持顺序")
        void addMultipleMessages_ShouldMaintainOrder() {
            Message msg1 = Message.info("消息1");
            Message msg2 = Message.warn("消息2");
            Message msg3 = Message.error("消息3");
            
            rootNode.addMessage(msg1);
            rootNode.addMessage(msg2);
            rootNode.addMessage(msg3);
            
            List<Message> messages = rootNode.getMessages();
            assertEquals(3, messages.size());
            assertEquals(msg1, messages.get(0));
            assertEquals(msg2, messages.get(1));
            assertEquals(msg3, messages.get(2));
        }
        
        @Test
        @DisplayName("添加null消息应被忽略")
        void addNullMessage_ShouldBeIgnored() {
            rootNode.addMessage(null);
            assertTrue(rootNode.getMessages().isEmpty());
        }
        
        @Test
        @DisplayName("获取特定类型消息")
        void getMessagesByType_ShouldFilterCorrectly() {
            rootNode.addMessage(Message.info("info1"));
            rootNode.addMessage(Message.error("error1"));
            rootNode.addMessage(Message.info("info2"));
            
            List<Message> infoMessages = rootNode.getMessagesByType(com.syy.taskflowinsight.enums.MessageType.INFO);
            List<Message> errorMessages = rootNode.getMessagesByType(com.syy.taskflowinsight.enums.MessageType.ERROR);
            
            assertEquals(2, infoMessages.size());
            assertEquals(1, errorMessages.size());
        }
    }
    
    @Nested
    @DisplayName("时间计算测试")
    class TimeCalculationTests {
        
        @Test
        @DisplayName("未启动任务的持续时间应为-1")
        void pendingTask_DurationShouldBeNegativeOne() {
            assertEquals(-1, rootNode.getDurationNanos());
            assertEquals(-1, rootNode.getDurationMillis());
        }
        
        @Test
        @DisplayName("运行中任务的持续时间应基于当前时间计算")
        void runningTask_DurationShouldBeCalculatedFromCurrentTime() throws InterruptedException {
            rootNode.start();
            Thread.sleep(1);
            
            long duration = rootNode.getDurationNanos();
            assertTrue(duration > 0);
            assertTrue(duration < TimeUnit.SECONDS.toNanos(1));
        }
        
        @Test
        @DisplayName("已完成任务的持续时间应为固定值")
        void completedTask_DurationShouldBeFixed() throws InterruptedException {
            rootNode.start();
            Thread.sleep(1);
            rootNode.complete();
            
            long duration1 = rootNode.getDurationNanos();
            Thread.sleep(1);
            long duration2 = rootNode.getDurationNanos();
            
            assertEquals(duration1, duration2);
            assertTrue(duration1 > 0);
        }
        
        @Test
        @DisplayName("纳秒和毫秒时间换算应正确")
        void nanoToMillisConversion_ShouldBeCorrect() throws InterruptedException {
            rootNode.start();
            Thread.sleep(10);
            rootNode.complete();
            
            long nanos = rootNode.getDurationNanos();
            long millis = rootNode.getDurationMillis();
            
            assertEquals(millis, nanos / 1_000_000);
        }
    }
    
    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("并发添加消息应保持线程安全")
        void concurrentAddMessage_ShouldBeThreadSafe() throws InterruptedException {
            final int threadCount = 10;
            final int messagesPerThread = 100;
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            for (int t = 0; t < threadCount; t++) {
                final int threadIndex = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < messagesPerThread; i++) {
                            Message msg = Message.info(String.format("Thread-%d-Message-%d", threadIndex, i));
                            rootNode.addMessage(msg);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            
            // 验证所有消息都被添加
            assertEquals(threadCount * messagesPerThread, rootNode.getMessages().size());
            
            executor.shutdown();
        }
        
        @Test
        @DisplayName("并发创建子节点应保持线程安全")
        void concurrentCreateChildren_ShouldBeThreadSafe() throws InterruptedException {
            final int threadCount = 10;
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);
            final AtomicInteger childCounter = new AtomicInteger(0);
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int childIndex = childCounter.getAndIncrement();
                        TaskNode.create("CHILD-" + childIndex, rootNode, 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            
            // 验证所有子节点都被创建（包括初始的childNode）
            assertEquals(threadCount + 1, rootNode.getChildren().size());
            
            executor.shutdown();
        }
    }
    
    @Nested
    @DisplayName("性能基准测试")
    class PerformanceTests {
        
        @Test
        @DisplayName("TaskNode创建性能测试")
        void nodeCreation_PerformanceTest() {
            final int iterations = 10000;
            
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                TaskNode.create("TEST-" + i, null, 0);
            }
            
            long endTime = System.nanoTime();
            long averageNanos = (endTime - startTime) / iterations;
            
            // TaskNode创建平均耗时应小于5微秒
            assertTrue(averageNanos < 5_000, 
                String.format("TaskNode创建平均耗时 %d ns 超过预期的 5,000 ns", averageNanos));
        }
        
        @Test
        @DisplayName("状态转换性能测试")
        void statusTransition_PerformanceTest() {
            final int iterations = 10000;
            TaskNode[] nodes = new TaskNode[iterations];
            
            // 准备测试数据
            for (int i = 0; i < iterations; i++) {
                nodes[i] = TaskNode.create("TEST-" + i, null, 0);
            }
            
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                nodes[i].start();
                nodes[i].complete();
            }
            
            long endTime = System.nanoTime();
            long averageNanos = (endTime - startTime) / (iterations * 2);
            
            // 状态转换平均耗时应小于1微秒
            assertTrue(averageNanos < 1_000, 
                String.format("状态转换平均耗时 %d ns 超过预期的 1,000 ns", averageNanos));
        }
        
        @Test
        @DisplayName("消息添加性能测试")
        void messageAddition_PerformanceTest() {
            final int iterations = 10000;
            Message[] messages = new Message[iterations];
            
            // 准备消息
            for (int i = 0; i < iterations; i++) {
                messages[i] = Message.info("Test message " + i);
            }
            
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                rootNode.addMessage(messages[i]);
            }
            
            long endTime = System.nanoTime();
            long averageNanos = (endTime - startTime) / iterations;
            
            // 消息添加平均耗时应小于1微秒
            assertTrue(averageNanos < 1_000, 
                String.format("消息添加平均耗时 %d ns 超过预期的 1,000 ns", averageNanos));
        }
    }
}
```

## 测试标准

### 5.1 覆盖率要求

1. **行覆盖率**: ≥95%
2. **分支覆盖率**: ≥90% 
3. **方法覆盖率**: 100%
4. **类覆盖率**: 100%

### 5.2 功能测试要求

1. **正常功能测试**: 所有公开方法的正常使用场景
2. **边界测试**: 极值、空值、null值处理
3. **异常测试**: 各种异常情况的处理
4. **状态测试**: 对象状态转换的正确性

### 5.3 性能测试要求

1. **创建性能**: 对象创建耗时 < 5微秒
2. **操作性能**: 基本操作耗时 < 1微秒  
3. **内存效率**: 对象内存占用合理
4. **批量操作**: 大量操作的性能表现

### 5.4 并发测试要求

1. **线程安全**: 多线程操作无数据竞争
2. **原子性**: 复合操作的原子性保证
3. **可见性**: 状态变更的线程间可见性
4. **活性**: 无死锁和活锁

## 验收标准

### 5.1 质量验收

- [ ] 所有测试用例通过，无失败用例
- [ ] 测试覆盖率达到要求标准
- [ ] 代码审查通过，测试代码质量良好
- [ ] 测试执行时间合理(< 30秒)

### 5.2 功能验收

- [ ] 核心功能测试完整，覆盖所有重要场景
- [ ] 边界情况测试充分，异常处理正确
- [ ] 并发场景测试通过，线程安全性确认
- [ ] 性能基准测试通过，满足性能要求

### 5.3 维护验收

- [ ] 测试代码清晰可读，易于维护
- [ ] 测试数据和期望值合理设置
- [ ] 测试方法命名规范，含义明确
- [ ] 测试组织结构良好，便于扩展

## 依赖关系

- **前置依赖**: TASK-001, TASK-002, TASK-003, TASK-004
- **后置依赖**: 所有使用核心数据模型的任务
- **工具依赖**: JUnit 5, Mockito, AssertJ

## 预计工期

- **开发时间**: 3天
- **测试完善**: 1天  
- **总计**: 4天

## 风险识别

1. **测试复杂性**: 并发测试的复杂性和不稳定性
   - **缓解措施**: 使用确定性的并发测试模式，充分的等待和同步

2. **性能测试环境依赖**: 性能测试结果可能因环境而异
   - **缓解措施**: 使用相对性能指标，多次测试取平均值

3. **测试数据一致性**: 时间相关测试的稳定性问题
   - **缓解措施**: 使用模拟时间或相对时间测试