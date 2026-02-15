package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.render.RenderStyle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TfiListDiff 渲染功能集成测试
 * <p>
 * 测试通过 TfiListDiffFacade 进行列表比较并渲染 Markdown 报告的完整流程。
 * </p>
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0
 */
@SpringBootTest
class TfiListDiffRenderIntegrationTests {

    @Autowired
    private TfiListDiffFacade listDiff;

    @Test
    void testRenderEmptyComparison() {
        // Given: 两个空列表
        List<User> oldList = Collections.emptyList();
        List<User> newList = Collections.emptyList();

        // When: 比较并渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result);

        // Then: 应该显示无变更消息
        assertNotNull(report);
        assertTrue(report.contains("✅ No Changes Detected"));
    }

    @Test
    void testRenderWithDefaultStyle() {
        // Given: 有变更的列表
        List<User> oldList = Arrays.asList(
                new User(1001L, "Alice", 25),
                new User(1002L, "Bob", 30)
        );
        List<User> newList = Arrays.asList(
                new User(1001L, "Alice", 26), // 年龄变更
                new User(1003L, "Charlie", 28) // 新增
        );

        // When: 使用默认样式渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result);

        // Then: 应该包含标准格式元素
        assertNotNull(report);
        assertTrue(report.contains("# Entity List Comparison Report"));
        assertTrue(report.contains("## Summary"));
        assertTrue(report.contains("## Statistics")); // 默认显示统计
        assertTrue(report.contains("## ➕ Added Entities"));
        assertTrue(report.contains("## ✏️ Modified Entities"));
        assertTrue(report.contains("## ❌ Deleted Entities"));
    }

    @Test
    void testRenderWithSimpleStyle() {
        // Given: 有变更的列表
        List<Product> oldList = Arrays.asList(
                new Product("P001", "Laptop", 999.99)
        );
        List<Product> newList = Arrays.asList(
                new Product("P001", "Laptop", 899.99) // 价格变更
        );

        // When: 使用简洁样式渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result, "simple");

        // Then: 应该使用简洁格式
        assertNotNull(report);
        assertTrue(report.contains("# Entity List Comparison Report"));
        assertFalse(report.contains("## Statistics")); // 简洁样式不显示统计
        assertFalse(report.contains("## Summary")); // SUMMARY 级别不显示摘要
    }

    @Test
    void testRenderWithDetailedStyle() {
        // Given: 有变更的列表
        List<Order> oldList = Arrays.asList(
                new Order("ORD001", "pending", 100.0)
        );
        List<Order> newList = Arrays.asList(
                new Order("ORD001", "completed", 100.0) // 状态变更
        );

        // When: 使用详细样式渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result, "detailed");

        // Then: 应该包含详细信息
        assertNotNull(report);
        assertTrue(report.contains("## Statistics"));
        assertFalse(report.contains("_Generated at:")); // PR-06: 详细样式不显示时间戳（golden test稳定性）
        assertTrue(report.contains("**Operation**:")); // 详细级别显示操作类型
    }

    @Test
    void testRenderWithRenderStyleObject() {
        // Given: 有变更的列表
        List<User> oldList = Arrays.asList(new User(1001L, "Alice", 25));
        List<User> newList = Arrays.asList(new User(1001L, "Alice Updated", 25));

        // When: 使用 RenderStyle 对象渲染
        CompareResult result = listDiff.diff(oldList, newList);
        RenderStyle customStyle = RenderStyle.builder()
                .detailLevel(RenderStyle.DetailLevel.NORMAL)
                .tableFormat(RenderStyle.TableFormat.GITHUB)
                .showStatistics(true)
                .showTimestamp(true)
                .maxValueLength(50)
                .build();
        String report = listDiff.render(result, customStyle);

        // Then: 应该应用自定义样式
        assertNotNull(report);
        assertTrue(report.contains("## Statistics"));
        assertTrue(report.contains("_Generated at:"));
        assertTrue(report.contains("| Field | Old Value | New Value | Type |"));
    }

    @Test
    void testRenderWithUnknownStyleString() {
        // Given: 有变更的列表
        List<User> oldList = Arrays.asList(new User(1001L, "Alice", 25));
        List<User> newList = Arrays.asList(new User(1001L, "Bob", 25));

        // When: 使用未知样式字符串（应该回退到标准样式）
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result, "unknown-style");

