package com.devops.agent.service;

import com.devops.agent.model.AiInsightResponse;
import com.devops.agent.model.OllamaGenerateRequest;
import com.devops.agent.model.OllamaGenerateResponse;
import com.devops.agent.model.VulnerabilityDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AiInsightsService {
    private static final Logger log = LoggerFactory.getLogger(AiInsightsService.class);

    private final WebClient ollamaWebClient;
    private final ObjectMapper objectMapper;

    public AiInsightResponse analyzeVulnerability(VulnerabilityDto dto) {
        try {
            String vulnerabilityJson = objectMapper.writeValueAsString(dto);
            String prompt = buildPrompt(vulnerabilityJson);

            OllamaGenerateRequest request =
                    new OllamaGenerateRequest("qwen2.5-coder:7b", prompt, false);

            long start = System.currentTimeMillis();

            OllamaGenerateResponse ollamaResponse = ollamaWebClient
                    .post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .timeout(Duration.ofSeconds(60))
                    .doOnError(ex ->
                            log.error("Error calling Ollama for vulnerability {}", dto.getId(), ex))
                    .block();

            long end = System.currentTimeMillis();
            log.info("Ollama call took {} ms", (end - start));

            if (ollamaResponse == null || ollamaResponse.getResponse() == null) {
                throw new RuntimeException("Ollama returned empty response");
            }

            String aiJson = ollamaResponse.getResponse().trim();
            log.debug("Raw AI JSON response: {}", aiJson);

            // Parse AI response to extract only the 6 analysis fields
            AiAnalysisFields aiFields = objectMapper.readValue(aiJson, AiAnalysisFields.class);

            // Build final response: copy input fields + add AI analysis fields
            return AiInsightResponse.builder()
                    .cveId(dto.getCveId())
                    .title(dto.getTitle())
                    .severity(dto.getSeverity())
                    .cvssScore(dto.getCvssScore())
                    .cwe(dto.getCwe())
                    .packageName(dto.getPackageName())
                    .currentVersion(dto.getCurrentVersion())
                    .fixedVersion(dto.getFixedVersion())
                    .affectedProjects(dto.getAffectedProjects())
                    .description(dto.getDescription())
                    .aiRemediationAnalysis(aiFields.aiRemediationAnalysis())
                    .estimatedTime(aiFields.estimatedTime())
                    .riskLevel(aiFields.riskLevel())
                    .automationAvailable(aiFields.automationAvailable())
                    .recommendedRemediationSteps(aiFields.recommendedRemediationSteps())
                    .mavenDependencyUpdateSnippet(aiFields.mavenDependencyUpdateSnippet())
                    .references(dto.getReferences())
                    .build();

        } catch (Exception e) {
            log.error("Failed to analyze vulnerability via AI", e);
            throw new RuntimeException("AI analysis failed", e);
        }
    }

    // Inner record to parse only AI-generated fields from Ollama response
    private record AiAnalysisFields(
            String aiRemediationAnalysis,
            String estimatedTime,
            String riskLevel,
            Boolean automationAvailable,
            java.util.List<String> recommendedRemediationSteps,
            String mavenDependencyUpdateSnippet
    ) {}

    private String buildPrompt(String vulnerabilityJson) {
        return """
            You are a security remediation assistant.

            You will receive a JSON object with vulnerability details from a dependency scan.
            Analyze the vulnerability and respond with ONLY a JSON object containing these 6 fields:

            {
              "aiRemediationAnalysis": "A concise summary of the vulnerability and recommended fix (2-3 sentences)",
              "estimatedTime": "Estimated time to remediate (e.g., '2-4 hours', '1-2 days')",
              "riskLevel": "Risk assessment - one of: 'Critical', 'High', 'Medium', 'Low'",
              "automationAvailable": true or false (true if fix can be automated, usually true for dependency updates),
              "recommendedRemediationSteps": ["STEP 1", "STEP 2", "STEP 3"],
              "mavenDependencyUpdateSnippet": "Maven <dependency> XML snippet with fixed version (use \\n for line breaks)"
            }

            CRITICAL RULES:
            1. Respond with VALID JSON ONLY. No markdown code blocks, no extra text, no explanations.
            2. Generate ONLY the 6 fields listed above - nothing else.
            3. For recommendedRemediationSteps, provide EXACTLY 3 steps, each actionable and specific.
            4. For mavenDependencyUpdateSnippet, create a proper Maven <dependency> block using the fixedVersion from input, with \\n for newlines.
            5. Base estimatedTime on complexity: simple version bumps = "2-4 hours", complex changes = "1-2 days".
            6. Set riskLevel based on input severity: CRITICAL->"Critical", HIGH->"High", MEDIUM->"Medium", LOW->"Low".
            7. Set automationAvailable to true for simple dependency updates, false for complex fixes requiring code changes.

            Here is the vulnerability JSON to analyze:

            """ + vulnerabilityJson + """
            """;
    }
}
