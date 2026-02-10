package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for Ollama embedding API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaEmbedRequest {
    private String model;
    private String prompt;
}
