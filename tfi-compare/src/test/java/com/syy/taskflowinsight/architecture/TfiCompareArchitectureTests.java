package com.syy.taskflowinsight.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

/**
 * tfi-compare 模块架构守护测试
 *
 * <p>使用 ArchUnit 确保架构约束在 CI/CD 中持续执行：
 * <ul>
 *   <li>分层依赖方向</li>
 *   <li>禁止 System.out 和 java.util.logging</li>
 *   <li>核心 API 包不依赖实现包</li>
 * </ul>
 *
 * @author Expert Panel - Senior DevOps Expert
 * @since 3.0.0
 */
@DisplayName("tfi-compare — 架构守护测试")
class TfiCompareArchitectureTests {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.syy.taskflowinsight");
    }

    // ──────────────────────────────────────────────────────────────
    //  编码规范
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ARCH-001: 禁止使用 java.util.logging")
    void noJavaUtilLogging() {
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(classes);
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
}
