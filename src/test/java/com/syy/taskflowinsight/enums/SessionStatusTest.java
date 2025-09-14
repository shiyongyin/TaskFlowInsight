package com.syy.taskflowinsight.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionStatus枚举测试类
 * 测试会话状态枚举的所有功能和状态转换逻辑
 */
class SessionStatusTest {
    
    @Test
    void testEnumValues() {
        SessionStatus[] values = SessionStatus.values();
        assertEquals(3, values.length);
        
        assertEquals(SessionStatus.RUNNING, SessionStatus.valueOf("RUNNING"));
        assertEquals(SessionStatus.COMPLETED, SessionStatus.valueOf("COMPLETED"));
        assertEquals(SessionStatus.ERROR, SessionStatus.valueOf("ERROR"));
    }
    
    @Test
    void testIsActive() {
        assertTrue(SessionStatus.RUNNING.isActive());
        assertFalse(SessionStatus.COMPLETED.isActive());
        assertFalse(SessionStatus.ERROR.isActive());
    }
    
    @Test
    void testIsTerminated() {
        assertFalse(SessionStatus.RUNNING.isTerminated());
        assertTrue(SessionStatus.COMPLETED.isTerminated());
        assertTrue(SessionStatus.ERROR.isTerminated());
    }
    
    @Test
    void testIsCompleted() {
        assertFalse(SessionStatus.RUNNING.isCompleted());
        assertTrue(SessionStatus.COMPLETED.isCompleted());
        assertFalse(SessionStatus.ERROR.isCompleted());
    }
    
    @Test
    void testIsError() {
        assertFalse(SessionStatus.RUNNING.isError());
        assertFalse(SessionStatus.COMPLETED.isError());
        assertTrue(SessionStatus.ERROR.isError());
    }
    
    @Test
    void testCanTransitionToFromRunning() {
        // RUNNING可以转换到COMPLETED和ERROR
        assertTrue(SessionStatus.RUNNING.canTransitionTo(SessionStatus.COMPLETED));
        assertTrue(SessionStatus.RUNNING.canTransitionTo(SessionStatus.ERROR));
        assertFalse(SessionStatus.RUNNING.canTransitionTo(SessionStatus.RUNNING));
    }
    
    @Test
    void testCanTransitionToFromCompleted() {
        // COMPLETED不能转换到任何状态
        assertFalse(SessionStatus.COMPLETED.canTransitionTo(SessionStatus.RUNNING));
        assertFalse(SessionStatus.COMPLETED.canTransitionTo(SessionStatus.COMPLETED));
        assertFalse(SessionStatus.COMPLETED.canTransitionTo(SessionStatus.ERROR));
    }
    
    @Test
    void testCanTransitionToFromError() {
        // ERROR不能转换到任何状态
        assertFalse(SessionStatus.ERROR.canTransitionTo(SessionStatus.RUNNING));
        assertFalse(SessionStatus.ERROR.canTransitionTo(SessionStatus.COMPLETED));
        assertFalse(SessionStatus.ERROR.canTransitionTo(SessionStatus.ERROR));
    }
    
    @Test
    void testCanTransitionToWithNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SessionStatus.RUNNING.canTransitionTo(null)
        );
        assertEquals("Target status cannot be null", exception.getMessage());
    }
    
    @Test
    void testStateTransitionLogic() {
        // 验证状态转换的完整逻辑
        SessionStatus[] allStates = SessionStatus.values();
        
        for (SessionStatus from : allStates) {
            for (SessionStatus to : allStates) {
                boolean canTransition = from.canTransitionTo(to);
                
                if (from == SessionStatus.RUNNING) {
                    // RUNNING只能转换到终止状态
                    assertEquals(to == SessionStatus.COMPLETED || to == SessionStatus.ERROR, canTransition);
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
        SessionStatus[] values = SessionStatus.values();
        assertEquals(SessionStatus.RUNNING, values[0]);
        assertEquals(SessionStatus.COMPLETED, values[1]);
        assertEquals(SessionStatus.ERROR, values[2]);
        
        // 验证状态分类的完整性
        for (SessionStatus status : values) {
            boolean isActive = status.isActive();
            boolean isTerminated = status.isTerminated();
            
            // 每个状态要么是活跃的，要么是终止的，不能两者都是或都不是
            assertTrue(isActive ^ isTerminated);
        }
    }
    
    @Test
    void testStatusSemantics() {
        // 验证状态语义的一致性
        
        // RUNNING状态的语义
        assertTrue(SessionStatus.RUNNING.isActive());
        assertFalse(SessionStatus.RUNNING.isTerminated());
        assertFalse(SessionStatus.RUNNING.isCompleted());
        assertFalse(SessionStatus.RUNNING.isError());
        
        // COMPLETED状态的语义
        assertFalse(SessionStatus.COMPLETED.isActive());
        assertTrue(SessionStatus.COMPLETED.isTerminated());
        assertTrue(SessionStatus.COMPLETED.isCompleted());
        assertFalse(SessionStatus.COMPLETED.isError());
        
        // ERROR状态的语义
        assertFalse(SessionStatus.ERROR.isActive());
        assertTrue(SessionStatus.ERROR.isTerminated());
        assertFalse(SessionStatus.ERROR.isCompleted());
        assertTrue(SessionStatus.ERROR.isError());
    }
}