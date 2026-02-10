package com.devops.agent.util;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for fetching logs from CloudWatch Logs
 * Shared between DevOpsInsightService and CloudWatchLogsService
 */
@Slf4j
public class CloudWatchLogsUtil {

    private CloudWatchLogsUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Fetch all logs from a log group within a time range
     * Uses log streams approach for reliable fetching
     *
     * @param client CloudWatch Logs client
     * @param logGroupName Log group name
     * @param startTime Start time in milliseconds
     * @param endTime End time in milliseconds
     * @param maxStreams Maximum number of log streams to fetch (0 = unlimited)
     * @param maxEventsPerStream Maximum events per stream (0 = unlimited)
     * @return List of raw log events
     */
    public static List<OutputLogEvent> fetchLogsFromGroup(
            CloudWatchLogsClient client,
            String logGroupName,
            long startTime,
            long endTime,
            int maxStreams,
            int maxEventsPerStream) {

        List<OutputLogEvent> allLogs = new ArrayList<>();

        try {
            // Get log streams for this log group (most recent first)
            DescribeLogStreamsRequest.Builder streamsRequestBuilder = DescribeLogStreamsRequest.builder()
                    .logGroupName(logGroupName)
                    .orderBy(OrderBy.LAST_EVENT_TIME)
                    .descending(true);

            if (maxStreams > 0) {
                streamsRequestBuilder.limit(maxStreams);
            }

            DescribeLogStreamsRequest streamsRequest = streamsRequestBuilder.build();
            DescribeLogStreamsResponse streamsResponse = client.describeLogStreams(streamsRequest);

            log.info("Found {} log streams in group {}", streamsResponse.logStreams().size(), logGroupName);

            for (LogStream stream : streamsResponse.logStreams()) {
                // Skip streams that have no events in the time range (basic optimization)
                if (stream.lastIngestionTime() != null && stream.lastIngestionTime() < startTime) {
                    continue;
                }

                List<OutputLogEvent> streamLogs = fetchLogsFromStream(
                        client, logGroupName, stream.logStreamName(),
                        startTime, endTime, maxEventsPerStream);
                allLogs.addAll(streamLogs);
            }

        } catch (CloudWatchLogsException e) {
            log.error("Error fetching logs from group {}: {}", logGroupName, e.awsErrorDetails().errorMessage(), e);
        }

        return allLogs;
    }

    /**
     * Fetch logs from a specific log stream with pagination
     */
    private static List<OutputLogEvent> fetchLogsFromStream(
            CloudWatchLogsClient client,
            String logGroupName,
            String logStreamName,
            long startTime,
            long endTime,
            int maxEvents) {

        List<OutputLogEvent> logs = new ArrayList<>();
        String nextToken = null;
        int totalFetched = 0;

        try {
            do {
                GetLogEventsRequest.Builder requestBuilder = GetLogEventsRequest.builder()
                        .logGroupName(logGroupName)
                        .logStreamName(logStreamName)
                        .startTime(startTime)
                        .endTime(endTime)
                        .startFromHead(true);

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                if (maxEvents > 0) {
                    requestBuilder.limit(Math.min(10000, maxEvents - totalFetched));
                }

                GetLogEventsResponse response = client.getLogEvents(requestBuilder.build());

                List<OutputLogEvent> events = response.events();
                logs.addAll(events);
                totalFetched += events.size();

                // Check if we have more events and haven't hit the limit
                String newToken = response.nextForwardToken();
                if (newToken != null && !newToken.equals(nextToken) &&
                    (maxEvents == 0 || totalFetched < maxEvents)) {
                    nextToken = newToken;
                } else {
                    break;
                }

            } while (true);

            if (!logs.isEmpty()) {
                log.debug("Fetched {} events from stream {} in group {}",
                    logs.size(), logStreamName, logGroupName);
            }

        } catch (CloudWatchLogsException e) {
            log.warn("Error fetching logs from stream {} in group {}: {}",
                logStreamName, logGroupName, e.awsErrorDetails().errorMessage());
        }

        return logs;
    }

