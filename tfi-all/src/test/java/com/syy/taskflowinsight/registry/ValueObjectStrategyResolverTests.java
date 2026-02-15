package com.syy.taskflowinsight.registry;

import com.syy.taskflowinsight.annotation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ValueObjectStrategyResolver策略解析器测试
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class ValueObjectStrategyResolverTests {
    
    @BeforeEach
    void setUp() {
        DiffRegistry.clear();
    }
    
    @Test
    void testValueObjectDefaultStrategy() {
        @ValueObject
        class TestValueObject {}
        
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(new TestValueObject());
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy);
    }
    
    @Test
    void testExplicitEqualsStrategy() {
        @ValueObject(strategy = ValueObjectCompareStrategy.EQUALS)
        class TestValueObject {}
        
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(new TestValueObject());
        assertEquals(ValueObjectCompareStrategy.EQUALS, strategy);
    }
    
    @Test
    void testExplicitFieldsStrategy() {
        @ValueObject(strategy = ValueObjectCompareStrategy.FIELDS)
        class TestValueObject {}
        
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(new TestValueObject());
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy);
    }
    
    @Test
    void testAutoStrategyResolution() {
        @ValueObject(strategy = ValueObjectCompareStrategy.AUTO)
        class TestValueObject {}
        
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(new TestValueObject());
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy); // AUTO解析为FIELDS
    }
    
    @Test
    void testProgrammaticRegistration() {
        class TestClass {}
        
        // 程序化注册策略
        DiffRegistry.registerValueObject(TestClass.class, ValueObjectCompareStrategy.EQUALS);
        
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(new TestClass());
        assertEquals(ValueObjectCompareStrategy.EQUALS, strategy);
    }
    
    @Test
    void testAnnotationPriorityOverRegistration() {
        @ValueObject(strategy = ValueObjectCompareStrategy.FIELDS)
        class TestClass {}
        
        // 程序化注册不同的策略
        DiffRegistry.registerValueObject(TestClass.class, ValueObjectCompareStrategy.EQUALS);
        
        // 注解优先级应该更高
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(new TestClass());
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy);
    }
    
    @Test
    void testDefaultStrategyForUnregisteredClass() {
        class TestClass {}
        
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(new TestClass());
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy); // 默认策略
    }
    
    @Test
    void testNullObjectHandling() {
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(null);
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy); // null对象返回默认FIELDS策略
    }
    
    @Test
    void testClassBasedResolution() {
        @ValueObject(strategy = ValueObjectCompareStrategy.EQUALS)
        class TestValueObject {}
        
        ValueObjectCompareStrategy strategy = ValueObjectStrategyResolver.resolveStrategy(TestValueObject.class);
        assertEquals(ValueObjectCompareStrategy.EQUALS, strategy);
    }
    
    @Test
    void testRegistrationOverride() {
        class TestClass {}
        
        // 第一次注册
        DiffRegistry.registerValueObject(TestClass.class, ValueObjectCompareStrategy.EQUALS);
        assertEquals(ValueObjectCompareStrategy.EQUALS, 
                    ValueObjectStrategyResolver.resolveStrategy(new TestClass()));
        
        // 重新注册不同策略
        DiffRegistry.registerValueObject(TestClass.class, ValueObjectCompareStrategy.FIELDS);
        assertEquals(ValueObjectCompareStrategy.FIELDS, 
                    ValueObjectStrategyResolver.resolveStrategy(new TestClass()));
    }
    
    @Test
    void testStrategyResolutionPriority() {
        // 测试策略解析优先级：注解 > 程序化注册 > 默认
        
        // 1. 有注解时，使用注解策略
        @ValueObject(strategy = ValueObjectCompareStrategy.EQUALS)
        class AnnotatedClass {}
        
        assertEquals(ValueObjectCompareStrategy.EQUALS, 
                    ValueObjectStrategyResolver.resolveStrategy(new AnnotatedClass()));
        
        // 2. 无注解但有程序化注册时，使用注册策略
        class RegisteredClass {}
        DiffRegistry.registerValueObject(RegisteredClass.class, ValueObjectCompareStrategy.EQUALS);
        
        assertEquals(ValueObjectCompareStrategy.EQUALS, 
                    ValueObjectStrategyResolver.resolveStrategy(new RegisteredClass()));
        
        // 3. 无注解无注册时，使用默认策略
        class DefaultClass {}
        
        assertEquals(ValueObjectCompareStrategy.FIELDS, 
                    ValueObjectStrategyResolver.resolveStrategy(new DefaultClass()));
    }
}