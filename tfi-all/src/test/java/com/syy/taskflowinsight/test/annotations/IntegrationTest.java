package com.syy.taskflowinsight.test.annotations;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.annotation.*;

/**
 * 集成测试注解
 * 标记集成测试，使用轻量级容器
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("integration")
@ExtendWith(SpringExtension.class)
public @interface IntegrationTest {
    /**
     * 测试分组
     */
    String[] groups() default {"integration"};
}