    /**
     * Fetch logs with filter pattern (alternative approach)
     * Use this when you need to filter by pattern
     */
    public static List<FilteredLogEvent> fetchLogsWithFilter(
            CloudWatchLogsClient client,
            String logGroupName,
            long startTime,
            long endTime,
            String filterPattern) {

        List<FilteredLogEvent> allLogs = new ArrayList<>();
        String nextToken = null;

        try {
            do {
                FilterLogEventsRequest.Builder requestBuilder = FilterLogEventsRequest.builder()
                        .logGroupName(logGroupName)
                        .startTime(startTime)
                        .endTime(endTime)
                        .limit(10000);

                if (filterPattern != null && !filterPattern.isEmpty()) {
                    requestBuilder.filterPattern(filterPattern);
                }

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                FilterLogEventsResponse response = client.filterLogEvents(requestBuilder.build());
                allLogs.addAll(response.events());
                nextToken = response.nextToken();

                log.debug("Fetched {} filtered events from group {} (nextToken={})",
                    response.events().size(), logGroupName, nextToken);

            } while (nextToken != null);

        } catch (CloudWatchLogsException e) {
            log.error("Error filtering logs from group {}: {}", logGroupName, e.awsErrorDetails().errorMessage());
        }

        return allLogs;
    }

    /**
     * Fetch CloudWatch metrics for multiple services
     *
     * @param client CloudWatch client with proper credentials
     * @param serviceNames List of service names to fetch metrics for
     * @param namespace AWS namespace (e.g., "AWS/ECS")
     * @param metricNames List of metric names (e.g., "CPUUtilization", "MemoryUtilization")
     * @param start Start time
     * @param end End time
     * @return Map of service name to map of metric name to metric data results
     */
    public static Map<String, Map<String, MetricDataResult>> fetchMetricsForServices(
            CloudWatchClient client,
            List<String> serviceNames,
            String namespace,
            List<String> metricNames,
            Instant start,
            Instant end) {

        Map<String, Map<String, MetricDataResult>> allMetrics = new HashMap<>();

        if (serviceNames == null || serviceNames.isEmpty()) {
            log.warn("No service names provided for metrics fetch");
            return allMetrics;
        }

        for (String serviceName : serviceNames) {
            Map<String, MetricDataResult> serviceMetrics = fetchMetricsForService(
                    client, serviceName, namespace, metricNames, start, end);
            allMetrics.put(serviceName, serviceMetrics);
        }

        return allMetrics;
    }

    /**
     * Fetch CloudWatch metrics for a single service
     */
    private static Map<String, MetricDataResult> fetchMetricsForService(
            CloudWatchClient client,
            String serviceName,
            String namespace,
            List<String> metricNames,
            Instant start,
            Instant end) {

        Map<String, MetricDataResult> metrics = new HashMap<>();

        try {
            List<MetricDataQuery> queries = new ArrayList<>();

            // Build queries for all metrics
            for (int i = 0; i < metricNames.size(); i++) {
                String metricName = metricNames.get(i);

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
                        .id("m" + i)
                        .metricStat(metricStat)
                        .build();

                queries.add(query);
            }

            GetMetricDataRequest request = GetMetricDataRequest.builder()
                    .startTime(start)
                    .endTime(end)
                    .metricDataQueries(queries)
                    .build();

            GetMetricDataResponse response = client.getMetricData(request);

            // Map results back to metric names
            for (int i = 0; i < response.metricDataResults().size(); i++) {
                MetricDataResult result = response.metricDataResults().get(i);
                if (i < metricNames.size()) {
                    metrics.put(metricNames.get(i), result);
                }
            }

            log.debug("Fetched {} metrics for service {}", metrics.size(), serviceName);

        } catch (CloudWatchException e) {
            log.error("Error fetching metrics for service {}: {}", serviceName, e.awsErrorDetails().errorMessage());
        }

        return metrics;
    }
}
