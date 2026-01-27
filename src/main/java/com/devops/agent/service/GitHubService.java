package com.devops.agent.service;

import com.devops.agent.model.OllamaGenerateRequest;
import com.devops.agent.model.OllamaGenerateResponse;
import com.devops.agent.model.ProjectConfiguration;
import com.devops.agent.model.PullRequestResponse;
import com.devops.agent.service.exception.GitHubServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubService {

    @Autowired
    private WebClient ollamaWebClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectConfigurationService projectConfigurationService;

    @Autowired
    private SecretsManagerService secretsManagerService;

    @Value("${github.repository.owner:sdc-pune}")
    private String defaultRepositoryOwner;

    @Value("${github.repository.name:shoma-ui}")
    private String defaultRepositoryName;

    @Value("${github.token:}")
    private String defaultGithubToken;

    /**
     * Helper class to hold project-specific GitHub configuration
     */
    private static class GitHubConfig {
        String owner;
        String repo;
        String token;

        GitHubConfig(String owner, String repo, String token) {
            this.owner = owner;
            this.repo = repo;
            this.token = token;
        }
    }

    /**
     * Get GitHub configuration for a specific project
     */
    private GitHubConfig getGitHubConfig(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            // Use default configuration
            return new GitHubConfig(defaultRepositoryOwner, defaultRepositoryName, defaultGithubToken);
        }

        try {
            // Fetch project configuration from DynamoDB
            ProjectConfiguration projectConfig = projectConfigurationService.getConfiguration(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            // Fetch secrets from Secrets Manager
            Map<String, String> secrets = secretsManagerService.getSecrets(projectId);
            String githubToken = secrets.get("github-token");

            if (githubToken == null || githubToken.isEmpty()) {
                throw new RuntimeException("GitHub token not found for project: " + projectId);
            }

            return new GitHubConfig(
                    projectConfig.getGithubOwner(),
                    projectConfig.getGithubRepo(),
                    githubToken
            );
        } catch (Exception e) {
            log.error("Failed to get GitHub config for project {}: {}", projectId, e.getMessage(), e);
            throw new RuntimeException("Failed to get GitHub configuration for project: " + projectId, e);
        }
    }

    /**
     * Get all open pull requests from the past 2 weeks from the repository.
     * Uses GitHub GraphQL API for efficiency to fetch all required data in minimal API calls.
     *
     * @param projectId The project ID to fetch PRs for (null for default project)
     */
    public List<PullRequestResponse> getOpenPullRequests(String projectId) {
        GitHubConfig config = getGitHubConfig(projectId);
        log.info("Fetching open pull requests from past 2 weeks for {}/{} (project: {})",
                config.owner, config.repo, projectId != null ? projectId : "default");
        try {
            LocalDate twoWeeksAgo = LocalDate.now(ZoneId.of("UTC")).minusWeeks(2);

            String query = """
                    query ($owner: String!, $name: String!, $after: String) {
                      repository(owner: $owner, name: $name) {
                        pullRequests(states: OPEN, orderBy: {field: CREATED_AT, direction: DESC}, first: 100, after: $after) {
                          nodes {
                            number
                            title
                            state
                            url
                            createdAt
                            updatedAt
                            headRefName
                            baseRefName
                            author {
                              ... on User {
                                login
                                name
                                avatarUrl
                              }
                            }
                            commits(first: 20) {
                              totalCount
                              nodes {
                                commit {
                                  message
                                }
                              }
                            }
                            additions
                            deletions
                            changedFiles
                            reviewRequests(first: 10) {
                              nodes {
                                requestedReviewer {
                                  ... on User {
                                    login
                                    name
                                  }
                                }
                              }
                            }
                            labels(first: 20) {
                              nodes {
                                name
                              }
                            }
                            statusCheckRollup {
                              state
                            }
                          }
                          pageInfo {
                            endCursor
                            hasNextPage
                          }
                        }
                      }
                    }
                    """;

            Map<String, Object> variables = new HashMap<>();
            variables.put("owner", config.owner);
            variables.put("name", config.repo);
            variables.put("after", null);

            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("query", query);
            bodyMap.put("variables", variables);

            HttpClient httpClient = HttpClient.newHttpClient();

            List<PullRequestResponse> results = new ArrayList<>();
            String cursor = null;
            boolean hasNext = true;

            while (hasNext) {
                variables.put("after", cursor);
                String requestBody = objectMapper.writeValueAsString(bodyMap);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/graphql"))
                        .header("Authorization", "bearer " + config.token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new GitHubServiceException("GraphQL request failed with status: " + response.statusCode() + ", body: " + response.body());
                }

                JsonNode json = objectMapper.readTree(response.body());

                if (json.has("errors")) {
                    throw new GitHubServiceException("GraphQL errors: " + json.get("errors").toString());
                }

                JsonNode nodes = json.path("data").path("repository").path("pullRequests").path("nodes");
                JsonNode pageInfo = json.path("data").path("repository").path("pullRequests").path("pageInfo");

                cursor = pageInfo.path("endCursor").asText(null);
                hasNext = pageInfo.path("hasNextPage").asBoolean(false);

                boolean continuePaging = true;
                for (JsonNode node : nodes) {
                    Instant createdInstant = Instant.parse(node.get("createdAt").asText());
                    LocalDate createdDate = createdInstant.atZone(ZoneId.of("UTC")).toLocalDate();
                    if (createdDate.isBefore(twoWeeksAgo)) {
                        continuePaging = false;
                        hasNext = false;
                        break;
                    }
                    PullRequestResponse prResponse = mapFromJson(node);
                    results.add(prResponse);
                }
                if (!continuePaging) {
                    break;
                }
            }

            return results;
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching pull requests: {}", e.getMessage(), e);
            throw new GitHubServiceException("Failed to fetch pull requests", e);
        }
    }

    /**
     * Map GraphQL JSON node to response DTO
     */
    private PullRequestResponse mapFromJson(JsonNode node) {
        int number = node.get("number").asInt();
        String title = node.get("title").asText();
        String state = node.get("state").asText();
        String url = node.get("url").asText();
        Instant createdAt = Instant.parse(node.get("createdAt").asText());
        Instant updatedAt = Instant.parse(node.get("updatedAt").asText());
        String headRef = node.get("headRefName").asText();
        String baseRef = node.get("baseRefName").asText();
        JsonNode author = node.get("author");
        String authorLogin = author != null ? author.path("login").asText(null) : null;
        String authorName = author != null ? author.path("name").asText(null) : null;
        String authorAvatar = author != null ? author.path("avatarUrl").asText(null) : null;
        String authorDisplay = (authorName != null && !authorName.isBlank()) ? authorName : authorLogin;
        JsonNode commitsNode = node.path("commits");
        int commits = commitsNode.path("totalCount").asInt(0);
        int additions = node.get("additions").asInt(0);
        int deletions = node.get("deletions").asInt(0);
        int filesChanged = node.get("changedFiles").asInt(0);

        List<String> commitMessages = new ArrayList<>();
        JsonNode msgNodes = commitsNode.path("nodes");
        if (!msgNodes.isMissingNode() && msgNodes.isArray()) {
            for (JsonNode c : msgNodes) {
                String message = c.path("commit").path("message").asText(null);
                if (message != null && !message.isBlank()) {
                    commitMessages.add(message);
                }
            }
        }

        List<String> reviewers = new ArrayList<>();
        JsonNode reviewRequests = node.path("reviewRequests").path("nodes");
        if (!reviewRequests.isMissingNode() && reviewRequests.isArray()) {
            for (JsonNode r : reviewRequests) {
                JsonNode requestedReviewer = r.path("requestedReviewer");
                if (!requestedReviewer.isMissingNode()) {
                    String name = requestedReviewer.path("name").asText(null);
                    String login = requestedReviewer.path("login").asText(null);
                    String reviewerName = (name != null && !name.isBlank()) ? name : login;
                    if (reviewerName != null) {
                        reviewers.add(reviewerName);
                    }
                }
            }
        }

        List<String> labels = StreamSupport.stream(node.path("labels").path("nodes").spliterator(), false)
                .map(l -> l.get("name").asText(null))
                .filter(Objects::nonNull)
                .toList();

        String priority = computePriority(labels);

        String status = node.path("statusCheckRollup").path("state").asText("UNKNOWN");

        String createdHuman = humanize(createdAt);
        String updatedHuman = humanize(updatedAt);

        // Generate AI suggestion based on commit messages
        String aiSuggestion = summarizeCommitsWithOllama(commitMessages, number);

        return PullRequestResponse.builder()
                .number(number)
                .id("PR-" + number)
                .title(title)
                .state(state)
                .status(status)
                .author(authorDisplay)
                .avatar(authorAvatar)
                .url(url)
                .createdAt(createdHuman)
                .updatedAt(updatedHuman)
                .branch(headRef)
                .targetBranch(baseRef)
                .baseBranch(baseRef) // deprecated but filled for compatibility
                .commits(commits)
                .additions(additions)
                .deletions(deletions)
                .filesChanged(filesChanged)
                .reviewers(reviewers)
                .labels(labels)
                .priority(priority)
                // Placeholder pipeline fields; integrate with actual CI source as available
                .pipeline(statusToPipeline(status))
                .pipelineType("Mock Auth")
                // AI suggestion from commit messages
                .aiSuggestion(aiSuggestion)
                .aiInsight(null)
                .build();
    }

    /**
     * Get a specific pull request by number
     *
     * @param projectId The project ID (null for default project)
     * @param number The pull request number
     */
    public PullRequestResponse getPullRequest(String projectId, int number) {
        GitHubConfig config = getGitHubConfig(projectId);
        log.info("Fetching pull request #{} for {}/{} (project: {})", number, config.owner, config.repo,
                projectId != null ? projectId : "default");
        try {
            String query = """
                    query ($owner: String!, $name: String!, $number: Int!) {
                      repository(owner: $owner, name: $name) {
                        pullRequest(number: $number) {
                          number
                          title
                          state
                          url
                          createdAt
                          updatedAt
                          headRefName
                          baseRefName
                          author {
                            ... on User {
                              login
                              name
                              avatarUrl
                            }
                          }
                          commits(first: 20) {
                            totalCount
                            nodes {
                              commit {
                                message
                              }
                            }
                          }
                          additions
                          deletions
                          changedFiles
                          reviewRequests(first: 10) {
                            nodes {
                              requestedReviewer {
                                ... on User {
                                  login
                                  name
                                }
                              }
                            }
                          }
                          labels(first: 20) {
                            nodes {
                              name
                            }
                          }
                          statusCheckRollup {
                            state
                          }
                        }
                      }
                    }
                    """;

            Map<String, Object> variables = new HashMap<>();
            variables.put("owner", config.owner);
            variables.put("name", config.repo);
            variables.put("number", number);

            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("query", query);
            bodyMap.put("variables", variables);

            String requestBody = objectMapper.writeValueAsString(bodyMap);

            HttpClient httpClient = HttpClient.newHttpClient();
            log.info("GitHub token present: {}",
                    config.token);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/graphql"))
                    .header("Authorization", "Bearer " + config.token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new GitHubServiceException("GraphQL request failed with status: " + response.statusCode() + ", body: " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());

            if (json.has("errors")) {
                throw new GitHubServiceException("GraphQL errors: " + json.get("errors").toString());
            }

            JsonNode prNode = json.path("data").path("repository").path("pullRequest");
            if (prNode.isMissingNode()) {
                throw new GitHubServiceException("Pull request #" + number + " not found");
            }

            return mapFromJson(prNode);
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching pull request #{}: {}", number, e.getMessage(), e);
            throw new GitHubServiceException("Failed to fetch pull request #" + number, e);
        }
    }

    private String humanize(Instant instant) {
        if (instant == null) return null;
        Instant now = Instant.now();
        Duration d = Duration.between(instant, now);
        long seconds = Math.max(0, d.getSeconds());
        if (seconds < 60) return seconds + " seconds ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        if (days < 7) return days + (days == 1 ? " day ago" : " days ago");
        long weeks = days / 7;
        if (weeks < 4) return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        long months = days / 30;
        if (months < 12) return months + (months == 1 ? " month ago" : " months ago");
        long years = days / 365;
        return years + (years == 1 ? " year ago" : " years ago");
    }

    private String computePriority(List<String> labels) {
        if (labels == null || labels.isEmpty()) return "Normal";
        String lowered = String.join(" ", labels).toLowerCase();
        if (lowered.contains("p0") || lowered.contains("critical") || lowered.contains("high-priority") || lowered.contains("urgent")) {
            return "High";
        }
        if (lowered.contains("medium") || lowered.contains("p1") || lowered.contains("priority")) {
            return "Medium";
        }
        if (lowered.contains("low") || lowered.contains("p2")) {
            return "Low";
        }
        return "Normal";
    }

    private String statusToPipeline(String status) {
        if (status == null) return "Unknown";
        return switch (status.toUpperCase()) {
            case "SUCCESS" -> "Passed";
            case "FAILURE", "ERROR" -> "Failed";
            case "PENDING" -> "Running";
            default -> "Unknown";
        };
    }

    /**
     * Summarize PR commits using Ollama AI model
     * Returns a concise summary of what the PR does based on commit messages
     */
    private String summarizeCommitsWithOllama(List<String> commitMessages, int prNumber) {
        if (commitMessages == null || commitMessages.isEmpty()) {
            return null;
        }

        try {
            // Build the commit messages into a single text
            StringBuilder commitsText = new StringBuilder();
            for (int i = 0; i < commitMessages.size(); i++) {
                commitsText.append(i + 1).append(". ").append(commitMessages.get(i)).append("\n");
            }

            String prompt = String.format(
                    "Analyze the following commit messages from Pull Request #%d and provide a brief, actionable suggestion (max 15 words) on what needs attention or improvement:\n\n%s\n\nSuggestion:",
                    prNumber,
                    commitsText
            );

            OllamaGenerateRequest ollamaRequest = new OllamaGenerateRequest("qwen2.5-coder:7b", prompt, false);

            // Make synchronous call with timeout
            OllamaGenerateResponse ollamaResponse = ollamaWebClient
                    .post()
                    .uri("/api/generate")
                    .bodyValue(ollamaRequest)
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (ollamaResponse != null && ollamaResponse.getResponse() != null) {
                String summary = ollamaResponse.getResponse().trim();
                // Limit to reasonable length
                if (summary.length() > 200) {
                    summary = summary.substring(0, 197) + "...";
                }
                return summary;
            }
        } catch (Exception e) {
            log.warn("Failed to get Ollama summary for PR #{}: {}", prNumber, e.getMessage());
        }

        return null;
    }
}