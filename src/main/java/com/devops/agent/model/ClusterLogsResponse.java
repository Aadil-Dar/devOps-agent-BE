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
public class ClusterLogsResponse {
    private List<ClusterLogEntry> logs;
    private String summary;         // AI-generated summary
    private int totalErrors;
    private int totalWarnings;
    private int totalLogs;
    private String clusterId;
    private String timeRange;
}

