package com.devops.agent.controller;

import com.devops.agent.model.ClusterLogEntry;
import com.devops.agent.service.CloudWatchLogsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/log-clusters")
@RequiredArgsConstructor
@Slf4j
public class LogClustersController {

    private final CloudWatchLogsService cloudWatchLogsService;

    @GetMapping
    public ResponseEntity<List<ClusterLogEntry>> getLogClusters(
            @RequestParam(required = false, defaultValue = "6h") String timeRange,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) List<String> severities,
            @RequestParam(required = false) List<String> services,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String projectId
    ) {
        log.info("GET /api/log-clusters - timeRange: {}, environment: {}, severities: {}, services: {}, search: {}, clusterId: {}, projectId: {}",
                timeRange, environment, severities, services, search, clusterId, projectId);
        try {
            var clusters = cloudWatchLogsService.getLogClusters(timeRange, environment, severities, services, search, clusterId, projectId);
            return ResponseEntity.ok(clusters);
        } catch (Exception e) {
            log.error("Error fetching log clusters", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
