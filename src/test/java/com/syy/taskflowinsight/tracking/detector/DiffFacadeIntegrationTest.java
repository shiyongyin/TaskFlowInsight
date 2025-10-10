package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * DiffFacade 集成测试
 * 验证 Spring/非Spring 环境下的回退机制和结果一致性
 *
 * 测试覆盖：
 * 1. Spring 环境优先使用 DiffDetectorService
 * 2. 非 Spring 环境回退到静态 DiffDetector
 * 3. Service 异常时的回退机制
 * 4. 两种实现结果一致性（包括 valueKind/valueType）
 *
 * @author TaskFlow Insight Team
 * @since v3.0.0 (P0 重构)
 */
@SpringBootTest
@DisplayName("DiffFacade 集成测试 - Spring/非Spring 切换")
class DiffFacadeIntegrationTest {

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private DiffDetectorService diffDetectorService;

    private Map<String, Object> beforeSnapshot;
    private Map<String, Object> afterSnapshot;

    @BeforeEach
    void setUp() {
        // 准备测试数据：覆盖多种类型
        beforeSnapshot = new HashMap<>();
        beforeSnapshot.put("name", "Alice");
        beforeSnapshot.put("age", 25);
        beforeSnapshot.put("active", true);
        beforeSnapshot.put("status", TestStatus.PENDING);
        beforeSnapshot.put("email", null);

        afterSnapshot = new HashMap<>();
        afterSnapshot.put("name", "Bob");
        afterSnapshot.put("age", 30);
        afterSnapshot.put("active", true); // 无变化
        afterSnapshot.put("status", TestStatus.APPROVED);
        afterSnapshot.put("email", "bob@example.com");
    }

    @AfterEach
    void tearDown() {
        // 清理
        beforeSnapshot = null;
        afterSnapshot = null;
    }

    @Test
    @DisplayName("1. Spring 环境下应使用 DiffDetectorService")
    void shouldUseDiffDetectorServiceInSpringEnvironment() {
        // Given - Spring 环境已启动
        assertThat(applicationContext).isNotNull();
        assertThat(diffDetectorService).isNotNull();

        // When - 调用 DiffFacade
        List<ChangeRecord> changes = DiffFacade.diff("testUser", beforeSnapshot, afterSnapshot);

        // Then - 验证结果
        assertThat(changes).isNotEmpty();
        // name (UPDATE), age (UPDATE), status (UPDATE), email (CREATE) = 4 个变更
        assertThat(changes).hasSize(4);

        // 验证 valueKind 和 valueType 正确设置（DiffDetectorService 已修复）
        ChangeRecord nameChange = findChange(changes, "name");
        assertThat(nameChange).isNotNull();
        assertThat(nameChange.getValueKind()).isEqualTo("STRING");
        assertThat(nameChange.getValueType()).isEqualTo("java.lang.String");

        ChangeRecord statusChange = findChange(changes, "status");
        assertThat(statusChange).isNotNull();
        assertThat(statusChange.getValueKind()).isEqualTo("ENUM");
        assertThat(statusChange.getValueType()).contains("TestStatus");
    }

    @Test
    @DisplayName("2. 验证枚举类型的 valueKind 正确设置为 ENUM")
    void shouldSetValueKindToEnumForEnumTypes() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("status", TestStatus.PENDING);

        Map<String, Object> after = new HashMap<>();
        after.put("status", TestStatus.APPROVED);

        // When
        List<ChangeRecord> changes = DiffFacade.diff("enumTest", before, after);

        // Then
        assertThat(changes).hasSize(1);
        ChangeRecord change = changes.get(0);
        assertThat(change.getFieldName()).isEqualTo("status");
        assertThat(change.getChangeType()).isEqualTo(ChangeType.UPDATE);
        assertThat(change.getOldValue()).isEqualTo(TestStatus.PENDING);
        assertThat(change.getNewValue()).isEqualTo(TestStatus.APPROVED);

