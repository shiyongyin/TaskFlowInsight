package com.syy.tfi.kernel.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证样例可以在无 Spring 环境下形成一条完整终态 JSON。 */
class PlainJavaKernelExampleTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void publicApiExampleProducesOneFinalCanonicalSession() throws Exception {
        List<String> output = new ArrayList<>();

        PlainJavaKernelExample.run(output::add);

        assertThat(output).singleElement();
        var session = JSON.readTree(output.get(0));
        assertThat(session.path("schema").asText()).isEqualTo("tfi-flow/1");
        assertThat(session.path("name").asText()).isEqualTo("order.submit");
        assertThat(session.path("status").asText()).isEqualTo("OK");
        assertThat(session.path("root").path("children")).hasSize(1);
        assertThat(session.path("root").path("records")).hasSize(2);
    }
}
