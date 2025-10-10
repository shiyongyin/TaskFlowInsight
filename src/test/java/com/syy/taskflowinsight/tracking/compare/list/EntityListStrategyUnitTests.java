package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.tracking.compare.CompareConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityListStrategyUnitTests {

    @Test
    void getStrategyNameReturnsConstant() {
        EntityListStrategy s = new EntityListStrategy();
        assertEquals(CompareConstants.STRATEGY_ENTITY, s.getStrategyName());
    }
}

