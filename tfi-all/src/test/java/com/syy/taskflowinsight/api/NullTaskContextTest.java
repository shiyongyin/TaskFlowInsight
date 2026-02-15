package com.syy.taskflowinsight.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NullTaskContext 全面测试套件
 * 测试目标：发现空对象模式实现中的潜在问题
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("NullTaskContext 综合测试")
class NullTaskContextTest {

    @Nested
    @DisplayName("单例模式验证")
    class SingletonPatternTests {
        
        @Test
        @DisplayName("单例实例应该唯一且可重复访问")
        void singletonInstanceShouldBeUniqueAndReusable() {
            NullTaskContext instance1 = NullTaskContext.INSTANCE;
            NullTaskContext instance2 = NullTaskContext.INSTANCE;
            
            assertThat(instance1).isSameAs(instance2);
            assertThat(instance1).isNotNull();
        }
        
        @Test
        @DisplayName("toString方法应该返回有意义的标识")
        void toStringShouldReturnMeaningfulIdentification() {
            String result = NullTaskContext.INSTANCE.toString();
            
            assertThat(result).isEqualTo("NullTaskContext");
            assertThat(result).isNotEmpty();
            assertThat(result).doesNotContain("@"); // 避免默认Object.toString()
        }
    }

    @Nested
    @DisplayName("任务上下文链式调用")
    class FluentInterfaceTests {
        
        @Test
        @DisplayName("所有链式方法应该返回同一实例")
        void allFluentMethodsShouldReturnSameInstance() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            assertThat(context.message("test")).isSameAs(context);
            assertThat(context.debug("test")).isSameAs(context);
            assertThat(context.warn("test")).isSameAs(context);
            assertThat(context.error("test")).isSameAs(context);
            assertThat(context.error("test", new RuntimeException())).isSameAs(context);
            assertThat(context.attribute("key", "value")).isSameAs(context);
            assertThat(context.tag("tag")).isSameAs(context);
            assertThat(context.success()).isSameAs(context);
            assertThat(context.fail()).isSameAs(context);
            assertThat(context.fail(new RuntimeException())).isSameAs(context);
            assertThat(context.subtask("subtask")).isSameAs(context);
        }
        
        @Test
        @DisplayName("链式调用应该保持一致性")
        void chainedCallsShouldMaintainConsistency() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            TaskContext result = context
                .message("Starting task")
                .debug("Debug info")
                .attribute("key", "value")
                .tag("important")
                .warn("Warning message")
                .success();
            
