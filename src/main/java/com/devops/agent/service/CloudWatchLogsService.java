package com.devops.agent.service;

import com.devops.agent.model.LogEntry;
import com.devops.agent.model.PaginatedLogsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudWatchLogsService {

    private final CloudWatchLogsClient cloudWatchLogsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final Pattern SEVERITY_PATTERN = Pattern.compile("\\b(ERROR|WARN|INFO|DEBUG)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("(?:requestId|traceId|trace-id|request-id)[:\\s=]([a-zA-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVICE_PATTERN = Pattern.compile("(?:service|application)[:\\s=]([a-zA-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Fetch logs from CloudWatch with filtering and pagination
     */
    public PaginatedLogsResponse getLogs(
            String timeRange,
            String environment,
            List<String> severities,
            List<String> services,
            String search,
            String clusterId,
            int page,
            int size
    ) {
        log.info("Fetching logs - timeRange: {}, environment: {}, severities: {}, services: {}, search: {}, clusterId: {}, page: {}, size: {}",
                timeRange, environment, severities, services, search, clusterId, page, size);

        try {
            // Calculate time range
            long[] timeRangeMillis = calculateTimeRange(timeRange);
            long startTime = timeRangeMillis[0];
            long endTime = timeRangeMillis[1];

            // Get log group names based on environment and clusterId
            List<String> logGroupNames = getLogGroupNames(environment, clusterId);

            if (logGroupNames.isEmpty()) {
                log.warn("No log groups found for environment: {} and clusterId: {}", environment, clusterId);
                return PaginatedLogsResponse.builder()
                        .logs(Collections.emptyList())
                        .total(0)
                        .page(page)
                        .size(size)
                        .totalPages(0)
                        .build();
            }

            // Fetch logs from all matching log groups
            List<LogEntry> allLogs = new ArrayList<>();
            for (String logGroupName : logGroupNames) {
                List<LogEntry> groupLogs = fetchLogsFromGroup(logGroupName, startTime, endTime);
                allLogs.addAll(groupLogs);
            }

            // Apply filters
            List<LogEntry> filteredLogs = filterLogs(allLogs, severities, services, search);

            // Sort by timestamp (newest first)
            filteredLogs.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

            // Calculate pagination
            int total = filteredLogs.size();
            int totalPages = (int) Math.ceil((double) total / size);
            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, total);

            List<LogEntry> paginatedLogs = startIndex < total
                    ? filteredLogs.subList(startIndex, endIndex)
                    : Collections.emptyList();

            return PaginatedLogsResponse.builder()
                    .logs(paginatedLogs)
                    .total(total)
                    .page(page)
                    .size(size)
                    .totalPages(totalPages)
                    .build();

        } catch (Exception e) {
            log.error("Error fetching logs from CloudWatch", e);
            throw new RuntimeException("Failed to fetch logs", e);
        }
    }

    /**
     * Calculate time range in milliseconds based on timeRange string
     */
    private long[] calculateTimeRange(String timeRange) {
        long endTime = System.currentTimeMillis();
        long startTime;

        if (timeRange == null || timeRange.isEmpty()) {
            timeRange = "24h"; // default to last 24 hours
        }

        switch (timeRange.toLowerCase()) {
            case "1h":
                startTime = Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli();
                break;
            case "6h":
                startTime = Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli();
                break;
            case "24h":
                startTime = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli();
                break;
            case "today":
                startTime = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
                break;
            default:
                startTime = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli();
        }

        return new long[]{startTime, endTime};
    }

    /**
     * Get log group names based on environment and clusterId
     */
    private List<String> getLogGroupNames(String environment, String clusterId) {
        try {
            DescribeLogGroupsRequest.Builder requestBuilder = DescribeLogGroupsRequest.builder();

            // Build filter pattern for log group names
            if (environment != null && !environment.isEmpty()) {
                requestBuilder.logGroupNamePrefix("/aws/ecs/" + environment);
            } else {
                requestBuilder.logGroupNamePrefix("/aws/ecs/");
            }

            DescribeLogGroupsResponse response = cloudWatchLogsClient.describeLogGroups(requestBuilder.build());

            List<String> logGroupNames = response.logGroups().stream()
                    .map(LogGroup::logGroupName)
                    .filter(name -> clusterId == null || clusterId.isEmpty() || name.contains(clusterId))
                    .collect(Collectors.toList());

            log.info("Found {} log groups", logGroupNames.size());
            return logGroupNames;

        } catch (CloudWatchLogsException e) {
            log.warn("Error fetching log groups: {}", e.getMessage());
            // Return default log groups if specific ones not found
            return Arrays.asList(
                    "/aws/ecs/prod/order-service",
                    "/aws/ecs/prod/payment-service",
                    "/aws/ecs/prod/user-service"
            );
        }
    }

    /**
     * Fetch logs from a specific log group
     */
    private List<LogEntry> fetchLogsFromGroup(String logGroupName, long startTime, long endTime) {
        List<LogEntry> logs = new ArrayList<>();

        try {
            // Get log streams for this log group (most recent first)
            DescribeLogStreamsRequest streamsRequest = DescribeLogStreamsRequest.builder()
                    .logGroupName(logGroupName)
                    .orderBy(OrderBy.LAST_EVENT_TIME)
                    .descending(true)
                    .limit(10) // Limit to most recent streams
                    .build();

            DescribeLogStreamsResponse streamsResponse = cloudWatchLogsClient.describeLogStreams(streamsRequest);

            for (LogStream stream : streamsResponse.logStreams()) {
                // Fetch events from this stream
                GetLogEventsRequest eventsRequest = GetLogEventsRequest.builder()
                        .logGroupName(logGroupName)
                        .logStreamName(stream.logStreamName())
                        .startTime(startTime)
                        .endTime(endTime)
                        .startFromHead(false) // Start from most recent
                        .limit(100) // Limit events per stream
                        .build();

                GetLogEventsResponse eventsResponse = cloudWatchLogsClient.getLogEvents(eventsRequest);

                for (OutputLogEvent event : eventsResponse.events()) {
                    LogEntry logEntry = parseLogEvent(event, logGroupName, stream.logStreamName());
                    logs.add(logEntry);
                }
            }

        } catch (CloudWatchLogsException e) {
            log.warn("Error fetching logs from group {}: {}", logGroupName, e.getMessage());
        }

        return logs;
    }

    /**
     * Parse a CloudWatch log event into a LogEntry
     */
    private LogEntry parseLogEvent(OutputLogEvent event, String logGroupName, String logStreamName) {
        String message = event.message();
        String severity = extractSeverity(message);
        String service = extractService(message, logGroupName);
        String host = extractHost(logStreamName);
        String requestId = extractRequestId(message);
        Map<String, Object> parsed = parseStructuredLog(message);

        String timestamp = Instant.ofEpochMilli(event.timestamp())
                .atZone(ZoneId.systemDefault())
                .format(ISO_FORMATTER);

        return LogEntry.builder()
                .id(UUID.randomUUID().toString()) // Generate unique ID
                .timestamp(timestamp)
                .severity(severity)
                .service(service)
                .host(host)
                .message(message)
                .requestId(requestId)
                .parsed(parsed)
                .build();
    }

    /**
     * Extract severity from log message
     */
    private String extractSeverity(String message) {
        Matcher matcher = SEVERITY_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "INFO"; // default
    }

    /**
     * Extract service name from log message or log group name
     */
    private String extractService(String message, String logGroupName) {
        // Try to extract from message first
        Matcher matcher = SERVICE_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Extract from log group name
        // e.g., /aws/ecs/prod/order-service -> order-service
        String[] parts = logGroupName.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }

        return "unknown-service";
    }

    /**
     * Extract host from log stream name
     */
    private String extractHost(String logStreamName) {
        // Log stream names often contain task IDs or IPs
        // e.g., ecs/order-service/abc123 or ip-10-0-1-123
        if (logStreamName.contains("ip-")) {
            int ipStart = logStreamName.indexOf("ip-");
            int ipEnd = logStreamName.indexOf("/", ipStart);
            if (ipEnd == -1) ipEnd = logStreamName.length();
            return logStreamName.substring(ipStart, ipEnd);
        }
        return logStreamName;
    }

    /**
     * Extract request ID from log message
     */
    private String extractRequestId(String message) {
        Matcher matcher = REQUEST_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Try to parse structured log (JSON) from message
     */
    private Map<String, Object> parseStructuredLog(String message) {
        try {
            // Check if message looks like JSON
            if (message.trim().startsWith("{") && message.trim().endsWith("}")) {
                JsonNode jsonNode = objectMapper.readTree(message);
                Map<String, Object> result = new HashMap<>();
                jsonNode.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isNumber()) {
                        result.put(entry.getKey(), entry.getValue().asInt());
                    } else if (entry.getValue().isBoolean()) {
                        result.put(entry.getKey(), entry.getValue().asBoolean());
                    } else {
                        result.put(entry.getKey(), entry.getValue().asText());
                    }
                });
                return result;
            }
        } catch (Exception e) {
            // Not structured JSON, ignore
        }
        return null;
    }

    /**
     * Filter logs based on criteria
     */
    private List<LogEntry> filterLogs(
            List<LogEntry> logs,
            List<String> severities,
            List<String> services,
            String search
    ) {
        return logs.stream()
                .filter(log -> severities == null || severities.isEmpty()
                        || severities.stream().anyMatch(s -> s.equalsIgnoreCase(log.getSeverity())))
                .filter(log -> services == null || services.isEmpty()
                        || services.stream().anyMatch(s -> s.equalsIgnoreCase(log.getService())))
                .filter(log -> search == null || search.isEmpty()
                        || log.getMessage().toLowerCase().contains(search.toLowerCase()))
                .collect(Collectors.toList());
    }
}

