# TFI-MVP-260 单元测试

## 任务概述
为DiffDetector标量对比逻辑实现全面的单元测试，确保各种输入场景下的稳定性和可靠性。

## 核心目标
- [ ] 覆盖null/类型变化/相等/不等场景
- [ ] 测试String/Number/Boolean/Date类型
- [ ] 实现全分支覆盖
- [ ] 验证valueType/valueKind/valueRepr正确性
- [ ] 确保日期比较基于时间戳

## 实现清单

### 1. DiffDetector标量测试主类
```java
package com.syy.taskflowinsight.core.diff;

import com.syy.taskflowinsight.core.ChangeRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DiffDetector标量对比测试")
class DiffDetectorScalarTest {
    
    private DiffDetector diffDetector;
    
    @BeforeEach
    void setUp() {
        diffDetector = new DiffDetector();
    }
    
    @Test
    @DisplayName("测试null值场景")
    void testNullValues() {
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        
        // null -> null (无变化)
        before.put("field1", null);
        after.put("field1", null);
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertTrue(changes.isEmpty(), "null -> null should not generate changes");
        
        // null -> value
        before.put("field2", null);
        after.put("field2", "new value");
        
        changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        ChangeRecord change = changes.get(0);
        assertEquals("TestObject.field2", change.getFieldPath());
        assertEquals("null", change.getOldValueRepr());
        assertEquals("new value", change.getNewValueRepr());
        assertEquals("SCALAR", change.getValueKind());
        assertEquals("String", change.getValueType());
        
        // value -> null
        before.put("field3", "old value");
        after.put("field3", null);
        
        changes = diffDetector.diff("TestObject", Map.of("field3", "old value"), 
                                  Map.of("field3", (Object) null));
        assertEquals(1, changes.size());
        
        change = changes.get(0);
        assertEquals("old value", change.getOldValueRepr());
        assertEquals("null", change.getNewValueRepr());
    }
    
    @Test
    @DisplayName("测试类型变化")
    void testTypeChanges() {
        // String -> Number
        Map<String, Object> before = Map.of("field", "123");
        Map<String, Object> after = Map.of("field", 123);
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        ChangeRecord change = changes.get(0);
        assertEquals("String", change.getOldValueType());
        assertEquals("Integer", change.getNewValueType());
        assertEquals("123", change.getOldValueRepr());
        assertEquals("123", change.getNewValueRepr());
        
        // Number -> Boolean
        before = Map.of("field", 1);
        after = Map.of("field", true);
        
        changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        change = changes.get(0);
        assertEquals("Integer", change.getOldValueType());
        assertEquals("Boolean", change.getNewValueType());
    }
    
    @Test
    @DisplayName("测试String类型比较")
    void testStringComparison() {
        // 相等字符串
        Map<String, Object> before = Map.of("name", "John");
        Map<String, Object> after = Map.of("name", "John");
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertTrue(changes.isEmpty());
        
        // 不等字符串
        before = Map.of("name", "John");
        after = Map.of("name", "Jane");
        
        changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        ChangeRecord change = changes.get(0);
        assertEquals("TestObject.name", change.getFieldPath());
        assertEquals("John", change.getOldValueRepr());
        assertEquals("Jane", change.getNewValueRepr());
        assertEquals("String", change.getValueType());
        assertEquals("SCALAR", change.getValueKind());
        
        // 空字符串处理
        before = Map.of("name", "");
        after = Map.of("name", "John");
        
        changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        assertEquals("", changes.get(0).getOldValueRepr());
        
        // 包含特殊字符的字符串
        String specialString = "Line1\nLine2\tTab\"Quote\\Backslash";
        before = Map.of("text", "normal");
        after = Map.of("text", specialString);
        
        changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        change = changes.get(0);
        assertEquals("normal", change.getOldValueRepr());
        assertTrue(change.getNewValueRepr().contains("\\n"));
        assertTrue(change.getNewValueRepr().contains("\\t"));
        assertTrue(change.getNewValueRepr().contains("\\\""));
        assertTrue(change.getNewValueRepr().contains("\\\\"));
    }
    
    @ParameterizedTest
    @DisplayName("测试Number类型比较")
    @CsvSource({
        "100, 100, false",    // 相等
        "100, 200, true",     // 不等
        "0, 0, false",        // 零值相等
        "-10, 10, true"       // 负数正数
    })
    void testNumberComparison(int oldValue, int newValue, boolean shouldChange) {
        Map<String, Object> before = Map.of("amount", oldValue);
        Map<String, Object> after = Map.of("amount", newValue);
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        
        if (shouldChange) {
            assertEquals(1, changes.size());
            ChangeRecord change = changes.get(0);
            assertEquals("TestObject.amount", change.getFieldPath());
            assertEquals(String.valueOf(oldValue), change.getOldValueRepr());
            assertEquals(String.valueOf(newValue), change.getNewValueRepr());
            assertEquals("Integer", change.getValueType());
        } else {
            assertTrue(changes.isEmpty());
        }
    }
    
    @Test
    @DisplayName("测试各种Number类型")
    void testVariousNumberTypes() {
        // Integer
        testSingleNumberChange(100, 200, "Integer");
        
        // Long
        testSingleNumberChange(100L, 200L, "Long");
        
        // Double
        testSingleNumberChange(100.5, 200.5, "Double");
        
        // Float
        testSingleNumberChange(100.5f, 200.5f, "Float");
        
        // BigDecimal
        testSingleNumberChange(
            new java.math.BigDecimal("100.50"), 
            new java.math.BigDecimal("200.50"), 
            "BigDecimal"
        );
    }
    
    private void testSingleNumberChange(Object oldValue, Object newValue, String expectedType) {
        Map<String, Object> before = Map.of("value", oldValue);
        Map<String, Object> after = Map.of("value", newValue);
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        ChangeRecord change = changes.get(0);
        assertEquals(expectedType, change.getValueType());
        assertEquals("SCALAR", change.getValueKind());
    }
    
    @ParameterizedTest
    @DisplayName("测试Boolean类型比较")
    @CsvSource({
        "true, true, false",   // 相等
        "false, false, false", // 相等
        "true, false, true",   // 不等
        "false, true, true"    // 不等
    })
    void testBooleanComparison(boolean oldValue, boolean newValue, boolean shouldChange) {
        Map<String, Object> before = Map.of("enabled", oldValue);
        Map<String, Object> after = Map.of("enabled", newValue);
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        
        if (shouldChange) {
            assertEquals(1, changes.size());
            ChangeRecord change = changes.get(0);
            assertEquals("TestObject.enabled", change.getFieldPath());
            assertEquals(String.valueOf(oldValue), change.getOldValueRepr());
            assertEquals(String.valueOf(newValue), change.getNewValueRepr());
            assertEquals("Boolean", change.getValueType());
        } else {
            assertTrue(changes.isEmpty());
        }
    }
    
    @Test
    @DisplayName("测试Date类型比较")
    void testDateComparison() {
        Date date1 = new Date(1000000000L);
        Date date2 = new Date(1000000000L); // 相同时间戳
        Date date3 = new Date(2000000000L); // 不同时间戳
        
        // 相等时间戳
        Map<String, Object> before = Map.of("createTime", date1);
        Map<String, Object> after = Map.of("createTime", date2);
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertTrue(changes.isEmpty(), "Same timestamp should not generate changes");
        
        // 不同时间戳
        before = Map.of("createTime", date1);
        after = Map.of("createTime", date3);
        
        changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        ChangeRecord change = changes.get(0);
        assertEquals("TestObject.createTime", change.getFieldPath());
        assertEquals("Date", change.getValueType());
        assertEquals("SCALAR", change.getValueKind());
        
        // 验证基于时间戳比较
        assertTrue(change.getOldValueRepr().contains("1000000000"));
        assertTrue(change.getNewValueRepr().contains("2000000000"));
    }
    
    @Test
    @DisplayName("测试LocalDateTime类型比较")
    void testLocalDateTimeComparison() {
        LocalDateTime dt1 = LocalDateTime.of(2023, 1, 1, 10, 0, 0);
        LocalDateTime dt2 = LocalDateTime.of(2023, 1, 1, 10, 0, 0); // 相同时间
        LocalDateTime dt3 = LocalDateTime.of(2023, 1, 2, 10, 0, 0); // 不同时间
        
        // 相等时间
        Map<String, Object> before = Map.of("eventTime", dt1);
        Map<String, Object> after = Map.of("eventTime", dt2);
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertTrue(changes.isEmpty());
        
        // 不等时间
        before = Map.of("eventTime", dt1);
        after = Map.of("eventTime", dt3);
        
        changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        ChangeRecord change = changes.get(0);
        assertEquals("LocalDateTime", change.getValueType());
    }
    
    @Test
    @DisplayName("测试混合类型场景")
    void testMixedTypeScenario() {
        Map<String, Object> before = new HashMap<>();
        before.put("name", "John");
        before.put("age", 30);
        before.put("active", true);
        before.put("createTime", new Date(1000000000L));
        
        Map<String, Object> after = new HashMap<>();
        after.put("name", "Jane");      // String变化
        after.put("age", 30);          // Number不变
        after.put("active", false);    // Boolean变化
        after.put("createTime", new Date(2000000000L)); // Date变化
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertEquals(3, changes.size()); // 3个字段发生变化
        
        // 验证每个变化
        Map<String, ChangeRecord> changeMap = new HashMap<>();
        for (ChangeRecord change : changes) {
            String fieldName = change.getFieldPath().split("\\.")[1];
            changeMap.put(fieldName, change);
        }
        
        // 验证name变化
        ChangeRecord nameChange = changeMap.get("name");
        assertNotNull(nameChange);
        assertEquals("John", nameChange.getOldValueRepr());
        assertEquals("Jane", nameChange.getNewValueRepr());
        assertEquals("String", nameChange.getValueType());
        
        // 验证active变化
        ChangeRecord activeChange = changeMap.get("active");
        assertNotNull(activeChange);
        assertEquals("true", activeChange.getOldValueRepr());
        assertEquals("false", activeChange.getNewValueRepr());
        assertEquals("Boolean", activeChange.getValueType());
        
        // 验证createTime变化
        ChangeRecord timeChange = changeMap.get("createTime");
        assertNotNull(timeChange);
        assertEquals("Date", timeChange.getValueType());
    }
    
    @Test
    @DisplayName("测试边界值")
    void testBoundaryValues() {
        // 最大最小整数
        testSingleNumberChange(Integer.MAX_VALUE, Integer.MIN_VALUE, "Integer");
        
        // 极大极小浮点数
        testSingleNumberChange(Double.MAX_VALUE, Double.MIN_VALUE, "Double");
        
        // 超长字符串
        String veryLongString = "a".repeat(10000);
        Map<String, Object> before = Map.of("text", "short");
        Map<String, Object> after = Map.of("text", veryLongString);
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        ChangeRecord change = changes.get(0);
        assertTrue(change.getNewValueRepr().length() <= 8192, 
            "Long strings should be truncated");
    }
    
    @Test
    @DisplayName("测试字段路径正确性")
    void testFieldPathCorrectness() {
        Map<String, Object> before = Map.of(
            "simpleField", "old",
            "complex.field", "old",
            "field_with_underscore", "old"
        );
        
        Map<String, Object> after = Map.of(
            "simpleField", "new",
            "complex.field", "new", 
            "field_with_underscore", "new"
        );
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", before, after);
        assertEquals(3, changes.size());
        
        Set<String> expectedPaths = Set.of(
            "TestObject.simpleField",
            "TestObject.complex.field",
            "TestObject.field_with_underscore"
        );
        
        Set<String> actualPaths = new HashSet<>();
        for (ChangeRecord change : changes) {
            actualPaths.add(change.getFieldPath());
        }
        
        assertEquals(expectedPaths, actualPaths);
    }
    
    @Test
    @DisplayName("测试无变化场景")
    void testNoChangeScenarios() {
        // 完全相同的对象
        Map<String, Object> identical = Map.of(
            "name", "John",
            "age", 30,
            "active", true
        );
        
        List<ChangeRecord> changes = diffDetector.diff("TestObject", identical, identical);
        assertTrue(changes.isEmpty());
        
        // 空Map比较
        changes = diffDetector.diff("TestObject", new HashMap<>(), new HashMap<>());
        assertTrue(changes.isEmpty());
    }
}
```

