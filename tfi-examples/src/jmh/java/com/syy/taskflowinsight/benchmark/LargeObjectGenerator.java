package com.syy.taskflowinsight.benchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合成对象生成器（用于JMH基准测试）
 *
 * 生成包含大量字段的对象，模拟真实场景中的复杂业务对象。
 * 用于测试过滤链路在大对象场景下的性能表现。
 *
 * @author TaskFlow Insight Team
 * @since 2025-10-09
 */
public class LargeObjectGenerator {

    /**
     * 生成包含100个字段的大对象
     *
     * @return 合成的大对象
     */
    public static LargeBusinessObject generateLargeObject() {
        LargeBusinessObject obj = new LargeBusinessObject();

        // 填充100个字段（模拟真实业务对象）
        for (int i = 0; i < 100; i++) {
            obj.fields.put("field" + i, "value" + i);
        }

        // 添加一些敏感字段（用于测试过滤）
        obj.fields.put("password", "secret123");
        obj.fields.put("internal.token", "token456");
        obj.fields.put("debug.info", "debugData");

        // 添加嵌套对象
        obj.nested = new NestedObject();
        obj.nested.id = "nested001";
        obj.nested.data = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            obj.nested.data.put("nestedField" + i, "nestedValue" + i);
        }

        // 添加集合
        obj.items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Item item = new Item();
            item.id = "item" + i;
            item.value = "itemValue" + i;
            obj.items.add(item);
        }

        return obj;
    }

    /**
     * 生成小对象（用于对比基线性能）
     *
     * @return 小型对象
     */
    public static SmallObject generateSmallObject() {
        SmallObject obj = new SmallObject();
        obj.id = "small001";
        obj.name = "SmallObject";
        obj.value = 12345;
        return obj;
    }

    // ========== 测试模型类 ==========

    public static class LargeBusinessObject {
        public Map<String, String> fields = new HashMap<>();
        public NestedObject nested;
        public List<Item> items;
    }

    public static class NestedObject {
        public String id;
        public Map<String, String> data;
    }

    public static class Item {
        public String id;
        public String value;
    }

    public static class SmallObject {
        public String id;
        public String name;
        public int value;
    }
}
