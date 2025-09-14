package com.syy.taskflowinsight.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskStatus枚举测试类
 * 测试任务状态枚举的所有功能和状态转换逻辑
 */
class TaskStatusTest {
    
    @Test
    void testEnumValues() {
        TaskStatus[] values = TaskStatus.values();
        assertEquals(3, values.length);
        
        assertEquals(TaskStatus.RUNNING, TaskStatus.valueOf("RUNNING"));
        assertEquals(TaskStatus.COMPLETED, TaskStatus.valueOf("COMPLETED"));
        assertEquals(TaskStatus.FAILED, TaskStatus.valueOf("FAILED"));
    }
    
    @Test
    void testIsActive() {
        assertTrue(TaskStatus.RUNNING.isActive());
        assertFalse(TaskStatus.COMPLETED.isActive());
        assertFalse(TaskStatus.FAILED.isActive());
    }
    
    @Test
    void testIsTerminated() {
        assertFalse(TaskStatus.RUNNING.isTerminated());
        assertTrue(TaskStatus.COMPLETED.isTerminated());
        assertTrue(TaskStatus.FAILED.isTerminated());
    }
    
    @Test
    void testIsSuccessful() {
        assertFalse(TaskStatus.RUNNING.isSuccessful());
        assertTrue(TaskStatus.COMPLETED.isSuccessful());
        assertFalse(TaskStatus.FAILED.isSuccessful());
    }
    
    @Test
    void testIsFailed() {
        assertFalse(TaskStatus.RUNNING.isFailed());
        assertFalse(TaskStatus.COMPLETED.isFailed());
        assertTrue(TaskStatus.FAILED.isFailed());
    }
    
    @Test
    void testCanTransitionToFromRunning() {
        // RUNNING可以转换到COMPLETED和FAILED
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.COMPLETED));
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.FAILED));
        assertFalse(TaskStatus.RUNNING.canTransitionTo(TaskStatus.RUNNING));
    }
    
    @Test
    void testCanTransitionToFromCompleted() {
        // COMPLETED不能转换到任何状态
        assertFalse(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.RUNNING));
        assertFalse(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.COMPLETED));
        assertFalse(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.FAILED));
    }
    
    @Test
    void testCanTransitionToFromFailed() {
        // FAILED不能转换到任何状态
        assertFalse(TaskStatus.FAILED.canTransitionTo(TaskStatus.RUNNING));
        assertFalse(TaskStatus.FAILED.canTransitionTo(TaskStatus.COMPLETED));
        assertFalse(TaskStatus.FAILED.canTransitionTo(TaskStatus.FAILED));
    }
    
    @Test
    void testCanTransitionToWithNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TaskStatus.RUNNING.canTransitionTo(null)
        );
        assertEquals("Target status cannot be null", exception.getMessage());
    }
    
    @Test
    void testStateTransitionLogic() {
        // 验证状态转换的完整逻辑
        TaskStatus[] allStates = TaskStatus.values();
        
        for (TaskStatus from : allStates) {
            for (TaskStatus to : allStates) {
                boolean canTransition = from.canTransitionTo(to);
                
                if (from == TaskStatus.RUNNING) {
                    // RUNNING只能转换到终止状态
                    assertEquals(to == TaskStatus.COMPLETED || to == TaskStatus.FAILED, canTransition);
                } else {
                    // 终止状态不能转换
                    assertFalse(canTransition);
                }
            }
        }
    }
    
    @Test
    void testEnumConsistency() {
        // 确保枚举顺序的一致性
        TaskStatus[] values = TaskStatus.values();
        assertEquals(TaskStatus.RUNNING, values[0]);
        assertEquals(TaskStatus.COMPLETED, values[1]);
        assertEquals(TaskStatus.FAILED, values[2]);
        
        // 验证状态分类的完整性
        for (TaskStatus status : values) {
            boolean isActive = status.isActive();
            boolean isTerminated = status.isTerminated();
            
            // 每个状态要么是活跃的，要么是终止的，不能两者都是或都不是
            assertTrue(isActive ^ isTerminated);
        }
    }
}