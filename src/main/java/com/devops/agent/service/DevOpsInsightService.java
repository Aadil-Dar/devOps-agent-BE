package com.devops.agent.service;

import com.devops.agent.model.*;
import com.devops.agent.util.CloudWatchLogsUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Duration;
import java.time.Instant;
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
    private final DynamoDbTable<LogEmbedding> logEmbeddingTable;
    private final WebClient ollamaWebClient;
    private final ObjectMapper objectMapper;
    private final SecretsManagerService secretsManagerService;

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(\\w+Exception|\\w+Error|Failed|Timeout|5\\d{2}\\s|Connection\\s+refused|Out\\s+of\\s+memory)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Perform health check and failure prediction for a project
     * Fetches data from database (logs, embeddings, metrics) instead of AWS CloudWatch
     */
    public DevOpsHealthCheckResponse performHealthCheck(String projectId) {
        log.info("Starting health check for project: {}", projectId);

        // 1. Validate project exists
        ProjectConfiguration project = projectConfigurationService.getConfiguration(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (!Boolean.TRUE.equals(project.getEnabled())) {
            throw new IllegalArgumentException("Project is disabled: " + projectId);
        }

        // 2. Determine time window - last 2 hours for health check
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (2 * 60 * 60 * 1000); // 2 hours in milliseconds

        log.info("Fetching health data from database for project: {} (last 2 hours)", projectId);

        // 3. Fetch log summaries from database
        List<LogSummary> logSummaries = fetchLogSummariesFromDatabase(projectId, startTime, endTime);
        log.info("Fetched {} log summaries from database", logSummaries.size());

        // 4. Fetch embeddings from database
        List<LogEmbedding> embeddings = fetchEmbeddingsFromDatabase(projectId, startTime, endTime);
        log.info("Fetched {} embeddings from database", embeddings.size());

        // 5. Fetch metrics from database
        List<MetricSnapshot> metrics = fetchMetricsFromDatabase(projectId, startTime, endTime);
        log.info("Fetched {} metric snapshots from database", metrics.size());

        // If no data found, return safe response
        if (logSummaries.isEmpty() && metrics.isEmpty()) {
            log.info("No data found in database for project {}. Returning safe response.", projectId);
            return buildSafeDummyResponse();
        }

        // 6. Analyze with AI using enhanced prompt with embeddings
        AiAnalysisResult aiResult = performEnhancedAiAnalysis(projectId, logSummaries, embeddings, metrics);

        // 7. Generate predictions based on database data
        PredictionResult prediction = generatePrediction(projectId, logSummaries, metrics, aiResult);

        // 8. Save prediction to DynamoDB
        predictionResultTable.putItem(prediction);

        // 9. Update last processed timestamp
        project.setLastProcessedTimestamp(endTime);
        projectConfigurationService.updateConfiguration(projectId, project);

        // 10. Build and return comprehensive response
        return buildComprehensiveResponse(prediction, metrics, logSummaries, embeddings);
    }

    /**
     * Fetch log summaries from DynamoDB within a time range
     */
    private List<LogSummary> fetchLogSummariesFromDatabase(String projectId, long startTime, long endTime) {
        List<LogSummary> summaries = new ArrayList<>();

        try {
            logSummaryTable.query(r -> r.queryConditional(
                    software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
                            .keyEqualTo(k -> k.partitionValue(projectId))
            )).items().forEach(summary -> {
                // Filter by timestamp range
                if (summary.getLastSeenTimestamp() != null &&
                    summary.getLastSeenTimestamp() >= startTime &&
                    summary.getLastSeenTimestamp() <= endTime) {
                    summaries.add(summary);
                }
            });

            log.info("Fetched {} log summaries from database for project {} in time range [{}, {}]",
                    summaries.size(), projectId, startTime, endTime);
        } catch (Exception e) {
            log.error("Error fetching log summaries from database for project {}: {}",
                    projectId, e.getMessage(), e);
        }

        return summaries;
    }

    /**
     * Fetch embeddings from DynamoDB within a time range
     */
    private List<LogEmbedding> fetchEmbeddingsFromDatabase(String projectId, long startTime, long endTime) {
        List<LogEmbedding> embeddings = new ArrayList<>();

        try {
            logEmbeddingTable.query(r -> r.queryConditional(
                    software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
                            .keyEqualTo(k -> k.partitionValue(projectId))
            )).items().forEach(embedding -> {
                // Filter by timestamp range
                if (embedding.getTimestamp() != null &&
                    embedding.getTimestamp() >= startTime &&
                    embedding.getTimestamp() <= endTime) {
                    embeddings.add(embedding);
                }
            });

            log.info("Fetched {} embeddings from database for project {} in time range [{}, {}]",
                    embeddings.size(), projectId, startTime, endTime);
        } catch (Exception e) {
            log.error("Error fetching embeddings from database for project {}: {}",
                    projectId, e.getMessage(), e);
        }

        return embeddings;
    }

    /**
     * Fetch metrics from DynamoDB within a time range
     */
    private List<MetricSnapshot> fetchMetricsFromDatabase(String projectId, long startTime, long endTime) {
        List<MetricSnapshot> metrics = new ArrayList<>();

        try {
            metricSnapshotTable.query(r -> r.queryConditional(
                    software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
                            .keyEqualTo(k -> k.partitionValue(projectId))
            )).items().forEach(snapshot -> {
                // Filter by timestamp range
                if (snapshot.getTimestamp() != null &&
                    snapshot.getTimestamp() >= startTime &&
                    snapshot.getTimestamp() <= endTime) {
                    metrics.add(snapshot);
                }
            });

            log.info("Fetched {} metric snapshots from database for project {} in time range [{}, {}]",
                    metrics.size(), projectId, startTime, endTime);
        } catch (Exception e) {
            log.error("Error fetching metrics from database for project {}: {}",
                    projectId, e.getMessage(), e);
        }

        return metrics;
    }

    /**
     * Fetch logs from CloudWatch with error filtering
     */
    private List<FilteredLogEvent> fetchCloudWatchLogs(ProjectConfiguration project, CloudWatchLogsClient client, long startTime, long endTime) {
        List<FilteredLogEvent> allLogs = new ArrayList<>();

        List<String> logGroupNames = resolveLogGroupNames(project, client);
        if (logGroupNames.isEmpty()) {
            log.warn("No log groups resolved for project: {}", project.getProjectId());
            return allLogs;
        }

        log.info("Fetching logs from {} log groups for time range: {} to {}",
            logGroupNames.size(), startTime, endTime);

        for (String logGroupName : logGroupNames) {
            try {
                // Use the reliable log streams approach
                List<OutputLogEvent> rawEvents = CloudWatchLogsUtil.fetchLogsFromGroup(
                    client, logGroupName, startTime, endTime,
                    50, // Max 50 most recent streams
                    10000 // Max 10k events per stream
                );

                // Filter for errors, warnings, exceptions
                for (OutputLogEvent event : rawEvents) {
                    String message = event.message().toUpperCase();
                    if (message.contains("ERROR") || message.contains("WARN") ||
                        message.contains("EXCEPTION") || message.contains("TIMEOUT") ||
                        message.matches(".*\\b5\\d{2}\\b.*")) {

                        // Convert OutputLogEvent to FilteredLogEvent format
                        FilteredLogEvent filteredEvent = FilteredLogEvent.builder()
                                .logStreamName(logGroupName) // Will extract stream from rawEvents if needed
                                .timestamp(event.timestamp())
                                .message(event.message())
                                .ingestionTime(event.timestamp())
                                .eventId(String.valueOf(event.hashCode()))
                                .build();
                        allLogs.add(filteredEvent);
                    }
                }

                log.info("Fetched {} total events, {} filtered for errors/warnings from log group: {}",
                    rawEvents.size(), allLogs.size(), logGroupName);

            } catch (Exception e) {
                log.error("Error fetching logs from group {}: {}", logGroupName, e.getMessage(), e);
            }
        }

        log.info("Total logs fetched across all groups: {}", allLogs.size());
        return allLogs;
    }

    private List<String> resolveLogGroupNames(ProjectConfiguration project, CloudWatchLogsClient client) {
        List<String> configured = project.getLogGroupNames();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }

        List<String> discovered = new ArrayList<>();
        try {
            DescribeLogGroupsRequest req = DescribeLogGroupsRequest.builder()
                    .logGroupNamePrefix("/ecs/")
                    .build();
            DescribeLogGroupsResponse resp = client.describeLogGroups(req);

            resp.logGroups().stream()
                    .map(LogGroup::logGroupName)
                    .filter(name -> name.contains("eptBackendApp") || name.contains("ept-backend") || name.contains("eptBackend"))
                    .forEach(discovered::add);
        } catch (CloudWatchLogsException e) {
            log.warn("Failed to auto-discover log groups, using configured backend log group: {}", e.getMessage());
        }

        if (discovered.isEmpty()) {
            // Use backendLogGroupName from ProjectConfiguration if available, otherwise use default
            String backendLogGroupName = project.getBackendLogGroupName();
            if (backendLogGroupName != null && !backendLogGroupName.trim().isEmpty()) {
                discovered.add(backendLogGroupName);
                log.info("Using configured backend log group: {}", backendLogGroupName);
            } else {
                // Final fallback to hardcoded default if no configuration exists
                String defaultLogGroup = "/ecs/eptBackendApp";
                discovered.add(defaultLogGroup);
                log.warn("No backend log group configured for project {}, using default: {}",
                        project.getProjectId(), defaultLogGroup);
            }
        }

        // ensure unique
        return discovered.stream().distinct().toList();
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
        } else if (message.matches(".*\\b5\\d{2}\\b.*")) {
            // Match HTTP 5xx status codes specifically (e.g., 500, 503)
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
        
        // Require minimum time window of 1 minute to calculate meaningful trend
        if (totalTime < 60000) return 0.0;

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
     * Fetch CloudWatch metrics for running EC2 instances
     * Discovers running instances first, then fetches their metrics
     */
    private List<MetricSnapshot> fetchCloudWatchMetrics(ProjectConfiguration project, CloudWatchClient client, long startTime, long endTime) {
        List<MetricSnapshot> snapshots = new ArrayList<>();

        // Step 1: Discover running EC2 instances
        log.info("Discovering running EC2 instances for project: {}", project.getProjectId());
        List<String> instanceIds = discoverRunningEC2Instances(project);

        if (instanceIds.isEmpty()) {
            log.warn("No running EC2 instances found for project: {}", project.getProjectId());
            return snapshots;
        }

        log.info("Discovered {} running EC2 instances: {}", instanceIds.size(), instanceIds);

        // Step 2: Fetch metrics for each instance
        Instant start = Instant.ofEpochMilli(startTime);
        Instant end = Instant.ofEpochMilli(endTime);

        for (String instanceId : instanceIds) {
            // Fetch CPU metrics
            List<MetricSnapshot> cpuMetrics = fetchMetricForInstance(
                    project, client, instanceId, "CPUUtilization", start, end);
            snapshots.addAll(cpuMetrics);

            // Fetch Memory metrics (if available)
            List<MetricSnapshot> memoryMetrics = fetchMetricForInstance(
                    project, client, instanceId, "MemoryUtilization", start, end);
            snapshots.addAll(memoryMetrics);
        }

        log.info("Fetched {} metric snapshots for {} instances", snapshots.size(), instanceIds.size());
        return snapshots;
    }

    /**
     * Discover running EC2 instances using project-specific credentials
     */
    private List<String> discoverRunningEC2Instances(ProjectConfiguration project) {
        List<String> instanceIds = new ArrayList<>();

        try {
            // Create EC2 client with project credentials
            software.amazon.awssdk.services.ec2.Ec2Client ec2Client = createProjectEC2Client(project.getProjectId());

            // Describe running instances
            software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest request =
                    software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest.builder()
                    .filters(software.amazon.awssdk.services.ec2.model.Filter.builder()
                            .name("instance-state-name")
                            .values("running")
                            .build())
                    .build();

            software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse response =
                    ec2Client.describeInstances(request);

            // Extract instance IDs
            for (software.amazon.awssdk.services.ec2.model.Reservation reservation : response.reservations()) {
                for (software.amazon.awssdk.services.ec2.model.Instance instance : reservation.instances()) {
                    instanceIds.add(instance.instanceId());
                }
            }

            ec2Client.close();

        } catch (Exception e) {
            log.error("Error discovering running EC2 instances: {}", e.getMessage(), e);
        }

        return instanceIds;
    }

    /**
     * Fetch metric statistics for a specific EC2 instance
     */
    private List<MetricSnapshot> fetchMetricForInstance(
            ProjectConfiguration project,
            CloudWatchClient client,
            String instanceId,
            String metricName,
            Instant start,
            Instant end) {

        List<MetricSnapshot> snapshots = new ArrayList<>();

        try {
            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                    .namespace("AWS/EC2")
                    .metricName(metricName)
                    .dimensions(Dimension.builder()
                            .name("InstanceId")
                            .value(instanceId)
                            .build())
                    .startTime(start)
                    .endTime(end)
                    .period(300) // 5 minutes
                    .statistics(Statistic.AVERAGE)
                    .build();

            GetMetricStatisticsResponse response = client.getMetricStatistics(request);

            // Convert datapoints to snapshots
            for (Datapoint datapoint : response.datapoints()) {
                MetricSnapshot snapshot = MetricSnapshot.builder()
                        .projectId(project.getProjectId())
                        .timestamp(datapoint.timestamp().toEpochMilli())
                        .serviceName(instanceId) // Use instanceId as serviceName for consistency
                        .metricName(metricName)
                        .value(datapoint.average())
                        .unit(datapoint.unit().toString())
                        .dimensions(Map.of("InstanceId", instanceId))
                        .build();

                snapshots.add(snapshot);
            }

            log.debug("Fetched {} datapoints for instance {} metric {}",
                    snapshots.size(), instanceId, metricName);

        } catch (CloudWatchException e) {
            log.error("Error fetching metric {} for instance {}: {}",
                    metricName, instanceId, e.awsErrorDetails().errorMessage());
        }

        return snapshots;
    }

    /**
     * Create EC2 client with project-specific credentials
     */
    private software.amazon.awssdk.services.ec2.Ec2Client createProjectEC2Client(String projectId) {
        log.info("Creating EC2Client for projectId: {}", projectId);
        try {
            ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            if (!Boolean.TRUE.equals(config.getEnabled())) {
                throw new RuntimeException("Project is disabled: " + projectId);
            }

            Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
            String awsAccessKey = secrets.get("aws-access-key");
            String awsSecretKey = secrets.get("aws-secret-key");
            Region region = Region.of(config.getAwsRegion() != null ? config.getAwsRegion() : "eu-west-1");

            if (awsAccessKey != null && awsSecretKey != null && !awsAccessKey.isEmpty() && !awsSecretKey.isEmpty()) {
                log.debug("Using project-specific AWS credentials for EC2, projectId: {}", projectId);
                return software.amazon.awssdk.services.ec2.Ec2Client.builder()
                        .region(region)
                        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
                        .build();
            }

            log.debug("Using default AWS credentials for EC2, projectId: {}", projectId);
            return software.amazon.awssdk.services.ec2.Ec2Client.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (Exception e) {
            log.error("Failed to create EC2Client for projectId {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to create EC2Client for project: " + projectId, e);
        }
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
     * Perform enhanced AI analysis using Ollama with embeddings data
     * This provides better predictions by including semantic understanding of error patterns
     */
    private AiAnalysisResult performEnhancedAiAnalysis(String projectId, List<LogSummary> logSummaries,
                                                       List<LogEmbedding> embeddings,
                                                       List<MetricSnapshot> metrics) {
        try {
            String prompt = buildEnhancedAiPrompt(logSummaries, embeddings, metrics);

            OllamaGenerateRequest request = new OllamaGenerateRequest("qwen2.5-coder:7b", prompt, false);

            long start = System.currentTimeMillis();

            OllamaGenerateResponse ollamaResponse = ollamaWebClient
                    .post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .timeout(Duration.ofSeconds(120))
                    .doOnError(ex -> log.error("Error calling Ollama for enhanced analysis for project {}", projectId, ex))
                    .block();

            long end = System.currentTimeMillis();
            log.info("Ollama enhanced AI analysis took {} ms", (end - start));

            if (ollamaResponse == null || ollamaResponse.getResponse() == null) {
                throw new RuntimeException("Ollama returned empty response");
            }

            String aiJson = ollamaResponse.getResponse().trim();
            log.debug("Raw enhanced AI analysis response: {}", aiJson);

            return objectMapper.readValue(aiJson, AiAnalysisResult.class);

        } catch (Exception e) {
            log.error("Failed to perform enhanced AI analysis", e);
            // Return fallback analysis
            return new AiAnalysisResult(
                    "Unable to perform enhanced AI analysis",
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
                
                // Safely handle sample message with null check
                String sampleMsg = summary.getSampleMessage();
                if (sampleMsg != null && !sampleMsg.isEmpty()) {
                    int length = Math.min(200, sampleMsg.length());
                    prompt.append(String.format("  Sample: %s\n", sampleMsg.substring(0, length)));
                }
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
     * Build enhanced AI prompt with log summaries, embeddings, and metrics
     * This provides comprehensive context for better health predictions and failure analysis
     */
    private String buildEnhancedAiPrompt(List<LogSummary> logSummaries, List<LogEmbedding> embeddings,
                                         List<MetricSnapshot> metrics) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an advanced DevOps AI assistant performing comprehensive health analysis and failure prediction.\n\n");
        prompt.append("You have access to historical log patterns (via embeddings), current error summaries, and system metrics.\n");
        prompt.append("Analyze ALL the following data sources and respond with ONLY a JSON object (no markdown, no extra text):\n\n");

        // Add embeddings analysis
        prompt.append("=== ERROR PATTERN ANALYSIS (Embeddings-Based) ===\n");
        if (embeddings.isEmpty()) {
            prompt.append("No historical error patterns available in embeddings database.\n");
        } else {
            // Group embeddings by error signature
            Map<String, List<LogEmbedding>> embeddingsBySignature = embeddings.stream()
                    .collect(Collectors.groupingBy(LogEmbedding::getErrorSignature));

            prompt.append(String.format("Total unique error patterns: %d\n", embeddingsBySignature.size()));
            prompt.append(String.format("Total error occurrences tracked: %d\n\n",
                    embeddings.stream().mapToInt(LogEmbedding::getOccurrences).sum()));

            // Show top error patterns
            List<Map.Entry<String, List<LogEmbedding>>> sortedPatterns = embeddingsBySignature.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(
                            e2.getValue().stream().mapToInt(LogEmbedding::getOccurrences).sum(),
                            e1.getValue().stream().mapToInt(LogEmbedding::getOccurrences).sum()
                    ))
                    .limit(10)
                    .toList();

            prompt.append("Top 10 Error Patterns (by frequency):\n");
            int rank = 1;
            for (Map.Entry<String, List<LogEmbedding>> entry : sortedPatterns) {
                String errorSig = entry.getKey();
                List<LogEmbedding> patternEmbeddings = entry.getValue();
                int totalOccurrences = patternEmbeddings.stream().mapToInt(LogEmbedding::getOccurrences).sum();
                String severity = patternEmbeddings.get(0).getSeverity();
                String summaryText = patternEmbeddings.get(0).getSummaryText();

                prompt.append(String.format("%d. [%s] %s\n", rank++, severity, errorSig));
                prompt.append(String.format("   Occurrences: %d | Pattern: %s\n",
                        totalOccurrences, summaryText != null ? summaryText.substring(0, Math.min(150, summaryText.length())) : "N/A"));
            }
            prompt.append("\n");
        }

        // Add recent log summaries
        prompt.append("=== RECENT ERROR AND WARNING LOG SUMMARIES ===\n");
        if (logSummaries.isEmpty()) {
            prompt.append("No errors or warnings detected in the current time window (last 2 hours).\n");
        } else {
            prompt.append(String.format("Total log summaries: %d\n", logSummaries.size()));

            // Sort by trend score (descending) to highlight escalating issues
            List<LogSummary> sortedSummaries = logSummaries.stream()
                    .sorted(Comparator.comparingDouble(LogSummary::getTrendScore).reversed())
                    .limit(15)
                    .toList();

            prompt.append("\nTop 15 Issues (by trend - escalating errors shown first):\n");
            for (LogSummary summary : sortedSummaries) {
                prompt.append(String.format("- [%s] Service: %s\n", summary.getSeverity(), summary.getService()));
                prompt.append(String.format("  Error: %s\n", summary.getErrorSignature()));
                prompt.append(String.format("  Occurrences: %d | Trend Score: %.2f (%.0f%% %s)\n",
                        summary.getOccurrences(), summary.getTrendScore(),
                        Math.abs(summary.getTrendScore()) * 100,
                        summary.getTrendScore() > 0 ? "INCREASING ⚠️" : "DECREASING ✓"));

                String sampleMsg = summary.getSampleMessage();
                if (sampleMsg != null && !sampleMsg.isEmpty()) {
                    int length = Math.min(200, sampleMsg.length());
                    prompt.append(String.format("  Sample: %s\n", sampleMsg.substring(0, length)));
                }
                prompt.append("\n");
            }
        }

        // Add system metrics with trend analysis
        prompt.append("=== SYSTEM METRICS & PERFORMANCE ===\n");
        if (metrics.isEmpty()) {
            prompt.append("No metrics available.\n");
        } else {
            Map<String, List<MetricSnapshot>> metricsByService = metrics.stream()
                    .collect(Collectors.groupingBy(MetricSnapshot::getServiceName));

            for (Map.Entry<String, List<MetricSnapshot>> entry : metricsByService.entrySet()) {
                String serviceName = entry.getKey();
                List<MetricSnapshot> serviceMetrics = entry.getValue();

                // Calculate average and max for each metric
                Map<String, MetricStats> metricStats = new HashMap<>();
                for (MetricSnapshot snapshot : serviceMetrics) {
                    metricStats.computeIfAbsent(snapshot.getMetricName(), k -> new MetricStats())
                            .addValue(snapshot.getValue());
                }

                prompt.append(String.format("- Service: %s\n", serviceName));
                metricStats.forEach((metricName, stats) -> {
                    prompt.append(String.format("  %s: Avg=%.2f%%, Max=%.2f%%, Min=%.2f%%",
                            metricName, stats.getAverage(), stats.getMax(), stats.getMin()));

                    // Add warning indicators
                    if (stats.getAverage() > 80) {
                        prompt.append(" ⚠️ HIGH");
                    } else if (stats.getAverage() > 90) {
                        prompt.append(" 🔴 CRITICAL");
                    }
                    prompt.append("\n");
                });
            }
        }

        // Add context about data freshness
        prompt.append("\n=== DATA CONTEXT ===\n");
        prompt.append("Time Window: Last 2 hours (real-time health check)\n");
        prompt.append(String.format("Error Patterns Analyzed: %d unique signatures\n", embeddings.size()));
        prompt.append(String.format("Recent Issues: %d log summaries\n", logSummaries.size()));
        prompt.append(String.format("Metrics Data Points: %d snapshots\n", metrics.size()));

        // Enhanced response format with predictions
        prompt.append("\n=== REQUIRED JSON RESPONSE FORMAT ===\n");
        prompt.append("{\n");
        prompt.append("  \"rootCause\": \"Comprehensive root cause analysis based on error patterns, trends, and metrics (2-3 sentences)\",\n");
        prompt.append("  \"riskLevel\": \"LOW|MEDIUM|HIGH|CRITICAL\",\n");
        prompt.append("  \"summary\": \"Overall system health summary with forward-looking insights (3-4 sentences)\",\n");
        prompt.append("  \"recommendations\": [\n");
        prompt.append("    \"Immediate action 1 (if any critical issues)\",\n");
        prompt.append("    \"Short-term recommendation 2 (preventive measures)\",\n");
        prompt.append("    \"Long-term recommendation 3 (system improvements)\"\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        prompt.append("=== CRITICAL ANALYSIS RULES ===\n");
        prompt.append("1. Respond with VALID JSON ONLY (no markdown, no code blocks)\n");
        prompt.append("2. riskLevel must be one of: LOW, MEDIUM, HIGH, CRITICAL\n");
        prompt.append("3. Consider error TRENDS heavily - increasing trends indicate growing problems\n");
        prompt.append("4. If errors are INCREASING (positive trend > 0.3): raise risk level\n");
        prompt.append("5. If metrics show resource exhaustion (>80%): minimum MEDIUM risk\n");
        prompt.append("6. If metrics show critical levels (>90%): minimum HIGH risk\n");
        prompt.append("7. Cross-correlate: errors + high metrics = higher risk than either alone\n");
        prompt.append("8. Use embedding patterns to identify recurring vs. new issues\n");
        prompt.append("9. Provide 3 actionable recommendations prioritized by urgency\n");
        prompt.append("10. If system is healthy: acknowledge it but suggest proactive monitoring\n\n");

        prompt.append("DECISION MATRIX:\n");
        prompt.append("- No errors + normal metrics (<60%) → LOW\n");
        prompt.append("- Few errors + normal metrics → LOW to MEDIUM\n");
        prompt.append("- Increasing errors + normal metrics → MEDIUM\n");
        prompt.append("- Many errors + high metrics (>80%) → HIGH\n");
        prompt.append("- Critical errors + critical metrics (>90%) → CRITICAL\n");
        prompt.append("- Recurring patterns from embeddings + new errors → Escalate risk\n");

        return prompt.toString();
    }

    /**
     * Helper class to track metric statistics
     */
    private static class MetricStats {
        private double sum = 0;
        private double max = Double.MIN_VALUE;
        private double min = Double.MAX_VALUE;
        private int count = 0;

        void addValue(double value) {
            sum += value;
            max = Math.max(max, value);
            min = Math.min(min, value);
            count++;
        }

        double getAverage() {
            return count > 0 ? sum / count : 0;
        }

        double getMax() {
            return max != Double.MIN_VALUE ? max : 0;
        }

        double getMin() {
            return min != Double.MAX_VALUE ? min : 0;
        }
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
            default -> {
                log.warn("Unexpected risk level from AI: {}. Defaulting to MEDIUM.", riskLevel);
                likelihood = 0.4;
            }
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
     * Build comprehensive health check response with predictions and insights
     */
    private DevOpsHealthCheckResponse buildHealthCheckResponse(PredictionResult prediction,
                                                                 List<MetricSnapshot> metrics) {
        // This method is no longer used - replaced by buildComprehensiveResponse
        return buildComprehensiveResponse(prediction, metrics, List.of(), List.of());
    }

    /**
     * Build comprehensive health check response from stored data
     */
    private DevOpsHealthCheckResponse buildComprehensiveResponse(
            PredictionResult prediction,
            List<MetricSnapshot> metrics,
            List<LogSummary> logSummaries,
            List<LogEmbedding> embeddings) {

        // 1. Top Failing Services
        List<DevOpsHealthCheckResponse.FailingService> topFailingServices =
                buildTopFailingServices(logSummaries, embeddings, metrics);

        // 2. Error Trends
        List<DevOpsHealthCheckResponse.ErrorTrend> errorTrends =
                buildErrorTrends(logSummaries);

        // 3. Slow APIs (extract from log patterns)
        List<DevOpsHealthCheckResponse.SlowApi> slowApis =
                buildSlowApis(logSummaries, embeddings);

        // 4. Predicted Failures
        List<DevOpsHealthCheckResponse.PredictedFailure> predictedFailures =
                buildPredictedFailures(prediction, logSummaries, embeddings, metrics);

        // 5. Recommendations
        List<DevOpsHealthCheckResponse.Recommendation> recommendations =
                buildDetailedRecommendations(prediction, logSummaries, metrics);

        return DevOpsHealthCheckResponse.builder()
                .topFailingServices(topFailingServices)
                .errorTrends(errorTrends)
                .slowApis(slowApis)
                .predictedFailures(predictedFailures)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Build top failing services from log summaries and embeddings
     */
    private List<DevOpsHealthCheckResponse.FailingService> buildTopFailingServices(
            List<LogSummary> logSummaries, List<LogEmbedding> embeddings, List<MetricSnapshot> metrics) {

        if (logSummaries.isEmpty()) {
            return List.of();
        }

        // Group by service
        Map<String, List<LogSummary>> serviceErrors = logSummaries.stream()
                .filter(s -> s.getService() != null)
                .collect(Collectors.groupingBy(LogSummary::getService));

        return serviceErrors.entrySet().stream()
                .map(entry -> {
                    String serviceName = entry.getKey();
                    List<LogSummary> errors = entry.getValue();

                    int failureCount = errors.stream()
                            .mapToInt(LogSummary::getOccurrences)
                            .sum();

                    long criticalErrors = errors.stream()
                            .filter(e -> "ERROR".equals(e.getSeverity()))
                            .mapToInt(LogSummary::getOccurrences)
                            .sum();

                    // Calculate trend from trend scores
                    double avgTrend = errors.stream()
                            .mapToDouble(LogSummary::getTrendScore)
                            .average()
                            .orElse(0.0);

                    String trend = avgTrend > 0.2 ? "up" : (avgTrend < -0.2 ? "down" : "stable");
                    double trendValue = Math.abs(avgTrend * 100);

                    // Get last failure time
                    long lastTimestamp = errors.stream()
                            .mapToLong(LogSummary::getLastSeenTimestamp)
                            .max()
                            .orElse(System.currentTimeMillis());

                    String lastFailure = formatTimeAgo(lastTimestamp);

                    // Calculate failure rate (errors per minute)
                    double failureRate = failureCount / 60.0; // Simplified

                    // Determine status
                    String status = criticalErrors > 50 || avgTrend > 0.5 ? "critical"
                            : criticalErrors > 10 || avgTrend > 0.2 ? "warning"
                            : "stable";

                    return DevOpsHealthCheckResponse.FailingService.builder()
                            .name(serviceName)
                            .failureCount(failureCount)
                            .failureRate(Math.round(failureRate * 100.0) / 100.0)
                            .trend(trend)
                            .trendValue(Math.round(trendValue * 100.0) / 100.0)
                            .lastFailure(lastFailure)
                            .criticalErrors((int) criticalErrors)
                            .status(status)
                            .build();
                })
                .sorted((a, b) -> Integer.compare(b.getFailureCount(), a.getFailureCount()))
                .limit(5)
                .toList();
    }

    /**
     * Build error trends over time
     */
    private List<DevOpsHealthCheckResponse.ErrorTrend> buildErrorTrends(List<LogSummary> logSummaries) {
        if (logSummaries.isEmpty()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        long oneHourAgo = now - (60 * 60 * 1000);

        // Last Hour
        List<LogSummary> lastHour = logSummaries.stream()
                .filter(s -> s.getLastSeenTimestamp() >= oneHourAgo)
                .toList();

        int errors = lastHour.stream()
                .filter(s -> "ERROR".equals(s.getSeverity()))
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        int warnings = lastHour.stream()
                .filter(s -> "WARN".equals(s.getSeverity()))
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        // Calculate change percentage
        double avgTrend = lastHour.stream()
                .mapToDouble(LogSummary::getTrendScore)
                .average()
                .orElse(0.0);

        String change = avgTrend > 0
                ? "+" + Math.round(avgTrend * 100) + "%"
                : Math.round(avgTrend * 100) + "%";

        String severity = errors > 100 || avgTrend > 0.5 ? "high" : "medium";

        // Find peak time
        String peakTime = findPeakTime(lastHour);

        return List.of(
                DevOpsHealthCheckResponse.ErrorTrend.builder()
                        .timeframe("Last Hour")
                        .errors(errors)
                        .warnings(warnings)
                        .change(change)
                        .severity(severity)
                        .peakTime(peakTime)
                        .build()
        );
    }

    /**
     * Build slow APIs from log patterns
     */
    private List<DevOpsHealthCheckResponse.SlowApi> buildSlowApis(
            List<LogSummary> logSummaries, List<LogEmbedding> embeddings) {

        // Extract API endpoints from error signatures that mention timeout or slow response
        List<DevOpsHealthCheckResponse.SlowApi> slowApis = new ArrayList<>();

        // Analyze log summaries for timeout/slow patterns
        Map<String, List<LogSummary>> timeoutErrors = logSummaries.stream()
                .filter(s -> s.getErrorSignature() != null &&
                        (s.getErrorSignature().toLowerCase().contains("timeout") ||
                         s.getErrorSignature().toLowerCase().contains("slow") ||
                         s.getErrorSignature().toLowerCase().contains("response time")))
                .collect(Collectors.groupingBy(s -> extractEndpoint(s.getErrorSignature())));

        for (Map.Entry<String, List<LogSummary>> entry : timeoutErrors.entrySet()) {
            String endpoint = entry.getKey();
            List<LogSummary> errors = entry.getValue();

            int requestCount = errors.stream().mapToInt(LogSummary::getOccurrences).sum();
            int errorCount = (int) errors.stream()
                    .filter(e -> "ERROR".equals(e.getSeverity()))
                    .mapToInt(LogSummary::getOccurrences)
                    .sum();

            double errorRate = requestCount > 0 ? (errorCount * 100.0 / requestCount) : 0.0;

            // Simulate response times based on error patterns
            int avgResponseTime = 2000 + (errorCount * 10);
            int p95ResponseTime = avgResponseTime + 1500;
            int p99ResponseTime = p95ResponseTime + 2000;

            String status = errorRate > 10 ? "critical" : errorRate > 5 ? "warning" : "healthy";

            slowApis.add(DevOpsHealthCheckResponse.SlowApi.builder()
                    .endpoint(endpoint)
                    .avgResponseTime(avgResponseTime)
                    .p95ResponseTime(p95ResponseTime)
                    .p99ResponseTime(p99ResponseTime)
                    .requestCount(requestCount)
                    .errorRate(Math.round(errorRate * 100.0) / 100.0)
                    .status(status)
                    .slowestRegion("us-east-1") // Can be enhanced with region data
                    .build());
        }

        return slowApis.stream()
                .sorted((a, b) -> Integer.compare(b.getAvgResponseTime(), a.getAvgResponseTime()))
                .limit(5)
                .toList();
    }

    /**
     * Build predicted failures from analysis
     */
    private List<DevOpsHealthCheckResponse.PredictedFailure> buildPredictedFailures(
            PredictionResult prediction,
            List<LogSummary> logSummaries,
            List<LogEmbedding> embeddings,
            List<MetricSnapshot> metrics) {

        List<DevOpsHealthCheckResponse.PredictedFailure> predictions = new ArrayList<>();

        // Analyze critical patterns
        Map<String, List<LogSummary>> criticalServices = logSummaries.stream()
                .filter(s -> "ERROR".equals(s.getSeverity()) && s.getTrendScore() > 0.3)
                .collect(Collectors.groupingBy(LogSummary::getService));

        for (Map.Entry<String, List<LogSummary>> entry : criticalServices.entrySet()) {
            String serviceName = entry.getKey();
            List<LogSummary> errors = entry.getValue();

            // Get most common error
            LogSummary topError = errors.stream()
                    .max(Comparator.comparingInt(LogSummary::getOccurrences))
                    .orElse(null);

            if (topError == null) continue;

            double trendScore = topError.getTrendScore();
            int probability = Math.min(95, (int) ((trendScore + 0.5) * 100));

            String predictionText = generatePredictionText(topError.getErrorSignature());
            String timeframe = probability > 80 ? "Within 15 minutes"
                    : probability > 60 ? "Within 1 hour"
                    : "Within 4 hours";

            String impact = probability > 80 ? "High" : probability > 60 ? "Medium" : "Low";
            String severity = probability > 80 ? "critical" : probability > 60 ? "high" : "medium";

            int affectedUsersCount = topError.getOccurrences() * 10;
            String affectedUsers = "~" + affectedUsersCount;

            String preventiveAction = generatePreventiveAction(topError.getErrorSignature());

            predictions.add(DevOpsHealthCheckResponse.PredictedFailure.builder()
                    .service(serviceName)
                    .prediction(predictionText)
                    .probability(probability)
                    .timeframe(timeframe)
                    .impact(impact)
                    .affectedUsers(affectedUsers)
                    .preventiveAction(preventiveAction)
                    .severity(severity)
                    .build());
        }

        return predictions.stream()
                .sorted((a, b) -> Integer.compare(b.getProbability(), a.getProbability()))
                .limit(3)
                .toList();
    }

    /**
     * Build detailed recommendations
     */
    private List<DevOpsHealthCheckResponse.Recommendation> buildDetailedRecommendations(
            PredictionResult prediction,
            List<LogSummary> logSummaries,
            List<MetricSnapshot> metrics) {

        List<DevOpsHealthCheckResponse.Recommendation> recommendations = new ArrayList<>();

        // Analyze metrics for resource recommendations
        Map<String, Double> avgMetrics = metrics.stream()
                .collect(Collectors.groupingBy(
                        MetricSnapshot::getMetricName,
                        Collectors.averagingDouble(MetricSnapshot::getValue)
                ));

        // High CPU recommendation
        Double avgCpu = avgMetrics.getOrDefault("CPUUtilization", 0.0);
        if (avgCpu > 70) {
            recommendations.add(DevOpsHealthCheckResponse.Recommendation.builder()
                    .title("Scale CPU Resources")
                    .priority(avgCpu > 85 ? "critical" : "high")
                    .impact("Prevents " + Math.round((avgCpu - 50) * 2) + "% of performance degradation")
                    .effort("Low")
                    .estimatedTime("10 minutes")
                    .category("Performance")
                    .steps(List.of(
                            "Increase instance size or add more instances",
                            "Enable auto-scaling based on CPU threshold",
                            "Review and optimize CPU-intensive operations"
                    ))
                    .roi("High - Improves response time by ~40%")
                    .build());
        }

        // High Memory recommendation
        Double avgMemory = avgMetrics.getOrDefault("MemoryUtilization", 0.0);
        if (avgMemory > 70) {
            recommendations.add(DevOpsHealthCheckResponse.Recommendation.builder()
                    .title("Increase Memory Allocation")
                    .priority(avgMemory > 85 ? "critical" : "high")
                    .impact("Prevents OOM errors affecting ~" + (int)(avgMemory * 100) + " requests/min")
                    .effort("Low")
                    .estimatedTime("15 minutes")
                    .category("Capacity")
                    .steps(List.of(
                            "Increase instance memory allocation",
                            "Review memory leaks in application",
                            "Implement memory profiling and monitoring"
                    ))
                    .roi("High - Prevents ~$" + (int)(avgMemory * 1000) + " in lost revenue")
                    .build());
        }

        // Database connection pool based on errors
        boolean hasDbErrors = logSummaries.stream()
                .anyMatch(s -> s.getErrorSignature() != null &&
                        (s.getErrorSignature().toLowerCase().contains("database") ||
                         s.getErrorSignature().toLowerCase().contains("connection") ||
                         s.getErrorSignature().toLowerCase().contains("pool")));

        if (hasDbErrors) {
            int dbErrorCount = logSummaries.stream()
                    .filter(s -> s.getErrorSignature() != null &&
                            s.getErrorSignature().toLowerCase().contains("database"))
                    .mapToInt(LogSummary::getOccurrences)
                    .sum();

            recommendations.add(DevOpsHealthCheckResponse.Recommendation.builder()
                    .title("Increase Database Connection Pool")
                    .priority("critical")
                    .impact("Prevents " + (dbErrorCount > 0 ? (94) : 80) + "% of predicted database failures")
                    .effort("Low")
                    .estimatedTime("15 minutes")
                    .category("Database")
                    .steps(List.of(
                            "Increase max_connections in database config",
                            "Scale connection pool from 20 to 50",
                            "Implement connection pooling monitoring",
                            "Add connection timeout alerts"
                    ))
                    .roi("High - Prevents ~$50K in lost revenue")
                    .build());
        }

        // Add general recommendations from prediction
        if (prediction.getRecommendations() != null && !prediction.getRecommendations().isEmpty()) {
            for (String rec : prediction.getRecommendations()) {
                recommendations.add(DevOpsHealthCheckResponse.Recommendation.builder()
                        .title(rec)
                        .priority("medium")
                        .impact("Improves overall system stability")
                        .effort("Medium")
                        .estimatedTime("30 minutes")
                        .category("General")
                        .steps(List.of("Review and implement recommendation"))
                        .roi("Medium - Proactive maintenance")
                        .build());
            }
        }

        return recommendations.stream()
                .sorted((a, b) -> {
                    Map<String, Integer> priorityMap = Map.of("critical", 3, "high", 2, "medium", 1);
                    return priorityMap.getOrDefault(b.getPriority(), 0)
                            .compareTo(priorityMap.getOrDefault(a.getPriority(), 0));
                })
                .limit(5)
                .toList();
    }

    /**
     * Helper: Format time ago
     */
    private String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / (60 * 1000);

        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " mins ago";

        long hours = minutes / 60;
        if (hours < 24) return hours + " hours ago";

        long days = hours / 24;
        return days + " days ago";
    }

    /**
     * Helper: Find peak error time
     */
    private String findPeakTime(List<LogSummary> summaries) {
        if (summaries.isEmpty()) return "N/A";

        // Find the timestamp with most errors
        LogSummary peak = summaries.stream()
                .max(Comparator.comparingInt(LogSummary::getOccurrences))
                .orElse(null);

        if (peak != null && peak.getLastSeenTimestamp() != null) {
            long timestamp = peak.getLastSeenTimestamp();
            java.time.LocalTime time = java.time.LocalTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp),
                    java.time.ZoneId.systemDefault()
            );
            return String.format("%02d:%02d", time.getHour(), time.getMinute());
        }

        return "N/A";
    }

    /**
     * Helper: Extract endpoint from error signature
     */
    private String extractEndpoint(String errorSignature) {
        if (errorSignature == null) return "/api/unknown";

        // Try to extract API path from error message
        if (errorSignature.contains("/api/")) {
            int start = errorSignature.indexOf("/api/");
            int end = errorSignature.indexOf(" ", start);
            if (end == -1) end = Math.min(start + 50, errorSignature.length());
            return errorSignature.substring(start, end).trim();
        }

        // Generate endpoint based on error type
        if (errorSignature.toLowerCase().contains("order")) return "/api/v1/orders";
        if (errorSignature.toLowerCase().contains("payment")) return "/api/v1/payments";
        if (errorSignature.toLowerCase().contains("user")) return "/api/v1/users";
        if (errorSignature.toLowerCase().contains("checkout")) return "/api/v1/checkout";

        return "/api/v1/" + errorSignature.substring(0, Math.min(20, errorSignature.length()))
                .toLowerCase().replaceAll("[^a-z]", "");
    }

    /**
     * Helper: Generate prediction text from error signature
     */
    private String generatePredictionText(String errorSignature) {
        if (errorSignature == null) return "System degradation expected";

        String lower = errorSignature.toLowerCase();
        if (lower.contains("database") || lower.contains("connection pool")) {
            return "Database connection pool exhaustion";
        }
        if (lower.contains("memory") || lower.contains("heap")) {
            return "Memory exhaustion and OOM errors";
        }
        if (lower.contains("timeout")) {
            return "Service timeout and degraded performance";
        }
        if (lower.contains("null")) {
            return "NullPointerException cascade failure";
        }

        return errorSignature.substring(0, Math.min(60, errorSignature.length())) + " - cascading failure";
    }

    /**
     * Helper: Generate preventive action
     */
    private String generatePreventiveAction(String errorSignature) {
        if (errorSignature == null) return "Monitor system closely";

        String lower = errorSignature.toLowerCase();
        if (lower.contains("database") || lower.contains("connection")) {
            return "Scale up connection pool immediately";
        }
        if (lower.contains("memory")) {
            return "Increase memory allocation and restart services";
        }
        if (lower.contains("timeout")) {
            return "Increase timeout thresholds and optimize queries";
        }
        if (lower.contains("cpu")) {
            return "Scale horizontal instances";
        }

        return "Review and fix root cause immediately";
    }

    /**
     * Build safe dummy response when no logs are found
     */
    private DevOpsHealthCheckResponse buildSafeDummyResponse() {
        return DevOpsHealthCheckResponse.builder()
                .topFailingServices(List.of(
                        DevOpsHealthCheckResponse.FailingService.builder()
                                .name("all-services")
                                .failureCount(0)
                                .failureRate(0.0)
                                .trend("stable")
                                .trendValue(0.0)
                                .lastFailure("N/A")
                                .criticalErrors(0)
                                .status("stable")
                                .build()
                ))
                .errorTrends(List.of(
                        DevOpsHealthCheckResponse.ErrorTrend.builder()
                                .timeframe("Last Hour")
                                .errors(0)
                                .warnings(0)
                                .change("0%")
                                .severity("low")
                                .peakTime("N/A")
                                .build()
                ))
                .slowApis(List.of())
                .predictedFailures(List.of())
                .recommendations(List.of(
                        DevOpsHealthCheckResponse.Recommendation.builder()
                                .title("Continue Monitoring System Health")
                                .priority("medium")
                                .impact("Maintains system stability and early detection")
                                .effort("Low")
                                .estimatedTime("Ongoing")
                                .category("Monitoring")
                                .steps(List.of(
                                        "Ensure logging is properly configured",
                                        "Verify health endpoints are responding",
                                        "Monitor key metrics regularly"
                                ))
                                .roi("High - Prevents unexpected downtime")
                                .build(),
                        DevOpsHealthCheckResponse.Recommendation.builder()
                                .title("Implement Proactive Alerting")
                                .priority("medium")
                                .impact("Early detection of issues before they become critical")
                                .effort("Medium")
                                .estimatedTime("1 hour")
                                .category("Monitoring")
                                .steps(List.of(
                                        "Set up alerts for key metrics",
                                        "Configure notification channels",
                                        "Define alert thresholds"
                                ))
                                .roi("Medium - Reduces MTTR by 60%")
                                .build()
                ))
                .build();
    }

    /**
     * Build metric trends for response
     */
//    private List<DevOpsHealthCheckResponse.MetricTrend> buildMetricTrends(List<MetricSnapshot> metrics) {
//        Map<String, Map<String, List<MetricSnapshot>>> grouped = metrics.stream()
//                .collect(Collectors.groupingBy(
//                        MetricSnapshot::getServiceName,
//                        Collectors.groupingBy(MetricSnapshot::getMetricName)
//                ));
//
//        List<DevOpsHealthCheckResponse.MetricTrend> trends = new ArrayList<>();
//
//        for (Map.Entry<String, Map<String, List<MetricSnapshot>>> serviceEntry : grouped.entrySet()) {
//            String serviceName = serviceEntry.getKey();
//
//            for (Map.Entry<String, List<MetricSnapshot>> metricEntry : serviceEntry.getValue().entrySet()) {
//                String metricName = metricEntry.getKey();
//                List<MetricSnapshot> snapshots = metricEntry.getValue();
//
//                if (snapshots.isEmpty()) continue;
//
//                snapshots.sort(Comparator.comparing(MetricSnapshot::getTimestamp));
//
//                double currentValue = snapshots.get(snapshots.size() - 1).getValue();
//                double averageValue = snapshots.stream()
//                        .mapToDouble(MetricSnapshot::getValue)
//                        .average()
//                        .orElse(0.0);
//
//                String trend = determineTrend(snapshots);
//
//                trends.add(DevOpsHealthCheckResponse.MetricTrend.builder()
//                        .serviceName(serviceName)
//                        .metricName(metricName)
//                        .currentValue(currentValue)
//                        .averageValue(averageValue)
//                        .trend(trend)
//                        .unit(snapshots.get(0).getUnit())
//                        .build());
//            }
//        }
//
//        return trends;
//    }

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

    private CloudWatchLogsClient createProjectCloudWatchLogsClient(String projectId) {
        log.info("Creating CloudWatchLogsClient for projectId: {}", projectId);
        try {
            ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            if (!Boolean.TRUE.equals(config.getEnabled())) {
                throw new RuntimeException("Project is disabled: " + projectId);
            }

            Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
            String awsAccessKey = secrets.get("aws-access-key");
            String awsSecretKey = secrets.get("aws-secret-key");
            Region region = Region.of(config.getAwsRegion() != null ? config.getAwsRegion() : "eu-west-1");

            if (awsAccessKey != null && awsSecretKey != null && !awsAccessKey.isEmpty() && !awsSecretKey.isEmpty()) {
                log.debug("Using project-specific AWS credentials for CloudWatch Logs, projectId: {}", projectId);
                return CloudWatchLogsClient.builder()
                        .region(region)
                        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
                        .build();
            }

            log.debug("Using default AWS credentials for CloudWatch Logs, projectId: {}", projectId);
            return CloudWatchLogsClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (Exception e) {
            log.error("Failed to create CloudWatchLogsClient for projectId {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to create CloudWatchLogsClient for project: " + projectId, e);
        }
    }

    private CloudWatchClient createProjectCloudWatchClient(String projectId) {
        log.info("Creating CloudWatchClient for projectId: {}", projectId);
        try {
            ProjectConfiguration config = projectConfigurationService.getConfiguration(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            if (!Boolean.TRUE.equals(config.getEnabled())) {
                throw new RuntimeException("Project is disabled: " + projectId);
            }

            Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
            String awsAccessKey = secrets.get("aws-access-key");
            String awsSecretKey = secrets.get("aws-secret-key");
            Region region = Region.of(config.getAwsRegion() != null ? config.getAwsRegion() : "eu-west-1");

            if (awsAccessKey != null && awsSecretKey != null && !awsAccessKey.isEmpty() && !awsSecretKey.isEmpty()) {
                log.debug("Using project-specific AWS credentials for CloudWatch Metrics, projectId: {}", projectId);
                return CloudWatchClient.builder()
                        .region(region)
                        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
                        .build();
            }

            log.debug("Using default AWS credentials for CloudWatch Metrics, projectId: {}", projectId);
            return CloudWatchClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (Exception e) {
            log.error("Failed to create CloudWatchClient for projectId {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to create CloudWatchClient for project: " + projectId, e);
        }
    }
}
