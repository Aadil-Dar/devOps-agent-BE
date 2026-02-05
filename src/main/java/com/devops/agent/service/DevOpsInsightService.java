package com.devops.agent.service;

import com.devops.agent.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for DevOps AI-assisted failure prediction and health monitoring
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DevOpsInsightService {

    private final ProjectConfigurationService projectConfigurationService;
    private final CloudWatchLogsClient cloudWatchLogsClient;
    private final CloudWatchClient cloudWatchClient;
    private final DynamoDbTable<LogSummary> logSummaryTable;
    private final DynamoDbTable<MetricSnapshot> metricSnapshotTable;
    private final DynamoDbTable<PredictionResult> predictionResultTable;
    private final WebClient ollamaWebClient;
    private final ObjectMapper objectMapper;

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(\\w+Exception|\\w+Error|Failed|Timeout|5\\d{2}\\s|Connection\\s+refused|Out\\s+of\\s+memory)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Perform health check and failure prediction for a project
     */
    public DevOpsHealthCheckResponse performHealthCheck(String projectId) {
        log.info("Starting health check for project: {}", projectId);

        // 1. Validate project exists
        ProjectConfiguration project = projectConfigurationService.getConfiguration(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (!Boolean.TRUE.equals(project.getEnabled())) {
            throw new IllegalArgumentException("Project is disabled: " + projectId);
        }

        // 2. Determine time window - from last processed timestamp or last hour
        long endTime = System.currentTimeMillis();
        long startTime = project.getLastProcessedTimestamp() != null
                ? project.getLastProcessedTimestamp()
                : endTime - (60 * 60 * 1000); // 1 hour in milliseconds

        // 3. Fetch and process CloudWatch logs
        List<FilteredLogEvent> rawLogs = fetchCloudWatchLogs(project, startTime, endTime);
        List<LogSummary> logSummaries = processLogs(projectId, rawLogs);

        // 4. Fetch CloudWatch metrics
        List<MetricSnapshot> metrics = fetchCloudWatchMetrics(project, startTime, endTime);

        // 5. Save log summaries and metrics to DynamoDB
        logSummaries.forEach(logSummaryTable::putItem);
        metrics.forEach(metricSnapshotTable::putItem);

        // 6. Analyze with AI
        AiAnalysisResult aiResult = performAiAnalysis(projectId, logSummaries, metrics);

        // 7. Generate predictions
        PredictionResult prediction = generatePrediction(projectId, logSummaries, metrics, aiResult);

        // 8. Save prediction to DynamoDB
        predictionResultTable.putItem(prediction);

        // 9. Update last processed timestamp
        project.setLastProcessedTimestamp(endTime);
        projectConfigurationService.updateConfiguration(projectId, project);

        // 10. Build and return response
        return buildHealthCheckResponse(prediction, metrics);
    }

    /**
     * Fetch logs from CloudWatch with error filtering
     */
    private List<FilteredLogEvent> fetchCloudWatchLogs(ProjectConfiguration project, long startTime, long endTime) {
        List<FilteredLogEvent> allLogs = new ArrayList<>();

        List<String> logGroupNames = project.getLogGroupNames();
        if (logGroupNames == null || logGroupNames.isEmpty()) {
            log.warn("No log groups configured for project: {}", project.getProjectId());
            return allLogs;
        }

        // Filter pattern to capture errors and warnings only
        String filterPattern = "?ERROR ?WARN ?Exception ?Timeout ?5xx";

        for (String logGroupName : logGroupNames) {
            try {
                FilterLogEventsRequest request = FilterLogEventsRequest.builder()
                        .logGroupName(logGroupName)
                        .startTime(startTime)
                        .endTime(endTime)
                        .filterPattern(filterPattern)
                        .limit(1000) // Limit per request
                        .build();

                FilterLogEventsResponse response = cloudWatchLogsClient.filterLogEvents(request);
                allLogs.addAll(response.events());

                log.info("Fetched {} logs from log group: {}", response.events().size(), logGroupName);
            } catch (CloudWatchLogsException e) {
                log.error("Error fetching logs from group {}: {}", logGroupName, e.getMessage());
            }
        }

        return allLogs;
    }

    /**
     * Process logs: normalize, group, deduplicate, calculate trends
     */
    private List<LogSummary> processLogs(String projectId, List<FilteredLogEvent> rawLogs) {
        Map<String, LogSummaryBuilder> summaryMap = new HashMap<>();

        for (FilteredLogEvent event : rawLogs) {
            String message = event.message();
            String service = extractServiceFromLogGroup(event.logStreamName());
            String severity = extractSeverity(message);
            String errorSignature = normalizeErrorSignature(message);

            String key = service + "#" + errorSignature + "#" + severity;

            LogSummaryBuilder builder = summaryMap.computeIfAbsent(key, k -> new LogSummaryBuilder());
            builder.addOccurrence(event.timestamp(), message);
            builder.service = service;
            builder.severity = severity;
            builder.errorSignature = errorSignature;
        }

        // Build final summaries with trend calculation
        List<LogSummary> summaries = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, LogSummaryBuilder> entry : summaryMap.entrySet()) {
            LogSummaryBuilder builder = entry.getValue();
            double trend = calculateTrend(builder.timestamps);

            LogSummary summary = LogSummary.builder()
                    .projectId(projectId)
                    .summaryId(entry.getKey() + "#" + currentTime)
                    .service(builder.service)
                    .errorSignature(builder.errorSignature)
                    .severity(builder.severity)
                    .occurrences(builder.count)
                    .firstSeenTimestamp(builder.firstSeen)
                    .lastSeenTimestamp(builder.lastSeen)
                    .sampleMessage(builder.sampleMessage)
                    .trendScore(trend)
                    .build();

            summaries.add(summary);
        }

        log.info("Processed {} logs into {} summaries", rawLogs.size(), summaries.size());
        return summaries;
    }

    /**
     * Helper class for building log summaries
     */
    private static class LogSummaryBuilder {
        String service;
        String severity;
        String errorSignature;
        int count = 0;
        Long firstSeen;
        Long lastSeen;
        String sampleMessage;
        List<Long> timestamps = new ArrayList<>();

        void addOccurrence(Long timestamp, String message) {
            count++;
            timestamps.add(timestamp);
            if (firstSeen == null || timestamp < firstSeen) {
                firstSeen = timestamp;
            }
            if (lastSeen == null || timestamp > lastSeen) {
                lastSeen = timestamp;
            }
            if (sampleMessage == null) {
                sampleMessage = message.length() > 500 ? message.substring(0, 500) : message;
            }
        }
    }

    /**
     * Extract service name from log stream or group
     */
    private String extractServiceFromLogGroup(String logStream) {
        // Example: /aws/ecs/prod/order-service or ecs/order-service/abc123
        if (logStream.contains("/")) {
            String[] parts = logStream.split("/");
            for (String part : parts) {
                if (part.contains("service") || part.contains("-api") || part.contains("-app")) {
                    return part;
                }
            }
            return parts[parts.length - 1];
        }
        return "unknown-service";
    }

    /**
     * Extract severity from log message
     */
    private String extractSeverity(String message) {
        if (message.toUpperCase().contains("ERROR") || message.toUpperCase().contains("EXCEPTION")) {
            return "ERROR";
        } else if (message.toUpperCase().contains("WARN")) {
            return "WARN";
        } else if (message.toUpperCase().contains("5XX") || message.contains(" 5")) {
            return "ERROR";
        }
        return "WARN";
    }

    /**
     * Normalize error signature by extracting the error pattern
     */
    private String normalizeErrorSignature(String message) {
        Matcher matcher = ERROR_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Fallback: extract first 50 chars
        return message.length() > 50 ? message.substring(0, 50).replaceAll("[^a-zA-Z0-9\\s]", "") : message;
    }

    /**
     * Calculate trend score (positive = increasing frequency, negative = decreasing)
     */
    private double calculateTrend(List<Long> timestamps) {
        if (timestamps.size() < 2) return 0.0;

        timestamps.sort(Long::compareTo);
        long totalTime = timestamps.get(timestamps.size() - 1) - timestamps.get(0);
        if (totalTime == 0) return 0.0;

        // Split into two halves and compare frequency
        int midpoint = timestamps.size() / 2;
        int firstHalfCount = midpoint;
        int secondHalfCount = timestamps.size() - midpoint;

        // Normalize by time to get rate
        double firstHalfRate = (double) firstHalfCount / (totalTime / 2.0);
        double secondHalfRate = (double) secondHalfCount / (totalTime / 2.0);

        return secondHalfRate - firstHalfRate;
    }

    /**
     * Fetch CloudWatch metrics (CPU, Memory, etc.)
     */
    private List<MetricSnapshot> fetchCloudWatchMetrics(ProjectConfiguration project, long startTime, long endTime) {
        List<MetricSnapshot> snapshots = new ArrayList<>();

        List<String> serviceNames = project.getServiceNames();
        if (serviceNames == null || serviceNames.isEmpty()) {
            log.warn("No service names configured for project: {}", project.getProjectId());
            return snapshots;
        }

        Instant start = Instant.ofEpochMilli(startTime);
        Instant end = Instant.ofEpochMilli(endTime);

        // Fetch CPU and Memory metrics for each service
        for (String serviceName : serviceNames) {
            snapshots.addAll(fetchMetricData(project, serviceName, "CPUUtilization", "AWS/ECS", start, end));
            snapshots.addAll(fetchMetricData(project, serviceName, "MemoryUtilization", "AWS/ECS", start, end));
        }

        log.info("Fetched {} metric snapshots", snapshots.size());
        return snapshots;
    }

    /**
     * Fetch specific metric data from CloudWatch
     */
    private List<MetricSnapshot> fetchMetricData(ProjectConfiguration project, String serviceName,
                                                  String metricName, String namespace,
                                                  Instant start, Instant end) {
        List<MetricSnapshot> snapshots = new ArrayList<>();

        try {
            Dimension serviceNameDimension = Dimension.builder()
                    .name("ServiceName")
                    .value(serviceName)
                    .build();

            Metric metric = Metric.builder()
                    .namespace(namespace)
                    .metricName(metricName)
                    .dimensions(serviceNameDimension)
                    .build();

            MetricStat metricStat = MetricStat.builder()
                    .metric(metric)
                    .period(300) // 5 minutes
                    .stat("Average")
                    .build();

            MetricDataQuery query = MetricDataQuery.builder()
                    .id("m1")
                    .metricStat(metricStat)
                    .build();

            GetMetricDataRequest request = GetMetricDataRequest.builder()
                    .startTime(start)
                    .endTime(end)
                    .metricDataQueries(query)
                    .build();

            GetMetricDataResponse response = cloudWatchClient.getMetricData(request);

            if (!response.metricDataResults().isEmpty()) {
                MetricDataResult result = response.metricDataResults().get(0);
                List<Double> values = result.values();
                List<Instant> timestamps = result.timestamps();

                for (int i = 0; i < values.size(); i++) {
                    MetricSnapshot snapshot = MetricSnapshot.builder()
                            .projectId(project.getProjectId())
                            .timestamp(timestamps.get(i).toEpochMilli())
                            .serviceName(serviceName)
                            .metricName(metricName)
                            .value(values.get(i))
                            .unit("Percent")
                            .dimensions(Map.of("ServiceName", serviceName))
                            .build();

                    snapshots.add(snapshot);
                }
            }
        } catch (CloudWatchException e) {
            log.error("Error fetching metric {} for service {}: {}", metricName, serviceName, e.getMessage());
        }

        return snapshots;
    }

    /**
     * Perform AI analysis using Ollama
     */
    private AiAnalysisResult performAiAnalysis(String projectId, List<LogSummary> logSummaries,
                                                List<MetricSnapshot> metrics) {
        try {
            String prompt = buildAiPrompt(logSummaries, metrics);

            OllamaGenerateRequest request = new OllamaGenerateRequest("qwen2.5-coder:7b", prompt, false);

            long start = System.currentTimeMillis();

            OllamaGenerateResponse ollamaResponse = ollamaWebClient
                    .post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .timeout(Duration.ofSeconds(90))
                    .doOnError(ex -> log.error("Error calling Ollama for project {}", projectId, ex))
                    .block();

            long end = System.currentTimeMillis();
            log.info("Ollama AI analysis took {} ms", (end - start));

            if (ollamaResponse == null || ollamaResponse.getResponse() == null) {
                throw new RuntimeException("Ollama returned empty response");
            }

            String aiJson = ollamaResponse.getResponse().trim();
            log.debug("Raw AI analysis response: {}", aiJson);

            return objectMapper.readValue(aiJson, AiAnalysisResult.class);

        } catch (Exception e) {
            log.error("Failed to perform AI analysis", e);
            // Return fallback analysis
            return new AiAnalysisResult(
                    "Unable to perform AI analysis",
                    "MEDIUM",
                    "Check system logs manually",
                    List.of("Review error logs", "Check system resources", "Monitor application health")
            );
        }
    }

    /**
     * Build AI prompt with log summaries and metrics
     */
    private String buildAiPrompt(List<LogSummary> logSummaries, List<MetricSnapshot> metrics) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a DevOps AI assistant analyzing system health and predicting failures.\n\n");
        prompt.append("Analyze the following data and respond with ONLY a JSON object (no markdown, no extra text):\n\n");

        // Add log summaries
        prompt.append("=== ERROR AND WARNING LOG SUMMARIES ===\n");
        if (logSummaries.isEmpty()) {
            prompt.append("No errors or warnings detected in the current time window.\n");
        } else {
            for (LogSummary summary : logSummaries) {
                prompt.append(String.format("- Service: %s, Error: %s, Severity: %s, Occurrences: %d, Trend: %.2f\n",
                        summary.getService(), summary.getErrorSignature(), summary.getSeverity(),
                        summary.getOccurrences(), summary.getTrendScore()));
                prompt.append(String.format("  Sample: %s\n", summary.getSampleMessage().substring(0,
                        Math.min(200, summary.getSampleMessage().length()))));
            }
        }

        // Add metrics
        prompt.append("\n=== SYSTEM METRICS ===\n");
        if (metrics.isEmpty()) {
            prompt.append("No metrics available.\n");
        } else {
            Map<String, List<MetricSnapshot>> metricsByService = metrics.stream()
                    .collect(Collectors.groupingBy(MetricSnapshot::getServiceName));

            for (Map.Entry<String, List<MetricSnapshot>> entry : metricsByService.entrySet()) {
                String serviceName = entry.getKey();
                List<MetricSnapshot> serviceMetrics = entry.getValue();

                Map<String, Double> avgMetrics = serviceMetrics.stream()
                        .collect(Collectors.groupingBy(
                                MetricSnapshot::getMetricName,
                                Collectors.averagingDouble(MetricSnapshot::getValue)
                        ));

                prompt.append(String.format("- Service: %s\n", serviceName));
                avgMetrics.forEach((metricName, avgValue) ->
                        prompt.append(String.format("  %s: %.2f%%\n", metricName, avgValue)));
            }
        }

        prompt.append("\n=== REQUIRED JSON RESPONSE FORMAT ===\n");
        prompt.append("{\n");
        prompt.append("  \"rootCause\": \"Brief root cause analysis (1-2 sentences)\",\n");
        prompt.append("  \"riskLevel\": \"LOW|MEDIUM|HIGH|CRITICAL\",\n");
        prompt.append("  \"summary\": \"Overall system health summary (2-3 sentences)\",\n");
        prompt.append("  \"recommendations\": [\"recommendation 1\", \"recommendation 2\", \"recommendation 3\"]\n");
        prompt.append("}\n\n");
        prompt.append("CRITICAL RULES:\n");
        prompt.append("1. Respond with VALID JSON ONLY\n");
        prompt.append("2. riskLevel must be one of: LOW, MEDIUM, HIGH, CRITICAL\n");
        prompt.append("3. If no errors and metrics are normal: riskLevel=LOW\n");
        prompt.append("4. If errors are increasing (positive trend): increase risk level\n");
        prompt.append("5. If CPU/Memory > 80%: risk level should be at least MEDIUM\n");
        prompt.append("6. Provide exactly 3 actionable recommendations\n");

        return prompt.toString();
    }

    /**
     * AI analysis result structure
     */
    private record AiAnalysisResult(
            String rootCause,
            String riskLevel,
            String summary,
            List<String> recommendations
    ) {}

    /**
     * Generate prediction based on logs, metrics, and AI analysis
     */
    private PredictionResult generatePrediction(String projectId, List<LogSummary> logSummaries,
                                                 List<MetricSnapshot> metrics, AiAnalysisResult aiResult) {
        // Calculate counts
        int errorCount = (int) logSummaries.stream()
                .filter(s -> "ERROR".equals(s.getSeverity()))
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        int warningCount = (int) logSummaries.stream()
                .filter(s -> "WARN".equals(s.getSeverity()))
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        int totalLogCount = logSummaries.stream().mapToInt(LogSummary::getOccurrences).sum();

        // Calculate failure likelihood based on multiple factors
        double likelihood = calculateFailureLikelihood(logSummaries, metrics, aiResult.riskLevel());

        // Determine timeframe based on risk level and likelihood
        String timeframe = determineTimeframe(aiResult.riskLevel(), likelihood, logSummaries);

        return PredictionResult.builder()
                .projectId(projectId)
                .timestamp(System.currentTimeMillis())
                .riskLevel(aiResult.riskLevel())
                .summary(aiResult.summary())
                .recommendations(aiResult.recommendations())
                .predictionTimeframe(timeframe)
                .failureLikelihood(likelihood)
                .rootCause(aiResult.rootCause())
                .logCount(totalLogCount)
                .errorCount(errorCount)
                .warningCount(warningCount)
                .build();
    }

    /**
     * Calculate failure likelihood (0.0 to 1.0)
     */
    private double calculateFailureLikelihood(List<LogSummary> logSummaries, List<MetricSnapshot> metrics,
                                               String riskLevel) {
        double likelihood = 0.0;

        // Base likelihood from risk level
        switch (riskLevel) {
            case "CRITICAL" -> likelihood = 0.9;
            case "HIGH" -> likelihood = 0.7;
            case "MEDIUM" -> likelihood = 0.4;
            case "LOW" -> likelihood = 0.1;
        }

        // Adjust based on error trends
        double avgTrend = logSummaries.stream()
                .mapToDouble(LogSummary::getTrendScore)
                .average()
                .orElse(0.0);

        if (avgTrend > 0) {
            likelihood += 0.1; // Increasing errors
        }

        // Adjust based on high metrics
        double avgCpu = metrics.stream()
                .filter(m -> "CPUUtilization".equals(m.getMetricName()))
                .mapToDouble(MetricSnapshot::getValue)
                .average()
                .orElse(0.0);

        if (avgCpu > 80) {
            likelihood += 0.15;
        }

        return Math.min(1.0, Math.max(0.0, likelihood));
    }

    /**
     * Determine failure timeframe
     */
    private String determineTimeframe(String riskLevel, double likelihood, List<LogSummary> logSummaries) {
        boolean hasIncreasingErrors = logSummaries.stream()
                .anyMatch(s -> s.getTrendScore() > 0.5);

        if (riskLevel.equals("CRITICAL") && likelihood > 0.8) {
            return "within 1-2 hours";
        } else if (riskLevel.equals("HIGH") || (riskLevel.equals("CRITICAL") && hasIncreasingErrors)) {
            return "within 4-6 hours";
        } else if (riskLevel.equals("MEDIUM")) {
            return "within 12-24 hours";
        } else {
            return "low risk, no immediate failure expected";
        }
    }

    /**
     * Build final health check response
     */
    private DevOpsHealthCheckResponse buildHealthCheckResponse(PredictionResult prediction,
                                                                 List<MetricSnapshot> metrics) {
        // Group metrics by service and calculate trends
        List<DevOpsHealthCheckResponse.MetricTrend> metricTrends = buildMetricTrends(metrics);

        DevOpsHealthCheckResponse.PredictionDetails predictionDetails =
                DevOpsHealthCheckResponse.PredictionDetails.builder()
                        .timeframe(prediction.getPredictionTimeframe())
                        .likelihood(prediction.getFailureLikelihood())
                        .rootCause(prediction.getRootCause())
                        .build();

        return DevOpsHealthCheckResponse.builder()
                .riskLevel(prediction.getRiskLevel())
                .summary(prediction.getSummary())
                .recommendations(prediction.getRecommendations())
                .predictions(predictionDetails)
                .logCount(prediction.getLogCount())
                .errorCount(prediction.getErrorCount())
                .warningCount(prediction.getWarningCount())
                .metricTrends(metricTrends)
                .timestamp(prediction.getTimestamp())
                .build();
    }

    /**
     * Build metric trends for response
     */
    private List<DevOpsHealthCheckResponse.MetricTrend> buildMetricTrends(List<MetricSnapshot> metrics) {
        Map<String, Map<String, List<MetricSnapshot>>> grouped = metrics.stream()
                .collect(Collectors.groupingBy(
                        MetricSnapshot::getServiceName,
                        Collectors.groupingBy(MetricSnapshot::getMetricName)
                ));

        List<DevOpsHealthCheckResponse.MetricTrend> trends = new ArrayList<>();

        for (Map.Entry<String, Map<String, List<MetricSnapshot>>> serviceEntry : grouped.entrySet()) {
            String serviceName = serviceEntry.getKey();

            for (Map.Entry<String, List<MetricSnapshot>> metricEntry : serviceEntry.getValue().entrySet()) {
                String metricName = metricEntry.getKey();
                List<MetricSnapshot> snapshots = metricEntry.getValue();

                if (snapshots.isEmpty()) continue;

                snapshots.sort(Comparator.comparing(MetricSnapshot::getTimestamp));

                double currentValue = snapshots.get(snapshots.size() - 1).getValue();
                double averageValue = snapshots.stream()
                        .mapToDouble(MetricSnapshot::getValue)
                        .average()
                        .orElse(0.0);

                String trend = determineTrend(snapshots);

                trends.add(DevOpsHealthCheckResponse.MetricTrend.builder()
                        .serviceName(serviceName)
                        .metricName(metricName)
                        .currentValue(currentValue)
                        .averageValue(averageValue)
                        .trend(trend)
                        .unit(snapshots.get(0).getUnit())
                        .build());
            }
        }

        return trends;
    }

    /**
     * Determine metric trend direction
     */
    private String determineTrend(List<MetricSnapshot> snapshots) {
        if (snapshots.size() < 2) return "STABLE";

        int midpoint = snapshots.size() / 2;
        double firstHalfAvg = snapshots.subList(0, midpoint).stream()
                .mapToDouble(MetricSnapshot::getValue)
                .average()
                .orElse(0.0);

        double secondHalfAvg = snapshots.subList(midpoint, snapshots.size()).stream()
                .mapToDouble(MetricSnapshot::getValue)
                .average()
                .orElse(0.0);

        double change = secondHalfAvg - firstHalfAvg;

        if (Math.abs(change) < 5.0) return "STABLE";
        return change > 0 ? "INCREASING" : "DECREASING";
    }
}
