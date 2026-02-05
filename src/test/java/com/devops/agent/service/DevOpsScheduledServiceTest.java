package com.devops.agent.service;

import com.devops.agent.model.ProjectConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevOpsScheduledServiceTest {

    @Mock
    private DevOpsInsightService devOpsInsightService;

    @Mock
    private ProjectConfigurationService projectConfigurationService;

    private DevOpsScheduledService scheduledService;

    @BeforeEach
    void setUp() {
        scheduledService = new DevOpsScheduledService(devOpsInsightService, projectConfigurationService);
    }

    @Test
    void performScheduledHealthChecks_shouldProcessAllEnabledProjects() {
        // Given
        ProjectConfiguration enabledProject1 = ProjectConfiguration.builder()
                .projectId("project1")
                .enabled(true)
                .logGroupNames(List.of("/aws/ecs/prod/service1"))
                .build();

        ProjectConfiguration enabledProject2 = ProjectConfiguration.builder()
                .projectId("project2")
                .enabled(true)
                .logGroupNames(List.of("/aws/ecs/prod/service2"))
                .build();

        ProjectConfiguration disabledProject = ProjectConfiguration.builder()
                .projectId("project3")
                .enabled(false)
                .logGroupNames(List.of("/aws/ecs/prod/service3"))
                .build();

        ProjectConfiguration noLogsProject = ProjectConfiguration.builder()
                .projectId("project4")
                .enabled(true)
                .build();

        when(projectConfigurationService.listAllConfigurations())
                .thenReturn(List.of(enabledProject1, enabledProject2, disabledProject, noLogsProject));

        // When
        scheduledService.performScheduledHealthChecks();

        // Then
        verify(devOpsInsightService, times(1)).performHealthCheck("project1");
        verify(devOpsInsightService, times(1)).performHealthCheck("project2");
        verify(devOpsInsightService, never()).performHealthCheck("project3");
        verify(devOpsInsightService, never()).performHealthCheck("project4");
    }

    @Test
    void performScheduledHealthChecks_whenOneProjectFails_shouldContinueWithOthers() {
        // Given
        ProjectConfiguration project1 = ProjectConfiguration.builder()
                .projectId("project1")
                .enabled(true)
                .logGroupNames(List.of("/aws/ecs/prod/service1"))
                .build();

        ProjectConfiguration project2 = ProjectConfiguration.builder()
                .projectId("project2")
                .enabled(true)
                .logGroupNames(List.of("/aws/ecs/prod/service2"))
                .build();

        when(projectConfigurationService.listAllConfigurations())
                .thenReturn(List.of(project1, project2));

        when(devOpsInsightService.performHealthCheck("project1"))
                .thenThrow(new RuntimeException("Failed to check project1"));

        // When
        scheduledService.performScheduledHealthChecks();

        // Then
        verify(devOpsInsightService, times(1)).performHealthCheck("project1");
        verify(devOpsInsightService, times(1)).performHealthCheck("project2");
    }

    @Test
    void performScheduledHealthChecks_whenNoEnabledProjects_shouldNotCallService() {
        // Given
        when(projectConfigurationService.listAllConfigurations())
                .thenReturn(List.of());

        // When
        scheduledService.performScheduledHealthChecks();

        // Then
        verify(devOpsInsightService, never()).performHealthCheck(anyString());
    }
}
