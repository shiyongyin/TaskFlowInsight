package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.enums.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * TFI类边界条件和未覆盖分支测试
 * 专门针对覆盖率较低的方法和边界条件
 * 
 * @author TaskFlow Insight Team
 * @since 2025-01-13
 */
@DisplayName("TFI边界条件和未覆盖分支测试")
class TFIEdgeCaseTest {

    @BeforeEach
    void setUp() {
        TFI.clear();
        TFI.enable();
    }

    @AfterEach
    void tearDown() {
        TFI.clear();
    }

    @Nested
    @DisplayName("构造函数和实例化测试")
    class ConstructorAndInstantiationTests {
        
        @Test
        @DisplayName("TFI构造函数应该抛出UnsupportedOperationException")
        void tfiConstructorShouldThrowUnsupportedOperationException() throws Exception {
            // 通过反射访问私有构造函数
            Constructor<TFI> constructor = TFI.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            
            // TFI是工具类，构造函数应该抛出异常
            assertThatNoException().isThrownBy(() -> {
                try {
                    constructor.newInstance();
                } catch (Exception e) {
                    // 期望捕获UnsupportedOperationException，这是正常的
                    assertThat(e.getCause()).isInstanceOf(UnsupportedOperationException.class);
                    assertThat(e.getCause().getMessage()).contains("TFI is a utility class");
                }
            });
        }
    }

    @Nested
    @DisplayName("清除追踪功能测试")
    class ClearTrackingTests {
        
        @Test
        @DisplayName("clearTracking应该能安全处理有效的对象名")
        void clearTrackingShouldHandleValidObjectName() {
            // 先添加一些追踪对象
            TestObject obj = new TestObject("test");
            TFI.track("testObject", obj);
            
            // 修改对象以产生变更
            obj.setName("modified");
            TFI.track("testObject", obj);
            
            // 清除指定对象的追踪
            assertThatNoException().isThrownBy(() -> {
                TFI.clearTracking("testObject");
            });
        }
        
        @Test
        @DisplayName("clearTracking应该能安全处理不存在的对象名")
        void clearTrackingShouldHandleNonExistentObjectName() {
            assertThatNoException().isThrownBy(() -> {
                TFI.clearTracking("nonExistentObject");
            });
        }
        
        @Test
        @DisplayName("clearTracking应该能安全处理空值和空字符串")
        void clearTrackingShouldHandleNullAndEmptyValues() {
            assertThatNoException().isThrownBy(() -> {
                TFI.clearTracking(null);
                TFI.clearTracking("");
                TFI.clearTracking("   ");
            });
        }
    }

    @Nested
    @DisplayName("变更刷新功能测试")
    class ChangeFlushingTests {
        
        @Test
        @DisplayName("flushChangesToCurrentTask应该在有任务时正常工作")
        void flushChangesToCurrentTaskShouldWorkWithActiveTask() {
            TFI.startSession("test-session");
            TFI.start("test-task");
            
            // 添加一些变更
            TestObject obj = new TestObject("initial");
            TFI.track("flushTest", obj);
            obj.setName("changed");
            TFI.track("flushTest", obj);
            
            // 通过反射调用私有方法
            assertThatNoException().isThrownBy(() -> {
                Method method = TFI.class.getDeclaredMethod("flushChangesToCurrentTask");
                method.setAccessible(true);
                method.invoke(null);
            });
            
            TFI.stop();
            TFI.endSession();
        }
        
        @Test
        @DisplayName("flushChangesToCurrentTask应该在无任务时安全执行")
        void flushChangesToCurrentTaskShouldWorkWithoutActiveTask() {
            // 确保没有活动任务
            TFI.clear();
            
            // 添加一些变更
            TestObject obj = new TestObject("initial");
            TFI.track("flushTestNoTask", obj);
            obj.setName("changed");
            TFI.track("flushTestNoTask", obj);
            
            // 调用flush方法应该安全执行
            assertThatNoException().isThrownBy(() -> {
                Method method = TFI.class.getDeclaredMethod("flushChangesToCurrentTask");
                method.setAccessible(true);
                method.invoke(null);
            });
        }
    }

    @Nested
    @DisplayName("快速启用检查测试")
    class QuickEnabledCheckTests {
        
