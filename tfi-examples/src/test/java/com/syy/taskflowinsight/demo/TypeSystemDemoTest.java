package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.*;
import com.syy.taskflowinsight.api.TrackingOptions;
import com.syy.taskflowinsight.registry.ObjectTypeResolver;
import com.syy.taskflowinsight.registry.ValueObjectStrategyResolver;
import com.syy.taskflowinsight.registry.DiffRegistry;
import com.syy.taskflowinsight.tracking.snapshot.ObjectSnapshotDeep;
import com.syy.taskflowinsight.tracking.snapshot.SnapshotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity / ValueObject 类型系统功能验证测试。
 *
 * <p>验证 {@code @Entity}, {@code @ValueObject}, {@code @Key},
 * {@code @DiffInclude}, {@code @DiffIgnore}, {@code @ShallowReference}
 * 等注解在类型解析和快照捕获中的行为。</p>
 *
 * @since 3.0.0
 */
class TypeSystemDemoTest {

    @Entity
    static class User {
        @Key private Long id;
        @DiffInclude private String name;
        @DiffInclude private String email;
        @ShallowReference private Department department;
        @DiffIgnore private String password;

        User(Long id, String name, String email, Department department, String password) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.department = department;
            this.password = password;
        }
    }

    @Entity
    static class Department {
        @Key private String deptId;
        private String name;

        Department(String deptId, String name) {
            this.deptId = deptId;
            this.name = name;
        }
    }

    @ValueObject
    static class Address {
        @DiffInclude private String street;
        @DiffInclude private String city;
        @DiffInclude private String zipCode;
        @DiffIgnore private String metadata;

        Address(String street, String city, String zipCode, String metadata) {
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
    @DisplayName("@Entity / @ValueObject 类型应被正确识别")
    void testTypeAutoDetection() {
        Department dept = new Department("DEPT-001", "Engineering");
        User user = new User(1L, "John Doe", "john@example.com", dept, "secret123");
        Address address = new Address("123 Main St", "Springfield", "12345", "meta");

        assertThat(ObjectTypeResolver.resolveType(user).name()).isEqualTo("ENTITY");
        assertThat(ObjectTypeResolver.resolveType(dept).name()).isEqualTo("ENTITY");
        assertThat(ObjectTypeResolver.resolveType(address).name()).isEqualTo("VALUE_OBJECT");
    }

    @Test
    @DisplayName("ValueObject 策略应被正确解析")
    void testValueObjectStrategy() {
        Address address = new Address("123 Main St", "Springfield", "12345", "meta");

        assertThat(ValueObjectStrategyResolver.resolveStrategy(address)).isNotNull();
    }

    @Test
    @DisplayName("程序化注册 ValueObject 类型应生效")
    void testProgrammaticRegistration() {
        class Product {
            private String sku = "SKU001";
            private String name = "Laptop";
            private BigDecimal price = new BigDecimal("999.99");
        }

        DiffRegistry.registerValueObject(Product.class, ValueObjectCompareStrategy.EQUALS);
        Product product = new Product();

        assertThat(ObjectTypeResolver.resolveType(product).name()).isEqualTo("VALUE_OBJECT");
        assertThat(ValueObjectStrategyResolver.resolveStrategy(product).name()).isEqualTo("EQUALS");
    }

    @Test
    @DisplayName("类型感知快照应排除 @DiffIgnore 字段并浅引用 @ShallowReference 字段")
    void testTypeAwareSnapshot() {
        Department dept = new Department("DEPT-001", "Engineering");
        User user = new User(1L, "John Doe", "john@example.com", dept, "secret123");

        ObjectSnapshotDeep snapshot = new ObjectSnapshotDeep(new SnapshotConfig());
        TrackingOptions options = TrackingOptions.builder()
                .enableTypeAware(true)
                .includeFields("id", "name", "email", "department", "password")
                .maxDepth(2)
                .build();

        Map<String, Object> result = snapshot.captureDeep(user, options);

        assertThat(result).containsKey("id");
        assertThat(result).containsKey("name");
        assertThat(result).containsKey("email");
        assertThat(result).containsKey("department");
        assertThat(result).doesNotContainKey("password");
    }
}
