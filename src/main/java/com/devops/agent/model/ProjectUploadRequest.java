package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for uploading project configuration from admin panel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUploadRequest {

    // Non-sensitive configuration
    private String projectName;
    private String githubOwner;
    private String githubRepo;
    private String awsRegion;
    private String createdBy;
    private String backendLogGroupName;

    // Sensitive credentials (will be stored in Secrets Manager)
    private String githubToken;
    private String awsAccessKey;
    private String awsSecretKey;
}

