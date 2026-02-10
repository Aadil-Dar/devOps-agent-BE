package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for project operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    private String projectId;
    private String projectName;
    private String githubOwner;
    private String githubRepo;
    private String awsRegion;
    private String backendLogGroupName;
    private Boolean enabled;
    private Long createdAt;
    private Long updatedAt;
    private String createdBy;
    private String message;
}

