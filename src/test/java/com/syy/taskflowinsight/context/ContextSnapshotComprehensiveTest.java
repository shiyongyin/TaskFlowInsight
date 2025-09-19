package com.syy.taskflowinsight.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ContextSnapshot 全面测试
 * 目标：从32%覆盖率提升到90%+
 */
@SpringBootTest
@DisplayName("ContextSnapshot 全面测试")
class ContextSnapshotComprehensiveTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("正常构造应该成功")
        void normalConstruction_shouldSucceed() {
            String contextId = "ctx-123";
            String sessionId = "sess-456";
            String taskPath = "/task/path";
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot = new ContextSnapshot(contextId, sessionId, taskPath, timestamp);
            
            assertThat(snapshot.getContextId()).isEqualTo(contextId);
            assertThat(snapshot.getSessionId()).isEqualTo(sessionId);
            assertThat(snapshot.getTaskPath()).isEqualTo(taskPath);
            assertThat(snapshot.getTimestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("允许sessionId为null")
        void constructionWithNullSessionId_shouldBeAllowed() {
            String contextId = "ctx-123";
            String taskPath = "/task/path";
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot = new ContextSnapshot(contextId, null, taskPath, timestamp);
            
            assertThat(snapshot.getContextId()).isEqualTo(contextId);
            assertThat(snapshot.getSessionId()).isNull();
            assertThat(snapshot.getTaskPath()).isEqualTo(taskPath);
            assertThat(snapshot.getTimestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("允许taskPath为null")
        void constructionWithNullTaskPath_shouldBeAllowed() {
            String contextId = "ctx-123";
            String sessionId = "sess-456";
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot = new ContextSnapshot(contextId, sessionId, null, timestamp);
            
            assertThat(snapshot.getContextId()).isEqualTo(contextId);
            assertThat(snapshot.getSessionId()).isEqualTo(sessionId);
            assertThat(snapshot.getTaskPath()).isNull();
            assertThat(snapshot.getTimestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("contextId为null应该抛出异常")
        void constructionWithNullContextId_shouldThrowException() {
            String sessionId = "sess-456";
            String taskPath = "/task/path";
            long timestamp = System.nanoTime();
            
            assertThatThrownBy(() -> new ContextSnapshot(null, sessionId, taskPath, timestamp))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("contextId cannot be null");
        }

        @Test
        @DisplayName("所有null值（除contextId）应该被允许")
        void constructionWithAllNullsExceptContextId_shouldBeAllowed() {
            String contextId = "ctx-123";
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot = new ContextSnapshot(contextId, null, null, timestamp);
            
            assertThat(snapshot.getContextId()).isEqualTo(contextId);
            assertThat(snapshot.getSessionId()).isNull();
            assertThat(snapshot.getTaskPath()).isNull();
            assertThat(snapshot.getTimestamp()).isEqualTo(timestamp);
        }
    }

    @Nested
    @DisplayName("状态查询方法测试")
    class StateQueryTests {

        @Test
        @DisplayName("hasSession在有sessionId时应该返回true")
        void hasSession_withSessionId_shouldReturnTrue() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "sess-456", null, System.nanoTime());
            
            assertThat(snapshot.hasSession()).isTrue();
        }

        @Test
        @DisplayName("hasSession在sessionId为null时应该返回false")
        void hasSession_withNullSessionId_shouldReturnFalse() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, System.nanoTime());
            
            assertThat(snapshot.hasSession()).isFalse();
        }

        @Test
        @DisplayName("hasSession在sessionId为空字符串时应该返回true")
        void hasSession_withEmptyStringSessionId_shouldReturnTrue() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "", null, System.nanoTime());
            
            assertThat(snapshot.hasSession()).isTrue();
        }

        @Test
        @DisplayName("hasTask在有taskPath时应该返回true")
        void hasTask_withTaskPath_shouldReturnTrue() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, "/task/path", System.nanoTime());
            
            assertThat(snapshot.hasTask()).isTrue();
        }

        @Test
        @DisplayName("hasTask在taskPath为null时应该返回false")
        void hasTask_withNullTaskPath_shouldReturnFalse() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, System.nanoTime());
            
            assertThat(snapshot.hasTask()).isFalse();
        }

        @Test
        @DisplayName("hasTask在taskPath为空字符串时应该返回true")
        void hasTask_withEmptyStringTaskPath_shouldReturnTrue() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, "", System.nanoTime());
            
            assertThat(snapshot.hasTask()).isTrue();
        }
    }

    @Nested
    @DisplayName("年龄计算测试")
    class AgeCalculationTests {

        @Test
        @DisplayName("getAgeNanos应该返回正确的纳秒数")
        void getAgeNanos_shouldReturnCorrectNanos() throws InterruptedException {
            long startTime = System.nanoTime();
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, startTime);
            
            // 等待一小段时间
            Thread.sleep(10);
            
            long age = snapshot.getAgeNanos();
            
            // 年龄应该是正数，且大于0
            assertThat(age).isPositive();
            // 年龄应该大约是10毫秒（但允许一些误差）
            assertThat(age).isGreaterThan(5_000_000); // 5ms
            assertThat(age).isLessThan(100_000_000); // 100ms（允许较大误差）
        }

        @Test
        @DisplayName("getAgeMillis应该返回正确的毫秒数")
        void getAgeMillis_shouldReturnCorrectMillis() throws InterruptedException {
            long startTime = System.nanoTime();
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, startTime);
            
            // 等待一小段时间
            Thread.sleep(20);
            
            long ageMillis = snapshot.getAgeMillis();
            
            // 年龄应该是正数
            assertThat(ageMillis).isGreaterThanOrEqualTo(0);
            // 应该大约是20毫秒（但允许一些误差）
            assertThat(ageMillis).isLessThan(200); // 允许较大误差
        }

        @Test
        @DisplayName("连续调用getAgeNanos应该返回递增的值")
        void continuousGetAgeNanos_shouldReturnIncreasingValues() throws InterruptedException {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, System.nanoTime());
            
            long age1 = snapshot.getAgeNanos();
            Thread.sleep(5);
            long age2 = snapshot.getAgeNanos();
            
            assertThat(age2).isGreaterThan(age1);
        }

        @Test
        @DisplayName("时间戳为未来时间应该返回负年龄")
        void futureTimestamp_shouldReturnNegativeAge() {
            long futureTime = System.nanoTime() + 1_000_000_000; // 1秒后
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, futureTime);
            
            long age = snapshot.getAgeNanos();
            
            assertThat(age).isNegative();
        }
    }

    @Nested
    @DisplayName("恢复上下文测试")
    class RestoreContextTests {

        @Test
        @DisplayName("restore应该返回ManagedThreadContext实例")
        void restore_shouldReturnManagedThreadContext() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "sess-456", "task-path", System.nanoTime());
            
            ManagedThreadContext context = snapshot.restore();
            
            assertThat(context).isNotNull();
        }

        @Test
        @DisplayName("多次调用restore应该返回不同的实例")
        void multipleRestore_shouldReturnDifferentInstances() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "sess-456", "task-path", System.nanoTime());
            
            ManagedThreadContext context1 = snapshot.restore();
            ManagedThreadContext context2 = snapshot.restore();
            
            assertThat(context1).isNotSameAs(context2);
        }

        @Test
        @DisplayName("最小化快照restore应该成功")
        void minimumSnapshot_restore_shouldSucceed() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, System.nanoTime());
            
            ManagedThreadContext context = snapshot.restore();
            
            assertThat(context).isNotNull();
        }
    }

    @Nested
    @DisplayName("equals和hashCode测试")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("相同参数的快照应该相等")
        void snapshotsWithSameParameters_shouldBeEqual() {
            String contextId = "ctx-123";
            String sessionId = "sess-456";
            String taskPath = "/task/path";
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot1 = new ContextSnapshot(contextId, sessionId, taskPath, timestamp);
            ContextSnapshot snapshot2 = new ContextSnapshot(contextId, sessionId, taskPath, timestamp);
            
            assertThat(snapshot1).isEqualTo(snapshot2);
            assertThat(snapshot1.hashCode()).isEqualTo(snapshot2.hashCode());
        }

        @Test
        @DisplayName("不同contextId的快照应该不相等")
        void snapshotsWithDifferentContextId_shouldNotBeEqual() {
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot1 = new ContextSnapshot("ctx-123", "sess-456", "/task/path", timestamp);
            ContextSnapshot snapshot2 = new ContextSnapshot("ctx-456", "sess-456", "/task/path", timestamp);
            
            assertThat(snapshot1).isNotEqualTo(snapshot2);
        }

        @Test
        @DisplayName("不同sessionId的快照应该不相等")
        void snapshotsWithDifferentSessionId_shouldNotBeEqual() {
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot1 = new ContextSnapshot("ctx-123", "sess-456", "/task/path", timestamp);
            ContextSnapshot snapshot2 = new ContextSnapshot("ctx-123", "sess-789", "/task/path", timestamp);
            
            assertThat(snapshot1).isNotEqualTo(snapshot2);
        }

        @Test
        @DisplayName("不同taskPath的快照应该不相等")
        void snapshotsWithDifferentTaskPath_shouldNotBeEqual() {
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot1 = new ContextSnapshot("ctx-123", "sess-456", "/task/path1", timestamp);
            ContextSnapshot snapshot2 = new ContextSnapshot("ctx-123", "sess-456", "/task/path2", timestamp);
            
            assertThat(snapshot1).isNotEqualTo(snapshot2);
        }

        @Test
        @DisplayName("不同timestamp的快照应该不相等")
        void snapshotsWithDifferentTimestamp_shouldNotBeEqual() {
            ContextSnapshot snapshot1 = new ContextSnapshot("ctx-123", "sess-456", "/task/path", 1000000L);
            ContextSnapshot snapshot2 = new ContextSnapshot("ctx-123", "sess-456", "/task/path", 2000000L);
            
            assertThat(snapshot1).isNotEqualTo(snapshot2);
        }

        @Test
        @DisplayName("与自身比较应该相等")
        void compareWithSelf_shouldBeEqual() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "sess-456", "/task/path", System.nanoTime());
            
            assertThat(snapshot).isEqualTo(snapshot);
        }

        @Test
        @DisplayName("与null比较应该不相等")
        void compareWithNull_shouldNotBeEqual() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "sess-456", "/task/path", System.nanoTime());
            
            assertThat(snapshot).isNotEqualTo(null);
        }

        @Test
        @DisplayName("与不同类型比较应该不相等")
        void compareWithDifferentType_shouldNotBeEqual() {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "sess-456", "/task/path", System.nanoTime());
            
            assertThat(snapshot).isNotEqualTo("string");
        }

        @Test
        @DisplayName("null值的快照equals应该正确工作")
        void snapshotsWithNullValues_equalsShouldWork() {
            long timestamp = System.nanoTime();
            
            ContextSnapshot snapshot1 = new ContextSnapshot("ctx-123", null, null, timestamp);
            ContextSnapshot snapshot2 = new ContextSnapshot("ctx-123", null, null, timestamp);
            ContextSnapshot snapshot3 = new ContextSnapshot("ctx-123", "sess-456", null, timestamp);
            
            assertThat(snapshot1).isEqualTo(snapshot2);
            assertThat(snapshot1).isNotEqualTo(snapshot3);
        }
    }

    @Nested
    @DisplayName("toString测试")
    class ToStringTests {

        @Test
        @DisplayName("toString应该包含所有关键信息")
        void toString_shouldContainAllKeyInfo() {
            String contextId = "ctx-123";
            String sessionId = "sess-456";
            String taskPath = "/task/path";
            ContextSnapshot snapshot = new ContextSnapshot(contextId, sessionId, taskPath, System.nanoTime());
            
            String result = snapshot.toString();
            
            assertThat(result).contains(contextId);
            assertThat(result).contains(sessionId);
            assertThat(result).contains(taskPath);
            assertThat(result).contains("ContextSnapshot");
            assertThat(result).contains("age=");
            assertThat(result).contains("ms");
        }

        @Test
        @DisplayName("toString对于null值应该正确显示")
        void toString_shouldHandleNullValuesCorrectly() {
            String contextId = "ctx-123";
            ContextSnapshot snapshot = new ContextSnapshot(contextId, null, null, System.nanoTime());
            
            String result = snapshot.toString();
            
            assertThat(result).contains(contextId);
            assertThat(result).contains("null");
            assertThat(result).contains("ContextSnapshot");
        }

        @Test
        @DisplayName("toString应该包含年龄信息")
        void toString_shouldIncludeAgeInfo() throws InterruptedException {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, System.nanoTime());
            
            Thread.sleep(10);
            String result = snapshot.toString();
            
            assertThat(result).matches(".*age=\\d+ms.*");
        }
    }

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("并发访问getAgeNanos应该线程安全")
        @Timeout(10)
        void concurrentGetAgeNanos_shouldBeThreadSafe() throws InterruptedException {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "sess-456", "/task/path", System.nanoTime());
            int threadCount = 10;
            int operationsPerThread = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            long age = snapshot.getAgeNanos();
                            assertThat(age).isGreaterThanOrEqualTo(0);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }
            
            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("并发restore应该返回不同实例")
        @Timeout(10)
        void concurrentRestore_shouldReturnDifferentInstances() throws InterruptedException {
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", "sess-456", "task-path", System.nanoTime());
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ManagedThreadContext[] contexts = new ManagedThreadContext[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        contexts[index] = snapshot.restore();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }
            
            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            
            // 验证所有实例都不为null且不相同
            for (int i = 0; i < threadCount; i++) {
                assertThat(contexts[i]).isNotNull();
                for (int j = i + 1; j < threadCount; j++) {
                    assertThat(contexts[i]).isNotSameAs(contexts[j]);
                }
            }
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("极大的timestamp应该被处理")
        void largeTimestamp_shouldBeHandled() {
            long largeTimestamp = Long.MAX_VALUE - 1000;
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, largeTimestamp);
            
            assertThat(snapshot.getTimestamp()).isEqualTo(largeTimestamp);
            // 年龄应该是负数（因为timestamp在未来）
            assertThat(snapshot.getAgeNanos()).isNegative();
        }

        @Test
        @DisplayName("极小的timestamp应该被处理")
        void smallTimestamp_shouldBeHandled() {
            long smallTimestamp = 1000L;
            ContextSnapshot snapshot = new ContextSnapshot("ctx-123", null, null, smallTimestamp);
            
            assertThat(snapshot.getTimestamp()).isEqualTo(smallTimestamp);
            // 年龄应该是正数且很大
            assertThat(snapshot.getAgeNanos()).isPositive();
        }

        @Test
        @DisplayName("很长的字符串应该被处理")
        void longStrings_shouldBeHandled() {
            String longString = "a".repeat(10000);
            ContextSnapshot snapshot = new ContextSnapshot(longString, longString, longString, System.nanoTime());
            
            assertThat(snapshot.getContextId()).isEqualTo(longString);
            assertThat(snapshot.getSessionId()).isEqualTo(longString);
            assertThat(snapshot.getTaskPath()).isEqualTo(longString);
        }

        @Test
        @DisplayName("包含特殊字符的字符串应该被处理")
        void stringsWithSpecialCharacters_shouldBeHandled() {
            String specialString = "ctx\n\t\r\"'\\/<>{}[]()";
            ContextSnapshot snapshot = new ContextSnapshot(specialString, specialString, specialString, System.nanoTime());
            
            assertThat(snapshot.getContextId()).isEqualTo(specialString);
            assertThat(snapshot.getSessionId()).isEqualTo(specialString);
            assertThat(snapshot.getTaskPath()).isEqualTo(specialString);
        }
    }

    @Nested
    @DisplayName("不可变性测试")
    class ImmutabilityTests {

        @Test
        @DisplayName("所有字段应该保持不可变")
        void allFields_shouldRemainImmutable() {
            String originalContextId = "ctx-123";
            String originalSessionId = "sess-456";
            String originalTaskPath = "/task/path";
            long originalTimestamp = System.nanoTime();
            
            ContextSnapshot snapshot = new ContextSnapshot(originalContextId, originalSessionId, originalTaskPath, originalTimestamp);
            
            // 多次获取值应该返回相同的结果
            assertThat(snapshot.getContextId()).isEqualTo(originalContextId);
            assertThat(snapshot.getContextId()).isEqualTo(originalContextId);
            assertThat(snapshot.getSessionId()).isEqualTo(originalSessionId);
            assertThat(snapshot.getSessionId()).isEqualTo(originalSessionId);
            assertThat(snapshot.getTaskPath()).isEqualTo(originalTaskPath);
            assertThat(snapshot.getTaskPath()).isEqualTo(originalTaskPath);
            assertThat(snapshot.getTimestamp()).isEqualTo(originalTimestamp);
            assertThat(snapshot.getTimestamp()).isEqualTo(originalTimestamp);
        }
    }
}