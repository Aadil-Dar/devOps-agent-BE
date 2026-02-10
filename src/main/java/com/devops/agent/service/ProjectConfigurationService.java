package com.devops.agent.service;

import com.devops.agent.model.ProjectConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing project configurations in DynamoDB
 */
@Slf4j
@Service
public class ProjectConfigurationService {

    @Autowired
    private DynamoDbTable<ProjectConfiguration> projectTable;

    /**
     * Save project configuration to DynamoDB
     */
    public void saveConfiguration(ProjectConfiguration config) {
        try {
            log.info("Saving project configuration for projectId: {}", config.getProjectId());
            projectTable.putItem(config);
            log.info("Successfully saved project configuration: {}", config.getProjectId());
        } catch (DynamoDbException e) {
            log.error("Failed to save project configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save project configuration", e);
        }
    }

    /**
     * Get project configuration by ID
     */
    public Optional<ProjectConfiguration> getConfiguration(String projectId) {
        try {
            log.debug("Fetching project configuration for projectId: {}", projectId);
            Key key = Key.builder()
                    .partitionValue(projectId)
                    .build();

            ProjectConfiguration config = projectTable.getItem(key);
            return Optional.ofNullable(config);
        } catch (DynamoDbException e) {
            log.error("Failed to fetch project configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch project configuration", e);
        }
    }

    /**
     * Update project configuration
     */
    public ProjectConfiguration updateConfiguration(String projectId, ProjectConfiguration updatedConfig) {
        try {
            log.info("Updating project configuration for projectId: {}", projectId);

            // Ensure projectId matches
            updatedConfig.setProjectId(projectId);
            updatedConfig.setUpdatedAt(System.currentTimeMillis());

            projectTable.updateItem(updatedConfig);
            log.info("Successfully updated project configuration: {}", projectId);
            return updatedConfig;
        } catch (DynamoDbException e) {
            log.error("Failed to update project configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update project configuration", e);
        }
    }

    /**
     * Delete project configuration
     */
    public void deleteConfiguration(String projectId) {
        try {
            log.info("Deleting project configuration for projectId: {}", projectId);
            Key key = Key.builder()
                    .partitionValue(projectId)
                    .build();

            projectTable.deleteItem(key);
            log.info("Successfully deleted project configuration: {}", projectId);
        } catch (DynamoDbException e) {
            log.error("Failed to delete project configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete project configuration", e);
        }
    }

    /**
     * List all project configurations
     */
    public List<ProjectConfiguration> listAllConfigurations() {
        try {
            log.debug("Fetching all project configurations");
            List<ProjectConfiguration> configs = new ArrayList<>();
            projectTable.scan().items().forEach(configs::add);
            log.info("Found {} project configurations", configs.size());
            return configs;
        } catch (DynamoDbException e) {
            log.error("Failed to list project configurations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to list project configurations", e);
        }
    }

    /**
     * Check if project exists
     */
    public boolean projectExists(String projectId) {
        return getConfiguration(projectId).isPresent();
    }
}

