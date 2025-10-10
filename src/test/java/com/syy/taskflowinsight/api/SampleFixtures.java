package com.syy.taskflowinsight.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 稳定的样例数据工厂（占位）。
 */
final class SampleFixtures {
    private SampleFixtures() {}

    static Object sampleA() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", 1001);
        m.put("status", "PENDING");
        m.put("total", "100.00");
        return m;
    }

    static Object sampleB() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", 1001);
        m.put("status", "PAID");
        m.put("total", "120.00");
        return m;
    }
}

