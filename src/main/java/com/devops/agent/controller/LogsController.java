package com.devops.agent.controller;

import com.devops.agent.model.ClusterLogsResponse;
import com.devops.agent.model.PaginatedLogsResponse;
import com.devops.agent.model.LogSummaryResponse;
import com.devops.agent.service.CloudWatchLogsService;
import com.devops.agent.service.ClusterLogsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class LogsController {

    private final CloudWatchLogsService cloudWatchLogsService;
    private final ClusterLogsService clusterLogsService;

    /**
     * GET /api/logs
     * Fetch raw/filtered log entries from CloudWatch
     *
     * Query Parameters:
     * - timeRange: string ("1h", "6h", "24h", "today") - default: "24h"
     * - environment: string ("prod", "stage", "dev") - optional
     * - severities: array of strings (["ERROR", "WARN", "INFO", "DEBUG"]) - optional
     * - services: array of strings (["order-service", "payment-service"]) - optional
     * - search: string (text match on message) - optional
     * - clusterId: string (filters to a specific cluster) - optional
     * - page: int (default: 1)
     * - size: int (default: 20)
     *
     * Response: PaginatedLogsResponse with logs, total, page, size, totalPages
     */
    @GetMapping
    public ResponseEntity<PaginatedLogsResponse> getLogs(
            @RequestParam(required = false, defaultValue = "6h") String timeRange,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) List<String> severities,
            @RequestParam(required = false) List<String> services,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        log.info("GET /api/logs - timeRange: {}, environment: {}, severities: {}, services: {}, search: {}, clusterId: {}, projectId: {}, page: {}, size: {}",
                timeRange, environment, severities, services, search, clusterId, "ad7bf91a-172c-48aa-9ffb-9abd84f5d827", page, size);

        try {
            // Validate page and size
            if (page < 1) {
                log.warn("Invalid page number: {}. Setting to 1", page);
                page = 1;
            }
            if (size < 1 || size > 100) {
                log.warn("Invalid page size: {}. Setting to 20", size);
                size = 20;
            }

            // Validate timeRange
            if (timeRange != null && !isValidTimeRange(timeRange)) {
                log.warn("Invalid timeRange: {}. Using default 24h", timeRange);
                return ResponseEntity.badRequest().build();
            }

            PaginatedLogsResponse response = cloudWatchLogsService.getLogs(
                    timeRange,
                    environment,
                    severities,
                    services,
                    search,
                    clusterId,
                    projectId,
                    page,
                    size
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching logs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Validate timeRange parameter
     */
    private boolean isValidTimeRange(String timeRange) {
        return timeRange.matches("^(1h|6h|24h|today)$");
    }

    /**
     * GET /api/logs/clusters
     * Fetch optimized logs for a specific cluster with AI summary
     * Fast performance with parallel fetching and intelligent parsing
     *
     * Query Parameters:
     * - clusterId: string (default: "ept-backend-service-cluster")
     *
     * Response: ClusterLogsResponse with logs list, AI summary, and statistics
     */
    @GetMapping("/clusters")
    public ResponseEntity<ClusterLogsResponse> getClusterLogs(
            @RequestParam(required = false, defaultValue = "ept-backend-service-cluster") String clusterId
    ) {
        log.info("GET /api/logs/clusters - clusterId: {}", clusterId);

        try {
            ClusterLogsResponse response = clusterLogsService.getClusterLogs(clusterId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching cluster logs for: {}", clusterId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/logs/summary
     * Summarize logs for frontend health cards
     *
     * Query Parameters:
     * - timeRange: string ("1h", "6h", "24h", "today") - default: "6h"
     * - environment: string ("prod", "stage", "dev") - optional
     * - severities: array of strings (["ERROR", "WARN", "INFO", "DEBUG"]) - optional
     * - services: array of strings (["order-service", "payment-service"]) - optional
     * - search: string (text match on message) - optional
     * - clusterId: string (filters to a specific cluster) - optional
     *
     * Response: LogSummaryResponse with summary statistics
     */
    @GetMapping("/summary")
    public ResponseEntity<LogSummaryResponse> summarizeLogs(
            @RequestParam(required = false, defaultValue = "6h") String timeRange,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) List<String> severities,
            @RequestParam(required = false) List<String> services,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String projectId
    ) {
        log.info("GET /api/logs/summary - timeRange: {}, environment: {}, severities: {}, services: {}, search: {}, clusterId: {}, projectId: {}",
                timeRange, environment, severities, services, search, clusterId, projectId);
        try {
            if (timeRange != null && !isValidTimeRange(timeRange)) {
                return ResponseEntity.badRequest().build();
            }
            var summary = cloudWatchLogsService.summarizeLogs(timeRange, environment, severities, services, search, clusterId, projectId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error summarizing logs", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
