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
public class PullRequestResponse {
    // Existing fields
    private int number;
    private String title;
    private String state;
    private String status;
    private String author;
    private String url;
    private String createdAt; // humanized, e.g., "2 hours ago"
    private String updatedAt; // humanized
    private String branch;

    // Renamed field for clarity in frontend contract
    private String targetBranch; // previously baseBranch

    // New fields per frontend requirements
    private String id; // e.g., PR-124
    private String avatar; // author avatar url

    private String pipeline; // e.g., Failed
    private String pipelineType; // e.g., Mock Auth

    private String aiSuggestion;
    private String aiInsight;

    private int commits;
    private int additions;
    private int deletions;
    private int filesChanged;

    private List<String> reviewers;
    private List<String> labels;

    private String priority; // derived from labels or set as default

    /**
     * @deprecated Use {@link #targetBranch} instead. Will be removed in a future release.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    private String baseBranch;
}
