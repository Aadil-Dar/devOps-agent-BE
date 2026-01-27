package com.devops.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing secrets in AWS Secrets Manager
 */
@Slf4j
@Service
public class SecretsManagerService {

    @Autowired
    private SecretsManagerClient secretsManagerClient;

    @Value("${aws.secrets-manager.prefix:devops-agent/projects/}")
    private String secretsPrefix;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Store secrets for a project in Secrets Manager
     */
    public void storeSecrets(String projectId, Map<String, String> credentials) {
        String secretName = secretsPrefix + projectId;

        try {
            log.info("Storing secrets for projectId: {}", projectId);

            // Convert credentials to JSON
            String secretValue = objectMapper.writeValueAsString(credentials);

            try {
                // Try to update existing secret
                UpdateSecretRequest updateRequest = UpdateSecretRequest.builder()
                        .secretId(secretName)
                        .secretString(secretValue)
                        .build();

                secretsManagerClient.updateSecret(updateRequest);
                log.info("Updated existing secrets for project: {}", projectId);

            } catch (ResourceNotFoundException e) {
                // Create new secret if it doesn't exist
                CreateSecretRequest createRequest = CreateSecretRequest.builder()
                        .name(secretName)
                        .secretString(secretValue)
                        .tags(
                            Tag.builder().key("ProjectId").value(projectId).build(),
                            Tag.builder().key("ManagedBy").value("devops-agent").build(),
                            Tag.builder().key("CreatedAt").value(String.valueOf(System.currentTimeMillis())).build()
                        )
                        .build();

                secretsManagerClient.createSecret(createRequest);
                log.info("Created new secrets for project: {}", projectId);
            }

        } catch (Exception e) {
            log.error("Failed to store secrets for projectId {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to store secrets", e);
        }
    }

    /**
     * Retrieve secrets for a project (cached for performance)
     */
    @Cacheable(value = "projectSecrets", key = "#projectId")
    public Map<String, String> getSecrets(String projectId) {
        String secretName = secretsPrefix + projectId;

        try {
            log.debug("Fetching secrets for projectId: {}", projectId);

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();

            // Parse JSON to Map
            Map<String, String> secrets = objectMapper.readValue(
                    secretString,
                    new TypeReference<Map<String, String>>() {}
            );

            log.debug("Successfully retrieved secrets for project: {}", projectId);
            return secrets;

        } catch (ResourceNotFoundException e) {
            log.error("Secrets not found for projectId: {}", projectId);
            throw new RuntimeException("Secrets not found for project: " + projectId, e);
        } catch (Exception e) {
            log.error("Failed to retrieve secrets for projectId {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve secrets", e);
        }
    }

    /**
     * Delete secrets for a project
     */
    public void deleteSecrets(String projectId) {
        String secretName = secretsPrefix + projectId;

        try {
            log.info("Deleting secrets for projectId: {}", projectId);

            DeleteSecretRequest request = DeleteSecretRequest.builder()
                    .secretId(secretName)
                    .forceDeleteWithoutRecovery(false) // Allow recovery within 30 days
                    .build();

            secretsManagerClient.deleteSecret(request);
            log.info("Successfully deleted secrets for project: {}", projectId);

        } catch (ResourceNotFoundException e) {
            log.warn("Secrets not found for projectId: {} (may already be deleted)", projectId);
        } catch (Exception e) {
            log.error("Failed to delete secrets for projectId {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete secrets", e);
        }
    }

    /**
     * Check if secrets exist for a project
     */
    public boolean secretsExist(String projectId) {
        String secretName = secretsPrefix + projectId;

        try {
            DescribeSecretRequest request = DescribeSecretRequest.builder()
                    .secretId(secretName)
                    .build();

            secretsManagerClient.describeSecret(request);
            return true;

        } catch (ResourceNotFoundException e) {
            return false;
        }
    }
}

