package com.devops.agent.service;

import com.devops.agent.model.*;
import com.devops.agent.util.CloudWatchLogsUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudWatchLogsService {

    private final CloudWatchLogsClient cloudWatchLogsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProjectConfigurationService projectConfigurationService;
    private final SecretsManagerService secretsManagerService;

    private static final String DEFAULT_LOG_GROUP = "/ecs/eptBackendApp";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final Pattern SEVERITY_PATTERN = Pattern.compile("\\b(ERROR|WARN|INFO|DEBUG)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("(?:requestId|traceId|trace-id|request-id)[:\\s=]([a-zA-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVICE_PATTERN = Pattern.compile("(?:service|application)[:\\s=]([a-zA-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENDPOINT_PATTERN = Pattern.compile("\\b(?:GET|POST|PUT|DELETE)\\s+([^\\s]+)");
    private static final Pattern LATENCY_PATTERN = Pattern.compile("(\\d+)(?=ms)");
    private static final Map<String, Integer> SEVERITY_RANKS = Map.of(
            "ERROR", 4,
            "WARN", 3,
            "INFO", 2,
            "DEBUG", 1
    );

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
            String projectId,
            int page,
            int size
    ) {
        log.info("Fetching logs - timeRange: {}, environment: {}, severities: {}, services: {}, search: {}, clusterId: {}, page: {}, size: {}",
                timeRange, environment, severities, services, search, clusterId, page, size);

        try {
            var filteredLogs = fetchFilteredLogs(timeRange, environment, severities, services, search, clusterId, projectId);

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

    public List<ClusterLogEntry> getLogClusters(
            String timeRange,
            String environment,
            List<String> severities,
            List<String> services,
            String search,
            String clusterId,
            String projectId
    ) {
        var logs = fetchFilteredLogs(timeRange, environment, severities, services, search, clusterId, projectId);
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<LogEntry>> grouped = logs.stream()
                .collect(Collectors.groupingBy(log -> buildClusterTitle(log.getMessage(), log.getSeverity())));

        var idCounter = new AtomicInteger(1);

        return grouped.entrySet().stream()
                .map(entry -> buildClusterEntry("c" + idCounter.getAndIncrement(), entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(ClusterLogEntry::getCount).reversed())
                .toList();
    }

    public LogSummaryResponse summarizeLogs(
            String timeRange,
            String environment,
            List<String> severities,
            List<String> services,
            String search,
            String clusterId,
            String projectId
    ) {
        var logs = fetchFilteredLogs(timeRange, environment, severities, services, search, clusterId, projectId);
        long total = logs.size();
        long errors = logs.stream().filter(l -> "ERROR".equalsIgnoreCase(l.getSeverity())).count();
        long warns = logs.stream().filter(l -> "WARN".equalsIgnoreCase(l.getSeverity())).count();
        long infos = logs.stream().filter(l -> "INFO".equalsIgnoreCase(l.getSeverity())).count();
        long debugs = logs.stream().filter(l -> "DEBUG".equalsIgnoreCase(l.getSeverity())).count();

        Map<String, Long> serviceCounts = logs.stream()
                .collect(Collectors.groupingBy(l -> Optional.ofNullable(l.getService()).orElse("unknown-service"), Collectors.counting()));

        String topService = serviceCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown-service");

        Set<String> uniqueClusters = logs.stream()
                .map(l -> buildClusterTitle(l.getMessage(), l.getSeverity()))
                .collect(Collectors.toSet());

        int severityScore = (int) Math.max(0, Math.min(100, (errors * 10) + (warns * 3)));

        var timeline = buildTimeline(logs);

        return LogSummaryResponse.builder()
                .totalLogs(total)
                .errorCount(errors)
                .warnCount(warns)
                .infoCount(infos)
                .debugCount(debugs)
                .uniqueClusters(uniqueClusters.size())
                .severityScore(severityScore)
                .topService(topService)
                .timelineData(timeline)
                .build();
    }

    public SystemHealthReport getSystemHealthReport(String environment, String projectId) {
        var logs = fetchFilteredLogs("6h", environment, null, null, null, null, projectId);
        Instant now = Instant.now();

        Map<String, List<LogEntry>> logsByService = logs.stream()
                .collect(Collectors.groupingBy(l -> Optional.ofNullable(l.getService()).orElse("unknown-service")));

        List<TopFailingService> topFailingServices = logsByService.entrySet().stream()
                .map(entry -> buildFailingService(entry.getKey(), entry.getValue(), now))
                .sorted(Comparator.comparingLong(TopFailingService::getFailureCount).reversed())
                .limit(5)
                .toList();

        List<ErrorTrend> errorTrends = buildErrorTrends(logs, now);
        List<SlowApi> slowApis = detectSlowApis(logs);
        List<PredictedFailure> predictedFailures = buildPredictedFailures(topFailingServices, slowApis);
        List<Recommendation> recommendations = buildRecommendations(predictedFailures);

        return SystemHealthReport.builder()
                .topFailingServices(topFailingServices)
                .errorTrends(errorTrends)
                .slowApis(slowApis)
                .predictedFailures(predictedFailures)
                .recommendations(recommendations)
                .build();
    }

    private List<LogEntry> fetchFilteredLogs(
            String timeRange,
            String environment,
            List<String> severities,
            List<String> services,
            String search,
            String clusterId,
            String projectId
    ) {
        if (projectId != null && !projectId.isBlank()) {
            try (CloudWatchLogsClient projectClient = createProjectCloudWatchClient(projectId)) {
                return fetchFilteredLogsWithClient(projectClient, timeRange, environment, severities, services, search, clusterId);
            }
        }
        return fetchFilteredLogsWithClient(cloudWatchLogsClient, timeRange, environment, severities, services, search, clusterId);
    }

    private List<LogEntry> fetchFilteredLogsWithClient(
            CloudWatchLogsClient client,
            String timeRange,
            String environment,
            List<String> severities,
            List<String> services,
            String search,
            String clusterId
    ) {
        long[] timeRangeMillis = calculateTimeRange(timeRange);
        long startTime = timeRangeMillis[0];
        long endTime = timeRangeMillis[1];

        List<String> logGroupNames = getLogGroupNames(client, environment, clusterId);

        if (logGroupNames.isEmpty()) {
            log.warn("No log groups found for environment: {} and clusterId: {}", environment, clusterId);
            return Collections.emptyList();
        }

        List<LogEntry> allLogs = new ArrayList<>();
        for (String logGroupName : logGroupNames) {
            List<LogEntry> groupLogs = fetchLogsFromGroup(client, logGroupName, startTime, endTime);
            allLogs.addAll(groupLogs);
        }

        return filterLogs(allLogs, severities, services, search).stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .toList();
    }

    /**
     * Calculate time range in milliseconds based on timeRange string
     */
    private long[] calculateTimeRange(String timeRange) {
        long endTime = System.currentTimeMillis();
        long startTime;

        if (timeRange == null || timeRange.isEmpty()) {
            timeRange = "6h"; // default to last 6 hours
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
                startTime = Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli();
        }

        return new long[]{startTime, endTime};
    }

    private CloudWatchLogsClient createProjectCloudWatchClient(String projectId) {
        ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new RuntimeException("Project is disabled: " + projectId);
        }

        Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
        String awsAccessKey = secrets.get("aws-access-key");
        String awsSecretKey = secrets.get("aws-secret-key");
        Region region = Region.of(config.getAwsRegion() != null ? config.getAwsRegion() : "eu-west-1");

        if (awsAccessKey != null && awsSecretKey != null && !awsAccessKey.isBlank() && !awsSecretKey.isBlank()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
            return CloudWatchLogsClient.builder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        }

        return CloudWatchLogsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Get log group names based on environment and clusterId
     */
    private List<String> getLogGroupNames(CloudWatchLogsClient client, String environment, String clusterId) {
        try {
            List<String> logGroups = new ArrayList<>();
            logGroups.add(DEFAULT_LOG_GROUP);

            DescribeLogGroupsResponse response = client.describeLogGroups(
                    DescribeLogGroupsRequest.builder().logGroupNamePrefix("/ecs/").build());

            response.logGroups().stream()
                    .map(LogGroup::logGroupName)
                    .filter(name -> name.contains(DEFAULT_LOG_GROUP))
                    .filter(name -> clusterId == null || clusterId.isEmpty() || name.contains(clusterId))
                    .forEach(logGroups::add);

            return logGroups.stream().distinct().toList();

        } catch (CloudWatchLogsException e) {
            log.warn("Error fetching log groups, falling back to default {}: {}", DEFAULT_LOG_GROUP, e.getMessage());
            return List.of(DEFAULT_LOG_GROUP);
        }
    }

    /**
     * Fetch logs from a specific log group
     */
    private List<LogEntry> fetchLogsFromGroup(CloudWatchLogsClient client, String logGroupName, long startTime, long endTime) {
        List<LogEntry> logs = new ArrayList<>();

        try {
            // Use shared utility for reliable log fetching
            List<OutputLogEvent> events = CloudWatchLogsUtil.fetchLogsFromGroup(
                client, logGroupName, startTime, endTime,
                10, // Max 10 most recent streams
                100  // Max 100 events per stream
            );

            for (OutputLogEvent event : events) {
                LogEntry logEntry = parseLogEvent(event, logGroupName, "stream-" + event.hashCode());
                logs.add(logEntry);
            }

        } catch (Exception e) {
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

    private List<LogSummaryTimelinePoint> buildTimeline(List<LogEntry> logs) {
        if (logs.isEmpty()) {
            return Collections.nCopies(6, LogSummaryTimelinePoint.builder()
                    .time(Instant.now().minus(6, ChronoUnit.HOURS).toString())
                    .count(0)
                    .topErrors(Collections.emptyList())
                    .build());
        }

        Instant end = Instant.now();
        Instant start = end.minus(6, ChronoUnit.HOURS);

        List<LogSummaryTimelinePoint> points = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            Instant bucketStart = start.plus(i, ChronoUnit.HOURS);
            Instant bucketEnd = bucketStart.plus(1, ChronoUnit.HOURS);

            List<LogEntry> bucketLogs = logs.stream()
                    .filter(l -> {
                        Instant ts = parseTimestamp(l.getTimestamp());
                        return !ts.isBefore(bucketStart) && ts.isBefore(bucketEnd);
                    })
                    .toList();

            Map<String, Long> errorCounts = bucketLogs.stream()
                    .filter(l -> "ERROR".equalsIgnoreCase(l.getSeverity()))
                    .collect(Collectors.groupingBy(l -> buildClusterTitle(l.getMessage(), l.getSeverity()), Collectors.counting()));

            List<String> topErrors = errorCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(2)
                    .map(Map.Entry::getKey)
                    .toList();

            points.add(LogSummaryTimelinePoint.builder()
                    .time(bucketStart.toString())
                    .count(bucketLogs.size())
                    .topErrors(topErrors)
                    .build());
        }

        return points;
    }

    private ClusterLogEntry buildClusterEntry(String id, String title, List<LogEntry> logs) {
        List<LogEntry> sorted = logs.stream()
                .sorted(Comparator.comparing(l -> parseTimestamp(l.getTimestamp())))
                .toList();

        Set<String> affectedHosts = sorted.stream()
                .map(LogEntry::getHost)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> affectedServices = sorted.stream()
                .map(LogEntry::getService)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<SampleLog> sampleLogs = sorted.stream()
                .limit(3)
                .map(l -> SampleLog.builder()
                        .timestamp(l.getTimestamp())
                        .message(l.getMessage())
                        .host(l.getHost())
                        .requestId(l.getRequestId())
                        .build())
                .toList();

        String severity = sorted.stream()
                .map(LogEntry::getSeverity)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(this::severityRank))
                .orElse("INFO");

        return ClusterLogEntry.builder()
                .id(id)
                .title(title)
                .count(sorted.size())
                .firstSeen(sorted.getFirst().getTimestamp())
                .lastSeen(sorted.getLast().getTimestamp())
                .affectedHosts(new ArrayList<>(affectedHosts))
                .affectedServices(new ArrayList<>(affectedServices))
                .sampleLogs(sampleLogs)
                .severity(severity)
                .build();
    }

    private TopFailingService buildFailingService(String service, List<LogEntry> logs, Instant now) {
        long total = logs.size();
        long errors = logs.stream().filter(l -> "ERROR".equalsIgnoreCase(l.getSeverity())).count();
        long criticalErrors = logs.stream()
                .filter(l -> "ERROR".equalsIgnoreCase(l.getSeverity()) || "WARN".equalsIgnoreCase(l.getSeverity()))
                .count();

        double failureRate = total == 0 ? 0 : (errors * 100.0) / total;

        Instant lastFailureTs = logs.stream()
                .filter(l -> "ERROR".equalsIgnoreCase(l.getSeverity()))
                .map(l -> parseTimestamp(l.getTimestamp()))
                .max(Comparator.naturalOrder())
                .orElse(null);

        Trend trend = calculateTrend(logs, now);

        String status;
        if (failureRate >= 20 || errors > 50) {
            status = "critical";
        } else if (failureRate >= 5 || errors > 10) {
            status = "warning";
        } else {
            status = "stable";
        }

        return TopFailingService.builder()
                .name(service)
                .failureCount(errors)
                .failureRate(round(failureRate))
                .trend(trend.direction())
                .trendValue(trend.change())
                .lastFailure(lastFailureTs != null ? humanizeDuration(Duration.between(lastFailureTs, now)) : "No recent failures")
                .criticalErrors(criticalErrors)
                .status(status)
                .build();
    }

    private List<ErrorTrend> buildErrorTrends(List<LogEntry> logs, Instant now) {
        Instant lastHour = now.minus(1, ChronoUnit.HOURS);
        Instant prevHour = now.minus(2, ChronoUnit.HOURS);
        Instant lastSixHours = now.minus(6, ChronoUnit.HOURS);

        long lastHourErrors = countBySeverityAndRange(logs, "ERROR", lastHour, now);
        long lastHourWarnings = countBySeverityAndRange(logs, "WARN", lastHour, now);
        long prevHourErrors = countBySeverityAndRange(logs, "ERROR", prevHour, lastHour);
        long prevHourWarnings = countBySeverityAndRange(logs, "WARN", prevHour, lastHour);

        Trend hourTrend = computeChange(lastHourErrors + lastHourWarnings, prevHourErrors + prevHourWarnings);

        long sixHourErrors = countBySeverityAndRange(logs, "ERROR", lastSixHours, now);
        long sixHourWarnings = countBySeverityAndRange(logs, "WARN", lastSixHours, now);
        long previousSixHourErrors = countBySeverityAndRange(logs, "ERROR", lastSixHours.minus(6, ChronoUnit.HOURS), lastSixHours);
        long previousSixHourWarnings = countBySeverityAndRange(logs, "WARN", lastSixHours.minus(6, ChronoUnit.HOURS), lastSixHours);

        Trend sixHourTrend = computeChange(sixHourErrors + sixHourWarnings, previousSixHourErrors + previousSixHourWarnings);

        String peakTime = determinePeakHour(logs, lastSixHours, now);

        return List.of(
                ErrorTrend.builder()
                        .timeframe("Last Hour")
                        .errors(lastHourErrors)
                        .warnings(lastHourWarnings)
                        .change(formatChange(hourTrend.change()))
                        .severity(hourTrend.direction().equals("up") && (lastHourErrors > 50 || lastHourWarnings > 100) ? "high" : "medium")
                        .peakTime(peakTime)
                        .build(),
                ErrorTrend.builder()
                        .timeframe("Last 6 Hours")
                        .errors(sixHourErrors)
                        .warnings(sixHourWarnings)
                        .change(formatChange(sixHourTrend.change()))
                        .severity(sixHourTrend.direction().equals("up") && (sixHourErrors > 150 || sixHourWarnings > 300) ? "high" : "medium")
                        .peakTime(peakTime)
                        .build()
        );
    }

    private List<SlowApi> detectSlowApis(List<LogEntry> logs) {
        Map<String, List<LogEntry>> byEndpoint = logs.stream()
                .map(log -> new AbstractMap.SimpleEntry<>(extractEndpoint(log.getMessage()), log))
                .filter(e -> e.getKey() != null)
                .collect(Collectors.groupingBy(AbstractMap.SimpleEntry::getKey, Collectors.mapping(AbstractMap.SimpleEntry::getValue, Collectors.toList())));

        return byEndpoint.entrySet().stream()
                .map(entry -> buildSlowApi(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(SlowApi::getP95ResponseTime).reversed())
                .limit(5)
                .toList();
    }

    private List<PredictedFailure> buildPredictedFailures(List<TopFailingService> failingServices, List<SlowApi> slowApis) {
        List<PredictedFailure> predictions = new ArrayList<>();

        for (TopFailingService service : failingServices) {
            if (!"critical".equals(service.getStatus()) && service.getTrendValue() < 10) {
                continue;
            }
            String prediction = "Error rate trending " + service.getTrend() + ", likely incident if unmitigated";
            predictions.add(PredictedFailure.builder()
                    .service(service.getName())
                    .prediction(prediction)
                    .probability(Math.min(100, (int) Math.round(service.getFailureRate() + service.getTrendValue())))
                    .timeframe("Within 15 minutes")
                    .impact(service.getFailureRate() > 20 ? "High" : "Medium")
                    .affectedUsers("~" + Math.max(100, service.getFailureCount() * 50))
                    .preventiveAction("Throttle retries and scale " + service.getName())
                    .severity(service.getFailureRate() > 20 ? "critical" : "high")
                    .build());
        }

        for (SlowApi api : slowApis) {
            if (api.getP99ResponseTime() > 5000 || api.getErrorRate() > 5) {
                predictions.add(PredictedFailure.builder()
                        .service(api.getEndpoint())
                        .prediction("High latency may trigger downstream timeouts")
                        .probability(80)
                        .timeframe("Within 30 minutes")
                        .impact("High")
                        .affectedUsers("~" + Math.max(200, api.getRequestCount() / 2))
                        .preventiveAction("Scale pods in " + api.getSlowestRegion())
                        .severity(api.getStatus())
                        .build());
            }
        }

        return predictions.stream()
                .sorted(Comparator.comparingInt(PredictedFailure::getProbability).reversed())
                .limit(5)
                .toList();
    }

    private List<Recommendation> buildRecommendations(List<PredictedFailure> predictedFailures) {
        if (predictedFailures.isEmpty()) {
            return List.of(Recommendation.builder()
                    .title("System stable")
                    .priority("medium")
                    .impact("No critical risks detected")
                    .effort("Low")
                    .estimatedTime("10 minutes")
                    .category("Stability")
                    .steps(List.of("Continue monitoring", "Validate alerts are firing"))
                    .roi("Maintains uptime")
                    .build());
        }

        return predictedFailures.stream()
                .map(p -> Recommendation.builder()
                        .title("Mitigate " + p.getService())
                        .priority(p.getSeverity())
                        .impact(p.getImpact() + " impact if unaddressed")
                        .effort("Low")
                        .estimatedTime("15 minutes")
                        .category("Reliability")
                        .steps(List.of(
                                "Rate-limit noisy callers for " + p.getService(),
                                "Add one replica or scale infrastructure",
                                "Flush connection pool and verify health checks"
                        ))
                        .roi("Prevents ~" + p.getProbability() + "% of predicted incidents")
                        .build())
                .toList();
    }

    private Trend calculateTrend(List<LogEntry> logs, Instant now) {
        Instant lastHour = now.minus(1, ChronoUnit.HOURS);
        Instant prevHour = now.minus(2, ChronoUnit.HOURS);

        long current = logs.stream()
                .filter(l -> "ERROR".equalsIgnoreCase(l.getSeverity()))
                .filter(l -> !parseTimestamp(l.getTimestamp()).isBefore(lastHour))
                .count();
        long previous = logs.stream()
                .filter(l -> "ERROR".equalsIgnoreCase(l.getSeverity()))
                .filter(l -> {
                    Instant ts = parseTimestamp(l.getTimestamp());
                    return !ts.isBefore(prevHour) && ts.isBefore(lastHour);
                })
                .count();

        return computeChange(current, previous);
    }

    private Trend computeChange(long current, long previous) {
        if (previous == 0 && current == 0) {
            return new Trend("stable", 0);
        }
        if (previous == 0) {
            return new Trend("up", 100);
        }
        double change = ((double) (current - previous) / previous) * 100;
        String direction = change > 5 ? "up" : change < -5 ? "down" : "stable";
        return new Trend(direction, Math.abs(round(change)));
    }

    private long countBySeverityAndRange(List<LogEntry> logs, String severity, Instant start, Instant end) {
        return logs.stream()
                .filter(l -> severity.equalsIgnoreCase(l.getSeverity()))
                .filter(l -> {
                    Instant ts = parseTimestamp(l.getTimestamp());
                    return !ts.isBefore(start) && ts.isBefore(end);
                })
                .count();
    }

    private String determinePeakHour(List<LogEntry> logs, Instant start, Instant end) {
        Map<Instant, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            Instant bucketStart = start.plus(i, ChronoUnit.HOURS);
            Instant bucketEnd = bucketStart.plus(1, ChronoUnit.HOURS);
            long count = logs.stream()
                    .filter(l -> {
                        Instant ts = parseTimestamp(l.getTimestamp());
                        return !ts.isBefore(bucketStart) && ts.isBefore(bucketEnd) && "ERROR".equalsIgnoreCase(l.getSeverity());
                    })
                    .count();
            counts.put(bucketStart, count);
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(e.getKey()))
                .orElse("00:00");
    }

    private SlowApi buildSlowApi(String endpoint, List<LogEntry> logs) {
        List<Long> latencies = logs.stream()
                .map(l -> parseLatency(l.getMessage()))
                .filter(OptionalLong::isPresent)
                .map(OptionalLong::getAsLong)
                .sorted()
                .toList();

        long avg = latencies.isEmpty() ? 1200 : (long) latencies.stream().mapToLong(Long::longValue).average().orElse(1200);
        long p95 = latencies.isEmpty() ? avg + 800 : percentile(latencies, 0.95);
        long p99 = latencies.isEmpty() ? avg + 1500 : percentile(latencies, 0.99);

        long requestCount = logs.size();
        double errorRate = logs.stream().filter(l -> "ERROR".equalsIgnoreCase(l.getSeverity())).count() * 100.0 / Math.max(1, requestCount);

        String status;
        if (p95 > 4000 || errorRate > 10) {
            status = "critical";
        } else if (p95 > 2500 || errorRate > 5) {
            status = "warning";
        } else {
            status = "healthy";
        }

        return SlowApi.builder()
                .endpoint(endpoint)
                .avgResponseTime(avg)
                .p95ResponseTime(p95)
                .p99ResponseTime(p99)
                .requestCount(requestCount)
                .errorRate(round(errorRate))
                .status(status)
                .slowestRegion("us-east-1")
                .build();
    }

    private OptionalLong parseLatency(String message) {
        Matcher matcher = LATENCY_PATTERN.matcher(message);
        if (matcher.find()) {
            try {
                return OptionalLong.of(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return OptionalLong.empty();
    }

    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        index = Math.min(Math.max(index, 0), values.size() - 1);
        return values.get(index);
    }

    private String buildClusterTitle(String message, String severity) {
        if (message == null || message.isBlank()) {
            return severity + " log";
        }

        Matcher matcher = Pattern.compile("(\\w+Exception|\\w+Error)").matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        String cleaned = message
                .replaceAll("\\d{4}-\\d{2}-\\d{2}", "")
                .replaceAll("\\d{2}:\\d{2}:\\d{2}", "")
                .replaceAll("\\b(ERROR|WARN|INFO|DEBUG)\\b", "")
                .strip();

        int endIndex = Math.min(cleaned.length(), 60);
        String title = cleaned.substring(0, endIndex).strip();
        if (title.isEmpty()) {
            title = severity + " issue";
        }
        return title;
    }

    private String extractEndpoint(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = ENDPOINT_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private int severityRank(String severity) {
        return SEVERITY_RANKS.getOrDefault(severity == null ? "INFO" : severity.toUpperCase(), 1);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String formatChange(double change) {
        String sign = change > 0 ? "+" : "";
        return sign + round(change) + "%";
    }

    private String humanizeDuration(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " mins ago";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + " hours ago";
        }
        long days = duration.toDays();
        return days + " days ago";
    }

    private record Trend(String direction, double change) {}
}
