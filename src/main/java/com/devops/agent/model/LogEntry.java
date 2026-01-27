package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private String id;              // CloudWatch eventId or UUID
    private String timestamp;       // ISO string
    private String severity;        // ERROR, WARN, INFO, DEBUG
    private String service;         // e.g., 'order-service'
    private String host;            // e.g., 'ip-10-0-1-123'
    private String message;
    private String requestId;       // Trace ID
    private Map<String, Object> parsed;  // e.g., { userId: 123, errorCode: 'DB_TIMEOUT' }
}

