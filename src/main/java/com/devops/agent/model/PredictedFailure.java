package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictedFailure {
    private String service;
    private String prediction;
    private int probability;
    private String timeframe;
    private String impact;
    private String affectedUsers;
    private String preventiveAction;
    private String severity;
}
