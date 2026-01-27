package com.devops.agent.controller;

import com.devops.agent.model.ZombieResourceDtos.ZombieResourceResponseDto;
import com.devops.agent.service.ZombieResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zombies")
@RequiredArgsConstructor
@Slf4j
public class ZombieResourceController {

    private final ZombieResourceService zombieResourceService;

    @GetMapping
    public ResponseEntity<ZombieResourceResponseDto> findZombies(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "false") boolean notify) {
        log.info("Scanning for idle AWS resources. projectId={}, notify={}", projectId, notify);
        try {
            ZombieResourceResponseDto response = zombieResourceService.findZombieResources(projectId, notify);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Zombie resource scan failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
