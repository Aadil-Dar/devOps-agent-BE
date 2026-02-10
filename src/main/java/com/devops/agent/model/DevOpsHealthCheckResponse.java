package com.devops.agent.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Comprehensive DevOps health check response with predictions and insights
 */
@Value
@Builder
public class DevOpsHealthCheckResponse {
    List<FailingService> topFailingServices;
    List<ErrorTrend> errorTrends;
    List<SlowApi> slowApis;
    List<PredictedFailure> predictedFailures;
    List<Recommendation> recommendations;

    @Value
    @Builder
    public static class FailingService {
        String name;
        Integer failureCount;
        Double failureRate;
        String trend;           // up|down|stable
        Double trendValue;
        String lastFailure;
        Integer criticalErrors;
        String status;          // critical|warning|stable
    }

    @Value
    @Builder
    public static class ErrorTrend {
        String timeframe;
        Integer errors;
        Integer warnings;
        String change;
        String severity;        // high|medium|low
        String peakTime;
    }

    @Value
    @Builder
    public static class SlowApi {
        String endpoint;
        Integer avgResponseTime;
        Integer p95ResponseTime;
        Integer p99ResponseTime;
        Integer requestCount;
        Double errorRate;
        String status;          // critical|warning|healthy
        String slowestRegion;
    }

    @Value
    @Builder
    public static class PredictedFailure {
        String service;
        String prediction;
        Integer probability;
        String timeframe;
        String impact;
        String affectedUsers;
        String preventiveAction;
        String severity;        // critical|high|medium
    }

    @Value
    @Builder
    public static class Recommendation {
        String title;
        String priority;        // critical|high|medium
        String impact;
        String effort;
        String estimatedTime;
        String category;
        List<String> steps;
        String roi;
    }
}
