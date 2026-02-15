package com.syy.taskflowinsight.tracking.compare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompareConstantsTests {

    @Test
    void testEntityStrategyConstantExists() {
        assertEquals("ENTITY", CompareConstants.STRATEGY_ENTITY,
                "STRATEGY_ENTITY should equal 'ENTITY'");
    }

    @Test
    void testEntityStrategyConstantDistinct() {
        assertNotEquals(CompareConstants.STRATEGY_SIMPLE, CompareConstants.STRATEGY_ENTITY);
        assertNotEquals(CompareConstants.STRATEGY_LEVENSHTEIN, CompareConstants.STRATEGY_ENTITY);
        assertNotEquals(CompareConstants.STRATEGY_AS_SET, CompareConstants.STRATEGY_ENTITY);
    }
}

