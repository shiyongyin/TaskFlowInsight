package com.syy.taskflowinsight.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compare 最终运行时闭包的进程级可变状态分类合同。
 *
 * <p>候选集合与显式分类双向相等，新增或删除字段都必须同步完成 owner 审核，不能靠隐藏 exclusion 假绿。</p>
 */
class CompareArtifactStaticStateContractTests {

    @Test
    void knownMutableStaticHolderTypesAreExhaustivelyClassified() throws IOException {
        Set<String> immutablePrivateExemptions = Set.of(
                "com.syy.taskflowinsight.tracking.projection.MaskingPolicy#SAFE_RULES",
                "com.syy.taskflowinsight.tracking.compare.ValueSnapshot#SCALAR_TYPE_CODES",
                "com.syy.taskflowinsight.tracking.compare.CompareSemanticFingerprint#BUILT_IN_ALGORITHMS",
                "com.syy.taskflowinsight.exporter.change.CanonicalProjectionJsonWriter#HEX",
                "com.syy.taskflowinsight.tracking.compare.FieldChange$ReferenceDetail#JSON_HEX",
                "com.syy.taskflowinsight.tracking.compare.internal.ValueSnapshotFormatter#HEX");
        Set<String> existingDebt = Set.of(
                "com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil#defaultMaxAttempts",
                "com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil#defaultBaseDelayMs",
                "com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil#globalStats",
                "com.syy.taskflowinsight.tracking.summary.CollectionSummary#instance",
                "com.syy.taskflowinsight.tracking.query.ChangeAdapters#CUSTOMIZERS",
                "com.syy.taskflowinsight.tracking.ssot.key.EntityKeyUtils#REFERENCE_ID_CACHE",
                "com.syy.taskflowinsight.tracking.format.ValueReprFormatter#dateFormatter");
        Set<String> expected = Stream.concat(
                        immutablePrivateExemptions.stream(), existingDebt.stream())
                .collect(Collectors.toUnmodifiableSet());

        Map<String, JavaField> candidates = staticStateCandidates(importBuiltJar("tfi-compare"));

        assertThat(candidates.keySet()).containsExactlyInAnyOrderElementsOf(expected);
        immutablePrivateExemptions.forEach(id -> assertThat(candidates.get(id).getModifiers())
                .as("immutable-private exemption 修饰符漂移: %s", id)
                .containsExactlyInAnyOrder(
                        JavaModifier.PRIVATE, JavaModifier.STATIC, JavaModifier.FINAL));
    }

    private static Map<String, JavaField> staticStateCandidates(JavaClasses classes) {
        Set<String> mutableHolderTypes = Set.of(
                "com.syy.taskflowinsight.concurrent.ConcurrentRetryUtil$RetryStats",
                "com.syy.taskflowinsight.tracking.format.TfiDateTimeFormatter");
        Map<String, JavaField> candidates = new LinkedHashMap<>();
        classes.stream()
                .sorted(Comparator.comparing(javaClass -> javaClass.getName()))
                .flatMap(javaClass -> javaClass.getFields().stream()
                        .sorted(Comparator.comparing(JavaField::getName)))
                .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
                .filter(field -> !field.getModifiers().contains(JavaModifier.SYNTHETIC))
                .filter(field -> isCandidate(field, mutableHolderTypes))
                .forEach(field -> candidates.put(fieldId(field), field));
        return Map.copyOf(candidates);
    }

    private static boolean isCandidate(JavaField field, Set<String> mutableHolderTypes) {
        return !field.getModifiers().contains(JavaModifier.FINAL)
                || field.getRawType().isArray()
                || field.getRawType().isAssignableTo(Map.class)
                || field.getRawType().isAssignableTo(Collection.class)
                || field.getRawType().isAssignableTo(ThreadLocal.class)
                || field.getRawType().getPackageName().equals("java.util.concurrent.atomic")
                || mutableHolderTypes.contains(field.getRawType().getName());
    }

    private static JavaClasses importBuiltJar(String artifactId) throws IOException {
        try (JarFile jar = new JarFile(findBuiltJar(artifactId).toFile())) {
            return new ClassFileImporter().importJar(jar);
        }
    }

    private static Path findBuiltJar(String artifactId) throws IOException {
        Path repositoryRoot = Path.of(System.getProperty("basedir")).toAbsolutePath()
                .normalize().getParent();
        Path target = repositoryRoot.resolve(artifactId).resolve("target");
        Properties coordinates = new Properties();
        try (var input = Files.newInputStream(target.resolve("maven-archiver/pom.properties"))) {
            coordinates.load(input);
        }
        assertThat(coordinates.getProperty("artifactId")).isEqualTo(artifactId);
        Path currentJar = target.resolve(
                coordinates.getProperty("artifactId") + "-" + coordinates.getProperty("version") + ".jar");
        assertThat(currentJar).as("本轮 Maven 构建的 %s JAR", artifactId).isRegularFile();
        return currentJar;
    }

    private static String fieldId(JavaField field) {
        return field.getOwner().getName() + "#" + field.getName();
    }
}
