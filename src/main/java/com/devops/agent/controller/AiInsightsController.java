package com.devops.agent.controller;


import com.devops.agent.model.AiInsightResponse;
import com.devops.agent.model.VulnerabilityDto;
import com.devops.agent.service.AiInsightsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-insights")
public class AiInsightsController {

    private final AiInsightsService aiInsightsService;

    public AiInsightsController(AiInsightsService aiInsightsService) {
        this.aiInsightsService = aiInsightsService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AiInsightResponse> analyze(@RequestBody VulnerabilityDto dto) {
        AiInsightResponse response = aiInsightsService.analyzeVulnerability(dto);
        return ResponseEntity.ok(response);
    }
}