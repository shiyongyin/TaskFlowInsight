package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上下文清理集成测试
 * 验证三处清理点（stop/close/endSession）都能正确清理变更追踪数据
 * 
 * @author TaskFlow Insight Team
 * @version 2.0.0
 * @since 2025-01-10
 */
class ContextCleanupIT {
    
    static class TestObject {
        private String value = "initial";
        public void setValue(String value) { this.value = value; }
    }
    
    @BeforeEach
    void setUp() {
        TFI.enable();
        TFI.setChangeTrackingEnabled(true);
        TFI.clear();
    }
    
    @AfterEach
    void tearDown() {
        TFI.clear();
    }
    
    @Test
    void testStopClearsTracking() {
        // Given
        TFI.startSession("StopCleanupSession");
        TFI.start("TestTask");
        
        TestObject obj = new TestObject();
        TFI.track("TestObj", obj, "value");
        obj.setValue("modified");
        
        // When - stop清理
        TFI.stop();
        
        // Then - 再次获取变更应该为空
        List<ChangeRecord> changes = TFI.getChanges();
        assertTrue(changes.isEmpty(), "stop后应该清理所有追踪数据");
    }
    
    @Test
    void testCloseClearsTracking() {
        // Given
        TFI.startSession("CloseCleanupSession");
        TFI.start("TestTask");
        
        TestObject obj = new TestObject();
        TFI.track("TestObj", obj, "value");
        obj.setValue("modified");
        
        // When - 关闭上下文
        ManagedThreadContext context = ManagedThreadContext.current();
        assertNotNull(context);
        context.close();
        
        // Then - 变更应该被清理
        List<ChangeRecord> changes = TFI.getChanges();
        assertTrue(changes.isEmpty(), "close后应该清理所有追踪数据");
    }
    
    @Test
    void testEndSessionClearsTracking() {
        // Given
        TFI.startSession("EndSessionCleanupSession");
        TFI.start("TestTask");
        
        TestObject obj = new TestObject();
        TFI.track("TestObj", obj, "value");
        obj.setValue("modified");
        
        // 先获取变更确认有数据
        List<ChangeRecord> changesBefore = TFI.getChanges();
        assertFalse(changesBefore.isEmpty(), "修改后应该有变更记录");
        
        // When - 结束会话
        TFI.endSession();
        
        // Then - 变更应该被清理
        List<ChangeRecord> changesAfter = TFI.getChanges();
        assertTrue(changesAfter.isEmpty(), "endSession后应该清理所有追踪数据");
    }
    
    @Test
    void testMultipleClosesAreIdempotent() {
        // Given
        TFI.startSession("IdempotentSession");
        TFI.start("TestTask");
        
        TestObject obj = new TestObject();
        TFI.track("TestObj", obj, "value");
        
        ManagedThreadContext context = ManagedThreadContext.current();
        assertNotNull(context);
        
        // When - 多次关闭
        context.close();
        context.close(); // 第二次close应该是幂等的
        
        // Then - 不应该抛出异常
        List<ChangeRecord> changes = TFI.getChanges();
        assertTrue(changes.isEmpty());
    }
    
    @Test
    void testClearAllTrackingDirectly() {
        // Given
        TFI.startSession("DirectClearSession");
        TFI.start("TestTask");
        
        TestObject obj1 = new TestObject();
        TestObject obj2 = new TestObject();
        
        TFI.track("Obj1", obj1, "value");
        TFI.track("Obj2", obj2, "value");
        
        obj1.setValue("changed1");
        obj2.setValue("changed2");
        
        // 验证有变更
        List<ChangeRecord> changesBefore = TFI.getChanges();
        assertEquals(2, changesBefore.size(), "应该有2个变更");
        
        // When - 直接清理
        TFI.clearAllTracking();
        
        // Then
        List<ChangeRecord> changesAfter = TFI.getChanges();
        assertTrue(changesAfter.isEmpty(), "clearAllTracking后应该无变更");
    }
    
    @Test
    void testTrackingDisabledNoCleaning() {
        // Given - 禁用变更追踪
        TFI.setChangeTrackingEnabled(false);
        
        TFI.startSession("DisabledSession");
        TFI.start("TestTask");
        
        TestObject obj = new TestObject();
        TFI.track("TestObj", obj, "value");
        obj.setValue("modified");
        
        // When
        TFI.stop();
        
        // Then - 由于禁用，不应该有任何追踪数据
        List<ChangeRecord> changes = TFI.getChanges();
        assertTrue(changes.isEmpty());
    }
}