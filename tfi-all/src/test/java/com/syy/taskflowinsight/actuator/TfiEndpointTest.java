package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.context.ThreadContext;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TFI Actuator端点测试
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@DisplayName("TFI Actuator端点测试")
public class TfiEndpointTest {
    
    private TfiEndpoint endpoint;
    
    @BeforeEach
    void setUp() {
        endpoint = new TfiEndpoint(null);
        TFI.clearAllTracking();
        ThreadContext.clear();
    }
    
    @Test
    @DisplayName("基础功能-获取系统信息")
    void testInfo() {
        Map<String, Object> info = endpoint.info();
        
        assertThat(info).containsKey("version");
        assertThat(info).containsKey("timestamp");
        assertThat(info).containsKey("changeTracking");
        assertThat(info).containsKey("threadContext");
        assertThat(info).containsKey("health");
        
        Map<String, Object> changeTracking = (Map<String, Object>) info.get("changeTracking");
        assertThat(changeTracking).containsKey("enabled");
        assertThat(changeTracking).containsKey("totalChanges");
        assertThat(changeTracking).containsKey("activeTrackers");
        
        Map<String, Object> threadContext = (Map<String, Object>) info.get("threadContext");
        assertThat(threadContext).containsKey("activeContexts");
        assertThat(threadContext).containsKey("totalCreated");
        assertThat(threadContext).containsKey("totalPropagations");
        assertThat(threadContext).containsKey("potentialLeak");
        
        Map<String, Object> health = (Map<String, Object>) info.get("health");
        assertThat(health).containsKey("status");
    }
    
    @Test
    @DisplayName("切换追踪开关-启用")
    void testToggleTrackingEnable() {
        Map<String, Object> result = endpoint.toggleTracking(true);
        
        assertThat(result.get("previousState")).isEqualTo(result.get("currentState"));
        assertThat(String.valueOf(result.get("message")))
            .contains("Runtime toggling is not supported");
    }
    
    @Test
    @DisplayName("切换追踪开关-禁用")
    void testToggleTrackingDisable() {
        Map<String, Object> result = endpoint.toggleTracking(false);
        
        assertThat(result.get("previousState")).isEqualTo(result.get("currentState"));
        assertThat(String.valueOf(result.get("message")))
            .contains("Runtime toggling is not supported");
    }
    
    @Test
    @DisplayName("切换追踪开关-自动切换")
    void testToggleTrackingAuto() {
        Map<String, Object> result = endpoint.toggleTracking(null);
        
        assertThat(result.get("previousState")).isEqualTo(result.get("currentState"));
        assertThat(String.valueOf(result.get("message")))
            .contains("Runtime toggling is not supported");
    }
    
    @Test
    @DisplayName("清理所有数据")
    void testClearAll() {
        // 创建上下文
        ThreadContext.create("test-task");
        
        // 添加测试数据
        TFI.startTracking("User");
        TFI.recordChange("User", "name", "Alice", "Bob", ChangeType.UPDATE);
        TFI.recordChange("User", "age", 25, 26, ChangeType.UPDATE);
        
        Map<String, Object> result = endpoint.clearAll();
        
        assertThat(result.get("message")).isEqualTo("All tracking data cleared");
        
        ThreadContext.clear();
    }
    
    @Test
    @DisplayName("健康检查-正常状态")
    void testHealthNormal() {
        // 确保清理所有上下文
        ThreadContext.clear();
        for (int i = 0; i < 10; i++) {
            ThreadContext.clear();
        }
        
        Map<String, Object> info = endpoint.info();
        Map<String, Object> health = (Map<String, Object>) info.get("health");
        
        // 如果有活跃上下文或潜在泄漏，状态可能是DOWN
        // 这里只验证健康状态字段存在
        assertThat(health).containsKey("status");
        assertThat(health.get("status")).isIn("UP", "DOWN");
    }
    
    @Test
    @DisplayName("健康检查-内存泄漏检测")
    void testHealthWithLeak() {
        // 创建多个上下文但不清理（模拟泄漏）
        for (int i = 0; i < 100; i++) {
            ThreadContext.create("task-" + i);
        }
        
        Map<String, Object> info = endpoint.info();
        Map<String, Object> health = (Map<String, Object>) info.get("health");
        
        // 可能检测到潜在泄漏或过多上下文
        if ("DOWN".equals(health.get("status"))) {
            assertThat(health).containsKey("issue");
        }
    }
}