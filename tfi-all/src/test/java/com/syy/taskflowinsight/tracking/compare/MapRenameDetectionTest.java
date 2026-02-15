package com.syy.taskflowinsight.tracking.compare;

import com.syy.taskflowinsight.tracking.ChangeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Map重命名检测测试
 * 验证MapCompareStrategy的键重命名检测功能
 * 
 * @author TaskFlow Insight Team
 * @version 2.1.0
 * @since 2025-01-13
 */
@SpringBootTest
class MapRenameDetectionTest {
    
    @Autowired
    private CompareService compareService;
    
    @Test
    void testKeyRenameDetection() {
        // 测试键重命名检测：相似度≥0.9且值相同
        Map<String, String> map1 = new HashMap<>();
        map1.put("userName", "alice");
        map1.put("userEmail", "alice@example.com");
        map1.put("userAge", "25");
        
        Map<String, String> map2 = new HashMap<>();
        map2.put("user_name", "alice");     // userName -> user_name (相似度≥0.9)
        map2.put("user_email", "alice@example.com"); // userEmail -> user_email (相似度≥0.9)
        map2.put("userAge", "25");          // 保持不变
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        assertFalse(result.isIdentical());
        
        // 应该检测到2个重命名操作
        long moveCount = result.getChangesByType(ChangeType.MOVE).size();
        assertEquals(2, moveCount);
        
        // 验证重命名的具体内容
        assertTrue(result.getChangesByType(ChangeType.MOVE).stream()
            .anyMatch(c -> "userName".equals(c.getFieldName()) &&
                          "alice".equals(c.getOldValue()) &&
                          "alice".equals(c.getNewValue()) &&
                          "user_name".equals(c.getFieldPath())));
        
        assertTrue(result.getChangesByType(ChangeType.MOVE).stream()
            .anyMatch(c -> "userEmail".equals(c.getFieldName()) &&
                          "alice@example.com".equals(c.getOldValue()) &&
                          "alice@example.com".equals(c.getNewValue()) &&
                          "user_email".equals(c.getFieldPath())));
    }
    
    @Test
    void testSimilarityThreshold() {
        // 测试相似度阈值：<0.7的不应该被识别为重命名
        Map<String, String> map1 = new HashMap<>();
        map1.put("name", "value");
        
        Map<String, String> map2 = new HashMap<>();
        map2.put("address", "value"); // name -> address 相似度很低 (0.14)
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        // 不应该检测到重命名，应该是DELETE+CREATE
        assertTrue(result.getChangesByType(ChangeType.MOVE).isEmpty());
        
        assertTrue(result.getChangesByType(ChangeType.DELETE).stream()
            .anyMatch(c -> "name".equals(c.getFieldName())));
        
        assertTrue(result.getChangesByType(ChangeType.CREATE).stream()
            .anyMatch(c -> "address".equals(c.getFieldName())));
    }
    
    @Test
    void testValueConsistencyRequired() {
        // 测试值一致性要求：键相似但值不同的不应该识别为重命名
        Map<String, String> map1 = new HashMap<>();
        map1.put("userName", "alice");
        
        Map<String, String> map2 = new HashMap<>();
        map2.put("user_name", "bob"); // 键相似但值不同
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        // 不应该检测到重命名
        assertTrue(result.getChangesByType(ChangeType.MOVE).isEmpty());
        
        // 应该是DELETE+CREATE
        assertFalse(result.getChangesByType(ChangeType.DELETE).isEmpty());
        assertFalse(result.getChangesByType(ChangeType.CREATE).isEmpty());
    }
    
    @Test
    void testCandidatePairsDegradation() {
        // 测试K>1000降级：候选配对数过多时禁用重命名检测
        Map<String, Integer> map1 = new HashMap<>();
        Map<String, Integer> map2 = new HashMap<>();
        
        // 创建35个删除键和30个新增键，候选配对数=35*30=1050>1000
        for (int i = 0; i < 35; i++) {
            map1.put("oldKey" + i, i);
        }
        
        for (int i = 0; i < 30; i++) {
            map2.put("newKey" + i, i); // 值可能匹配但键差异大
        }
        
        // 添加一些保持不变的键
        for (int i = 0; i < 10; i++) {
            String commonKey = "common" + i;
            map1.put(commonKey, i + 100);
            map2.put(commonKey, i + 100);
        }
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        // 因为降级，不应该有MOVE操作
        assertTrue(result.getChangesByType(ChangeType.MOVE).isEmpty());
        
        // 应该只有CREATE和DELETE操作
        int total = result.getChangeCount();
        int cd = result.getChangesByType(ChangeType.CREATE, ChangeType.DELETE).size();
        assertEquals(total, cd);
    }
    
