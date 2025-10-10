package com.syy.taskflowinsight.tracking.ssot.key;

import com.syy.taskflowinsight.annotation.Key;
import com.syy.taskflowinsight.tracking.cache.ReflectionMetaCache;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单一事实源：稳定键生成
 * 规则：仅基于 @Key/约定/复合属性生成；禁止 toString() 兜底
 */
public final class EntityKeyUtils {

    private static final Map<Class<?>, List<Field>> KEY_FIELDS_CACHE = new ConcurrentHashMap<>();
    // 轻量缓存：引用标识字符串（弱键，避免内存泄漏）。注意：键通常稳定（@Key），如模型允许变更需谨慎使用。
    private static final java.util.Map<Object, String> REFERENCE_ID_CACHE =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 受控未解析标记（用于降级或标注） */
    public static final String UNRESOLVED = "__UNRESOLVED__";

    /**
     * 可选的反射元数据缓存（由 Spring 配置注入）。
     * 存在时用于获取类字段，减少反射开销。
     */
    private static volatile ReflectionMetaCache reflectionCache;

    private EntityKeyUtils() {}

    /**
     * 尝试计算稳定键（带字段名），若无法稳定识别，返回 Optional.empty()
     * 例如：id=1001|username=alice
     */
    public static Optional<String> tryComputeStableKey(Object entity) {
        if (entity == null) return Optional.empty();

        List<Field> keyFields = collectKeyFields(entity.getClass());
        if (keyFields.isEmpty()) {
            // 禁止 toString 兜底；返回 empty 交由上层降级
            return Optional.empty();
        }

        // 生成带字段名的稳定键：field=value 用 | 连接
        List<String> parts = new ArrayList<>();
        for (Field f : keyFields) {
            Object v = get(f, entity);
            Optional<String> norm = normalizeKeyComponent(v);
            if (norm.isEmpty()) return Optional.empty(); // 有组件不可稳定表示 → 整体放弃
            parts.add(f.getName() + "=" + escape(norm.get()));
        }
        return Optional.of(String.join("|", parts));
    }

    /**
     * 计算或返回受控未解析标记（带字段名）
     */
    public static String computeStableKeyOrUnresolved(Object entity) {
        return tryComputeStableKey(entity).orElse(UNRESOLVED);
    }

    /**
     * 计算稳定键或返回 null（适用于 null 对象）
     *
     * @param entity 实体对象（可为 null）
     * @return 实体键，或 null（当对象为 null 时）
     * @since v3.1.0-P1
     */
    public static String computeStableKeyOrNull(Object entity) {
        if (entity == null) {
            return null;
        }
        return tryComputeStableKey(entity).orElse(null);
    }

    /**
     * 计算“紧凑键”（仅值，冒号连接），用于 entity[<compactKey>] 路径与索引映射。
     * 单字段示例："1001"；复合："1001:US"。
     */
    public static Optional<String> tryComputeCompactKey(Object entity) {
        if (entity == null) return Optional.empty();
        List<Field> keyFields = collectKeyFields(entity.getClass());
        if (keyFields.isEmpty()) return Optional.empty();
        List<String> parts = new ArrayList<>();
        for (Field f : keyFields) {
            Object v = get(f, entity);
            Optional<String> norm = normalizeKeyComponent(v);
            if (norm.isEmpty()) return Optional.empty();
            parts.add(escape(norm.get()));
        }
        return Optional.of(String.join(":", parts));
    }

    /**
     * 计算或返回受控未解析标记（紧凑值模式）。
     */
    public static String computeCompactKeyOrUnresolved(Object entity) {
        return tryComputeCompactKey(entity).orElse(UNRESOLVED);
    }


