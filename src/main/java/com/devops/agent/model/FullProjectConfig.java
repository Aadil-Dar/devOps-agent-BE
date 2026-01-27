package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Complete project configuration including both config and secrets
 * Used internally by worker services
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullProjectConfig {

    // Configuration (from DynamoDB)
    private String projectId;
    private String projectName;
    private String githubOwner;
    private String githubRepo;
    private String awsRegion;

    // Secrets (from Secrets Manager)
    private String githubToken;
    private String awsAccessKey;
    private String awsSecretKey;

    // Optional fields
    private Boolean enabled;
    private Long createdAt;
}

