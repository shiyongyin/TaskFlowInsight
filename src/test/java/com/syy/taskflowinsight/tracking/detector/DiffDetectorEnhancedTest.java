package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.tracking.ChangeType;
import com.syy.taskflowinsight.tracking.model.ChangeRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Enhanced tests targeting low-coverage areas in DiffDetector
 * Focus: DiffDetector constructor (0%), diffWithMode (71%), getValueKind (68%), toRepr (71%)
 */
class DiffDetectorEnhancedTest {

    // ========== Constructor Tests (提升0%→100%) ==========

    @Test
    @DisplayName("DiffDetector - 构造函数抛出异常")
    void diffDetector_constructorThrowsException() throws Exception {
        Constructor<DiffDetector> constructor = DiffDetector.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        
        assertThatThrownBy(() -> constructor.newInstance())
            .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
            .hasCauseInstanceOf(UnsupportedOperationException.class)
            .hasRootCauseMessage("Utility class");
    }

    // ========== diffWithMode Method Edge Cases (提升71%→80%+) ==========

    @Test
    @DisplayName("diffWithMode - 异常场景处理")
    void diffWithMode_exceptionHandling() {
        // 测试异常对象 - 创建一个会抛异常的Map
        Map<String, Object> problematicMap = new HashMap<String, Object>() {
            @Override
            public Set<String> keySet() {
                throw new RuntimeException("Test exception");
            }
        };
        
        // 应该返回空列表而不是抛异常
        List<ChangeRecord> result = DiffDetector.diffWithMode("TestObject", problematicMap, 
            Collections.singletonMap("key", "value"), DiffDetector.DiffMode.COMPAT);
        
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("diffWithMode - 增强模式DELETE场景")
    void diffWithMode_enhancedModeDeleteScenario() {
        Map<String, Object> before = Map.of("deletedField", "oldValue");
        Map<String, Object> after = Collections.emptyMap();
        
        List<ChangeRecord> changes = DiffDetector.diffWithMode("TestObject", before, after, 
            DiffDetector.DiffMode.ENHANCED);
        
        assertThat(changes).hasSize(1);
        ChangeRecord change = changes.get(0);
        assertThat(change.getChangeType()).isEqualTo(ChangeType.DELETE);
        assertThat(change.getFieldName()).isEqualTo("deletedField");
        assertThat(change.getValueRepr()).isEqualTo("oldValue"); // DELETE用旧值
        assertThat(change.getReprOld()).isEqualTo("oldValue");
        assertThat(change.getReprNew()).isNull();
    }

    @Test
    @DisplayName("diffWithMode - 增强模式CREATE/UPDATE场景")
    void diffWithMode_enhancedModeCreateUpdateScenario() {
        Map<String, Object> before = Map.of("updateField", "oldValue");
        Map<String, Object> after = Map.of("updateField", "newValue", "createField", "createdValue");
        
        List<ChangeRecord> changes = DiffDetector.diffWithMode("TestObject", before, after, 
            DiffDetector.DiffMode.ENHANCED);
        
        assertThat(changes).hasSize(2);
        
        // CREATE场景
        ChangeRecord createChange = changes.stream()
            .filter(c -> "createField".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertThat(createChange.getChangeType()).isEqualTo(ChangeType.CREATE);
        assertThat(createChange.getValueRepr()).isEqualTo("createdValue"); // CREATE用新值
        assertThat(createChange.getReprOld()).isNull();
        assertThat(createChange.getReprNew()).isEqualTo("createdValue");
        
        // UPDATE场景
        ChangeRecord updateChange = changes.stream()
            .filter(c -> "updateField".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertThat(updateChange.getChangeType()).isEqualTo(ChangeType.UPDATE);
        assertThat(updateChange.getValueRepr()).isEqualTo("newValue"); // UPDATE用新值
        assertThat(updateChange.getReprOld()).isEqualTo("oldValue");
        assertThat(updateChange.getReprNew()).isEqualTo("newValue");
    }

    @Test
    @DisplayName("diffWithMode - 兼容模式DELETE场景valueRepr为null")
    void diffWithMode_compatModeDeleteScenario() {
        Map<String, Object> before = Map.of("deletedField", "value");
        Map<String, Object> after = Collections.emptyMap();
        
        List<ChangeRecord> changes = DiffDetector.diffWithMode("TestObject", before, after, 
            DiffDetector.DiffMode.COMPAT);
        
        assertThat(changes).hasSize(1);
        ChangeRecord change = changes.get(0);
        assertThat(change.getChangeType()).isEqualTo(ChangeType.DELETE);
        assertThat(change.getValueRepr()).isNull(); // 兼容模式DELETE置空
    }

    @Test
    @DisplayName("diffWithMode - 值类型和分类设置")
    void diffWithMode_valueTypeAndKindSetting() {
        Map<String, Object> before = Collections.emptyMap();
        Map<String, Object> after = Map.of(
            "stringField", "value",
            "numberField", 42
        );
        
        List<ChangeRecord> changes = DiffDetector.diffWithMode("TestObject", before, after, 
            DiffDetector.DiffMode.COMPAT);
        
        // 检查string类型
        ChangeRecord stringChange = changes.stream()
            .filter(c -> "stringField".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertThat(stringChange.getValueType()).isEqualTo("java.lang.String");
        assertThat(stringChange.getValueKind()).isEqualTo("STRING");
        
        // 检查number类型
        ChangeRecord numberChange = changes.stream()
            .filter(c -> "numberField".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertThat(numberChange.getValueType()).isEqualTo("java.lang.Integer");
        assertThat(numberChange.getValueKind()).isEqualTo("NUMBER");
        
    }

    // ========== getValueKind Method Coverage (提升68%→90%+) ==========

    enum TestEnum { VALUE1, VALUE2 }

    @Test
    @DisplayName("getValueKind - 所有类型覆盖")
    void getValueKind_allTypeCoverage() throws Exception {
        // 通过反射访问私有方法
        java.lang.reflect.Method getValueKindMethod = 
            DiffDetector.class.getDeclaredMethod("getValueKind", Object.class);
        getValueKindMethod.setAccessible(true);
        
        // 测试所有类型
        assertThat(getValueKindMethod.invoke(null, (Object) null)).isEqualTo("NULL");
        assertThat(getValueKindMethod.invoke(null, "string")).isEqualTo("STRING");
        assertThat(getValueKindMethod.invoke(null, 42)).isEqualTo("NUMBER");
        assertThat(getValueKindMethod.invoke(null, 3.14)).isEqualTo("NUMBER");
        assertThat(getValueKindMethod.invoke(null, true)).isEqualTo("BOOLEAN");
        assertThat(getValueKindMethod.invoke(null, new Date())).isEqualTo("DATE");
        assertThat(getValueKindMethod.invoke(null, TestEnum.VALUE1)).isEqualTo("ENUM");
        assertThat(getValueKindMethod.invoke(null, Arrays.asList("a", "b"))).isEqualTo("COLLECTION");
        assertThat(getValueKindMethod.invoke(null, Collections.singletonMap("key", "value"))).isEqualTo("MAP");
        assertThat(getValueKindMethod.invoke(null, (Object) new String[]{"a", "b"})).isEqualTo("ARRAY");
        assertThat(getValueKindMethod.invoke(null, new Object())).isEqualTo("OTHER");
    }

    // ========== toRepr Method Coverage (提升71%→100%) ==========

    @Test
    @DisplayName("toRepr - null值处理")
    void toRepr_nullHandling() throws Exception {
        // 通过反射访问私有方法
        java.lang.reflect.Method toReprMethod = 
            DiffDetector.class.getDeclaredMethod("toRepr", Object.class);
        toReprMethod.setAccessible(true);
        
        // 测试null值
        Object result = toReprMethod.invoke(null, (Object) null);
        assertThat(result).isNull();
        
        // 测试非null值
        Object nonNullResult = toReprMethod.invoke(null, "test");
        assertThat(nonNullResult).isEqualTo("test");
    }

    // ========== Edge Cases and Integration Tests ==========

    @Test
    @DisplayName("diffWithMode - Date类型归一化处理")
    void diffWithMode_dateNormalization() {
        Date date1 = new Date(1000L);
        Date date2 = new Date(2000L);
        Date sameDate = new Date(1000L);
        
        Map<String, Object> before = Map.of("dateField", date1);
        Map<String, Object> after = Map.of("dateField", date2);
        
        List<ChangeRecord> changes = DiffDetector.diffWithMode("TestObject", before, after, 
            DiffDetector.DiffMode.COMPAT);
        
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getChangeType()).isEqualTo(ChangeType.UPDATE);
        
        // 测试相同Date不产生变更
        Map<String, Object> beforeSame = Map.of("dateField", date1);
        Map<String, Object> afterSame = Map.of("dateField", sameDate);
        
        List<ChangeRecord> noChanges = DiffDetector.diffWithMode("TestObject", beforeSame, afterSame, 
            DiffDetector.DiffMode.COMPAT);
        
        assertThat(noChanges).isEmpty();
    }

    @Test
    @DisplayName("diffWithMode - 复杂对象类型处理")
    void diffWithMode_complexObjectTypes() {
        Map<String, Object> complexValue = Map.of("nested", "value");
        List<String> listValue = Arrays.asList("item1", "item2");
        Set<Integer> setValue = Set.of(1, 2, 3);
        
        Map<String, Object> before = Collections.emptyMap();
        Map<String, Object> after = Map.of(
            "mapField", complexValue,
            "listField", listValue,
            "setField", setValue
        );
        
        List<ChangeRecord> changes = DiffDetector.diffWithMode("TestObject", before, after, 
            DiffDetector.DiffMode.ENHANCED);
        
        assertThat(changes).hasSize(3);
        
        // 验证MAP类型
        ChangeRecord mapChange = changes.stream()
            .filter(c -> "mapField".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertThat(mapChange.getValueKind()).isEqualTo("MAP");
        
        // 验证COLLECTION类型
        ChangeRecord listChange = changes.stream()
            .filter(c -> "listField".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertThat(listChange.getValueKind()).isEqualTo("COLLECTION");
        
        ChangeRecord setChange = changes.stream()
            .filter(c -> "setField".equals(c.getFieldName()))
            .findFirst().orElseThrow();
        assertThat(setChange.getValueKind()).isEqualTo("COLLECTION");
    }

    @Test
    @DisplayName("diffWithMode - null参数边界测试")
    void diffWithMode_nullParameterBoundaryTest() {
        // 测试所有null参数组合
        List<ChangeRecord> result1 = DiffDetector.diffWithMode("Test", null, null, 
            DiffDetector.DiffMode.COMPAT);
        assertThat(result1).isEmpty();
        
        List<ChangeRecord> result2 = DiffDetector.diffWithMode("Test", null, 
            Map.of("key", "value"), DiffDetector.DiffMode.COMPAT);
        assertThat(result2).hasSize(1);
        assertThat(result2.get(0).getChangeType()).isEqualTo(ChangeType.CREATE);
        
        List<ChangeRecord> result3 = DiffDetector.diffWithMode("Test", 
            Map.of("key", "value"), null, DiffDetector.DiffMode.COMPAT);
        assertThat(result3).hasSize(1);
        assertThat(result3.get(0).getChangeType()).isEqualTo(ChangeType.DELETE);
    }

    @Test
    @DisplayName("diffWithMode - 字段排序验证")
    void diffWithMode_fieldOrderingVerification() {
        Map<String, Object> before = Map.of("z_field", "value1", "a_field", "value2");
        Map<String, Object> after = Map.of("z_field", "newValue1", "a_field", "newValue2");
        
        List<ChangeRecord> changes = DiffDetector.diffWithMode("TestObject", before, after, 
            DiffDetector.DiffMode.COMPAT);
        
        // 验证字段按字典序排序
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).getFieldName()).isEqualTo("a_field");
        assertThat(changes.get(1).getFieldName()).isEqualTo("z_field");
    }
}