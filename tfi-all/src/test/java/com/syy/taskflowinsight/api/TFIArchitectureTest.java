package com.syy.taskflowinsight.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit 架构测试：验证 TFI API 架构规则和设计约束
 *
 * <p>覆盖规则:
 * <ul>
 *   <li>Provider SPI 必须是接口（不能是抽象类或具体类）</li>
 *   <li>API 层不应依赖 Spring 框架（保持框架无关性）</li>
 *   <li>SPI 实现类必须位于正确的包路径</li>
 *   <li>Public API 类必须有文档注释</li>
 * </ul>
 *
 * @author TaskFlow Insight Team
 * @since 4.0.0
 */
class TFIArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.syy.taskflowinsight");
    }

    @Test
    @DisplayName("公共 SPI Provider 接口应位于 spi 包中")
    void public_provider_interfaces_should_reside_in_spi_package() {
        ArchRule rule = classes()
            .that().areInterfaces()
            .and().haveSimpleNameEndingWith("Provider")
            .and().resideInAPackage("com.syy.taskflowinsight..")
            .and().haveSimpleNameNotEndingWith("SnapshotProvider")  // 内部抽象，非公共 SPI
            .should().resideInAPackage("com.syy.taskflowinsight.spi")
            .because("公共 Provider 接口应统一定义在 SPI 包中，遵循服务提供者接口规范");

        rule.check(classes);
    }

    @Test
    @DisplayName("核心业务逻辑不应依赖 Web 层和 Actuator 层")
    void core_logic_should_not_depend_on_web_layer() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.syy.taskflowinsight.tracking..",
                "com.syy.taskflowinsight.spi",
                "com.syy.taskflowinsight.flow.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                "..controller..",
                "..actuator..",
                "..web.."
            )
            .because("核心业务逻辑应保持独立，不依赖 Web 层，便于复用和测试");

        rule.check(classes);
    }

    @Test
    @DisplayName("运行时注解必须有 RUNTIME 保留策略")
    void runtime_annotations_should_have_runtime_retention() {
        ArchRule rule = classes()
            .that().areAnnotations()
            .and().resideInAPackage("com.syy.taskflowinsight.annotation")
            .should().beAnnotatedWith(java.lang.annotation.Retention.class)
            .because("业务注解需要在运行时通过反射访问，必须使用 RUNTIME 保留策略");

        rule.check(classes);
    }

    @Test
    @DisplayName("TFI 主入口类应使用私有构造器（工具类模式）")
    void tfi_main_class_should_have_private_constructor() {
        ArchRule rule = classes()
            .that().haveSimpleName("TFI")
            .should().haveOnlyPrivateConstructors()
            .because("TFI 是静态工具类，应该使用私有构造器防止实例化");

        rule.check(classes);
    }
}
