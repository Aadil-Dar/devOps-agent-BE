package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for log processing API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogProcessingResponse {
    private String projectId;
    private Long processingTimestamp;
    private Integer totalLogsProcessed;
    private Integer errorCount;
    private Integer warningCount;
    private Integer summariesCreated;
    private Integer embeddingsCreated;
    private String aiSummary;
    private String overallSeverity; // LOW, MEDIUM, HIGH, CRITICAL
    private List<LogSummaryDto> topErrors;
    private ProcessingStats stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogSummaryDto {
        private String service;
        private String errorSignature;
        private String severity;
        private Integer occurrences;
        private Long firstSeenTimestamp;
        private Long lastSeenTimestamp;
        private String sampleMessage;
        private Double trendScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingStats {
        private Long logFetchDurationMs;
        private Long logProcessingDurationMs;
        private Long embeddingGenerationDurationMs;
        private Long aiSummarizationDurationMs;
        private Long dbSaveDurationMs;
        private Long totalDurationMs;
    }
}
