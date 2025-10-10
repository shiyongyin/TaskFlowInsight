package com.syy.taskflowinsight.api;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 静态便捷方法集成测试
 */
@SpringBootTest
class TfiListDiffStaticMethodsTests {

    @Entity
    static class User {
        @Key
        Long id;
        String name;
        User(Long id, String name) { this.id = id; this.name = name; }
    }

    @Test
    void testStaticRenderMethods() {
        List<String> oldList = Arrays.asList("a", "b");
        List<String> newList = Arrays.asList("a", "c");

        CompareResult result = TfiListDiff.diff(oldList, newList);

        assertDoesNotThrow(() -> TfiListDiff.render(result));
        assertDoesNotThrow(() -> TfiListDiff.render(result, "simple"));
        assertDoesNotThrow(() -> TfiListDiff.render(result, "detailed"));
    }

    @Test
    void testDiffEntitiesConvenience() {
        List<User> oldUsers = Arrays.asList(new User(1L, "Alice"));
        List<User> newUsers = Arrays.asList(new User(1L, "Alice"), new User(2L, "Bob"));

        var entityResult = TfiListDiff.diffEntities(oldUsers, newUsers);
        assertNotNull(entityResult);
        // 可用性检查
        entityResult.isIdentical();
        entityResult.getSimilarity();
    }
}

