package com.syy.taskflowinsight.architecture;

import com.syy.taskflowinsight.api.CompareOperations;
import com.syy.taskflowinsight.tracking.compare.CompareEngine;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * tfi-compare 最终运行时闭包的模块边界合同。
 *
 * <p>扫描范围限定为当前制品，不把 Flow/Spring/Ops 等外部 owner 计入。</p>
 */
@DisplayName("tfi-compare — 架构守护测试")
class CompareArchitectureContractTests {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        Path module = Path.of(System.getProperty("basedir"));
        classes = new ClassFileImporter().importPath(module.resolve("target/classes"));
    }

    @Test
    @DisplayName("ARCH-000: 只扫描Compare制品且Operations实现唯一")
    void importsOnlyCompareArtifactAndKeepsOneOperationsOwner() {
        List<String> implementations = classes.stream()
                .filter(javaClass -> !javaClass.isInterface())
                .filter(javaClass -> javaClass.isAssignableTo(CompareOperations.class))
                .map(JavaClass::getName)
                .toList();

        assertThat(classes).isNotEmpty();
        assertThat(implementations).containsExactly(CompareEngine.class.getName());
    }

    // ──────────────────────────────────────────────────────────────
    //  编码规范
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ARCH-001: 禁止使用 java.util.logging")
    void noJavaUtilLogging() {
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(classes);
    }

    @Test
    @DisplayName("ARCH-001B: 禁止写标准输出")
    void noStandardStreamAccess() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
    }

    @Test
    @DisplayName("ARCH-001C: 纯Compare不得依赖框架")
    void pureCompareDoesNotDependOnFrameworks() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "io.micrometer..",
                        "jakarta..",
                        "com.github.benmanes.caffeine..",
                        "com.fasterxml.jackson..",
                        "org.aspectj..")
                .because("Spring、Ops与序列化框架分别属于外部模块owner")
                .check(classes);
    }

    @Test
    @DisplayName("ARCH-001D: 外部owner包不得回流Compare")
    void externalOwnerPackagesAreAbsent() {
        assertThat(classes.stream().map(JavaClass::getPackageName))
                .noneMatch(packageName -> packageName.contains(".compare.spring")
                        || packageName.contains(".ops.compare")
                        || packageName.equals("com.syy.taskflowinsight.metrics")
                        || packageName.startsWith("com.syy.taskflowinsight.metrics."));
    }

    @Test
    @DisplayName("ARCH-001E: 请求状态不得成为static mutable事实")
    void requestStateHasNoStaticMutableFields() {
        List<String> mutableFields = classes.stream()
                .filter(javaClass -> javaClass.getPackageName()
                        .startsWith("com.syy.taskflowinsight.tracking.compare.internal"))
                .flatMap(javaClass -> javaClass.getFields().stream())
                .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
                .filter(field -> !field.getModifiers().contains(JavaModifier.SYNTHETIC))
                .filter(field -> !field.getModifiers().contains(JavaModifier.FINAL))
                .map(field -> field.getOwner().getName() + "#" + field.getName())
                .toList();

        assertThat(mutableFields).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────
    //  分层依赖
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ARCH-002: detector 包不依赖 compare.list 包")
    void detectorShouldNotDependOnListCompare() {
        noClasses()
                .that().resideInAPackage("..tracking.detector..")
                .should().dependOnClassesThat().resideInAPackage("..tracking.compare.list..")
                .because("detector 是底层差异检测层，不应依赖高层 list 比较策略")
                .check(classes);
    }

    @Test
    @DisplayName("ARCH-003: annotation 包不依赖 tracking 内部实现包（compare.list / detector / snapshot）")
    void annotationShouldNotDependOnTrackingInternals() {
        // @CustomComparator 引用 PropertyComparator 接口属于设计意图，
        // 此规则仅约束 annotation 不直接依赖内部实现包
        noClasses()
                .that().resideInAPackage("..annotation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..tracking.compare.list..",
                        "..tracking.detector..",
                        "..tracking.snapshot.."
                )
                .because("annotation 是元数据层，不应依赖跟踪内部实现")
                .check(classes);
    }

    @Test
    @DisplayName("ARCH-004: 模型包不依赖 Spring 框架")
    void modelShouldNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("..tracking.model..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("model 包应保持 POJO，不依赖 Spring")
                .check(classes);
    }

    // ──────────────────────────────────────────────────────────────
    //  命名规范
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ARCH-005: Strategy 实现类名以 Strategy 结尾")
    void strategyImplementations_shouldEndWithStrategy() {
        classes()
                .that().implement(com.syy.taskflowinsight.tracking.compare.CompareStrategy.class)
                .should().haveSimpleNameEndingWith("Strategy")
                .because("策略实现应遵循命名约定")
                .check(classes);
    }

    @Test
    @DisplayName("ARCH-006: ListCompareStrategy 实现类名以 Strategy 结尾")
    void listStrategyImplementations_shouldEndWithStrategy() {
        classes()
                .that().implement(com.syy.taskflowinsight.tracking.compare.list.ListCompareStrategy.class)
                .should().haveSimpleNameEndingWith("Strategy")
                .because("列表策略实现应遵循命名约定")
                .check(classes);
    }

    // ──────────────────────────────────────────────────────────────
    //  底层洁净度（snapshot/path 不应反向依赖高层）
    //
    //  说明：detector↔compare 与 detector↔path 存在“领域固有”的相互递归
    //  （嵌套结构对比、变更排序），属于有意为之的闭环，详见对应包的 package-info，
    //  故此处不引入全局 beFreeOfCycles 规则（会误伤这些固有环）；
    //  转而锁定“最底层 snapshot 保持洁净、path 不沾染高层 compare”，
    //  防止闭环向更多包扩散。
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ARCH-007: snapshot 包不依赖 compare/detector（保持最底层洁净）")
    void snapshotShouldNotDependOnCompareOrDetector() {
        noClasses()
                .that().resideInAPackage("..tracking.snapshot..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..tracking.compare..",
                        "..tracking.detector..")
                .because("snapshot 是最底层的状态捕获层，必须可被 detector/compare 复用而不反向依赖")
                .check(classes);
    }

    @Test
    @DisplayName("ARCH-008: path 仅可依赖 compare 的 bounded ValueSnapshot")
    void pathShouldNotDependOnCompare() {
        // canonical动态地址必须复用同一ValueSnapshot identity wire；除此之外仍禁止path反向依赖比较策略。
        DescribedPredicate<JavaClass> forbiddenCompareDependency = resideInAPackage("..tracking.compare..")
                .and(DescribedPredicate.not(equivalentTo(ValueSnapshot.class)));
        noClasses()
                .that().resideInAPackage("..tracking.path..")
                .should().dependOnClassesThat(forbiddenCompareDependency)
                .because("path只共享bounded ValueSnapshot值合同，不应依赖其他compare策略或服务")
                .check(classes);
    }

    // ──────────────────────────────────────────────────────────────
    //  收敛回归守护（防止已删除的并行实现“复活”）
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ARCH-009: snapshot 包不得再出现 *Optimized 并行实现（收敛回归守护）")
    void snapshotShouldNotReintroduceOptimizedVariant() {
        // canonical CompareEngine 已唯一拥有对象图捕获，snapshot 包不能恢复并行执行图。
        noClasses()
                .that().resideInAPackage("..tracking.snapshot..")
                .should().haveSimpleNameEndingWith("Optimized")
                .because("对象图捕获已统一到 canonical engine，不应再出现并行实现")
                .check(classes);
    }

    @Test
    @DisplayName("ARCH-010: 不得再出现 DateCompareStrategy（已统一到 EnhancedDateCompareStrategy）")
    void shouldNotReintroduceDeprecatedDateStrategy() {
        noClasses()
                .that().resideInAPackage("..tracking.compare..")
                .should().haveSimpleName("DateCompareStrategy")
                .because("日期比较已统一为 EnhancedDateCompareStrategy（0ms 精确语义），不应恢复带示例容差的旧实现")
                .check(classes);
    }

    @Test
    @DisplayName("ARCH-011: snapshot 包不得再出现 *SnapshotStrategy 策略层（已删除的死代码）")
    void snapshotShouldNotReintroduceStrategyLayer() {
        // SnapshotStrategy / ShallowSnapshotStrategy / DeepSnapshotStrategy 为未使用的死代码，已删除。
        noClasses()
                .that().resideInAPackage("..tracking.snapshot..")
                .should().haveSimpleNameEndingWith("SnapshotStrategy")
                .because("canonical engine 已拥有捕获策略，不应再引入未使用的 snapshot 策略层")
                .check(classes);
    }
}
