package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EntityListStrategy Spring组件注册集成测试
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
@SpringBootTest
class EntityListStrategyComponentTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void testEntityListStrategyBeanRegistration() {
        // 验证 Spring 容器中存在名为 "entityListStrategy" 的 Bean
        assertTrue(context.containsBean("entityListStrategy"),
                "Spring container should contain entityListStrategy bean");

        // 获取 Bean 并验证类型
        Object bean = context.getBean("entityListStrategy");
        assertNotNull(bean, "entityListStrategy bean should not be null");
        assertInstanceOf(EntityListStrategy.class, bean,
                "Bean should be instance of EntityListStrategy");

        // 验证 Bean 实现了 ListCompareStrategy 接口
        assertInstanceOf(ListCompareStrategy.class, bean,
                "Bean should implement ListCompareStrategy interface");
    }

    @Test
    void testEntityListStrategyUsesConstant() {
        // 获取策略实例
        EntityListStrategy strategy = context.getBean("entityListStrategy", EntityListStrategy.class);

        // 验证 getStrategyName() 返回正确的常量值
        assertEquals(CompareConstants.STRATEGY_ENTITY, strategy.getStrategyName(),
                "Strategy name should equal STRATEGY_ENTITY constant");
        assertEquals("ENTITY", strategy.getStrategyName(),
                "Strategy name should be 'ENTITY'");
    }

    @Test
    void testEntityListStrategyBeanIsSingleton() {
        // 验证 Bean 是单例模式
        EntityListStrategy bean1 = context.getBean("entityListStrategy", EntityListStrategy.class);
        EntityListStrategy bean2 = context.getBean("entityListStrategy", EntityListStrategy.class);

        assertSame(bean1, bean2,
                "entityListStrategy bean should be singleton (same instance)");
    }
}