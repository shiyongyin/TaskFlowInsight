package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.annotation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TrackingOptions类型感知扩展测试
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class TrackingOptionsTypeAwareTests {
    
    @Test
    void testDefaultTypeAwareDisabled() {
        TrackingOptions options = TrackingOptions.shallow();
        assertFalse(options.isTypeAwareEnabled());
        assertNull(options.getForcedObjectType());
        assertNull(options.getForcedStrategy());
    }
    
    @Test
    void testEnableTypeAware() {
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .build();
        
        assertTrue(options.isTypeAwareEnabled());
        assertNull(options.getForcedObjectType());
        assertNull(options.getForcedStrategy());
    }
    
    @Test
    void testEnableTypeAwareWithBoolean() {
        TrackingOptions options1 = TrackingOptions.builder()
            .enableTypeAware(true)
            .build();
        assertTrue(options1.isTypeAwareEnabled());
        
        TrackingOptions options2 = TrackingOptions.builder()
            .enableTypeAware(false)
            .build();
        assertFalse(options2.isTypeAwareEnabled());
    }
    
    @Test
    void testForceObjectType() {
        TrackingOptions options = TrackingOptions.builder()
            .forceObjectType(ObjectType.ENTITY)
            .build();
        
        assertEquals(ObjectType.ENTITY, options.getForcedObjectType());
    }
    
    @Test
    void testForceStrategy() {
        TrackingOptions options = TrackingOptions.builder()
            .forceStrategy(ValueObjectCompareStrategy.EQUALS)
            .build();
        
        assertEquals(ValueObjectCompareStrategy.EQUALS, options.getForcedStrategy());
    }
    
    @Test
    void testCompleteTypeAwareConfiguration() {
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .forceObjectType(ObjectType.VALUE_OBJECT)
            .forceStrategy(ValueObjectCompareStrategy.FIELDS)
            .maxDepth(5)
            .includeFields("field1", "field2")
            .excludeFields("field3")
            .enableCycleDetection(true)
            .build();
        
        assertTrue(options.isTypeAwareEnabled());
        assertEquals(ObjectType.VALUE_OBJECT, options.getForcedObjectType());
        assertEquals(ValueObjectCompareStrategy.FIELDS, options.getForcedStrategy());
        assertEquals(5, options.getMaxDepth());
        assertTrue(options.getIncludeFields().contains("field1"));
        assertTrue(options.getIncludeFields().contains("field2"));
        assertTrue(options.getExcludeFields().contains("field3"));
        assertTrue(options.isEnableCycleDetection());
    }
    
    @Test
    void testToStringIncludesTypeAwareFields() {
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .forceObjectType(ObjectType.ENTITY)
            .forceStrategy(ValueObjectCompareStrategy.EQUALS)
            .build();
        
        String toString = options.toString();
        assertTrue(toString.contains("typeAwareEnabled=true"));
        assertTrue(toString.contains("forcedObjectType=ENTITY"));
        assertTrue(toString.contains("forcedStrategy=EQUALS"));
    }
    
    @Test
    void testBuilderChaining() {
        TrackingOptions options = TrackingOptions.builder()
            .depth(TrackingOptions.TrackingDepth.DEEP)
            .enableTypeAware()
            .forceObjectType(ObjectType.VALUE_OBJECT)
            .forceStrategy(ValueObjectCompareStrategy.FIELDS)
            .maxDepth(10)
            .build();
        
        assertEquals(TrackingOptions.TrackingDepth.DEEP, options.getDepth());
        assertTrue(options.isTypeAwareEnabled());
        assertEquals(ObjectType.VALUE_OBJECT, options.getForcedObjectType());
        assertEquals(ValueObjectCompareStrategy.FIELDS, options.getForcedStrategy());
        assertEquals(10, options.getMaxDepth());
    }
    
    @Test
    void testDeepDefaultsWithTypeAware() {
        TrackingOptions options = TrackingOptions.deep();
        
        // deep()默认不启用类型感知
        assertFalse(options.isTypeAwareEnabled());
        assertNull(options.getForcedObjectType());
        assertNull(options.getForcedStrategy());
        
        // 但可以后续启用
        TrackingOptions enhanced = TrackingOptions.builder()
            .depth(options.getDepth())
            .maxDepth(options.getMaxDepth())
            .collectionStrategy(options.getCollectionStrategy())
            .enableTypeAware()
            .build();
        
        assertTrue(enhanced.isTypeAwareEnabled());
    }
    
    @Test
    void testTypeAwareFieldsImmutability() {
        TrackingOptions options = TrackingOptions.builder()
            .enableTypeAware()
            .forceObjectType(ObjectType.ENTITY)
            .forceStrategy(ValueObjectCompareStrategy.EQUALS)
            .build();
        
        // 验证返回的值是不可变的（原始类型本身就是不可变的）
        assertTrue(options.isTypeAwareEnabled());
        assertEquals(ObjectType.ENTITY, options.getForcedObjectType());
        assertEquals(ValueObjectCompareStrategy.EQUALS, options.getForcedStrategy());
        
        // 这些值在构建后不应该改变
        assertSame(ObjectType.ENTITY, options.getForcedObjectType());
        assertSame(ValueObjectCompareStrategy.EQUALS, options.getForcedStrategy());
    }
}