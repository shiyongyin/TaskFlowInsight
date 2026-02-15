package com.syy.taskflowinsight.spi;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * SPI 包的架构约束（不依赖 Spring/Micrometer）。
 */
class SpiArchTests {

    @Test
    @DisplayName("spi 包不得依赖 Spring/Micrometer")
    void spi_should_not_depend_on_spring_or_micrometer() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.syy.taskflowinsight.spi");

        ArchRule rule = noClasses()
            .that().resideInAPackage("com.syy.taskflowinsight.spi..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "io.micrometer.."
            );

        rule.check(classes);
    }
}

