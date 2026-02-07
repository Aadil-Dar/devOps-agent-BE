package com.devops.agent.controller;

import com.devops.agent.model.DevOpsHealthCheckRequest;
import com.devops.agent.model.DevOpsHealthCheckResponse;
import com.devops.agent.service.DevOpsInsightService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevOpsInsightController.class)
class DevOpsInsightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DevOpsInsightService devOpsInsightService;

    @Test
    @WithMockUser
    void healthCheck_withValidRequest_shouldReturnOk() throws Exception {
        // Given
        DevOpsHealthCheckRequest request = new DevOpsHealthCheckRequest("test-project");

        DevOpsHealthCheckResponse.PredictionDetails predictions =
                DevOpsHealthCheckResponse.PredictionDetails.builder()
                        .timeframe("within 12-24 hours")
                        .likelihood(0.3)
                        .rootCause("No critical issues")
                        .build();

        DevOpsHealthCheckResponse response = DevOpsHealthCheckResponse.builder()
                .riskLevel("LOW")
                .summary("System is healthy")
                .recommendations(List.of("Continue monitoring", "Review logs", "Optimize performance"))
                .predictions(predictions)
                .logCount(10)
                .errorCount(0)
                .warningCount(2)
                .metricTrends(List.of())
                .timestamp(System.currentTimeMillis())
                .build();

        when(devOpsInsightService.performHealthCheck(anyString())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/devops/healthCheck")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.summary").value("System is healthy"))
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.warningCount").value(2))
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.recommendations.length()").value(3))
                .andExpect(jsonPath("$.predictions.likelihood").value(0.3));
    }

    @Test
    @WithMockUser
    void healthCheck_withInvalidProject_shouldReturnBadRequest() throws Exception {
        // Given
        DevOpsHealthCheckRequest request = new DevOpsHealthCheckRequest("invalid-project");

        when(devOpsInsightService.performHealthCheck(anyString()))
                .thenThrow(new IllegalArgumentException("Project not found"));

        // When & Then
        mockMvc.perform(post("/api/devops/healthCheck")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthCheck_withoutAuthentication_shouldReturnUnauthorized() throws Exception {
        // Given
        DevOpsHealthCheckRequest request = new DevOpsHealthCheckRequest("test-project");

        // When & Then
        mockMvc.perform(post("/api/devops/healthCheck")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void healthCheck_whenServiceThrowsException_shouldReturnInternalServerError() throws Exception {
        // Given
        DevOpsHealthCheckRequest request = new DevOpsHealthCheckRequest("test-project");

        when(devOpsInsightService.performHealthCheck(anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        mockMvc.perform(post("/api/devops/healthCheck")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser
    void healthCheck_withHighRisk_shouldReturnCorrectResponse() throws Exception {
        // Given
        DevOpsHealthCheckRequest request = new DevOpsHealthCheckRequest("critical-project");

        DevOpsHealthCheckResponse.PredictionDetails predictions =
                DevOpsHealthCheckResponse.PredictionDetails.builder()
                        .timeframe("within 1-2 hours")
                        .likelihood(0.9)
                        .rootCause("Database connection pool exhausted")
                        .build();

        DevOpsHealthCheckResponse.MetricTrend cpuTrend =
                DevOpsHealthCheckResponse.MetricTrend.builder()
                        .serviceName("api-service")
                        .metricName("CPUUtilization")
                        .currentValue(92.5)
                        .averageValue(85.0)
                        .trend("INCREASING")
                        .unit("Percent")
                        .build();

        DevOpsHealthCheckResponse response = DevOpsHealthCheckResponse.builder()
                .riskLevel("CRITICAL")
                .summary("Critical issues detected. Service may fail soon.")
                .recommendations(List.of(
                        "Increase database connection pool size",
                        "Scale up application instances",
                        "Enable circuit breaker"
                ))
                .predictions(predictions)
                .logCount(150)
                .errorCount(45)
                .warningCount(30)
                .metricTrends(List.of(cpuTrend))
                .timestamp(System.currentTimeMillis())
                .build();

        when(devOpsInsightService.performHealthCheck(anyString())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/devops/healthCheck")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.errorCount").value(45))
                .andExpect(jsonPath("$.warningCount").value(30))
                .andExpect(jsonPath("$.predictions.likelihood").value(0.9))
                .andExpect(jsonPath("$.predictions.timeframe").value("within 1-2 hours"))
                .andExpect(jsonPath("$.metricTrends").isArray())
                .andExpect(jsonPath("$.metricTrends[0].trend").value("INCREASING"))
                .andExpect(jsonPath("$.metricTrends[0].currentValue").value(92.5));
    }
}
