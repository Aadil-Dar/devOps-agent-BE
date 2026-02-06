package com.devops.agent.controller;
import com.devops.agent.model.SystemHealthReport;
import com.devops.agent.service.CloudWatchLogsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/system-health")
@RequiredArgsConstructor
@Slf4j
public class SystemHealthController {
    private final CloudWatchLogsService cloudWatchLogsService;
    @GetMapping
    public ResponseEntity<SystemHealthReport> getSystemHealth(@RequestParam(required = false) String environment) {
        log.info("GET /api/system-health - environment: {}", environment);
        try {
            var report = cloudWatchLogsService.getSystemHealthReport(environment,"ad7bf91a-172c-48aa-9ffb-9abd84f5d827");
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Error fetching system health report", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
