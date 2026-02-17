package com.syy.taskflowinsight.store;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StoreConfig}.
 */
class StoreConfigTest {

    @Test
    void defaultConfig_returnsConfigWithReasonableDefaults() {
        StoreConfig config = StoreConfig.defaultConfig();

        assertNotNull(config);
        assertEquals(10000, config.getMaxSize());
        assertEquals(Duration.ofMinutes(60), config.getDefaultTtl());
        assertEquals(Duration.ofMinutes(30), config.getIdleTimeout());
        assertTrue(config.isRecordStats());
        assertEquals(StoreConfig.EvictionStrategy.LRU, config.getEvictionStrategy());
    }

    @Test
    void fifoConfig_returnsFifoStrategy() {
        StoreConfig config = StoreConfig.fifoConfig();

        assertNotNull(config);
        assertEquals(StoreConfig.EvictionStrategy.FIFO, config.getEvictionStrategy());
        assertEquals(1000, config.getMaxSize());
        assertEquals(Duration.ofMinutes(30), config.getDefaultTtl());
        assertTrue(config.isRecordStats());
    }

    @Test
    void l1Config_hasSmallerMaxSizeThanL2() {
        StoreConfig l1 = StoreConfig.l1Config();
        StoreConfig l2 = StoreConfig.l2Config();

        assertNotNull(l1);
        assertNotNull(l2);
        assertEquals(1000, l1.getMaxSize());
        assertEquals(10000, l2.getMaxSize());
        assertTrue(l1.getMaxSize() < l2.getMaxSize());
    }

    @Test
    void l1Config_hasShorterTtlThanL2() {
        StoreConfig l1 = StoreConfig.l1Config();
        StoreConfig l2 = StoreConfig.l2Config();

        assertEquals(Duration.ofMinutes(10), l1.getDefaultTtl());
        assertEquals(Duration.ofMinutes(60), l2.getDefaultTtl());
    }

    @Test
    void l2Config_hasExpectedValues() {
        StoreConfig config = StoreConfig.l2Config();

        assertNotNull(config);
        assertEquals(10000, config.getMaxSize());
        assertEquals(Duration.ofMinutes(60), config.getDefaultTtl());
        assertEquals(Duration.ofMinutes(30), config.getIdleTimeout());
    }
}
