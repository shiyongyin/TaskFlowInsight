package com.syy.tfi.kernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.syy.tfi.kernel.KernelConfig;
import com.syy.tfi.kernel.KernelRuntime;
import com.syy.tfi.kernel.Stage;
import com.syy.tfi.kernel.Tfi;
import com.syy.tfi.kernel.context.ContextHandle;
import com.syy.tfi.kernel.model.FlowSession;
import com.syy.tfi.kernel.model.Record;
import com.syy.tfi.kernel.model.StageNode;
import com.syy.tfi.kernel.spi.FlowSink;
import com.syy.tfi.kernel.spi.IdGenerator;
import com.syy.tfi.kernel.spi.KernelClock;
import com.syy.tfi.kernel.spi.Sampler;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 固化内核的公共面和运行时身份，防止后续便利能力绕过架构 Gate。
 */
class KernelArchitectureContractTest {

    @Test
    void publicTypesAndMethodsMatchTheFrozenContract() throws Exception {
        Set<Class<?>> publicTypes = loadClasses().stream()
                .filter(KernelArchitectureContractTest::isPubliclyReachable)
                .collect(Collectors.toSet());
        assertThat(publicTypes).containsExactlyInAnyOrder(
                Tfi.class, KernelRuntime.class, Stage.class, KernelConfig.class, ContextHandle.class,
                FlowSession.class, StageNode.class, Record.class,
                com.syy.tfi.kernel.model.FlowStatus.class,
                com.syy.tfi.kernel.model.RecordType.class,
                FlowSink.class, Sampler.class, IdGenerator.class, KernelClock.class);

        assertPublicMethods(Tfi.class, Set.of(
                "begin(java.lang.String)->com.syy.tfi.kernel.Stage",
                "stage(java.lang.String)->com.syy.tfi.kernel.Stage",
                "stage(java.lang.String,java.lang.Runnable)->void",
                "call(java.lang.String,java.util.function.Supplier)->java.lang.Object",
                "message(java.lang.String)->void",
                "change(java.lang.String,java.lang.Object,java.lang.Object)->void",
                "error(java.lang.String)->void",
                "error(java.lang.String,java.lang.Throwable)->void",
                "capture()->com.syy.tfi.kernel.context.ContextHandle",
                "clear()->void",
                "setEnabled(boolean)->void",
                "isEnabled()->boolean",
                "toJson(com.syy.tfi.kernel.model.FlowSession)->java.lang.String",
                "currentToJson()->java.lang.String",
                "currentToConsole()->java.lang.String",
                "configure(com.syy.tfi.kernel.KernelConfig)->void"));
        assertPublicMethods(KernelRuntime.class, Set.of(
                "create(com.syy.tfi.kernel.KernelConfig)->com.syy.tfi.kernel.KernelRuntime",
                "begin(java.lang.String)->com.syy.tfi.kernel.Stage",
                "stage(java.lang.String)->com.syy.tfi.kernel.Stage",
                "stage(java.lang.String,java.lang.Runnable)->void",
                "call(java.lang.String,java.util.function.Supplier)->java.lang.Object",
                "message(java.lang.String)->void",
                "change(java.lang.String,java.lang.Object,java.lang.Object)->void",
                "error(java.lang.String)->void",
                "error(java.lang.String,java.lang.Throwable)->void",
                "capture()->com.syy.tfi.kernel.context.ContextHandle",
                "clear()->void",
                "setEnabled(boolean)->void",
                "isEnabled()->boolean",
                "currentToJson()->java.lang.String",
                "currentToConsole()->java.lang.String",
                "close()->void"));
        assertPublicMethods(Stage.class, Set.of(
                "attr(java.lang.String,java.lang.Object)->com.syy.tfi.kernel.Stage",
                "message(java.lang.String)->void",
                "change(java.lang.String,java.lang.Object,java.lang.Object)->void",
                "error(java.lang.String)->void",
                "error(java.lang.String,java.lang.Throwable)->void",
                "record(com.syy.tfi.kernel.model.RecordType,java.lang.String,java.lang.String,java.util.Map)->boolean",
                "remainingEncodedBytes()->int",
                "close()->void"));
        assertPublicMethods(ContextHandle.class, Set.of(
                "wrap(java.lang.Runnable)->java.lang.Runnable",
                "wrap(java.util.concurrent.Callable)->java.util.concurrent.Callable"));
        assertPublicMethods(FlowSink.class, Set.of(
                "accept(com.syy.tfi.kernel.model.FlowSession)->void"));
        assertPublicMethods(Sampler.class, Set.of(
                "shouldRecord(java.lang.String)->boolean",
                "always()->com.syy.tfi.kernel.spi.Sampler",
                "rateLimited(int,com.syy.tfi.kernel.spi.KernelClock)->com.syy.tfi.kernel.spi.Sampler"));
        assertPublicMethods(IdGenerator.class, Set.of(
                "nextId()->java.lang.String",
                "ulid(com.syy.tfi.kernel.spi.KernelClock)->com.syy.tfi.kernel.spi.IdGenerator"));
        assertPublicMethods(KernelClock.class, Set.of(
                "wallTimeMillis()->long",
                "monotonicNanos()->long",
                "system()->com.syy.tfi.kernel.spi.KernelClock"));
        assertPublicMethods(FlowSession.class, Set.of(
                "sessionId()->java.lang.String", "parentSessionId()->java.lang.String",
                "name()->java.lang.String", "attrs()->java.util.Map",
                "status()->com.syy.tfi.kernel.model.FlowStatus", "startMs()->long", "durMs()->long",
                "truncated()->boolean", "incompleteReasons()->java.util.List",
                "root()->com.syy.tfi.kernel.model.StageNode"));
        assertPublicMethods(StageNode.class, Set.of(
                "name()->java.lang.String", "status()->com.syy.tfi.kernel.model.FlowStatus",
                "startMs()->long", "durMs()->long", "attrs()->java.util.Map",
                "records()->java.util.List", "children()->java.util.List"));
        assertPublicMethods(Record.class, Set.of(
                "type()->com.syy.tfi.kernel.model.RecordType", "code()->java.lang.String",
                "text()->java.lang.String", "data()->java.util.Map", "atMs()->long"));
        assertPublicMethods(KernelConfig.class, Set.of(
                "enabled()->boolean", "sinks()->java.util.List",
                "sampler()->com.syy.tfi.kernel.spi.Sampler",
                "idGenerator()->com.syy.tfi.kernel.spi.IdGenerator",
                "clock()->com.syy.tfi.kernel.spi.KernelClock", "maxStages()->int",
                "maxSessionEncodedBytes()->int", "maxRecordEncodedBytes()->int", "maxAttrs()->int",
                "defaults()->com.syy.tfi.kernel.KernelConfig", "equals(java.lang.Object)->boolean",
                "hashCode()->int", "toString()->java.lang.String"));
        assertThat(Arrays.stream(KernelConfig.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("enabled", "sinks", "sampler", "idGenerator", "clock",
                        "maxStages", "maxSessionEncodedBytes", "maxRecordEncodedBytes", "maxAttrs");
        assertThat(KernelConfig.class.getConstructors()).hasSize(1);
    }

    @Test
    void runtimeUsesOneThreadLocalAndNoAutonomousLifecycleMechanism() throws Exception {
        List<Class<?>> classes = loadClasses();
        List<Field> threadLocals = classes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> ThreadLocal.class.isAssignableFrom(field.getType()))
                .toList();
        assertThat(threadLocals).singleElement().satisfies(field ->
                assertThat(field.getDeclaringClass()).isEqualTo(KernelRuntime.class));

        String compactSources = readMainSources().replaceAll("\\s+", "");
        assertThat(compactSources)
                .doesNotContain("Executors.")
                .doesNotContain("ScheduledExecutor")
                .doesNotContain("newThread(")
                .doesNotContain("newjava.lang.Thread(")
                .doesNotContain("Thread.ofPlatform(")
                .doesNotContain("Thread.ofVirtual(")
                .doesNotContain("Thread.startVirtualThread(")
                .doesNotContain("newTimer(")
                .doesNotContain("ForkJoinPool.")
                .doesNotContain("CompletableFuture.runAsync(")
                .doesNotContain("CompletableFuture.supplyAsync(")
                .doesNotContain("addShutdownHook")
                .doesNotContain("ServiceLoader")
                .doesNotContain("com.syy.taskflowinsight");

        Path main = repositoryRoot().resolve("tfi-kernel/src/main/java/com/syy/tfi/kernel");
        String tfiSource = Files.readString(main.resolve("Tfi.java"));
        assertThat(tfiSource)
                .containsOnlyOnce(".reconfigureDefault(")
                .doesNotContain("new SessionState(")
                .doesNotContain("new ThreadLocal");
        try (Stream<Path> paths = Files.walk(main)) {
            assertThat(paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("Tfi.java"))
                    .map(KernelArchitectureContractTest::readString)
                    .filter(source -> source.contains(".reconfigureDefault(")))
                    .isEmpty();
        }
    }

