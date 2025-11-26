package com.devops.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestResponse {
    private int number;
    private String title;
    private String state;
    private String status;
    private String author;
    private String url;
    private String createdAt;
    private String updatedAt;
    private String branch;
    private String baseBranch;
}
