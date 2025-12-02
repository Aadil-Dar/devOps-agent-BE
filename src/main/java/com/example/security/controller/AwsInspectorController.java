package com.example.security.controller;

import com.example.security.model.VulnerabilityDetailDto;
import com.example.security.model.VulnerabilitySummaryDto;
import com.example.security.service.AwsInspectorService;
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
     */
    @GetMapping
    public ResponseEntity<List<VulnerabilitySummaryDto>> getAllVulnerabilities() {
        log.info("GET /api/vulnerabilities - Fetching all vulnerabilities");
        try {
            List<VulnerabilitySummaryDto> vulnerabilities = awsInspectorService.getAllVulnerabilities();
            return ResponseEntity.ok(vulnerabilities);
        } catch (Exception e) {
            log.error("Error fetching vulnerabilities", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get vulnerability details by ID (ARN or ID)
     */
    @GetMapping("/{id}")
    public ResponseEntity<VulnerabilityDetailDto> getVulnerabilityById(@PathVariable String id) {
        log.info("GET /api/vulnerabilities/{} - Fetching vulnerability details", id);
        try {
            VulnerabilityDetailDto vulnerability = awsInspectorService.getVulnerabilityById(id);
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
}
