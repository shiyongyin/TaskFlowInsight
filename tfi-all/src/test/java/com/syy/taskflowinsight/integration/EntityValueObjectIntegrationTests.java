package com.syy.taskflowinsight.integration;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entity/ValueObject类型系统集成测试
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class EntityValueObjectIntegrationTests {
    
    @Entity
    static class User {
        @Key
        private Long id;
        private String name;
        @ShallowReference
        private Address address;
        @DiffIgnore
        private String internalToken;
        
        public User(Long id, String name, Address address, String token) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.internalToken = token;
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
    
    @Entity
    static class Order {
        @Key
        private String orderId;
        @DiffInclude
        private BigDecimal amount;
        @ShallowReference
        private Customer customer;
        @DiffIgnore
        private String processingNotes;
        
        public Order(String orderId, BigDecimal amount, Customer customer, String notes) {
            this.orderId = orderId;
            this.amount = amount;
            this.customer = customer;
            this.processingNotes = notes;
        }
    }
    
    @Entity
    static class Customer {
        @Key
        private Long customerId;
        private String name;
        private String email;
        
        public Customer(Long customerId, String name, String email) {
            this.customerId = customerId;
            this.name = name;
            this.email = email;
        }
    }
    
    @BeforeEach
    void setUp() {
        ObjectTypeResolver.clearCache();
        DiffRegistry.clear();
    }
    
    @Test
    void testCompleteUserAddressScenario() {
        // 创建测试数据
        Address address = new Address("123 Main St", "Springfield", "12345", "internal");
        User user = new User(1L, "John Doe", address, "secret-token");
        
        // 测试类型解析
        assertEquals(ObjectType.ENTITY, ObjectTypeResolver.resolveType(user));
        assertEquals(ObjectType.VALUE_OBJECT, ObjectTypeResolver.resolveType(address));
        
        // 测试策略解析
        assertEquals(ValueObjectCompareStrategy.FIELDS, ValueObjectStrategyResolver.resolveStrategy(address));
        
        // 测试快照捕获
        SnapshotConfig config = new SnapshotConfig();
        config.setEnableDeep(true);
        config.setMaxDepth(10);
        
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .maxDepth(5)
            .includeFields("id", "name", "address")
            .build();
        
        Map<String, Object> result = snapshot.captureDeep(user, options);
        
        // 验证Entity的Key字段被捕获
        assertTrue(result.containsKey("id"));
        assertEquals(1L, result.get("id"));
        
        // 验证Entity的@DiffInclude字段被捕获（如果在includeFields中）
        assertTrue(result.containsKey("name"));
        assertEquals("John Doe", result.get("name"));
        
        // 验证@DiffIgnore字段被忽略
        assertFalse(result.containsKey("internalToken"));
        
        // 验证@ShallowReference字段以浅层方式处理
        assertTrue(result.containsKey("address"));
        // 注意：由于Address没有@Key字段，会使用toString()
        assertNotNull(result.get("address"));
    }
    
    @Test
    void testValueObjectDeepProcessing() {
        @ValueObject
        class ContactInfo {
            @DiffInclude
            private String email;
            @DiffInclude
            private String phone;
            @DiffIgnore
            private String lastVerified;
            
            public ContactInfo(String email, String phone, String lastVerified) {
                this.email = email;
                this.phone = phone;
                this.lastVerified = lastVerified;
            }
        }
        
        ContactInfo contact = new ContactInfo("john@example.com", "555-1234", "2025-01-17");
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .maxDepth(3)
            .build();
        
        Map<String, Object> result = snapshot.captureDeep(contact, options);
        
        // ValueObject应该进行深度字段处理
        assertTrue(result.containsKey("email"));
        assertTrue(result.containsKey("phone"));
        assertEquals("john@example.com", result.get("email"));
        assertEquals("555-1234", result.get("phone"));
        
        // @DiffIgnore字段应该被忽略
        assertFalse(result.containsKey("lastVerified"));
    }
    
    @Test
    void testEntityShallowProcessing() {
        Customer customer = new Customer(100L, "Alice Smith", "alice@example.com");
        Order order = new Order("ORD-001", new BigDecimal("99.99"), customer, "urgent");
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .includeFields("orderId", "amount", "customer")
            .build();
        
        Map<String, Object> result = snapshot.captureDeep(order, options);
        
        // Entity的Key字段应该被捕获
        assertTrue(result.containsKey("orderId"));
        assertEquals("ORD-001", result.get("orderId"));
        
        // @DiffInclude字段应该被捕获
        assertTrue(result.containsKey("amount"));
        assertEquals(new BigDecimal("99.99"), result.get("amount"));
        
        // @DiffIgnore字段应该被忽略
        assertFalse(result.containsKey("processingNotes"));
        
        // @ShallowReference字段应该被浅层处理（只有Key字段）
        assertTrue(result.containsKey("customer"));
        assertEquals(100L, result.get("customer")); // 应该是customer的Key字段值
    }
    
    @Test
    void testProgrammaticRegistrationIntegration() {
        // 没有注解的类
        class Product {
            private String sku;
            private String name;
            private BigDecimal price;
            
            public Product(String sku, String name, BigDecimal price) {
                this.sku = sku;
                this.name = name;
                this.price = price;
            }
        }
        
        // 程序化注册为ValueObject
        DiffRegistry.registerValueObject(Product.class, ValueObjectCompareStrategy.FIELDS);
        
        Product product = new Product("SKU001", "Laptop", new BigDecimal("999.99"));
        
        // 验证类型解析
        assertEquals(ObjectType.VALUE_OBJECT, ObjectTypeResolver.resolveType(product));
        assertEquals(ValueObjectCompareStrategy.FIELDS, ValueObjectStrategyResolver.resolveStrategy(product));
        
        // 验证快照处理
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .build();
        
        Map<String, Object> result = snapshot.captureDeep(product, options);
        
        // ValueObject应该进行深度字段处理
        assertTrue(result.containsKey("sku"));
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("price"));
    }
    
    @Test
    void testTypeAwareDisabledFallback() {
        @Entity
        class SimpleUser {
            @Key
            private Long id = 1L;
            private String name = "Test";
        }
        
        SimpleUser user = new SimpleUser();
        
        SnapshotConfig config = new SnapshotConfig();
        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(config);
        
        // 禁用类型感知
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware(false)
            .build();
        
        Map<String, Object> result = snapshot.captureDeep(user, options);
        
        // 应该使用原始的处理逻辑，处理所有字段
        assertTrue(result.containsKey("id"));
        assertTrue(result.containsKey("name"));
    }
}