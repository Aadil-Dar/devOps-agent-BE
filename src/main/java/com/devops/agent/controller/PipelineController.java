package com.devops.agent.controller;

import com.devops.agent.model.PipelineStatusResponse;
import com.devops.agent.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
@Slf4j
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * Get all pipelines
     */
    @GetMapping
    public ResponseEntity<List<PipelineStatusResponse>> getAllPipelines() {
        log.info("GET /api/pipelines - Fetching all pipelines");
        try {
            List<PipelineStatusResponse> pipelines = pipelineService.getAllPipelines();
            return ResponseEntity.ok(pipelines);
        } catch (Exception e) {
            log.error("Error fetching pipelines", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get pipeline status by name
     * PR -> pipeline -> stages -> actions
     */
    @GetMapping("/{pipelineName}")
    public ResponseEntity<PipelineStatusResponse> getPipelineStatus(
            @PathVariable String pipelineName) {
        log.info("GET /api/pipelines/{} - Fetching pipeline status", pipelineName);
        try {
            PipelineStatusResponse status = pipelineService.getPipelineStatus(pipelineName);
            return ResponseEntity.ok(status);
        } catch (RuntimeException e) {
            log.error("Error fetching pipeline status", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get pipeline execution history
     */
    @GetMapping("/{pipelineName}/history")
    public ResponseEntity<List<PipelineStatusResponse>> getPipelineExecutionHistory(
            @PathVariable String pipelineName,
            @RequestParam(defaultValue = "10") int maxResults) {
        log.info("GET /api/pipelines/{}/history - Fetching execution history", pipelineName);
        try {
            List<PipelineStatusResponse> history = pipelineService.getPipelineExecutionHistory(
                    pipelineName, maxResults);
            return ResponseEntity.ok(history);
        } catch (RuntimeException e) {
            log.error("Error fetching pipeline execution history", e);
            return ResponseEntity.notFound().build();
        }
    }
}