### 2. 数值精度测试
```java
package com.syy.taskflowinsight.core.diff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("数值精度测试")
class NumberPrecisionTest {
    
    private final DiffDetector diffDetector = new DiffDetector();
    
    @Test
    @DisplayName("测试浮点数精度比较")
    void testFloatingPointPrecision() {
        // 精确相等
        Map<String, Object> before = Map.of("value", 0.1 + 0.2);
        Map<String, Object> after = Map.of("value", 0.3);
        
        // 由于浮点数精度问题，这两个值实际上不相等
        var changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size()); // 应该检测到变化
        
        // BigDecimal精确比较
        BigDecimal bd1 = new BigDecimal("0.30");
        BigDecimal bd2 = new BigDecimal("0.3");
        
        before = Map.of("value", bd1);
        after = Map.of("value", bd2);
        
        changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size()); // 不同的scale应该被检测
    }
    
    @Test
    @DisplayName("测试数值类型转换")
    void testNumericTypeConversion() {
        // int vs long
        Map<String, Object> before = Map.of("value", 100);
        Map<String, Object> after = Map.of("value", 100L);
        
        var changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size()); // 类型不同应该被检测
        
        var change = changes.get(0);
        assertEquals("Integer", change.getOldValueType());
        assertEquals("Long", change.getNewValueType());
    }
}
```

