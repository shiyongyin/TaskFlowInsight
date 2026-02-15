package com.syy.taskflowinsight.tracking.detector;

import com.syy.taskflowinsight.annotation.Entity;
import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.annotation.ShallowReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * DiffDetectorService 外科式测试
 * 覆盖未覆盖的私有方法：isShallowReferenceFieldResolved、resolveFieldValue、
 * isShallowReferenceField、init（通过反射或直接调用）
 *
 * @author Senior Test Expert
 * @since 3.0.0
 */
@DisplayName("DiffDetectorService 外科式测试")
class DiffDetectorServiceEdgeCaseTests {

    private DiffDetectorService service;

    @BeforeEach
    void setUp() {
        service = new DiffDetectorService();
    }

    // ── init (@PostConstruct) ──

    @Nested
    @DisplayName("init 方法")
    class InitMethod {

        @Test
        @DisplayName("init 手动调用不抛异常")
        void init_invokedManually_doesNotThrow() {
            assertThatCode(() -> service.init()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("init 后 precisionMetrics 已尝试启用 Micrometer")
        void init_enablesMicrometerIfAvailable() {
            service.init();
            // init 内部调用 precisionMetrics.enableMicrometerIfAvailable()
            assertThat(service.getMetricsSnapshot()).isNotNull();
        }
    }

    // ── isShallowReferenceField（私有，反射调用）──

    @Nested
    @DisplayName("isShallowReferenceField — 反射测试")
    class IsShallowReferenceFieldReflection {

        private Method method;

        @BeforeEach
        void setUpReflection() throws Exception {
            method = DiffDetectorService.class.getDeclaredMethod(
                "isShallowReferenceField", Object.class, Object.class, String.class);
            method.setAccessible(true);
        }

        @Test
        @DisplayName("fieldPath 为 null 返回 false")
        void fieldPathNull_returnsFalse() throws Exception {
            RefHolder root = new RefHolder();
            Boolean result = (Boolean) method.invoke(service, root, root, (String) null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("fieldPath 为空字符串返回 false")
        void fieldPathEmpty_returnsFalse() throws Exception {
            RefHolder root = new RefHolder();
            Boolean result = (Boolean) method.invoke(service, root, root, "");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("rootA 和 rootB 均为 null 返回 false")
        void bothRootsNull_returnsFalse() throws Exception {
            Boolean result = (Boolean) method.invoke(service, null, null, "ref");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("rootA 为 null 时使用 rootB")
        void rootANull_usesRootB() throws Exception {
            RefHolder rootB = new RefHolder();
            rootB.ref = new KeyEntity("id1");
            Boolean result = (Boolean) method.invoke(service, null, rootB, "ref");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("rootB 为 null 时使用 rootA")
        void rootBNull_usesRootA() throws Exception {
            RefHolder rootA = new RefHolder();
            rootA.ref = new KeyEntity("id1");
            Boolean result = (Boolean) method.invoke(service, rootA, null, "ref");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("@ShallowReference 字段返回 true")
        void shallowRefField_returnsTrue() throws Exception {
            RefHolder root = new RefHolder();
            root.ref = new KeyEntity("id1");
            Boolean result = (Boolean) method.invoke(service, root, root, "ref");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("非 @ShallowReference 字段返回 false")
        void nonShallowRefField_returnsFalse() throws Exception {
            PlainHolder root = new PlainHolder();
            root.plain = "value";
            Boolean result = (Boolean) method.invoke(service, root, root, "plain");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("路径带类名前缀时正确解析")
        void pathWithClassPrefix_resolvesCorrectly() throws Exception {
            RefHolder root = new RefHolder();
            root.ref = new KeyEntity("id1");
            String path = "RefHolder.ref";
            Boolean result = (Boolean) method.invoke(service, root, root, path);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("嵌套路径解析末段 @ShallowReference")
        void nestedPath_resolvesLastSegment() throws Exception {
            OuterHolder root = new OuterHolder();
            root.inner = new InnerHolder();
            root.inner.ref = new KeyEntity("id1");
            Boolean result = (Boolean) method.invoke(service, root, root, "inner.ref");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("嵌套路径末段非 @ShallowReference 返回 false")
        void nestedPath_lastSegmentNotShallowRef_returnsFalse() throws Exception {
            OuterHolder root = new OuterHolder();
            root.inner = new InnerHolder();
            root.inner.data = "x";
            Boolean result = (Boolean) method.invoke(service, root, root, "inner.data");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("字段不存在返回 false")
        void fieldNotFound_returnsFalse() throws Exception {
            RefHolder root = new RefHolder();
            Boolean result = (Boolean) method.invoke(service, root, root, "nonexistent");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("中间路径为 null 返回 false")
        void intermediateNull_returnsFalse() throws Exception {
            OuterHolder root = new OuterHolder();
            root.inner = null;
            Boolean result = (Boolean) method.invoke(service, root, root, "inner.ref");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("路径含索引时 stripIndex 正确解析")
        void pathWithIndex_stripsIndexCorrectly() throws Exception {
            ListRefHolder root = new ListRefHolder();
            root.items = new ArrayList<>();
            root.items.add(new KeyEntity("id1"));
            Boolean result = (Boolean) method.invoke(service, root, root, "items[0]");
            assertThat(result).isTrue();
        }
    }

    // ── isShallowReferenceFieldResolved（私有，反射调用）──

    @Nested
    @DisplayName("isShallowReferenceFieldResolved — 反射测试")
    class IsShallowReferenceFieldResolvedReflection {

        private Method method;

        @BeforeEach
        void setUpReflection() throws Exception {
            method = DiffDetectorService.class.getDeclaredMethod(
                "isShallowReferenceFieldResolved", Object.class, Object.class, String.class);
            method.setAccessible(true);
        }

        @Test
        @DisplayName("fieldPath 为 null 返回 false")
        void fieldPathNull_returnsFalse() throws Exception {
            RefHolder root = new RefHolder();
            Boolean result = (Boolean) method.invoke(service, root, root, (String) null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("fieldPath 为空返回 false")
        void fieldPathEmpty_returnsFalse() throws Exception {
            RefHolder root = new RefHolder();
            Boolean result = (Boolean) method.invoke(service, root, root, "");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("root 为 null 返回 false")
        void rootNull_returnsFalse() throws Exception {
            Boolean result = (Boolean) method.invoke(service, null, null, "ref");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("简单 @ShallowReference 字段返回 true")
        void simpleShallowRef_returnsTrue() throws Exception {
            RefHolder root = new RefHolder();
            root.ref = new KeyEntity("id1");
            Boolean result = (Boolean) method.invoke(service, root, root, "ref");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("路径带类名前缀")
        void pathWithClassPrefix() throws Exception {
            RefHolder root = new RefHolder();
            root.ref = new KeyEntity("id1");
            Boolean result = (Boolean) method.invoke(service, root, root, "RefHolder.ref");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("List 索引路径 items[0] 且 items 为 @ShallowReference")
        void listIndexPath_itemsShallowRef() throws Exception {
            ListRefHolder root = new ListRefHolder();
            root.items = new ArrayList<>();
            root.items.add(new KeyEntity("id1"));
            Boolean result = (Boolean) method.invoke(service, root, root, "items[0]");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("List 索引越界返回 false")
        void listIndexOutOfBounds_returnsFalse() throws Exception {
            ListRefHolder root = new ListRefHolder();
            root.items = new ArrayList<>();
            root.items.add(new KeyEntity("id1"));
            Boolean result = (Boolean) method.invoke(service, root, root, "items[5]");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Map 索引路径")
        void mapIndexPath() throws Exception {
            MapRefHolder root = new MapRefHolder();
            root.byId = new HashMap<>();
            root.byId.put("k1", new KeyEntity("id1"));
            Boolean result = (Boolean) method.invoke(service, root, root, "byId[k1]");
            assertThat(result).isFalse(); // byId[k1] 取的是 map 的 value，末段字段是 byId，byId 有 @ShallowReference
            // 等等，末段是 "byId[k1]"，fieldName 是 "byId"，所以检查的是 byId 字段是否有 @ShallowReference
            // byId 是 Map，没有 @ShallowReference。所以返回 false。
            // 需要有一个 @ShallowReference 的 Map 字段？不，@ShallowReference 通常用于引用类型。
            // 看代码：last 时检查 f.isAnnotationPresent(ShallowReference)，f 是 byId 字段。
            // MapRefHolder.byId 没有 @ShallowReference，所以 false。正确。
        }

        @Test
        @DisplayName("数组索引路径")
        void arrayIndexPath() throws Exception {
            ArrayRefHolder root = new ArrayRefHolder();
            root.refs = new KeyEntity[]{new KeyEntity("id1")};
            Boolean result = (Boolean) method.invoke(service, root, root, "refs[0]");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("数组索引越界返回 false")
        void arrayIndexOutOfBounds_returnsFalse() throws Exception {
            ArrayRefHolder root = new ArrayRefHolder();
            root.refs = new KeyEntity[]{new KeyEntity("id1")};
            Boolean result = (Boolean) method.invoke(service, root, root, "refs[10]");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("嵌套路径末段为 @ShallowReference")
        void nestedPath_lastSegmentShallowRef() throws Exception {
            OuterHolder root = new OuterHolder();
            root.inner = new InnerHolder();
            root.inner.ref = new KeyEntity("id1");
            Boolean result = (Boolean) method.invoke(service, root, root, "inner.ref");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("中间值为 null 返回 false")
        void intermediateNull_returnsFalse() throws Exception {
            OuterHolder root = new OuterHolder();
            root.inner = null;
            Boolean result = (Boolean) method.invoke(service, root, root, "inner.ref");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("字段不存在返回 false")
        void fieldNotFound_returnsFalse() throws Exception {
            RefHolder root = new RefHolder();
            Boolean result = (Boolean) method.invoke(service, root, root, "nonexistent");
            assertThat(result).isFalse();
        }
    }

    // ── resolveFieldValue（私有，反射调用）──

    @Nested
    @DisplayName("resolveFieldValue — 反射测试")
    class ResolveFieldValueReflection {

        private Method method;

        @BeforeEach
        void setUpReflection() throws Exception {
            method = DiffDetectorService.class.getDeclaredMethod(
                "resolveFieldValue", Object.class, String.class);
            method.setAccessible(true);
        }

        @Test
        @DisplayName("root 为 null 返回 null")
        void rootNull_returnsNull() throws Exception {
            Object result = method.invoke(service, null, "ref");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("fieldPath 为 null 返回 null")
        void fieldPathNull_returnsNull() throws Exception {
            RefHolder root = new RefHolder();
            Object result = method.invoke(service, root, (String) null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("fieldPath 为空返回 null")
        void fieldPathEmpty_returnsNull() throws Exception {
            RefHolder root = new RefHolder();
            Object result = method.invoke(service, root, "");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("简单字段解析")
        void simpleField_resolvesValue() throws Exception {
            RefHolder root = new RefHolder();
            KeyEntity entity = new KeyEntity("id1");
            root.ref = entity;
            Object result = method.invoke(service, root, "ref");
            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("路径带类名前缀")
        void pathWithClassPrefix() throws Exception {
            RefHolder root = new RefHolder();
            root.ref = new KeyEntity("id1");
            Object result = method.invoke(service, root, "RefHolder.ref");
            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(KeyEntity.class);
        }

        @Test
        @DisplayName("嵌套路径点号解析")
        void nestedPath_dotNotation() throws Exception {
            OuterHolder root = new OuterHolder();
            root.inner = new InnerHolder();
            root.inner.data = "nested-value";
            Object result = method.invoke(service, root, "inner.data");
            assertThat(result).isEqualTo("nested-value");
        }

        @Test
        @DisplayName("List 索引解析 items[0]")
        void listIndex_resolvesElement() throws Exception {
            ListRefHolder root = new ListRefHolder();
            KeyEntity entity = new KeyEntity("id1");
            root.items = new ArrayList<>();
            root.items.add(entity);
            Object result = method.invoke(service, root, "items[0]");
            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("List 索引越界返回 null")
        void listIndexOutOfBounds_returnsNull() throws Exception {
            ListRefHolder root = new ListRefHolder();
            root.items = new ArrayList<>();
            root.items.add(new KeyEntity("id1"));
            Object result = method.invoke(service, root, "items[99]");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Map 索引解析 byId[k1]")
        void mapIndex_resolvesValue() throws Exception {
            MapRefHolder root = new MapRefHolder();
            KeyEntity entity = new KeyEntity("id1");
            root.byId = new HashMap<>();
            root.byId.put("k1", entity);
            Object result = method.invoke(service, root, "byId[k1]");
            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("Map 整数键回退解析")
        void mapIndex_integerKeyFallback() throws Exception {
            MapIntKeyHolder root = new MapIntKeyHolder();
            KeyEntity entity = new KeyEntity("id1");
            root.byId = new HashMap<>();
            root.byId.put(42, entity);
            Object result = method.invoke(service, root, "byId[42]");
            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("字段不存在返回 null")
        void fieldNotFound_returnsNull() throws Exception {
            RefHolder root = new RefHolder();
            Object result = method.invoke(service, root, "nonexistent");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("中间字段为 null 返回 null")
        void intermediateNull_returnsNull() throws Exception {
            OuterHolder root = new OuterHolder();
            root.inner = null;
            Object result = method.invoke(service, root, "inner.data");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("多层嵌套路径")
        void multiLevelNestedPath() throws Exception {
            Level3Holder root = new Level3Holder();
            root.l2 = new Level2Holder();
            root.l2.l1 = new Level1Holder();
            root.l2.l1.value = 42;
            Object result = method.invoke(service, root, "l2.l1.value");
            assertThat(result).isEqualTo(42);
        }
    }

    // ── 测试模型 ──

    static class PlainHolder {
        String plain;
    }

    static class RefHolder {
        @ShallowReference
        KeyEntity ref;
    }

    @Entity
    static class KeyEntity {
        @Key
        String id;

        KeyEntity(String id) {
            this.id = id;
        }
    }

    static class InnerHolder {
        @ShallowReference
        KeyEntity ref;
        String data;
    }

    static class OuterHolder {
        InnerHolder inner;
    }

    static class ListRefHolder {
        @ShallowReference
        List<KeyEntity> items;
    }

    static class MapRefHolder {
        Map<String, KeyEntity> byId;
    }

    static class MapIntKeyHolder {
        Map<Integer, KeyEntity> byId;
    }

    static class ArrayRefHolder {
        @ShallowReference
        KeyEntity[] refs;
    }

    static class Level1Holder {
        int value;
    }

    static class Level2Holder {
        Level1Holder l1;
    }

    static class Level3Holder {
        Level2Holder l2;
    }
}
