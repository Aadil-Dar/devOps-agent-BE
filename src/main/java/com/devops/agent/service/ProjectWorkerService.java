package com.devops.agent.service;

import com.devops.agent.model.FullProjectConfig;
import com.devops.agent.model.ProjectConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Worker service that combines configuration and secrets for DevOps operations
 * This is the main service used by controllers to get complete project configurations
 */
@Slf4j
@Service
public class ProjectWorkerService {

    @Autowired
    private ProjectConfigurationService configService;

    @Autowired
    private SecretsManagerService secretsService;

    /**
     * Get complete project configuration (config + secrets)
     * Cached for performance
     */
    @Cacheable(value = "projectConfigs", key = "#projectId")
    public FullProjectConfig getFullProjectConfig(String projectId) {
        log.info("Fetching full configuration for projectId: {}", projectId);

        // 1. Fetch configuration from DynamoDB
        ProjectConfiguration config = configService.getConfiguration(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        // 2. Check if project is enabled
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            log.warn("Project {} is disabled", projectId);
            throw new RuntimeException("Project is disabled: " + projectId);
        }

        // 3. Fetch secrets from Secrets Manager
        Map<String, String> secrets = secretsService.getSecrets(projectId);

        // 4. Combine into FullProjectConfig
        FullProjectConfig fullConfig = FullProjectConfig.builder()
                .projectId(config.getProjectId())
                .projectName(config.getProjectName())
                .githubOwner(config.getGithubOwner())
                .githubRepo(config.getGithubRepo())
                .awsRegion(config.getAwsRegion())
                .githubToken(secrets.get("github-token"))
                .awsAccessKey(secrets.get("aws-access-key"))
                .awsSecretKey(secrets.get("aws-secret-key"))
                .enabled(config.getEnabled())
                .createdAt(config.getCreatedAt())
                .build();

        log.info("Successfully retrieved full configuration for project: {}", projectId);
        return fullConfig;
    }

    /**
     * Evict cache when configuration is updated
     */
    @CacheEvict(value = {"projectConfigs", "projectSecrets"}, key = "#projectId")
    public void evictCache(String projectId) {
        log.info("Evicted cache for projectId: {}", projectId);
    }

    /**
     * Validate project configuration
     */
    public boolean validateProjectConfig(String projectId) {
        try {
            getFullProjectConfig(projectId);
            return true;
        } catch (Exception e) {
            log.error("Project configuration validation failed for {}: {}", projectId, e.getMessage());
            return false;
        }
    }

    /**
     * Get GitHub credentials for a project
     */
    public GitHubCredentials getGitHubCredentials(String projectId) {
        FullProjectConfig config = getFullProjectConfig(projectId);
        return new GitHubCredentials(
                config.getGithubOwner(),
                config.getGithubRepo(),
                config.getGithubToken()
        );
    }

    /**
     * Get AWS credentials for a project
     */
    public AwsCredentials getAwsCredentials(String projectId) {
        FullProjectConfig config = getFullProjectConfig(projectId);
        return new AwsCredentials(
                config.getAwsRegion(),
                config.getAwsAccessKey(),
                config.getAwsSecretKey()
        );
    }

    // Inner classes for credentials
    public static class GitHubCredentials {
        public final String owner;
        public final String repo;
        public final String token;

        public GitHubCredentials(String owner, String repo, String token) {
            this.owner = owner;
            this.repo = repo;
            this.token = token;
        }
    }

    public static class AwsCredentials {
        public final String region;
        public final String accessKey;
        public final String secretKey;

        public AwsCredentials(String region, String accessKey, String secretKey) {
            this.region = region;
            this.accessKey = accessKey;
            this.secretKey = secretKey;
        }
    }
}

