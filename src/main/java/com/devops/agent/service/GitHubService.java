package com.devops.agent.service;

import com.devops.agent.model.PullRequestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubService {

    private final GitHub gitHubClient;

    @Value("${github.repository.owner:sdc-pune}")
    private String repositoryOwner;

    @Value("${github.repository.name:devOps-agent}")
    private String repositoryName;

    /**
     * Get all open pull requests from the repository
     */
    public List<PullRequestResponse> getOpenPullRequests() {
        log.info("Fetching open pull requests for {}/{}", repositoryOwner, repositoryName);
        try {
            GHRepository repository = gitHubClient.getRepository(repositoryOwner + "/" + repositoryName);
            List<GHPullRequest> pullRequests = repository.getPullRequests(GHIssueState.OPEN);

            return pullRequests.stream()
                    .map(this::mapToPullRequestResponse)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error fetching pull requests: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch pull requests", e);
        }
    }

    /**
     * Get a specific pull request by number
     */
    public PullRequestResponse getPullRequest(int number) {
        log.info("Fetching pull request #{} for {}/{}", number, repositoryOwner, repositoryName);
        try {
            GHRepository repository = gitHubClient.getRepository(repositoryOwner + "/" + repositoryName);
            GHPullRequest pullRequest = repository.getPullRequest(number);
            return mapToPullRequestResponse(pullRequest);
        } catch (IOException e) {
            log.error("Error fetching pull request #{}: {}", number, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch pull request #" + number, e);
        }
    }

    /**
     * Map GitHub pull request to response DTO
     */
    private PullRequestResponse mapToPullRequestResponse(GHPullRequest pr) {
        try {
            // Get the status check from commit status
            String status = getCommitStatus(pr);

            return PullRequestResponse.builder()
                    .number(pr.getNumber())
                    .title(pr.getTitle())
                    .state(pr.getState().name())
                    .status(status)
                    .author(pr.getUser().getLogin())
                    .url(pr.getHtmlUrl().toString())
                    .createdAt(pr.getCreatedAt() != null ? pr.getCreatedAt().toString() : null)
                    .updatedAt(pr.getUpdatedAt() != null ? pr.getUpdatedAt().toString() : null)
                    .branch(pr.getHead().getRef())
                    .baseBranch(pr.getBase().getRef())
                    .build();
        } catch (IOException e) {
            log.error("Error mapping pull request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to map pull request", e);
        }
    }

    /**
     * Get the commit status for the PR head
     * Returns UNKNOWN if status cannot be retrieved to avoid rate limiting issues
     */
    private String getCommitStatus(GHPullRequest pr) {
        try {
            GHCommitStatus lastStatus = pr.getRepository()
                    .getLastCommitStatus(pr.getHead().getSha());
            
            if (lastStatus != null) {
                return lastStatus.getState().name();
            }
        } catch (Exception e) {
            // Log at debug level to avoid noise - status retrieval is optional
            log.debug("Could not fetch commit status for PR #{}: {}", pr.getNumber(), e.getMessage());
        }
        return "UNKNOWN";
    }
}
