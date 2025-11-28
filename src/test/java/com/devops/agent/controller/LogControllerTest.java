package com.devops.agent.controller;

import com.devops.agent.model.LogSummaryResponse;
import com.devops.agent.service.LogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogController.class)
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogService logService;

    @Test
    void testGetAllLogGroups_Success() throws Exception {
        // Arrange
        List<String> logGroups = Arrays.asList(
                "/aws/lambda/test-function",
                "/aws/ecs/my-service"
        );
        when(logService.getAllLogGroups()).thenReturn(logGroups);

        // Act & Assert
        mockMvc.perform(get("/api/logs/groups")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("/aws/lambda/test-function"))
                .andExpect(jsonPath("$[1]").value("/aws/ecs/my-service"));
    }

    @Test
    void testGetAllLogGroups_Error() throws Exception {
        // Arrange
        when(logService.getAllLogGroups())
                .thenThrow(new RuntimeException("CloudWatch error"));

        // Act & Assert
        mockMvc.perform(get("/api/logs/groups")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetLogStreams_Success() throws Exception {
        // Arrange
        List<String> streams = Arrays.asList(
                "2024/01/15/stream-1",
                "2024/01/15/stream-2"
        );
        when(logService.getLogStreams(anyString())).thenReturn(streams);

        // Act & Assert
        mockMvc.perform(get("/api/logs/groups/test-group/streams")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("2024/01/15/stream-1"));
    }

    @Test
    void testGetLogStreams_NotFound() throws Exception {
        // Arrange
        when(logService.getLogStreams(anyString()))
                .thenThrow(new RuntimeException("Log group not found"));

        // Act & Assert
        mockMvc.perform(get("/api/logs/groups/non-existent/streams")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetLogSummary_Success() throws Exception {
        // Arrange
        LogSummaryResponse.LogStatistics stats = LogSummaryResponse.LogStatistics.builder()
                .errorCount(2L)
                .warningCount(3L)
                .infoCount(5L)
                .totalCount(10L)
                .build();

        LogSummaryResponse summary = LogSummaryResponse.builder()
                .logGroupName("/aws/lambda/test-function")
                .logStreamName("2024/01/15/stream-1")
                .totalEvents(10L)
                .statistics(stats)
                .build();

        when(logService.getLogSummary(anyString(), anyString(), anyInt())).thenReturn(summary);

        // Act & Assert
        mockMvc.perform(get("/api/logs/groups/test-group/streams/test-stream/summary")
                        .param("hours", "24")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logGroupName").value("/aws/lambda/test-function"))
                .andExpect(jsonPath("$.logStreamName").value("2024/01/15/stream-1"))
                .andExpect(jsonPath("$.totalEvents").value(10))
                .andExpect(jsonPath("$.statistics.errorCount").value(2))
                .andExpect(jsonPath("$.statistics.warningCount").value(3))
                .andExpect(jsonPath("$.statistics.totalCount").value(10));
    }

    @Test
    void testGetLogSummary_NotFound() throws Exception {
        // Arrange
        when(logService.getLogSummary(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Log stream not found"));

        // Act & Assert
        mockMvc.perform(get("/api/logs/groups/test-group/streams/test-stream/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetLogSummaryForGroup_Success() throws Exception {
        // Arrange
        LogSummaryResponse.LogStatistics stats = LogSummaryResponse.LogStatistics.builder()
                .errorCount(1L)
                .warningCount(2L)
                .infoCount(7L)
                .totalCount(10L)
                .build();

        LogSummaryResponse summary = LogSummaryResponse.builder()
                .logGroupName("/aws/lambda/test-function")
                .logStreamName("latest-stream")
                .totalEvents(10L)
                .statistics(stats)
                .build();

        when(logService.getLogSummaryForGroup(anyString(), any())).thenReturn(summary);

        // Act & Assert
        mockMvc.perform(get("/api/logs/groups/test-group/summary")
                        .param("hours", "12")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logGroupName").value("/aws/lambda/test-function"))
                .andExpect(jsonPath("$.totalEvents").value(10));
    }

    @Test
    void testGetLogSummaryForGroup_NotFound() throws Exception {
        // Arrange
        when(logService.getLogSummaryForGroup(anyString(), any()))
                .thenThrow(new RuntimeException("Log group not found"));

        // Act & Assert
        mockMvc.perform(get("/api/logs/groups/non-existent/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
