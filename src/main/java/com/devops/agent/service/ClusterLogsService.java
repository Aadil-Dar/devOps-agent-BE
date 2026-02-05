package com.devops.agent.service;

import com.devops.agent.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterLogsService {

    private final CloudWatchLogsClient cloudWatchLogsClient;
    private final WebClient ollamaWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Thread pool for parallel processing (Java 17 compatible)
    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newCachedThreadPool();

    // Optimized thread pool for CPU-intensive tasks
    private static final ExecutorService CPU_EXECUTOR = Executors.newWorkStealingPool();

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    // Pre-compiled patterns for better performance
    private static final Pattern SEVERITY_PATTERN = Pattern.compile("\\b(ERROR|WARN|INFO|DEBUG)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("(?:requestId|traceId|trace-id|request-id|req)[:\\s=]([a-zA-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("(\\w+Exception|\\w+Error)(?:\\s+(?:at|in)\\s+([\\w.$]+))?");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("(?:service|application)[:\\s=]([a-zA-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

    private static final String LOG_GROUP_NAME = "/ecs/eptBackendApp";
    private static final int STREAM_LIMIT = 40;
    private static final int EVENTS_PER_STREAM = 100;

    // Cache for severity rankings
    private static final Map<String, Integer> SEVERITY_RANKS = Map.of(
            "ERROR", 3,
            "WARN", 2,
            "INFO", 1,
            "DEBUG", 0
    );

    /**
     * Fetch all logs from log group using FilterLogEvents API (matches CloudWatch console behavior)
     */
    public ClusterLogsResponse getClusterLogs(String clusterId) {
        var startTime = System.currentTimeMillis();
        log.info("Fetching logs for cluster: {} from log group: {}", clusterId, LOG_GROUP_NAME);

        try {
            // Calculate time range (last 6 hours)
            var endTimeMillis = System.currentTimeMillis();
            var startTimeMillis = Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli();

            // Fetch all logs directly using FilterLogEvents (same as CloudWatch console)
            var rawLogs = fetchAllLogsFromLogGroup(startTimeMillis, endTimeMillis);

            if (rawLogs.isEmpty()) {
                log.warn("No logs found in log group: {}", LOG_GROUP_NAME);
                return buildEmptyResponse();
            }

            log.info("Fetched {} total logs from log group", rawLogs.size());

            // Parallel filtering using virtual threads
            var errorLogs = rawLogs.parallelStream()
                    .filter(l -> "ERROR".equals(l.severity()))
                    .toList();

            log.info("Filtered {} error logs out of {} total logs", errorLogs.size(), rawLogs.size());

            // Group logs using AI with timeout protection
            var groupedLogs = groupLogsByAI(errorLogs);

            // Parallel statistics calculation
            var stats = calculateStatisticsParallel(rawLogs, errorLogs);

            // Generate AI summary asynchronously
            var summaryFuture = CompletableFuture.supplyAsync(
                    () -> generateQuickSummary(groupedLogs),
                    VIRTUAL_EXECUTOR
            );

            var summary = summaryFuture.orTimeout(5, TimeUnit.SECONDS)
                    .exceptionally(ex -> generateFallbackSummary(groupedLogs))
                    .join();

            var duration = System.currentTimeMillis() - startTime;
            log.info("Processed {} logs into {} clusters in {}ms", rawLogs.size(), groupedLogs.size(), duration);

            return ClusterLogsResponse.builder()
                    .logs(groupedLogs)
                    .summary(summary)
                    .totalErrors(stats.errors())
                    .totalWarnings(stats.warnings())
                    .totalLogs(rawLogs.size())
                    .clusterId(LOG_GROUP_NAME)
                    .timeRange("6h")
                    .build();

        } catch (Exception e) {
            log.error("Error fetching cluster logs", e);
            return buildEmptyResponse();
        }
    }

    /**
     * Fetch all logs from log group using FilterLogEvents API with pagination
     * This matches CloudWatch console behavior and fetches ALL logs in time range
     */
    private List<RawLogEntry> fetchAllLogsFromLogGroup(long startTime, long endTime) {
        var allLogs = new ArrayList<RawLogEntry>();
        String nextToken = null;
        int pageCount = 0;
        final int MAX_PAGES = 20; // Limit to prevent infinite loops

        try {
            log.info("Fetching logs from {} between {} and {}",
                LOG_GROUP_NAME,
                Instant.ofEpochMilli(startTime),
                Instant.ofEpochMilli(endTime));

            do {
                var requestBuilder = FilterLogEventsRequest.builder()
                        .logGroupName(LOG_GROUP_NAME)
                        .startTime(startTime)
                        .endTime(endTime)
                        .limit(10000); // Max limit per request

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                var response = cloudWatchLogsClient.filterLogEvents(requestBuilder.build());
                var events = response.events();

                log.debug("Page {}: Fetched {} events", pageCount + 1, events.size());

                // Parse events in parallel
                var pageLogs = events.parallelStream()
                        .map(this::parseFilteredLogEvent)
                        .toList();

                allLogs.addAll(pageLogs);

                nextToken = response.nextToken();
                pageCount++;

                // Break if no more pages or reached max pages
                if (nextToken == null || pageCount >= MAX_PAGES) {
                    break;
                }

            } while (true);

            log.info("Successfully fetched {} logs from {} pages", allLogs.size(), pageCount);

        } catch (Exception e) {
            log.error("Error fetching logs from log group: {}", e.getMessage(), e);
        }

        return allLogs;
    }

    /**
     * Parse FilteredLogEvent into RawLogEntry
     */
    private RawLogEntry parseFilteredLogEvent(FilteredLogEvent event) {
        var message = event.message();
        if (message == null || message.isBlank()) {
            message = "[Empty log message]";
        }

        var timestamp = ISO_FORMATTER.format(
                Instant.ofEpochMilli(event.timestamp()).atZone(ZoneId.of("UTC"))
        );

        var logStreamName = event.logStreamName() != null ? event.logStreamName() : "unknown";

        return new RawLogEntry(
                timestamp,
                message,
                extractHost(logStreamName),
                extractSeverity(message),
                extractService(message),
                extractRequestId(message)
        );
    }



    /**
     * AI-powered log grouping with parallel processing
     */
    private List<ClusterLogEntry> groupLogsByAI(List<RawLogEntry> errorLogs) {
        if (errorLogs.isEmpty()) {
            return List.of();
        }

        try {
            // Build prompt efficiently using StringBuilder with capacity hint
            var logsData = new StringBuilder(errorLogs.size() * 150);
            logsData.append("Analyze and group these error logs by their root cause. ")
                    .append("Group related errors together even if the messages differ slightly. ")
                    .append("Return a JSON array where each element has: groupTitle (string), logIndices (array of numbers).\n\n");

            for (int i = 0; i < errorLogs.size(); i++) {
                var log = errorLogs.get(i);
                logsData.append("[%d] %s | Host: %s | Time: %s\n".formatted(
                        i,
                        truncateMessage(log.message(), 200),
                        log.host(),
                        log.timestamp()
                ));
            }

            var prompt = logsData +
                    "\n\nProvide ONLY a valid JSON array response in this format:\n" +
                    "[{\"groupTitle\": \"NullPointerException in OrderProcessor\", \"logIndices\": [0, 2, 5]}]\n" +
                    "Response:";

            log.debug("Sending {} error logs to AI for grouping", errorLogs.size());

            var request = new OllamaGenerateRequest("qwen2.5-coder:7b", prompt, false);

            var aiResponse = ollamaWebClient
                    .post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (aiResponse != null && aiResponse.getResponse() != null) {
                var responseText = aiResponse.getResponse().strip();
                log.debug("AI grouping response received");

                var clusters = parseAIGroupingResponse(responseText, errorLogs);

                if (!clusters.isEmpty()) {
                    log.info("AI grouped {} logs into {} clusters", errorLogs.size(), clusters.size());
                    return clusters;
                }
            }

        } catch (Exception e) {
            log.warn("AI grouping failed, using pattern-based grouping: {}", e.getMessage());
        }

        return groupLogsByPattern(errorLogs);
    }

    /**
     * High-performance AI response parsing with parallel cluster building
     */
    private List<ClusterLogEntry> parseAIGroupingResponse(String aiResponse, List<RawLogEntry> errorLogs) {
        try {
            // Extract JSON efficiently
            var jsonStart = aiResponse.indexOf('[');
            var jsonEnd = aiResponse.lastIndexOf(']');

            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                log.warn("No valid JSON array in AI response");
                return List.of();
            }

            var jsonPart = aiResponse.substring(jsonStart, jsonEnd + 1);
            var groupsNode = objectMapper.readTree(jsonPart);

            if (!groupsNode.isArray()) {
                return List.of();
            }

            var idCounter = new AtomicInteger(1);

            // Parallel cluster building using virtual threads
            return StreamSupport.stream(groupsNode.spliterator(), true)
                    .map(groupNode -> {
                        var groupTitle = groupNode.has("groupTitle") ?
                                groupNode.get("groupTitle").asText() : "Unknown Error Group";

                        var indicesNode = groupNode.get("logIndices");
                        if (indicesNode == null || !indicesNode.isArray()) {
                            return null;
                        }

                        // Collect group logs
                        var groupLogs = StreamSupport.stream(indicesNode.spliterator(), false)
                                .mapToInt(JsonNode::asInt)
                                .filter(idx -> idx >= 0 && idx < errorLogs.size())
                                .mapToObj(errorLogs::get)
                                .sorted(Comparator.comparing(RawLogEntry::timestamp))
                                .toList();

                        if (groupLogs.isEmpty()) {
                            return null;
                        }

                        return buildClusterEntry("c" + idCounter.getAndIncrement(), groupTitle, groupLogs);
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(ClusterLogEntry::getCount).reversed())
                    .toList();

        } catch (Exception e) {
            log.error("Error parsing AI response: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Build cluster entry with minimal allocations
     */
    private ClusterLogEntry buildClusterEntry(String id, String title, List<RawLogEntry> logs) {
        var affectedHosts = logs.stream()
                .map(RawLogEntry::host)
                .filter(h -> h != null && !h.isEmpty())
                .distinct()
                .toList();

        var affectedServices = logs.stream()
                .map(RawLogEntry::service)
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .toList();

        var sampleLogs = logs.stream()
                .limit(3)
                .map(l -> SampleLog.builder()
                        .timestamp(l.timestamp())
                        .message(l.message())
                        .host(l.host())
                        .requestId(l.requestId())
                        .build())
                .toList();

        return ClusterLogEntry.builder()
                .id(id)
                .title(title)
                .count(logs.size())
                .firstSeen(logs.get(0).timestamp())
                .lastSeen(logs.get(logs.size() - 1).timestamp())
                .affectedHosts(affectedHosts)
                .affectedServices(affectedServices)
                .sampleLogs(sampleLogs)
                .severity("ERROR")
                .build();
    }

    /**
     * Pattern-based grouping with parallel processing
     */
    private List<ClusterLogEntry> groupLogsByPattern(List<RawLogEntry> rawLogs) {
        // Parallel grouping using ConcurrentHashMap
        var groupedByTitle = rawLogs.parallelStream()
                .collect(Collectors.groupingByConcurrent(
                        log -> extractTitle(log.message(), log.severity())
                ));

        var idCounter = new AtomicInteger(1);

        // Parallel cluster creation
        return groupedByTitle.entrySet().parallelStream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> {
                    var title = entry.getKey();
                    var logs = entry.getValue().stream()
                            .sorted(Comparator.comparing(RawLogEntry::timestamp))
                            .toList();

                    var affectedHosts = logs.stream()
                            .map(RawLogEntry::host)
                            .filter(h -> h != null && !h.isEmpty())
                            .distinct()
                            .toList();

                    var affectedServices = logs.stream()
                            .map(RawLogEntry::service)
                            .filter(s -> s != null && !s.isEmpty())
                            .distinct()
                            .toList();

                    var sampleLogs = logs.stream()
                            .limit(3)
                            .map(l -> SampleLog.builder()
                                    .timestamp(l.timestamp())
                                    .message(l.message())
                                    .host(l.host())
                                    .requestId(l.requestId())
                                    .build())
                            .toList();

                    var severity = logs.stream()
                            .map(RawLogEntry::severity)
                            .max(Comparator.comparing(this::severityRank))
                            .orElse("INFO");

                    return ClusterLogEntry.builder()
                            .id("c" + idCounter.getAndIncrement())
                            .title(title)
                            .count(logs.size())
                            .firstSeen(logs.get(0).timestamp())
                            .lastSeen(logs.get(logs.size() - 1).timestamp())
                            .affectedHosts(affectedHosts)
                            .affectedServices(affectedServices)
                            .sampleLogs(sampleLogs)
                            .severity(severity)
                            .build();
                })
                .sorted(Comparator
                        .comparingInt((ClusterLogEntry e) -> severityRank(e.getSeverity()))
                        .reversed()
                        .thenComparingInt(ClusterLogEntry::getCount).reversed())
                .toList();
    }

    /**
     * Parallel statistics calculation using LongAdder for thread safety
     */
    private LogStatistics calculateStatisticsParallel(List<RawLogEntry> allLogs, List<RawLogEntry> errorLogs) {
        var errorCount = new LongAdder();
        var warnCount = new LongAdder();

        allLogs.parallelStream().forEach(log -> {
            switch (log.severity()) {
                case "ERROR" -> errorCount.increment();
                case "WARN" -> warnCount.increment();
            }
        });

        return new LogStatistics(errorCount.intValue(), warnCount.intValue());
    }

    /**
     * Extract title with optimized string operations
     */
    private String extractTitle(String message, String severity) {
        // Try exception pattern first
        var exceptionMatcher = EXCEPTION_PATTERN.matcher(message);
        if (exceptionMatcher.find()) {
            var exception = exceptionMatcher.group(1);
            var location = exceptionMatcher.group(2);
            if (location != null) {
                var lastDot = location.lastIndexOf('.');
                var className = lastDot > 0 ? location.substring(lastDot + 1) : location;
                return exception + " in " + className;
            }
            return exception;
        }

        // Clean and extract title
        var cleaned = message
                .replaceAll("\\d{4}-\\d{2}-\\d{2}", "")
                .replaceAll("\\d{2}:\\d{2}:\\d{2}", "")
                .replaceAll("\\b(ERROR|WARN|INFO|DEBUG)\\b", "")
                .strip();

        var dotIndex = cleaned.indexOf('.');
        var endIndex = dotIndex > 0 ? Math.min(dotIndex, 50) : Math.min(cleaned.length(), 50);

        var title = cleaned.substring(0, endIndex).strip();

        if (title.isEmpty() || title.length() < 10) {
            var words = cleaned.split("\\s+", 6);
            title = severity + ": " + String.join(" ", Arrays.copyOf(words, Math.min(5, words.length)));
        }

        return title;
    }

    private String truncateMessage(String message, int maxLength) {
        return message == null ? "" :
                message.length() <= maxLength ? message :
                        message.substring(0, maxLength) + "...";
    }

    private int severityRank(String severity) {
        return SEVERITY_RANKS.getOrDefault(severity, 0);
    }

    private String extractSeverity(String message) {
        var matcher = SEVERITY_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        var lower = message.toLowerCase();
        return (lower.contains("exception") || lower.contains("error")) ? "ERROR" : "INFO";
    }

    private String extractService(String message) {
        var matcher = SERVICE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : "eptBackendApp";
    }

    private String extractHost(String logStreamName) {
        var ipIdx = logStreamName.indexOf("ip-");
        if (ipIdx >= 0) {
            var endIdx = logStreamName.indexOf("/", ipIdx);
            endIdx = endIdx > 0 ? endIdx : Math.min(ipIdx + 20, logStreamName.length());
            return logStreamName.substring(ipIdx, endIdx);
        }
        return logStreamName.length() > 20 ? logStreamName.substring(0, 20) : logStreamName;
    }

    private String extractRequestId(String message) {
        var matcher = REQUEST_ID_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String generateQuickSummary(List<ClusterLogEntry> logs) {
        if (logs.isEmpty()) {
            return "No logs found in " + LOG_GROUP_NAME + " in the last 6 hours.";
        }

        try {
            var summaryInput = new StringBuilder(500);
            summaryInput.append("Log Group: ").append(LOG_GROUP_NAME).append("\n");
            summaryInput.append("Critical issues:\n");

            for (int i = 0; i < Math.min(5, logs.size()); i++) {
                var log = logs.get(i);
                summaryInput.append("%d. [%s] %s (count: %d)\n".formatted(
                        i + 1, log.getSeverity(), log.getTitle(), log.getCount()
                ));
            }

            var prompt = "Analyze these logs and provide a brief 2-sentence summary:\n\n%s\n\nSummary:".formatted(summaryInput);
            var request = new OllamaGenerateRequest("qwen2.5-coder:7b", prompt, false);

            var response = ollamaWebClient
                    .post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response != null && response.getResponse() != null) {
                var summary = response.getResponse().strip();
                return summary.length() > 300 ? summary.substring(0, 297) + "..." : summary;
            }

        } catch (Exception e) {
            log.debug("AI summary failed: {}", e.getMessage());
        }

        return generateFallbackSummary(logs);
    }

    private String generateFallbackSummary(List<ClusterLogEntry> logs) {
        var errorCount = logs.stream().filter(l -> "ERROR".equals(l.getSeverity())).count();
        var warnCount = logs.stream().filter(l -> "WARN".equals(l.getSeverity())).count();

        return "Log group %s: Found %d error patterns and %d warning patterns in last 6 hours from %d log streams.".formatted(
                LOG_GROUP_NAME, errorCount, warnCount, STREAM_LIMIT
        );
    }

    private ClusterLogsResponse buildEmptyResponse() {
        return ClusterLogsResponse.builder()
                .logs(List.of())
                .summary("No logs found in log group: " + LOG_GROUP_NAME)
                .totalErrors(0)
                .totalWarnings(0)
                .totalLogs(0)
                .clusterId(LOG_GROUP_NAME)
                .timeRange("6h")
                .build();
    }

    /**
     * Immutable record for raw log entries (Java 16+)
     * Records are more efficient than Lombok @Data for immutable data
     */
    private record RawLogEntry(
            String timestamp,
            String message,
            String host,
            String severity,
            String service,
            String requestId
    ) {}

    /**
     * Record for statistics to avoid mutable state
     */
    private record LogStatistics(int errors, int warnings) {}
}