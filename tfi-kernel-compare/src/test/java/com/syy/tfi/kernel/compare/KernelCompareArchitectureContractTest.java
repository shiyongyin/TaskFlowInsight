package com.syy.tfi.kernel.compare;

import static org.assertj.core.api.Assertions.assertThat;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** 冻结 bridge 的最小公共面和纯 Java 边界，防止阶段切片演变为第二套框架或 SPI。 */
class KernelCompareArchitectureContractTest {

    @Test
    void publicTypesAndSignaturesMatchTheFrozenContract() throws Exception {
        Set<Class<?>> publicTypes = loadClasses().stream()
                .filter(KernelCompareArchitectureContractTest::isPubliclyReachable)
                .collect(Collectors.toSet());

        assertThat(publicTypes).containsExactlyInAnyOrder(
                KernelCompareRecorder.class,
                KernelCompareRecordPolicy.class,
                CompareRecordResult.class,
                CompareRecordStatus.class);
        assertThat(publicTypes).allSatisfy(type -> assertThat(Modifier.isFinal(type.getModifiers())).isTrue());

        assertPublicConstructor(
                KernelCompareRecorder.class,
                CompareOperations.class,
                CompareProjectionFactory.class,
                MaskingPolicy.class,
                KernelCompareRecordPolicy.class);
        assertPublicConstructor(KernelCompareRecordPolicy.class, int.class);
        assertPublicConstructor(
                CompareRecordResult.class,
                CompareRecordStatus.class,
                Optional.class,
                int.class,
                int.class);

        assertPublicMethods(KernelCompareRecorder.class, Set.of(
                "compareAndRecord(com.syy.tfi.kernel.Stage,java.lang.String,java.lang.Object,java.lang.Object)"
                        + "->com.syy.tfi.kernel.compare.CompareRecordResult",
                "record(com.syy.tfi.kernel.Stage,java.lang.String,"
                        + "com.syy.taskflowinsight.tracking.compare.CompareResult)"
                        + "->com.syy.tfi.kernel.compare.CompareRecordResult"));
        assertPublicMethods(KernelCompareRecordPolicy.class, Set.of(
                "defaults()->com.syy.tfi.kernel.compare.KernelCompareRecordPolicy",
                "maxRecordedChanges()->int",
                "equals(java.lang.Object)->boolean",
                "hashCode()->int",
                "toString()->java.lang.String"));
        assertPublicMethods(CompareRecordResult.class, Set.of(
                "status()->com.syy.tfi.kernel.compare.CompareRecordStatus",
                "compareResult()->java.util.Optional",
                "availableChanges()->int",
                "recordedChanges()->int",
                "equals(java.lang.Object)->boolean",
                "hashCode()->int",
                "toString()->java.lang.String"));

        assertThat(componentNames(KernelCompareRecordPolicy.class))
                .containsExactly("maxRecordedChanges");
        assertThat(componentNames(CompareRecordResult.class))
                .containsExactly("status", "compareResult", "availableChanges", "recordedChanges");
        assertThat(CompareRecordStatus.values()).containsExactly(
                CompareRecordStatus.SKIPPED_NO_RECORDING_CAPACITY,
                CompareRecordStatus.RECORDED_SUMMARY,
                CompareRecordStatus.RECORDED_DETAILS,
                CompareRecordStatus.RECORDED_PARTIAL_DETAILS,
                CompareRecordStatus.RECORDED_DETAIL_FAILURE,
                CompareRecordStatus.EXECUTED_NOT_RECORDED);
    }

    @Test
    void recorderKeepsImmutableStateAndMainSourcesAvoidForbiddenSurfaces() throws IOException {
        assertThat(Arrays.stream(KernelCompareRecorder.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers())))
                .allSatisfy(field -> assertThat(Modifier.isFinal(field.getModifiers())).isTrue());

        String sources = readMainSources();
        assertThat(sources)
                .doesNotContain("org.springframework")
                .doesNotContain("com.fasterxml.jackson")
                .doesNotContain("com.github.benmanes.caffeine")
                .doesNotContain("io.micrometer")
                .doesNotContain("org.aspectj")
                .doesNotContain("com.syy.taskflowinsight.tracking.compare.internal")
                .doesNotContain("java.lang.reflect")
                .doesNotContain("ThreadLocal")
                .doesNotContain("Executor")
                .doesNotContain("ServiceLoader");

        assertThat(Modifier.isFinal(ProjectionNodeDataConverter.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(ProjectionNodeDataConverter.class.getModifiers())).isFalse();
        String converter = Files.readString(repositoryRoot().resolve(
                "tfi-kernel-compare/src/main/java/com/syy/tfi/kernel/compare/ProjectionNodeDataConverter.java"));
        assertThat(converter)
                .contains("ProjectionNode")
                .doesNotContain(
                        "import com.syy.taskflowinsight.tracking.compare.FieldChange",
                        "import com.syy.taskflowinsight.tracking.path.ComparePath",
                        "import com.syy.taskflowinsight.tracking.compare.ChangeSide");
    }

    @Test
    void pomKeepsExactlyTheTwoCoreMainDependencies() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element project = factory.newDocumentBuilder()
                .parse(repositoryRoot().resolve("tfi-kernel-compare/pom.xml").toFile())
                .getDocumentElement();
        Element dependencies = directChildren(project, "dependencies").getFirst();

        List<String> mainDependencies = directChildren(dependencies, "dependency").stream()
                .filter(dependency -> !"test".equals(childText(dependency, "scope")))
                .map(dependency -> childText(dependency, "groupId")
                        + ":" + childText(dependency, "artifactId"))
                .toList();

        assertThat(mainDependencies).containsExactly("com.syy:tfi-kernel", "com.syy:tfi-compare-core");
    }

    private static void assertPublicConstructor(Class<?> type, Class<?>... parameters) {
        List<Constructor<?>> constructors = Arrays.asList(type.getConstructors());
        assertThat(constructors).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).containsExactly(parameters));
    }

    private static void assertPublicMethods(Class<?> type, Set<String> expected) {
        Set<String> actual = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(KernelCompareArchitectureContractTest::signature)
                .collect(Collectors.toSet());
        assertThat(actual).as(type.getName()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + parameters + ")->" + method.getReturnType().getName();
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<Class<?>> loadClasses() throws IOException {
        Path classes = repositoryRoot().resolve("tfi-kernel-compare/target/classes/com/syy/tfi/kernel/compare");
        ClassLoader loader = KernelCompareArchitectureContractTest.class.getClassLoader();
        try (Stream<Path> paths = Files.walk(classes)) {
            return paths.filter(path -> path.toString().endsWith(".class"))
                    .map(classes::relativize)
                    .map(Path::toString)
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .map(name -> name.replace(File.separatorChar, '.'))
                    .map(name -> "com.syy.tfi.kernel.compare." + name)
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
            throw new IllegalStateException("Cannot load bridge class " + name, failure);
        }
    }

    private static String readMainSources() throws IOException {
        Path main = repositoryRoot().resolve("tfi-kernel-compare/src/main/java");
        StringBuilder sources = new StringBuilder();
        try (Stream<Path> paths = Files.walk(main)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                sources.append(Files.readString(path));
            }
        }
        return sources.toString();
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> matches = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static String childText(Element parent, String localName) {
        return directChildren(parent, localName).stream()
                .findFirst()
                .map(Element::getTextContent)
                .map(String::trim)
                .orElse("");
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
