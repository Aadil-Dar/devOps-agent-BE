package com.devops.agent.controller;

import com.devops.agent.model.DevOpsHealthCheckResponse;
import com.devops.agent.model.LogProcessingResponse;
import com.devops.agent.service.DevOpsInsightService;
import com.devops.agent.service.LogProcessingService;
import com.devops.agent.service.MetricProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * Controller for DevOps AI-assisted health monitoring, failure prediction, and log processing
 */
@RestController
@RequestMapping("/api/devops")
@RequiredArgsConstructor
@Slf4j
public class DevOpsInsightController {

    private final DevOpsInsightService devOpsInsightService;
    private final LogProcessingService logProcessingService;
    private final MetricProcessingService metricProcessingService;

    /**
     * Perform health check and failure prediction for a project
     * Uses cached data from DynamoDB for fast predictions
     *
     * @param projectId projectId
     * @return Health check response with risk level, predictions, and recommendations
     */
    @GetMapping("/healthCheck")
    public ResponseEntity<DevOpsHealthCheckResponse> performHealthCheck(@RequestParam
            String projectId) {

        log.info("Health check requested for project: {}", projectId);

        try {
            DevOpsHealthCheckResponse response = devOpsInsightService.performHealthCheck(projectId);
//            log.info("Health check completed for project: {} with risk level: {}",
//                    projectId, response.getRiskLevel());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for health check: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error performing health check for project: {}", projectId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Process logs endpoint - fetches logs from CloudWatch, filters, summarizes,
     * creates embeddings using Ollama, and saves to DynamoDB
     * Also triggers async metric collection in background for performance
     *
     * @param projectId projectId
     * @return Log processing response with summaries and statistics
     */
    @PostMapping("/process-logs")
    public ResponseEntity<LogProcessingResponse> processLogs(@RequestParam String projectId) {
        log.info("Log processing requested for project: {}", projectId);

        try {
            // Process logs synchronously
            LogProcessingResponse response = logProcessingService.processLogs(projectId);

            // Trigger async metric processing in background (non-blocking)
            CompletableFuture<Integer> metricsFuture = metricProcessingService.processMetricsAsync(projectId);
            metricsFuture.thenAccept(count ->
                    log.info("Background metric processing completed for project {}. Collected {} metrics.",
                            projectId, count)
            ).exceptionally(ex -> {
                log.error("Background metric processing failed for project {}: {}", projectId, ex.getMessage());
                return null;
            });

            log.info("Log processing completed for project: {} - {} logs processed, {} summaries created",
                    projectId, response.getTotalLogsProcessed(), response.getSummariesCreated());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request for log processing: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing logs for project: {}", projectId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
