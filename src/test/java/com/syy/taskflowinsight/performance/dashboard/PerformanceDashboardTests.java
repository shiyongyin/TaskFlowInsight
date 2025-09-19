package com.syy.taskflowinsight.performance.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PerformanceDashboard.class)
class PerformanceDashboardTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Overview returns UNKNOWN status when monitor not present")
    void overview_unknown_whenMonitorMissing() throws Exception {
        mockMvc.perform(get("/api/performance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("UNKNOWN")))
            .andExpect(jsonPath("$.metrics_summary").exists())
            .andExpect(jsonPath("$.alerts_summary").exists())
            .andExpect(jsonPath("$.system_health").exists());
    }

    @Test
    @DisplayName("Report full aggregates subreports with monitor missing")
    void report_full_withMonitorMissing() throws Exception {
        mockMvc.perform(get("/api/performance/report/full"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overview").exists())
            .andExpect(jsonPath("$.realtime.error", is("Monitor not available")))
            .andExpect(jsonPath("$.benchmark.message", is("No benchmark report available")))
            .andExpect(jsonPath("$.alerts").exists());
    }

    @Test
    @DisplayName("History returns error when monitor not present")
    void history_error_whenMonitorMissing() throws Exception {
        mockMvc.perform(get("/api/performance/history/all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error", is("Monitor not available")));
    }

    @Test
    @DisplayName("Benchmark endpoint returns error when runner missing")
    void benchmark_error_whenRunnerMissing() throws Exception {
        mockMvc.perform(post("/api/performance/benchmark/all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error", is("Benchmark runner not available")));
    }

    @Test
    @DisplayName("SLA configure returns error when monitor missing")
    void sla_error_whenMonitorMissing() throws Exception {
        mockMvc.perform(post("/api/performance/sla/snapshot"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error", is("Monitor not available")));
    }

    @Test
    @DisplayName("Clear alerts returns error when monitor missing")
    void clearAlerts_error_whenMonitorMissing() throws Exception {
        mockMvc.perform(delete("/api/performance/alerts/all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error", is("Monitor not available")));
    }
}

