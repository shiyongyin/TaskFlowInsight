package com.syy.taskflowinsight.registry;

import com.syy.taskflowinsight.annotation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DiffRegistry程序化注册机制测试
 * 
 * @author TaskFlow Insight Team
 * @version 3.0.0
 * @since 2025-01-17
 */
class DiffRegistryTests {
    
    @BeforeEach
    void setUp() {
        DiffRegistry.clear();
    }
    
    @Test
    void testEntityRegistration() {
        class TestClass {}
        
        DiffRegistry.registerEntity(TestClass.class);
        
        ObjectType type = DiffRegistry.getRegisteredType(TestClass.class);
        assertEquals(ObjectType.ENTITY, type);
    }
    
    @Test
    void testValueObjectRegistrationWithStrategy() {
        class TestClass {}
        
        DiffRegistry.registerValueObject(TestClass.class, ValueObjectCompareStrategy.EQUALS);
        
        ObjectType type = DiffRegistry.getRegisteredType(TestClass.class);
        ValueObjectCompareStrategy strategy = DiffRegistry.getRegisteredStrategy(TestClass.class);
        
        assertEquals(ObjectType.VALUE_OBJECT, type);
        assertEquals(ValueObjectCompareStrategy.EQUALS, strategy);
    }
    
    @Test
    void testValueObjectRegistrationWithDefaultStrategy() {
        class TestClass {}
        
        DiffRegistry.registerValueObject(TestClass.class);
        
        ObjectType type = DiffRegistry.getRegisteredType(TestClass.class);
        ValueObjectCompareStrategy strategy = DiffRegistry.getRegisteredStrategy(TestClass.class);
        
        assertEquals(ObjectType.VALUE_OBJECT, type);
        assertEquals(ValueObjectCompareStrategy.FIELDS, strategy); // 默认策略
    }
    
    @Test
    void testUnregisteredClassQuery() {
        class TestClass {}
        
        ObjectType type = DiffRegistry.getRegisteredType(TestClass.class);
        ValueObjectCompareStrategy strategy = DiffRegistry.getRegisteredStrategy(TestClass.class);
        
        assertNull(type); // 未注册返回null
        assertEquals(ValueObjectCompareStrategy.AUTO, strategy); // 默认策略
    }
    
    @Test
    void testBatchRegistration() {
        class TestClass1 {}
        class TestClass2 {}
        
        Map<Class<?>, ObjectType> types = new HashMap<>();
        types.put(TestClass1.class, ObjectType.ENTITY);
        types.put(TestClass2.class, ObjectType.VALUE_OBJECT);
        
        Map<Class<?>, ValueObjectCompareStrategy> strategies = new HashMap<>();
        strategies.put(TestClass1.class, ValueObjectCompareStrategy.EQUALS);
        strategies.put(TestClass2.class, ValueObjectCompareStrategy.FIELDS);
        
        DiffRegistry.registerAll(types, strategies);
        
        assertEquals(ObjectType.ENTITY, DiffRegistry.getRegisteredType(TestClass1.class));
        assertEquals(ObjectType.VALUE_OBJECT, DiffRegistry.getRegisteredType(TestClass2.class));
        assertEquals(ValueObjectCompareStrategy.EQUALS, DiffRegistry.getRegisteredStrategy(TestClass1.class));
        assertEquals(ValueObjectCompareStrategy.FIELDS, DiffRegistry.getRegisteredStrategy(TestClass2.class));
    }
    
    @Test
    void testUnregistration() {
        class TestClass {}
        
        DiffRegistry.registerEntity(TestClass.class);
        assertEquals(ObjectType.ENTITY, DiffRegistry.getRegisteredType(TestClass.class));
        
        DiffRegistry.unregister(TestClass.class);
        assertNull(DiffRegistry.getRegisteredType(TestClass.class));
    }
    
    @Test
    void testRegistrationStats() {
        class TestEntity1 {}
        class TestEntity2 {}
        class TestValueObject1 {}
        
        DiffRegistry.registerEntity(TestEntity1.class);
        DiffRegistry.registerEntity(TestEntity2.class);
        DiffRegistry.registerValueObject(TestValueObject1.class);
        
        Map<ObjectType, Long> stats = DiffRegistry.getRegistrationStats();
        
        assertEquals(2L, stats.get(ObjectType.ENTITY).longValue());
        assertEquals(1L, stats.get(ObjectType.VALUE_OBJECT).longValue());
    }
    
    @Test
    void testSizeTracking() {
        assertEquals(0, DiffRegistry.size());
        
        class TestClass1 {}
        class TestClass2 {}
        
        DiffRegistry.registerEntity(TestClass1.class);
        assertEquals(1, DiffRegistry.size());
        
        DiffRegistry.registerValueObject(TestClass2.class);
        assertEquals(2, DiffRegistry.size());
        
        DiffRegistry.unregister(TestClass1.class);
        assertEquals(1, DiffRegistry.size());
        
        DiffRegistry.clear();
        assertEquals(0, DiffRegistry.size());
    }
    
    @Test
    void testRegistrationOverride() {
        class TestClass {}
        
        // 第一次注册为Entity
        DiffRegistry.registerEntity(TestClass.class);
        assertEquals(ObjectType.ENTITY, DiffRegistry.getRegisteredType(TestClass.class));
        
        // 重新注册为ValueObject
        DiffRegistry.registerValueObject(TestClass.class, ValueObjectCompareStrategy.EQUALS);
        assertEquals(ObjectType.VALUE_OBJECT, DiffRegistry.getRegisteredType(TestClass.class));
        assertEquals(ValueObjectCompareStrategy.EQUALS, DiffRegistry.getRegisteredStrategy(TestClass.class));
    }
    
    @Test
    void testNullInputHandling() {
        assertThrows(NullPointerException.class, () -> {
            DiffRegistry.registerEntity(null);
        });
        
        assertThrows(NullPointerException.class, () -> {
            DiffRegistry.registerValueObject(null, ValueObjectCompareStrategy.FIELDS);
        });
        
        assertThrows(NullPointerException.class, () -> {
            DiffRegistry.registerValueObject(String.class, null);
        });
    }
    
    @Test
    void testThreadSafety() throws InterruptedException {
        // 简单的并发测试
        class TestClass {}
        
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    if (index % 2 == 0) {
                        DiffRegistry.registerEntity(TestClass.class);
                    } else {
                        DiffRegistry.registerValueObject(TestClass.class, ValueObjectCompareStrategy.FIELDS);
                    }
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证最终状态是一致的
        ObjectType finalType = DiffRegistry.getRegisteredType(TestClass.class);
        assertNotNull(finalType);
        assertTrue(finalType == ObjectType.ENTITY || finalType == ObjectType.VALUE_OBJECT);
    }
}