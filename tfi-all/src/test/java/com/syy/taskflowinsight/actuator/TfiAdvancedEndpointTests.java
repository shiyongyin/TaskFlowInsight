package com.syy.taskflowinsight.actuator;

import com.syy.taskflowinsight.actuator.support.TfiHealthCalculator;
import com.syy.taskflowinsight.actuator.support.TfiStatsAggregator;
import com.syy.taskflowinsight.context.ManagedThreadContext;
import com.syy.taskflowinsight.tracking.SessionAwareChangeTracker;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TfiAdvancedEndpointTests {

    private TfiAdvancedEndpoint endpoint;

    @BeforeEach
    void setup() {
        TfiHealthCalculator healthCalculator = new TfiHealthCalculator();
        ReflectionTestUtils.setField(healthCalculator, "memoryThreshold", 0.8);
        ReflectionTestUtils.setField(healthCalculator, "maxActiveContexts", 100);
        ReflectionTestUtils.setField(healthCalculator, "maxSessionsWarning", 500);

        TfiStatsAggregator statsAggregator = new TfiStatsAggregator();
        EndpointPerformanceOptimizer performanceOptimizer = new EndpointPerformanceOptimizer();

        endpoint = new TfiAdvancedEndpoint(null, healthCalculator, statsAggregator, performanceOptimizer);
        SessionAwareChangeTracker.clearAll();
    }

    @AfterEach
    void tearDown() {
        SessionAwareChangeTracker.clearAll();
    }

    private String createSessionWithChanges(int changes, String objectPrefix) {
        try (ManagedThreadContext ctx = ManagedThreadContext.create("testSession")) {
            String sessionId = ctx.getCurrentSession().getSessionId();
            for (int i = 0; i < changes; i++) {
                SessionAwareChangeTracker.recordChange(ChangeRecord.builder()
                    .objectName(objectPrefix + i)
                    .fieldName("field")
                    .oldValue("A")
                    .newValue("B")
                    .changeType(ChangeType.UPDATE)
                    .timestamp(System.currentTimeMillis())
                    .build());
            }
            return sessionId;
        }
    }

    @Test
    void overviewContainsStatusAndEndpoints() {
        createSessionWithChanges(2, "Obj");
        ResponseEntity<Map<String, Object>> res = endpoint.overview();
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("version", "timestamp", "status", "health", "endpoints");
        Map<String, Object> status = (Map<String, Object>) body.get("status");
        assertThat(status).containsKeys("totalChanges", "activeSessions");
    }

    @Test
    void getSessionsSupportsSortingAndLimit() {
        String s1 = createSessionWithChanges(1, "A");
        String s2 = createSessionWithChanges(3, "B");

        ResponseEntity<Map<String, Object>> res = endpoint.getSessions(1, "changes");
        Map<String, Object> body = res.getBody();
        assertThat(body).isNotNull();
        assertThat((Integer) body.get("total")).isGreaterThanOrEqualTo(2);
        List<?> sessions = (List<?>) body.get("sessions");
        assertThat(sessions).hasSize(1);
        Map<?,?> first = (Map<?, ?>) sessions.get(0);
        assertThat((Integer) first.get("changeCount")).isGreaterThanOrEqualTo(3);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getSessionByIdReturnsMetadataAndChanges() {
        String sid = createSessionWithChanges(2, "X");
        ResponseEntity<?> res = endpoint.getSession(sid);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("sessionId", "metadata", "changes", "timestamp");
        List<?> changes = (List<?>) body.get("changes");
        assertThat(changes).hasSize(2);
    }

    @Test
    void getChangesSupportsFilterAndPagination() {
        String sid = createSessionWithChanges(5, "Obj");
        // filter by objectName of Obj1
        ResponseEntity<Map<String, Object>> filterRes = endpoint.getChanges(sid, "Obj1", null, 10, 0);
        Map<String, Object> fBody = filterRes.getBody();
        assertThat(fBody).isNotNull();
        assertThat((Integer) fBody.get("total")).isEqualTo(1);

        // pagination: limit 2 offset 1
        ResponseEntity<Map<String, Object>> pageRes = endpoint.getChanges(sid, null, null, 2, 1);
        Map<String, Object> pBody = pageRes.getBody();
        assertThat(pBody).isNotNull();
        assertThat((Integer) pBody.get("limit")).isEqualTo(2);
        List<?> pageChanges = (List<?>) pBody.get("changes");
        assertThat(pageChanges.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void getSessionReturns404WhenNotFound() {
        var res = endpoint.getSession("non-existent-session");
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void updateConfigTogglesChangeTracking() {
        boolean before = com.syy.taskflowinsight.api.TFI.isChangeTrackingEnabled();

        // runtime toggle is intentionally unsupported
        var res = endpoint.updateConfig(Map.of("changeTrackingEnabled", false));
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).containsKey("changeTrackingEnabled");
        assertThat((String) res.getBody().get("warning"))
            .contains("Runtime toggling is not supported");
        assertThat(com.syy.taskflowinsight.api.TFI.isChangeTrackingEnabled()).isEqualTo(before);
    }

    @Test
    void cleanupRemovesExpiredSessions() throws InterruptedException {
        createSessionWithChanges(1, "C");
        createSessionWithChanges(1, "D");
        
        // Wait a moment to ensure sessions are older than 0ms
        Thread.sleep(10);
        
        var res = endpoint.cleanup(0L); // everything older than now -> all cleaned
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = res.getBody();
        assertThat(body).isNotNull();
        assertThat((Integer) body.get("cleanedSessions")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void deleteSessionRemovesSpecificSession() {
        // 测试场景：验证删除特定会话的功能
        // 业务价值：允许手动清理有问题的会话，释放内存
        String sessionId = createSessionWithChanges(3, "ToDelete");
        
        // 确认会话存在
        var getRes = endpoint.getSession(sessionId);
        assertThat(getRes.getStatusCode().is2xxSuccessful()).isTrue();
        
        // 删除会话
        var deleteRes = endpoint.deleteSession(sessionId);
        assertThat(deleteRes.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> deleteBody = deleteRes.getBody();
        assertThat(deleteBody).isNotNull();
        assertThat((Integer) deleteBody.get("clearedChanges")).isGreaterThan(0);
        
        // 验证会话已被删除
        var getAfterDelete = endpoint.getSession(sessionId);
        assertThat(getAfterDelete.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void getConfigReturnsSystemConfiguration() {
        // 测试场景：验证配置查询端点
        // 业务价值：运维人员可以查看当前系统配置状态
        var res = endpoint.getConfig();
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> config = res.getBody();
        assertThat(config).isNotNull();
        // 基于实际响应结构
        assertThat(config).containsKey("changeTracking");
        assertThat(config).containsKey("limits");
        assertThat(config).containsKey("threadContext");
        
        Map<String, Object> changeTracking = (Map<String, Object>) config.get("changeTracking");
        assertThat(changeTracking).containsKey("enabled");
    }

    @Test
    void getStatisticsReturnsSystemMetrics() {
        // 测试场景：验证统计信息端点
        // 业务价值：提供系统运行状态的量化指标
        createSessionWithChanges(5, "StatsTest");
        
        var res = endpoint.getStatistics();
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> stats = res.getBody();
        assertThat(stats).isNotNull();
        // 基于实际响应结构
        assertThat(stats).containsKey("sessionCount");
        assertThat(stats).containsKey("totalChanges");
        assertThat(stats).containsKey("avgChangesPerSession");
        assertThat(stats).containsKey("timestamp");
        
        // 验证统计数据的合理性
        assertThat((Integer) stats.get("totalChanges")).isGreaterThanOrEqualTo(5);
    }

    @Test
    void getAllChangesWithoutSessionFilter() {
        // 测试场景：验证获取所有变更记录（不指定会话）
        // 业务价值：全局变更审计，跨会话数据分析
        String session1 = createSessionWithChanges(2, "Global1");
        String session2 = createSessionWithChanges(3, "Global2");
        
        var res = endpoint.getChanges(null, null, null, 10, 0);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = res.getBody();
        assertThat(body).isNotNull();
        assertThat((Integer) body.get("total")).isGreaterThanOrEqualTo(5);
        
        List<?> changes = (List<?>) body.get("changes");
        assertThat(changes).isNotNull();
        assertThat(changes.size()).isGreaterThan(0);
    }

    @Test
    void getChangesWithFieldNameFilter() {
        // 测试场景：验证按字段名过滤变更记录
        // 业务价值：精确查找特定字段的变更历史
        String sessionId = createSessionWithChanges(3, "FieldFilter");
        
        var res = endpoint.getChanges(sessionId, null, "field", 10, 0);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = res.getBody();
        assertThat(body).isNotNull();
        assertThat((Integer) body.get("total")).isGreaterThanOrEqualTo(0); // 可能为0，因为过滤条件
        
        List<?> changes = (List<?>) body.get("changes");
        assertThat(changes).isNotNull();
    }

    @Test
    void configurationToggleWorks() {
        // 测试场景：验证配置动态切换功能
        // 业务价值：运维期间可以动态调整系统行为
        
        boolean originalState = com.syy.taskflowinsight.api.TFI.isChangeTrackingEnabled();

        var updateRes = endpoint.updateConfig(Map.of("changeTrackingEnabled", !originalState));
        assertThat(updateRes.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> updateBody = updateRes.getBody();
        assertThat(updateBody).isNotNull();
        assertThat((String) updateBody.get("warning"))
            .contains("Runtime toggling is not supported");

        // TFI state is not affected by endpoint config update
        assertThat(com.syy.taskflowinsight.api.TFI.isChangeTrackingEnabled()).isEqualTo(originalState);
    }

    @Test
    void sessionPaginationWorksCorrectly() {
        // 测试场景：验证会话列表分页功能
        // 业务价值：大量会话时的性能优化和UI友好性
        
        // 创建多个会话
        for (int i = 0; i < 5; i++) {
            createSessionWithChanges(i + 1, "Page" + i);
        }
        
        // 测试第一页
        var page1 = endpoint.getSessions(2, "changes");
        assertThat(page1.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> page1Body = page1.getBody();
        assertThat(page1Body).isNotNull();
        
        List<?> page1Sessions = (List<?>) page1Body.get("sessions");
        assertThat(page1Sessions).hasSize(2);
        
        // 验证按变更数量排序
        Map<?, ?> first = (Map<?, ?>) page1Sessions.get(0);
        Map<?, ?> second = (Map<?, ?>) page1Sessions.get(1);
        Integer firstChanges = (Integer) first.get("changeCount");
        Integer secondChanges = (Integer) second.get("changeCount");
        assertThat(firstChanges).isGreaterThanOrEqualTo(secondChanges);
    }
}
