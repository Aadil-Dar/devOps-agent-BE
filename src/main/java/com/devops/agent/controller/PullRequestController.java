package com.devops.agent.controller;

import com.devops.agent.model.PullRequestResponse;
import com.devops.agent.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pull-requests")
@RequiredArgsConstructor
@Slf4j
public class PullRequestController {

    private final GitHubService gitHubService;

    /**
     * Get all open pull requests for a specific project
     * @param projectId Optional project ID to fetch PRs for a specific project (uses default if not provided)
     */
    @GetMapping
    public ResponseEntity<List<PullRequestResponse>> getOpenPullRequests(
            @RequestParam(required = false) String projectId) {
        log.info("GET /api/pull-requests - Fetching open pull requests for projectId: {}",
                projectId != null ? projectId : "default");
        try {
            List<PullRequestResponse> pullRequests = gitHubService.getOpenPullRequests(projectId);
            return ResponseEntity.ok(pullRequests);
        } catch (Exception e) {
            log.error("Error fetching pull requests for projectId {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get a specific pull request by number for a specific project
     * @param number The pull request number
     * @param projectId Optional project ID (uses default if not provided)
     */
    @GetMapping("/{number}")
    public ResponseEntity<PullRequestResponse> getPullRequest(
            @PathVariable int number,
            @RequestParam(required = false) String projectId) {
        log.info("GET /api/pull-requests/{} - Fetching pull request for projectId: {}",
                number, projectId != null ? projectId : "default");
        try {
            PullRequestResponse pullRequest = gitHubService.getPullRequest(projectId, number);
            return ResponseEntity.ok(pullRequest);
        } catch (RuntimeException e) {
            log.error("Error fetching pull request #{} for projectId {}: {}", number, projectId, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }
}
