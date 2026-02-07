package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB entity for storing grouped log summaries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class LogSummary {

    private String projectId;
    private String summaryId; // format: service#errorSignature#timestamp
    private String service;
    private String errorSignature; // normalized error pattern
    private String severity;
    private Integer occurrences;
    private Long firstSeenTimestamp;
    private Long lastSeenTimestamp;
    private String sampleMessage;
    private Double trendScore; // positive = increasing, negative = decreasing

    @DynamoDbPartitionKey
    @DynamoDbAttribute("projectId")
    public String getProjectId() {
        return projectId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("summaryId")
    public String getSummaryId() {
        return summaryId;
    }

    @DynamoDbAttribute("service")
    public String getService() {
        return service;
    }

    @DynamoDbAttribute("errorSignature")
    public String getErrorSignature() {
        return errorSignature;
    }

    @DynamoDbAttribute("severity")
    public String getSeverity() {
        return severity;
    }

    @DynamoDbAttribute("occurrences")
    public Integer getOccurrences() {
        return occurrences;
    }

    @DynamoDbAttribute("firstSeenTimestamp")
    public Long getFirstSeenTimestamp() {
        return firstSeenTimestamp;
    }

    @DynamoDbAttribute("lastSeenTimestamp")
    public Long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    @DynamoDbAttribute("sampleMessage")
    public String getSampleMessage() {
        return sampleMessage;
    }

    @DynamoDbAttribute("trendScore")
    public Double getTrendScore() {
        return trendScore;
    }
}
