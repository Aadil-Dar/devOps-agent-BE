package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmResponse {
    private String alarmName;
    private String alarmArn;
    private String stateValue;
    private String stateReason;
    private String metricName;
    private String namespace;
    private String updatedTimestamp;
}
