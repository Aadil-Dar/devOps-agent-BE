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
     * Get all open pull requests
     */
    @GetMapping
    public ResponseEntity<List<PullRequestResponse>> getOpenPullRequests() {
        log.info("GET /api/pull-requests - Fetching open pull requests");
        try {
            List<PullRequestResponse> pullRequests = gitHubService.getOpenPullRequests();
            return ResponseEntity.ok(pullRequests);
        } catch (Exception e) {
            log.error("Error fetching pull requests", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get a specific pull request by number
     */
    @GetMapping("/{number}")
    public ResponseEntity<PullRequestResponse> getPullRequest(@PathVariable int number) {
        log.info("GET /api/pull-requests/{} - Fetching pull request", number);
        try {
            PullRequestResponse pullRequest = gitHubService.getPullRequest(number);
            return ResponseEntity.ok(pullRequest);
        } catch (RuntimeException e) {
            log.error("Error fetching pull request #{}", number, e);
            return ResponseEntity.notFound().build();
        }
    }
}
