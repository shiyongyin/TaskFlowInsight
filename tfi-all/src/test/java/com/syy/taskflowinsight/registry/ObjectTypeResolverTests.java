package com.syy.taskflowinsight.registry;

import com.syy.taskflowinsight.annotation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ObjectTypeResolver类型解析器测试
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class ObjectTypeResolverTests {
    
    @BeforeEach
    void setUp() {
        ObjectTypeResolver.clearCache();
        DiffRegistry.clear();
    }
    
    @Test
    void testEntityAnnotationResolution() {
        @Entity
        class TestEntity {}
        
        ObjectType type = ObjectTypeResolver.resolveType(new TestEntity());
        assertEquals(ObjectType.ENTITY, type);
    }
    
    @Test
    void testEntityAnnotationWithCustomName() {
        @Entity(name = "CustomEntity")
        class TestEntity {}
        
        ObjectType type = ObjectTypeResolver.resolveType(new TestEntity());
        assertEquals(ObjectType.ENTITY, type);
    }
    
    @Test
    void testValueObjectAnnotationResolution() {
        @ValueObject
        class TestValueObject {}
        
        ObjectType type = ObjectTypeResolver.resolveType(new TestValueObject());
        assertEquals(ObjectType.VALUE_OBJECT, type);
    }
    
    @Test
    void testValueObjectAnnotationWithCustomStrategy() {
        @ValueObject(strategy = ValueObjectCompareStrategy.EQUALS)
        class TestValueObject {}
        
        ObjectType type = ObjectTypeResolver.resolveType(new TestValueObject());
        assertEquals(ObjectType.VALUE_OBJECT, type);
    }
    
    @Test
    void testKeyFieldResolution() {
        class TestClass {
            @Key
            private String id = "test";
        }
        
        ObjectType type = ObjectTypeResolver.resolveType(new TestClass());
        assertEquals(ObjectType.ENTITY, type);
    }
    
    @Test
    void testMultipleKeyFieldsResolution() {
        class TestClass {
            @Key
            private String id = "test";
            @Key  
            private Long version = 1L;
        }
        
        ObjectType type = ObjectTypeResolver.resolveType(new TestClass());
        assertEquals(ObjectType.ENTITY, type);
    }
    
    @Test
    void testProgrammaticRegistration() {
        class UnknownClass {}
        
        // 初始状态应该是VALUE_OBJECT（默认规则）
        assertEquals(ObjectType.VALUE_OBJECT, ObjectTypeResolver.resolveType(new UnknownClass()));
        
        // 程序化注册后应该能正确识别
        DiffRegistry.registerEntity(UnknownClass.class);
        // 清空缓存以使注册生效
        ObjectTypeResolver.clearCache();
        assertEquals(ObjectType.ENTITY, ObjectTypeResolver.resolveType(new UnknownClass()));
    }
    
    @Test
    void testBasicTypeResolution() {
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType("test"));
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType(123));
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType(123L));
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType(123.45));
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType(true));
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType(LocalDateTime.now()));
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType(new BigDecimal("123.45")));
    }
    
    @Test
    void testCollectionTypeResolution() {
        assertEquals(ObjectType.COLLECTION, ObjectTypeResolver.resolveType(Arrays.asList(1, 2, 3)));
        assertEquals(ObjectType.COLLECTION, ObjectTypeResolver.resolveType(new HashMap<>()));
        assertEquals(ObjectType.COLLECTION, ObjectTypeResolver.resolveType(new HashSet<>()));
        assertEquals(ObjectType.COLLECTION, ObjectTypeResolver.resolveType(new String[]{"a", "b"}));
        assertEquals(ObjectType.COLLECTION, ObjectTypeResolver.resolveType(new int[]{1, 2, 3}));
    }
    
    @Test
    void testNullObjectResolution() {
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType(null));
    }
    
    @Test
    void testDefaultRuleForUnknownClasses() {
        class UnknownClass {}
        
        ObjectType type = ObjectTypeResolver.resolveType(new UnknownClass());
        assertEquals(ObjectType.VALUE_OBJECT, type); // 默认规则
    }
    
    @Test
    void testAnnotationPriorityOverKeyFields() {
        @ValueObject  // ValueObject注解优先级高于@Key字段检测
        class TestClass {
            @Key
            private String id = "test";
        }
        
        ObjectType type = ObjectTypeResolver.resolveType(new TestClass());
        assertEquals(ObjectType.VALUE_OBJECT, type);
    }
    
    @Test
    void testEntityAnnotationPriorityOverValueObject() {
        @Entity
        @ValueObject  // Entity注解优先级最高
        class TestClass {}
        
        ObjectType type = ObjectTypeResolver.resolveType(new TestClass());
        assertEquals(ObjectType.ENTITY, type);
    }
    
    @Test
    void testCacheEffectiveness() {
        class TestClass {}
        TestClass instance = new TestClass();
        
        // 第一次解析
        ObjectType type1 = ObjectTypeResolver.resolveType(instance);
        assertEquals(ObjectType.VALUE_OBJECT, type1);
        
        // 第二次解析应该使用缓存
        ObjectType type2 = ObjectTypeResolver.resolveType(instance);
        assertEquals(type1, type2);
        
        // 验证缓存大小
        assertTrue(ObjectTypeResolver.getCacheSize() > 0);
        
        // 清空缓存
        ObjectTypeResolver.clearCache();
        assertEquals(0, ObjectTypeResolver.getCacheSize());
    }
    
    @Test
    void testPriorityOrder() {
        // 测试完整的6级优先级顺序
        
        // 1. Entity注解优先级最高
        @Entity
        class EntityClass {
            @Key
            private String id = "test";
        }
        assertEquals(ObjectType.ENTITY, ObjectTypeResolver.resolveType(new EntityClass()));
        
        // 2. ValueObject注解优先级第二
        @ValueObject
        class ValueObjectClass {
            @Key
            private String id = "test";
        }
        assertEquals(ObjectType.VALUE_OBJECT, ObjectTypeResolver.resolveType(new ValueObjectClass()));
        
        // 3. @Key字段检测优先级第三（无注解时）
        class KeyFieldClass {
            @Key
            private String id = "test";
        }
        assertEquals(ObjectType.ENTITY, ObjectTypeResolver.resolveType(new KeyFieldClass()));
        
        // 4. 程序化注册优先级第四（无注解和Key字段时）
        class RegisteredClass {}
        DiffRegistry.registerEntity(RegisteredClass.class);
        assertEquals(ObjectType.ENTITY, ObjectTypeResolver.resolveType(new RegisteredClass()));
        
        // 5. 基本类型判断优先级第五
        assertEquals(ObjectType.BASIC_TYPE, ObjectTypeResolver.resolveType("string"));
        
        // 6. 默认为VALUE_OBJECT
        class DefaultClass {}
        assertEquals(ObjectType.VALUE_OBJECT, ObjectTypeResolver.resolveType(new DefaultClass()));
    }
}