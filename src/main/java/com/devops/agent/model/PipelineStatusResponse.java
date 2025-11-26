package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStatusResponse {
    private String pipelineName;
    private String status;
    private String latestExecutionId;
    private String createdTime;
    private String lastUpdatedTime;
}
