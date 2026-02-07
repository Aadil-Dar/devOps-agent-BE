package com.devops.agent.controller;

import com.devops.agent.model.DevOpsHealthCheckRequest;
import com.devops.agent.model.DevOpsHealthCheckResponse;
import com.devops.agent.service.DevOpsInsightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for DevOps AI-assisted health monitoring and failure prediction
 */
@RestController
@RequestMapping("/api/devops")
@RequiredArgsConstructor
@Slf4j
public class DevOpsInsightController {

    private final DevOpsInsightService devOpsInsightService;

    /**
     * Perform health check and failure prediction for a project
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
            log.info("Health check completed for project: {} with risk level: {}",
                    projectId, response.getRiskLevel());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for health check: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error performing health check for project: {}", projectId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
