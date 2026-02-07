package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Map;

/**
 * DynamoDB entity for storing CloudWatch metrics snapshots
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class MetricSnapshot {

    private String projectId;
    private Long timestamp;
    private String serviceName;
    private String metricName;
    private Double value;
    private String unit;
    private Map<String, String> dimensions;

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

    @DynamoDbAttribute("serviceName")
    public String getServiceName() {
        return serviceName;
    }

    @DynamoDbAttribute("metricName")
    public String getMetricName() {
        return metricName;
    }

    @DynamoDbAttribute("value")
    public Double getValue() {
        return value;
    }

    @DynamoDbAttribute("unit")
    public String getUnit() {
        return unit;
    }

    @DynamoDbAttribute("dimensions")
    public Map<String, String> getDimensions() {
        return dimensions;
    }
}