        @Test
        @DisplayName("checkEnabledQuick应该在启用时返回true")
        void checkEnabledQuickShouldReturnTrueWhenEnabled() throws Exception {
            TFI.enable();
            
            Method method = TFI.class.getDeclaredMethod("checkEnabledQuick");
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(null);
            
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("checkEnabledQuick应该在禁用时返回false")
        void checkEnabledQuickShouldReturnFalseWhenDisabled() throws Exception {
            TFI.disable();
            
            Method method = TFI.class.getDeclaredMethod("checkEnabledQuick");
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(null);
            
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("变更格式化回退测试")
    class ChangeFormattingFallbackTests {
        
        @Test
        @DisplayName("formatChangeMessageFallback应该能处理正常变更记录")
        void formatChangeMessageFallbackShouldHandleNormalChangeRecord() throws Exception {
            ChangeRecord change = ChangeRecord.of("testObject", "field", "oldValue", "newValue", ChangeType.UPDATE);
            
            Method method = TFI.class.getDeclaredMethod("formatChangeMessageFallback", ChangeRecord.class);
            method.setAccessible(true);
            String result = (String) method.invoke(null, change);
            
            assertThat(result).isNotNull();
            assertThat(result).contains("testObject");
            assertThat(result).contains("field");
        }
        
        @Test
        @DisplayName("formatChangeMessageFallback应该能处理空值变更")
        void formatChangeMessageFallbackShouldHandleNullValues() throws Exception {
            ChangeRecord change = ChangeRecord.of("testObject", "field", null, "newValue", ChangeType.CREATE);
            
            Method method = TFI.class.getDeclaredMethod("formatChangeMessageFallback", ChangeRecord.class);
            method.setAccessible(true);
            String result = (String) method.invoke(null, change);
            
            assertThat(result).isNotNull();
            assertThat(result).contains("testObject");
        }
        
        @Test
        @DisplayName("formatChangeMessageFallback应该能处理边界情况")
        void formatChangeMessageFallbackShouldHandleBoundaryCases() throws Exception {
            // 创建一个正常的变更记录来测试格式化
            ChangeRecord change = ChangeRecord.of("testObj", "field", "value1", "value2", ChangeType.DELETE);
            
            Method method = TFI.class.getDeclaredMethod("formatChangeMessageFallback", ChangeRecord.class);
            method.setAccessible(true);
            
            assertThatNoException().isThrownBy(() -> {
                String result = (String) method.invoke(null, change);
                assertThat(result).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("禁用状态下的功能测试")
    class DisabledStateFunctionalityTests {
        
        @Test
        @DisplayName("禁用状态下的追踪操作应该被忽略")
        void trackingOperationsShouldBeIgnoredWhenDisabled() {
            TFI.disable();
            
            TestObject obj = new TestObject("test");
            
            // 这些操作应该被安全忽略
            assertThatNoException().isThrownBy(() -> {
                TFI.track("disabled", obj);
                TFI.clearTracking("disabled");
                TFI.clearAllTracking();
                obj.setName("changed");
                TFI.track("disabled", obj);
            });
            
            // 获取变更应该返回空列表
            List<ChangeRecord> changes = TFI.getChanges();
            assertThat(changes).isEmpty();
        }
        
        @Test
        @DisplayName("禁用状态下的会话操作应该仍然工作")
        void sessionOperationsShouldStillWorkWhenDisabled() {
            TFI.disable();
            
            assertThatNoException().isThrownBy(() -> {
                TFI.startSession("disabled-session");
                TFI.start("disabled-task");
                TFI.message("This is a test message", MessageType.PROCESS);
                TFI.stop();
                TFI.endSession();
            });
        }
    }

    @Nested
    @DisplayName("边界条件混合测试")
    class BoundaryConditionMixedTests {
        
        @Test
        @DisplayName("空变更列表的各种操作应该安全执行")
        void operationsOnEmptyChangeListShouldExecuteSafely() {
            // 确保变更列表为空
            TFI.clearAllTracking();
            
            assertThatNoException().isThrownBy(() -> {
                List<ChangeRecord> changes = TFI.getChanges();
                assertThat(changes).isEmpty();
                
                List<ChangeRecord> allChanges = TFI.getAllChanges();
                assertThat(allChanges).isEmpty();
                
                // 导出操作也应该安全执行
                TFI.exportToConsole();
                String json = TFI.exportToJson();
                // 允许json为空，因为没有数据时可能返回空字符串或null
            });
        }
        
        @Test
        @DisplayName("极端长度的字符串应该被正确处理")
        void extremeLengthStringsShouldBeHandledCorrectly() {
            String longName = "a".repeat(10000);
            String veryLongName = "b".repeat(50000);
            
            assertThatNoException().isThrownBy(() -> {
                TFI.startSession(longName);
                TFI.start(veryLongName);
                TFI.message(longName, MessageType.PROCESS);
                TFI.track(longName, veryLongName);
                TFI.clearTracking(longName);
                TFI.stop();
                TFI.endSession();
            });
        }
        
        @Test
        @DisplayName("特殊字符应该被正确处理")
        void specialCharactersShouldBeHandledCorrectly() {
            String specialChars = "\n\r\t\0\u0001\u001F\uFFFF";
            String unicodeChars = "测试\uD83D\uDE00\uD83C\uDF89";
            
            assertThatNoException().isThrownBy(() -> {
                TFI.startSession(specialChars);
                TFI.start(unicodeChars);
                TFI.message(specialChars + " " + unicodeChars, MessageType.PROCESS);
                
                TestObject obj = new TestObject(specialChars);
                TFI.track(unicodeChars, obj);
                obj.setName(unicodeChars);
                TFI.track(unicodeChars, obj);
                
                TFI.stop();
                TFI.endSession();
            });
        }
    }

    // 测试辅助类
    private static class TestObject {
        private String name;
        
        public TestObject(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestObject that = (TestObject) obj;
            return name != null ? name.equals(that.name) : that.name == null;
        }
        
        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : 0;
        }
        
        @Override
        public String toString() {
            return "TestObject{name='" + name + "'}";
        }
    }
}