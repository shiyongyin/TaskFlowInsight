package com.syy.taskflowinsight.tracking.compare.list;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "tfi.compare.auto-route.entity.enabled=false"
})
class CompareRoutingPropertiesBindingTests {

    @Autowired
    private CompareRoutingProperties props;

    @Test
    void testBinding() {
        assertNotNull(props);
        assertNotNull(props.getEntity());
        assertFalse(props.getEntity().isEnabled(), "auto-route entity should be disabled via binding");
    }
}

