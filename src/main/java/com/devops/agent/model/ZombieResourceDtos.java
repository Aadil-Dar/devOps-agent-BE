package com.devops.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTOs for idle/"zombie" AWS resource analysis.
 */
public final class ZombieResourceDtos {

    private ZombieResourceDtos() {
        // utility holder
    }

    public enum ZombieResourceStatus {
        IDLE("idle"),
        SCHEDULED("scheduled"),
        FLAGGED("flagged");

        private final String value;

        ZombieResourceStatus(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ZombieResourceDto {
        private String id;
        private String name;
        private String type;
        private String region;
        private ZombieResourceStatus status;
        private int idleDays;
        private double costPerDay;
        private int lastActivityDays;
        private String prNumber;
        private String author;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPointDto {
        private String date;
        private double waste;
        private double saved;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakdownDto {
        private String type;
        private int count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZombieResourceSummaryDto {
        private double dailyWaste;
        private double avgIdleDays;
        private int flagged;
        private int scheduled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZombieResourceResponseDto {
        private List<ZombieResourceDto> resources;
        private List<TrendPointDto> trend;
        private List<BreakdownDto> breakdown;
        private ZombieResourceSummaryDto summary;
    }

    /** UI-friendly item */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZombieResourceItem {
        private String id;
        private String type;
        private String name;
        private int age;
        private double cost;
        private String prNumber;
        private String author;
        private String lastActivity;
        private ZombieResourceStatus status;
        private String region;
    }
}
