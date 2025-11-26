package com.devops.agent.controller;

import com.devops.agent.model.PullRequestResponse;
import com.devops.agent.service.GitHubService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PullRequestController.class)
class PullRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitHubService gitHubService;

    @Test
    void testGetOpenPullRequests_Success() throws Exception {
        // Arrange
        PullRequestResponse pr1 = PullRequestResponse.builder()
                .number(1)
                .title("Test PR 1")
                .state("OPEN")
                .status("SUCCESS")
                .author("testuser1")
                .url("https://github.com/test/repo/pull/1")
                .branch("feature-1")
                .baseBranch("main")
                .build();

        PullRequestResponse pr2 = PullRequestResponse.builder()
                .number(2)
                .title("Test PR 2")
                .state("OPEN")
                .status("PENDING")
                .author("testuser2")
                .url("https://github.com/test/repo/pull/2")
                .branch("feature-2")
                .baseBranch("main")
                .build();

        List<PullRequestResponse> pullRequests = Arrays.asList(pr1, pr2);
        when(gitHubService.getOpenPullRequests()).thenReturn(pullRequests);

        // Act & Assert
        mockMvc.perform(get("/api/pull-requests")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].title").value("Test PR 1"))
                .andExpect(jsonPath("$[0].state").value("OPEN"))
                .andExpect(jsonPath("$[0].author").value("testuser1"))
                .andExpect(jsonPath("$[1].number").value(2))
                .andExpect(jsonPath("$[1].title").value("Test PR 2"));
    }

    @Test
    void testGetOpenPullRequests_Error() throws Exception {
        // Arrange
        when(gitHubService.getOpenPullRequests()).thenThrow(new RuntimeException("GitHub API error"));

        // Act & Assert
        mockMvc.perform(get("/api/pull-requests")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetPullRequest_Success() throws Exception {
        // Arrange
        PullRequestResponse pr = PullRequestResponse.builder()
                .number(1)
                .title("Test PR")
                .state("OPEN")
                .status("SUCCESS")
                .author("testuser")
                .url("https://github.com/test/repo/pull/1")
                .branch("feature-branch")
                .baseBranch("main")
                .build();

        when(gitHubService.getPullRequest(1)).thenReturn(pr);

        // Act & Assert
        mockMvc.perform(get("/api/pull-requests/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.title").value("Test PR"))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.author").value("testuser"));
    }

    @Test
    void testGetPullRequest_NotFound() throws Exception {
        // Arrange
        when(gitHubService.getPullRequest(anyInt())).thenThrow(new RuntimeException("PR not found"));

        // Act & Assert
        mockMvc.perform(get("/api/pull-requests/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
