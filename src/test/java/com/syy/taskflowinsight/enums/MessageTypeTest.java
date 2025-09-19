package com.syy.taskflowinsight.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageType 枚举测试 - 业务消息类型定义与功能验证
 * 
 * <h2>测试设计思路：</h2>
 * <ul>
 *   <li>基于枚举特性设计完整性测试，验证所有枚举值的存在</li>
 *   <li>使用业务语义测试验证每种消息类型的业务含义正确性</li>
 *   <li>通过级别比较测试验证消息重要性层次设计</li>
 *   <li>采用状态判断测试验证类型识别方法的准确性</li>
 *   <li>使用一致性测试确保枚举设计的逻辑完整性</li>
 * </ul>
 * 
 * <h2>覆盖范围：</h2>
 * <ul>
 *   <li><strong>枚举完整性：</strong>4种消息类型（PROCESS、METRIC、CHANGE、ALERT）</li>
 *   <li><strong>显示名称：</strong>业务流程、核心指标、变更记录、异常提示的中文显示</li>
 *   <li><strong>级别系统：</strong>1-4级重要性级别设定（PROCESS=1，ALERT=4）</li>
 *   <li><strong>类型判断：</strong>isProcess/isMetric/isChange/isAlert方法验证</li>
 *   <li><strong>级别比较：</strong>compareLevel方法的大小比较逻辑</li>
 *   <li><strong>重要性标记：</strong>isImportant方法识别CHANGE/ALERT为重要消息</li>
 *   <li><strong>异常处理：</strong>null参数的IllegalArgumentException处理</li>
 *   <li><strong>字符串表示：</strong>toString方法包含中文显示名称</li>
 * </ul>
 * 
 * <h2>业务语义验证：</h2>
 * <ul>
 *   <li><strong>PROCESS（业务流程）：</strong>级别1，普通重要性，业务操作流程记录</li>
 *   <li><strong>METRIC（核心指标）：</strong>级别2，普通重要性，性能指标数据</li>
 *   <li><strong>CHANGE（变更记录）：</strong>级别3，重要信息，对象状态变更</li>
 *   <li><strong>ALERT（异常提示）：</strong>级别4，重要信息，错误和警告</li>
 * </ul>
 * 
 * <h2>期望结果：</h2>
 * <ul>
 *   <li><strong>枚举完整：</strong>4个枚举值按预期顺序存在，valueOf正常工作</li>
 *   <li><strong>显示正确：</strong>每种类型的中文显示名称准确匹配业务含义</li>
 *   <li><strong>级别递增：</strong>级别值从1到4递增，符合重要性设计</li>
 *   <li><strong>类型识别准确：</strong>每种类型的is方法只对自身返回true</li>
 *   <li><strong>比较逻辑正确：</strong>级别比较符合数值大小关系</li>
 *   <li><strong>重要性分类合理：</strong>CHANGE/ALERT标记为重要，PROCESS/METRIC为普通</li>
 * </ul>
 * 
 * @author TaskFlow Insight Team
 * @version 1.0.0
 * @since 2025-01-06
 */
class MessageTypeTest {
    
    @Test
    void testEnumValues() {
        MessageType[] values = MessageType.values();
        assertEquals(4, values.length);
        
        assertEquals(MessageType.PROCESS, MessageType.valueOf("PROCESS"));
        assertEquals(MessageType.METRIC, MessageType.valueOf("METRIC"));
        assertEquals(MessageType.CHANGE, MessageType.valueOf("CHANGE"));
        assertEquals(MessageType.ALERT, MessageType.valueOf("ALERT"));
    }
    
    @Test
    void testDisplayNames() {
        assertTrue(MessageType.PROCESS.getDisplayName().contains("业务流程"));
        assertTrue(MessageType.METRIC.getDisplayName().contains("核心指标"));
        assertTrue(MessageType.CHANGE.getDisplayName().contains("变更记录"));
        assertTrue(MessageType.ALERT.getDisplayName().contains("异常提示"));
    }
    
    @Test
    void testLevels() {
        assertEquals(1, MessageType.PROCESS.getLevel());
        assertEquals(2, MessageType.METRIC.getLevel());
        assertEquals(3, MessageType.CHANGE.getLevel());
        assertEquals(4, MessageType.ALERT.getLevel());
    }
    
    @Test
    void testBusinessMessageTypes() {
        assertTrue(MessageType.PROCESS.isProcess());
        assertFalse(MessageType.PROCESS.isMetric());
        assertFalse(MessageType.PROCESS.isChange());
        assertFalse(MessageType.PROCESS.isAlert());
        
        assertTrue(MessageType.METRIC.isMetric());
        assertFalse(MessageType.METRIC.isProcess());
        
        assertTrue(MessageType.CHANGE.isChange());
        assertFalse(MessageType.CHANGE.isAlert());
        
        assertTrue(MessageType.ALERT.isAlert());
        assertFalse(MessageType.ALERT.isProcess());
    }
    
    @Test
    void testCompareLevel() {
        // PROCESS vs ALERT
        assertTrue(MessageType.PROCESS.compareLevel(MessageType.ALERT) < 0);
        assertTrue(MessageType.ALERT.compareLevel(MessageType.PROCESS) > 0);
        
        // METRIC vs CHANGE
        assertTrue(MessageType.METRIC.compareLevel(MessageType.CHANGE) < 0);
        assertTrue(MessageType.CHANGE.compareLevel(MessageType.METRIC) > 0);
        
        // 相同类型比较
        assertEquals(0, MessageType.PROCESS.compareLevel(MessageType.PROCESS));
        assertEquals(0, MessageType.ALERT.compareLevel(MessageType.ALERT));
    }
    
    @Test
    void testCompareLevelWithNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MessageType.PROCESS.compareLevel(null)
        );
        assertEquals("Other message type cannot be null", exception.getMessage());
    }
    
    @Test
    void testToString() {
        assertTrue(MessageType.PROCESS.toString().contains("业务流程"));
        assertTrue(MessageType.METRIC.toString().contains("核心指标"));
        assertTrue(MessageType.CHANGE.toString().contains("变更记录"));
        assertTrue(MessageType.ALERT.toString().contains("异常提示"));
    }
    
    @Test
    void testIsImportant() {
        assertFalse(MessageType.PROCESS.isImportant());
        assertFalse(MessageType.METRIC.isImportant());
        assertTrue(MessageType.CHANGE.isImportant());
        assertTrue(MessageType.ALERT.isImportant());
    }
    
    @Test
    void testEnumConsistency() {
        // 确保枚举顺序和值的一致性
        MessageType[] values = MessageType.values();
        assertEquals(MessageType.PROCESS, values[0]);
        assertEquals(MessageType.METRIC, values[1]);
        assertEquals(MessageType.CHANGE, values[2]);
        assertEquals(MessageType.ALERT, values[3]);
        
        // 验证级别设计的一致性
        assertTrue(MessageType.ALERT.getLevel() > MessageType.PROCESS.getLevel());
    }
}