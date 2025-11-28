package com.devops.agent.service;

import com.devops.agent.model.LogSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LogService {

    private final CloudWatchLogsClient cloudWatchLogsClient;

    // Patterns to detect log levels
    private static final Pattern ERROR_PATTERN = Pattern.compile("(?i)\\b(error|exception|failed|failure|fatal)\\b");
    private static final Pattern WARNING_PATTERN = Pattern.compile("(?i)\\b(warn|warning|alert)\\b");
    private static final Pattern INFO_PATTERN = Pattern.compile("(?i)\\b(info|information|success|completed)\\b");

    /**
     * Get all log groups
     */
    public List<String> getAllLogGroups() {
        log.info("Fetching all CloudWatch log groups");
        try {
            DescribeLogGroupsResponse response = cloudWatchLogsClient.describeLogGroups();
            return response.logGroups().stream()
                    .map(LogGroup::logGroupName)
                    .collect(Collectors.toList());
        } catch (CloudWatchLogsException e) {
            log.error("Error fetching log groups: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch log groups", e);
        }
    }

    /**
     * Get log streams for a specific log group
     */
    public List<String> getLogStreams(String logGroupName) {
        log.info("Fetching log streams for log group: {}", logGroupName);
        try {
            DescribeLogStreamsRequest request = DescribeLogStreamsRequest.builder()
                    .logGroupName(logGroupName)
                    .orderBy(OrderBy.LAST_EVENT_TIME)
                    .descending(true)
                    .limit(10)
                    .build();

            DescribeLogStreamsResponse response = cloudWatchLogsClient.describeLogStreams(request);
            return response.logStreams().stream()
                    .map(LogStream::logStreamName)
                    .collect(Collectors.toList());
        } catch (CloudWatchLogsException e) {
            log.error("Error fetching log streams: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch log streams for: " + logGroupName, e);
        }
    }

    /**
     * Get summarized logs for a specific log group and stream
     */
    public LogSummaryResponse getLogSummary(String logGroupName, String logStreamName, Integer hours) {
        log.info("Fetching log summary for log group: {}, stream: {}, hours: {}", 
                logGroupName, logStreamName, hours);
        
        try {
            // Calculate time range (default to last 24 hours if not specified)
            int hoursToFetch = (hours != null && hours > 0) ? hours : 24;
            long endTime = Instant.now().toEpochMilli();
            long startTime = Instant.now().minus(hoursToFetch, ChronoUnit.HOURS).toEpochMilli();

            // Fetch log events
            GetLogEventsRequest request = GetLogEventsRequest.builder()
                    .logGroupName(logGroupName)
                    .logStreamName(logStreamName)
                    .startTime(startTime)
                    .endTime(endTime)
                    .limit(100)
                    .startFromHead(false) // Get most recent events
                    .build();

            GetLogEventsResponse response = cloudWatchLogsClient.getLogEvents(request);
            List<OutputLogEvent> logEvents = response.events();

            // Process and categorize log events
            List<LogSummaryResponse.LogEventSummary> eventSummaries = new ArrayList<>();
            long errorCount = 0;
            long warningCount = 0;
            long infoCount = 0;

            for (OutputLogEvent event : logEvents) {
                String message = event.message();
                String level = determineLogLevel(message);
                
                switch (level) {
                    case "ERROR":
                        errorCount++;
                        break;
                    case "WARNING":
                        warningCount++;
                        break;
                    case "INFO":
                        infoCount++;
                        break;
                    case "UNKNOWN":
                        // Intentionally not counted in statistics
                        break;
                    default:
                        // Should not happen, but handle gracefully
                        break;
                }

                eventSummaries.add(LogSummaryResponse.LogEventSummary.builder()
                        .timestamp(Instant.ofEpochMilli(event.timestamp()).toString())
                        .message(truncateMessage(message, 200))
                        .level(level)
                        .build());
            }

            // Build statistics
            LogSummaryResponse.LogStatistics statistics = LogSummaryResponse.LogStatistics.builder()
                    .errorCount(errorCount)
                    .warningCount(warningCount)
                    .infoCount(infoCount)
                    .totalCount((long) logEvents.size())
                    .build();

            return LogSummaryResponse.builder()
                    .logGroupName(logGroupName)
                    .logStreamName(logStreamName)
                    .totalEvents((long) logEvents.size())
                    .startTime(Instant.ofEpochMilli(startTime).toString())
                    .endTime(Instant.ofEpochMilli(endTime).toString())
                    .events(eventSummaries)
                    .statistics(statistics)
                    .build();

        } catch (CloudWatchLogsException e) {
            log.error("Error fetching log summary: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch log summary", e);
        }
    }

    /**
     * Get summarized logs for a log group (latest stream)
     */
    public LogSummaryResponse getLogSummaryForGroup(String logGroupName, Integer hours) {
        log.info("Fetching log summary for log group: {}", logGroupName);
        
        // Get the most recent log stream
        List<String> streams = getLogStreams(logGroupName);
        if (streams.isEmpty()) {
            throw new RuntimeException("No log streams found for log group: " + logGroupName);
        }
        
        String latestStream = streams.get(0);
        return getLogSummary(logGroupName, latestStream, hours);
    }

    /**
     * Determine log level based on message content
     */
    private String determineLogLevel(String message) {
        if (message == null) {
            return "UNKNOWN";
        }
        
        if (ERROR_PATTERN.matcher(message).find()) {
            return "ERROR";
        } else if (WARNING_PATTERN.matcher(message).find()) {
            return "WARNING";
        } else if (INFO_PATTERN.matcher(message).find()) {
            return "INFO";
        }
        
        return "UNKNOWN";
    }

    /**
     * Truncate long messages for summary display
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "";
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength) + "...";
    }
}
