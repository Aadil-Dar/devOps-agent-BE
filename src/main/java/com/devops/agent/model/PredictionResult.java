package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

/**
 * DynamoDB entity for storing AI-generated failure predictions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class PredictionResult {

    private String projectId;
    private Long timestamp;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String summary;
    private List<String> recommendations;
    private String predictionTimeframe; // e.g., "within 2 hours", "within 24 hours"
    private Double failureLikelihood; // 0.0 to 1.0
    private String rootCause;
    private Integer logCount;
    private Integer errorCount;
    private Integer warningCount;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("projectId")
    public String getProjectId() {
        return projectId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("timestamp")
    public Long getTimestamp() {
        return timestamp;
    }

    @DynamoDbAttribute("riskLevel")
    public String getRiskLevel() {
        return riskLevel;
    }

    @DynamoDbAttribute("summary")
    public String getSummary() {
        return summary;
    }

    @DynamoDbAttribute("recommendations")
    public List<String> getRecommendations() {
        return recommendations;
    }

    @DynamoDbAttribute("predictionTimeframe")
    public String getPredictionTimeframe() {
        return predictionTimeframe;
    }

    @DynamoDbAttribute("failureLikelihood")
    public Double getFailureLikelihood() {
        return failureLikelihood;
    }

    @DynamoDbAttribute("rootCause")
    public String getRootCause() {
        return rootCause;
    }

    @DynamoDbAttribute("logCount")
    public Integer getLogCount() {
        return logCount;
    }

    @DynamoDbAttribute("errorCount")
    public Integer getErrorCount() {
        return errorCount;
    }

    @DynamoDbAttribute("warningCount")
    public Integer getWarningCount() {
        return warningCount;
    }
}
