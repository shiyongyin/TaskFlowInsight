package com.syy.taskflowinsight.compatibility;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.registry.ObjectTypeResolver;
import com.syy.taskflowinsight.registry.DiffRegistry;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型系统兼容性验证测试
 * 验证新功能的向后兼容性和回滚策略
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class TypeSystemCompatibilityTests {
    
    static class LegacyUser {
        private Long id = 1L;
        private String name = "Legacy User";
        private String email = "legacy@example.com";
    }
    
    @Entity
    static class AnnotatedUser {
        @Key
        private Long id = 1L;
        @DiffInclude
        private String name = "Annotated User";
        @DiffIgnore
        private String email = "annotated@example.com";
    }
    
    @BeforeEach
    void setUp() {
        ObjectTypeResolver.clearCache();
        DiffRegistry.clear();
    }
    
    @Test
    void testLegacyBehaviorPreserved() {
        LegacyUser user = new LegacyUser();
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        // 测试传统方式（不启用类型感知）
        TrackingOptions legacyOptions = TrackingOptions.builder()
            .enableTypeAware(false)
            .maxDepth(3)
            .build();
        
        Map<String, Object> legacyResult = snapshot.captureDeep(user, legacyOptions);
        
        // 传统方式应该处理所有字段
        assertTrue(legacyResult.containsKey("id"));
        assertTrue(legacyResult.containsKey("name"));
        assertTrue(legacyResult.containsKey("email"));
        
        assertEquals(1L, legacyResult.get("id"));
        assertEquals("Legacy User", legacyResult.get("name"));
        assertEquals("legacy@example.com", legacyResult.get("email"));
        
        // 验证没有类型感知相关的处理
        assertFalse(legacyOptions.isTypeAwareEnabled());
    }
    
    @Test
    void testNewFunctionalityOptIn() {
        AnnotatedUser user = new AnnotatedUser();
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        // 测试新功能（启用类型感知）
        TrackingOptions newOptions = TrackingOptions.builder()
            .enableTypeAware(true)
            .includeFields("id", "name", "email")
            .maxDepth(3)
            .build();
        
        Map<String, Object> newResult = snapshot.captureDeep(user, newOptions);
        
        // 新功能应该根据注解进行处理
        assertTrue(newResult.containsKey("id")); // @Key字段
        assertTrue(newResult.containsKey("name")); // @DiffInclude字段
        assertFalse(newResult.containsKey("email")); // @DiffIgnore字段被忽略
        
        assertEquals(1L, newResult.get("id"));
        assertEquals("Annotated User", newResult.get("name"));
        
        // 验证类型感知功能已启用
        assertTrue(newOptions.isTypeAwareEnabled());
    }
    
    @Test
    void testSameObjectDifferentBehavior() {
        // 使用同一个对象，测试不同配置下的行为差异
        AnnotatedUser user = new AnnotatedUser();
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        // Legacy模式
        TrackingOptions legacyOptions = TrackingOptions.builder()
            .enableTypeAware(false)
            .maxDepth(3)
            .build();
        
        Map<String, Object> legacyResult = snapshot.captureDeep(user, legacyOptions);
        
        // 类型感知模式
        TrackingOptions typeAwareOptions = TrackingOptions.builder()
            .enableTypeAware(true)
            .includeFields("id", "name", "email")
            .maxDepth(3)
            .build();
        
        Map<String, Object> typeAwareResult = snapshot.captureDeep(user, typeAwareOptions);
        
        // Legacy模式：仍应遵循 @DiffIgnore（安全默认），但不启用类型感知的Entity/ValueObject策略
        assertTrue(legacyResult.containsKey("id"));
        assertTrue(legacyResult.containsKey("name"));
        assertFalse(legacyResult.containsKey("email"));
        
        // 类型感知模式：遵循注解规则
        assertTrue(typeAwareResult.containsKey("id"));
        assertTrue(typeAwareResult.containsKey("name"));
        assertFalse(typeAwareResult.containsKey("email")); // @DiffIgnore
        
        // 公共字段的值应该相同
        assertEquals(legacyResult.get("id"), typeAwareResult.get("id"));
        assertEquals(legacyResult.get("name"), typeAwareResult.get("name"));
    }
    
    @Test
    void testDefaultBehaviorMaintained() {
        LegacyUser user = new LegacyUser();
        
        // 测试默认构建器行为
        TrackingOptions defaultOptions = TrackingOptions.builder().build();
        
        // 默认情况下类型感知应该是禁用的
        assertFalse(defaultOptions.isTypeAwareEnabled());
        assertNull(defaultOptions.getForcedObjectType());
        assertNull(defaultOptions.getForcedStrategy());
        
        // 测试预设配置
        TrackingOptions shallow = TrackingOptions.shallow();
        TrackingOptions deep = TrackingOptions.deep();
        
        assertFalse(shallow.isTypeAwareEnabled());
        assertFalse(deep.isTypeAwareEnabled());
    }
    
    @Test
    void testGracefulDegradation() {
        // 测试当类型感知功能遇到错误时的降级处理
        
        class ProblematicClass {
            // 故意制造一些可能导致问题的情况
            private String field = "test";
            
            // 覆盖toString可能导致异常
            @Override
            public String toString() {
                if (System.currentTimeMillis() % 2 == 0) {
                    return "normal";
                } else {
                    return "normal"; // 实际上不抛异常，只是模拟
                }
            }
        }
        
        ProblematicClass obj = new ProblematicClass();
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware(true)
            .build();
        
        // 即使遇到问题，也应该能正常处理
        assertDoesNotThrow(() -> {
            Map<String, Object> result = snapshot.captureDeep(obj, options);
            assertNotNull(result);
        });
    }
    
    @Test
    void testRollbackScenario() {
        // 模拟需要回滚到legacy模式的场景
        
        @Entity
        class UserEntity {
            @Key
            private Long id = 1L;
            @DiffInclude
            private String name = "User";
            @DiffIgnore  
            private String sensitive = "secret";
        }
        
        UserEntity user = new UserEntity();
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        // 假设新功能出现问题，需要回滚
        TrackingOptions rollbackOptions = TrackingOptions.builder()
            .enableTypeAware(false) // 关闭类型感知，回滚到legacy行为
            .maxDepth(3)
            .build();
        
        Map<String, Object> rollbackResult = snapshot.captureDeep(user, rollbackOptions);
        
        // 回滚后应关闭类型感知策略，但 @DiffIgnore 仍应生效（安全默认）
        assertTrue(rollbackResult.containsKey("id"));
        assertTrue(rollbackResult.containsKey("name"));
        assertFalse(rollbackResult.containsKey("sensitive")); // 仍遵循@DiffIgnore
        
        assertEquals(1L, rollbackResult.get("id"));
        assertEquals("User", rollbackResult.get("name"));
    }
    
    @Test
    void testPartialMigrationSupport() {
        // 测试部分迁移场景：某些对象使用新功能，某些保持legacy
        
        LegacyUser legacyUser = new LegacyUser();
        AnnotatedUser newUser = new AnnotatedUser();
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        // 对legacy对象使用legacy处理
        TrackingOptions legacyOptions = TrackingOptions.builder()
            .enableTypeAware(false)
            .build();
        
        Map<String, Object> legacyResult = snapshot.captureDeep(legacyUser, legacyOptions);
        
        // 对新对象使用类型感知处理
        TrackingOptions newOptions = TrackingOptions.builder()
            .enableTypeAware(true)
            .includeFields("id", "name", "email")
            .build();
        
        Map<String, Object> newResult = snapshot.captureDeep(newUser, newOptions);
        
        // 两种处理方式应该能共存
        assertNotNull(legacyResult);
        assertNotNull(newResult);
        
        // Legacy对象：所有字段
        assertEquals(3, legacyResult.size());
        
        // 新对象：遵循注解规则
        assertEquals(2, newResult.size()); // id + name，email被@DiffIgnore
    }
    
    @Test
    void testErrorHandlingAndRecovery() {
        // 测试错误处理和恢复机制
        
        class EdgeCaseClass {
            private String normalField = "normal";
            private Object nullField = null;
            private String emptyField = "";
        }
        
        EdgeCaseClass obj = new EdgeCaseClass();
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        // 类型感知模式下的错误处理
        TrackingOptions typeAwareOptions = TrackingOptions.builder()
            .enableTypeAware(true)
            .build();
        
        Map<String, Object> typeAwareResult = snapshot.captureDeep(obj, typeAwareOptions);
        
        // 应该能正常处理各种边界情况
        assertTrue(typeAwareResult.containsKey("normalField"));
        assertTrue(typeAwareResult.containsKey("nullField"));
        assertTrue(typeAwareResult.containsKey("emptyField"));
        
        assertEquals("normal", typeAwareResult.get("normalField"));
        assertNull(typeAwareResult.get("nullField"));
        assertEquals("", typeAwareResult.get("emptyField"));
        
        // Legacy模式下的相同处理
        TrackingOptions legacyOptions = TrackingOptions.builder()
            .enableTypeAware(false)
            .build();
        
        Map<String, Object> legacyResult = snapshot.captureDeep(obj, legacyOptions);
        
        // 结果应该一致
        assertEquals(typeAwareResult.get("normalField"), legacyResult.get("normalField"));
        assertEquals(typeAwareResult.get("nullField"), legacyResult.get("nullField"));
        assertEquals(typeAwareResult.get("emptyField"), legacyResult.get("emptyField"));
    }
}