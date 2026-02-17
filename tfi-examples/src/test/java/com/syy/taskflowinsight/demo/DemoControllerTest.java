package com.syy.taskflowinsight.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link DemoController} 端点集成测试。
 *
 * <p>使用 {@code @SpringBootTest + MockMvc} 验证 REST 端点的
 * HTTP 状态码和 JSON 响应结构。</p>
 *
 * @since 3.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/demo/hello/{name} 返回 200 + 正确 message")
    void testHelloEndpoint() throws Exception {
        mockMvc.perform(get("/api/demo/hello/World"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, World"))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    @DisplayName("POST /api/demo/process 返回 200 + processed 状态")
    void testProcessEndpoint() throws Exception {
        mockMvc.perform(post("/api/demo/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\": \"test-payload\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"))
                .andExpect(jsonPath("$.data").value("test-payload"));
    }

    @Test
    @DisplayName("POST /api/demo/async 返回 200 + submitted 状态")
    void testAsyncEndpoint() throws Exception {
        mockMvc.perform(post("/api/demo/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\": \"async-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.asyncStarted").value(true));
    }

    @Test
    @DisplayName("POST /api/demo/async-comparison 返回 200 + comparison_started")
    void testAsyncComparisonEndpoint() throws Exception {
        mockMvc.perform(post("/api/demo/async-comparison")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\": \"comparison-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("comparison_started"));
    }
}
