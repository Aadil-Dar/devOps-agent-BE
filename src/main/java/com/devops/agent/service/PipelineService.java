package com.devops.agent.service;

import com.devops.agent.model.PipelineStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineService {

    private final CodePipelineClient codePipelineClient;

    /**
     * Get all pipelines
     */
    public List<PipelineStatusResponse> getAllPipelines() {
        log.info("Fetching all pipelines");
        try {
            ListPipelinesResponse response = codePipelineClient.listPipelines();
            return response.pipelines().stream()
                    .map(summary -> PipelineStatusResponse.builder()
                            .pipelineName(summary.name())
                            .createdTime(summary.created() != null ? summary.created().toString() : null)
                            .lastUpdatedTime(summary.updated() != null ? summary.updated().toString() : null)
                            .build())
                    .collect(Collectors.toList());
        } catch (CodePipelineException e) {
            log.error("Error fetching pipelines: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch pipelines", e);
        }
    }

    /**
     * Get pipeline status by name
     */
    public PipelineStatusResponse getPipelineStatus(String pipelineName) {
        log.info("Fetching status for pipeline: {}", pipelineName);
        try {
            GetPipelineStateRequest request = GetPipelineStateRequest.builder()
                    .name(pipelineName)
                    .build();

            GetPipelineStateResponse response = codePipelineClient.getPipelineState(request);

            return PipelineStatusResponse.builder()
                    .pipelineName(response.pipelineName())
                    .status(response.stageStates().isEmpty() ? "UNKNOWN" : 
                            response.stageStates().get(0).latestExecution() != null ?
                                    response.stageStates().get(0).latestExecution().statusAsString() : "NO_EXECUTION")
                    .latestExecutionId(response.stageStates().isEmpty() ? null :
                            response.stageStates().get(0).latestExecution() != null ?
                                    response.stageStates().get(0).latestExecution().pipelineExecutionId() : null)
                    .lastUpdatedTime(response.updated() != null ? response.updated().toString() : null)
                    .build();
        } catch (PipelineNotFoundException e) {
            log.error("Pipeline not found: {}", pipelineName);
            throw new RuntimeException("Pipeline not found: " + pipelineName, e);
        } catch (CodePipelineException e) {
            log.error("Error fetching pipeline status: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch pipeline status", e);
        }
    }

    /**
     * Get pipeline execution history
     */
    public List<PipelineStatusResponse> getPipelineExecutionHistory(String pipelineName, int maxResults) {
        log.info("Fetching execution history for pipeline: {}", pipelineName);
        try {
            ListPipelineExecutionsRequest request = ListPipelineExecutionsRequest.builder()
                    .pipelineName(pipelineName)
                    .maxResults(maxResults)
                    .build();

            ListPipelineExecutionsResponse response = codePipelineClient.listPipelineExecutions(request);

            return response.pipelineExecutionSummaries().stream()
                    .map(execution -> PipelineStatusResponse.builder()
                            .pipelineName(pipelineName)
                            .status(execution.statusAsString())
                            .latestExecutionId(execution.pipelineExecutionId())
                            .createdTime(execution.startTime() != null ? execution.startTime().toString() : null)
                            .lastUpdatedTime(execution.lastUpdateTime() != null ? execution.lastUpdateTime().toString() : null)
                            .build())
                    .collect(Collectors.toList());
        } catch (PipelineNotFoundException e) {
            log.error("Pipeline not found: {}", pipelineName);
            throw new RuntimeException("Pipeline not found: " + pipelineName, e);
        } catch (CodePipelineException e) {
            log.error("Error fetching pipeline execution history: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch pipeline execution history", e);
        }
    }
}
