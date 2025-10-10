package com.syy.taskflowinsight.demo;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试Entity Key格式化方法
 */
public class Demo06FormatTest {

    @Entity(name = "Warehouse")
    public static class Warehouse {
        @Key
        private Long warehouseId;
        @Key
        private String regionCode;

        public Warehouse(Long warehouseId, String regionCode) {
            this.warehouseId = warehouseId;
            this.regionCode = regionCode;
        }
    }

    @Entity(name = "Product")
    public static class Product {
        @Key
        private Long productId;

        public Product(Long productId) {
            this.productId = productId;
        }
    }

    @Test
    public void testFormatEntityKeyForDisplay() {
        // 测试单主键
        Product product = new Product(123L);
        String result1 = formatEntityKeyForDisplay(product, "entity[123]");
        assertEquals("Product[productId=123]", result1);
        System.out.println("✅ 单主键: " + result1);

        // 测试联合主键
        Warehouse warehouse = new Warehouse(1001L, "US");
        String result2 = formatEntityKeyForDisplay(warehouse, "entity[1001:US]");
        assertEquals("Warehouse[warehouseId=1001, regionCode=US]", result2);
        System.out.println("✅ 联合主键: " + result2);
    }

    // 复制Demo06的方法
    private static String formatEntityKeyForDisplay(Object entity, String entityKey) {
        if (entity == null) {
            return "Entity[" + entityKey + "]";
        }

        // 从 "entity[1001:US]" 提取出 "1001:US"
        String compositeKeyValue = extractIdFromEntityKey(entityKey);

        String entityName = getEntityName(entity);
        List<Field> keyFields = getKeyFields(entity.getClass());

        if (keyFields.isEmpty()) {
            return entityName + "[" + compositeKeyValue + "]";
        }

        if (keyFields.size() == 1) {
            // 单主键：Product[productId=1]
            return String.format("%s[%s=%s]",
                entityName,
                keyFields.get(0).getName(),
                compositeKeyValue);
        } else {
            // 联合主键：Warehouse[warehouseId=1001, regionCode="US"]
            // compositeKeyValue格式: "1001:US"
            String[] values = compositeKeyValue.split(":", -1);
            List<String> pairs = new ArrayList<>();

            for (int i = 0; i < Math.min(keyFields.size(), values.length); i++) {
                String unescaped = values[i].replace("\\:", ":");
                pairs.add(keyFields.get(i).getName() + "=" + unescaped);
            }

            return String.format("%s[%s]", entityName, String.join(", ", pairs));
        }
    }

    private static String extractIdFromEntityKey(String entityKey) {
        int start = entityKey.indexOf("[");
        int end = entityKey.indexOf("]");
        if (start >= 0 && end > start) {
            return entityKey.substring(start + 1, end);
        }
        return entityKey;
    }

    private static String getEntityName(Object entity) {
        if (entity == null) {
            return "Entity";
        }

        Class<?> clazz = entity.getClass();
        Entity annotation = clazz.getAnnotation(Entity.class);

        if (annotation != null && !annotation.name().isEmpty()) {
            return annotation.name();
        }

        return clazz.getSimpleName();
    }

    private static List<Field> getKeyFields(Class<?> clazz) {
        List<Field> keyFields = new ArrayList<>();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Key.class)) {
                    field.setAccessible(true);
                    keyFields.add(field);
                }
            }
            clazz = clazz.getSuperclass();
        }

        return keyFields;
    }
}
