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
public class PaginatedLogsResponse {
    private List<LogEntry> logs;
    private long total;
    private int page;
    private int size;
    private int totalPages;
}

