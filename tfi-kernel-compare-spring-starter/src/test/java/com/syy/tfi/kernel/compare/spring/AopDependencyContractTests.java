package com.syy.tfi.kernel.compare.spring;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.Ordered;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

class AopDependencyContractTests {

    /** 标准 Boot AOP feature dependency 独有且由基础 validator 字符串探测的 package。 */
    private static final String ASPECTJ_WEAVER_PACKAGE = "org.aspectj.weaver";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    TfiKernelCompareArtifactGuardAutoConfiguration.class,
                    TfiKernelRuntimeAutoConfiguration.class,
                    TfiCompareCoreAutoConfiguration.class,
                    TfiKernelCompareAutoConfiguration.class,
                    TfiKernelCompareAopAutoConfiguration.class));

    @Test
    void defaultPathStartsWithoutAspectjMarkerOrAdvisor() {
        contextRunner.withClassLoader(new FilteredClassLoader(ASPECTJ_WEAVER_PACKAGE))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(Advisor.class)).isEmpty();
                    assertThat(context).doesNotHaveBean("tfiKernelCompareAdvisor");
                });
    }

    @Test
    void enabledAopWithoutFeatureDependencyFailsWithAopCode() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(ASPECTJ_WEAVER_PACKAGE))
                .withPropertyValues("tfi.kernel-compare.aop.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("KCS_E_1101")
                            .hasStackTraceContaining("optional feature dependency is required");
                    assertThat(stackTrace(context.getStartupFailure()))
                            .doesNotContain("NoClassDefFoundError");
                });
    }

    @Test
    void invalidIntegrationCombinationKeepsConfigurationCodePriority() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(ASPECTJ_WEAVER_PACKAGE))
                .withPropertyValues(
                        "tfi.kernel-compare.enabled=false",
                        "tfi.kernel-compare.aop.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("KCS_E_1003");
                    assertThat(stackTrace(context.getStartupFailure()))
                            .doesNotContain("KCS_E_1101");
                });
    }

    @Test
    void enabledAopWithFeatureDependencyCreatesTheFixedOrderAdvisor() {
        contextRunner.withPropertyValues("tfi.kernel-compare.aop.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("tfiKernelCompareAdvisor");
                    Advisor advisor = context.getBean("tfiKernelCompareAdvisor", Advisor.class);
                    assertThat(advisor).isInstanceOf(Ordered.class);
                    assertThat(((Ordered) advisor).getOrder())
                            .isEqualTo(TfiKernelCompareAopAutoConfiguration.ADVISOR_ORDER);
                });
    }

    @Test
    void enabledAopWithoutAutoProxyCreatorFailsInsteadOfSilentlyIgnoringAnnotations() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TfiKernelCompareArtifactGuardAutoConfiguration.class,
                        TfiKernelRuntimeAutoConfiguration.class,
                        TfiCompareCoreAutoConfiguration.class,
                        TfiKernelCompareAutoConfiguration.class,
                        TfiKernelCompareAopAutoConfiguration.class))
                .withPropertyValues("tfi.kernel-compare.aop.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("KCS_E_1101")
                            .hasStackTraceContaining("auto-proxy creator is required");
                });
    }

    @Test
    void aopFeatureDependencyIsOptionalInThePublishedPom() throws Exception {
        Path pomPath = repositoryRoot().resolve(
                "tfi-kernel-compare-spring-starter/target/flattened-pom.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(pomPath.toFile());
        NodeList dependencies = document.getElementsByTagName("dependency");
        Element aopDependency = null;
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            String artifactId = dependency.getElementsByTagName("artifactId").item(0)
                    .getTextContent().trim();
            if ("spring-boot-starter-aop".equals(artifactId)) {
                aopDependency = dependency;
                break;
            }
        }

        assertThat(aopDependency).isNotNull();
        assertThat(aopDependency.getElementsByTagName("optional").item(0).getTextContent().trim())
                .isEqualTo("true");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("tfi-kernel-compare-spring-starter"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
