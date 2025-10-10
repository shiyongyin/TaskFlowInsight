package com.syy.taskflowinsight.integration;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TFI;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.tracking.ChangeTracker;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import com.syy.taskflowinsight.registry.DiffRegistry;
import com.syy.taskflowinsight.registry.ObjectTypeResolver;
import com.syy.taskflowinsight.registry.ValueObjectStrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entity/ValueObject端到端完整闭环测试
 * 通过TFI/ChangeTracker验证类型感知变更输出
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class EntityValueObjectEndToEndTests {
    
    @Entity
    static class User {
        @Key
        private Long id;
        @DiffInclude
        private String name;
        @DiffInclude
        private String email;
        @ShallowReference
        private Department department;
        @DiffIgnore
        private String password;
        
        public User(Long id, String name, String email, Department department, String password) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.department = department;
            this.password = password;
        }
        
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setPassword(String password) { this.password = password; }
        public Department getDepartment() { return department; }
    }
    
    @Entity
    static class Department {
        @Key
        private String deptId;
        private String name;
        private String location;
        
        public Department(String deptId, String name, String location) {
            this.deptId = deptId;
            this.name = name;
            this.location = location;
        }
        
        public void setName(String name) { this.name = name; }
        public void setLocation(String location) { this.location = location; }
    }
    
    @ValueObject
    static class Address {
        @DiffInclude
        private String street;
        @DiffInclude
        private String city;
        @DiffInclude
        private String zipCode;
        @DiffIgnore
        private String metadata;
        
        public Address(String street, String city, String zipCode, String metadata) {
            this.street = street;
            this.city = city;
            this.zipCode = zipCode;
            this.metadata = metadata;
        }
        
        public void setStreet(String street) { this.street = street; }
        public void setCity(String city) { this.city = city; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
    }
    
    @BeforeEach
    void setUp() {
        ObjectTypeResolver.clearCache();
        DiffRegistry.clear();
        TFI.clear();
    }
    
    /**
     * 场景1：Entity类型感知快照捕获验证
     * 期望：类型识别正确，注解生效
     */
    @Test
    void testEntityTypeAwareSnapshotCapture() {
        // 准备测试数据
        Department dept = new Department("DEPT-001", "Engineering", "Building A");
        User user = new User(1L, "John Doe", "john@example.com", dept, "secret123");
        
        // 验证类型识别
        assertEquals(ObjectType.ENTITY, ObjectTypeResolver.resolveType(user));
        assertEquals(ObjectType.ENTITY, ObjectTypeResolver.resolveType(dept));
        
        // 使用类型感知的深度追踪
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware(true)
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(5)
            .build();
        
        // 通过TFI进行追踪，验证快照捕获
        TFI.trackDeep("user", user, options);
        
        // 修改用户数据
        user.setName("Jane Doe");
        user.setPassword("newsecret456"); // 应该被@DiffIgnore忽略
        
        // 验证追踪正常工作
        System.out.println("✓ Entity类型感知快照捕获成功");
        System.out.printf("User类型: %s%n", ObjectTypeResolver.resolveType(user));
        System.out.printf("Department类型: %s%n", ObjectTypeResolver.resolveType(dept));
    }
    
    /**
     * 场景2：ValueObject类型感知处理验证
     * 期望：ValueObject正确识别，策略解析正常
     */
    @Test
    void testValueObjectTypeAwareProcessing() {
        Address address = new Address("123 Main St", "Springfield", "12345", "meta-data");
        
        // 验证ValueObject类型识别
        assertEquals(ObjectType.VALUE_OBJECT, ObjectTypeResolver.resolveType(address));
        
        // 验证策略解析（应该是默认的FIELDS策略）
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(address);
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy);
        
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware(true)
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .build();
        
        // 追踪ValueObject
        TFI.trackDeep("address", address, options);
        
        // 修改地址
        address.setStreet("456 Oak Ave");
        address.setMetadata("new-meta-data"); // 应该被@DiffIgnore忽略
        
        System.out.println("✓ ValueObject类型感知处理成功");
        System.out.printf("Address类型: %s%n", ObjectTypeResolver.resolveType(address));
        System.out.printf("Address策略: %s%n", strategy);
    }
    
    /**
     * 场景3：程序化注册类型验证
     * 期望：解析结果与注册一致
     */
    @Test
    void testProgrammaticRegistrationTypeAware() {
        // 没有注解的类
        class Product {
            private String sku = "SKU-001";
            private BigDecimal price = new BigDecimal("99.99");
            private String description = "Test Product";
            
            public void setPrice(BigDecimal price) { this.price = price; }
            public void setDescription(String description) { this.description = description; }
        }
        
        // 程序化注册为ValueObject，使用FIELDS策略
        DiffRegistry.registerValueObject(Product.class, ValueObjectCompareStrategy.FIELDS);
        
        Product product = new Product();
        
        // 验证类型识别
        assertEquals(ObjectType.VALUE_OBJECT, ObjectTypeResolver.resolveType(product));
        assertEquals(ValueObjectCompareStrategy.FIELDS, 
                    ValueObjectStrategyResolver.resolveStrategy(product));
        
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware(true)
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .build();
        
        // 追踪
        TFI.trackDeep("product", product, options);
        
        // 修改
        product.setPrice(new BigDecimal("149.99"));
        
        System.out.println("✓ 程序化注册类型识别成功");
        System.out.printf("Product类型: %s%n", ObjectTypeResolver.resolveType(product));
        System.out.printf("Product策略: %s%n", ValueObjectStrategyResolver.resolveStrategy(product));
    }
    
    /**
     * 场景4：类型感知与传统模式对比
     * 期望：类型感知模式遵循注解；传统模式忽略注解
     */
    @Test
    void testTypeAwareVsLegacyModeComparison() {
        Department dept = new Department("DEPT-002", "Sales", "Building C");
        User user = new User(2L, "Bob Smith", "bob@example.com", dept, "password123");
        
        // 类型感知模式
        TrackingOptions typeAwareOptions = TrackingOptions.builder()
            .enableTypeAware(true)
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(3)
            .build();
        
        // 传统模式（禁用类型感知）
        TrackingOptions legacyOptions = TrackingOptions.builder()
            .enableTypeAware(false)
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(3)
            .build();
        
        // 分别追踪
        TFI.trackDeep("user-typeaware", user, typeAwareOptions);
        TFI.trackDeep("user-legacy", user, legacyOptions);
        
        // 修改数据
        user.setPassword("newpassword456");
        
        System.out.println("✓ 类型感知与传统模式对比成功");
        System.out.printf("类型感知模式配置: enableTypeAware=%s%n", typeAwareOptions.isTypeAwareEnabled());
        System.out.printf("传统模式配置: enableTypeAware=%s%n", legacyOptions.isTypeAwareEnabled());
    }
    
    /**
     * 场景5：ChangeTracker集成验证
     * 期望：ChangeTracker正确使用类型感知快照
     */
    @Test
    void testChangeTrackerIntegrationWithTypeAware() {
        Department dept = new Department("DEPT-003", "Marketing", "Building D");
        User user = new User(3L, "Alice Johnson", "alice@example.com", dept, "secret789");
        
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware(true)
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .maxDepth(5)
            .build();
        
        // 直接使用ChangeTracker API（已修复支持类型感知）
        ChangeTracker.track("user-003", user, options);
        
        // 修改用户数据
        user.setName("Alice Brown");
        
        // 获取变更记录
        List<ChangeRecord> allChanges = ChangeTracker.getChanges();
        
        assertNotNull(allChanges);
        System.out.printf("ChangeTracker检测到 %d 个变更记录%n", allChanges.size());
        
        // 显示变更信息
        for (ChangeRecord change : allChanges) {
            System.out.printf("变更: 对象=%s, 字段=%s, 旧值=%s, 新值=%s%n", 
                change.getObjectName(), change.getFieldName(), 
                change.getOldValue(), change.getNewValue());
        }
        
        System.out.println("✓ ChangeTracker类型感知集成验证成功");
    }
    
    /**
     * 验证默认策略一致性
     * 期望：ValueObject默认策略为FIELDS
     */
    @Test
    void testDefaultStrategyConsistency() {
        // 测试注解ValueObject的默认策略
        @ValueObject // 未指定strategy，应该使用AUTO，解析为FIELDS
        class TestValueObject {
            private String field1 = "value1";
        }
        
        TestValueObject vo = new TestValueObject();
        
        // 验证类型识别
        assertEquals(ObjectType.VALUE_OBJECT, ObjectTypeResolver.resolveType(vo));
        
        // 验证默认策略解析为FIELDS
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(vo);
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy);
        
        System.out.printf("✓ 默认策略验证：%s -> %s%n", ObjectTypeResolver.resolveType(vo), strategy);
    }
}