    @Test
    void testMultipleRenames() {
        // 测试多个重命名检测
        Map<String, String> map1 = new HashMap<>();
        map1.put("firstName", "John");
        map1.put("lastName", "Doe");
        map1.put("emailAddr", "john.doe@example.com");
        map1.put("phoneNum", "123-456-7890");
        
        Map<String, String> map2 = new HashMap<>();
        map2.put("first_name", "John");     // firstName -> first_name
        map2.put("last_name", "Doe");       // lastName -> last_name
        map2.put("email_addr", "john.doe@example.com"); // emailAddr -> email_addr
        map2.put("phone_num", "123-456-7890");          // phoneNum -> phone_num
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        // 应该检测到4个重命名
        long moveCount = result.getChangesByType(ChangeType.MOVE).size();
        assertEquals(4, moveCount);
        
        // 不应该有其他类型的变更
        assertEquals(result.getChangeCount(), result.getChangesByType(ChangeType.MOVE).size());
    }
    
    @Test
    void testNoRenameDetectionWhenDisabled() {
        // 测试当候选配对数超限时的行为
        Map<String, String> map1 = new HashMap<>();
        map1.put("oldName", "value");
        
        Map<String, String> map2 = new HashMap<>();
        map2.put("old_name", "value"); // 应该被识别为重命名的情况
        
        // 但是构造一个会触发降级的场景（通过大量其他键）
        for (int i = 0; i < 50; i++) {
            map1.put("delete" + i, "val" + i);
        }
        for (int i = 0; i < 25; i++) {
            map2.put("create" + i, "val" + i);
        }
        // 候选配对数 = 51 * 26 = 1326 > 1000，应该降级
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        // 因为降级，不应该有MOVE操作
        assertTrue(result.getChanges().stream()
            .noneMatch(c -> c.getChangeType() == ChangeType.MOVE));
    }
    
    @Test
    void testEdgeCaseSimilarity() {
        // 测试边界相似度情况
        Map<String, String> map1 = new HashMap<>();
        map1.put("test", "value");
        
        Map<String, String> map2 = new HashMap<>();
        map2.put("tests", "value"); // test -> tests，相似度应该≥0.9
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        // 验证是否检测到重命名（这取决于具体的相似度计算）
        boolean hasMove = !result.getChangesByType(ChangeType.MOVE).isEmpty();
        
        if (hasMove) {
            // 如果检测到重命名，验证详细信息
            assertTrue(result.getChangesByType(ChangeType.MOVE).stream()
                .anyMatch(c -> "test".equals(c.getFieldName()) &&
                              "tests".equals(c.getFieldPath())));
        }
        
        // 至少应该有变更
        assertFalse(result.isIdentical());
    }
    
    @Test
    void testNullKeyHandling() {
        // 测试null键的处理
        Map<String, String> map1 = new HashMap<>();
        map1.put(null, "nullValue");
        map1.put("key1", "value1");
        
        Map<String, String> map2 = new HashMap<>();
        map2.put("nullKey", "nullValue"); // null -> nullKey
        map2.put("key1", "value1");
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        // 应该能正常处理，不抛异常
        assertNotNull(result);
        assertFalse(result.isIdentical());
    }
    
    @Test
    void testComplexObjectValues() {
        // 测试复杂对象值的重命名检测
        Map<String, Object> map1 = new HashMap<>();
        map1.put("userData", Map.of("name", "John", "age", 30));
        map1.put("config", Map.of("theme", "dark"));
        
        Map<String, Object> map2 = new HashMap<>();
        map2.put("user_data", Map.of("name", "John", "age", 30)); // 应该检测为重命名
        map2.put("configuration", Map.of("theme", "dark")); // config -> configuration 可能不够相似
        
        CompareOptions options = CompareOptions.builder().build();
        CompareResult result = compareService.compare(map1, map2, options);
        
        assertFalse(result.isIdentical());
        
        // 至少应该检测到一个重命名（userData -> user_data）
        boolean hasUserDataMove = result.getChanges().stream()
            .anyMatch(c -> c.getChangeType() == ChangeType.MOVE &&
                          "userData".equals(c.getFieldName()) &&
                          "user_data".equals(c.getFieldPath()));
        
        assertTrue(hasUserDataMove);
    }
}