            assertThat(result).isSameAs(context);
        }
    }

    @Nested
    @DisplayName("状态和属性验证")
    class StateAndPropertyTests {
        
        @Test
        @DisplayName("任务状态应该返回安全的默认值")
        void taskStateShouldReturnSafeDefaults() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            assertThat(context.isClosed()).isFalse();
            assertThat(context.getTaskName()).isEqualTo("");
            assertThat(context.getTaskId()).isEqualTo("");
        }
        
        @Test
        @DisplayName("close方法应该安全执行")
        void closeShouldExecuteSafely() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            // 应该不抛出异常
            context.close();
            
            // 状态应该保持不变
            assertThat(context.isClosed()).isFalse();
        }
    }

    @Nested
    @DisplayName("边界条件和异常处理")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("空值参数应该被安全处理")
        void nullParametersShouldBeHandledSafely() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            // 这些调用应该不抛出异常
            assertThat(context.message(null)).isSameAs(context);
            assertThat(context.debug(null)).isSameAs(context);
            assertThat(context.warn(null)).isSameAs(context);
            assertThat(context.error(null)).isSameAs(context);
            assertThat(context.error(null, null)).isSameAs(context);
            assertThat(context.error("test", null)).isSameAs(context);
            assertThat(context.attribute(null, null)).isSameAs(context);
            assertThat(context.attribute("key", null)).isSameAs(context);
            assertThat(context.tag(null)).isSameAs(context);
            assertThat(context.fail(null)).isSameAs(context);
            assertThat(context.subtask(null)).isSameAs(context);
        }
        
        @Test
        @DisplayName("极端长度字符串应该被安全处理")
        void extremeLengthStringsShouldBeHandledSafely() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            String emptyString = "";
            String longString = "a".repeat(10000);
            
            assertThat(context.message(emptyString)).isSameAs(context);
            assertThat(context.message(longString)).isSameAs(context);
            assertThat(context.tag(emptyString)).isSameAs(context);
            assertThat(context.tag(longString)).isSameAs(context);
            assertThat(context.subtask(emptyString)).isSameAs(context);
            assertThat(context.subtask(longString)).isSameAs(context);
        }
        
        @Test
        @DisplayName("特殊字符应该被安全处理")
        void specialCharactersShouldBeHandledSafely() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            String specialChars = "\n\r\t\0\u0001\u001F\uFFFF";
            String unicodeChars = "测试\uD83D\uDE00\uD83C\uDF89";
            
            assertThat(context.message(specialChars)).isSameAs(context);
            assertThat(context.debug(unicodeChars)).isSameAs(context);
            assertThat(context.attribute(specialChars, unicodeChars)).isSameAs(context);
        }
    }

    @Nested
    @DisplayName("内存和性能特性")
    class MemoryAndPerformanceTests {
        
        @Test
        @DisplayName("频繁调用不应导致内存泄漏")
        void frequentCallsShouldNotCauseMemoryLeaks() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            // 执行大量操作，验证没有累积状态
            for (int i = 0; i < 10000; i++) {
                context.message("message" + i)
                      .debug("debug" + i)
                      .attribute("key" + i, "value" + i)
                      .tag("tag" + i)
                      .success();
            }
            
            // 验证状态保持不变
            assertThat(context.getTaskName()).isEqualTo("");
            assertThat(context.getTaskId()).isEqualTo("");
            assertThat(context.isClosed()).isFalse();
        }
        
        @Test
        @DisplayName("多层嵌套子任务调用应保持一致性")
        void deeplyNestedSubtaskCallsShouldMaintainConsistency() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            TaskContext current = context;
            
            // 创建深层嵌套调用
            for (int i = 0; i < 100; i++) {
                current = current.subtask("level" + i);
                assertThat(current).isSameAs(context);
            }
            
            // 验证最终状态
            assertThat(current.getTaskName()).isEqualTo("");
            assertThat(current.getTaskId()).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("类型安全和契约验证")
    class TypeSafetyAndContractTests {
        
        @Test
        @DisplayName("TaskContext接口契约应该被正确实现")
        void taskContextContractShouldBeProperlyImplemented() {
            TaskContext context = NullTaskContext.INSTANCE;
            
            // 验证接口类型
            assertThat(context).isInstanceOf(TaskContext.class);
            assertThat(context).isInstanceOf(NullTaskContext.class);
            
            // 验证所有方法都有正确的返回类型
            TaskContext result = context.message("test");
            assertThat(result).isInstanceOf(TaskContext.class);
        }
        
        @Test
        @DisplayName("方法参数类型应该被正确处理")
        void methodParameterTypesShouldBeHandledCorrectly() {
            NullTaskContext context = NullTaskContext.INSTANCE;
            
            // 测试不同类型的属性值
            assertThat(context.attribute("string", "value")).isSameAs(context);
            assertThat(context.attribute("int", 42)).isSameAs(context);
            assertThat(context.attribute("boolean", true)).isSameAs(context);
            assertThat(context.attribute("array", new int[]{1, 2, 3})).isSameAs(context);
            assertThat(context.attribute("object", new Object())).isSameAs(context);
            
            // 测试异常类型
            assertThat(context.error("test", new RuntimeException())).isSameAs(context);
            assertThat(context.error("test", new Error())).isSameAs(context);
            assertThat(context.fail(new Exception())).isSameAs(context);
        }
    }
}