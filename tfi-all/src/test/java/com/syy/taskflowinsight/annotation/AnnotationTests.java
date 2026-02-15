package com.syy.taskflowinsight.annotation;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 注解定义测试
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class AnnotationTests {
    
    @Test
    void testEntityAnnotationDefaultValues() throws Exception {
        @Entity
        class TestEntity {}
        
        Entity annotation = TestEntity.class.getAnnotation(Entity.class);
        assertNotNull(annotation);
        assertEquals("", annotation.name());
    }
    
    @Test
    void testEntityAnnotationWithCustomName() throws Exception {
        @Entity(name = "CustomUser")
        class TestEntity {}
        
        Entity annotation = TestEntity.class.getAnnotation(Entity.class);
        assertNotNull(annotation);
        assertEquals("CustomUser", annotation.name());
    }
    
    @Test
    void testValueObjectAnnotationDefaultValues() throws Exception {
        @ValueObject
        class TestValueObject {}
        
        ValueObject annotation = TestValueObject.class.getAnnotation(ValueObject.class);
        assertNotNull(annotation);
        assertEquals(ValueObjectCompareStrategy.AUTO, annotation.strategy());
    }
    
    @Test
    void testValueObjectAnnotationWithCustomStrategy() throws Exception {
        @ValueObject(strategy = ValueObjectCompareStrategy.EQUALS)
        class TestValueObject {}
        
        ValueObject annotation = TestValueObject.class.getAnnotation(ValueObject.class);
        assertNotNull(annotation);
        assertEquals(ValueObjectCompareStrategy.EQUALS, annotation.strategy());
    }
    
    @Test
    void testKeyAnnotationExists() throws Exception {
        class TestClass {
            @Key
            private String id;
        }
        
        Field field = TestClass.class.getDeclaredField("id");
        assertTrue(field.isAnnotationPresent(Key.class));
    }
    
    @Test
    void testShallowReferenceAnnotationExists() throws Exception {
        class TestClass {
            @ShallowReference
            private Object reference;
        }
        
        Field field = TestClass.class.getDeclaredField("reference");
        assertTrue(field.isAnnotationPresent(ShallowReference.class));
    }
    
    @Test
    void testDiffIgnoreAnnotationExists() throws Exception {
        class TestClass {
            @DiffIgnore
            private String ignoredField;
        }
        
        Field field = TestClass.class.getDeclaredField("ignoredField");
        assertTrue(field.isAnnotationPresent(DiffIgnore.class));
    }
    
    @Test
    void testDiffIncludeAnnotationExists() throws Exception {
        class TestClass {
            @DiffInclude
            private String includedField;
        }
        
        Field field = TestClass.class.getDeclaredField("includedField");
        assertTrue(field.isAnnotationPresent(DiffInclude.class));
    }
    
    @Test
    void testObjectTypeEnumValues() {
        assertEquals(4, ObjectType.values().length);
        assertEquals("Entity", ObjectType.ENTITY.getDisplayName());
        assertEquals("ValueObject", ObjectType.VALUE_OBJECT.getDisplayName());
        assertEquals("BasicType", ObjectType.BASIC_TYPE.getDisplayName());
        assertEquals("Collection", ObjectType.COLLECTION.getDisplayName());
    }
    
    @Test
    void testValueObjectCompareStrategyEnumValues() {
        assertEquals(3, ValueObjectCompareStrategy.values().length);
        assertEquals("Auto", ValueObjectCompareStrategy.AUTO.getDisplayName());
        assertEquals("Equals", ValueObjectCompareStrategy.EQUALS.getDisplayName());
        assertEquals("Fields", ValueObjectCompareStrategy.FIELDS.getDisplayName());
    }
}