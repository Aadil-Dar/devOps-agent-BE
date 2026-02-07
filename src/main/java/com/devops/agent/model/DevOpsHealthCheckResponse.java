package com.devops.agent.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for DevOps health check API
 */
@Value
@Builder
public class DevOpsHealthCheckResponse {
    String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    String summary;
    List<String> recommendations;
    PredictionDetails predictions;
    Integer logCount;
    Integer errorCount;
    Integer warningCount;
    List<MetricTrend> metricTrends;
    Long timestamp;

    @Value
    @Builder
    public static class PredictionDetails {
        String timeframe; // "within 2 hours", "within 24 hours", etc.
        Double likelihood; // 0.0 to 1.0
        String rootCause;
    }

    @Value
    @Builder
    public static class MetricTrend {
        String serviceName;
        String metricName;
        Double currentValue;
        Double averageValue;
        String trend; // INCREASING, STABLE, DECREASING
        String unit;
    }
}