    /**
     * P3.1: 公开 collectKeyFields 方法供其他组件复用（SSOT 合并）
     *
     * @param type 类型
     * @return @Key 字段列表（从父类到子类顺序，带缓存）
     */
    public static List<Field> collectKeyFields(Class<?> type) {
        return KEY_FIELDS_CACHE.computeIfAbsent(type, t -> {
            List<Field> res = new ArrayList<>();
            Deque<Class<?>> stack = new ArrayDeque<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) stack.push(c);
            while (!stack.isEmpty()) {
                Class<?> current = stack.pop();
                List<Field> fields = getDeclaredFields(current);
                for (Field f : fields) {
                    if (f.isAnnotationPresent(Key.class)) {
                        f.setAccessible(true);
                        res.add(f);
                    }
                }
            }
            return Collections.unmodifiableList(res);
        });
    }

    /**
     * 从 ReflectionMetaCache 获取字段，若未配置则回退到 Class#getDeclaredFields。
     */
    private static List<Field> getDeclaredFields(Class<?> clazz) {
        ReflectionMetaCache cache = reflectionCache;
        if (cache != null) {
            return cache.getFieldsOrResolve(clazz, ReflectionMetaCache::defaultFieldResolver);
        }
        return Arrays.asList(clazz.getDeclaredFields());
    }

    /**
     * 注入 ReflectionMetaCache（由配置类在 Bean 创建后调用）。
     */
    public static void setReflectionMetaCache(ReflectionMetaCache cache) {
        reflectionCache = cache;
    }

    private static Optional<String> normalizeKeyComponent(Object v) {
        if (v == null) return Optional.of("∅");
        if (v instanceof CharSequence s) return Optional.of(s.toString());
        if (v instanceof Number || v instanceof Boolean || v instanceof Enum) return Optional.of(String.valueOf(v));
        if (v.getClass().isArray()) return Optional.of(Arrays.deepToString((Object[]) toObjectArray(v)));
        // P1.2: 稳定化 Collection/Map - 排序后拼接，避免迭代顺序不确定
        if (v instanceof Collection<?> c) {
            List<String> sorted = c.stream().map(String::valueOf).sorted().toList();
            return Optional.of(String.join(",", sorted));
        }
        if (v instanceof Map<?,?> m) {
            List<String> sorted = m.entrySet().stream().map(Object::toString).sorted().toList();
            return Optional.of(String.join(",", sorted));
        }
        // 非推荐类型：若对象 equals/hashCode 为值语义，可使用 hashCode ；否则放弃
        int hc = Objects.hashCode(v); // 值语义的 hashCode 可稳定；默认 identity 的不推荐
        return hc == System.identityHashCode(v) ? Optional.empty() : Optional.of(Integer.toString(hc));
    }

    private static Object[] toObjectArray(Object array) {
        int len = java.lang.reflect.Array.getLength(array);
        Object[] obj = new Object[len];
        for (int i = 0; i < len; i++) obj[i] = java.lang.reflect.Array.get(array, i);
        return obj;
    }

    private static Object get(Field f, Object target) {
        try { return f.get(target); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    /**
     * 计算引用标识（P1-T2：用于浅引用语义）
     * <p>
     * 优先级：
     * 1. 如果对象标注 @Entity 且有 @Key 字段，使用 EntityKeyUtils 生成稳定键
     * 2. 否则使用 Class简名 + "@" + identityHashCode 的十六进制
     * </p>
     *
     * @param entity 实体对象（可为 null）
     * @return 引用标识字符串，null 返回 "null"
     * @since v3.1.0-P1
     */
    public static String computeReferenceIdentifier(Object entity) {
        if (entity == null) {
            return "null";
        }
        // 命中缓存（弱引用，不保留对象生命周期）
        try {
            String cached = REFERENCE_ID_CACHE.get(entity);
            if (cached != null) {
                return cached;
            }
        } catch (Throwable ignore) { /* safe no-op */ }

        // 尝试使用稳定键
        Optional<String> stableKey = tryComputeCompactKey(entity);
        if (stableKey.isPresent() && !UNRESOLVED.equals(stableKey.get())) {
            // 格式：ClassName[key]
            String className = entity.getClass().getSimpleName();
            String out = className + "[" + stableKey.get() + "]";
            try { REFERENCE_ID_CACHE.put(entity, out); } catch (Throwable ignore) {}
            return out;
        }

        // 降级：使用 identityHashCode
        String className = entity.getClass().getSimpleName();
        String identityHash = Integer.toHexString(System.identityHashCode(entity));
        String out = className + "@" + identityHash;
        try { REFERENCE_ID_CACHE.put(entity, out); } catch (Throwable ignore) {}
        return out;
    }

    private static String escape(String s) {
        return s.replace("|", "\\|").replace("=", "\\=").replace("#", "\\#").replace("[", "\\[").replace("]", "\\]").replace(":", "\\:");
    }
}
