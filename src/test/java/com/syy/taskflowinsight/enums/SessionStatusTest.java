package com.syy.taskflowinsight.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionStatus 枚举测试 - 会话状态定义与状态转换逻辑验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>基于会话生命周期设计状态转换测试</li>
 *   <li>使用状态语义测试验证每种状态的业务含义</li>
 *   <li>通过完备性矩阵测试确保所有状态转换路径都被验证</li>
 *   <li>采用状态分类测试验证活跃/终止状态的正确分类</li>
 *   <li>使用一致性测试确保状态设计的逻辑完整性</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>枚举完整性：</strong>3种会话状态（RUNNING、COMPLETED、ERROR）</li>
 *   <li><strong>状态分类：</strong>活跃状态（isActive）vs 终止状态（isTerminated）</li>
 *   <li><strong>结果分类：</strong>完成状态（isCompleted）vs 错误状态（isError）</li>
 *   <li><strong>状态转换：</strong>canTransitionTo方法验证合法转换路径</li>
 *   <li><strong>转换规则：</strong>RUNNING→COMPLETED/ERROR，终止状态不可再转换</li>
 *   <li><strong>状态语义：</strong>每种状态的完整语义验证</li>
 *   <li><strong>异常处理：</strong>null参数的IllegalArgumentException处理</li>
 *   <li><strong>状态互斥：</strong>活跃与终止状态的互斥性验证</li>
 * </ul>
 * 
 * <h2>状态转换规则：</h2>
 * <ul>
 *   <li><strong>RUNNING（运行中）：</strong>活跃状态，可转换到COMPLETED或ERROR</li>
 *   <li><strong>COMPLETED（已完成）：</strong>终止状态，成功完成，不可再转换</li>
 *   <li><strong>ERROR（错误终止）：</strong>终止状态，异常结束，不可再转换</li>
 * </ul>
 * 
 * <h2>测试场景：</h2>
 * <ul>
 *   <li><strong>基础属性：</strong>枚举值存在性、valueOf正确性</li>
 *   <li><strong>状态分类：</strong>每种状态的isActive/isTerminated分类</li>
 *   <li><strong>结果判断：</strong>完成/错误状态的正确识别</li>
 *   <li><strong>转换验证：</strong>3×3状态转换矩阵的完整测试</li>
 *   <li><strong>语义一致性：</strong>每种状态的所有属性方法验证</li>
 *   <li><strong>逻辑完整性：</strong>活跃与终止状态的互斥性</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>枚举完整：</strong>3个枚举值按预期顺序存在</li>
 *   <li><strong>分类正确：</strong>RUNNING为活跃，COMPLETED/ERROR为终止</li>
 *   <li><strong>结果准确：</strong>只有COMPLETED为完成，只有ERROR为错误</li>
 *   <li><strong>转换合理：</strong>只有从RUNNING能转换到终止状态</li>
 *   <li><strong>语义一致：</strong>每种状态的所有属性方法返回值符合预期</li>
 *   <li><strong>逻辑完整：</strong>活跃与终止状态严格互斥</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
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