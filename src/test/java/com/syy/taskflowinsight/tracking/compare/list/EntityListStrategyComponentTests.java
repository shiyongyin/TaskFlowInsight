package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EntityListStrategyComponentTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ListCompareExecutor executor;

    @Test
    void entityListStrategyBeanIsRegistered() {
        assertTrue(applicationContext.containsBean("entityListStrategy"),
            "Bean 'entityListStrategy' should be present in context");

        Object bean = applicationContext.getBean("entityListStrategy");
        assertNotNull(bean);
        assertTrue(bean instanceof EntityListStrategy);
        assertTrue(bean instanceof ListCompareStrategy);
    }

    @Test
    void listCompareExecutorContainsEntityStrategy() {
        assertTrue(executor.getSupportedStrategies().contains(CompareConstants.STRATEGY_ENTITY),
            "ListCompareExecutor should contain ENTITY strategy");
    }
}

