package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorTrend {
    private String timeframe;
    private long errors;
    private long warnings;
    private String change;
    private String severity;
    private String peakTime;
}