    private static void assertPublicMethods(Class<?> type, Set<String> expected) {
        Set<String> actual = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(KernelArchitectureContractTest::signature)
                .collect(Collectors.toSet());
        assertThat(actual).as(type.getName()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + parameters + ")->" + method.getReturnType().getName();
    }

    private static List<Class<?>> loadClasses() throws Exception {
        Path classes = repositoryRoot().resolve("tfi-kernel/target/classes/com/syy/tfi/kernel");
        ClassLoader loader = KernelArchitectureContractTest.class.getClassLoader();
        try (Stream<Path> paths = Files.walk(classes)) {
            return paths.filter(path -> path.toString().endsWith(".class"))
                    .map(classes::relativize)
                    .map(Path::toString)
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .map(name -> name.replace(File.separatorChar, '.'))
                    .map(name -> "com.syy.tfi.kernel." + name)
                    .<Class<?>>map(name -> load(name, loader))
                    .toList();
        }
    }

    private static boolean isPubliclyReachable(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getEnclosingClass()) {
            if (!Modifier.isPublic(current.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Cannot load kernel class " + name, failure);
        }
    }

    private static String readMainSources() throws IOException {
        Path main = repositoryRoot().resolve("tfi-kernel/src/main/java");
        StringBuilder sources = new StringBuilder();
        try (Stream<Path> paths = Files.walk(main)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                sources.append(Files.readString(path));
            }
        }
        return sources.toString();
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read source " + path, failure);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("tfi-flow-core"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return current;
    }
}
