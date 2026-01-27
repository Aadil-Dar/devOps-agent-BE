package com.devops.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private GitHub gitHubClient;

    @Mock
    private GHRepository repository;

    private GitHubService gitHubService;

    @BeforeEach
    void setUp() {
        gitHubService = new GitHubService();
    }

    @Test
    void testGetOpenPullRequests_IOException() throws IOException {
        // Arrange
        when(gitHubClient.getRepository(anyString())).thenThrow(new IOException("Connection error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> gitHubService.getOpenPullRequests());
        assertTrue(exception.getMessage().contains("Failed to fetch pull requests"));
    }

    @Test
    void testGetPullRequest_NotFound() throws IOException {
        // Arrange
        int prNumber = 999;
        when(gitHubClient.getRepository(anyString())).thenReturn(repository);
        when(repository.getPullRequest(prNumber)).thenThrow(new IOException("Not found"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> gitHubService.getPullRequest(prNumber));
        assertTrue(exception.getMessage().contains("Failed to fetch pull request"));
    }

    @Test
    void testGetOpenPullRequests_RepositoryAccessSuccess() throws IOException {
        // Arrange
        when(gitHubClient.getRepository(anyString())).thenReturn(repository);
        when(repository.getPullRequests(any(GHIssueState.class))).thenReturn(java.util.Collections.emptyList());

        // Act
        var result = gitHubService.getOpenPullRequests();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(gitHubClient, times(1)).getRepository(anyString());
        verify(repository, times(1)).getPullRequests(GHIssueState.OPEN);
    }
}
