package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopFailingService {
    private String name;
    private long failureCount;
    private double failureRate;
    private String trend;
    private double trendValue;
    private String lastFailure;
    private long criticalErrors;
    private String status;
}
