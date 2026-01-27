package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterLogEntry {
    private String id;                          // e.g., "c1", "c2"
    private String title;                       // e.g., "NullPointerException in OrderProcessor"
    private int count;                          // Number of occurrences
    private String firstSeen;                   // ISO timestamp of first occurrence
    private String lastSeen;                    // ISO timestamp of last occurrence
    private List<String> affectedHosts;         // List of affected hosts
    private List<String> affectedServices;      // List of affected services
    private List<SampleLog> sampleLogs;         // Sample log entries
    private String severity;                    // ERROR, WARN, INFO, DEBUG
}

