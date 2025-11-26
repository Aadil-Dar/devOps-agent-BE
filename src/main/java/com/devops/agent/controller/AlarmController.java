package com.devops.agent.controller;

import com.devops.agent.model.AlarmResponse;
import com.devops.agent.service.AlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
@Slf4j
public class AlarmController {

    private final AlarmService alarmService;

    /**
     * Get all CloudWatch alarms
     */
    @GetMapping
    public ResponseEntity<List<AlarmResponse>> getAllAlarms() {
        log.info("GET /api/alarms - Fetching all alarms");
        try {
            List<AlarmResponse> alarms = alarmService.getAllAlarms();
            return ResponseEntity.ok(alarms);
        } catch (Exception e) {
            log.error("Error fetching alarms", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get alarms by state
     */
    @GetMapping("/state/{state}")
    public ResponseEntity<List<AlarmResponse>> getAlarmsByState(
            @PathVariable String state) {
        log.info("GET /api/alarms/state/{} - Fetching alarms by state", state);
        try {
            List<AlarmResponse> alarms = alarmService.getAlarmsByState(state);
            return ResponseEntity.ok(alarms);
        } catch (RuntimeException e) {
            log.error("Error fetching alarms by state", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get alarm by name
     */
    @GetMapping("/{alarmName}")
    public ResponseEntity<AlarmResponse> getAlarmByName(
            @PathVariable String alarmName) {
        log.info("GET /api/alarms/{} - Fetching alarm details", alarmName);
        try {
            AlarmResponse alarm = alarmService.getAlarmByName(alarmName);
            return ResponseEntity.ok(alarm);
        } catch (RuntimeException e) {
            log.error("Error fetching alarm", e);
            return ResponseEntity.notFound().build();
        }
    }
}
