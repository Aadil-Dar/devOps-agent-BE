package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlowApi {
    private String endpoint;
    private long avgResponseTime;
    private long p95ResponseTime;
    private long p99ResponseTime;
    private long requestCount;
    private double errorRate;
    private String status;
    private String slowestRegion;
}
