package com.devops.agent.service;

import com.devops.agent.model.*;
import com.devops.agent.util.CloudWatchLogsUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * High-performance service for log processing, filtering, summarization and embedding generation
 * Optimized for token efficiency and fast retrieval
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogProcessingService {

    private final ProjectConfigurationService projectConfigurationService;
    private final DynamoDbTable<LogSummary> logSummaryTable;
    private final DynamoDbTable<LogEmbedding> logEmbeddingTable;
    private final WebClient ollamaWebClient;
    private final SecretsManagerService secretsManagerService;

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(\\w+Exception|\\w+Error|Failed|Timeout|5\\d{2}\\s|Connection\\s+refused|Out\\s+of\\s+memory)",
            Pattern.CASE_INSENSITIVE
    );
    private static final ExecutorService EMBEDDING_EXECUTOR = Executors.newFixedThreadPool(5);

    /**
     * Process logs: fetch, filter, summarize, embed, and save to DynamoDB
     * With intelligent caching: returns DB logs if fresh (< 2 hours), otherwise fetches new logs
     */
    public LogProcessingResponse processLogs(String projectId) {
        log.info("Starting log processing for project: {}", projectId);
        long startTime = System.currentTimeMillis();

        // Validate project
        ProjectConfiguration project = projectConfigurationService.getConfiguration(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (!Boolean.TRUE.equals(project.getEnabled())) {
            throw new IllegalArgumentException("Project is disabled: " + projectId);
        }

        LogProcessingResponse.ProcessingStats.ProcessingStatsBuilder statsBuilder =
                LogProcessingResponse.ProcessingStats.builder();

        long currentTime = System.currentTimeMillis();
        long twoHoursAgo = currentTime - (2 * 60 * 60 * 1000); // 2 hours in milliseconds

        // Step 1: Check if fresh logs exist in DB (within last 2 hours)
        log.info("Checking for cached logs in DB for project: {}", projectId);
        long dbCheckStart = System.currentTimeMillis();
        List<LogSummary> cachedSummaries = fetchLogsFromDatabase(projectId, twoHoursAgo, currentTime);
        long dbCheckDuration = System.currentTimeMillis() - dbCheckStart;

        if (!cachedSummaries.isEmpty()) {
            // Fresh logs found in DB, return from cache
            log.info("Found {} fresh cached log summaries (< 2 hours old) for project: {}",
                    cachedSummaries.size(), projectId);
            statsBuilder.logFetchDurationMs(dbCheckDuration)
                    .logProcessingDurationMs(0L)
                    .embeddingGenerationDurationMs(0L)
                    .aiSummarizationDurationMs(0L)
                    .dbSaveDurationMs(0L);

            return buildResponseFromCachedSummaries(projectId, cachedSummaries, statsBuilder, startTime);
        }

        // Step 2: Get last processed timestamp from database (from last saved log)
        Long lastProcessedTimestamp = getLastProcessedTimestampFromDatabase(projectId);
        long startTimeMs;
        long endTimeMs = currentTime;

        if (lastProcessedTimestamp == null) {
            // Never processed before - fetch last 24 hours
            startTimeMs = currentTime - (24 * 60 * 60 * 1000);
            log.info("No previous processing found in DB. Fetching logs from last 24 hours for project: {}", projectId);
        } else {
            // Always fetch from last processed timestamp to ensure no logs are missed
            startTimeMs = lastProcessedTimestamp;
            long timeSinceLastProcessing = currentTime - lastProcessedTimestamp;
            log.info("Fetching logs since last processing ({} ms ago, {} hours) for project: {}",
                    timeSinceLastProcessing, timeSinceLastProcessing / (60 * 60 * 1000), projectId);
        }

        // Step 3: Fetch logs from CloudWatch
        long fetchStart = System.currentTimeMillis();
        List<FilteredLogEvent> rawLogs;
        try (CloudWatchLogsClient logsClient = createProjectCloudWatchLogsClient(projectId)) {
            rawLogs = fetchCloudWatchLogs(project, logsClient, startTimeMs, endTimeMs);
        }
        long fetchDuration = System.currentTimeMillis() - fetchStart;
        statsBuilder.logFetchDurationMs(fetchDuration);
        log.info("Fetched {} raw logs from CloudWatch in {}ms", rawLogs.size(), fetchDuration);

        // Step 4: Always fetch existing logs from last 2 hours to combine with new logs
        // This ensures we have complete context and don't lose older logs when showing results
        List<LogSummary> existingSummaries = Collections.emptyList();
        if (lastProcessedTimestamp != null) {
            // Fetch existing logs from DB to combine with new logs
            log.info("Fetching existing logs from DB (last 2 hours) to combine with new logs");
            existingSummaries = fetchLogsFromDatabase(projectId, twoHoursAgo, currentTime);
            log.info("Found {} existing summaries to combine", existingSummaries.size());
        }

        if (rawLogs.isEmpty() && existingSummaries.isEmpty()) {
            log.info("No logs found for project {}. Returning empty response.", projectId);
            return buildEmptyResponse(projectId, statsBuilder, startTime);
        }

        // Step 5: Process and group new logs
        long processStart = System.currentTimeMillis();
        List<LogSummary> newLogSummaries = rawLogs.isEmpty()
                ? Collections.emptyList()
                : processAndGroupLogs(projectId, rawLogs);

        // Combine new summaries with existing summaries
        List<LogSummary> combinedSummaries = combineLogSummaries(existingSummaries, newLogSummaries, projectId);
        long processDuration = System.currentTimeMillis() - processStart;
        statsBuilder.logProcessingDurationMs(processDuration);
        log.info("Processed {} raw logs into {} new summaries, combined with {} existing = {} total summaries in {}ms",
                rawLogs.size(), newLogSummaries.size(), existingSummaries.size(),
                combinedSummaries.size(), processDuration);

        // Step 6: Generate embeddings for new/updated summaries
        long embeddingStart = System.currentTimeMillis();
        List<LogEmbedding> embeddings = generateEmbeddings(projectId, combinedSummaries);
        long embeddingDuration = System.currentTimeMillis() - embeddingStart;
        statsBuilder.embeddingGenerationDurationMs(embeddingDuration);
        log.info("Generated {} embeddings in {}ms", embeddings.size(), embeddingDuration);

        // Step 7: Generate AI summary
        long aiStart = System.currentTimeMillis();
        String aiSummary = generateAiSummary(combinedSummaries);
        long aiDuration = System.currentTimeMillis() - aiStart;
        statsBuilder.aiSummarizationDurationMs(aiDuration);
        log.info("Generated AI summary in {}ms", aiDuration);

        // Step 8: Save to DynamoDB (overwrite existing)
        long dbStart = System.currentTimeMillis();
        saveToDatabase(combinedSummaries, embeddings);
        long dbDuration = System.currentTimeMillis() - dbStart;
        statsBuilder.dbSaveDurationMs(dbDuration);
        log.info("Saved data to DynamoDB in {}ms", dbDuration);

        // Build response
        long totalDuration = System.currentTimeMillis() - startTime;
        statsBuilder.totalDurationMs(totalDuration);

        return buildResponse(projectId, rawLogs, combinedSummaries, embeddings, aiSummary,
                statsBuilder.build(), endTimeMs);
    }

    /**
     * Fetch logs from CloudWatch with error/warning filtering
     */
    private List<FilteredLogEvent> fetchCloudWatchLogs(
            ProjectConfiguration project,
            CloudWatchLogsClient client,
            long startTime,
            long endTime) {

        List<FilteredLogEvent> allLogs = new ArrayList<>();
        List<String> logGroupNames = resolveLogGroupNames(project, client);

        if (logGroupNames.isEmpty()) {
            log.warn("No log groups resolved for project: {}", project.getProjectId());
            return allLogs;
        }

        log.info("Fetching logs from {} log groups", logGroupNames.size());

        for (String logGroupName : logGroupNames) {
            try {
                List<OutputLogEvent> rawEvents = CloudWatchLogsUtil.fetchLogsFromGroup(
                        client, logGroupName, startTime, endTime,
                        50,    // Max 50 most recent streams
                        10000  // Max 10k events per stream
                );

                // Filter for errors, warnings, exceptions
                for (OutputLogEvent event : rawEvents) {
                    String message = event.message();
                    String upperMessage = message.toUpperCase();

                    if (upperMessage.contains("ERROR") ||
                        upperMessage.contains("WARN") ||
                        upperMessage.contains("EXCEPTION") ||
                        upperMessage.contains("TIMEOUT") ||
                        upperMessage.matches(".*\\b5\\d{2}\\b.*")) {

                        FilteredLogEvent filteredEvent = FilteredLogEvent.builder()
                                .logStreamName(logGroupName)
                                .timestamp(event.timestamp())
                                .message(message)
                                .ingestionTime(event.timestamp())
                                .eventId(String.valueOf(event.hashCode()))
                                .build();
                        allLogs.add(filteredEvent);
                    }
                }

                log.debug("Fetched {} filtered events from log group: {}",
                        allLogs.size(), logGroupName);

            } catch (Exception e) {
                log.error("Error fetching logs from group {}: {}", logGroupName, e.getMessage(), e);
            }
        }

        return allLogs;
    }

    /**
     * Get the last processed timestamp from the most recent log in database
     */
    private Long getLastProcessedTimestampFromDatabase(String projectId) {
        try {
            Long maxTimestamp = null;

            // Query all log summaries for the project and find the maximum lastSeenTimestamp
            for (LogSummary summary : logSummaryTable.query(r -> r.queryConditional(
                    software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
                            .keyEqualTo(k -> k.partitionValue(projectId))
            )).items()) {
                if (summary.getLastSeenTimestamp() != null) {
                    if (maxTimestamp == null || summary.getLastSeenTimestamp() > maxTimestamp) {
                        maxTimestamp = summary.getLastSeenTimestamp();
                    }
                }
            }

            if (maxTimestamp != null) {
                log.info("Found last processed timestamp from DB for project {}: {}", projectId, maxTimestamp);
            } else {
                log.info("No previous logs found in DB for project {}", projectId);
            }

            return maxTimestamp;
        } catch (Exception e) {
            log.error("Error fetching last processed timestamp from database for project {}: {}",
                    projectId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetch log summaries from DynamoDB within a time range
     */
    private List<LogSummary> fetchLogsFromDatabase(String projectId, long startTime, long endTime) {
        List<LogSummary> summaries = new ArrayList<>();

        try {
            // Query DynamoDB for all log summaries in the time range
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

            log.info("Fetched {} log summaries from DB for project {} in time range [{}, {}]",
                    summaries.size(), projectId, startTime, endTime);
        } catch (Exception e) {
            log.error("Error fetching logs from database for project {}: {}", projectId, e.getMessage(), e);
        }

        return summaries;
    }

    /**
     * Combine existing and new log summaries by merging similar errors
     */
    private List<LogSummary> combineLogSummaries(List<LogSummary> existingSummaries,
                                                   List<LogSummary> newSummaries,
                                                   String projectId) {
        if (existingSummaries.isEmpty()) {
            return newSummaries;
        }

        if (newSummaries.isEmpty()) {
            return existingSummaries;
        }

        // Create a map keyed by service#errorSignature#severity
        Map<String, LogSummary> combinedMap = new HashMap<>();

        // Add existing summaries to map
        for (LogSummary summary : existingSummaries) {
            String key = summary.getService() + "#" + summary.getErrorSignature() + "#" + summary.getSeverity();
            combinedMap.put(key, summary);
        }

        // Merge or add new summaries
        long currentTime = System.currentTimeMillis();
        for (LogSummary newSummary : newSummaries) {
            String key = newSummary.getService() + "#" + newSummary.getErrorSignature() + "#" + newSummary.getSeverity();

            if (combinedMap.containsKey(key)) {
                // Merge with existing summary
                LogSummary existing = combinedMap.get(key);
                LogSummary merged = LogSummary.builder()
                        .projectId(projectId)
                        .summaryId(key + "#" + currentTime)
                        .service(existing.getService())
                        .errorSignature(existing.getErrorSignature())
                        .severity(existing.getSeverity())
                        .occurrences(existing.getOccurrences() + newSummary.getOccurrences())
                        .firstSeenTimestamp(Math.min(existing.getFirstSeenTimestamp(), newSummary.getFirstSeenTimestamp()))
                        .lastSeenTimestamp(Math.max(existing.getLastSeenTimestamp(), newSummary.getLastSeenTimestamp()))
                        .sampleMessage(newSummary.getSampleMessage() != null ? newSummary.getSampleMessage() : existing.getSampleMessage())
                        .trendScore(calculateCombinedTrend(existing, newSummary))
                        .build();
                combinedMap.put(key, merged);
            } else {
                // Add new summary
                combinedMap.put(key, newSummary);
            }
        }

        return new ArrayList<>(combinedMap.values());
    }

    /**
     * Calculate combined trend score for merged summaries
     */
    private double calculateCombinedTrend(LogSummary existing, LogSummary newSummary) {
        // Simple approach: average the trend scores weighted by occurrences
        int totalOccurrences = existing.getOccurrences() + newSummary.getOccurrences();
        if (totalOccurrences == 0) return 0.0;

        double weightedTrend = (existing.getTrendScore() * existing.getOccurrences() +
                                newSummary.getTrendScore() * newSummary.getOccurrences()) / totalOccurrences;

        return weightedTrend;
    }

    /**
     * Build response from cached log summaries (no processing needed)
     */
    private LogProcessingResponse buildResponseFromCachedSummaries(
            String projectId,
            List<LogSummary> cachedSummaries,
            LogProcessingResponse.ProcessingStats.ProcessingStatsBuilder statsBuilder,
            long startTime) {

        // Calculate counts
        int errorCount = cachedSummaries.stream()
                .filter(s -> "ERROR".equals(s.getSeverity()))
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        int warningCount = cachedSummaries.stream()
                .filter(s -> "WARN".equals(s.getSeverity()))
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        int totalLogsProcessed = cachedSummaries.stream()
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        // Determine overall severity
        String overallSeverity = determineOverallSeverity(cachedSummaries);

        // Generate AI summary (quick)
        long aiStart = System.currentTimeMillis();
        String aiSummary = generateAiSummary(cachedSummaries);
        long aiDuration = System.currentTimeMillis() - aiStart;
        statsBuilder.aiSummarizationDurationMs(aiDuration);

        // Top errors
        List<LogProcessingResponse.LogSummaryDto> topErrors = cachedSummaries.stream()
                .sorted(Comparator.comparingInt(LogSummary::getOccurrences).reversed())
                .limit(10)
                .map(s -> LogProcessingResponse.LogSummaryDto.builder()
                        .service(s.getService())
                        .errorSignature(s.getErrorSignature())
                        .severity(s.getSeverity())
                        .occurrences(s.getOccurrences())
                        .firstSeenTimestamp(s.getFirstSeenTimestamp())
                        .lastSeenTimestamp(s.getLastSeenTimestamp())
                        .sampleMessage(s.getSampleMessage())
                        .trendScore(s.getTrendScore())
                        .build())
                .collect(Collectors.toList());

        long totalDuration = System.currentTimeMillis() - startTime;
        statsBuilder.totalDurationMs(totalDuration);

        log.info("Returned cached response for project {} with {} summaries", projectId, cachedSummaries.size());

        return LogProcessingResponse.builder()
                .projectId(projectId)
                .processingTimestamp(System.currentTimeMillis())
                .totalLogsProcessed(totalLogsProcessed)
                .errorCount(errorCount)
                .warningCount(warningCount)
                .summariesCreated(cachedSummaries.size())
                .embeddingsCreated(0) // Not counted for cached response
                .aiSummary(aiSummary)
                .overallSeverity(overallSeverity)
                .topErrors(topErrors)
                .stats(statsBuilder.build())
                .build();
    }

    /**
     * Resolve log group names from config or auto-discover
     */
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
                    .filter(name -> name.contains("eptBackendApp") ||
                                    name.contains("ept-backend") ||
                                    name.contains("eptBackend"))
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

        return discovered.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Process and group logs into summaries with deduplication
     */
    private List<LogSummary> processAndGroupLogs(String projectId, List<FilteredLogEvent> rawLogs) {
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

        // Build final summaries
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
                sampleMessage = message.length() > 500 ? message.substring(0, 500) : message;
            }
        }
    }

    /**
     * Generate embeddings for log summaries using Ollama nomic-embed-text model
     */
    private List<LogEmbedding> generateEmbeddings(String projectId, List<LogSummary> summaries) {
        if (summaries.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompletableFuture<LogEmbedding>> futures = new ArrayList<>();

        for (LogSummary summary : summaries) {
            CompletableFuture<LogEmbedding> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Create condensed text for embedding
                    String embeddingText = createEmbeddingText(summary);

                    // Call Ollama embedding API
                    OllamaEmbedRequest request = OllamaEmbedRequest.builder()
                            .model("nomic-embed-text")
                            .prompt(embeddingText)
                            .build();

                    OllamaEmbedResponse response = ollamaWebClient
                            .post()
                            .uri("/api/embeddings")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(OllamaEmbedResponse.class)
                            .timeout(Duration.ofSeconds(30))
                            .block();

                    if (response == null || response.getEmbedding() == null) {
                        log.warn("Failed to generate embedding for summary: {}", summary.getSummaryId());
                        return null;
                    }

                    // Create LogEmbedding entity
                    return LogEmbedding.builder()
                            .projectId(projectId)
                            .embeddingId(summary.getSummaryId() + "#emb")
                            .summaryId(summary.getSummaryId())
                            .embedding(response.getEmbedding())
                            .errorSignature(summary.getErrorSignature())
                            .severity(summary.getSeverity())
                            .timestamp(summary.getLastSeenTimestamp())
                            .occurrences(summary.getOccurrences())
                            .summaryText(embeddingText)
                            .build();

                } catch (Exception e) {
                    log.error("Error generating embedding for summary {}: {}",
                            summary.getSummaryId(), e.getMessage());
                    return null;
                }
            }, EMBEDDING_EXECUTOR);

            futures.add(future);
        }

        // Wait for all embeddings to complete
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Create condensed text for embedding generation
     */
    private String createEmbeddingText(LogSummary summary) {
        return String.format(
                "Service: %s | Error: %s | Severity: %s | Occurrences: %d | Sample: %s",
                summary.getService(),
                summary.getErrorSignature(),
                summary.getSeverity(),
                summary.getOccurrences(),
                summary.getSampleMessage() != null && summary.getSampleMessage().length() > 200
                        ? summary.getSampleMessage().substring(0, 200)
                        : summary.getSampleMessage()
        );
    }

    /**
     * Generate AI summary of all log summaries
     */
    private String generateAiSummary(List<LogSummary> summaries) {
        if (summaries.isEmpty()) {
            return "No errors or warnings detected in the logs.";
        }

        try {
            // Build concise prompt
            StringBuilder prompt = new StringBuilder();
            prompt.append("Analyze these log summaries and provide a brief 2-3 sentence summary:\n\n");

            // Top 10 most critical summaries
            summaries.stream()
                    .sorted(Comparator.comparingInt(LogSummary::getOccurrences).reversed())
                    .limit(10)
                    .forEach(s -> {
                        prompt.append(String.format("- %s: %s (%d occurrences, trend: %.2f)\n",
                                s.getSeverity(), s.getErrorSignature(), s.getOccurrences(), s.getTrendScore()));
                    });

            prompt.append("\nProvide a concise summary focusing on the most critical issues.");

            // Call Ollama
            OllamaGenerateRequest request = new OllamaGenerateRequest("qwen2.5-coder:7b", prompt.toString(), false);

            OllamaGenerateResponse response = ollamaWebClient
                    .post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (response != null && response.getResponse() != null) {
                return response.getResponse().trim();
            }

            return "AI summarization temporarily unavailable.";

        } catch (Exception e) {
            log.error("Error generating AI summary: {}", e.getMessage());
            return "Unable to generate AI summary due to technical issues.";
        }
    }

    /**
     * Save log summaries and embeddings to DynamoDB in parallel
     */
    private void saveToDatabase(List<LogSummary> summaries, List<LogEmbedding> embeddings) {
        CompletableFuture<Void> summariesFuture = CompletableFuture.runAsync(() -> {
            summaries.forEach(logSummaryTable::putItem);
            log.info("Saved {} log summaries to DynamoDB", summaries.size());
        });

        CompletableFuture<Void> embeddingsFuture = CompletableFuture.runAsync(() -> {
            embeddings.forEach(logEmbeddingTable::putItem);
            log.info("Saved {} embeddings to DynamoDB", embeddings.size());
        });

        // Wait for both to complete
        CompletableFuture.allOf(summariesFuture, embeddingsFuture).join();
    }

    /**
     * Build response from processed data
     */
    private LogProcessingResponse buildResponse(
            String projectId,
            List<FilteredLogEvent> rawLogs,
            List<LogSummary> summaries,
            List<LogEmbedding> embeddings,
            String aiSummary,
            LogProcessingResponse.ProcessingStats stats,
            Long timestamp) {

        // Calculate counts
        int errorCount = summaries.stream()
                .filter(s -> "ERROR".equals(s.getSeverity()))
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        int warningCount = summaries.stream()
                .filter(s -> "WARN".equals(s.getSeverity()))
                .mapToInt(LogSummary::getOccurrences)
                .sum();

        // Determine overall severity
        String overallSeverity = determineOverallSeverity(summaries);

        // Top errors
        List<LogProcessingResponse.LogSummaryDto> topErrors = summaries.stream()
                .sorted(Comparator.comparingInt(LogSummary::getOccurrences).reversed())
                .limit(10)
                .map(s -> LogProcessingResponse.LogSummaryDto.builder()
                        .service(s.getService())
                        .errorSignature(s.getErrorSignature())
                        .severity(s.getSeverity())
                        .occurrences(s.getOccurrences())
                        .firstSeenTimestamp(s.getFirstSeenTimestamp())
                        .lastSeenTimestamp(s.getLastSeenTimestamp())
                        .sampleMessage(s.getSampleMessage())
                        .trendScore(s.getTrendScore())
                        .build())
                .collect(Collectors.toList());

        return LogProcessingResponse.builder()
                .projectId(projectId)
                .processingTimestamp(timestamp)
                .totalLogsProcessed(rawLogs.size())
                .errorCount(errorCount)
                .warningCount(warningCount)
                .summariesCreated(summaries.size())
                .embeddingsCreated(embeddings.size())
                .aiSummary(aiSummary)
                .overallSeverity(overallSeverity)
                .topErrors(topErrors)
                .stats(stats)
                .build();
    }

    /**
     * Build empty response when no logs found
     */
    private LogProcessingResponse buildEmptyResponse(
            String projectId,
            LogProcessingResponse.ProcessingStats.ProcessingStatsBuilder statsBuilder,
            long startTime) {

        long totalDuration = System.currentTimeMillis() - startTime;
        statsBuilder.totalDurationMs(totalDuration)
                .logProcessingDurationMs(0L)
                .embeddingGenerationDurationMs(0L)
                .aiSummarizationDurationMs(0L)
                .dbSaveDurationMs(0L);

        return LogProcessingResponse.builder()
                .projectId(projectId)
                .processingTimestamp(System.currentTimeMillis())
                .totalLogsProcessed(0)
                .errorCount(0)
                .warningCount(0)
                .summariesCreated(0)
                .embeddingsCreated(0)
                .aiSummary("No errors or warnings detected in the time window.")
                .overallSeverity("LOW")
                .topErrors(Collections.emptyList())
                .stats(statsBuilder.build())
                .build();
    }

    /**
     * Determine overall severity based on log summaries
     */
    private String determineOverallSeverity(List<LogSummary> summaries) {
        long criticalErrors = summaries.stream()
                .filter(s -> "ERROR".equals(s.getSeverity()))
                .filter(s -> s.getOccurrences() > 50 || s.getTrendScore() > 0.5)
                .count();

        if (criticalErrors > 5) return "CRITICAL";

        long highErrors = summaries.stream()
                .filter(s -> "ERROR".equals(s.getSeverity()))
                .filter(s -> s.getOccurrences() > 20)
                .count();

        if (highErrors > 3) return "HIGH";

        long errors = summaries.stream()
                .filter(s -> "ERROR".equals(s.getSeverity()))
                .count();

        if (errors > 0) return "MEDIUM";

        return "LOW";
    }

    /**
     * Extract service name from log stream/group
     */
    private String extractServiceFromLogGroup(String logStreamName) {
        if (logStreamName == null) return "unknown";

        if (logStreamName.contains("eptBackendApp")) return "eptBackendApp";
        if (logStreamName.contains("ept-backend")) return "ept-backend";
        if (logStreamName.contains("/ecs/")) {
            String[] parts = logStreamName.split("/");
            return parts.length > 2 ? parts[2] : "ecs-service";
        }

        return "unknown";
    }

    /**
     * Extract severity from log message
     */
    private String extractSeverity(String message) {
        String upper = message.toUpperCase();
        if (upper.contains("ERROR") || upper.matches(".*\\b5\\d{2}\\b.*")) return "ERROR";
        if (upper.contains("WARN")) return "WARN";
        if (upper.contains("EXCEPTION") || upper.contains("TIMEOUT")) return "ERROR";
        return "WARN";
    }

    /**
     * Normalize error signature for grouping
     */
    private String normalizeErrorSignature(String message) {
        // Extract error type
        java.util.regex.Matcher matcher = ERROR_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fallback: first 100 chars
        return message.length() > 100 ? message.substring(0, 100) : message;
    }

    /**
     * Calculate trend score: positive = increasing, negative = decreasing
     */
    private double calculateTrend(List<Long> timestamps) {
        if (timestamps.size() < 2) return 0.0;

        timestamps.sort(Long::compareTo);
        long timeRange = timestamps.get(timestamps.size() - 1) - timestamps.get(0);
        if (timeRange == 0) return 0.0;

        // Split into two halves
        int midpoint = timestamps.size() / 2;
        int firstHalfCount = midpoint;
        int secondHalfCount = timestamps.size() - midpoint;

        // Calculate rate for each half
        double firstRate = (double) firstHalfCount / (timeRange / 2.0);
        double secondRate = (double) secondHalfCount / (timeRange / 2.0);

        // Return normalized trend
        return (secondRate - firstRate) / Math.max(firstRate, 1.0);
    }

    /**
     * Create CloudWatch Logs client with project-specific credentials
     */
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
                log.debug("Using project-specific AWS credentials for CloudWatch Logs");
                return CloudWatchLogsClient.builder()
                        .region(region)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
                        .build();
            }

            log.debug("Using default AWS credentials for CloudWatch Logs");
            return CloudWatchLogsClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (Exception e) {
            log.error("Error creating CloudWatchLogsClient for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Failed to create CloudWatchLogsClient", e);
        }
    }
}
