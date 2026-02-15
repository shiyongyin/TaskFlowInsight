package com.syy.taskflowinsight.tracking.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ValueReprFormatter基础测试")
class ValueReprFormatterTest {

    @Test
    @DisplayName("字符串双引号包围")
    void shouldWrapStringsInDoubleQuotes() {
        String result = ValueReprFormatter.format("hello");
        assertThat(result).isEqualTo("\"hello\"");
    }

    @Test  
    @DisplayName("特殊字符转义")
    void shouldEscapeSpecialChars() {
        String result = ValueReprFormatter.format("say \"hello\"");
        assertThat(result).isEqualTo("\"say \\\"hello\\\"\"");
    }

    @Test
    @DisplayName("数值格式化") 
    void shouldFormatNumbers() {
        assertThat(ValueReprFormatter.format(42)).isEqualTo("42");
        assertThat(ValueReprFormatter.format(3.14)).isEqualTo("3.14");
    }

    @Test
    @DisplayName("null值处理")
    void shouldFormatNull() {
        assertThat(ValueReprFormatter.format(null)).isEqualTo("null");
    }
}
