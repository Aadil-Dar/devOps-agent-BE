package com.devops.agent.service;

import com.devops.agent.model.ProjectConfiguration;
import com.devops.agent.model.PullRequestResponse;
import com.devops.agent.model.ZombieResourceDtos.BreakdownDto;
import com.devops.agent.model.ZombieResourceDtos.TrendPointDto;
import com.devops.agent.model.ZombieResourceDtos.ZombieResourceDto;
import com.devops.agent.model.ZombieResourceDtos.ZombieResourceResponseDto;
import com.devops.agent.model.ZombieResourceDtos.ZombieResourceStatus;
import com.devops.agent.model.ZombieResourceDtos.ZombieResourceSummaryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.model.DescribeTagsRequest;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagDescription;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTagsResponse;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.ListPipelineExecutionsRequest;
import software.amazon.awssdk.services.codepipeline.model.ListPipelineExecutionsResponse;
import software.amazon.awssdk.services.codepipeline.model.PipelineExecutionSummary;
import software.amazon.awssdk.services.codepipeline.model.PipelineSummary;
import software.amazon.awssdk.services.codepipeline.model.CodePipelineException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.FilterType;
import software.amazon.awssdk.services.pricing.model.GetProductsRequest;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ZombieResourceService {

    private final Ec2Client ec2Client;
    private final ElasticLoadBalancingV2Client elbClient;
    private final CloudWatchClient cloudWatchClient;
    private final ProjectConfigurationService projectConfigurationService;
    private final SecretsManagerService secretsManagerService;
    private final GitHubService gitHubService;
    private final CodePipelineClient codePipelineClient;
    private final SecretsManagerClient secretsManagerClient;

    private PricingClient pricingClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int SNAPSHOT_IDLE_THRESHOLD_DAYS = 7;
    private static final int LOAD_BALANCER_IDLE_LOOKBACK_DAYS = 4;
    private static final int VOLUME_IDLE_THRESHOLD_DAYS = 3;
    private static final int PIPELINE_IDLE_THRESHOLD_DAYS = 7;
    private static final int SECRET_IDLE_THRESHOLD_DAYS = 30;

    public ZombieResourceResponseDto findZombieResources(String projectId, boolean notifyAuthors) {
        try {
            Region region = resolveRegion(projectId);
            Ec2Client projectEc2 = projectId == null ? ec2Client : buildEc2Client(projectId, region);
            ElasticLoadBalancingV2Client projectElb = projectId == null ? elbClient : buildElbClient(projectId, region);
            CloudWatchClient projectCw = projectId == null ? cloudWatchClient : buildCloudWatchClient(projectId, region);
            CodePipelineClient projectCp = projectId == null ? codePipelineClient : buildCodePipelineClient(projectId, region);
            SecretsManagerClient projectSm = projectId == null ? secretsManagerClient : buildSecretsManagerClient(projectId, region);

            List<ZombieResourceDto> resources = new ArrayList<>();
            resources.addAll(findIdleEips(projectEc2, region));
            resources.addAll(findAgingSnapshots(projectEc2, region));
            resources.addAll(findIdleVolumes(projectEc2, region));
//            resources.addAll(findIdleLoadBalancers(projectElb, projectCw, region));
            resources.addAll(findIdleSecrets(projectSm, region));
//            resources.addAll(findIdlePipelines(projectCp, region));

            enrichWithGitHubMetadata(resources, projectId);

            if (notifyAuthors) {
                resources.stream()
                        .filter(r -> r.getStatus() == ZombieResourceStatus.FLAGGED)
                        .forEach(this::sendSlackNudge);
            }

            return assembleResponse(resources);
        } catch (Exception e) {
            log.warn("Idle resource scan failed; returning fallback sample. Reason: {}", e.getMessage());
            return fallbackResponse();
        }
    }

    private Region resolveRegion(String projectId) {
        if (projectId == null) {
            return ec2Client.serviceClientConfiguration().region();
        }
        Optional<ProjectConfiguration> config = projectConfigurationService.getConfiguration(projectId);
        String region = config.map(ProjectConfiguration::getAwsRegion).orElse("eu-west-1");
        return Region.of(region);
    }

    private Ec2Client buildEc2Client(String projectId, Region region) {
        Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
        String accessKey = secrets.get("aws-access-key");
        String secretKey = secrets.get("aws-secret-key");
        if (isPopulated(accessKey) && isPopulated(secretKey)) {
            return Ec2Client.builder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        return Ec2Client.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    private ElasticLoadBalancingV2Client buildElbClient(String projectId, Region region) {
        Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
        String accessKey = secrets.get("aws-access-key");
        String secretKey = secrets.get("aws-secret-key");
        if (isPopulated(accessKey) && isPopulated(secretKey)) {
            return ElasticLoadBalancingV2Client.builder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        return ElasticLoadBalancingV2Client.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    private CloudWatchClient buildCloudWatchClient(String projectId, Region region) {
        Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
        String accessKey = secrets.get("aws-access-key");
        String secretKey = secrets.get("aws-secret-key");
        if (isPopulated(accessKey) && isPopulated(secretKey)) {
            return CloudWatchClient.builder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        return CloudWatchClient.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    private CodePipelineClient buildCodePipelineClient(String projectId, Region region) {
        Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
        String accessKey = secrets.get("aws-access-key");
        String secretKey = secrets.get("aws-secret-key");
        if (isPopulated(accessKey) && isPopulated(secretKey)) {
            return CodePipelineClient.builder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        return CodePipelineClient.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    private SecretsManagerClient buildSecretsManagerClient(String projectId, Region region) {
        Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
        String accessKey = secrets.get("aws-access-key");
        String secretKey = secrets.get("aws-secret-key");
        if (isPopulated(accessKey) && isPopulated(secretKey)) {
            return SecretsManagerClient.builder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        return SecretsManagerClient.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    private List<ZombieResourceDto> findIdleEips(Ec2Client client, Region region) {
        List<Address> addresses = client.describeAddresses(DescribeAddressesRequest.builder().build()).addresses();
        return addresses.stream()
                .filter(a -> a.associationId() == null)
                .map(a -> ZombieResourceDto.builder()
                        .id(a.allocationId())
                        .name(a.publicIp())
                        .type("ElasticIP")
                        .region(region.id())
                        .status(ZombieResourceStatus.IDLE)
                        .idleDays(4)
                        .lastActivityDays(4)
                        .costPerDay(0.005)
                        .build())
                .collect(Collectors.toList());
    }

    private List<ZombieResourceDto> findAgingSnapshots(Ec2Client client, Region region) {
        List<Snapshot> snapshots = client.describeSnapshots(DescribeSnapshotsRequest.builder()
                        .ownerIds("self")
                        .filters(Filter.builder().name("status").values("completed").build())
                        .build())
                .snapshots();

        Instant now = Instant.now();
        List<ZombieResourceDto> results = new ArrayList<>();
        for (Snapshot snapshot : snapshots) {
            int ageDays = (int) ChronoUnit.DAYS.between(snapshot.startTime(), now);
            if (ageDays >= SNAPSHOT_IDLE_THRESHOLD_DAYS) {
                results.add(ZombieResourceDto.builder()
                        .id(snapshot.snapshotId())
                        .name(snapshot.description())
                        .type("Snapshot")
                        .region(region.id())
                        .status(ZombieResourceStatus.FLAGGED)
                        .idleDays(ageDays)
                        .lastActivityDays(ageDays)
                        .costPerDay(0.05)
                        .build());
            }
        }
        return results;
    }

    private List<ZombieResourceDto> findIdleVolumes(Ec2Client client, Region region) {
        List<Volume> volumes = client.describeVolumes(DescribeVolumesRequest.builder()
                        .filters(Filter.builder().name("status").values("available").build())
                        .build())
                .volumes();

        Instant now = Instant.now();
        List<ZombieResourceDto> results = new ArrayList<>();
        for (Volume v : volumes) {
            int ageDays = (int) ChronoUnit.DAYS.between(v.createTime(), now);
            if (ageDays >= VOLUME_IDLE_THRESHOLD_DAYS) {
                results.add(ZombieResourceDto.builder()
                        .id(v.volumeId())
                        .name(Optional.ofNullable(v.tags()).orElse(Collections.emptyList()).stream()
                                .filter(t -> "Name".equalsIgnoreCase(t.key()))
                                .findFirst()
                                .map(Tag::value)
                                .orElse(v.volumeId()))
                        .type("EBS Volume")
                        .region(region.id())
                        .status(ZombieResourceStatus.FLAGGED)
                        .idleDays(ageDays)
                        .lastActivityDays(ageDays)
                        .costPerDay(0.1)
                        .build());
            }
        }
        return results;
    }

    private List<ZombieResourceDto> findIdleLoadBalancers(ElasticLoadBalancingV2Client elb, CloudWatchClient cw, Region region) {
        List<LoadBalancer> loadBalancers = elb.describeLoadBalancers(DescribeLoadBalancersRequest.builder().build()).loadBalancers();
        List<ZombieResourceDto> result = new ArrayList<>();
        Instant end = Instant.now();
        Instant start = end.minus(LOAD_BALANCER_IDLE_LOOKBACK_DAYS, ChronoUnit.DAYS);

        for (LoadBalancer lb : loadBalancers) {
            double requests = fetchRequestCount(cw, lb.loadBalancerArn(), start, end);
            if (requests < 1.0d) {
                result.add(ZombieResourceDto.builder()
                        .id(lb.loadBalancerArn())
                        .name(lb.loadBalancerName())
                        .type("LoadBalancer")
                        .region(region.id())
                        .status(ZombieResourceStatus.FLAGGED)
                        .idleDays(LOAD_BALANCER_IDLE_LOOKBACK_DAYS)
                        .lastActivityDays(LOAD_BALANCER_IDLE_LOOKBACK_DAYS)
                        .costPerDay(1.20)
                        .build());
            }
        }
        return result;
    }

    private List<ZombieResourceDto> findIdleSecrets(SecretsManagerClient client, Region region) {
        try {
            ListSecretsResponse response = client.listSecrets(ListSecretsRequest.builder().maxResults(100).build());
            Instant now = Instant.now();
            List<ZombieResourceDto> results = new ArrayList<>();
            for (SecretListEntry entry : response.secretList()) {
                Instant lastUsed = Optional.ofNullable(entry.lastAccessedDate()).orElse(entry.lastChangedDate());
                if (lastUsed == null) {
                    lastUsed = entry.createdDate();
                }
                if (lastUsed == null) continue;
                int idle = (int) ChronoUnit.DAYS.between(lastUsed, now);
                if (idle >= SECRET_IDLE_THRESHOLD_DAYS) {
                    double costPerDay = resolveDailyPrice(
                            "AWSSecretsManager",
                            Map.of(
                                    "location", pricingLocation(region),
                                    "usagetype", "SecretUsage"
                            ),
                            0.02d
                    );
                    results.add(ZombieResourceDto.builder()
                            .id(entry.arn())
                            .name(entry.name())
                            .type("Secret")
                            .region(region.id())
                            .status(ZombieResourceStatus.FLAGGED)
                            .idleDays(idle)
                            .lastActivityDays(idle)
                            .costPerDay(costPerDay)
                            .build());
                }
            }
            return results;
        } catch (ResourceNotFoundException e) {
            log.debug("Secrets list not found: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.debug("Failed to list secrets: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ZombieResourceDto> findIdlePipelines(CodePipelineClient client, Region region) {
        try {
            List<PipelineSummary> pipelines = client.listPipelines().pipelines();
            List<ZombieResourceDto> results = new ArrayList<>();
            Instant now = Instant.now();
            for (PipelineSummary p : pipelines) {
                ListPipelineExecutionsResponse execs = client.listPipelineExecutions(ListPipelineExecutionsRequest.builder()
                        .pipelineName(p.name())
                        .maxResults(1)
                        .build());
                Optional<PipelineExecutionSummary> latest = execs.pipelineExecutionSummaries().stream().findFirst();
                Instant lastExec = latest.map(PipelineExecutionSummary::lastUpdateTime).orElse(p.created());
                if (lastExec == null) {
                    lastExec = now.minus(PIPELINE_IDLE_THRESHOLD_DAYS, ChronoUnit.DAYS);
                }
                int idle = (int) ChronoUnit.DAYS.between(lastExec, now);
                if (idle >= PIPELINE_IDLE_THRESHOLD_DAYS) {
                    results.add(ZombieResourceDto.builder()
                            .id(p.name())
                            .name(p.name())
                            .type("CodePipeline")
                            .region(region.id())
                            .status(ZombieResourceStatus.FLAGGED)
                            .idleDays(idle)
                            .lastActivityDays(idle)
                            .costPerDay(0.5)
                            .build());
                }
            }
            return results;
        } catch (CodePipelineException e) {
            log.debug("Pipeline scan failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private double fetchRequestCount(CloudWatchClient cw, String lbArn, Instant start, Instant end) {
        try {
            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                    .namespace("AWS/ApplicationELB")
                    .metricName("RequestCount")
                    .dimensions(Dimension.builder().name("LoadBalancer").value(lbArn).build())
                    .statistics(Statistic.valueOf(Statistic.SUM.toString()))
                    .startTime(start)
                    .endTime(end)
                    .period(3600)
                    .unit(StandardUnit.COUNT)
                    .build();

            GetMetricStatisticsResponse response = cw.getMetricStatistics(request);
            return response.datapoints().stream()
                    .mapToDouble(dp -> dp.sum() == null ? 0.0 : dp.sum())
                    .sum();
        } catch (Exception e) {
            log.debug("Failed to fetch RequestCount for {}: {}", lbArn, e.getMessage());
            return 0.0d;
        }
    }

    private void enrichWithGitHubMetadata(List<ZombieResourceDto> resources, String projectId) {
        Map<String, String> tagsByResource = fetchTags(resources);
        for (ZombieResourceDto resource : resources) {
            String prTag = tagsByResource.get(resource.getId() + "::pr");
            if (prTag != null) {
                resource.setPrNumber(prTag);
                try {
                    int prNumber = Integer.parseInt(prTag.replace("PR-", "").replace("#", ""));
                    PullRequestResponse pr = gitHubService.getPullRequest(projectId, prNumber);
                    resource.setAuthor(pr.getAuthor());
                } catch (Exception e) {
                    log.debug("Unable to enrich PR {}: {}", prTag, e.getMessage());
                }
            }
            String authorTag = tagsByResource.get(resource.getId() + "::author");
            if (authorTag != null) {
                resource.setAuthor(authorTag);
            }
        }
    }

    private Map<String, String> fetchTags(List<ZombieResourceDto> resources) {
        Map<String, String> result = new HashMap<>();
        List<String> ec2ResourceIds = resources.stream()
                .filter(r -> r.getType().equals("ElasticIP") || r.getType().equals("Snapshot") || r.getType().equals("EBS Volume"))
                .map(ZombieResourceDto::getId)
                .toList();

        if (!ec2ResourceIds.isEmpty()) {
            try {
                List<TagDescription> tags = ec2Client.describeTags(DescribeTagsRequest.builder()
                                .filters(Filter.builder().name("resource-id").values(ec2ResourceIds).build())
                                .build())
                        .tags();
                for (TagDescription tag : tags) {
                    if (isPrTag(tag.key())) {
                        result.put(tag.resourceId() + "::pr", tag.value());
                    }
                    if (isAuthorTag(tag.key())) {
                        result.put(tag.resourceId() + "::author", tag.value());
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to fetch EC2 tags: {}", e.getMessage());
            }
        }

        List<String> lbArns = resources.stream()
                .filter(r -> r.getType().equals("LoadBalancer"))
                .map(ZombieResourceDto::getId)
                .toList();
        if (!lbArns.isEmpty()) {
            try {
                DescribeTagsResponse response = elbClient.describeTags(builder -> builder.resourceArns(lbArns));
                response.tagDescriptions().forEach(desc -> desc.tags().forEach(tag -> {
                    if (isPrTag(tag.key())) {
                        result.put(desc.resourceArn() + "::pr", tag.value());
                    }
                    if (isAuthorTag(tag.key())) {
                        result.put(desc.resourceArn() + "::author", tag.value());
                    }
                }));
            } catch (LoadBalancerNotFoundException e) {
                log.debug("LB not found while reading tags: {}", e.getMessage());
            } catch (Exception e) {
                log.debug("Failed to fetch ELB tags: {}", e.getMessage());
            }
        }
        return result;
    }

    private boolean isPrTag(String key) {
        String k = key.toLowerCase();
        return k.contains("pr") || k.contains("pullrequest") || k.contains("pull-request");
    }

    private boolean isAuthorTag(String key) {
        String k = key.toLowerCase();
        return k.contains("author") || k.contains("createdby") || k.contains("owner") || k.contains("creator");
    }

    private void sendSlackNudge(ZombieResourceDto resource) {
        try {
            Map<String, String> secrets = secretsManagerService.getSecrets("default");
            String webhook = secrets.getOrDefault("slack-webhook", null);
            if (!isPopulated(webhook)) {
                log.debug("Slack webhook not configured; skipping notify for {}", resource.getId());
                return;
            }

            String mention = resource.getAuthor() != null ? "@" + resource.getAuthor() : "team";
            String prText = resource.getPrNumber() != null ? " via PR " + resource.getPrNumber() : "";
            String text = String.format("Hey %s, you created %s (%s)%s. It has been idle for %d days. Should I delete it or schedule a shutdown?",
                    mention,
                    resource.getName(),
                    resource.getType(),
                    prText,
                    resource.getIdleDays());

            String payload = "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("Slack notification failed for {}: {}", resource.getId(), e.getMessage());
        }
    }

    private ZombieResourceResponseDto assembleResponse(List<ZombieResourceDto> resources) {
        double dailyWaste = resources.stream().mapToDouble(ZombieResourceDto::getCostPerDay).sum();
        double avgIdle = resources.isEmpty() ? 0 : resources.stream().mapToInt(ZombieResourceDto::getIdleDays).average().orElse(0);
        long flagged = resources.stream().filter(r -> r.getStatus() == ZombieResourceStatus.FLAGGED).count();
        long scheduled = resources.stream().filter(r -> r.getStatus() == ZombieResourceStatus.SCHEDULED).count();

        List<BreakdownDto> breakdown = resources.stream()
                .collect(Collectors.groupingBy(ZombieResourceDto::getType, Collectors.counting()))
                .entrySet().stream()
                .map(e -> BreakdownDto.builder().type(e.getKey()).count(e.getValue().intValue()).build())
                .sorted(Comparator.comparing(BreakdownDto::getType))
                .toList();

        List<TrendPointDto> trend = buildDefaultTrend();

        return ZombieResourceResponseDto.builder()
                .resources(resources)
                .trend(trend)
                .breakdown(breakdown)
                .summary(ZombieResourceSummaryDto.builder()
                        .dailyWaste(roundTwoDecimals(dailyWaste))
                        .avgIdleDays(roundTwoDecimals(avgIdle))
                        .flagged((int) flagged)
                        .scheduled((int) scheduled)
                        .build())
                .build();
    }

    private List<TrendPointDto> buildDefaultTrend() {
        Instant now = Instant.now();
        List<TrendPointDto> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            Instant day = now.minus(i, ChronoUnit.DAYS);
            trend.add(TrendPointDto.builder()
                    .date(DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(day.truncatedTo(ChronoUnit.DAYS)))
                    .waste(15 - i)
                    .saved(i)
                    .build());
        }
        return trend;
    }

    private ZombieResourceResponseDto fallbackResponse() {
        List<ZombieResourceDto> resources = List.of(
                ZombieResourceDto.builder()
                        .id("eipalloc-123")
                        .name("54.12.34.56")
                        .type("ElasticIP")
                        .region("eu-west-1")
                        .status(ZombieResourceStatus.IDLE)
                        .idleDays(4)
                        .lastActivityDays(4)
                        .costPerDay(0.005)
                        .prNumber("104")
                        .author("John")
                        .build(),
                ZombieResourceDto.builder()
                        .id("snap-456")
                        .name("db-backup-2024-01-02")
                        .type("Snapshot")
                        .region("eu-west-1")
                        .status(ZombieResourceStatus.FLAGGED)
                        .idleDays(21)
                        .lastActivityDays(21)
                        .costPerDay(0.12)
                        .prNumber("104")
                        .author("John")
                        .build(),
                ZombieResourceDto.builder()
                        .id("arn:alb/test")
                        .name("app-alb")
                        .type("LoadBalancer")
                        .region("eu-west-1")
                        .status(ZombieResourceStatus.FLAGGED)
                        .idleDays(4)
                        .lastActivityDays(4)
                        .costPerDay(1.2)
                        .prNumber("104")
                        .author("John")
                        .build(),
                ZombieResourceDto.builder()
                        .id("vol-123")
                        .name("orphaned-data")
                        .type("EBS Volume")
                        .region("eu-west-1")
                        .status(ZombieResourceStatus.FLAGGED)
                        .idleDays(5)
                        .lastActivityDays(5)
                        .costPerDay(0.1)
                        .prNumber("104")
                        .author("John")
                        .build(),
                ZombieResourceDto.builder()
                        .id("arn:aws:secretsmanager:eu-west-1:123:secret:old")
                        .name("stale/secret")
                        .type("Secret")
                        .region("eu-west-1")
                        .status(ZombieResourceStatus.FLAGGED)
                        .idleDays(45)
                        .lastActivityDays(45)
                        .costPerDay(0.02)
                        .prNumber("104")
                        .author("John")
                        .build(),
                ZombieResourceDto.builder()
                        .id("pipeline-legacy")
                        .name("legacy-pipeline")
                        .type("CodePipeline")
                        .region("eu-west-1")
                        .status(ZombieResourceStatus.FLAGGED)
                        .idleDays(12)
                        .lastActivityDays(12)
                        .costPerDay(0.5)
                        .prNumber("104")
                        .author("John")
                        .build()
        );
        return assembleResponse(resources);
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean isPopulated(String value) {
        return value != null && !value.isEmpty();
    }

    private double resolveDailyPrice(String serviceCode, Map<String, String> filters, double fallback) {
        try {
            PricingClient pricing = pricingClient();
            List<software.amazon.awssdk.services.pricing.model.Filter> priceFilters = new ArrayList<>();
            filters.forEach((field, value) -> priceFilters.add(software.amazon.awssdk.services.pricing.model.Filter.builder()
                    .type(FilterType.TERM_MATCH)
                    .field(field)
                    .value(value)
                    .build()));

            GetProductsResponse response = pricing.getProducts(GetProductsRequest.builder()
                    .serviceCode(serviceCode)
                    .filters(priceFilters)
                    .maxResults(1)
                    .build());

            Optional<String> priceJson = response.priceList().stream().findFirst();
            if (priceJson.isEmpty()) {
                return fallback;
            }

            JsonNode root = objectMapper.readTree(priceJson.get());
            Iterator<JsonNode> onDemand = root.path("terms").path("OnDemand").elements();
            if (!onDemand.hasNext()) {
                return fallback;
            }
            JsonNode firstTerm = onDemand.next();
            Iterator<JsonNode> dimensions = firstTerm.path("priceDimensions").elements();
            if (!dimensions.hasNext()) {
                return fallback;
            }
            String usd = dimensions.next().path("pricePerUnit").path("USD").asText();
            if (usd == null || usd.isEmpty()) {
                return fallback;
            }
            double perUnit = Double.parseDouble(usd);
            return roundTwoDecimals(perUnit / 30.0d);
        } catch (Exception e) {
            log.debug("Pricing lookup failed for {}: {}", serviceCode, e.getMessage());
            return fallback;
        }
    }

    private String pricingLocation(Region region) {
        return switch (region.id()) {
            case "us-east-1" -> "US East (N. Virginia)";
            case "us-west-2" -> "US West (Oregon)";
            case "eu-west-1" -> "EU (Ireland)";
            case "eu-central-1" -> "EU (Frankfurt)";
            default -> region.id();
        };
    }

    private PricingClient pricingClient() {
        if (pricingClient == null) {
            pricingClient = PricingClient.builder()
                    .region(Region.US_EAST_1)
                    .build();
        }
        return pricingClient;
    }
}
