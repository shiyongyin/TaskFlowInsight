package com.syy.taskflowinsight.compatibility;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 从已发布 Core JAR 生成公共编译期常量 manifest。
 *
 * <p>生成器拒绝当前 {@code target/classes}，并对子项目包采用 child-first 隔离加载，
 * 原因是同名当前类若被父加载器抢先解析，会把工作树常量伪装成发布基线。
 * 常量 holder 还需显式列入已审查集合，避免读取未知静态初始化器产生副作用。
 *
 * @since 3.1.0
 */
public final class PublicConstantManifestGenerator {

    private static final String PROJECT_PREFIX = "com.syy.taskflowinsight.";
    private static final Set<String> REVIEWED_HOLDERS = Set.of(
            "com.syy.taskflowinsight.internal.ConfigDefaults",
            "com.syy.taskflowinsight.internal.ConfigDefaults$Keys",
            "com.syy.taskflowinsight.internal.FlowConfigDefaults");

    private PublicConstantManifestGenerator() {
    }

    /**
     * 打印按 key 排序的 {@code owner#field=type:value} manifest。
     *
     * @param args 唯一参数必须是仓库解析出的发布版 Core JAR
     * @throws Exception JAR 不合法、发现未审查 holder 或反射读取失败时抛出
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one baseline JAR argument");
        }

        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
            throw new IllegalArgumentException("Baseline JAR does not exist: " + jar);
        }
        if (jar.toString().contains("target/classes")) {
            throw new IllegalArgumentException("Baseline must not come from target/classes: " + jar);
        }

        for (String line : generate(jar)) {
            System.out.println(line);
        }
    }

    static List<String> generate(Path jar) throws Exception {
        List<String> classNames = classNames(jar);
        List<Field> constants = new ArrayList<>();
        URL jarUrl = jar.toUri().toURL();
        try (ChildFirstProjectClassLoader loader = new ChildFirstProjectClassLoader(jarUrl)) {
            for (String className : classNames) {
                Class<?> type = Class.forName(className, false, loader);
                for (Field field : type.getFields()) {
                    if (isCompileTimeConstant(field)
                            && field.getDeclaringClass().getName().startsWith(PROJECT_PREFIX)) {
                        constants.add(field);
                    }
                }
            }

            Set<String> holders = constants.stream()
                    .map(field -> field.getDeclaringClass().getName())
                    .collect(java.util.stream.Collectors.toSet());
            if (!holders.equals(REVIEWED_HOLDERS)) {
                Set<String> unreviewed = new java.util.TreeSet<>(holders);
                unreviewed.removeAll(REVIEWED_HOLDERS);
                Set<String> missing = new java.util.TreeSet<>(REVIEWED_HOLDERS);
                missing.removeAll(holders);
                throw new IllegalStateException(
                        "Constant holder review mismatch; unreviewed=" + unreviewed + ", missing=" + missing);
            }

            return constants.stream()
                    .distinct()
                    .map(PublicConstantManifestGenerator::manifestLine)
                    .sorted()
                    .toList();
        }
    }

    static boolean isCompileTimeConstant(Field field) {
        int modifiers = field.getModifiers();
        Class<?> type = field.getType();
        return Modifier.isPublic(modifiers)
                && Modifier.isStatic(modifiers)
                && Modifier.isFinal(modifiers)
                && (type.isPrimitive() || type == String.class);
    }

    static String fieldKey(Field field) {
        return field.getDeclaringClass().getName() + "#" + field.getName();
    }

    static String typeName(Field field) {
        return field.getType().getName();
    }

    static Object readValue(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot read public constant " + fieldKey(field), ex);
        }
    }

    private static String manifestLine(Field field) {
        return fieldKey(field) + "=" + typeName(field) + ":" + readValue(field);
    }

    private static List<String> classNames(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            return jarFile.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith("com/syy/taskflowinsight/"))
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.equals("module-info.class"))
                    .map(name -> name.substring(0, name.length() - 6).replace('/', '.'))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 对项目类采用 child-first、对第三方依赖采用常规父委托。
     *
     * <p>这种边界既阻止当前 checkout 的同名类污染基线，
     * 又允许基线类解析 SLF4J 等外部签名。
     */
    private static final class ChildFirstProjectClassLoader extends URLClassLoader {

        private ChildFirstProjectClassLoader(URL jarUrl) {
            super(new URL[] {jarUrl}, ClassLoader.getSystemClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(PROJECT_PREFIX)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
            return super.loadClass(name, resolve);
        }
    }
}
