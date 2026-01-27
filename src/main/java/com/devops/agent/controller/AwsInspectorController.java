package com.devops.agent.controller;

import com.devops.agent.model.VulnerabilityDetailDto;
import com.devops.agent.model.VulnerabilitySummaryDto;
import com.devops.agent.service.AwsInspectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vulnerabilities")
@RequiredArgsConstructor
@Slf4j
public class AwsInspectorController {

    private final AwsInspectorService awsInspectorService;

    /**
     * Get all vulnerabilities (paginated list of summaries)
     * @param projectId Optional project ID to fetch vulnerabilities for a specific project
     */
    @GetMapping
    public ResponseEntity<List<VulnerabilitySummaryDto>> getAllVulnerabilities(
            @RequestParam(required = false) String projectId) {
        log.info("GET /api/vulnerabilities - Fetching all vulnerabilities for projectId: {}",
                projectId != null ? projectId : "default");
        try {
            List<VulnerabilitySummaryDto> vulnerabilities;

            if (projectId != null && !projectId.isEmpty()) {
                vulnerabilities = awsInspectorService.getAllVulnerabilitiesForProject(projectId);
            } else {
                vulnerabilities = awsInspectorService.getAllVulnerabilities();
            }

            return ResponseEntity.ok(vulnerabilities);
        } catch (RuntimeException e) {
            log.error("Error fetching vulnerabilities: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching vulnerabilities", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get vulnerability details by ID (ARN or ID)
     * @param id The vulnerability ID or ARN
     * @param projectId Optional project ID to fetch vulnerability for a specific project
     */
    @GetMapping("/{id}")
    public ResponseEntity<VulnerabilityDetailDto> getVulnerabilityById(
            @PathVariable String id,
            @RequestParam(required = false) String projectId) {
        log.info("GET /api/vulnerabilities/{} - Fetching vulnerability details for projectId: {}",
                id, projectId != null ? projectId : "default");
        try {
            VulnerabilityDetailDto vulnerability;

            if (projectId != null && !projectId.isEmpty()) {
                vulnerability = awsInspectorService.getVulnerabilityByIdForProject(projectId, id);
            } else {
                vulnerability = awsInspectorService.getVulnerabilityById(id);
            }

            return ResponseEntity.ok(vulnerability);
        } catch (IllegalArgumentException e) {
            log.error("Invalid vulnerability ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error fetching vulnerability with ID: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching vulnerability with ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clear vulnerability cache for a specific project
     * Useful for forcing a refresh after vulnerability scans
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Void> clearCache(@RequestParam String projectId) {
        log.info("DELETE /api/vulnerabilities/cache - Clearing cache for projectId: {}", projectId);
        try {
            awsInspectorService.clearVulnerabilitiesCache(projectId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error clearing cache for projectId: {}", projectId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clear all vulnerability caches
     * Admin operation to refresh all cached data
     */
    @DeleteMapping("/cache/all")
    public ResponseEntity<Void> clearAllCaches() {
        log.info("DELETE /api/vulnerabilities/cache/all - Clearing all vulnerability caches");
        try {
            awsInspectorService.clearAllVulnerabilityCaches();
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error clearing all caches", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
