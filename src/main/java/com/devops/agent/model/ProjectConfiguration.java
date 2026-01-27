package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.Map;

/**
 * DynamoDB entity for storing project configurations (non-sensitive data)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ProjectConfiguration {

    private String projectId;
    private String projectName;
    private String githubOwner;
    private String githubRepo;
    private String awsRegion;
    private String awsAccountId;
    private String secretsPath;
    private Boolean enabled;
    private Long createdAt;
    private Long updatedAt;
    private String createdBy;
    private Map<String, String> metadata;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("projectId")
    public String getProjectId() {
        return projectId;
    }

    @DynamoDbAttribute("projectName")
    public String getProjectName() {
        return projectName;
    }

    @DynamoDbAttribute("githubOwner")
    public String getGithubOwner() {
        return githubOwner;
    }

    @DynamoDbAttribute("githubRepo")
    public String getGithubRepo() {
        return githubRepo;
    }

    @DynamoDbAttribute("awsRegion")
    public String getAwsRegion() {
        return awsRegion;
    }

    @DynamoDbAttribute("awsAccountId")
    public String getAwsAccountId() {
        return awsAccountId;
    }

    @DynamoDbAttribute("secretsPath")
    public String getSecretsPath() {
        return secretsPath;
    }

    @DynamoDbAttribute("enabled")
    public Boolean getEnabled() {
        return enabled;
    }

    @DynamoDbAttribute("createdAt")
    public Long getCreatedAt() {
        return createdAt;
    }

    @DynamoDbAttribute("updatedAt")
    public Long getUpdatedAt() {
        return updatedAt;
    }

    @DynamoDbAttribute("createdBy")
    public String getCreatedBy() {
        return createdBy;
    }

    @DynamoDbAttribute("metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }
}

