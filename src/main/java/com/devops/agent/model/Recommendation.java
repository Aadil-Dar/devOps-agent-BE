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
public class Recommendation {
    private String title;
    private String priority;
    private String impact;
    private String effort;
    private String estimatedTime;
    private String category;
    private List<String> steps;
    private String roi;
}