### 3. 特殊字符处理测试
```java
package com.syy.taskflowinsight.core.diff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("特殊字符处理测试")
class SpecialCharacterTest {
    
    private final DiffDetector diffDetector = new DiffDetector();
    
    @ParameterizedTest
    @DisplayName("测试各种特殊字符转义")
    @ValueSource(strings = {
        "Line1\nLine2",           // 换行符
        "Col1\tCol2",             // 制表符
        "Say \"Hello\"",          // 双引号
        "Path\\to\\file",         // 反斜杠
        "Multi\r\nLine",          // 回车换行
        "\u0000null char",        // 空字符
        "Unicode: \u4e2d\u6587"   // Unicode字符
    })
    void testSpecialCharacterEscaping(String testString) {
        Map<String, Object> before = Map.of("text", "normal");
        Map<String, Object> after = Map.of("text", testString);
        
        var changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        String newValueRepr = changes.get(0).getNewValueRepr();
        
        // 验证转义
        if (testString.contains("\n")) {
            assertTrue(newValueRepr.contains("\\n"));
        }
        if (testString.contains("\t")) {
            assertTrue(newValueRepr.contains("\\t"));
        }
        if (testString.contains("\"")) {
            assertTrue(newValueRepr.contains("\\\""));
        }
        if (testString.contains("\\")) {
            assertTrue(newValueRepr.contains("\\\\"));
        }
        if (testString.contains("\r")) {
            assertTrue(newValueRepr.contains("\\r"));
        }
    }
    
    @Test
    @DisplayName("测试极长字符串截断")
    void testVeryLongStringTruncation() {
        String veryLongString = "x".repeat(20000);
        
        Map<String, Object> before = Map.of("text", "short");
        Map<String, Object> after = Map.of("text", veryLongString);
        
        var changes = diffDetector.diff("TestObject", before, after);
        assertEquals(1, changes.size());
        
        String newValueRepr = changes.get(0).getNewValueRepr();
        assertTrue(newValueRepr.length() <= 8192, "Should be truncated");
        assertTrue(newValueRepr.endsWith("... (truncated)"), "Should end with truncation marker");
    }
}
```

## 验证步骤
- [ ] 全分支覆盖测试通过
- [ ] null值场景处理正确
- [ ] 类型变化检测准确
- [ ] 日期比较基于时间戳
- [ ] valueType/valueKind/valueRepr正确
- [ ] 特殊字符正确转义
- [ ] 边界值处理稳定
- [ ] 字段路径格式正确

## 完成标准
- [ ] 所有单元测试通过
- [ ] 测试命名清晰有意义
- [ ] 无硬编码魔数
- [ ] 测试覆盖率达到95%以上
- [ ] 性能测试确认无明显回归