package com.devops.agent.service;

import com.devops.agent.model.ProjectConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled service for automated health checks and failure predictions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DevOpsScheduledService {

    private final DevOpsInsightService devOpsInsightService;
    private final ProjectConfigurationService projectConfigurationService;

    /**
     * Run health checks for all enabled projects every 10 minutes
     */
//    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    public void performScheduledHealthChecks() {
        log.info("Starting scheduled health checks for all enabled projects");

        try {
            List<ProjectConfiguration> allProjects = projectConfigurationService.listAllConfigurations();
            List<ProjectConfiguration> enabledProjects = allProjects.stream()
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .filter(p -> p.getLogGroupNames() != null && !p.getLogGroupNames().isEmpty())
                    .toList();

            log.info("Found {} enabled projects with log groups configured", enabledProjects.size());

            for (ProjectConfiguration project : enabledProjects) {
                try {
                    log.info("Running scheduled health check for project: {}", project.getProjectId());
                    devOpsInsightService.performHealthCheck(project.getProjectId());
                    log.info("Successfully completed scheduled health check for project: {}", project.getProjectId());
                } catch (Exception e) {
                    log.error("Failed to perform scheduled health check for project: {}",
                            project.getProjectId(), e);
                    // Continue with next project even if one fails
                }
            }

            log.info("Completed scheduled health checks for all projects");
        } catch (Exception e) {
            log.error("Error during scheduled health checks execution", e);
        }
    }
}
