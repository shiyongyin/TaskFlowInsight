package com.syy.taskflowinsight.tracking.compare.list;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareOptions;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证关闭自动路由后，不会自动使用 ENTITY 策略。
 * 当 tfi.compare.auto-route.entity.enabled=false 时，缺省应回退为 SIMPLE 策略。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "tfi.compare.auto-route.entity.enabled=false"
})
class ListCompareExecutorAutoRouteDisabledTests {

    @Autowired
    private ListCompareExecutor executor;

    @Entity
    static class User {
        @Key
        Long id;
        String name;

        User(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Test
    void shouldUseSimpleWhenAutoRouteDisabledEvenForEntityLists() {
        // Given: 明确为实体列表（带 @Entity 和 @Key）
        List<User> oldList = Arrays.asList(new User(1L, "Alice"), new User(2L, "Bob"));
        List<User> newList = Arrays.asList(new User(1L, "Alice"), new User(2L, "Bobby"));

        CompareOptions options = CompareOptions.builder().build();

        // When: 执行比较（未显式指定策略）
        CompareResult result = executor.compare(oldList, newList, options);

        // Then: 自动路由被禁用，应当走 SIMPLE 策略
        // SIMPLE 策略的特征：基于索引输出，如 "[1]"，且不包含 entity[...] 路径
        assertNotNull(result);
        assertNotNull(result.getChanges());
        assertFalse(result.getChanges().isEmpty());

        // 不应出现 entity[...] 路径/名称
        for (FieldChange c : result.getChanges()) {
            String name = c.getFieldName();
            String path = c.getFieldPath();
            if (name != null) assertFalse(name.contains("entity["), "fieldName should not contain entity[...] when SIMPLE is used");
            if (path != null) assertFalse(path.contains("entity["), "fieldPath should not contain entity[...] when SIMPLE is used");
        }

        // 应至少包含对索引 [1] 的更新（第二个元素名称改变）
        assertTrue(result.getChanges().stream()
                .anyMatch(c -> "[1]".equals(c.getFieldName())),
                "SIMPLE strategy should report index-based change at [1]");
    }
}

