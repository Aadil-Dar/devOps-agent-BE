package com.devops.agent.controller;

import com.devops.agent.model.*;
import com.devops.agent.service.ProjectConfigurationService;
import com.devops.agent.service.ProjectWorkerService;
import com.devops.agent.service.SecretsManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin Controller for managing project configurations
 * Endpoints for CRUD operations on projects
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/projects")
@CrossOrigin(origins = "*")
public class AdminProjectController {

    @Autowired
    private ProjectConfigurationService configService;

    @Autowired
    private SecretsManagerService secretsService;

    @Autowired
    private ProjectWorkerService workerService;

    /**
     * Upload/Create new project configuration
     * POST /api/admin/projects/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<ProjectResponse> uploadProject(@RequestBody ProjectUploadRequest request) {
        try {
            log.info("Received project upload request for: {}", request.getProjectName());

            // Validate request
            validateUploadRequest(request);

            // Generate unique project ID
            String projectId = UUID.randomUUID().toString();

            // 1. Store non-sensitive configuration in DynamoDB
            ProjectConfiguration config = ProjectConfiguration.builder()
                    .projectId(projectId)
                    .projectName(request.getProjectName())
                    .githubOwner(request.getGithubOwner())
                    .githubRepo(request.getGithubRepo())
                    .awsRegion(request.getAwsRegion() != null ? request.getAwsRegion() : "eu-west-1")
                    .secretsPath("devops-agent/projects/" + projectId)
                    .enabled(true)
                    .createdAt(System.currentTimeMillis())
                    .updatedAt(System.currentTimeMillis())
                    .createdBy(request.getCreatedBy() != null ? request.getCreatedBy() : "admin")
                    .metadata(new HashMap<>())
                    .build();

            configService.saveConfiguration(config);

            // 2. Store sensitive credentials in Secrets Manager
            Map<String, String> secrets = new HashMap<>();
            secrets.put("github-token", request.getGithubToken());

            if (request.getAwsAccessKey() != null) {
                secrets.put("aws-access-key", request.getAwsAccessKey());
            }
            if (request.getAwsSecretKey() != null) {
                secrets.put("aws-secret-key", request.getAwsSecretKey());
            }

            secretsService.storeSecrets(projectId, secrets);

            // 3. Build response
            ProjectResponse response = ProjectResponse.builder()
                    .projectId(projectId)
                    .projectName(config.getProjectName())
                    .githubOwner(config.getGithubOwner())
                    .githubRepo(config.getGithubRepo())
                    .awsRegion(config.getAwsRegion())
                    .enabled(config.getEnabled())
                    .createdAt(config.getCreatedAt())
                    .updatedAt(config.getUpdatedAt())
                    .createdBy(config.getCreatedBy())
                    .message("Project created successfully")
                    .build();

            log.info("Successfully created project: {}", projectId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to upload project: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProjectResponse.builder()
                            .message("Failed to create project: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get project configuration by ID
     * GET /api/admin/projects/{projectId}
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable String projectId) {
        try {
            log.info("Fetching project: {}", projectId);

            ProjectConfiguration config = configService.getConfiguration(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            ProjectResponse response = mapToResponse(config, "Project retrieved successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ProjectResponse.builder()
                            .message("Project not found: " + projectId)
                            .build());
        }
    }

    /**
     * List all projects
     * GET /api/admin/projects
     */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects() {
        try {
            log.info("Listing all projects");

            List<ProjectConfiguration> configs = configService.listAllConfigurations();
            List<ProjectResponse> responses = configs.stream()
                    .map(config -> mapToResponse(config, null))
                    .collect(Collectors.toList());

            log.info("Found {} projects", responses.size());
            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Failed to list projects: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    /**
     * Update project configuration
     * PUT /api/admin/projects/{projectId}
     */
    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable String projectId,
            @RequestBody ProjectUploadRequest request) {
        try {
            log.info("Updating project: {}", projectId);

            // Check if project exists
            ProjectConfiguration existingConfig = configService.getConfiguration(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            // Update configuration
            ProjectConfiguration updatedConfig = ProjectConfiguration.builder()
                    .projectId(projectId)
                    .projectName(request.getProjectName())
                    .githubOwner(request.getGithubOwner())
                    .githubRepo(request.getGithubRepo())
                    .awsRegion(request.getAwsRegion() != null ? request.getAwsRegion() : existingConfig.getAwsRegion())
                    .secretsPath(existingConfig.getSecretsPath())
                    .enabled(existingConfig.getEnabled())
                    .createdAt(existingConfig.getCreatedAt())
                    .updatedAt(System.currentTimeMillis())
                    .createdBy(existingConfig.getCreatedBy())
                    .metadata(existingConfig.getMetadata())
                    .build();

            configService.updateConfiguration(projectId, updatedConfig);

            // Update secrets if provided
            if (request.getGithubToken() != null || request.getAwsAccessKey() != null) {
                Map<String, String> secrets = new HashMap<>();

                if (request.getGithubToken() != null) {
                    secrets.put("github-token", request.getGithubToken());
                }
                if (request.getAwsAccessKey() != null) {
                    secrets.put("aws-access-key", request.getAwsAccessKey());
                }
                if (request.getAwsSecretKey() != null) {
                    secrets.put("aws-secret-key", request.getAwsSecretKey());
                }

                if (!secrets.isEmpty()) {
                    secretsService.storeSecrets(projectId, secrets);
                }
            }

            // Evict cache
            workerService.evictCache(projectId);

            ProjectResponse response = mapToResponse(updatedConfig, "Project updated successfully");

            log.info("Successfully updated project: {}", projectId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProjectResponse.builder()
                            .message("Failed to update project: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Enable/Disable project
     * PATCH /api/admin/projects/{projectId}/toggle
     */
    @PatchMapping("/{projectId}/toggle")
    public ResponseEntity<ProjectResponse> toggleProject(@PathVariable String projectId) {
        try {
            log.info("Toggling project status: {}", projectId);

            ProjectConfiguration config = configService.getConfiguration(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            config.setEnabled(!Boolean.TRUE.equals(config.getEnabled()));
            config.setUpdatedAt(System.currentTimeMillis());

            configService.updateConfiguration(projectId, config);
            workerService.evictCache(projectId);

            String status = Boolean.TRUE.equals(config.getEnabled()) ? "enabled" : "disabled";
            ProjectResponse response = mapToResponse(config, "Project " + status + " successfully");

            log.info("Successfully toggled project {} to {}", projectId, status);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to toggle project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProjectResponse.builder()
                            .message("Failed to toggle project: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Delete project
     * DELETE /api/admin/projects/{projectId}
     */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Map<String, String>> deleteProject(@PathVariable String projectId) {
        try {
            log.info("Deleting project: {}", projectId);

            // Check if project exists
            if (!configService.projectExists(projectId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Project not found: " + projectId));
            }

            // Delete configuration
            configService.deleteConfiguration(projectId);

            // Delete secrets
            secretsService.deleteSecrets(projectId);

            // Evict cache
            workerService.evictCache(projectId);

            log.info("Successfully deleted project: {}", projectId);
            return ResponseEntity.ok(Map.of(
                    "message", "Project deleted successfully",
                    "projectId", projectId
            ));

        } catch (Exception e) {
            log.error("Failed to delete project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to delete project: " + e.getMessage()));
        }
    }

    /**
     * Validate project configuration
     * POST /api/admin/projects/{projectId}/validate
     */
    @PostMapping("/{projectId}/validate")
    public ResponseEntity<Map<String, Object>> validateProject(@PathVariable String projectId) {
        try {
            log.info("Validating project: {}", projectId);

            boolean isValid = workerService.validateProjectConfig(projectId);

            return ResponseEntity.ok(Map.of(
                    "projectId", projectId,
                    "valid", isValid,
                    "message", isValid ? "Project configuration is valid" : "Project configuration is invalid"
            ));

        } catch (Exception e) {
            log.error("Validation failed for project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "projectId", projectId,
                    "valid", false,
                    "message", "Validation failed: " + e.getMessage()
            ));
        }
    }

    // Helper methods

    private void validateUploadRequest(ProjectUploadRequest request) {
        if (request.getProjectName() == null || request.getProjectName().isEmpty()) {
            throw new IllegalArgumentException("Project name is required");
        }
        if (request.getGithubOwner() == null || request.getGithubOwner().isEmpty()) {
            throw new IllegalArgumentException("GitHub owner is required");
        }
        if (request.getGithubRepo() == null || request.getGithubRepo().isEmpty()) {
            throw new IllegalArgumentException("GitHub repository is required");
        }
        if (request.getGithubToken() == null || request.getGithubToken().isEmpty()) {
            throw new IllegalArgumentException("GitHub token is required");
        }
    }

    private ProjectResponse mapToResponse(ProjectConfiguration config, String message) {
        return ProjectResponse.builder()
                .projectId(config.getProjectId())
                .projectName(config.getProjectName())
                .githubOwner(config.getGithubOwner())
                .githubRepo(config.getGithubRepo())
                .awsRegion(config.getAwsRegion())
                .enabled(config.getEnabled())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .createdBy(config.getCreatedBy())
                .message(message)
                .build();
    }
}