        // Then: 应该使用标准样式
        assertNotNull(report);
        assertTrue(report.contains("## Statistics")); // 标准样式显示统计
        assertFalse(report.contains("_Generated at:")); // 标准样式不显示时间戳
    }

    @Test
    void testRenderNullResult() {
        // When: 渲染 null 结果
        String report = listDiff.render(null);

        // Then: 应该返回空字符串
        assertNotNull(report);
        assertEquals("", report);
    }

    @Test
    void testRenderAddedEntities() {
        // Given: 只有新增的列表
        List<User> oldList = Collections.emptyList();
        List<User> newList = Arrays.asList(
                new User(1001L, "Alice", 25),
                new User(1002L, "Bob", 30)
        );

        // When: 比较并渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result);

        // Then: 应该只有新增节
        assertNotNull(report);
        assertTrue(report.contains("## ➕ Added Entities"));
        assertFalse(report.contains("## ✏️ Modified Entities"));
        assertFalse(report.contains("## ❌ Deleted Entities"));
        assertTrue(report.contains("### Entity: `entity[1001]`"));
        assertTrue(report.contains("### Entity: `entity[1002]`"));
    }

    @Test
    void testRenderDeletedEntities() {
        // Given: 只有删除的列表
        List<User> oldList = Arrays.asList(
                new User(1001L, "Alice", 25),
                new User(1002L, "Bob", 30)
        );
        List<User> newList = Collections.emptyList();

        // When: 比较并渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result);

        // Then: 应该只有删除节
        assertNotNull(report);
        assertFalse(report.contains("## ➕ Added Entities"));
        assertFalse(report.contains("## ✏️ Modified Entities"));
        assertTrue(report.contains("## ❌ Deleted Entities"));
        assertTrue(report.contains("### Entity: `entity[1001]`"));
        assertTrue(report.contains("### Entity: `entity[1002]`"));
    }

    @Test
    void testRenderModifiedEntities() {
        // Given: 只有修改的列表
        List<User> oldList = Arrays.asList(
                new User(1001L, "Alice", 25),
                new User(1002L, "Bob", 30)
        );
        List<User> newList = Arrays.asList(
                new User(1001L, "Alice Updated", 26),
                new User(1002L, "Bob Updated", 31)
        );

        // When: 比较并渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result);

        // Then: 应该只有修改节
        assertNotNull(report);
        assertFalse(report.contains("## ➕ Added Entities"));
        assertTrue(report.contains("## ✏️ Modified Entities"));
        assertFalse(report.contains("## ❌ Deleted Entities"));
        assertTrue(report.contains("### Entity: `entity[1001]`"));
        assertTrue(report.contains("### Entity: `entity[1002]`"));
    }

    @Test
    void testRenderComplexChanges() {
        // Given: 包含新增、修改、删除的复杂场景
        List<Product> oldList = Arrays.asList(
                new Product("P001", "Laptop", 999.99),
                new Product("P002", "Mouse", 29.99),
                new Product("P003", "Keyboard", 79.99)
        );
        List<Product> newList = Arrays.asList(
                new Product("P001", "Laptop", 899.99), // 价格修改
                new Product("P003", "Mechanical Keyboard", 99.99), // 名称和价格修改
                new Product("P004", "Monitor", 299.99) // 新增
                // P002 被删除
        );

        // When: 比较并渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result);

        // Then: 应该包含所有三种操作
        assertNotNull(report);
        assertTrue(report.contains("## ➕ Added Entities"));
        assertTrue(report.contains("## ✏️ Modified Entities"));
        assertTrue(report.contains("## ❌ Deleted Entities"));

        // 验证统计数据
        assertTrue(report.contains("➕ Added"));
        assertTrue(report.contains("✏️ Modified"));
        assertTrue(report.contains("❌ Deleted"));

        // 验证具体实体
        assertTrue(report.contains("P001")); // 修改
        assertTrue(report.contains("P002")); // 删除
        assertTrue(report.contains("P003")); // 修改
        assertTrue(report.contains("P004")); // 新增
    }

    @Test
    void testRenderWithSpecialCharacters() {
        // Given: 包含特殊字符的实体
        List<Product> oldList = Arrays.asList(
                new Product("P001", "Product | with | pipes", 99.99)
        );
        List<Product> newList = Arrays.asList(
                new Product("P001", "Product\nwith\nnewlines", 99.99)
        );

        // When: 比较并渲染
        CompareResult result = listDiff.diff(oldList, newList);
        String report = listDiff.render(result);

        // Then: 特殊字符应该被正确转义
        assertNotNull(report);
        assertTrue(report.contains("\\|")); // 管道符转义
        assertTrue(report.contains("Product with newlines")); // 换行符替换为空格
    }

    @Test
    void testRenderWithLongValues() {
        // Given: 包含超长值的实体
        String longName = "A".repeat(200);
        List<Product> oldList = Arrays.asList(
                new Product("P001", "Short Name", 99.99)
        );
        List<Product> newList = Arrays.asList(
                new Product("P001", longName, 99.99)
        );

        // When: 比较并渲染（使用自定义长度限制）
        CompareResult result = listDiff.diff(oldList, newList);
        RenderStyle style = RenderStyle.builder().maxValueLength(50).build();
        String report = listDiff.render(result, style);

        // Then: 长值应该被截断
        assertNotNull(report);
        assertTrue(report.contains("..."));
        assertFalse(report.contains("A".repeat(100))); // 不应该包含完整的长字符串
    }

    @Test
    void testEndToEndWorkflow() {
        // Given: 完整的用户场景
        List<Order> oldOrders = Arrays.asList(
                new Order("ORD001", "pending", 100.0),
                new Order("ORD002", "completed", 200.0)
        );
        List<Order> newOrders = Arrays.asList(
                new Order("ORD001", "completed", 100.0), // 状态更新
                new Order("ORD003", "pending", 150.0) // 新订单
                // ORD002 被取消/删除
        );

        // When: 执行完整流程：比较 -> 渲染
        CompareResult result = listDiff.diff(oldOrders, newOrders);
        String report = listDiff.render(result, RenderStyle.detailed());

        // Then: 验证完整报告
        assertNotNull(report);

        // 标题和摘要
        assertTrue(report.contains("# Entity List Comparison Report"));
        assertTrue(report.contains("## Summary"));

        // 统计信息
        assertTrue(report.contains("## Statistics"));
        assertTrue(report.contains("| Operation | Count | Percentage |"));

        // 变更详情
        assertTrue(report.contains("## ➕ Added Entities"));
        assertTrue(report.contains("### Entity: `entity[ORD003]`"));
        assertTrue(report.contains("## ✏️ Modified Entities"));
        assertTrue(report.contains("### Entity: `entity[ORD001]`"));
        assertTrue(report.contains("## ❌ Deleted Entities"));
        assertTrue(report.contains("### Entity: `entity[ORD002]`"));

        // 详细信息（DETAILED 级别）
        assertTrue(report.contains("**Operation**:"));
        // Note: **Type**: is conditional - only shown if entityClass is set
        assertTrue(report.contains("**Changes**:"));

        // 时间戳 (PR-06: detailed样式不再显示时间戳)
        assertFalse(report.contains("_Generated at:"));

        // 字段变更表格
        assertTrue(report.contains("| Field | Old Value | New Value | Type |"));
    }

    // 测试用实体类

    @Entity
    static class User {
        @Key
        private Long id;
        private String name;
        private Integer age;

        public User(Long id, String name, Integer age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        // Getters
        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Integer getAge() {
            return age;
        }
    }

    @Entity
    static class Product {
        @Key
        private String productId;
        private String name;
        private Double price;

        public Product(String productId, String name, Double price) {
            this.productId = productId;
            this.name = name;
            this.price = price;
        }

        // Getters
        public String getProductId() {
            return productId;
        }

        public String getName() {
            return name;
        }

        public Double getPrice() {
            return price;
        }
    }

    @Entity
    static class Order {
        @Key
        private String orderId;
        private String status;
        private Double amount;

        public Order(String orderId, String status, Double amount) {
            this.orderId = orderId;
            this.status = status;
            this.amount = amount;
        }

        // Getters
        public String getOrderId() {
            return orderId;
        }

        public String getStatus() {
            return status;
        }

        public Double getAmount() {
            return amount;
        }
    }
}