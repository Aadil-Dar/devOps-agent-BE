package com.devops.agent.service;

import com.devops.agent.model.AlarmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlarmService {

    private final CloudWatchClient cloudWatchClient;

    /**
     * Get all CloudWatch alarms
     */
    public List<AlarmResponse> getAllAlarms() {
        log.info("Fetching all CloudWatch alarms");
        try {
            DescribeAlarmsResponse response = cloudWatchClient.describeAlarms();
            return mapMetricAlarmsToResponse(response.metricAlarms());
        } catch (CloudWatchException e) {
            log.error("Error fetching alarms: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch alarms", e);
        }
    }

    /**
     * Get alarms by state
     */
    public List<AlarmResponse> getAlarmsByState(String state) {
        log.info("Fetching alarms with state: {}", state);
        try {
            StateValue stateValue = StateValue.fromValue(state.toUpperCase());
            DescribeAlarmsRequest request = DescribeAlarmsRequest.builder()
                    .stateValue(stateValue)
                    .build();

            DescribeAlarmsResponse response = cloudWatchClient.describeAlarms(request);
            return mapMetricAlarmsToResponse(response.metricAlarms());
        } catch (IllegalArgumentException e) {
            log.error("Invalid state value: {}", state);
            throw new RuntimeException("Invalid state value. Valid values are: OK, ALARM, INSUFFICIENT_DATA", e);
        } catch (CloudWatchException e) {
            log.error("Error fetching alarms by state: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch alarms by state", e);
        }
    }

    /**
     * Get alarm details by name
     */
    public AlarmResponse getAlarmByName(String alarmName) {
        log.info("Fetching alarm: {}", alarmName);
        try {
            DescribeAlarmsRequest request = DescribeAlarmsRequest.builder()
                    .alarmNames(alarmName)
                    .build();

            DescribeAlarmsResponse response = cloudWatchClient.describeAlarms(request);

            if (response.metricAlarms().isEmpty()) {
                log.error("Alarm not found: {}", alarmName);
                throw new RuntimeException("Alarm not found: " + alarmName);
            }

            return mapMetricAlarmToResponse(response.metricAlarms().get(0));
        } catch (CloudWatchException e) {
            log.error("Error fetching alarm: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch alarm", e);
        }
    }

    /**
     * Map MetricAlarms to AlarmResponse list
     */
    private List<AlarmResponse> mapMetricAlarmsToResponse(List<MetricAlarm> metricAlarms) {
        return metricAlarms.stream()
                .map(this::mapMetricAlarmToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Map MetricAlarm to AlarmResponse
     */
    private AlarmResponse mapMetricAlarmToResponse(MetricAlarm alarm) {
        return AlarmResponse.builder()
                .alarmName(alarm.alarmName())
                .alarmArn(alarm.alarmArn())
                .stateValue(alarm.stateValueAsString())
                .stateReason(alarm.stateReason())
                .metricName(alarm.metricName())
                .namespace(alarm.namespace())
                .updatedTimestamp(alarm.stateUpdatedTimestamp() != null ? 
                        alarm.stateUpdatedTimestamp().toString() : null)
                .build();
    }
}
