package com.devops.agent.service;

import com.devops.agent.model.MetricSnapshot;
import com.devops.agent.model.ProjectConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance async service for metrics collection using multithreading
 * Fetches metrics from CloudWatch and saves to DynamoDB in background
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetricProcessingService {

    private final ProjectConfigurationService projectConfigurationService;
    private final DynamoDbTable<MetricSnapshot> metricSnapshotTable;
    private final SecretsManagerService secretsManagerService;

    /**
     * Asynchronously fetch and save metrics for a project
     */
    @Async
    public CompletableFuture<Integer> processMetricsAsync(String projectId) {
        log.info("Starting async metric processing for project: {}", projectId);
        long startTime = System.currentTimeMillis();

        try {
            ProjectConfiguration project = projectConfigurationService.getConfiguration(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

            if (!Boolean.TRUE.equals(project.getEnabled())) {
                throw new IllegalArgumentException("Project is disabled: " + projectId);
            }

            // Determine time window
            long endTimeMs = System.currentTimeMillis();
            long startTimeMs = endTimeMs - (60 * 60 * 1000); // Last 1 hour for metrics

            // Fetch metrics
            List<MetricSnapshot> metrics;
            try (CloudWatchClient metricsClient = createProjectCloudWatchClient(projectId)) {
                metrics = fetchCloudWatchMetrics(project, metricsClient, startTimeMs, endTimeMs);
            }

            // Save to DynamoDB
            metrics.forEach(metricSnapshotTable::putItem);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Async metric processing completed for project {} in {}ms. Saved {} metrics.",
                    projectId, duration, metrics.size());

            return CompletableFuture.completedFuture(metrics.size());

        } catch (Exception e) {
            log.error("Error in async metric processing for project {}: {}", projectId, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fetch CloudWatch metrics with parallel processing and aggregation
     * Calculates average values for each metric type and stores only one snapshot per metric
     */
    private List<MetricSnapshot> fetchCloudWatchMetrics(
            ProjectConfiguration project,
            CloudWatchClient client,
            long startTime,
            long endTime) {

        List<MetricSnapshot> allMetrics = Collections.synchronizedList(new ArrayList<>());
        Instant startInstant = Instant.ofEpochMilli(startTime);
        Instant endInstant = Instant.ofEpochMilli(endTime);

        // Discover running EC2 instances
        List<String> instanceIds = discoverRunningInstances(project);
        log.info("Discovered {} running EC2 instances for project {}",
                instanceIds.size(), project.getProjectId());

        if (instanceIds.isEmpty()) {
            log.warn("No running instances found for project: {}", project.getProjectId());
            return allMetrics;
        }

        // Metrics to fetch
        List<String> metricNames = Arrays.asList("CPUUtilization", "NetworkIn", "NetworkOut");

        // Fetch metrics in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String instanceId : instanceIds) {
            for (String metricName : metricNames) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    List<MetricSnapshot> snapshots = fetchMetricForInstance(
                            project, client, instanceId, metricName, startInstant, endInstant);
                    allMetrics.addAll(snapshots);
                });
                futures.add(future);
            }
        }

        // Wait for all metrics to be fetched
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Fetched {} raw metric datapoints before aggregation", allMetrics.size());

        // Aggregate metrics: Calculate average for each metric type
        List<MetricSnapshot> aggregatedMetrics = aggregateMetricsByType(project.getProjectId(), allMetrics);

        log.info("Aggregated to {} metric snapshots (one per metric type)", aggregatedMetrics.size());
        return aggregatedMetrics;
    }

    /**
     * Aggregate metrics by type, calculating average values
     * Returns one MetricSnapshot per metric type with current timestamp
     */
    private List<MetricSnapshot> aggregateMetricsByType(String projectId, List<MetricSnapshot> rawMetrics) {
        if (rawMetrics.isEmpty()) {
            return List.of();
        }

        // Group metrics by metric name
        Map<String, List<MetricSnapshot>> metricsByName = new HashMap<>();
        for (MetricSnapshot metric : rawMetrics) {
            metricsByName.computeIfAbsent(metric.getMetricName(), k -> new ArrayList<>()).add(metric);
        }

        List<MetricSnapshot> aggregated = new ArrayList<>();
        long currentTimestamp = System.currentTimeMillis();

        // Calculate average for each metric type
        for (Map.Entry<String, List<MetricSnapshot>> entry : metricsByName.entrySet()) {
            String metricName = entry.getKey();
            List<MetricSnapshot> snapshots = entry.getValue();

            // Calculate average value
            double sum = 0.0;
            int count = 0;
            String unit = "None";

            for (MetricSnapshot snapshot : snapshots) {
                if (snapshot.getValue() != null) {
                    sum += snapshot.getValue();
                    count++;
                    if (snapshot.getUnit() != null) {
                        unit = snapshot.getUnit();
                    }
                }
            }

            if (count > 0) {
                double average = sum / count;

                // Create aggregated snapshot with current timestamp
                MetricSnapshot aggregatedSnapshot = MetricSnapshot.builder()
                        .projectId(projectId)
                        .timestamp(currentTimestamp)
                        .serviceName(projectId) // Use projectId as service name for aggregated metrics
                        .metricName(metricName)
                        .value(average)
                        .unit(unit)
                        .dimensions(Map.of("aggregated", "true", "datapoints", String.valueOf(count)))
                        .build();

                aggregated.add(aggregatedSnapshot);

                log.info("Aggregated {} datapoints for {}: average = {} {}",
                        count, metricName, String.format("%.2f", average), unit);
            }
        }

        return aggregated;
    }

    /**
     * Discover running EC2 instances for the project
     */
    private List<String> discoverRunningInstances(ProjectConfiguration project) {
        List<String> instanceIds = new ArrayList<>();

        try (Ec2Client ec2Client = createProjectEC2Client(project.getProjectId())) {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .filters(Filter.builder()
                            .name("instance-state-name")
                            .values("running")
                            .build())
                    .build();

            DescribeInstancesResponse response = ec2Client.describeInstances(request);

            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    instanceIds.add(instance.instanceId());
                }
            }

        } catch (Exception e) {
            log.error("Error discovering EC2 instances: {}", e.getMessage(), e);
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

            for (Datapoint datapoint : response.datapoints()) {
                MetricSnapshot snapshot = MetricSnapshot.builder()
                        .projectId(project.getProjectId())
                        .timestamp(datapoint.timestamp().toEpochMilli())
                        .serviceName(instanceId)
                        .metricName(metricName)
                        .value(datapoint.average())
                        .unit(datapoint.unit().toString())
                        .dimensions(Map.of("InstanceId", instanceId))
                        .build();

                snapshots.add(snapshot);
            }

            log.debug("Fetched {} datapoints for {} - {}",
                    snapshots.size(), instanceId, metricName);

        } catch (CloudWatchException e) {
            log.error("Error fetching metric {} for instance {}: {}",
                    metricName, instanceId, e.awsErrorDetails().errorMessage());
        }

        return snapshots;
    }

    /**
     * Create CloudWatch client with project-specific credentials
     */
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

            if (awsAccessKey != null && awsSecretKey != null &&
                !awsAccessKey.isEmpty() && !awsSecretKey.isEmpty()) {
                log.debug("Using project-specific AWS credentials for CloudWatch");
                return CloudWatchClient.builder()
                        .region(region)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
                        .build();
            }

            log.debug("Using default AWS credentials for CloudWatch");
            return CloudWatchClient.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (Exception e) {
            log.error("Error creating CloudWatchClient for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Failed to create CloudWatchClient", e);
        }
    }

    /**
     * Create EC2 client with project-specific credentials
     */
    private Ec2Client createProjectEC2Client(String projectId) {
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

            if (awsAccessKey != null && awsSecretKey != null &&
                !awsAccessKey.isEmpty() && !awsSecretKey.isEmpty()) {
                log.debug("Using project-specific AWS credentials for EC2");
                return Ec2Client.builder()
                        .region(region)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
                        .build();
            }

            log.debug("Using default AWS credentials for EC2");
            return Ec2Client.builder()
                    .region(region)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (Exception e) {
            log.error("Error creating EC2Client for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Failed to create EC2Client", e);
        }
    }
}
