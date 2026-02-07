package com.devops.agent.model;

import lombok.Value;

/**
 * Request DTO for DevOps health check API
 */
@Value
public class DevOpsHealthCheckRequest {
    String projectId;
}