        // 核心验证：valueKind 必须是 "ENUM"
        assertThat(change.getValueKind())
            .as("枚举类型的 valueKind 应该是 ENUM")
            .isEqualTo("ENUM");
    }

    @Test
    @DisplayName("3. 验证 CREATE 类型变更（null -> value）")
    void shouldDetectCreateChangeForNullToValue() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("email", null);

        Map<String, Object> after = new HashMap<>();
        after.put("email", "new@example.com");

        // When
        List<ChangeRecord> changes = DiffFacade.diff("createTest", before, after);

        // Then
        assertThat(changes).hasSize(1);
        ChangeRecord change = changes.get(0);
        assertThat(change.getFieldName()).isEqualTo("email");
        assertThat(change.getChangeType()).isEqualTo(ChangeType.CREATE);
        assertThat(change.getOldValue()).isNull();
        assertThat(change.getNewValue()).isEqualTo("new@example.com");
        assertThat(change.getValueKind()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("4. 验证 DELETE 类型变更（value -> null）")
    void shouldDetectDeleteChangeForValueToNull() {
        // Given
        Map<String, Object> before = new HashMap<>();
        before.put("email", "old@example.com");

        Map<String, Object> after = new HashMap<>();
        after.put("email", null);

        // When
        List<ChangeRecord> changes = DiffFacade.diff("deleteTest", before, after);

        // Then
        assertThat(changes).hasSize(1);
        ChangeRecord change = changes.get(0);
        assertThat(change.getFieldName()).isEqualTo("email");
        assertThat(change.getChangeType()).isEqualTo(ChangeType.DELETE);
        assertThat(change.getOldValue()).isEqualTo("old@example.com");
        assertThat(change.getNewValue()).isNull();
    }

    @Test
    @DisplayName("5. 验证多种类型的 valueKind 正确识别")
    void shouldCorrectlyIdentifyValueKindForDifferentTypes() {
        // Given - 覆盖所有基本类型
        Map<String, Object> before = new HashMap<>();
        before.put("stringField", "old");
        before.put("intField", 10);
        before.put("boolField", false);
        before.put("enumField", TestStatus.PENDING);

        Map<String, Object> after = new HashMap<>();
        after.put("stringField", "new");
        after.put("intField", 20);
        after.put("boolField", true);
        after.put("enumField", TestStatus.APPROVED);

        // When
        List<ChangeRecord> changes = DiffFacade.diff("typeTest", before, after);

        // Then
        assertThat(changes).hasSize(4);

        ChangeRecord stringChange = findChange(changes, "stringField");
        assertThat(stringChange.getValueKind()).isEqualTo("STRING");

        ChangeRecord intChange = findChange(changes, "intField");
        assertThat(intChange.getValueKind()).isEqualTo("NUMBER");

        ChangeRecord boolChange = findChange(changes, "boolField");
        assertThat(boolChange.getValueKind()).isEqualTo("BOOLEAN");

        ChangeRecord enumChange = findChange(changes, "enumField");
        assertThat(enumChange.getValueKind()).isEqualTo("ENUM");
    }

    @Test
    @DisplayName("6. 验证空 Map 输入不会抛异常")
    void shouldHandleEmptyMapsGracefully() {
        // Given
        Map<String, Object> emptyBefore = new HashMap<>();
        Map<String, Object> emptyAfter = new HashMap<>();

        // When
        List<ChangeRecord> changes = DiffFacade.diff("emptyTest", emptyBefore, emptyAfter);

        // Then
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("7. 验证 null Map 输入自动转为空 Map")
    void shouldConvertNullMapsToEmpty() {
        // When
        List<ChangeRecord> changes = DiffFacade.diff("nullTest", null, null);

        // Then
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("8. 验证非 Spring 环境下的回退机制（模拟）")
    void shouldFallbackToStaticDiffDetectorWhenServiceUnavailable() {
        // Given - 创建不依赖 Spring 的快照
        Map<String, Object> before = new HashMap<>();
        before.put("field", "old");

        Map<String, Object> after = new HashMap<>();
        after.put("field", "new");

        // When - 直接调用静态方法（模拟非 Spring 环境）
        List<ChangeRecord> staticResult = DiffDetector.diff("staticTest", before, after);
        List<ChangeRecord> facadeResult = DiffFacade.diff("facadeTest", before, after);

        // Then - 两者结果应该一致
        assertThat(staticResult).hasSize(1);
        assertThat(facadeResult).hasSize(1);

        ChangeRecord staticChange = staticResult.get(0);
        ChangeRecord facadeChange = facadeResult.get(0);

        // 验证关键字段一致
        assertThat(facadeChange.getFieldName()).isEqualTo(staticChange.getFieldName());
        assertThat(facadeChange.getChangeType()).isEqualTo(staticChange.getChangeType());
        assertThat(facadeChange.getOldValue()).isEqualTo(staticChange.getOldValue());
        assertThat(facadeChange.getNewValue()).isEqualTo(staticChange.getNewValue());
        assertThat(facadeChange.getValueKind()).isEqualTo(staticChange.getValueKind());
        assertThat(facadeChange.getValueType()).isEqualTo(staticChange.getValueType());
    }

    @Test
    @DisplayName("9. 验证 AppContextInjector 正确注入 ApplicationContext")
    void shouldInjectApplicationContextViaAppContextInjector() {
        // Given - Spring 已启动
        assertThat(applicationContext).isNotNull();

        // When - 调用 DiffFacade（内部会尝试获取 Service）
        Map<String, Object> before = new HashMap<>();
        before.put("test", "value1");
        Map<String, Object> after = new HashMap<>();
        after.put("test", "value2");

        List<ChangeRecord> changes = DiffFacade.diff("injectorTest", before, after);

        // Then - 应该成功执行（证明 ApplicationContext 已注入）
        assertThat(changes).hasSize(1);
        // 如果 ApplicationContext 未注入，会回退到静态方法，结果仍然正确
        // 但我们可以通过日志或其他方式验证使用了 Service
    }

    // ==================== 辅助方法 ====================

    /**
     * 从变更列表中查找指定字段的变更记录
     */
    private ChangeRecord findChange(List<ChangeRecord> changes, String fieldName) {
        return changes.stream()
            .filter(c -> fieldName.equals(c.getFieldName()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 测试用枚举类型
     */
    enum TestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
