package com.devops.agent.controller;

import com.devops.agent.model.LogSummaryResponse;
import com.devops.agent.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class LogController {

    private final LogService logService;

    /**
     * Get all CloudWatch log groups
     */
    @GetMapping("/groups")
    public ResponseEntity<List<String>> getAllLogGroups() {
        log.info("GET /api/logs/groups - Fetching all log groups");
        try {
            List<String> logGroups = logService.getAllLogGroups();
            return ResponseEntity.ok(logGroups);
        } catch (Exception e) {
            log.error("Error fetching log groups", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get log streams for a specific log group
     */
    @GetMapping("/groups/{logGroupName}/streams")
    public ResponseEntity<List<String>> getLogStreams(
            @PathVariable String logGroupName) {
        log.info("GET /api/logs/groups/{}/streams - Fetching log streams", logGroupName);
        try {
            List<String> streams = logService.getLogStreams(logGroupName);
            return ResponseEntity.ok(streams);
        } catch (RuntimeException e) {
            log.error("Error fetching log streams", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get summarized logs for a specific log group and stream
     * Hours parameter defaults to 24 if not provided
     */
    @GetMapping("/groups/{logGroupName}/streams/{logStreamName}/summary")
    public ResponseEntity<LogSummaryResponse> getLogSummary(
            @PathVariable String logGroupName,
            @PathVariable String logStreamName,
            @RequestParam(required = false) Integer hours) {
        log.info("GET /api/logs/groups/{}/streams/{}/summary - Fetching log summary", 
                logGroupName, logStreamName);
        try {
            LogSummaryResponse summary = logService.getLogSummary(logGroupName, logStreamName, hours);
            return ResponseEntity.ok(summary);
        } catch (RuntimeException e) {
            log.error("Error fetching log summary", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get summarized logs for a log group (uses most recent stream)
     * Hours parameter defaults to 24 if not provided
     */
    @GetMapping("/groups/{logGroupName}/summary")
    public ResponseEntity<LogSummaryResponse> getLogSummaryForGroup(
            @PathVariable String logGroupName,
            @RequestParam(required = false) Integer hours) {
        log.info("GET /api/logs/groups/{}/summary - Fetching log summary for group", logGroupName);
        try {
            LogSummaryResponse summary = logService.getLogSummaryForGroup(logGroupName, hours);
            return ResponseEntity.ok(summary);
        } catch (RuntimeException e) {
            log.error("Error fetching log summary for group", e);
            return ResponseEntity.notFound().build();
        }
    }
}
