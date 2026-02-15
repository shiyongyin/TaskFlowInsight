package com.syy.taskflowinsight.test.annotations;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.*;

/**
 * 单元测试注解
 * 标记纯单元测试，不加载Spring容器
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("unit")
@ExtendWith(MockitoExtension.class)
public @interface UnitTest {
    /**
     * 测试分组
     */
    String[] groups() default {"fast"};
}