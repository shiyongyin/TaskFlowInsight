package com.syy.taskflowinsight.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit 测试：确保 API 包不依赖 Spring
 *
 * <p>v4.0.0 架构约束：
 * <ul>
 *   <li>com.syy.taskflowinsight.api.. 不得依赖 org.springframework..</li>
 *   <li>确保 TFI 类完全解耦 Spring，可在纯 Java 环境运行</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
class ApiNoSpringDependencyTests {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.syy.taskflowinsight");

    /**
     * 规则：API 包不得依赖 Spring
     * <p>已知例外（Tech Debt for v4.1.0 refactoring）：</p>
     * <ul>
     *   <li>TfiListDiff - 遗留静态工具类，计划重构为 Provider 模式</li>
     *   <li>TfiListDiffFacade - Spring Bean facade，计划迁移到 api-spring 模块</li>
     *   <li>DiffBuilder.fromSpring() - Spring 配置便捷方法，计划提取到 api-spring</li>
     * </ul>
     */
    @Test
    void apiPackage_should_not_depend_on_spring() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.syy.taskflowinsight.api..")
            .and().haveSimpleNameNotContaining("TfiListDiff")  // Exclude both TfiListDiff and TfiListDiffFacade
            .and().haveSimpleNameNotContaining("DiffBuilder")   // Exclude DiffBuilder with fromSpring() method
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "org.springframework.boot..",
                "org.springframework.context..",
                "org.springframework.beans.."
            )
            .because("API 包必须保持 Spring-free，以支持纯 Java 环境 (TFI 类已解耦完成)");

        rule.check(CLASSES);
    }

    /**
     * 规则：TFI 类不得包含 Spring 注解
     */
    @Test
    void tfi_class_should_not_have_spring_annotations() {
        ArchRule rule = noClasses()
            .that().haveSimpleNameEndingWith("TFI")
            .should().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
            .because("TFI 类必须是纯静态工具类，不得包含 Spring 注解");

        rule.check(CLASSES);
    }

    /**
     * 规则：SPI 包不得依赖 Spring
     */
    @Test
    void spi_package_should_not_depend_on_spring() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.syy.taskflowinsight.spi..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "org.springframework.boot..",
                "org.springframework.context..",
                "org.springframework.beans.."
            )
            .because("SPI 包必须保持框架无关，可在任何环境使用");

        rule.check(CLASSES);
    }
}
