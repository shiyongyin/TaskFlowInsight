package com.syy.taskflowinsight.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskStatus 枚举测试 - 任务状态定义与状态转换逻辑验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>基于有限状态机理论设计状态转换测试</li>
 *   <li>使用状态分类测试验证活跃/终止状态的正确分类</li>
 *   <li>通过完备性测试确保所有状态转换路径都被验证</li>
 *   <li>采用异常安全测试验证错误输入的处理</li>
 *   <li>使用一致性测试确保状态设计的逻辑完整性</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>枚举完整性：</strong>3种任务状态（RUNNING、COMPLETED、FAILED）</li>
 *   <li><strong>状态分类：</strong>活跃状态（isActive）vs 终止状态（isTerminated）</li>
 *   <li><strong>结果分类：</strong>成功状态（isSuccessful）vs 失败状态（isFailed）</li>
 *   <li><strong>状态转换：</strong>canTransitionTo方法验证合法转换路径</li>
 *   <li><strong>转换规则：</strong>RUNNING→COMPLETED/FAILED，终止状态不可再转换</li>
 *   <li><strong>异常处理：</strong>null参数的IllegalArgumentException处理</li>
 *   <li><strong>状态互斥：</strong>活跃与终止状态的互斥性验证</li>
 * </ul>
 * 
 * <h2>状态转换规则：</h2>
 * <ul>
 *   <li><strong>RUNNING（运行中）：</strong>活跃状态，可转换到COMPLETED或FAILED</li>
 *   <li><strong>COMPLETED（已完成）：</strong>终止状态，成功结果，不可再转换</li>
 *   <li><strong>FAILED（已失败）：</strong>终止状态，失败结果，不可再转换</li>
 * </ul>
 * 
 * <h2>测试场景：</h2>
 * <ul>
 *   <li><strong>基础属性：</strong>枚举值存在性、valueOf正确性</li>
 *   <li><strong>状态分类：</strong>每种状态的isActive/isTerminated分类</li>
 *   <li><strong>结果判断：</strong>成功/失败状态的正确识别</li>
 *   <li><strong>转换验证：</strong>所有状态间转换可能性的完整矩阵测试</li>
 *   <li><strong>逻辑一致性：</strong>活跃与终止状态的互斥性</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>枚举完整：</strong>3个枚举值按预期顺序存在</li>
 *   <li><strong>分类正确：</strong>RUNNING为活跃，COMPLETED/FAILED为终止</li>
 *   <li><strong>结果准确：</strong>只有COMPLETED为成功，只有FAILED为失败</li>
 *   <li><strong>转换合理：</strong>只有从RUNNING能转换到终止状态</li>
 *   <li><strong>逻辑一致：</strong>活跃与终止状态严格互斥</li>
 *   <li><strong>异常安全：</strong>null输入正确抛出异常</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
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