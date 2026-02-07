package com.devops.agent.service;

import com.devops.agent.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevOpsInsightServiceTest {

    @Mock
    private ProjectConfigurationService projectConfigurationService;

    @Mock
    private CloudWatchLogsClient cloudWatchLogsClient;

    @Mock
    private CloudWatchClient cloudWatchClient;

    @Mock
    private DynamoDbTable<LogSummary> logSummaryTable;

    @Mock
    private DynamoDbTable<MetricSnapshot> metricSnapshotTable;

    @Mock
    private DynamoDbTable<PredictionResult> predictionResultTable;

    @Mock
    private WebClient ollamaWebClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;

    private DevOpsInsightService devOpsInsightService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        devOpsInsightService = new DevOpsInsightService(
                projectConfigurationService,
                cloudWatchLogsClient,
                cloudWatchClient,
                logSummaryTable,
                metricSnapshotTable,
                predictionResultTable,
                ollamaWebClient,
                objectMapper
        );
    }

    @Test
    void performHealthCheck_whenProjectNotFound_shouldThrowException() {
        // Given
        String projectId = "non-existent";
        when(projectConfigurationService.getConfiguration(projectId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> devOpsInsightService.performHealthCheck(projectId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void performHealthCheck_whenProjectDisabled_shouldThrowException() {
        // Given
        String projectId = "disabled-project";
        ProjectConfiguration project = ProjectConfiguration.builder()
                .projectId(projectId)
                .enabled(false)
                .build();

        when(projectConfigurationService.getConfiguration(projectId))
                .thenReturn(Optional.of(project));

        // When & Then
        assertThatThrownBy(() -> devOpsInsightService.performHealthCheck(projectId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project is disabled");
    }

    @Test
    void performHealthCheck_withNoErrors_shouldReturnLowRisk() throws Exception {
        // Given
        String projectId = "test-project";
        ProjectConfiguration project = ProjectConfiguration.builder()
                .projectId(projectId)
                .projectName("Test Project")
                .enabled(true)
                .awsRegion("us-east-1")
                .logGroupNames(List.of("/aws/ecs/prod/test-service"))
                .serviceNames(List.of("test-service"))
                .lastProcessedTimestamp(System.currentTimeMillis() - 3600000) // 1 hour ago
                .build();

        when(projectConfigurationService.getConfiguration(projectId))
                .thenReturn(Optional.of(project));

        // Mock CloudWatch Logs - no errors
        FilterLogEventsResponse logsResponse = FilterLogEventsResponse.builder()
                .events(List.of())
                .build();
        when(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
                .thenReturn(logsResponse);

        // Mock CloudWatch Metrics
        MetricDataResult metricResult = MetricDataResult.builder()
                .values(List.of(30.0, 35.0, 32.0))
                .timestamps(List.of(
                        Instant.now().minusSeconds(600),
                        Instant.now().minusSeconds(300),
                        Instant.now()
                ))
                .build();

        GetMetricDataResponse metricsResponse = GetMetricDataResponse.builder()
                .metricDataResults(List.of(metricResult))
                .build();
        when(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class)))
                .thenReturn(metricsResponse);

        // Mock Ollama AI response
        String aiResponse = """
                {
                  "rootCause": "No critical issues detected",
                  "riskLevel": "LOW",
                  "summary": "System is operating normally with no errors detected.",
                  "recommendations": ["Continue monitoring", "Maintain current configuration", "Review logs periodically"]
                }
                """;

        OllamaGenerateResponse ollamaResponse = new OllamaGenerateResponse(aiResponse, false, null, null);

        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OllamaGenerateResponse.class))
                .thenReturn(Mono.just(ollamaResponse));

        // When
        DevOpsHealthCheckResponse response = devOpsInsightService.performHealthCheck(projectId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRiskLevel()).isEqualTo("LOW");
        assertThat(response.getErrorCount()).isZero();
        assertThat(response.getWarningCount()).isZero();
        assertThat(response.getRecommendations()).hasSize(3);

        // Verify interactions
        verify(logSummaryTable, never()).putItem(any());
        verify(predictionResultTable, times(1)).putItem(any());
        verify(projectConfigurationService, times(1)).updateConfiguration(eq(projectId), any());
    }

    @Test
    void performHealthCheck_withErrors_shouldReturnHighRisk() throws Exception {
        // Given
        String projectId = "test-project";
        ProjectConfiguration project = ProjectConfiguration.builder()
                .projectId(projectId)
                .projectName("Test Project")
                .enabled(true)
                .awsRegion("us-east-1")
                .logGroupNames(List.of("/aws/ecs/prod/test-service"))
                .serviceNames(List.of("test-service"))
                .lastProcessedTimestamp(System.currentTimeMillis() - 3600000)
                .build();

        when(projectConfigurationService.getConfiguration(projectId))
                .thenReturn(Optional.of(project));

        // Mock CloudWatch Logs with errors
        FilteredLogEvent errorEvent1 = FilteredLogEvent.builder()
                .timestamp(System.currentTimeMillis())
                .message("ERROR: NullPointerException in OrderService at line 123")
                .logStreamName("/aws/ecs/prod/test-service/task1")
                .build();

        FilteredLogEvent errorEvent2 = FilteredLogEvent.builder()
                .timestamp(System.currentTimeMillis() + 1000)
                .message("ERROR: NullPointerException in OrderService at line 123")
                .logStreamName("/aws/ecs/prod/test-service/task2")
                .build();

        FilterLogEventsResponse logsResponse = FilterLogEventsResponse.builder()
                .events(List.of(errorEvent1, errorEvent2))
                .build();
        when(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
                .thenReturn(logsResponse);

        // Mock CloudWatch Metrics with high CPU
        MetricDataResult metricResult = MetricDataResult.builder()
                .values(List.of(85.0, 87.0, 90.0))
                .timestamps(List.of(
                        Instant.now().minusSeconds(600),
                        Instant.now().minusSeconds(300),
                        Instant.now()
                ))
                .build();

        GetMetricDataResponse metricsResponse = GetMetricDataResponse.builder()
                .metricDataResults(List.of(metricResult))
                .build();
        when(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class)))
                .thenReturn(metricsResponse);

        // Mock Ollama AI response with high risk
        String aiResponse = """
                {
                  "rootCause": "NullPointerException recurring in OrderService indicating code defect",
                  "riskLevel": "HIGH",
                  "summary": "Critical errors detected with high CPU usage. Service may fail soon.",
                  "recommendations": ["Fix NullPointerException in OrderService", "Scale up resources", "Enable circuit breaker"]
                }
                """;

        OllamaGenerateResponse ollamaResponse = new OllamaGenerateResponse(aiResponse, false, null, null);

        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OllamaGenerateResponse.class))
                .thenReturn(Mono.just(ollamaResponse));

        // When
        DevOpsHealthCheckResponse response = devOpsInsightService.performHealthCheck(projectId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRiskLevel()).isEqualTo("HIGH");
        assertThat(response.getErrorCount()).isEqualTo(2);
        assertThat(response.getLogCount()).isEqualTo(2);
        assertThat(response.getRecommendations()).hasSize(3);
        assertThat(response.getPredictions()).isNotNull();
        assertThat(response.getPredictions().getLikelihood()).isGreaterThan(0.5);

        // Verify log summaries were saved
        ArgumentCaptor<LogSummary> logSummaryCaptor = ArgumentCaptor.forClass(LogSummary.class);
        verify(logSummaryTable, atLeastOnce()).putItem(logSummaryCaptor.capture());

        LogSummary savedLogSummary = logSummaryCaptor.getValue();
        assertThat(savedLogSummary.getProjectId()).isEqualTo(projectId);
        assertThat(savedLogSummary.getSeverity()).isEqualTo("ERROR");
        assertThat(savedLogSummary.getOccurrences()).isEqualTo(2);
    }

    @Test
    void performHealthCheck_whenOllamaFails_shouldReturnFallbackAnalysis() throws Exception {
        // Given
        String projectId = "test-project";
        ProjectConfiguration project = ProjectConfiguration.builder()
                .projectId(projectId)
                .enabled(true)
                .awsRegion("us-east-1")
                .logGroupNames(List.of("/aws/ecs/prod/test-service"))
                .serviceNames(List.of("test-service"))
                .lastProcessedTimestamp(System.currentTimeMillis() - 3600000)
                .build();

        when(projectConfigurationService.getConfiguration(projectId))
                .thenReturn(Optional.of(project));

        // Mock CloudWatch responses
        when(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
                .thenReturn(FilterLogEventsResponse.builder().events(List.of()).build());

        when(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class)))
                .thenReturn(GetMetricDataResponse.builder()
                        .metricDataResults(List.of())
                        .build());

        // Mock Ollama failure
        when(ollamaWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OllamaGenerateResponse.class))
                .thenReturn(Mono.error(new RuntimeException("Ollama service unavailable")));

        // When
        DevOpsHealthCheckResponse response = devOpsInsightService.performHealthCheck(projectId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(response.getSummary()).contains("Unable to perform AI analysis");
        assertThat(response.getRecommendations()).hasSize(3);
    }
}
