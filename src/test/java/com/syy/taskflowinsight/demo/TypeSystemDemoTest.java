package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.registry.ObjectTypeResolver;
import com.syy.taskflowinsight.registry.ValueObjectStrategyResolver;
import com.syy.taskflowinsight.registry.DiffRegistry;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Entity/ValueObject类型系统功能演示
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class TypeSystemDemoTest {
    
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
    }
    
    @Entity  
    static class Department {
        @Key
        private String deptId;
        private String name;
        
        public Department(String deptId, String name) {
            this.deptId = deptId;
            this.name = name;
        }
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
    }
    
    @BeforeEach
    void setUp() {
        ObjectTypeResolver.clearCache();
        DiffRegistry.clear();
    }
    
    @Test
    void demonstrateTypeSystemFeatures() {
        System.out.println("=== TaskFlowInsight Entity/ValueObject 类型系统演示 ===\n");
        
        // 1. 创建测试数据
        Department dept = new Department("DEPT-001", "Engineering");
        User user = new User(1L, "John Doe", "john@example.com", dept, "secret123");
        Address address = new Address("123 Main St", "Springfield", "12345", "internal-meta");
        
        System.out.println("1. 类型自动识别");
        System.out.println("   User类型: " + ObjectTypeResolver.resolveType(user));
        System.out.println("   Department类型: " + ObjectTypeResolver.resolveType(dept));
        System.out.println("   Address类型: " + ObjectTypeResolver.resolveType(address));
        System.out.println();
        
        // 2. 策略解析
        System.out.println("2. ValueObject策略解析");
        System.out.println("   Address策略: " + ValueObjectStrategyResolver.resolveStrategy(address));
        System.out.println();
        
        // 3. 程序化注册演示
        class Product {
            private String sku = "SKU001";
            private String name = "Laptop";
            private BigDecimal price = new BigDecimal("999.99");
        }
        
        DiffRegistry.registerValueObject(Product.class, ValueObjectCompareStrategy.EQUALS);
        Product product = new Product();
        
        System.out.println("3. 程序化注册");
        System.out.println("   Product类型: " + ObjectTypeResolver.resolveType(product));
        System.out.println("   Product策略: " + ValueObjectStrategyResolver.resolveStrategy(product));
        System.out.println();
        
        // 4. 快照捕获对比
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        System.out.println("4. 快照捕获对比");
        
        // Legacy模式
        TrackingOptions legacyOptions = TrackingOptions.builder()
            .enableTypeAware(false)
            .maxDepth(2)
            .build();
        
        Map<String, Object> legacyResult = snapshot.captureDeep(user, legacyOptions);
        System.out.println("   Legacy模式字段数: " + legacyResult.size());
        System.out.println("   Legacy模式字段: " + legacyResult.keySet());
        
        // 类型感知模式
        TrackingOptions typeAwareOptions = TrackingOptions.builder()
            .enableTypeAware(true)
            .includeFields("id", "name", "email", "department", "password")
            .maxDepth(2)
            .build();
        
        Map<String, Object> typeAwareResult = snapshot.captureDeep(user, typeAwareOptions);
        System.out.println("   类型感知模式字段数: " + typeAwareResult.size());
        System.out.println("   类型感知模式字段: " + typeAwareResult.keySet());
        System.out.println();
        
        // 5. 注解效果展示
        System.out.println("5. 注解效果展示");
        System.out.println("   @Key字段(id): " + typeAwareResult.get("id"));
        System.out.println("   @DiffInclude字段(name): " + typeAwareResult.get("name"));
        System.out.println("   @DiffInclude字段(email): " + typeAwareResult.get("email"));
        System.out.println("   @ShallowReference字段(department): " + typeAwareResult.get("department"));
        System.out.println("   @DiffIgnore字段(password): " + 
            (typeAwareResult.containsKey("password") ? "存在" : "已忽略"));
        System.out.println();
        
        // 6. ValueObject深度处理
        Map<String, Object> addressResult = snapshot.captureDeep(address, typeAwareOptions);
        System.out.println("6. ValueObject深度处理");
        System.out.println("   Address字段: " + addressResult.keySet());
        System.out.println("   street: " + addressResult.get("street"));
        System.out.println("   city: " + addressResult.get("city"));
        System.out.println("   zipCode: " + addressResult.get("zipCode"));
        System.out.println("   metadata(ignored): " + 
            (addressResult.containsKey("metadata") ? "存在" : "已忽略"));
        System.out.println();
        
        System.out.println("=== 演示完成 ===");
    }
}