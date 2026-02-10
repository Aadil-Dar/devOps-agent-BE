package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for Ollama embedding API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaEmbedResponse {
    private List<Double> embedding;
}
