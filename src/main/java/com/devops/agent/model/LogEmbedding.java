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
 * DynamoDB entity for storing log embeddings for fast similarity search
 * Uses Ollama's nomic-embed-text model for embeddings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class LogEmbedding {

    private String projectId;
    private String embeddingId; // format: summaryId#timestamp
    private String summaryId; // Reference to LogSummary
    private List<Double> embedding; // 768-dimensional vector from nomic-embed-text
    private String errorSignature;
    private String severity;
    private Long timestamp;
    private Integer occurrences;
    private String summaryText; // Condensed text used to generate embedding

    @DynamoDbPartitionKey
    @DynamoDbAttribute("projectId")
    public String getProjectId() {
        return projectId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("embeddingId")
    public String getEmbeddingId() {
        return embeddingId;
    }

    @DynamoDbAttribute("summaryId")
    public String getSummaryId() {
        return summaryId;
    }

    @DynamoDbAttribute("embedding")
    public List<Double> getEmbedding() {
        return embedding;
    }

    @DynamoDbAttribute("errorSignature")
    public String getErrorSignature() {
        return errorSignature;
    }

    @DynamoDbAttribute("severity")
    public String getSeverity() {
        return severity;
    }

    @DynamoDbAttribute("timestamp")
    public Long getTimestamp() {
        return timestamp;
    }

    @DynamoDbAttribute("occurrences")
    public Integer getOccurrences() {
        return occurrences;
    }

    @DynamoDbAttribute("summaryText")
    public String getSummaryText() {
        return summaryText;
    }
}
