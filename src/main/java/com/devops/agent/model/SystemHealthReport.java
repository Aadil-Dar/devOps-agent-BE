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
public class SystemHealthReport {
    private List<TopFailingService> topFailingServices;
    private List<ErrorTrend> errorTrends;
    private List<SlowApi> slowApis;
    private List<PredictedFailure> predictedFailures;
    private List<Recommendation> recommendations;
}
