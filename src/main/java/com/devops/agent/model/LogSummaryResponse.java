package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSummaryResponse {
    private String logGroupName;
    private String logStreamName;
    private Long totalEvents;
    private String startTime;
    private String endTime;
    private List<LogEventSummary> events;
    private LogStatistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogEventSummary {
        private String timestamp;
        private String message;
        private String level;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogStatistics {
        private Long errorCount;
        private Long warningCount;
        private Long infoCount;
        private Long totalCount;
    }
}
